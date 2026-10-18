package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What you change on the TV itself (dock order, hidden apps, recent apps). The dock starts from
 * config.xml's dock_apps; once you edit it with the remote, the saved order wins.
 */
final class Prefs {

    private static final String DOCK = "dock";
    private static final String HIDDEN = "hidden";
    private static final String RECENT = "recent";
    private static final int RECENT_KEEP = 12;

    private Prefs() {
    }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences("tvhome", Context.MODE_PRIVATE);
    }

    static List<String> dock(Context c) {
        SharedPreferences p = of(c);
        if (!p.contains(DOCK)) return Arrays.asList(c.getResources().getStringArray(R.array.dock_apps));
        return split(p.getString(DOCK, ""));
    }

    static void setDock(Context c, List<String> pkgs) {
        of(c).edit().putString(DOCK, TextUtils.join(",", pkgs)).apply();
    }

    static Set<String> hidden(Context c) {
        return new HashSet<>(split(of(c).getString(HIDDEN, "")));
    }

    static void hide(Context c, String pkg) {
        Set<String> s = hidden(c);
        s.add(pkg);
        of(c).edit().putString(HIDDEN, TextUtils.join(",", s)).apply();
    }

    /** Most recent first. */
    static List<String> recent(Context c) {
        return split(of(c).getString(RECENT, ""));
    }

    static void pushRecent(Context c, String pkg) {
        List<String> r = recent(c);
        r.remove(pkg);
        r.add(0, pkg);
        while (r.size() > RECENT_KEEP) r.remove(r.size() - 1);
        of(c).edit().putString(RECENT, TextUtils.join(",", r)).apply();
    }

    static void removeRecent(Context c, String pkg) {
        List<String> r = recent(c);
        r.remove(pkg);
        of(c).edit().putString(RECENT, TextUtils.join(",", r)).apply();
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            if (!part.trim().isEmpty()) out.add(part.trim());
        }
        return out;
    }
}
