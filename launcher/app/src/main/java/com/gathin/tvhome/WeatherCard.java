package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The clock screensaver's weather card (layout weather_card; config.xml weather_card, latitude and longitude):
 * place and temperature, the sky and today's range, then the next six days, on a card tinted after the sky like
 * a weather widget. Hidden until there is a forecast; switched off or without a place, nothing is fetched. Forecasts
 * come from {@link Weather}, asked for off the main thread at most once an hour.
 */
final class WeatherCard {

    /** After a failed request, wait this long before the next. */
    private static final long RETRY_MS = 15 * 60_000L;
    private static final int WEEK_DAYS = 6;

    private final Context context;
    private final View card;
    /** Called when the card first shows while running: the content grew, so the scene moves it. */
    private final Runnable onAppear;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean enabled;
    private final double latitude;
    private final double longitude;
    private ExecutorService network;
    private boolean running;
    private boolean fetching;
    private long lastAttempt;

    /** root is the inflated dream_clock layout. */
    WeatherCard(View root, Runnable onAppear) {
        context = root.getContext();
        card = root.findViewById(R.id.weather);
        this.onAppear = onAppear;
        enabled = context.getResources().getBoolean(R.bool.weather_card);
        latitude = coordinate(R.string.latitude);
        longitude = coordinate(R.string.longitude);
        if (on()) show(Weather.load(file(), System.currentTimeMillis()));
    }

    /** Asks for a new forecast once the kept one is an hour old. The scene calls it on start and every minute. */
    void refresh() {
        running = true;
        if (!on() || fetching) return;
        long now = System.currentTimeMillis();
        File file = file();
        if (now - file.lastModified() < Weather.REFRESH_MS || now - lastAttempt < RETRY_MS) return;
        fetching = true;
        lastAttempt = now;
        if (network == null) network = Executors.newSingleThreadExecutor();
        network.execute(() -> {
            Weather weather;
            try {
                weather = Weather.fetch(file, latitude, longitude);
            } catch (IOException | JSONException e) {
                weather = null;   // keep showing the last forecast; try again later
            }
            Weather result = weather;
            handler.post(() -> {
                fetching = false;
                if (running && result != null) show(result);
            });
        });
    }

    void stop() {
        running = false;
        if (network != null) network.shutdownNow();
        network = null;
    }

    private boolean on() {
        return enabled && !Double.isNaN(latitude) && !Double.isNaN(longitude);
    }

    private double coordinate(int res) {
        try {
            return Double.parseDouble(context.getString(res).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private File file() {
        return new File(context.getApplicationContext().getFilesDir(), Weather.FILE_NAME);
    }

    private void show(Weather weather) {
        if (weather == null) return;
        List<Weather.Day> days = weather.from(LocalDate.now().toString());
        if (days.isEmpty()) return;
        Weather.Day today = days.get(0);
        Weather.Kind kind = Weather.kind(weather.code);

        card.setBackground(sky(kind, weather.day));
        String place = context.getString(R.string.weather_place).trim();
        TextView placeView = card.findViewById(R.id.weatherPlace);
        placeView.setText(place);
        placeView.setVisibility(place.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) card.findViewById(R.id.weatherTemp))
                .setText(context.getString(R.string.weather_degrees, weather.temperature));
        ((WeatherIcon) card.findViewById(R.id.weatherIcon)).setWeather(kind, weather.day);
        ((TextView) card.findViewById(R.id.weatherSummary)).setText(Weather.description(weather.code));
        ((TextView) card.findViewById(R.id.weatherRange))
                .setText(context.getString(R.string.weather_high_low, today.max, today.min));

        LinearLayout week = card.findViewById(R.id.weatherWeek);
        week.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 1; i < days.size() && i <= WEEK_DAYS; i++) {
            Weather.Day d = days.get(i);
            View column = inflater.inflate(R.layout.item_weather_day, week, false);
            ((TextView) column.findViewById(R.id.day)).setText(dayName(d.date));
            ((WeatherIcon) column.findViewById(R.id.icon)).setWeather(Weather.kind(d.code), true);
            ((TextView) column.findViewById(R.id.max)).setText(context.getString(R.string.weather_degrees, d.max));
            ((TextView) column.findViewById(R.id.min)).setText(context.getString(R.string.weather_degrees, d.min));
            week.addView(column);
        }

        boolean appeared = card.getVisibility() != View.VISIBLE;
        card.setVisibility(View.VISIBLE);
        if (appeared && running) onAppear.run();
    }

    /** "THU" for a Thursday, in the TV's language. */
    private static String dayName(String date) {
        try {
            return LocalDate.parse(date).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .replace(".", "").toUpperCase(Locale.getDefault());
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The card's colour follows the sky: blue for sun, grey for cloud and rain, navy at night. */
    private GradientDrawable sky(Weather.Kind kind, boolean day) {
        int top;
        int bottom;
        if (!day) {
            top = 0x22335A;
            bottom = 0x0C1630;
        } else if (kind == Weather.Kind.CLEAR) {
            top = 0x3A8EE6;
            bottom = 0x1C59B5;
        } else if (kind == Weather.Kind.PARTLY_CLOUDY) {
            top = 0x4E87C7;
            bottom = 0x2A5791;
        } else if (kind == Weather.Kind.RAIN || kind == Weather.Kind.STORM) {
            top = 0x4D5B6E;
            bottom = 0x252E3D;
        } else {
            top = 0x64707F;
            bottom = 0x394350;
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xD9000000 | top, 0xD9000000 | bottom});
        float density = context.getResources().getDisplayMetrics().density;
        bg.setCornerRadius(26 * density);
        bg.setStroke(Math.round(density), 0x26FFFFFF);
        return bg;
    }
}
