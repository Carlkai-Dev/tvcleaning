package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Soft, static colour fields behind the glass. Four full-screen gradients at 42% alpha cost a TV GPU
 * an offscreen layer plus five full-screen passes every frame, so they are painted once, on size
 * changes, into a quarter-size bitmap and drawn as one stretched texture; the fields are smooth enough
 * that the stretch is invisible. One opaque draw takes the view's alpha directly, without a layer.
 */
public final class AtmosphereView extends View {
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect frame = new Rect();
    private Bitmap baked;
    public AtmosphereView(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w <= 0 || h <= 0) return;
        int bw = Math.max(1, w / 4), bh = Math.max(1, h / 4);
        Bitmap bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF727270);
        float[][] spots = {{.08f,.1f,.48f},{.42f,.24f,.4f},{.84f,.35f,.43f},{.6f,.9f,.56f}};
        int[] colors = {0x9C87B0B4, 0x8CCBB492, 0x3DAD808E, 0x5288A3B6};
        Paint cloud = new Paint(Paint.DITHER_FLAG);
        for (int i = 0; i < spots.length; i++) {
            cloud.setShader(new RadialGradient(bw * spots[i][0], bh * spots[i][1], bw * spots[i][2],
                    colors[i], colors[i] & 0xFFFFFF, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, bw, bh, cloud);
        }
        baked = bitmap;
    }
    @Override public boolean hasOverlappingRendering() { return false; }
    @Override protected void onDraw(Canvas canvas) {
        if (baked == null) return;
        frame.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(baked, null, frame, image);
    }
}
