package io.github.epochcoder.homelab;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The next two weeks of Sonarr's and Radarr's calendars, grouped by day, with what is missing, free
 * space and any health warning in the headline. Loaded each time the tab is shown.
 */
final class UpcomingPage extends Page {
    private static final int UPCOMING_DAYS = 14;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final TextView summary;
    private final LinearLayout list;
    private int generation;

    UpcomingPage(MainActivity host) {
        super(host, R.layout.page_upcoming);
        summary = view.findViewById(R.id.summary);
        list = view.findViewById(R.id.list);
    }

    @Override
    void onShow() {
        load();
    }

    @Override
    void onHide() {
        generation++;
    }

    @Override
    void onDestroy() {
        io.shutdownNow();
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

    private void load() {
        Arr radarr = host.radarr;
        Arr sonarr = host.sonarr;
        if (radarr == null && sonarr == null) {
            empty(
                    R.drawable.ic_tab_upcoming,
                    R.string.empty_upcoming_title,
                    getString(R.string.empty_upcoming));
            return;
        }
        empty(0, 0, null);
        int gen = ++generation;
        if (list.getChildCount() == 0) {
            summary.setText(R.string.loading);
        }
        String start = Format.utcMidnight(System.currentTimeMillis(), 0);
        String end = Format.utcMidnight(System.currentTimeMillis(), UPCOMING_DAYS + 1);
        io.execute(
                () -> {
                    List<Arr.Upcoming> items = new ArrayList<>();
                    List<String> lines = new ArrayList<>();
                    List<String> problems = new ArrayList<>();
                    long[] disk = null;
                    for (Arr arr : new Arr[] {sonarr, radarr}) {
                        if (arr == null) {
                            continue;
                        }
                        try {
                            items.addAll(arr.calendar(start, end));
                            int missing = arr.missing();
                            if (missing > 0) {
                                lines.add(
                                        host.getResources()
                                                .getQuantityString(
                                                        arr.kind == Arr.Kind.MOVIE
                                                                ? R.plurals.up_missing_movies
                                                                : R.plurals.up_missing_episodes,
                                                        missing,
                                                        missing));
                            }
                            long[] d = arr.disk();
                            if (d != null && (disk == null || d[1] > disk[1])) {
                                disk = d;
                            }
                            for (Arr.Health h : arr.health()) {
                                problems.add(arr.name() + ": " + h.message);
                            }
                        } catch (Exception e) {
                            problems.add(getString(R.string.error, arr.name(), e.getMessage()));
                        }
                    }
                    if (disk != null) {
                        lines.add(
                                getString(
                                        R.string.up_free,
                                        Format.bytes(disk[0]),
                                        Format.bytes(disk[1])));
                    }
                    Collections.sort(items, (a, b) -> a.when.compareTo(b.when));
                    String text =
                            lines.isEmpty()
                                    ? getString(R.string.up_all_there)
                                    : String.join("   ·   ", lines);
                    host.runOnUiThread(
                            () -> {
                                if (host.isFinishing() || gen != generation) {
                                    return;
                                }
                                show(items, text, problems);
                            });
                });
    }

    /** Grouped by day in the TV's timezone: Today, Tomorrow, then the weekday and date. */
    private void show(List<Arr.Upcoming> items, String text, List<String> problems) {
        summary.setText(text);
        list.removeAllViews();
        for (String p : problems) {
            TextView t = header(host, p);
            t.setAllCaps(false);
            t.setLetterSpacing(0f);
            t.setTextColor(host.getColor(R.color.warn));
            list.addView(t);
        }
        if (items.isEmpty()) {
            list.addView(
                    header(
                            host,
                            host.getResources()
                                    .getQuantityString(
                                            R.plurals.up_none, UPCOMING_DAYS, UPCOMING_DAYS)));
            return;
        }
        java.text.DateFormat time = DateFormat.getTimeFormat(host);
        String dayPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        String lastDay = "";
        for (Arr.Upcoming u : items) {
            long ms = Format.isoMillis(u.when);
            String day = ms < 0 ? "" : dayLabel(ms, dayPattern);
            if (!day.equals(lastDay)) {
                list.addView(header(host, day));
                lastDay = day;
            }
            View row = LayoutInflater.from(host).inflate(R.layout.item_upcoming, list, false);
            // A film's release is a day, not a time.
            ((TextView) row.findViewById(R.id.time))
                    .setText(ms < 0 || u.kind == Arr.Kind.MOVIE ? "" : time.format(ms));
            ((TextView) row.findViewById(R.id.title)).setText(u.title);
            ((TextView) row.findViewById(R.id.subtitle)).setText(u.subtitle);
            ((TextView) row.findViewById(R.id.kind))
                    .setText(u.kind == Arr.Kind.MOVIE ? R.string.kind_movie : R.string.kind_series);
            row.findViewById(R.id.owned).setVisibility(u.hasFile ? View.VISIBLE : View.GONE);
            list.addView(row);
        }
    }

    /** A small accent heading over a group of rows: a day here, Finished on Downloads. */
    static TextView header(Context ctx, String text) {
        TextView h = new TextView(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;
        h.setText(text);
        h.setTextSize(14);
        h.setTextColor(ctx.getColor(R.color.accent));
        h.setAllCaps(true);
        h.setLetterSpacing(0.1f);
        h.setTypeface(
                android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.NORMAL));
        h.setPadding(
                Math.round(6 * density), Math.round(18 * density), 0, Math.round(10 * density));
        return h;
    }

    private String dayLabel(long ms, String pattern) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ms);
        int days = (int) ((startOfDay(then) - startOfDay(now)) / 86_400_000L);
        if (days == 0) {
            return getString(R.string.up_today);
        }
        if (days == 1) {
            return getString(R.string.up_tomorrow);
        }
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
}
