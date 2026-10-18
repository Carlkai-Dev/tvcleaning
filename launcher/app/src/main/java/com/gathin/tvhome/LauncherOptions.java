package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** Small cached preferences: drawing and animations never read disk or retain an Activity. */
final class LauncherOptions {
    static float halo = 1f;
    static float dockOpacity = 1f;
    static boolean glass;
    static int motion;

    static void load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE);
        int glow = prefs.getInt("appearance_halo", 0);
        halo = glow == 1 ? 0.4f : glow == 2 ? 0f : 1f;
        int opacity = prefs.getInt("appearance_dock", 0);
        glass = opacity == 3;
        dockOpacity = glass ? 0.18f : opacity == 1 ? 0.55f : opacity == 2 ? 1.6f : 1f;
        motion = prefs.getInt("appearance_motion", 0);
    }

    static long duration(long normal) {
        return motion == 2 ? 0 : motion == 1 ? Math.max(1, normal / 2) : normal;
    }
}
