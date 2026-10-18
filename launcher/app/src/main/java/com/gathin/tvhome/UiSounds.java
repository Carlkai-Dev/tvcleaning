package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.SoundPool;
import android.provider.Settings;

/**
 * The launcher's own navigation and select sounds (res/raw, made by launcher/sfx/make_sfx.py), played instead of
 * the system's: every focusable view sets soundEffectsEnabled="false". Follows the TV's "sound effects" setting
 * like the system sounds do.
 *
 * Optional (config.xml keep_audio_awake): on TVs whose single output (for example HDMI ARC to a soundbar) goes to
 * standby after a few seconds of silence, waking it delays each sound noticeably, and some soundbars switch their
 * amplifier off on all-zero samples and swallow the first sounds. While the home screen is in front, a looping
 * track of noise at about -75 dBFS, far below hearing, keeps both awake. It stops in onPause, so it never keeps
 * the TV from sleeping.
 */
final class UiSounds {

    private static final float VOLUME = 0.75f;
    private static final int RATE = 48000;   // the output's own rate, like res/raw: no resampling, fast track
    private static final AudioAttributes ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    private final Context context;
    private final SoundPool pool;
    private final int nav, select;
    private final boolean keepOutputAwake;
    private AudioTrack keepAwake;

    UiSounds(Context context) {
        this.context = context;
        keepOutputAwake = context.getResources().getBoolean(R.bool.keep_audio_awake);
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(ATTRS).build();
        nav = pool.load(context, R.raw.nav, 1);
        select = pool.load(context, R.raw.select, 1);
    }

    void nav() {
        play(nav);
    }

    void select() {
        play(select);
    }

    private void play(int id) {
        if (Settings.System.getInt(context.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0) return;
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f);
    }

    /** Home screen in front: start the near-silent loop, if enabled. */
    void resume() {
        if (!keepOutputAwake || keepAwake != null) return;
        int frames = RATE / 2;   // half a second, looped
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(ATTRS)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(frames * 2)
                    .build();
            short[] hiss = new short[frames];
            java.util.Random random = new java.util.Random(1);
            for (int i = 0; i < frames; i++) hiss[i] = (short) (random.nextInt(13) - 6);
            track.write(hiss, 0, frames);
            track.setLoopPoints(0, frames, -1);
            track.play();
            keepAwake = track;
        } catch (RuntimeException e) {
            // Called from onResume: a failure here must never take the home screen down.
            if (track != null) track.release();
        }
    }

    /** Home screen left (an app opened or the TV sleeping): let the output go to standby again. */
    void pause() {
        if (keepAwake == null) return;
        try {
            keepAwake.stop();
        } catch (IllegalStateException ignored) {
        }
        keepAwake.release();
        keepAwake = null;
    }

    void release() {
        pause();
        pool.release();
    }
}
