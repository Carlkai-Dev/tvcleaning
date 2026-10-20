package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.provider.Settings;

/**
 * Android-side capability probing for Projector Mode. Runs at most once per process and only when
 * a projector surface opens with the mode enabled; the result is cached, never polled. Only
 * public, exported entry points are probed — no reflection, no private APIs.
 */
final class ProjectorDetector {

    /** Packages whose launch intent counts as the official projector settings entry. M1 measured
     *  none of these has a launch/leanback entry on the YYT stick, so there the generic TV
     *  settings shortcut is what remains; the probe stays for other firmware. */
    private static final String[] PROJECTOR_SETTINGS_PACKAGES = {
            "com.sdt.projector.observer", "com.sdt.customization", "com.droidlogic.tv.settings"
    };
    // No direct focus/keystone/zoom entry point is probed yet: a vendor package merely launching
    // does not prove it opens that feature's screen. They stay shortcut/unsupported until the
    // on-device discovery names a specific exported activity.

    private static ProjectorCapabilities cached;

    private ProjectorDetector() {}

    static synchronized ProjectorCapabilities detect(Context ctx) {
        if (cached != null) return cached;
        ProjectorCapabilities.Probe p = new ProjectorCapabilities.Probe();
        try {
            PackageManager pm = ctx.getPackageManager();
            p.genericSettings = resolves(pm, new Intent(Settings.ACTION_SETTINGS));
            p.displaySettings = resolves(pm, new Intent(Settings.ACTION_DISPLAY_SETTINGS));
            p.soundSettings = resolves(pm, new Intent(Settings.ACTION_SOUND_SETTINGS));
            p.projectorSettingsDirect = launchesAny(pm, PROJECTOR_SETTINGS_PACKAGES);
            p.focusDirect = false;
            p.keystoneDirect = false;
            p.zoomDirect = false;
            TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (TvInputInfo info : tim.getTvInputList()) {
                    if (info.isPassthroughInput() && !info.isHidden(ctx)) { p.hasPassthroughInput = true; break; }
                }
            }
            Config cfg = Config.load(ctx);
            p.inputFallback = !cfg.sourcePanelService.isEmpty() || !cfg.sourcePanelAction.isEmpty()
                    || !cfg.inputAction.isEmpty();
        } catch (Exception e) {
            p.probeFailed = true;
        }
        cached = ProjectorCapabilities.resolve(p);
        return cached;
    }

    /** Official projector-settings intent for the Dashboard, or null when nothing public resolves. */
    static Intent projectorSettingsIntent(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : PROJECTOR_SETTINGS_PACKAGES) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        Intent generic = new Intent(Settings.ACTION_SETTINGS);
        return generic.resolveActivity(pm) != null ? generic : null;
    }

    static String moduleProfile(Context ctx) {
        String detected = ProjectorProfile.matchModule(Build.MANUFACTURER, Build.DEVICE, Build.MODEL, Build.BOARD);
        return ProjectorProfile.effectiveProfile(Config.load(ctx).projectorProfile, detected);
    }

    private static boolean resolves(PackageManager pm, Intent i) {
        return i.resolveActivity(pm) != null;
    }

    private static boolean launchesAny(PackageManager pm, String[] packages) {
        for (String pkg : packages) {
            if (pm.getLeanbackLaunchIntentForPackage(pkg) != null
                    || pm.getLaunchIntentForPackage(pkg) != null) return true;
        }
        return false;
    }
}
