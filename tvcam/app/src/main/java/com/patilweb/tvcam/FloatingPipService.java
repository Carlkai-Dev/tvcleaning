package com.patilweb.tvcam;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * System overlay PiP so the live USB camera stays visible over other apps.
 */
public class FloatingPipService extends Service {
    private static final String TAG = "TvCamPip";
    private static final String CHANNEL = "tvcam_pip";
    private static final int NOTIF_ID = 3002;

    private static FloatingPipService instance;

    private WindowManager windowManager;
    private View overlay;
    private TextureView textureView;
    private TextView badge;
    private boolean wantVisible;

    static boolean canDrawOverlays(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            if (Settings.canDrawOverlays(ctx)) return true;
        } catch (Exception ignored) {
        }
        // Some TV builds lag Settings.canDrawOverlays behind AppOps.
        try {
            AppOpsManager ops = ctx.getSystemService(AppOpsManager.class);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    static void start(Context ctx) {
        if (!Prefs.pipActive(ctx)) {
            Log.i(TAG, "start skipped: pip inactive (pref/camera)");
            return;
        }
        if (!canDrawOverlays(ctx)) {
            Log.i(TAG, "start skipped: no overlay permission");
            return;
        }
        Log.i(TAG, "starting FloatingPipService");
        ContextCompat.startForegroundService(ctx, new Intent(ctx, FloatingPipService.class));
    }

    static void stop(Context ctx) {
        Log.i(TAG, "stopping FloatingPipService");
        ctx.stopService(new Intent(ctx, FloatingPipService.class));
    }

    static void sync(Context ctx) {
        if (Prefs.pipActive(ctx) && canDrawOverlays(ctx)) {
            start(ctx);
            boolean inApp = TvCamApp.get() != null && TvCamApp.get().isInApp();
            setVisible(!inApp);
        } else {
            stop(ctx);
        }
    }

    /** Show overlay over other apps; hide while TV Cam UI is open. */
    static void setVisible(boolean visible) {
        FloatingPipService s = instance;
        if (s == null) return;
        s.wantVisible = visible;
        s.applyVisibility();
    }

    static void updateBadge(String text, int color) {
        FloatingPipService s = instance;
        if (s != null) s.setBadge(text, color);
    }

    static void refreshLayout() {
        FloatingPipService s = instance;
        if (s != null) s.updateLayoutParams();
    }

    static boolean isRunning() {
        return instance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wantVisible = true;
        ensureChannel();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Log.i(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (!Prefs.pipActive(this) || !canDrawOverlays(this)) {
            Log.w(TAG, "self-stop: pipActive=" + Prefs.pipActive(this)
                    + " pref=" + Prefs.pipOverview(this)
                    + " overlay=" + canDrawOverlays(this));
            removeOverlay();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean inApp = TvCamApp.get() != null && TvCamApp.get().isInApp();
        wantVisible = !inApp;
        showOverlay();
        applyVisibility();
        return START_STICKY;
    }

    private void startAsForeground() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Notification n = nb
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.pip_overlay_running))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);
    }

    private void showOverlay() {
        if (overlay != null) {
            updateLayoutParams();
            return;
        }
        if (windowManager == null) return;

        overlay = LayoutInflater.from(this).inflate(R.layout.floating_pip, null);
        textureView = overlay.findViewById(R.id.floatPreview);
        badge = overlay.findViewById(R.id.floatBadge);
        badge.setText("LIVE");

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int w, int h) {
                Log.i(TAG, "float surface ready " + w + "x" + h);
                CamHub.get().onFloatingSurfaceReady(textureView, surface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int w, int h) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                Log.i(TAG, "float surface destroyed");
                CamHub.get().onFloatingSurfaceDestroyed();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });

        try {
            windowManager.addView(overlay, buildLayoutParams());
            Log.i(TAG, "overlay added visible=" + wantVisible);
        } catch (Exception e) {
            Log.e(TAG, "addView failed", e);
            overlay = null;
            stopSelf();
        }
    }

    private void applyVisibility() {
        if (overlay == null) return;
        // INVISIBLE keeps the TextureView surface alive; GONE would destroy it.
        overlay.setVisibility(wantVisible ? View.VISIBLE : View.INVISIBLE);
        Log.i(TAG, "applyVisibility " + (wantVisible ? "VISIBLE" : "INVISIBLE"));
        if (wantVisible && textureView != null && textureView.isAvailable()
                && textureView.getSurfaceTexture() != null) {
            CamHub.get().onFloatingSurfaceReady(textureView, textureView.getSurfaceTexture());
        }
    }

    private void updateLayoutParams() {
        if (overlay == null || windowManager == null) return;
        try {
            windowManager.updateViewLayout(overlay, buildLayoutParams());
        } catch (Exception ignored) {
        }
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        int size = Prefs.pipSizePx(this);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        float density = getResources().getDisplayMetrics().density;
        int bottomReserve = Math.round(96f * density);
        PipLayout.applyWindow(this, lp, size,
                Prefs.pipXPct(this), Prefs.pipYPct(this), bottomReserve);
        return lp;
    }

    void setBadge(String text, int color) {
        if (badge == null) return;
        badge.setText(text);
        badge.setTextColor(color);
    }

    private void removeOverlay() {
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeView(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
        textureView = null;
        badge = null;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, getString(R.string.pip_overlay_channel),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        removeOverlay();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
