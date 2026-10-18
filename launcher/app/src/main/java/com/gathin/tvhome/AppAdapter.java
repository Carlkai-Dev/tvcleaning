package com.gathin.tvhome;

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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions form the dock.
 * The saved user order takes precedence over the default favourites and alphabetical order.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);
        void onTileMenuRequested(int position, View card);

        /** Called for all four D-pad directions. Return true when handled. */
        boolean onTileDirectionKey(int position, int keyCode);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        final String sourcePath;
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon, String sourcePath) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.sourcePath = sourcePath;
        }
    }

    static final float FOCUS_SCALE = 1.20f;
    static final float MENU_SCALE = 1.24f;

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (res/values/config.xml, dock_apps). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();
    private final int artworkWidth;
    private final int artworkHeight;
    private final int iconSize;
    private List<Entry> moveSnapshot;
    private int focusedDockPosition = RecyclerView.NO_POSITION;
    private final DecelerateInterpolator dockEase = new DecelerateInterpolator(1.5f);

    AppAdapter(Context ctx, TileListener listener, int dockSize) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        artworkWidth = (int) Math.ceil(ctx.getResources().getDimension(R.dimen.tile_width) * MENU_SCALE);
        artworkHeight = (int) Math.ceil(ctx.getResources().getDimension(R.dimen.tile_height) * MENU_SCALE);
        iconSize = (int) Math.ceil(32 * ctx.getResources().getDisplayMetrics().density * MENU_SCALE);
        this.favourites = Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps));
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        Set<String> userHidden = userHiddenPackages();
        Map<ComponentName, Entry> previous = new HashMap<>();
        for (Entry entry : items) previous.put(entry.component, entry);
        List<Entry> fresh = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || userHidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            String sourcePath = ri.activityInfo.applicationInfo.sourceDir;
            String label = String.valueOf(ri.loadLabel(pm));
            Entry cached = previous.get(component);
            if (cached != null && sourcePath.equals(cached.sourcePath) && label.equals(cached.label)) {
                fresh.add(cached);
                continue;
            }
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            // Keep only a tile-sized image, including headroom for the long-press scale.
            // Banner apps do not need an additional launcher icon in memory.
            Drawable icon = banner == null ? fitArtwork(ri.loadIcon(pm), iconSize, iconSize) : null;
            fresh.add(new Entry(label, component, fitArtwork(banner, artworkWidth, artworkHeight), icon, sourcePath));
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
        List<String> saved = new ArrayList<>();
        try {
            JSONArray order = new JSONArray(ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
                    .getString("app_order", "[]"));
            for (int i = 0; i < order.length(); i++) saved.add(order.getString(i));
        } catch (JSONException ignored) { }
        if (!saved.isEmpty()) {
            // Stable sort: newly installed apps retain their default relative order at the end.
            Collections.sort(fresh, (a, b) -> {
                int ia = saved.indexOf(a.component.flattenToShortString());
                int ib = saved.indexOf(b.component.flattenToShortString());
                return Integer.compare(ia < 0 ? Integer.MAX_VALUE : ia,
                        ib < 0 ? Integer.MAX_VALUE : ib);
            });
        }
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    private Drawable fitArtwork(Drawable drawable, int maxWidth, int maxHeight) {
        if (drawable == null) return null;
        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : maxWidth;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : maxHeight;
        float scale = Math.min(1f, Math.min(maxWidth / (float) width, maxHeight / (float) height));
        width = Math.max(1, Math.round(width * scale));
        height = Math.max(1, Math.round(height * scale));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(ctx.getResources().getDisplayMetrics().densityDpi);
        Rect bounds = new Rect(drawable.getBounds());
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(bitmap));
        drawable.setBounds(bounds);
        return new BitmapDrawable(ctx.getResources(), bitmap);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);   // the dock has no labels
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.card.animate().cancel();
        h.name.animate().cancel();
        h.highlight.animate().cancel();
        h.highlight.setAlpha(focused ? 1f : 0f);
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        h.card.setTranslationX(dockOffsetFor(position));
        h.card.setElevation(focused ? 3f * ctx.getResources().getDisplayMetrics().density : 0f);
        h.name.setTranslationY(focused ? 2f * ctx.getResources().getDisplayMetrics().density
                : -4f * ctx.getResources().getDisplayMetrics().density);
        h.card.setContentDescription(e.label);
        if (e.banner != null) {
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
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int current = h.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION) listener.onTileMenuRequested(current, h.card);
            return true;
        });
    }

    private float dockOffsetFor(int position) {
        if (focusedDockPosition == RecyclerView.NO_POSITION || position < 0 || position >= dockSize
                || Math.abs(position - focusedDockPosition) != 1) return 0f;
        return (position < focusedDockPosition ? -5f : 5f) * ctx.getResources().getDisplayMetrics().density;
    }

    /** Only the immediate neighbours move; dock geometry and the saved app order stay fixed. */
    void updateDockSpacing(RecyclerView grid, int position, boolean animate) {
        focusedDockPosition = position >= 0 && position < dockSize ? position : RecyclerView.NO_POSITION;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View item = grid.getChildAt(i);
            View card = item.findViewById(R.id.card);
            if (card == null) continue;
            float offset = dockOffsetFor(grid.getChildAdapterPosition(item));
            if (Math.abs(card.getTranslationX() - offset) < 0.1f) continue;
            if (animate) card.animate().translationX(offset).setDuration(200)
                    .setInterpolator(dockEase).start();
            else {
                card.animate().cancel();
                card.setTranslationX(offset);
            }
        }
    }

    void beginMove() {
        moveSnapshot = new ArrayList<>(items);
    }

    void move(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        Collections.swap(items, from, to);
        // Rebind dock/grid labels as well as positions when crossing the dock boundary.
        notifyDataSetChanged();
    }

    boolean finishMove(boolean save) {
        if (save) {
            JSONArray order = new JSONArray();
            List<String> keys = new ArrayList<>();
            for (Entry entry : items) keys.add(entry.component.flattenToShortString());
            // Keep hidden apps in the stored order so showing them again preserves their place.
            try {
                JSONArray previous = new JSONArray(ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
                        .getString("app_order", "[]"));
                Set<String> userHidden = userHiddenPackages();
                for (int i = 0; i < previous.length(); i++) {
                    String key = previous.getString(i);
                    ComponentName component = ComponentName.unflattenFromString(key);
                    if (component != null && userHidden.contains(component.getPackageName()) && !keys.contains(key))
                        keys.add(Math.min(i, keys.size()), key);
                }
            } catch (JSONException ignored) { }
            for (String key : keys) order.put(key);
            if (!ctx.getSharedPreferences("home", Context.MODE_PRIVATE).edit()
                    .putString("app_order", order.toString()).commit()) return false;
        } else if (moveSnapshot != null) {
            items.clear();
            items.addAll(moveSnapshot);
            notifyDataSetChanged();
        }
        moveSnapshot = null;
        return true;
    }

    Entry entryAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    Set<String> userHiddenPackages() {
        return new HashSet<>(ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
                .getStringSet("hidden_packages", Collections.emptySet()));
    }

    boolean setHidden(String pkg, boolean hide) {
        Set<String> packages = userHiddenPackages();
        if (hide) {
            // Snapshot the current default order the first time a tile is hidden.
            if (!ctx.getSharedPreferences("home", Context.MODE_PRIVATE).contains("app_order")
                    && !finishMove(true)) return false;
            packages.add(pkg);
        } else packages.remove(pkg);
        return ctx.getSharedPreferences("home", Context.MODE_PRIVATE).edit()
                .putStringSet("hidden_packages", packages).commit();
    }

    private void launch(Entry e) {
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
        return items.get(position).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
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

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        final GlassView highlight;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);
            highlight = v.findViewById(R.id.tileGlass);

            final float density = v.getResources().getDisplayMetrics().density;
            final float radius = v.getResources().getDimension(R.dimen.tile_radius);
            final DecelerateInterpolator ease = new DecelerateInterpolator(1.5f);
            View artwork = v.findViewById(R.id.artwork);
            artwork.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            // Clip the image only. The halo has its own 6 dp margin inside the scaled parent.
            artwork.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        view.animate().scaleX(MENU_SCALE).scaleY(MENU_SCALE)
                                .setInterpolator(ease)
                                .setDuration(ViewConfiguration.getLongPressTimeout()).start();
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        view.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE)
                                .setInterpolator(ease).setDuration(160).start();
                    }
                    return false; // Android still distinguishes click from long click.
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileDirectionKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                highlight.animate().alpha(focused ? 1f : 0f).setDuration(focused ? 130 : 100).start();
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().scaleX(s).scaleY(s)
                        .setDuration(focused ? 200 : 160)
                        .setInterpolator(ease)
                        .start();
                view.setElevation(focused ? 3f * density : 0f);
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 2f : -density * 4f)
                        .setDuration(160).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
