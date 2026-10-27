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

    @Test public void aChoiceMadeOnTheTvWinsWhenItIsAllowed() {
        assertEquals("neon_grid", Config.chosenOption("neon_grid", "rain_city", Config.DREAM_SCENES));
        assertEquals("green", Config.chosenOption("green", "magenta", Config.DREAM_PALETTES));
    }

    @Test public void noChoiceOrAnUnknownOneKeepsTheThemeValue() {
        assertEquals("rain_city", Config.chosenOption(null, "rain_city", Config.DREAM_SCENES));
        assertEquals("amber", Config.chosenOption("purple", "amber", Config.DREAM_PALETTES));
    }

    @Test public void theRowCyclesThroughTheOptionsAndWrapsRound() {
        assertEquals("amber", Config.nextOption("magenta", Config.DREAM_PALETTES));
        assertEquals("green", Config.nextOption("amber", Config.DREAM_PALETTES));
        assertEquals("magenta", Config.nextOption("green", Config.DREAM_PALETTES));
        assertEquals("magenta", Config.nextOption("unknown", Config.DREAM_PALETTES));
    }
}
