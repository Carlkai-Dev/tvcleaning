package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Scan only while the dropdown is open; Android owns saving and connecting. */
final class WifiDropdown {
    static final int LOCATION_REQUEST = 41;
    static final int SAVE_REQUEST = 42;
    private final Activity activity;
    private final ViewGroup root;
    private final View anchor;
    private final WifiManager wifi;
    private HeaderDropdown dropdown;
    private AlertDialog passwordDialog;
    private boolean listening;
    private boolean scanPending;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scanTimeout = () -> {
        scanPending = false;
        render();
    };
    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (dropdown == null || !dropdown.window.isShowing()) return;
            handler.removeCallbacks(scanTimeout);
            scanPending = false;
            render();
        }
    };

    WifiDropdown(Activity activity, ViewGroup root, View anchor) {
        this.activity = activity;
        this.root = root;
        this.anchor = anchor;
        wifi = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    void show() {
        if (dropdown != null && dropdown.window.isShowing()) return;
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        showAfterPermission();
    }

    void showAfterPermission() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (dropdown != null && dropdown.window.isShowing()) return;
        dropdown = new HeaderDropdown(activity, root, anchor);
        dropdown.window.setOnDismissListener(() -> {
            stopListening();
            dropdown = null;
            anchor.requestFocus();
        });
        render();
        dropdown.show();
        if (canScan()) {
            activity.registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            listening = true;
            requestScan();
        }
    }

    private boolean canScan() {
        LocationManager location = activity.getSystemService(LocationManager.class);
        return wifi != null && wifi.isWifiEnabled()
                && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && location != null && (Build.VERSION.SDK_INT >= 28 ? location.isLocationEnabled()
                : Settings.Secure.getInt(activity.getContentResolver(), Settings.Secure.LOCATION_MODE, 0) != 0);
    }

    private void requestScan() {
        handler.removeCallbacks(scanTimeout);
        try { scanPending = wifi != null && wifi.startScan(); }
        catch (SecurityException e) { scanPending = false; }
        if (scanPending) handler.postDelayed(scanTimeout, 8000);
        // Existing results remain usable when Android throttles a new scan.
        render();
    }

    private void stopListening() {
        handler.removeCallbacks(scanTimeout);
        if (listening) {
            activity.unregisterReceiver(scanReceiver);
            listening = false;
        }
        scanPending = false;
    }

    void dismiss() {
        if (dropdown != null) dropdown.window.dismiss();
        stopListening();
        if (passwordDialog != null) passwordDialog.dismiss();
    }

    private String currentSsid() {
        try {
            WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
            String ssid = info == null || info.getNetworkId() < 0 ? null : info.getSSID();
            if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) return null;
            return ssid.startsWith("\"") && ssid.endsWith("\"") ? ssid.substring(1, ssid.length() - 1) : ssid;
        } catch (SecurityException e) { return null; }
    }

    private void render() {
        if (dropdown == null) return;
        LinearLayout rows = dropdown.rows;
        View oldFocus = rows.findFocus();
        Object focusedKey = oldFocus == null ? null : oldFocus.getTag();
        rows.removeAllViews();
        heading(activity.getString(R.string.wifi));
        if (!canScan()) {
            int message = wifi == null || !wifi.isWifiEnabled() ? R.string.wifi_disabled
                    : activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    ? R.string.wifi_permission_needed : R.string.wifi_location_needed;
            heading(activity.getString(message));
            if (message == R.string.wifi_location_needed)
                row("location", activity.getString(R.string.wifi_location_settings), "", false,
                        () -> openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else {
            String current = currentSsid();
            Map<String, ScanResult> unique = new HashMap<>();
            try {
                for (ScanResult result : wifi.getScanResults()) {
                    if (result.SSID == null || result.SSID.isEmpty()) continue;
                    String key = result.SSID + '\n' + security(result);
                    ScanResult previous = unique.get(key);
                    if (previous == null || result.level > previous.level) unique.put(key, result);
                }
            } catch (SecurityException e) { /* Permission can be revoked while open. */ }
            List<ScanResult> networks = new ArrayList<>(unique.values());
            networks.sort(Comparator.<ScanResult>comparingInt(n -> n.SSID.equals(current) ? 0 : 1)
                    .thenComparing(Comparator.comparingInt((ScanResult n) -> n.level).reversed())
                    .thenComparing(n -> n.SSID, String.CASE_INSENSITIVE_ORDER));
            if (current != null && networks.stream().noneMatch(n -> n.SSID.equals(current)))
                row("current", current, activity.getString(R.string.connected), true, () -> {});
            if (networks.isEmpty()) heading(activity.getString(scanPending ? R.string.wifi_scanning : R.string.wifi_no_networks));
            for (ScanResult network : networks) {
                boolean connected = network.SSID.equals(current);
                String status = activity.getString(connected ? R.string.connected
                        : "open".equals(security(network)) ? R.string.wifi_open : R.string.wifi_secured);
                row(network.SSID + '\n' + security(network), network.SSID, status, connected,
                        () -> { if (!connected) select(network); });
            }
            if (!supportsSaveDialog()) heading(activity.getString(R.string.wifi_system_step));
            row("refresh", activity.getString(R.string.wifi_refresh), "", false, this::requestScan);
        }
        row("settings", activity.getString(R.string.wifi_more_settings), "", false,
                () -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        // Preserve the selected network when the one-shot scan arrives.
        if (focusedKey != null) {
            View replacement = rows.findViewWithTag(focusedKey);
            if (replacement != null) replacement.requestFocus();
            else for (int i = 0; i < rows.getChildCount(); i++)
                if (rows.getChildAt(i).isFocusable()) { rows.getChildAt(i).requestFocus(); break; }
        }
    }

    private void heading(String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(12);
        label.setTypeface(activity.getResources().getFont(R.font.outfit));
        label.setFontVariationSettings("'wght' 400");
        label.setTextColor(0xFFDFDFE5);
        int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
        label.setPadding(pad, pad / 2, pad, pad / 2);
        dropdown.rows.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private void row(String key, String title, String state, boolean connected, Runnable action) {
        View row = activity.getLayoutInflater().inflate(R.layout.item_input, dropdown.rows, false);
        row.setTag(key);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_wifi);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        status.setFontVariationSettings("'wght' 400");
        status.setText(state);
        if (state.isEmpty()) status.setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(connected ? View.VISIBLE : View.GONE);
        row.setOnClickListener(v -> action.run());
        HeaderDropdown.containNavigation(row);
        dropdown.rows.addView(row);
    }

    private String security(ScanResult network) {
        String caps = network.capabilities == null ? "" : network.capabilities;
        if (caps.contains("EAP") || caps.contains("WEP") || caps.contains("WAPI")) return "system";
        if (caps.contains("PSK")) return "wpa2";
        if (caps.contains("SAE")) return "wpa3";
        if (caps.contains("OWE")) return "owe";
        // Unknown protected modes must never be treated as an open network.
        if (caps.contains("RSN") || caps.contains("WPA") || caps.contains("DPP")) return "system";
        return "open";
    }

    private boolean supportsSaveDialog() {
        if (Build.VERSION.SDK_INT < 30) return false;
        ResolveInfo handler = activity.getPackageManager().resolveActivity(new Intent(Settings.ACTION_WIFI_ADD_NETWORKS), 0);
        // AOSP TV routes this action to its generic network page, ignoring the proposed credentials.
        return handler != null && handler.activityInfo != null
                && !handler.activityInfo.name.endsWith(".connectivity.NetworkActivity");
    }

    private void select(ScanResult network) {
        if (Build.VERSION.SDK_INT < 30 || !supportsSaveDialog() || "system".equals(security(network))) {
            Toast.makeText(activity, activity.getString(R.string.wifi_choose_in_system, network.SSID), Toast.LENGTH_LONG).show();
            openSettings(Settings.ACTION_WIFI_SETTINGS);
            return;
        }
        showConnectionDialog(network);
    }

    @android.annotation.TargetApi(30)
    private void showConnectionDialog(ScanResult network) {
        dropdown.window.dismiss();
        EditText password = new EditText(activity);
        password.setSingleLine(true);
        password.setHint(R.string.wifi_password);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        boolean protectedNetwork = "wpa2".equals(security(network)) || "wpa3".equals(security(network));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(network.SSID)
                .setMessage(R.string.wifi_confirm_help)
                .setPositiveButton(R.string.wifi_continue, null)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {})
                .create();
        if (protectedNetwork) dialog.setView(password);
        dialog.setOnDismissListener(d -> {
            password.getText().clear();
            passwordDialog = null;
        });
        passwordDialog = dialog;
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder().setSsid(network.SSID);
                String value = password.getText().toString();
                switch (security(network)) {
                    case "wpa2": builder.setWpa2Passphrase(value); break;
                    case "wpa3": builder.setWpa3Passphrase(value); break;
                    case "owe": builder.setIsEnhancedOpen(true); break;
                }
                ArrayList<WifiNetworkSuggestion> networks = new ArrayList<>();
                networks.add(builder.build());
                Intent intent = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                        .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, networks);
                activity.startActivityForResult(intent, SAVE_REQUEST);
                dialog.dismiss();
            } catch (IllegalArgumentException e) {
                password.setError(activity.getString(R.string.wifi_bad_password));
            } catch (android.content.ActivityNotFoundException | SecurityException e) {
                dialog.dismiss();
                openSettings(Settings.ACTION_WIFI_SETTINGS);
            }
        });
    }

    void onSaveResult(int resultCode, Intent data) {
        if (Build.VERSION.SDK_INT < 30) return;
        if (resultCode == Activity.RESULT_OK) {
            ArrayList<Integer> results = data == null ? null : data.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST);
            boolean saved = results != null && !results.isEmpty() && (results.get(0) == Settings.ADD_WIFI_RESULT_SUCCESS
                    || results.get(0) == Settings.ADD_WIFI_RESULT_ALREADY_EXISTS);
            Toast.makeText(activity, saved ? R.string.wifi_saved : R.string.wifi_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openSettings(String action) {
        dismiss();
        try { activity.startActivity(new Intent(action)); }
        catch (android.content.ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
