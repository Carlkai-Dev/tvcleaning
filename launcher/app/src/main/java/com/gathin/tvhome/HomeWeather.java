package com.gathin.tvhome;

import android.os.Handler;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Weather and air quality for the top right of Home, from Open-Meteo (no key, no account), as the Weather add-on
 * gets them. One fetch at most every 30 minutes, and only when Home asks, which it does while it is on screen; a
 * reading older than three hours, or none at all, hides the block instead of showing stale numbers.
 */
final class HomeWeather {

    static final long REFRESH_MS = 30 * 60_000L;
    static final long STALE_MS = 3 * 60 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;
    /** US AQI from here up gets the advice line. */
    static final int POOR_AIR = 150;

    interface Listener {
        /** The three lines, or null to hide the block. */
        void onWeather(String[] lines);
    }

    private final String city;
    private final String where;
    private long lastTry;
    private boolean busy;
    private int code = -1, aqi = -1;
    private boolean day;
    private double tempC;
    private long readAt;

    HomeWeather(String city, String latitude, String longitude) {
        this.city = city;
        this.where = "latitude=" + latitude + "&longitude=" + longitude;
    }

    /** Shows what it has now, and fetches in the background when the last try is 30 minutes old. */
    void refresh(Handler handler, Listener listener) {
        listener.onWeather(lines());
        long now = SystemClock.elapsedRealtime();
        if (busy || (lastTry != 0 && now - lastTry < REFRESH_MS)) return;
        busy = true;
        lastTry = now;
        new Thread(() -> {
            int c = -1, a = -1;
            boolean d = true;
            double t = 0;
            try {
                // icon_seamless, as the Weather add-on: the default best_match model had Mumbai 2° cool and drizzling
                // on a clear afternoon.
                JSONObject cur = get("https://api.open-meteo.com/v1/forecast?" + where + "&models=icon_seamless"
                        + "&current=temperature_2m,weather_code,is_day&timezone=auto").getJSONObject("current");
                c = cur.getInt("weather_code");
                d = cur.optInt("is_day", 1) == 1;
                t = cur.getDouble("temperature_2m");
                try {
                    JSONObject air = get("https://air-quality-api.open-meteo.com/v1/air-quality?" + where
                            + "&current=us_aqi").getJSONObject("current");
                    a = air.isNull("us_aqi") ? -1 : air.getInt("us_aqi");
                } catch (IOException | JSONException ignored) {
                    // Weather without air quality is still worth showing.
                }
            } catch (IOException | JSONException e) {
                c = -1;
            }
            int fc = c, fa = a;
            boolean fd = d;
            double ft = t;
            handler.post(() -> {
                busy = false;
                if (fc >= 0) {
                    code = fc;
                    aqi = fa;
                    day = fd;
                    tempC = ft;
                    readAt = SystemClock.elapsedRealtime();
                }
                listener.onWeather(lines());
            });
        }, "home-weather").start();
    }

    private String[] lines() {
        if (code < 0 || SystemClock.elapsedRealtime() - readAt > STALE_MS) return null;
        return format(code, day, tempC, aqi, city);
    }

    /** {"☁  Cloudy, Mumbai", "29° · Air quality 156 Unhealthy", advice or "", and the screensaver's "☁  29°  Cloudy, Mumbai"}. */
    static String[] format(int code, boolean day, double tempC, int aqi, String city) {
        String top = icon(code, day) + "  " + sky(code, day) + ", " + city;
        String temp = String.format(Locale.ROOT, "%d°", Math.round(tempC));
        String mid = aqi < 0 ? temp : temp + " · Air quality " + aqi + " " + aqiLabel(aqi);
        String advice = aqi >= POOR_AIR ? "Poor air · keep windows closed" : "";
        String one = icon(code, day) + "  " + temp + "  " + sky(code, day) + ", " + city;
        return new String[]{top, mid, advice, one};
    }

    static String aqiLabel(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Poor";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    /** WMO weather codes, in words. */
    static String sky(int code, boolean day) {
        switch (code) {
            case 0: return day ? "Sunny" : "Clear";
            case 1: return day ? "Mostly sunny" : "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: case 56: case 57: return "Drizzle";
            case 61: return "Light rain";
            case 63: case 66: return "Rain";
            case 65: case 67: return "Heavy rain";
            case 80: case 81: case 82: return "Showers";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return code >= 71 && code <= 86 ? "Snow" : "Haze";
        }
    }

    private static String icon(int code, boolean day) {
        if (code <= 1) return day ? "☀" : "☾";
        if (code == 2) return "⛅";
        if (code == 3) return "☁";
        if (code == 45 || code == 48) return "🌫";
        if (code >= 95) return "⛈";
        return "🌧";
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }
}
