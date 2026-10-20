package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The wallpaper the wall is serving right now. One small GET for the newest key, one for the
 * picture, both off the main thread; the file is cached so a failed hour keeps the last picture.
 * Nothing here draws: it hands the file back and {@link MainActivity#showWallpaper} puts it up.
 */
final class RemoteWallpaper {

    interface Callback { void onPicture(File file); }

    private static final String TAG = "TvHome.Wallpaper";
    /** Enough history to survive a few missed hours without growing on a 8 GB /data. */
    private static final int KEEP = 6;

    private final File dir;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;
    private String lastKey;

    RemoteWallpaper(Context ctx) {
        File external = Config.dir(ctx);
        dir = external != null ? external : ctx.getFilesDir();
    }

    void refresh(String baseUrl, final Callback callback) {
        if (baseUrl == null || baseUrl.isEmpty() || running) return;
        final String base = baseUrl.replaceAll("/+$", "");
        running = true;
        new Thread(() -> {
            try {
                // The index address is signed for a year and baked into the config, so this
                // never touches the wall. The wall is what the browser uses; the TV cannot reach it.
                JSONObject index = new JSONObject(new String(get(base), "UTF-8"));
                org.json.JSONArray items = index.optJSONArray("items");
                if (items == null || items.length() == 0) return;
                JSONObject top = items.getJSONObject(0);
                String key = top.optString("key", "");
                if (key.isEmpty() || key.equals(lastKey)) {
                    final File same = file(key);
                    if (same.isFile() && same.length() > 0) main.post(() -> callback.onPicture(same));
                    return;
                }
                File target = file(key);
                if (!target.isFile() || target.length() == 0) {
                    String signed = top.optString("url", "");
                    if (signed.isEmpty()) throw new IllegalStateException("index entry has no url");
                    write(target, get(signed));
                }
                lastKey = key;
                trim();
                final File out = target;
                main.post(() -> callback.onPicture(out));
            } catch (Exception | OutOfMemoryError e) {
                Log.w(TAG, "no new picture: " + e.getMessage());
            } finally {
                running = false;
            }
        }, "wallpaper-fetch").start();
    }

    private File file(String key) {
        return new File(dir, "wall_" + key.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    private static String redirect(String url) throws Exception {
        URL u = openV4(url);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Host", new URL(url).getHost());
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            int code = c.getResponseCode();
            if (code != 302 && code != 301) throw new IllegalStateException(url + " answered " + code + ", wanted a redirect");
            String loc = c.getHeaderField("Location");
            if (loc == null || loc.isEmpty()) throw new IllegalStateException(url + " redirected nowhere");
            return loc;
        } finally {
            c.disconnect();
        }
    }

    private static URL openV4(String url) throws Exception {
        return new URL(url);
    }

    private static byte[] get(String url) throws Exception {
        URL u = openV4(url);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        try {
            c.setRequestProperty("Host", new URL(url).getHost());
            c.setConnectTimeout(10000);
            c.setReadTimeout(60000);
            c.setRequestProperty("accept", "application/json, image/jpeg");
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException(url + " answered " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[16 * 1024];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > 16 * 1024 * 1024) throw new IllegalStateException("picture over 16 MB");
                }
                return buf.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static void write(File target, byte[] bytes) throws Exception {
        File tmp = new File(target.getParentFile(), target.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            try (FileOutputStream out = new FileOutputStream(target)) { out.write(bytes); }
            // A tmp left behind would be picked up by nothing; drop it.
            if (!tmp.delete()) tmp.deleteOnExit();
        }
    }

    /** Keeps the newest few, so a month of hours does not fill the TV. */
    private void trim() {
        File[] all = dir.listFiles((d, name) -> name.startsWith("wall_") && name.endsWith(".jpg"));
        if (all == null || all.length <= KEEP) return;
        Arrays.sort(all, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = KEEP; i < all.length; i++) {
            if (!all[i].delete()) Log.w(TAG, "could not prune " + all[i].getName());
        }
    }
}
