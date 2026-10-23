package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Power theme's welcome: when the TV comes on it greets for the time of day and asks for the mood, one card per
 * mode, with the mode for this hour suggested and started by itself after ten seconds unless a key is pressed.
 * Back keeps the last mode. Opened from Home with EXTRA_PICKER it is the mode picker: no greeting, no countdown.
 */
public class WelcomeActivity extends Activity {
    static final String EXTRA_PICKER = "com.gathin.tvhome.extra.MODE_PICKER";
    private static final long AUTO_MS = 10_000;
    private static final int COLUMNS = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerTheme power;
    private int suggested;
    private boolean counting;
    private View bar;
    private ObjectAnimator barAnim;
    private TextView hint;
    private boolean animations;
    private final Runnable autoPick = () -> pick(suggested);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Config cfg = Config.load(this);
        power = cfg.power;
        if (!power.on()) {
            finish();
            return;
        }
        animations = cfg.animations && !cfg.reducedMotion;
        boolean picker = getIntent().getBooleanExtra(EXTRA_PICKER, false);
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        boolean weekend = now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY;
        String last = PowerTheme.chosen(this);
        int current = Math.max(0, PowerTheme.indexOf(power.modes, last));
        suggested = picker ? current : power.suggested(hour, weekend, last);
        String part = PowerTheme.daypart(hour);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, sky(part)));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView hello = text(picker ? getString(R.string.power_pick_title) : hello(part, cfg.greetingName),
                picker ? 40 : 56, R.string.font_medium);
        column.addView(hello, centred());
        TextView question = text(getString(picker ? R.string.power_pick_question : question(part)), 24,
                R.string.font_regular);
        question.setAlpha(0.88f);
        LinearLayout.LayoutParams qp = centred();
        qp.topMargin = dp(8);
        qp.bottomMargin = dp(28);
        column.addView(question, qp);

        View[] cards = new View[power.modes.size()];
        LinearLayout row = null;
        for (int i = 0; i < cards.length; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                column.addView(row, centred());
            }
            cards[i] = card(i, !picker && i == suggested);
            row.addView(cards[i]);
        }

        hint = text("", 15, R.string.font_regular);
        hint.setAlpha(0.65f);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = dp(36);
        root.addView(hint, hp);
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);

        cards[suggested].requestFocus();
        counting = !picker;
        if (counting) {
            handler.postDelayed(autoPick, AUTO_MS);
            if (bar != null && animations) {
                barAnim = ObjectAnimator.ofFloat(bar, View.SCALE_X, 1f, 0f).setDuration(AUTO_MS);
                barAnim.setInterpolator(new LinearInterpolator());
                barAnim.start();
            }
        }
        updateHint();
    }

    private View card(int index, boolean isSuggested) {
        PowerTheme.Mode mode = power.modes.get(index);
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(300), dp(104));
        lp.setMargins(dp(10), dp(10), dp(10), dp(10));
        card.setLayoutParams(lp);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(20), 0, dp(20), 0);
        card.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView dot = text(mode.mood.substring(0, 1).toUpperCase(Locale.getDefault()), 24, R.string.font_medium);
        dot.setGravity(Gravity.CENTER);
        dot.setTextColor(0xFF0B0D10);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(mode.color);
        dot.setBackground(circle);
        inner.addView(dot, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.setMarginStart(dp(16));
        inner.addView(words, wp);
        TextView mood = text(mode.mood, 22, R.string.font_medium);
        words.addView(mood);
        TextView line = text(mode.line.isEmpty() ? mode.name : mode.line, 14, R.string.font_regular);
        line.setAlpha(0.75f);
        words.addView(line);

        if (isSuggested) {
            TextView tag = text(getString(R.string.power_suggested), 10, R.string.font_medium);
            tag.setLetterSpacing(0.14f);
            tag.setAlpha(0.7f);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            tp.setMargins(0, dp(10), dp(14), 0);
            card.addView(tag, tp);
            bar = new View(this);
            bar.setBackgroundColor(mode.color);
            bar.setPivotX(0);
            card.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4),
                    Gravity.BOTTOM));
        }

        TextView[] texts = {mood, line};
        style(card, bg, texts, false);
        card.setOnFocusChangeListener((v, has) -> style(card, bg, texts, has));
        card.setOnClickListener(v -> pick(index));
        return card;
    }

    /** Unfocused: frosted on the sky. Focused: white with dark text, a little larger. */
    private void style(View card, GradientDrawable bg, TextView[] texts, boolean focused) {
        bg.setColor(focused ? 0xF5FFFFFF : 0x22FFFFFF);
        bg.setStroke(dp(1), focused ? 0xFFFFFFFF : 0x30FFFFFF);
        for (TextView t : texts) t.setTextColor(focused ? 0xFF0B0D10 : Color.WHITE);
        float scale = focused ? 1.06f : 1f;
        if (animations) {
            card.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
        } else {
            card.setScaleX(scale);
            card.setScaleY(scale);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Any key stops the countdown: the person is choosing.
        if (counting && event.getAction() == KeyEvent.ACTION_DOWN) stopCountdown();
        return super.dispatchKeyEvent(event);
    }

    private void stopCountdown() {
        counting = false;
        handler.removeCallbacks(autoPick);
        if (barAnim != null) barAnim.cancel();
        if (bar != null) bar.setVisibility(View.INVISIBLE);
        updateHint();
    }

    private void updateHint() {
        String keys = getString(R.string.power_hint);
        hint.setText(counting ? getString(R.string.power_auto, power.modes.get(suggested).mood, keys) : keys);
    }

    private void pick(int index) {
        handler.removeCallbacks(autoPick);
        PowerTheme.choose(this, power.modes.get(index).id);
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(autoPick);
        finish();   // never left behind: Home or a launched app ends it, the last mode stays
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private String hello(String part, String name) {
        boolean named = name != null && !name.isEmpty();
        switch (part) {
            case "morning":
                return named ? getString(R.string.power_hello_morning_name, name) : getString(R.string.power_hello_morning);
            case "afternoon":
                return named ? getString(R.string.power_hello_afternoon_name, name) : getString(R.string.power_hello_afternoon);
            case "evening":
                return named ? getString(R.string.power_hello_evening_name, name) : getString(R.string.power_hello_evening);
            default:
                return named ? getString(R.string.power_hello_night_name, name) : getString(R.string.power_hello_night);
        }
    }

    private static int question(String part) {
        switch (part) {
            case "morning": return R.string.power_question_morning;
            case "afternoon": return R.string.power_question_afternoon;
            case "evening": return R.string.power_question_evening;
            default: return R.string.power_question_night;
        }
    }

    /** Dark skies for the time of day: dawn, daylight, sunset, night. White text reads on all four. */
    private static int[] sky(String part) {
        switch (part) {
            case "morning": return new int[]{0xFF1D2B4A, 0xFF7A4A5E, 0xFFB8735A};
            case "afternoon": return new int[]{0xFF0F3352, 0xFF2A6585, 0xFF3F7F96};
            case "evening": return new int[]{0xFF1A1036, 0xFF5A2A5E, 0xFFA8573A};
            default: return new int[]{0xFF04060F, 0xFF0E1630, 0xFF1C2550};
        }
    }

    private TextView text(String s, float sp, int font) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        Typeface face = UiFont.get(this, font);
        if (face != null) t.setTypeface(face);
        return t;
    }

    private static LinearLayout.LayoutParams centred() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
