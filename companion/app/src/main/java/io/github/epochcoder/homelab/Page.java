package io.github.epochcoder.homelab;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * One tab of {@link MainActivity}: a view in the page area and the work behind it. The host shows
 * one page at a time and passes on its lifecycle; a page starts its network work in {@link #onShow}
 * and stops it in {@link #onHide} and {@link #onPause}, so only the visible page talks to the
 * servers.
 */
abstract class Page {
    final MainActivity host;
    final View view;

    Page(MainActivity host, int layout) {
        this.host = host;
        view = LayoutInflater.from(host).inflate(layout, host.pageContainer(), false);
    }

    /** The page has become the visible tab. */
    void onShow() {}

    /** Another tab was chosen. */
    void onHide() {}

    /** The app came back to the screen with this page visible. */
    void onResume() {}

    /**
     * The app left the screen (another app, the screensaver, or a detail screen of ours) with this
     * page visible.
     */
    void onPause() {}

    void onDestroy() {}

    /**
     * Settings saved a change; {@link MainActivity}'s clients have already been rebuilt from it.
     */
    void onConfigChanged() {}

    /** Where Down from the tab bar lands; null leaves it to the focus search. */
    View focusTarget() {
        return null;
    }

    /** First look at every key while this page is visible; true consumes it. */
    boolean onKey(KeyEvent event) {
        return false;
    }

    boolean visible() {
        return host.current() == this;
    }

    /**
     * The shared empty state (empty_state.xml) over the page's content: an icon, a headline, a
     * sentence and a button to Settings. Pass 0 as the title to hide it and show the content again.
     */
    void empty(int icon, int title, String message) {
        View empty = view.findViewById(R.id.empty);
        View content = view.findViewById(R.id.content);
        boolean on = title != 0;
        empty.setVisibility(on ? View.VISIBLE : View.GONE);
        if (content != null) {
            content.setVisibility(on ? View.GONE : View.VISIBLE);
        }
        if (!on) {
            return;
        }
        ((ImageView) empty.findViewById(R.id.empty_icon)).setImageResource(icon);
        ((TextView) empty.findViewById(R.id.empty_title)).setText(title);
        ((TextView) empty.findViewById(R.id.empty_message)).setText(message);
        TextView primary = empty.findViewById(R.id.empty_primary);
        primary.setText(R.string.open_settings);
        primary.setOnClickListener(v -> host.openSettings());
    }

    boolean showingEmpty() {
        return view.findViewById(R.id.empty).getVisibility() == View.VISIBLE;
    }

    View emptyButton() {
        return view.findViewById(R.id.empty_primary);
    }

    String getString(int id, Object... args) {
        return host.getString(id, args);
    }

    int dp(int v) {
        return Math.round(v * host.getResources().getDisplayMetrics().density);
    }
}
