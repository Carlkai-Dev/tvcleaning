package com.gathin.tvhome;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Launch existing TV pages. The launcher never scans, pairs, or switches radios itself. */
final class QuickSettingsActions {
    static final String DREAM = "com.google.android.pano.action.SLEEP";
    private static final String TV_SETTINGS = "com.android.tv.settings";

    static Intent intent(Context context, int control) {
        if (control == R.id.quickAudioOutput) {
            Intent output = exportedActivity(context,
                    "com.xiaomi.gtv.settings.display.sound.MiAudioOutputActivity");
            if (output != null) return output;
            // Other Google TVs can expose their dedicated output picker through this overlay.
            Intent slice = settingsSlice(context, "default_audio_output_settings_slice_uri");
            if (slice != null) return slice;
            return MainActivity.systemIntent(context, Settings.ACTION_SOUND_SETTINGS);
        }
        if (control == R.id.quickApps) {
            Intent apps = exportedActivity(context, "com.android.tv.settings.device.apps.AppsActivity");
            if (apps != null) return apps;
            return MainActivity.systemIntent(context, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        }
        if (control == R.id.quickBluetooth) {
            // Google TV's public accessories slice includes both connected devices and Pair accessory.
            // Read its resource overlay: OEMs can put the slice in a different provider.
            Intent slice = settingsSlice(context, "connected_devices_slice_uri");
            if (slice != null) return slice;
            Intent bluetooth = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            if (bluetooth.resolveActivity(context.getPackageManager()) != null) return bluetooth;
            // Older AOSP TVs list accessories in this exported activity.
            Intent accessories = new Intent().setClassName(TV_SETTINGS,
                    "com.android.tv.settings.accessories.AccessoriesActivity");
            if (accessories.resolveActivity(context.getPackageManager()) != null) return accessories;
            return MainActivity.systemIntent(context, Settings.ACTION_SETTINGS);
        }
        if (control == R.id.quickScreensaver) {
            Intent dream = new Intent(DREAM);
            if (dream.resolveActivity(context.getPackageManager()) != null) return dream;
            Intent settings = new Intent(Settings.ACTION_DREAM_SETTINGS);
            if (settings.resolveActivity(context.getPackageManager()) != null) return settings;
            settings = new Intent().setClassName(TV_SETTINGS,
                    "com.android.tv.settings.device.display.daydream.DaydreamActivity");
            if (settings.resolveActivity(context.getPackageManager()) != null) return settings;
            return MainActivity.systemIntent(context, Settings.ACTION_SETTINGS);
        }
        String action = control == R.id.quickPicture ? Settings.ACTION_DISPLAY_SETTINGS
                : control == R.id.quickSound ? Settings.ACTION_SOUND_SETTINGS : Settings.ACTION_SETTINGS;
        return MainActivity.systemIntent(context, action);
    }

    private static Intent exportedActivity(Context context, String className) {
        ComponentName component = new ComponentName(TV_SETTINGS, className);
        try {
            android.content.pm.ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
            if (info.exported && info.enabled && info.applicationInfo.enabled)
                return new Intent().setComponent(component);
        } catch (PackageManager.NameNotFoundException ignored) { }
        return null;
    }

    private static Intent settingsSlice(Context context, String resource) {
        try {
            android.content.res.Resources res = context.getPackageManager().getResourcesForApplication(TV_SETTINGS);
            int id = res.getIdentifier(resource, "string", TV_SETTINGS);
            String uri = id != 0 ? res.getString(id) : null;
            if (uri != null && uri.startsWith("content://")) {
                Intent slice = new Intent("android.settings.SLICE_SETTINGS").setPackage(TV_SETTINGS)
                        .putExtra("slice_uri", uri);
                if (slice.resolveActivity(context.getPackageManager()) != null) return slice;
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        return null;
    }

    private QuickSettingsActions() { }
}
