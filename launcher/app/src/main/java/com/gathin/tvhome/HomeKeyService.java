package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * With stock_home_redirect set, it also sends the stock launcher's home screen back here, for firmware that turns
 * that launcher on again after it was disabled (XGIMI's xrmservice does, on its own remote keys), or that cannot be
 * disabled at all, and a short press of Home opens this launcher even when the stock home is already on screen.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private boolean consumeVolumeKeys;
    /** app_keys, the same maps InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();

    /** Waits for the user to unlock when Android binds this service at boot; null once the config is read. */
    private BroadcastReceiver unlockReceiver;
    /** The stock launcher's HOME activities (stock_home_redirect), disabled ones included. */
    private final Set<String> stockHome = new HashSet<>();
    /** Stops key filtering while a game controller is connected; null unless gamepad_passthrough and key_filter, or before the unlock. */
    private GamepadPassthrough gamepads;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // The service is directBootAware: Android 11 skips a service that is not while the user is still
        // locked at boot, writes accessibility_enabled=0 and, on TCL G03 firmware, never binds it after the
        // unlock. Config.load reads credential-protected storage, so wait for the unlock before reading it.
        UserManager users = getSystemService(UserManager.class);
        if (users.isUserUnlocked()) {
            loadConfig();
            return;
        }
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopWaitingForUnlock();
                loadConfig();
            }
        };
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked()) {
            stopWaitingForUnlock();
            loadConfig();
        }
    }

    private void stopWaitingForUnlock() {
        if (unlockReceiver == null) return;
        unregisterReceiver(unlockReceiver);
        unlockReceiver = null;
    }

    @Override
    public void onDestroy() {
        stopWaitingForUnlock();
        stopWatch();
        if (gamepads != null) gamepads.stop();
        if (consumeVolumeKeys) VolumeKeys.stop();
        super.onDestroy();
    }

    private void loadConfig() {
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop. Here, not
        // in onServiceConnected: the note is in credential-protected storage too, readable only after the unlock.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        consumeVolumeKeys = config.consumeVolumeKeys;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        stockHome.clear();
        if (!config.stockHomeRedirect.isEmpty()) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(config.stockHomeRedirect);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                stockHome.add(ri.activityInfo.name);
            }
        }
        // home_key_service.xml subscribes to no events, so the redirect asks for window changes only when it is on.
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null && !stockHome.isEmpty()) {
            info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            setServiceInfo(info);
        }
        if (!stockHome.isEmpty()) {
            getSharedPreferences("stock", MODE_PRIVATE).edit()
                    .putString("stock_pkg", config.stockHomeRedirect).apply();
            redirectPkg = config.stockHomeRedirect;
            probeAtBoot();
            startWatch();
        } else stopWatch();
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
        // gamepad_passthrough, off by default. Only with key_filter on: off, the service never filters keys.
        if (config.gamepadPassthrough && config.keyFilter && gamepads == null) {
            gamepads = new GamepadPassthrough(this);
            gamepads.start();
        }
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (consumeVolumeKeys && VolumeKeys.handle(this, event)) return true;
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, appScancodes.get(event.getScanCode()),
                holdAppScancodes.get(event.getScanCode()))) return true;
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : SystemSettings.intent(this, Settings.ACTION_SETTINGS);
                try {
                    startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        if (RemoteKeys.handle(this, event, appKeys.get(event.getKeyCode()),
                holdAppKeys.get(event.getKeyCode()))) return true;
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && !stockHome.isEmpty()) {
            redirectHomeKey(event);
            return false;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // stock_home_redirect: the firmware turned the stock launcher back on and its home screen came up.
        if (stockHome.isEmpty() || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = event.getClassName();
        if (cls != null && stockHome.contains(cls.toString())) openHome();
        // Any other window change (an app closing onto the stock home, a dialog going away) is a
        // moment the stock home can follow without a window event of its own: open a watch window.
        else startWatch();
    }

    /** How long after boot the first bind still counts as "at boot" for probeAtBoot. */
    static final long BOOT_WINDOW_MS = 3 * 60 * 1000L;

    /** stock_home_redirect at boot: the stock home is often on screen before Android binds this service, so no
     *  window event for it arrives (#1116). Once per boot, open ProbeActivity: when it closes, whatever was in
     *  front comes back and sends a window event, and onAccessibilityEvent covers it only if it is the stock home.
     *  An HDMI input or an app that the TV resumed at boot is left alone. */
    private void probeAtBoot() {
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences prefs = getSharedPreferences("boot", MODE_PRIVATE);
        if (!firstBindAfterBoot(boot, prefs.getInt("probe_boot", -1), SystemClock.elapsedRealtime())) return;
        prefs.edit().putInt("probe_boot", boot).apply();
        try {
            startActivity(new Intent(this, ProbeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    static boolean firstBindAfterBoot(int boot, int lastBoot, long uptimeMs) {
        return boot >= 0 && boot != lastBoot && uptimeMs < BOOT_WINDOW_MS;
    }

    /** Lets the system see the key, so a long press still does what the firmware gives it. */
    private static final long HOME_KEY_DELAY_MS = 250;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** stock_home_redirect on the Home key: when the stock home is already on screen (it came up at boot, before
     *  Android bound this service), Home brings it up again with no window change, so the redirect above never
     *  fires and the TV stays on the stock home until another app opens (#1116). A short press opens this
     *  launcher shortly after the system has handled the key; a long press (Google TV's quick settings) is left
     *  alone. Held time, not repeats: key repeats are made after accessibility filtering, so none arrive here. */
    private void redirectHomeKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()
                && isShortPress(event.getDownTime(), event.getEventTime(), ViewConfiguration.getLongPressTimeout())) {
            handler.postDelayed(this::openHome, HOME_KEY_DELAY_MS);
        }
    }

    static boolean isShortPress(long downTime, long upTime, long longPressTimeout) {
        return upTime - downTime < longPressTimeout;
    }

    private void openHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    @Override
    public void onInterrupt() {
    }

    // ---- stock_home_redirect by usage stats, over short event-driven watch windows ----
    // Firmwares that deliver no window-change event for the stock launcher's home screen (Hisense
    // VIDAA, the Vidda C1S projector, MT9669, Android 9) stay blind to onAccessibilityEvent above,
    // while usage stats still record its MOVE_TO_FOREGROUND with the right class — as they do for
    // Xiaomi's PatchWall up to Android 6. The sampling never runs on a schedule of its own: each
    // trigger (config bound, a window event, screen on, dream stopped, system dialogs closed)
    // opens a watch window that samples usage stats once a second and stops, so nothing runs while
    // the TV just sits on a screen. The key filter stays off, so VIDAA's own volume handling is
    // untouched.

    private static final long POLL_MS = 1000L;
    private static final long WATCH_MS = 10_000L;
    /** A window can start long after the stock home came up (screen on over an auto-raised home),
     *  so each sample reaches back far enough to see that transition. */
    private static final long LOOKBACK_MS = 90_000L;
    private boolean watchRegistered;
    private boolean usageStatsDeniedLogged;
    private long watchUntil;
    private String lastFrontPkg;
    private String lastFrontCls;
    private long broughtBackAt;
    private String redirectPkg;

    /** Whether usage stats may be read (GET_USAGE_STATS, granted over adb); see startWatch. */
    private boolean usageStatsAllowed() {
        AppOpsManager appOps = getSystemService(AppOpsManager.class);
        return appOps != null && appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** Opens a watch window: usage stats are sampled once a second for WATCH_MS, then the handler
     *  goes quiet until the next trigger. Re-triggers while a window is open only extend it. */
    private void startWatch() {
        if (stockHome.isEmpty()) return;
        if (!watchRegistered) {
            // All protected system broadcasts: on this firmware (Android 9) the plain
            // registration receives them; no export flag is needed.
            IntentFilter triggers = new IntentFilter();
            triggers.addAction(Intent.ACTION_SCREEN_ON);
            triggers.addAction(Intent.ACTION_DREAMING_STOPPED);
            triggers.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            registerReceiver(watchReceiver, triggers);
            watchRegistered = true;
        }
        if (!usageStatsAllowed()) {
            if (!usageStatsDeniedLogged) {
                usageStatsDeniedLogged = true;
                Log.i("TvHome.HomeKey", "usage stats not allowed; stock_home_redirect stays event-only");
            }
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now < watchUntil) {
            watchUntil = now + WATCH_MS;
            return;
        }
        watchUntil = now + WATCH_MS;
        handler.post(pollForeground);
    }

    private void stopWatch() {
        watchUntil = 0;
        if (watchRegistered) {
            try {
                unregisterReceiver(watchReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            watchRegistered = false;
        }
    }

    /** The package and class of the latest MOVE_TO_FOREGROUND within [lookbackMs], or null. */
    @SuppressWarnings("deprecation")
    private String[] latestForeground(long lookbackMs) {
        UsageStatsManager stats = getSystemService(UsageStatsManager.class);
        if (stats == null) return null;
        long now = System.currentTimeMillis();
        UsageEvents events = stats.queryEvents(now - lookbackMs, now);
        UsageEvents.Event event = new UsageEvents.Event();
        String pkg = null;
        String cls = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && event.getClassName() != null) {
                pkg = event.getPackageName();
                cls = event.getClassName();
            }
        }
        if (pkg == null || cls == null) return null;
        return new String[]{pkg, cls};
    }

    private final Runnable pollForeground = new Runnable() {
        @Override public void run() {
            if (SystemClock.uptimeMillis() >= watchUntil) return;
            handler.postDelayed(this, POLL_MS);
            if (stockHome.isEmpty()) return;
            String[] front = latestForeground(LOOKBACK_MS);
            if (front == null) return;
            lastFrontPkg = front[0];
            lastFrontCls = front[1];
            if (!stockHome.contains(front[1]) || !front[0].equals(redirectPkg)) return;
            if (SystemClock.uptimeMillis() - broughtBackAt < 1000) return;
            if (SystemClock.uptimeMillis() < getSharedPreferences("stock", MODE_PRIVATE)
                    .getLong("stock_allow_until", 0)) return;
            broughtBackAt = SystemClock.uptimeMillis();
            Log.i("TvHome.HomeKey", "stock launcher in front by usage stats; opening Home");
            openHome();
        }
    };

    /** The triggers: system events that often precede, or mark, the stock home coming up on its own.
     *  A Home key among them clears a deliberate-open allowance and brings the launcher back when
     *  the stock home is already in front. */
    private final BroadcastReceiver watchReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if ("homekey".equals(intent.getStringExtra("reason"))) {
                getSharedPreferences("stock", MODE_PRIVATE).edit().putLong("stock_allow_until", 0).apply();
                if (lastFrontCls != null && stockHome.contains(lastFrontCls)) {
                    broughtBackAt = SystemClock.uptimeMillis();
                    openHome();
                }
            }
            startWatch();
        }
    };
}
