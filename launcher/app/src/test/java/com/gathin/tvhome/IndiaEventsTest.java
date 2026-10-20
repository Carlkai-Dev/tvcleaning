package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class IndiaEventsTest {
    @Test public void dussehra2026MatchesTodayWindow() {
        IndiaEvents.Event e = IndiaEvents.forDate(LocalDate.of(2026, 10, 20));
        assertNotNull(e);
        assertEquals("dussehra", e.id);
    }

    @Test public void maharashtraDayComesBeforeIndia() {
        IndiaEvents.Event e = IndiaEvents.forDate(LocalDate.of(2026, 5, 1));
        assertNotNull(e);
        assertEquals("mh_day", e.id);
        assertEquals(true, e.maharashtra);
    }

    @Test public void republicDayIsFixed() {
        assertEquals("republic", IndiaEvents.forDate(LocalDate.of(2026, 1, 26)).id);
    }

    @Test public void ordinaryDayHasNoNamedEvent() {
        assertNull(IndiaEvents.forDate(LocalDate.of(2026, 4, 10)));
    }
}
