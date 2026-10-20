package com.gathin.tvhome;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * recently used apps and closing them, like a phone's recents screen.
 *
 * The list comes from UsageStatsManager, which needs the usage access app-op. Android TV has no screen to
 * grant it, so build-and-install.ps1 does it over adb (appops set com.gathin.tvhome GET_USAGE_STATS allow);
 * without it the list is empty and only Close all works. Closing uses killBackgroundProcesses, a normal
 * permission: it ends an app's processes that are in the background, which is every other app while Home is
 * in front. A real force-stop needs a system signature, so foreground services may restart themselves.
 */
final class Recents {
    private Recents() { }

    private static final long WINDOW_MS = 3L * 24 * 60 * 60 * 1000;

    static boolean hasUsageAccess(Context ctx) {
        AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                ctx.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** Launchable apps used in the last three days, most recent first; never this launcher. */
    static List<String> recent(Context ctx, int max) {
        List<String> out = new ArrayList<>();
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null || !hasUsageAccess(ctx)) return out;
        long now = System.currentTimeMillis();
        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - WINDOW_MS, now);
        List<UsageStats> used = new ArrayList<>();
        for (UsageStats s : stats.values()) {
            if (s.getLastTimeUsed() > 0 && s.getTotalTimeInForeground() > 0) used.add(s);
        }
        Collections.sort(used, (a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
        Set<String> launchable = launchable(ctx, false);
        for (UsageStats s : used) {
            String pkg = s.getPackageName();
            if (launchable.contains(pkg)) out.add(pkg);
            if (out.size() >= max) break;
        }
        return out;
    }

    /** Ends pkg's background processes. */
    static void close(Context ctx, String pkg) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) am.killBackgroundProcesses(pkg);
    }

    /** Closes every app the owner installed (system apps such as the Play Store or Live TV are left alone). */
    static void closeAll(Context ctx) {
        for (String pkg : launchable(ctx, true)) close(ctx, pkg);
    }

    /** Memory the system can hand to apps right now, in bytes. */
    static long available(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private static Set<String> launchable(Context ctx, boolean userAppsOnly) {
        PackageManager pm = ctx.getPackageManager();
        Set<String> pkgs = new LinkedHashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                if (ai.packageName.equals(ctx.getPackageName())) continue;
                if (userAppsOnly && (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
                pkgs.add(ai.packageName);
            }
        }
        return pkgs;
    }
}
