package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When an app was last opened from Home, for the cinematic shelf's "Last opened" line and grid sorting.
 * Launch times are cached in memory to ensure UI thread operations (such as list sorting) never block on disk I/O.
 */
final class LastOpened {
    private static final String PREFS = "last_opened";
    private static final Map<String, Long> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean seeded = false;

    private LastOpened() {
    }

    /**
     * Seeds the in-memory cache once from SharedPreferences.
     */
    static void seed(Context ctx) {
        if (ctx == null || seeded) return;
        Context appCtx = ctx.getApplicationContext();
        synchronized (CACHE) {
            if (!seeded) {
                seeded = true;
                try {
                    Map<String, ?> all = prefs(appCtx).getAll();
                    for (Map.Entry<String, ?> e : all.entrySet()) {
                        if (e.getValue() instanceof Long) {
                            CACHE.put(e.getKey(), (Long) e.getValue());
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Notes that Home just opened this app. */
    static void record(Context ctx, String pkg) {
        if (ctx == null || pkg == null) return;
        long now = System.currentTimeMillis();
        CACHE.put(pkg, now);
        prefs(ctx.getApplicationContext()).edit().putLong(pkg, now).apply();
    }

    /** Returns a snapshot copy of the cached launch times for stable sorting. */
    static Map<String, Long> snapshot(Context ctx) {
        if (!seeded && ctx != null) {
            seed(ctx);
        }
        return new HashMap<>(CACHE);
    }

    /** Milliseconds since the epoch, or 0 when Home never opened the app. */
    static long get(Context ctx, String pkg) {
        if (pkg == null) return 0L;
        if (!seeded && ctx != null) {
            seed(ctx);
        }
        Long val = CACHE.get(pkg);
        return val != null ? val : 0L;
    }

    /**
     * "5 minutes ago" within the hour, then the day and the time: "today, 11:40 am", "yesterday, 9:40 pm",
     * "3 days ago, 9:40 pm", "Oct 12, 9:40 pm". The parts are Android's own wording in the TV's language.
     */
    static String describe(Context ctx, long when) {
        long now = System.currentTimeMillis();
        // Under a minute Android says "0 minutes ago"; one minute reads better.
        when = Math.min(when, now - DateUtils.MINUTE_IN_MILLIS);
        if (now - when < DateUtils.HOUR_IN_MILLIS) {
            return DateUtils.getRelativeTimeSpanString(when, now, DateUtils.MINUTE_IN_MILLIS).toString();
        }
        String day = DateUtils.getRelativeTimeSpanString(when, now, DateUtils.DAY_IN_MILLIS).toString();
        // It follows "Last opened" mid-sentence: "yesterday", not "Yesterday". Month names keep their capital.
        if (Locale.getDefault().getLanguage().equals("en") && (day.startsWith("Yesterday") || day.startsWith("Today"))) {
            day = Character.toLowerCase(day.charAt(0)) + day.substring(1);
        }
        return day + ", " + DateUtils.formatDateTime(ctx, when, DateUtils.FORMAT_SHOW_TIME);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
