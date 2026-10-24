package com.gathin.tvhome;

/**
 * The line under the date: the moon's phase and the Hindu tithi ("Waxing gibbous · Shukla Navami"), worked out on
 * the TV from the sun's and the moon's longitudes, so it needs no network. A tithi is each 12° the moon gains on
 * the sun; the low-precision series below (Meeus, chapters 25 and 47) is good to about 0.2°, which puts a tithi
 * change within a few minutes of a printed panchang.
 */
final class Panchang {

    private static final String[] TITHI = {"Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi",
            "Saptami", "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi"};

    private Panchang() {
    }

    /** The full line for a moment, as epoch milliseconds. */
    static String line(long epochMs) {
        double e = elongation(epochMs);
        return phase(e) + " · " + tithi(e);
    }

    /** How far the moon is ahead of the sun, 0 to 360°. */
    static double elongation(long epochMs) {
        double t = (epochMs / 86_400_000.0 + 2440587.5 - 2451545.0) / 36525.0;
        double m = rad(357.5291092 + 35999.0502909 * t);
        double sun = 280.46646 + 36000.76983 * t
                + (1.914602 - 0.004817 * t) * Math.sin(m) + 0.019993 * Math.sin(2 * m) + 0.000289 * Math.sin(3 * m);
        double d = rad(297.8501921 + 445267.1114034 * t);
        double mm = rad(134.9633964 + 477198.8675055 * t);
        double f = rad(93.2720950 + 483202.0175233 * t);
        double moon = 218.3164477 + 481267.88123421 * t
                + 6.288774 * Math.sin(mm) + 1.274027 * Math.sin(2 * d - mm) + 0.658314 * Math.sin(2 * d)
                + 0.213618 * Math.sin(2 * mm) - 0.185116 * Math.sin(m) - 0.114332 * Math.sin(2 * f)
                + 0.058793 * Math.sin(2 * d - 2 * mm) + 0.057066 * Math.sin(2 * d - m - mm)
                + 0.053322 * Math.sin(2 * d + mm) + 0.045758 * Math.sin(2 * d - m) - 0.040923 * Math.sin(m - mm)
                - 0.034720 * Math.sin(d) - 0.030383 * Math.sin(m + mm);
        return ((moon - sun) % 360 + 360) % 360;
    }

    /** "Shukla Navami", "Purnima", "Krishna Dashami", "Amavasya". */
    static String tithi(double elongation) {
        int i = (int) (elongation / 12) % 30;
        if (i == 14) return "Purnima";
        if (i == 29) return "Amavasya";
        return (i < 15 ? "Shukla " : "Krishna ") + TITHI[i % 15];
    }

    /** The named phases hold for about half a day either side (6°); the rest is crescent or gibbous. */
    static String phase(double e) {
        if (e < 6 || e >= 354) return "New moon";
        if (Math.abs(e - 90) < 6) return "First quarter";
        if (Math.abs(e - 180) < 6) return "Full moon";
        if (Math.abs(e - 270) < 6) return "Last quarter";
        if (e < 90) return "Waxing crescent";
        if (e < 180) return "Waxing gibbous";
        if (e < 270) return "Waning gibbous";
        return "Waning crescent";
    }

    private static double rad(double deg) {
        return Math.toRadians(deg % 360);
    }
}
