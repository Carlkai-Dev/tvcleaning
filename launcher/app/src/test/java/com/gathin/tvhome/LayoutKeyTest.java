package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** layout from a theme: honeycomb or words by name, grid (today's Home) for anything else. */
public class LayoutKeyTest {

    @Test
    public void honeycombByNameAnyCase() {
        assertEquals(Config.LAYOUT_HONEYCOMB, Config.layout("honeycomb"));
        assertEquals(Config.LAYOUT_HONEYCOMB, Config.layout(" Honeycomb "));
    }

    @Test
    public void wordsByNameAnyCase() {
        assertEquals(Config.LAYOUT_WORDS, Config.layout("words"));
        assertEquals(Config.LAYOUT_WORDS, Config.layout(" WORDS "));
    }

    @Test
    public void anythingElseIsTheGrid() {
        assertEquals(Config.LAYOUT_GRID, Config.layout("grid"));
        assertEquals(Config.LAYOUT_GRID, Config.layout(""));
        assertEquals(Config.LAYOUT_GRID, Config.layout(null));
        assertEquals(Config.LAYOUT_GRID, Config.layout("bento"));
        assertEquals(Config.LAYOUT_GRID, Config.layout("big words"));
    }
}
