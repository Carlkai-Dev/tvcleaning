package com.gathin.tvhome;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lorem Picsum photo list (no account). Fetches the public list API, sizes URLs for the TV,
 * then reuses {@link WallpaperUrls} cache.
 */
final class PicsumPhotos {
    /** wallpaper_choice value. */
    static final String CHOICE = "picsum";
    private static final String TAG = "TvHome.PicsumWall";
    private static final String LIST = "https://picsum.photos/v2/list?page=1&limit=8";
    private static final int MAX = 8;
    private static final int CONNECT_MS = 12_000;
    private static final int READ_MS = 30_000;
    /** Floor so a tiny reported display still requests a usable wallpaper. */
    private static final int MIN_EDGE = 1920;

    private PicsumPhotos() {
    }

    /**
     * Fetches Picsum image URLs at (at least) TV resolution, stores them, downloads into the cache.
     * @return null on success, or a short error for a toast
     */
    static String fetchAndCache(Context ctx) {
        List<String> urls;
        try {
            urls = fetchImageUrls();
        } catch (IOException e) {
            Log.w(TAG, "list failed", e);
            return "list failed";
        }
        if (urls.isEmpty()) return "empty";
        int[] screen = screenSize(ctx);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) text.append('\n');
            text.append(sizedUrl(urls.get(i), screen[0], screen[1]));
        }
        WallpaperUrls.deleteCache(ctx);
        WallpaperUrls.save(ctx, text.toString());
        return WallpaperUrls.downloadAll(ctx);
    }

    static List<String> fetchImageUrls() throws IOException {
        return parseImageUrls(get(LIST));
    }

    /**
     * Public for unit tests: decode id fields from the Picsum list JSON.
     * Lightweight string scan (no org.json) so JVM unit tests stay mock-free.
     * Returns unsized {@code https://picsum.photos/id/{id}/…} bases for {@link #sizedUrl}.
     */
    static List<String> parseImageUrls(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        int from = 0;
        while (out.size() < MAX) {
            int key = json.indexOf("\"id\"", from);
            if (key < 0) break;
            int colon = json.indexOf(':', key + 4);
            if (colon < 0) break;
            int q1 = json.indexOf('"', colon + 1);
            if (q1 < 0) break;
            int q2 = json.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            from = q2 + 1;
            String id = json.substring(q1 + 1, q2).trim();
            if (id.isEmpty()) continue;
            // Skip non-numeric ids (e.g. accidental matches); Picsum ids are digits.
            boolean digits = true;
            for (int i = 0; i < id.length(); i++) {
                char c = id.charAt(i);
                if (c < '0' || c > '9') {
                    digits = false;
                    break;
                }
            }
            if (!digits) continue;
            String url = "https://picsum.photos/id/" + id + "/200/200";
            if (WallpaperUrls.valid(url) && !out.contains(url)) out.add(url);
        }
        return out;
    }

    /**
     * Rebuilds a Picsum URL at roughly TV-covering resolution (longer edge, floored).
     * Accepts {@code /id/{id}/…} or a full download_url.
     */
    static String sizedUrl(String base, int screenW, int screenH) {
        if (base == null || base.isEmpty()) return base;
        int edge = Math.max(screenW, screenH);
        if (edge < MIN_EDGE) edge = MIN_EDGE;
        // Prefer landscape wallpaper dimensions: longer edge × shorter-ish.
        int w = edge;
        int h = Math.max(1, (int) (edge * 9f / 16f));
        String id = extractId(base);
        if (id != null) return "https://picsum.photos/id/" + id + "/" + w + "/" + h;
        return base;
    }

    private static String extractId(String url) {
        // .../id/123/... or download_url with /id/123/
        int at = url.indexOf("/id/");
        if (at < 0) return null;
        int start = at + 4;
        int end = start;
        while (end < url.length()) {
            char c = url.charAt(end);
            if (c < '0' || c > '9') break;
            end++;
        }
        return end > start ? url.substring(start, end) : null;
    }

    private static int[] screenSize(Context ctx) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getRealMetrics(metrics);
        else metrics = ctx.getResources().getDisplayMetrics();
        int w = Math.max(1, metrics.widthPixels);
        int h = Math.max(1, metrics.heightPixels);
        return new int[]{w, h};
    }

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setRequestProperty("User-Agent", "CleanYourTV-Wallpaper/1");
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) throw new IOException("http " + code);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(chunk)) >= 0) {
                total += n;
                if (total > 512 * 1024) throw new IOException("response too large");
                buf.write(chunk, 0, n);
            }
            if (code < 200 || code >= 300) throw new IOException("http " + code);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
