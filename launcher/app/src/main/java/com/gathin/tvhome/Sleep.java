package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Turn off now and the sleep timer. The standby itself is done by {@link SleepService}. */
final class Sleep {

    private static final String AT = "sleep_at";
    /** A timer that fires this late (the TV was off, the alarm waited) is dropped instead of sleeping a TV just turned on. */
    private static final long STALE_MS = 2 * 60_000L;
    private static final int[] STEPS = {30, 60, 90};

    private Sleep() {
    }

    /** Fires when the sleep timer runs out. */
    public static class Alarm extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            long at = Prefs.of(c).getLong(AT, 0);
            Prefs.of(c).edit().remove(AT).apply();
            if (at > 0 && Math.abs(System.currentTimeMillis() - at) < STALE_MS) now(c);
        }
    }

    static boolean now(Context c) {
        if (SleepService.lock()) return true;
        Toast.makeText(c, R.string.sleep_needs_setup, Toast.LENGTH_LONG).show();
        return false;
    }

    /** Minutes left on the timer, or 0 when it is off. */
    static int minutesLeft(Context c) {
        long at = Prefs.of(c).getLong(AT, 0);
        long left = at - System.currentTimeMillis();
        return left > 0 ? (int) Math.ceil(left / 60_000.0) : 0;
    }

    /** Off → 30 → 60 → 90 → off. From a running timer, jump to the next step above what is left. Returns the new minutes. */
    static int cycle(Context c) {
        int left = minutesLeft(c);
        int next = 0;
        for (int s : STEPS) {
            if (s > left) {
                next = s;
                break;
            }
        }
        set(c, next);
        return next;
    }

    private static void set(Context c, int minutes) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, Alarm.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.cancel(pi);
        if (minutes <= 0) {
            Prefs.of(c).edit().remove(AT).apply();
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        Prefs.of(c).edit().putLong(AT, at).apply();
        if (am != null) am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
    }
}
