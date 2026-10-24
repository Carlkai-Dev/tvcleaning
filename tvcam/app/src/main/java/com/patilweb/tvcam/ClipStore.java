package com.patilweb.tvcam;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class ClipStore {
    static final String ID_INTERNAL = "internal";
    static final String ID_DEVICE = "device";
    static final String ID_PUBLIC = "public";
    static final String ID_REMOVABLE_PREFIX = "removable:";

    private ClipStore() {}

    /** One selectable storage destination. */
    static final class Location {
        final String id;
        final String label;
        final File root;

        Location(String id, String label, File root) {
            this.id = id;
            this.label = label;
            this.root = root;
        }
    }

    static ArrayList<Location> availableLocations(Context ctx) {
        ArrayList<Location> list = new ArrayList<>();

        File internal = ensureDir(new File(ctx.getFilesDir(), "CCTV"));
        list.add(new Location(ID_INTERNAL,
                "Internal (app private) · " + freeLabel(internal),
                internal));

        File deviceMovies = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (deviceMovies != null) {
            File device = ensureDir(new File(deviceMovies, "CCTV"));
            list.add(new Location(ID_DEVICE,
                    "Device storage · " + freeLabel(device),
                    device));
        }

        File publicBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (publicBase != null) {
            File pub = ensureDir(new File(publicBase, "TVCam"));
            list.add(new Location(ID_PUBLIC,
                    "Shared Movies/TVCam · " + freeLabel(pub),
                    pub));
        }

        File[] externals = ctx.getExternalFilesDirs(Environment.DIRECTORY_MOVIES);
        if (externals != null) {
            for (int i = 1; i < externals.length; i++) {
                File vol = externals[i];
                if (vol == null) continue;
                File rem = ensureDir(new File(vol, "CCTV"));
                String id = ID_REMOVABLE_PREFIX + rem.getAbsolutePath();
                list.add(new Location(id,
                        "USB / SD · " + freeLabel(rem),
                        rem));
            }
        }
        return list;
    }

    static Location selected(Context ctx) {
        String id = Prefs.storageId(ctx);
        ArrayList<Location> all = availableLocations(ctx);
        for (Location loc : all) {
            if (loc.id.equals(id)) return loc;
        }
        // Fallback: device storage, else internal.
        for (Location loc : all) {
            if (ID_DEVICE.equals(loc.id)) return loc;
        }
        return all.get(0);
    }

    static File root(Context ctx) {
        return selected(ctx).root;
    }

    static File snaps(Context ctx) {
        File moviesRoot = root(ctx);
        // Keep snaps next to clips: …/CCTV/snaps or sibling Pictures under same volume.
        File snapDir = ensureDir(new File(moviesRoot, "snaps"));
        return snapDir;
    }

    static String pathLabel(Context ctx) {
        Location loc = selected(ctx);
        return loc.root.getAbsolutePath();
    }

    static File newClipFile(Context ctx) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(root(ctx), "clip_" + stamp + ".mp4");
    }

    static File newSnapFile(Context ctx) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(snaps(ctx), "snap_" + stamp + ".jpg");
    }

    static ArrayList<File> listClips(Context ctx) {
        File[] files = root(ctx).listFiles((dir, name) ->
                name.endsWith(".mp4") || name.endsWith(".3gp"));
        ArrayList<File> out = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            out.addAll(Arrays.asList(files));
        }
        return out;
    }

    static void pruneOld(Context ctx) {
        long keepMs = TimeUnit.DAYS.toMillis(Prefs.retainDays(ctx));
        long cutoff = System.currentTimeMillis() - keepMs;
        File[] files = root(ctx).listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    static String formatWhen(long ms) {
        return new SimpleDateFormat("MMM d · HH:mm:ss", Locale.getDefault()).format(new Date(ms));
    }

    /** True when the MP4 contains a moov atom (required for VideoView playback). */
    static boolean isPlayableMp4(File file) {
        if (file == null || !file.isFile() || file.length() < 64) return false;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long size = raf.length();
            long offset = 0;
            byte[] type = new byte[4];
            while (offset + 8 <= size) {
                raf.seek(offset);
                int boxSize = raf.readInt();
                raf.readFully(type);
                String name = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
                long length = boxSize & 0xffffffffL;
                if (boxSize == 1) {
                    length = raf.readLong();
                } else if (boxSize == 0) {
                    length = size - offset;
                }
                if (length < 8) break;
                if ("moov".equals(name)) return true;
                offset += length;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    static void deleteUnplayable(Context ctx) {
        File[] files = root(ctx).listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".mp4") && !isPlayableMp4(f)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static File ensureDir(File dir) {
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private static String freeLabel(File dir) {
        try {
            File path = dir;
            while (path != null && !path.exists()) {
                path = path.getParentFile();
            }
            if (path == null) return "? free";
            StatFs stat = new StatFs(path.getAbsolutePath());
            long free = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            return formatSize(free) + " free";
        } catch (Exception e) {
            return "? free";
        }
    }
}
