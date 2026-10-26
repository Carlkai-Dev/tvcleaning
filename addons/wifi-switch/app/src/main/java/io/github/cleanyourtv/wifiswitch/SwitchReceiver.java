package io.github.cleanyourtv.wifiswitch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;

/**
 * Switches Wi-Fi on or off for the launcher's Wi-Fi drop-down. A broadcast, not a screen, so the drop-down stays
 * open while its switch slides. Boolean extra "on"; without it, Wi-Fi is flipped.
 */
public class SwitchReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return;
        boolean on = intent.hasExtra("on") ? intent.getBooleanExtra("on", true) : !wm.isWifiEnabled();
        wm.setWifiEnabled(on);
    }
}
