package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * My System TV: Home's backgrounds, as in the preview. The videos live in the theme folder's videos/ (pushed
 * over adb, each with a still of the same name for the Control Center's picker); "sky" is the drawn sky that was
 * there before. The choice is kept in shared preferences, so the Control Center can change it while Home listens.
 */
final class Fondos {

    static final String PREFS = "fondo";
    static final String KEY = "id";
    static final String SKY = "sky";
    static final String DEFAULT = "gg-sunset";
    /** The Control Center's Modo A + B: the showcase over a focused dock app (MainActivity, Showcase). */
    static final String VITRINA = "vitrina";

    /** id, name: the order the picker lists them in. */
    private static final String[][] ALL = {
            {"gg-sunset", "Cascada"},
            {"gg-night", "Cueva verde"},
            {"sequoia", "Eco de sombras"},
            {"noche", "Capa roja"},
            {"utah", "Entre llamas"},
            {SKY, "Cielo"},
    };

    static final class Fondo {
        final String id;
        final String name;
        /** The video, or null for the sky. */
        @Nullable final File video;
        /** The picker's still, or null. */
        @Nullable final File still;

        Fondo(String id, String name, @Nullable File video, @Nullable File still) {
            this.id = id;
            this.name = name;
            this.video = video;
            this.still = still;
        }
    }

    private Fondos() {
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The backgrounds this TV has: the videos that were pushed, then the sky. */
    static List<Fondo> available(Context ctx) {
        File dir = Config.dir(ctx);
        File videos = dir == null ? null : new File(dir, "videos");
        List<Fondo> out = new ArrayList<>();
        for (String[] f : ALL) {
            if (SKY.equals(f[0])) {
                out.add(new Fondo(SKY, f[1], null, null));
                continue;
            }
            File video = videos == null ? null : new File(videos, f[0] + ".mp4");
            if (video == null || !video.isFile()) continue;
            File still = new File(videos, f[0] + ".jpg");
            out.add(new Fondo(f[0], f[1], video, still.isFile() ? still : null));
        }
        return out;
    }

    /** The chosen background, or the first one there is when the choice is gone. */
    static Fondo current(Context ctx) {
        List<Fondo> all = available(ctx);
        String id = prefs(ctx).getString(KEY, DEFAULT);
        for (Fondo f : all) if (f.id.equals(id)) return f;
        return all.get(0);
    }

    /** Fork de Natalia: cuándo cambió el fondo por última vez (a mano o por la rotación), en milisegundos. */
    static final String CHANGED = "cambiado";

    /** Elegido a mano: el reloj de la rotación empieza de nuevo, así el fondo elegido dura su intervalo completo. */
    static void choose(Context ctx, String id) {
        prefs(ctx).edit().putString(KEY, id).putLong(CHANGED, System.currentTimeMillis()).apply();
    }

    /**
     * Fork de Natalia (fondo_rotation_minutes): pasa al siguiente video si ya pasó el intervalo. El Cielo no rota
     * ni entra en la rotación: si es el elegido, se queda. true si cambió el fondo.
     */
    static boolean rotateIfDue(Context ctx, int minutes) {
        if (minutes <= 0) return false;
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        long changed = p.getLong(CHANGED, 0);
        // Sin marca todavía (o con el reloj de la tele movido hacia atrás): el intervalo empieza ahora.
        if (changed == 0 || changed > now) {
            p.edit().putLong(CHANGED, now).apply();
            return false;
        }
        if (now - changed < minutes * 60_000L) return false;
        List<Fondo> all = available(ctx);
        String current = p.getString(KEY, DEFAULT);
        if (SKY.equals(current)) return false;
        List<Fondo> videos = new ArrayList<>();
        int at = -1;
        for (Fondo f : all) {
            if (f.video == null) continue;
            if (f.id.equals(current)) at = videos.size();
            videos.add(f);
        }
        if (videos.size() < 2) return false;
        p.edit().putString(KEY, videos.get((at + 1) % videos.size()).id).putLong(CHANGED, now).apply();
        return true;
    }
}
