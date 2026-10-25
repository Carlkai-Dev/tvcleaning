package com.patilweb.tvcam;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * App-wide camera + motion + PiP. Keeps a live overview over other apps when
 * floating PiP (overlay permission) is enabled.
 */
final class CamHub implements CameraEngine.Listener {
    private static final String TAG = "TvCamHub";
    private static final long MOTION_QUIET_MS = 4000;
    private static final long POLL_MS = 250;

    private static CamHub instance;

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final CameraEngine engine;
    private WeakReference<TextureView> mainPreviewRef = new WeakReference<>(null);
    private WeakReference<TextureView> floatPreviewRef = new WeakReference<>(null);
    private WeakReference<CameraEngine.Listener> uiListener = new WeakReference<>(null);
    private Runnable poll;
    private Runnable segmentRotator;
    private boolean motionToastShown;
    private boolean running;
    private boolean usingFloating;
    private boolean backgroundArmed;

    private CamHub(Context context) {
        app = context.getApplicationContext();
        engine = new CameraEngine(app, this);
        engine.startBackground();
    }

    static void init(Context context) {
        if (instance == null) instance = new CamHub(context);
    }

    static CamHub get() {
        return instance;
    }

    CameraEngine engine() {
        return engine;
    }

    void setPreview(TextureView preview) {
        mainPreviewRef = new WeakReference<>(preview);
    }

    void setUiListener(CameraEngine.Listener listener) {
        uiListener = new WeakReference<>(listener);
    }

    void onAppResumed() {
        Log.i(TAG, "onAppResumed");
        backgroundArmed = false;
        engine.setLoopMode(Prefs.loopArmed(app));
        engine.setMotionMode(Prefs.motionArmed(app));
        usingFloating = false;
        FloatingPipService.setVisible(false);
        TextureView main = mainPreviewRef.get();
        if (main != null && main.isAvailable() && main.getSurfaceTexture() != null) {
            engine.attachPreview(main.getSurfaceTexture());
        }
        startPoll();
        PipOverlay.refreshVisibility();
        maybeStartService();
    }

    void onAppBackgrounded() {
        if (backgroundArmed) {
            // Already switched once for this background session.
            FloatingPipService.setVisible(true);
            return;
        }
        backgroundArmed = true;
        Log.i(TAG, "onAppBackgrounded pipActive=" + Prefs.pipActive(app)
                + " pref=" + Prefs.pipOverview(app)
                + " overlay=" + FloatingPipService.canDrawOverlays(app));
        PipOverlay.detach(null);

        if (Prefs.pipActive(app) && FloatingPipService.canDrawOverlays(app)) {
            cancelSegmentRotator();
            if (engine.hasActiveClip()) {
                engine.stopRecordingSync();
            }
            FloatingPipService.start(app);
            FloatingPipService.setVisible(true);
            usingFloating = true;
            startPoll();
            TextureView flt = floatPreviewRef.get();
            if (flt != null && flt.isAvailable() && flt.getSurfaceTexture() != null) {
                Log.i(TAG, "attach camera to floating now");
                engine.attachPreview(flt.getSurfaceTexture());
            } else {
                Log.i(TAG, "floating surface not ready yet — will attach on callback");
            }
            ui.postDelayed(() -> {
                engine.setLoopMode(Prefs.loopArmed(app));
                engine.setMotionMode(Prefs.motionArmed(app));
                if (Prefs.loopArmed(app) && !engine.hasActiveClip()) {
                    engine.startRecording();
                    scheduleSegmentRotator();
                }
            }, 500);
            maybeStartService();
            return;
        }

        stopPoll();
        FloatingPipService.stop(app);
        cancelSegmentRotator();
        if (engine.hasActiveClip()) {
            engine.stopRecordingSync();
        }
        if (!Prefs.loopArmed(app) && !Prefs.motionArmed(app)) {
            engine.release();
        }
        usingFloating = false;
        backgroundArmed = false;
    }

    void onFloatingSurfaceReady(TextureView view, SurfaceTexture surface) {
        floatPreviewRef = new WeakReference<>(view);
        boolean inApp = TvCamApp.get() != null && TvCamApp.get().isResumed();
        Log.i(TAG, "onFloatingSurfaceReady inApp=" + inApp + " bg=" + backgroundArmed);
        if (inApp) {
            return;
        }
        usingFloating = true;
        engine.attachPreview(surface);
        startPoll();
    }

    void onFloatingSurfaceDestroyed() {
        floatPreviewRef = new WeakReference<>(null);
    }

    private void startPoll() {
        if (running) return;
        running = true;
        poll = () -> {
            tick();
            if (running) ui.postDelayed(poll, POLL_MS);
        };
        ui.post(poll);
    }

    private void stopPoll() {
        running = false;
        if (poll != null) ui.removeCallbacks(poll);
    }

    private void tick() {
        TextureView preview = usingFloating ? floatPreviewRef.get() : mainPreviewRef.get();
        if (preview == null || !preview.isAvailable()) {
            preview = mainPreviewRef.get();
            if (preview == null || !preview.isAvailable()) {
                preview = floatPreviewRef.get();
            }
        }
        if (preview == null || !preview.isAvailable()) return;

        Bitmap frame = null;
        try {
            frame = preview.getBitmap(200, 150);
            if (frame == null) return;

            if (Prefs.motionArmed(app)) {
                boolean moving = engine.motionDetector()
                        .analyzeBitmap(frame, Prefs.motionThreshold(app));
                handleMotionGate(moving);
            }

            String badge = "LIVE";
            int color = app.getColor(R.color.accent_soft);
            if (engine.hasActiveClip() && engine.isPaused()) {
                badge = "PAUSE";
                color = app.getColor(R.color.text_secondary);
            } else if (engine.hasActiveClip()) {
                badge = "REC";
                color = app.getColor(R.color.rec_red);
            } else if (Prefs.motionArmed(app)) {
                badge = "MOTION";
                color = app.getColor(R.color.accent);
            }

            if (Prefs.pipActive(app) && !usingFloating) {
                PipOverlay.showFrame(frame);
                PipOverlay.setBadge(badge, color);
            }
            FloatingPipService.updateBadge(badge, color);
        } catch (Throwable t) {
            Log.w(TAG, "tick", t);
        } finally {
            if (frame != null && !frame.isRecycled()) frame.recycle();
        }
    }

    private void handleMotionGate(boolean moving) {
        if (moving) {
            if (!engine.hasActiveClip()) {
                engine.startRecording();
                scheduleSegmentRotator();
                if (!motionToastShown) {
                    toast("Motion → recording");
                    motionToastShown = true;
                }
            } else if (engine.isPaused()) {
                engine.resumeRecording();
            }
            return;
        }
        if (engine.hasActiveClip()
                && !engine.isPaused()
                && engine.motionDetector().millisSinceMotion() >= MOTION_QUIET_MS) {
            engine.pauseRecording();
            motionToastShown = false;
        }
    }

    void scheduleSegmentRotator() {
        cancelSegmentRotator();
        long ms = Prefs.segmentMinutes(app) * 60_000L;
        segmentRotator = () -> {
            if (engine.hasActiveClip()
                    && (engine.isLoopMode() || Prefs.loopArmed(app) || Prefs.motionArmed(app))) {
                if (!engine.isPaused()) engine.rotateSegment();
                ui.postDelayed(segmentRotator, ms);
            }
        };
        ui.postDelayed(segmentRotator, ms);
    }

    void cancelSegmentRotator() {
        if (segmentRotator != null) {
            ui.removeCallbacks(segmentRotator);
            segmentRotator = null;
        }
    }

    void maybeStartService() {
        if (Prefs.loopArmed(app) || Prefs.motionArmed(app) || Prefs.pipActive(app)) {
            Intent i = new Intent(app, CctvService.class);
            ContextCompat.startForegroundService(app, i);
        }
    }

    /** Camera plugged/unplugged — turn floating PiP on or off to match. */
    void onCameraAvailabilityChanged(boolean connected) {
        Log.i(TAG, "camera availability connected=" + connected);
        ui.post(() -> {
            PipOverlay.refreshVisibility();
            FloatingPipService.sync(app);
            if (!connected) {
                // Keep pref as-is; just stop overlay. Re-open when cam returns if pref ON.
                if (usingFloating) {
                    usingFloating = false;
                }
            } else if (Prefs.pipOverview(app) && !TvCamApp.get().isResumed()) {
                FloatingPipService.sync(app);
            }
            maybeStartService();
        });
    }

    void resetMotionToast() {
        motionToastShown = false;
    }

    private void toast(String msg) {
        ui.post(() -> Toast.makeText(app, msg, Toast.LENGTH_SHORT).show());
    }

    private CameraEngine.Listener ui() {
        return uiListener.get();
    }

    @Override
    public void onCameraReady(String label, Size previewSize) {
        CameraEngine.Listener l = ui();
        if (l != null) l.onCameraReady(label, previewSize);
        if (Prefs.loopArmed(app) && !engine.hasActiveClip()) {
            engine.setLoopMode(true);
            engine.startRecording();
            scheduleSegmentRotator();
        }
    }

    @Override
    public void onCameraError(String message) {
        CameraEngine.Listener l = ui();
        if (l != null) l.onCameraError(message);
    }

    @Override
    public void onRecordingStarted(File file) {
        CameraEngine.Listener l = ui();
        if (l != null) l.onRecordingStarted(file);
    }

    @Override
    public void onRecordingStopped(File file) {
        CameraEngine.Listener l = ui();
        if (l != null) l.onRecordingStopped(file);
        ClipStore.pruneOld(app);
    }

    @Override
    public void onRecordingPaused() {
        CameraEngine.Listener l = ui();
        if (l != null) l.onRecordingPaused();
        toast("Still → recording paused");
    }

    @Override
    public void onRecordingResumed() {
        CameraEngine.Listener l = ui();
        if (l != null) l.onRecordingResumed();
        toast("Motion → recording resumed");
    }

    @Override
    public void onSnapshotSaved(File file) {
        CameraEngine.Listener l = ui();
        if (l != null) l.onSnapshotSaved(file);
    }
}
