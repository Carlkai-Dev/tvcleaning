package io.github.epochcoder.homelab;

import android.net.http.SslCertificate;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Trust for services behind a reverse proxy with its own CA, Caddy's {@code tls internal} being the usual
 * one. A TV has no practical way to install a user CA, so the root certificate travels in config.json
 * ({@code ca_pem}) and is trusted here in addition to, never instead of, the system CAs. Hostnames are still
 * verified by HttpsURLConnection. Proxies with a public certificate (Let's Encrypt over a DNS challenge)
 * need none of this.
 */
final class Tls {
    private static final String TAG = "Homelab.Tls";

    private Tls() {}

    /** A socket factory trusting the system CAs plus the certificates in {@code pem}, or null when pem is empty or broken. */
    static SSLSocketFactory socketFactory(String pem) {
        if (pem == null || pem.isEmpty()) return null;
        try {
            Collection<? extends Certificate> certs = CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            if (certs.isEmpty()) throw new CertificateException("no certificate in ca_pem");
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int i = 0;
            for (Certificate c : certs) store.setCertificateEntry("ca" + i++, c);
            X509TrustManager extra = trustManager(store);
            X509TrustManager system = trustManager(null);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new Either(system, extra)}, null);
            return ctx.getSocketFactory();
        } catch (Exception e) {
            Log.w(TAG, "ca_pem is not usable, trusting the system CAs only: " + e);
            return null;
        }
    }

    private static X509TrustManager trustManager(KeyStore store) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(store);
        for (TrustManager tm : f.getTrustManagers()) if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
        throw new IllegalStateException("no X509TrustManager");
    }

    /** Accepts a chain either trust manager accepts: the system's first, then the configured CA's. */
    private static final class Either implements X509TrustManager {
        private final X509TrustManager first;
        private final X509TrustManager second;

        Either(X509TrustManager first, X509TrustManager second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                first.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                second.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            first.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = first.getAcceptedIssuers();
            X509Certificate[] b = second.getAcceptedIssuers();
            X509Certificate[] all = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }
    }

    /**
     * True when {@code url}'s server passes a full TLS check with {@code tls} (chain to a trusted CA and the
     * hostname) and presents {@code leaf}. The WebView only knows the system CAs and only hands its error
     * handler the leaf, so DashboardActivity asks this before it proceeds past an SSL error. Blocking: call
     * it off the main thread.
     */
    static boolean sameCertificate(String url, SSLSocketFactory tls, byte[] leaf) {
        if (tls == null || leaf == null) return false;
        HttpsURLConnection c = null;
        try {
            c = (HttpsURLConnection) new URL(url).openConnection();
            c.setSSLSocketFactory(tls);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            c.setRequestMethod("HEAD");
            c.connect();
            Certificate[] served = c.getServerCertificates();
            return served.length > 0 && Arrays.equals(served[0].getEncoded(), leaf);
        } catch (IOException | CertificateException | ClassCastException e) {
            Log.w(TAG, "TLS check of " + url + " failed: " + e);
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** The DER bytes of a WebView certificate, or null. */
    static byte[] encoded(SslCertificate cert) {
        if (cert == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                X509Certificate x = cert.getX509Certificate();
                return x == null ? null : x.getEncoded();
            }
            // Before Android 10 the certificate is only reachable through its saved state.
            Bundle state = SslCertificate.saveState(cert);
            return state == null ? null : state.getByteArray("x509-certificate");
        } catch (CertificateException e) {
            return null;
        }
    }
}
