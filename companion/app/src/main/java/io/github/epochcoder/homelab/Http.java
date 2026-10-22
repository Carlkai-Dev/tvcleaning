package io.github.epochcoder.homelab;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Blocking JSON and image requests for one server and its API key header. Call off the main thread.
 * Errors come back as IOExceptions whose message is short enough to put on screen. {@link #send} is
 * the raw form for servers that log in with a form and a cookie instead of a key (qBittorrent).
 *
 * <p>Redirects are not followed: a request carries an API key or a session cookie, and a redirect
 * (Caddy's forward_auth sending the request to a login page, or a misconfigured proxy) would take
 * that along to another host. They come back as an error naming where they lead. Only {@link
 * #anonymous()}, for the public image hosts, follows them.
 */
final class Http {
    private static final int TIMEOUT_MS = 15_000;

    /**
     * Larger than any Immich preview or *arr JSON page the app asks for; a runaway response stops
     * here.
     */
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private final SSLSocketFactory tls;
    private final String keyHeader;
    private final String key;
    private final boolean followRedirects;

    Http(SSLSocketFactory tls, String keyHeader, String key) {
        this(tls, keyHeader, key, false);
    }

    private Http(SSLSocketFactory tls, String keyHeader, String key, boolean followRedirects) {
        this.tls = tls;
        this.keyHeader = keyHeader;
        this.key = key;
        this.followRedirects = followRedirects;
    }

    /** No key, no cookies, system CAs, redirects followed: for public images only. */
    static Http anonymous() {
        return new Http(null, "", null, true);
    }

    String get(String url) throws IOException {
        return json(request("GET", url, null));
    }

    String post(String url, String body) throws IOException {
        return json(request("POST", url, body));
    }

    byte[] bytes(String url) throws IOException {
        return request("GET", url, null);
    }

    private byte[] request(String method, String url, String body) throws IOException {
        Response r =
                send(
                        method,
                        url,
                        body,
                        body == null ? null : "application/json",
                        Collections.emptyMap());
        if (r.redirect()) {
            throw new IOException(redirected(r.location));
        }
        if (r.code >= 400) {
            String detail = r.text();
            throw new IOException(
                    "HTTP " + r.code + (detail.isEmpty() ? "" : ", " + shorten(detail)));
        }
        return r.body;
    }

    /** A response of any status, with the cookies it set and where a redirect pointed. */
    static final class Response {
        final int code;
        final byte[] body;

        /** "name=value" of each Set-Cookie, attributes dropped. */
        final List<String> cookies;

        /** The Location header, or "". */
        final String location;

        Response(int code, byte[] body, List<String> cookies, String location) {
            this.code = code;
            this.body = body;
            this.cookies = cookies;
            this.location = location;
        }

        boolean redirect() {
            return code >= 300 && code < 400;
        }

        String text() {
            return Http.text(body).trim();
        }

        /** The body as JSON text, refusing a web page as {@link #get} does. */
        String json() throws IOException {
            return Http.json(body);
        }
    }

    /**
     * One request that does not throw on an HTTP error status; network and TLS failures still
     * throw.
     */
    Response send(
            String method, String url, String body, String contentType, Map<String, String> headers)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            if (tls != null && c instanceof HttpsURLConnection) {
                ((HttpsURLConnection) c).setSSLSocketFactory(tls);
            }
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(followRedirects);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json, image/*");
            if (key != null && !key.isEmpty()) {
                c.setRequestProperty(keyHeader, key);
            }
            for (Map.Entry<String, String> h : headers.entrySet()) {
                c.setRequestProperty(h.getKey(), h.getValue());
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            List<String> cookies = new ArrayList<>();
            List<String> set = c.getHeaderFields().get("Set-Cookie");
            if (set == null) {
                set = c.getHeaderFields().get("set-cookie");
            }
            if (set != null) {
                for (String v : set) {
                    cookies.add(v.split(";", 2)[0].trim());
                }
            }
            String location = c.getHeaderField("Location");
            byte[] data = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            return new Response(code, data, cookies, location == null ? "" : location);
        } finally {
            c.disconnect();
        }
    }

    /**
     * The body as JSON text. A web page here usually means an auth layer in front of the API (Caddy
     * forward_auth).
     */
    private static String json(byte[] body) throws IOException {
        String s = text(body).trim();
        if (s.startsWith("<")) {
            throw new IOException(
                    "got a web page instead of JSON: is a login in front of this URL? Exempt /api"
                            + " in Caddy");
        }
        return s;
    }

    private static byte[] read(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = s.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) {
                    throw new IOException("response over " + (MAX_BYTES >> 20) + " MB");
                }
            }
            return out.toByteArray();
        }
    }

    private static String text(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String shorten(String s) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 160 ? one.substring(0, 160) + "…" : one;
    }

    /**
     * The error for a redirect: where it went, and the usual reason, a login in front of the API.
     */
    static String redirected(String location) {
        String where = location;
        try {
            String host = new URL(new URL("http://x/"), location).getHost();
            if (host != null && !host.isEmpty() && !host.equals("x")) {
                where = host;
            }
        } catch (java.net.MalformedURLException e) {
            where = location; // not a URL at all: name it as the server sent it
        }
        return "redirected"
                + (where.isEmpty() ? "" : " to " + where)
                + ": is a login in front of this URL? Exempt /api in Caddy, or use the service's"
                + " LAN address";
    }

    /** {@code part} as one URL query value. */
    static String encode(String part) {
        try {
            return java.net.URLEncoder.encode(part, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
