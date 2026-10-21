package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A weather symbol drawn in code: yellow sun, pale moon, white clouds, blue rain. No image files. */
public final class WeatherIcon extends View {

    private static final int SUN = 0xFFFFD60A;
    private static final int MOON = 0xFFE5E5EA;
    private static final int CLOUD = 0xFFF2F2F7;
    private static final int STORM_CLOUD = 0xFFAEAEB2;
    private static final int MIST = 0xFFC7C7CC;
    private static final int RAIN = 0xFF64D2FF;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Weather.Kind kind = Weather.Kind.CLEAR;
    private boolean day = true;

    public WeatherIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    void setWeather(Weather.Kind kind, boolean day) {
        this.kind = kind;
        this.day = day;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f);
        switch (kind) {
            case CLEAR:
                if (day) sun(canvas, s * 0.5f, s * 0.5f, s * 0.22f);
                else moon(canvas, s * 0.5f, s * 0.5f, s * 0.3f);
                break;
            case PARTLY_CLOUDY:
                if (day) sun(canvas, s * 0.36f, s * 0.36f, s * 0.15f);
                else moon(canvas, s * 0.38f, s * 0.36f, s * 0.2f);
                cloud(canvas, s * 0.56f, s * 0.58f, s * 0.66f, CLOUD);
                break;
            case FOG:
                cloud(canvas, s * 0.5f, s * 0.38f, s * 0.72f, MIST);
                line(canvas, s, 0.18f, 0.82f, 0.76f);
                line(canvas, s, 0.28f, 0.72f, 0.9f);
                break;
            case RAIN:
                cloud(canvas, s * 0.5f, s * 0.4f, s * 0.78f, CLOUD);
                stroke.setColor(RAIN);
                stroke.setStrokeWidth(s * 0.055f);
                for (float x : new float[]{0.34f, 0.5f, 0.66f}) {
                    canvas.drawLine(x * s, 0.74f * s, (x - 0.05f) * s, 0.88f * s, stroke);
                }
                break;
            case SNOW:
                cloud(canvas, s * 0.5f, s * 0.4f, s * 0.78f, CLOUD);
                fill.setColor(0xFFFFFFFF);
                float[][] flakes = {{0.34f, 0.78f}, {0.5f, 0.84f}, {0.66f, 0.78f}, {0.42f, 0.93f}, {0.58f, 0.93f}};
                for (float[] f : flakes) canvas.drawCircle(f[0] * s, f[1] * s, s * 0.04f, fill);
                break;
            case STORM:
                cloud(canvas, s * 0.5f, s * 0.38f, s * 0.8f, STORM_CLOUD);
                Path bolt = new Path();
                bolt.moveTo(0.54f * s, 0.6f * s);
                bolt.lineTo(0.4f * s, 0.8f * s);
                bolt.lineTo(0.5f * s, 0.8f * s);
                bolt.lineTo(0.44f * s, 0.97f * s);
                bolt.lineTo(0.63f * s, 0.72f * s);
                bolt.lineTo(0.53f * s, 0.72f * s);
                bolt.close();
                fill.setColor(SUN);
                canvas.drawPath(bolt, fill);
                break;
            default:
                cloud(canvas, s * 0.5f, s * 0.52f, s * 0.84f, CLOUD);
                break;
        }
        canvas.restore();
    }

    private void sun(Canvas canvas, float cx, float cy, float r) {
        fill.setColor(SUN);
        canvas.drawCircle(cx, cy, r, fill);
        stroke.setColor(SUN);
        stroke.setStrokeWidth(r * 0.2f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            canvas.drawLine(cx + dx * r * 1.4f, cy + dy * r * 1.4f, cx + dx * r * 1.75f, cy + dy * r * 1.75f, stroke);
        }
    }

    /** A crescent: a disc with an offset disc cut out of it. */
    private void moon(Canvas canvas, float cx, float cy, float r) {
        Path disc = new Path();
        disc.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        disc.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(MOON);
        canvas.drawPath(disc, fill);
    }

    /** A cloud {@code w} wide: a rounded base with two bumps, roughly centred on (cx, cy). */
    private void cloud(Canvas canvas, float cx, float cy, float w, int color) {
        float h = w * 0.34f;
        fill.setColor(color);
        rect.set(cx - w / 2f, cy, cx + w / 2f, cy + h);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, fill);
        canvas.drawCircle(cx - w * 0.18f, cy + h * 0.05f, w * 0.21f, fill);
        canvas.drawCircle(cx + w * 0.1f, cy - h * 0.25f, w * 0.29f, fill);
    }

    private void line(Canvas canvas, float s, float x1, float x2, float y) {
        stroke.setColor(MIST);
        stroke.setStrokeWidth(s * 0.06f);
        canvas.drawLine(x1 * s, y * s, x2 * s, y * s, stroke);
    }
}
