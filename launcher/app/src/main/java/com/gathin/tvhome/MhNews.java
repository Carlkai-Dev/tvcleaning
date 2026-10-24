package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maharashtra news in Marathi: Google News RSS titles/snippets plus internet photos as backdrops,
 * cached for the day and shown when the launcher is idle.
 */
final class MhNews {
    static final class Story {
        final String title;
        final String summary;
        final File image;

        Story(String title, String summary, File image) {
            this.title = title;
            this.summary = summary;
            this.image = image;
        }
    }

    interface Ready {
        void onReady(int count);
    }

    private static final String TAG = "MhNews";
    private static final String PREFS = "mh_news";
    private static final String UA = "CleanYourTV/2.3 (Android TV; Maharashtra Marathi news; local install)";
    private static final String RSS =
            "https://news.google.com/rss/search?q=%E0%A4%AE%E0%A4%B9%E0%A4%BE%E0%A4%B0%E0%A4%BE%E0%A4%B7%E0%A5%8D%E0%A4%9F%E0%A5%8D%E0%A4%B0&hl=mr&gl=IN&ceid=IN:mr";
    private static final int MIN_STORIES = 8;
    private static final int MAX_STORIES = 20;
    private static final int MAX_IMAGES = 20;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Pattern ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern DESC = Pattern.compile("<description>(.*?)</description>", Pattern.DOTALL);

    private static volatile List<Story> cached = Collections.emptyList();

    private MhNews() {}

    static List<Story> stories() {
        return cached;
    }

    static int count() {
        return cached.size();
    }

    static Story at(int index) {
        List<Story> list = cached;
        if (list.isEmpty()) return null;
        int i = ((index % list.size()) + list.size()) % list.size();
        return list.get(i);
    }

    /** Background fetch once per day; keeps existing stories usable while refreshing. */
    static void ensureDaily(Context ctx, Ready ready) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            try {
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String today = LocalDate.now().toString();
                if (today.equals(prefs.getString("day", "")) && cached.size() >= MIN_STORIES) {
                    if (ready != null) ready.onReady(cached.size());
                    return;
                }
                File imgDir = new File(app.getCacheDir(), "mh_news_images");
                imgDir.mkdirs();
                List<File> images = ensureImages(imgDir);
                List<Story> stories = parseRss(images);
                if (stories.size() < MIN_STORIES / 2 && !images.isEmpty()) {
                    // Fallback: still show Maharashtra photo cards with a short Marathi label.
                    stories = fallbackStories(images);
                }
                if (!stories.isEmpty()) {
                    cached = stories;
                    prefs.edit().putString("day", today).apply();
                }
                if (ready != null) ready.onReady(cached.size());
            } catch (Exception e) {
                Log.w(TAG, "ensureDaily failed", e);
                if (ready != null) ready.onReady(cached.size());
            }
        });
    }

    private static List<Story> fallbackStories(List<File> images) {
        String[] titles = {
                "महाराष्ट्रची ताज्या बातम्या",
                "मुंबई · पुणे · नागपूर",
                "राज्यातील महत्त्वाच्या घडामोडी",
                "महाराष्ट्र डेस्क",
        };
        String[] bodies = {
                "लाँचर निष्क्रिय असताना राज्यातील बातम्या येथे दिसतात. कोणतीही बाण की दाबा आणि होम स्क्रीनवर परत या.",
                "दररोज इंटरनेटवरून मराठी बातम्या कॅश केल्या जातात.",
                "बातमी वाचण्यासाठी स्क्रीनवर थांबा; होमसाठी कोणतीही बाण की दाबा.",
                "महाराष्ट्र · मराठी न्यूज",
        };
        List<Story> out = new ArrayList<>();
        for (int i = 0; i < images.size() && i < MAX_STORIES; i++) {
            out.add(new Story(titles[i % titles.length], bodies[i % bodies.length], images.get(i)));
        }
        return out;
    }

    private static List<Story> parseRss(List<File> images) throws Exception {
        String xml = httpGet(RSS);
        List<Story> out = new ArrayList<>();
        Matcher items = ITEM.matcher(xml);
        int img = 0;
        while (items.find() && out.size() < MAX_STORIES) {
            String block = items.group(1);
            String title = first(TITLE, block);
            String desc = first(DESC, block);
            title = cleanHtml(title);
            desc = cleanHtml(desc);
            if (title.isEmpty()) continue;
            // Google titles often end with " - Source"
            int dash = title.lastIndexOf(" - ");
            if (dash > 10) title = title.substring(0, dash).trim();
            if (desc.length() > 280) desc = desc.substring(0, 277) + "…";
            File image = images.isEmpty() ? null : images.get(img % images.size());
            img++;
            out.add(new Story(title, desc, image));
        }
        return out;
    }

    private static String first(Pattern p, String block) {
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1) : "";
    }

    private static String cleanHtml(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String unescaped = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString();
        unescaped = Html.fromHtml(unescaped, Html.FROM_HTML_MODE_LEGACY).toString();
        return unescaped.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static List<File> ensureImages(File dir) {
        File[] existing = dir.listFiles((d, n) -> {
            String s = n.toLowerCase(Locale.US);
            return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png");
        });
        List<File> list = new ArrayList<>();
        if (existing != null) {
            Collections.addAll(list, existing);
            Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
        }
        if (list.size() >= MIN_STORIES) return list;
        Set<String> urls = new LinkedHashSet<>();
        for (String q : new String[]{
                "Maharashtra India Mumbai",
                "Mumbai India skyline",
                "Pune Maharashtra",
                "Gateway of India Mumbai",
                "Maharashtra countryside India"
        }) {
            urls.addAll(searchWikimedia(q, MAX_IMAGES));
            if (urls.size() >= MAX_IMAGES) break;
        }
        int idx = list.size();
        for (String url : urls) {
            if (idx >= MAX_IMAGES) break;
            File out = new File(dir, String.format(Locale.US, "%02d.jpg", idx));
            if (out.exists() && out.length() > 8_000) {
                list.add(out);
                idx++;
                continue;
            }
            try {
                if (httpToFile(url, out) && out.length() > 8_000) {
                    list.add(out);
                    idx++;
                } else if (out.exists()) //noinspection ResultOfMethodCallIgnored
                    out.delete();
            } catch (Exception e) {
                if (out.exists()) //noinspection ResultOfMethodCallIgnored
                    out.delete();
            }
        }
        Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
        return list;
    }

    private static List<String> searchWikimedia(String query, int limit) {
        List<String> urls = new ArrayList<>();
        try {
            String q = URLEncoder.encode(query, "UTF-8");
            String api = "https://commons.wikimedia.org/w/api.php?action=query&format=json"
                    + "&generator=search&gsrnamespace=6&gsrsearch=" + q
                    + "&gsrlimit=" + Math.min(40, limit)
                    + "&prop=imageinfo&iiprop=url|mime&iiurlwidth=1280";
            String json = httpGet(api);
            JSONObject root = new JSONObject(json);
            JSONObject pages = root.optJSONObject("query") == null ? null
                    : root.getJSONObject("query").optJSONObject("pages");
            if (pages == null) return urls;
            JSONArray names = pages.names();
            if (names == null) return urls;
            for (int i = 0; i < names.length() && urls.size() < limit; i++) {
                JSONObject page = pages.getJSONObject(names.getString(i));
                JSONArray info = page.optJSONArray("imageinfo");
                if (info == null || info.length() == 0) continue;
                JSONObject ii = info.getJSONObject(0);
                String mime = ii.optString("mime", "");
                if (!(mime.startsWith("image/jpeg") || mime.equals("image/png"))) continue;
                String url = ii.optString("thumburl", "");
                if (url.isEmpty()) url = ii.optString("url", "");
                if (url.isEmpty()) continue;
                int qmark = url.indexOf('?');
                if (qmark > 0) url = url.substring(0, qmark);
                urls.add(url);
            }
        } catch (Exception e) {
            Log.w(TAG, "wikimedia search failed", e);
        }
        return urls;
    }

    private static String httpGet(String urlSpec) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlSpec).openConnection();
        c.setRequestProperty("User-Agent", UA);
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
        if (c.getResponseCode() >= 400) {
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
}
