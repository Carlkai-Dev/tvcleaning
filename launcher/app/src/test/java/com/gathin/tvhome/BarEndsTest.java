package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BarEndsTest {
    @Test public void theUsbPillIsTheStartWhenShown() {
        // USB, Inputs, Wi-Fi, Settings
        assertEquals(0, BarEnds.start(new boolean[]{true, true, true, true}, false));
    }

    @Test public void withoutTheUsbPillInputsIsTheStart() {
        assertEquals(1, BarEnds.start(new boolean[]{false, true, true, true}, false));
    }

    @Test public void theMirroredBarStartsAtItsLastShownChild() {
        // Settings, Wi-Fi, Inputs, USB
        assertEquals(3, BarEnds.start(new boolean[]{true, true, true, true}, true));
        assertEquals(2, BarEnds.start(new boolean[]{true, true, true, false}, true));
    }

    @Test public void anEmptyBarHasNoStart() {
        assertEquals(-1, BarEnds.start(new boolean[]{false, false}, false));
        assertEquals(-1, BarEnds.start(new boolean[0], true));
    }
}
