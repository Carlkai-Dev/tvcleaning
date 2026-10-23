package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

/**
 * Home as a honeycomb (layout "honeycomb"): the apps as round icons in a hex-packed field, the focused one large in
 * the middle and the others shrinking with their distance from it, like a fisheye. The field slides so the focused
 * icon stays in the middle; reduced_motion (or animations off) makes it jump. {@link Honeycomb} has the geometry
 * and the D-pad rules; this view only draws them.
 *
 * It keeps one small circular bitmap per app, made the first time it is drawn, and draws it through a BitmapShader,
 * so a frame is one circle per visible icon with no blur and nothing allocated.
 */
final class HoneycombView extends AppFieldView {

    /** Neighbours this far apart, and an icon this wide before the fisheye, in screen (view) widths. */
    private static final float SPACING = 0.13f, DIAMETER = 0.10f;
    /** The largest bitmap kept per icon, in pixels: the middle icon on a 1080p screen is about 230. */
    private static final int MAX_BITMAP = 256;
    /** A legacy (non-adaptive) icon covers this share of its coloured circle. */
    private static final float LEGACY_INSET = 0.62f;

    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();

    private Honeycomb field = new Honeycomb(0);
    private Paint[] shaders = new Paint[0];
    /** Bitmap size the shaders were made at; they are made again when the view's size changes it. */
    private int bitmapSize;
    private CharSequence shownName = "";

    HoneycombView(Context context, Listener listener, boolean animate) {
        super(context, listener, animate, 320);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.WHITE);
        ringPaint.setStrokeWidth(3f * density);
        namePaint.setColor(Color.WHITE);
        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f, getResources().getDisplayMetrics()));
        namePaint.setTypeface(UiFont.get(context, R.string.font_light));
        namePaint.setShadowLayer(4f * density, 0f, density, 0xCC000000);
    }

    @Override
    void onApps(int count) {
        field = new Honeycomb(count);
        // Dropped, not recycled: the last frame drawn may still use them. They are made again as they are drawn.
        shaders = new Paint[count];
    }

    @Override
    int move(int from, int direction) {
        return field.move(from, direction);
    }

    @Override
    float targetX(int index) {
        return field.x[index];
    }

    @Override
    float targetY(int index) {
        return field.y[index];
    }

    @Override
    void anchorBounds(int width, int height, Rect out) {
        int side = Math.max(1, Math.round(width * DIAMETER * Honeycomb.CENTRE_SCALE));
        int cx = width / 2, cy = Math.round((height - nameRoom()) / 2f);
        out.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
    }

    @Override
    void onFocusMoved() {
        fitName();
    }

    /** The name as drawn: cut with an ellipsis when it is wider than most of the screen. Not per frame: it allocates. */
    private void fitName() {
        String name = focusedLabel();
        float max = getWidth() * 0.8f;
        shownName = max > 0 && namePaint.measureText(name) > max ? TextUtils.ellipsize(name, namePaint, max, TextUtils.TruncateAt.END) : name;
    }

    /** The middle of the field: centred across, and above the name line at the bottom. */
    private float centreY() {
        return (getHeight() - nameRoom()) / 2f;
    }

    private float nameRoom() {
        return namePaint.getTextSize() * 2.2f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int size = Math.min(MAX_BITMAP, Math.round(w * DIAMETER * Honeycomb.CENTRE_SCALE));
        if (size != bitmapSize) {
            bitmapSize = size;
            shaders = new Paint[positions.length];
        }
        fitName();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        if (w == 0 || field.count == 0) return;
        float spacing = w * SPACING, base = w * DIAMETER;
        float cx = w / 2f, cy = centreY();
        int h = getHeight();
        for (int i = 0; i < field.count; i++) {
            float dx = (field.x[i] - camX) * spacing, dy = (field.y[i] - camY) * spacing;
            float d = Honeycomb.distance(dx / w, dy / w);
            float a = Honeycomb.alpha(d);
            if (a <= 0f) continue;
            float radius = base * Honeycomb.scale(d) / 2f;
            float px = cx + dx, py = cy + dy;
            if (px + radius < 0 || px - radius > w || py + radius < 0 || py - radius > h) continue;
            Paint paint = shader(i);
            paint.setAlpha(Math.round(a * 255));
            // The shader is in bitmap pixels: scale the canvas to the icon's size and draw the bitmap's circle.
            float k = 2f * radius / bitmapSize;
            canvas.save();
            canvas.translate(px - radius, py - radius);
            canvas.scale(k, k);
            canvas.drawCircle(bitmapSize / 2f, bitmapSize / 2f, bitmapSize / 2f, paint);
            canvas.restore();
            if (i == focus && hasFocus()) canvas.drawCircle(px, py, radius + ringPaint.getStrokeWidth(), ringPaint);
        }
        if (shownName.length() > 0) canvas.drawText(shownName, 0, shownName.length(), cx, h - nameRoom() * 0.35f, namePaint);
    }

    /** The icon's paint, its shader made from a circular-to-be bitmap the first time it is drawn. */
    private Paint shader(int i) {
        Paint p = shaders[i];
        if (p != null) return p;
        int size = Math.max(1, bitmapSize);
        int pos = positions[i];
        // RGB_565, as for the wallpaper: the shader's circle is the mask, so the square's corners are never seen.
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        c.drawColor(adapter.ambientColorAt(pos));
        Drawable icon = adapter.iconAt(pos);
        if (icon != null) {
            // A private copy: changing the bounds of the adapter's drawable could disturb anything else showing it.
            Drawable d = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
            if (d instanceof AdaptiveIconDrawable) {
                // The layers are 108 units with the visible 72 in the middle; the circle is the visible square's.
                AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) d;
                int extra = size / 4;
                bounds.set(-extra, -extra, size + extra, size + extra);
                for (Drawable layer : new Drawable[]{adaptive.getBackground(), adaptive.getForeground()}) {
                    if (layer == null) continue;
                    layer.setBounds(bounds);
                    layer.draw(c);
                }
            } else {
                int inset = Math.round(size * (1f - LEGACY_INSET) / 2f);
                d.setBounds(inset, inset, size - inset, size - inset);
                d.draw(c);
            }
        }
        p = new Paint(iconPaint);
        p.setShader(new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        shaders[i] = p;
        return p;
    }
}
