package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * Where a home screen from foreign_home_activities is installed (Fire OS, whose launcher is protected and
 * ignores set-home-activity), it brings this launcher back whenever that home screen comes to the front, and
 * takes KEYCODE_HOME where the firmware lets it through. Fire OS 7 does not: it starts its home before the
 * key reaches accessibility, and Android then holds activity starts back for about five seconds, so an
 * overlay with a still of this launcher (HomeSnapshot; black before there is one) covers that home screen
 * until this launcher is in front. Disabling the service undoes it.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    /** Installed home screens from foreign_home_activities; empty on TVs where the stock launcher can be replaced. */
    private List<ComponentName> foreignHomes = Collections.emptyList();
    /** Covers the foreign home while this launcher's start is held back; null when not showing. */
    private View cover;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideCover = this::hideCover;
    /** Longer than the app-switch hold after Home (5 s), so the cover never outlives a launcher that fails to start. */
    private static final long COVER_TIMEOUT_MS = 7000;
    /** Set by the Inputs pill: the foreign home stays until something else comes to the front (its Inputs or
     *  Live tab opening an HDMI port), since only its own apps may switch inputs on Fire OS (TUNE_INPUT). */
    private static volatile boolean letThrough;
    /** The foreign home is in front, so Back on it returns to this launcher. */
    private boolean foreignInFront;

    /** The first installed home screen from foreign_home_activities, or null on TVs that have none. */
    static ComponentName foreignHome(android.content.Context ctx) {
        List<ComponentName> homes = installedForeignHomes(ctx);
        return homes.isEmpty() ? null : homes.get(0);
    }

    /** The next time the foreign home comes to the front, leave it there (see letThrough). */
    static void letForeignHomeThrough() {
        letThrough = true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        foreignHomes = installedForeignHomes(this);
        if (!foreignHomes.isEmpty()) {
            // Window changes only where a foreign home exists; everywhere else the service stays keys-only.
            AccessibilityServiceInfo info = getServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.packageNames = null;
            setServiceInfo(info);
        }
    }

    private static List<ComponentName> installedForeignHomes(android.content.Context ctx) {
        List<ComponentName> found = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        for (String entry : ctx.getResources().getStringArray(R.array.foreign_home_activities)) {
            ComponentName home = ComponentName.unflattenFromString(entry.trim());
            if (home == null) continue;
            try {
                if (pm.getApplicationInfo(home.getPackageName(), 0).enabled) found.add(home);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return found;
    }

    private void showCover() {
        if (cover == null) {
            // A still of Home as it was left makes Home look instant; black until there is one (after boot).
            Bitmap still = HomeSnapshot.get();
            if (still != null) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.FIT_XY);
                image.setImageBitmap(still);
                cover = image;
            } else {
                cover = new View(this);
            }
            cover.setBackgroundColor(Color.BLACK);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.OPAQUE);
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(cover, lp);
            } catch (RuntimeException e) {
                cover = null;
                return;
            }
        }
        handler.removeCallbacks(hideCover);
        handler.postDelayed(hideCover, COVER_TIMEOUT_MS);
    }

    private void hideCover() {
        handler.removeCallbacks(hideCover);
        if (cover == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        } catch (RuntimeException ignored) {
        }
        cover = null;
    }

    /** Brings this launcher to the front. As Home (asHome), it goes back to the first tile as a Home press does;
     *  replacing a foreign home it keeps the last focused tile, which is also what the cover's still shows. */
    private void openLauncher(boolean asHome) {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (asHome) home.addCategory(Intent.CATEGORY_HOME);
        else home.setClass(this, MainActivity.class);
        startActivity(home);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Keys would reach the foreign home under the cover, where nobody can see what they select.
        if (cover != null) return true;
        // Reached through the Inputs pill: Back leaves the foreign home for this launcher.
        if (foreignInFront && letThrough && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                letThrough = false;
                openLauncher(false);
            }
            return true;
        }
        // A scancode entry is more specific than any keycode, so it goes first.
        String scanApp = appScancodes.get(event.getScanCode());
        if (scanApp != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, scanApp);
            return true;
        }
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }
        boolean home = event.getKeyCode() == KeyEvent.KEYCODE_ALL_APPS
                || (event.getKeyCode() == KeyEvent.KEYCODE_HOME && !foreignHomes.isEmpty());
        if (!home) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openLauncher(true);
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (foreignHomes.isEmpty() || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;
        if (getPackageName().contentEquals(pkg)) {
            // One of this launcher's activities, not the cover itself (which reports as android.view.View).
            if (cls.toString().startsWith(getPackageName() + ".")) {
                hideCover();
                foreignInFront = false;
            }
            return;
        }
        for (ComponentName foreign : foreignHomes) {
            if (foreign.getPackageName().contentEquals(pkg)) {
                if (!foreign.getClassName().contentEquals(cls)) return;   // the foreign home's own menus and dialogs
                foreignInFront = true;
                if (letThrough) return;
                showCover();
                openLauncher(false);
                return;
            }
        }
        // Another app's activity came to the front, e.g. the HDMI player started from the foreign home's Inputs
        // tab. Popups and overlays (volume, toasts) report a view class instead and leave things as they are.
        if (isActivity(pkg.toString(), cls.toString())) {
            foreignInFront = false;
            letThrough = false;
        }
    }

    private boolean isActivity(String pkg, String cls) {
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        hideCover();
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }
}
