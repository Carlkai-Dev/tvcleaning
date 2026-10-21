package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();

        // Automatically ensure HomeKeyService is active upon boot using WRITE_SECURE_SETTINGS
        try {
            Settings.Secure.putString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    "com.gathin.tvhome/com.gathin.tvhome.HomeKeyService");
            Settings.Secure.putInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    1);
        } catch (Throwable ignored) {}

        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                long maxDurationMs = 30000; // 30 seconds
                while (!MainActivity.isForeground && (System.currentTimeMillis() - startTime < maxDurationMs)) {
                    Intent launch = new Intent(context, MainActivity.class);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    try {
                        context.startActivity(launch);
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed to launch MainActivity during boot loop", t);
                    }

                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            } finally {
                pr.finish();
            }
        }, "BootLaunchLoop").start();
    }
}
