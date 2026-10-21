package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;
import java.util.Map;

/**
 * High-performance remote key and window manager for Fire TV:
 * Intercepts KEYCODE_HOME and window state changes with instant task reordering,
 * system back action, and a resilient non-blocking retry loop to reliably take over
 * from the stock Amazon launcher without blocking or freezing the screen.
 */
public class HomeKeyService extends AccessibilityService {

    private static final String TAG = "HomeKeyService";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOMESTARTER = "com.amazon.firehomestarter";

    public static volatile HomeKeyService instance;

    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    private Map<Integer, String> appKeys = Collections.emptyMap();

    private long lastLaunchTime = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private boolean isRetrying = false;

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRetrying) return;
            if (retryCount < 35) { // Retries every 200ms for up to 7.0 seconds (safely outlasting 5.0s stopAppSwitches)
                retryCount++;
                performTakeover();
                handler.postDelayed(this, 200);
            } else {
                isRetrying = false;
            }
        }
    };

    public static void notifyCleanYourTVResumed() {
        HomeKeyService current = instance;
        if (current != null) {
            current.onMainActivityResumed();
        }
    }

    public void onMainActivityResumed() {
        handler.post(() -> {
            isRetrying = false;
            handler.removeCallbacks(retryRunnable);
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    private void launchMainActivity() {
        Intent home = new Intent(this, MainActivity.class);
        home.setAction(Intent.ACTION_MAIN);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(home);
        } catch (Exception e) {
            Log.w(TAG, "startActivity failed", e);
        }
    }

    private void performTakeover() {
        // 1. Instant task reordering: bring existing MainActivity task to front
        try {
            int taskId = MainActivity.mainTaskId;
            if (taskId > 0) {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am != null) {
                    am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION);
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "moveTaskToFront failed: " + t.getMessage());
        }

        // 2. Instant dismissal of Amazon Launcher: simulate system Back action
        try {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Throwable ignored) {}

        // 3. Fallback direct startActivity
        launchMainActivity();
    }

    private void launchCleanYourTV() {
        long now = System.currentTimeMillis();
        if (now - lastLaunchTime < 100) return;
        lastLaunchTime = now;

        performTakeover();

        if (!isRetrying) {
            isRetrying = true;
            retryCount = 0;
            handler.postDelayed(retryRunnable, 200);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // Fast key-level interception
        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_ALL_APPS) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchCleanYourTV();
            }
            return true;
        }

        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }

        String app = appKeys.get(keyCode);
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkgSeq = event.getPackageName();
            if (pkgSeq != null) {
                String pkg = pkgSeq.toString();
                if (pkg.equals(AMAZON_LAUNCHER) || pkg.equals(AMAZON_HOMESTARTER)) {
                    launchCleanYourTV();
                } else if (pkg.equals(getPackageName())) {
                    onMainActivityResumed();
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        isRetrying = false;
        handler.removeCallbacks(retryRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        isRetrying = false;
        handler.removeCallbacks(retryRunnable);
    }
}
