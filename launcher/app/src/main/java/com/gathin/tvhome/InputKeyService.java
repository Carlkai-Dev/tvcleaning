package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it; on Google TV the stock launcher handles that key, so with the stock launcher
 * disabled the button does nothing. Also opens one app on another button (config.xml, app_key_scancodes and
 * app_key_package), e.g. the TCL remote's TV button. Only those keys are consumed; every other key passes through.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private int[] appScancodes = new int[0];
    private String appPackage = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        appScancodes = getResources().getIntArray(R.array.app_key_scancodes);
        appPackage = getString(R.string.app_key_package).trim();
    }

    /** Leanback entry first, then the phone-style one; null when the app is missing or disabled. */
    private Intent appIntent() {
        if (appPackage.isEmpty()) return null;
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(appPackage);
        if (intent == null) intent = pm.getLaunchIntentForPackage(appPackage);
        return intent;
    }

    private boolean isAppKey(KeyEvent event) {
        for (int code : appScancodes) if (event.getScanCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (isAppKey(event)) {
            Intent app = appIntent();
            if (app == null) return false;   // app gone: let the button do what the firmware does
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                try {
                    startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ignored) {
                }
            }
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_TV) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            try {
                startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
