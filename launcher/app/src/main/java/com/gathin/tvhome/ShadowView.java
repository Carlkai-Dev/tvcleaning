package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * The soft drop shadow under the focused tile. It sits behind the grid, so while it springs to the
 * next tile it passes under the other tiles instead of over them, and it follows {@link GlassView}
 * (same size and position). The blur is rendered once per size into an alpha-only bitmap: blurring on
 * every frame is too much for a TV GPU.
 */
public class ShadowView extends View {

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float density;
    private float radius;
    private Bitmap shadow;

    public ShadowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        paint.setColor(0x99000000);
    }

    void setRadius(float px) {
        if (px == radius) return;
        radius = px;
        build();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        build();
    }

    private void build() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (shadow == null || shadow.getWidth() != w || shadow.getHeight() != h) {
            if (shadow != null) shadow.recycle();
            shadow = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        } else {
            shadow.eraseColor(0);
        }
        float inset = GlassView.INSET_DP * density;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setMaskFilter(new BlurMaskFilter(14f * density, BlurMaskFilter.Blur.NORMAL));
        // Slightly narrower than the tile and dropped below it, like a card lifted off the page.
        float side = 6f * density, drop = 12f * density;
        new Canvas(shadow).drawRoundRect(inset + side, inset + drop, w - inset - side, h - inset + drop * 0.6f,
                radius, radius, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shadow != null) canvas.drawBitmap(shadow, 0, 0, paint);
    }
}
