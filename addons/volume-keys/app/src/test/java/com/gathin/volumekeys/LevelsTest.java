package com.gathin.volumekeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LevelsTest {
    @Test public void keysMapToTheirPosition() {
        assertEquals(0, Levels.indexOf(183));
        assertEquals(3, Levels.indexOf(186));
        assertEquals(-1, Levels.indexOf(24));
    }

    @Test public void levelsStayInRange() {
        assertEquals(0, Levels.clamp(-3, 100));
        assertEquals(15, Levels.clamp(40, 15));
        assertEquals(20, Levels.clamp(20, 100));
    }

    @Test public void serviceListIsMatchedWholeEntry() {
        assertTrue(MainActivity.containsService("a/.X:b/.Y", "b/.Y"));
        assertFalse(MainActivity.containsService("a/.X:b/.YZ", "b/.Y"));
    }
}
