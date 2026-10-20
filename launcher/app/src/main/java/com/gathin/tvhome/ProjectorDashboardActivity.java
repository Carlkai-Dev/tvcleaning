package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhome.ProjectorCapabilities.Feature;
import com.gathin.tvhome.ProjectorCapabilities.State;

/**
 * Projector Dashboard: official entry points only.
 * A row whose capability is not actionable explains that instead of performing a fake action;
 * direct control is offered only where ProjectorDetector probed a public entry point.
 */
public final class ProjectorDashboardActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Config.load(this).projectorMode) { finish(); return; }
        setContentView(R.layout.activity_projector_dashboard);
        ((TextView) findViewById(R.id.projectorDevice)).setText(
                getString(R.string.projector_device, Build.MANUFACTURER, Build.MODEL, Build.DEVICE));
        ProjectorCapabilities caps = ProjectorDetector.detect(this);
        LinearLayout rows = findViewById(R.id.projectorRows);
        addRow(rows, R.string.projector_settings, caps.state(Feature.PROJECTOR_SETTINGS),
                () -> open(ProjectorDetector.projectorSettingsIntent(this)));
        addRow(rows, R.string.inputs, caps.state(Feature.HDMI), () -> {
            startActivity(new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_SHOW_INPUTS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        addRow(rows, R.string.quick_picture, caps.state(Feature.PICTURE), () -> {
            Intent display = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            open(display.resolveActivity(getPackageManager()) != null
                    ? display : new Intent(Settings.ACTION_SETTINGS));
        });
        addRow(rows, R.string.projector_audio, caps.state(Feature.AUDIO),
                () -> open(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        // Advanced projection controls live in the projector's own menu/remote, not in Android:
        // when no Android entry point exists the row explains the real hardware path instead of
        // only saying unavailable (owner-reported on the T5 Max).
        addRow(rows, R.string.projector_autofocus, caps.state(Feature.AUTOFOCUS),
                R.string.projector_guide_autofocus,
                () -> open(ProjectorDetector.projectorSettingsIntent(this)));
        addRow(rows, R.string.projector_keystone, caps.state(Feature.KEYSTONE),
                R.string.projector_guide_keystone,
                () -> open(ProjectorDetector.projectorSettingsIntent(this)));
        addRow(rows, R.string.projector_zoom, caps.state(Feature.ZOOM),
                R.string.projector_guide_keystone,
                () -> open(ProjectorDetector.projectorSettingsIntent(this)));
        addRow(rows, R.string.tv_settings, caps.state(Feature.SYSTEM),
                () -> open(new Intent(Settings.ACTION_SETTINGS)));
        addRow(rows, R.string.projector_diagnostics, caps.state(Feature.DIAGNOSTICS),
                () -> startActivity(new Intent(this, ProjectorHealthActivity.class)));
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    private void addRow(LinearLayout rows, int title, State state, Runnable action) {
        addRow(rows, title, state, 0, action);
    }

    private void addRow(LinearLayout rows, int title, State state, int guide, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(lp);
        row.setPadding((int) (18 * getResources().getDisplayMetrics().density),
                (int) (10 * getResources().getDisplayMetrics().density),
                (int) (18 * getResources().getDisplayMetrics().density),
                (int) (10 * getResources().getDisplayMetrics().density));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(18);
        TextView s = new TextView(this);
        // Unsupported advanced controls name the hardware path that actually works.
        s.setText(state == State.UNSUPPORTED && guide != 0 ? guide : stateLabel(state));
        s.setTextColor(0xFFACB8C1);
        s.setTextSize(14);
        row.addView(t);
        row.addView(s);
        boolean actionable = ProjectorCapabilities.actionable(state);
        row.setAlpha(actionable ? 1f : 0.55f);
        row.setOnClickListener(v -> {
            if (!actionable) {
                Toast.makeText(this, guide != 0 ? guide : R.string.not_available, Toast.LENGTH_LONG).show();
                return;
            }
            action.run();
        });
        rows.addView(row);
    }

    private int stateLabel(State s) {
        switch (s) {
            case SUPPORTED_DIRECT: return R.string.projector_state_direct;
            case SUPPORTED_SETTINGS_SHORTCUT: return R.string.projector_state_shortcut;
            case REQUIRES_PRIVILEGED_ACCESS: return R.string.projector_state_privileged;
            case UNSUPPORTED: return R.string.projector_state_unsupported;
            default: return R.string.projector_state_unknown;
        }
    }

    private void open(Intent i) {
        if (i == null) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
