package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A thin rounded level bar, filled part over a faint track, that glides to each new level: the settings sheet's volume. */
public final class LevelBar extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    /** The level as drawn, 0 to 1. */
    private float level;
    private ValueAnimator glide;

    public LevelBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setColors(0xFFFFFFFF, 0x3DFFFFFF);
    }

    void setColors(int fillColor, int trackColor) {
        fill.setColor(fillColor);
        track.setColor(trackColor);
        invalidate();
    }

    void setLevel(float target, boolean animate) {
        target = Math.max(0f, Math.min(1f, target));
        if (glide != null) glide.cancel();
        if (!animate || !isAttachedToWindow()) {
            level = target;
            invalidate();
            return;
        }
        glide = ValueAnimator.ofFloat(level, target);
        glide.setDuration(140);
        glide.setInterpolator(new DecelerateInterpolator());
        glide.addUpdateListener(a -> {
            level = (float) a.getAnimatedValue();
            invalidate();
        });
        glide.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);
        if (level <= 0f) return;
        // Never narrower than it is tall, so the lowest levels still read as a dot.
        rect.set(0, 0, Math.max(h, w * level), h);
        canvas.drawRoundRect(rect, r, r, fill);
    }
}
