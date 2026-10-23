package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Persists the user's favorited apps in the launcher's private files directory.
 *
 * <p>Format: UTF-8 text, one package name per line, sorted, identical to library_apps.txt.
 * Written atomically via {@link AtomicFile} so a crash mid-write leaves the last good copy.</p>
 */
final class Favorites {

    private static final String TAG = "TvHome.Favorites";
    /** File name inside {@link Context#getFilesDir()}. */
    static final String FILE_NAME = "favorites.txt";
    /** Maximum number of favorites (generous for TV; keeps the file small and validation fast). */
    static final int MAX_FAVORITES = 64;
    private static final int MAX_BYTES = MAX_FAVORITES * 256;

    private Favorites() { }

    // ---- public API ---------------------------------------------------------------------------

    /** Loads the current favorites set; returns an empty set on any error (never null). */
    static Set<String> load(Context context) {
        return read(store(context));
    }

    /**
     * Adds or removes {@code pkg} from the favorites file atomically.
     *
     * @param add {@code true} to add, {@code false} to remove.
     * @return {@code true} on success; {@code false} when the file could not be written (the
     *         existing content is preserved and the caller should show an error).
     */
    static boolean save(Context context, String pkg, boolean add) {
        Set<String> current;
        try {
            current = readStrict(store(context));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Keeping unreadable favorites unchanged", e);
            return false;
        }
        return write(store(context), current, pkg, add);
    }

    // ---- logic (package-private for tests) ---------------------------------------------------

    /**
     * Returns a new set that is {@code saved} with {@code pkg} added or removed.
     *
     * @throws IllegalArgumentException on an invalid package name or if adding would exceed
     *                                  {@link #MAX_FAVORITES}.
     */
    static Set<String> change(Set<String> saved, String pkg, boolean add) {
        if (!validPackage(pkg)) throw new IllegalArgumentException("Invalid package: " + pkg);
        Set<String> next = new HashSet<>(saved);
        if (add) {
            next.add(pkg);
            if (next.size() > MAX_FAVORITES) throw new IllegalArgumentException("Too many favorites");
        } else {
            next.remove(pkg);
        }
        return next;
    }

    /** Encodes the set as sorted, newline-separated package names (UTF-8). */
    static String encode(Set<String> favorites) {
        if (favorites.size() > MAX_FAVORITES) throw new IllegalArgumentException("Too many favorites");
        StringBuilder sb = new StringBuilder();
        for (String pkg : new TreeSet<>(favorites)) {
            if (!validPackage(pkg)) throw new IllegalArgumentException("Invalid package: " + pkg);
            sb.append(pkg).append('\n');
        }
        return sb.toString();
    }

    /** Parses a string produced by {@link #encode}; throws {@link IllegalArgumentException} on any error. */
    static Set<String> decode(String text) {
        if (text.length() > MAX_BYTES) throw new IllegalArgumentException("Favorites file too large");
        Set<String> result = new HashSet<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') continue;
            if (i == start && i == text.length()) break;   // trailing newline
            String pkg = text.substring(start, i);
            if (!validPackage(pkg) || !result.add(pkg) || result.size() > MAX_FAVORITES)
                throw new IllegalArgumentException("Invalid favorites file");
            start = i + 1;
        }
        return result;
    }

    /**
     * Returns {@code true} when {@code pkg} is a syntactically valid Android package name.
     * Replicates {@link LibraryApps#validPackage} so Favorites has no dependency on it.
     */
    static boolean validPackage(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.length() > 255) return false;
        boolean first = true;
        boolean hasSeparator = false;
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            if (c == '.') {
                if (first) return false;
                first = true;
                hasSeparator = true;
            } else {
                boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
                if (!letter && (first || c < '0' || c > '9')) return false;
                first = false;
            }
        }
        return hasSeparator && !first;
    }

    // ---- file I/O ----------------------------------------------------------------------------

    private static AtomicFile store(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    private static Set<String> read(AtomicFile file) {
        try {
            return readStrict(file);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not read favorites", e);
            return Collections.emptySet();
        }
    }

    private static Set<String> readStrict(AtomicFile file) throws IOException {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists())
            return Collections.emptySet();
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_BYTES) throw new IOException("Favorites file too large");
                bytes.write(buffer, 0, count);
            }
            return decode(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private static boolean write(AtomicFile file, Set<String> current, String pkg, boolean add) {
        FileOutputStream output = null;
        try {
            Set<String> next = change(current, pkg, add);
            byte[] bytes = encode(next).getBytes(StandardCharsets.UTF_8);
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
            return true;
        } catch (IOException | RuntimeException e) {
            if (output != null) file.failWrite(output);
            Log.w(TAG, "Could not save favorites", e);
            return false;
        }
    }
}
