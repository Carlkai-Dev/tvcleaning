package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.gathin.tvhome/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild:
 * {@code tools/theme.sh themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Choices made in the launcher's own settings screen ({@link LauncherSettingsActivity}) are kept in
 * shared preferences and applied last, so they survive a theme push; Lite mode then overrides the rest.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
final class Config {
    private static final String TAG = "TvHome.Config";
    static final String FILE_NAME = "config.json";
    static final String WALLPAPER_NAME = "wallpaper.jpg";
    /** Choices made in the launcher's own settings screen; they win over config.xml and the theme. */
    static final String PREFS = "launcher_settings";
    /** Values of the "background" choice. */
    static final String BG_GRADIENT = "gradient", BG_BLACK = "black";

    List<String> dockApps;
    List<String> hiddenApps;
    String liveTvPackage;
    String liveTvAction;
    String sourcePanelService;
    String sourcePanelAction;
    int columns;
    int rowsBelowDock;
    boolean shamsiDate;
    String vpnApp;
    boolean showHomeSettingsShortcut;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    int backgroundColor = 0xFF000000;
    /** Full-screen picture behind the launcher, or null for the plain colour. */
    File wallpaper;
    /** Focus, select and edge sounds (still subject to the TV's own system-sounds setting). */
    boolean uiSounds;
    /** Draw the glass frame on the focused tile; off leaves only the shadow and the sheen. */
    boolean glassFrame;
    /** Lean the focused tile in the direction of travel, with the banner shifting inside it. */
    boolean tilt;
    /** Background gradient colours, top to bottom; empty for the plain background colour. */
    int[] backgroundGradient;
    /** Band behind the dock row, with alpha. */
    int dockBandColor;
    /** Motion: glass springs, tile zoom, fades, smooth scrolling. Off makes every change instant. */
    boolean animations;
    /** Decoration: drop shadow, sheen, the app-coloured ambient glow and the glass's outer glow. */
    boolean effects;
    /** macOS Dock style magnification: the focused tile's row neighbours grow a little and part. */
    boolean magnify;
    /** Lite mode: no animations, no effects, no tilt, plain background. The fastest Home. */
    boolean lite;

    private Config() {
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        Config c = new Config();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        c.liveTvPackage = res.getString(R.string.live_tv_package).trim();
        c.liveTvAction = res.getString(R.string.live_tv_action).trim();
        c.sourcePanelService = res.getString(R.string.source_panel_service).trim();
        c.sourcePanelAction = res.getString(R.string.source_panel_action).trim();
        c.columns = res.getInteger(R.integer.columns);
        c.rowsBelowDock = res.getInteger(R.integer.rows_below_dock);
        c.shamsiDate = res.getBoolean(R.bool.shamsi_date);
        c.vpnApp = res.getString(R.string.vpn_app).trim();
        c.showHomeSettingsShortcut = res.getBoolean(R.bool.show_home_settings_shortcut);
        c.uiSounds = res.getBoolean(R.bool.ui_sounds);
        c.glassFrame = res.getBoolean(R.bool.glass_frame);
        c.tilt = res.getBoolean(R.bool.tilt);
        c.magnify = res.getBoolean(R.bool.magnify);
        c.animations = res.getBoolean(R.bool.animations);
        c.effects = res.getBoolean(R.bool.effects);
        c.lite = res.getBoolean(R.bool.lite);
        List<String> gradient = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.background_gradient)));
        c.dockBandColor = parseColor(res.getString(R.string.dock_band_color), 0x16FFFFFF);

        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        JSONObject json = read(file);
        if (json != null) {
            dock = stringList(json, "dock_apps", dock);
            hidden = stringList(json, "hidden_apps", hidden);
            c.liveTvPackage = string(json, "live_tv_package", c.liveTvPackage);
            c.liveTvAction = string(json, "live_tv_action", c.liveTvAction);
            c.sourcePanelService = string(json, "source_panel_service", c.sourcePanelService);
            c.sourcePanelAction = string(json, "source_panel_action", c.sourcePanelAction);
            c.columns = integer(json, "columns", c.columns, 1, 8);
            c.rowsBelowDock = integer(json, "rows_below_dock", c.rowsBelowDock, 0, 3);
            c.shamsiDate = bool(json, "shamsi_date", c.shamsiDate);
            c.vpnApp = string(json, "vpn_app", c.vpnApp);
            c.showHomeSettingsShortcut = bool(json, "show_home_settings_shortcut", c.showHomeSettingsShortcut);
            c.backgroundColor = color(json, "background_color", c.backgroundColor);
            c.uiSounds = bool(json, "ui_sounds", c.uiSounds);
            c.glassFrame = bool(json, "glass_frame", c.glassFrame);
            c.tilt = bool(json, "tilt", c.tilt);
            c.magnify = bool(json, "magnify", c.magnify);
            c.animations = bool(json, "animations", c.animations);
            c.effects = bool(json, "effects", c.effects);
            c.lite = bool(json, "lite", c.lite);
            gradient = stringList(json, "background_gradient", gradient);
            c.dockBandColor = colorWithAlpha(json, "dock_band_color", c.dockBandColor);
        }
        c.dockApps = Collections.unmodifiableList(dock);
        c.hiddenApps = Collections.unmodifiableList(hidden);
        c.backgroundGradient = gradientColors(gradient);
        c.wallpaper = dir != null ? new File(dir, WALLPAPER_NAME) : null;
        if (c.wallpaper != null && !(c.wallpaper.isFile() && c.wallpaper.length() > 0)) c.wallpaper = null;
        // A theme can also switch the wallpaper off while the file is still there.
        if (json != null && json.has("wallpaper") && !bool(json, "wallpaper", true)) c.wallpaper = null;

        // The settings screen on the TV, last, so a choice made there survives the next theme push.
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        c.lite = p.getBoolean("lite", c.lite);
        c.animations = p.getBoolean("animations", c.animations);
        c.effects = p.getBoolean("effects", c.effects);
        c.tilt = p.getBoolean("tilt", c.tilt);
        c.magnify = p.getBoolean("magnify", c.magnify);
        c.glassFrame = p.getBoolean("glass_frame", c.glassFrame);
        c.uiSounds = p.getBoolean("ui_sounds", c.uiSounds);
        int cols = p.getInt("columns", 0);
        if (cols >= 1 && cols <= 8) c.columns = cols;
        String bg = p.getString("background", "");
        if (BG_BLACK.equals(bg)) {
            c.backgroundGradient = new int[0];
            c.backgroundColor = 0xFF000000;
            c.wallpaper = null;
        } else if (BG_GRADIENT.equals(bg) && c.backgroundGradient.length == 0) {
            c.backgroundGradient = gradientColors(Arrays.asList(res.getStringArray(R.array.background_gradient)));
        }

        // Lite wins over everything: the plainest, fastest Home, with the glass frame as the focus cue.
        if (c.lite) {
            c.animations = false;
            c.effects = false;
            c.tilt = false;
            c.magnify = false;
            c.glassFrame = true;
            c.backgroundGradient = new int[0];
            c.wallpaper = null;
        }
        c.columns = Math.max(1, c.columns);
        c.rowsBelowDock = Math.max(0, c.rowsBelowDock);
        return c;
    }

    /** 2 or 3 valid opaque colours, or an empty array: anything else means "no gradient". */
    private static int[] gradientColors(List<String> list) {
        if (list.size() < 2 || list.size() > 3) return new int[0];
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            Integer c = tryParse(list.get(i));
            if (c == null) return new int[0];
            out[i] = c | 0xFF000000;
        }
        return out;
    }

    private static Integer tryParse(String s) {
        try {
            return Color.parseColor(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** #RRGGBB or #AARRGGBB, alpha kept. */
    private static int parseColor(String s, int fallback) {
        Integer c = tryParse(s);
        return c == null ? fallback : c;
    }

    /** Reads and parses the overlay; any problem means "no overlay", never a crash on the home screen. */
    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Like {@link #color} but keeps the alpha of an #AARRGGBB value. */
    private static int colorWithAlpha(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        return v instanceof String ? parseColor((String) v, fallback) : fallback;
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
