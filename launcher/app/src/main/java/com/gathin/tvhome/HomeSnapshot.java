package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

/**
 * A still of Home as it was last left, for HomeKeyService's cover on Fire OS: Android holds this launcher's start
 * back for about five seconds after Home, and showing the still instead of black makes Home look instant. One
 * RGB_565 bitmap at screen size (about 4 MB at 1080p), reused; Home and the service share the app's process.
 */
final class HomeSnapshot {
    private static Bitmap still;

    private HomeSnapshot() {
    }

    /** Draws Home into the still; called as Home goes to the background. */
    static void capture(View root) {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        try {
            if (still == null || still.getWidth() != w || still.getHeight() != h) {
                still = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            }
            Canvas canvas = new Canvas(still);
            canvas.drawColor(Color.BLACK);
            root.draw(canvas);
        } catch (RuntimeException | OutOfMemoryError e) {
            still = null;   // the cover falls back to black
        }
    }

    /** The last still, or null before Home was ever left (right after boot). */
    static Bitmap get() {
        return still;
    }
}
