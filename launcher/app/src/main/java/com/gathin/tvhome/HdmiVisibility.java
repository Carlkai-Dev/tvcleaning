package com.gathin.tvhome;

import android.media.tv.TvInputManager;

/** Policy for bare HDMI ports; known CEC devices keep their wake-up entry. */
final class HdmiVisibility {
    private HdmiVisibility() {}

    static boolean visible(boolean hideInactive, int state) {
        return !hideInactive || (state != TvInputManager.INPUT_STATE_CONNECTED_STANDBY
                && state != TvInputManager.INPUT_STATE_DISCONNECTED);
    }
}
