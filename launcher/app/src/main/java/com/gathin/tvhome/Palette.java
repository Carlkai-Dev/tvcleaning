package com.gathin.tvhome;

import java.util.Locale;

/**
 * A theme's own colours, from config.json: {@code accent_color}, {@code text_color}, {@code surface_color} and
 * {@code glow_color}. Every key is optional and a malformed one is ignored on its own, so a theme without them
 * looks exactly like the reference launcher. They only change which colours are painted: no bitmaps, no extra
 * views, nothing that costs memory.
 *
 * While a festive skin is on, the festival's accent wins over {@code accent_color}, as it always has.
 */
final class Palette {

    static final Palette NONE = new Palette(0, 0, 0, Glow.APP, 0);

    enum Glow { APP, FIXED, OFF }

    /** Focus highlight and greeting, 0xAARRGGBB; 0 keeps white glass. */
    final int accent;
    /** Clock, date and the app name above the dock; 0 keeps white. */
    final int text;
    /** The dock band; 0 keeps the default dark band. */
    final int surface;
    final Glow glow;
    private final int glowColor;

    private Palette(int accent, int text, int surface, Glow glow, int glowColor) {
        this.accent = accent;
        this.text = text;
        this.surface = surface;
        this.glow = glow;
        this.glowColor = glowColor;
    }

    /**
     * Builds the palette from raw config values. Colours are {@code #RRGGBB}; {@code glow} is {@code "app"}
     * (the colour sampled from the focused app's artwork, the default), {@code "off"}, or a {@code #RRGGBB}
     * that every app shares. Anything else falls back to the default for that key.
     */
    static Palette parse(String accent, String text, String surface, String glow) {
        int a = Festival.parseColor(accent), t = Festival.parseColor(text), s = Festival.parseColor(surface);
        String g = glow == null ? "" : glow.trim().toLowerCase(Locale.ROOT);
        Glow mode = Glow.APP;
        int fixed = 0;
        if (g.equals("off")) {
            mode = Glow.OFF;
        } else {
            fixed = Festival.parseColor(g);
            if (fixed != 0) mode = Glow.FIXED;
        }
        if (a == 0 && t == 0 && s == 0 && mode == Glow.APP) return NONE;
        return new Palette(a, t, s, mode, fixed);
    }

    /** The ambient glow for an app whose artwork gave {@code appColor}; 0 when the glow is off. */
    int glowFor(int appColor) {
        switch (glow) {
            case FIXED:
                return glowColor;
            case OFF:
                return 0;
            default:
                return appColor;
        }
    }

    boolean glowOff() {
        return glow == Glow.OFF;
    }

    /** {@link #text} with the alpha of a view's own colour, so dimmer lines (the date) stay dimmer. */
    int textWithAlphaOf(int original) {
        if (text == 0) return original;
        return (original & 0xFF000000) | (text & 0x00FFFFFF);
    }
}
