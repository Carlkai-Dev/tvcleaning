package io.github.epochcoder.homelab;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numbers as people read them on a TV across the room (sizes, speeds, time left), and the ISO 8601 dates the
 * servers speak. Pure, so it is unit-tested.
 */
final class Format {
    /** qBittorrent's "never" for an ETA: 100 days. */
    static final long ETA_INFINITY = 8_640_000L;
    /** An ISO 8601 date, with or without a time, fraction of a second and zone. */
    private static final Pattern ISO = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})(?:T(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d+)?)?)?(Z|[+-]\\d{2}:?\\d{2})?");

    private Format() {}

    /** "812 KB", "4.2 GB": binary units, as the *arr apps and qBittorrent show them. */
    static String bytes(long b) {
        if (b < 1024) return b + " B";
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double v = b;
        int u = -1;
        do {
            v /= 1024;
            u++;
        } while (v >= 1024 && u < units.length - 1);
        return (v >= 100 || u == 0 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v))
                + " " + units[u];
    }

    /** "4.2 MB/s", or "" when nothing is moving, so an idle line stays quiet. */
    static String speed(long bytesPerSecond) {
        return bytesPerSecond <= 0 ? "" : bytes(bytesPerSecond) + "/s";
    }

    /** "45 s", "12 min", "1 h 5 min", "3 d 4 h"; "" for unknown or never. */
    static String duration(long seconds) {
        if (seconds <= 0 || seconds >= ETA_INFINITY) return "";
        if (seconds < 60) return seconds + " s";
        long m = seconds / 60;
        if (m < 60) return m + " min";
        long h = m / 60;
        if (h < 24) return m % 60 == 0 ? h + " h" : h + " h " + (m % 60) + " min";
        long d = h / 24;
        return h % 24 == 0 ? d + " d" : d + " d " + (h % 24) + " h";
    }

    /** The *arr "timeleft" ("01:02:03" or "1.02:03:04") in seconds, or -1. */
    static long arrTimeLeft(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            long days = 0;
            String rest = s;
            int dot = s.indexOf('.');
            if (dot > 0 && dot < s.indexOf(':')) {
                days = Long.parseLong(s.substring(0, dot));
                rest = s.substring(dot + 1);
            }
            String[] p = rest.split(":");
            if (p.length != 3) return -1;
            return days * 86400 + Long.parseLong(p[0]) * 3600 + Long.parseLong(p[1]) * 60
                    + (long) Double.parseDouble(p[2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Midnight UTC {@code days} days from {@code nowMillis}'s day (negative is before), as ISO 8601. */
    static String utcMidnight(long nowMillis, int days) {
        long day = 24L * 60 * 60 * 1000;
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        return iso.format(new Date((nowMillis / day + days) * day));
    }

    /** An ISO 8601 date or date-time as epoch milliseconds (no zone means UTC), or -1. */
    static long isoMillis(String s) {
        if (s == null) return -1;
        Matcher m = ISO.matcher(s.trim());
        if (!m.matches()) return -1;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        c.clear();
        c.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
                m.group(4) == null ? 0 : Integer.parseInt(m.group(4)), m.group(5) == null ? 0 : Integer.parseInt(m.group(5)),
                m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
        long ms = c.getTimeInMillis();
        String zone = m.group(7);
        if (zone != null && !zone.equals("Z")) {
            String z = zone.replace(":", "");
            int offset = (Integer.parseInt(z.substring(1, 3)) * 60 + Integer.parseInt(z.substring(3, 5))) * 60_000;
            ms -= z.charAt(0) == '-' ? -offset : offset;
        }
        return ms;
    }

    /** 0.4234 as "42%". */
    static String percent(double fraction) {
        return Math.round(Math.max(0, Math.min(1, fraction)) * 100) + "%";
    }
}
