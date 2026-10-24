package io.github.ptrinh.tvremote;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The plain-Java half of LAN mode ({@link LocalServer}): reading an HTTP/1.1 request with hard limits, who may
 * get an answer (private addresses only), the pairing check, the same command checks as the relay
 * (relay/src/index.js), the manifest, and WebSocket framing on the server side. Nothing here touches Android,
 * so it is tested on the JVM. Every byte that arrives is treated as hostile: anything past a limit or out of
 * shape is a {@link BadRequest}. Request parsing is ported from the launcher fork's WebHttp.
 */
final class LanHttp {
    static final int MAX_HEAD = 8 * 1024;
    static final int MAX_BODY = 8 * 1024;
    static final int MAX_PARAMS = 32;
    static final int MAX_HEADERS = 64;
    static final int MAX_TEXT = 500;
    static final int MAX_ARGS = 3000;

    /** The same shapes the relay accepts. */
    static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{12,32}");
    static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{22,64}");
    static final Pattern CMD = Pattern.compile("[a-z][a-z0-9_.-]{0,40}");
    static final Pattern PAIRING = Pattern.compile("[A-Za-z0-9_-]{12,32}[.][A-Za-z0-9_-]{22,64}");

    private LanHttp() {
    }

    static final class BadRequest extends Exception {
        final int status;

        BadRequest(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    static final class Request {
        final String method;
        final String path;
        final Map<String, String> query;
        /** Header names in lower case. */
        final Map<String, String> headers;

        Request(String method, String path, Map<String, String> query, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
        }

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.US));
        }

        /** Content-Length, -1 when absent; a malformed or negative one is a bad request. */
        long contentLength() throws BadRequest {
            String v = header("content-length");
            if (v == null) return -1;
            if (v.isEmpty() || v.length() > 12 || !v.chars().allMatch(Character::isDigit)) {
                throw new BadRequest(400, "bad Content-Length");
            }
            return Long.parseLong(v);
        }

        boolean websocket() {
            String u = header("upgrade");
            return u != null && u.equalsIgnoreCase("websocket");
        }
    }

    /** Reads up to the blank line that ends the head, never more than {@link #MAX_HEAD} bytes. */
    static String readHead(InputStream in) throws IOException, BadRequest {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        int state = 0;   // how much of \r\n\r\n has been seen
        while (true) {
            int b = in.read();
            if (b < 0) throw new BadRequest(400, "connection closed in the head");
            out.write(b);
            if (out.size() > MAX_HEAD) throw new BadRequest(431, "head too large");
            if (b == '\r' && (state == 0 || state == 2)) state++;
            else if (b == '\n' && (state == 1 || state == 3)) state++;
            else state = b == '\r' ? 1 : 0;
            if (state == 4) break;
        }
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    /** Parses a request head: GET, HEAD or POST, an origin-form target, HTTP/1.0 or 1.1, simple headers. */
    static Request parseHead(String head) throws BadRequest {
        String[] lines = head.split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length != 3) throw new BadRequest(400, "bad request line");
        String method = first[0];
        if (!method.equals("GET") && !method.equals("POST") && !method.equals("HEAD")) throw new BadRequest(405, "method not allowed");
        if (!first[2].equals("HTTP/1.1") && !first[2].equals("HTTP/1.0")) throw new BadRequest(505, "version");
        String target = first[1];
        if (!target.startsWith("/") || target.length() > 2048) throw new BadRequest(400, "bad target");
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        Map<String, String> query = parseQuery(q < 0 ? "" : target.substring(q + 1));
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon <= 0) throw new BadRequest(400, "bad header");
            if (headers.size() >= MAX_HEADERS) throw new BadRequest(431, "too many headers");
            String name = line.substring(0, colon).trim().toLowerCase(Locale.US);
            headers.put(name, line.substring(colon + 1).trim());
        }
        if (headers.containsKey("transfer-encoding")) throw new BadRequest(501, "no chunked bodies");
        return new Request(method, path, query, headers);
    }

    /** a=1&b=two%20words; at most {@link #MAX_PARAMS} pairs, later duplicates ignored. */
    static Map<String, String> parseQuery(String s) throws BadRequest {
        Map<String, String> out = new LinkedHashMap<>();
        if (s.isEmpty()) return out;
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            if (out.size() >= MAX_PARAMS) throw new BadRequest(400, "too many parameters");
            int eq = pair.indexOf('=');
            String k = decode(eq < 0 ? pair : pair.substring(0, eq));
            String v = decode(eq < 0 ? "" : pair.substring(eq + 1));
            if (!out.containsKey(k)) out.put(k, v);
        }
        return out;
    }

    private static String decode(String s) throws BadRequest {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            throw new BadRequest(400, "bad escape");
        }
    }

    /** The body of a POST: Content-Length required, at most {@link #MAX_BODY} bytes, UTF-8. */
    static String readBody(Request r, InputStream in) throws IOException, BadRequest {
        long len = r.contentLength();
        if (len < 0) throw new BadRequest(411, "Content-Length required");
        if (len > MAX_BODY) throw new BadRequest(413, "too large");
        byte[] b = new byte[(int) len];
        for (int off = 0; off < b.length; ) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) throw new BadRequest(400, "body cut short");
            off += n;
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Whether a remote address may use the server: loopback, link-local and the private ranges. */
    static boolean local(byte[] addr) {
        if (addr == null) return false;
        if (addr.length == 4) {
            int a = addr[0] & 0xFF, b = addr[1] & 0xFF;
            return a == 10 || a == 127 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168)
                    || (a == 169 && b == 254);
        }
        if (addr.length == 16) {
            boolean loopback = true;
            for (int i = 0; i < 15; i++) loopback &= addr[i] == 0;
            if (loopback && addr[15] == 1) return true;
            int first = ((addr[0] & 0xFF) << 8) | (addr[1] & 0xFF);
            if ((first & 0xFFC0) == 0xFE80) return true;          // link-local
            if ((first & 0xFE00) == 0xFC00) return true;          // unique local
            boolean mapped = true;                                 // ::ffff:a.b.c.d
            for (int i = 0; i < 10; i++) mapped &= addr[i] == 0;
            if (mapped && (addr[10] & 0xFF) == 0xFF && (addr[11] & 0xFF) == 0xFF) {
                return local(new byte[]{addr[12], addr[13], addr[14], addr[15]});
            }
        }
        return false;
    }

    // ---- pairing and commands ------------------------------------------------------------------------

    /** This TV's id and the phone key whose SHA-256 (hex) is {@code phoneHash}; compared in constant time. */
    static boolean paired(String id, String key, String myId, String phoneHash) {
        if (id == null || key == null || myId == null || phoneHash == null) return false;
        if (!ID.matcher(id).matches() || !KEY.matcher(key).matches()) return false;
        return same(id, myId) & same(sha256(key), phoneHash);
    }

    static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static boolean validPairing(String p) {
        return p != null && PAIRING.matcher(p).matches();
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The relay's checks on a command before it reaches the TV: {status, error} to answer with, or null when it
     * may run. text is already trimmed; argsJson is the args object as JSON (null when absent), argsIsObject
     * false when "args" is there but not an object.
     */
    static Object[] checkCommand(String cmd, String text, String argsJson, boolean argsIsObject) {
        if (cmd == null || !CMD.matcher(cmd).matches()) return new Object[]{400, "bad command name"};
        if (text.length() > MAX_TEXT) return new Object[]{400, "text is at most 500 characters"};
        if (!argsIsObject) return new Object[]{400, "args is an object"};
        if (argsJson != null && argsJson.length() > MAX_ARGS) return new Object[]{413, "args too large"};
        if (cmd.equals("banner") && text.isEmpty()) return new Object[]{400, "Type a message first."};
        if (cmd.equals("set_pin") && !text.matches("[0-9]{4}")) return new Object[]{400, "The PIN is 4 digits."};
        if (cmd.equals("lock_timer") && !(text.matches("[0-9]{1,3}") && Integer.parseInt(text) >= 1 && Integer.parseInt(text) <= 720)) {
            return new Object[]{400, "The timer is 1 to 720 minutes."};
        }
        if (cmd.equals("set_psk") && text.length() > 64) return new Object[]{400, "PSK is at most 64 characters"};
        return null;
    }

    // ---- page and manifest ----------------------------------------------------------------------------

    /** The page; a paired one (/?p=<id>.<key>) links the manifest that starts the home-screen app paired. */
    static String page(String page, String p) {
        return validPairing(p) ? page.replace("href=\"/manifest.webmanifest\"", "href=\"/manifest.webmanifest?p=" + p + "\"") : page;
    }

    /** The relay's manifest; start_url "/?p=<id>.<key>" for a valid pairing. */
    static String manifest(String p) {
        String start = validPairing(p) ? "/?p=" + p : "/";
        return "{\"id\":\"/\",\"name\":\"TV Remote\",\"short_name\":\"TV Remote\",\"start_url\":\"" + start + "\","
                + "\"scope\":\"/\",\"display\":\"standalone\",\"background_color\":\"#0b0b0f\",\"theme_color\":\"#0b0b0f\","
                + "\"icons\":[{\"src\":\"/icon-192.png\",\"sizes\":\"192x192\",\"type\":\"image/png\",\"purpose\":\"any\"},"
                + "{\"src\":\"/icon-512.png\",\"sizes\":\"512x512\",\"type\":\"image/png\",\"purpose\":\"any\"},"
                + "{\"src\":\"/icon-512.png\",\"sizes\":\"512x512\",\"type\":\"image/png\",\"purpose\":\"maskable\"}]}";
    }

    /** The relay's page policy, with ws: for the live socket and nothing that asks for https. */
    static final String CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self' ws:; "
            + "img-src 'self' data: blob:; font-src 'self'; manifest-src 'self'; worker-src 'self'; base-uri 'none'; "
            + "form-action 'none'; frame-ancestors 'none'";

    /** The asset behind a path and its type, or null. */
    static String[] asset(String path) {
        switch (path) {
            case "/icon-512.png":
                return new String[]{"icon-512.png", "image/png"};
            case "/icon-192.png":
            case "/favicon.png":
            case "/favicon.ico":
                return new String[]{"icon-192.png", "image/png"};
            case "/apple-touch-icon.png":
            case "/apple-touch-icon-precomposed.png":
            case "/apple-touch-icon-180x180.png":
                return new String[]{"apple-touch-icon.png", "image/png"};
            case "/outfit.ttf":
                return new String[]{"outfit.ttf", "font/ttf"};
            case "/sw.js":
                return new String[]{"sw.js", "text/javascript"};
            default:
                return null;
        }
    }

    static String reason(int status) {
        switch (status) {
            case 101: return "Switching Protocols";
            case 200: return "OK";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 411: return "Length Required";
            case 413: return "Payload Too Large";
            case 426: return "Upgrade Required";
            case 431: return "Request Header Fields Too Large";
            case 501: return "Not Implemented";
            case 503: return "Service Unavailable";
            case 505: return "HTTP Version Not Supported";
            default: return "Error";
        }
    }

    // ---- WebSocket ------------------------------------------------------------------------------------

    static String wsAccept(String key) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** One server frame: final, unmasked. */
    static byte[] frame(int op, byte[] data) {
        ByteArrayOutputStream f = new ByteArrayOutputStream(data.length + 10);
        f.write(0x80 | op);
        if (data.length < 126) f.write(data.length);
        else if (data.length < 65536) {
            f.write(126);
            f.write(data.length >> 8);
            f.write(data.length & 0xff);
        } else {
            f.write(127);
            for (int i = 7; i >= 0; i--) f.write((int) ((long) data.length >>> (8 * i)) & 0xff);
        }
        f.write(data, 0, data.length);
        return f.toByteArray();
    }

    static final class Frame {
        final boolean fin;
        final int op;
        final byte[] data;

        Frame(boolean fin, int op, byte[] data) {
            this.fin = fin;
            this.op = op;
            this.data = data;
        }
    }

    /** One client frame: it must be masked (RFC 6455) and at most {@code max} bytes. */
    static Frame readFrame(InputStream in, int max) throws IOException {
        int b0 = in.read(), b1 = in.read();
        if (b0 < 0 || b1 < 0) throw new EOFException();
        if ((b1 & 0x80) == 0) throw new IOException("unmasked client frame");
        long len = b1 & 0x7f;
        if (len == 126) len = (byte1(in) << 8) | byte1(in);
        else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | byte1(in);
        }
        if (len < 0 || len > max) throw new IOException("frame too large");
        byte[] mask = {(byte) byte1(in), (byte) byte1(in), (byte) byte1(in), (byte) byte1(in)};
        byte[] data = new byte[(int) len];
        for (int off = 0; off < len; ) {
            int n = in.read(data, off, (int) len - off);
            if (n < 0) throw new EOFException();
            off += n;
        }
        for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
        return new Frame((b0 & 0x80) != 0, b0 & 0x0f, data);
    }

    private static int byte1(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException();
        return b;
    }
}
