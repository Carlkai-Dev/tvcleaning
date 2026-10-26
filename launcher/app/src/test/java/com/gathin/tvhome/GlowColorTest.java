package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** glow_color from config.xml or a theme: a six-digit #RRGGBB as 0xRRGGBB, 0 (off) for anything else. */
public class GlowColorTest {

    @Test
    public void sixDigitHexAnyCaseWithSpaces() {
        assertEquals(0xFF2D95, Config.glowColor("#FF2D95"));
        assertEquals(0xFF2D95, Config.glowColor(" #ff2d95 "));
        assertEquals(0x8A2BE2, Config.glowColor("#8a2be2"));
        assertEquals(0xFFFFFF, Config.glowColor("#FFFFFF"));
    }

    @Test
    public void unsetOrMalformedIsOff() {
        assertEquals(0, Config.glowColor(null));
        assertEquals(0, Config.glowColor(""));
        assertEquals(0, Config.glowColor("   "));
        assertEquals(0, Config.glowColor("pink"));
        assertEquals(0, Config.glowColor("FF2D95"));      // no hash
        assertEquals(0, Config.glowColor("#F2D"));        // three digits
        assertEquals(0, Config.glowColor("#80FF2D95"));   // alpha: the glass sets its own
        assertEquals(0, Config.glowColor("#GG2D95"));     // not hex
    }

    @Test
    public void blackCountsAsUnset() {
        // GlassView.setAccent reads 0 as the plain white glass, so black is the one colour that cannot be chosen.
        assertEquals(0, Config.glowColor("#000000"));
    }
}
