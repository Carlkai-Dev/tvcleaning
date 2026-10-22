package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** AppAdapter.tileWidthPx with rows_below_dock: the dock, its title and the rows must fit the grid's height. */
public class TileFitTest {

    private static final float D = 2f;              // 1080p TV: 2 px per dp
    // Readable Home geometry at 1920x1080: 56dp content edges and 8dp grid padding leave 1920 - 2 x (56 + 8) x 2;
    // the grid starts under 32dp top padding, the 48dp top bar and a 10dp margin: 1080 - (32 + 48 + 10) x 2.
    private static final int INNER = 1664;          // grid width minus padding at 1920 px
    private static final int GRID_H = 900;          // grid height under the top bar

    private static int height(int tileW, int rows, boolean title) {
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(tileW), D);
        return itemH * (rows + 1) + Math.round((AppAdapter.DOCK_EXTRA_DP + AppAdapter.BOTTOM_ROOM_DP
                + (title ? AppAdapter.TITLE_ROOM_DP : 0)) * D);
    }

    @Test
    public void noRowsBelowDockKeepsTheWidthOnlySize() {
        int widthOnly = AppAdapter.tileWidthPx(INNER, 4, D);
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 0, true, true, D));
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, 0, 4, 3, true, true, D));   // not laid out yet
    }

    @Test
    public void dockTitleAndRowsFitTheHeightUnlessThatMeansTinyTiles() {
        int floor = Math.round(AppAdapter.tileWidthPx(INNER, 4, D) * AppAdapter.MIN_HEIGHT_FIT);
        for (int rows = 1; rows <= 3; rows++) {
            for (boolean title : new boolean[]{true, false}) {
                int w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, rows, title, true, D);
                assertTrue("rows " + rows + " title " + title + " below the floor", w >= floor);
                if (w > floor) assertTrue("rows " + rows + " title " + title, height(w, rows, title) <= GRID_H + 2);
            }
        }
    }

    @Test
    public void oneRowUnderATitledDockFits() {
        int w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, true, true, D);
        assertTrue(w < AppAdapter.tileWidthPx(INNER, 4, D));
        assertTrue(height(w, 1, true) <= GRID_H + 2);
    }

    @Test
    public void moreRowsStopAtTheFloorInsteadOfShrinkingTheTiles() {
        // Two or three rows under a titled dock would need tiles under half their size: they stop at 85% and
        // the extra rows run off the bottom instead.
        int floor = Math.round(AppAdapter.tileWidthPx(INNER, 4, D) * AppAdapter.MIN_HEIGHT_FIT);
        assertEquals(floor, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 2, true, true, D));
        assertEquals(floor, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 3, true, true, D));
    }

    @Test
    public void plainGridFitsFourRowsAtSixColumns() {
        // dock_row false, rows_below_dock 3: the first row and 3 more under a 12dp top margin.
        int w = AppAdapter.tileWidthPx(INNER, GRID_H, 6, 3, true, false, D);
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(w), false, D);
        int needed = itemH * 4 + Math.round((AppAdapter.TOP_ROOM_DP + AppAdapter.COMPACT_BOTTOM_ROOM_DP) * D);
        assertTrue(needed + " > " + GRID_H, needed <= GRID_H + 2);
        // The 24dp names under the tiles cost some height, so four rows at six columns shrink the tiles a little,
        // never below the floor.
        assertTrue(w >= Math.round(AppAdapter.tileWidthPx(INNER, 6, D) * AppAdapter.MIN_HEIGHT_FIT));
    }

    @Test
    public void droppingTheTitleGivesBiggerTiles() {
        assertTrue(AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, false, true, D)
                > AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, true, true, D));
    }
}
