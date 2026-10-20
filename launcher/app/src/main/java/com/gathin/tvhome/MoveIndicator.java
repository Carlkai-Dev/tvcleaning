package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small glass direction handles around the lifted app. Never paints over its artwork. */
final class MoveIndicator extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final RectF tile = new RectF();
    private final float density;
    /** The grid runs right to left (Hebrew): the handle on the left points one place on in the order. */
    private final boolean rtl;
    private int position, count, columns;

    MoveIndicator(Context context, boolean rtl) {
        super(context);
        this.rtl = rtl;
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
    }

    void follow(float cx, float cy, float width, float height, int position, int count, int columns) {
        tile.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2);
        this.position = position; this.count = count; this.columns = columns;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        handle(canvas, tile.left - 10 * density, tile.centerY(), -90, rtl ? AppOrder.Direction.RIGHT : AppOrder.Direction.LEFT);
        handle(canvas, tile.right + 10 * density, tile.centerY(), 90, rtl ? AppOrder.Direction.LEFT : AppOrder.Direction.RIGHT);
        handle(canvas, tile.centerX(), tile.top - 10 * density, 0, AppOrder.Direction.UP);
        handle(canvas, tile.centerX(), tile.bottom + 10 * density, 180, AppOrder.Direction.DOWN);
    }

    private void handle(Canvas canvas, float x, float y, float rotation, AppOrder.Direction direction) {
        if (AppOrder.target(position, count, columns, direction) == position) return;
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        float r = 8 * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDD41454D);
        canvas.drawCircle(0, 0, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.6f * density);
        paint.setColor(0x90FFFFFF);
        canvas.drawCircle(0, 0, r, paint);
        paint.setStrokeWidth(1.5f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFFFFFFF);
        arrow.reset();
        arrow.moveTo(-2.6f * density, 1.3f * density);
        arrow.lineTo(0, -1.3f * density);
        arrow.lineTo(2.6f * density, 1.3f * density);
        canvas.drawPath(arrow, paint);
        canvas.restoreToCount(save);
    }
}
