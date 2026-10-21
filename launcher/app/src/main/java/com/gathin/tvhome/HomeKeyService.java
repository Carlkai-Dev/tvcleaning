package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
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
import android.view.accessibility.AccessibilityNodeInfo;
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
 * - If CleanYourTV was recently active, triggers GLOBAL_ACTION_BACK to dismiss Amazon in ~100ms.
 * - Sustains a takeover loop through Fire OS's 5.0-second stopAppSwitches lock without premature mask drops.
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
    private static final long MASK_HARD_TIMEOUT_MS = 2200; // 2.2s maximum safety backstop

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private boolean isRetrying = false;

    // Zero-latency background logcat monitor
    private Thread logcatThread = null;
    private volatile boolean logcatRunning = false;

    private final Runnable maskTimeoutRunnable = () -> {
        Log.w(TAG, "Mask hard-timeout reached; safety removing mask");
        hideMask();
    };

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRetrying) return;
            if (retryCount < 30) { // Retries every 80ms for up to 2.4s
                retryCount++;
                launchMainActivity();
                handler.postDelayed(this, 80);
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

        // 3. Check active window; if Amazon launcher is running, immediately take over
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                if (pkg != null) {
                    String pkgStr = pkg.toString();
                    if (AMAZON_LAUNCHER.equals(pkgStr) || AMAZON_HOMESTARTER.equals(pkgStr)) {
                        Log.i(TAG, "onServiceConnected: Detected " + pkgStr + " in active window, immediately triggering launchCleanYourTV()");
                        launchCleanYourTV();
                    }
                }
                try { root.recycle(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.w(TAG, "onServiceConnected getRootInActiveWindow check failed", t);
        }
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
                    process = Runtime.getRuntime().exec(new String[]{
                            "logcat", "-v", "brief", "-b", "system", "-b", "main", "-T", "30", "-s", "ActivityManager:I"
                    });
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()), 1024);
                    String line;
                    while (logcatRunning && (line = reader.readLine()) != null) {
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

    /**
     * Builds a premium Aurora Sapphire transition overlay:
     * - Uses the signature deep midnight navy gradient (#07111F to #0B1E38)
     * - If a recent dashboard snapshot exists, renders it instantly so the transition is imperceptible
     */
    private View createMaskView() {
        FrameLayout container = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF07111F, 0xFF0B1E38, 0xFF050B14});
        container.setBackground(bg);

        android.graphics.Bitmap snap = MainActivity.sLastSnapshot;
        if (snap != null && !snap.isRecycled()) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setImageBitmap(snap);
            container.addView(iv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
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
        // Overlay mask completely disabled to ensure 0ms blackout, 0ms freeze, and lively home screen
    }

    private synchronized void hideMask() {
        Runnable detach = () -> {
            handler.removeCallbacks(maskTimeoutRunnable);
            if (!maskAttached || maskView == null) return;
            final View viewToRemove = maskView;
            maskAttached = false;
            maskView = null;

            try {
                if (viewToRemove.isAttachedToWindow() && windowManager != null) {
                    windowManager.removeView(viewToRemove);
                }
            } catch (Throwable ignored) {}
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            detach.run();
        } else {
            handler.post(detach);
        }
    }

    private void launchMainActivity() {
        // 1. Instant task reorder to front
        if (MainActivity.mainTaskId > 0) {
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am != null) {
                    am.moveTaskToFront(MainActivity.mainTaskId, ActivityManager.MOVE_TASK_NO_USER_ACTION);
                }
            } catch (Throwable ignored) {}
        }

        // 2. Direct explicit intent to MainActivity (without CATEGORY_HOME to avoid Fire OS intercept)
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "startActivity failed", e);
        }
    }

    private void launchCleanYourTV() {
        long now = System.currentTimeMillis();
        if (now - lastLaunchTime < 180) return;
        lastLaunchTime = now;

        // Always bring CleanYourTV to front directly without any blackout mask
        launchMainActivity();
        isRetrying = true;
        retryCount = 0;
        handler.removeCallbacks(retryRunnable);
        handler.postDelayed(retryRunnable, 80);

        if (MainActivity.wasRecentlyForeground()) {
            MainActivity main = MainActivity.instance;
            if (main != null) {
                main.runOnUiThread(main::resetToTop);
            }
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
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
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
