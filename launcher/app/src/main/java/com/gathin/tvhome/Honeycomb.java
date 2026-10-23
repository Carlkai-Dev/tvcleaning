package com.gathin.tvhome;

import java.util.Arrays;

/**
 * The honeycomb layout's geometry (config.xml layout "honeycomb"), kept free of Android so it can be tested on the
 * JVM: where each app sits in the hex-packed field, where the D-pad goes from it, and how large an icon is drawn
 * at a given distance from the middle of the screen. {@link HoneycombView} draws it.
 *
 * The field is a hexagonal packing: rows {@link #ROW_PITCH} apart, every odd row shifted by half a cell. Apps are
 * placed from the middle outwards, so the first ones (the dock apps) sit around the middle and the field grows as
 * a wide ellipse, like the screen. Positions are in cells: one cell is the distance between two neighbours.
 */
final class Honeycomb {
    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;

    /** Distance between two rows in cells: sqrt(3) / 2, so all six neighbours are one cell away. */
    static final float ROW_PITCH = 0.8660254f;
    /** Rows count this much more than columns when placing apps, which makes the field wider than tall. */
    static final float ROW_WEIGHT = 1.5f;

    /** Fisheye scale in the middle, at the edge, and the distance (in screen widths) where it reaches the edge. */
    static final float CENTRE_SCALE = 1.2f, EDGE_SCALE = 0.2f, EDGE_DISTANCE = 0.5f;
    /** Icons fade out from FADE_START and are gone beyond HIDE_DISTANCE (screen widths from the middle). */
    static final float FADE_START = 0.55f, HIDE_DISTANCE = 0.62f;
    /** Vertical distance counts this much more, so the icons above and below shrink a little sooner. */
    static final float VERTICAL_WEIGHT = 1.15f;

    final int count;
    final int[] row, col;
    final float[] x, y;

    Honeycomb(int count) {
        this.count = Math.max(0, count);
        row = new int[this.count];
        col = new int[this.count];
        x = new float[this.count];
        y = new float[this.count];
        if (this.count == 0) return;
        // A block of cells comfortably larger than the ellipse the apps fill, sorted by distance from the middle.
        int k = (int) Math.ceil(Math.sqrt(this.count)) + 1;
        int rows = 2 * k + 1, cols = 4 * k + 1;
        long[] keys = new long[rows * cols];
        int n = 0;
        for (int r = -k; r <= k; r++) {
            for (int c = -2 * k; c <= 2 * k; c++) {
                float cx = cellX(r, c), cy = r * ROW_PITCH * ROW_WEIGHT;
                // Distance first; on a tie the angle, so a ring fills in the same order every time.
                long dist = Math.round((cx * cx + cy * cy) * 1000);
                double angle = Math.atan2(-cy, cx);
                if (angle < 0) angle += 2 * Math.PI;
                long turn = Math.round(angle * 1000);
                keys[n++] = (dist << 32) | (turn << 16) | ((long) (r + k) << 8) | (c + 2 * k);
            }
        }
        Arrays.sort(keys);
        for (int i = 0; i < this.count; i++) {
            int r = (int) ((keys[i] >> 8) & 0xFF) - k, c = (int) (keys[i] & 0xFF) - 2 * k;
            row[i] = r;
            col[i] = c;
            x[i] = cellX(r, c);
            y[i] = r * ROW_PITCH;
        }
    }

    /** Odd rows sit half a cell to the right. */
    private static float cellX(int r, int c) {
        return c + (Math.floorMod(r, 2) == 1 ? 0.5f : 0f);
    }

    /**
     * The app the D-pad goes to from {@code from}, or -1 when there is none that way. Left and Right stay in the
     * row. Up and Down go to the nearest app in the next row; on a tie, right going down and left going up, so
     * Down then Up comes back to the same app.
     */
    int move(int from, int direction) {
        if (from < 0 || from >= count) return -1;
        if (direction == LEFT || direction == RIGHT) {
            int want = col[from] + (direction == RIGHT ? 1 : -1);
            for (int i = 0; i < count; i++) if (row[i] == row[from] && col[i] == want) return i;
            return -1;
        }
        int want = row[from] + (direction == DOWN ? 1 : -1);
        int best = -1;
        float bestGap = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (row[i] != want) continue;
            float gap = Math.abs(x[i] - x[from]);
            boolean tie = Math.abs(gap - bestGap) < 1e-3f;
            if (best < 0 || gap < bestGap - 1e-3f || tie && (direction == DOWN ? x[i] > x[best] : x[i] < x[best])) {
                best = i;
                bestGap = gap;
            }
        }
        return best;
    }

    /** Distance from the middle of the screen in screen widths, for offsets also given in screen widths. */
    static float distance(float dx, float dy) {
        float v = dy * VERTICAL_WEIGHT;
        return (float) Math.sqrt(dx * dx + v * v);
    }

    /** The fisheye: CENTRE_SCALE in the middle, falling with the square of the distance to EDGE_SCALE at the edge. */
    static float scale(float distance) {
        float t = distance / EDGE_DISTANCE;
        return Math.max(EDGE_SCALE, CENTRE_SCALE - (CENTRE_SCALE - EDGE_SCALE) * t * t);
    }

    /** 1 up to FADE_START, falling to 0 at HIDE_DISTANCE; an icon at 0 is not drawn. */
    static float alpha(float distance) {
        if (distance <= FADE_START) return 1f;
        if (distance >= HIDE_DISTANCE) return 0f;
        return (HIDE_DISTANCE - distance) / (HIDE_DISTANCE - FADE_START);
    }
}
