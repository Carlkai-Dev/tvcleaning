package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Settings views exist only while this small anchored panel is open. */
final class LauncherSettings {
    private final Activity activity;
    private final View anchor;
    private final Runnable apply;
    private final SharedPreferences prefs;
    private Dialog dialog;
    private LinearLayout panel;
    private LinearLayout rows;
    private int page;
    private int downPresses;
    private final float density;

    LauncherSettings(Activity activity, View anchor, Runnable apply) {
        this.activity = activity;
        this.anchor = anchor;
        this.apply = apply;
        density = activity.getResources().getDisplayMetrics().density;
        prefs = activity.getSharedPreferences("home", Activity.MODE_PRIVATE);
    }

    void show() {
        if (dialog != null) return;
        downPresses = 0;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackgroundResource(R.drawable.header_glass);
        dialog.setContentView(panel);
        dialog.setOnKeyListener((d, key, event) -> {
            if (key == KeyEvent.KEYCODE_BACK) {
                downPresses = 0;
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (page != 0) render(0, page == 1 ? 0 : page == 2 ? 1 : 3);
                    else dismiss();
                }
                return true;
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            View focus = dialog.getCurrentFocus();
            boolean last = page == 0 && focus != null && "advanced".equals(focus.getTag());
            if (last && key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getRepeatCount() == 0 && ++downPresses == 5) render(3, 0);
                return true;
            }
            downPresses = 0;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            return false;
        });
        dialog.setOnDismissListener(d -> {
            dialog = null;
            panel = null;
            rows = null;
            downPresses = 0;
            anchor.requestFocus();
        });
        render(0, 0);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setWindowAnimations(0);
        window.setGravity(Gravity.TOP | Gravity.END);
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        WindowManager.LayoutParams parameters = window.getAttributes();
        parameters.x = activity.getWindow().getDecorView().getWidth() - location[0] - anchor.getWidth();
        parameters.y = location[1] + anchor.getHeight() + dp(10);
        window.setAttributes(parameters);
        dialog.show();
        window.setLayout(dp(280), -2);
    }

    void dismiss() { if (dialog != null) dialog.dismiss(); }

    private void render(int destination, int selected) {
        page = destination;
        downPresses = 0;
        panel.removeAllViews();
        TextView heading = text(12, 0xFFDFDFE5);
        heading.setText(page == 1 ? R.string.launcher_clock : page == 2 ? R.string.launcher_appearance
                : page == 3 ? R.string.launcher_information : R.string.settings);
        heading.setPadding(dp(12), dp(5), dp(12), dp(10));
        panel.addView(heading);
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panel.addView(scroll);
        if (page == 0) {
            row("clock", R.string.launcher_clock, "›", () -> render(1, 0));
            row("appearance", R.string.launcher_appearance, "›", () -> render(2, 0));
            row("display", R.string.launcher_display, "↗", () -> open(Settings.ACTION_DISPLAY_SETTINGS));
            row("advanced", R.string.launcher_advanced, "↗", () -> open(Settings.ACTION_SETTINGS));
        } else if (page == 1) {
            cycle("clock_format", R.string.launcher_clock_format,
                    new int[]{R.string.launcher_12h, R.string.launcher_24h});
            toggle("clock_greeting", R.string.launcher_greeting);
            toggle("clock_date", R.string.launcher_date);
        } else if (page == 2) {
            cycle("appearance_halo", R.string.launcher_halo,
                    new int[]{R.string.launcher_normal, R.string.launcher_soft, R.string.launcher_off});
            cycle("appearance_dock", R.string.launcher_dock,
                    new int[]{R.string.launcher_current, R.string.launcher_lighter, R.string.launcher_opaque});
            cycle("appearance_motion", R.string.launcher_motion,
                    new int[]{R.string.launcher_normal, R.string.launcher_reduced, R.string.launcher_off});
        } else {
            String version;
            try { version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
            catch (android.content.pm.PackageManager.NameNotFoundException e) { version = "—"; }
            info(R.string.launcher_version, version);
            info(R.string.launcher_device, Build.MANUFACTURER + " · " + Build.MODEL);
            info(R.string.launcher_android, Build.VERSION.RELEASE);
        }
        rows.getChildAt(Math.min(selected, rows.getChildCount() - 1)).requestFocus();
    }

    private void cycle(String key, int label, int[] choices) {
        int value = Math.floorMod(prefs.getInt(key, 0), choices.length);
        row(key, label, activity.getString(choices[value]), () -> {
            int next = (Math.floorMod(prefs.getInt(key, 0), choices.length) + 1) % choices.length;
            prefs.edit().putInt(key, next).apply();
            apply.run();
            updateValue(key, label, activity.getString(choices[next]));
        });
    }

    private void toggle(String key, int label) {
        row(key, label, activity.getString(prefs.getBoolean(key, true) ? R.string.launcher_on : R.string.launcher_off), () -> {
            boolean next = !prefs.getBoolean(key, true);
            prefs.edit().putBoolean(key, next).apply();
            apply.run();
            updateValue(key, label, activity.getString(next ? R.string.launcher_on : R.string.launcher_off));
        });
    }

    private void updateValue(String key, int label, String value) {
        View row = rows.findViewWithTag(key);
        ((TextView) row.findViewById(android.R.id.text2)).setText(value);
        row.setContentDescription(activity.getString(label) + ": " + value);
    }

    private View row(String key, int label, String value, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setTag(key);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackgroundResource(R.drawable.input_row_bg);
        row.setFocusable(true);
        row.setClickable(true);
        TextView title = text(15, Color.WHITE);
        title.setText(label);
        title.setDuplicateParentStateEnabled(true);
        title.setTextColor(activity.getColorStateList(R.color.menu_text));
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView detail = text(12, Color.WHITE);
        detail.setId(android.R.id.text2);
        detail.setText(value);
        detail.setGravity(Gravity.END);
        detail.setDuplicateParentStateEnabled(true);
        detail.setTextColor(title.getTextColors());
        row.addView(detail, new LinearLayout.LayoutParams(-2, -2));
        row.setContentDescription(activity.getString(label) + ": " + value);
        row.setOnClickListener(v -> action.run());
        row.setOnFocusChangeListener((v, focused) -> { if (focused) downPresses = 0; });
        rows.addView(row, new LinearLayout.LayoutParams(-1, dp(42)));
        return row;
    }

    private void info(int label, String value) {
        TextView item = text(14, Color.WHITE);
        item.setText(activity.getString(R.string.launcher_info_value, activity.getString(label), value));
        item.setPadding(dp(12), dp(8), dp(12), dp(8));
        item.setFocusable(true);
        item.setBackgroundResource(R.drawable.input_row_bg);
        item.setTextColor(activity.getColorStateList(R.color.menu_text));
        rows.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView text(int size, int color) {
        TextView text = new TextView(activity);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(activity.getResources().getFont(R.font.outfit));
        text.setFontVariationSettings("'wght' 400");
        return text;
    }

    private int dp(int value) { return (int) (value * density); }

    private void open(String action) {
        dismiss();
        try { activity.startActivity(new Intent(action)); }
        catch (android.content.ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
