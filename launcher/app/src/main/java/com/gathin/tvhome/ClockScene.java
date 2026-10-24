package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock). With a theme's wallpapers it shows them as Home does (the
 * same time-of-day groups and dim) with the clock, date and weather centred, nudged a few pixels every minute so
 * nothing burns in. Without wallpapers the thin clock and date sit on black over a slow ambient glow, and every
 * minute the clock drifts to a new spot and the glow shifts colour.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** How far the centred clock wanders from the middle, in dp. */
    private static final int NUDGE_DP = 14;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** The theme's wallpapers, or null without them (then the glow and the drift). */
    private final WallpaperPlayer wallpapers;
    private final HomeWeather weather;
    private final TextView weatherLine;
    private final float density;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            if (wallpapers != null) {
                wallpapers.update();
            } else {
                shiftGlow();
            }
            refreshWeather();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        Context context = root.getContext();
        density = context.getResources().getDisplayMetrics().density;
        content = root.findViewById(R.id.dreamContent);
        weatherLine = root.findViewById(R.id.dreamWeather);
        View ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        Config cfg = Config.load(context);
        if (cfg.wallpaperEnabled && !cfg.wallpapers.isEmpty()) {
            ambient.setVisibility(View.GONE);
            wallpapers = new WallpaperPlayer(context, root.findViewById(R.id.dreamRoot), cfg);
        } else {
            wallpapers = null;
            ambient.setBackground(glow);
            ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                    glow.setGradientRadius((r - l) * 0.55f));
            colorIndex = random.nextInt(PALETTE.length);
            setGlow(PALETTE[colorIndex]);
        }
        String city = context.getString(R.string.weather_city).trim();
        weather = city.isEmpty() ? null : new HomeWeather(city,
                context.getString(R.string.latitude), context.getString(R.string.longitude));
    }

    void start() {
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        if (wallpapers != null) wallpapers.start();
        refreshWeather();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
        if (wallpapers != null) wallpapers.close();   // a screensaver is never started again once stopped
    }

    private void refreshWeather() {
        if (weather == null) return;
        weather.refresh(handler, lines -> {
            weatherLine.setVisibility(lines == null ? View.GONE : View.VISIBLE);
            if (lines != null) weatherLine.setText(lines[3]);
        });
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /** Centred and nudged over wallpapers; otherwise a random spot that keeps the whole clock on screen, the glow behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        if (wallpapers != null) {
            float nudge = NUDGE_DP * density;
            content.setX(maxX / 2f + nudge * (2 * random.nextFloat() - 1));
            content.setY(maxY / 2f + nudge * (2 * random.nextFloat() - 1));
            return;
        }
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
