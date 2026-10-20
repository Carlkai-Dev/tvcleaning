package com.gathin.streambrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Built-in sites plus user-added URLs. Hidden built-ins and custom entries live in SharedPreferences.
 */
final class SiteStore {
    private static final String PREFS = "sites";
    private static final String KEY_CUSTOM = "custom_json";
    private static final String KEY_HIDDEN = "hidden_ids";
    private static final int MAX_CUSTOM = 24;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final int[] accents;

    SiteStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.accents = new int[]{
                ctx.getColor(R.color.tile_tbcpl),
                ctx.getColor(R.color.tile_kartoons),
                ctx.getColor(R.color.tile_nightflix),
                ctx.getColor(R.color.tile_cinegram),
                ctx.getColor(R.color.tile_cinejoy),
                ctx.getColor(R.color.tile_movy),
                ctx.getColor(R.color.tile_redflix),
        };
    }

    List<Site> defaults() {
        List<Site> list = new ArrayList<>();
        list.add(new Site("tbcpl", ctx.getString(R.string.site_tbcpl), ctx.getString(R.string.site_tbcpl_desc),
                "https://tbcpl.lol/", accents[0], false, false));
        list.add(new Site("kartoons", ctx.getString(R.string.site_kartoons), ctx.getString(R.string.site_kartoons_desc),
                "https://kartoons.to/home", accents[1], false, false));
        list.add(new Site("nightflix", ctx.getString(R.string.site_nightflix), ctx.getString(R.string.site_nightflix_desc),
                "https://nightflix.to/", accents[2], false, false));
        list.add(new Site("cinegram", ctx.getString(R.string.site_cinegram), ctx.getString(R.string.site_cinegram_desc),
                "https://cinegram.tv/", accents[3], true, false));
        list.add(new Site("cinejoy", ctx.getString(R.string.site_cinejoy), ctx.getString(R.string.site_cinejoy_desc),
                "https://cinejoy.pk/", accents[4], false, false));
        list.add(new Site("movy", ctx.getString(R.string.site_movy), ctx.getString(R.string.site_movy_desc),
                "https://www.movy.sx/", accents[5], false, false));
        list.add(new Site("redflix", ctx.getString(R.string.site_redflix), ctx.getString(R.string.site_redflix_desc),
                "http://redflix.biz/", accents[6], false, false));
        return list;
    }

    /** Visible sites: non-hidden built-ins + custom. */
    List<Site> allVisible() {
        Set<String> hidden = hiddenIds();
        List<Site> out = new ArrayList<>();
        for (Site s : defaults()) {
            if (!hidden.contains(s.id)) out.add(s);
        }
        out.addAll(loadCustom());
        return out;
    }

    boolean addCustom(String title, String url) {
        String cleanUrl = normalizeUrl(url);
        if (cleanUrl == null) return false;
        String name = title == null ? "" : title.trim();
        if (name.isEmpty()) {
            Uri u = Uri.parse(cleanUrl);
            name = u.getHost() != null ? u.getHost() : "Site";
        }
        if (name.length() > 24) name = name.substring(0, 24);
        List<Site> custom = loadCustom();
        if (custom.size() >= MAX_CUSTOM) return false;
        for (Site s : allVisible()) {
            if (s.url.equalsIgnoreCase(cleanUrl)) return false;
        }
        boolean aggressive = cleanUrl.toLowerCase(Locale.US).contains("cinegram");
        int accent = accents[custom.size() % accents.length];
        String id = "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        custom.add(new Site(id, name, cleanUrl, cleanUrl, accent, aggressive, true));
        return saveCustom(custom);
    }

    /** Hide a built-in or delete a custom site. */
    boolean remove(String id) {
        if (id == null) return false;
        if (id.startsWith("c_")) {
            List<Site> custom = loadCustom();
            boolean changed = false;
            for (Iterator<Site> it = custom.iterator(); it.hasNext(); ) {
                if (id.equals(it.next().id)) {
                    it.remove();
                    changed = true;
                    break;
                }
            }
            return changed && saveCustom(custom);
        }
        Set<String> hidden = hiddenIds();
        if (!hidden.add(id)) return false;
        return prefs.edit().putStringSet(KEY_HIDDEN, hidden).commit();
    }

    static String normalizeUrl(String raw) {
        if (raw == null) return null;
        String u = raw.trim();
        if (u.isEmpty()) return null;
        if (!u.contains("://")) u = "https://" + u;
        Uri uri = Uri.parse(u);
        String scheme = uri.getScheme();
        if (scheme == null) return null;
        scheme = scheme.toLowerCase(Locale.US);
        if (!scheme.equals("http") && !scheme.equals("https")) return null;
        if (uri.getHost() == null || uri.getHost().isEmpty()) return null;
        return u;
    }

    private Set<String> hiddenIds() {
        Set<String> set = prefs.getStringSet(KEY_HIDDEN, null);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    private List<Site> loadCustom() {
        List<Site> out = new ArrayList<>();
        String json = prefs.getString(KEY_CUSTOM, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "");
                String title = o.optString("title", "Site");
                String url = o.optString("url", "");
                if (id.isEmpty() || normalizeUrl(url) == null) continue;
                int accent = o.optInt("accent", accents[i % accents.length]);
                boolean aggressive = o.optBoolean("aggressive", url.toLowerCase(Locale.US).contains("cinegram"));
                out.add(new Site(id, title, url, url, accent, aggressive, true));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private boolean saveCustom(List<Site> custom) {
        JSONArray arr = new JSONArray();
        try {
            for (Site s : custom) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", s.title);
                o.put("url", s.url);
                o.put("accent", s.accentColor);
                o.put("aggressive", s.aggressiveAdBlock);
                arr.put(o);
            }
            return prefs.edit().putString(KEY_CUSTOM, arr.toString()).commit();
        } catch (Exception e) {
            return false;
        }
    }
}
