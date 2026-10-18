package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.time.LocalDate;

/** Local daily phrases, stable through restarts. No network, alarms or additional timer. */
final class DailyGreeting {
    private final Context context;
    private final SharedPreferences prefs;
    private final Resources phraseResources;
    private String[] general;
    private String[] personal;
    private long cachedDay = Long.MIN_VALUE;
    private int cachedMode = -1;
    private String cachedName;
    private String cachedPhrase;

    DailyGreeting(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE);
        phraseResources = context.getResources();
    }

    int mode() {
        return Math.floorMod(prefs.getInt("greeting_mode", prefs.getBoolean("clock_greeting", true) ? 0 : 3), 4);
    }

    String message(Calendar now) {
        int mode = mode();
        if (mode == 0 || mode == 3) {
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int res = hour < 5 ? R.string.good_night : hour < 12 ? R.string.good_morning
                    : hour < 17 ? R.string.good_afternoon : hour < 22 ? R.string.good_evening : R.string.good_night;
            return context.getString(res);
        }
        long day = LocalDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH)).toEpochDay();
        String name = prefs.getString("greeting_name", "").trim();
        if (day == cachedDay && mode == cachedMode && name.equals(cachedName))
            return cachedPhrase;
        if (general == null) {
            general = phraseResources.getStringArray(R.array.daily_phrases);
            personal = phraseResources.getStringArray(R.array.personal_daily_phrases);
        }
        if (!prefs.contains("greeting_seed"))
            prefs.edit().putInt("greeting_seed", new Random().nextInt(1 << 20)).apply();
        int seed = prefs.getInt("greeting_seed", 0);
        boolean hasName = !name.isEmpty();
        boolean personalOnly = mode == 2 && hasName;
        int count = personalOnly ? personal.length : general.length + (hasName ? personal.length : 0);
        // Both 40/20-item pools and the combined 60-item pool have no repeats within a full cycle.
        int index = Math.floorMod(day * 37L + seed, count);
        String template = personalOnly ? personal[index]
                : index < general.length ? general[index] : personal[index - general.length];
        Locale locale = phraseResources.getConfiguration().getLocales().get(0);
        cachedPhrase = String.format(locale, template, name);
        cachedDay = day;
        cachedMode = mode;
        cachedName = name;
        return cachedPhrase;
    }

}
