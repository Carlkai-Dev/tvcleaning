package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class FireTvHomePolicyTest {
    private FireTvHomePolicy policy() {
        return new FireTvHomePolicy("stock.home", new String[]{"stock.home.HomeActivity_vNext", "stock.home.HomeActivity"});
    }

    @Test public void homeWindowsQueueButOtherAppsAndStockUtilitiesCancel() {
        FireTvHomePolicy p = policy();
        assertEquals(1, p.window("stock.home", "stock.home.HomeActivity_vNext", true));
        assertEquals(-1, p.window("other.app", "stock.home.HomeActivity_vNext", true));
        assertFalse(p.request(true, 1000));
        assertEquals(1, p.window("stock.home", "stock.home.HomeActivity", true));
        assertEquals(-1, p.window("stock.home", "stock.home.SettingsActivity", true));
        assertFalse(p.request(true, 1200));
    }

    @Test public void childViewsDoNotGuessHomeOrEraseAnObservedHome() {
        FireTvHomePolicy p = policy();
        assertEquals(0, p.window("stock.home", "android.widget.FrameLayout", true));
        assertFalse(p.request(true, 1000));
        p.window("stock.home", "stock.home.HomeActivity", true);
        assertEquals(0, p.window("stock.home", "android.widget.FrameLayout", true));
        assertTrue(p.request(true, 1000));
    }

    @Test public void offSwitchAlsoCancelsAnAlreadyQueuedRequest() {
        FireTvHomePolicy p = policy();
        p.window("stock.home", "stock.home.HomeActivity", true);
        assertFalse(p.request(false, 1000));
        assertEquals(-1, p.window("stock.home", "stock.home.HomeActivity", false));
        assertFalse(p.request(true, 1200));
    }

    @Test public void otherStockWindowsCancelAsInTheDeviceTestedHelper() {
        FireTvHomePolicy p = policy();
        p.window("stock.home", "stock.home.HomeActivity", true);
        assertEquals(-1, p.window("stock.home", "stock.home.ui.AppLibrary_vNext", true));
        assertFalse(p.request(true, 1000));
    }

    @Test public void requestsAreBoundedButASecondHomeEventIsNotDroppedByCooldown() {
        FireTvHomePolicy p = policy();
        p.window("stock.home", "stock.home.HomeActivity", true);
        assertTrue(p.request(true, 1000));
        p.window("stock.home", "stock.home.HomeActivity", true);
        assertTrue(p.request(true, 1200));
        assertTrue(p.request(true, 1400));
        assertFalse(p.request(true, 1600));
        assertTrue(p.request(true, 6000));
    }
}
