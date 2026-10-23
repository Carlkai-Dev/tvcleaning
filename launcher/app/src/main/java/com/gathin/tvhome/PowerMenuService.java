package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Restarts the TV for the top bar's power button. An ordinary app cannot reboot the TV: PowerManager.reboot needs the
 * signature permission REBOOT, and DevicePolicyManager.reboot a device owner, which a TV signed in to a Google account
 * cannot have. An accessibility service may open Android's power menu, though, and press its Restart item. The owner
 * has already said yes in the launcher's own prompt, so the service presses it for them: on some firmware that menu
 * is a phone layout the D-pad cannot reach (Philips, Android 9).
 *
 * It reads window content only for a few seconds after the owner chose Restart, looking for the item labelled with
 * Android's own "Restart" string; it subscribes to no events and filters no keys (power_menu_service.xml). If the
 * item is not found the menu simply stays open. Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.PowerMenuService
 *   settings put secure accessibility_enabled 1
 * (append with a colon if other services are already listed there).
 */
public class PowerMenuService extends AccessibilityService {

    private static final long SEARCH_MS = 4000;
    private static final long POLL_MS = 150;

    private static WeakReference<PowerMenuService> running = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long searchUntil;

    /** Opens the power menu and presses its Restart item; false when the service is not enabled or the system refused. */
    static boolean restart() {
        PowerMenuService service = running.get();
        if (service == null || !service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) return false;
        service.searchUntil = SystemClock.uptimeMillis() + SEARCH_MS;
        service.handler.removeCallbacksAndMessages(null);
        service.handler.postDelayed(service::pressRestart, POLL_MS);
        return true;
    }

    private void pressRestart() {
        // Android's own "Restart", in the TV's current language: the menu is drawn with the same string.
        int id = Resources.getSystem().getIdentifier("global_action_restart", "string", "android");
        String label = id == 0 ? null : getString(id);
        if (label != null) {
            for (AccessibilityWindowInfo window : getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                // Skip Home itself: the power button's description is the same word.
                if (root == null || getPackageName().contentEquals(root.getPackageName())) continue;
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(label);
                for (AccessibilityNodeInfo node : found) {
                    for (AccessibilityNodeInfo n = node; n != null; n = n.getParent()) {
                        if (n.isClickable()) {
                            if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                            break;
                        }
                    }
                }
            }
        }
        if (SystemClock.uptimeMillis() < searchUntil) handler.postDelayed(this::pressRestart, POLL_MS);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = new WeakReference<>(this);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        handler.removeCallbacksAndMessages(null);
        running = new WeakReference<>(null);
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
