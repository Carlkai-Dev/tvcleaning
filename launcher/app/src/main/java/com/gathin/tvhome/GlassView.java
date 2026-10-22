package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * The focus frame: a 4dp band of glass around the focused tile. Android 9 has no live blur, so the glass is
 * drawn from light alone: one band, brightest at the top with a little light returning at the bottom, a crisp
 * highlight on its outer edge, a faint sheen over the top 40% of the tile, and a slight glow under it.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Room around the zoomed tile for the frame and its glow. MainActivity sizes the view with it. */
    static final float PAD_DP = 14f;
    private float inset;
    private final Path frame = new Path();
    private final RectF tile = new RectF();
    private final RectF outer = new RectF();
    private final RectF inner = new RectF();
    private final float density;
    private float radius;
    /** Glass tint as 0xRRGGBB: white normally, a festival's accent (diya gold) while a festive skin is on. */
    private int tint = 0xFFFFFF;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 16f * density;
        inset = PAD_DP * density;
        glow.setStyle(Paint.Style.STROKE);
        outerEdge.setStyle(Paint.Style.STROKE);
        outerEdge.setStrokeWidth(1f * density);
        // Cached as a texture: the spring only changes translation, so the GPU moves the frame without
        // replaying the path and gradients on every frame.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Tints the glass; 0 restores plain white. */
    void setAccent(int argb) {
        tint = argb == 0 ? 0xFFFFFF : lighten(argb & 0xFFFFFF);
        rebuildShaders(getHeight());
        invalidate();
    }

    /** Room between the view's edge and the tile; less room means less glow (the app library's shelf is tight). */
    void setInset(float px) {
        inset = px;
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    /** The accent lifted two thirds of the way to white, so it still reads as glass, not paint. */
    private static int lighten(int rgb) {
        int r = (((rgb >> 16) & 0xFF) + 255 * 2) / 3, g = (((rgb >> 8) & 0xFF) + 255 * 2) / 3,
                b = ((rgb & 0xFF) + 255 * 2) / 3;
        return (r << 16) | (g << 8) | b;
    }

    private int c(int alpha) {
        return alpha << 24 | tint;
    }

    private void rebuildShaders(int h) {
        if (h <= 0) return;
        float[] stops = {0f, 0.5f, 1f};
        // The band: strong light at the top, thinner glass at the sides, some light bouncing back at the bottom.
        band.setShader(new LinearGradient(0, 0, 0, h, new int[]{c(0xF2), c(0xB8), c(0xD1)}, stops,
                Shader.TileMode.CLAMP));
        outerEdge.setShader(new LinearGradient(0, 0, 0, h, new int[]{c(0xFF), c(0x99), c(0xCC)}, stops,
                Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, 0, 0, h * 0.4f, c(0x2E), c(0x00), Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildShaders(h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // The view is the zoomed tile plus `inset` on each side: 4dp of glass, then a soft glow fading out over
        // the rest. Every corner is concentric with the tile's: outer radius = tile radius + 4dp.
        float glass = 4f * density, overlap = 1f * density;
        tile.set(inset, inset, getWidth() - inset, getHeight() - inset);
        outer.set(tile.left - glass, tile.top - glass, tile.right + glass, tile.bottom + glass);
        float ro = radius + glass;
        // The band reaches 1dp over the tile's edge, so sub-pixel placement can never open a seam between
        // them; its inner radius shrinks by the same 1dp to stay concentric with the tile's corner.
        inner.set(tile.left + overlap, tile.top + overlap, tile.right - overlap, tile.bottom - overlap);
        float ri = Math.max(0f, radius - overlap);

        // A slight glow under the frame: thin rings, each fainter, out to the view's edge. Drawn once into the
        // hardware layer like the rest, so it costs nothing while the frame moves.
        float room = inset - glass;
        int rings = Math.max(1, Math.round(room / density));
        float step = room / rings;
        glow.setStrokeWidth(step + 0.5f);
        for (int i = 0; i < rings; i++) {
            float o = step * (i + 0.5f);
            float t = 1f - (float) i / rings;
            glow.setColor(Math.round(0x2E * t * t) << 24 | tint);
            canvas.drawRoundRect(outer.left - o, outer.top - o, outer.right + o, outer.bottom + o, ro + o, ro + o, glow);
        }

        // rewind() resets the fill type to WINDING, so set EVEN_ODD again each time; the inner rect also runs the
        // other way, which leaves the tile uncovered under either rule.
        frame.rewind();
        frame.setFillType(Path.FillType.EVEN_ODD);
        frame.addRoundRect(outer, ro, ro, Path.Direction.CW);
        frame.addRoundRect(inner, ri, ri, Path.Direction.CCW);
        canvas.drawPath(frame, band);

        // Sheen over the top 40% of the tile only, so the logo below stays clear.
        canvas.save();
        canvas.clipRect(tile.left, tile.top, tile.right, tile.top + tile.height() * 0.4f);
        canvas.drawRoundRect(tile, radius, radius, sheen);
        canvas.restore();

        float e = outerEdge.getStrokeWidth() / 2f;
        canvas.drawRoundRect(outer.left + e, outer.top + e, outer.right - e, outer.bottom - e, ro - e, ro - e, outerEdge);
    }
}
