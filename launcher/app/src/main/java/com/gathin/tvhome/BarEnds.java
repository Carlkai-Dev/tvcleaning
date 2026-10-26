package com.gathin.tvhome;

/** Which top-bar button sits at the bar's start, the end Settings is not on (the USB pill, else Inputs, ...). */
final class BarEnds {
    private BarEnds() {}

    /**
     * The index of the first shown button in bar order, or -1 when none is shown. The Hebrew bar
     * (rtl_menu_bar) lists its buttons the other way round, so there the start is the last shown child.
     */
    static int start(boolean[] shown, boolean mirrored) {
        int n = shown.length;
        for (int k = 0; k < n; k++) {
            int i = mirrored ? n - 1 - k : k;
            if (shown[i]) return i;
        }
        return -1;
    }
}
