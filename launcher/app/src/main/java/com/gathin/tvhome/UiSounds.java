package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.provider.Settings;

/**
 * Short, quiet interface sounds: a tick when focus lands on a tile or pill, a chime on select, a thud
 * when you push past the end of a row. Loaded once into a SoundPool, which plays them with no decode
 * delay. They follow the TV's own "System sounds" setting and the launcher's ui_sounds key.
 *
 * The files in res/raw are original placeholders, synthesised (mono, 16-bit, 22 kHz, under 100 ms):
 * <pre>
 * ffmpeg -nostdin -f lavfi -i "aevalsrc=exprs='0.6*sin(2*PI*2400*t)*exp(-t*140)':s=22050:d=0.04" -ac 1 -c:a pcm_s16le ui_focus.wav
 * ffmpeg -nostdin -f lavfi -i "aevalsrc=exprs='0.5*(sin(2*PI*1050*t)+0.6*sin(2*PI*1575*t))*exp(-t*45)':s=22050:d=0.09" -ac 1 -c:a pcm_s16le ui_select.wav
 * ffmpeg -nostdin -f lavfi -i "aevalsrc=exprs='0.8*sin(2*PI*(170-600*t)*t)*exp(-t*55)':s=22050:d=0.08" -ac 1 -c:a pcm_s16le ui_bump.wav
 * </pre>
 * Replace them with your own or CC0 sounds of the same names; never with Apple's.
 */
final class UiSounds {
    private static final float VOLUME = 0.3f;

    private final Context ctx;
    private final boolean configured;
    private SoundPool pool;
    private int focus, select, bump;
    private boolean enabled;

    UiSounds(Context ctx, boolean configured) {
        this.ctx = ctx.getApplicationContext();
        this.configured = configured;
        if (!configured) return;
        pool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        focus = pool.load(ctx, R.raw.ui_focus, 1);
        select = pool.load(ctx, R.raw.ui_select, 1);
        bump = pool.load(ctx, R.raw.ui_bump, 1);
        refresh();
    }

    /** Re-reads the TV's system-sounds setting; call when Home comes back to the front. */
    void refresh() {
        enabled = configured && Settings.System.getInt(ctx.getContentResolver(),
                Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
    }

    void focus() {
        play(focus);
    }

    void select() {
        play(select);
    }

    void bump() {
        play(bump);
    }

    private void play(int id) {
        if (enabled && pool != null && id != 0) pool.play(id, VOLUME, VOLUME, 0, 0, 1f);
    }

    void release() {
        if (pool != null) pool.release();
        pool = null;
    }
}
