package com.dvopenlabs.weatheralerts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** One bounded request. No service, retries or work scheduled while the screen is closed. */
final class AlertsRequest {
    private volatile boolean cancelled;
    private volatile HttpsURLConnection connection;

    void cancel() {
        cancelled = true;
        HttpsURLConnection current = connection;
        if (current != null) current.disconnect();
    }

    String fetch(String area) throws IOException {
        if (Areas.index(area) < 0) throw new IOException("Invalid area");
        // Use the documented active endpoint; accept its redirect only within the same HTTPS API.
        URL url = new URL("https://api.weather.gov/alerts/active?area=" + area);
        for (int redirect = 0; redirect < 4; redirect++) {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            connection = conn;
            try {
                if (cancelled) throw new IOException("Cancelled");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "CleanYourTV-WeatherAlerts/1.0 (https://github.com/DVOpenLabs/cleanyourtv)");
                conn.setRequestProperty("Accept", "application/geo+json");
                int status = conn.getResponseCode();
                if (status == 301 || status == 302 || status == 307 || status == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect");
                    URL next = new URL(url, location);
                    if (!"https".equals(next.getProtocol()) || !"api.weather.gov".equals(next.getHost())
                            || next.getUserInfo() != null || next.getPort() != -1 && next.getPort() != 443)
                        throw new IOException("Unexpected redirect");
                    url = next;
                    continue;
                }
                if (status != 200) throw new IOException("Feed unavailable (HTTP " + status + ")");
                if (conn.getContentLengthLong() > Alerts.MAX_BYTES) throw new IOException("Feed too large");
                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                        if (out.size() + count > Alerts.MAX_BYTES) throw new IOException("Feed too large");
                        out.write(buffer, 0, count);
                    }
                    return new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            } finally {
                conn.disconnect();
                connection = null;
            }
        }
        throw new IOException("Too many redirects");
    }
}
