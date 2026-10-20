package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Dark or light look. The colours live in values/colors.xml (dark) and values-notnight/colors.xml
 * (light); an activity picks one by pinning the night flag of its own configuration, whatever the TV says.
 */
final class UiMode {
    static final String DARK = "dark";
    static final String LIGHT = "light";

    private UiMode() {
    }

    static boolean light(Context c) {
        return LIGHT.equals(Prefs.getString(c, Prefs.UI_MODE, DARK));
    }

    /** For attachBaseContext: the same context with the chosen look pinned. */
    static Context wrap(Context base) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        int night = light(base) ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(cfg);
    }
}
