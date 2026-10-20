package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * On-TV wallpaper from image URLs. One URL per line in launcher settings; each download lands in the
 * app's private cache and {@link WallpaperPlayer} shows them like theme files. No Google account and
 * no video: only http(s) image bodies under a size cap.
 */
final class WallpaperUrls {
    static final String URLS_KEY = "wallpaper_urls";
    /** wallpaper_choice value: use the cached URL downloads instead of the theme. */
    static final String CHOICE = "url";
    private static final String TAG = "TvHome.WallpaperUrl";
    private static final String DIR = "wallpaper_url";
    private static final int MAX_URLS = 6;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    /** Teto total do cache em disco; acima disso apaga os ficheiros mais antigos. */
    private static final long MAX_CACHE_BYTES = 12L * 1024 * 1024;
    /** Ficheiros mais velhos que isto são apagados (7 dias). */
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int CONNECT_MS = 12_000;
    private static final int READ_MS = 30_000;

    private WallpaperUrls() {
    }

    static String saved(Context ctx) {
        return prefs(ctx).getString(URLS_KEY, "");
    }

    static void save(Context ctx, String text) {
        prefs(ctx).edit().putString(URLS_KEY, text == null ? "" : text.trim()).apply();
    }

    static void clear(Context ctx) {
        prefs(ctx).edit().remove(URLS_KEY).apply();
        deleteCache(ctx);
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    /** One http(s) URL per non-empty line, capped; invalid lines are dropped. */
    static List<String> parse(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String u = line.trim();
            if (u.isEmpty()) continue;
            if (!valid(u)) continue;
            if (!out.contains(u)) out.add(u);
            if (out.size() == MAX_URLS) break;
        }
        return out;
    }

    static boolean valid(String url) {
        if (url == null || url.length() > 2048) return false;
        String lower = url.toLowerCase(Locale.US);
        return (lower.startsWith("https://") || lower.startsWith("http://"))
                && !url.contains(" ") && !url.contains("\r");
    }

    static File cacheDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR);
        // noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    static File fileFor(Context ctx, String url) {
        return new File(cacheDir(ctx), hash(url) + ".img");
    }

    /** Cached files for the saved URL list, in list order; missing downloads are skipped. */
    static List<File> cachedFiles(Context ctx) {
        List<String> urls = parse(saved(ctx));
        List<File> out = new ArrayList<>();
        for (String url : urls) {
            File f = fileFor(ctx, url);
            if (f.isFile() && f.length() > 0) out.add(f);
        }
        return out;
    }

    /**
     * Downloads every saved URL into the cache (skips ones already present). Returns null on success,
     * or a short error the settings screen can toast.
     */
    static String downloadAll(Context ctx) {
        List<String> urls = parse(saved(ctx));
        if (urls.isEmpty()) return "empty";
        deleteOrphans(ctx, urls);
        pruneExpired(ctx);
        for (String url : urls) {
            File dest = fileFor(ctx, url);
            if (dest.isFile() && dest.length() > 0) continue;
            String err = downloadOne(url, dest);
            if (err != null) {
                // noinspection ResultOfMethodCallIgnored
                dest.delete();
                return err;
            }
        }
        pruneToBudget(ctx);
        return cachedFiles(ctx).isEmpty() ? "empty" : null;
    }

    static void deleteCache(Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            // noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /**
     * Remove órfãos, expirados e excesso de tamanho. Chamado ao carregar Home com fonte remota
     * para o cache não crescer indefinidamente entre sessões.
     */
    static void prune(Context ctx) {
        deleteOrphans(ctx, parse(saved(ctx)));
        pruneExpired(ctx);
        pruneToBudget(ctx);
    }

    private static void deleteOrphans(Context ctx, List<String> urls) {
        List<String> keep = new ArrayList<>();
        for (String url : urls) keep.add(fileFor(ctx, url).getName());
        File[] files = cacheDir(ctx).listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!keep.contains(f.getName())) {
                // noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static void pruneExpired(Context ctx) {
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        File[] files = cacheDir(ctx).listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile()) continue;
            if (f.lastModified() > 0 && f.lastModified() < cutoff) {
                // noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** Se o cache passar do teto, apaga os ficheiros mais antigos primeiro. */
    private static void pruneToBudget(Context ctx) {
        File[] files = cacheDir(ctx).listFiles();
        if (files == null || files.length == 0) return;
        long total = 0;
        List<File> imgs = new ArrayList<>();
        for (File f : files) {
            if (!f.isFile()) continue;
            imgs.add(f);
            total += f.length();
        }
        if (total <= MAX_CACHE_BYTES) return;
        Collections.sort(imgs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : imgs) {
            if (total <= MAX_CACHE_BYTES) break;
            long size = f.length();
            if (f.delete()) total -= size;
        }
    }

    private static String downloadOne(String url, File dest) {
        HttpURLConnection conn = null;
        File partial = new File(dest.getPath() + ".part");
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "CleanYourTV-Wallpaper/1");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return "http " + code;
            String type = conn.getContentType();
            if (type != null) {
                String t = type.toLowerCase(Locale.US);
                if (!(t.startsWith("image/") || t.contains("octet-stream"))) return "not image";
            }
            long declared = conn.getContentLengthLong();
            if (declared > MAX_BYTES) return "too large";
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(partial)) {
                byte[] buf = new byte[8192];
                int total = 0;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    total += n;
                    if (total > MAX_BYTES) return "too large";
                    out.write(buf, 0, n);
                }
                if (total == 0) return "empty";
            }
            if (!partial.renameTo(dest)) {
                // noinspection ResultOfMethodCallIgnored
                dest.delete();
                if (!partial.renameTo(dest)) return "save failed";
            }
            return null;
        } catch (IOException e) {
            Log.w(TAG, "download failed: " + url, e);
            return "download failed";
        } finally {
            // noinspection ResultOfMethodCallIgnored
            partial.delete();
            if (conn != null) conn.disconnect();
        }
    }

    private static String hash(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format(Locale.US, "%02x", dig[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    /** For tests: same order rules without Android. */
    static List<String> parseForTest(String text) {
        return Collections.unmodifiableList(parse(text));
    }
}
