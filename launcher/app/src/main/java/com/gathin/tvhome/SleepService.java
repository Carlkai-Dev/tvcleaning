package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Turns the TV off for Control Center and the sleep timer. Many Android TVs ship without device admin
 * support, so this uses the accessibility "lock screen" action instead, which puts the TV in standby.
 * It listens to no events and reads no screen content (res/xml/sleep_service.xml); build-and-install.sh
 * switches it on over adb.
 */
public class SleepService extends AccessibilityService {

    private static SleepService running;

    /** True when the TV went to standby; false when the service is not switched on. */
    static boolean lock() {
        return running != null && running.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected void onServiceConnected() {
        running = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        running = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
