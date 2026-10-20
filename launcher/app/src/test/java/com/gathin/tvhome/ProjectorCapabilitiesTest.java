package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.ProjectorCapabilities.Feature;
import com.gathin.tvhome.ProjectorCapabilities.Probe;
import com.gathin.tvhome.ProjectorCapabilities.State;

/** Plain JVM tests for projector capability resolution. */
public class ProjectorCapabilitiesTest {

    @Test
    public void failedProbeIsUnknownExceptDiagnostics() {
        Probe p = new Probe();
        p.probeFailed = true;
        ProjectorCapabilities c = ProjectorCapabilities.resolve(p);
        assertEquals(State.UNKNOWN, c.state(Feature.AUTOFOCUS));
        assertEquals(State.UNKNOWN, c.state(Feature.HDMI));
        assertEquals(State.SUPPORTED_DIRECT, c.state(Feature.DIAGNOSTICS));
    }

    @Test
    public void nullProbeIsUnknown() {
        ProjectorCapabilities c = ProjectorCapabilities.resolve(null);
        assertEquals(State.UNKNOWN, c.state(Feature.PICTURE));
        assertEquals(State.SUPPORTED_DIRECT, c.state(Feature.DIAGNOSTICS));
    }

    @Test
    public void genericStickGetsShortcutsNotFakeDirectControls() {
        Probe p = new Probe();
        p.genericSettings = true;
        p.displaySettings = true;
        p.soundSettings = true;
        ProjectorCapabilities c = ProjectorCapabilities.resolve(p);
        assertEquals(State.SUPPORTED_SETTINGS_SHORTCUT, c.state(Feature.PROJECTOR_SETTINGS));
        // Generic settings contain no projection controls: no fake shortcut for advanced features.
        assertEquals(State.UNSUPPORTED, c.state(Feature.AUTOFOCUS));
        assertEquals(State.UNSUPPORTED, c.state(Feature.KEYSTONE));
        assertEquals(State.SUPPORTED_SETTINGS_SHORTCUT, c.state(Feature.PICTURE));
        assertEquals(State.UNSUPPORTED, c.state(Feature.HDMI));
        assertFalse(ProjectorCapabilities.actionable(c.state(Feature.HDMI)));
    }

    @Test
    public void pictureFallsBackToGenericSettings() {
        Probe p = new Probe();
        p.genericSettings = true;   // no dedicated display activity: Display & Sound inside TV settings
        ProjectorCapabilities c = ProjectorCapabilities.resolve(p);
        assertEquals(State.SUPPORTED_SETTINGS_SHORTCUT, c.state(Feature.PICTURE));
        Probe none = new Probe();
        assertEquals(State.UNSUPPORTED, ProjectorCapabilities.resolve(none).state(Feature.PICTURE));
    }

    @Test
    public void probedDirectFocusIsDirect() {
        Probe p = new Probe();
        p.projectorSettingsDirect = true;
        p.focusDirect = true;
        p.hasPassthroughInput = true;
        ProjectorCapabilities c = ProjectorCapabilities.resolve(p);
        assertEquals(State.SUPPORTED_DIRECT, c.state(Feature.AUTOFOCUS));
        assertEquals(State.SUPPORTED_DIRECT, c.state(Feature.HDMI));
        assertTrue(ProjectorCapabilities.actionable(c.state(Feature.AUTOFOCUS)));
        // Keystone was not probed direct: with a real projector-settings entry it is a shortcut.
        assertEquals(State.SUPPORTED_SETTINGS_SHORTCUT, c.state(Feature.KEYSTONE));
    }

    @Test
    public void hdmiFallbackAndSystemStates() {
        Probe p = new Probe();
        p.inputFallback = true;   // configured input_action / source panel (e.g. XGIMI): Inputs menu opens
        p.genericSettings = true;
        ProjectorCapabilities c = ProjectorCapabilities.resolve(p);
        assertEquals(State.SUPPORTED_DIRECT, c.state(Feature.HDMI));
        assertEquals(State.SUPPORTED_SETTINGS_SHORTCUT, c.state(Feature.SYSTEM));
        Probe none = new Probe();
        assertEquals(State.UNSUPPORTED, ProjectorCapabilities.resolve(none).state(Feature.SYSTEM));
    }

    @Test
    public void privilegedOnlySettingsPropagate() {
        Probe p = new Probe();
        p.projectorSettingsNeedsPrivilege = true;
        ProjectorCapabilities c = ProjectorCapabilities.resolve(p);
        assertEquals(State.REQUIRES_PRIVILEGED_ACCESS, c.state(Feature.PROJECTOR_SETTINGS));
        assertEquals(State.REQUIRES_PRIVILEGED_ACCESS, c.state(Feature.KEYSTONE));
        assertFalse(ProjectorCapabilities.actionable(c.state(Feature.KEYSTONE)));
    }
}
