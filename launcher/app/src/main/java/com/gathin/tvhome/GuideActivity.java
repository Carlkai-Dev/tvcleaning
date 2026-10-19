package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Recommended picture or sound settings (picture_guide_rows / sound_guide_rows), shown in the settings
 * panel next to the TV's own menu. Read-only: the TV keeps these values in a store only its system apps
 * may write, so OK on any row opens the matching TV settings page to change them there. Reached from the
 * Settings pill's menu when config tv_guides is on.
 */
public class GuideActivity extends Activity {

    static final String EXTRA_SOUND = "sound";

    // TV settings pages, tried in order. Sony's Sound page has an OEM action; the standard sound action
    // opens the combined Display & Sound page there.
    private static final String[] PICTURE_ACTIONS = {Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS};
    private static final String[] SOUND_ACTIONS = {"android.settings.oem.SOUND_SETTINGS",
            Settings.ACTION_SOUND_SETTINGS, Settings.ACTION_SETTINGS};

    private UiSounds sounds;
    private String[] actions;

    static Intent intent(Context ctx, boolean sound) {
        return new Intent(ctx, GuideActivity.class).putExtra(EXTRA_SOUND, sound);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        boolean sound = getIntent().getBooleanExtra(EXTRA_SOUND, false);
        actions = sound ? SOUND_ACTIONS : PICTURE_ACTIONS;
        ((TextView) findViewById(R.id.panelTitle)).setText(sound ? R.string.sound_guide : R.string.picture_guide);
        ((TextView) findViewById(R.id.panelHint)).setText(sound ? R.string.sound_guide_hint : R.string.picture_guide_hint);
        sounds = new UiSounds(this, Config.load(this).uiSounds);
        LinearLayout rows = findViewById(R.id.rows);
        addRow(rows, getString(sound ? R.string.sound_guide_open : R.string.picture_guide_open), "");
        for (String item : getResources().getStringArray(sound ? R.array.sound_guide_rows : R.array.picture_guide_rows)) {
            int bar = item.indexOf('|');
            addRow(rows, bar < 0 ? item : item.substring(0, bar), bar < 0 ? "" : item.substring(bar + 1));
        }
        rows.getChildAt(0).requestFocus();
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

    private void addRow(LinearLayout rows, String title, String value) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        lp.height = (int) (44 * getResources().getDisplayMetrics().density);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        status.setText(value);
        status.setTextSize(14f);
        row.setOnFocusChangeListener((v, focused) -> {
            if (focused) sounds.focus();
        });
        row.setOnClickListener(v -> {
            sounds.select();
            openTvSettings();
        });
        rows.addView(row);
    }

    private void openTvSettings() {
        for (String action : actions) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }
}
