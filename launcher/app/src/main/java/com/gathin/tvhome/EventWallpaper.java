package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Builds a full-screen RGB_565 backdrop for an {@link IndiaEvents.Event}, sized exactly to the TV
 * so FIT_XY fills the display without crop leftover or letterboxing.
 */
final class EventWallpaper {
    private EventWallpaper() {}

    static Bitmap render(IndiaEvents.Event event, int screenW, int screenH) {
        int w = Math.max(640, screenW);
        int h = Math.max(360, screenH);
        // Cap decoded size for 1 GB TVs while keeping the screen aspect ratio exact.
        long pixels = (long) w * h;
        if (pixels > WallpaperSize.MAX_PIXELS) {
            double scale = Math.sqrt(WallpaperSize.MAX_PIXELS / (double) pixels);
            w = Math.max(640, (int) (w * scale));
            h = Math.max(360, (int) (h * scale));
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{event.colorTop, event.colorMid, event.colorBottom},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        drawGlow(c, w * 0.22f, h * 0.35f, w * 0.38f, event.accent, 0x55);
        drawGlow(c, w * 0.78f, h * 0.55f, w * 0.30f, event.accent, 0x33);
        drawGlow(c, w * 0.50f, h * 0.16f, w * 0.42f, 0xFFFFFFFF, 0x18);
        return bmp;
    }

    /**
     * Scales {@code src} with center-crop so the result is exactly {@code screenW}×{@code screenH}.
     * Recycles intermediate bitmaps when safe.
     */
    static Bitmap coverExact(Bitmap src, int screenW, int screenH) {
        if (src == null || screenW <= 0 || screenH <= 0) return src;
        if (src.getWidth() == screenW && src.getHeight() == screenH) return src;
        float scale = Math.max(screenW / (float) src.getWidth(), screenH / (float) src.getHeight());
        int sw = Math.max(1, Math.round(src.getWidth() * scale));
        int sh = Math.max(1, Math.round(src.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(src, sw, sh, true);
        int x = Math.max(0, (sw - screenW) / 2);
        int y = Math.max(0, (sh - screenH) / 2);
        int cw = Math.min(screenW, sw);
        int ch = Math.min(screenH, sh);
        Bitmap out = Bitmap.createBitmap(scaled, x, y, cw, ch);
        if (out.getWidth() != screenW || out.getHeight() != screenH) {
            Bitmap fitted = Bitmap.createScaledBitmap(out, screenW, screenH, true);
            if (out != scaled && out != src) out.recycle();
            out = fitted;
        }
        if (scaled != src && scaled != out) scaled.recycle();
        if (src != out && !src.isRecycled()) {
            // Caller owns src; leave it unless we replaced it entirely from a temp decode.
        }
        return out;
    }

    private static void drawGlow(Canvas c, float cx, float cy, float radius, int color, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int a = Math.max(0, Math.min(255, alpha));
        int opaque = color | 0xFF000000;
        int soft = (a << 24) | (opaque & 0x00FFFFFF);
        p.setShader(new RadialGradient(cx, cy, Math.max(1f, radius),
                new int[]{soft, soft & 0x00FFFFFF},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius, p);
    }
}
