package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tiles of the last load, kept on disk so a cold start can show them without opening any other app's APK.
 * A small index plus one PNG per tile in the cache directory. Used only from {@link AppLoader}'s worker.
 *
 * A missing, old or damaged store is ignored, never an error: the normal load builds the tiles and saves them again.
 */
final class TileStore {

    private static final int MAGIC = 0x54494C45;   // "TILE"
    private static final int VERSION = 3;
    private static final String INDEX = "index";

    /** One saved tile: the banner, or the icon of an app without one. */
    static final class Tile {
        final String key;
        final String label;
        /** The package's lastUpdateTime when the tile was built. */
        final long stamp;
        final int ambient;
        final boolean banner;
        final boolean leanback;
        final String file;
        /** Decoded by {@link #read()}; null when the image could not be read. */
        Bitmap image;

        Tile(String key, String label, long stamp, int ambient, boolean banner, boolean leanback, String file) {
            this.key = key;
            this.label = label;
            this.stamp = stamp;
            this.ambient = ambient;
            this.banner = banner;
            this.leanback = leanback;
            this.file = file;
        }
    }

    /** What was saved, and for which locale and banner size. */
    static final class Contents {
        final String locale;
        final int width, height;
        final List<Tile> tiles;

        Contents(String locale, int width, int height, List<Tile> tiles) {
            this.locale = locale;
            this.width = width;
            this.height = height;
            this.tiles = tiles;
        }
    }

    private final File dir;
    private int written;

    TileStore(Context app) {
        dir = new File(app.getCacheDir(), "tiles");
    }

    /**
     * The saved tiles with their images decoded straight to GPU memory, or null when there is nothing usable.
     * {@code build} is the launcher's own lastUpdateTime: tiles saved by another build of the launcher may have been
     * drawn, coloured or labelled differently, so they are not used.
     */
    Contents read(long build) {
        Contents c;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(dir, INDEX))))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readLong() != build) return null;
            String locale = in.readUTF();
            int w = in.readInt(), h = in.readInt(), n = in.readInt();
            if (n < 0 || n > 4096) return null;
            List<Tile> tiles = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Tile t = new Tile(in.readUTF(), in.readUTF(), in.readLong(), in.readInt(),
                        in.readBoolean(), in.readBoolean(), in.readUTF());
                if (t.file.indexOf('/') >= 0) return null;
                tiles.add(t);
            }
            c = new Contents(locale, w, h, tiles);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        // About 13 ms per tile on a TV CPU, while the main thread inflates the layout. Decoding on more threads
        // measured no earlier: the extra threads slow the inflation down by as much.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.HARDWARE;
        for (Tile t : c.tiles) {
            try {
                t.image = BitmapFactory.decodeFile(new File(dir, t.file).getPath(), opts);
            } catch (RuntimeException ignored) {
            }
        }
        return c;
    }

    /** Saves one tile image and returns its file name, or null when it could not be written. */
    String writeImage(Bitmap image) {
        // Banners live in GPU memory; copy() reads one back, which compress() cannot do.
        Bitmap soft = image.getConfig() == Bitmap.Config.HARDWARE ? image.copy(Bitmap.Config.ARGB_8888, false) : image;
        if (soft == null) return null;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            // Never reuse a name: the current index may still point at it (the clock can step back at boot).
            String name;
            do {
                name = Long.toHexString(System.currentTimeMillis()) + "-" + (written++) + ".png";
            } while (new File(dir, name).exists());
            File tmp = new File(dir, name + ".tmp");
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
                if (!soft.compress(Bitmap.CompressFormat.PNG, 100, out)) return null;
            }
            return tmp.renameTo(new File(dir, name)) ? name : null;
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (soft != image) soft.recycle();
        }
    }

    /**
     * Replaces the index with these tiles and deletes every file it no longer names. The images are written
     * before the index and the index is swapped in with a rename, so a crash leaves the old store or the new one.
     */
    void writeIndex(long build, String locale, int w, int h, List<Tile> tiles) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(dir, INDEX + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(build);
                out.writeUTF(locale);
                out.writeInt(w);
                out.writeInt(h);
                out.writeInt(tiles.size());
                for (Tile t : tiles) {
                    out.writeUTF(t.key);
                    out.writeUTF(t.label);
                    out.writeLong(t.stamp);
                    out.writeInt(t.ambient);
                    out.writeBoolean(t.banner);
                    out.writeBoolean(t.leanback);
                    out.writeUTF(t.file);
                }
            }
            if (!tmp.renameTo(new File(dir, INDEX))) return;
            Set<String> keep = new HashSet<>();
            keep.add(INDEX);
            for (Tile t : tiles) keep.add(t.file);
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                if (!keep.contains(f.getName())) f.delete();
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
