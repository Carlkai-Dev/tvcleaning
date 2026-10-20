package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;

import java.lang.reflect.Field;

/** Exercises header activation without opening external apps or changing network settings. */
public final class SettingsLaunchSmoke extends Instrumentation {
    private MainActivity activity;
    private volatile Intent launched;
    private volatile int launches;
    private int checks;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        ActivityMonitor monitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                launched = new Intent(intent);
                launches++;
                return new ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        Field vpnField = null;
        String originalVpn = null;
        try {
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            SystemClock.sleep(700);
            vpnField = MainActivity.class.getDeclaredField("vpnApp");
            vpnField.setAccessible(true);
            originalVpn = (String) vpnField.get(activity);
            setVpn(vpnField, "");
            addMonitor(monitor);

            click(R.id.btnWifi);
            expectSettings(Settings.ACTION_WIFI_SETTINGS);
            click(R.id.btnSettings);
            expectQuickSettings();
            for (int key : new int[]{KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER}) {
                press(R.id.btnWifi, key);
                expectSettings(Settings.ACTION_WIFI_SETTINGS);
                press(R.id.btnSettings, key);
                expectQuickSettings();
            }

            // An absent configured VPN must retain the direct Wi-Fi fallback.
            setVpn(vpnField, "com.gathin.tvhome.missing.vpn");
            click(R.id.btnWifi);
            expectSettings(Settings.ACTION_WIFI_SETTINGS);

            // Use our own installed Leanback entry as a fixture; the monitor prevents launching it.
            setVpn(vpnField, getTargetContext().getPackageName());
            click(R.id.btnWifi);
            expectOneLaunch();
            check(Intent.ACTION_MAIN.equals(launched.getAction()), "Configured VPN uses its app entry");
            check(launched.getComponent() != null && getTargetContext().getPackageName()
                    .equals(launched.getComponent().getPackageName()), "Configured app is preserved");
            check((launched.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) == 0,
                    "Settings task reset must not affect VPN app launches");
            resetLaunch();
            runOnMainSync(() -> activity.findViewById(R.id.btnWifi).performLongClick());
            waitForIdleSync();
            expectSettings(Settings.ACTION_WIFI_SETTINGS);

            result.putString("stream", "PASS: " + checks + " settings activation checks.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL after " + checks + " settings activation checks: "
                    + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            removeMonitor(monitor);
            if (vpnField != null && originalVpn != null) setVpn(vpnField, originalVpn);
        }
    }

    private void setVpn(Field field, String value) {
        runOnMainSync(() -> {
            try { field.set(activity, value); }
            catch (IllegalAccessException e) { throw new RuntimeException(e); }
        });
    }

    private void resetLaunch() { launched = null; launches = 0; }

    private void click(int id) {
        resetLaunch();
        runOnMainSync(() -> activity.findViewById(id).performClick());
        waitForIdleSync();
    }

    private void press(int id, int key) {
        resetLaunch();
        runOnMainSync(() -> {
            activity.onNewIntent(new Intent(Intent.ACTION_MAIN));
        });
        waitForIdleSync();
        SystemClock.sleep(200);
        runOnMainSync(() -> {
            View button = activity.findViewById(id);
            check(button.requestFocusFromTouch(), "Header button accepts remote focus");
        });
        sendKeyDownUpSync(key);
        waitForIdleSync();
    }

    private void expectOneLaunch() {
        check(launches == 1 && launched != null, "One activation dispatches exactly one launch");
    }

    private void expectQuickSettings() throws Exception {
        check(launches == 0 && launched == null, "Gear opens local controls without an external launch");
        Field field = MainActivity.class.getDeclaredField("quickSettings");
        field.setAccessible(true);
        QuickSettingsPanel panel = (QuickSettingsPanel) field.get(activity);
        check(panel != null && panel.isShowing(), "Gear shows quick settings");
        runOnMainSync(panel::dismiss);
        waitForIdleSync();
    }

    private void expectSettings(String action) {
        expectOneLaunch();
        check(action.equals(launched.getAction()), "Header opens the requested settings screen");
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP;
        check((launched.getFlags() & flags) == flags,
                "Settings opens its requested screen above any previous submenu");
    }

    private void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        checks++;
    }
}
