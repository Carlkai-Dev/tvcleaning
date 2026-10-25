package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The header's weather line (config.xml weather): current temperature, today's high and low and a sky symbol,
 * from Open-Meteo (free, no key; the request carries only the configured coordinates). Fetched off the UI thread
 * at most every 30 minutes; the last answer is kept, so Home shows it at once and while the network is down.
 */
final class Weather {

    private static final long REFRESH_MS = 30 * 60 * 1000L;
    private static final String PREFS = "weather";

    private final Context ctx;
    private final String label;
    private final String lat;
    private final String lon;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean fetching;

    /** label, lat and lon: the place to show (Config.weatherLabel and its coordinates). */
    Weather(Context ctx, String label, String lat, String lon) {
        this.ctx = ctx.getApplicationContext();
        this.label = label;
        this.lat = lat;
        this.lon = lon;
    }

    /** Shows the cached line right away, then fetches a fresh one if the cache is old. */
    void update(Consumer<CharSequence> show) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // The cache belongs to one place: a theme that moves the weather elsewhere fetches at once.
        String place = lat + "," + lon;
        boolean samePlace = place.equals(p.getString("place", null));
        String cached = samePlace ? p.getString("json", null) : null;
        if (cached != null) {
            CharSequence line = format(cached);
            if (line != null) show.accept(line);
        }
        if (fetching || samePlace && System.currentTimeMillis() - p.getLong("at", 0L) < REFRESH_MS) return;
        fetching = true;
        new Thread(() -> {
            String json = fetch();
            main.post(() -> {
                fetching = false;
                if (json == null) return;
                CharSequence line = format(json);
                if (line == null) return;
                p.edit().putString("json", json).putString("place", place).putLong("at", System.currentTimeMillis()).apply();
                show.accept(line);
            });
        }, "weather").start();
    }

    private String fetch() {
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code,is_day"
                    + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1");
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** "16°C  · Ciudad de México (19° / 13°)", the temperature large and bold; null for an answer it cannot read. */
    private CharSequence format(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject now = o.getJSONObject("current");
            JSONObject daily = o.getJSONObject("daily");
            long temp = Math.round(now.getDouble("temperature_2m"));
            long max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
            long min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
            // As in the preview: no sky symbol, the temperature where it was. Fork de Natalia (estilo Pixel): un
            // ícono a color según el clima y si es de día o de noche, como el clima del Pixel.
            SpannableStringBuilder s = new SpannableStringBuilder();
            if (AppAdapter.pixelStyle) appendIcon(s, now.optInt("weather_code", 0), now.optInt("is_day", 1) == 1);
            int start = s.length();
            s.append(String.format(Locale.ROOT, "%d°C", temp));
            s.setSpan(new AbsoluteSizeSpan(18, true), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(Typeface.BOLD), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(0xFFFFFFFF), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.append(String.format(Locale.ROOT, "  · %s (%d° / %d°)", label, max, min));
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** The colored icon for a WMO weather code (Open-Meteo's weather_code), day or night. */
    static int icon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
        if (code <= 2) return day ? R.drawable.wx_partly_cloudy_day : R.drawable.wx_partly_cloudy_night;
        if (code == 3) return R.drawable.wx_cloud;                                          // overcast
        if (code == 45 || code == 48) return R.drawable.wx_foggy;                           // fog
        if (code >= 95) return R.drawable.wx_thunderstorm;                                  // thunderstorm
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snowy;   // snow
        return R.drawable.wx_rainy;                                                         // drizzle, rain, showers
    }

    /** The icon, as tall as the 18sp temperature beside it, then a space. */
    private void appendIcon(SpannableStringBuilder s, int code, boolean day) {
        Drawable d = ctx.getDrawable(icon(code, day));
        if (d == null) return;
        int size = Math.round(22 * ctx.getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, size, size);
        int align = Build.VERSION.SDK_INT >= 29 ? DynamicDrawableSpan.ALIGN_CENTER : DynamicDrawableSpan.ALIGN_BASELINE;
        s.append("\u00A0");
        s.setSpan(new ImageSpan(d, align), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append("  ");
    }
}
