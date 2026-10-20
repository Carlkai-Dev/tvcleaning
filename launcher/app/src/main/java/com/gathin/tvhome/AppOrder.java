package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Component keys, never labels or adapter positions, identify apps in a saved order. */
final class AppOrder {
    enum Direction { LEFT, RIGHT, UP, DOWN }

    static List<String> reconcile(List<String> defaults, List<String> saved) {
        Set<String> remaining = new LinkedHashSet<>(defaults);
        List<String> result = new ArrayList<>();
        for (String key : saved) if (remaining.remove(key)) result.add(key);
        result.addAll(remaining);
        return result;
    }

    /** Replace visible slots while retaining ranks of temporarily missing or hidden apps. */
    static List<String> merge(List<String> saved, List<String> visible) {
        List<String> result = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>(saved);
        Set<String> present = new LinkedHashSet<>(visible);
        int next = 0;
        for (String key : unique) {
            if (present.contains(key)) {
                if (next < visible.size()) result.add(visible.get(next++));
            } else {
                result.add(key);
            }
        }
        while (next < visible.size()) result.add(visible.get(next++));
        return result;
    }

    static int target(int position, int count, int columns, Direction direction) {
        if (position < 0 || position >= count || columns < 1) return position;
        switch (direction) {
            case LEFT: return position % columns == 0 ? position : position - 1;
            case RIGHT: return position % columns == columns - 1 || position == count - 1
                    ? position : position + 1;
            case UP: return position < columns ? position : position - columns;
            case DOWN: return position / columns == (count - 1) / columns
                    ? position : Math.min(position + columns, count - 1);
            default: return position;
        }
    }

    /** The folder's short final row is centred: vertical focus follows the closest visible tile. */
    static int centeredTarget(int position, int count, int columns, Direction direction) {
        int ordinary = target(position, count, columns, direction);
        if (position < 0 || position >= count || columns < 1) return ordinary;
        int remainder = count % columns, lastRow = count - remainder;
        if (remainder == 0 || lastRow == 0) return ordinary;
        float inset = (columns - remainder) / 2f;
        if (direction == Direction.UP && position >= lastRow)
            return lastRow - columns + Math.min(columns - 1, Math.round(position - lastRow + inset));
        if (direction == Direction.DOWN && position >= lastRow - columns && position < lastRow)
            return lastRow + Math.max(0, Math.min(remainder - 1, Math.round(position % columns - inset)));
        return ordinary;
    }
}
