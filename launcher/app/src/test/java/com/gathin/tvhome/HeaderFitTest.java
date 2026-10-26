package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HeaderFitTest {
    @Test public void aHeaderWithRoomIsLeftAlone() {
        assertEquals(HeaderFit.NONE, HeaderFit.next(false, true, false));
        assertEquals(HeaderFit.NONE, HeaderFit.next(false, false, true));
    }

    @Test public void theLabelGoesFirst() {
        assertEquals(HeaderFit.FOLD_LABEL, HeaderFit.next(true, true, false));
        assertEquals(HeaderFit.FOLD_LABEL, HeaderFit.next(true, true, true));
    }

    @Test public void thenTheDateMovesBelowTheClock() {
        assertEquals(HeaderFit.DATE_BELOW, HeaderFit.next(true, false, false));
    }

    @Test public void aDateAlreadyBelowHasNothingLeftToGive() {
        assertEquals(HeaderFit.NONE, HeaderFit.next(true, false, true));
    }
}
