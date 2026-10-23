package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Power theme's welcome: when the TV comes on it greets for the time of day and asks for the mood, one card per
 * mode, with the mode for this hour suggested and started by itself after ten seconds unless a key is pressed.
 * Back keeps the last mode. Opened from Home with EXTRA_PICKER it is the mode picker: no greeting, no countdown.
 * It speaks Home's language: Outfit, the spaced capitals of the greeting, thin large type, dark glass, a white
 * focus ring.
 */
public class WelcomeActivity extends Activity {
    static final String EXTRA_PICKER = "com.gathin.tvhome.extra.MODE_PICKER";
    private static final int AUTO_SECONDS = 10;
    private static final int COLUMNS = 3;
    private static final int GLASS = 0x14FFFFFF;
    private static final int GLASS_FOCUSED = 0x29FFFFFF;
    private static final int HAIRLINE = 0x1FFFFFFF;
    private static final int MUTED = 0xA6FFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerTheme power;
    private int suggested;
    private int secondsLeft;
    private boolean counting;
    private View bar;
    private ObjectAnimator barAnim;
    private TextView countdown;
    private boolean animations;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!counting) return;
            if (--secondsLeft <= 0) {
                pick(suggested);
                return;
            }
            updateCountdown();
            handler.postDelayed(this, 1000);
        }
    };

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
        int margin = dp(64);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(backdrop(part));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        // Top-aligned like Home's clock block, leaving the bottom band to the footer.
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        cp.setMargins(margin, dp(52), margin, 0);
        root.addView(column, cp);

        // The greeting in Home's style: small spaced capitals, then the question in thin large type.
        String eyebrowText = picker ? getString(R.string.power_pick_title) : hello(part, cfg.greetingName);
        TextView eyebrow = text(eyebrowText.toUpperCase(Locale.getDefault()), 14, R.string.font_medium, MUTED);
        eyebrow.setLetterSpacing(0.25f);
        column.addView(eyebrow);
        TextView question = text(getString(picker ? R.string.power_pick_question : question(part)), 42,
                R.string.font_light, Color.WHITE);
        LinearLayout.LayoutParams qp = wrap();
        qp.topMargin = dp(8);
        qp.bottomMargin = dp(32);
        column.addView(question, qp);

        int screen = getResources().getDisplayMetrics().widthPixels;
        int gap = dp(20);
        int cardWidth = (screen - 2 * margin - (COLUMNS - 1) * gap) / COLUMNS;
        View[] cards = new View[power.modes.size()];
        LinearLayout row = null;
        for (int i = 0; i < cards.length; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = wrap();
                if (i > 0) rp.topMargin = gap;
                column.addView(row, rp);
            }
            cards[i] = card(i, !picker && i == suggested, cardWidth, i % COLUMNS > 0 ? gap : 0);
            row.addView(cards[i]);
        }
        column.setClipChildren(false);
        root.setClipChildren(false);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        countdown = text("", 15, R.string.font_medium, power.modes.get(suggested).color);
        LinearLayout.LayoutParams dp8 = wrap();
        dp8.setMarginEnd(dp(8));   // goes with the countdown, so the hint stays put once it hides
        footer.addView(countdown, dp8);
        footer.addView(text(getString(R.string.power_hint), 15, R.string.font_regular, MUTED));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        fp.setMargins(margin, 0, margin, dp(40));
        root.addView(footer, fp);

        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);

        cards[suggested].requestFocus();
        counting = !picker;
        secondsLeft = AUTO_SECONDS;
        if (counting) {
            handler.postDelayed(tick, 1000);
            if (bar != null && animations) {
                barAnim = ObjectAnimator.ofFloat(bar, View.SCALE_X, 1f, 0f).setDuration(AUTO_SECONDS * 1000L);
                barAnim.setInterpolator(new LinearInterpolator());
                barAnim.start();
            }
        }
        updateCountdown();
    }

    private View card(int index, boolean isSuggested, int width, int startMargin) {
        PowerTheme.Mode mode = power.modes.get(index);
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(132));
        lp.setMarginStart(startMargin);
        card.setLayoutParams(lp);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(24), dp(18), dp(24), dp(16));
        card.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView icon = new ImageView(this);
        icon.setImageResource(icon(mode.id));
        icon.setImageTintList(ColorStateList.valueOf(mode.color));
        inner.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView mood = text(mode.mood, 24, R.string.font_regular, Color.WHITE);
        LinearLayout.LayoutParams mp = wrap();
        mp.topMargin = dp(10);
        inner.addView(mood, mp);
        TextView line = text(mode.line.isEmpty() ? mode.name : mode.line, 15, R.string.font_light, MUTED);
        line.setSingleLine(true);
        line.setEllipsize(android.text.TextUtils.TruncateAt.END);
        inner.addView(line);

        if (isSuggested) {
            // A small pill in the mode's colour, and a thin line that runs down with the countdown.
            TextView tag = text(getString(R.string.power_suggested), 11, R.string.font_medium, 0xFF0B0D10);
            tag.setLetterSpacing(0.12f);
            tag.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(12));
            pill.setColor(mode.color);
            tag.setBackground(pill);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            tp.setMargins(0, dp(16), dp(18), 0);
            card.addView(tag, tp);
            bar = new View(this);
            bar.setBackgroundColor(mode.color);
            bar.setPivotX(0);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3),
                    Gravity.BOTTOM);
            bp.setMargins(dp(18), 0, dp(18), dp(1));
            card.addView(bar, bp);
        }

        style(card, bg, false);
        card.setOnFocusChangeListener((v, has) -> style(card, bg, has));
        card.setOnClickListener(v -> pick(index));
        return card;
    }

    /** Unfocused: dark glass with a hairline. Focused: a white ring, a little lighter and lifted, as on Home. */
    private void style(View card, GradientDrawable bg, boolean focused) {
        bg.setColor(focused ? GLASS_FOCUSED : GLASS);
        bg.setStroke(focused ? dp(2) : dp(1), focused ? Color.WHITE : HAIRLINE);
        card.setElevation(focused ? dp(12) : 0);
        float scale = focused ? 1.04f : 1f;
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
        handler.removeCallbacks(tick);
        if (barAnim != null) barAnim.cancel();
        if (bar != null) bar.setVisibility(View.INVISIBLE);
        updateCountdown();
    }

    private void updateCountdown() {
        if (counting) {
            countdown.setText(getString(R.string.power_auto, power.modes.get(suggested).mood, secondsLeft));
            countdown.setVisibility(View.VISIBLE);
        } else {
            countdown.setVisibility(View.GONE);
        }
    }

    private void pick(int index) {
        counting = false;
        handler.removeCallbacks(tick);
        PowerTheme.choose(this, power.modes.get(index).id);
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);
        // Never left behind: Home or a launched app ends it, and the last mode stays. A configuration change
        // (TVs and the emulator apply one as the keyboard service starts after boot) also stops it, only to
        // recreate it straight away; finishing then would close the welcome a few seconds in.
        if (!isChangingConfigurations()) finish();
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

    private static int icon(String id) {
        switch (id) {
            case "entertain": return R.drawable.ic_mode_entertain;
            case "movies": return R.drawable.ic_mode_movies;
            case "games": return R.drawable.ic_mode_games;
            case "kids": return R.drawable.ic_mode_kids;
            case "news": return R.drawable.ic_mode_news;
            case "markets": return R.drawable.ic_mode_markets;
            default: return R.drawable.ic_mode_other;
        }
    }

    /**
     * Home's near-black, with one soft glow for the time of day from the top left: amber at dawn, teal by day,
     * rose at dusk, blue at night. Dark enough that white text and the white focus ring always read.
     */
    private LayerDrawable backdrop(String part) {
        int tint;
        switch (part) {
            case "morning": tint = 0x66C7803A; break;
            case "afternoon": tint = 0x552A8FA8; break;
            case "evening": tint = 0x66A0446E; break;
            default: tint = 0x55344C9C; break;
        }
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A0C11, 0xFF050608});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{tint, 0x00000000});
        glow.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.75f);
        glow.setGradientCenter(0.15f, 0f);
        return new LayerDrawable(new android.graphics.drawable.Drawable[]{base, glow});
    }

    private TextView text(String s, float sp, int font, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        Typeface face = UiFont.get(this, font);
        if (face != null) t.setTypeface(face);
        return t;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
