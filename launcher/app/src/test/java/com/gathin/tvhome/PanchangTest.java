package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.Test;

public class PanchangTest {

    private static long ist(String local) {
        return ZonedDateTime.parse(local + "+05:30[Asia/Kolkata]").toInstant().toEpochMilli();
    }

    @Test
    public void dussehraEveIsShuklaNavami() {
        // The reference screenshot: Tuesday 20 October 2026, 03:12 in India.
        assertEquals("Waxing gibbous · Shukla Navami", Panchang.line(ist("2026-10-20T03:12")));
    }

    @Test
    public void diwaliEveningIsAmavasya() {
        assertEquals("Amavasya", Panchang.tithi(Panchang.elongation(ist("2026-11-08T19:00"))));
    }

    @Test
    public void fullMoonOfSharadPurnima() {
        // Sharad Purnima night, 25 October 2026; the moon is full at 09:41 the next morning.
        assertEquals("Purnima", Panchang.tithi(Panchang.elongation(ist("2026-10-25T20:00"))));
    }

    @Test
    public void festivalGreetings() {
        assertEquals("Happy Diwali", IndianFestivals.greeting(LocalDate.of(2026, 11, 8)));
        assertEquals("Happy Navratri", IndianFestivals.greeting(LocalDate.of(2027, 10, 3)));
        assertEquals("Merry Christmas", IndianFestivals.greeting(LocalDate.of(2031, 12, 25)));
        assertNull(IndianFestivals.greeting(LocalDate.of(2026, 10, 24)));
    }

    @Test
    public void dockHeaderCounter() {
        assertEquals("1-6 / 12", DockDecoration.rowRange(3, 6, 12));
        assertEquals("7-12 / 12", DockDecoration.rowRange(7, 6, 12));
        assertEquals("7-9 / 9", DockDecoration.rowRange(8, 6, 9));
    }

    @Test
    public void weatherLines() {
        String[] bad = HomeWeather.format(3, true, 28.6, 162, "Mumbai");
        assertEquals("☁  Cloudy, Mumbai", bad[0]);
        assertEquals("29° · Air quality 162 Unhealthy", bad[1]);
        assertEquals("Poor air · keep windows closed", bad[2]);
        assertEquals("☁  29°  Cloudy, Mumbai", bad[3]);
        assertEquals("", HomeWeather.format(0, false, 24, 60, "Mumbai")[2]);
        assertEquals("24°", HomeWeather.format(0, false, 24, -1, "Mumbai")[1]);
    }
}
