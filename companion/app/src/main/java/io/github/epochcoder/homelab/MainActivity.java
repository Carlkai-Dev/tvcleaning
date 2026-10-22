package io.github.epochcoder.homelab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import java.util.EnumMap;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;

/**
 * The whole companion in one launcher tile: tabs across the top for the Homepage dashboard, Search,
 * Downloads, Coming up and Settings, one {@link Page} visible below them at a time.
 *
 * <p>Moving along the tabs switches pages, as on most TV apps; Down goes into the page, Up from the
 * top of a page and Back from anywhere in it return to its tab, and Back on the tabs leaves. The
 * clients for the servers live here, built from config.json, and are rebuilt whenever Settings
 * saves, so every page sees a change at once.
 *
 * <p>The screensaver's gear in the TV's settings opens this activity through the SettingsActivity
 * alias, which starts on the Settings tab.
 */
public class MainActivity extends Activity {
    private static final String STATE_TAB = "tab";

    enum Tab {
        DASHBOARD,
        SEARCH,
        DOWNLOADS,
        UPCOMING,
        SETTINGS
    }

    Config config;
    SSLSocketFactory tls;
    Arr radarr;
    Arr sonarr;
    Qbit qbit;

    /** config.json as last read, to notice a push-config.sh while the app was in the background. */
    private String configText;

    private FrameLayout container;
    private final Map<Tab, Button> tabs = new EnumMap<>(Tab.class);
    private final Map<Tab, Page> pages = new EnumMap<>(Tab.class);
    private SearchPage search;
    private Tab tab;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        container = findViewById(R.id.pages);
        loadConfig();

        search = new SearchPage(this);
        pages.put(Tab.DASHBOARD, new DashboardPage(this));
        pages.put(Tab.SEARCH, search);
        pages.put(Tab.DOWNLOADS, new DownloadsPage(this));
        pages.put(Tab.UPCOMING, new UpcomingPage(this));
        pages.put(Tab.SETTINGS, new SettingsPage(this));
        tabs.put(Tab.DASHBOARD, (Button) findViewById(R.id.tab_dashboard));
        tabs.put(Tab.SEARCH, (Button) findViewById(R.id.tab_search));
        tabs.put(Tab.DOWNLOADS, (Button) findViewById(R.id.tab_downloads));
        tabs.put(Tab.UPCOMING, (Button) findViewById(R.id.tab_upcoming));
        tabs.put(Tab.SETTINGS, (Button) findViewById(R.id.tab_settings));
        for (Tab t : Tab.values()) {
            Page p = pages.get(t);
            p.view.setVisibility(View.GONE);
            container.addView(p.view);
            tabs.get(t)
                    .setOnClickListener(
                            v -> {
                                select(t);
                                View target = p.focusTarget();
                                if (target != null) {
                                    target.requestFocus();
                                }
                            });
            tabs.get(t)
                    .setOnFocusChangeListener(
                            (v, has) -> {
                                if (has && tab != t) {
                                    select(t);
                                }
                            });
        }

        Tab first = startTab();
        if (state != null && state.getString(STATE_TAB) != null) {
            first = Tab.valueOf(state.getString(STATE_TAB));
        }
        select(first);
        tabs.get(first).requestFocus();
    }

    /**
     * Where a fresh start lands: Settings from the screensaver's gear or when nothing is set up,
     * else the first useful tab.
     */
    private Tab startTab() {
        Intent i = getIntent();
        boolean fromDreamSettings =
                i.getComponent() != null
                        && i.getComponent().getClassName().endsWith(".SettingsActivity");
        if (fromDreamSettings) {
            return Tab.SETTINGS;
        }
        if (config.homepage.configured()) {
            return Tab.DASHBOARD;
        }
        if (radarr != null || sonarr != null) {
            return Tab.SEARCH;
        }
        if (qbit != null) {
            return Tab.DOWNLOADS;
        }
        return Tab.SETTINGS;
    }

    private void loadConfig() {
        configText = Config.loadJson(this).toString();
        config = Config.load(this);
        tls = Tls.socketFactory(config.caPem);
        radarr = config.radarr.configured() ? new Arr(Arr.Kind.MOVIE, config.radarr, tls) : null;
        sonarr = config.sonarr.configured() ? new Arr(Arr.Kind.SERIES, config.sonarr, tls) : null;
        qbit = config.qbittorrent.configured() ? new Qbit(config.qbittorrent, tls) : null;
    }

    /** Settings wrote config.json: rebuild the clients and tell the other pages. */
    void configSaved(Page from) {
        loadConfig();
        for (Page p : pages.values()) {
            if (p != from) {
                p.onConfigChanged();
            }
        }
    }

    FrameLayout pageContainer() {
        return container;
    }

    Page current() {
        return tab == null ? null : pages.get(tab);
    }

    void select(Tab t) {
        if (t == tab) {
            return;
        }
        Page old = current();
        tab = t;
        // Hiding a page that holds the focus hands it to the first focusable view, the Dashboard
        // tab, whose focus listener would switch pages in the middle of this switch. Give it to the
        // new tab first.
        View focused = getCurrentFocus();
        if (old != null && focused != null && !isTab(focused)) {
            tabs.get(t).requestFocus();
        }
        if (old != null) {
            old.onHide();
            old.view.setVisibility(View.GONE);
        }
        for (Tab each : Tab.values()) {
            tabs.get(each).setSelected(each == t);
        }
        Page p = pages.get(t);
        p.view.setAlpha(0f);
        p.view.setVisibility(View.VISIBLE);
        p.view.animate().alpha(1f).setDuration(180).start();
        p.onShow();
    }

    /** From an empty state's button: straight into Settings, on its first section. */
    void openSettings() {
        select(Tab.SETTINGS);
        View target = current().focusTarget();
        (target != null ? target : tabs.get(Tab.SETTINGS)).requestFocus();
    }

    /** Back to the current page's tab, as Back and Up from the top of a page do. */
    void focusTab() {
        tabs.get(tab).requestFocus();
    }

    private boolean isTab(View v) {
        return tabs.containsValue(v);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Page page = current();
        if (page != null && page.onKey(event)) {
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        View focused = getCurrentFocus();
        boolean onTab = focused != null && isTab(focused);
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                // Left to the focus search, Up from the right of a page would land on whichever tab
                // is nearest and switch to it; it should come back to the page's own tab.
                if (!onTab && focused != null) {
                    View next =
                            FocusFinder.getInstance()
                                    .findNextFocus(
                                            (ViewGroup) getWindow().getDecorView(),
                                            focused,
                                            View.FOCUS_UP);
                    if (next == null || isTab(next)) {
                        focusTab();
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (onTab) {
                    View target = page.focusTarget();
                    if (target != null && target.isShown()) {
                        target.requestFocus();
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (!onTab) {
                    focusTab();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        search.onActivityResult(request, result, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (tab != null) {
            out.putString(STATE_TAB, tab.name());
        }
    }

    /**
     * Back from the background with config.json pushed meanwhile: every page starts over with it.
     */
    @Override
    protected void onRestart() {
        super.onRestart();
        if (!Config.loadJson(this).toString().equals(configText)) {
            configSaved(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        current().onResume();
    }

    @Override
    protected void onPause() {
        resumed = false;
        current().onPause();
        super.onPause();
    }

    boolean resumed() {
        return resumed;
    }

    @Override
    protected void onDestroy() {
        for (Page p : pages.values()) {
            p.onDestroy();
        }
        super.onDestroy();
    }
}
