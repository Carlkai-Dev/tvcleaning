package io.github.epochcoder.homelab;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A scripted HTTP/1.1 server on loopback for the client tests: every request is recorded and
 * answered by the handler. One request per connection, which is all HttpURLConnection needs when
 * told "Connection: close".
 */
final class FakeServer implements AutoCloseable {
    static final class Request {
        final String method;

        /** Path and query, as sent. */
        final String path;

        /** Header names in lower case. */
        final Map<String, String> headers;

        final String body;

        Request(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }

    static final class Reply {
        final int code;
        final String body;
        final Map<String, String> headers = new LinkedHashMap<>();

        Reply(int code, String body) {
            this.code = code;
            this.body = body;
        }

        Reply header(String name, String value) {
            headers.put(name, value);
            return this;
        }
    }

    interface Handler {
        Reply handle(Request r);
    }

    static Reply ok(String body) {
        return new Reply(200, body);
    }

    interface Call {
        void run() throws java.io.IOException;
    }

    /** The message of the IOException {@code call} must throw. */
    static String failure(Call call) {
        try {
            call.run();
        } catch (java.io.IOException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected an IOException");
    }

    final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private final ServerSocket socket;
    private final Handler handler;

    FakeServer(Handler handler) throws IOException {
        this.handler = handler;
        socket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        Thread t = new Thread(this::serve, "fake-server");
        t.setDaemon(true);
        t.start();
    }

    String url() {
        return "http://127.0.0.1:" + socket.getLocalPort();
    }

    /** The recorded requests whose path starts with {@code prefix}. */
    List<Request> to(String prefix) {
        List<Request> out = new ArrayList<>();
        synchronized (requests) {
            for (Request r : requests) {
                if (r.path.startsWith(prefix)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    /** The last exchange that ended in an exception, if any. */
    volatile Exception lastError;

    private void serve() {
        while (!socket.isClosed()) {
            try (Socket s = socket.accept()) {
                InputStream in = new BufferedInputStream(s.getInputStream());
                String[] start = line(in).split(" ", -1);
                Map<String, String> headers = new LinkedHashMap<>();
                for (String h = line(in); !h.isEmpty(); h = line(in)) {
                    int colon = h.indexOf(':');
                    if (colon > 0) {
                        headers.put(
                                h.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                                h.substring(colon + 1).trim());
                    }
                }
                int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
                byte[] body = new byte[length];
                for (int n = 0; n < length; ) {
                    int r = in.read(body, n, length - n);
                    if (r < 0) {
                        break;
                    }
                    n += r;
                }
                Request req =
                        new Request(
                                start[0],
                                start[1],
                                headers,
                                new String(body, StandardCharsets.UTF_8));
                requests.add(req);
                Reply reply = handler.handle(req);
                byte[] out = reply.body.getBytes(StandardCharsets.UTF_8);
                StringBuilder head =
                        new StringBuilder("HTTP/1.1 " + reply.code + " X\r\n")
                                .append("Content-Length: ")
                                .append(out.length)
                                .append("\r\nConnection: close\r\n");
                for (Map.Entry<String, String> h : reply.headers.entrySet()) {
                    head.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
                }
                OutputStream o = s.getOutputStream();
                o.write(head.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII));
                o.write(out);
                o.flush();
            } catch (IOException | RuntimeException e) {
                // A client that hangs up mid-exchange (or the socket closing at shutdown) ends only
                // that exchange; the loop goes on to the next one, or stops once the socket is
                // closed. Kept for a debugger looking at a failing test.
                lastError = e;
            }
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int c = in.read(); c >= 0 && c != '\n'; c = in.read()) {
            if (c != '\r') {
                b.write(c);
            }
        }
        return b.toString(StandardCharsets.US_ASCII.name());
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
