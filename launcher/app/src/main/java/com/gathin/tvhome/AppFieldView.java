package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * What the layouts that replace the grid share (config.xml layout, see {@link HoneycombView} and {@link WordsView}):
 * one focusable view drawing the adapter's apps itself, a focused app the D-pad moves, OK to open it and hold OK
 * for the tile menu or App info, and a camera that slides to the focused app (or jumps with reduced_motion).
 *
 * The tiles still come from {@link AppAdapter}, which loads, orders and launches them as for the grid. Directions
 * are {@link Honeycomb#LEFT} and its siblings.
 */
abstract class AppFieldView extends ViewGroup {

    interface Listener {
        /** The focused app changed, or the field took focus; position is the adapter's. */
        void onFieldFocused(int position);
    }

    final boolean animate;
    final float density;
    /** Laid out over the focused app: the tile menu anchors to it, as to a grid tile. */
    final View anchor;
    AppAdapter adapter;
    /** Adapter position of each app in the field (empty dock slots are left out). */
    int[] positions = new int[0];
    int focus;
    /** The field point drawn at the focus spot, in the subclass's units; the slide runs from and to. */
    float camX, camY;

    private final Listener listener;
    private final Rect anchorBounds = new Rect();
    private final ValueAnimator slide = ValueAnimator.ofFloat(0f, 1f);
    private float fromX, fromY, toX, toY;
    private boolean longPressed;

    AppFieldView(Context context, Listener listener, boolean animate, long slideMs) {
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
        slide.setDuration(slideMs);
        slide.setInterpolator(new DecelerateInterpolator(1.5f));
        slide.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            camX = fromX + (toX - fromX) * t;
            camY = fromY + (toY - fromY) * t;
            invalidate();
        });
    }

    /** The field takes these apps: geometry and caches for {@code count} apps, in {@link #positions} order. */
    abstract void onApps(int count);

    /** The app the D-pad goes to from {@code from}, or -1 when there is none that way. */
    abstract int move(int from, int direction);

    /** Where the camera rests when this app is focused. */
    abstract float targetX(int index);

    abstract float targetY(int index);

    /** Where the anchor sits in a view of this size: over the focused app once the camera has arrived. */
    abstract void anchorBounds(int width, int height, Rect out);

    /** Called after the focus moved (also without a slide): update what is drawn for the focused app. */
    void onFocusMoved() {
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
        onApps(n);
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

    /** The focused app's label, or "" while there is none. */
    String focusedLabel() {
        return adapter == null || positions.length == 0 ? "" : adapter.labelAt(positions[focus]);
    }

    private void focusIndex(int index, boolean glide) {
        focus = positions.length == 0 ? 0 : Math.max(0, Math.min(index, positions.length - 1));
        float tx = positions.length == 0 ? 0f : targetX(focus), ty = positions.length == 0 ? 0f : targetY(focus);
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
        setContentDescription(focusedLabel());
        onFocusMoved();
        invalidate();
    }

    private void notifyFocus() {
        if (positions.length > 0) listener.onFieldFocused(positions[focus]);
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
        int next = move(focus, direction);
        if (next < 0) {
            // Up from the top goes on to the top bar; the other edges keep the focus here.
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
        adapter.longPressAt(positions[focus], anchor);   // the tile menu or App info, anchored over the app
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

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        anchorBounds(w, h, anchorBounds);
        anchor.measure(MeasureSpec.makeMeasureSpec(Math.max(1, anchorBounds.width()), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(1, anchorBounds.height()), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // The focused app always ends up at the same spot, so the anchor sits there whatever the slide is doing.
        anchorBounds(r - l, b - t, anchorBounds);
        anchor.layout(anchorBounds.left, anchorBounds.top, anchorBounds.left + anchor.getMeasuredWidth(),
                anchorBounds.top + anchor.getMeasuredHeight());
    }

    @Override
    protected void onDetachedFromWindow() {
        slide.cancel();
        super.onDetachedFromWindow();
    }
}
