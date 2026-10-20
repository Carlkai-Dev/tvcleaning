package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LiveWeatherService {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS = "weather_cache";
    private static final long CACHE_DURATION = 30 * 60 * 1000L; // 30 minutes

    public interface Callback {
        void onWeatherUpdated(String weatherString, String city);
    }

    private static LiveWeatherService instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LiveWeatherService(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized LiveWeatherService getInstance(Context context) {
        if (instance == null) {
            instance = new LiveWeatherService(context);
        }
        return instance;
    }

    public void fetchWeather(Callback callback) {
        String cachedText = prefs.getString("weather_text", "");
        String cachedCity = prefs.getString("city", "");
        long lastFetch = prefs.getLong("last_fetch", 0);

        if (!cachedText.isEmpty() && (System.currentTimeMillis() - lastFetch < CACHE_DURATION)) {
            if (callback != null) callback.onWeatherUpdated(cachedText, cachedCity);
            return;
        }

        if (!cachedText.isEmpty() && callback != null) {
            callback.onWeatherUpdated(cachedText, cachedCity);
        }

        executor.execute(() -> {
            try {
                double lat = 28.6139;
                double lon = 77.2090;
                String city = "Delhi";

                try {
                    URL geoUrl = new URL("http://ip-api.com/json/?fields=city,lat,lon,status");
                    HttpURLConnection geoConn = (HttpURLConnection) geoUrl.openConnection();
                    geoConn.setConnectTimeout(3000);
                    geoConn.setReadTimeout(3000);
                    if (geoConn.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(geoConn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();
                        JSONObject geoObj = new JSONObject(sb.toString());
                        if ("success".equalsIgnoreCase(geoObj.optString("status"))) {
                            lat = geoObj.getDouble("lat");
                            lon = geoObj.getDouble("lon");
                            city = geoObj.optString("city", city);
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "IP geo fallback to timezone default", e);
                }

                String weatherUrlStr = String.format(java.util.Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true",
                        lat, lon);
                URL weatherUrl = new URL(weatherUrlStr);
                HttpURLConnection wConn = (HttpURLConnection) weatherUrl.openConnection();
                wConn.setConnectTimeout(4000);
                wConn.setReadTimeout(4000);

                if (wConn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(wConn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();

                    JSONObject wObj = new JSONObject(sb.toString());
                    JSONObject current = wObj.getJSONObject("current_weather");
                    double temp = current.getDouble("temperature");
                    int code = current.getInt("weathercode");

                    String icon = getWeatherIcon(code);
                    String result = String.format(java.util.Locale.US, "%s %d°C", icon, Math.round(temp));
                    final String finalCity = city;

                    prefs.edit()
                            .putString("weather_text", result)
                            .putString("city", finalCity)
                            .putLong("last_fetch", System.currentTimeMillis())
                            .apply();

                    mainHandler.post(() -> {
                        if (callback != null) callback.onWeatherUpdated(result, finalCity);
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not fetch weather", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onWeatherUpdated("⛅ 26°C", "Delhi");
                });
            }
        });
    }

    private static String getWeatherIcon(int code) {
        if (code == 0) return "☀️";
        if (code <= 3) return "⛅";
        if (code <= 48) return "🌫️";
        if (code <= 67) return "🌧️";
        if (code <= 77) return "❄️";
        if (code <= 82) return "🌦️";
        if (code <= 86) return "🌨️";
        return "⛈️";
    }
}
