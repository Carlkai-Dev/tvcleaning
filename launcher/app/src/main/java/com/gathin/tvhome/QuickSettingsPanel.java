package com.gathin.tvhome;

import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.SoundEffectConstants;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/** Remote-friendly glass shortcuts to the TV's own settings pages. */
final class QuickSettingsPanel extends FrameLayout {
    interface Listener {
        void onAction(int control);
        void onClosed(boolean restoreFocus);
    }

    private final Activity activity;
    private final ViewGroup home;
    private final Listener listener;
    private final Bitmap backdrop;
    private final float unit, fontScale;
    private final boolean reducedMotion;
    private final boolean animationsEnabled;
    private final boolean navigationSounds;
    private final LinearLayout content;
    private final ScrollView scroll;
    private final ColorDrawable scrim = new ColorDrawable(0xFF030C1A);
    private final View homeShortcuts;
    private final float shortcutAlpha;
    private final int shortcutLayer;
    private final View previousFocus;
    private final ArrayList<HomeChildState> homeChildren = new ArrayList<>();
    private TextView launcherSettingsLabel, allSettingsLabel, closeLabel;
    private ValueAnimator transition;
    private ViewTreeObserver.OnPreDrawListener entryDraw;
    private float progress;
    private boolean closing;
    private Runnable afterClose;
    private int selected = R.id.quickAllSettings;
    private boolean restoreFocus = true;

    QuickSettingsPanel(Activity activity, View home, boolean reducedMotion, boolean animations,
                       boolean navigationSounds, Listener listener) {
        super(activity);
        this.activity = activity;
        this.home = (ViewGroup) home;
        this.listener = listener;
        // Launcher settings' Animations off, like the system's animator scale at 0, opens and closes in place.
        animationsEnabled = animations && ValueAnimator.areAnimatorsEnabled();
        this.reducedMotion = reducedMotion || !animationsEnabled;
        this.navigationSounds = navigationSounds;
        unit = Math.min(home.getWidth() / 960f, home.getHeight() / 540f);
        fontScale = activity.getResources().getConfiguration().fontScale;
        homeShortcuts = home.findViewById(R.id.topButtons);
        shortcutAlpha = homeShortcuts.getAlpha();
        shortcutLayer = homeShortcuts.getLayerType();
        previousFocus = home.findFocus();
        homeShortcuts.setAlpha(0f);
        try { backdrop = FolderBackdrop.capture(home); }
        finally { homeShortcuts.setAlpha(shortcutAlpha); }
        FrameLayout overlay = this;
        scrim.setAlpha(0);
        overlay.setOnClickListener(v -> dismiss());
        overlay.setSoundEffectsEnabled(navigationSounds);
        overlay.setFocusable(false);
        View dimmer = new View(activity);
        dimmer.setBackground(scrim);
        dimmer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.addView(dimmer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClickable(true);
        scroll.setSoundEffectsEnabled(navigationSounds);
        scroll.setFocusable(false);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(px(336),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP);
        position.setMargins(0, px(24), px(26), px(20));
        overlay.addView(scroll, position);
        content = column();
        content.setId(R.id.quickPanel);
        content.setPadding(px(10), px(8), px(10), px(10));
        content.setClipChildren(false);
        // Focus scales a capsule into this padding. clipChildren=false alone does not stop
        // ViewGroup from clipping all its drawing to the padded content rectangle.
        content.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        showMain();
    }

    void open(int focus) {
        selected = focus;
        applyProgress(0f);
        for (int i = 0; i < home.getChildCount(); i++)
            homeChildren.add(new HomeChildState(home.getChildAt(i)));
        home.addView(this, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // One window submits both the real shortcuts and the panel in the same frame. A
        // second window with a translucent copy can double-draw the pills during handoff.
        homeShortcuts.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        focus(focus);
        entryDraw = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (content.getWidth() == 0) return true;
                removeEntryListener();
                if (isShowing() && !closing) {
                    focus(focus);
                    animateTo(1f);
                }
                return true;
            }
        };
        content.getViewTreeObserver().addOnPreDrawListener(entryDraw);
    }

    private void applyProgress(float value) {
        progress = value;
        scroll.setAlpha(value);
        scroll.setTranslationX(reducedMotion ? 0f : px(24) * (1f - value));
        scrim.setAlpha(Math.round(0x42 * value));
        homeShortcuts.setAlpha(shortcutAlpha * (1f - value));
    }

    private void animateTo(float target) {
        cancelTransition();
        float distance = Math.abs(target - progress);
        if (!animationsEnabled || distance < .001f) {
            applyProgress(target);
            if (target == 0f) finishDismiss();
            return;
        }
        // Composite one cached layer during motion instead of redrawing each glass capsule.
        scroll.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        scroll.buildLayer();
        long duration = reducedMotion ? (target == 1f ? 180 : 140) : (target == 1f ? 320 : 220);
        transition = ValueAnimator.ofFloat(progress, target);
        transition.setDuration(Math.max(90, Math.round(duration * distance)));
        transition.setInterpolator(target == 1f
                ? new PathInterpolator(.22f, .61f, .36f, 1f)
                : new PathInterpolator(.4f, 0f, 1f, 1f));
        transition.addUpdateListener(animation -> applyProgress((float) animation.getAnimatedValue()));
        transition.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (transition != animation) return;
                transition = null;
                scroll.setLayerType(View.LAYER_TYPE_NONE, null);
                if (target == 0f) finishDismiss();
            }
        });
        transition.start();
    }

    private void cancelTransition() {
        ValueAnimator previous = transition;
        transition = null;
        if (previous != null) {
            previous.removeAllListeners();
            previous.removeAllUpdateListeners();
            previous.cancel();
        }
    }

    private void removeEntryListener() {
        if (entryDraw == null) return;
        if (content.getViewTreeObserver().isAlive())
            content.getViewTreeObserver().removeOnPreDrawListener(entryDraw);
        entryDraw = null;
    }

    void dismiss() {
        if (!isShowing() || closing) return;
        closing = true;
        removeEntryListener();
        if (activity.isFinishing() || activity.isDestroyed()) finishDismiss();
        else animateTo(0f);
    }

    private void finishDismiss() {
        removeEntryListener();
        cancelTransition();
        applyProgress(0f);
        scroll.setLayerType(View.LAYER_TYPE_NONE, null);
        homeShortcuts.setLayerType(shortcutLayer, null);
        if (!isShowing()) return;
        for (HomeChildState child : homeChildren) child.restore();
        homeChildren.clear();
        // Restore focus before removal so Android cannot briefly focus a Home app instead.
        if (previousFocus != null && previousFocus.isAttachedToWindow()) previousFocus.requestFocusFromTouch();
        home.removeView(this);
        listener.onClosed(restoreFocus);
        Runnable action = afterClose;
        afterClose = null;
        if (action != null && !activity.isFinishing() && !activity.isDestroyed()) action.run();
    }

    boolean isShowing() { return getParent() == home; }
    View getCurrentFocus() { return findFocus(); }

    void closeForAction(Runnable action) {
        if (closing) return;
        restoreFocus = false;
        afterClose = action;
        dismiss();
    }

    int selectedControl() { return selected; }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (closing) return true;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) dismiss();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        int direction;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = View.FOCUS_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP: direction = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = View.FOCUS_DOWN; break;
            default: return super.dispatchKeyEvent(event);
        }
        // Keep remote focus within the panel, including at the edges of its explicit routes.
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View current = getCurrentFocus();
            if (current == null) focus(selected);
            else {
                View next = current.focusSearch(direction);
                if (next != null && next != current && contains(next) && next.requestFocus(direction)
                        && navigationSounds)
                    playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
            }
        }
        return true;
    }

    void closeWithoutFocus() {
        restoreFocus = false;
        afterClose = null;
        closing = true;
        finishDismiss();
    }

    private boolean contains(View view) {
        while (view != null && view != this)
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        return view == this;
    }

    /** Background content stays drawn but cannot receive focus or accessibility actions. */
    private static final class HomeChildState {
        final View view;
        final int accessibility, descendants;
        final boolean focusable;
        HomeChildState(View view) {
            this.view = view;
            accessibility = view.getImportantForAccessibility();
            focusable = view.isFocusable();
            descendants = view instanceof ViewGroup ? ((ViewGroup) view).getDescendantFocusability() : 0;
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            view.setFocusable(false);
            if (view instanceof ViewGroup)
                ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        void restore() {
            view.setImportantForAccessibility(accessibility);
            view.setFocusable(focusable);
            if (view instanceof ViewGroup) ((ViewGroup) view).setDescendantFocusability(descendants);
        }
    }

    private void showMain() {
        LinearLayout header = row();
        header.setGravity(Gravity.TOP);
        TextView back = text(activity.getString(R.string.quick_back), 11, 400, 0xC9FFFFFF);
        back.setPadding(px(24), 0, 0, 0);
        header.addView(back, new LinearLayout.LayoutParams(0, px(38), 1));
        back.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout launcherSettings = column();
        launcherSettings.setGravity(Gravity.CENTER_HORIZONTAL);
        launcherSettings.setClipChildren(false);
        // As far from All settings as All settings is from Close; All settings sits 10 to the right.
        launcherSettings.setTranslationX(px(12));
        launcherSettings.addView(circle(R.id.quickLauncherSettings, R.drawable.ic_quick_launcher_settings,
                R.string.quick_launcher_settings, () -> listener.onAction(R.id.quickLauncherSettings)));
        launcherSettingsLabel = text(activity.getString(R.string.quick_launcher_settings), 11, 500, 0xDBFFFFFF);
        launcherSettingsLabel.setId(R.id.quickLauncherSettingsLabel);
        launcherSettingsLabel.setGravity(Gravity.CENTER);
        launcherSettingsLabel.setMaxLines(2);
        launcherSettingsLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        launcherSettingsLabel.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams launcherLabelParams = new LinearLayout.LayoutParams(
                px(100), ViewGroup.LayoutParams.WRAP_CONTENT);
        launcherLabelParams.gravity = Gravity.CENTER_HORIZONTAL;
        launcherLabelParams.topMargin = px(6);
        launcherSettings.addView(launcherSettingsLabel, launcherLabelParams);
        header.addView(launcherSettings, new LinearLayout.LayoutParams(px(38), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout allSettings = column();
        allSettings.setGravity(Gravity.CENTER_HORIZONTAL);
        allSettings.setClipChildren(false);
        allSettings.setTranslationX(px(10));
        allSettings.addView(circle(R.id.quickAllSettings, R.drawable.ic_settings,
                R.string.quick_all_settings, () -> listener.onAction(R.id.quickAllSettings)));
        allSettingsLabel = text(activity.getString(R.string.quick_all_settings), 11, 500, 0xDBFFFFFF);
        allSettingsLabel.setId(R.id.quickAllSettingsLabel);
        allSettingsLabel.setGravity(Gravity.CENTER);
        allSettingsLabel.setMaxLines(2);
        allSettingsLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Keep the label's space so moving to a tile cannot shift the controls under focus.
        allSettingsLabel.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                px(100), ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.CENTER_HORIZONTAL;
        labelParams.topMargin = px(6);
        allSettings.addView(allSettingsLabel, labelParams);
        header.addView(allSettings, new LinearLayout.LayoutParams(px(80), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout close = column();
        close.setGravity(Gravity.CENTER_HORIZONTAL);
        close.setClipChildren(false);
        close.addView(circle(R.id.quickClose, R.drawable.ic_quick_close, R.string.quick_close, this::dismiss));
        closeLabel = text(activity.getString(R.string.quick_close_label), 11, 500, 0xDBFFFFFF);
        closeLabel.setId(R.id.quickCloseLabel);
        closeLabel.setGravity(Gravity.CENTER);
        closeLabel.setMaxLines(2);
        closeLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        closeLabel.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams closeLabelParams = new LinearLayout.LayoutParams(px(52),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLabelParams.gravity = Gravity.CENTER_HORIZONTAL;
        closeLabelParams.topMargin = px(6);
        close.addView(closeLabel, closeLabelParams);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(px(38), ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.leftMargin = px(8);
        header.addView(close, closeParams);
        add(header, 0);

        LinearLayout first = row();
        first.addView(pill(R.id.quickBluetooth, R.drawable.ic_quick_bluetooth,
                R.string.quick_bluetooth, activity.getString(R.string.settings)), half());
        first.addView(pill(R.id.quickAudioOutput, R.drawable.ic_quick_audio_output,
                R.string.quick_audio_output, null), halfRight());
        add(first, 12);
        LinearLayout second = row();
        second.addView(pill(R.id.quickPicture, R.drawable.ic_quick_picture, R.string.quick_picture,
                activity.getString(R.string.settings)), half());
        second.addView(pill(R.id.quickSound, R.drawable.ic_quick_sound, R.string.quick_sound,
                activity.getString(R.string.settings)), halfRight());
        add(second, 10);
        LinearLayout third = row();
        third.addView(pill(R.id.quickScreensaver, R.drawable.ic_quick_screensaver,
                R.string.quick_screensaver, activity.getString(R.string.quick_start_now)), half());
        third.addView(pill(R.id.quickApps, R.drawable.ic_quick_apps, R.string.quick_apps,
                activity.getString(R.string.settings)), halfRight());
        add(third, 10);
        connect(R.id.quickLauncherSettings, R.id.quickLauncherSettings, R.id.quickAllSettings,
                R.id.quickLauncherSettings, R.id.quickBluetooth);
        connect(R.id.quickAllSettings, R.id.quickLauncherSettings, R.id.quickClose, R.id.quickAllSettings, R.id.quickBluetooth);
        connect(R.id.quickClose, R.id.quickAllSettings, R.id.quickClose, R.id.quickClose, R.id.quickAudioOutput);
        connect(R.id.quickBluetooth, R.id.quickBluetooth, R.id.quickAudioOutput, R.id.quickAllSettings, R.id.quickPicture);
        connect(R.id.quickAudioOutput, R.id.quickBluetooth, R.id.quickAudioOutput, R.id.quickClose, R.id.quickSound);
        connect(R.id.quickPicture, R.id.quickPicture, R.id.quickSound, R.id.quickBluetooth, R.id.quickScreensaver);
        connect(R.id.quickSound, R.id.quickPicture, R.id.quickSound, R.id.quickAudioOutput, R.id.quickApps);
        connect(R.id.quickScreensaver, R.id.quickScreensaver, R.id.quickApps, R.id.quickPicture, R.id.quickScreensaver);
        connect(R.id.quickApps, R.id.quickScreensaver, R.id.quickApps, R.id.quickSound, R.id.quickApps);
    }

    private View pill(int id, int icon, int label, String summary) {
        LinearLayout button = row();
        button.setId(id);
        button.setMinimumHeight(px(70));
        button.setPadding(px(12), px(10), px(12), px(10));
        glass(button);
        FrameLayout symbol = new FrameLayout(activity);
        GradientDrawable white = new GradientDrawable();
        white.setShape(GradientDrawable.OVAL);
        white.setColor(Color.WHITE);
        symbol.setBackground(white);
        ImageView image = new ImageView(activity);
        image.setImageResource(icon);
        image.setColorFilter(0xFF007AFF);
        int size = px(24);
        symbol.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        button.addView(symbol, new LinearLayout.LayoutParams(px(38), px(38)));
        LinearLayout copy = column();
        TextView title = text(activity.getString(label), id == R.id.quickAudioOutput ? 13 : 14, 600, Color.WHITE);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);
        if (summary != null) {
            TextView subtitle = text(summary, 11, 500, 0xDBFFFFFF);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subtitleParams.topMargin = px(3);
            copy.addView(subtitle, subtitleParams);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyParams.leftMargin = px(9);
        button.addView(copy, copyParams);
        button.setContentDescription(activity.getString(label) + (summary != null ? ", " + summary : ""));
        makeButton(button, () -> listener.onAction(id));
        return button;
    }

    private ImageView circle(int id, int icon, int description, Runnable action) {
        ImageView view = new ImageView(activity);
        view.setId(id);
        view.setImageResource(icon);
        view.setColorFilter(Color.WHITE);
        view.setContentDescription(activity.getString(description));
        view.setPadding(px(10), px(10), px(10), px(10));
        view.setLayoutParams(new LinearLayout.LayoutParams(px(38), px(38)));
        glass(view);
        makeButton(view, action);
        return view;
    }

    private void glass(View view) {
        QuickGlassDrawable glass = new QuickGlassDrawable(backdrop, unit);
        view.setBackground(glass);
        view.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int[] here = new int[2], origin = new int[2];
            v.getLocationOnScreen(here);
            home.getLocationOnScreen(origin);
            // The snapshot belongs to the resting layout, independent of the entrance slide.
            here[0] -= Math.round(scroll.getTranslationX());
            glass.setRegion(new Rect(here[0] - origin[0], here[1] - origin[1],
                    here[0] - origin[0] + v.getWidth(), here[1] - origin[1] + v.getHeight()),
                    home.getWidth(), home.getHeight());
        });
    }

    private void makeButton(View view, Runnable action) {
        view.setFocusable(true);
        view.setClickable(true);
        view.setDefaultFocusHighlightEnabled(false);
        view.setSoundEffectsEnabled(navigationSounds);
        if (view instanceof ViewGroup) ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        view.setOnClickListener(v -> {
            if (closing) return;
            selected = v.getId();
            action.run();
        });
        view.setOnFocusChangeListener((v, focused) -> {
            if (focused) selected = v.getId();
            if (v.getId() == R.id.quickLauncherSettings && launcherSettingsLabel != null)
                launcherSettingsLabel.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            if (v.getId() == R.id.quickAllSettings && allSettingsLabel != null)
                allSettingsLabel.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            if (v.getId() == R.id.quickClose && closeLabel != null)
                closeLabel.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            if (!reducedMotion) v.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f)
                    .setDuration(130).start();
        });
    }

    private void connect(int id, int left, int right, int up, int down) {
        View view = content.findViewById(id);
        if (view == null) return;
        view.setNextFocusLeftId(left); view.setNextFocusRightId(right);
        view.setNextFocusUpId(up); view.setNextFocusDownId(down);
    }

    private void focus(int id) {
        if (!isShowing()) return;
        View view = content.findViewById(id);
        if (view == null) view = content.findViewById(R.id.quickAllSettings);
        if (view != null) view.requestFocusFromTouch();
    }

    private void add(View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = px(topMargin);
        content.addView(child, params);
    }

    private LinearLayout.LayoutParams half() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private LinearLayout.LayoutParams halfRight() { LinearLayout.LayoutParams p = half(); p.leftMargin = px(10); return p; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); v.setClipChildren(false); return v; }
    private TextView text(String value, int size, int weight, int color) {
        TextView text = new TextView(activity);
        text.setText(value); text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit * fontScale);
        text.setTypeface(Typeface.create("sans-serif" + (weight >= 500 ? "-medium" : ""), Typeface.NORMAL));
        text.setIncludeFontPadding(false);
        return text;
    }
    private int px(float value) { return Math.round(value * unit); }
}
