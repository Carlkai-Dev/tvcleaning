package com.gathin.tvhome;

/** usb_pill_package: whether its app leaves the grid for the USB pill. */
final class UsbPill {
    private UsbPill() {}

    /**
     * Only when the pill can be shown. top_menu folds the bar into one gear with no USB row, so the app keeps its
     * tile there; otherwise it would have no way in from Home. An explicit hidden_apps entry hides it either way.
     */
    static boolean leavesGrid(String usbPillPackage, boolean topMenu) {
        return usbPillPackage != null && !usbPillPackage.isEmpty() && !topMenu;
    }
}
