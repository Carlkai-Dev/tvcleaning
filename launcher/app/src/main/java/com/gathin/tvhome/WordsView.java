package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

/**
 * Home as "Big words" (layout "words"): no icons, only the app names in large type down the left, the focused one
 * at full size and the others smaller and fading with their distance from it, with the focused app's colour
 * washing in from the right. Up and Down move; the column slides so the focused name stays at the same height
 * (reduced_motion: it jumps). {@link Words} has the rules.
 *
 * Meant for weak TVs: text and one gradient. The names are cut to fit once per reload, so a frame allocates
 * nothing; the gradient is made again only when the focus moves. The two text sizes never animate, so the glyph
 * cache holds two sizes and no more.
 */
final class WordsView extends AppFieldView {

    /** The names start this far from the left, may take this much of the width, and the focused one sits this low. */
    private static final float LEFT = 0.07f, MAX_WIDTH = 0.5f, FOCUS_Y = 0.45f;
    /** The colour wash covers the screen from here to the right edge, at this alpha. */
    private static final float WASH_FROM = 0.45f;
    private static final int WASH_ALPHA = 0x59;

    private final TextPaint bigPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint smallPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint washPaint = new Paint();
    /** Baseline to baseline, in pixels. */
    private final float pitch;
    private CharSequence[] bigNames = new CharSequence[0], smallNames = new CharSequence[0];
    private int washColor;

    WordsView(Context context, Listener listener, boolean animate) {
        super(context, listener, animate, 220);
        float sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, getResources().getDisplayMetrics());
        for (TextPaint p : new TextPaint[]{bigPaint, smallPaint}) {
            p.setColor(Color.WHITE);
            p.setTypeface(UiFont.get(context, R.string.font_medium));
        }
        bigPaint.setTextSize(64f * sp);
        smallPaint.setTextSize(32f * sp);
        pitch = 60f * sp;
    }

    @Override
    void onApps(int count) {
        fitNames();
    }

    /** Every name cut with an ellipsis to MAX_WIDTH, at both sizes. Allocates: once per reload or resize. */
    private void fitNames() {
        int n = positions.length;
        bigNames = new CharSequence[n];
        smallNames = new CharSequence[n];
        float max = getWidth() * MAX_WIDTH;
        for (int i = 0; i < n; i++) {
            String name = adapter.labelAt(positions[i]);
            bigNames[i] = fit(name, bigPaint, max);
            smallNames[i] = fit(name, smallPaint, max);
        }
    }

    private static CharSequence fit(String name, TextPaint paint, float max) {
        return max > 0 && paint.measureText(name) > max ? TextUtils.ellipsize(name, paint, max, TextUtils.TruncateAt.END) : name;
    }

    @Override
    int move(int from, int direction) {
        return Words.move(from, direction, positions.length);
    }

    @Override
    float targetX(int index) {
        return 0f;
    }

    @Override
    float targetY(int index) {
        return index;
    }

    @Override
    void anchorBounds(int width, int height, Rect out) {
        float size = bigPaint.getTextSize();
        int left = Math.round(width * LEFT), base = Math.round(height * FOCUS_Y);
        out.set(left, Math.round(base - size), left + Math.round(width * MAX_WIDTH), Math.round(base + size * 0.3f));
    }

    @Override
    void onFocusMoved() {
        int color = positions.length == 0 ? 0 : adapter.ambientColorAt(positions[focus]);
        if (color != washColor || washPaint.getShader() == null) {
            washColor = color;
            updateWash(getWidth());
        }
    }

    private void updateWash(int w) {
        if (w == 0) return;
        int end = (washColor & 0x00FFFFFF) | (WASH_ALPHA << 24);
        washPaint.setShader(new LinearGradient(w * WASH_FROM, 0, w, 0, washColor & 0x00FFFFFF, end, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (adapter != null) fitNames();
        updateWash(w);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        int n = Math.min(positions.length, bigNames.length);
        if (w == 0 || n == 0) return;
        if (washPaint.getShader() != null) canvas.drawRect(w * WASH_FROM, 0, w, h, washPaint);
        float x = w * LEFT, focusY = h * FOCUS_Y;
        boolean focused = hasFocus();
        for (int i = 0; i < n; i++) {
            float rows = i - camY;
            float y = focusY + rows * pitch;
            if (y < -pitch || y > h + pitch) continue;
            boolean big = i == focus;
            TextPaint paint = big ? bigPaint : smallPaint;
            // Off the field (focus on the top bar), the focused name dims too, so it doesn't look selected.
            float a = Words.alpha(rows) * (big && !focused ? 0.6f : 1f);
            paint.setAlpha(Math.round(a * 255));
            CharSequence name = big ? bigNames[i] : smallNames[i];
            canvas.drawText(name, 0, name.length(), x, y, paint);
        }
    }
}
