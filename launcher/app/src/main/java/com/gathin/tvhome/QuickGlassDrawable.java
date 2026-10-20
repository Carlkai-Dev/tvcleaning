package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A capsule cut from one shared, tiny blurred Home snapshot, with a luminous glass rim. */
final class QuickGlassDrawable extends Drawable {
    private final Bitmap backdrop;
    private final Rect source = new Rect();
    private final RectF shape = new RectF();
    private final Path clip = new Path();
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float unit;
    private int alpha = 255;
    private boolean focused;

    QuickGlassDrawable(Bitmap backdrop, float unit) {
        this.backdrop = backdrop;
        this.unit = unit;
        rim.setStyle(Paint.Style.STROKE);
        focusRim.setStyle(Paint.Style.STROKE);
        focusRim.setStrokeWidth(unit);
        focusGlow.setStyle(Paint.Style.STROKE);
        focusGlow.setColor(0xFF0A84FF);
        light.setColor(0xFFFFFFFF);
    }

    void setRegion(Rect region, int homeWidth, int homeHeight) {
        source.set(Math.max(0, region.left * backdrop.getWidth() / homeWidth),
                Math.max(0, region.top * backdrop.getHeight() / homeHeight),
                Math.min(backdrop.getWidth(), Math.max(1, region.right * backdrop.getWidth() / homeWidth)),
                Math.min(backdrop.getHeight(), Math.max(1, region.bottom * backdrop.getHeight() / homeHeight)));
        invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        shape.set(bounds);
        shape.inset(unit, unit);
        clip.reset();
        clip.addRoundRect(shape, shape.height() / 2f, shape.height() / 2f, Path.Direction.CW);
        fill.setShader(new LinearGradient(0, 0, bounds.width(), bounds.height(),
                new int[]{0x64D5DFF2, 0x3EAAB9D2, 0x589AAAC6}, null, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, bounds.width(), bounds.height(),
                new int[]{0xE6FFFFFF, 0x5EECF1FF, 0xB8CEDEFF}, null, Shader.TileMode.CLAMP));
        focusRim.setShader(new LinearGradient(0, 0, bounds.width(), bounds.height(),
                new int[]{0xFFB9E0FF, 0xFF59AFFF, 0xFF238CFF}, null, Shader.TileMode.CLAMP));
    }

    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] state) {
        boolean next = false;
        for (int value : state) if (value == android.R.attr.state_focused) next = true;
        if (next == focused) return false;
        focused = next;
        invalidateSelf();
        return true;
    }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clip);
        if (!source.isEmpty()) canvas.drawBitmap(backdrop, source, shape, image);
        canvas.drawRoundRect(shape, shape.height() / 2f, shape.height() / 2f, fill);
        if (focused) {
            light.setAlpha(alpha * 32 / 255);
            canvas.drawRoundRect(shape, shape.height() / 2f, shape.height() / 2f, light);
            // Diffuse the blue inward, keeping the halo inside the capsule so it cannot
            // be clipped by the adjacent tile or a scroll edge on TV hardware.
            focusGlow.setStrokeWidth(6f * unit);
            focusGlow.setAlpha(alpha * 10 / 255);
            canvas.drawRoundRect(shape, shape.height() / 2f, shape.height() / 2f, focusGlow);
            focusGlow.setStrokeWidth(3.5f * unit);
            focusGlow.setAlpha(alpha * 16 / 255);
            canvas.drawRoundRect(shape, shape.height() / 2f, shape.height() / 2f, focusGlow);
            focusGlow.setStrokeWidth(2f * unit);
            focusGlow.setAlpha(alpha * 24 / 255);
            canvas.drawRoundRect(shape, shape.height() / 2f, shape.height() / 2f, focusGlow);
        }
        canvas.restoreToCount(save);
        Paint edge = focused ? focusRim : rim;
        rim.setStrokeWidth(unit * .8f);
        edge.setAlpha(alpha);
        canvas.drawRoundRect(shape, shape.height() / 2f, shape.height() / 2f, edge);
    }

    @Override public void setAlpha(int alpha) { this.alpha = alpha; image.setAlpha(alpha); fill.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { image.setColorFilter(filter); fill.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
