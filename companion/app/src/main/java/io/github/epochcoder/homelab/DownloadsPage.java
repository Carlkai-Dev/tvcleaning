package io.github.epochcoder.homelab;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * qBittorrent's torrents, named the way Sonarr and Radarr know them, with speed and time left, and
 * whatever else the two apps are fetching or importing. It refreshes every few seconds while on
 * screen, updating rows in place so the focus stays put; OK stops or starts a torrent.
 */
final class DownloadsPage extends Page {
    private static final String TAG = "Homelab.Downloads";
    private static final long REFRESH_MS = 3000;
    private static final int FINISHED_SHOWN = 8;

    private final Handler main = new Handler(Looper.getMainLooper());

    /** Polling on its own thread, so a slow client never holds up the other tabs. */
    private final ExecutorService poll = Executors.newSingleThreadExecutor();

    private final TextView summary;
    private final LinearLayout list;

    /** Bumped by every refresh, so only the newest one shows its answer and schedules the next. */
    private int generation;

    /** Rows by key (torrent hash or app and title), reused across refreshes. */
    private final Map<String, View> rows = new HashMap<>();

    private List<String> order = Collections.emptyList();

    private final Runnable refresh = this::load;

    DownloadsPage(MainActivity host) {
        super(host, R.layout.page_downloads);
        summary = view.findViewById(R.id.summary);
        list = view.findViewById(R.id.list);
    }

    @Override
    void onShow() {
        load();
    }

    @Override
    void onHide() {
        stop();
    }

    @Override
    void onResume() {
        load();
    }

    @Override
    void onPause() {
        stop();
    }

    @Override
    void onConfigChanged() {
        list.removeAllViews();
        rows.clear();
        order = Collections.emptyList();
    }

    @Override
    void onDestroy() {
        stop();
        poll.shutdownNow();
    }

    @Override
    View focusTarget() {
        if (showingEmpty()) {
            return emptyButton();
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            if (list.getChildAt(i).isFocusable()) {
                return list.getChildAt(i);
            }
        }
        return null;
    }

    private void stop() {
        generation++;
        main.removeCallbacks(refresh);
    }

    /** One line of the list, from a torrent, an app's queue, or both. */
    static final class Download {
        final String key;
        final String title;
        final String detail;
        final double progress;
        final boolean finished;
        final Qbit.Torrent torrent;

        Download(
                String key,
                String title,
                String detail,
                double progress,
                boolean finished,
                Qbit.Torrent torrent) {
            this.key = key;
            this.title = title;
            this.detail = detail;
            this.progress = progress;
            this.finished = finished;
            this.torrent = torrent;
        }
    }

    private void load() {
        main.removeCallbacks(refresh);
        int gen = ++generation;
        Arr radarr = host.radarr;
        Arr sonarr = host.sonarr;
        Qbit qbit = host.qbit;
        if (radarr == null && sonarr == null && qbit == null) {
            empty(
                    R.drawable.ic_tab_downloads,
                    R.string.empty_downloads_title,
                    getString(R.string.dl_not_configured));
            return;
        }
        empty(0, 0, null);
        poll.execute(
                () -> {
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
                    for (Arr arr : new Arr[] {radarr, sonarr}) {
                        if (arr == null) {
                            continue;
                        }
                        try {
                            queue.addAll(arr.queue());
                        } catch (Exception e) {
                            errors.add(getString(R.string.error, arr.name(), e.getMessage()));
                        }
                    }
                    List<Download> found = downloads(torrents, queue, FINISHED_SHOWN);
                    int active = 0;
                    for (Download d : found) {
                        if (!d.finished) {
                            active++;
                        }
                    }
                    String text = summary(speeds, active, errors);
                    host.runOnUiThread(
                            () -> {
                                if (host.isFinishing() || !visible() || gen != generation) {
                                    return;
                                }
                                show(found, text);
                                if (host.resumed()) {
                                    main.postDelayed(refresh, REFRESH_MS);
                                }
                            });
                });
    }

    private String summary(long[] speeds, int active, List<String> errors) {
        StringBuilder s = new StringBuilder();
        s.append(
                active == 0
                        ? getString(R.string.dl_none)
                        : host.getResources()
                                .getQuantityString(R.plurals.dl_active, active, active));
        if (speeds != null) {
            s.append("   ·   ")
                    .append(
                            getString(
                                    R.string.dl_speeds,
                                    speedOrIdle(speeds[0]),
                                    speedOrIdle(speeds[1])));
        }
        for (String e : errors) {
            s.append('\n').append(e);
        }
        return s.toString();
    }

    private static String speedOrIdle(long b) {
        String s = Format.speed(b);
        return s.isEmpty() ? "0 B/s" : s;
    }

    /**
     * Torrents first, each named as Sonarr or Radarr know it when one of them sent it (matched by
     * hash), then the apps' queue entries no torrent accounts for (another client, Usenet, or no
     * qBittorrent set up). Unfinished before finished; at most {@code finishedShown} finished
     * torrents. Pure, so it is unit-tested.
     */
    static List<Download> downloads(
            List<Qbit.Torrent> torrents, List<Arr.QueueEntry> queue, int finishedShown) {
        Map<String, Arr.QueueEntry> byHash = new HashMap<>();
        for (Arr.QueueEntry q : queue) {
            if (!q.downloadId.isEmpty()) {
                byHash.put(q.downloadId.toLowerCase(Locale.ROOT), q);
            }
        }
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
                if (!eta.isEmpty()) {
                    bits.add(eta + " left");
                }
            } else {
                add(
                        bits,
                        Format.speed(t.uploadSpeed).isEmpty()
                                ? ""
                                : "↑ " + Format.speed(t.uploadSpeed));
            }
            bits.add(Format.bytes(t.size));
            if (q != null) {
                bits.add(appState(q));
            } else if (!t.category.isEmpty()) {
                bits.add(t.category);
            }
            Download d =
                    new Download(
                            t.hash, title, String.join("  ·  ", bits), t.progress, t.complete(), t);
            (t.complete() ? finished : active).add(d);
        }
        for (Arr.QueueEntry q : queue) {
            if (!q.downloadId.isEmpty()
                    && !byHash.containsKey(q.downloadId.toLowerCase(Locale.ROOT))) {
                continue;
            }
            List<String> bits = new ArrayList<>();
            bits.add(q.state);
            String eta = Format.duration(q.timeLeft);
            if (!eta.isEmpty()) {
                bits.add(eta + " left");
            }
            if (q.size > 0) {
                bits.add(Format.bytes(q.size));
            }
            add(bits, q.error);
            bits.add(q.kind == Arr.Kind.MOVIE ? "Radarr" : "Sonarr");
            active.add(
                    new Download(
                            q.kind + ":" + q.title + ":" + q.downloadId,
                            q.title,
                            String.join("  ·  ", bits),
                            q.progress,
                            false,
                            null));
        }
        List<Download> out = new ArrayList<>(active);
        out.addAll(finished.subList(0, Math.min(finishedShown, finished.size())));
        return out;
    }

    /**
     * "Sonarr", or "Sonarr: importPending" when the app is doing more than waiting for the
     * download.
     */
    private static String appState(Arr.QueueEntry q) {
        String app = q.kind == Arr.Kind.MOVIE ? "Radarr" : "Sonarr";
        return q.state.isEmpty() || q.state.equals("downloading") ? app : app + ": " + q.state;
    }

    private static void add(List<String> bits, String s) {
        if (s != null && !s.isEmpty()) {
            bits.add(s);
        }
    }

    /**
     * Updates rows in place when the same downloads are listed, so focus and scroll stay where they
     * are.
     */
    private void show(List<Download> found, String text) {
        summary.setText(text);
        List<String> keys = new ArrayList<>();
        for (Download d : found) {
            keys.add(d.key);
        }
        if (!keys.equals(order)) {
            String focused = null;
            View f = host.getCurrentFocus();
            for (Map.Entry<String, View> e : rows.entrySet()) {
                if (e.getValue() == f) {
                    focused = e.getKey();
                }
            }
            list.removeAllViews();
            Map<String, View> keep = new LinkedHashMap<>();
            boolean finishedHeader = false;
            for (Download d : found) {
                if (d.finished && !finishedHeader) {
                    list.addView(UpcomingPage.header(host, getString(R.string.dl_finished)));
                    finishedHeader = true;
                }
                View row = rows.get(d.key);
                if (row == null) {
                    row = LayoutInflater.from(host).inflate(R.layout.item_download, list, false);
                }
                if (row.getParent() != null) {
                    ((ViewGroup) row.getParent()).removeView(row);
                }
                list.addView(row);
                keep.put(d.key, row);
            }
            rows.clear();
            rows.putAll(keep);
            order = keys;
            if (focused != null && rows.containsKey(focused)) {
                rows.get(focused).requestFocus();
            }
        }
        for (Download d : found) {
            bind(rows.get(d.key), d);
        }
    }

    private void bind(View row, Download d) {
        ((TextView) row.findViewById(R.id.title)).setText(d.title);
        ((TextView) row.findViewById(R.id.percent)).setText(Format.percent(d.progress));
        ((ProgressBar) row.findViewById(R.id.bar)).setProgress((int) Math.round(d.progress * 1000));
        ((TextView) row.findViewById(R.id.detail)).setText(d.detail);
        row.setAlpha(d.torrent != null && d.torrent.stopped() ? 0.6f : 1f);
        row.setOnClickListener(d.torrent == null ? null : v -> toggle(d.torrent));
    }

    private void toggle(Qbit.Torrent t) {
        Qbit qbit = host.qbit;
        if (qbit == null) {
            return;
        }
        new AlertDialog.Builder(host)
                .setTitle(t.name)
                .setPositiveButton(
                        t.stopped() ? R.string.dl_start : R.string.dl_stop,
                        (dlg, w) ->
                                poll.execute(
                                        () -> {
                                            try {
                                                qbit.toggle(t);
                                            } catch (Exception e) {
                                                Log.w(TAG, "toggle: " + e);
                                                host.runOnUiThread(
                                                        () ->
                                                                summary.setText(
                                                                        getString(
                                                                                R.string.error,
                                                                                "qBittorrent",
                                                                                e.getMessage())));
                                            }
                                            host.runOnUiThread(
                                                    () -> {
                                                        if (visible()) {
                                                            load();
                                                        }
                                                    });
                                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
