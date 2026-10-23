package com.gathin.tvhome;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The honeycomb layout's field, D-pad and fisheye (Honeycomb), without a TV. */
public class HoneycombTest {

    private static float weighted(Honeycomb f, int i) {
        float y = f.y[i] * Honeycomb.ROW_WEIGHT;
        return f.x[i] * f.x[i] + y * y;
    }

    @Test
    public void noAppsIsAnEmptyField() {
        Honeycomb f = new Honeycomb(0);
        assertEquals(0, f.count);
        assertEquals(-1, f.move(0, Honeycomb.RIGHT));
    }

    @Test
    public void theFirstAppSitsInTheMiddleAndTheRestGrowOutwards() {
        Honeycomb f = new Honeycomb(50);
        assertEquals(0f, f.x[0], 0f);
        assertEquals(0f, f.y[0], 0f);
        for (int i = 1; i < f.count; i++) {
            assertTrue("app " + i + " is nearer the middle than app " + (i - 1), weighted(f, i) >= weighted(f, i - 1) - 1e-3f);
        }
    }

    @Test
    public void everyAppHasItsOwnCellAndOddRowsAreShiftedByHalf() {
        Honeycomb f = new Honeycomb(80);
        Set<Long> cells = new HashSet<>();
        for (int i = 0; i < f.count; i++) {
            assertTrue(cells.add(((long) f.row[i] << 32) ^ (f.col[i] & 0xFFFFFFFFL)));
            float shift = Math.floorMod(f.row[i], 2) == 1 ? 0.5f : 0f;
            assertEquals(f.col[i] + shift, f.x[i], 1e-6f);
            assertEquals(f.row[i] * Honeycomb.ROW_PITCH, f.y[i], 1e-6f);
        }
    }

    @Test
    public void everyRowIsUnbrokenSoLeftAndRightNeverSkipAGap() {
        for (int n : new int[]{1, 2, 7, 12, 31, 50, 120}) {
            Honeycomb f = new Honeycomb(n);
            for (int i = 0; i < f.count; i++) {
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, inRow = 0;
                for (int j = 0; j < f.count; j++) {
                    if (f.row[j] != f.row[i]) continue;
                    inRow++;
                    min = Math.min(min, f.col[j]);
                    max = Math.max(max, f.col[j]);
                }
                assertEquals("row " + f.row[i] + " of " + n, max - min + 1, inRow);
            }
        }
    }

    @Test
    public void theFieldIsWiderThanItIsTall() {
        Honeycomb f = new Honeycomb(50);
        float wide = 0, tall = 0;
        for (int i = 0; i < f.count; i++) {
            wide = Math.max(wide, Math.abs(f.x[i]));
            tall = Math.max(tall, Math.abs(f.y[i]));
        }
        assertTrue(wide > tall * 1.3f);
    }

    @Test
    public void leftAndRightStayInTheRowAndUndoEachOther() {
        Honeycomb f = new Honeycomb(50);
        for (int i = 0; i < f.count; i++) {
            int right = f.move(i, Honeycomb.RIGHT);
            if (right >= 0) {
                assertEquals(f.row[i], f.row[right]);
                assertEquals(f.col[i] + 1, f.col[right]);
                assertEquals(i, f.move(right, Honeycomb.LEFT));
            }
        }
    }

    @Test
    public void downGoesRightAndUpGoesLeftOnATie() {
        Honeycomb f = new Honeycomb(50);
        int down = f.move(0, Honeycomb.DOWN), up = f.move(0, Honeycomb.UP);
        assertEquals(1, f.row[down]);
        assertEquals(0.5f, f.x[down], 1e-6f);
        assertEquals(-1, f.row[up]);
        assertEquals(-0.5f, f.x[up], 1e-6f);
    }

    @Test
    public void downThenUpComesBack() {
        Honeycomb f = new Honeycomb(50);
        for (int i = 0; i < f.count; i++) {
            int down = f.move(i, Honeycomb.DOWN);
            // When Down went right (the zigzag), Up from there ties between this app and the one to its right and
            // goes left: back here. At a row's right end Down has to go left, and Up then goes further left.
            if (down >= 0 && f.x[down] > f.x[i]) assertEquals("app " + i, i, f.move(down, Honeycomb.UP));
        }
    }

    @Test
    public void upAndDownGoToTheNearestAppInTheNextRow() {
        Honeycomb f = new Honeycomb(50);
        for (int i = 0; i < f.count; i++) {
            for (int dir : new int[]{Honeycomb.UP, Honeycomb.DOWN}) {
                int next = f.move(i, dir);
                int want = f.row[i] + (dir == Honeycomb.DOWN ? 1 : -1);
                boolean rowThere = false;
                for (int j = 0; j < f.count; j++) {
                    if (f.row[j] != want) continue;
                    rowThere = true;
                    if (next >= 0) assertTrue(Math.abs(f.x[next] - f.x[i]) <= Math.abs(f.x[j] - f.x[i]) + 1e-3f);
                }
                assertEquals(rowThere, next >= 0);
                if (next >= 0) assertEquals(want, f.row[next]);
            }
        }
    }

    @Test
    public void nothingBeyondTheEdges() {
        Honeycomb f = new Honeycomb(1);
        for (int dir : new int[]{Honeycomb.LEFT, Honeycomb.RIGHT, Honeycomb.UP, Honeycomb.DOWN}) {
            assertEquals(-1, f.move(0, dir));
        }
        assertEquals(-1, f.move(5, Honeycomb.LEFT));
    }

    @Test
    public void theFisheyeShrinksFromTheMiddleToTheEdge() {
        assertEquals(1.2f, Honeycomb.scale(0f), 1e-6f);
        assertEquals(0.2f, Honeycomb.scale(0.5f), 1e-5f);
        assertEquals(0.2f, Honeycomb.scale(2f), 1e-6f);
        float last = Float.MAX_VALUE;
        for (float d = 0f; d <= 0.6f; d += 0.01f) {
            float s = Honeycomb.scale(d);
            assertTrue(s <= last);
            last = s;
        }
    }

    @Test
    public void iconsFadeOutNearTheEdgeAndAreHiddenBeyondIt() {
        assertEquals(1f, Honeycomb.alpha(0f), 0f);
        assertEquals(1f, Honeycomb.alpha(Honeycomb.FADE_START), 0f);
        assertEquals(0.5f, Honeycomb.alpha((Honeycomb.FADE_START + Honeycomb.HIDE_DISTANCE) / 2f), 1e-4f);
        assertEquals(0f, Honeycomb.alpha(Honeycomb.HIDE_DISTANCE), 0f);
        assertEquals(0f, Honeycomb.alpha(1f), 0f);
    }

    @Test
    public void verticalDistanceCountsALittleMore() {
        assertEquals(0.3f, Honeycomb.distance(0.3f, 0f), 1e-6f);
        assertEquals(0.3f, Honeycomb.distance(-0.3f, 0f), 1e-6f);
        assertTrue(Honeycomb.distance(0f, 0.2f) > Honeycomb.distance(0.2f, 0f));
    }
}
