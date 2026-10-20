package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SmartUsageManager {
    private static final String TAG = "TvHome.SmartUsage";
    private static final String PREF_NAME = "smart_usage_data";
    private static final String KEY_DATA = "usage_json";

    public static final int CATEGORY_ALL = 0;
    public static final int CATEGORY_MOVIES = 1;
    public static final int CATEGORY_LIVE_SPORTS = 2;
    public static final int CATEGORY_MEDIA = 3;
    public static final int CATEGORY_TOOLS = 4;

    private static SmartUsageManager instance;
    private final Context ctx;
    private final SharedPreferences prefs;
    private final Map<String, List<Long>> launchHistory = new HashMap<>();

    private SmartUsageManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized SmartUsageManager getInstance(Context context) {
        if (instance == null) {
            instance = new SmartUsageManager(context);
        }
        return instance;
    }

    private void load() {
        String json = prefs.getString(KEY_DATA, "{}");
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                JSONArray arr = obj.getJSONArray(pkg);
                List<Long> timestamps = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    timestamps.add(arr.getLong(i));
                }
                launchHistory.put(pkg, timestamps);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load usage data", e);
        }
    }

    private void save() {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, List<Long>> entry : launchHistory.entrySet()) {
                JSONArray arr = new JSONArray();
                List<Long> list = entry.getValue();
                int start = Math.max(0, list.size() - 50);
                for (int i = start; i < list.size(); i++) {
                    arr.put(list.get(i));
                }
                obj.put(entry.getKey(), arr);
            }
            prefs.edit().putString(KEY_DATA, obj.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save usage data", e);
        }
    }

    public synchronized void recordLaunch(String pkg) {
        if (pkg == null) return;
        List<Long> timestamps = launchHistory.computeIfAbsent(pkg, k -> new ArrayList<>());
        timestamps.add(System.currentTimeMillis());
        save();
    }

    public synchronized int getLaunchCount(String pkg) {
        List<Long> list = launchHistory.get(pkg);
        return list == null ? 0 : list.size();
    }

    public synchronized String getUsageSummary(String pkg) {
        List<Long> list = launchHistory.get(pkg);
        if (list == null || list.isEmpty()) return "Rarely used";

        int morning = 0, afternoon = 0, evening = 0, night = 0;
        Calendar cal = Calendar.getInstance();
        for (long t : list) {
            cal.setTimeInMillis(t);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour >= 5 && hour < 12) morning++;
            else if (hour >= 12 && hour < 17) afternoon++;
            else if (hour >= 17 && hour < 22) evening++;
            else night++;
        }

        int max = Math.max(Math.max(morning, afternoon), Math.max(evening, night));
        String preferredTime;
        if (max == morning) preferredTime = "Mornings";
        else if (max == afternoon) preferredTime = "Afternoons";
        else if (max == evening) preferredTime = "Evenings";
        else preferredTime = "Late Night";

        return "Used " + list.size() + " times · Most active " + preferredTime;
    }

    public synchronized List<String> getPredictedApps(int limit) {
        Calendar cal = Calendar.getInstance();
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);
        long now = System.currentTimeMillis();
        long twoDaysAgo = now - (48L * 3600 * 1000);

        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : launchHistory.entrySet()) {
            double score = 0;
            for (long t : entry.getValue()) {
                cal.setTimeInMillis(t);
                int h = cal.get(Calendar.HOUR_OF_DAY);
                int diff = Math.abs(h - currentHour);
                if (diff > 12) diff = 24 - diff;
                if (diff <= 2) score += 3.0;
                else if (diff <= 4) score += 1.5;

                if (t >= twoDaysAgo) score += 2.0;
                else score += 0.5;
            }
            scores.put(entry.getKey(), score);
        }

        List<Map.Entry<String, Double>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            result.add(list.get(i).getKey());
        }
        return result;
    }

    public static int getAppCategory(String pkg, String label) {
        String lower = (pkg + " " + (label == null ? "" : label)).toLowerCase(Locale.US);

        if (lower.contains("netflix") || lower.contains("hotstar") || lower.contains("jiohotstar")
                || lower.contains("sonyliv") || lower.contains("sony liv") || lower.contains("moviebox")
                || lower.contains("primevideo") || lower.contains("prime video") || lower.contains("disney")
                || lower.contains("hulu") || lower.contains("hbo") || lower.contains("zee5")
                || lower.contains("voot") || lower.contains("aha") || lower.contains("apple tv")
                || lower.contains("plex") || lower.contains("kodi") || lower.contains("stremio")
                || lower.contains("cinema") || lower.contains("film") || lower.contains("movie")
                || lower.contains("series")) {
            return CATEGORY_MOVIES;
        }

        if (lower.contains("cricfy") || lower.contains("livetv") || lower.contains("live tv")
                || lower.contains("jiocinema") || lower.contains("sports") || lower.contains("cricket")
                || lower.contains("fancode") || lower.contains("tivimate") || lower.contains("iptv")
                || lower.contains("ott navigator") || lower.contains("yupptv") || lower.contains("football")) {
            return CATEGORY_LIVE_SPORTS;
        }

        if (lower.contains("smarttube") || lower.contains("youtube") || lower.contains("spotify")
                || lower.contains("vlc") || lower.contains("mxplayer") || lower.contains("music")
                || lower.contains("player") || lower.contains("audio") || lower.contains("twitch")
                || lower.contains("soundcloud") || lower.contains("radio")) {
            return CATEGORY_MEDIA;
        }

        return CATEGORY_TOOLS;
    }
}
