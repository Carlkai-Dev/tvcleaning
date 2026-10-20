package com.gathin.tvhome;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.slice.Slice;
import android.app.slice.SliceItem;
import android.app.slice.SliceManager;
import android.app.slice.SliceSpec;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The panel the remote's Settings button opens when {@code settings_key_picture_sound} is on (HomeKeyService),
 * like the stock Google TV one: a top level with Display & Picture and Sound, and each page drawn from the TV's
 * own menu slice (Realtek Google TV; the launcher must be the default Home app to bind them). Left/Right change
 * a picker or slider live, after a short pause, so the picture changes on the app behind; choices listed in
 * {@code picture_sound_ok_only} wait for OK. OK opens a sub-page or runs a row's action. Back goes up a level
 * and closes the panel from the top; Settings closes it at once. If neither slice can be read it opens
 * {@code settings_key_action} instead.
 *
 * Memory: slices are bound only while the panel is open, each on one short-lived thread, with no pinning or
 * callbacks; the activity finishes on every way out and drops everything it read.
 */
@TargetApi(Build.VERSION_CODES.P)
public final class PictureSoundActivity extends Activity {
    private static final String TAG = "PictureSound";
    private static final long SEND_DELAY_MS = 300;
    private static final long REFRESH_DELAY_MS = 400;
    /** The fill-in extras the TV's slices read, as its own Google TV panel sends them (RtkMenuSliceProvider). */
    private static final String EXTRA_PREFERENCE_KEY = "extra_preference_key";
    private static final String EXTRA_SLIDER_VALUE = "android.app.slice.extra.SLIDER_VALUE";

    private ScrollView scroll;
    private LinearLayout rows;
    private SliceManager slices;
    private String[] okOnly;
    /** Top level: the pages that could be read, their titles, and the pages bound at open (used once). */
    private final ArrayList<String> rootUris = new ArrayList<>();
    private final ArrayList<String> rootTitles = new ArrayList<>();
    private final ArrayList<SlicePage.Page> opened = new ArrayList<>();
    /** Open pages, top level excluded, and the row each was opened from. */
    private final ArrayList<String> stack = new ArrayList<>();
    private final ArrayList<Integer> cameFrom = new ArrayList<>();
    private SlicePage.Page page;
    /** Focusable rows top to bottom, with their page row (null at top level) and its index. */
    private final ArrayList<View> focusables = new ArrayList<>();
    private final ArrayList<SlicePage.Row> focusRows = new ArrayList<>();
    private final ArrayList<Integer> focusIndex = new ArrayList<>();
    /** Bumped by every bind and every change, so a stale bind result is dropped. */
    private int generation;
    /** A page is being opened or returned to: keys other than Back and Settings wait for it. */
    private boolean loading;
    private Runnable pendingSend;
    private SlicePage.Row pendingRow;
    private final Runnable refresh = this::refresh;
    /** Remote keycodes besides KEYCODE_SETTINGS that close the panel (settings_key_keycodes). */
    private int[] closeKeycodes = new int[0];
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {   // no slice API: the button still does something
            openFallback();
            return;
        }
        setContentView(R.layout.activity_picture_sound);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        scroll = findViewById(R.id.scroll);
        rows = findViewById(R.id.rows);
        slices = getApplicationContext().getSystemService(SliceManager.class);
        okOnly = getResources().getStringArray(R.array.picture_sound_ok_only);
        // The same Settings buttons that open the panel close it (config.xml or a theme's settings_key_keycodes).
        closeKeycodes = Config.load(this).settingsKeycodes;
        final String[] uris = {getString(R.string.picture_sound_picture_uri), getString(R.string.picture_sound_sound_uri)};
        final int g = ++generation;
        bindAsync(this, slices, uris, (panel, pages) -> panel.showOpened(g, uris, pages));
    }

    /** What a bind hands back on the main thread, to a panel that is still open. */
    private interface Bound {
        void deliver(PictureSoundActivity panel, SlicePage.Page[] pages);
    }

    /**
     * Reads uris on one short-lived thread (the TV's menu service can be slow to answer) and passes the pages to
     * done on the main thread. The thread holds the panel only weakly, so a slow or hung provider cannot keep a
     * closed panel alive, and a panel that has closed in the meantime never sees the result.
     */
    private static void bindAsync(PictureSoundActivity panel, SliceManager sm, String[] uris, Bound done) {
        final WeakReference<PictureSoundActivity> ref = new WeakReference<>(panel);
        new Thread(() -> {
            SlicePage.Page[] pages = new SlicePage.Page[uris.length];
            for (int i = 0; i < uris.length; i++) pages[i] = read(sm, uris[i]);
            MAIN.post(() -> {
                PictureSoundActivity p = ref.get();
                if (p != null && !p.isFinishing() && !p.isDestroyed()) done.deliver(p, pages);
            });
        }, "PictureSoundBind").start();
    }

    private static SlicePage.Page read(SliceManager sm, String uri) {
        if (sm == null || uri == null || uri.trim().isEmpty()) return null;
        try {
            Set<SliceSpec> specs = new HashSet<>();
            specs.add(new SliceSpec("androidx.slice.LIST", 1));
            specs.add(new SliceSpec("androidx.slice.BASIC", 1));
            Slice slice = sm.bindSlice(Uri.parse(uri.trim()), specs);
            return slice == null ? null : SlicePage.parse(new Root(slice));
        } catch (RuntimeException e) {   // no such provider, no permission, provider crash
            Log.w(TAG, "cannot read " + uri + ": " + e.getMessage());
            return null;
        }
    }

    private boolean stale(int g) {
        return g != generation || isFinishing() || isDestroyed();
    }

    private void showOpened(int g, String[] uris, SlicePage.Page[] pages) {
        if (stale(g)) return;
        for (int i = 0; i < uris.length; i++) {
            if (pages[i] == null) continue;
            rootUris.add(uris[i]);
            rootTitles.add(pages[i].title != null ? pages[i].title : Uri.parse(uris[i]).getLastPathSegment());
            opened.add(pages[i]);
        }
        if (rootUris.isEmpty()) {
            openFallback();
            return;
        }
        showTop(0);
    }

    /** The top level: one row per page. */
    private void showTop(int focus) {
        page = null;
        clearRows();
        for (int i = 0; i < rootUris.size(); i++) {
            final int index = i;
            View row = inflateRow(rootTitles.get(i), "›");
            row.setOnClickListener(v -> openRoot(index));
            addFocusable(row, null, i);
        }
        showRows(focus);
    }

    private void openRoot(int index) {
        cameFrom.add(index);
        stack.add(rootUris.get(index));
        SlicePage.Page p = opened.get(index);
        opened.set(index, null);   // only fresh the first time; later visits bind again
        if (p != null) render(p, 0);
        else load(0, PUSH);
    }

    private static final int REFRESH = 0, PUSH = 1, POP = 2;

    /** Binds the page on top of the stack and shows it, focusing near row focus. */
    private void load(int focus, int why) {
        if (stack.isEmpty()) return;
        final String uri = stack.get(stack.size() - 1);
        final int g = ++generation;
        loading = why != REFRESH;
        bindAsync(this, slices, new String[] {uri}, (panel, pages) -> panel.loaded(g, focus, why, pages[0]));
    }

    private void loaded(int g, int focus, int why, SlicePage.Page p) {
        if (stale(g)) return;
        loading = false;
        if (p != null) {
            render(p, focus);
        } else if (why == PUSH) {   // the sub-page cannot be read: stay where we are
            stack.remove(stack.size() - 1);
            cameFrom.remove(cameFrom.size() - 1);
        } else if (why == POP) {
            stack.clear();
            cameFrom.clear();
            showTop(0);
        }
    }

    /** Re-reads the open page after a change (values and enabled rows follow, e.g. Bass with sound style). */
    private void refresh() {
        if (page == null || stack.isEmpty()) return;
        if (pendingSend != null) {
            scheduleRefresh();
            return;
        }
        load(focusedIndex(), REFRESH);
    }

    private void scheduleRefresh() {
        scroll.removeCallbacks(refresh);
        scroll.postDelayed(refresh, REFRESH_DELAY_MS);
    }

    private void render(SlicePage.Page p, int focus) {
        page = p;
        clearRows();
        if (p.title != null) {
            TextView header = new TextView(this);
            header.setText(p.title);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            header.setTextColor(0xFFE3E3E3);
            header.setTypeface(getResources().getFont(R.font.outfit_medium));
            header.setGravity(Gravity.CENTER_HORIZONTAL);
            int dp = Math.round(getResources().getDisplayMetrics().density);
            header.setPadding(0, 10 * dp, 0, 6 * dp);
            header.setFocusable(false);
            rows.addView(header);
        }
        for (int i = 0; i < p.rows.size(); i++) {
            final SlicePage.Row r = p.rows.get(i);
            final View row = inflateRow(r.title, null);
            bind(row, r);
            if (r.kind == SlicePage.CENTERED) ((TextView) row.findViewById(R.id.title)).setGravity(Gravity.CENTER);
            if (!r.enabled) {
                row.setEnabled(false);
                row.setFocusable(false);
                row.setAlpha(0.45f);
                rows.addView(row);
                continue;
            }
            final int index = i;
            row.setOnClickListener(v -> onRowClick(r, index));
            if (r.kind == SlicePage.PICKER) {
                // Leaving a picker that shows an unapplied OK-only choice puts the TV's current one back.
                row.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus && r != pendingRow && r.shown != Math.max(0, r.selected)) {
                        r.shown = Math.max(0, r.selected);
                        bind(row, r);
                    }
                });
            }
            addFocusable(row, r, i);
        }
        showRows(focus);
    }

    private View inflateRow(String title, String value) {
        View row = getLayoutInflater().inflate(R.layout.item_slice_row, rows, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView v = row.findViewById(R.id.value);
        if (value != null) v.setText(value);
        else v.setVisibility(View.GONE);
        return row;
    }

    /** Shows a row's current value. */
    private static void bind(View row, SlicePage.Row r) {
        TextView value = row.findViewById(R.id.value);
        switch (r.kind) {
            case SlicePage.PICKER:
                value.setText("‹  " + r.choices.get(r.shown).label + "  ›");
                value.setVisibility(View.VISIBLE);
                break;
            case SlicePage.SEEKBAR:
                value.setText(String.valueOf(r.value));
                value.setVisibility(View.VISIBLE);
                row.findViewById(R.id.bar).setVisibility(View.VISIBLE);
                float f = SlicePage.fraction(r.value, r.min, r.max);
                ((LinearLayout.LayoutParams) row.findViewById(R.id.fill).getLayoutParams()).weight = f;
                ((LinearLayout.LayoutParams) row.findViewById(R.id.rest).getLayoutParams()).weight = 1f - f;
                row.findViewById(R.id.bar).requestLayout();
                break;
            case SlicePage.SWITCH:
                row.findViewById(R.id.dot).setVisibility(r.checked ? View.VISIBLE : View.GONE);
                break;
            case SlicePage.LINK:
                value.setText("›");
                value.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    private void clearRows() {
        rows.removeAllViews();
        focusables.clear();
        focusRows.clear();
        focusIndex.clear();
    }

    private void addFocusable(View row, SlicePage.Row r, int index) {
        rows.addView(row);
        focusables.add(row);
        focusRows.add(r);
        focusIndex.add(index);
    }

    /** Makes the panel visible and focuses the first focusable row at or after index focus. */
    private void showRows(int focus) {
        scroll.setVisibility(View.VISIBLE);
        if (focusables.isEmpty()) return;
        int pick = focusables.size() - 1;
        for (int i = 0; i < focusables.size(); i++) {
            if (focusIndex.get(i) >= focus) {
                pick = i;
                break;
            }
        }
        focusables.get(pick).requestFocus();
        if (pick == 0) scroll.scrollTo(0, 0);
    }

    private int focusedIndex() {
        int i = focusables.indexOf(getCurrentFocus());
        return i < 0 ? 0 : focusIndex.get(i);
    }

    private void onRowClick(SlicePage.Row r, int index) {
        switch (r.kind) {
            case SlicePage.PICKER:
                if (pendingRow == r) cancelSend();
                if (r.shown != r.selected) {
                    applyPicker(r);
                    scheduleRefresh();
                }
                break;
            case SlicePage.SWITCH:
                flushSend();
                if (send(r.action, fill(r).putExtra(Slice.EXTRA_TOGGLE_STATE, !r.checked))) {
                    r.checked = !r.checked;
                    generation++;
                    scheduleRefresh();
                }
                break;
            case SlicePage.LINK:
                flushSend();
                cameFrom.add(index);
                stack.add(r.target);
                load(0, PUSH);
                break;
            case SlicePage.ACTION:
            case SlicePage.CENTERED:
                // e.g. Reset to default: the TV shows its own confirm screen, which closes the panel.
                flushSend();
                if (send(r.action, fill(r))) scheduleRefresh();
                break;
            default:
                break;
        }
    }

    /** Left/Right on a picker or seekbar: shows the new value now and sends it after a short pause. */
    private void adjust(int focused, boolean forward) {
        SlicePage.Row r = focusRows.get(focused);
        View row = focusables.get(focused);
        if (r == null) return;
        if (r.kind == SlicePage.PICKER) {
            int next = SlicePage.nextIndex(r.shown, r.choices.size(), forward);
            if (next == r.shown) return;
            if (pendingRow != r) flushSend();
            r.shown = next;
            bind(row, r);
            cancelSend();
            generation++;   // drop a refresh already on its way
            if (next == r.selected || SlicePage.okOnly(r.choices.get(next).label, okOnly)) return;
            scheduleSend(r, () -> applyPicker(r));
        } else if (r.kind == SlicePage.SEEKBAR) {
            int next = SlicePage.step(r.value, r.min, r.max, forward);
            if (next == r.value) return;
            if (pendingRow != r) flushSend();
            r.value = next;
            bind(row, r);
            generation++;
            scheduleSend(r, () -> send(r.action, fill(r)
                    .putExtra(EXTRA_SLIDER_VALUE, next)));
        }
    }

    private void applyPicker(SlicePage.Row r) {
        SlicePage.Choice c = r.choices.get(r.shown);
        if (send(c.action, fill(r).putExtra(Slice.EXTRA_TOGGLE_STATE, true))) r.selected = r.shown;
    }

    private void scheduleSend(SlicePage.Row r, Runnable send) {
        cancelSend();
        pendingRow = r;
        pendingSend = () -> {
            pendingSend = null;
            pendingRow = null;
            send.run();
            scheduleRefresh();
        };
        scroll.postDelayed(pendingSend, SEND_DELAY_MS);
    }

    private void cancelSend() {
        if (pendingSend != null) scroll.removeCallbacks(pendingSend);
        pendingSend = null;
        pendingRow = null;
    }

    /** Sends a waiting change now (another row is being changed, or the page or panel is closing). */
    private void flushSend() {
        Runnable r = pendingSend;
        if (r == null) return;
        scroll.removeCallbacks(r);
        r.run();
    }

    private static Intent fill(SlicePage.Row r) {
        Intent fill = new Intent();
        if (r.key != null) fill.putExtra(EXTRA_PREFERENCE_KEY, r.key);
        return fill;
    }

    /** Sends a slice action, as the TV's own menu would. */
    private boolean send(Object action, Intent fill) {
        try {
            ((PendingIntent) action).send(this, 0, fill);
            return true;
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "cannot send: " + e.getMessage());
            return false;
        }
    }

    /** Back: up a level, or closed from the top level. */
    private void back() {
        loading = false;
        flushSend();
        scroll.removeCallbacks(refresh);
        if (stack.isEmpty()) {
            finish();
            return;
        }
        stack.remove(stack.size() - 1);
        int from = cameFrom.remove(cameFrom.size() - 1);
        generation++;
        if (stack.isEmpty()) showTop(from);
        else load(from, POP);
    }

    private void openFallback() {
        try {
            startActivity(HomeKeyService.settingsActionIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "no settings: " + e.getMessage());
        }
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (scroll == null) return super.dispatchKeyEvent(event);
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down && !event.isCanceled()) back();
            return true;
        }
        // Only reached when HomeKeyService is off; with it on, Settings arrives as onNewIntent.
        if (code == KeyEvent.KEYCODE_SETTINGS || isCloseKey(code)) {
            if (!down) finish();
            return true;
        }
        if (loading && (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || (code >= KeyEvent.KEYCODE_DPAD_UP
                && code <= KeyEvent.KEYCODE_DPAD_CENTER))) {   // D-pad and OK
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down && !focusables.isEmpty()) {
                int current = focusables.indexOf(getCurrentFocus());
                int next = current < 0 ? 0
                        : SlicePage.nextIndex(current, focusables.size(), code == KeyEvent.KEYCODE_DPAD_DOWN);
                focusables.get(next).requestFocus();
                if (next == 0) scroll.smoothScrollTo(0, 0);
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down) {
                int current = focusables.indexOf(getCurrentFocus());
                if (current >= 0) adjust(current, code == KeyEvent.KEYCODE_DPAD_RIGHT);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isCloseKey(int code) {
        for (int c : closeKeycodes) if (c == code) return true;
        return false;
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, the TV's own screen, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        if (scroll != null) flushSend();
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        generation++;   // late bind results are dropped
        if (scroll != null) {
            cancelSend();
            scroll.removeCallbacks(refresh);
        }
        page = null;
        rootUris.clear();
        rootTitles.clear();
        opened.clear();
        stack.clear();
        cameFrom.clear();
        focusables.clear();
        focusRows.clear();
        focusIndex.clear();
        super.onDestroy();
    }

    /** The bound slice as the root {@link SlicePage.Node}. */
    private static final class Root implements SlicePage.Node {
        private final Slice slice;

        Root(Slice slice) {
            this.slice = slice;
        }

        @Override public String format() { return SliceItem.FORMAT_SLICE; }
        @Override public String subType() { return null; }
        @Override public List<String> hints() { return slice.getHints(); }
        @Override public CharSequence text() { return null; }
        @Override public int intValue() { return 0; }
        @Override public Object action() { return null; }
        @Override public List<SlicePage.Node> children() { return wrap(slice.getItems()); }
    }

    /** One SliceItem as a {@link SlicePage.Node}. */
    private static final class Item implements SlicePage.Node {
        private final SliceItem item;

        Item(SliceItem item) {
            this.item = item;
        }

        @Override public String format() { return item.getFormat(); }
        @Override public String subType() { return item.getSubType(); }
        @Override public List<String> hints() { return item.getHints(); }

        @Override
        public CharSequence text() {
            return SliceItem.FORMAT_TEXT.equals(item.getFormat()) ? item.getText() : null;
        }

        @Override
        public int intValue() {
            return SliceItem.FORMAT_INT.equals(item.getFormat()) ? item.getInt() : 0;
        }

        @Override
        public Object action() {
            return SliceItem.FORMAT_ACTION.equals(item.getFormat()) ? item.getAction() : null;
        }

        @Override
        public List<SlicePage.Node> children() {
            String f = item.getFormat();
            if (SliceItem.FORMAT_SLICE.equals(f) || SliceItem.FORMAT_ACTION.equals(f)) {
                Slice inner = item.getSlice();
                if (inner != null) return wrap(inner.getItems());
            }
            return Collections.emptyList();
        }
    }

    private static List<SlicePage.Node> wrap(List<SliceItem> items) {
        List<SlicePage.Node> out = new ArrayList<>(items.size());
        for (SliceItem i : items) out.add(new Item(i));
        return out;
    }
}
