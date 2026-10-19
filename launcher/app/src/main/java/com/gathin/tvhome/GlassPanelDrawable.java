package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Translucent material with a lit upper rim; no live blur or offscreen render loop on older TVs. */
final class GlassPanelDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius, density;
    GlassPanelDrawable(float density, float radius) {
        this.density = density;
        this.radius = radius * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(density);
    }
    @Override protected void onBoundsChange(Rect bounds) {
        rect.set(bounds);
        rect.inset(density, density);
        fill.setShader(new LinearGradient(0, bounds.top, bounds.right, bounds.bottom,
                new int[]{0x658C9AA5, 0x326C7585, 0x486D727D}, null, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{0xB8F3FFFF, 0x26FFFFFF, 0x55FFFFFF}, null, Shader.TileMode.CLAMP));
    }
    @Override public void draw(Canvas canvas) {
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, rim);
    }
    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); rim.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
