package com.gathin.tvhome;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * The text of the battery pill (config.xml battery_pill): the level as a percentage in the launcher's locale, so
 * French reads "18 %" and English "18%", from the level and scale ACTION_BATTERY_CHANGED carries.
 */
final class BatteryPill {

    private BatteryPill() {
    }

    /** True when the broadcast carried a usable level; a set without a battery reports -1 or a scale of 0. */
    static boolean hasLevel(int level, int scale) {
        return level >= 0 && scale > 0;
    }

    static String label(int level, int scale, Locale locale) {
        int percent = Math.max(0, Math.min(100, Math.round(100f * level / scale)));
        NumberFormat format = NumberFormat.getPercentInstance(locale);
        format.setMaximumFractionDigits(0);
        return format.format(percent / 100.0);
    }
}
