package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
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
            for (int delay : new int[]{1500, 3000, 5500}) {
                try {
                    Thread.sleep(delay == 1500 ? 1500 : delay - 1500);
                } catch (InterruptedException ignored) {}
                Intent launch = new Intent(context, MainActivity.class);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                try {
                    context.startActivity(launch);
                } catch (Throwable ignored) {}
            }
        }).start();
    }
}
