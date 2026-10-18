package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** The dock's finish on header controls and panels. Shaders rebuild only on size/finish changes. */
final class GlassSurface extends Drawable {
    static final float[] SURFACE_STOPS = {0f, 0.34f, 0.78f, 1f};
    static final float[] EDGE_STOPS = {0f, 0.55f, 1f};
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selection = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    private final float inset;
    private final boolean pill;
    private boolean focused;
    private boolean dirty = true;
    private float opacity = -1;
    private int alpha = 255;

    GlassSurface(Context context, float radiusDp, boolean pill) {
        float density = context.getResources().getDisplayMetrics().density;
        radius = radiusDp * density;
        inset = 0.4f * density;
        this.pill = pill;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(0.8f * density);
        selection.setColor(0xF5FFFFFF);
    }

    static int tintColor() { return withOpacity(0x3014161A); }

    static int[] surfaceColors() {
        return LauncherOptions.glass ? new int[]{0x16FFFFFF, 0x05FFFFFF, 0x02FFFFFF, 0x08FFFFFF}
                : new int[]{withOpacity(0x3CFFFFFF), withOpacity(0x23FFFFFF),
                        withOpacity(0x17FFFFFF), withOpacity(0x20FFFFFF)};
    }

    static int[] edgeColors() {
        return LauncherOptions.glass ? new int[]{0xB0FFFFFF, 0x30FFFFFF, 0x70FFFFFF}
                : new int[]{0x70FFFFFF, 0x20FFFFFF, 0x40FFFFFF};
    }

    private static int withOpacity(int color) {
        int a = Math.min(255, (int) ((color >>> 24) * LauncherOptions.dockOpacity));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        rect.set(bounds);
        rect.inset(inset, inset);
        dirty = true;
    }

    @Override public boolean isStateful() { return pill; }

    @Override protected boolean onStateChange(int[] states) {
        boolean next = false;
        for (int state : states) if (state == android.R.attr.state_focused) next = true;
        if (!pill || next == focused) return false;
        focused = next;
        invalidateSelf();
        return true;
    }

    @Override public void draw(Canvas canvas) {
        if (rect.isEmpty()) return;
        if (focused) {
            selection.setAlpha(245 * alpha / 255);
            canvas.drawRoundRect(rect, radius, radius, selection);
            return;
        }
        if (dirty || opacity != LauncherOptions.dockOpacity) {
            opacity = LauncherOptions.dockOpacity;
            tint.setColor(tintColor());
            surface.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    surfaceColors(), SURFACE_STOPS, Shader.TileMode.CLAMP));
            edge.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    edgeColors(), EDGE_STOPS, Shader.TileMode.CLAMP));
            dirty = false;
        }
        tint.setAlpha((tintColor() >>> 24) * alpha / 255);
        surface.setAlpha(alpha);
        edge.setAlpha(alpha);
        canvas.drawRoundRect(rect, radius, radius, tint);
        canvas.drawRoundRect(rect, radius, radius, surface);
        canvas.drawRoundRect(rect, radius, radius, edge);
    }

    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) {
        tint.setColorFilter(filter);
        surface.setColorFilter(filter);
        edge.setColorFilter(filter);
        selection.setColorFilter(filter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
