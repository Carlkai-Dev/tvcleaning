package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The launcher's own settings, opened by holding the Settings pill on Home: Lite mode, animations,
 * effects, tilt, magnification, glass frame, sounds, background and tiles per row, plus the way back to the stock
 * home screen. Choices are saved in {@link Config#PREFS} and applied over the theme by
 * {@link Config#load}; Home rebuilds itself when you come back ({@link #version}).
 */
public class LauncherSettingsActivity extends Activity {

    /** Bumped on every change, so Home knows to rebuild with the new settings. */
    static int version;

    private static final String[] STOCK_LAUNCHERS = {
            "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher"};
    private static final int[] COLUMN_CHOICES = {4, 5, 6};

    private SharedPreferences prefs;
    private LinearLayout rows;
    private UiSounds sounds;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
        rows = findViewById(R.id.rows);
        sounds = new UiSounds(this, Config.load(this).uiSounds);
        build(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sounds.refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sounds.release();
    }

    /** (Re)builds the rows from the current settings and focuses the row at this index. */
    private void build(int focus) {
        rows.removeAllViews();
        Config c = Config.load(this);
        boolean lite = c.lite;

        addRow(getString(R.string.set_lite), getString(lite ? R.string.set_lite_on : R.string.off),
                () -> put("lite", !lite));
        addToggle(R.string.set_animations, "animations", c.animations, lite);
        addToggle(R.string.set_effects, "effects", c.effects, lite);
        addToggle(R.string.set_tilt, "tilt", c.tilt, lite);
        addToggle(R.string.set_magnify, "magnify", c.magnify, lite);
        addToggle(R.string.set_glass_frame, "glass_frame", c.glassFrame, lite);
        addToggle(R.string.set_sounds, "ui_sounds", c.uiSounds, false);

        String bg = prefs.getString("background", "");
        int bgLabel = Config.BG_BLACK.equals(bg) ? R.string.bg_black
                : Config.BG_GRADIENT.equals(bg) ? R.string.bg_gradient : R.string.bg_theme;
        addRow(getString(R.string.set_background), lite ? getString(R.string.locked_by_lite, getString(R.string.bg_black)) : getString(bgLabel), () -> {
            if (lite) {
                toast(R.string.needs_lite_off);
                return;
            }
            // Theme -> Gradient -> Black -> Theme
            String next = Config.BG_GRADIENT.equals(bg) ? Config.BG_BLACK
                    : Config.BG_BLACK.equals(bg) ? "" : Config.BG_GRADIENT;
            if (next.isEmpty()) prefs.edit().remove("background").apply();
            else prefs.edit().putString("background", next).apply();
            changed();
        });

        addRow(getString(R.string.set_columns), String.valueOf(c.columns), () -> {
            int next = COLUMN_CHOICES[0];
            for (int i = 0; i < COLUMN_CHOICES.length; i++) {
                if (COLUMN_CHOICES[i] == c.columns) next = COLUMN_CHOICES[(i + 1) % COLUMN_CHOICES.length];
            }
            prefs.edit().putInt("columns", next).apply();
            changed();
        });

        addRow(getString(R.string.set_reset), "", () -> {
            prefs.edit().clear().apply();
            toast(R.string.set_reset_done);
            changed();
        });

        String stock = installedStockLauncher();
        if (stock != null) {
            addRow(getString(R.string.set_stock_home), getString(stockEnabled(stock)
                    ? R.string.set_stock_enabled : R.string.set_stock_disabled), () -> {
                // Enabling a disabled system app needs a permission no sideloaded app gets, but the TV's
                // own App info page has an Enable button that works from the remote.
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + stock)));
                    toast(R.string.set_stock_home_help);
                } catch (ActivityNotFoundException e) {
                    toast(R.string.not_available);
                }
            });
        }

        View row = rows.getChildAt(Math.min(focus, rows.getChildCount() - 1));
        if (row != null) row.requestFocus();
    }

    private void addToggle(int title, String key, boolean value, boolean lockedByLite) {
        String status = getString(value ? R.string.on : R.string.off);
        if (lockedByLite) status = getString(R.string.locked_by_lite, status);
        addRow(getString(title), status, () -> {
            if (lockedByLite) toast(R.string.needs_lite_off);
            else put(key, !value);
        });
    }

    private void addRow(String title, String status, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        lp.height = (int) (48 * getResources().getDisplayMetrics().density);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView statusView = row.findViewById(R.id.status);
        statusView.setText(status);
        statusView.setTextSize(14f);   // read from the sofa, not up close like the Inputs menu
        final int index = rows.getChildCount();
        row.setOnFocusChangeListener((v, focused) -> {
            if (focused) sounds.focus();
        });
        row.setOnClickListener(v -> {
            sounds.select();
            action.run();
            if (!isFinishing()) build(index);
        });
        rows.addView(row);
    }

    private void put(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        changed();
    }

    private void changed() {
        version++;
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    /** The stock launcher on this TV, enabled or not; null when there is none we know. */
    private String installedStockLauncher() {
        for (String pkg : STOCK_LAUNCHERS) {
            try {
                getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    private boolean stockEnabled(String pkg) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
