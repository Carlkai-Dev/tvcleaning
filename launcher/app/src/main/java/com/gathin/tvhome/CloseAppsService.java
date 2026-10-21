package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Free memory: a remote button (close_apps_keycodes) or the quick settings panel's tile force-stops the apps used in
 * the last hours through the TV's Settings (RecentAppsCloser). A service of its own, apart from InputKeyService,
 * because it can read the screen and take a screenshot, which the key helpers never need.
 * Opt-in: CLOSE_APPS_SERVICE=1 build-and-install.sh (or -CloseAppsService on Windows) enables it.
 */
public class CloseAppsService extends AccessibilityService {

    /** The running service, for the quick settings panel's Free memory tile (same process). */
    private static CloseAppsService instance;
    private int[] closeAppsKeycodes = new int[0];

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        closeAppsKeycodes = Config.load(this).closeAppsKeycodes;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        for (int code : closeAppsKeycodes) {
            if (event.getKeyCode() != code) continue;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RecentAppsCloser.start(this);
            return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    static CloseAppsService current() {
        return instance;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
