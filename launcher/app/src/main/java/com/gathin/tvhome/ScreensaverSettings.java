package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Screensaver options: when it starts, 12/24-hour clock, dim at night, and a preview. Each row cycles through
 * its values on OK. It shows up as a "Screensaver" tile in the grid and as the gear next to the screensaver in
 * the TV's own settings.
 */
public class ScreensaverSettings extends Activity {

    static final String PREFS = "screensaver";
    static final String KEY_CLOCK = "clock";      // "system", "12" or "24"
    static final String KEY_NIGHT = "night_dim";  // boolean
    private static final int[] START_MINUTES = {5, 10, 15, 30, 60};

    private SharedPreferences prefs;
    private TextView startValue, clockValue, nightValue;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = prefs(this);
        float d = getResources().getDisplayMetrics().density;

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundResource(R.drawable.wallpaper);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        panel.setPadding((int) (8 * d), (int) (8 * d), (int) (8 * d), (int) (8 * d));

        TextView title = new TextView(this);
        title.setText(R.string.screensaver);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(getResources().getFont(R.font.outfit));
        title.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (12 * d));
        panel.addView(title);

        startValue = addRow(panel, R.string.start_after, v -> cycleStart());
        clockValue = addRow(panel, R.string.clock_format, v -> cycleClock());
        nightValue = addRow(panel, R.string.dim_at_night, v -> {
            prefs.edit().putBoolean(KEY_NIGHT, !prefs.getBoolean(KEY_NIGHT, false)).apply();
            refresh();
        });
        addRow(panel, R.string.preview, v -> preview()).setText("");

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                (int) (440 * d), ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER);
        root.addView(panel, lp);
        setContentView(root);
        panel.getChildAt(1).requestFocus();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();   // the start time can change in the TV's settings too
    }

    private TextView addRow(LinearLayout panel, int label, View.OnClickListener click) {
        View row = getLayoutInflater().inflate(R.layout.item_input, panel, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(label);
        TextView value = row.findViewById(R.id.status);
        value.setTextSize(15);
        row.setOnClickListener(click);
        panel.addView(row);
        return value;
    }

    private void refresh() {
        int min = timeoutMinutes();
        startValue.setText(min > 0 ? getString(R.string.minutes, min) : "");
        String clock = prefs.getString(KEY_CLOCK, "system");
        clockValue.setText("12".equals(clock) ? R.string.clock_12 : "24".equals(clock) ? R.string.clock_24 : R.string.clock_system);
        nightValue.setText(prefs.getBoolean(KEY_NIGHT, false) ? R.string.on : R.string.off);
    }

    private int timeoutMinutes() {
        int ms = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 0);
        return ms / 60_000;
    }

    private void cycleStart() {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, R.string.needs_permission, Toast.LENGTH_LONG).show();
            return;
        }
        int cur = timeoutMinutes(), next = START_MINUTES[0];
        for (int i = 0; i < START_MINUTES.length; i++) {
            if (START_MINUTES[i] == cur) next = START_MINUTES[(i + 1) % START_MINUTES.length];
        }
        // A value we did not set (say 2 minutes) jumps to the first option above it.
        boolean known = false;
        for (int m : START_MINUTES) known |= m == cur;
        if (!known) {
            for (int m : START_MINUTES) if (m > cur) { next = m; break; }
        }
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, next * 60_000);
        refresh();
    }

    private void cycleClock() {
        String cur = prefs.getString(KEY_CLOCK, "system");
        prefs.edit().putString(KEY_CLOCK, "system".equals(cur) ? "12" : "12".equals(cur) ? "24" : "system").apply();
        refresh();
    }

    private void preview() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator"));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
