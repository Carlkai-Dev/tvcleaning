package com.patilweb.streambrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;

/**
 * On-screen arrow pointer for Android TV: D-pad moves it, OK injects a tap into the WebView.
 */
public class TvCursorView extends View {

    private static final float STEP_DP = 28f;
    private static final float FAST_DP = 56f;

    private WebView webView;
    private Drawable arrow;
    private float cursorX;
    private float cursorY;
    private int arrowW;
    private int arrowH;
    private boolean placed;

    public TvCursorView(Context context) {
        super(context);
        init();
    }

    public TvCursorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setWillNotDraw(false);
        arrow = getResources().getDrawable(R.drawable.cursor_arrow, getContext().getTheme());
        float d = getResources().getDisplayMetrics().density;
        arrowW = Math.round(36 * d);
        arrowH = Math.round(36 * d);
        if (arrow != null) arrow.setBounds(0, 0, arrowW, arrowH);
    }

    public void attach(WebView web) {
        this.webView = web;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!placed && w > 0 && h > 0) {
            cursorX = w * 0.5f;
            cursorY = h * 0.45f;
            placed = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (arrow == null) return;
        canvas.save();
        canvas.translate(cursorX, cursorY);
        arrow.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        float d = getResources().getDisplayMetrics().density;
        boolean repeat = event.getRepeatCount() > 0;
        float step = (repeat ? FAST_DP : STEP_DP) * d;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-step, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(step, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                move(0, -step);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(0, step);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                clickAtCursor();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void move(float dx, float dy) {
        float maxX = Math.max(0, getWidth() - arrowW);
        float maxY = Math.max(0, getHeight() - arrowH);
        float nextX = clamp(cursorX + dx, 0, maxX);
        float nextY = clamp(cursorY + dy, 0, maxY);

        // Edge scroll the page when the pointer hits the border.
        if (webView != null) {
            int scroll = Math.round(48 * getResources().getDisplayMetrics().density);
            if (dx < 0 && cursorX <= 1) webView.scrollBy(-scroll, 0);
            if (dx > 0 && cursorX >= maxX - 1) webView.scrollBy(scroll, 0);
            if (dy < 0 && cursorY <= 1) webView.scrollBy(0, -scroll);
            if (dy > 0 && cursorY >= maxY - 1) webView.scrollBy(0, scroll);
        }

        cursorX = nextX;
        cursorY = nextY;
        invalidate();
    }

    private void clickAtCursor() {
        if (webView == null) return;
        // Tip of the arrow is near top-left of the drawable.
        float tipX = cursorX + arrowW * 0.15f;
        float tipY = cursorY + arrowH * 0.12f;
        long now = System.currentTimeMillis();
        int meta = 0;
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, tipX, tipY, meta);
        MotionEvent up = MotionEvent.obtain(now, now + ViewConfiguration.getTapTimeout() / 2,
                MotionEvent.ACTION_UP, tipX, tipY, meta);
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        // Brief flash feedback
        animate().alpha(0.45f).setDuration(60).withEndAction(() ->
                animate().alpha(1f).setDuration(80).start()).start();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
