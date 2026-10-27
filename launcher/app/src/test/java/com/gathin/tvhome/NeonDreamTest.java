package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NeonDreamTest {
    @Test public void eachPaletteNameHasItsOwnColours() {
        assertEquals(0, NeonDream.NeonView.paletteIndex("magenta"));
        assertEquals(1, NeonDream.NeonView.paletteIndex("amber"));
        assertEquals(2, NeonDream.NeonView.paletteIndex("green"));
    }

    @Test public void anUnknownPaletteFallsBackToMagenta() {
        assertEquals(0, NeonDream.NeonView.paletteIndex("purple"));
        assertEquals(0, NeonDream.NeonView.paletteIndex(""));
        assertEquals(0, NeonDream.NeonView.paletteIndex(null));
    }

    @Test public void onlyNeonGridPicksTheGrid() {
        assertTrue(NeonDream.NeonView.isGrid("neon_grid"));
        assertFalse(NeonDream.NeonView.isGrid("rain_city"));
        assertFalse(NeonDream.NeonView.isGrid("anything else"));
        assertFalse(NeonDream.NeonView.isGrid(null));
    }
}
