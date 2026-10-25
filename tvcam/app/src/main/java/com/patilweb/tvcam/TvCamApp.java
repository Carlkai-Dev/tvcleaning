package com.patilweb.tvcam;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Tracks in-app foreground and hosts the shared camera + floating PiP overlay.
 */
public class TvCamApp extends Application {
    private static final String TAG = "TvCamApp";
    private static TvCamApp instance;
    private int startedCount;
    private int resumedCount;
    private Activity currentActivity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable leaveRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        CameraMonitor.init(this);
        CamHub.init(this);
        CameraMonitor.get().addListener(connected ->
                CamHub.get().onCameraAvailabilityChanged(connected));
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
                startedCount++;
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (leaveRunnable != null) {
                    handler.removeCallbacks(leaveRunnable);
                    leaveRunnable = null;
                }
                resumedCount++;
                currentActivity = activity;
                PipOverlay.attach(activity);
                CamHub.get().onAppResumed();
                // Keep service warm; hide overlay while inside TV Cam.
                if (Prefs.pipActive(TvCamApp.this) && FloatingPipService.canDrawOverlays(TvCamApp.this)) {
                    FloatingPipService.start(TvCamApp.this);
                    FloatingPipService.setVisible(false);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (currentActivity == activity) {
                    PipOverlay.detach(activity);
                }
                resumedCount = Math.max(0, resumedCount - 1);
                // Home / other app: fire soon after last activity pauses.
                if (resumedCount == 0) {
                    leaveRunnable = () -> {
                        leaveRunnable = null;
                        if (resumedCount == 0) {
                            Log.i(TAG, "leave foreground → floating PiP");
                            CamHub.get().onAppBackgrounded();
                        }
                    };
                    handler.postDelayed(leaveRunnable, 350);
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedCount = Math.max(0, startedCount - 1);
                if (startedCount == 0) {
                    Log.i(TAG, "all stopped → floating PiP");
                    CamHub.get().onAppBackgrounded();
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }
        });
    }

    static TvCamApp get() {
        return instance;
    }

    boolean isInApp() {
        return resumedCount > 0 || startedCount > 0;
    }

    boolean isResumed() {
        return resumedCount > 0;
    }

    Activity currentActivity() {
        return currentActivity;
    }
}
