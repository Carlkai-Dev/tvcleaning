package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

public class BatteryPillTest {

    @Test
    public void percentInTheLocale() {
        assertEquals("18%", BatteryPill.label(18, 100, Locale.US));
        assertEquals("18 %", BatteryPill.label(18, 100, Locale.FRANCE));
    }

    @Test
    public void scaleOtherThanAHundredAndClamping() {
        assertEquals("50%", BatteryPill.label(2, 4, Locale.US));
        assertEquals("100%", BatteryPill.label(120, 100, Locale.US));
    }

    @Test
    public void noLevelWithoutABattery() {
        assertFalse(BatteryPill.hasLevel(-1, 100));
        assertFalse(BatteryPill.hasLevel(18, 0));
        assertTrue(BatteryPill.hasLevel(0, 100));
    }
}
