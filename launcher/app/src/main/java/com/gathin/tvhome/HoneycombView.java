package com.gathin.tvhome;

import android.animation.ValueAnimator;
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
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * Home as a honeycomb (config.xml layout "honeycomb"): the apps as round icons in a hex-packed field, the focused
 * one large in the middle and the others shrinking with their distance from it, like a fisheye. The field slides
 * so the focused icon stays in the middle; reduced_motion (or animations off) makes it jump. {@link Honeycomb} has
 * the geometry and the D-pad rules; this view only draws them and takes the keys.
 *
 * The tiles still come from {@link AppAdapter}, which loads, orders and launches them as for the grid; this view
 * keeps one small circular bitmap per app, made the first time it is drawn, and draws it through a BitmapShader,
 * so a frame is one circle per visible icon with no blur and nothing allocated.
 */
final class HoneycombView extends ViewGroup {

    interface Listener {
        /** The focused app changed, or the field took focus; position is the adapter's. */
        void onHoneycombFocused(int position);
    }

    /** Neighbours this far apart, and an icon this wide before the fisheye, in screen (view) widths. */
    private static final float SPACING = 0.13f, DIAMETER = 0.10f;
    /** The largest bitmap kept per icon, in pixels: the middle icon on a 1080p screen is about 230. */
    private static final int MAX_BITMAP = 256;
    /** A legacy (non-adaptive) icon covers this share of its coloured circle. */
    private static final float LEGACY_INSET = 0.62f;

    private final Listener listener;
    private final boolean animate;
    private final float density;
    /** Laid out over the focused icon: the tile menu anchors to it, as to a grid tile. */
    private final View anchor;
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();
    private final ValueAnimator slide = ValueAnimator.ofFloat(0f, 1f);

    private AppAdapter adapter;
    private Honeycomb field = new Honeycomb(0);
    /** Adapter position of each app in the field (empty dock slots are left out). */
    private int[] positions = new int[0];
    private Paint[] shaders = new Paint[0];
    private int focus;
    /** The field point drawn in the middle of the view, in cells, and where the slide runs from and to. */
    private float camX, camY, fromX, fromY, toX, toY;
    /** Bitmap size the shaders were made at; they are made again when the view's size changes it. */
    private int bitmapSize;
    private String name = "";
    private CharSequence shownName = "";
    private boolean longPressed;

    HoneycombView(Context context, Listener listener, boolean animate) {
        super(context);
        this.listener = listener;
        this.animate = animate;
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setFocusable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        anchor = new View(context);
        anchor.setFocusable(false);
        addView(anchor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.WHITE);
        ringPaint.setStrokeWidth(3f * density);
        namePaint.setColor(Color.WHITE);
        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f, getResources().getDisplayMetrics()));
        namePaint.setTypeface(UiFont.get(context, R.string.font_light));
        namePaint.setShadowLayer(4f * density, 0f, density, 0xCC000000);
        slide.setDuration(320);
        slide.setInterpolator(new DecelerateInterpolator(1.5f));
        slide.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            camX = fromX + (toX - fromX) * t;
            camY = fromY + (toY - fromY) * t;
            invalidate();
        });
    }

    /** Takes the adapter's tiles again (after a reload or clear), keeping the focus on the same app when it is still there. */
    void setApps(AppAdapter source) {
        String focusedKey = adapter != null && focus < positions.length ? adapter.keyAt(positions[focus]) : null;
        adapter = source;
        int total = source.appCount(), n = 0;
        int[] keep = new int[total];
        for (int p = 0; p < total; p++) if (!source.isEmptyAt(p)) keep[n++] = p;
        positions = new int[n];
        System.arraycopy(keep, 0, positions, 0, n);
        field = new Honeycomb(n);
        // Dropped, not recycled: the last frame drawn may still use them. They are made again as they are drawn.
        shaders = new Paint[n];
        int pos = focusedKey == null ? -1 : source.positionOf(focusedKey);
        focusIndex(pos >= 0 ? indexOf(pos) : 0, false);
    }

    /** Puts the focus on the app at this adapter position (or the next app after an empty slot), without sliding. */
    void focusPosition(int position) {
        focusIndex(Math.max(0, indexOf(position)), false);
        if (hasFocus()) notifyFocus();
    }

    /** Index in the field of the first app at or after an adapter position; the last app past the end, -1 for none. */
    private int indexOf(int position) {
        for (int i = 0; i < positions.length; i++) if (positions[i] >= position) return i;
        return positions.length - 1;
    }

    private void focusIndex(int index, boolean glide) {
        focus = positions.length == 0 ? 0 : Math.max(0, Math.min(index, positions.length - 1));
        float tx = field.count == 0 ? 0f : field.x[focus], ty = field.count == 0 ? 0f : field.y[focus];
        slide.cancel();
        if (glide && animate && isLaidOut()) {
            fromX = camX;
            fromY = camY;
            toX = tx;
            toY = ty;
            slide.start();
        } else {
            camX = toX = tx;
            camY = toY = ty;
        }
        name = adapter == null || positions.length == 0 ? "" : adapter.labelAt(positions[focus]);
        setContentDescription(name);
        fitName();
        invalidate();
    }

    /** The name as drawn: cut with an ellipsis when it is wider than most of the screen. Not per frame: it allocates. */
    private void fitName() {
        float max = getWidth() * 0.8f;
        shownName = max > 0 && namePaint.measureText(name) > max ? TextUtils.ellipsize(name, namePaint, max, TextUtils.TruncateAt.END) : name;
    }

    private void notifyFocus() {
        if (positions.length > 0) listener.onHoneycombFocused(positions[focus]);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        if (gained) notifyFocus();
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = Honeycomb.LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = Honeycomb.RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP: direction = Honeycomb.UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = Honeycomb.DOWN; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() == 0) {
                    longPressed = false;
                    event.startTracking();   // onKeyLongPress, or a click on the key's release
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
        int next = field.move(focus, direction);
        if (next < 0) {
            // Up from the top row goes on to the top bar; the other edges keep the focus here.
            return direction != Honeycomb.UP;
        }
        focusIndex(next, true);
        notifyFocus();
        return true;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (positions.length == 0 || !isOk(keyCode)) return super.onKeyLongPress(keyCode, event);
        longPressed = true;
        adapter.longPressAt(positions[focus], anchor);   // the tile menu or App info, anchored over the icon
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!isOk(keyCode)) return super.onKeyUp(keyCode, event);
        if (event.isTracking() && !event.isCanceled() && !longPressed && positions.length > 0) {
            adapter.launchAt(positions[focus]);
        }
        longPressed = false;
        return true;
    }

    private static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
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
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        int side = Math.max(1, Math.round(getMeasuredWidth() * DIAMETER * Honeycomb.CENTRE_SCALE));
        anchor.measure(MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // The focused icon always ends up in the middle, so the anchor sits there whatever the slide is doing.
        int side = anchor.getMeasuredWidth();
        int cx = (r - l) / 2, cy = Math.round(((b - t) - nameRoom()) / 2f);
        anchor.layout(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
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

    @Override
    protected void onDetachedFromWindow() {
        slide.cancel();
        super.onDetachedFromWindow();
    }
}
