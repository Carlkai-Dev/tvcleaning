package com.gathin.tvhome;

import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Today's weather and the days ahead for the screensaver, from Open-Meteo (open-meteo.com: free, no account, key
 * or cookies). Only the place's coordinates go out, rounded to a tenth of a degree (about 10 km), under a plain
 * User-Agent instead of the one naming the TV model, and only when config.xml turns weather_card on; otherwise
 * the launcher never opens a connection. The last answer is kept in the launcher's
 * private files (weather.json), so the screensaver shows it at once and keeps it through a dropped connection.
 */
final class Weather {

    static final String FILE_NAME = "weather.json";
    /** How old the kept forecast may get before the screensaver asks again. */
    static final long REFRESH_MS = 60 * 60_000L;
    /** Older than this, the kept forecast is not shown at all. */
    static final long MAX_AGE_MS = 12 * 60 * 60_000L;
    /** An answer is a few kilobytes; anything far bigger is not one. */
    private static final int MAX_BYTES = 64 * 1024;

    enum Kind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, STORM }

    static final class Day {
        /** yyyy-MM-dd in the place's own time zone. */
        final String date;
        final int code;
        final int max;
        final int min;

        Day(String date, int code, int max, int min) {
            this.date = date;
            this.code = code;
            this.max = max;
            this.min = min;
        }
    }

    final int temperature;
    /** WMO weather code, as Open-Meteo reports it. */
    final int code;
    final boolean day;
    final List<Day> days;

    private Weather(int temperature, int code, boolean day, List<Day> days) {
        this.temperature = temperature;
        this.code = code;
        this.day = day;
        this.days = Collections.unmodifiableList(days);
    }

    /** The days from {@code today} (yyyy-MM-dd) on: a kept forecast from yesterday starts a day later. */
    List<Day> from(String today) {
        List<Day> out = new ArrayList<>();
        for (Day d : days) if (d.date.compareTo(today) >= 0) out.add(d);
        return out;
    }

    /** The request for these coordinates; rounding them here is what keeps the place approximate. */
    static String url(double latitude, double longitude) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.1f&longitude=%.1f"
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7",
                latitude, longitude);
    }

    static Kind kind(int code) {
        if (code == 0) return Kind.CLEAR;
        if (code == 1 || code == 2) return Kind.PARTLY_CLOUDY;
        if (code == 45 || code == 48) return Kind.FOG;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return Kind.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Kind.SNOW;
        if (code >= 95 && code <= 99) return Kind.STORM;
        return Kind.CLOUDY;
    }

    static int description(int code) {
        switch (code) {
            case 0: return R.string.weather_clear;
            case 1: return R.string.weather_mostly_clear;
            case 2: return R.string.weather_partly_cloudy;
            case 45: case 48: return R.string.weather_fog;
            case 51: case 53: case 55: case 56: case 57: return R.string.weather_drizzle;
            case 65: return R.string.weather_heavy_rain;
            case 80: case 81: case 82: return R.string.weather_showers;
            default: break;
        }
        switch (kind(code)) {
            case RAIN: return R.string.weather_rain;
            case SNOW: return R.string.weather_snow;
            case STORM: return R.string.weather_storm;
            default: return R.string.weather_cloudy;
        }
    }

    static Weather parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject current = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        JSONArray dates = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray max = daily.getJSONArray("temperature_2m_max");
        JSONArray min = daily.getJSONArray("temperature_2m_min");
        List<Day> days = new ArrayList<>();
        for (int i = 0; i < dates.length(); i++) {
            if (codes.isNull(i) || max.isNull(i) || min.isNull(i)) continue;
            days.add(new Day(dates.getString(i), codes.getInt(i),
                    (int) Math.round(max.getDouble(i)), (int) Math.round(min.getDouble(i))));
        }
        return new Weather((int) Math.round(current.getDouble("temperature_2m")), current.getInt("weather_code"),
                current.optInt("is_day", 1) == 1, days);
    }

    /** The kept forecast, or null when there is none or it is too old to show. */
    static Weather load(File file, long now) {
        if (!file.isFile() || now - file.lastModified() > MAX_AGE_MS) return null;
        try {
            return parse(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Asks Open-Meteo and keeps the answer. Blocks on the network: never on the main thread. */
    static Weather fetch(File file, double latitude, double longitude) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(url(latitude, longitude)).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "tvhome");
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            String body;
            try (InputStream in = c.getInputStream()) {
                body = read(in);
            }
            Weather weather = parse(body);   // only a valid answer replaces the kept one
            save(file, body);
            return weather;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) != -1; ) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("answer too large");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void save(File file, String body) throws IOException {
        AtomicFile af = new AtomicFile(file);
        FileOutputStream out = af.startWrite();
        try {
            out.write(body.getBytes(StandardCharsets.UTF_8));
            af.finishWrite(out);
        } catch (IOException e) {
            af.failWrite(out);
            throw e;
        }
    }
}
