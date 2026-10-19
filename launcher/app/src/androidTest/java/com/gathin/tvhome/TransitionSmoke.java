package com.gathin.tvhome;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileWriter;

/** Emulator-only frame/geometry checks for dock expansion, reversal, and interruptions. */
public final class TransitionSmoke extends Instrumentation {
    private MainActivity activity;
    private AppAdapter adapter;
    private int checks;
    private final List<String> frames = new ArrayList<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!android.os.Build.FINGERPRINT.contains("generic")
                    && !android.os.Build.MODEL.contains("Emulator")
                    && !android.os.Build.PRODUCT.contains("sdk"))
                throw new IllegalStateException("Transition fixtures must run on an emulator");
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            runOnMainSync(() -> {
                try { adapter = LauncherSmoke.seedApps(activity, 43); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            settle(); home(0);
            sample("expand", () -> dispatch(KeyEvent.KEYCODE_DPAD_DOWN), -1);
            expect(6, "Down selects second row");
            key(KeyEvent.KEYCODE_DPAD_UP, 60);
            sample("collapse", () -> dispatch(KeyEvent.KEYCODE_DPAD_UP), 1);
            expect(0, "Up collapses onto dock");
            for (int i = 0; i < 3; i++) {
                home(i);
                runOnMainSync(() -> dispatch(KeyEvent.KEYCODE_DPAD_DOWN));
                SystemClock.sleep(90);
                runOnMainSync(() -> {
                    float before = frame(0)[1];
                    dispatch(KeyEvent.KEYCODE_DPAD_UP);
                    dispatch(KeyEvent.KEYCODE_DPAD_UP);
                    check(Math.abs(frame(0)[1] - before) <= 3,
                            "Reversing mid-slide starts at current screen position");
                });
                settle();
                expect(i, "Reverse expansion keeps dock column");
                check(Boolean.FALSE.equals(field("expanded")), "Reverse leaves collapsed state");
                runOnMainSync(() -> dispatch(KeyEvent.KEYCODE_DPAD_DOWN));
                SystemClock.sleep(80);
                runOnMainSync(() -> activity.onNewIntent(new Intent(Intent.ACTION_MAIN)));
                settle();
                expect(0, "Home cancels transition without stale focus");
                check(Boolean.FALSE.equals(field("gridTransition")), "Home cancels transition state");
            }
            home(3);
            burst(KeyEvent.KEYCODE_DPAD_DOWN, 5);
            expect(33, "Rapid Down during expansion retains all input");
            key(KeyEvent.KEYCODE_DPAD_UP, 80);
            expect(27, "Navigation continues after expansion burst");
            home(0);
            runOnMainSync(() -> {
                dispatch(KeyEvent.KEYCODE_DPAD_DOWN);
                dispatch(KeyEvent.KEYCODE_DPAD_UP);
                dispatch(KeyEvent.KEYCODE_DPAD_UP);
                dispatch(KeyEvent.KEYCODE_DPAD_DOWN);
            });
            settle();
            expect(6, "Queued Down Up Up Down returns to expanded second row");
            check(Boolean.TRUE.equals(field("expanded")), "Queued reversal ends expanded");
            check(Math.abs(activity.findViewById(R.id.gridHost).getTranslationY()) <= 1,
                    "Queued reversal brings selected row fully on screen");
            // Pause must cancel the old animation rather than letting it mutate a resumed layout.
            home(0);
            runOnMainSync(() -> dispatch(KeyEvent.KEYCODE_DPAD_DOWN));
            SystemClock.sleep(80);
            runOnMainSync(() -> {
                callActivityOnPause(activity);
                activity.onNewIntent(new Intent(Intent.ACTION_MAIN));
                callActivityOnResume(activity);
            });
            settle();
            check(Boolean.FALSE.equals(field("gridTransition")), "Resume cancels slide callback");
            expect(0, "Home during pause restores first dock app");
            result.putString("stream", "PASS: " + checks + " transition checks.\n");
            saveFrames(); finish(-1, result);
        } catch (Throwable failure) {
            try { saveFrames(); } catch (Exception ignored) { }
            result.putString("stream", "FAIL after " + checks + " transition checks: "
                    + android.util.Log.getStackTraceString(failure) + "\n" + state());
            finish(0, result);
        }
    }
    private void sample(String name, Runnable input, int direction) throws Exception {
        List<float[]> trace = new ArrayList<>();
        View root = activity.findViewById(R.id.root);
        ViewTreeObserver.OnPreDrawListener[] listener = new ViewTreeObserver.OnPreDrawListener[1];
        long start = SystemClock.uptimeMillis();
        runOnMainSync(() -> {
            listener[0] = () -> { trace.add(frame(start)); return true; };
            trace.add(frame(start));
            root.getViewTreeObserver().addOnPreDrawListener(listener[0]);
            input.run();
        });
        SystemClock.sleep(850); waitForIdleSync();
        runOnMainSync(() -> root.getViewTreeObserver().removeOnPreDrawListener(listener[0]));
        float maxReversal = 0, maxSpeed = 0, maxPaddingChange = 0, maxHostChange = 0, maxHaloError = 0;
        for (int i = 0; i < trace.size(); i++) {
            float[] f = trace.get(i);
            frames.add(name + "," + f[0] + "," + f[1] + "," + f[2] + "," + f[3] + "," + f[4]);
            maxPaddingChange = Math.max(maxPaddingChange, Math.abs(f[3] - trace.get(0)[3]));
            maxHostChange = Math.max(maxHostChange, Math.abs(f[4] - trace.get(0)[4]));
            maxHaloError = Math.max(maxHaloError, f[5]);
            if (i == 0) continue;
            float dy = f[1] - trace.get(i-1)[1], dt = f[0] - trace.get(i-1)[0];
            maxReversal = Math.max(maxReversal, -direction * dy);
            if (dt > 0) maxSpeed = Math.max(maxSpeed, Math.abs(dy) / dt);
        }
        Bundle progress = new Bundle();
        progress.putString("stream", name + ": frames=" + trace.size() + ", reversal=" + maxReversal
                + ", max pixels/ms=" + maxSpeed + ", padding shift=" + maxPaddingChange
                + ", host layout shift=" + maxHostChange + "\n"); sendStatus(0, progress);
        check(trace.size() > 4, name + " produces multiple animation frames");
        check(maxReversal <= 3, name + " moves in one direction without snapping back");
        check(maxPaddingChange == 0 && maxHostChange == 0,
                name + " keeps grid layout stable while sliding");
        check(maxHaloError <= 3, name + " keeps halo attached throughout slide");
        check(Math.abs(trace.get(trace.size()-1)[1] - trace.get(0)[1]) > 100,
                name + " completes the slide");
    }
    private float[] frame(long start) {
        RecyclerView grid = activity.findViewById(R.id.grid);
        View host = activity.findViewById(R.id.gridHost);
        RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
        float top = first == null ? Float.NaN : host.getY() + grid.getTop() + first.itemView.getTop();
        View card = activity.getCurrentFocus(), halo = activity.findViewById(R.id.glass);
        float haloError = 0;
        if (card != null && card.getId() == R.id.card && halo.getAlpha() > .1f) {
            android.graphics.Rect rect = new android.graphics.Rect(0, 0, card.getWidth(), card.getHeight());
            ((android.view.ViewGroup) activity.findViewById(R.id.root)).offsetDescendantRectToMyCoords(card, rect);
            float center = rect.exactCenterY() + host.getTranslationY();
            haloError = Math.abs(halo.getY() + halo.getHeight()/2f - center);
        }
        return new float[]{SystemClock.uptimeMillis()-start, top, host.getTranslationY(),
                grid.getPaddingTop(), host.getTop(), haloError};
    }
    private void saveFrames() throws Exception {
        File out = new File(getTargetContext().getExternalFilesDir(null), "transition-frames.csv");
        try (FileWriter writer = new FileWriter(out)) {
            writer.write("phase,time_ms,dock_top,translation_y,padding_top,host_top\n");
            for (String frame : frames) writer.write(frame + "\n");
        }
    }
    private void home(int column) {
        runOnMainSync(() -> activity.onNewIntent(new Intent(Intent.ACTION_MAIN)));
        settle();
        runOnMainSync(() -> invoke("focusTile", new Class<?>[]{int.class}, column));
        settle();
    }
    private void key(int code, long delay) {
        sendKeyDownUpSync(code);
        SystemClock.sleep(delay);
        waitForIdleSync();
    }
    private void burst(int code, int count) {
        runOnMainSync(() -> { for (int i = 0; i < count; i++) dispatch(code); });
        settle();
    }
    private void dispatch(int code) {
        long time = SystemClock.uptimeMillis();
        activity.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0));
        activity.dispatchKeyEvent(new KeyEvent(time, time + 1, KeyEvent.ACTION_UP, code, 0));
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(650); waitForIdleSync(); }
    private void expect(int position, String action) {
        int[] actual = {-1};
        runOnMainSync(() -> {
            RecyclerView grid = activity.findViewById(R.id.grid);
            View focused = activity.getCurrentFocus();
            RecyclerView.ViewHolder holder = focused == null ? null : grid.findContainingViewHolder(focused);
            if (holder != null) actual[0] = holder.getBindingAdapterPosition();
        });
        if (actual[0] != position) throw new AssertionError(action + ": wanted " + position
                + ", got " + actual[0] + "; " + state());
        checks++;
        if (checks % 12 == 0) {
            Bundle progress = new Bundle();
            progress.putString("stream", "Passed " + checks + ": " + action + "\n");
            sendStatus(0, progress);
        }
    }
    private void check(boolean success, String action) {
        if (!success) throw new AssertionError(action + "; " + state());
        checks++;
    }
    private String state() {
        try {
            return "focused=" + activity.getCurrentFocus() + ", last=" + field("lastFocused")
                    + ", requested=" + field("requestedFocusKey") + ", transition=" + field("gridTransition")
                    + ", expanded=" + field("expanded");
        } catch (Exception e) { return e.toString(); }
    }
    private Object field(String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity);
    }
    private void invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, types);
            method.setAccessible(true); method.invoke(activity, args);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
