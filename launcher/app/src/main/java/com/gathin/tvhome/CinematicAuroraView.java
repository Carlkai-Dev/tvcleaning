package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CinematicAuroraView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean active = true;

    private static class Orb {
        float cx, cy;
        float radius;
        int color;
        float speedX, speedY;
        float phase;

        Orb(float cx, float cy, float radius, int color, float speedX, float speedY, float phase) {
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.color = color;
            this.speedX = speedX;
            this.speedY = speedY;
            this.phase = phase;
        }
    }

    private final Orb[] orbs = new Orb[]{
            new Orb(0.2f, 0.3f, 450f, 0x301E3C72, 0.0004f, 0.0003f, 0f),
            new Orb(0.8f, 0.2f, 520f, 0x282A5298, 0.0003f, 0.0005f, 1.5f),
            new Orb(0.5f, 0.7f, 480f, 0x220B486B, 0.0005f, 0.0002f, 3.0f),
            new Orb(0.15f, 0.8f, 400f, 0x253B0066, 0.0002f, 0.0004f, 4.5f),
            new Orb(0.85f, 0.85f, 440f, 0x22134E5E, 0.0004f, 0.0003f, 2.2f)
    };

    public CinematicAuroraView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        long time = SystemClock.uptimeMillis();

        for (Orb orb : orbs) {
            float curX = (float) (orb.cx + 0.12f * Math.sin(time * orb.speedX + orb.phase)) * w;
            float curY = (float) (orb.cy + 0.10f * Math.cos(time * orb.speedY + orb.phase)) * h;

            paint.setShader(new RadialGradient(
                    curX, curY, orb.radius,
                    new int[]{orb.color, 0x00000000},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));

            canvas.drawCircle(curX, curY, orb.radius, paint);
        }

        if (active && getVisibility() == VISIBLE) {
            postInvalidateDelayed(40); // smooth ~25 FPS with virtually 0% CPU impact
        }
    }
}
