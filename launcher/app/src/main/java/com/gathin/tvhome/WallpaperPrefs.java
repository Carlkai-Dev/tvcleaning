package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** On-TV wallpaper choice: default (theme/plain), custom image, or India special-day auto. */
final class WallpaperPrefs {
    static final String MODE_DEFAULT = "default";
    static final String MODE_CUSTOM = "custom";
    static final String MODE_INDIA = "india";
    static final String KEY_MODE = "wallpaper_mode";
    static final String CUSTOM_FILE = "custom_wallpaper.jpg";

    private WallpaperPrefs() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static String mode(Context ctx) {
        SharedPreferences p = prefs(ctx);
        // One-shot: turn on festival photo wallpapers for existing installs that still had "default".
        if (!p.getBoolean("wallpaper_festival_photos_v1", false)) {
            p.edit().putString(KEY_MODE, MODE_INDIA).putBoolean("wallpaper_festival_photos_v1", true).apply();
        }
        return IndiaEvents.normalizeMode(p.getString(KEY_MODE, MODE_INDIA));
    }

    static void setMode(Context ctx, String mode) {
        prefs(ctx).edit().putString(KEY_MODE, IndiaEvents.normalizeMode(mode)).apply();
    }

    static File customFile(Context ctx) {
        return new File(ctx.getFilesDir(), CUSTOM_FILE);
    }

    static boolean hasCustom(Context ctx) {
        File f = customFile(ctx);
        return f.isFile() && f.length() > 0;
    }
}
