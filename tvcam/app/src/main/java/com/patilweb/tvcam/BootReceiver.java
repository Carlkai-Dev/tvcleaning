package com.patilweb.tvcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Prefs.startOnBoot(context)) return;

        Prefs.setLoopArmed(context, true);
        Intent app = new Intent(context, MainActivity.class);
        app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(app);

        Intent svc = new Intent(context, CctvService.class);
        ContextCompat.startForegroundService(context, svc);
    }
}
