package io.github.epochcoder.homelab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * qBittorrent's WebUI API (v2), as far as the Downloads tab needs it: the torrent list, the global
 * speeds, and stop or start one torrent. Logs in with the WebUI user and password when they are
 * set; with none, it relies on qBittorrent's "bypass authentication for clients in whitelisted
 * subnets". An expired session (403, or 401 on some versions) logs in again once. Works with
 * qBittorrent 5 (stop/start) and 4 (pause/resume).
 *
 * <p>Not thread-safe: each screen uses its instance from one background thread.
 */
final class Qbit {
    static final class Torrent {
        final String hash;
        final String name;
        final double progress;
        final long size;
        final long downloadSpeed;
        final long uploadSpeed;

        /** Seconds; Format.ETA_INFINITY or more for never. */
        final long eta;

        final String state;
        final String category;

        Torrent(
                String hash,
                String name,
                double progress,
                long size,
                long downloadSpeed,
                long uploadSpeed,
                long eta,
                String state,
                String category) {
            this.hash = hash;
            this.name = name;
            this.progress = progress;
            this.size = size;
            this.downloadSpeed = downloadSpeed;
            this.uploadSpeed = uploadSpeed;
            this.eta = eta;
            this.state = state;
            this.category = category;
        }

        boolean stopped() {
            return state.startsWith("paused") || state.startsWith("stopped");
        }

        boolean complete() {
            return progress >= 1;
        }

        /**
         * What the state means to someone watching: "Downloading", "Stalled", "Seeding",
         * "Stopped"...
         */
        String stateLabel() {
            switch (state) {
                case "downloading":
                case "forcedDL":
                    return "Downloading";
                case "metaDL":
                case "forcedMetaDL":
                    return "Fetching metadata";
                case "stalledDL":
                    return "Stalled";
                case "queuedDL":
                    return "Queued";
                case "checkingDL":
                case "checkingUP":
                case "checkingResumeData":
                    return "Checking";
                case "pausedDL":
                case "stoppedDL":
                    return "Stopped";
                case "uploading":
                case "forcedUP":
                case "stalledUP":
                    return "Seeding";
                case "queuedUP":
                    return "Done, queued to seed";
                case "pausedUP":
                case "stoppedUP":
                    return "Done";
                case "moving":
                    return "Moving";
                case "allocating":
                    return "Allocating";
                case "error":
                    return "Error";
                case "missingFiles":
                    return "Files missing";
                default:
                    return state;
            }
        }
    }

    private final Config.QBittorrent cfg;
    private final Http http;
    private final Map<String, String> headers = new HashMap<>();

    Qbit(Config.QBittorrent cfg, SSLSocketFactory tls) {
        this.cfg = cfg;
        this.http = new Http(tls, "", null);
        // qBittorrent's CSRF check compares Referer with the host it is reached at; the base URL is
        // exactly that.
        headers.put("Referer", cfg.url);
    }

    /** Newest first, finished ones too: what the screen shows is up to it. */
    List<Torrent> torrents() throws IOException {
        return parseTorrents(call("GET", "/api/v2/torrents/info?sort=added_on&reverse=true", null));
    }

    /** {download, upload} in bytes per second, for the whole client. */
    long[] speeds() throws IOException {
        try {
            JSONObject o = new JSONObject(call("GET", "/api/v2/transfer/info", null));
            return new long[] {o.optLong("dl_info_speed"), o.optLong("up_info_speed")};
        } catch (JSONException e) {
            throw new IOException("unexpected response from qBittorrent: " + e.getMessage());
        }
    }

    String version() throws IOException {
        return "qBittorrent " + call("GET", "/api/v2/app/version", null);
    }

    /** Stops a running torrent or starts a stopped one. */
    void toggle(Torrent t) throws IOException {
        String form = "hashes=" + Http.encode(t.hash);
        String now = t.stopped() ? "start" : "stop";
        String old = t.stopped() ? "resume" : "pause";
        try {
            call("POST", "/api/v2/torrents/" + now, form);
        } catch (NotFound e) {
            call("POST", "/api/v2/torrents/" + old, form); // qBittorrent 4.x
        }
    }

    private static final class NotFound extends IOException {
        NotFound(String m) {
            super(m);
        }
    }

    private String call(String method, String path, String form) throws IOException {
        Http.Response r = send(method, path, form);
        if (unauthorized(r) && login()) {
            r = send(method, path, form);
        }
        if (r.redirect()) {
            throw new IOException(Http.redirected(r.location));
        }
        if (r.code == 404) {
            throw new NotFound("HTTP 404 from qBittorrent: " + path);
        }
        if (unauthorized(r)) {
            throw new IOException(
                    cfg.username.isEmpty()
                            ? "qBittorrent wants a login: set the user and password, or whitelist"
                                    + " this network in qBittorrent"
                            : "qBittorrent refused the login: check the user and password");
        }
        if (r.code >= 400) {
            throw new IOException("HTTP " + r.code + " from qBittorrent");
        }
        return r.json();
    }

    private Http.Response send(String method, String path, String form) throws IOException {
        return http.send(
                method, cfg.url + path, form, "application/x-www-form-urlencoded", headers);
    }

    private static boolean unauthorized(Http.Response r) {
        return r.code == 401 || r.code == 403;
    }

    /** False when there is nothing to log in with; the refusal then stands. */
    private boolean login() throws IOException {
        if (cfg.username.isEmpty()) {
            return false;
        }
        headers.remove("Cookie");
        Http.Response r =
                send(
                        "POST",
                        "/api/v2/auth/login",
                        "username="
                                + Http.encode(cfg.username)
                                + "&password="
                                + Http.encode(cfg.password));
        if (r.code == 403) {
            throw new IOException("qBittorrent has banned this address after failed logins");
        }
        if (r.redirect()) {
            throw new IOException(Http.redirected(r.location));
        }
        String cookie = sessionCookie(r.cookies);
        if (cookie.isEmpty() || !r.text().startsWith("Ok")) {
            throw new IOException("qBittorrent refused the login: check the user and password");
        }
        headers.put("Cookie", cookie);
        return true;
    }

    /**
     * The session cookie(s) from a login: "SID" by default, but builds and proxies that rename it
     * keep "SID" in the name (QBT_SID_8080). Several are joined as one Cookie header.
     */
    static String sessionCookie(List<String> cookies) {
        List<String> out = new ArrayList<>();
        for (String c : cookies) {
            int eq = c.indexOf('=');
            if (eq > 0
                    && c.substring(0, eq).toUpperCase(Locale.ROOT).contains("SID")
                    && eq < c.length() - 1) {
                out.add(c);
            }
        }
        return String.join("; ", out);
    }

    static List<Torrent> parseTorrents(String json) throws IOException {
        try {
            JSONArray a = new JSONArray(json);
            List<Torrent> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || o.optString("hash").isEmpty()) {
                    continue;
                }
                out.add(
                        new Torrent(
                                o.optString("hash").toLowerCase(Locale.ROOT),
                                o.optString("name"),
                                o.optDouble("progress", 0),
                                o.optLong("size"),
                                o.optLong("dlspeed"),
                                o.optLong("upspeed"),
                                o.optLong("eta", Format.ETA_INFINITY),
                                o.optString("state"),
                                o.optString("category")));
            }
            return out;
        } catch (JSONException e) {
            throw new IOException("unexpected response from qBittorrent: " + e.getMessage());
        }
    }
}
