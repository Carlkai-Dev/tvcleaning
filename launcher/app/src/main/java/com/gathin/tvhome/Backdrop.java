package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * the wallpaper as frosted glass. Holds a tiny, heavily blurred copy of the wallpaper on screen
 * and maps it under any view, so the dock band and the top pills show the picture behind them, blurred.
 * Android 11 has no RenderEffect; the wallpaper is static, so one blur per wallpaper is all it takes. When the
 * wallpaper changes (day/night), {@link #set} swaps the copy and redraws every registered view.
 */
final class Backdrop {

    /** Width of the blurred copy; drawn scaled up with filtering, which adds to the softness. */
    private static final int BLUR_WIDTH = 160;

    private Bitmap blur;
    private ImageView wall;
    private float scale;
    private int version;
    private final List<View> users = new ArrayList<>();
    private final int[] wallAt = new int[2];
    private final int[] targetAt = new int[2];

    /** wall is the full-screen wallpaper view showing src; null clears the glass (solid fallbacks draw). */
    void set(Bitmap src, ImageView wall) {
        if (src == null || wall == null || wall.getDrawable() == null) {
            blur = null;
            this.wall = null;
        } else {
            blur = blur(src);
            this.wall = wall;
            scale = (float) wall.getDrawable().getIntrinsicWidth() / blur.getWidth();
        }
        version++;
        redraw();
    }

    void register(View v) {
        users.add(v);
    }

    void redraw() {
        for (View v : users) v.invalidate();
    }

    /** Paints that draw the backdrop; one per view, since each needs its own matrix. */
    static final class Painter {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix matrix = new Matrix();
        private BitmapShader shader;
        private int version = -1;

        /** Lines the blurred wallpaper up under target; false when there is no wallpaper to show. */
        boolean prepare(Backdrop bd, View target) {
            if (bd.blur == null || bd.wall == null) return false;
            if (version != bd.version) {
                shader = new BitmapShader(bd.blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                paint.setShader(shader);
                version = bd.version;
            }
            // blur px -> wallpaper px -> wall view (CENTER_CROP matrix) -> target's coordinates
            bd.wall.getLocationInWindow(bd.wallAt);
            target.getLocationInWindow(bd.targetAt);
            matrix.set(bd.wall.getImageMatrix());
            matrix.preScale(bd.scale, bd.scale);
            matrix.postTranslate(bd.wallAt[0] - bd.targetAt[0], bd.wallAt[1] - bd.targetAt[1]);
            shader.setLocalMatrix(matrix);
            return true;
        }
    }

    /** A small, heavily blurred copy (about 60 KB at 160x90). */
    static Bitmap blur(Bitmap src) {
        int w = BLUR_WIDTH, h = Math.max(1, Math.round((float) src.getHeight() * w / src.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
        int[] px = new int[w * h], tmp = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {   // three box passes approximate a gaussian
            boxBlur(px, tmp, w, h, 3, true);
            boxBlur(tmp, px, w, h, 3, false);
        }
        small.setPixels(px, 0, w, 0, 0, w, h);
        return small;
    }

    private static void boxBlur(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h, div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int ra = 0, ga = 0, ba = 0;
            for (int k = -r; k <= r; k++) {
                int c = in[index(line, Math.max(0, Math.min(len - 1, k)), w, horizontal)];
                ra += (c >> 16) & 0xFF; ga += (c >> 8) & 0xFF; ba += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[index(line, i, w, horizontal)] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int add = in[index(line, Math.min(len - 1, i + r + 1), w, horizontal)];
                int sub = in[index(line, Math.max(0, i - r), w, horizontal)];
                ra += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                ga += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                ba += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }
}
