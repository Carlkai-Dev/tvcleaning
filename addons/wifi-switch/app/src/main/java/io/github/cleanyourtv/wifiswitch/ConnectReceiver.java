package io.github.cleanyourtv.wifiswitch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;

/**
 * Joins a Wi-Fi network for the launcher's Wi-Fi drop-down. Android 10 and later only let apps built for
 * Android 9 add and join networks this way, which is why it lives here and not in the launcher.
 *
 * Extras: "ssid" (required), "security" ("open", "psk" for WPA/WPA2, "sae" for WPA3), "password".
 * The password is used for the network configuration only; it is never logged.
 */
public class ConnectReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        String ssid = intent.getStringExtra("ssid");
        if (wm == null || ssid == null || ssid.isEmpty()) return;
        String security = intent.getStringExtra("security");
        String password = intent.getStringExtra("password");

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = quote(ssid);
        if ("sae".equals(security) && Build.VERSION.SDK_INT >= 30) {
            config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
            config.preSharedKey = quote(password == null ? "" : password);
        } else if ("psk".equals(security) || "sae".equals(security)) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.preSharedKey = quote(password == null ? "" : password);
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }

        if (!wm.isWifiEnabled()) wm.setWifiEnabled(true);
        int id = wm.addNetwork(config);
        if (id == -1) return;
        wm.disconnect();
        wm.enableNetwork(id, true);
        wm.reconnect();
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }
}
