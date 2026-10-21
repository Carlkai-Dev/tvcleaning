package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.Collections;
import java.util.Map;

/**
 * High-performance remote key and window manager for Fire TV:
 * Intercepts KEYCODE_HOME and window state changes with a stealth visual mask
 * and resilient retry loop to completely eliminate any flash of the Amazon stock launcher.
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

    private WindowManager windowManager;
    private View maskView = null;
    private boolean maskAttached = false;
    private static final long MASK_HARD_TIMEOUT_MS = 6000; // 6.0s hard safety backstop

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private boolean isRetrying = false;

    private final Runnable maskTimeoutRunnable = () -> {
        Log.w(TAG, "Mask hard-timeout reached; removing mask");
        hideMask();
    };

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRetrying) return;
            if (retryCount < 40) { // Retries every 150ms for up to ~6.0s (outlasts 5.0s stopAppSwitches)
                retryCount++;
                performTakeover();
                handler.postDelayed(this, 150);
            } else {
                isRetrying = false;
                hideMask();
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
            hideMask();
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
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
    }

    private View createMaskView() {
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0xFF000000); // 100% Pitch black stealth base

        Bitmap snapshot = MainActivity.sLastSnapshot;
        if (snapshot != null && !snapshot.isRecycled()) {
            ImageView img = new ImageView(this);
            img.setImageBitmap(snapshot);
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            container.addView(img, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            // Elegant centered CleanYourTV banner
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.banner);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            container.addView(logo, lp);
        }
        return container;
    }

    private WindowManager.LayoutParams getMaskLayoutParams() {
        // Flags: 792 (0x318) = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.FILL;
        return params;
    }

    private synchronized void showMask() {
        if (maskAttached) return;
        handler.post(() -> {
            if (maskAttached) return;
            try {
                if (windowManager == null) {
                    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                }
                maskView = createMaskView();
                windowManager.addView(maskView, getMaskLayoutParams());
                maskAttached = true;

                // Arm the hard safety timeout once
                handler.removeCallbacks(maskTimeoutRunnable);
                handler.postDelayed(maskTimeoutRunnable, MASK_HARD_TIMEOUT_MS);
            } catch (Throwable t) {
                Log.w(TAG, "showMask failed", t);
                maskAttached = false;
                maskView = null;
            }
        });
    }

    private synchronized void hideMask() {
        handler.post(() -> {
            handler.removeCallbacks(maskTimeoutRunnable);
            if (!maskAttached || maskView == null) return;
            final View viewToRemove = maskView;
            maskAttached = false;
            maskView = null;

            try {
                viewToRemove.animate()
                        .alpha(0.0f)
                        .setDuration(120)
                        .withEndAction(() -> {
                            try {
                                if (viewToRemove.isAttachedToWindow() && windowManager != null) {
                                    windowManager.removeView(viewToRemove);
                                }
                            } catch (Throwable ignored) {}
                        })
                        .start();
            } catch (Throwable t) {
                try {
                    if (viewToRemove.isAttachedToWindow() && windowManager != null) {
                        windowManager.removeView(viewToRemove);
                    }
                } catch (Throwable ignored) {}
            }
        });
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
        // 1. Move task to front
        try {
            int taskId = MainActivity.mainTaskId;
            if (taskId > 0) {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am != null) {
                    am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Direct startActivity
        launchMainActivity();
    }

    private void launchCleanYourTV() {
        long now = System.currentTimeMillis();
        if (now - lastLaunchTime < 100) return;
        lastLaunchTime = now;

        showMask();
        performTakeover();

        if (!isRetrying) {
            isRetrying = true;
            retryCount = 0;
            handler.removeCallbacks(retryRunnable);
            handler.postDelayed(retryRunnable, 100);
        }
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
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
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        // STRICT SAFETY: Do NOT react to our own package events.
        // Adding maskView causes TYPE_WINDOW_STATE_CHANGED for com.gathin.tvhome.
        if (pkg.equals(getPackageName())) {
            return;
        }

        if (pkg.equals(AMAZON_LAUNCHER) || pkg.equals(AMAZON_HOMESTARTER)) {
            launchCleanYourTV();
        }
    }

    @Override
    public void onInterrupt() {
        isRetrying = false;
        handler.removeCallbacks(retryRunnable);
        hideMask();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        isRetrying = false;
        handler.removeCallbacks(retryRunnable);
        hideMask();
    }
}
