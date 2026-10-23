package com.gathin.tvhome;

/**
 * The "Big words" layout's rules (layout "words"), kept free of Android so they can be tested on the JVM: one
 * column of app names, Up and Down move through it, and a name fades with its distance from the focused one.
 * {@link WordsView} draws it.
 */
final class Words {
    private Words() {}

    /** Alpha one row from the focus, and the floor the rows further away fade down to. */
    static final float NEXT_ALPHA = 0.45f, FAR_ALPHA = 0.12f;
    /** How much each further row fades. */
    static final float ROW_FADE = 0.06f;

    /** Up and Down step through the list; Left, Right and the ends go nowhere (-1). */
    static int move(int from, int direction, int count) {
        if (from < 0 || from >= count) return -1;
        if (direction == Honeycomb.UP) return from > 0 ? from - 1 : -1;
        if (direction == Honeycomb.DOWN) return from < count - 1 ? from + 1 : -1;
        return -1;
    }

    /** 1 for the focused row, NEXT_ALPHA one row away, then fading by ROW_FADE a row down to FAR_ALPHA. */
    static float alpha(float rows) {
        float d = Math.abs(rows);
        if (d <= 1f) return 1f - (1f - NEXT_ALPHA) * d;
        return Math.max(FAR_ALPHA, NEXT_ALPHA - ROW_FADE * (d - 1f));
    }
}
