package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dadb.AdbKeyPair;
import dadb.Dadb;

/**
 * One-tap clean-up and setup, the on-TV version of tools/disable.sh + launcher/build-and-install.sh.
 *
 * Disabling other apps needs shell permission that a normal APK does not have, so this connects to the TV's own
 * adb daemon on 127.0.0.1:5555 (USB/network debugging must be on) and runs the same commands the scripts do.
 * The TV shows an "Allow USB debugging?" prompt the first time; tick "Always allow" and press OK.
 * Nothing is uninstalled, and Restore undoes every step.
 */
public class SetupActivity extends Activity {

    private static final String PREFS = "setup";
    private static final String KEY_DISABLED = "disabled";
    private static final String STOCK_LAUNCHER = "com.google.android.tvlauncher";
    private static final String ME = "com.gathin.tvhome";
    private static final String PLAY_STORE = "com.android.vending";

    private TextView log;
    private ScrollView scroll;
    private Button setupBtn, restoreBtn;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.wallpaper);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        int pad = (int) (28 * d);
        panel.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.tv_setup);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        panel.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Removes ads, telemetry and unused apps, and sets this launcher as home. "
                + "Nothing is uninstalled; Restore undoes it. The TV will ask you to allow USB debugging once.");
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(15);
        hint.setPadding(0, (int) (6 * d), 0, (int) (14 * d));
        panel.addView(hint);

        LinearLayout row = new LinearLayout(this);
        setupBtn = button("Clean & set up");
        restoreBtn = button("Restore original");
        row.addView(setupBtn);
        row.addView(restoreBtn);
        panel.addView(row);

        log = new TextView(this);
        log.setTextColor(0xFFDDDDDD);
        log.setTextSize(14);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setPadding(0, (int) (14 * d), 0, 0);
        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.addView(log);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (760 * d), (int) (520 * d), Gravity.CENTER);
        root.addView(panel, lp);
        setContentView(root);

        setupBtn.setOnClickListener(v -> start(true));
        restoreBtn.setOnClickListener(v -> start(false));
        android.view.View.OnFocusChangeListener tint = (v, has) -> ((Button) v).setTextColor(has ? Color.BLACK : Color.WHITE);
        setupBtn.setOnFocusChangeListener(tint);
        restoreBtn.setOnFocusChangeListener(tint);
        setupBtn.requestFocus();
    }

    private Button button(String text) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextSize(16);
        bt.setPadding(40, 0, 40, 0);
        bt.setTextColor(Color.WHITE);
        bt.setBackground(focusBackground());
        return bt;
    }

    /** Dark pill that turns white with dark text while focused, so the remote's selection is obvious. */
    private android.graphics.drawable.Drawable focusBackground() {
        android.graphics.drawable.GradientDrawable idle = new android.graphics.drawable.GradientDrawable();
        idle.setColor(0x33FFFFFF);
        idle.setCornerRadius(60f);
        android.graphics.drawable.GradientDrawable focus = new android.graphics.drawable.GradientDrawable();
        focus.setColor(Color.WHITE);
        focus.setCornerRadius(60f);
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focus);
        sl.addState(new int[]{}, idle);
        return sl;
    }

    private void start(boolean setup) {
        if (busy) return;
        busy = true;
        setupBtn.setEnabled(false);
        restoreBtn.setEnabled(false);
        log.setText("");
        new Thread(() -> {
            try {
                run(setup);
            } catch (Throwable t) {
                say("");
                say("Failed: " + t);
                say("Check that USB / network debugging is on in Developer options, then try again.");
            } finally {
                busy = false;
                runOnUiThread(() -> {
                    setupBtn.setEnabled(true);
                    restoreBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private void run(boolean setup) throws Exception {
        say("Connecting to the TV's debug bridge...");
        say("If a prompt appears, tick \"Always allow\" and press OK.");
        File priv = new File(getFilesDir(), "adbkey");
        File pub = new File(getFilesDir(), "adbkey.pub");
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub);
        try (Dadb adb = Dadb.create("127.0.0.1", 5555, AdbKeyPair.read(priv, pub))) {
            say("Connected.");
            if (setup) doSetup(adb); else doRestore(adb);
        }
    }

    // ---- setup ----

    private void doSetup(Dadb adb) throws Exception {
        Set<String> installed = packages(adb, "pm list packages");
        Set<String> alreadyOff = packages(adb, "pm list packages -d");

        // Make this launcher home first and check it took, so the home-screen packages below are only disabled once
        // there is a replacement.
        say("Setting this launcher as home...");
        sh(adb, "pm grant " + ME + " android.permission.ACCESS_FINE_LOCATION");
        sh(adb, "appops set " + ME + " WRITE_SETTINGS allow");
        String set = sh(adb, "cmd package set-home-activity --user 0 " + ME + "/.MainActivity");
        String prefs = sh(adb, "dumpsys package preferred-xml");
        if (!set.contains("Success") || !prefs.contains("item name=\"" + ME + "/.MainActivity\"")) {
            say("Could not set this launcher as home (" + set.trim() + "). Nothing else was changed.");
            return;
        }

        List<String> todo = new ArrayList<>();
        for (String p : debloatList()) {
            if (installed.contains(p) && !alreadyOff.contains(p)) todo.add(p);
        }
        if (installed.contains(STOCK_LAUNCHER) && !alreadyOff.contains(STOCK_LAUNCHER)) todo.add(STOCK_LAUNCHER);

        say("Disabling " + todo.size() + " apps...");
        SharedPreferences store = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> done = new LinkedHashSet<>(store.getStringSet(KEY_DISABLED, new HashSet<>()));
        int ok = 0;
        for (String p : todo) {
            String out = sh(adb, "pm disable-user --user 0 " + p);
            if (out.contains("new state: disabled")) {
                ok++;
                done.add(p);
                say("  off  " + p);
            } else {
                say("  skip " + p);
            }
        }
        store.edit().putStringSet(KEY_DISABLED, done).apply();

        say("Stopping Play Store from updating apps during playback...");
        sh(adb, "cmd appops set " + PLAY_STORE + " RUN_IN_BACKGROUND deny");
        sh(adb, "cmd appops set " + PLAY_STORE + " RUN_ANY_IN_BACKGROUND deny");
        sh(adb, "am force-stop " + PLAY_STORE);

        say("");
        say("Done: " + ok + " apps disabled. Press Home on the remote.");
    }

    // ---- restore ----

    private void doRestore(Dadb adb) throws Exception {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_DISABLED, null);
        Set<String> targets = new LinkedHashSet<>();
        if (saved != null && !saved.isEmpty()) targets.addAll(saved);
        else targets.addAll(debloatList());
        targets.add(STOCK_LAUNCHER);

        say("Re-enabling " + targets.size() + " apps...");
        for (String p : targets) sh(adb, "pm enable " + p);

        say("Making the stock launcher home again...");
        String comp = sh(adb, "cmd package resolve-activity --brief -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME " + STOCK_LAUNCHER).trim();
        String[] lines = comp.split("\n");
        comp = lines[lines.length - 1].trim();
        if (comp.contains("/")) sh(adb, "cmd package set-home-activity --user 0 " + comp);

        sh(adb, "cmd appops set " + PLAY_STORE + " RUN_IN_BACKGROUND allow");
        sh(adb, "cmd appops set " + PLAY_STORE + " RUN_ANY_IN_BACKGROUND allow");
        prefs.edit().remove(KEY_DISABLED).apply();
        say("");
        say("Restored. Press Home on the remote.");
    }

    // ---- helpers ----

    private List<String> debloatList() throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("debloat.txt")))) {
            String line;
            while ((line = r.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                line = line.trim();
                if (!line.isEmpty()) out.add(line);
            }
        }
        return out;
    }

    private Set<String> packages(Dadb adb, String cmd) throws Exception {
        Set<String> set = new HashSet<>();
        for (String line : sh(adb, cmd).split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) set.add(line.substring(8));
        }
        return set;
    }

    private String sh(Dadb adb, String cmd) throws Exception {
        return adb.shell(cmd).getAllOutput();
    }

    private void say(String s) {
        runOnUiThread(() -> {
            log.append(s + "\n");
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}
