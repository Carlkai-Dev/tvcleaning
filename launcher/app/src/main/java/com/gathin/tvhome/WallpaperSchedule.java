package com.gathin.tvhome;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which wallpaper file to show at a given day and time. A theme's config.json lists rules
 * ("wallpaper_schedule"), each one naming a picture in the theme folder, the days it applies to and a
 * time window; the first rule that matches wins, and when none does the plain {@code wallpaper.jpg}
 * stays. Pure maths with no Android types, so the window logic is unit-tested on the JVM.
 *
 * A window that ends before it starts (18:00 to 04:00) runs over midnight and belongs to the day it
 * started on: "sat 18:00-04:00" covers Saturday evening and the small hours of Sunday.
 */
final class WallpaperSchedule {
    private WallpaperSchedule() { }

    /** Plain file names only: no folders, so a theme cannot point the launcher outside its own directory. */
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}\\.jpg");
    private static final Pattern TIME = Pattern.compile("([01]?\\d|2[0-3]):([0-5]\\d)");
    private static final String[] DAY_NAMES = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    static final class Rule {
        final String file;
        /** Bit 0 is Monday ... bit 6 is Sunday (ISO order, like java.time.DayOfWeek minus one). */
        final int days;
        final int fromMinute;
        final int toMinute;

        Rule(String file, int days, int fromMinute, int toMinute) {
            this.file = file;
            this.days = days;
            this.fromMinute = fromMinute;
            this.toMinute = toMinute;
        }
    }

    /**
     * Builds a rule from the values read out of the JSON; null when anything is missing or malformed, so one
     * bad entry never switches off the others. {@code days} are three-letter English names (mon ... sun);
     * {@code from} and {@code to} are "HH:MM". Equal times mean the whole day.
     */
    static Rule rule(String file, List<String> days, String from, String to) {
        if (file == null || !FILE.matcher(file).matches() || file.contains("..")) return null;
        if (days == null || days.isEmpty()) return null;
        int mask = 0;
        for (String d : days) {
            int bit = dayBit(d);
            if (bit < 0) return null;
            mask |= 1 << bit;
        }
        int f = minutes(from);
        int t = minutes(to);
        if (f < 0 || t < 0) return null;
        return new Rule(file, mask, f, t);
    }

    /**
     * The picture to show, or null to keep the plain wallpaper.
     *
     * @param isoDay  1 = Monday ... 7 = Sunday
     * @param minute  minutes since midnight, 0 to 1439
     */
    static String pick(List<Rule> rules, int isoDay, int minute) {
        if (rules == null) return null;
        for (Rule r : rules) {
            if (matches(r, isoDay, minute)) return r.file;
        }
        return null;
    }

    static boolean matches(Rule r, int isoDay, int minute) {
        int today = 1 << (isoDay - 1);
        int yesterday = 1 << ((isoDay + 5) % 7);
        if (r.fromMinute == r.toMinute) return (r.days & today) != 0;          // the whole day
        if (r.fromMinute < r.toMinute) {                                        // same-day window
            return (r.days & today) != 0 && minute >= r.fromMinute && minute < r.toMinute;
        }
        // Over midnight: the evening part is today's, the small hours belong to yesterday's window.
        if ((r.days & today) != 0 && minute >= r.fromMinute) return true;
        return (r.days & yesterday) != 0 && minute < r.toMinute;
    }

    private static int dayBit(String name) {
        if (name == null) return -1;
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.length() > 3) n = n.substring(0, 3);
        for (int i = 0; i < DAY_NAMES.length; i++) {
            if (DAY_NAMES[i].equals(n)) return i;
        }
        return -1;
    }

    private static int minutes(String hhmm) {
        if (hhmm == null) return -1;
        java.util.regex.Matcher m = TIME.matcher(hhmm.trim());
        if (!m.matches()) return -1;
        return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
    }
}
