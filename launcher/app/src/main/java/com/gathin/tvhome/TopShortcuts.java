package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.TextView;

/**
 * Cores dos atalhos superiores (pills): padrão frosted, contraste com o wallpaper, ou visual de vidro
 * alinhado à faixa do dock.
 */
final class TopShortcuts {
    static final String STYLE_DEFAULT = "default";
    static final String STYLE_WALLPAPER = "wallpaper";
    static final String STYLE_GLASS = "glass";

    /** Relative luminance above this → wallpaper is “light” → pills go dark. */
    static final float LIGHT_WALLPAPER = 0.52f;

    private TopShortcuts() {
    }

    static String normalize(String value) {
        if (STYLE_WALLPAPER.equals(value) || STYLE_GLASS.equals(value)) return value;
        return STYLE_DEFAULT;
    }

    /**
     * Luminância média (0–1) da faixa superior da imagem (onde ficam os atalhos).
     * Bitmap nulo → fundo escuro.
     */
    static float topLuminance(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return 0f;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w <= 0 || h <= 0) return 0f;
        int y0 = 0;
        int y1 = Math.max(1, h / 5);
        int x0 = w * 2 / 3;
        int stepX = Math.max(1, (w - x0) / 24);
        int stepY = Math.max(1, y1 / 8);
        long sum = 0;
        int n = 0;
        for (int y = y0; y < y1; y += stepY) {
            for (int x = x0; x < w; x += stepX) {
                int c = bitmap.getPixel(x, y);
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                // Rec. 709 relative luminance
                sum += (long) (0.2126 * r + 0.7152 * g + 0.0722 * b);
                n++;
            }
        }
        return n == 0 ? 0f : (sum / (float) n) / 255f;
    }

    static boolean wallpaperIsLight(float luminance) {
        return luminance >= LIGHT_WALLPAPER;
    }

    static float colorLuminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (float) ((0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0);
    }

    /**
     * @param tintIcons false quando o festival já desenha ícones coloridos próprios
     */
    static void apply(TextView[] pills, String style, float wallpaperLuminance, int glassOpacity,
                      float density, boolean tintIcons) {
        style = normalize(style);
        if (STYLE_DEFAULT.equals(style)) {
            for (TextView pill : pills) {
                if (pill == null) continue;
                pill.setBackgroundResource(R.drawable.pill_bg);
                ColorStateList text = pill.getResources().getColorStateList(R.color.pill_text, null);
                pill.setTextColor(text);
                if (tintIcons) pill.setCompoundDrawableTintList(text);
            }
            return;
        }
        boolean darkPills = STYLE_GLASS.equals(style) || wallpaperIsLight(wallpaperLuminance);
        int opacity = Config.clampDockGlassOpacity(glassOpacity);
        for (TextView pill : pills) {
            if (pill == null) continue;
            pill.setBackground(buildBackground(darkPills, STYLE_GLASS.equals(style), opacity, density));
            ColorStateList text = textColors(darkPills);
            pill.setTextColor(text);
            if (tintIcons) pill.setCompoundDrawableTintList(text);
        }
    }

    private static ColorStateList textColors(boolean darkPills) {
        // Focado: pill clara → texto escuro; fora: texto claro sobre fundo escuro/frost.
        return new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0xFF111111, 0xFFFFFFFF});
    }

    private static StateListDrawable buildBackground(boolean darkPills, boolean glass, int opacity,
                                                     float density) {
        StateListDrawable sel = new StateListDrawable();
        sel.addState(new int[]{android.R.attr.state_focused}, roundRect(0xF5FFFFFF, 0, 0, density));
        sel.addState(new int[]{android.R.attr.state_activated}, roundRect(0x596EC1FF, 0xFF6EC1FF, 1.5f, density));
        if (glass || darkPills) {
            int alpha = glass ? opacity * 255 / 100 : 0xB3;
            int fill = (alpha << 24) | 0x00121014;
            int stroke = glass ? 0x26FFFFFF : 0x40FFFFFF;
            sel.addState(new int[]{}, roundRect(fill, stroke, 1f, density));
        } else {
            // Wallpaper escuro: frosted claro (como o padrão).
            GradientDrawable idle = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x2EFFFFFF, 0x14FFFFFF});
            idle.setCornerRadius(21f * density);
            idle.setStroke(Math.max(1, Math.round(density)), 0x38FFFFFF);
            sel.addState(new int[]{}, idle);
        }
        return sel;
    }

    private static GradientDrawable roundRect(int fill, int stroke, float strokeDp, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(21f * density);
        if (stroke != 0) d.setStroke(Math.max(1, Math.round(strokeDp * density)), stroke);
        return d;
    }

    /** Aplica nos quatro pills do topo; ignora views gone. */
    static void applyAll(View inputs, View wifi, View reorder, View settings, String style,
                         float wallpaperLuminance, int glassOpacity, float density, boolean tintIcons) {
        apply(new TextView[]{
                inputs instanceof TextView ? (TextView) inputs : null,
                wifi instanceof TextView ? (TextView) wifi : null,
                reorder instanceof TextView ? (TextView) reorder : null,
                settings instanceof TextView ? (TextView) settings : null,
        }, style, wallpaperLuminance, glassOpacity, density, tintIcons);
    }
}
