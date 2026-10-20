package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for projector identity matching. */
public class ProjectorProfileTest {

    @Test
    public void yytStickMatchesModuleProfileNeverCommercialModel() {
        String id = ProjectorProfile.matchModule("SkyworthDigital", "YYT", "4K Google TV Stick", "HP46B");
        assertEquals(ProjectorProfile.SKYWORTH_YYT_MODULE, id);
        // The same stick ships in BYINTEK and Wanbo projectors: no commercial name may be inferred.
        assertFalse(id.contains("byintek"));
        assertFalse(id.contains("wanbo"));
    }

    @Test
    public void unknownDeviceIsGeneric() {
        assertEquals(ProjectorProfile.GENERIC,
                ProjectorProfile.matchModule("SomeTV", "tv1", "Living Room TV", ""));
        assertEquals(ProjectorProfile.GENERIC, ProjectorProfile.matchModule(null, null, null, null));
    }

    @Test
    public void manualSelectionWinsOnlyWhenKnown() {
        assertEquals(ProjectorProfile.GENERIC,
                ProjectorProfile.effectiveProfile("generic-android-projector", ProjectorProfile.SKYWORTH_YYT_MODULE));
        assertEquals(ProjectorProfile.SKYWORTH_YYT_MODULE,
                ProjectorProfile.effectiveProfile("auto", ProjectorProfile.SKYWORTH_YYT_MODULE));
        // A commercial name is not a valid selection and must not override detection.
        assertEquals(ProjectorProfile.SKYWORTH_YYT_MODULE,
                ProjectorProfile.effectiveProfile("byintek-t5-max", ProjectorProfile.SKYWORTH_YYT_MODULE));
    }

    @Test
    public void projectorHintsAreDetected() {
        assertTrue(ProjectorProfile.looksLikeProjector("SkyworthDigital", "YYT", "4K Google TV Stick"));
        assertTrue(ProjectorProfile.looksLikeProjector("XGIMI", "xk03h", "Horizon"));
        assertFalse(ProjectorProfile.looksLikeProjector("Sony", "bravia", "Bravia TV"));
    }
}
