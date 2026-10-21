package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SlideshowTest {
    @Test
    public void sampleSizeNeverDropsBelowTheScreen() {
        assertEquals(1, Slideshow.sampleSize(1440, 1080, 1920, 1080));
        assertEquals(1, Slideshow.sampleSize(3839, 2159, 1920, 1080));
        assertEquals(2, Slideshow.sampleSize(3840, 2160, 1920, 1080));
        assertEquals(4, Slideshow.sampleSize(8000, 6000, 1920, 1080));
    }

    @Test
    public void landscapesCloseToTheScreenFillIt() {
        assertTrue("16:9", Slideshow.fills(1920, 1080, 1920, 1080, false));
        assertTrue("3:2 camera", Slideshow.fills(1440, 960, 1920, 1080, false));
        assertFalse("4:3 phone fits", Slideshow.fills(1440, 1080, 1920, 1080, false));
        assertTrue("4:3 with always", Slideshow.fills(1440, 1080, 1920, 1080, true));
        assertFalse("panorama fits", Slideshow.fills(4000, 1000, 1920, 1080, false));
        assertFalse("portrait never", Slideshow.fills(1080, 1440, 1920, 1080, true));
        assertFalse("square fits by default", Slideshow.fills(1080, 1080, 1920, 1080, false));
    }

    @Test
    public void portraitNeedsToBeClearlyTaller() {
        assertTrue(Slideshow.isPortrait(1080, 1440));
        assertFalse(Slideshow.isPortrait(1080, 1100));
        assertFalse(Slideshow.isPortrait(1440, 1080));
    }
}
