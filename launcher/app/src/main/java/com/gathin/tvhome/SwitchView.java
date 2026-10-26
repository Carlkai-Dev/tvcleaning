package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * An on/off switch for the Wi-Fi drop-down (wifi_menu), drawn in the pills' style: a rounded track with their
 * 1.5dp outline and a round knob that slides between the ends with a slight spring. On, the track fills with
 * the given colour. On a focused (white) row the outline and knob go dark so they stay visible.
 */
final class SwitchView extends View {

    private static final float TRACK_W = 44, TRACK_H = 24, KNOB_INSET = 3, STROKE = 1.5f;
    private static final int SLIDE_MS = 260;

    private final float density;
    private final int onColor;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private ValueAnimator slide;
    private boolean checked;
    private boolean onLightRow;
    /** 0 = off (knob left), 1 = on (knob right). */
    private float position;

    SwitchView(Context context, int onColor) {
        super(context);
        this.onColor = onColor;
        density = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(STROKE * density);
        fill.setStyle(Paint.Style.FILL);
        knob.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setChecked(boolean on, boolean animate) {
        checked = on;
        float target = on ? 1f : 0f;
        if (slide != null) slide.cancel();
        if (!animate) {
            position = target;
            invalidate();
            return;
        }
        slide = ValueAnimator.ofFloat(position, target);
        slide.setDuration(SLIDE_MS);
        slide.setInterpolator(new OvershootInterpolator(1.2f));
        slide.addUpdateListener(a -> {
            position = (float) a.getAnimatedValue();
            invalidate();
        });
        slide.start();
    }

    boolean isChecked() {
        return checked;
    }

    /** The row's focus state: focused rows are white, so the off-state outline and knob switch to dark. */
    void setOnLightRow(boolean light) {
        onLightRow = light;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(TRACK_W * density), Math.round(TRACK_H * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float half = STROKE * density / 2;
        track.set(half, half, getWidth() - half, getHeight() - half);
        float r = track.height() / 2;
        // Clamp the overshoot for colour, so the fill never goes past fully on or off.
        float p = Math.max(0f, Math.min(1f, position));

        int offOutline = onLightRow ? 0x66000000 : 0x73FFFFFF;
        fill.setColor(onColor);
        fill.setAlpha(Math.round(255 * p));
        canvas.drawRoundRect(track, r, r, fill);
        stroke.setColor(blend(offOutline, onColor, p));
        canvas.drawRoundRect(track, r, r, stroke);

        float inset = KNOB_INSET * density;
        float knobR = getHeight() / 2f - inset;
        float left = inset + knobR, right = getWidth() - inset - knobR;
        float cx = left + (right - left) * position;
        knob.setColor(p > 0.5f || !onLightRow ? 0xFFFFFFFF : 0xCC1A1A1A);
        canvas.drawCircle(cx, getHeight() / 2f, knobR, knob);
    }

    private static int blend(int a, int b, float t) {
        int aA = a >>> 24, aR = (a >> 16) & 0xFF, aG = (a >> 8) & 0xFF, aB = a & 0xFF;
        int bA = b >>> 24, bR = (b >> 16) & 0xFF, bG = (b >> 8) & 0xFF, bB = b & 0xFF;
        return (Math.round(aA + (bA - aA) * t) << 24) | (Math.round(aR + (bR - aR) * t) << 16)
                | (Math.round(aG + (bG - aG) * t) << 8) | Math.round(aB + (bB - aB) * t);
    }
}
