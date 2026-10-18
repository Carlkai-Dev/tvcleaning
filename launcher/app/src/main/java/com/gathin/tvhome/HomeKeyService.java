package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * For TVs where the stock launcher can't be disabled (Google TV's setup wizard then takes over Home
 * with a recovery screen). The stock launcher stays the system home, and this service sends the
 * user here instead: it catches the Home key, and leaves the stock launcher whenever it comes to the
 * front (after boot, or Back out of an app). If this launcher fails, the stock one still works.
 * Some firmwares (TCL) handle Home before accessibility sees it; the window change still catches it.
 *
 * Enable over adb: settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 */
public class HomeKeyService extends AccessibilityService {

    /** At most this many redirects per window, so a crashing launcher can't bounce forever. */
    private static final int MAX_REDIRECTS = 3;
    private static final long WINDOW_MS = 10_000L;

    private String stockLauncher;
    private long windowStart;
    private int redirects;

    @Override
    protected void onServiceConnected() {
        stockLauncher = getString(R.string.stock_launcher_package);
        goHome();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg != null && !stockLauncher.isEmpty() && stockLauncher.contentEquals(pkg)) goHome();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            redirects = 0;
            goHome();
        }
        return true;
    }

    private void goHome() {
        long now = SystemClock.elapsedRealtime();
        if (now - windowStart > WINDOW_MS) {
            windowStart = now;
            redirects = 0;
        }
        if (++redirects > MAX_REDIRECTS) return;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    @Override
    public void onInterrupt() {
    }
}
