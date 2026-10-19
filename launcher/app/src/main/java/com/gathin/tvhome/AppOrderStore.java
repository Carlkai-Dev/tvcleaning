package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small, atomic, app-private file. No draft is written until the user confirms. */
final class AppOrderStore {
    static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private static final Object LOCK = new Object();
    private final AtomicFile file;

    AppOrderStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "app-order-v1.json"));
    }

    List<String> read() {
        synchronized (LOCK) {
            List<String> keys = new ArrayList<>();
            try {
                JSONObject value = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
                if (value.getInt("version") != 1) return keys;
                JSONArray order = value.getJSONArray("order");
                for (int i = 0; i < order.length(); i++) {
                    Object key = order.get(i);
                    if (!(key instanceof String) || ((String) key).isEmpty()) return new ArrayList<>();
                    if (!keys.contains(key)) keys.add((String) key);
                }
            } catch (Exception ignored) {
                // First run, interrupted write, or corrupt data: use the theme's default order.
                keys.clear();
            }
            return keys;
        }
    }

    boolean write(List<String> visible) {
        synchronized (LOCK) {
            FileOutputStream out = null;
            try {
                List<String> merged = AppOrder.merge(read(), visible);
                JSONObject value = new JSONObject().put("version", 1).put("order", new JSONArray(merged));
                byte[] data = value.toString().getBytes(StandardCharsets.UTF_8);
                out = file.startWrite();
                out.write(data);
                file.finishWrite(out);
                out = null;
                // AtomicFile logs some sync/rename failures instead of throwing them.
                return java.util.Arrays.equals(data, file.readFully());
            } catch (Exception failure) {
                if (out != null) file.failWrite(out);
                return false;
            }
        }
    }
}
