package com.gathin.tvhome;

import android.content.ComponentName;

/**
 * An activity named in config.xml as "package/.Activity" or "package/full.ClassName", for screens that
 * have no leanback entry and cannot be told apart from their package's other MAIN activities by an action
 * (live_tv_package, launch_actions).
 */
final class ActivitySpec {
    private ActivitySpec() {}

    /** {package, fully qualified class}, or null when spec is not of the form "package/class". */
    static String[] split(String spec) {
        if (spec == null) return null;
        int slash = spec.indexOf('/');
        if (slash <= 0 || slash == spec.length() - 1) return null;
        String pkg = spec.substring(0, slash).trim();
        String cls = spec.substring(slash + 1).trim();
        if (pkg.isEmpty() || cls.isEmpty()) return null;
        if (cls.startsWith(".")) cls = pkg + cls;
        return new String[]{pkg, cls};
    }

    /** The component, or null when spec does not name an activity. */
    static ComponentName component(String spec) {
        String[] parts = split(spec);
        return parts == null ? null : new ComponentName(parts[0], parts[1]);
    }
}
