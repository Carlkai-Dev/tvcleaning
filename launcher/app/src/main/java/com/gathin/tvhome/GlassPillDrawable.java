package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * pill_bg as frosted glass. Unfocused, the pill shows the blurred wallpaper behind it with a
 * light frost, a sheen along the top and a bright rim (see {@link Backdrop}); focused it is the same solid white
 * as pill_bg so the remote's position stays obvious, and the reorder button's activated look is kept. Without a
 * wallpaper it draws pill_bg's plain translucent gradient.
 */
final class GlassPillDrawable extends Drawable {

    private final View host;
    private final Backdrop backdrop;
    private final Backdrop.Painter painter = new Backdrop.Painter();
    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float sheenHeight = -1;
    private boolean focused, activated;

    GlassPillDrawable(View host, Backdrop backdrop, float density) {
        this.host = host;
        this.backdrop = backdrop;
        radius = 21 * density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        backdrop.register(host);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        rect.set(getBounds());
        if (focused) {
            fill.setShader(null);
            fill.setColor(0xF5FFFFFF);
            c.drawRoundRect(rect, radius, radius, fill);
            return;
        }
        if (activated) {
            fill.setShader(null);
            fill.setColor(0x596EC1FF);
            c.drawRoundRect(rect, radius, radius, fill);
            stroke.setStrokeWidth(1.5f * host.getResources().getDisplayMetrics().density);
            stroke.setColor(0xFF6EC1FF);
            c.drawRoundRect(rect, radius, radius, stroke);
            return;
        }
        rect.inset(0.5f, 0.5f);
        if (painter.prepare(backdrop, host)) {
            c.drawRoundRect(rect, radius, radius, painter.paint);
            fill.setShader(null);
            fill.setColor(0x29FFFFFF);
            c.drawRoundRect(rect, radius, radius, fill);
            if (rect.height() != sheenHeight) {
                sheenHeight = rect.height();
                sheen.setShader(new LinearGradient(0, rect.top, 0, rect.top + sheenHeight * 0.55f,
                        0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(rect, radius, radius, sheen);
            stroke.setColor(0x59FFFFFF);
        } else {
            // pill_bg's resting look: a faint white gradient and rim.
            fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x2EFFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius, radius, fill);
            stroke.setColor(0x38FFFFFF);
        }
        stroke.setStrokeWidth(host.getResources().getDisplayMetrics().density);
        c.drawRoundRect(rect, radius, radius, stroke);
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean f = false, a = false;
        for (int s : state) {
            if (s == android.R.attr.state_focused) f = true;
            else if (s == android.R.attr.state_activated) a = true;
        }
        if (f == focused && a == activated) return false;
        focused = f;
        activated = a;
        invalidateSelf();
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
