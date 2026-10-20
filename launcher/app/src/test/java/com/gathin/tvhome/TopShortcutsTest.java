package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TopShortcutsTest {

    @Test
    public void normalizeAcceptsKnownStyles() {
        assertEquals(TopShortcuts.STYLE_DEFAULT, TopShortcuts.normalize(null));
        assertEquals(TopShortcuts.STYLE_DEFAULT, TopShortcuts.normalize(""));
        assertEquals(TopShortcuts.STYLE_DEFAULT, TopShortcuts.normalize("other"));
        assertEquals(TopShortcuts.STYLE_WALLPAPER, TopShortcuts.normalize("wallpaper"));
        assertEquals(TopShortcuts.STYLE_GLASS, TopShortcuts.normalize("glass"));
    }

    @Test
    public void wallpaperLightnessThreshold() {
        assertFalse(TopShortcuts.wallpaperIsLight(0.2f));
        assertFalse(TopShortcuts.wallpaperIsLight(TopShortcuts.LIGHT_WALLPAPER - 0.01f));
        assertTrue(TopShortcuts.wallpaperIsLight(TopShortcuts.LIGHT_WALLPAPER));
        assertTrue(TopShortcuts.wallpaperIsLight(0.9f));
    }

    @Test
    public void colorLuminanceBlackAndWhite() {
        assertEquals(0f, TopShortcuts.colorLuminance(0xFF000000), 0.001f);
        assertEquals(1f, TopShortcuts.colorLuminance(0xFFFFFFFF), 0.001f);
        assertTrue(TopShortcuts.colorLuminance(0xFFFFFFFF) > TopShortcuts.colorLuminance(0xFF808080));
    }
}
