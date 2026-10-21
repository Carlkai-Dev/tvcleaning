package io.github.epochcoder.homelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One film or show from a search, with everything needed to decide: backdrop, poster, year, runtime or seasons,
 * ratings, overview, and whether it is already in the library. The first button has focus, so adding it and
 * starting the search is a single OK press: "Add and search" for something new, "Search again" for something
 * the library has. Quality and, for a show, which episodes to monitor sit next to it for when the defaults
 * from Settings are not what this one needs.
 */
public class DetailActivity extends Activity {
    private static final String EXTRA_KIND = "kind";
    private static final String EXTRA_JSON = "json";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Arr arr;
    private Arr.Item item;
    private Arr.Defaults defaults;
    private String monitor;
    private boolean busy;
    private boolean loadingDefaults;
    /** Add was pressed before the defaults arrived: add as soon as they do. */
    private boolean addWhenLoaded;

    private TextView library;
    private TextView status;
    private Button primary;
    private Button quality;
    private Button monitorButton;

    static Intent intent(Context ctx, Arr.Item item) {
        return new Intent(ctx, DetailActivity.class)
                .putExtra(EXTRA_KIND, item.kind.name())
                .putExtra(EXTRA_JSON, item.raw.toString());
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_detail);
        Arr.Kind kind = Arr.Kind.valueOf(getIntent().getStringExtra(EXTRA_KIND));
        try {
            item = new Arr.Item(kind, new JSONObject(getIntent().getStringExtra(EXTRA_JSON)));
        } catch (JSONException | NullPointerException e) {
            finish();
            return;
        }
        Config config = Config.load(this);
        Config.Arr cfg = kind == Arr.Kind.MOVIE ? config.radarr : config.sonarr;
        arr = new Arr(kind, cfg, Tls.socketFactory(config.caPem));
        monitor = cfg.monitor;

        library = findViewById(R.id.library);
        status = findViewById(R.id.status);
        primary = findViewById(R.id.primary);
        quality = findViewById(R.id.quality);
        monitorButton = findViewById(R.id.monitor);

        bind(item);
        primary.setOnClickListener(v -> onPrimary());
        quality.setOnClickListener(v -> pickQuality());
        monitorButton.setOnClickListener(v -> pickMonitor());
        primary.requestFocus();

        if (!item.inLibrary()) loadDefaults();
    }

    /**
     * The quality profile and root folder an add uses, fetched while the screen is read. A press on Add before
     * they arrive waits for them; a failure leaves Add able to try again.
     */
    private void loadDefaults() {
        if (loadingDefaults) return;
        loadingDefaults = true;
        quality.setText(R.string.loading);
        io.execute(() -> {
            try {
                Arr.Defaults d = arr.defaults();
                runOnUiThread(() -> {
                    loadingDefaults = false;
                    defaults = d;
                    quality.setText(getString(R.string.quality_button, d.profileName));
                    if (addWhenLoaded) {
                        addWhenLoaded = false;
                        onPrimary();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingDefaults = false;
                    addWhenLoaded = false;
                    quality.setText(R.string.s_quality_profile);
                    fail(e);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    /** Fills the screen from {@code i}; called again after an add with the library's answer. Package-private for previews. */
    void bind(Arr.Item i) {
        item = i;
        boolean movie = i.kind == Arr.Kind.MOVIE;
        ((TextView) findViewById(R.id.kind)).setText(movie ? R.string.kind_movie : R.string.kind_series);
        ((TextView) findViewById(R.id.title)).setText(i.title);
        ((TextView) findViewById(R.id.placeholder)).setText(i.title);
        setLine(findViewById(R.id.meta), i.meta());
        setLine(findViewById(R.id.rating), i.rating);
        setLine(findViewById(R.id.overview), i.overview);
        findViewById(R.id.poster_frame).setClipToOutline(true);
        Images.load(findViewById(R.id.poster), i.poster, 480, 720);
        Images.load(findViewById(R.id.backdrop), i.fanart, 1280, 720);

        if (i.inLibrary()) {
            boolean complete = movie ? i.hasFile : i.episodes > 0 && i.episodeFiles >= i.episodes;
            // A show just added has no episode list yet; "0 of 0" in amber would read as a failure.
            boolean counted = movie || i.episodes > 0;
            library.setTextColor(getColor(!counted ? R.color.text_dim : complete ? R.color.ok : R.color.warn));
            library.setText(movie ? getString(i.hasFile ? R.string.library_downloaded : R.string.library_missing)
                    : counted ? getResources().getQuantityString(R.plurals.library_episodes, i.episodes, i.episodeFiles, i.episodes)
                    : getString(R.string.library_in));
            primary.setText(R.string.search_again);
            quality.setVisibility(View.GONE);
            monitorButton.setVisibility(View.GONE);
        } else {
            library.setTextColor(getColor(R.color.text_dim));
            library.setText(statusLine(i));
            primary.setText(R.string.add_search);
            quality.setVisibility(View.VISIBLE);
            monitorButton.setVisibility(movie ? View.GONE : View.VISIBLE);
            monitorButton.setText(getString(R.string.monitor_button, monitorLabel(monitor)));
        }
    }

    /** "Not in your library", with the release state a film's add depends on. */
    private String statusLine(Arr.Item i) {
        String s = getString(R.string.not_in_library);
        switch (i.status) {
            case "announced": case "tba": return s + "  ·  " + getString(R.string.status_announced);
            case "inCinemas": return s + "  ·  " + getString(R.string.status_in_cinemas);
            case "upcoming": return s + "  ·  " + getString(R.string.status_upcoming);
            case "ended": return s + "  ·  " + getString(R.string.status_ended);
            case "continuing": return s + "  ·  " + getString(R.string.status_continuing);
            default: return s;
        }
    }

    private void onPrimary() {
        if (busy) return;
        if (!item.inLibrary() && defaults == null) {
            addWhenLoaded = true;
            status.setTextColor(getColor(R.color.text_dim));
            status.setText(R.string.adding);
            loadDefaults();
            return;
        }
        busy = true;
        status.setTextColor(getColor(R.color.text_dim));
        status.setText(item.inLibrary() ? R.string.searching : R.string.adding);
        io.execute(() -> {
            try {
                if (item.inLibrary()) {
                    arr.searchNow(item);
                    runOnUiThread(() -> done(getString(R.string.search_started, arr.name())));
                } else {
                    Arr.Item added = arr.add(item, defaults, monitor);
                    runOnUiThread(() -> {
                        bind(added);
                        setResult(RESULT_OK);
                        done(getString(R.string.added_searching, arr.name()));
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    fail(e);
                });
            }
        });
    }

    private void done(String message) {
        busy = false;
        status.setTextColor(getColor(R.color.ok));
        status.setText(message);
        primary.requestFocus();
    }

    private void fail(Exception e) {
        status.setTextColor(getColor(R.color.bad));
        status.setText(getString(R.string.error, arr.name(), e.getMessage()));
    }

    private void pickQuality() {
        if (defaults == null) {
            loadDefaults();
            return;
        }
        io.execute(() -> {
            try {
                JSONArray profiles = arr.qualityProfiles();
                runOnUiThread(() -> {
                    List<String> names = new ArrayList<>();
                    List<Integer> ids = new ArrayList<>();
                    int selected = 0;
                    for (int i = 0; i < profiles.length(); i++) {
                        JSONObject p = profiles.optJSONObject(i);
                        if (p == null) continue;
                        if (p.optInt("id") == defaults.profileId) selected = names.size();
                        names.add(p.optString("name"));
                        ids.add(p.optInt("id"));
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.s_quality_profile)
                            .setSingleChoiceItems(names.toArray(new String[0]), selected, (d, which) -> {
                                defaults = new Arr.Defaults(ids.get(which), names.get(which), defaults.rootFolder);
                                quality.setText(getString(R.string.quality_button, defaults.profileName));
                                d.dismiss();
                            })
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> fail(e));
            }
        });
    }

    private void pickMonitor() {
        String[] labels = new String[Arr.SERIES_MONITOR.length];
        int selected = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = monitorLabel(Arr.SERIES_MONITOR[i]);
            if (Arr.SERIES_MONITOR[i].equals(monitor)) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.s_monitor)
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    monitor = Arr.SERIES_MONITOR[which];
                    monitorButton.setText(getString(R.string.monitor_button, monitorLabel(monitor)));
                    d.dismiss();
                })
                .show();
    }

    /** The words for one of {@link Arr#SERIES_MONITOR}; Settings uses the same. */
    static String monitorLabel(Context ctx, String m) {
        switch (m) {
            case "future": return ctx.getString(R.string.m_future);
            case "missing": return ctx.getString(R.string.m_missing);
            case "firstSeason": return ctx.getString(R.string.m_first_season);
            case "latestSeason": return ctx.getString(R.string.m_latest_season);
            case "pilot": return ctx.getString(R.string.m_pilot);
            case "none": return ctx.getString(R.string.m_none);
            default: return ctx.getString(R.string.m_all);
        }
    }

    private String monitorLabel(String m) {
        return monitorLabel(this, m);
    }

    private static void setLine(TextView v, String text) {
        v.setText(text);
        v.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
