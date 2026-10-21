package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The settings sheet the Settings pill and the remote's Settings button open when {@code settings_key_panel} is
 * on: a volume slider, then grouped rows with coloured icons, on a dark sheet at the right, over Home or
 * whatever app is playing. Translucent and in a task of its own, so the app behind keeps running and Back
 * returns to it. Pressing Settings again closes the sheet. Rows open the TV's own settings screens, which no
 * app can restyle; the sheet in front of them is ours.
 */
public final class QuickSettingsActivity extends Activity {

    // Apple's system colours, dark appearance.
    private static final int PINK = 0xFFFF375F;
    private static final int BLUE = 0xFF0A84FF;
    private static final int ORANGE = 0xFFFF9F0A;
    private static final int INDIGO = 0xFF5E5CE6;
    private static final int GREEN = 0xFF30D158;
    private static final int GRAY = 0xFF8E8E93;
    private static final int GRAPHITE = 0xFF636366;

    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";

    private AudioManager audio;
    private View volumeRow;
    /** The remote's volume keys while the sheet is up: keeps the slider in step. */
    private final BroadcastReceiver volumeChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            bindVolume(true);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        overridePendingTransition(0, 0);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        View scrim = findViewById(R.id.scrim);
        // Darkens the right of the screen behind the sheet, fading out to the left.
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, 0x33000000, 0xA6000000}));
        scrim.setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.subtitle)).setText(subtitle());

        LinearLayout rows = findViewById(R.id.rows);
        volumeRow = addVolumeRow(group(rows));
        LinearLayout tv = group(rows);
        addRow(tv, R.drawable.ic_wifi, BLUE, R.string.quick_network, networkName(),
                new Intent(Settings.ACTION_WIFI_SETTINGS));
        // Android TV 11 has no Display screen of its own: picture and sound share Display & Sound.
        Intent display = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        if (display.resolveActivity(getPackageManager()) == null) display = new Intent(Settings.ACTION_SOUND_SETTINGS);
        addRow(tv, R.drawable.ic_picture, ORANGE, R.string.quick_picture, null, display);
        LinearLayout general = group(rows);
        addRow(general, R.drawable.ic_apps, INDIGO, R.string.quick_apps, getString(R.string.quick_apps_hint),
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(general, R.drawable.ic_home_settings, GREEN, R.string.launcher_settings, null,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        addRow(general, R.drawable.ic_info, GRAY, R.string.quick_about, null,
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        addRow(group(rows), R.drawable.ic_settings, GRAPHITE, R.string.tv_settings, null,
                new Intent(Settings.ACTION_SETTINGS));

        bindVolume(false);
        volumeRow.requestFocus();
        View panel = findViewById(R.id.panel);
        panel.setAlpha(0f);
        panel.setTranslationX(dp(36));
        panel.animate().alpha(1f).translationX(0f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(2f)).start();

        IntentFilter filter = new IntentFilter(VOLUME_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(volumeChanged, filter);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(volumeChanged);
        super.onDestroy();
    }

    /** Settings pressed again while the sheet is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the sheet. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        findViewById(R.id.panel).animate().alpha(0f).translationX(dp(36)).setDuration(180)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(this::finish).start();
    }

    /** A rounded group of rows, iOS style. */
    private LinearLayout group(LinearLayout rows) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(rounded(0xFF2C2C2E, 16));
        int pad = dp(4);
        group.setPadding(pad, pad, pad, pad);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (rows.getChildCount() > 0) params.topMargin = dp(12);
        rows.addView(group, params);
        return group;
    }

    private void addRow(LinearLayout group, int icon, int color, int title, String value, Intent target) {
        View row = inflateRow(group, R.layout.item_setting, icon, color);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView valueView = row.findViewById(R.id.value);
        if (value != null) valueView.setText(value);
        else valueView.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }

    /** Left and Right change the volume, OK mutes and unmutes. */
    private View addVolumeRow(LinearLayout group) {
        View row = inflateRow(group, R.layout.item_setting_volume, R.drawable.ic_volume_up, PINK);
        row.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                bindVolume(true);
            }
            return true;
        });
        row.setOnClickListener(v -> {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
            bindVolume(true);
        });
        return row;
    }

    private View inflateRow(LinearLayout group, int layout, int icon, int color) {
        View row = getLayoutInflater().inflate(layout, group, false);
        ImageView iconView = row.findViewById(R.id.icon);
        iconView.setImageResource(icon);
        iconView.setBackground(rounded(color, 8));
        row.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(150).start();
            if (v == volumeRow) bindVolume(false);
        });
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getLayoutParams();
        if (group.getChildCount() > 0) params.topMargin = dp(2);
        group.addView(row, params);
        return row;
    }

    private void bindVolume(boolean animate) {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        boolean focused = volumeRow.isFocused();
        ((ImageView) volumeRow.findViewById(R.id.icon)).setImageResource(
                muted || volume == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
        LevelBar bar = volumeRow.findViewById(R.id.bar);
        // Dark on the white focused row, white on the dark sheet.
        bar.setColors(focused ? 0xFF1C1C1E : 0xFFFFFFFF, focused ? 0x26000000 : 0x3DFFFFFF);
        bar.setLevel(volume / (float) max, animate);
        bar.setAlpha(muted ? 0.35f : 1f);
        ((TextView) volumeRow.findViewById(R.id.value)).setText(String.valueOf(volume));
    }

    /** "Television · Android TV 11": the name the TV goes by on the network. */
    private String subtitle() {
        String name = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        if (name == null || name.trim().isEmpty()) name = Build.MODEL;
        return name.trim() + " · Android TV " + Build.VERSION.RELEASE;
    }

    /** The Wi-Fi network's name, Ethernet on a cable, or Offline. */
    private String networkName() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm != null ? cm.getNetworkCapabilities(cm.getActiveNetwork()) : null;
        if (caps == null) return getString(R.string.offline);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return getString(R.string.ethernet);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
        String ssid = info != null ? info.getSSID() : null;
        if (ssid != null) {
            ssid = ssid.replace("\"", "").trim();
            if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) return ssid;
        }
        return getString(R.string.connected);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
