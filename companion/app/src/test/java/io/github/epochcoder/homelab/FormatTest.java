package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatTest {
    @Test
    public void bytes() {
        assertEquals("512 B", Format.bytes(512));
        assertEquals("1 KB", Format.bytes(1024));
        assertEquals("4.2 GB", Format.bytes(4_509_715_660L));
        assertEquals("812 MB", Format.bytes(812L * 1024 * 1024));
        assertEquals("2.0 TB", Format.bytes(2L * 1024 * 1024 * 1024 * 1024));
    }

    @Test
    public void speedIsQuietWhenIdle() {
        assertEquals("", Format.speed(0));
        assertEquals("4.2 MB/s", Format.speed(4_404_019));
    }

    @Test
    public void durations() {
        assertEquals("", Format.duration(0));
        assertEquals("", Format.duration(Format.ETA_INFINITY));
        assertEquals("45 s", Format.duration(45));
        assertEquals("12 min", Format.duration(720));
        assertEquals("2 h", Format.duration(7200));
        assertEquals("2 h 35 min", Format.duration(155 * 60));
        assertEquals("3 d 4 h", Format.duration((3 * 24 + 4) * 3600));
    }

    @Test
    public void arrTimeLeft() {
        assertEquals(3723, Format.arrTimeLeft("01:02:03"));
        assertEquals(86400 + 3723, Format.arrTimeLeft("1.01:02:03"));
        assertEquals(5, Format.arrTimeLeft("00:00:05.1234567"));
        assertEquals(-1, Format.arrTimeLeft(""));
        assertEquals(-1, Format.arrTimeLeft("soon"));
    }

    @Test
    public void isoDates() {
        assertEquals(1792584000000L, Format.isoMillis("2026-10-21T12:00:00Z"));
        assertEquals(1792584000000L, Format.isoMillis("2026-10-21T12:00:00.000Z"));
        assertEquals(1792584000000L, Format.isoMillis("2026-10-21T14:00:00+02:00"));
        assertEquals(1792540800000L, Format.isoMillis("2026-10-21"));
        assertEquals(-1, Format.isoMillis("yesterday"));
        assertEquals("2026-10-21T00:00:00.000Z", Format.utcMidnight(1792584000000L, 0));
        assertEquals("2026-11-04T00:00:00.000Z", Format.utcMidnight(1792584000000L, 14));
    }

    @Test
    public void percent() {
        assertEquals("42%", Format.percent(0.4234));
        assertEquals("100%", Format.percent(1.5));
        assertEquals("0%", Format.percent(-1));
    }
}
