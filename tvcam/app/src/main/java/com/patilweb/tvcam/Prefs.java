package com.patilweb.tvcam;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;

final class Prefs {
    private static final String NAME = "tvcam_prefs";

    static final int[] SEGMENT_MINUTES = {1, 5, 10, 30};
    static final int[] RETAIN_DAYS = {1, 3, 7, 14, 30};
    static final String[] MOTION_LABELS = {"Low", "Medium", "High"};
    /** Lower = more sensitive. Compared against avg luma delta 0..255. */
    static final int[] MOTION_THRESHOLDS = {18, 32, 55};

    /** 3×3 PiP placement preview labels. */
    static final String[] PIP_POSITION_LABELS = {
            "Top left", "Top center", "Top right",
            "Middle left", "Center", "Middle right",
            "Bottom left", "Bottom center", "Bottom right"
    };
    static final int[] PIP_GRAVITIES = {
            Gravity.TOP | Gravity.START,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL,
            Gravity.TOP | Gravity.END,
            Gravity.CENTER_VERTICAL | Gravity.START,
            Gravity.CENTER,
            Gravity.CENTER_VERTICAL | Gravity.END,
            Gravity.BOTTOM | Gravity.START,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
            Gravity.BOTTOM | Gravity.END
    };

    /** PiP box size in dp. */
    static final int[] PIP_SIZE_DP = {120, 160, 200, 280, 360};
    static final String[] PIP_SIZE_LABELS = {
            "XS · 120", "Small · 160", "Medium · 200", "Large · 280", "XL · 360"
    };

    private Prefs() {}

    static SharedPreferences get(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static int segmentMinutes(Context ctx) {
        return get(ctx).getInt("segment_min", 5);
    }

    static void setSegmentMinutes(Context ctx, int minutes) {
        get(ctx).edit().putInt("segment_min", minutes).apply();
    }

    static int retainDays(Context ctx) {
        return get(ctx).getInt("retain_days", 7);
    }

    static void setRetainDays(Context ctx, int days) {
        get(ctx).edit().putInt("retain_days", days).apply();
    }

    static int motionLevel(Context ctx) {
        return get(ctx).getInt("motion_level", 1);
    }

    static void setMotionLevel(Context ctx, int level) {
        get(ctx).edit().putInt("motion_level", Math.max(0, Math.min(2, level))).apply();
    }

    static int motionThreshold(Context ctx) {
        return MOTION_THRESHOLDS[motionLevel(ctx)];
    }

    static boolean recordAudio(Context ctx) {
        return get(ctx).getBoolean("record_audio", true);
    }

    static void setRecordAudio(Context ctx, boolean on) {
        get(ctx).edit().putBoolean("record_audio", on).apply();
    }

    static boolean timestampOverlay(Context ctx) {
        return get(ctx).getBoolean("timestamp", true);
    }

    static void setTimestampOverlay(Context ctx, boolean on) {
        get(ctx).edit().putBoolean("timestamp", on).apply();
    }

    static boolean startOnBoot(Context ctx) {
        return get(ctx).getBoolean("boot", false);
    }

    static void setStartOnBoot(Context ctx, boolean on) {
        get(ctx).edit().putBoolean("boot", on).apply();
    }

    static boolean loopArmed(Context ctx) {
        return get(ctx).getBoolean("loop_armed", false);
    }

    static void setLoopArmed(Context ctx, boolean on) {
        get(ctx).edit().putBoolean("loop_armed", on).apply();
    }

    static boolean motionArmed(Context ctx) {
        return get(ctx).getBoolean("motion_armed", false);
    }

    static void setMotionArmed(Context ctx, boolean on) {
        get(ctx).edit().putBoolean("motion_armed", on).apply();
    }

    static boolean pipOverview(Context ctx) {
        return get(ctx).getBoolean("pip_overview", true);
    }

    static void setPipOverview(Context ctx, boolean on) {
        get(ctx).edit().putBoolean("pip_overview", on).apply();
    }

    /** Storage location id — see ClipStore.ID_* */
    static String storageId(Context ctx) {
        return get(ctx).getString("storage_id", ClipStore.ID_DEVICE);
    }

    static void setStorageId(Context ctx, String id) {
        get(ctx).edit().putString("storage_id", id).apply();
    }

    /** 0..8 index into PIP_POSITION_LABELS / PIP_GRAVITIES. Default bottom-right. */
    static int pipPosition(Context ctx) {
        return Math.max(0, Math.min(8, get(ctx).getInt("pip_pos", 8)));
    }

    static void setPipPosition(Context ctx, int position) {
        get(ctx).edit().putInt("pip_pos", Math.max(0, Math.min(8, position))).apply();
    }

    static int pipGravity(int position) {
        int i = Math.max(0, Math.min(8, position));
        return PIP_GRAVITIES[i];
    }

    /** Index into PIP_SIZE_DP. Default Medium (200). */
    static int pipSizeIndex(Context ctx) {
        return Math.max(0, Math.min(PIP_SIZE_DP.length - 1, get(ctx).getInt("pip_size_idx", 2)));
    }

    static void setPipSizeIndex(Context ctx, int index) {
        get(ctx).edit()
                .putInt("pip_size_idx", Math.max(0, Math.min(PIP_SIZE_DP.length - 1, index)))
                .apply();
    }

    static int pipSizeDp(Context ctx) {
        return PIP_SIZE_DP[pipSizeIndex(ctx)];
    }

    /** Pixel size for current density. */
    static int pipSizePx(Context ctx) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.max(80, Math.round(pipSizeDp(ctx) * density));
    }
}
