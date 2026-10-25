package com.patilweb.tvcam;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Shared PiP placement: percent of free area (0..100) from top-left.
 * Grid presets map to corners / edges / center.
 */
final class PipLayout {
    private PipLayout() {}

    /** Convert 0..8 grid index → (xPct, yPct). */
    static float[] pctForGrid(int position) {
        int i = Math.max(0, Math.min(8, position));
        int col = i % 3; // 0 left, 1 mid, 2 right
        int row = i / 3; // 0 top, 1 mid, 2 bottom
        return new float[]{col * 50f, row * 50f};
    }

    /** Nearest grid cell for current percents (for highlighting). */
    static int nearestGrid(float xPct, float yPct) {
        int col = Math.round(xPct / 50f);
        int row = Math.round(yPct / 50f);
        col = Math.max(0, Math.min(2, col));
        row = Math.max(0, Math.min(2, row));
        return row * 3 + col;
    }

    static int[] screenSize(Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.heightPixels;
        WindowManager wm = ctx.getSystemService(WindowManager.class);
        if (wm != null) {
            DisplayMetrics real = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(real);
            w = real.widthPixels;
            h = real.heightPixels;
        }
        return new int[]{w, h};
    }

    /**
     * Apply TOP|START gravity + x/y from percents into FrameLayout params.
     * bottomReservePx keeps PiP above the control bar when near the bottom.
     */
    static void applyFrame(Context ctx, FrameLayout.LayoutParams lp, int sizePx,
                           float xPct, float yPct, int bottomReservePx) {
        lp.width = sizePx;
        lp.height = sizePx;
        lp.gravity = Gravity.TOP | Gravity.START;
        int[] scr = screenSize(ctx);
        int usableH = Math.max(sizePx, scr[1] - bottomReservePx);
        int maxX = Math.max(0, scr[0] - sizePx);
        int maxY = Math.max(0, usableH - sizePx);
        int left = Math.round(maxX * (clamp(xPct) / 100f));
        int top = Math.round(maxY * (clamp(yPct) / 100f));
        lp.setMargins(left, top, 0, 0);
    }

    /** WindowManager overlay params (TYPE_APPLICATION_OVERLAY). */
    static void applyWindow(Context ctx, WindowManager.LayoutParams lp, int sizePx,
                            float xPct, float yPct, int bottomReservePx) {
        lp.width = sizePx;
        lp.height = sizePx;
        lp.gravity = Gravity.TOP | Gravity.START;
        int[] scr = screenSize(ctx);
        int usableH = Math.max(sizePx, scr[1] - bottomReservePx);
        int maxX = Math.max(0, scr[0] - sizePx);
        int maxY = Math.max(0, usableH - sizePx);
        lp.x = Math.round(maxX * (clamp(xPct) / 100f));
        lp.y = Math.round(maxY * (clamp(yPct) / 100f));
    }

    /** Place a child View inside a parent of known size (preview mock). */
    static void placeInParent(ViewGroup.MarginLayoutParams lp, int parentW, int parentH,
                              int sizePx, float xPct, float yPct) {
        lp.width = sizePx;
        lp.height = sizePx;
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).gravity = Gravity.TOP | Gravity.START;
        }
        int maxX = Math.max(0, parentW - sizePx);
        int maxY = Math.max(0, parentH - sizePx);
        int left = Math.round(maxX * (clamp(xPct) / 100f));
        int top = Math.round(maxY * (clamp(yPct) / 100f));
        lp.setMargins(left, top, 0, 0);
    }

    static float clamp(float pct) {
        if (pct < 0f) return 0f;
        if (pct > 100f) return 100f;
        return pct;
    }
}
