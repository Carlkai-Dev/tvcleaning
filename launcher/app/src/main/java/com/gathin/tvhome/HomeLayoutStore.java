package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** One atomic transaction for home order, names and folder membership. Migrates v1 without deleting it. */
final class HomeLayoutStore {
    private static final Object LOCK = new Object();
    private final AtomicFile file;
    private final AppOrderStore legacy;
    HomeLayoutStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "home-layout-v2.json"));
        legacy = new AppOrderStore(context);
    }
    HomeLayout read() {
        synchronized (LOCK) {
            HomeLayout layout = new HomeLayout();
            try {
                JSONObject json = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
                if (json.getInt("version") != 2) throw new IllegalArgumentException();
                strings(json.getJSONArray("home"), layout.home);
                JSONArray folders = json.getJSONArray("folders");
                for (int i = 0; i < folders.length(); i++) {
                    JSONObject object = folders.getJSONObject(i);
                    String id = object.getString("id");
                    if (!id.startsWith(HomeLayout.FOLDER_PREFIX)) throw new IllegalArgumentException();
                    HomeLayout.Folder folder = new HomeLayout.Folder(id, object.getString("name"));
                    strings(object.getJSONArray("apps"), folder.apps);
                    layout.folders.put(id, folder);
                }
                layout.reconcile(java.util.Collections.emptyList());
                return layout;
            } catch (Exception ignored) {
                layout = new HomeLayout();
                layout.home.addAll(legacy.read());
                return layout;
            }
        }
    }
    private static void strings(JSONArray array, java.util.List<String> result) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (!(value instanceof String) || ((String) value).isEmpty()) throw new IllegalArgumentException();
            result.add((String) value);
        }
    }
    boolean write(HomeLayout layout) {
        synchronized (LOCK) {
            FileOutputStream out = null;
            try {
                JSONArray folders = new JSONArray();
                for (HomeLayout.Folder folder : layout.folders.values())
                    folders.put(new JSONObject().put("id", folder.id).put("name", folder.name)
                            .put("apps", new JSONArray(folder.apps)));
                byte[] bytes = new JSONObject().put("version", 2).put("home", new JSONArray(layout.home))
                        .put("folders", folders).toString().getBytes(StandardCharsets.UTF_8);
                out = file.startWrite();
                out.write(bytes);
                file.finishWrite(out);
                out = null;
                return java.util.Arrays.equals(bytes, file.readFully());
            } catch (Exception failure) {
                if (out != null) file.failWrite(out);
                return false;
            }
        }
    }
}
