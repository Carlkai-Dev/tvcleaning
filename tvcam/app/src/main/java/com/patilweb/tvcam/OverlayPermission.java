package com.patilweb.tvcam;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Opens the TV "Display over other apps" / System Alert screen directly.
 * Xiaomi / Android TV uses SystemAlertActivity, not the phone Settings UI.
 */
final class OverlayPermission {
    private OverlayPermission() {}

    static boolean isGranted(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return Settings.canDrawOverlays(ctx);
    }

    /**
     * Jump straight to the overlay permission screen.
     * @return true if at least one settings activity was started
     */
    static boolean openSettings(Activity activity) {
        // 1) Android TV special-access list (works on this MiTV).
        if (tryStart(activity, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) {
            toast(activity, "Turn ON for TV Cam in the list");
            return true;
        }

        // 2) Explicit Xiaomi / AOSP TV component.
        Intent explicit = new Intent();
        explicit.setComponent(new ComponentName(
                "com.android.tv.settings",
                "com.android.tv.settings.device.apps.specialaccess.SystemAlertActivity"));
        if (tryStart(activity, explicit)) {
            toast(activity, "Turn ON for TV Cam in the list");
            return true;
        }

        // 3) Package-specific overlay page (phones / some TVs).
        Intent pkg = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
        if (tryStart(activity, pkg)) {
            toast(activity, "Allow display over other apps");
            return true;
        }

        // 4) App info page as last resort.
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity.getPackageName()));
        if (tryStart(activity, details)) {
            toast(activity, "Open Advanced / Special access → Display over other apps");
            return true;
        }

        toast(activity, "Open Settings → Apps → Special app access → Display over other apps");
        return false;
    }

    /** If PiP is on but overlay is off, jump to the permission screen once. */
    static void ensureForPip(Activity activity) {
        if (!Prefs.pipOverview(activity)) return;
        if (isGranted(activity)) return;
        openSettings(activity);
    }

    private static boolean tryStart(Activity activity, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(activity.getPackageManager()) == null
                    && intent.getComponent() == null) {
                return false;
            }
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }
}
