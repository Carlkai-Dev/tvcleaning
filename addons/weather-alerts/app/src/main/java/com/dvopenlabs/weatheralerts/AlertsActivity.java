package com.dvopenlabs.weatheralerts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.Instant;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** A manually opened NWS viewer; never takes Home or requests notification/overlay access. */
public final class AlertsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private String area;
    private Button choose, refresh;
    private TextView status, empty;
    private ListView list;
    private boolean active;
    private long generation;
    private AlertsRequest request;
    private Future<?> task;
    private List<Alerts.Alert> alerts = new ArrayList<>();
    private long checked;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("area", MODE_PRIVATE);
        area = preferences.getString("code", "");
        if (Areas.index(area) < 0) area = "";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(24), dp(40), dp(20));
        root.setBackgroundColor(0xFF0D1624);
        setContentView(root);
        TextView title = text(getString(R.string.app_name), 30, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);
        root.addView(text(getString(R.string.source), 14, 0xFFB7C5D9));
        LinearLayout controls = new LinearLayout(this);
        controls.setPadding(0, dp(12), 0, dp(8));
        choose = button(getString(R.string.choose_state));
        choose.setId(View.generateViewId());
        refresh = button(getString(R.string.refresh));
        refresh.setId(View.generateViewId());
        controls.addView(choose, new LinearLayout.LayoutParams(dp(300), dp(48)));
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(130), dp(48));
        refreshParams.leftMargin = dp(12);
        controls.addView(refresh, refreshParams);
        root.addView(controls);
        root.addView(text(getString(R.string.coverage), 13, 0xFFB7C5D9));
        status = text("", 15, 0xFFFFCC88);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);
        empty = text("", 20, Color.WHITE);
        empty.setPadding(0, dp(12), 0, dp(12));
        root.addView(empty);
        list = new ListView(this);
        list.setId(View.generateViewId());
        list.setItemsCanFocus(false);
        list.setSelector(surface(0xFF275176, 0xFFFFFFFF));
        list.setDrawSelectorOnTop(false);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(8));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(text(getString(R.string.footer), 12, 0xFFB7C5D9));
        choose.setNextFocusRightId(refresh.getId());
        refresh.setNextFocusLeftId(choose.getId());
        list.setNextFocusUpId(refresh.getId());
        for (Button control : new Button[]{choose, refresh}) control.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (list.getCount() > 0) { list.requestFocus(); list.setSelection(0); }
                return true;
            }
            return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_LEFT && v == choose
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT && v == refresh;
        });
        list.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int index = list.getSelectedItemPosition();
            if (key == KeyEvent.KEYCODE_DPAD_UP && index <= 0) { refresh.requestFocus(); return true; }
            return key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                    || key == KeyEvent.KEYCODE_DPAD_DOWN && index == list.getCount() - 1;
        });
        choose.setOnClickListener(v -> chooseArea());
        refresh.setOnClickListener(v -> refresh());
        list.setOnItemClickListener((parent, view, position, id) -> details(alerts.get(position)));
        render();
        choose.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        if (!area.isEmpty()) refresh();
    }

    @Override protected void onPause() {
        active = false;
        cancel();
        super.onPause();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void cancel() {
        generation++;
        if (request != null) request.cancel();
        if (task != null) task.cancel(true);
        request = null;
        task = null;
    }

    private void chooseArea() {
        new AlertDialog.Builder(this).setTitle(R.string.choose_state)
                .setSingleChoiceItems(Areas.NAMES, Areas.index(area), (dialog, which) -> {
                    String next = Areas.CODES[which];
                    dialog.dismiss();
                    if (next.equals(area)) return;
                    cancel();
                    area = next;
                    preferences.edit().putString("code", area).apply();
                    checked = 0;
                    alerts.clear();
                    render();
                    refresh();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void refresh() {
        if (!active || area.isEmpty()) return;
        cancel();
        long token = generation;
        String selected = area;
        AlertsRequest pending = new AlertsRequest();
        request = pending;
        status.setText(getString(R.string.checking, Areas.NAMES[Areas.index(area)]));
        // Keep the button focusable while loading; another press replaces rather than queues a request.
        task = worker.submit(() -> {
            List<Alerts.Alert> result = null;
            try { result = Alerts.parse(pending.fetch(selected), Instant.now()); }
            catch (Exception ignored) { /* The UI distinguishes unavailable from a successful empty feed. */ }
            List<Alerts.Alert> received = result;
            main.post(() -> {
                if (!active || token != generation || !selected.equals(area)) return;
                request = null;
                task = null;
                if (received == null) {
                    status.setText(checked == 0 ? getString(R.string.failed)
                            : getString(R.string.stale, time(checked)));
                    return;
                }
                alerts = received;
                checked = System.currentTimeMillis();
                render();
            });
        });
    }

    private void render() {
        boolean configured = !area.isEmpty();
        choose.setText(configured ? Areas.NAMES[Areas.index(area)] : getString(R.string.choose_state));
        refresh.setEnabled(configured);
        empty.setVisibility(alerts.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(configured ? checked == 0 ? "" : getString(R.string.empty) : getString(R.string.not_configured));
        if (checked == 0) status.setText("");
        else {
            String summary = getString(R.string.checked, getResources().getQuantityString(R.plurals.alert_count,
                    alerts.size(), alerts.size()), time(checked));
            status.setText(alerts.size() > Alerts.MAX_SHOWN
                    ? getString(R.string.limited_summary, summary, Alerts.MAX_SHOWN, alerts.size()) : summary);
        }
        // Use a mutable list: ArrayAdapter's array constructor cannot support later catalogue changes.
        List<String> rows = new ArrayList<>();
        for (Alerts.Alert alert : alerts.subList(0, Math.min(alerts.size(), Alerts.MAX_SHOWN)))
            rows.add(alert.severity() + " · " + alert.headline() + "\n" + alert.area());
        int selection = Math.max(0, list.getSelectedItemPosition());
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setTextSize(18);
                row.setTextColor(Color.WHITE);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                row.setMaxLines(3);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setMinHeight(dp(76));
                return row;
            }
        });
        list.setSelection(Math.min(selection, rows.size() - 1));
    }

    private void details(Alerts.Alert alert) {
        ScrollView scroll = new ScrollView(this);
        TextView body = text(alert.severity() + "\n" + alert.area() + "\n\n"
                + getString(R.string.expires, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date.from(alert.expires()))) + "\n\n"
                + (alert.expires().isAfter(Instant.now()) ? "" : getString(R.string.expired) + "\n\n")
                + alert.description() + "\n\n" + (alert.instruction().isEmpty()
                ? getString(R.string.no_instructions) : alert.instruction()), 18, Color.WHITE);
        body.setPadding(dp(24), dp(12), dp(24), dp(24));
        scroll.addView(body);
        scroll.setFocusable(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(alert.headline()).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnShowListener(d -> scroll.requestFocus());
        dialog.show();
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},
                new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{0xFF101B2A, 0xFF7F8FA4, Color.WHITE}));
        button.setBackground(focusBackground());
        return button;
    }

    private android.graphics.drawable.StateListDrawable focusBackground() {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, surface(0xFFFFCC88, 0xFFFFFFFF));
        states.addState(new int[]{android.R.attr.state_selected}, surface(0xFF275176, 0xFFFFFFFF));
        states.addState(new int[]{}, surface(0xFF1B2A40, 0xFF52708F));
        return states;
    }

    private GradientDrawable surface(int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(2), stroke);
        return shape;
    }

    private TextView text(String label, int size, int color) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.START);
        return text;
    }

    private String time(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(millis));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
