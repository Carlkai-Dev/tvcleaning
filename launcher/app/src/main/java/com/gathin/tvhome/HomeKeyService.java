package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;

/**
 * Ultra-fast zero-latency Home button interception and window manager for Fire TV:
 * - Uses continuous real-time system logcat streaming to detect Amazon Home launch at 0ms.
 * - Instantly displays a pitch-black stealth overlay mask BEFORE Amazon renders a single frame.
 * - Sustains a takeover loop through Fire OS's 5.0-second stopAppSwitches lock.
 * - Seamlessly reveals CleanYourTV the moment it resumes.
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
    private static final long MASK_HARD_TIMEOUT_MS = 6500; // 6.5s hard safety backstop

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private boolean isRetrying = false;

    // Zero-latency background logcat monitor
    private Thread logcatThread = null;
    private volatile boolean logcatRunning = false;

    private final Runnable maskTimeoutRunnable = () -> {
        Log.w(TAG, "Mask hard-timeout reached; removing mask");
        hideMask();
    };

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRetrying) return;
            if (retryCount < 55) { // Retries every 100ms for up to 5.5s (outlasts 5.0s stopAppSwitches)
                retryCount++;
                performTakeover();
                handler.postDelayed(this, 100);
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

        // Explicitly register runtime key filtering flags
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                info = new AccessibilityServiceInfo();
            }
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
            Log.i(TAG, "setServiceInfo SUCCESS! flags=" + info.flags);
        } catch (Throwable t) {
            Log.e(TAG, "setServiceInfo FAILED", t);
        }

        // 1. Prewarm WindowManager overlay render pipeline
        prewarmMask();

        // 2. Start zero-latency logcat monitor daemon
        startLogcatMonitor();
    }

    /**
     * Prewarms the WindowManager overlay path with a tiny 1x1 view so GPU shaders
     * and window tokens are pre-cached, eliminating cold-start overlay latency.
     */
    private void prewarmMask() {
        handler.postDelayed(() -> {
            try {
                if (windowManager == null) {
                    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                }
                if (windowManager != null && !maskAttached) {
                    View warm = new View(this);
                    WindowManager.LayoutParams lp = getMaskLayoutParams();
                    lp.width = 1;
                    lp.height = 1;
                    windowManager.addView(warm, lp);
                    handler.postDelayed(() -> {
                        try {
                            if (warm.isAttachedToWindow() && windowManager != null) {
                                windowManager.removeView(warm);
                            }
                        } catch (Throwable ignored) {}
                    }, 80);
                    Log.i(TAG, "Overlay render pipeline successfully prewarmed");
                }
            } catch (Throwable t) {
                Log.w(TAG, "prewarmMask notice", t);
            }
        }, 500);
    }

    /**
     * Spawns a high-priority background thread to stream ActivityManager logcat events.
     * Fires at 0ms the moment PhoneWindowManager issues the HOME intent.
     */
    private void startLogcatMonitor() {
        if (logcatRunning) return;
        logcatRunning = true;
        logcatThread = new Thread(() -> {
            Log.i(TAG, "Zero-latency logcat monitor active");
            while (logcatRunning) {
                Process process = null;
                BufferedReader reader = null;
                try {
                    // Stream only recent and new ActivityManager events from system and main buffers
                    process = Runtime.getRuntime().exec(new String[]{
                            "logcat", "-v", "brief", "-b", "system", "-b", "main", "-T", "1", "-s", "ActivityManager:I"
                    });
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()), 1024);
                    String line;
                    while (logcatRunning && (line = reader.readLine()) != null) {
                        // Look for START of Amazon launcher
                        if (line.contains("cmp=com.amazon.tv.launcher") || line.contains("cmp=com.amazon.firehomestarter")) {
                            if (line.contains("START") || line.contains("category.HOME") || line.contains("HomeActivity")) {
                                Log.i(TAG, ">>> [0ms Trigger] Detected Amazon Launcher start in logcat: " + line);
                                handler.post(this::launchCleanYourTV);
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Logcat monitor exception: " + t.getMessage());
                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Throwable ignored) {}
                    }
                    if (process != null) {
                        try { process.destroy(); } catch (Throwable ignored) {}
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }, "HomeLogcatMonitor");
        logcatThread.setPriority(Thread.MAX_PRIORITY);
        logcatThread.start();
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
        Runnable attach = () -> {
            if (maskAttached) return;
            try {
                if (windowManager == null) {
                    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                }
                maskView = createMaskView();
                windowManager.addView(maskView, getMaskLayoutParams());
                maskAttached = true;
                Log.i(TAG, "Stealth mask attached to WindowManager");

                // Arm the hard safety timeout once
                handler.removeCallbacks(maskTimeoutRunnable);
                handler.postDelayed(maskTimeoutRunnable, MASK_HARD_TIMEOUT_MS);
            } catch (Throwable t) {
                Log.w(TAG, "showMask failed", t);
                maskAttached = false;
                maskView = null;
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            attach.run();
        } else {
            handler.post(attach);
        }
    }

    private synchronized void hideMask() {
        Runnable detach = () -> {
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
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            detach.run();
        } else {
            handler.post(detach);
        }
    }

    private void launchMainActivity() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setComponent(new ComponentName(this, MainActivity.class));
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
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
        int action = event.getAction();

        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_ALL_APPS) {
            if (action == KeyEvent.ACTION_UP && !event.isCanceled()) {
                launchCleanYourTV();
            }
            return true;
        }

        if (isSettingsKey(event)) {
            if (action == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }

        String app = appKeys.get(keyCode);
        if (app != null) {
            if (action == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        // STRICT SAFETY: Do NOT react to our own package events.
        if (pkg.equals(getPackageName())) {
            return;
        }

        if (pkg.equals(AMAZON_LAUNCHER) || pkg.equals(AMAZON_HOMESTARTER)) {
            Log.i(TAG, ">>> [Accessibility Backup] detected " + pkg + ", triggering launchCleanYourTV()");
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
        logcatRunning = false;
        if (logcatThread != null) {
            logcatThread.interrupt();
            logcatThread = null;
        }
        if (instance == this) instance = null;
        isRetrying = false;
        handler.removeCallbacks(retryRunnable);
        hideMask();
    }
}
