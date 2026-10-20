package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.List;

public final class SmartRamOptimizer {

    public static final class Result {
        public final long freedMb;
        public final int newPercentFree;
        public final long newAvailMb;
        public final long totalMb;

        public Result(long freedMb, int newPercentFree, long newAvailMb, long totalMb) {
            this.freedMb = freedMb;
            this.newPercentFree = newPercentFree;
            this.newAvailMb = newAvailMb;
            this.totalMb = totalMb;
        }
    }

    private SmartRamOptimizer() {}

    public static int getFreeRamPercent(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return (int) (mi.availMem * 100 / mi.totalMem);
    }

    public static long getFreeRamMb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    public static Result optimize(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return new Result(0, 0, 0, 0);

        ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);

        String myPkg = ctx.getPackageName();
        PackageManager pm = ctx.getPackageManager();

        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.pkgList == null) continue;
                for (String pkg : p.pkgList) {
                    if (pkg.equals(myPkg)) continue;
                    if (pkg.equals("android") || pkg.equals("com.android.systemui")) continue;
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                        if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                            am.killBackgroundProcesses(pkg);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        System.gc();
        System.runFinalization();
        System.gc();

        ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(after);

        long freed = Math.max(0, after.availMem - before.availMem) / (1024 * 1024);
        int percent = (int) (after.availMem * 100 / after.totalMem);
        long availMb = after.availMem / (1024 * 1024);
        long totalMb = after.totalMem / (1024 * 1024);

        return new Result(freed, percent, availMb, totalMb);
    }
}
