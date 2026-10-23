package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The "Big words" layout's D-pad and fading (Words), without a TV. */
public class WordsTest {

    @Test
    public void upAndDownStepThroughTheList() {
        assertEquals(3, Words.move(2, Honeycomb.DOWN, 5));
        assertEquals(1, Words.move(2, Honeycomb.UP, 5));
    }

    @Test
    public void theEndsAndTheSidesGoNowhere() {
        assertEquals(-1, Words.move(0, Honeycomb.UP, 5));
        assertEquals(-1, Words.move(4, Honeycomb.DOWN, 5));
        assertEquals(-1, Words.move(2, Honeycomb.LEFT, 5));
        assertEquals(-1, Words.move(2, Honeycomb.RIGHT, 5));
        assertEquals(-1, Words.move(0, Honeycomb.DOWN, 0));
        assertEquals(-1, Words.move(7, Honeycomb.UP, 5));
    }

    @Test
    public void namesFadeWithTheirDistanceFromTheFocus() {
        assertEquals(1f, Words.alpha(0f), 0f);
        assertEquals(Words.NEXT_ALPHA, Words.alpha(1f), 1e-6f);
        assertEquals(Words.NEXT_ALPHA, Words.alpha(-1f), 1e-6f);
        assertEquals(Words.NEXT_ALPHA - Words.ROW_FADE, Words.alpha(2f), 1e-6f);
        assertEquals(Words.FAR_ALPHA, Words.alpha(40f), 0f);
        float last = 2f;
        for (float d = 0f; d < 10f; d += 0.25f) {
            float a = Words.alpha(d);
            assertTrue(a <= last);
            last = a;
        }
    }
}
