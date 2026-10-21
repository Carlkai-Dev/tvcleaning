package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Zen list: one app per row, its name set large and light, an index on the left.
 *
 * <p>There is no artwork on the list. The wallpaper is the picture; the list is type on top of it,
 * which is the whole point of the layout. The focused row is white at full strength, the rest sit
 * back at a third of it, and the only other mark is a faint wash behind the focused row.
 *
 * <p>The first row is still the pinned dock (dock_apps, in order): the list keeps it at the bottom of
 * the screen, so Home always opens on the same favourites.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View row);

        /** Called for a D-pad direction key on a row. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);
    }

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2, EMPTY = 3;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        /** One line under the name: a card's caption, or the package name when two apps share a label. */
        final String meta;

        Entry(int kind, String label, ComponentName component, String meta) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.meta = meta;
        }
    }

    /** Resting and focused type sizes, in sp. The row is tall enough for the focused size. */
    static final float RESTING_SP = 22f, FOCUSED_SP = 32f;
    private static final int INK_FOCUS = 0xFFFFFFFF;
    private static final int INK_REST = 0x52FFFFFF;
    private static final int INK_INDEX_FOCUS = 0x8AFFFFFF;
    private static final int INK_INDEX_REST = 0x33FFFFFF;

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock). */
    private final boolean fillDock;
    /** Dock apps found at the last reload, and the empty slots after them. */
    private int docked, emptySlots;
    /** Never shown (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final boolean animations;
    private final boolean showHomeSettings;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): short fades, no growth. */
    private final boolean reducedMotion;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.fillDock = cfg.fillDock;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.animations = cfg.animations;
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.reducedMotion = cfg.reducedMotion;
        // Rows keep their identity across reload(), so a focused row stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /**
     * Rebuilds the rows. A shown module card stands in for every row of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    void reload(List<Cards.Card> cards) {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> fresh = new ArrayList<>();
        Map<String, Integer> labelCounts = new HashMap<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this row
            String label = card != null ? card.label : Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
            // Same cleaner as the card prompt: a label with a bidi override or line separator must not
            // redraw the row as something else (#117).
            fresh.add(new Entry(card != null ? Entry.CARD : Entry.APP, label, component, card != null ? card.caption : null));
            Integer n = labelCounts.get(label.toLowerCase());
            labelCounts.put(label.toLowerCase(), n == null ? 1 : n + 1);
        }
        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        // Two apps can present the same name (a card, a store build of the same service). A list of
        // bare names gives no way to tell them apart, so the package goes under the second one.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < fresh.size(); i++) {
            Entry e = fresh.get(i);
            String key = e.label.toLowerCase();
            if (seen.add(key)) continue;
            fresh.set(i, new Entry(e.kind, e.label, e.component, e.meta == null ? e.pkg : e.meta));
        }
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null));
        }
        docked = 0;
        while (docked < fresh.size() && favourites.contains(fresh.get(docked).pkg)) docked++;
        emptySlots = DockSlots.empty(docked, dockSize, fresh.size(), fillDock);
        for (int i = 0; i < emptySlots; i++) {
            fresh.add(docked + i, new Entry(Entry.EMPTY, "", new ComponentName(ctx, "empty-dock-slot-" + i), null));
        }
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v, listener, animations, reducedMotion);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        if (e.kind == Entry.EMPTY) {
            // A free dock slot keeps its place in the list but shows nothing and takes no focus.
            h.row.setVisibility(View.INVISIBLE);
            h.row.setFocusable(false);
            return;
        }
        h.row.setVisibility(View.VISIBLE);
        h.row.setFocusable(true);
        h.index.setText(String.format(java.util.Locale.ROOT, "%02d", position + 1));
        h.name.setText(e.label);
        if (e.meta != null && !e.meta.isEmpty()) {
            h.meta.setText(e.meta);
            h.meta.setVisibility(View.VISIBLE);
        } else {
            h.meta.setVisibility(View.GONE);
        }
        boolean focused = h.row.isFocused();
        h.index.setTextColor(focused ? INK_INDEX_FOCUS : INK_INDEX_REST);
        h.name.setTextColor(focused ? INK_FOCUS : INK_REST);
        h.name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, focused ? FOCUSED_SP : RESTING_SP);
        h.row.setOnClickListener(v -> launch(e));
        h.row.setOnLongClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    /** Opens the row's activity by explicit component, with no extras: a card can name nothing else. */
    private void launch(Entry e) {
        if (e.kind == Entry.HOME_SETTINGS) {
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
            if (ctx instanceof Activity) {
                ((Activity) ctx).overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
            }
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

    /** Dock slots that hold an app: the whole dock unless some slots are empty. */
    int dockFilled() {
        return emptySlots > 0 ? docked : dockSize;
    }

    /** A position focus can land on: an empty dock slot gives the last dock app. */
    int focusablePosition(int position) {
        return DockSlots.focusable(position, docked, emptySlots);
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View row;
        final TextView index;
        final TextView name;
        final TextView meta;
        final boolean animations;
        final boolean reducedMotion;

        Holder(View v, TileListener listener, boolean animations, boolean reducedMotion) {
            super(v);
            row = v.findViewById(R.id.row);
            index = v.findViewById(R.id.index);
            name = v.findViewById(R.id.name);
            meta = v.findViewById(R.id.meta);
            this.animations = animations;
            this.reducedMotion = reducedMotion;

            row.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            row.setOnFocusChangeListener((view, focused) -> {
                int pos = getBindingAdapterPosition();
                Holder h = this;
                index.setTextColor(focused ? INK_INDEX_FOCUS : INK_INDEX_REST);
                name.setTextColor(focused ? INK_FOCUS : INK_REST);
                name.animate().cancel();
                name.setScaleX(1f);
                name.setScaleY(1f);
                // Type size, not a scale: 34sp to 46sp reads as the same letterform growing, and it
                // re-lays out at the new size instead of magnifying a raster of the old one.
                name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, focused ? FOCUSED_SP : RESTING_SP);
                name.setTextColor(focused ? INK_FOCUS : INK_REST);
                index.setTextColor(focused ? INK_INDEX_FOCUS : INK_INDEX_REST);
                if (!animations || reducedMotion) {
                    view.animate().cancel();
                    meta.setAlpha(focused ? 1f : 0f);
                } else {
                    meta.animate().alpha(focused ? 1f : 0f).setDuration(160).start();
                }
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
