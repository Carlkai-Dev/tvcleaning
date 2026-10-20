package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bing daily wallpaper archive (no account). Fetches the JSON list, builds absolute image URLs,
 * then reuses {@link WallpaperUrls} cache like Google featured.
 */
final class BingWallpaper {
    /** wallpaper_choice value. */
    static final String CHOICE = "bing";
    private static final String TAG = "TvHome.BingWall";
    private static final String ARCHIVE =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8";
    private static final String HOST = "https://www.bing.com";
    private static final int MAX = 8;
    private static final int CONNECT_MS = 12_000;
    private static final int READ_MS = 30_000;

    private BingWallpaper() {
    }

    /**
     * Fetches Bing image URLs, stores them, downloads into the cache.
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
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) text.append('\n');
            text.append(urls.get(i));
        }
        WallpaperUrls.deleteCache(ctx);
        WallpaperUrls.save(ctx, text.toString());
        return WallpaperUrls.downloadAll(ctx);
    }

    static List<String> fetchImageUrls() throws IOException {
        return parseImageUrls(get(ARCHIVE));
    }

    /**
     * Public for unit tests: decode absolute image URLs from the HPImageArchive JSON body.
     * Lightweight string scan (no org.json) so JVM unit tests stay mock-free.
     */
    static List<String> parseImageUrls(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        int from = 0;
        while (out.size() < MAX) {
            int key = json.indexOf("\"url\"", from);
            if (key < 0) break;
            int colon = json.indexOf(':', key + 5);
            if (colon < 0) break;
            int q1 = json.indexOf('"', colon + 1);
            if (q1 < 0) break;
            int q2 = json.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            from = q2 + 1;
            String path = json.substring(q1 + 1, q2).trim();
            if (path.isEmpty()) continue;
            String url = path.startsWith("http://") || path.startsWith("https://")
                    ? path : HOST + path;
            if (WallpaperUrls.valid(url) && !out.contains(url)) out.add(url);
        }
        return out;
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
