package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Emulator-only rendered-background regression check; original theme files are restored. */
public final class WallpaperSmoke extends Instrumentation {
    private MainActivity activity;
    private int checks;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_OK;
        File config = null, wallpaper = null;
        byte[] originalConfig = null, originalWallpaper = null;
        boolean captured = false;
        try {
            if (!android.os.Build.FINGERPRINT.contains("generic")
                    && !android.os.Build.MODEL.contains("Emulator")
                    && !android.os.Build.PRODUCT.contains("sdk"))
                throw new IllegalStateException("Wallpaper fixtures must run on an emulator");
            File dir = getTargetContext().getExternalFilesDir(null);
            config = new File(dir, "config.json");
            wallpaper = new File(dir, "wallpaper.jpg");
            originalConfig = config.isFile() ? Files.readAllBytes(config.toPath()) : null;
            originalWallpaper = wallpaper.isFile() ? Files.readAllBytes(wallpaper.toPath()) : null;
            captured = true;
            Files.write(config.toPath(), ("{\"wallpaper\":true,\"columns\":6,"
                    + "\"rows_below_dock\":0,\"module_cards\":false}").getBytes(StandardCharsets.UTF_8));
            Bitmap blue = Bitmap.createBitmap(320, 180, Bitmap.Config.RGB_565);
            blue.eraseColor(Color.rgb(15, 35, 230));
            try (FileOutputStream out = new FileOutputStream(wallpaper)) {
                blue.compress(Bitmap.CompressFormat.JPEG, 95, out);
            }
            blue.recycle();
            launch();
            expectBlue("Wallpaper is visible on Home");
            expand();
            expectBlue("Wallpaper stays visible after expanding the app grid");
            call("syncFolderPresentation"); settle();
            expectBlue("Wallpaper stays visible when expanded presentation is refreshed");
            call("setExpanded", boolean.class, false); settle();
            expectBlue("Wallpaper is visible after returning to Home");
            close();

            // A failed decode must retain the normal plain-background treatment.
            Files.write(wallpaper.toPath(), new byte[]{1, 2, 3, 4});
            launch(); expand();
            runOnMainSync(() -> check(activity.findViewById(R.id.atmosphere).getAlpha() > .9f,
                    "Invalid wallpaper keeps the plain-grid fallback"));
            close();
            Files.delete(wallpaper.toPath());
            launch(); expand();
            runOnMainSync(() -> check(activity.findViewById(R.id.atmosphere).getAlpha() > .9f,
                    "Missing wallpaper keeps the plain-grid fallback"));
            result.putString("stream", "PASS: " + checks + " wallpaper checks.\n");
        } catch (Throwable failure) {
            code = Activity.RESULT_CANCELED;
            result.putString("stream", "FAIL after " + checks + " wallpaper checks: "
                    + android.util.Log.getStackTraceString(failure));
        } finally {
            close();
            if (captured) {
                try {
                    restore(config, originalConfig);
                    restore(wallpaper, originalWallpaper);
                } catch (Exception failure) {
                    code = Activity.RESULT_CANCELED;
                    result.putString("stream", "Theme restore failed: " + failure);
                }
            }
        }
        finish(code, result);
    }

    private void launch() {
        activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        settle();
    }

    private void close() {
        if (activity == null) return;
        runOnMainSync(() -> activity.finish());
        waitForIdleSync();
        activity = null;
    }

    private void expand() { call("setExpanded", boolean.class, true); settle(); }
    private void settle() { SystemClock.sleep(800); waitForIdleSync(); }

    private void call(String name, Class<?> type, Object value) {
        invoke(name, new Class<?>[]{type}, new Object[]{value});
    }

    private void call(String name) { invoke(name, new Class<?>[0], new Object[0]); }

    private void invoke(String name, Class<?>[] types, Object[] args) {
        runOnMainSync(() -> {
            try {
                Method method = MainActivity.class.getDeclaredMethod(name, types);
                method.setAccessible(true);
                method.invoke(activity, args);
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    private void expectBlue(String message) {
        runOnMainSync(() -> {
            View root = activity.findViewById(R.id.root);
            Bitmap frame = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(frame));
            int pixel = frame.getPixel(2, root.getHeight() / 2);
            frame.recycle();
            check(Color.blue(pixel) - Math.max(Color.red(pixel), Color.green(pixel)) > 50,
                    message + ": pixel=" + Integer.toHexString(pixel));
        });
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private void restore(File file, byte[] bytes) throws Exception {
        if (bytes == null) Files.deleteIfExists(file.toPath());
        else Files.write(file.toPath(), bytes);
    }
}
