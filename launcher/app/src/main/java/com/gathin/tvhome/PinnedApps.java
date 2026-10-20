package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Two dock rows the owner can rearrange on the TV. Defaults come from config ({@code dock_apps} /
 * {@code dock_apps_2}); hold-OK pin/move writes to SharedPreferences and wins until cleared.
 */
final class PinnedApps {
    private static final String PREFS = "pinned_apps";
    private static final String KEY_ROW1 = "row1";
    private static final String KEY_ROW2 = "row2";
    private static final String KEY_CUSTOMIZED = "customized";

    private final SharedPreferences prefs;
    private final int columns;
    private List<String> row1;
    private List<String> row2;

    PinnedApps(Context ctx, List<String> defaultRow1, List<String> defaultRow2, int columns) {
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.columns = Math.max(1, columns);
        if (prefs.getBoolean(KEY_CUSTOMIZED, false)) {
            row1 = loadList(KEY_ROW1);
            row2 = loadList(KEY_ROW2);
        } else {
            row1 = capped(defaultRow1);
            row2 = capped(defaultRow2);
        }
    }

    List<String> row1() {
        return Collections.unmodifiableList(row1);
    }

    List<String> row2() {
        return Collections.unmodifiableList(row2);
    }

    /** Row that currently holds this package: 1, 2, or 0 if unpinned. */
    int rowOf(String pkg) {
        if (pkg == null) return 0;
        if (row1.contains(pkg)) return 1;
        if (row2.contains(pkg)) return 2;
        return 0;
    }

    /** Pin or move {@code pkg} onto row 1 or 2 (drops it from the other row). */
    boolean setRow(String pkg, int row) {
        if (pkg == null || pkg.isEmpty() || (row != 1 && row != 2)) return false;
        List<String> target = row == 1 ? new ArrayList<>(row1) : new ArrayList<>(row2);
        List<String> other = row == 1 ? new ArrayList<>(row2) : new ArrayList<>(row1);
        other.remove(pkg);
        target.remove(pkg);
        if (target.size() >= columns) {
            // Full row: replace the last slot so pin always succeeds.
            target.remove(target.size() - 1);
        }
        target.add(pkg);
        if (row == 1) {
            row1 = capped(target);
            row2 = capped(other);
        } else {
            row2 = capped(target);
            row1 = capped(other);
        }
        return persist();
    }

    boolean unpin(String pkg) {
        if (pkg == null) return false;
        boolean changed = row1.remove(pkg) | row2.remove(pkg);
        return changed && persist();
    }

    /**
     * Swap {@code pkg} with its neighbour in the same row.
     * @param delta {@code -1} left, {@code +1} right (visual order; caller mirrors for RTL if needed)
     */
    boolean moveInRow(String pkg, int delta) {
        if (pkg == null || delta == 0) return false;
        int row = rowOf(pkg);
        if (row == 0) return false;
        List<String> list = new ArrayList<>(row == 1 ? row1 : row2);
        int i = list.indexOf(pkg);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= list.size()) return false;
        Collections.swap(list, i, j);
        if (row == 1) row1 = list;
        else row2 = list;
        return persist();
    }

    /** Whether {@code pkg} can move one step left or right in its row. */
    boolean canMove(String pkg, int delta) {
        if (pkg == null || delta == 0) return false;
        int row = rowOf(pkg);
        if (row == 0) return false;
        List<String> list = row == 1 ? row1 : row2;
        int i = list.indexOf(pkg);
        int j = i + delta;
        return i >= 0 && j >= 0 && j < list.size();
    }

    private boolean persist() {
        return prefs.edit()
                .putBoolean(KEY_CUSTOMIZED, true)
                .putString(KEY_ROW1, join(row1))
                .putString(KEY_ROW2, join(row2))
                .commit();
    }

    private List<String> loadList(String key) {
        return capped(split(prefs.getString(key, "")));
    }

    private List<String> capped(List<String> in) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String s : in) {
            if (s != null) {
                String t = s.trim();
                if (!t.isEmpty()) unique.add(t);
            }
            if (unique.size() >= columns) break;
        }
        return new ArrayList<>(unique);
    }

    private static List<String> split(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }
}
