package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.os.Build;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remote buttons that only the stock launcher answered, for InputKeyService and HomeKeyService: app_keys
 * (config.xml or a theme) maps a keycode to the app it opens, as "190:com.google.android.youtube.tv" or
 * "KEYCODE_BUTTON_3:com.google.android.youtube.tv", or a scancode as "scan:490:com.wbd.hbomax". A "hold:"
 * prefix makes an entry the long-press action instead: "hold:KEYCODE_BUTTON_8:org.xbmc.kodi" opens Kodi when
 * that button is held for HOLD_MS, while the unprefixed entry still opens on a tap; "hold:scan:490:..." does
 * the same for a scancode. Prefix it with "hold:3000ms:" to set a per-entry hold time. One set of maps for both
 * services; Android hands a key to every service that filters,
 * so both may call handle() for one event, and it answers each press once.
 * A target of "input:<input id>" switches to that TV input (an HDMI port) instead of opening an app, a target
 * of "inputs" opens the input switcher, for a remote whose source button sends a key Android does not map,
 * one of the action words (back, home, recents, mute, sleep, screensaver, settings, wifi) does that action, and
 * "assistant:<text>" sends a fixed text query to Android's Assistant, and "package/.Activity" opens that
 * activity rather than the app's launcher entry (an add-on's quick bar, say).
 */
final class RemoteKeys {

    /** How long a button is held before its "hold:" entry wins (Android's long-press timeout). */
    static final long HOLD_MS = 500;

    /** The app_keys target that opens the input switcher, as "scan:365:inputs". */
    static final String INPUTS = "inputs";

    /** Pre-compiled regex patterns to avoid re-compiling Patterns on every entry during key parsing. */
    private static final Pattern NUMERIC_KEY = Pattern.compile("[0-9]{1,6}");
    private static final Pattern KEYCODE_KEY = Pattern.compile("KEYCODE_[A-Z0-9_]+");
    private static final Pattern HOLD_DURATION = Pattern.compile("^([0-9]{1,6})ms:(.+)$");

    private RemoteKeys() { }

    /** keycode -> package, with key names resolved by Android. */
    static Map<Integer, String> parseAppKeys(String[] entries) {
        return parseAppKeys(Arrays.asList(entries), KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseAppKeys(Iterable<String> entries) {
        return parseAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    /**
     * keycode -> package. The key is a number or a KEYCODE_ name (resolved by {@code names}; 0, KEYCODE_UNKNOWN,
     * means it isn't one). Entries without a colon, with an unknown key or with no package are skipped; for a
     * keycode listed twice the last entry wins.
     */
    static Map<Integer, String> parseAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split(":", 2);
            if (parts.length != 2 || parts[1].trim().isEmpty()) continue;
            String key = parts[0].trim();
            int code;
            if (NUMERIC_KEY.matcher(key).matches()) {
                code = Integer.parseInt(key);
            } else if (KEYCODE_KEY.matcher(key).matches()) {
                code = names.applyAsInt(key);   // keyCodeFromString takes digits only from API 29, names always
            } else {
                continue;
            }
            if (code > 0) keys.put(code, parts[1].trim());
        }
        return keys;
    }

    /** keycode -> package from the "hold:" entries; the rest of each entry reads as it does in parseAppKeys. */
    static Map<Integer, String> parseHoldAppKeys(String[] entries) {
        return parseHoldAppKeys(Arrays.asList(entries), KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseHoldAppKeys(Iterable<String> entries) {
        return parseHoldAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseHoldAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        return parseAppKeys(holdEntries(entries), names);
    }

    static Map<Integer, Long> parseHoldAppKeyDelays(Iterable<String> entries, ToIntFunction<String> names) {
        Map<Integer, Long> delays = new HashMap<>();
        for (HoldEntry entry : parsedHoldEntries(entries)) {
            for (Integer key : parseAppKeys(Arrays.asList(entry.mapping), names).keySet()) {
                delays.put(key, entry.delayMs);
            }
        }
        return delays;
    }

    /** keycode -> target from the "front:" entries, used while the button's own app is already in front. */
    static Map<Integer, String> parseFrontAppKeys(Iterable<String> entries) {
        return parseFrontAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseFrontAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        return parseAppKeys(prefixed(entries, "front:"), names);
    }

    static Map<Integer, String> parseFrontAppScancodes(Iterable<String> entries) {
        return parseAppScancodes(prefixed(entries, "front:"));
    }

    /**
     * Applies the key_filter choice (config.xml or a theme): when false the service drops
     * FLAG_REQUEST_FILTER_KEY_EVENTS at runtime, so it stays enabled for nothing but stops filtering.
     * Some firmware (TCL Google TV on Android 12) drops key auto-repeat once ANY service filters keys:
     * long-pressing volume then steps once per press. Filtering can only be skipped while the service
     * runs, since the flag is also declared in the manifest metadata.
     */
    static void applyKeyFilter(AccessibilityService service, boolean keyFilter) {
        if (keyFilter) return;
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) return;
        info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        service.setServiceInfo(info);
    }

    /**
     * scancode -> package, from the "scan:490:com.wbd.hbomax" entries of the same list. For remotes that send
     * several app buttons as one keycode and tell them apart by scancode (Sony BRAVIA: YouTube, Prime Video and
     * Disney+ are all keycode 196, scancodes 485, 490 and 491). Find it with `getevent -lt` while pressing the
     * button: the EV_KEY code, in hex. Other entries are skipped; for a scancode listed twice the last entry wins.
     */
    static Map<Integer, String> parseAppScancodes(Iterable<String> entries) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split(":", 3);
            if (parts.length != 3 || !parts[0].trim().equals("scan") || parts[2].trim().isEmpty()) continue;
            String code = parts[1].trim();
            if (!NUMERIC_KEY.matcher(code).matches()) continue;
            int scan = Integer.parseInt(code);
            if (scan > 0) keys.put(scan, parts[2].trim());
        }
        return keys;
    }

    /** scancode -> package from the "hold:scan:..." entries; the rest reads as it does in parseAppScancodes. */
    static Map<Integer, String> parseHoldAppScancodes(Iterable<String> entries) {
        return parseAppScancodes(holdEntries(entries));
    }

    static Map<Integer, Long> parseHoldAppScancodeDelays(Iterable<String> entries) {
        Map<Integer, Long> delays = new HashMap<>();
        for (HoldEntry entry : parsedHoldEntries(entries)) {
            for (Integer scan : parseAppScancodes(Arrays.asList(entry.mapping)).keySet()) {
                delays.put(scan, entry.delayMs);
            }
        }
        return delays;
    }

    /** The entries with this prefix ("hold:", "front:"), the prefix removed, so the normal parsers can read them. */
    private static Iterable<String> prefixed(Iterable<String> entries, String prefix) {
        List<String> found = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String text = entry.trim();
            if (text.startsWith(prefix)) found.add(text.substring(prefix.length()).trim());
        }
        return found;
    }

    private static final class HoldEntry {
        final String mapping;
        final long delayMs;

        HoldEntry(String mapping, long delayMs) {
            this.mapping = mapping;
            this.delayMs = delayMs;
        }
    }

    private static List<HoldEntry> parsedHoldEntries(Iterable<String> entries) {
        List<HoldEntry> found = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String text = entry.trim();
            if (!text.startsWith("hold:")) continue;
            String mapping = text.substring("hold:".length()).trim();
            Matcher duration = HOLD_DURATION.matcher(mapping);
            if (!duration.matches()) {
                found.add(new HoldEntry(mapping, HOLD_MS));
                continue;
            }
            long delay;
            try {
                delay = Long.parseLong(duration.group(1));
            } catch (NumberFormatException invalid) {
                continue;
            }
            if (delay > 0) found.add(new HoldEntry(duration.group(2).trim(), delay));
        }
        return found;
    }

    private static List<String> holdEntries(Iterable<String> entries) {
        List<String> mappings = new ArrayList<>();
        for (HoldEntry entry : parsedHoldEntries(entries)) mappings.add(entry.mapping);
        return mappings;
    }

    /** The package of a "guide:<package>" target, or null for any other target. */
    static String guidePackage(String target) {
        if (target == null || !target.startsWith("guide:")) return null;
        String pkg = target.substring(6).trim();
        return pkg.isEmpty() ? null : pkg;
    }

    /** The fixed query from an "assistant:<text>" target, or null when the target is not one. */
    static String assistantQuery(String target) {
        if (target == null || !target.startsWith("assistant:")) return null;
        String query = target.substring("assistant:".length()).trim();
        return query.isEmpty() ? null : query;
    }

    /**
     * True when a tap's target is the app already in front ("package" or "package/.Activity"), so its "front:"
     * entry applies instead. Inputs and action words never match.
     */
    static boolean inFront(String target, String front) {
        if (target == null || front == null || front.isEmpty()) return false;
        int slash = target.indexOf('/');
        return (slash < 0 ? target : target.substring(0, slash)).equals(front);
    }

    /** Created on first use: building a Handler while the class loads breaks the JVM tests. */
    private static Presses presses;

    /**
     * Consumes a key mapped by either list: the hold app opens once the button has been down for HOLD_MS, the
     * normal one on an earlier release. False means neither list has this key, so the caller can pass it on.
     */
    static boolean handle(Context ctx, KeyEvent event, String app, String holdApp) {
        return handle(ctx, event, app, holdApp, HOLD_MS);
    }

    static boolean handle(Context ctx, KeyEvent event, String app, String holdApp, long holdDelayMs) {
        if (app == null && holdApp == null) return false;
        if (presses == null) {
            Handler handler = new Handler(Looper.getMainLooper());
            presses = new Presses(new Presses.Timer() {
                @Override public void post(Runnable hold, long delayMs) { handler.postDelayed(hold, delayMs); }
                @Override public void cancel(Runnable hold) { handler.removeCallbacks(hold); }
            });
        }
        String button = event.getDeviceId() + ":" + event.getKeyCode() + ":" + event.getScanCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            presses.down(button, event.getEventTime(), event.getRepeatCount(), holdApp, holdDelayMs,
                    target -> launch(ctx, target));
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            presses.up(button, event.getEventTime(), event.getDownTime(), event.isCanceled(), app, holdApp,
                    holdDelayMs, target -> launch(ctx, target));
        }
        return true;
    }

    /**
     * Each button's presses, kept per device, keycode and scancode: KeyEvent.getDownTime() can't tell presses apart,
     * since a key pressed together with another can report the other's. Both services get every event, so a DOWN
     * or UP whose event time was already seen for that button is the other service's copy and is skipped. One small
     * record per mapped button that has been pressed, not a history.
     */
    static final class Presses {
        interface Timer {
            void post(Runnable hold, long delayMs);
            void cancel(Runnable hold);
        }

        private static final class Press {
            long downAt = -1, upAt = -1;
            boolean fired;
            Runnable hold;
        }

        private final Map<String, Press> buttons = new HashMap<>();
        private final Timer timer;

        Presses(Timer timer) {
            this.timer = timer;
        }

        void down(String button, long eventTime, int repeatCount, String holdApp, long holdDelayMs,
                Consumer<String> open) {
            if (repeatCount != 0) return;
            Press press = buttons.computeIfAbsent(button, b -> new Press());
            if (press.downAt == eventTime) return;
            if (press.hold != null) timer.cancel(press.hold);   // its UP never came
            press.downAt = eventTime;
            press.fired = false;
            press.hold = null;
            if (holdApp == null) return;
            press.hold = () -> {
                press.hold = null;
                press.fired = true;
                open.accept(holdApp);
            };
            timer.post(press.hold, holdDelayMs);
        }

        void up(String button, long eventTime, long downTime, boolean canceled, String app, String holdApp,
                long holdDelayMs, Consumer<String> open) {
            Press press = buttons.computeIfAbsent(button, b -> new Press());
            if (press.upAt == eventTime) return;
            boolean sawDown = press.downAt > press.upAt;
            press.upAt = eventTime;
            if (press.hold != null) {
                timer.cancel(press.hold);
                press.hold = null;
            }
            if (canceled || (sawDown && press.fired)) return;
            // Without its DOWN (the service started while the button was held), the event's own down time will do.
            open.accept(target(app, holdApp, eventTime - (sawDown ? press.downAt : downTime), holdDelayMs));
        }
    }

    /** The app a mapped key opens: its hold entry after HOLD_MS, otherwise its normal one. */
    static String target(String app, String holdApp, long heldMs) {
        return target(app, holdApp, heldMs, HOLD_MS);
    }

    static String target(String app, String holdApp, long heldMs, long holdDelayMs) {
        return heldMs >= holdDelayMs && holdApp != null ? holdApp : app;
    }

    /** The TV input id of an "input:<id>" target, e.g. "input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104"; null for a package. */
    static String inputId(String target) {
        if (target == null || !target.startsWith("input:")) return null;
        String id = target.substring("input:".length()).trim();
        return id.isEmpty() ? null : id;
    }

    /**
     * The accessibility global action for an action word, or 0 when the target is something else. "sleep" is
     * GLOBAL_ACTION_LOCK_SCREEN, which puts a TV in standby (as the Sleep Timer add-on does); Android has it from 28.
     */
    static int globalAction(String target, int sdk) {
        switch (target) {
            case "back": return AccessibilityService.GLOBAL_ACTION_BACK;
            case "home": return AccessibilityService.GLOBAL_ACTION_HOME;
            case "recents": return AccessibilityService.GLOBAL_ACTION_RECENTS;
            case "sleep": return sdk >= 28 ? AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN : 0;
            default: return 0;
        }
    }

    /** Whether a target is an action word rather than an app or an input. A package name always has a dot. */
    static boolean isAction(String target) {
        switch (target) {
            case "back": case "home": case "recents": case "mute": case "sleep":
            case "screensaver": case "settings": case "wifi":
                return true;
            default:
                return false;
        }
    }

    /** Runs an action word; false for an app or an input. Needs no permission the launcher does not already have. */
    private static boolean runAction(Context ctx, String target) {
        if (!isAction(target)) return false;
        int global = globalAction(target, Build.VERSION.SDK_INT);
        if (global != 0) {
            if (ctx instanceof AccessibilityService) ((AccessibilityService) ctx).performGlobalAction(global);
            return true;
        }
        switch (target) {
            case "mute":
                // The media stream a TV plays everything on, with its own volume bar, as a remote's mute button would.
                AudioManager audio = ctx.getSystemService(AudioManager.class);
                if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                break;
            case "screensaver":
                // The screensaver chosen in the TV's settings, through the system's "start now" shortcut; the
                // launcher's own clock where the firmware has no such shortcut.
                Intent dream = new Intent(Intent.ACTION_MAIN)
                        .setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
                if (ctx.getPackageManager().resolveActivity(dream, 0) == null) dream = new Intent(ctx, ClockActivity.class);
                start(ctx, dream);
                break;
            case "settings":
                start(ctx, SystemSettings.intent(ctx, Settings.ACTION_SETTINGS));
                break;
            case "wifi":
                start(ctx, SystemSettings.intent(ctx, Settings.ACTION_WIFI_SETTINGS));
                break;
            default:
                break;   // "sleep" below Android 9: consumed, nothing to do
        }
        return true;
    }

    private static void start(Context ctx, Intent intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    /**
     * True for a "package/.Activity" target. An input id has slashes too, and ComponentName.unflattenFromString()
     * would read "input:com.tcl.tvinput" as its package, so an "input:" target never counts.
     */
    static boolean isActivity(String target) {
        return target != null && inputId(target) == null && target.indexOf('/') > 0;
    }

    /** Opens an app from a remote button: its TV entry if it has one, else its phone entry. An "input:<id>" target
     *  switches to that TV input (an HDMI port) instead, an action word runs that action, and a
     *  "package/.Activity" target opens that activity. */
    static void launch(Context ctx, String pkg) {
        if (pkg == null || runAction(ctx, pkg)) return;
        if (pkg.startsWith("assistant:")) {
            String query = assistantQuery(pkg);
            if (query != null) start(ctx, new Intent(Intent.ACTION_ASSIST).putExtra("query", query));
            return;
        }
        if (INPUTS.equals(pkg)) {
            // This launcher's own Inputs menu, as InputsRequestActivity opens it: addressed to Home directly, so a
            // vendor input picker that also answers VIEW_INPUTS never turns it into a chooser.
            try {
                ctx.startActivity(new Intent(ctx, MainActivity.class).setAction(MainActivity.ACTION_VIEW_INPUTS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
            return;
        }
        String guide = guidePackage(pkg);
        if (guide != null) {
            // The program guide, asked for the way Android TV's Live TV apps take it: the programs directory.
            start(ctx, new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.Programs.CONTENT_URI, "vnd.android.cursor.dir/program")
                    .setPackage(guide));
            return;
        }
        String inputId = inputId(pkg);
        if (inputId != null) {
            // The same passthrough URI the Inputs menu opens, which the Live TV app handles.
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(inputId),
                            "vnd.android.cursor.item/channel")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(view);
            } catch (ActivityNotFoundException ignored) {
            }
            return;
        }
        ComponentName activity = isActivity(pkg) ? ComponentName.unflattenFromString(pkg) : null;
        if (activity != null) {
            try {
                ctx.startActivity(new Intent().setComponent(activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
            return;
        }
        Intent open = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return;
        try {
            ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }
}
