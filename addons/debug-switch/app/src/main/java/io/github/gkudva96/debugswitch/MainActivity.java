package io.github.gkudva96.debugswitch;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.net.Inet4Address;

/**
 * One screen: whether Wireless debugging is on, and OK to switch it on or off. It writes one setting,
 * adb_wifi_enabled, and only when OK is pressed. Nothing runs when the screen is closed.
 */
public class MainActivity extends Activity {
    /** The setting Developer options → Wireless debugging writes. */
    private static final String ADB_WIFI_ENABLED = "adb_wifi_enabled";
    /** adbd publishes the port a moment after the switch flips; read it again once. */
    private static final long PORT_DELAY_MS = 1500;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            refresh();
            main.removeCallbacks(refreshLater);
            main.postDelayed(refreshLater, PORT_DELAY_MS);
        }
    };
    private final Runnable refreshLater = this::refresh;

    private TextView state, hint, address;
    private Button toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        state = findViewById(R.id.state);
        hint = findViewById(R.id.hint);
        address = findViewById(R.id.address);
        toggle = findViewById(R.id.toggle);
        toggle.setOnClickListener(v -> flip());
        toggle.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Android switches it back off on its own: no Wi-Fi, or its "Allow on this network?" prompt.
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(ADB_WIFI_ENABLED), false, observer);
        refresh();
    }

    @Override
    protected void onPause() {
        getContentResolver().unregisterContentObserver(observer);
        main.removeCallbacks(refreshLater);
        super.onPause();
    }

    private boolean isOn() {
        return Settings.Global.getInt(getContentResolver(), ADB_WIFI_ENABLED, 0) == 1;
    }

    private boolean granted() {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    private void flip() {
        if (!granted()) return;
        try {
            Settings.Global.putInt(getContentResolver(), ADB_WIFI_ENABLED, isOn() ? 0 : 1);
        } catch (SecurityException e) {
            // The grant was taken back since the check; refresh shows the command again.
        }
        refresh();
    }

    private void refresh() {
        boolean on = isOn();
        boolean granted = granted();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network active = cm.getActiveNetwork();
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        boolean onWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);

        state.setText(on ? R.string.on : R.string.off);
        state.setTextColor(on ? 0xFF7CE38B : 0xFFFFFFFF);
        toggle.setText(on ? R.string.turn_off : R.string.turn_on);
        toggle.setEnabled(granted);

        switch (Debugging.hint(on, granted, onWifi)) {
            case NOT_GRANTED:
                hint.setText(getString(R.string.hint_grant, getPackageName()));
                break;
            case NEEDS_WIFI:
                hint.setText(R.string.hint_wifi);
                break;
            case OPEN:
                hint.setText(R.string.hint_open);
                break;
            default:
                hint.setText(R.string.hint_closed);
        }

        String ip = ipv4(cm, active);
        String endpoint = on ? Debugging.endpoint(ip, Debugging.port(prop("service.adb.tls.port"))) : null;
        if (endpoint != null) address.setText(getString(R.string.connect, endpoint));
        else if (ip != null) address.setText(getString(R.string.address, ip));
        else address.setText(R.string.no_address);
        address.setVisibility(View.VISIBLE);
    }

    private static String ipv4(ConnectivityManager cm, Network n) {
        LinkProperties lp = cm.getLinkProperties(n);
        if (lp == null) return null;
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.getAddress() instanceof Inet4Address) return la.getAddress().getHostAddress();
        }
        return null;
    }

    /** android.os.SystemProperties is hidden but readable; empty when this firmware refuses it. */
    private static String prop(String name) {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, name);
            return v == null ? "" : v.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }
}
