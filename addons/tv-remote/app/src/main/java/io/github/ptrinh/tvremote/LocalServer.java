package io.github.ptrinh.tvremote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LAN mode: the phone page and the relay's API served by the TV itself, over plain HTTP on the home network,
 * for when there is no relay yet (and beside the relay, for a phone that opens the LAN address). The page is
 * the relay's own (relay/src, copied into the APK's assets at build time), and it works unchanged: relative
 * fetch paths, and its WebSocket at location.host. Routes as in relay/src/index.js: GET / and the manifest,
 * icons, font and sw.js; POST /api/status and /api/cmd (the TV id and phone key in the body, 403 for a wrong
 * key); /api/push and /api/vapid say notifications need the relay; GET /phone is the live socket (auth message,
 * status pushes, live view on/off and the JPEG frames, ping/pong).
 *
 * Bound to every interface, but only private, link-local and loopback addresses get an answer. One accept
 * thread, a small fixed pool for the connections (a live socket holds one), one request per connection,
 * limits on size and time everywhere ({@link LanHttp}). Commands run through {@link RemoteRelay#ask}, the same
 * code as the relay's.
 */
final class LocalServer {
    static final String TAG = "TvRemoteLan";
    /** The port in the QR code's address. */
    static final int PORT = 8781;
    private static final int THREADS = 8;
    /** Live sockets at once; each holds a pool thread (the oldest goes when another comes). */
    private static final int MAX_PHONES = 4;
    private static final int REQUEST_TIMEOUT_MS = 10_000;
    /** The page pings every 30 s. */
    private static final int SOCKET_TIMEOUT_MS = 100_000;
    private static final int MAX_MESSAGE = 4096;
    private static final long ANSWER_MS = 10_000;
    private static final int MAX_DATA = 256 * 1024;
    private static final int MAX_LAN_FPS = 30;

    private static Context app;
    private static volatile boolean running;
    private static ExecutorService pool;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Set<Phone> phones = new CopyOnWriteArraySet<>();
    private static final Map<String, byte[]> assets = new ConcurrentHashMap<>();

    private LocalServer() {
    }

    /** Once per process. */
    static synchronized void start(Context ctx) {
        if (app != null) return;
        app = ctx.getApplicationContext();
        RemoteRelay.init(app);
        ThreadPoolExecutor p = new ThreadPoolExecutor(THREADS, THREADS, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16), daemon("lan-conn"));
        p.allowCoreThreadTimeOut(true);
        pool = p;
        Thread t = daemon("lan-accept").newThread(LocalServer::acceptLoop);
        t.start();
    }

    /** Listening on the port. */
    static boolean running() {
        return running;
    }

    /** The TV's address on the home network (IPv4, Wi-Fi or Ethernet), or null when it has none. */
    static String lanAddress() {
        try {
            String found = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || !LanHttp.local(a.getAddress()) || a.isLoopbackAddress()) continue;
                    String name = ni.getName();
                    // Wi-Fi and Ethernet first; anything else private (a VPN's tun has no broadcast) only if alone.
                    if (name.startsWith("wlan") || name.startsWith("eth")) return a.getHostAddress();
                    if (found == null) found = a.getHostAddress();
                }
            }
            return found;
        } catch (Exception e) {
            return null;
        }
    }

    private static void acceptLoop() {
        while (true) {
            try (ServerSocket s = new ServerSocket()) {
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(PORT), 16);
                running = true;
                Log.i(TAG, "listening on " + PORT);
                while (true) {
                    Socket c = s.accept();
                    if (!LanHttp.local(c.getInetAddress().getAddress())) {
                        // Not the home network: no answer at all.
                        closeQuietly(c);
                        continue;
                    }
                    try {
                        pool.execute(() -> handle(c));
                    } catch (RejectedExecutionException e) {
                        closeQuietly(c);
                    }
                }
            } catch (IOException e) {
                running = false;
                Log.w(TAG, "server stopped: " + e);
            }
            try {
                Thread.sleep(5000);   // port taken, or the network went: try again
            } catch (InterruptedException ignored) {
                // Again now.
            }
        }
    }

    // ---- HTTP ----------------------------------------------------------------------------------------

    private static void handle(Socket c) {
        boolean keep = false;
        try {
            c.setSoTimeout(REQUEST_TIMEOUT_MS);
            c.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream out = new BufferedOutputStream(c.getOutputStream());
            LanHttp.Request r;
            try {
                r = LanHttp.parseHead(LanHttp.readHead(in));
                if ("/phone".equals(r.path)) {
                    keep = upgrade(c, r, in, out);
                    return;
                }
                route(r, in, out);
            } catch (LanHttp.BadRequest e) {
                text(out, e.status, e.getMessage(), false);
            }
        } catch (IOException e) {
            // The phone went away.
        } catch (Exception e) {
            Log.w(TAG, "request failed: " + e);
        } finally {
            if (!keep) closeQuietly(c);
        }
    }

    private static void route(LanHttp.Request r, InputStream in, OutputStream out) throws Exception {
        String path = r.path;
        boolean head = r.method.equals("HEAD");
        if (path.equals("/")) {
            if (r.method.equals("POST")) throw new LanHttp.BadRequest(405, "method not allowed");
            String page = LanHttp.page(new String(asset("page.html"), StandardCharsets.UTF_8), r.query.get("p"));
            respond(out, 200, "text/html; charset=utf-8", "no-store",
                    "Content-Security-Policy: " + LanHttp.CSP + "\r\nReferrer-Policy: no-referrer\r\n",
                    page.getBytes(StandardCharsets.UTF_8), head);
            return;
        }
        if (path.equals("/manifest.webmanifest")) {
            String p = r.query.get("p");
            respond(out, 200, "application/manifest+json", LanHttp.validPairing(p) ? "private, no-store" : "public, max-age=3600",
                    "", LanHttp.manifest(p).getBytes(StandardCharsets.UTF_8), head);
            return;
        }
        String[] a = LanHttp.asset(path);
        if (a != null) {
            String cache = a[0].equals("sw.js") ? "no-cache" : a[0].equals("outfit.ttf") ? "public, max-age=2592000, immutable" : "public, max-age=86400";
            respond(out, 200, a[1], cache, "", asset(a[0]), head);
            return;
        }
        if (path.equals("/api/vapid")) {
            json(out, 200, new JSONObject().put("key", ""));
            return;
        }
        if (path.equals("/api/cmd") || path.equals("/api/status") || path.equals("/api/push")) {
            if (!r.method.equals("POST")) {
                json(out, 405, new JSONObject().put("ok", false).put("error", "POST only"));
                return;
            }
            api(path, LanHttp.readBody(r, in), out);
            return;
        }
        text(out, 404, "not found", head);
    }

    private static void api(String path, String body, OutputStream out) throws Exception {
        JSONObject b;
        try {
            b = new JSONObject(body);
        } catch (Exception e) {
            json(out, 400, new JSONObject().put("ok", false).put("error", "bad json"));
            return;
        }
        String id = b.optString("id", ""), key = b.optString("key", "");
        if (!LanHttp.ID.matcher(id).matches() || !LanHttp.KEY.matcher(key).matches()) {
            json(out, 400, new JSONObject().put("ok", false).put("error", "not paired"));
            return;
        }
        if (!LanHttp.paired(id, key, RemoteRelay.id(app), RemoteRelay.phoneHash(app))) {
            json(out, 403, new JSONObject().put("ok", false).put("error", "not paired"));
            return;
        }
        if (path.equals("/api/push")) {
            json(out, 200, new JSONObject().put("ok", false).put("msg", "Notifications need the relay."));
            return;
        }
        if (path.equals("/api/status")) {
            json(out, 200, statusJson(null).put("ok", true));
            return;
        }
        Object t = b.opt("text");
        String text = t instanceof String ? ((String) t).trim() : "";
        Object a = b.has("args") ? b.opt("args") : null;
        boolean isObject = a == null || a instanceof JSONObject;
        String argsJson = a instanceof JSONObject ? a.toString() : null;
        String cmd = b.opt("cmd") instanceof String ? b.optString("cmd") : "";
        Object[] bad = LanHttp.checkCommand(cmd, text, argsJson, isObject);
        if (bad != null) {
            json(out, (Integer) bad[0], new JSONObject().put("ok", false).put("error", bad[1]));
            return;
        }
        JSONObject ack = RemoteRelay.ask(cmd, text, a instanceof JSONObject ? (JSONObject) a : new JSONObject(), ANSWER_MS);
        if (ack == null) {
            json(out, 503, new JSONObject().put("ok", false).put("offline", true).put("msg", "The TV did not answer."));
            return;
        }
        if (ack.has("data") && String.valueOf(ack.get("data")).length() > MAX_DATA) ack.put("data", JSONObject.NULL);
        json(out, 200, ack);
    }

    /** The relay's status fields: here the TV is always online, and seen now. */
    private static JSONObject statusJson(String power) throws Exception {
        if (power == null) power = RemoteRelay.interactive() ? "on" : "off";
        return new JSONObject()
                .put("online", true)
                .put("name", RemoteRelay.tvName())
                .put("lastSeen", System.currentTimeMillis())
                .put("state", TvLock.state(app))
                .put("power", power);
    }

    private static byte[] asset(String name) throws IOException {
        byte[] b = assets.get(name);
        if (b != null) return b;
        try (InputStream in = app.getAssets().open(name)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            b = o.toByteArray();
        }
        assets.put(name, b);
        return b;
    }

    private static void json(OutputStream out, int status, JSONObject o) throws IOException {
        respond(out, status, "application/json", "no-store", "", o.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    private static void text(OutputStream out, int status, String s, boolean head) throws IOException {
        respond(out, status, "text/plain; charset=utf-8", "no-store", "", s.getBytes(StandardCharsets.UTF_8), head);
    }

    private static void respond(OutputStream out, int status, String type, String cache, String extra, byte[] body, boolean head)
            throws IOException {
        String h = "HTTP/1.1 " + status + " " + LanHttp.reason(status) + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: " + cache + "\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + extra
                + "Connection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.ISO_8859_1));
        if (!head) out.write(body);
        out.flush();
    }

    // ---- the live socket -----------------------------------------------------------------------------

    /** One phone's /phone socket. Writes go through its own thread, so a slow phone holds up nobody. */
    private static final class Phone {
        final Socket socket;
        final OutputStream out;
        final String id;
        final ExecutorService writer = Executors.newSingleThreadExecutor(daemon("lan-write"));
        final AtomicBoolean frameBusy = new AtomicBoolean();
        volatile boolean auth, live, closed;
        volatile int fps = 2;

        Phone(Socket socket, OutputStream out, String id) {
            this.socket = socket;
            this.out = out;
            this.id = id;
        }

        void send(byte[] frame) {
            if (closed) return;
            try {
                writer.execute(() -> write(frame));
            } catch (RejectedExecutionException ignored) {
                // Closing.
            }
        }

        void text(JSONObject m) {
            send(LanHttp.frame(0x1, m.toString().getBytes(StandardCharsets.UTF_8)));
        }

        boolean wantsFrame() {
            return auth && live && !closed && !frameBusy.get();
        }

        /** Dropped when the last one is still going out. */
        void sendFrame(byte[] frame) {
            if (!auth || !live || closed || !frameBusy.compareAndSet(false, true)) return;
            try {
                writer.execute(() -> {
                    try {
                        write(frame);
                    } finally {
                        frameBusy.set(false);
                    }
                });
            } catch (RejectedExecutionException e) {
                frameBusy.set(false);
            }
        }

        private void write(byte[] frame) {
            try {
                out.write(frame);
                out.flush();
            } catch (IOException e) {
                close();
            }
        }

        /** Closes once what is queued has gone out. */
        void closeAfterWrites() {
            try {
                writer.execute(this::close);
            } catch (RejectedExecutionException e) {
                close();
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            writer.shutdown();
            closeQuietly(socket);
        }
    }

    /** The WebSocket handshake, then the phone's messages until it goes. True: the socket is in use. */
    private static boolean upgrade(Socket c, LanHttp.Request r, InputStream in, OutputStream out) throws Exception {
        String key = r.header("sec-websocket-key");
        if (!r.method.equals("GET") || !r.websocket() || key == null || key.isEmpty() || key.length() > 64) {
            text(out, 426, "websocket only", false);
            return false;
        }
        String id = r.query.get("id");
        if (id == null || !LanHttp.ID.matcher(id).matches()) {
            text(out, 400, "bad id", false);
            return false;
        }
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + LanHttp.wsAccept(key) + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        c.setSoTimeout(SOCKET_TIMEOUT_MS);
        Phone phone = new Phone(c, out, id);
        if (phones.size() >= MAX_PHONES) {
            Phone oldest = phones.iterator().next();
            phones.remove(oldest);
            oldest.close();
        }
        phones.add(phone);
        try {
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            while (!phone.closed) {
                LanHttp.Frame f = LanHttp.readFrame(in, MAX_MESSAGE);
                if (f.op == 0x8) {
                    phone.send(LanHttp.frame(0x8, new byte[0]));
                    break;
                }
                if (f.op == 0x9) {
                    phone.send(LanHttp.frame(0xA, f.data));
                    continue;
                }
                if (f.op != 0x1 && f.op != 0x0) continue;
                message.write(f.data);
                if (message.size() > MAX_MESSAGE) break;
                if (!f.fin) continue;
                String text = message.toString("UTF-8");
                message.reset();
                received(phone, text);
            }
        } catch (IOException e) {
            // Gone, or silent for too long.
        } finally {
            boolean wasLive = phone.live;
            phones.remove(phone);
            phone.close();
            if (wasLive) liveChanged();
        }
        return true;
    }

    private static void received(Phone phone, String text) throws Exception {
        if (text.equals("ping")) {
            phone.send(LanHttp.frame(0x1, "pong".getBytes(StandardCharsets.US_ASCII)));
            return;
        }
        JSONObject m;
        try {
            m = new JSONObject(text);
        } catch (Exception e) {
            return;
        }
        String t = m.optString("t");
        if (t.equals("auth")) {
            Object key = m.opt("key");
            if (key instanceof String && LanHttp.paired(phone.id, (String) key, RemoteRelay.id(app), RemoteRelay.phoneHash(app))) {
                phone.auth = true;
                phone.text(statusJson(null).put("t", "status"));
            } else {
                phone.text(new JSONObject().put("t", "unpaired"));
                phone.closeAfterWrites();
            }
        } else if (t.equals("live") && phone.auth) {
            phone.live = Boolean.TRUE.equals(m.opt("on"));
            phone.fps = Math.max(1, Math.min(MAX_LAN_FPS, m.optInt("fps", 2)));
            liveChanged();
        }
    }

    /** Tells LiveService whether a phone here watches, at the fastest rate any of them asked for. */
    private static void liveChanged() {
        int fps = 0;
        for (Phone p : phones) if (p.auth && p.live && !p.closed) fps = Math.max(fps, p.fps);
        final int f = fps;
        main.post(() -> LiveService.setLanWanted(app, f > 0, f > 0 ? f : 2));
    }

    /** The lock state or power changed (power "on"/"off", or null to read it): to every paired phone here. */
    static void status(String power) {
        if (app == null || phones.isEmpty()) return;
        try {
            JSONObject m = statusJson(power).put("t", "status");
            for (Phone p : phones) if (p.auth) p.text(m);
        } catch (Exception e) {
            Log.w(TAG, "status not sent: " + e);
        }
    }

    /** Some phone here is watching and ready for the next picture. */
    static boolean canSendFrame() {
        for (Phone p : phones) if (p.wantsFrame()) return true;
        return false;
    }

    /** One JPEG to every watching phone that is not still busy with the last one. */
    static void sendFrame(byte[] jpeg) {
        if (phones.isEmpty()) return;
        byte[] f = LanHttp.frame(0x2, jpeg);
        for (Phone p : phones) p.sendFrame(f);
    }

    static void liveError(String why) {
        try {
            JSONObject m = new JSONObject().put("t", "live_error").put("msg", why == null ? "" : why);
            for (Phone p : phones) if (p.auth && p.live) p.text(m);
        } catch (Exception ignored) {
            // Nobody to tell.
        }
    }

    /** A new phone key: the phones paired with the old one are told and dropped. */
    static void keyChanged() {
        for (Phone p : phones) {
            if (!p.auth) continue;
            try {
                p.text(new JSONObject().put("t", "unpaired"));
            } catch (Exception ignored) {
                // Closed below either way.
            }
            p.auth = false;
            p.send(LanHttp.frame(0x8, new byte[0]));
            p.closeAfterWrites();
        }
        liveChanged();
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // Gone.
        }
    }
}
