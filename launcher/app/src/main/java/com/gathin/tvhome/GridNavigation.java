package com.gathin.tvhome;

import android.view.KeyEvent;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    /** The direction in app order a D-pad key moves: a right-to-left grid runs its order leftwards. Null for other keys. */
    static AppOrder.Direction direction(int keyCode, boolean rtl) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return rtl ? AppOrder.Direction.RIGHT : AppOrder.Direction.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return rtl ? AppOrder.Direction.LEFT : AppOrder.Direction.RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return AppOrder.Direction.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return AppOrder.Direction.DOWN;
            default: return null;
        }
    }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        AppOrder.Direction towards = direction(left ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT, rtl);
        return AppOrder.target(position, count, columns, towards) == position;
    }
}
