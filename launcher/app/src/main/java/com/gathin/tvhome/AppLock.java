package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * App lock: apps the owner picked (hold OK on Settings, then App lock) open from Home and the App
 * Library only after a 4-digit PIN. Only the launcher asks: voice search, the Recents list or another
 * app can still start them. The PIN is kept as a salted SHA-256 in the launcher's private preferences;
 * nothing leaves the TV.
 */
final class AppLock {
    static final String PREFS_NAME = "app_lock";
    static final String PREF_PIN = "pin";
    static final String PREF_APPS = "apps";
    static final int PIN_LENGTH = 4;
    /** Wrong PINs in a row before the pad makes you wait. */
    static final int MAX_TRIES = 5;
    static final long WAIT_MS = 30_000;

    private static int fails;
    private static long waitUntil;

    private AppLock() {
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** The locked packages (a copy: the preference's own set must not be changed). */
    static Set<String> apps(SharedPreferences p) {
        return new HashSet<>(p.getStringSet(PREF_APPS, Collections.emptySet()));
    }

    static boolean hasPin(SharedPreferences p) {
        return parse(p.getString(PREF_PIN, "")) != null;
    }

    /** Locked needs a PIN too, so a lost preference file opens everything rather than nothing. */
    static boolean locked(SharedPreferences p, String pkg) {
        return pkg != null && hasPin(p) && apps(p).contains(pkg);
    }

    static void setLocked(SharedPreferences p, String pkg, boolean on) {
        Set<String> apps = apps(p);
        if (on) apps.add(pkg);
        else apps.remove(pkg);
        p.edit().putStringSet(PREF_APPS, apps).apply();
    }

    /** Runs open now, or after the PIN when pkg is locked. */
    static void guard(Activity a, String pkg, Runnable open) {
        if (!locked(prefs(a), pkg)) {
            open.run();
            return;
        }
        verify(a, a.getString(R.string.app_lock_enter_for, label(a, pkg)), open);
    }

    /** Asks for the PIN; then runs only after the right one. Too many wrong ones in a row wait WAIT_MS. */
    static void verify(Activity a, String title, Runnable then) {
        PinPad.show(a, title, (pad, pin) -> {
            long now = SystemClock.elapsedRealtime();
            if (now < waitUntil) {
                pad.fail(a.getString(R.string.app_lock_wait, (int) ((waitUntil - now + 999) / 1000)));
                return;
            }
            if (matches(prefs(a).getString(PREF_PIN, ""), pin)) {
                fails = 0;
                pad.dismiss();
                then.run();
                return;
            }
            if (++fails >= MAX_TRIES) {
                fails = 0;
                waitUntil = now + WAIT_MS;
                pad.fail(a.getString(R.string.app_lock_wait, (int) (WAIT_MS / 1000)));
            } else {
                pad.fail(a.getString(R.string.app_lock_wrong));
            }
        });
    }

    /** A new PIN, typed twice, then then. */
    static void choosePin(Activity a, Runnable then) {
        final String[] first = new String[1];
        PinPad.show(a, a.getString(R.string.app_lock_new), (pad, pin) -> {
            if (first[0] == null) {
                first[0] = pin;
                pad.next(a.getString(R.string.app_lock_confirm));
                return;
            }
            if (!first[0].equals(pin)) {
                first[0] = null;
                pad.next(a.getString(R.string.app_lock_new));
                pad.fail(a.getString(R.string.app_lock_mismatch));
                return;
            }
            prefs(a).edit().putString(PREF_PIN, store(pin, new SecureRandom())).apply();
            pad.dismiss();
            Toast.makeText(a, R.string.app_lock_saved, Toast.LENGTH_SHORT).show();
            then.run();
        });
    }

    private static String label(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** "salt:hash", both hex. */
    static String store(String pin, SecureRandom random) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String s = hex(salt);
        return s + ":" + hash(s, pin);
    }

    static boolean matches(String stored, String pin) {
        String[] parts = parse(stored);
        if (parts == null || !valid(pin)) return false;
        return MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.US_ASCII),
                hash(parts[0], pin).getBytes(StandardCharsets.US_ASCII));
    }

    static boolean valid(String pin) {
        if (pin == null || pin.length() != PIN_LENGTH) return false;
        for (int i = 0; i < pin.length(); i++) if (pin.charAt(i) < '0' || pin.charAt(i) > '9') return false;
        return true;
    }

    private static String[] parse(String stored) {
        if (stored == null) return null;
        String[] parts = stored.split(":");
        return parts.length == 2 && parts[0].length() == 32 && parts[1].length() == 64 ? parts : null;
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest((salt + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every Android has SHA-256
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
