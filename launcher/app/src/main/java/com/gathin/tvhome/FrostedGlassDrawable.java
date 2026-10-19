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
import android.view.View;

/** A cached, genuinely blurred backdrop plus a smoky glass body and softly lit edge. API 26+. */
final class FrostedGlassDrawable extends Drawable {
    private final Bitmap backdrop;
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float radius, density;

    FrostedGlassDrawable(View home, Rect region, float density, float radiusDp) {
        this.density = density;
        radius = radiusDp * density;
        // Less than 10 KB for a typical menu; no full-screen bitmap, RenderEffect or live blur loop.
        backdrop = FolderBackdrop.capture(home, region, Math.max(1, region.width() / 8),
                Math.max(1, region.height() / 8), 4);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(.75f * density);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        rect.set(bounds);
        rect.inset(density * .5f, density * .5f);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        body.setShader(new LinearGradient(0, bounds.top, bounds.right, bounds.bottom,
                new int[]{0xAD424750, 0xC0272B32, 0xB8343840}, null, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, bounds.top, bounds.right, bounds.bottom,
                new int[]{0xA6FFFFFF, 0x26FFFFFF, 0x54FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clip);
        canvas.drawBitmap(backdrop, null, rect, image);
        canvas.drawRoundRect(rect, radius, radius, body);
        canvas.restoreToCount(save);
        canvas.drawRoundRect(rect, radius, radius, rim);
    }

    @Override public void setAlpha(int alpha) { image.setAlpha(alpha); body.setAlpha(alpha); rim.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { image.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void getOutline(android.graphics.Outline outline) { outline.setRoundRect(getBounds(), radius); }
}
