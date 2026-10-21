package com.gathin.tvhome;

import android.media.tv.TvInputManager;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HdmiVisibilityTest {
    @Test public void defaultKeepsInactivePortsAccessible() {
        assertTrue(HdmiVisibility.visible(false, TvInputManager.INPUT_STATE_CONNECTED_STANDBY));
        assertTrue(HdmiVisibility.visible(false, TvInputManager.INPUT_STATE_DISCONNECTED));
    }

    @Test public void optInHidesBothKindsOfInactivity() {
        assertFalse(HdmiVisibility.visible(true, TvInputManager.INPUT_STATE_CONNECTED_STANDBY));
        assertFalse(HdmiVisibility.visible(true, TvInputManager.INPUT_STATE_DISCONNECTED));
        assertTrue(HdmiVisibility.visible(true, TvInputManager.INPUT_STATE_CONNECTED));
    }

    @Test public void unknownOrSuppressedStateDoesNotHideAnInput() {
        assertTrue(HdmiVisibility.visible(true, -1));
        assertTrue(HdmiVisibility.visible(true, 3));
    }
}
