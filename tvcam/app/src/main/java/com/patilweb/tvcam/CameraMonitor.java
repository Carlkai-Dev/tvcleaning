package com.patilweb.tvcam;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks whether a USB / any camera is currently available on the TV.
 */
final class CameraMonitor {
    private static final String TAG = "TvCamMonitor";

    interface Listener {
        void onCameraAvailabilityChanged(boolean connected);
    }

    private static CameraMonitor instance;

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private CameraManager.AvailabilityCallback callback;
    private volatile boolean connected;

    private CameraMonitor(Context context) {
        app = context.getApplicationContext();
        connected = probeConnected();
        register();
    }

    static void init(Context context) {
        if (instance == null) instance = new CameraMonitor(context);
    }

    static CameraMonitor get() {
        return instance;
    }

    boolean isConnected() {
        return connected;
    }

    void addListener(Listener l) {
        if (l != null) listeners.addIfAbsent(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Re-scan camera list (e.g. after permission grant). */
    void refresh() {
        boolean now = probeConnected();
        if (now != connected) {
            connected = now;
            notifyListeners();
        } else {
            connected = now;
        }
    }

    private void register() {
        CameraManager mgr = app.getSystemService(CameraManager.class);
        if (mgr == null) return;
        callback = new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraAvailable(String cameraId) {
                Log.i(TAG, "available " + cameraId);
                ui.post(() -> setConnected(probeConnected()));
            }

            @Override
            public void onCameraUnavailable(String cameraId) {
                Log.i(TAG, "unavailable " + cameraId);
                ui.post(() -> setConnected(probeConnected()));
            }
        };
        try {
            mgr.registerAvailabilityCallback(callback, ui);
        } catch (Exception e) {
            Log.w(TAG, "registerAvailabilityCallback", e);
        }
    }

    private void setConnected(boolean now) {
        if (connected == now) return;
        connected = now;
        Log.i(TAG, "camera connected=" + now);
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener l : listeners) {
            try {
                l.onCameraAvailabilityChanged(connected);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean probeConnected() {
        CameraManager mgr = app.getSystemService(CameraManager.class);
        if (mgr == null) return false;
        try {
            String[] ids = mgr.getCameraIdList();
            if (ids == null || ids.length == 0) return false;
            // Prefer external USB; otherwise any camera counts as connected.
            for (String id : ids) {
                CameraCharacteristics c = mgr.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    return true;
                }
            }
            return ids.length > 0;
        } catch (Exception e) {
            Log.w(TAG, "probeConnected", e);
            return false;
        }
    }
}
