package com.gathin.tvhome;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Chrome/Google "featured" NTP wallpapers via the undocumented Backdrop service the browser uses.
 * No account: POST a tiny protobuf, parse image_url strings, then reuse {@link WallpaperUrls} cache.
 * The endpoint can change without notice (shortcut: Landscapes only; add a picker if more collections matter).
 */
final class GoogleFeatured {
    /** wallpaper_choice value. */
    static final String CHOICE = "google";
    private static final String TAG = "TvHome.GoogleWall";
    private static final String IMAGES_URL =
            "https://clients3.google.com/cast/chromecast/home/wallpaper/collection-images?rt=b";
    /** Chrome desktop NTP Landscapes collection. */
    private static final String COLLECTION = "landscape_for_NTP";
    private static final String LANGUAGE = "en-US";
    private static final int MAX = 8;
    private static final int CONNECT_MS = 12_000;
    private static final int READ_MS = 30_000;
    /** Floor so a tiny reported display still requests a usable wallpaper. */
    private static final int MIN_EDGE = 1920;

    private GoogleFeatured() {
    }

    /**
     * Fetches featured image URLs at (at least) TV resolution, stores them, downloads into the cache.
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
        // Drop prior (often thumbnail-sized) cache so the new sized URLs always re-download.
        WallpaperUrls.deleteCache(ctx);
        WallpaperUrls.save(ctx, text.toString());
        return WallpaperUrls.downloadAll(ctx);
    }

    /**
     * Backdrop/FIFE size suffix: same rule as Chromium's wallpaper downloader — {@code =w} twice the
     * longer screen edge — so the file is at least large enough to cover the TV after centre-crop.
     */
    static String sizedUrl(String base, int screenW, int screenH) {
        if (base == null || base.isEmpty()) return base;
        int cut = base.lastIndexOf('=');
        if (cut > base.lastIndexOf('/')) base = base.substring(0, cut);
        int edge = Math.max(screenW, screenH) * 2;
        if (edge < MIN_EDGE) edge = MIN_EDGE;
        return base + "=w" + edge;
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

    /** Public for unit tests: decode image_url fields from a GetImagesInCollectionResponse body. */
    static List<String> parseImageUrls(byte[] body) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < body.length && out.size() < MAX) {
            long tag = readVarint(body, i);
            if (tag < 0) break;
            i = (int) (tag >>> 32);
            int raw = (int) (tag & 0xFFFFFFFFL);
            int field = raw >>> 3;
            int wire = raw & 7;
            if (wire == 2) {
                long lenTag = readVarint(body, i);
                if (lenTag < 0) break;
                i = (int) (lenTag >>> 32);
                int len = (int) lenTag;
                if (len < 0 || i + len > body.length) break;
                if (field == 1) {
                    // nested Image message
                    extractUrls(body, i, i + len, out);
                }
                i += len;
            } else if (wire == 0) {
                long skip = readVarint(body, i);
                if (skip < 0) break;
                i = (int) (skip >>> 32);
            } else if (wire == 1) {
                i += 8;
            } else if (wire == 5) {
                i += 4;
            } else {
                break;
            }
        }
        return out;
    }

    static List<String> fetchImageUrls() throws IOException {
        // GetImagesInCollectionRequest: collection_id=1, language=2
        byte[] req = concat(encodeString(1, COLLECTION), encodeString(2, LANGUAGE));
        byte[] resp = post(IMAGES_URL, req);
        return parseImageUrls(resp);
    }

    private static void extractUrls(byte[] body, int from, int to, List<String> out) {
        int i = from;
        while (i < to && out.size() < MAX) {
            long tag = readVarint(body, i);
            if (tag < 0) return;
            i = (int) (tag >>> 32);
            int raw = (int) (tag & 0xFFFFFFFFL);
            int field = raw >>> 3;
            int wire = raw & 7;
            if (wire == 2) {
                long lenTag = readVarint(body, i);
                if (lenTag < 0) return;
                i = (int) (lenTag >>> 32);
                int len = (int) lenTag;
                if (len < 0 || i + len > to) return;
                if (field == 2) {
                    String url = new String(body, i, len, StandardCharsets.UTF_8);
                    if (WallpaperUrls.valid(url) && url.contains("googleusercontent.com") && !out.contains(url)) {
                        out.add(url);
                    }
                }
                i += len;
            } else if (wire == 0) {
                long skip = readVarint(body, i);
                if (skip < 0) return;
                i = (int) (skip >>> 32);
            } else if (wire == 1) {
                i += 8;
            } else if (wire == 5) {
                i += 4;
            } else {
                return;
            }
        }
    }

    private static byte[] encodeString(int field, String value) {
        byte[] utf = value.getBytes(StandardCharsets.UTF_8);
        byte[] tag = encodeVarint((field << 3) | 2);
        byte[] len = encodeVarint(utf.length);
        return concat(tag, len, utf);
    }

    private static byte[] encodeVarint(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5);
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
        return out.toByteArray();
    }

    /** Low 32 bits = value, high 32 bits = new index; -1 on failure. */
    private static long readVarint(byte[] body, int index) {
        long result = 0;
        int shift = 0;
        int i = index;
        while (i < body.length && shift <= 28) {
            int b = body[i++] & 0xFF;
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return (result & 0xFFFFFFFFL) | ((long) i << 32);
            shift += 7;
        }
        return -1;
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }

    private static byte[] post(String url, byte[] body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-protobuf");
            conn.setRequestProperty("User-Agent", "CleanYourTV-Wallpaper/1");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
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
            return buf.toByteArray();
        } finally {
            conn.disconnect();
        }
    }
}
