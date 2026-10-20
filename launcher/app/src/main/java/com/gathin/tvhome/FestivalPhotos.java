package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads 20–30 real photos for <em>today's</em> festival from the internet into app cache
 * (background, once per day per festival). No bundled assets. Wallpaper rotates randomly inside
 * that cached set when the user moves between launcher icons.
 */
final class FestivalPhotos {
    static final int MIN_COUNT = 20;
    static final int MAX_COUNT = 30;
    private static final String TAG = "FestivalPhotos";
    private static final String PREFS = "festival_photos";
    private static final String UA = "CleanYourTV/2.3 (Android TV; festival wallpaper cache; local install)";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    interface Ready {
        /** Fired as soon as photos are usable, and again when the daily download finishes. */
        void onReady(String eventId, int count);
    }

    private FestivalPhotos() {}

    static File dir(Context ctx, String eventId) {
        return new File(ctx.getCacheDir(), "festival_photos/" + safe(eventId));
    }

    static int count(Context ctx, IndiaEvents.Event event) {
        if (ctx == null || event == null) return 0;
        return photoCount(dir(ctx, event.id));
    }

    static File photoFile(Context ctx, IndiaEvents.Event event, int index) {
        if (ctx == null || event == null) return null;
        File[] files = listPhotos(dir(ctx, event.id));
        if (files == null || files.length == 0) return null;
        int i = ((index % files.length) + files.length) % files.length;
        return files[i];
    }

    static int pickIndex(int count, int avoid) {
        if (count <= 0) return 0;
        if (count == 1) return 0;
        int next = (int) (Math.random() * count);
        if (next == avoid) next = (avoid + 1 + (int) (Math.random() * (count - 1))) % count;
        return next;
    }

    /**
     * Background: if today's festival does not yet have 20–30 cached photos for <em>today's date</em>,
     * search the internet and download them. Existing photos for this festival are kept and topped up.
     */
    static void ensureDaily(Context ctx, IndiaEvents.Event event, Ready ready) {
        if (ctx == null || event == null) return;
        Context app = ctx.getApplicationContext();
        String id = event.id;
        String today = LocalDate.now().toString();
        IO.execute(() -> {
            try {
                File folder = dir(app, id);
                folder.mkdirs();
                int have = photoCount(folder);
                if (have > 0 && ready != null) ready.onReady(id, have);

                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String fetchedKey = "fetched_" + safe(id);
                String fetchedDay = prefs.getString(fetchedKey, "");
                boolean needFetch = have < MIN_COUNT || !today.equals(fetchedDay);
                if (!needFetch) return;

                Set<String> urls = new LinkedHashSet<>();
                for (String q : queriesFor(event)) {
                    // Wikimedia first (reliable); Openverse as extra internet source.
                    urls.addAll(searchWikimedia(q, MAX_COUNT));
                    if (urls.size() >= MAX_COUNT) break;
                    urls.addAll(searchOpenverse(q, MAX_COUNT));
                    if (urls.size() >= MAX_COUNT) break;
                }
                Log.i(TAG, id + ": downloading up to " + MAX_COUNT + " from " + urls.size() + " urls");
                int lastNotified = have;
                for (String url : urls) {
                    have = photoCount(folder);
                    if (have >= MAX_COUNT) break;
                    if (!downloadOne(folder, url, have)) continue;
                    have = photoCount(folder);
                    if (ready != null && have > lastNotified
                            && (have == 1 || have == 5 || have % 5 == 0 || have >= MIN_COUNT)) {
                        lastNotified = have;
                        ready.onReady(id, have);
                    }
                }
                have = photoCount(folder);
                if (have >= MIN_COUNT) {
                    prefs.edit().putString(fetchedKey, today).apply();
                }
                if (ready != null && have > 0) ready.onReady(id, have);
                Log.i(TAG, id + ": cache now " + have + " photos");
            } catch (Exception e) {
                Log.w(TAG, "daily fetch failed for " + id, e);
                int have = photoCount(dir(app, id));
                if (ready != null && have > 0) ready.onReady(id, have);
            }
        });
    }

    private static List<String> queriesFor(IndiaEvents.Event event) {
        List<String> q = new ArrayList<>();
        q.add(photoQuery(event));
        q.add(event.title + " festival India");
        q.add(photoQueryAlt(event));
        return q;
    }

    static String photoQuery(IndiaEvents.Event event) {
        if (event == null) return "India festival celebration";
        switch (event.id) {
            case "dussehra": return "Dussehra Vijayadashami India";
            case "navratri": return "Navratri Garba Durga India";
            case "diwali": return "Diwali Deepavali diya India";
            case "holi": return "Holi festival colours India";
            case "ganesh":
            case "ganesh_mh": return "Ganesh Chaturthi Mumbai India";
            case "rakhi": return "Raksha Bandhan India";
            case "republic": return "Republic Day India parade";
            case "independence": return "Independence Day India flag";
            case "gandhi": return "Gandhi Jayanti India";
            case "christmas": return "Christmas India celebration";
            case "newyear": return "New Year India celebration";
            case "children": return "Childrens Day India";
            case "teachers": return "Teachers Day India";
            case "gudi_padwa": return "Gudi Padwa Maharashtra";
            case "ashadhi": return "Ashadhi Ekadashi Pandharpur";
            case "pola": return "Pola festival Maharashtra";
            case "narali": return "Narali Purnima Maharashtra";
            case "vat_purnima": return "Vat Purnima Maharashtra";
            case "mh_day": return "Maharashtra Day celebration";
            case "shivaji": return "Shivaji Maharaj Jayanti";
            case "tilak": return "Lokmanya Tilak Jayanti";
            case "mh_everyday": return "Maharashtra India festival culture";
            default: return event.title + " India festival";
        }
    }

    private static String photoQueryAlt(IndiaEvents.Event event) {
        if (event == null) return "India festival";
        if (event.maharashtra) return "Maharashtra " + event.title;
        return event.title + " celebration India photo";
    }

    /** Creative Commons / Openverse image search (internet photos). */
    private static List<String> searchOpenverse(String query, int limit) {
        List<String> urls = new ArrayList<>();
        try {
            String q = URLEncoder.encode(query, "UTF-8");
            String api = "https://api.openverse.org/v1/images/?q=" + q
                    + "&page_size=" + Math.min(50, Math.max(limit, MIN_COUNT))
                    + "&license_type=commercial,modification&format=json";
            String json = httpGet(api);
            JSONObject root = new JSONObject(json);
            JSONArray results = root.optJSONArray("results");
            if (results == null) return urls;
            for (int i = 0; i < results.length() && urls.size() < limit; i++) {
                JSONObject item = results.getJSONObject(i);
                String url = item.optString("url", "");
                if (url.isEmpty()) url = item.optString("thumbnail", "");
                if (url.isEmpty()) continue;
                String lower = url.toLowerCase(Locale.US);
                if (!(lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
                        || lower.contains(".webp") || lower.contains("image"))) {
                    // still try — many CDNs omit extensions
                }
                urls.add(url);
            }
        } catch (Exception e) {
            Log.w(TAG, "openverse search failed: " + query, e);
        }
        return urls;
    }

    private static List<String> searchWikimedia(String query, int limit) {
        List<String> urls = new ArrayList<>();
        try {
            String q = URLEncoder.encode(query, "UTF-8");
            String api = "https://commons.wikimedia.org/w/api.php?action=query&format=json"
                    + "&generator=search&gsrnamespace=6&gsrsearch=" + q
                    + "&gsrlimit=" + Math.min(50, Math.max(limit, MIN_COUNT))
                    + "&prop=imageinfo&iiprop=url|mime|size&iiurlwidth=1280";
            String json = httpGet(api);
            JSONObject root = new JSONObject(json);
            JSONObject pages = root.optJSONObject("query") == null ? null
                    : root.getJSONObject("query").optJSONObject("pages");
            if (pages == null) return urls;
            JSONArray names = pages.names();
            if (names == null) return urls;
            List<JSONObject> ordered = new ArrayList<>();
            for (int i = 0; i < names.length(); i++) {
                ordered.add(pages.getJSONObject(names.getString(i)));
            }
            Collections.sort(ordered, (a, b) -> Integer.compare(a.optInt("index", 99), b.optInt("index", 99)));
            for (JSONObject page : ordered) {
                JSONArray info = page.optJSONArray("imageinfo");
                if (info == null || info.length() == 0) continue;
                JSONObject ii = info.getJSONObject(0);
                String mime = ii.optString("mime", "");
                if (!(mime.startsWith("image/jpeg") || mime.equals("image/png") || mime.equals("image/webp"))) {
                    continue;
                }
                String thumb = ii.optString("thumburl", "");
                String url = !thumb.isEmpty() ? thumb : ii.optString("url", "");
                if (url.isEmpty()) continue;
                int qmark = url.indexOf('?');
                if (qmark > 0) url = url.substring(0, qmark);
                urls.add(url);
                if (urls.size() >= limit) break;
            }
        } catch (Exception e) {
            Log.w(TAG, "wikimedia search failed: " + query, e);
        }
        return urls;
    }

    private static int photoCount(File folder) {
        File[] files = listPhotos(folder);
        return files == null ? 0 : files.length;
    }

    private static File[] listPhotos(File folder) {
        if (folder == null || !folder.isDirectory()) return null;
        File[] files = folder.listFiles((dir, name) -> {
            String n = name.toLowerCase(Locale.US);
            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
        });
        if (files == null || files.length == 0) return null;
        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
        return list.toArray(new File[0]);
    }

    private static boolean downloadOne(File folder, String url, int indexHint) {
        File out = new File(folder, String.format(Locale.US, "%02d.jpg", Math.max(0, indexHint)));
        if (out.exists() && out.length() > 10_000) {
            out = new File(folder, String.format(Locale.US, "%02d.jpg", photoCount(folder)));
            if (out.exists() && out.length() > 10_000) return true;
        }
        try {
            if (httpToFile(url, out) && out.length() > 10_000) return true;
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            Log.w(TAG, "download fail", e);
        }
        return false;
    }

    private static String httpGet(String urlSpec) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlSpec).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(15_000);
        c.setReadTimeout(25_000);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static boolean httpToFile(String urlSpec, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlSpec).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setConnectTimeout(15_000);
        c.setReadTimeout(40_000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code >= 400) {
            c.disconnect();
            return false;
        }
        String mime = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.US);
        if (!(mime.startsWith("image/") || mime.isEmpty() || mime.contains("octet-stream"))) {
            c.disconnect();
            return false;
        }
        File tmp = new File(out.getPath() + ".part");
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             FileOutputStream fos = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) >= 0) {
                fos.write(buf, 0, n);
                total += n;
                if (total > 8_000_000L) break;
            }
            fos.flush();
        } finally {
            c.disconnect();
        }
        if (tmp.length() < 8_000) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        if (out.exists()) //noinspection ResultOfMethodCallIgnored
            out.delete();
        return tmp.renameTo(out);
    }

    private static String safe(String id) {
        if (id == null || id.isEmpty()) return "unknown";
        return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
