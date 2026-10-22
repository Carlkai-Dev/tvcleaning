package io.github.epochcoder.homelab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Sonarr (v4) and Radarr (v5 and later) through their shared v3 API: look up and add with search,
 * search a library title again, and read the queue, the calendar, health and disk space. Adding
 * sends the lookup result back with a quality profile, a root folder, what to monitor and "search
 * now", the same body the web UI sends.
 */
final class Arr {
    enum Kind {
        MOVIE("movie", "includeMovie=true"),
        SERIES("series", "includeSeries=true&includeEpisode=true");

        final String resource;
        final String queueIncludes;

        Kind(String resource, String queueIncludes) {
            this.resource = resource;
            this.queueIncludes = queueIncludes;
        }
    }

    /**
     * What Sonarr monitors when a show is added, in the order Settings and the detail screen offer
     * them.
     */
    static final String[] SERIES_MONITOR = {
        "all", "future", "missing", "firstSeason", "latestSeason", "pilot", "none"
    };

    /**
     * A lookup or library result: what the screens show, and the server's own JSON to send back
     * when adding.
     */
    static final class Item {
        final Kind kind;
        final String title;
        final int year;
        final String overview;
        final String poster;
        final String fanart;
        final List<String> genres;

        /** Minutes; 0 when unknown. */
        final int runtime;

        final String certification;
        final String network;

        /** Seasons without specials; 0 for a film. */
        final int seasons;

        /** "IMDb 7.8 · RT 92%", "★ 8.4", or "". */
        final String rating;

        /**
         * The server's status word: continuing, ended, upcoming, released, inCinemas, announced...
         */
        final String status;

        /** The library's id, 0 when the title is not in the library. */
        final int id;

        /** A film whose file is on disk. */
        final boolean hasFile;

        final int episodeFiles;
        final int episodes;
        final JSONObject raw;

        Item(Kind kind, JSONObject o) {
            this.kind = kind;
            raw = o;
            title = o.optString("title");
            year = o.optInt("year");
            overview = o.optString("overview").replaceAll("\\s+", " ").trim();
            poster = image(o, "poster", o.optString("remotePoster"));
            fanart = image(o, "fanart", "");
            genres = strings(o.optJSONArray("genres"));
            runtime = o.optInt("runtime");
            certification = o.isNull("certification") ? "" : o.optString("certification");
            network = o.isNull("network") ? "" : o.optString("network");
            JSONArray s = o.optJSONArray("seasons");
            int n = 0;
            if (s != null) {
                for (int i = 0; i < s.length(); i++) {
                    JSONObject season = s.optJSONObject(i);
                    if (season != null && season.optInt("seasonNumber") > 0) {
                        n++;
                    }
                }
            }
            seasons = n;
            rating = rating(kind, o.optJSONObject("ratings"));
            status = o.optString("status");
            id = o.optInt("id");
            hasFile = o.optBoolean("hasFile");
            JSONObject stats = o.optJSONObject("statistics");
            episodeFiles = stats == null ? 0 : stats.optInt("episodeFileCount");
            episodes = stats == null ? 0 : stats.optInt("episodeCount");
        }

        boolean inLibrary() {
            return id > 0;
        }

        String label() {
            return year > 0 ? title + " (" + year + ")" : title;
        }

        /**
         * "2021 · 2 h 35 min · PG-13 · Science Fiction, Adventure" or "2022 · Apple TV+ · 2 seasons
         * · Drama".
         */
        String meta() {
            List<String> bits = new ArrayList<>();
            if (year > 0) {
                bits.add(String.valueOf(year));
            }
            if (kind == Kind.SERIES) {
                if (!network.isEmpty()) {
                    bits.add(network);
                }
                if (seasons > 0) {
                    bits.add(seasons == 1 ? "1 season" : seasons + " seasons");
                }
            } else if (runtime > 0) {
                bits.add(Format.duration(runtime * 60L));
            }
            if (!certification.isEmpty()) {
                bits.add(certification);
            }
            if (!genres.isEmpty()) {
                bits.add(String.join(", ", genres.subList(0, Math.min(3, genres.size()))));
            }
            return String.join("  ·  ", bits);
        }
    }

    /**
     * What an add uses: the quality profile and root folder from Settings, or the server's first
     * ones.
     */
    static final class Defaults {
        final int profileId;
        final String profileName;
        final String rootFolder;

        Defaults(int profileId, String profileName, String rootFolder) {
            this.profileId = profileId;
            this.profileName = profileName;
            this.rootFolder = rootFolder;
        }
    }

    /**
     * One download the app is tracking. {@link #downloadId} is the torrent hash for torrent
     * clients.
     */
    static final class QueueEntry {
        final Kind kind;
        final String title;
        final String release;
        final String downloadId;
        final double progress;
        final long size;

        /** Seconds, -1 when unknown. */
        final long timeLeft;

        /**
         * trackedDownloadState when the app has one (downloading, importPending, importing...),
         * else status.
         */
        final String state;

        final String error;

        QueueEntry(
                Kind kind,
                String title,
                String release,
                String downloadId,
                double progress,
                long size,
                long timeLeft,
                String state,
                String error) {
            this.kind = kind;
            this.title = title;
            this.release = release;
            this.downloadId = downloadId;
            this.progress = progress;
            this.size = size;
            this.timeLeft = timeLeft;
            this.state = state;
            this.error = error;
        }
    }

    /** An episode airing or a film coming out, from the calendar. */
    static final class Upcoming {
        final Kind kind;

        /** ISO date-time in UTC: the episode's air time, or the film's release day. */
        final String when;

        final String title;
        final String subtitle;
        final boolean hasFile;

        Upcoming(Kind kind, String when, String title, String subtitle, boolean hasFile) {
            this.kind = kind;
            this.when = when;
            this.title = title;
            this.subtitle = subtitle;
            this.hasFile = hasFile;
        }
    }

    /** A health check the app reports: type is notice, warning or error. */
    static final class Health {
        final String type;
        final String message;

        Health(String type, String message) {
            this.type = type;
            this.message = message;
        }
    }

    final Kind kind;
    private final Config.Arr cfg;
    private final Http http;
    private Defaults defaults;

    Arr(Kind kind, Config.Arr cfg, SSLSocketFactory tls) {
        this.kind = kind;
        this.cfg = cfg;
        this.http = new Http(tls, "X-Api-Key", cfg.apiKey);
    }

    String name() {
        return kind == Kind.MOVIE ? "Radarr" : "Sonarr";
    }

    List<Item> lookup(String term) throws IOException {
        return parseLookup(
                kind,
                http.get(
                        cfg.url
                                + "/api/v3/"
                                + kind.resource
                                + "/lookup?term="
                                + Http.encode(term)));
    }

    /** The profile and folder an add would use; asked once per screen, both lists rarely change. */
    synchronized Defaults defaults() throws IOException {
        if (defaults == null) {
            JSONObject profile = pick(qualityProfiles(), "name", cfg.qualityProfile);
            JSONObject folder = pick(rootFolders(), "path", cfg.rootFolder);
            if (profile == null) {
                throw new IOException("no quality profile on the server");
            }
            if (folder == null) {
                throw new IOException("no root folder on the server");
            }
            defaults =
                    new Defaults(
                            profile.optInt("id"),
                            profile.optString("name"),
                            folder.optString("path"));
        }
        return defaults;
    }

    /** Objects with id and name. */
    JSONArray qualityProfiles() throws IOException {
        return array(http.get(cfg.url + "/api/v3/qualityprofile"));
    }

    /** Objects with id and path. */
    JSONArray rootFolders() throws IOException {
        return array(http.get(cfg.url + "/api/v3/rootfolder"));
    }

    /** "Sonarr 4.0.15.2941": Settings' Test button, proving both the URL and the key. */
    String version() throws IOException {
        try {
            JSONObject s = new JSONObject(http.get(cfg.url + "/api/v3/system/status"));
            return s.optString("appName", name()) + " " + s.optString("version");
        } catch (JSONException e) {
            throw new IOException("unexpected response: " + e.getMessage());
        }
    }

    /** Adds and starts the search; returns the title as the library now holds it, with its id. */
    Item add(Item item, Defaults d, String monitor) throws IOException {
        String response =
                http.post(
                        cfg.url + "/api/v3/" + kind.resource,
                        addBody(kind, item.raw, d, monitor).toString());
        try {
            return new Item(kind, new JSONObject(response));
        } catch (JSONException e) {
            throw new IOException("added, but the answer did not parse: " + e.getMessage());
        }
    }

    /** Searches the indexers again for a title already in the library. */
    void searchNow(Item item) throws IOException {
        http.post(cfg.url + "/api/v3/command", searchCommand(kind, item.id).toString());
    }

    List<QueueEntry> queue() throws IOException {
        return parseQueue(
                kind,
                http.get(cfg.url + "/api/v3/queue?page=1&pageSize=100&" + kind.queueIncludes));
    }

    /** Episodes airing, or films coming out, between two ISO dates (UTC). */
    List<Upcoming> calendar(String start, String end) throws IOException {
        String q =
                "/api/v3/calendar?start="
                        + Http.encode(start)
                        + "&end="
                        + Http.encode(end)
                        + (kind == Kind.SERIES ? "&includeSeries=true" : "");
        return parseCalendar(kind, http.get(cfg.url + q), start, end);
    }

    List<Health> health() throws IOException {
        return parseHealth(http.get(cfg.url + "/api/v3/health"));
    }

    /** Monitored episodes or films that have aired or come out without a file. */
    int missing() throws IOException {
        try {
            return new JSONObject(
                            http.get(
                                    cfg.url
                                            + "/api/v3/wanted/missing?page=1&pageSize=1&monitored=true"))
                    .optInt("totalRecords");
        } catch (JSONException e) {
            throw new IOException("unexpected response: " + e.getMessage());
        }
    }

    /** Free and total bytes of the largest disk the app reports, or null when it reports none. */
    long[] disk() throws IOException {
        return largestDisk(array(http.get(cfg.url + "/api/v3/diskspace")));
    }

    // Parsing and request bodies, pure so they are unit-tested.

    static List<Item> parseLookup(Kind kind, String json) throws IOException {
        JSONArray a = array(json);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !o.optString("title").isEmpty()) {
                items.add(new Item(kind, o));
            }
        }
        return items;
    }

    /** A cover's remote URL by type ("poster", "fanart"), else {@code fallback}. */
    static String image(JSONObject o, String type, String fallback) {
        JSONArray images = o.optJSONArray("images");
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                JSONObject img = images.optJSONObject(i);
                if (img != null && type.equals(img.optString("coverType"))) {
                    String url = img.optString("remoteUrl");
                    if (url.startsWith("http")) {
                        return url;
                    }
                }
            }
        }
        return fallback == null || !fallback.startsWith("http") ? "" : fallback;
    }

    /** Radarr's ratings by source ("IMDb 7.8 · RT 92%"), Sonarr's single one ("★ 8.4"). */
    static String rating(Kind kind, JSONObject r) {
        if (r == null) {
            return "";
        }
        List<String> out = new ArrayList<>();
        if (kind == Kind.SERIES) {
            double v = r.optDouble("value", 0);
            if (v > 0) {
                out.add(String.format(Locale.ROOT, "★ %.1f", v));
            }
        } else {
            JSONObject imdb = r.optJSONObject("imdb");
            JSONObject tmdb = r.optJSONObject("tmdb");
            JSONObject rt = r.optJSONObject("rottenTomatoes");
            if (imdb != null && imdb.optDouble("value", 0) > 0) {
                out.add(String.format(Locale.ROOT, "IMDb %.1f", imdb.optDouble("value")));
            } else if (tmdb != null && tmdb.optDouble("value", 0) > 0) {
                out.add(String.format(Locale.ROOT, "TMDB %.1f", tmdb.optDouble("value")));
            }
            if (rt != null && rt.optDouble("value", 0) > 0) {
                out.add(Math.round(rt.optDouble("value")) + "% RT");
            }
        }
        return String.join("  ·  ", out);
    }

    /**
     * Films and shows in one list, best match first: a title equal to the search, then one starting
     * with it, then the rest, each group alternating films and shows in the order the servers
     * ranked them.
     */
    static List<Item> merge(List<Item> movies, List<Item> shows, String term) {
        List<Item> mixed = new ArrayList<>();
        for (int i = 0; i < Math.max(movies.size(), shows.size()); i++) {
            if (i < movies.size()) {
                mixed.add(movies.get(i));
            }
            if (i < shows.size()) {
                mixed.add(shows.get(i));
            }
        }
        String t = fold(term);
        List<Item> out = new ArrayList<>();
        for (int rank = 0; rank < 3; rank++) {
            for (Item item : mixed) {
                String title = fold(item.title);
                int r = title.equals(t) ? 0 : title.startsWith(t) ? 1 : 2;
                if (r == rank) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    private static String fold(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /**
     * The entry whose {@code key} equals {@code wanted} (no case), or the first one when wanted is
     * empty or absent.
     */
    static JSONObject pick(JSONArray a, String key, String wanted) {
        JSONObject first = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) {
                continue;
            }
            if (first == null) {
                first = o;
            }
            if (!wanted.isEmpty() && wanted.equalsIgnoreCase(o.optString(key))) {
                return o;
            }
        }
        return first;
    }

    /**
     * {@code monitor} is one of {@link #SERIES_MONITOR} for a show; films are always monitored and
     * searched.
     */
    static JSONObject addBody(Kind kind, JSONObject lookup, Defaults d, String monitor)
            throws IOException {
        try {
            JSONObject body =
                    new JSONObject(lookup.toString())
                            .put("qualityProfileId", d.profileId)
                            .put("rootFolderPath", d.rootFolder)
                            .put("monitored", true);
            if (kind == Kind.MOVIE) {
                body.put("minimumAvailability", "released")
                        .put(
                                "addOptions",
                                new JSONObject()
                                        .put("monitor", "movieOnly")
                                        .put("searchForMovie", true));
            } else {
                String m = monitor == null || monitor.isEmpty() ? "all" : monitor;
                body.put("seasonFolder", true)
                        .put("monitored", !"none".equals(m))
                        .put(
                                "addOptions",
                                new JSONObject()
                                        .put("monitor", m)
                                        .put(
                                                "searchForMissingEpisodes",
                                                !"none".equals(m) && !"future".equals(m))
                                        .put("searchForCutoffUnmetEpisodes", false));
            }
            return body;
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    static JSONObject searchCommand(Kind kind, int id) {
        try {
            return kind == Kind.MOVIE
                    ? new JSONObject()
                            .put("name", "MoviesSearch")
                            .put("movieIds", new JSONArray().put(id))
                    : new JSONObject().put("name", "SeriesSearch").put("seriesId", id);
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
    }

    static List<QueueEntry> parseQueue(Kind kind, String json) throws IOException {
        JSONArray records;
        try {
            records = new JSONObject(json).optJSONArray("records");
        } catch (JSONException e) {
            throw new IOException("unexpected queue response: " + e.getMessage());
        }
        List<QueueEntry> out = new ArrayList<>();
        if (records == null) {
            return out;
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject r = records.optJSONObject(i);
            if (r == null) {
                continue;
            }
            String release = r.optString("title");
            String title = release;
            JSONObject owner = r.optJSONObject(kind == Kind.MOVIE ? "movie" : "series");
            if (owner != null && !owner.optString("title").isEmpty()) {
                title = owner.optString("title");
                JSONObject ep = r.optJSONObject("episode");
                if (ep != null) {
                    title +=
                            String.format(
                                    Locale.ROOT,
                                    " S%02dE%02d",
                                    ep.optInt("seasonNumber"),
                                    ep.optInt("episodeNumber"));
                }
            }
            long size = (long) r.optDouble("size", 0);
            long left = (long) r.optDouble("sizeleft", 0);
            double progress = size > 0 ? (double) (size - left) / size : 0;
            String state = r.optString("trackedDownloadState", r.optString("status"));
            String error = r.isNull("errorMessage") ? "" : r.optString("errorMessage");
            out.add(
                    new QueueEntry(
                            kind,
                            title,
                            release,
                            r.optString("downloadId"),
                            progress,
                            size,
                            Format.arrTimeLeft(r.optString("timeleft")),
                            state,
                            error));
        }
        return out;
    }

    /**
     * Sonarr's calendar is episodes with their series; Radarr's is films, with up to three release
     * dates, of which the one inside the window is the one that counts.
     */
    static List<Upcoming> parseCalendar(Kind kind, String json, String start, String end)
            throws IOException {
        JSONArray a = array(json);
        List<Upcoming> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) {
                continue;
            }
            if (kind == Kind.SERIES) {
                JSONObject series = o.optJSONObject("series");
                String show = series == null ? "" : series.optString("title");
                String ep =
                        String.format(
                                Locale.ROOT,
                                "S%02dE%02d",
                                o.optInt("seasonNumber"),
                                o.optInt("episodeNumber"));
                String name = o.isNull("title") ? "" : o.optString("title");
                out.add(
                        new Upcoming(
                                kind,
                                o.optString("airDateUtc"),
                                show.isEmpty() ? name : show,
                                name.isEmpty() || show.isEmpty() ? ep : ep + "  ·  " + name,
                                o.optBoolean("hasFile")));
            } else {
                String[][] dates = {
                    {"digitalRelease", "Digital release"},
                    {"physicalRelease", "Physical release"},
                    {"inCinemas", "In cinemas"}
                };
                for (String[] d : dates) {
                    String when = o.isNull(d[0]) ? "" : o.optString(d[0]);
                    if (!when.isEmpty() && when.compareTo(start) >= 0 && when.compareTo(end) < 0) {
                        String year = o.optInt("year") > 0 ? " (" + o.optInt("year") + ")" : "";
                        out.add(
                                new Upcoming(
                                        kind,
                                        when,
                                        o.optString("title") + year,
                                        d[1],
                                        o.optBoolean("hasFile")));
                        break;
                    }
                }
            }
        }
        return out;
    }

    static List<Health> parseHealth(String json) throws IOException {
        JSONArray a = array(json);
        List<Health> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String type = o.optString("type");
            if (type.equals("warning") || type.equals("error")) {
                out.add(new Health(type, o.optString("message")));
            }
        }
        return out;
    }

    /**
     * {free, total} of the disk with the most space, ignoring the small system mounts a NAS also
     * reports.
     */
    static long[] largestDisk(JSONArray a) {
        long[] best = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) {
                continue;
            }
            long total = o.optLong("totalSpace");
            if (best == null || total > best[1]) {
                best = new long[] {o.optLong("freeSpace"), total};
            }
        }
        return best;
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            if (!a.optString(i).isEmpty()) {
                out.add(a.optString(i));
            }
        }
        return out;
    }

    private static JSONArray array(String json) throws IOException {
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            throw new IOException("unexpected response: " + e.getMessage());
        }
    }
}
