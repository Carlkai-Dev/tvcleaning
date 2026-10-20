package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.File;

/**
 * Wallpaper from today's festival photos cached by {@link FestivalPhotos} (downloaded from the
 * internet in the background). Soft gradient only until the first photo arrives.
 */
final class EventWallpaper {
    private EventWallpaper() {}

    static Bitmap render(Context ctx, IndiaEvents.Event event, int screenW, int screenH, int variant) {
        Bitmap photo = decodeCached(ctx, event, variant, screenW, screenH);
        if (photo == null) return fallbackGradient(event, screenW, screenH);
        Bitmap covered = coverExact(photo, screenW, screenH);
        if (covered != photo) photo.recycle();
        Bitmap tinted = tintForUi(covered, event);
        if (tinted != covered) covered.recycle();
        return tinted;
    }

    private static Bitmap decodeCached(Context ctx, IndiaEvents.Event event, int variant, int screenW, int screenH) {
        File file = FestivalPhotos.photoFile(ctx, event, variant);
        if (file == null || !file.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            int w = bounds.outWidth, h = bounds.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = WallpaperSize.sampleSize(w, h, Math.max(1, screenW), Math.max(1, screenH));
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inScaled = false;
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** Soft bottom wash so dock labels stay readable on bright festival photos. */
    private static Bitmap tintForUi(Bitmap src, IndiaEvents.Event event) {
        if (src == null) return null;
        Bitmap layer = src.copy(Bitmap.Config.ARGB_8888, true);
        if (layer == null) return src;
        Canvas c = new Canvas(layer);
        int w = layer.getWidth(), h = layer.getHeight();
        Paint p = new Paint();
        int accent = event != null ? event.accent : 0xFFFF6F00;
        p.setShader(new LinearGradient(0, h * 0.45f, 0, h,
                new int[]{Color.TRANSPARENT, Color.argb(40, 0, 0, 0), Color.argb(140, 0, 0, 0)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.5f, h * 0.2f, w * 0.55f,
                new int[]{Color.argb(50, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        Bitmap rgb = layer.copy(Bitmap.Config.RGB_565, false);
        layer.recycle();
        return rgb != null ? rgb : src;
    }

    private static Bitmap fallbackGradient(IndiaEvents.Event event, int screenW, int screenH) {
        int w = Math.max(640, screenW);
        int h = Math.max(360, screenH);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        int top = event != null ? event.colorTop : 0xFF1A1208;
        int mid = event != null ? event.colorMid : 0xFF0B0D12;
        int bottom = event != null ? event.colorBottom : 0xFF0A1A12;
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{top, mid, bottom},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return bmp;
    }

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
        return out;
    }
}
