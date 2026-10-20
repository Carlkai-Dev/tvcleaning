package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public final class SleepTimerManager {
    private static SleepTimerManager instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long sleepTimestamp = 0;
    private final Runnable sleepAction = new Runnable() {
        @Override
        public void run() {
            try {
                // Put Fire TV to sleep (standard Android TV KEYCODE_SLEEP = 223)
                Runtime.getRuntime().exec("input keyevent 223");
            } catch (Exception ignored) {
            }
            sleepTimestamp = 0;
        }
    };

    private SleepTimerManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SleepTimerManager getInstance(Context context) {
        if (instance == null) {
            instance = new SleepTimerManager(context);
        }
        return instance;
    }

    public void setTimer(int minutes) {
        handler.removeCallbacks(sleepAction);
        if (minutes <= 0) {
            sleepTimestamp = 0;
            Toast.makeText(context, "Sleep timer turned off", Toast.LENGTH_SHORT).show();
            return;
        }
        sleepTimestamp = System.currentTimeMillis() + (minutes * 60 * 1000L);
        handler.postDelayed(sleepAction, minutes * 60 * 1000L);
        Toast.makeText(context, "⏱️ Sleep timer set for " + minutes + " minutes", Toast.LENGTH_SHORT).show();
    }

    public void sleepNow() {
        handler.removeCallbacks(sleepAction);
        handler.post(sleepAction);
    }

    public int getRemainingMinutes() {
        if (sleepTimestamp <= 0) return 0;
        long diff = sleepTimestamp - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) Math.ceil(diff / 60000.0);
    }

    public boolean isActive() {
        return getRemainingMinutes() > 0;
    }
}
