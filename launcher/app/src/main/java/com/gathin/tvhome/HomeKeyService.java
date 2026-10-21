package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
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
 * Intercepts KEYCODE_HOME and window state changes with a resilient retry loop
 * to reliably take over from the stock Amazon launcher without blocking or freezing the screen.
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

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (retryCount < 40) { // Retries every 150ms for up to 6.0 seconds
                retryCount++;
                launchMainActivity();
                handler.postDelayed(this, 150);
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

    private void launchCleanYourTV() {
        long now = System.currentTimeMillis();
        if (now - lastLaunchTime > 150) {
            lastLaunchTime = now;
            launchMainActivity();
            retryCount = 0;
            handler.removeCallbacks(retryRunnable);
            handler.postDelayed(retryRunnable, 150);
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
        handler.removeCallbacks(retryRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        handler.removeCallbacks(retryRunnable);
    }
}
