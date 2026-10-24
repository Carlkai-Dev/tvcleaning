package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaSessionManager;
import android.os.Build;

import java.util.List;

/**
 * Whether this package is the one a Pause row may act on. Pause sends a media key and takes audio
 * focus, so the row is shown only when this package is in front, or it is the package that
 * receives media keys while media is playing. Both checks use public APIs.
 */
final class MediaPlaying {
    private MediaPlaying() {}

    static boolean forPackage(Context ctx, String pkg) {
        if (pkg == null) return false;
        return inFront(ctx, pkg) || (mediaPlaying(ctx) && pkg.equals(mediaKeyPackage(ctx)));
    }

    /** The public playback list names no package. It only says that some media player is active. */
    private static boolean mediaPlaying(Context ctx) {
        AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return false;
        List<AudioPlaybackConfiguration> configs = audio.getActivePlaybackConfigurations();
        if (configs == null) return false;
        for (AudioPlaybackConfiguration c : configs) {
            AudioAttributes a = c.getAudioAttributes();
            if (a != null && a.getUsage() == AudioAttributes.USAGE_MEDIA) return true;
        }
        return false;
    }

    /**
     * Package that receives media keys. A normal app is not told this: the call throws, and the
     * row then depends on {@link #inFront}.
     */
    private static String mediaKeyPackage(Context ctx) {
        if (Build.VERSION.SDK_INT < 29) return null;
        MediaSessionManager sessions =
                (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (sessions == null) return null;
        try {
            String name = sessions.getMediaKeyEventSessionPackageName();
            return name == null || name.isEmpty() ? null : name;
        } catch (SecurityException e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean inFront(Context ctx, String pkg) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        try {
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                ComponentName top = tasks.get(0).topActivity;
                if (top != null && pkg.equals(top.getPackageName())) return true;
            }
        } catch (SecurityException ignored) {
        }
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return false;
        for (ActivityManager.RunningAppProcessInfo info : procs) {
            if (info.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && info.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                continue;
            }
            if (info.pkgList == null) continue;
            for (String name : info.pkgList) {
                if (pkg.equals(name)) return true;
            }
        }
        return false;
    }
}
