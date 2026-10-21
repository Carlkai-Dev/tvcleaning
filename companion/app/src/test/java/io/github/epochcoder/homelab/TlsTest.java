package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Scanner;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Tls against a real HTTPS server presenting a chain shaped like Caddy's tls internal: site certificate for
 * localhost, then the intermediate, signed by a root the system does not know (fixtures in resources/tls).
 * The server is a few lines over an SSLServerSocket: unit tests compile against android.jar, which has no
 * com.sun.net.httpserver.
 */
public class TlsTest {
    private static SSLServerSocket server;
    private static int port;
    private static byte[] siteCert;

    @BeforeClass
    public static void startServer() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = resource("server.p12")) {
            ks.load(in, "test".toCharArray());
        }
        siteCert = ks.getCertificateChain("site")[0].getEncoded();
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "test".toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        server = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(0, 8, InetAddress.getLoopbackAddress());
        port = server.getLocalPort();
        Thread t = new Thread(TlsTest::serve, "tls-test-server");
        t.setDaemon(true);
        t.start();
    }

    /** Answers every request with 200 and "[]"; a failed handshake just ends that connection. */
    private static void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                String request = in.readLine();
                for (String line = request; line != null && !line.isEmpty(); ) line = in.readLine();
                boolean head = request != null && request.startsWith("HEAD ");
                OutputStream out = s.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\n"
                        + "Connection: close\r\n\r\n" + (head ? "" : "[]")).getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException ignored) {
            }
        }
    }

    @AfterClass
    public static void stopServer() throws IOException {
        if (server != null) server.close();
    }

    private static InputStream resource(String name) {
        InputStream in = TlsTest.class.getClassLoader().getResourceAsStream("tls/" + name);
        assertNotNull("missing fixture tls/" + name, in);
        return in;
    }

    private static String pem(String name) {
        try (Scanner s = new Scanner(resource(name), "US-ASCII").useDelimiter("\\A")) {
            return s.next();
        }
    }

    private static String get(SSLSocketFactory tls, String host) throws IOException {
        return new Http(tls, "x-api-key", "k").get("https://" + host + ":" + port + "/");
    }

    @Test
    public void trustsTheConfiguredRoot() throws Exception {
        assertEquals("[]", get(Tls.socketFactory(pem("root.pem")), "localhost"));
    }

    @Test
    public void refusesWithoutTheRoot() {
        for (SSLSocketFactory tls : new SSLSocketFactory[]{null, Tls.socketFactory(pem("other-root.pem"))}) {
            try {
                get(tls, "localhost");
                fail("connected without trusting the server's root");
            } catch (IOException expected) {
            }
        }
    }

    @Test
    public void stillChecksTheHostname() {
        try {
            // The certificate names localhost, not 127.0.0.1: trusting the root must not trust every name.
            get(Tls.socketFactory(pem("root.pem")), "127.0.0.1");
            fail("accepted a certificate for another name");
        } catch (IOException expected) {
        }
    }

    @Test
    public void sameCertificateConfirmsWhatTheWebViewSaw() throws Exception {
        SSLSocketFactory tls = Tls.socketFactory(pem("root.pem"));
        String url = "https://localhost:" + port + "/";
        assertTrue(Tls.sameCertificate(url, tls, siteCert));
        byte[] other = siteCert.clone();
        other[other.length - 1] ^= 1;
        assertFalse(Tls.sameCertificate(url, tls, other));
        assertFalse(Tls.sameCertificate(url, Tls.socketFactory(pem("other-root.pem")), siteCert));
        assertFalse(Tls.sameCertificate(url, null, siteCert));
    }

    @Test
    public void unusablePemTrustsSystemCasOnly() {
        assertNull(Tls.socketFactory(""));
        assertNull(Tls.socketFactory(null));
        assertNull(Tls.socketFactory("-----BEGIN CERTIFICATE-----\nnope\n-----END CERTIFICATE-----\n"));
    }
}
