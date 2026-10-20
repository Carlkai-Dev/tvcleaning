package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * Speaks “Open &lt;app&gt;” when a dock/grid tile is opened with OK on the remote.
 * Toggle lives in launcher settings ({@link #KEY_ENABLED}).
 */
final class AppAnnouncer implements TextToSpeech.OnInitListener {
    private static final String TAG = "TvHome.Voice";
    private static final String UTTERANCE = "open_app";
    /** SharedPreferences key in {@link Config#SETTINGS_NAME}; default on. */
    static final String KEY_ENABLED = "voice_open_apps";

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

    static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
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

    /** Speak “Open &lt;label&gt;” immediately; launch is not delayed. No-op when the setting is off. */
    void sayOpen(String label) {
        if (!isEnabled(appCtx)) return;
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
