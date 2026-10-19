package com.gathin.tvhome;

import android.app.Instrumentation;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Emulator-only native UI smoke checks. Artwork is deliberately synthetic; no TV data is changed. */
public final class LauncherSmoke extends Instrumentation {
    private MainActivity activity;
    private AppAdapter adapter;
    private int checks;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!android.os.Build.FINGERPRINT.contains("generic")
                    && !android.os.Build.MODEL.contains("Emulator")
                    && !android.os.Build.PRODUCT.contains("sdk"))
                throw new IllegalStateException("Synthetic inventory checks must run on an emulator");
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(() -> {
                try { seed(); } catch (Exception e) { throw new RuntimeException(e); }
            });
            settle();
            check(adapter.getItemCount() == 36, "36 fixture apps");
            Bitmap[] halo = new Bitmap[1];
            runOnMainSync(() -> {
                GlassView light = new GlassView(activity, null);
                light.layout(0, 0, 240, 160);
                light.setRadius(28);
                light.setTint(0xFF1ED760);
                halo[0] = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888);
                light.draw(new Canvas(halo[0]));
            });
            check(android.graphics.Color.alpha(halo[0].getPixel(120, 80)) == 0,
                    "Focus halo leaves app artwork completely untouched");
            int coloured = 0, white = 0;
            for (int y = 0; y < halo[0].getHeight(); y++) for (int x = 0; x < halo[0].getWidth(); x++) {
                int pixel = halo[0].getPixel(x, y);
                if (android.graphics.Color.alpha(pixel) < 8) continue;
                if (android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) * 2) coloured++;
                if (Math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.red(pixel)) < 10) white++;
            }
            check(coloured > 100, "Selected app colour lights the outside of the tile");
            check(white == 0, "Focus halo has no white outline");
            halo[0].recycle();
            capture("home");
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            check(Boolean.TRUE.equals(field(activity, "expanded")), "Down expands grid");
            check(activity.findViewById(R.id.topBar).getVisibility() == View.INVISIBLE, "Expanded grid hides header");
            RecyclerView expandedGrid = activity.findViewById(R.id.grid);
            View dock = expandedGrid.findViewHolderForAdapterPosition(0).itemView;
            check(dock.getTop() >= expandedGrid.getPaddingTop(), "Dock stays fully visible after expansion with many apps");
            capture("grid");
            key(KeyEvent.KEYCODE_DPAD_UP);
            key(KeyEvent.KEYCODE_DPAD_UP);
            check(Boolean.FALSE.equals(field(activity, "expanded")), "Up from dock collapses grid");

            // Exercise the actual remote long-press path and guard its release from launching anything.
            long now = SystemClock.uptimeMillis();
            sendKeySync(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
            sendKeySync(new KeyEvent(now, now + 600, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS));
            sendKeySync(new KeyEvent(now, now + 650, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
            settle();
            check(field(activity, "tileMenu") != null, "Long press opens actions");
            capture("menu");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(field(activity, "movingKey") != null, "Move begins rearranging");
            List<String> before = new ArrayList<>(adapter.keys());
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(!before.equals(adapter.keys()), "D-pad reorders");
            key(KeyEvent.KEYCODE_BACK);
            check(before.equals(adapter.keys()), "Back restores snapshot");
            HomeLayout original = adapter.snapshotLayout();
            runOnMainSync(() -> activity.onTileLongPressed(0, activity.getCurrentFocus()));
            settle();
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            AlertDialog destination = (AlertDialog) field(activity, "editDialog");
            check(destination.getListView().hasFocus(), "Move-to destinations take remote focus");
            check(destination.getListView().getSelectedItemPosition() == 0, "New Folder is selected by default");
            check(destination.getWindow().getDecorView().getBackground() instanceof FrostedGlassDrawable,
                    "Folder destinations have a captured frosted backdrop");
            capture("folder-chooser");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            AlertDialog naming = (AlertDialog) field(activity, "editDialog");
            check(findInput(naming.getWindow().getDecorView()).getText().length() == 0,
                    "New folder name starts empty");
            check(naming.getWindow().getDecorView().getBackground() instanceof FrostedGlassDrawable,
                    "Folder name dialog also has a frosted backdrop");
            setInTouchMode(true);
            runOnMainSync(() -> ((AlertDialog) fieldUnchecked(activity, "editDialog"))
                    .getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            settle();
            String created = adapter.keyAt(0);
            check(adapter.isFolder(0), "New Folder dialog saves a real folder");
            check(adapter.folder(created).name.isEmpty(), "Confirm without typing creates a nameless folder");
            check(new HomeLayoutStore(getTargetContext()).read().folders.get(created).name.isEmpty(),
                    "Empty folder name survives disk reload");
            check(activity.getCurrentFocus().getId() == R.id.card
                    && activity.getCurrentFocus().getContentDescription().toString()
                    .equals(activity.getString(R.string.unnamed_folder)),
                    "Naming a folder returns focus from touch mode to its tile");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(created.equals(adapter.activeFolder()), "OK opens the newly created folder");
            check(activity.findViewById(R.id.folderTitle).getVisibility() == View.GONE,
                    "Nameless folder has no empty title pill");
            key(KeyEvent.KEYCODE_DPAD_UP);
            check(activity.getCurrentFocus().getId() == R.id.card,
                    "Up in nameless folder keeps focus on a real app");
            key(KeyEvent.KEYCODE_BACK);
            check(new HomeLayoutStore(getTargetContext()).write(original), "Restore fixture order after create test");
            runOnMainSync(() -> adapter.restoreLayout(original));
            settle();

            HomeLayout draft = adapter.snapshotLayout();
            String folder = draft.createFolder(before.get(1), "Entertainment");
            draft.moveTo(before.get(2), folder); draft.moveTo(before.get(3), folder);
            draft.moveTo(before.get(4), folder); draft.moveTo(before.get(5), folder);
            draft.moveTo(before.get(6), folder);
            check(new HomeLayoutStore(getTargetContext()).write(draft), "Atomic folder save");
            HomeLayout restored = new HomeLayoutStore(getTargetContext()).read();
            check(restored.folders.get(folder).apps.size() == 6, "Folder membership reloads");
            runOnMainSync(() -> {
                adapter.restoreLayout(restored);
                activity.onFolderOpened(folder);
            });
            settle();
            check(adapter.activeFolder().equals(folder), "Folder opens");
            check(adapter.getItemCount() == 6 && adapter.columns() == 3, "Folder has 3 columns");
            RecyclerView folderGrid = activity.findViewById(R.id.grid);
            View first = folderGrid.findViewHolderForAdapterPosition(0).itemView;
            check(first.getTop() < folderGrid.getHeight() / 3, "Folder starts at top of panel");
            View last = folderGrid.findViewHolderForAdapterPosition(5).itemView;
            check(last.getBottom() <= folderGrid.getHeight(), "Both folder rows fit in panel");
            android.graphics.Rect rect = new android.graphics.Rect(0, 0,
                    activity.getCurrentFocus().getWidth(), activity.getCurrentFocus().getHeight());
            ((android.view.ViewGroup) activity.findViewById(R.id.root))
                    .offsetDescendantRectToMyCoords(activity.getCurrentFocus(), rect);
            View highlight = activity.findViewById(R.id.glass);
            check(Math.abs(highlight.getTranslationY() + highlight.getHeight() / 2f - rect.exactCenterY()) < 12,
                    "Folder highlight follows tile after layout");
            SystemClock.sleep(1800); // Let the platform cancellation toast clear before the screenshot.
            capture("folder");
            key(KeyEvent.KEYCODE_DPAD_UP);
            check(activity.findViewById(R.id.folderTitle).isFocused(), "Up reaches folder title");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(field(activity, "editDialog") instanceof AlertDialog, "Title opens rename");
            runOnMainSync(() -> {
                AlertDialog rename = (AlertDialog) fieldUnchecked(activity, "editDialog");
                findInput(rename.getWindow().getDecorView()).setText("   ");
                rename.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            settle();
            check(adapter.folder(folder).name.isEmpty(), "Rename accepts whitespace as an empty name");
            check(new HomeLayoutStore(getTargetContext()).read().folders.get(folder).name.isEmpty(),
                    "Renamed blank folder persists without losing its apps");
            check(adapter.getItemCount() == 6 && activity.findViewById(R.id.folderTitle).getVisibility() == View.GONE,
                    "Blank rename keeps the folder open and all six members");
            key(KeyEvent.KEYCODE_BACK);
            check(adapter.activeFolder() == null, "Back closes folder");
            for (int row = 0; row < 6; row++) key(KeyEvent.KEYCODE_DPAD_DOWN);
            runOnMainSync(() -> activity.onNewIntent(new Intent(Intent.ACTION_MAIN)));
            settle();
            check(Boolean.FALSE.equals(field(activity, "expanded")), "Home collapses grid");
            check(adapter.keyAt(0).equals(field(activity, "lastFocusedKey")), "Home returns to first tile");
            check(activity.getCurrentFocus().getId() == R.id.card
                    && ((RecyclerView) activity.findViewById(R.id.grid)).findContainingViewHolder(
                    activity.getCurrentFocus()).getBindingAdapterPosition() == 0,
                    "Home restores focus from a distant row to the dock");

            for (int row = 0; row < 6; row++) key(KeyEvent.KEYCODE_DPAD_DOWN);
            runOnMainSync(() -> {
                callActivityOnPause(activity);
                activity.onNewIntent(new Intent(Intent.ACTION_MAIN));
            });
            check(((Integer) field(activity, "lastFocused")) == 0
                    && adapter.keyAt(0).equals(field(activity, "lastFocusedKey")),
                    "Home reset survives delivery while activity is paused");
            runOnMainSync(() -> callActivityOnResume(activity));

            result.putString("stream", "PASS: " + checks + " native launcher checks. Synthetic artwork.\n");
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(failure));
            finish(0, result);
        }
    }

    @SuppressWarnings("unchecked") static AppAdapter seedApps(MainActivity activity, int count) throws Exception {
        AppAdapter adapter = (AppAdapter) field(activity, "adapter");
        Map<String, AppAdapter.Entry> inventory = (Map<String, AppAdapter.Entry>) field(adapter, "inventory");
        inventory.clear();
        String[] labels = {"TV", "YouTube", "Netflix", "Music", "Fitness", "Sing", "Settings", "App Store",
                "Spotify", "Prime Video", "Photos", "Games", "News", "Player", "Podcasts", "Disney+", "Viki", "Movies"};
        int[] colors = {0xFF151519,0xFFFAFAFA,0xFFF4F4F4,0xFFFF1855,0xFF151519,0xFFFF1855,
                0xFF9A9DA4,0xFF008DFF,0xFF121415,0xFF0085F5,0xFFF4F4F4,0xFFEC6953,
                0xFFD61537,0xFF278584,0xFF934DE4,0xFF036070,0xFF008FCC,0xFFEFAC30};
        for (int i = 0; i < count; i++) {
            int sample = i % labels.length;
            ComponentName component = new ComponentName("fixture.app" + i, "fixture.app" + i + ".Main");
            inventory.put(component.flattenToShortString(), new AppAdapter.Entry(labels[sample], component,
                    new SampleArtwork(labels[sample], colors[sample], sample == 1 || sample == 2 || sample == 10), null));
        }
        HomeLayout layout = new HomeLayout();
        layout.reconcile(new ArrayList<>(inventory.keySet()));
        adapter.restoreLayout(layout);
        return adapter;
    }
    private void seed() throws Exception {
        adapter = seedApps(activity, 36);
        Method focus = MainActivity.class.getDeclaredMethod("focusTile", int.class);
        focus.setAccessible(true); focus.invoke(activity, 0);
    }
    private void key(int code) { sendKeyDownUpSync(code); settle(); }
    private static android.widget.EditText findInput(View view) {
        if (view instanceof android.widget.EditText) return (android.widget.EditText) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.EditText found = findInput(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(550); waitForIdleSync(); }
    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name); checks++;
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object fieldUnchecked(Object object, String name) {
        try { return field(object, name); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private void capture(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        File folder = new File(getTargetContext().getExternalFilesDir(null), "ui-smoke");
        folder.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        image.recycle();
    }
    private static final class SampleArtwork extends Drawable {
        final String label; final int color; final boolean darkText;
        final Paint paint = new Paint(3);
        SampleArtwork(String label, int color, boolean darkText) { this.label=label; this.color=color; this.darkText=darkText; }
        @Override public void draw(Canvas canvas) {
            paint.setColor(color); canvas.drawRect(getBounds(), paint);
            paint.setColor(darkText ? 0xFF18191C : 0xFFFFFFFF);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            paint.setTextSize(getBounds().width() * Math.min(.20f, 1.4f / label.length()));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, getBounds().exactCenterX(), getBounds().exactCenterY()
                    - (paint.ascent() + paint.descent()) / 2, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
