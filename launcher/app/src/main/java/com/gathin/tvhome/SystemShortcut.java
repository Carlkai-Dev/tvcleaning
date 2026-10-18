package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.view.View;

/** Check external screens when building a menu and again before launching, never every frame. */
final class SystemShortcut {
    static boolean canOpen(Context context, Intent intent) {
        try {
            ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            ActivityInfo screen = resolved == null ? null : resolved.activityInfo;
            return screen != null && screen.enabled && screen.applicationInfo != null
                    && screen.applicationInfo.enabled
                    && (screen.exported || screen.applicationInfo.uid == Process.myUid())
                    && (screen.permission == null || screen.permission.isEmpty()
                            || context.checkSelfPermission(screen.permission) == PackageManager.PERMISSION_GRANTED);
        } catch (SecurityException e) {
            return false;
        }
    }

    static boolean open(Activity activity, Intent intent) {
        if (!canOpen(activity, intent)) return false;
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            // A package or permission may have changed while the menu was open.
            return false;
        }
    }

    static void disable(View row) {
        row.setEnabled(false);
        row.setClickable(false);
        row.setFocusable(false);
        row.setAlpha(0.45f);
    }
}
