package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UsbPillTest {
    @Test public void theAppLeavesTheGridForItsPill() {
        assertTrue(UsbPill.leavesGrid("com.xiaomi.mitv.mediaexplorer", false));
    }

    @Test public void withTopMenuThereIsNoPillSoTheTileStays() {
        assertFalse(UsbPill.leavesGrid("com.xiaomi.mitv.mediaexplorer", true));
    }

    @Test public void noPackageHidesNothing() {
        assertFalse(UsbPill.leavesGrid("", false));
        assertFalse(UsbPill.leavesGrid(null, false));
    }
}
