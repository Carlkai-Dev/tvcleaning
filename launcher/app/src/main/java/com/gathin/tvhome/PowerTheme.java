package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.provider.Settings;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Power theme: a theme with modes (Entertain, Movies, Games, Kids, News, Markets...). Each mode is a set of theme
 * keys laid over the theme's own, chosen on the TV from WelcomeActivity, which greets and asks for the mood when the
 * TV comes on, or with the remote's colour keys. Off unless the theme has "power_theme": true and a "modes" list;
 * with it off nothing here runs. The chosen mode lives in the "power" preferences, never in the theme folder.
 */
final class PowerTheme {
    static final String PREFS = "power";
    static final String MODE_KEY = "mode";
    private static final String BOOT_KEY = "welcome_boot";
    private static final String DAY_KEY = "welcome_day";

    /** power_welcome: show the welcome on every power-on, on the first of each day, or never. */
    static final String ASK_BOOT = "boot";
    static final String ASK_DAILY = "daily";
    static final String ASK_NEVER = "never";

    static final String RED = "red";
    static final String GREEN = "green";
    static final String YELLOW = "yellow";
    static final String BLUE = "blue";

    /** Keys a mode may not set: they belong to the theme itself. */
    private static final String[] THEME_ONLY = {"power_theme", "power_welcome", "modes"};

    static final PowerTheme NONE = new PowerTheme(Collections.emptyList(), ASK_NEVER);

    static final class Mode {
        final String id;
        final String name;
        /** The feeling the welcome offers for it ("Relaxed"), and the line under it ("Put something good on"). */
        final String mood;
        final String line;
        final int color;
        /** The remote colour key that switches to it: red, green, yellow, blue, or empty. */
        final String key;
        final JSONObject theme;

        Mode(String id, String name, String mood, String line, int color, String key, JSONObject theme) {
            this.id = id;
            this.name = name;
            this.mood = mood.isEmpty() ? name : mood;
            this.line = line;
            this.color = color;
            this.key = key;
            this.theme = theme;
        }
    }

    final List<Mode> modes;
    final String ask;

    PowerTheme(List<Mode> modes, String ask) {
        this.modes = Collections.unmodifiableList(modes);
        this.ask = ask;
    }

    boolean on() {
        return !modes.isEmpty();
    }

    /** The modes in the theme, or NONE when power_theme is off, the list is missing, or no entry is usable. */
    static PowerTheme from(JSONObject json) {
        if (json == null || !json.optBoolean("power_theme", false)) return NONE;
        JSONArray list = json.optJSONArray("modes");
        if (list == null) return NONE;
        List<Mode> modes = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", "").trim();
            String name = o.optString("name", "").trim();
            if (id.isEmpty() || name.isEmpty() || indexOf(modes, id) >= 0) continue;
            int color = 0xFF2BC3C9;
            try {
                color = Color.parseColor(o.optString("color", "#2BC3C9").trim());
            } catch (IllegalArgumentException ignored) {
            }
            JSONObject theme = o.optJSONObject("theme");
            modes.add(new Mode(id, name, o.optString("mood", "").trim(), o.optString("line", "").trim(),
                    color, colourName(o.optString("key", "")), theme != null ? theme : new JSONObject()));
        }
        return modes.isEmpty() ? NONE : new PowerTheme(modes, askValue(json.optString("power_welcome", ASK_BOOT)));
    }

    /** The theme with the chosen mode's keys laid over it; the theme as it is when the mode is unknown. */
    JSONObject overlay(JSONObject base, String modeId) {
        int i = indexOf(modes, modeId);
        if (i < 0) return base;
        try {
            JSONObject merged = new JSONObject(base.toString());
            JSONObject theme = modes.get(i).theme;
            for (Iterator<String> keys = theme.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!themeOnly(key)) merged.put(key, theme.get(key));
            }
            return merged;
        } catch (Exception e) {
            return base;   // a broken mode never breaks Home
        }
    }

    // ---- pure logic, unit-tested ----

    static String askValue(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return ASK_DAILY.equals(v) || ASK_NEVER.equals(v) ? v : ASK_BOOT;
    }

    static String colourName(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return RED.equals(v) || GREEN.equals(v) || YELLOW.equals(v) || BLUE.equals(v) ? v : "";
    }

    static boolean themeOnly(String key) {
        for (String k : THEME_ONLY) if (k.equals(key)) return true;
        return false;
    }

    static int indexOf(List<Mode> modes, String id) {
        if (id == null) return -1;
        for (int i = 0; i < modes.size(); i++) if (modes.get(i).id.equals(id)) return i;
        return -1;
    }

    /** morning (5-11), afternoon (12-16), evening (17-21) or night. */
    static String daypart(int hour) {
        if (hour >= 5 && hour < 12) return "morning";
        if (hour >= 12 && hour < 17) return "afternoon";
        if (hour >= 17 && hour < 22) return "evening";
        return "night";
    }

    /** The mode id the welcome suggests at this hour: news early, games on weekend days, movies late. */
    static String suggestedId(int hour, boolean weekend) {
        if (hour >= 5 && hour < 10) return "news";
        if (hour >= 10 && hour < 17) return weekend ? "games" : "entertain";
        if (hour >= 17 && hour < 21) return "entertain";
        return "movies";
    }

    /** Index of the suggested mode; if the theme has no such mode, the last chosen one, else the first. */
    int suggested(int hour, boolean weekend, String lastChosen) {
        int i = indexOf(modes, suggestedId(hour, weekend));
        if (i < 0) i = indexOf(modes, lastChosen);
        return Math.max(0, i);
    }

    Mode forKey(String colour) {
        if (colour == null || colour.isEmpty()) return null;
        for (Mode m : modes) if (colour.equals(m.key)) return m;
        return null;
    }

    /** The remote's colour keys by name, or null for any other key. */
    static String colourKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_PROG_RED: return RED;
            case KeyEvent.KEYCODE_PROG_GREEN: return GREEN;
            case KeyEvent.KEYCODE_PROG_YELLOW: return YELLOW;
            case KeyEvent.KEYCODE_PROG_BLUE: return BLUE;
            default: return null;
        }
    }

    /** Whether the welcome is due: once per boot, once per day, or never. An unknown boot count (-1) never asks. */
    static boolean welcomeDue(String ask, int boot, int lastBoot, long day, long lastDay) {
        if (ASK_NEVER.equals(ask)) return false;
        if (ASK_DAILY.equals(ask)) return day != lastDay;
        return boot >= 0 && boot != lastBoot;
    }

    // ---- on the TV ----

    static String chosen(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MODE_KEY, null);
    }

    /** Remembers the mode and moves Launcher settings' change count, so Home rebuilds with it on resume. */
    static void choose(Context ctx, String modeId) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MODE_KEY, modeId).apply();
        SharedPreferences settings = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        settings.edit().putLong(Config.SETTINGS_REVISION_KEY, settings.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
    }

    boolean welcomeDue(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return welcomeDue(ask, bootCount(ctx), p.getInt(BOOT_KEY, -1), today(), p.getLong(DAY_KEY, -1));
    }

    static void markWelcomed(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(BOOT_KEY, bootCount(ctx)).putLong(DAY_KEY, today()).apply();
    }

    private static int bootCount(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    private static long today() {
        return java.time.LocalDate.now().toEpochDay();
    }
}
