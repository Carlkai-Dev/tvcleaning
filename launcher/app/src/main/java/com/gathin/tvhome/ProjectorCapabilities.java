package com.gathin.tvhome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Projector capability resolution: pure JVM, no Android imports,
 * so the rules run in plain unit tests. Android probing lives in ProjectorDetector and only fills
 * a Probe; nothing here invents a direct control that was not probed.
 */
final class ProjectorCapabilities {

    enum State {
        SUPPORTED_DIRECT,
        SUPPORTED_SETTINGS_SHORTCUT,
        REQUIRES_PRIVILEGED_ACCESS,
        UNSUPPORTED,
        UNKNOWN
    }

    enum Feature {
        PROJECTOR_SETTINGS, HDMI, PICTURE, AUDIO, AUTOFOCUS, KEYSTONE, ZOOM, SYSTEM, DIAGNOSTICS
    }

    /** Raw probe facts; false means "not found", probeFailed means the probe itself could not run. */
    static final class Probe {
        boolean probeFailed;
        boolean projectorSettingsDirect;
        boolean projectorSettingsNeedsPrivilege;
        boolean genericSettings;
        boolean displaySettings;
        boolean soundSettings;
        boolean hasPassthroughInput;
        boolean inputFallback;
        boolean focusDirect;
        boolean keystoneDirect;
        boolean zoomDirect;
    }

    private final EnumMap<Feature, State> states;

    private ProjectorCapabilities(EnumMap<Feature, State> states) {
        this.states = states;
    }

    State state(Feature feature) {
        State s = states.get(feature);
        return s != null ? s : State.UNKNOWN;
    }

    Map<Feature, State> asMap() {
        return Collections.unmodifiableMap(states);
    }

    /** True only for states that may open something real. */
    static boolean actionable(State s) {
        return s == State.SUPPORTED_DIRECT || s == State.SUPPORTED_SETTINGS_SHORTCUT;
    }

    static ProjectorCapabilities resolve(Probe p) {
        EnumMap<Feature, State> out = new EnumMap<>(Feature.class);
        if (p == null || p.probeFailed) {
            for (Feature f : Feature.values()) out.put(f, State.UNKNOWN);
            out.put(Feature.DIAGNOSTICS, State.SUPPORTED_DIRECT);
            return new ProjectorCapabilities(out);
        }
        State projectorSettings = p.projectorSettingsDirect ? State.SUPPORTED_DIRECT
                : p.projectorSettingsNeedsPrivilege ? State.REQUIRES_PRIVILEGED_ACCESS
                : p.genericSettings ? State.SUPPORTED_SETTINGS_SHORTCUT : State.UNSUPPORTED;
        out.put(Feature.PROJECTOR_SETTINGS, projectorSettings);
        out.put(Feature.HDMI, p.hasPassthroughInput || p.inputFallback
                ? State.SUPPORTED_DIRECT : State.UNSUPPORTED);
        // Picture often lives inside the generic TV settings (Display & Sound) even when no
        // dedicated DISPLAY_SETTINGS activity exists (measured on the YYT stick, M1).
        out.put(Feature.PICTURE, p.displaySettings || p.genericSettings
                ? State.SUPPORTED_SETTINGS_SHORTCUT : State.UNSUPPORTED);
        out.put(Feature.AUDIO, p.soundSettings
                ? State.SUPPORTED_SETTINGS_SHORTCUT : State.UNSUPPORTED);
        out.put(Feature.AUTOFOCUS, advanced(p.focusDirect, projectorSettings));
        out.put(Feature.KEYSTONE, advanced(p.keystoneDirect, projectorSettings));
        out.put(Feature.ZOOM, advanced(p.zoomDirect, projectorSettings));
        out.put(Feature.SYSTEM, p.genericSettings
                ? State.SUPPORTED_SETTINGS_SHORTCUT : State.UNSUPPORTED);
        out.put(Feature.DIAGNOSTICS, State.SUPPORTED_DIRECT);
        return new ProjectorCapabilities(out);
    }

    /**
     * Focus/keystone/zoom: direct only when probed; shortcut only when a real projector-settings
     * entry point exists. A generic system-settings fallback does not contain projection controls
     * (measured on the YYT stick, M1), so claiming a shortcut there would be a fake feature.
     */
    private static State advanced(boolean direct, State projectorSettings) {
        if (direct) return State.SUPPORTED_DIRECT;
        if (projectorSettings == State.REQUIRES_PRIVILEGED_ACCESS) return State.REQUIRES_PRIVILEGED_ACCESS;
        return projectorSettings == State.SUPPORTED_DIRECT
                ? State.SUPPORTED_SETTINGS_SHORTCUT : State.UNSUPPORTED;
    }
}
