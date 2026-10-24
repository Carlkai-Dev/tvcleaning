package com.patilweb.tvcam;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements CameraEngine.Listener {
    private static final int REQ_PERMS = 42;

    private TextureView preview;
    private TextView statusBadge;
    private TextView cameraLabel;
    private TextView clockLabel;
    private TextView messageLabel;
    private View recDot;
    private Button btnRecord;
    private Button btnLoop;
    private Button btnMotion;
    private Button btnPip;

    private CameraEngine engine;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFmt =
            new SimpleDateFormat("EEE MMM d  HH:mm:ss", Locale.getDefault());
    private Runnable clockTick;
    private boolean surfaceReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.preview);
        statusBadge = findViewById(R.id.statusBadge);
        cameraLabel = findViewById(R.id.cameraLabel);
        clockLabel = findViewById(R.id.clockLabel);
        messageLabel = findViewById(R.id.messageLabel);
        recDot = findViewById(R.id.recDot);
        btnRecord = findViewById(R.id.btnRecord);
        btnLoop = findViewById(R.id.btnLoop);
        btnMotion = findViewById(R.id.btnMotion);
        btnPip = findViewById(R.id.btnPip);
        Button btnSnap = findViewById(R.id.btnSnap);
        Button btnGallery = findViewById(R.id.btnGallery);
        Button btnSettings = findViewById(R.id.btnSettings);

        CamHub hub = CamHub.get();
        engine = hub.engine();
        hub.setUiListener(this);
        hub.setPreview(preview);
        engine.setLoopMode(Prefs.loopArmed(this));
        engine.setMotionMode(Prefs.motionArmed(this));
        refreshToggleButtons();

        btnRecord.setOnClickListener(v -> toggleRecord());
        btnLoop.setOnClickListener(v -> toggleLoop());
        btnMotion.setOnClickListener(v -> toggleMotion());
        btnPip.setOnClickListener(v -> togglePip());
        btnSnap.setOnClickListener(v -> engine.takeSnapshot());
        btnGallery.setOnClickListener(v ->
                startActivity(new Intent(this, GalleryActivity.class)));
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int w, int h) {
                surfaceReady = true;
                tryOpenCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int w, int h) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                surfaceReady = false;
                // Never release here — CamHub / floating PiP owns camera lifetime.
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });

        btnRecord.requestFocus();
        ensurePermissions();
        ClipStore.pruneOld(this);
        ClipStore.deleteUnplayable(this);
        startClock();
    }

    private void ensurePermissions() {
        ArrayList<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
        } else {
            tryOpenCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            boolean cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
            if (cam) {
                tryOpenCamera();
            } else {
                showMessage(getString(R.string.permission_needed));
            }
        }
    }

    private void tryOpenCamera() {
        if (!surfaceReady || engine == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SurfaceTexture st = preview.getSurfaceTexture();
        if (st != null) {
            hideMessage();
            CamHub.get().setPreview(preview);
            engine.attachPreview(st);
            CamHub.get().maybeStartService();
        }
    }

    private void toggleRecord() {
        if (engine.hasActiveClip()) {
            engine.setLoopMode(false);
            Prefs.setLoopArmed(this, false);
            Prefs.setMotionArmed(this, false);
            engine.setMotionMode(false);
            engine.stopRecording();
            CamHub.get().cancelSegmentRotator();
            CamHub.get().resetMotionToast();
        } else {
            engine.startRecording();
            CamHub.get().scheduleSegmentRotator();
        }
        refreshToggleButtons();
    }

    private void toggleLoop() {
        boolean on = !Prefs.loopArmed(this);
        Prefs.setLoopArmed(this, on);
        engine.setLoopMode(on);
        if (on) {
            Prefs.setMotionArmed(this, false);
            engine.setMotionMode(false);
            CamHub.get().resetMotionToast();
            if (!engine.hasActiveClip()) {
                engine.startRecording();
                CamHub.get().scheduleSegmentRotator();
            } else if (engine.isPaused()) {
                engine.resumeRecording();
            }
            CamHub.get().maybeStartService();
            toast("Loop recording ON · " + Prefs.segmentMinutes(this) + " min clips");
        } else {
            if (engine.hasActiveClip()) {
                engine.stopRecording();
                CamHub.get().cancelSegmentRotator();
            }
            stopService(new Intent(this, CctvService.class));
            toast("Loop recording OFF");
        }
        refreshToggleButtons();
    }

    private void toggleMotion() {
        boolean on = !Prefs.motionArmed(this);
        Prefs.setMotionArmed(this, on);
        engine.setMotionMode(on);
        CamHub.get().resetMotionToast();
        if (on) {
            Prefs.setLoopArmed(this, false);
            engine.setLoopMode(false);
            if (engine.hasActiveClip()) {
                engine.stopRecording();
                CamHub.get().cancelSegmentRotator();
            }
            engine.motionDetector().reset();
            CamHub.get().maybeStartService();
            toast("Motion CCTV ON · records on move, pauses when still");
        } else {
            if (engine.hasActiveClip()) {
                engine.stopRecording();
                CamHub.get().cancelSegmentRotator();
            }
            stopService(new Intent(this, CctvService.class));
            toast("Motion detect OFF");
        }
        refreshToggleButtons();
    }

    private void togglePip() {
        boolean on = !Prefs.pipOverview(this);
        Prefs.setPipOverview(this, on);
        PipOverlay.refreshVisibility();
        if (on && !OverlayPermission.isGranted(this)) {
            toast(getString(R.string.pip_need_overlay));
            OverlayPermission.openSettings(this);
        }
        FloatingPipService.sync(this);
        CamHub.get().maybeStartService();
        refreshToggleButtons();
        toast(on ? "PiP ON · works over other apps too" : "PiP OFF");
    }

    private void startClock() {
        clockTick = () -> {
            if (Prefs.timestampOverlay(this)) {
                clockLabel.setVisibility(View.VISIBLE);
                clockLabel.setText(clockFmt.format(new Date()));
            } else {
                clockLabel.setVisibility(View.GONE);
            }
            if (engine.hasActiveClip() && !engine.isPaused()) {
                recDot.setVisibility(recDot.getVisibility() == View.VISIBLE
                        ? View.INVISIBLE : View.VISIBLE);
            }
            refreshToggleButtons();
            ui.postDelayed(clockTick, 1000);
        };
        ui.post(clockTick);
    }

    private void refreshToggleButtons() {
        boolean rec = engine.hasActiveClip() && !engine.isPaused();
        btnRecord.setText(engine.hasActiveClip() ? R.string.btn_stop : R.string.btn_record);
        btnRecord.setSelected(rec);
        btnLoop.setSelected(Prefs.loopArmed(this));
        btnMotion.setSelected(Prefs.motionArmed(this));
        btnPip.setSelected(Prefs.pipOverview(this));
        updateStatusUi();
    }

    private void updateStatusUi() {
        if (engine.hasActiveClip() && engine.isPaused()) {
            statusBadge.setText(R.string.status_paused);
            statusBadge.setTextColor(getColor(R.color.text_secondary));
            recDot.setVisibility(View.INVISIBLE);
        } else if (engine.hasActiveClip()) {
            statusBadge.setText(R.string.status_rec);
            statusBadge.setTextColor(getColor(R.color.rec_red));
            recDot.setVisibility(View.VISIBLE);
        } else if (Prefs.motionArmed(this)) {
            statusBadge.setText(R.string.status_motion);
            statusBadge.setTextColor(getColor(R.color.accent));
            recDot.setVisibility(View.GONE);
        } else {
            statusBadge.setText(R.string.status_live);
            statusBadge.setTextColor(getColor(R.color.accent_soft));
            recDot.setVisibility(View.GONE);
        }
    }

    private void showMessage(String msg) {
        messageLabel.setText(msg);
        messageLabel.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        messageLabel.setVisibility(View.GONE);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CamHub.get().setUiListener(this);
        CamHub.get().setPreview(preview);
        PipOverlay.refreshVisibility();
        refreshToggleButtons();
        if (surfaceReady) tryOpenCamera();
    }

    @Override
    protected void onPause() {
        // Stay live in-app: Gallery / Settings keep camera + bottom-right PiP.
        // Full release only when leaving the app (handled by CamHub.onAppBackgrounded).
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (clockTick != null) ui.removeCallbacks(clockTick);
        CamHub.get().setUiListener(null);
        super.onDestroy();
    }

    @Override
    public void onCameraReady(String label, Size previewSize) {
        ui.post(() -> {
            cameraLabel.setText(label + " · " + previewSize.getWidth() + "x" + previewSize.getHeight());
            hideMessage();
            refreshToggleButtons();
        });
    }

    @Override
    public void onCameraError(String message) {
        ui.post(() -> showMessage(message));
    }

    @Override
    public void onRecordingStarted(File file) {
        ui.post(() -> {
            refreshToggleButtons();
            toast(getString(R.string.status_rec) + " · " + file.getName());
        });
    }

    @Override
    public void onRecordingStopped(File file) {
        ui.post(() -> {
            refreshToggleButtons();
            toast(getString(R.string.clip_saved) + " · " + ClipStore.formatSize(file.length()));
        });
    }

    @Override
    public void onRecordingPaused() {
        ui.post(this::refreshToggleButtons);
    }

    @Override
    public void onRecordingResumed() {
        ui.post(this::refreshToggleButtons);
    }

    @Override
    public void onSnapshotSaved(File file) {
        ui.post(() -> toast(getString(R.string.snap_saved) + " · " + file.getName()));
    }
}
