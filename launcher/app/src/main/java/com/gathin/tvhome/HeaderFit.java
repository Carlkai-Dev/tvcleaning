package com.gathin.tvhome;

/**
 * What the header gives up, one step at a time, when the USB pill leaves the clock and date too little room: a date
 * cut short ("MONDAY 26...") or a clock wrapped onto two lines. First the pill drops its label and keeps the glyph,
 * then the date moves below the clock, as date_below_clock lays it out. A header with room changes nothing.
 */
final class HeaderFit {
    static final int NONE = 0;
    static final int FOLD_LABEL = 1;
    static final int DATE_BELOW = 2;

    private HeaderFit() {}

    static int next(boolean squeezed, boolean pillHasLabel, boolean dateBelowClock) {
        if (!squeezed) return NONE;
        if (pillHasLabel) return FOLD_LABEL;
        return dateBelowClock ? NONE : DATE_BELOW;
    }
}
