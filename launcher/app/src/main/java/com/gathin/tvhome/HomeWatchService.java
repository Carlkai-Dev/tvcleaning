package com.gathin.tvhome;

import android.Manifest;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Fire TV: Fire OS sends Home to Amazon's launcher and won't let it be disabled or out-ranked. This service
 * follows ActivityManager's log and, the moment the system starts Amazon's home activity, opens this launcher
 * on top of it. Needs READ_LOGS, granted over adb (see FIRETV.md); without it the service stops itself.
 *
 * After a Home press Android holds back activity starts from other apps for five seconds, so the launcher only
 * appears then. To hide Amazon's home meanwhile, the service puts a snapshot of the launcher over the screen
 * (an overlay is not held back; needs the SYSTEM_ALERT_WINDOW app-op) and drops it once the launcher is up.
 * Amazon's launcher itself stays resident: Fire OS refuses to kill it ("Cannot kill protected package").
 */
public class HomeWatchService extends Service {

    private static final String TAG = "HomeWatch";
    /** ActivityManager logs "START u0 {act=… cat=[android.intent.category.HOME] … cmp=<home>…}" for every Home. */
    private static final String AMAZON_HOME = "cmp=com.amazon.tv.launcher/.ui.HomeActivity";
    /** Longer than Android's five-second app-switch hold, in case the launcher never comes up. */
    private static final long COVER_TIMEOUT_MS = 8000L;

    /** Latest frame of the launcher, kept fresh by MainActivity. Same process, so a static. */
    static Bitmap snapshot;
    private static HomeWatchService running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Thread reader;
    private volatile Process logcat;
    private View cover;
    private final Runnable dropCover = this::removeCover;

    static void start(Context ctx) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) return;
        ctx.startService(new Intent(ctx, HomeWatchService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = this;
        if (reader == null || !reader.isAlive()) {
            reader = new Thread(this::follow, TAG);
            reader.start();
        }
        return START_STICKY;
    }

    private void follow() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // -T 1: start at the newest line, so a Home press from before the service started is not replayed.
                logcat = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "ActivityManager:I", "*:S")
                        .redirectErrorStream(true).start();
                BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("START u0") && line.contains("android.intent.category.HOME")
                            && line.contains(AMAZON_HOME)) {
                        final long seen = SystemClock.uptimeMillis();
                        handler.post(() -> goHome(seen));
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "logcat ended", e);
            }
            if (logcat != null) logcat.destroy();
            try {
                Thread.sleep(1000);   // logcat died (log rotation, OOM): follow it again
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Called by MainActivity once it is on screen again. */
    static void launcherShown() {
        HomeWatchService s = running;
        if (s != null && s.cover != null) s.handler.postDelayed(s.dropCover, 150);   // let its first frame land
    }

    private void showCover() {
        if (cover != null || (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this))) return;
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setBackgroundColor(Color.BLACK);
        if (snapshot != null) v.setImageBitmap(snapshot);
        v.setFocusable(true);
        v.setOnKeyListener((view, keyCode, event) -> true);   // swallow keys meant for Amazon's home underneath
        @SuppressWarnings("deprecation")
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(v, lp);
            cover = v;
            v.requestFocus();
            handler.postDelayed(dropCover, COVER_TIMEOUT_MS);
        } catch (RuntimeException e) {
            Log.w(TAG, "cover failed", e);
        }
    }

    private void removeCover() {
        handler.removeCallbacks(dropCover);
        if (cover == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        } catch (RuntimeException ignored) {
        }
        cover = null;
    }

    private void goHome(long seen) {
        showCover();
        Intent home = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(home);
            Log.i(TAG, "home bounce after " + (SystemClock.uptimeMillis() - seen) + " ms");
        } catch (ActivityNotFoundException ignored) {
            // Launcher disabled: leave Home to Amazon.
        }
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        removeCover();
        if (reader != null) reader.interrupt();
        if (logcat != null) logcat.destroy();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
