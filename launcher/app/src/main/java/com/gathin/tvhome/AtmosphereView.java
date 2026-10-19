package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Soft, static colour fields behind the glass. Shaders are allocated only on size changes. */
public final class AtmosphereView extends View {
    private final Paint[] clouds = {new Paint(3), new Paint(3), new Paint(3), new Paint(3)};
    public AtmosphereView(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float[][] spots = {{.08f,.1f,.48f},{.42f,.24f,.4f},{.84f,.35f,.43f},{.6f,.9f,.56f}};
        int[] colors = {0x9C87B0B4, 0x8CCBB492, 0x3DAD808E, 0x5288A3B6};
        for (int i = 0; i < clouds.length; i++) clouds[i].setShader(new RadialGradient(
                w * spots[i][0], h * spots[i][1], w * spots[i][2],
                colors[i], colors[i] & 0xFFFFFF, Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF727270);
        for (Paint paint : clouds) canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
