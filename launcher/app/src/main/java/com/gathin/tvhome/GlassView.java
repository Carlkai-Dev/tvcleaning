package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The focus highlight that floats over the grid and is moved with spring animations between tiles:
 * a light streak that sweeps across the tile on focus and, unless switched off (config glass_frame), a
 * translucent glass slab with a bright top specular, a crisp rim and a faint outer glow. Its
 * {@link ShadowView}, behind the grid, moves with it.
 */
public class GlassView extends View {

    /** Space around the tile inside this view (dp), room for the glow and the shadow below it. */
    static final float INSET_DP = 28f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sheenMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float density;
    private float radius;
    private boolean frame = true;
    private View follower;
    private float sheenProgress = -1f;   // -1: no sweep running
    private ValueAnimator sweep;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
    }

    void setRadius(float px) {
        if (px == radius) return;
        radius = px;
        invalidate();
    }

    /** A view kept at this view's position: the shadow, which lives behind the grid. */
    void setFollower(View v) {
        follower = v;
    }

    // Every move goes through these (the springs, the scroll listener, the first placement), so the
    // shadow follows without the callers knowing about it.
    @Override
    public void setTranslationX(float x) {
        super.setTranslationX(x);
        if (follower != null) follower.setTranslationX(x);
    }

    @Override
    public void setTranslationY(float y) {
        super.setTranslationY(y);
        if (follower != null) follower.setTranslationY(y);
    }

    /** False hides the glass slab (fill, specular, rim, glow); the shadow and the sheen stay. */
    void setFrame(boolean on) {
        frame = on;
        invalidate();
    }

    /** One light streak across the tile, left to right. */
    void sweep() {
        if (sweep == null) {
            sweep = ValueAnimator.ofFloat(0f, 1f);
            sweep.setDuration(650);
            sweep.setStartDelay(90);   // let the glass land first
            sweep.setInterpolator(new DecelerateInterpolator(1.2f));
            sweep.addUpdateListener(a -> {
                sheenProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            sweep.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator a) {
                    sheenProgress = -1f;
                    invalidate();
                }
            });
        }
        sweep.cancel();
        sweep.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = INSET_DP * density;
        float top = inset, bottom = h - inset, tileW = w - 2 * inset;
        fill.setShader(new LinearGradient(0, top, 0, bottom, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, top, 0, top + (bottom - top) * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        // A diagonal band about a third of the tile wide; moved across with a local matrix, never rebuilt.
        float band = Math.max(1f, tileW * 0.35f);
        sheen.setShader(new LinearGradient(0, 0, band, band * 0.35f,
                new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = INSET_DP * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);

        if (frame) {
            // Outer glow: three widening, fading strokes.
            for (int i = 1; i <= 3; i++) {
                glow.setStrokeWidth(i * 2f * density);
                glow.setColor((0x30 / i) << 24 | 0xFFFFFF);
                float o = i * density;
                canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
            }
            canvas.drawRoundRect(rect, radius, radius, fill);
        }

        if (frame || sheenProgress >= 0f) {
            canvas.save();
            canvas.clipPath(clip);
            if (frame) canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f, specular);
            if (sheenProgress >= 0f) {
                float band = rect.width() * 0.35f;
                float x = rect.left - band + sheenProgress * (rect.width() + band);
                sheenMatrix.setTranslate(x, rect.top);
                sheen.getShader().setLocalMatrix(sheenMatrix);
                canvas.drawRect(rect, sheen);
            }
            canvas.restore();
        }

        if (frame) canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
