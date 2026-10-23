package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** layout from a theme: honeycomb by name, grid (today's Home) for anything else. */
public class LayoutKeyTest {

    @Test
    public void honeycombByNameAnyCase() {
        assertEquals(Config.LAYOUT_HONEYCOMB, Config.layout("honeycomb"));
        assertEquals(Config.LAYOUT_HONEYCOMB, Config.layout(" Honeycomb "));
    }

    @Test
    public void anythingElseIsTheGrid() {
        assertEquals(Config.LAYOUT_GRID, Config.layout("grid"));
        assertEquals(Config.LAYOUT_GRID, Config.layout(""));
        assertEquals(Config.LAYOUT_GRID, Config.layout(null));
        assertEquals(Config.LAYOUT_GRID, Config.layout("bento"));
    }
}
