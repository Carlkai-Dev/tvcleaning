package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import java.util.Random;

/**
 * Screensaver: slow aurora scenes that crossfade, each with a gentle pan and zoom, under the launcher's
 * thin clock and date. The clock drifts to a new spot every minute and the scenes never sit still, so
 * nothing burns in. Four small bundled JPGs, no network, no blur, no video: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;
    private static final long SCENE_MS = 16_000L;   // time between scene changes
    private static final long FADE_MS = 3_500L;
    private static final int[] SCENES = {R.drawable.dream_1, R.drawable.dream_2, R.drawable.dream_3, R.drawable.dream_4};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private ImageView front, back;
    private int sceneIndex;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    private final Runnable sceneTick = new Runnable() {
        @Override
        public void run() {
            crossfade();
            handler.postDelayed(this, SCENE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        front = findViewById(R.id.sceneA);
        back = findViewById(R.id.sceneB);
        sceneIndex = random.nextInt(SCENES.length);
        show(front, SCENES[sceneIndex]);
        front.setAlpha(1f);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.postDelayed(clockTick, MOVE_MS);
        handler.postDelayed(sceneTick, SCENE_MS - FADE_MS);
    }

    @Override
    public void onDreamingStopped() {
        stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void stop() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(sceneTick);
        if (front != null) front.animate().cancel();
        if (back != null) back.animate().cancel();
    }

    /** Fade the next scene in over the current one, then swap roles. */
    private void crossfade() {
        sceneIndex = (sceneIndex + 1) % SCENES.length;
        final ImageView in = back, out = front;
        show(in, SCENES[sceneIndex]);
        in.animate().alpha(1f).setDuration(FADE_MS).start();
        out.animate().alpha(0f).setDuration(FADE_MS).start();
        front = in;
        back = out;
    }

    /** Load a scene and start its slow zoom and drift; it keeps moving for longer than it is visible. */
    private void show(ImageView v, int res) {
        v.animate().cancel();
        v.setImageResource(res);
        float dx = (random.nextBoolean() ? 1 : -1) * (30 + random.nextInt(40)) * getResources().getDisplayMetrics().density;
        float dy = (random.nextBoolean() ? 1 : -1) * (15 + random.nextInt(25)) * getResources().getDisplayMetrics().density;
        v.setScaleX(1.08f);
        v.setScaleY(1.08f);
        v.setTranslationX(-dx / 2f);
        v.setTranslationY(-dy / 2f);
        v.animate().scaleX(1.22f).scaleY(1.22f).translationX(dx / 2f).translationY(dy / 2f)
                .setDuration(SCENE_MS * 2).setInterpolator(new LinearInterpolator()).start();
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        content.setX(maxX * (0.1f + 0.8f * random.nextFloat()));
        content.setY(maxY * (0.1f + 0.8f * random.nextFloat()));
    }
}
