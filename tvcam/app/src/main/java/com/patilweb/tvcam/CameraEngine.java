package com.patilweb.tvcam;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera2 engine tuned for USB / external cameras (LifeCam HD-3000 on Android TV).
 */
final class CameraEngine {
    private static final String TAG = "TvCamEngine";

    interface Listener {
        void onCameraReady(String label, Size previewSize);

        void onCameraError(String message);

        void onRecordingStarted(File file);

        void onRecordingStopped(File file);

        void onRecordingPaused();

        void onRecordingResumed();

        void onSnapshotSaved(File file);
    }

    private final Context app;
    private final Listener listener;
    private final MotionDetector motionDetector = new MotionDetector();

    private HandlerThread camThread;
    private Handler camHandler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CaptureRequest.Builder previewBuilder;
    private ImageReader yuvReader;
    private ImageReader jpegReader;
    private MediaRecorder recorder;
    private SurfaceTexture previewTexture;
    private Surface previewSurface;
    private Size previewSize = new Size(640, 480);
    private String cameraId;
    private String cameraLabel = "USB Camera";

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean loopMode = new AtomicBoolean(false);
    private final AtomicBoolean motionMode = new AtomicBoolean(false);
    private File currentClip;
    private boolean wantSnapshot;

    CameraEngine(Context context, Listener listener) {
        this.app = context.getApplicationContext();
        this.listener = listener;
    }

    void startBackground() {
        if (camThread != null) return;
        camThread = new HandlerThread("tvcam-camera");
        camThread.start();
        camHandler = new Handler(camThread.getLooper());
    }

    void stopBackground() {
        if (camThread != null) {
            camThread.quitSafely();
            try {
                camThread.join(1500);
            } catch (InterruptedException ignored) {
            }
            camThread = null;
            camHandler = null;
        }
    }

    boolean isRecording() {
        return recording.get();
    }

    boolean isPaused() {
        return paused.get();
    }

    /** True when a clip is open (recording or paused). */
    boolean hasActiveClip() {
        return recording.get();
    }

    void setLoopMode(boolean on) {
        loopMode.set(on);
    }

    boolean isLoopMode() {
        return loopMode.get();
    }

    void setMotionMode(boolean on) {
        motionMode.set(on);
        if (!on) motionDetector.reset();
    }

    boolean isMotionMode() {
        return motionMode.get();
    }

    MotionDetector motionDetector() {
        return motionDetector;
    }

    void attachPreview(SurfaceTexture texture) {
        if (texture == null) return;
        previewTexture = texture;
        if (camera != null && camHandler != null) {
            // Rebind preview to a new SurfaceTexture (Main ↔ floating PiP).
            camHandler.post(() -> {
                if (recording.get()) {
                    stopRecordingInternal(false);
                }
                startPreviewSession();
            });
        } else {
            openCamera();
        }
    }

    void release() {
        stopRecordingInternal(false);
        closeSession();
        if (camera != null) {
            camera.close();
            camera = null;
        }
        releaseReaders();
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
        previewTexture = null;
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onCameraError(app.getString(R.string.permission_needed));
            return;
        }
        CameraManager mgr = app.getSystemService(CameraManager.class);
        if (mgr == null) {
            listener.onCameraError(app.getString(R.string.camera_missing));
            return;
        }
        try {
            cameraId = pickCameraId(mgr);
            if (cameraId == null) {
                listener.onCameraError(app.getString(R.string.camera_missing));
                return;
            }
            CameraCharacteristics chars = mgr.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map =
                    chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                previewSize = chooseSize(map.getOutputSizes(SurfaceTexture.class), 640, 480);
            }
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                cameraLabel = "LifeCam HD-3000 (USB)";
            } else {
                cameraLabel = "Camera " + cameraId;
            }
            mgr.openCamera(cameraId, stateCallback, camHandler);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "openCamera", e);
            listener.onCameraError(e.getMessage() != null ? e.getMessage() : "Camera open failed");
        }
    }

    private String pickCameraId(CameraManager mgr) throws CameraAccessException {
        String external = null;
        String any = null;
        for (String id : mgr.getCameraIdList()) {
            CameraCharacteristics c = mgr.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                external = id;
                break;
            }
            if (any == null) any = id;
        }
        return external != null ? external : any;
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            camera = cameraDevice;
            startPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            camera = null;
            listener.onCameraError(app.getString(R.string.camera_missing));
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            camera = null;
            listener.onCameraError("Camera error " + error);
        }
    };

    private void startPreviewSession() {
        if (camera == null || previewTexture == null) return;
        try {
            closeSession();
            releaseReaders();

            previewTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            if (previewSurface != null) previewSurface.release();
            previewSurface = new Surface(previewTexture);

            yuvReader = ImageReader.newInstance(
                    previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            yuvReader.setOnImageAvailableListener(this::onYuv, camHandler);

            Size jpegSize = previewSize;
            try {
                CameraManager mgr = app.getSystemService(CameraManager.class);
                StreamConfigurationMap map = mgr.getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    Size[] jpegs = map.getOutputSizes(ImageFormat.JPEG);
                    if (jpegs != null && jpegs.length > 0) {
                        jpegSize = chooseSize(jpegs, 640, 480);
                    }
                }
            } catch (Exception ignored) {
            }
            jpegReader = ImageReader.newInstance(
                    jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 2);
            jpegReader.setOnImageAvailableListener(this::onJpeg, camHandler);

            List<Surface> outputs = new ArrayList<>();
            outputs.add(previewSurface);
            outputs.add(yuvReader.getSurface());
            outputs.add(jpegReader.getSurface());

            camera.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                    session = captureSession;
                    try {
                        previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewBuilder.addTarget(previewSurface);
                        previewBuilder.addTarget(yuvReader.getSurface());
                        previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        session.setRepeatingRequest(previewBuilder.build(), null, camHandler);
                        listener.onCameraReady(cameraLabel, previewSize);
                    } catch (CameraAccessException e) {
                        listener.onCameraError(e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    listener.onCameraError("Preview session failed");
                }
            }, camHandler);
        } catch (CameraAccessException e) {
            listener.onCameraError(e.getMessage());
        }
    }

    private void onYuv(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            // Motion arming is driven from MainActivity (works while recording too).
            if (motionMode.get() && !recording.get()) {
                motionDetector.analyzeImage(image, Prefs.motionThreshold(app));
            }
        } finally {
            if (image != null) image.close();
        }
    }

    private void onJpeg(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireNextImage();
            if (image == null || !wantSnapshot) return;
            wantSnapshot = false;
            File out = ClipStore.newSnapFile(app);
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(bytes);
            }
            listener.onSnapshotSaved(out);
        } catch (IOException e) {
            Log.e(TAG, "snap", e);
        } finally {
            if (image != null) image.close();
        }
    }

    void takeSnapshot() {
        if (session == null || camera == null || jpegReader == null) return;
        try {
            wantSnapshot = true;
            CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(jpegReader.getSurface());
            b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 90);
            session.capture(b.build(), null, camHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "snapshot", e);
        }
    }

    void startRecording() {
        if (recording.get() || camera == null || previewSurface == null) return;
        camHandler.post(() -> {
            try {
                setupRecorderAndSession(true);
            } catch (Exception e) {
                Log.e(TAG, "startRecording", e);
                listener.onCameraError("Record failed: " + e.getMessage());
                stopRecordingInternal(false);
            }
        });
    }

    void stopRecording() {
        if (camHandler == null) {
            stopRecordingInternal(true);
            return;
        }
        camHandler.post(() -> stopRecordingInternal(true));
    }

    /** Block until the current clip is finalized (writes moov). */
    void stopRecordingSync() {
        if (camHandler == null) {
            stopRecordingInternal(true);
            return;
        }
        final java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(1);
        camHandler.post(() -> {
            try {
                stopRecordingInternal(true);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(4, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    /** Pause encoder while keeping the same clip open (motion idle). */
    void pauseRecording() {
        camHandler.post(() -> {
            if (!recording.get() || paused.get() || recorder == null) return;
            try {
                recorder.pause();
                paused.set(true);
                listener.onRecordingPaused();
            } catch (Exception e) {
                Log.w(TAG, "pause", e);
            }
        });
    }

    /** Resume after motion returns. */
    void resumeRecording() {
        camHandler.post(() -> {
            if (!recording.get() || !paused.get() || recorder == null) return;
            try {
                recorder.resume();
                paused.set(false);
                listener.onRecordingResumed();
            } catch (Exception e) {
                Log.w(TAG, "resume", e);
            }
        });
    }

    /** Rotate to a new segment while staying in loop mode. */
    void rotateSegment() {
        if (!recording.get()) return;
        camHandler.post(() -> {
            stopRecordingInternal(true);
            if (loopMode.get() || motionMode.get()) {
                try {
                    setupRecorderAndSession(true);
                } catch (Exception e) {
                    Log.e(TAG, "rotate", e);
                }
            }
        });
    }

    private void setupRecorderAndSession(boolean start) throws IOException, CameraAccessException {
        closeSession();
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Exception ignored) {
            }
            recorder = null;
        }

        currentClip = ClipStore.newClipFile(app);
        recorder = new MediaRecorder();
        recorder.setOnErrorListener((mr, what, extra) ->
                Log.e(TAG, "MediaRecorder error what=" + what + " extra=" + extra));
        boolean audio = Prefs.recordAudio(app)
                && ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (audio) {
            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(currentClip.getAbsolutePath());
        // Conservative settings for USB UVC + Android TV decoders.
        int w = previewSize.getWidth();
        int h = previewSize.getHeight();
        recorder.setVideoSize(w, h);
        recorder.setVideoFrameRate(30);
        recorder.setVideoEncodingBitRate(Math.max(800_000, w * h * 4));
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (audio) {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128_000);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioChannels(1);
        }
        recorder.prepare();

        // External UVC cams often allow only 2 processed streams + 1 JPEG.
        // Preview + MediaRecorder already use both processed slots; skip YUV while recording.
        Surface recorderSurface = recorder.getSurface();
        List<Surface> outputs = new ArrayList<>();
        outputs.add(previewSurface);
        outputs.add(recorderSurface);
        if (jpegReader != null) outputs.add(jpegReader.getSurface());

        final Surface recSurface = recorderSurface;
        camera.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                session = captureSession;
                try {
                    previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    previewBuilder.addTarget(previewSurface);
                    previewBuilder.addTarget(recSurface);
                    previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                    session.setRepeatingRequest(previewBuilder.build(), null, camHandler);
                    if (start) {
                        recorder.start();
                        recording.set(true);
                        paused.set(false);
                        listener.onRecordingStarted(currentClip);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "record session", e);
                    listener.onCameraError(e.getMessage());
                    stopRecordingInternal(false);
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                listener.onCameraError("Record session failed");
                startPreviewSession();
            }
        }, camHandler);
    }

    private void stopRecordingInternal(boolean restorePreview) {
        File finished = currentClip;
        // Stop feeding frames before finalizing — otherwise many TVs write MP4 without moov.
        closeSession();
        if (recording.get() && recorder != null) {
            try {
                if (paused.get()) {
                    try {
                        recorder.resume();
                        // Brief resume so the muxer can close cleanly after pause.
                        try {
                            Thread.sleep(80);
                        } catch (InterruptedException ignored) {
                        }
                    } catch (Exception ignored) {
                    }
                }
                recorder.stop();
            } catch (RuntimeException e) {
                Log.w(TAG, "recorder stop", e);
                finished = null;
            }
        }
        recording.set(false);
        paused.set(false);
        if (recorder != null) {
            try {
                recorder.reset();
            } catch (Exception ignored) {
            }
            try {
                recorder.release();
            } catch (Exception ignored) {
            }
            recorder = null;
        }
        if (finished != null && finished.exists()) {
            if (!ClipStore.isPlayableMp4(finished)) {
                Log.w(TAG, "dropping unplayable clip " + finished.getName());
                //noinspection ResultOfMethodCallIgnored
                finished.delete();
                finished = null;
            }
        }
        if (finished != null && finished.exists() && finished.length() > 0) {
            listener.onRecordingStopped(finished);
        }
        currentClip = null;
        if (restorePreview && camera != null && previewTexture != null) {
            startPreviewSession();
        }
    }

    private void closeSession() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
            session = null;
        }
    }

    private void releaseReaders() {
        if (yuvReader != null) {
            yuvReader.close();
            yuvReader = null;
        }
        if (jpegReader != null) {
            jpegReader.close();
            jpegReader = null;
        }
    }

    private static Size chooseSize(Size[] choices, int preferW, int preferH) {
        if (choices == null || choices.length == 0) return new Size(preferW, preferH);
        List<Size> list = Arrays.asList(choices);
        Collections.sort(list, Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()));
        Size best = list.get(list.size() - 1);
        for (Size s : list) {
            if (s.getWidth() <= preferW && s.getHeight() <= preferH) {
                best = s;
            }
        }
        // Prefer exact match if present.
        for (Size s : list) {
            if (s.getWidth() == preferW && s.getHeight() == preferH) return s;
        }
        return best;
    }
}
