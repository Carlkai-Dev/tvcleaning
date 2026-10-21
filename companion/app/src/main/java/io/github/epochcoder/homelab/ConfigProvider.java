package io.github.epochcoder.homelab;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Lets companion/push-config.sh put config.json, or the extra CA certificate on its own (ca.pem), into private
 * storage, where the adb shell cannot write directly. Only callers holding WRITE_SECURE_SETTINGS (the adb shell,
 * not ordinary apps) get in, only those two names are accepted, and they open write-only: the API keys inside
 * never come back out through here.
 *
 *   content write --uri content://io.github.epochcoder.homelab.config/config.json < /data/local/tmp/config.json
 *   content write --uri content://io.github.epochcoder.homelab.config/ca.pem < /data/local/tmp/root.crt
 *   content delete --uri content://io.github.epochcoder.homelab.config/config.json
 */
public class ConfigProvider extends ContentProvider {

    private File file(Uri uri) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        String name = segments.size() == 1 ? segments.get(0) : "";
        if (getContext() == null || !(Config.FILE_NAME.equals(name) || Config.CA_FILE_NAME.equals(name))) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        return new File(getContext().getFilesDir(), name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.contains("w")) throw new FileNotFoundException("write-only: " + uri);
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_WRITE_ONLY
                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            return file(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
