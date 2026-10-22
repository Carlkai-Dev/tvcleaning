package io.github.epochcoder.homelab;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Search asks Radarr and Sonarr at once, as soon as typing pauses, and shows one grid of posters,
 * best match first. OK on a poster opens {@link DetailActivity}, where one press adds it and starts
 * the search. A newer search always wins over an older answer.
 */
final class SearchPage extends Page {
    private static final long TYPING_PAUSE_MS = 400;
    private static final int VOICE_REQUEST = 1;
    private static final int DETAIL_REQUEST = 2;

    private final Handler main = new Handler(Looper.getMainLooper());

    /**
     * Radarr's and Sonarr's lookups side by side, and a third thread collecting both. Each search
     * queues its lookups before its collector, so a collector never waits on work queued behind it.
     */
    private final ExecutorService io = Executors.newFixedThreadPool(3);

    private final EditText query;
    private final TextView status;
    private final GridLayout results;
    private int generation;
    private String lastSearch = "";

    private final Runnable typingPaused = this::search;

    SearchPage(MainActivity host) {
        super(host, R.layout.page_search);
        query = view.findViewById(R.id.query);
        status = view.findViewById(R.id.search_status);
        results = view.findViewById(R.id.results);

        query.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

                    @Override
                    public void onTextChanged(CharSequence s, int a, int b, int c) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        main.removeCallbacks(typingPaused);
                        main.postDelayed(typingPaused, TYPING_PAUSE_MS);
                    }
                });
        query.setOnEditorActionListener(
                (v, action, event) -> {
                    boolean enter =
                            event != null
                                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                                    && event.getAction() == KeyEvent.ACTION_DOWN;
                    if (action != EditorInfo.IME_ACTION_SEARCH && !enter) {
                        return false;
                    }
                    main.removeCallbacks(typingPaused);
                    lastSearch =
                            ""; // Search on the keyboard always asks again, even for the same words
                    search();
                    hideKeyboard();
                    // Straight into the results, so the next press is already on the first poster.
                    if (results.getChildCount() > 0) {
                        results.getChildAt(0).requestFocus();
                    }
                    return true;
                });

        View mic = view.findViewById(R.id.mic);
        Intent voice = voiceIntent();
        if (voice.resolveActivity(host.getPackageManager()) != null) {
            mic.setVisibility(View.VISIBLE);
            mic.setOnClickListener(v -> host.startActivityForResult(voice, VOICE_REQUEST));
        }
        onConfigChanged();
    }

    private static Intent voiceIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    }

    @Override
    void onConfigChanged() {
        lastSearch = "";
        if (host.radarr == null && host.sonarr == null) {
            empty(
                    R.drawable.ic_tab_search,
                    R.string.empty_search_title,
                    getString(R.string.empty_search));
            return;
        }
        empty(0, 0, null);
        // Ask again with the new servers, but only once the tab is on screen: a save in Settings
        // should not send searches nobody is looking at.
        if (visible()) {
            onShow();
        }
    }

    @Override
    void onShow() {
        if (showingEmpty()) {
            return;
        }
        if (query.length() >= 2) {
            search();
        } else {
            setStatus(getString(R.string.search_prompt));
        }
    }

    @Override
    View focusTarget() {
        return showingEmpty() ? emptyButton() : query;
    }

    @Override
    void onDestroy() {
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
    }

    void onActivityResult(int request, int result, Intent data) {
        if (request == VOICE_REQUEST && result == Activity.RESULT_OK && data != null) {
            ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard != null && !heard.isEmpty()) {
                query.setText(heard.get(0));
                query.setSelection(query.length());
                main.removeCallbacks(typingPaused);
                search();
            }
        } else if (request == DETAIL_REQUEST && result == Activity.RESULT_OK) {
            // Something was added: ask again so its poster gets its tick.
            lastSearch = "";
            search();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
        }
    }

    private void search() {
        Arr radarr = host.radarr;
        Arr sonarr = host.sonarr;
        String term = query.getText().toString().trim();
        if (term.equals(lastSearch) || (radarr == null && sonarr == null)) {
            return;
        }
        lastSearch = term;
        int gen = ++generation;
        if (term.length() < 2) {
            results.removeAllViews();
            setStatus(getString(R.string.search_prompt));
            return;
        }
        setStatus(getString(R.string.searching));
        Future<List<Arr.Item>> movies =
                radarr == null ? null : io.submit(() -> radarr.lookup(term));
        Future<List<Arr.Item>> shows = sonarr == null ? null : io.submit(() -> sonarr.lookup(term));
        io.execute(
                () -> {
                    List<String> errors = new ArrayList<>();
                    List<Arr.Item> m = collect(movies, radarr, errors);
                    List<Arr.Item> s = collect(shows, sonarr, errors);
                    List<Arr.Item> merged = Arr.merge(m, s, term);
                    host.runOnUiThread(
                            () -> {
                                if (gen != generation || host.isFinishing()) {
                                    return;
                                }
                                showResults(merged);
                                setStatus(
                                        !errors.isEmpty()
                                                ? String.join("\n", errors)
                                                : merged.isEmpty()
                                                        ? getString(R.string.no_results, term)
                                                        : "");
                            });
                });
    }

    /**
     * The line under the search field; gone when empty, so the posters sit right under the field.
     */
    private void setStatus(String text) {
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<Arr.Item> collect(Future<List<Arr.Item>> f, Arr arr, List<String> errors) {
        if (f == null) {
            return Collections.emptyList();
        }
        try {
            return f.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            errors.add(getString(R.string.error, arr.name(), cause.getMessage()));
            return Collections.emptyList();
        }
    }

    /** One poster card per result, as many columns as fit the screen. */
    private void showResults(List<Arr.Item> items) {
        results.removeAllViews();
        int width = host.getResources().getDisplayMetrics().widthPixels - dp(96);
        results.setColumnCount(
                Math.max(
                        1,
                        width
                                / host.getResources()
                                        .getDimensionPixelSize(R.dimen.poster_card_width)));
        LayoutInflater inflater = LayoutInflater.from(host);
        for (Arr.Item item : items) {
            View card = inflater.inflate(R.layout.item_poster, results, false);
            TextView placeholder = card.findViewById(R.id.placeholder);
            placeholder.setText(item.title);
            ((TextView) card.findViewById(R.id.kind))
                    .setText(
                            item.kind == Arr.Kind.MOVIE
                                    ? R.string.kind_movie
                                    : R.string.kind_series);
            card.findViewById(R.id.owned)
                    .setVisibility(item.inLibrary() ? View.VISIBLE : View.GONE);
            ((TextView) card.findViewById(R.id.title)).setText(item.title);
            ((TextView) card.findViewById(R.id.year))
                    .setText(item.year > 0 ? String.valueOf(item.year) : "");
            card.findViewById(R.id.frame).setClipToOutline(true);
            Images.load(card.findViewById(R.id.poster), item.poster, 280, 420);
            card.setOnFocusChangeListener(
                    (v, has) ->
                            v.animate()
                                    .scaleX(has ? 1.08f : 1f)
                                    .scaleY(has ? 1.08f : 1f)
                                    .setDuration(150)
                                    .start());
            card.setOnClickListener(
                    v ->
                            host.startActivityForResult(
                                    DetailActivity.intent(host, item), DETAIL_REQUEST));
            results.addView(card);
        }
    }
}
