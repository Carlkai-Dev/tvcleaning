package com.gathin.tvhome;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * Speaks “Open &lt;app&gt;” when a dock/grid tile is opened with OK on the remote.
 */
final class AppAnnouncer implements TextToSpeech.OnInitListener {
    private static final String TAG = "TvHome.Voice";
    private static final String UTTERANCE = "open_app";

    private final Context appCtx;
    private TextToSpeech tts;
    private boolean ready;

    AppAnnouncer(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        try {
            tts = new TextToSpeech(appCtx, this);
        } catch (Exception e) {
            Log.w(TAG, "TTS unavailable: " + e.getMessage());
            tts = null;
        }
    }

    @Override
    public void onInit(int status) {
        if (tts == null || status != TextToSpeech.SUCCESS) {
            ready = false;
            return;
        }
        int lang = tts.setLanguage(Locale.getDefault());
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US);
        }
        tts.setSpeechRate(1.05f);
        ready = true;
    }

    /** Speak “Open &lt;label&gt;” immediately; launch is not delayed. */
    void sayOpen(String label) {
        if (label == null || label.trim().isEmpty()) return;
        String phrase = appCtx.getString(R.string.open_app_announce, label.trim());
        if (!ready || tts == null) return;
        try {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE);
            tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE);
        } catch (Exception e) {
            Log.w(TAG, "speak failed: " + e.getMessage());
        }
    }

    void shutdown() {
        ready = false;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
    }
}
