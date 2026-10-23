package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import java.util.Arrays;

import org.junit.Test;

public class PowerThemeTest {

    private static PowerTheme.Mode mode(String id, String key) {
        return new PowerTheme.Mode(id, id, "", "", 0xFF000000, key, null);
    }

    private static final PowerTheme SIX = new PowerTheme(Arrays.asList(mode("entertain", ""), mode("movies", "red"),
            mode("games", ""), mode("kids", "yellow"), mode("news", "blue"), mode("markets", "green")),
            PowerTheme.ASK_BOOT);

    @Test
    public void daypartsCoverTheClock() {
        assertEquals("night", PowerTheme.daypart(4));
        assertEquals("morning", PowerTheme.daypart(5));
        assertEquals("morning", PowerTheme.daypart(11));
        assertEquals("afternoon", PowerTheme.daypart(12));
        assertEquals("evening", PowerTheme.daypart(17));
        assertEquals("evening", PowerTheme.daypart(21));
        assertEquals("night", PowerTheme.daypart(22));
        assertEquals("night", PowerTheme.daypart(0));
    }

    @Test
    public void suggestionFollowsTheHourAndTheWeekend() {
        assertEquals(4, SIX.suggested(7, false, null));    // news in the morning
        assertEquals(0, SIX.suggested(14, false, null));   // entertain on a weekday afternoon
        assertEquals(2, SIX.suggested(14, true, null));    // games on a weekend afternoon
        assertEquals(0, SIX.suggested(19, true, null));    // entertain in the evening
        assertEquals(1, SIX.suggested(23, false, null));   // movies late
        assertEquals(1, SIX.suggested(2, false, null));
    }

    @Test
    public void missingSuggestionFallsBackToLastChoiceThenFirst() {
        PowerTheme two = new PowerTheme(Arrays.asList(mode("chill", ""), mode("sport", "")), PowerTheme.ASK_BOOT);
        assertEquals(1, two.suggested(7, false, "sport"));
        assertEquals(0, two.suggested(7, false, "gone"));
        assertEquals(0, two.suggested(7, false, null));
    }

    @Test
    public void welcomeOncePerBootOncePerDayOrNever() {
        assertTrue(PowerTheme.welcomeDue(PowerTheme.ASK_BOOT, 12, 11, 100, 100));
        assertFalse(PowerTheme.welcomeDue(PowerTheme.ASK_BOOT, 12, 12, 101, 100));
        assertFalse("an unknown boot count never asks", PowerTheme.welcomeDue(PowerTheme.ASK_BOOT, -1, 3, 1, 0));
        assertTrue(PowerTheme.welcomeDue(PowerTheme.ASK_DAILY, 12, 12, 101, 100));
        assertFalse(PowerTheme.welcomeDue(PowerTheme.ASK_DAILY, 13, 12, 100, 100));
        assertFalse(PowerTheme.welcomeDue(PowerTheme.ASK_NEVER, 13, 12, 101, 100));
    }

    @Test
    public void askValuesDefaultToEveryBoot() {
        assertEquals(PowerTheme.ASK_DAILY, PowerTheme.askValue(" Daily "));
        assertEquals(PowerTheme.ASK_NEVER, PowerTheme.askValue("never"));
        assertEquals(PowerTheme.ASK_BOOT, PowerTheme.askValue("sometimes"));
        assertEquals(PowerTheme.ASK_BOOT, PowerTheme.askValue(null));
    }

    @Test
    public void colourKeysMapToModes() {
        assertEquals(PowerTheme.RED, PowerTheme.colourKey(KeyEvent.KEYCODE_PROG_RED));
        assertEquals(PowerTheme.BLUE, PowerTheme.colourKey(KeyEvent.KEYCODE_PROG_BLUE));
        assertNull(PowerTheme.colourKey(KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals("movies", SIX.forKey(PowerTheme.RED).id);
        assertEquals("markets", SIX.forKey(PowerTheme.GREEN).id);
        assertNull(SIX.forKey(""));
        assertEquals("", PowerTheme.colourName("purple"));
        assertEquals(PowerTheme.YELLOW, PowerTheme.colourName(" Yellow"));
    }

    @Test
    public void modesCannotSetThemeOnlyKeys() {
        assertTrue(PowerTheme.themeOnly("modes"));
        assertTrue(PowerTheme.themeOnly("power_theme"));
        assertFalse(PowerTheme.themeOnly("columns"));
    }

    @Test
    public void moodFallsBackToTheModeName() {
        assertEquals("Movies", new PowerTheme.Mode("movies", "Movies", "", "", 0, "", null).mood);
        assertEquals("Relaxed", new PowerTheme.Mode("movies", "Movies", "Relaxed", "", 0, "", null).mood);
    }

    @Test
    public void noModesMeansOff() {
        assertFalse(PowerTheme.NONE.on());
        assertTrue(SIX.on());
        assertEquals(-1, PowerTheme.indexOf(SIX.modes, "sport"));
    }
}
