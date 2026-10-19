package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;

/**
 * The Outfit typeface, loaded from assets/ instead of res/font/ so the launcher runs on Android 5.1
 * (font resources need API 26). Outfit is a variable font; below API 28 it renders at its default
 * weight, which is the same behaviour this launcher already had on API 26-27.
 * Loads once per process; falls back to the platform light face if the asset cannot be parsed by an
 * old FreeType.
 */
public final class Outfit {

    private static Typeface typeface;

    public static Typeface get(Context context) {
        if (typeface == null) {
            Typeface t = null;
            try {
                t = Typeface.createFromAsset(context.getAssets(), "fonts/outfit.ttf");
            } catch (Exception ignored) {
            }
            typeface = t != null ? t : Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
        return typeface;
    }

    private Outfit() {
    }
}
