package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.UserManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
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
 * With stock_home_redirect set, it also sends the stock launcher's home screen back here, including when firmware
 * continues its own Home startup after this launcher has already opened.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {
    private static final long BOOT_HOME_LAUNCH_DELAY_MS = 1800;
    private static final long HOME_KEY_LAUNCH_DELAY_MS = 250;
    private static final long STOCK_HOME_REDIRECT_RETRY_DELAY_MS = 700;
    private static final int MAX_STOCK_HOME_REDIRECT_ATTEMPTS = 3;

    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same maps InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();

    /** Waits for the user to unlock when Android binds this service at boot; null once the config is read. */
    private BroadcastReceiver unlockReceiver;
    private BroadcastReceiver bootHomeReceiver;
    /** The stock launcher's HOME activities (stock_home_redirect), disabled ones included. */
    private final Set<String> stockHome = new HashSet<>();
    private boolean bootLauncherRedirected;
    private boolean homeLongPress;
    private final Handler launchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingBootHomeLaunch;
    private Runnable pendingStockHomeRedirect;
    private int stockHomeRedirectAttempts;

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
        cancelBootHomeLaunch();
        cancelStockHomeRedirect();
        if (bootHomeReceiver != null) {
            unregisterReceiver(bootHomeReceiver);
            bootHomeReceiver = null;
        }
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
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        stockHome.clear();
        cancelStockHomeRedirect();
        bootLauncherRedirected = false;
        if (!config.stockHomeRedirect.isEmpty()) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(config.stockHomeRedirect);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                stockHome.add(ri.activityInfo.name);
            }
        }
        // Watch for a stock Home window as soon as it appears, rather than waiting for it to finish drawing.
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null && !stockHome.isEmpty()) {
            info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            setServiceInfo(info);
        }
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
        configureBootHome(config);
    }

    private void configureBootHome(Config config) {
        if (!config.restoreKeyServices || !config.launchHomeAfterBoot) {
            cancelBootHomeLaunch();
            if (bootHomeReceiver != null) {
                unregisterReceiver(bootHomeReceiver);
                bootHomeReceiver = null;
            }
            return;
        }
        if (bootHomeReceiver == null) {
            bootHomeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    launchHomeAfterBoot();
                }
            };
            IntentFilter filter = new IntentFilter(BootHomeReceiver.ACTION_SERVICE_READY);
            registerBootHomeReceiver(filter);
        }
        scheduleBootHomeLaunch();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBootHomeReceiver(IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bootHomeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bootHomeReceiver, filter);
        }
    }

    private void launchHomeAfterBoot() {
        if (!BootHomeReceiver.isPending(this)) return;
        if (bootLauncherRedirected) {
            if (!BootHomeReceiver.clearPending(this)) {
                Log.e("TvHomeKeyService", "Launcher was opened after boot, but pending state could not be cleared");
            }
            return;
        }
        scheduleBootHomeLaunch();
    }

    private void scheduleBootHomeLaunch() {
        if (!BootHomeReceiver.isPending(this) || pendingBootHomeLaunch != null) return;
        pendingBootHomeLaunch = () -> {
            pendingBootHomeLaunch = null;
            if (!BootHomeReceiver.isPending(this) || bootLauncherRedirected) return;
            if (!openLauncher()) {
                Log.e("TvHomeKeyService", "Could not open the launcher after boot; leaving pending state for recovery");
                return;
            }
            bootLauncherRedirected = true;
            if (!BootHomeReceiver.clearPending(this)) {
                Log.e("TvHomeKeyService", "Launcher was opened after boot, but pending state could not be cleared");
            }
        };
        launchHandler.postDelayed(pendingBootHomeLaunch, BOOT_HOME_LAUNCH_DELAY_MS);
    }

    private void cancelBootHomeLaunch() {
        if (pendingBootHomeLaunch == null) return;
        launchHandler.removeCallbacks(pendingBootHomeLaunch);
        pendingBootHomeLaunch = null;
    }

    private void scheduleStockHomeRedirect() {
        scheduleStockHomeRedirect(0);
    }

    private void scheduleStockHomeRedirect(long delayMs) {
        if (pendingStockHomeRedirect != null
                || stockHomeRedirectAttempts >= MAX_STOCK_HOME_REDIRECT_ATTEMPTS) return;
        pendingStockHomeRedirect = () -> {
            pendingStockHomeRedirect = null;
            stockHomeRedirectAttempts++;
            if (openLauncher()) {
                Log.i("TvHomeKeyService", "Requested CleanYourTV after stock Home appeared (attempt "
                        + stockHomeRedirectAttempts + ")");
                bootLauncherRedirected = true;
                cancelBootHomeLaunch();
                if (BootHomeReceiver.isPending(this) && !BootHomeReceiver.clearPending(this)) {
                    Log.e("TvHomeKeyService", "Launcher was redirected after boot, but pending state could not be cleared");
                }
            }
            if (stockHomeRedirectAttempts < MAX_STOCK_HOME_REDIRECT_ATTEMPTS) {
                scheduleStockHomeRedirect(STOCK_HOME_REDIRECT_RETRY_DELAY_MS);
            }
        };
        if (delayMs == 0) {
            launchHandler.post(pendingStockHomeRedirect);
        } else {
            launchHandler.postDelayed(pendingStockHomeRedirect, delayMs);
        }
    }

    private void cancelStockHomeRedirect() {
        if (pendingStockHomeRedirect != null) {
            launchHandler.removeCallbacks(pendingStockHomeRedirect);
            pendingStockHomeRedirect = null;
        }
        stockHomeRedirectAttempts = 0;
    }

    private boolean openLauncher() {
        Intent launcher = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(launcher);
        } catch (ActivityNotFoundException | SecurityException failure) {
            Log.e("TvHomeKeyService", "Could not open the launcher after boot", failure);
            return false;
        }
        return true;
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
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

    private void redirectHomeKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            homeLongPress = event.getRepeatCount() > 0;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (!event.isCanceled() && !homeLongPress) {
                launchHandler.postDelayed(() -> {
                    Log.i("TvHomeKeyService", "Opening CleanYourTV after the physical Home key");
                    openLauncher();
                }, HOME_KEY_LAUNCH_DELAY_MS);
            }
            homeLongPress = false;
        }
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (stockHome.isEmpty() || (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED)) return;
        CharSequence className = event.getClassName();
        if (className == null) return;
        boolean stockWindow = stockHome.contains(className.toString());
        if (!stockWindow) {
            cancelStockHomeRedirect();
            return;
        }
        scheduleStockHomeRedirect();
    }

    @Override
    public void onInterrupt() {
    }
}
