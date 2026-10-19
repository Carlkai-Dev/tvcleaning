package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the dock. Configured favourites and alphabetical order are defaults until customized.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        void onTileLongPressed(int position, View card);

        boolean onTileClicked();

        void onFolderOpened(String key);

        void onTileFocusFrame(View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);
    }

    static final class Entry {
        final String label;
        final String key;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        int ambient;
        Drawable glyph;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.key = component.flattenToShortString();
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
        }
        Entry(String key, String label, Drawable preview) {
            this.key = key; this.label = label; this.component = null;
            this.pkg = null; this.banner = preview; this.icon = null;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 108f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final boolean showHomeSettings;
    private final List<Entry> items = new ArrayList<>();
    private final HomeLayoutStore layoutStore;
    private HomeLayout layout = new HomeLayout();
    private final java.util.Map<String, Entry> inventory = new java.util.LinkedHashMap<>();
    private String activeFolder;
    private int columns;

    HomeLayout snapshotLayout() { return layout.copy(); }
    String activeFolder() { return activeFolder; }
    int columns() { return columns; }
    boolean isFolder(int position) { return layout.folders.containsKey(keyAt(position)); }
    HomeLayout.Folder folder(String key) { return layout.folders.get(key); }
    void showFolder(String key) { activeFolder = key; columns = key == null ? dockSize : 3; rebuild(); }
    void restoreLayout(HomeLayout saved) { layout = saved.copy(); rebuild(); }

    private void rebuild() {
        if (activeFolder != null && !layout.folders.containsKey(activeFolder)) activeFolder = null;
        columns = activeFolder == null ? dockSize : 3;
        items.clear();
        List<String> available = new ArrayList<>(inventory.keySet());
        for (String key : layout.visible(activeFolder, available)) {
            HomeLayout.Folder folder = layout.folders.get(key);
            if (folder == null) items.add(inventory.get(key));
            else {
                List<Drawable> art = new ArrayList<>();
                for (String app : folder.apps) {
                    Entry e = inventory.get(app);
                    if (e != null) {
                        Drawable d = e.banner != null ? e.banner : e.icon;
                        art.add(d != null && d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d);
                    }
                }
                items.add(new Entry(key, folder.name, new FolderPreviewDrawable(art)));
            }
        }
        notifyDataSetChanged();
    }
    private String movingKey;
    private String menuKey;

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.layoutStore = new HomeLayoutStore(ctx);
        this.columns = dockSize;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }
        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        // Optional "Default home" tile (config.xml), only when this TV actually has a home-screen picker.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings)));
        }
        inventory.clear();
        for (Entry entry : fresh) inventory.put(entry.key, entry);
        layout = layoutStore.read();
        layout.reconcile(new ArrayList<>(inventory.keySet()));
        rebuild();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, columns, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        float d = ctx.getResources().getDisplayMetrics().density;
        RecyclerView parent = (RecyclerView) h.itemView.getParent();
        if (parent != null) {
            int w = tileWidthPx(parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight(), columns, d);
            int height = tileHeightPx(w);
            ViewGroup.LayoutParams cardLp = h.card.getLayoutParams();
            cardLp.width = w; cardLp.height = height; h.card.setLayoutParams(cardLp);
            ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) h.name.getLayoutParams();
            nameLp.width = w; nameLp.topMargin = height + Math.round(7 * d); h.name.setLayoutParams(nameLp);
        }
        h.card.setBackground(isFolder(position) ? new GlassPanelDrawable(d, 16) : ctx.getDrawable(R.drawable.tile_bg));
        android.text.SpannableString caption = new android.text.SpannableString("●  " + e.label);
        caption.setSpan(new FocusDotSpan(d), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        h.name.setText(caption);
        h.name.setVisibility(e.label.isEmpty() || (activeFolder == null && position < dockSize) || movingKey != null ? View.INVISIBLE : View.VISIBLE);
        // Keep captions quiet during rearrangement; the lifted app and direction handles show selection.
        h.card.setActivated(e.key.equals(movingKey)
                || e.key.equals(menuKey));
        boolean focused = h.card.isFocused() || h.card.isActivated();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.animate().cancel();
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        boolean glyphTile = "com.spotify.tv.android".equals(e.pkg) && e.icon != null;
        h.banner.setPadding(0, 0, 0, 0);
        h.banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (glyphTile) {
            // The reference uses Spotify's large glyph, without duplicating its wordmark in the tile.
            android.graphics.drawable.GradientDrawable backing = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF242624, 0xFF101311});
            h.card.setBackground(backing);
            int inset = Math.round(h.card.getLayoutParams().height * .06f);
            h.banner.setPadding(inset, inset, inset, inset);
            h.banner.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (e.glyph == null) e.glyph = spotifyGlyph(e.icon);
            h.banner.setImageDrawable(e.glyph);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setContentDescription(isFolder(position) ? e.label.isEmpty()
                ? ctx.getString(R.string.unnamed_folder) : ctx.getString(R.string.folder_description, e.label) : e.label);
        boolean moving = e.key.equals(movingKey);
        h.card.setElevation((moving ? 9 : 3) * d);
        h.card.setTranslationZ(focused ? 4 * d : 0f);
        h.card.setForeground(null);
        h.card.setOnClickListener(v -> {
            if (listener == null || !listener.onTileClicked()) {
                if (e.component == null) { if (listener != null) listener.onFolderOpened(e.key); }
                else launch(e);
            }
        });
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) listener.onTileLongPressed(pos, v);
            return true;
        });
    }

    List<String> keys() {
        List<String> result = new ArrayList<>();
        for (Entry entry : items) result.add(entry.key);
        return result;
    }

    void applyOrder(List<String> order) {
        List<String> keys = AppOrder.reconcile(keys(), order);
        java.util.Map<String, Entry> entries = new java.util.HashMap<>();
        for (Entry entry : items) entries.put(entry.key, entry);
        items.clear();
        for (String key : keys) items.add(entries.get(key));
        layout.reorder(activeFolder, keys());
        notifyDataSetChanged();
    }

    void move(int from, int to) {
        if (from == to) return;
        items.add(to, items.remove(from));
        layout.reorder(activeFolder, keys());
        notifyItemMoved(from, to);
        // Positions determine dock labels as well as decoration, even for displaced neighbours.
        notifyItemRangeChanged(Math.min(from, to), Math.abs(from - to) + 1);
    }

    void setMenuKey(String key) {
        menuKey = key;
        notifyItemRangeChanged(0, items.size());
    }

    void setMovingKey(String key) {
        movingKey = key;
        notifyItemRangeChanged(0, items.size());
    }

    boolean isApp(int position) {
        return position >= 0 && position < items.size() && items.get(position).component != null
                && !items.get(position).component.equals(new ComponentName(ctx, HomeSettingsActivity.class));
    }

    void openAppSettings(String key) {
        int position = positionOf(key);
        if (position < 0 || !isApp(position)) return;
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + items.get(position).pkg));
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(ctx, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void launch(Entry e) {
        if (e.component.equals(new ComponentName(ctx, HomeSettingsActivity.class))) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key.hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).key : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** Remove the Android icon's square black matte, preserving the green glyph's antialiasing. */
    private Drawable spotifyGlyph(Drawable original) {
        Drawable source = original.getConstantState() != null
                ? original.getConstantState().newDrawable().mutate() : original;
        Rect bounds = new Rect(source.getBounds());
        int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        source.setBounds(0, 0, size, size);
        source.draw(new Canvas(bitmap));
        source.setBounds(bounds);
        int[] pixels = new int[size * size];
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
        int peak = 0, colour = 0;
        for (int pixel : pixels) {
            int green = Color.green(pixel) - Math.max(Color.red(pixel), Color.blue(pixel));
            if (green > peak) { peak = green; colour = pixel & 0xFFFFFF; }
        }
        if (peak < 12) { bitmap.recycle(); return original; }
        int left = size, top = size, right = -1, bottom = -1;
        for (int i = 0; i < pixels.length; i++) {
            int green = Math.max(0, Color.green(pixels[i]) - Math.max(Color.red(pixels[i]), Color.blue(pixels[i])));
            int alpha = Color.alpha(pixels[i]) * green / peak;
            pixels[i] = (alpha << 24) | colour;
            if (alpha > 4) {
                int x = i % size, y = i / size;
                left = Math.min(left, x); top = Math.min(top, y);
                right = Math.max(right, x); bottom = Math.max(bottom, y);
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        Bitmap glyph = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
        if (glyph != bitmap) bitmap.recycle();
        return new android.graphics.drawable.BitmapDrawable(ctx.getResources(), glyph);
    }

    private static final class FocusDotSpan extends android.text.style.ReplacementSpan {
        private final float radius;
        FocusDotSpan(float density) { radius = 4.5f * density; }
        @Override public int getSize(@NonNull android.graphics.Paint paint, CharSequence text,
                int start, int end, android.graphics.Paint.FontMetricsInt metrics) { return Math.round(radius * 2); }
        @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, @NonNull android.graphics.Paint paint) {
            int colour = paint.getColor();
            paint.setColor(0xFF009AFF);
            canvas.drawCircle(x + radius, y + (paint.ascent() + paint.descent()) / 2, radius, paint);
            paint.setColor(colour);
        }
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 22f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        boolean suppressConfirmRelease;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            // The launcher draws its own app-colour focus light; avoid a platform fill behind glass.
            card.setDefaultFocusHighlightEnabled(false);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                    // A filled native elevation shadow shows through the translucent folder as an
                    // inset grey panel on TV hardware. Keep clipping, and use only our outer glow.
                    outline.setAlpha(view.getBackground() instanceof GlassPanelDrawable ? 0f : 1f);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                boolean confirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
                if (confirm) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                        suppressConfirmRelease = false;
                    // Some remotes report an explicit long-press repeat instead of waiting for
                    // View's timer. Handle both without allowing the release to launch the app.
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
                        view.cancelLongPress();
                        view.setPressed(false);
                        suppressConfirmRelease = true;
                        int pos = getBindingAdapterPosition();
                        if (listener != null && pos != RecyclerView.NO_POSITION)
                            listener.onTileLongPressed(pos, view);
                        return true;
                    }
                    if (event.getAction() == KeyEvent.ACTION_UP && suppressConfirmRelease) {
                        suppressConfirmRelease = false;
                        view.setPressed(false);
                        return true;
                    }
                }
                // Direction keys are handled by the activity, including while the target row is
                // being recycled. The card keeps only click/long-press key handling.
                return false;
            });

            card.setOnFocusChangeListener((view, focused) -> {
                boolean highlighted = focused || view.isActivated();
                float s = highlighted ? FOCUS_SCALE : 1.0f;
                view.animate().scaleX(s).scaleY(s).translationZ(highlighted ? 4 * density : 0f)
                        .setUpdateListener(animation -> {
                            if (listener != null && view.isFocused()) listener.onTileFocusFrame(view);
                        })
                        .setDuration(focused ? 260 : 180)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                        .start();
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 4f : -density * 6f)
                        .setDuration(220).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
