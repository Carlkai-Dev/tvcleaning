package io.github.epochcoder.homelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every setting in config.json, edited on the TV with the remote. Lists that live on a server (Immich albums
 * and tags, *arr quality profiles and root folders) are picked from what the server returns rather than
 * typed, and each server has a Test button. Changes are saved when the screen is left; everything reads
 * config.json at start, so they apply the next time a tile opens or the screensaver starts.
 *
 * The only setting not editable here is the extra CA certificate: it cannot sensibly be typed with a remote,
 * so it comes with companion/push-config.sh --ca, into its own file, and this screen leaves it alone.
 */
public class SettingsActivity extends Activity {
    private static final int LABEL_DP = 260;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** The whole file, edited in place, so keys this screen does not show survive a save. */
    private JSONObject json;
    private String savedText;
    /** Copy each text field into {@link #json}; switches and pickers write theirs at once. */
    private final List<Runnable> commits = new ArrayList<>();
    private LinearLayout form;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // The keyboard opens when a field is chosen with OK, not as soon as the screen appears.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        json = Config.loadJson(this);

        ScrollView scroll = new ScrollView(this);
        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(48), dp(27), dp(48), dp(27));
        scroll.addView(form);
        setContentView(scroll);

        heading(R.string.settings_name, 28);
        note(getString(R.string.s_tip));

        immich();
        homepage();
        arr(R.string.radarr, "radarr", Arr.Kind.MOVIE);
        arr(R.string.sonarr, "sonarr", Arr.Kind.SERIES);
        qbittorrent();

        heading(R.string.s_certificates, 22);
        note(getString(Config.load(this).caPem.isEmpty() ? R.string.s_ca_none : R.string.s_ca_set));

        action(R.string.s_save, () -> {
            if (commitAndSave()) {
                Toast.makeText(this, R.string.s_saved, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        // Building the form adds empty sections and normalises fields; only a real edit should write the file.
        for (Runnable r : commits) r.run();
        savedText = json.toString();
    }

    // Sections, one per service.

    private void immich() {
        JSONObject im = section("immich");
        heading(R.string.dream_name, 22);
        text(im, "url", R.string.s_url, InputType.TYPE_TEXT_VARIATION_URI);
        text(im, "api_key", R.string.s_api_key, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        test(() -> {
            int n = immich(im).albums().length();
            return getResources().getQuantityString(R.plurals.s_connected_albums, n, n);
        });
        number(im, "recent_days", R.string.s_recent_days);
        number(im, "max_photos", R.string.s_max_photos);
        number(im, "interval_seconds", R.string.s_interval);
        toggle(im, "shuffle", R.string.s_shuffle, true);
        toggle(im, "crop", R.string.s_crop, false);
        toggle(im, "show_clock", R.string.s_clock, true);
        toggle(im, "details", R.string.s_details, true);
        toggle(im, "motion", R.string.s_motion, true);
        toggle(im, "pair_portraits", R.string.s_pair, true);
        pickMany(im, "exclude_albums", R.string.s_exclude_albums,
                () -> names(immich(im).albums(), "albumName"));
        pickMany(im, "exclude_tags", R.string.s_exclude_tags,
                () -> names(immich(im).tags(), "value"));
        action(R.string.s_preview, () -> {
            commitAndSave();
            startActivity(new Intent(this, SlideshowActivity.class));
        });
    }

    private void homepage() {
        JSONObject hp = section("homepage");
        heading(R.string.dashboard_name, 22);
        text(hp, "url", R.string.s_url, InputType.TYPE_TEXT_VARIATION_URI);
        number(hp, "text_zoom", R.string.s_text_zoom);
        toggle(hp, "keep_awake", R.string.s_keep_awake, false);
    }

    private void qbittorrent() {
        JSONObject qb = section("qbittorrent");
        heading(R.string.qbittorrent, 22);
        text(qb, "url", R.string.s_url, InputType.TYPE_TEXT_VARIATION_URI);
        text(qb, "username", R.string.s_username, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        text(qb, "password", R.string.s_password, InputType.TYPE_TEXT_VARIATION_PASSWORD);
        note(getString(R.string.s_qbit_note));
        test(() -> {
            Config.QBittorrent c = new Config.QBittorrent(qb);
            need(c.configured(), R.string.s_fill_in_url);
            return getString(R.string.s_connected_to, new Qbit(c, tls()).version());
        });
    }

    private void arr(int title, String name, Arr.Kind kind) {
        JSONObject s = section(name);
        heading(title, 22);
        text(s, "url", R.string.s_url, InputType.TYPE_TEXT_VARIATION_URI);
        text(s, "api_key", R.string.s_api_key, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        test(() -> getString(R.string.s_connected_to, arr(kind, s).version()));
        pickOne(s, "quality_profile", R.string.s_quality_profile,
                () -> names(arr(kind, s).qualityProfiles(), "name"));
        pickOne(s, "root_folder", R.string.s_root_folder,
                () -> names(arr(kind, s).rootFolders(), "path"));
        if (kind == Arr.Kind.SERIES) {
            String[] labels = new String[Arr.SERIES_MONITOR.length];
            for (int i = 0; i < labels.length; i++) labels[i] = DetailActivity.monitorLabel(this, Arr.SERIES_MONITOR[i]);
            choice(s, "monitor", R.string.s_monitor, Arr.SERIES_MONITOR, labels);
        }
    }

    @Override
    protected void onPause() {
        commitAndSave();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private boolean commitAndSave() {
        for (Runnable r : commits) r.run();
        String text = json.toString();
        if (text.equals(savedText)) return true;
        try {
            Config.save(this, json);
            savedText = text;
            return true;
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.error, getString(R.string.settings_name), e.getMessage()),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** ca_pem in config.json and the separately pushed ca.pem; neither is edited on this screen. */
    private javax.net.ssl.SSLSocketFactory tls() {
        return Tls.socketFactory(Config.load(this).caPem);
    }

    private JSONObject section(String name) {
        JSONObject s = json.optJSONObject(name);
        if (s == null) {
            s = new JSONObject();
            put(json, name, s);
        }
        return s;
    }

    // Rows. Each is a label on the left and its control on the right, one D-pad stop per control.

    private void text(JSONObject s, String key, int label, int variation) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | variation);
        e.setText(s.optString(key));
        commits.add(() -> {
            String v = e.getText().toString().trim();
            if (v.isEmpty()) s.remove(key); else put(s, key, v);
        });
        row(label, e);
    }

    /** Empty means the default, which the hint shows. */
    private void number(JSONObject s, String key, int label) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (s.has(key)) e.setText(String.valueOf(s.optInt(key)));
        commits.add(() -> {
            try {
                put(s, key, Integer.parseInt(e.getText().toString().trim()));
            } catch (NumberFormatException ignored) {
                s.remove(key);
            }
        });
        row(label, e);
    }

    private void toggle(JSONObject s, String key, int label, boolean def) {
        Switch sw = new Switch(this);
        sw.setChecked(s.optBoolean(key, def));
        sw.setOnCheckedChangeListener((b, on) -> put(s, key, on));
        row(label, sw);
    }

    private interface Fetch {
        List<String> names() throws Exception;
    }

    /** A button naming the current choice; OK loads the server's list and offers it as checkboxes. */
    private void pickMany(JSONObject s, String key, int label, Fetch fetch) {
        Button b = button();
        Runnable summary = () -> b.setText(join(strings(s.optJSONArray(key)), getString(R.string.s_none)));
        summary.run();
        b.setOnClickListener(v -> load(fetch, names -> {
            List<String> current = strings(s.optJSONArray(key));
            boolean[] checked = new boolean[names.size()];
            for (int i = 0; i < checked.length; i++) checked[i] = containsIgnoreCase(current, names.get(i));
            new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setMultiChoiceItems(names.toArray(new String[0]), checked, (d, which, on) -> checked[which] = on)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        JSONArray picked = new JSONArray();
                        for (int i = 0; i < checked.length; i++) if (checked[i]) picked.put(names.get(i));
                        put(s, key, picked);
                        summary.run();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }));
        row(label, b);
    }

    /** Like pickMany with one answer; the first entry, the server's default, removes the key. */
    private void pickOne(JSONObject s, String key, int label, Fetch fetch) {
        Button b = button();
        Runnable summary = () -> b.setText(s.optString(key).isEmpty() ? getString(R.string.s_server_default) : s.optString(key));
        summary.run();
        b.setOnClickListener(v -> load(fetch, names -> {
            List<String> options = new ArrayList<>();
            options.add(getString(R.string.s_server_default));
            options.addAll(names);
            int selected = 0;
            for (int i = 1; i < options.size(); i++) if (options.get(i).equalsIgnoreCase(s.optString(key))) selected = i;
            new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setSingleChoiceItems(options.toArray(new String[0]), selected, (d, which) -> {
                        if (which == 0) s.remove(key); else put(s, key, options.get(which));
                        summary.run();
                        d.dismiss();
                    })
                    .show();
        }));
        row(label, b);
    }

    /** A fixed list of choices stored by value; the first is the default and removes the key. */
    private void choice(JSONObject s, String key, int label, String[] values, String[] labels) {
        Button b = button();
        Runnable summary = () -> {
            int i = java.util.Arrays.asList(values).indexOf(s.optString(key, values[0]));
            b.setText(labels[Math.max(0, i)]);
        };
        summary.run();
        b.setOnClickListener(v -> {
            int selected = Math.max(0, java.util.Arrays.asList(values).indexOf(s.optString(key, values[0])));
            new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setSingleChoiceItems(labels, selected, (d, which) -> {
                        if (which == 0) s.remove(key); else put(s, key, values[which]);
                        summary.run();
                        d.dismiss();
                    })
                    .show();
        });
        row(label, b);
    }

    private interface Check {
        String run() throws Exception;
    }

    /** A client for the section as it is now filled in, or the reason it cannot be one yet. */
    private Immich immich(JSONObject section) throws java.io.IOException {
        Config.Immich c = new Config.Immich(section);
        need(c.configured(), R.string.s_fill_in);
        return new Immich(c, tls());
    }

    private Arr arr(Arr.Kind kind, JSONObject section) throws java.io.IOException {
        Config.Arr c = new Config.Arr(section);
        need(c.configured(), R.string.s_fill_in);
        return new Arr(kind, c, tls());
    }

    /** Stops a Test or a picker before it sends a request to an empty address, with words instead of "no protocol". */
    private void need(boolean configured, int message) throws java.io.IOException {
        if (!configured) throw new java.io.IOException(getString(message));
    }

    private void test(Check check) {
        Button b = button();
        b.setText(R.string.s_test);
        TextView result = new TextView(this);
        result.setPadding(dp(16), 0, 0, 0);
        b.setOnClickListener(v -> {
            for (Runnable r : commits) r.run();
            result.setText(R.string.loading);
            io.execute(() -> {
                String msg;
                try {
                    msg = check.run();
                } catch (Exception e) {
                    msg = e.getMessage();
                }
                String m = msg;
                runOnUiThread(() -> result.setText(m));
            });
        });
        LinearLayout pair = new LinearLayout(this);
        pair.setGravity(android.view.Gravity.CENTER_VERTICAL);
        pair.addView(b);
        pair.addView(result);
        row(0, pair);
    }

    private interface Loaded {
        void show(List<String> names);
    }

    /** Fetches a server list off the main thread with the fields as they are now, then shows it. */
    private void load(Fetch fetch, Loaded loaded) {
        for (Runnable r : commits) r.run();
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                List<String> names = fetch.names();
                runOnUiThread(() -> {
                    if (!isFinishing()) loaded.show(names);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void action(int label, Runnable r) {
        Button b = button();
        b.setText(label);
        b.setOnClickListener(v -> r.run());
        row(0, b);
    }

    private void row(int label, View control) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView l = new TextView(this);
        if (label != 0) l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        row.addView(l, new LinearLayout.LayoutParams(dp(LABEL_DP), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(control, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        form.addView(row);
    }

    private Button button() {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        return b;
    }

    private void heading(int text, int sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(24), 0, dp(8));
        form.addView(t);
    }

    private void note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setAlpha(0.7f);
        form.addView(t);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static List<String> names(JSONArray items, String key) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            String n = o == null ? "" : o.optString(key).trim();
            if (!n.isEmpty() && !out.contains(n)) out.add(n);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) if (!a.optString(i).isEmpty()) out.add(a.optString(i));
        return out;
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        for (String each : list) if (each.toLowerCase(Locale.ROOT).equals(s.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static String join(List<String> list, String empty) {
        return list.isEmpty() ? empty : String.join(", ", list);
    }

    private static void put(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
