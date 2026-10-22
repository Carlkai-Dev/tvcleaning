package io.github.epochcoder.homelab;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Making the Immich slideshow the TV's screensaver from inside the app. Android keeps the choice in
 * two secure settings, which only an app holding WRITE_SECURE_SETTINGS may write; a TV grants that
 * only over ADB ({@code pm grant}, which build-and-install.sh does), so without it Settings sends
 * the viewer to the TV's own screensaver picker instead.
 */
final class Screensaver {
    private static final String TAG = "Homelab.Screensaver";
    // Settings.Secure.SCREENSAVER_COMPONENTS and SCREENSAVER_ENABLED: hidden API, stable names.
    private static final String COMPONENTS = "screensaver_components";
    private static final String ENABLED = "screensaver_enabled";

    private Screensaver() {}

    static String component(Context ctx) {
        return new ComponentName(ctx, PhotoDream.class).flattenToString();
    }

    static boolean canSet(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Whether PhotoDream is the screensaver now; false too when the TV will not say. */
    static boolean isOurs(Context ctx) {
        try {
            String current = Settings.Secure.getString(ctx.getContentResolver(), COMPONENTS);
            return current != null && current.split(",", -1)[0].equals(component(ctx));
        } catch (SecurityException e) {
            Log.i(TAG, "cannot read the screensaver choice: " + e.getMessage());
            return false;
        }
    }

    /** Selects PhotoDream and switches the screensaver on; needs {@link #canSet}. */
    static void select(Context ctx) {
        Settings.Secure.putString(ctx.getContentResolver(), COMPONENTS, component(ctx));
        Settings.Secure.putInt(ctx.getContentResolver(), ENABLED, 1);
    }
}
