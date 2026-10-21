package io.github.epochcoder.homelab;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Posters and backdrops for the Media screens, from the public image hosts the *arr lookups name (TMDB,
 * TheTVDB). Three background threads, a memory cache of an eighth of the app's heap, and each image decoded
 * no larger than its view needs. A view that has moved on to another URL by the time its image arrives keeps
 * the newer one.
 */
final class Images {
    private static final String TAG = "Homelab.Images";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "images");
        t.setDaemon(true);
        return t;
    });
    /** Public CDNs: no key, system CAs only, redirects followed. */
    private static final Http HTTP = Http.anonymous();
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8 / 1024)) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount() / 1024;
                }
            };

    private Images() {}

    /**
     * Shows {@code url} in {@code v}, decoded to about {@code w} x {@code h} pixels; clears it meanwhile. Cached
     * per URL and width, so a poster first seen small in the grid is fetched again, sharp, for the detail screen.
     */
    static void load(ImageView v, String url, int w, int h) {
        String key = key(url, w);
        v.setTag(R.id.image_url, key);
        Bitmap cached = url.isEmpty() ? null : CACHE.get(key);
        v.setImageBitmap(cached);
        if (cached != null || url.isEmpty()) return;
        POOL.execute(() -> {
            Bitmap b = null;
            try {
                byte[] data = HTTP.bytes(sized(url, w));
                b = decode(data, w, h);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, url + ": " + e);
            }
            if (b == null) return;
            CACHE.put(key, b);
            Bitmap shown = b;
            MAIN.post(() -> {
                if (!key.equals(v.getTag(R.id.image_url))) return;
                v.setAlpha(0f);
                v.setImageBitmap(shown);
                v.animate().alpha(1f).setDuration(200).start();
            });
        });
    }

    /** Puts an image in the cache for one URL and width, for screens rendered without a network. */
    static void put(String url, int w, Bitmap b) {
        CACHE.put(key(url, w), b);
    }

    static String key(String url, int w) {
        return url + "#" + w;
    }

    /**
     * TMDB serves any width from the same path, and the lookups name "original", often several megabytes;
     * ask for one just wider than the view instead. Other hosts are left as they are.
     */
    static String sized(String url, int width) {
        int i = url.indexOf("image.tmdb.org/t/p/");
        if (i < 0) return url;
        int start = i + "image.tmdb.org/t/p/".length();
        int end = url.indexOf('/', start);
        if (end < 0) return url;
        String size = width <= 185 ? "w185" : width <= 342 ? "w342" : width <= 500 ? "w500" : width <= 780 ? "w780" : "w1280";
        return url.substring(0, start) + size + url.substring(end);
    }

    private static Bitmap decode(byte[] data, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inSampleSize = Slideshow.sampleSize(o.outWidth, o.outHeight, Math.max(1, w), Math.max(1, h));
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }
}
