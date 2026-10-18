package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A fine glass rim and soft contour halo, sharing the artwork's parent transform.
 * The view has 6 dp of drawing room outside the artwork, including during alpha animation.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final float radius;
    static final int HALO_INSET_DP = 6;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = getResources().getDimension(R.dimen.tile_radius);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(0.8f * density);
        glow.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float top = HALO_INSET_DP * density;
        float bottom = h - top;
        fill.setShader(new LinearGradient(0, top, 0, bottom,
                0x0CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{0x20FFFFFF, 0x04FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.38f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, top, w, bottom,
                new int[]{0xC4FFFFFF, 0x60DCE7F7, 0x8CFFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = HALO_INSET_DP * density + 0.8f * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float innerRadius = radius - 0.8f * density;
        // Broad, faint strokes build a soft halo without an offscreen blur texture.
        glow.setStrokeWidth(9f * density);
        glow.setColor(0x04DCEAFF);
        canvas.drawRoundRect(rect, innerRadius, innerRadius, glow);
        glow.setStrokeWidth(5f * density);
        glow.setColor(0x08DCEAFF);
        canvas.drawRoundRect(rect, innerRadius, innerRadius, glow);
        canvas.drawRoundRect(rect, innerRadius, innerRadius, fill);
        canvas.drawRoundRect(rect, innerRadius, innerRadius, specular);
        canvas.drawRoundRect(rect, innerRadius, innerRadius, rim);
    }
}
