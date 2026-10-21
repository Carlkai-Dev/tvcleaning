package io.github.epochcoder.homelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.SSLSocketFactory;

/**
 * Media: find a film or show and add it, watch it download, see what is coming.
 *
 * - Search asks Radarr and Sonarr at once, as soon as typing pauses, and shows one grid of posters, best match
 *   first. OK on a poster opens {@link DetailActivity}, where one press adds it and starts the search.
 * - Downloads lists qBittorrent's torrents, named the way Sonarr and Radarr know them, with speed and time left,
 *   and whatever else the two apps are fetching or importing. It refreshes every few seconds while on screen,
 *   updating rows in place so the focus stays put; OK stops or starts a torrent.
 * - Coming up is the next two weeks of Sonarr's and Radarr's calendars, what is missing, free space and any
 *   health warning.
 *
 * Network work runs on background threads; a newer search or tab always wins over an older answer.
 */
public class MediaActivity extends Activity {
    private static final String TAG = "Homelab.Media";
    private static final long TYPING_PAUSE_MS = 400;
    private static final long REFRESH_MS = 3000;
    private static final int UPCOMING_DAYS = 14;
    private static final int FINISHED_SHOWN = 8;
    private static final int VOICE_REQUEST = 1;
    private static final int DETAIL_REQUEST = 2;

    private enum Tab { SEARCH, DOWNLOADS, UPCOMING }

    private final Handler main = new Handler(Looper.getMainLooper());
    /**
     * Searches: Radarr's and Sonarr's lookups side by side, and a third thread collecting both. Each search
     * queues its lookups before its collector, so a collector never waits on work queued behind it.
     */
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    /** Downloads polling on its own thread, so a slow client never holds up a search. */
    private final ExecutorService poll = Executors.newSingleThreadExecutor();

    private Arr radarr;
    private Arr sonarr;
    private Qbit qbit;
    private String configText;
    private Tab tab;
    private int searchGeneration;
    /** Bumped by every refresh, so only the newest one shows its answer and schedules the next. */
    private int downloadsGeneration;
    private String lastSearch = "";
    private boolean resumed;

    private Button[] tabs;
    private View[] pages;
    private EditText query;
    private TextView searchStatus;
    private GridLayout results;
    private TextView downloadsSummary;
    private LinearLayout downloads;
    private TextView upcomingSummary;
    private LinearLayout upcoming;
    /** Download rows by key (torrent hash or app and title), reused across refreshes. */
    private final Map<String, View> downloadRows = new HashMap<>();
    private List<String> downloadOrder = Collections.emptyList();

    private final Runnable typingPaused = this::search;
    private final Runnable refreshDownloads = this::loadDownloads;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_media);
        configText = Config.loadJson(this).toString();
        Config config = Config.load(this);
        SSLSocketFactory tls = Tls.socketFactory(config.caPem);
        if (config.radarr.configured()) radarr = new Arr(Arr.Kind.MOVIE, config.radarr, tls);
        if (config.sonarr.configured()) sonarr = new Arr(Arr.Kind.SERIES, config.sonarr, tls);
        if (config.qbittorrent.configured()) qbit = new Qbit(config.qbittorrent, tls);

        tabs = new Button[]{findViewById(R.id.tab_search), findViewById(R.id.tab_downloads), findViewById(R.id.tab_upcoming)};
        pages = new View[]{findViewById(R.id.page_search), findViewById(R.id.page_downloads), findViewById(R.id.page_upcoming)};
        query = findViewById(R.id.query);
        searchStatus = findViewById(R.id.search_status);
        results = findViewById(R.id.results);
        downloadsSummary = findViewById(R.id.downloads_summary);
        downloads = findViewById(R.id.downloads);
        upcomingSummary = findViewById(R.id.upcoming_summary);
        upcoming = findViewById(R.id.upcoming);

        for (Tab t : Tab.values()) {
            tabs[t.ordinal()].setOnClickListener(v -> select(t));
            // Moving along the tabs is enough to switch, as on most TV apps; OK is not needed.
            tabs[t.ordinal()].setOnFocusChangeListener((v, has) -> {
                if (has && tab != t) select(t);
            });
        }
        findViewById(R.id.settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                main.removeCallbacks(typingPaused);
                main.postDelayed(typingPaused, TYPING_PAUSE_MS);
            }
        });
        query.setOnEditorActionListener((v, action, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_SEARCH && !enter) return false;
            main.removeCallbacks(typingPaused);
            lastSearch = "";   // Search on the keyboard always asks again, even for the same words
            search();
            hideKeyboard();
            // Straight into the results, so the next press is already on the first poster.
            if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
            return true;
        });

        View mic = findViewById(R.id.mic);
        Intent voice = voiceIntent();
        if (voice.resolveActivity(getPackageManager()) != null) {
            mic.setVisibility(View.VISIBLE);
            mic.setOnClickListener(v -> startActivityForResult(voice, VOICE_REQUEST));
        }

        select(Tab.SEARCH);
        if (radarr == null && sonarr == null) {
            setStatus(getString(R.string.not_configured, "Sonarr and Radarr"));
            findViewById(R.id.settings).requestFocus();
        } else {
            setStatus(getString(R.string.search_prompt));
            query.requestFocus();
        }
    }

    private static Intent voiceIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VOICE_REQUEST && result == RESULT_OK && data != null) {
            ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard != null && !heard.isEmpty()) {
                query.setText(heard.get(0));
                query.setSelection(query.length());
                main.removeCallbacks(typingPaused);
                search();
            }
        } else if (request == DETAIL_REQUEST && result == RESULT_OK) {
            // Something was added: ask again so its poster gets its tick.
            lastSearch = "";
            search();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (tab == Tab.DOWNLOADS) loadDownloads();
    }

    @Override
    protected void onPause() {
        resumed = false;
        main.removeCallbacks(refreshDownloads);
        super.onPause();
    }

    /** Back from Settings with something changed: start over with what was saved there. */
    @Override
    protected void onRestart() {
        super.onRestart();
        if (!Config.loadJson(this).toString().equals(configText)) recreate();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        poll.shutdownNow();
        super.onDestroy();
    }

    /**
     * Up from the page goes to the current tab. The tabs switch pages as focus reaches them, and left to the
     * focus search, Up from the right of the page would land on whichever tab is nearest and switch to it.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            ViewGroup root = (ViewGroup) getWindow().getDecorView();
            View next = focused == null ? null : FocusFinder.getInstance().findNextFocus(root, focused, View.FOCUS_UP);
            if (next != null && next != tabs[tab.ordinal()] && isTab(next) && !isTab(focused)) {
                tabs[tab.ordinal()].requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isTab(View v) {
        for (Button b : tabs) if (b == v) return true;
        return false;
    }

    private void select(Tab t) {
        tab = t;
        for (Tab each : Tab.values()) {
            tabs[each.ordinal()].setSelected(each == t);
            pages[each.ordinal()].setVisibility(each == t ? View.VISIBLE : View.GONE);
        }
        main.removeCallbacks(refreshDownloads);
        if (t == Tab.DOWNLOADS) loadDownloads();
        if (t == Tab.UPCOMING) loadUpcoming();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    // Search

    private void search() {
        String term = query.getText().toString().trim();
        if (term.equals(lastSearch) || (radarr == null && sonarr == null)) return;
        lastSearch = term;
        int gen = ++searchGeneration;
        if (term.length() < 2) {
            results.removeAllViews();
            setStatus(getString(R.string.search_prompt));
            return;
        }
        setStatus(getString(R.string.searching));
        Future<List<Arr.Item>> movies = radarr == null ? null : io.submit(() -> radarr.lookup(term));
        Future<List<Arr.Item>> shows = sonarr == null ? null : io.submit(() -> sonarr.lookup(term));
        io.execute(() -> {
            List<String> errors = new ArrayList<>();
            List<Arr.Item> m = collect(movies, radarr, errors);
            List<Arr.Item> s = collect(shows, sonarr, errors);
            List<Arr.Item> merged = Arr.merge(m, s, term);
            runOnUiThread(() -> {
                if (gen != searchGeneration || isFinishing()) return;
                showResults(merged);
                setStatus(!errors.isEmpty() ? String.join("\n", errors)
                        : merged.isEmpty() ? getString(R.string.no_results, term) : "");
            });
        });
    }

    /** The line under the search field; gone when empty, so the posters sit right under the field. */
    private void setStatus(String text) {
        searchStatus.setText(text);
        searchStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<Arr.Item> collect(Future<List<Arr.Item>> f, Arr arr, List<String> errors) {
        if (f == null) return Collections.emptyList();
        try {
            return f.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            errors.add(getString(R.string.error, arr.name(), cause.getMessage()));
            return Collections.emptyList();
        }
    }

    /** One poster card per result, as many columns as fit the screen. Package-private for previews. */
    void showResults(List<Arr.Item> items) {
        results.removeAllViews();
        int width = getResources().getDisplayMetrics().widthPixels - dp(96);
        results.setColumnCount(Math.max(1, width / getResources().getDimensionPixelSize(R.dimen.poster_card_width)));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Arr.Item item : items) {
            View card = inflater.inflate(R.layout.item_poster, results, false);
            TextView placeholder = card.findViewById(R.id.placeholder);
            placeholder.setText(item.title);
            ((TextView) card.findViewById(R.id.kind)).setText(item.kind == Arr.Kind.MOVIE ? R.string.kind_movie : R.string.kind_series);
            card.findViewById(R.id.owned).setVisibility(item.inLibrary() ? View.VISIBLE : View.GONE);
            ((TextView) card.findViewById(R.id.title)).setText(item.title);
            ((TextView) card.findViewById(R.id.year)).setText(item.year > 0 ? String.valueOf(item.year) : "");
            card.findViewById(R.id.frame).setClipToOutline(true);
            Images.load(card.findViewById(R.id.poster), item.poster, 280, 420);
            card.setOnFocusChangeListener((v, has) ->
                    v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(150).start());
            card.setOnClickListener(v -> startActivityForResult(DetailActivity.intent(this, item), DETAIL_REQUEST));
            results.addView(card);
        }
    }

    // Downloads

    /** One line of the Downloads tab, from a torrent, an app's queue, or both. */
    static final class Download {
        final String key;
        final String title;
        final String detail;
        final double progress;
        final boolean finished;
        final Qbit.Torrent torrent;

        Download(String key, String title, String detail, double progress, boolean finished, Qbit.Torrent torrent) {
            this.key = key;
            this.title = title;
            this.detail = detail;
            this.progress = progress;
            this.finished = finished;
            this.torrent = torrent;
        }
    }

    private void loadDownloads() {
        main.removeCallbacks(refreshDownloads);
        int gen = ++downloadsGeneration;
        if (radarr == null && sonarr == null && qbit == null) {
            downloadsSummary.setText(R.string.dl_not_configured);
            return;
        }
        poll.execute(() -> {
            List<Qbit.Torrent> torrents = Collections.emptyList();
            long[] speeds = null;
            List<Arr.QueueEntry> queue = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            if (qbit != null) {
                try {
                    torrents = qbit.torrents();
                    speeds = qbit.speeds();
                } catch (Exception e) {
                    errors.add(getString(R.string.error, "qBittorrent", e.getMessage()));
                }
            }
            for (Arr arr : new Arr[]{radarr, sonarr}) {
                if (arr == null) continue;
                try {
                    queue.addAll(arr.queue());
                } catch (Exception e) {
                    errors.add(getString(R.string.error, arr.name(), e.getMessage()));
                }
            }
            List<Download> rows = downloads(torrents, queue, FINISHED_SHOWN);
            int active = 0;
            for (Download d : rows) if (!d.finished) active++;
            String summary = summary(speeds, active, errors);
            runOnUiThread(() -> {
                if (isFinishing() || tab != Tab.DOWNLOADS || gen != downloadsGeneration) return;
                showDownloads(rows, summary);
                if (resumed) main.postDelayed(refreshDownloads, REFRESH_MS);
            });
        });
    }

    private String summary(long[] speeds, int active, List<String> errors) {
        StringBuilder s = new StringBuilder();
        if (speeds != null) {
            s.append(getString(R.string.dl_speeds, speedOrIdle(speeds[0]), speedOrIdle(speeds[1]))).append("   ·   ");
        }
        s.append(active == 0 ? getString(R.string.dl_none) : getResources().getQuantityString(R.plurals.dl_active, active, active));
        for (String e : errors) s.append('\n').append(e);
        return s.toString();
    }

    private static String speedOrIdle(long b) {
        String s = Format.speed(b);
        return s.isEmpty() ? "0 B/s" : s;
    }

    /**
     * Torrents first, each named as Sonarr or Radarr know it when one of them sent it (matched by hash), then
     * the apps' queue entries no torrent accounts for (another client, Usenet, or no qBittorrent set up).
     * Unfinished before finished; at most {@code finishedShown} finished torrents. Pure, so it is unit-tested.
     */
    static List<Download> downloads(List<Qbit.Torrent> torrents, List<Arr.QueueEntry> queue, int finishedShown) {
        Map<String, Arr.QueueEntry> byHash = new HashMap<>();
        for (Arr.QueueEntry q : queue) if (!q.downloadId.isEmpty()) byHash.put(q.downloadId.toLowerCase(Locale.ROOT), q);
        List<Download> active = new ArrayList<>();
        List<Download> finished = new ArrayList<>();
        for (Qbit.Torrent t : torrents) {
            Arr.QueueEntry q = byHash.remove(t.hash);
            String title = q != null ? q.title : t.name;
            List<String> bits = new ArrayList<>();
            bits.add(t.stateLabel());
            if (!t.complete()) {
                add(bits, Format.speed(t.downloadSpeed));
                String eta = Format.duration(t.eta);
                if (!eta.isEmpty()) bits.add(eta + " left");
            } else {
                add(bits, Format.speed(t.uploadSpeed).isEmpty() ? "" : "↑ " + Format.speed(t.uploadSpeed));
            }
            bits.add(Format.bytes(t.size));
            if (q != null) bits.add(appState(q));
            else if (!t.category.isEmpty()) bits.add(t.category);
            Download d = new Download(t.hash, title, String.join("  ·  ", bits), t.progress, t.complete(), t);
            (t.complete() ? finished : active).add(d);
        }
        for (Arr.QueueEntry q : queue) {
            if (!q.downloadId.isEmpty() && !byHash.containsKey(q.downloadId.toLowerCase(Locale.ROOT))) continue;
            List<String> bits = new ArrayList<>();
            bits.add(q.state);
            String eta = Format.duration(q.timeLeft);
            if (!eta.isEmpty()) bits.add(eta + " left");
            if (q.size > 0) bits.add(Format.bytes(q.size));
            add(bits, q.error);
            bits.add(q.kind == Arr.Kind.MOVIE ? "Radarr" : "Sonarr");
            active.add(new Download(q.kind + ":" + q.title + ":" + q.downloadId, q.title,
                    String.join("  ·  ", bits), q.progress, false, null));
        }
        List<Download> out = new ArrayList<>(active);
        out.addAll(finished.subList(0, Math.min(finishedShown, finished.size())));
        return out;
    }

    /** "Sonarr", or "Sonarr: importPending" when the app is doing more than waiting for the download. */
    private static String appState(Arr.QueueEntry q) {
        String app = q.kind == Arr.Kind.MOVIE ? "Radarr" : "Sonarr";
        return q.state.isEmpty() || q.state.equals("downloading") ? app : app + ": " + q.state;
    }

    private static void add(List<String> bits, String s) {
        if (s != null && !s.isEmpty()) bits.add(s);
    }

    /** Updates rows in place when the same downloads are listed, so focus and scroll stay where they are. */
    void showDownloads(List<Download> rows, String summary) {
        downloadsSummary.setText(summary);
        List<String> order = new ArrayList<>();
        for (Download d : rows) order.add(d.key);
        if (!order.equals(downloadOrder)) {
            String focused = null;
            View f = getCurrentFocus();
            for (Map.Entry<String, View> e : downloadRows.entrySet()) if (e.getValue() == f) focused = e.getKey();
            downloads.removeAllViews();
            Map<String, View> keep = new LinkedHashMap<>();
            boolean finishedHeader = false;
            for (Download d : rows) {
                if (d.finished && !finishedHeader) {
                    downloads.addView(header(getString(R.string.dl_finished)));
                    finishedHeader = true;
                }
                View row = downloadRows.get(d.key);
                if (row == null) row = LayoutInflater.from(this).inflate(R.layout.item_download, downloads, false);
                if (row.getParent() != null) ((ViewGroup) row.getParent()).removeView(row);
                downloads.addView(row);
                keep.put(d.key, row);
            }
            downloadRows.clear();
            downloadRows.putAll(keep);
            downloadOrder = order;
            if (focused != null && downloadRows.containsKey(focused)) downloadRows.get(focused).requestFocus();
        }
        for (Download d : rows) bindDownload(downloadRows.get(d.key), d);
    }

    private void bindDownload(View row, Download d) {
        ((TextView) row.findViewById(R.id.title)).setText(d.title);
        ((TextView) row.findViewById(R.id.percent)).setText(Format.percent(d.progress));
        ((ProgressBar) row.findViewById(R.id.bar)).setProgress((int) Math.round(d.progress * 1000));
        ((TextView) row.findViewById(R.id.detail)).setText(d.detail);
        row.setAlpha(d.torrent != null && d.torrent.stopped() ? 0.6f : 1f);
        row.setOnClickListener(d.torrent == null ? null : v -> toggle(d.torrent));
    }

    private void toggle(Qbit.Torrent t) {
        new AlertDialog.Builder(this)
                .setTitle(t.name)
                .setPositiveButton(t.stopped() ? R.string.dl_start : R.string.dl_stop, (dlg, w) -> poll.execute(() -> {
                    try {
                        qbit.toggle(t);
                    } catch (Exception e) {
                        Log.w(TAG, "toggle: " + e);
                        runOnUiThread(() -> downloadsSummary.setText(getString(R.string.error, "qBittorrent", e.getMessage())));
                    }
                    runOnUiThread(this::loadDownloads);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private TextView header(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(15);
        h.setTextColor(getColor(R.color.text_dim));
        h.setAllCaps(true);
        h.setLetterSpacing(0.08f);
        h.setPadding(dp(18), dp(18), 0, dp(8));
        return h;
    }

    // Coming up

    private void loadUpcoming() {
        if (radarr == null && sonarr == null) {
            upcomingSummary.setText(getString(R.string.not_configured, "Sonarr and Radarr"));
            return;
        }
        upcomingSummary.setText(R.string.loading);
        String start = Format.utcMidnight(System.currentTimeMillis(), 0);
        String end = Format.utcMidnight(System.currentTimeMillis(), UPCOMING_DAYS + 1);
        poll.execute(() -> {
            List<Arr.Upcoming> items = new ArrayList<>();
            List<String> lines = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            long[] disk = null;
            for (Arr arr : new Arr[]{sonarr, radarr}) {
                if (arr == null) continue;
                try {
                    items.addAll(arr.calendar(start, end));
                    int missing = arr.missing();
                    if (missing > 0) {
                        lines.add(getResources().getQuantityString(arr.kind == Arr.Kind.MOVIE
                                ? R.plurals.up_missing_movies : R.plurals.up_missing_episodes, missing, missing));
                    }
                    long[] d = arr.disk();
                    if (d != null && (disk == null || d[1] > disk[1])) disk = d;
                    for (Arr.Health h : arr.health()) problems.add(arr.name() + ": " + h.message);
                } catch (Exception e) {
                    problems.add(getString(R.string.error, arr.name(), e.getMessage()));
                }
            }
            if (disk != null) lines.add(getString(R.string.up_free, Format.bytes(disk[0]), Format.bytes(disk[1])));
            Collections.sort(items, (a, b) -> a.when.compareTo(b.when));
            String summary = lines.isEmpty() ? getString(R.string.up_all_there) : String.join("   ·   ", lines);
            runOnUiThread(() -> {
                if (isFinishing() || tab != Tab.UPCOMING) return;
                showUpcoming(items, summary, problems);
            });
        });
    }

    /** Grouped by day in the TV's timezone: Today, Tomorrow, then the weekday and date. Package-private for previews. */
    void showUpcoming(List<Arr.Upcoming> items, String summary, List<String> problems) {
        upcomingSummary.setText(summary);
        upcoming.removeAllViews();
        for (String p : problems) {
            TextView t = header(p);
            t.setAllCaps(false);
            t.setLetterSpacing(0f);
            t.setTextColor(getColor(R.color.warn));
            upcoming.addView(t);
        }
        if (items.isEmpty()) {
            upcoming.addView(header(getResources().getQuantityString(R.plurals.up_none, UPCOMING_DAYS, UPCOMING_DAYS)));
            return;
        }
        java.text.DateFormat time = DateFormat.getTimeFormat(this);
        String dayPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        String lastDay = "";
        for (Arr.Upcoming u : items) {
            long ms = Format.isoMillis(u.when);
            String day = ms < 0 ? "" : dayLabel(ms, dayPattern);
            if (!day.equals(lastDay)) {
                upcoming.addView(header(day));
                lastDay = day;
            }
            View row = LayoutInflater.from(this).inflate(R.layout.item_upcoming, upcoming, false);
            // A film's release is a day, not a time.
            ((TextView) row.findViewById(R.id.time)).setText(ms < 0 || u.kind == Arr.Kind.MOVIE ? "" : time.format(ms));
            ((TextView) row.findViewById(R.id.title)).setText(u.title);
            ((TextView) row.findViewById(R.id.subtitle)).setText(u.subtitle);
            ((TextView) row.findViewById(R.id.kind)).setText(u.kind == Arr.Kind.MOVIE ? R.string.kind_movie : R.string.kind_series);
            row.findViewById(R.id.owned).setVisibility(u.hasFile ? View.VISIBLE : View.GONE);
            upcoming.addView(row);
        }
    }

    private String dayLabel(long ms, String pattern) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ms);
        int days = (int) ((startOfDay(then) - startOfDay(now)) / 86_400_000L);
        if (days == 0) return getString(R.string.up_today);
        if (days == 1) return getString(R.string.up_tomorrow);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(pattern, Locale.getDefault());
        f.setTimeZone(TimeZone.getDefault());
        return f.format(ms);
    }

    private static long startOfDay(Calendar c) {
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
