package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Recommended picture settings (picture_guide_rows), shown in the settings panel next to the TV's own
 * Picture menu. Read-only: the TV keeps these values in its own protected store, so OK on any row opens
 * the Picture settings to change them there. Reached from the Settings pill's menu when config
 * picture_guide is on.
 */
public class PictureGuideActivity extends Activity {

    private UiSounds sounds;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        ((TextView) findViewById(R.id.panelTitle)).setText(R.string.picture_guide);
        ((TextView) findViewById(R.id.panelHint)).setText(R.string.picture_guide_hint);
        sounds = new UiSounds(this, Config.load(this).uiSounds);
        LinearLayout rows = findViewById(R.id.rows);
        addRow(rows, getString(R.string.picture_guide_open), "");
        for (String item : getResources().getStringArray(R.array.picture_guide_rows)) {
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
            openPicture();
        });
        rows.addView(row);
    }

    /** The TV's Picture menu (DISPLAY_SETTINGS opens it on Sony), or Settings where that is missing. */
    private void openPicture() {
        for (String action : new String[]{Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }
}
