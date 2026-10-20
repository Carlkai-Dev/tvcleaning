package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private static final int PICK_WALLPAPER = 71;

    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView wallpaperDefault;
    private TextView wallpaperCustom;
    private TextView wallpaperIndia;
    private TextView wallpaperPick;
    private TextView wallpaperState;
    private int columns;
    private boolean animations;
    private boolean advancedEffects;
    private String wallpaperMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        wallpaperMode = WallpaperPrefs.mode(this);

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            LinearLayout row = (LinearLayout) columnChoices[0].getParent();
            row.removeAllViews();
            for (int i = columnChoices.length - 1; i >= 0; i--) row.addView(columnChoices[i]);
        }
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove("animations").remove("advanced_effects")
                    .remove(WallpaperPrefs.KEY_MODE).commit();
            Config current = Config.load(this);
            columns = current.columns;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            wallpaperMode = WallpaperPrefs.MODE_DEFAULT;
            setResult(RESULT_OK);
            refresh();
        });

        wallpaperDefault = findViewById(R.id.wallpaperDefault);
        wallpaperCustom = findViewById(R.id.wallpaperCustom);
        wallpaperIndia = findViewById(R.id.wallpaperIndia);
        wallpaperPick = findViewById(R.id.wallpaperPick);
        wallpaperState = findViewById(R.id.wallpaperState);
        wallpaperDefault.setOnClickListener(v -> setWallpaperMode(WallpaperPrefs.MODE_DEFAULT));
        wallpaperCustom.setOnClickListener(v -> {
            setWallpaperMode(WallpaperPrefs.MODE_CUSTOM);
            if (!WallpaperPrefs.hasCustom(this)) pickWallpaper();
        });
        wallpaperIndia.setOnClickListener(v -> setWallpaperMode(WallpaperPrefs.MODE_INDIA));
        wallpaperPick.setOnClickListener(v -> pickWallpaper());

        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void setWallpaperMode(String mode) {
        wallpaperMode = IndiaEvents.normalizeMode(mode);
        WallpaperPrefs.setMode(this, wallpaperMode);
        setResult(RESULT_OK);
        refresh();
    }

    private void pickWallpaper() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_WALLPAPER);
        } catch (ActivityNotFoundException e) {
            // Some Android TV builds have no document UI; fall back to GET_CONTENT.
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("image/*");
            try {
                startActivityForResult(Intent.createChooser(fallback, getString(R.string.wallpaper_pick)),
                        PICK_WALLPAPER);
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            final int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (takeFlags != 0) {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (SecurityException ignored) {
        }
        if (copyWallpaper(uri)) {
            wallpaperMode = WallpaperPrefs.MODE_CUSTOM;
            WallpaperPrefs.setMode(this, wallpaperMode);
            Toast.makeText(this, R.string.wallpaper_saved, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            refresh();
        } else {
            Toast.makeText(this, R.string.wallpaper_pick_failed, Toast.LENGTH_LONG).show();
        }
    }

    private boolean copyWallpaper(Uri uri) {
        File out = WallpaperPrefs.customFile(this);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) return false;
            byte[] buf = new byte[16 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                // Cap ~8 MB so a huge photo cannot fill the TV's storage.
                if (total > 8L * 1024 * 1024) {
                    fos.close();
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    return false;
                }
                fos.write(buf, 0, n);
            }
            return out.length() > 0;
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return false;
        }
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        followThemeChoice.setSelected(!hasOnTvChoices());

        wallpaperDefault.setSelected(WallpaperPrefs.MODE_DEFAULT.equals(wallpaperMode));
        wallpaperCustom.setSelected(WallpaperPrefs.MODE_CUSTOM.equals(wallpaperMode));
        wallpaperIndia.setSelected(WallpaperPrefs.MODE_INDIA.equals(wallpaperMode));
        String label = IndiaEvents.modeLabel(wallpaperMode, LocalDate.now());
        if (WallpaperPrefs.MODE_CUSTOM.equals(wallpaperMode) && !WallpaperPrefs.hasCustom(this)) {
            label = getString(R.string.wallpaper_custom_missing);
        }
        wallpaperState.setText(getString(R.string.wallpaper_state, label));
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects") || choices.contains(WallpaperPrefs.KEY_MODE);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
