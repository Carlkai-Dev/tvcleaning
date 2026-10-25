package com.gathin.volumekeys;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

/** The four colour buttons and the volume each one sets, kept in this app's preferences. */
final class Levels {
    static final int[] KEYS = {KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_GREEN,
            KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_PROG_BLUE};
    static final String[] NAMES = {"Red", "Green", "Yellow", "Blue"};
    static final int[] COLORS = {0xFFE5534B, 0xFF3FB950, 0xFFE3B341, 0xFF4493F8};
    static final int[] DEFAULTS = {10, 15, 20, 25};
    static final int MAX = 100;

    private Levels() { }

    /** Position of a keycode in KEYS, or -1 for any other key. */
    static int indexOf(int keyCode) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i] == keyCode) return i;
        return -1;
    }

    static int clamp(int level, int max) {
        return Math.max(0, Math.min(level, max));
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("levels", Context.MODE_PRIVATE);
    }

    static int get(Context c, int index) {
        return clamp(prefs(c).getInt("k" + index, DEFAULTS[index]), MAX);
    }

    static void set(Context c, int index, int level) {
        prefs(c).edit().putInt("k" + index, clamp(level, MAX)).apply();
    }
}
