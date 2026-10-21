package io.github.epochcoder.homelab;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The words under a screensaver photo, Google Photos style: what it is (the album, else the place, else the
 * date), then when and where, then who. Pure, so the choices are unit-tested; Slideshow supplies the date
 * pattern for the TV's language.
 */
final class Caption {
    /** People named before the rest are counted as "+N". */
    static final int MAX_PEOPLE = 3;

    final String title;
    final String subtitle;
    final String people;

    private Caption(String title, String subtitle, String people) {
        this.title = title;
        this.subtitle = subtitle;
        this.people = people;
    }

    /**
     * @param album       an album holding the photo, or ""
     * @param datePattern a SimpleDateFormat pattern for the date line, e.g. "EEEE, d MMMM yyyy"
     */
    static Caption of(Immich.Photo p, String album, String datePattern, Locale locale) {
        String date = date(p.localDateTime, datePattern, locale);
        String place = place(p);
        String title;
        List<String> sub = new ArrayList<>();
        if (!album.isEmpty()) {
            title = album;
            add(sub, date);
            add(sub, place);
        } else if (!place.isEmpty()) {
            title = place;
            add(sub, date);
        } else {
            title = date;
        }
        // A description the owner typed in Immich says more than any of these; it gets its own place.
        if (!p.description.isEmpty() && !p.description.equals(title)) sub.add(0, p.description);
        return new Caption(title, String.join("  ·  ", sub), people(p.people));
    }

    /** "Cape Town, South Africa"; the state stands in for a missing city ("Texas, United States"). */
    static String place(Immich.Photo p) {
        List<String> parts = new ArrayList<>();
        add(parts, p.city.isEmpty() ? p.state : p.city);
        add(parts, p.country);
        return String.join(", ", parts);
    }

    /** "Anna, Ben, Cara +2": names only, so no word needs translating. */
    static String people(List<String> names) {
        if (names.isEmpty()) return "";
        int shown = Math.min(MAX_PEOPLE, names.size());
        String s = String.join(", ", names.subList(0, shown));
        return names.size() > shown ? s + " +" + (names.size() - shown) : s;
    }

    /**
     * The local date in {@code pattern}, or "" when Immich gave none. Immich's localDateTime is wall-clock
     * time where the photo was taken, so only its date part is read and no timezone is applied.
     */
    static String date(String localDateTime, String pattern, Locale locale) {
        if (localDateTime == null || !localDateTime.matches("\\d{4}-\\d{2}-\\d{2}.*")) return "";
        int y = Integer.parseInt(localDateTime.substring(0, 4));
        int m = Integer.parseInt(localDateTime.substring(5, 7));
        int d = Integer.parseInt(localDateTime.substring(8, 10));
        if (m < 1 || m > 12 || d < 1 || d > 31) return "";
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar c = Calendar.getInstance(utc, locale);
        c.clear();
        c.set(y, m - 1, d);
        SimpleDateFormat f = new SimpleDateFormat(pattern, locale);
        f.setTimeZone(utc);
        return f.format(c.getTime());
    }

    private static void add(List<String> list, String s) {
        if (!s.isEmpty() && !list.contains(s)) list.add(s);
    }
}
