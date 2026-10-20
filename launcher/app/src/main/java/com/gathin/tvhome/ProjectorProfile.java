package com.gathin.tvhome;

import java.util.Locale;

/**
 * Projector identity matching: pure JVM. The Android module
 * identity (e.g. a SkyworthDigital YYT stick) is shared by several commercial projectors, so it
 * never proves the commercial model or its focus/keystone APIs; ambiguous matches stay generic
 * and the owner picks the commercial profile manually.
 */
final class ProjectorProfile {

    static final String GENERIC = "generic-android-projector";
    static final String SKYWORTH_YYT_MODULE = "skyworth-yyt-module";

    private ProjectorProfile() {}

    /**
     * Returns the module profile id that the identity proves, or GENERIC. Commercial model names
     * (BYINTEK T5 Max, Wanbo X5 Pro, ...) are deliberately never returned from build identity:
     * the same YYT stick ships in more than one projector.
     */
    static String matchModule(String manufacturer, String device, String model, String board) {
        String m = lower(manufacturer), d = lower(device), b = lower(board);
        if (("skyworthdigital".equals(m) || m.contains("skyworth")) && "yyt".equals(d)) {
            return SKYWORTH_YYT_MODULE;
        }
        if (b.contains("amlogic") || d.contains("amlogic")) return GENERIC;
        return GENERIC;
    }

    /**
     * Manual profile selection: a known selection wins; "auto" or anything unknown keeps
     * the detected module profile. Commercial model names are not valid selections.
     */
    static String effectiveProfile(String selection, String detected) {
        if (GENERIC.equals(selection) || SKYWORTH_YYT_MODULE.equals(selection)) return selection;
        return detected != null ? detected : GENERIC;
    }

    /** True when the device plausibly is a projector host and Projector Mode is worth offering. */
    static boolean looksLikeProjector(String manufacturer, String device, String model) {
        String all = lower(manufacturer) + " " + lower(device) + " " + lower(model);
        return all.contains("projector") || all.contains("yyt")
                || all.contains("xgimi") || all.contains("jmgo") || all.contains("wanbo")
                || all.contains("byintek");
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }
}
