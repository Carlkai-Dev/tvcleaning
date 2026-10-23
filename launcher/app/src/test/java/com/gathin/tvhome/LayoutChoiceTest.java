package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/** Home layout and Mood in Launcher settings. */
public class LayoutChoiceTest {

    @Test
    public void aLayoutChosenOnTheTvWinsOverTheTheme() {
        assertEquals(Config.LAYOUT_HONEYCOMB, Config.chosenLayout("honeycomb", Config.LAYOUT_GRID));
        assertEquals(Config.LAYOUT_GRID, Config.chosenLayout("grid", Config.LAYOUT_WORDS));
        assertEquals(Config.LAYOUT_WORDS, Config.chosenLayout(" Words ", Config.LAYOUT_HONEYCOMB));
    }

    @Test
    public void noChoiceOrAnUnknownOneKeepsTheTheme() {
        // null is what Config reads when nothing is saved, or after Follow theme or a theme push.
        assertEquals(Config.LAYOUT_HONEYCOMB, Config.chosenLayout(null, Config.LAYOUT_HONEYCOMB));
        assertEquals(Config.LAYOUT_WORDS, Config.chosenLayout("bento", Config.LAYOUT_WORDS));
        assertEquals(Config.LAYOUT_WORDS, Config.chosenLayout("", Config.LAYOUT_WORDS));
    }

    @Test
    public void theMoodRowShowsTheChosenModeElseTheFirst() {
        PowerTheme power = new PowerTheme(Arrays.asList(
                new PowerTheme.Mode("chill", "Chill", "Relaxed", "", 0, "", null),
                new PowerTheme.Mode("game", "Game", "", "", 0, "", null)), PowerTheme.ASK_NEVER);
        assertEquals("Relaxed", LauncherSettingsActivity.mood(power, "chill"));
        assertEquals("Game", LauncherSettingsActivity.mood(power, "game"));   // no mood: the name
        assertEquals("Relaxed", LauncherSettingsActivity.mood(power, null));
        assertEquals("Relaxed", LauncherSettingsActivity.mood(power, "gone"));
    }
}
