package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;

/** Uses the installed inventory unchanged. External launches are monitored, never executed. */
public final class QuickSettingsSmoke extends Instrumentation {
    private MainActivity activity;
    private Intent launched;
    private int launches, checks;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        ActivityMonitor monitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                launched = new Intent(intent); launches++;
                return new ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        try {
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .setAction(MainActivity.ACTION_QUICK_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            check(panel() != null, "Cold launch action opens controls after layout");
            check(panel().getWindowToken() == activity.findViewById(R.id.topButtons).getWindowToken(),
                    "Controls and Home shortcuts share one composited window");
            check(activity.findViewById(R.id.topButtons).getAlpha() == 0f,
                    "Actual Home shortcuts are hidden while controls are open");
            check(!(activity.findViewById(R.id.btnSettings) instanceof TextView), "Settings entry is icon only");
            View initialGear = panel().findViewById(R.id.quickAllSettings);
            check(currentFocus() == R.id.quickAllSettings, "Panel starts on All Settings for a second OK; focus="
                    + panel().getCurrentFocus() + ", gear=" + initialGear + ", shown=" + initialGear.isShown()
                    + ", focusable=" + initialGear.isFocusable() + ", windowFocus=" + initialGear.hasWindowFocus());
            View allLabel = panel().findViewById(R.id.quickAllSettingsLabel);
            View allGear = panel().findViewById(R.id.quickAllSettings);
            check(allGear instanceof android.widget.ImageView, "All Settings is an icon, without a button title");
            check(allLabel.getVisibility() == View.VISIBLE && allLabel.getTop() >= allGear.getBottom(),
                    "Small All Settings label is visible below the focused gear");
            int firstRowTop = ((View) panel().findViewById(R.id.quickBluetooth).getParent()).getTop();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            check(currentFocus() == R.id.quickBluetooth && allLabel.getVisibility() == View.INVISIBLE,
                    "Moving down to a tile hides All Settings");
            check(((View) panel().findViewById(R.id.quickBluetooth).getParent()).getTop() == firstRowTop,
                    "Hiding the label does not move the tiles");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP); waitForIdleSync();
            check(currentFocus() == R.id.quickAllSettings && allLabel.getVisibility() == View.VISIBLE,
                    "Returning to the gear shows its label again");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); waitForIdleSync();
            View closeLabel = panel().findViewById(R.id.quickCloseLabel);
            check(currentFocus() == R.id.quickClose && closeLabel.getVisibility() == View.VISIBLE
                    && allLabel.getVisibility() == View.INVISIBLE, "Close has its own focused caption");
            check(closeLabel.getTop() >= panel().findViewById(R.id.quickClose).getBottom(),
                    "Close caption is below its icon");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            check(closeLabel.getVisibility() == View.INVISIBLE, "Close caption hides when moving to a tile");
            focus(R.id.quickBluetooth);
            int[][] moves = {{KeyEvent.KEYCODE_DPAD_RIGHT, R.id.quickAudioOutput},
                    {KeyEvent.KEYCODE_DPAD_DOWN, R.id.quickSound},
                    {KeyEvent.KEYCODE_DPAD_LEFT, R.id.quickPicture},
                    {KeyEvent.KEYCODE_DPAD_DOWN, R.id.quickScreensaver},
                    {KeyEvent.KEYCODE_DPAD_RIGHT, R.id.quickApps},
                    {KeyEvent.KEYCODE_DPAD_UP, R.id.quickSound},
                    {KeyEvent.KEYCODE_DPAD_UP, R.id.quickAudioOutput},
                    {KeyEvent.KEYCODE_DPAD_LEFT, R.id.quickBluetooth},
                    {KeyEvent.KEYCODE_DPAD_UP, R.id.quickAllSettings},
                    {KeyEvent.KEYCODE_DPAD_RIGHT, R.id.quickClose},
                    {KeyEvent.KEYCODE_DPAD_DOWN, R.id.quickAudioOutput}};
            for (int[] move : moves) {
                sendKeyDownUpSync(move[0]); waitForIdleSync();
                check(currentFocus() == move[1], "D-pad reaches control " + move[1]);
            }
            for (int id : new int[]{R.id.quickBluetooth, R.id.quickScreensaver, R.id.quickPicture,
                    R.id.quickSound, R.id.quickAudioOutput, R.id.quickApps, R.id.quickAllSettings, R.id.quickClose}) {
                focus(id);
                SystemClock.sleep(180);
                waitForIdleSync();
                View view = panel().findViewById(id);
                Rect rect = new Rect(); view.getGlobalVisibleRect(rect);
                int focusedWidth = Math.round(view.getWidth() * view.getScaleX());
                int focusedHeight = Math.round(view.getHeight() * view.getScaleY());
                check(rect.width() >= focusedWidth - 2 && rect.height() >= focusedHeight - 2,
                        "Focused control is not clipped " + id + ": visible=" + rect.width() + "x" + rect.height()
                                + ", scaled=" + focusedWidth + "x" + focusedHeight);
                check(view.getContentDescription() != null, "Control has an accessible name " + id);
            }
            checkRow(R.id.quickBluetooth, R.id.quickAudioOutput);
            checkRow(R.id.quickPicture, R.id.quickSound);
            checkRow(R.id.quickScreensaver, R.id.quickApps);
            check(((View) panel().findViewById(R.id.quickBluetooth).getParent()).getTop()
                    < ((View) panel().findViewById(R.id.quickPicture).getParent()).getTop()
                    && ((View) panel().findViewById(R.id.quickPicture).getParent()).getTop()
                    < ((View) panel().findViewById(R.id.quickApps).getParent()).getTop(),
                    "Rows follow Bluetooth/Audio Output, Picture/Sound, Screensaver/Apps");
            View contents = panel().findViewById(R.id.quickPanel);
            check(textCount(contents, activity.getString(R.string.settings)) == 4,
                    "Bluetooth, Picture, Sound and Apps have the Settings subtitle");
            check(textCount(panel().findViewById(R.id.quickAudioOutput), activity.getString(R.string.settings)) == 0,
                    "Audio output has no Settings subtitle");
            check(textCount(contents, activity.getString(R.string.quick_back)) == 1,
                    "Back-to-close appears once in the header");
            check(textCount(contents, activity.getString(R.string.module_cards)) == 0,
                    "Module cards is absent from the control centre");
            check(panel().findViewById(R.id.quickAllSettingsLabel).getVisibility() == View.INVISIBLE,
                    "All Settings label is hidden while Close is focused");
            check(textCount(contents, activity.getString(R.string.wifi)) == 0,
                    "Wi-Fi is absent from the panel");
            check(focusableCount(contents) == 8, "Only six tiles plus All Settings and Close are present");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle();
            check(panel() == null, "Back closes controls");
            check(activity.findViewById(R.id.btnSettings).isFocused(), "Back restores gear focus");
            verifyShortcutFade();
            verifyDismissal();
            Object homeKey = field("lastFocusedKey");
            addMonitor(monitor);
            verifyInterruptedAction();
            for (int id : new int[]{R.id.quickBluetooth, R.id.quickScreensaver, R.id.quickPicture,
                    R.id.quickSound, R.id.quickAudioOutput, R.id.quickApps, R.id.quickAllSettings}) {
                runOnMainSync(() -> activity.findViewById(R.id.btnSettings).performClick()); settle();
                check(currentFocus() == R.id.quickAllSettings, "Reopening starts on the settings gear");
                launched = null; launches = 0;
                Intent expected = QuickSettingsActions.intent(activity, id);
                if (id == R.id.quickAllSettings) sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                else runOnMainSync(() -> {
                    View control = panel().findViewById(id);
                    control.performClick();
                    control.performClick(); // A second press during exit must not launch twice.
                });
                settle();
                check(launches == 1 && launched != null, "Exactly one external launch " + id);
                check(expected.filterEquals(launched), "Launch uses the TV's requested route " + id);
                check(java.util.Objects.equals(expected.getStringExtra("slice_uri"),
                        launched.getStringExtra("slice_uri")), "Bluetooth retains accessories slice");
                check((launched.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0,
                        "Settings does not reopen a stale submenu");
                // Simulate returning from a native page; the monitor suppressed the real transition.
                runOnMainSync(() -> { callActivityOnPause(activity); callActivityOnResume(activity); });
                settle();
                check(panel() != null && currentFocus() == id, "Return restores selected quick control " + id);
                check(java.util.Objects.equals(homeKey, field("lastFocusedKey")), "Native page keeps Home's selected app");
                if (id == R.id.quickBluetooth) {
                    runOnMainSync(() -> activity.onNewIntent(new Intent(Intent.ACTION_MAIN)));
                    settle();
                    runOnMainSync(() -> activity.findViewById(R.id.btnSettings).performClick());
                    settle();
                    check(currentFocus() == R.id.quickAllSettings,
                            "Fresh opening after Home starts on the gear instead of a previous native shortcut");
                    // Home deliberately selects the first app; later native returns preserve that
                    // selection, rather than the app selected before this Home-navigation check.
                    homeKey = field("lastFocusedKey");
                }
                runOnMainSync(() -> panel().dismiss()); settle();
            }
            removeMonitor(monitor);
            result.putString("stream", "PASS: " + checks + " quick settings checks.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL after " + checks + " quick settings checks: "
                    + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            removeMonitor(monitor);
            if (activity != null) runOnMainSync(() -> { if (panel() != null) panel().dismiss(); });
        }
    }

    private void verifyDismissal() {
        runOnMainSync(() -> activity.findViewById(R.id.btnSettings).performClick()); settle();
        QuickSettingsPanel closing = panel();
        runOnMainSync(() -> {
            closing.dismiss();
            if (android.animation.ValueAnimator.areAnimatorsEnabled())
                check(closing.isShowing(), "Exit retains the panel for its fade instead of removing it immediately");
            closing.dismiss();
        });
        settle();
        check(panel() == null, "Repeated dismissal completes once without leaving an overlay");
        runOnMainSync(() -> {
            activity.findViewById(R.id.btnSettings).performClick();
            panel().dismiss(); // Close before the entry's first pre-draw.
        });
        settle();
        check(panel() == null && activity.findViewById(R.id.btnSettings).isFocused(),
                "Closing during entry cannot reopen the panel or lose Home's gear focus");
    }

    private void verifyShortcutFade() {
        View shortcuts = activity.findViewById(R.id.topButtons);
        View root = activity.findViewById(R.id.root);
        java.util.ArrayList<Float> frames = new java.util.ArrayList<>();
        android.view.ViewTreeObserver.OnPreDrawListener frame = () -> {
            frames.add(shortcuts.getAlpha());
            return true;
        };
        runOnMainSync(() -> {
            frames.add(shortcuts.getAlpha());
            root.getViewTreeObserver().addOnPreDrawListener(frame);
            activity.findViewById(R.id.btnSettings).performClick();
        });
        settle();
        runOnMainSync(() -> {
            checkFade(frames, false);
            check(!activity.findViewById(R.id.btnWifi).requestFocusFromTouch(),
                    "Hidden Home shortcuts cannot steal modal focus");
            frames.clear();
            frames.add(shortcuts.getAlpha());
            panel().dismiss();
        });
        settle();
        runOnMainSync(() -> {
            root.getViewTreeObserver().removeOnPreDrawListener(frame);
            checkFade(frames, true);
            check(shortcuts.getAlpha() == 1f && activity.findViewById(R.id.btnSettings).isFocused(),
                    "Close restores the same Home controls and gear focus");
        });
    }

    private void checkFade(java.util.List<Float> frames, boolean openingHome) {
        check(!frames.isEmpty(), "Shortcut fade has rendered frames");
        float previous = openingHome ? 0f : 1f;
        for (float alpha : frames) {
            check(openingHome ? alpha >= previous : alpha <= previous,
                    "Home shortcut opacity never flashes in reverse during " + (openingHome ? "close" : "open"));
            previous = alpha;
        }
        check(previous == (openingHome ? 1f : 0f), "Shortcut fade reaches its endpoint");
    }

    private void verifyInterruptedAction() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        runOnMainSync(() -> activity.findViewById(R.id.btnSettings).performClick()); settle();
        launched = null; launches = 0;
        runOnMainSync(() -> {
            panel().findViewById(R.id.quickPicture).performClick();
            check(launches == 0, "Native navigation waits for the exit transition");
            callActivityOnPause(activity);
        });
        settle();
        check(launches == 0, "An interrupted exit cancels its pending native launch");
        runOnMainSync(() -> callActivityOnResume(activity)); settle();
        check(panel() != null && currentFocus() == R.id.quickPicture,
                "Resume restores the interrupted control without a delayed launch");
        runOnMainSync(() -> panel().dismiss()); settle();
    }

    private void checkRow(int left, int right) {
        View first = panel().findViewById(left), second = panel().findViewById(right);
        check(first.getParent() == second.getParent() && first.getTop() == second.getTop()
                && first.getLeft() < second.getLeft(), "Controls share the expected row " + left + "/" + right);
    }

    private int focusableCount(View view) {
        if (view.isFocusable()) return 1;
        int count = 0;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += focusableCount(group.getChildAt(i));
        }
        return count;
    }

    private void focus(int id) { runOnMainSync(() -> panel().findViewById(id).requestFocusFromTouch()); waitForIdleSync(); }
    private int textCount(View view, String expected) {
        int count = view instanceof TextView && expected.contentEquals(((TextView) view).getText()) ? 1 : 0;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += textCount(group.getChildAt(i), expected);
        }
        return count;
    }
    private int currentFocus() { View focus = panel().getCurrentFocus(); return focus != null ? focus.getId() : 0; }
    private QuickSettingsPanel panel() { return (QuickSettingsPanel) field("quickSettings"); }
    private Object field(String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(450); waitForIdleSync(); }
    private void check(boolean success, String message) { if (!success) throw new AssertionError(message); checks++; }
}
