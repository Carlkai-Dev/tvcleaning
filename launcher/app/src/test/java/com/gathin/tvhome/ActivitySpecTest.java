package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class ActivitySpecTest {
    @Test public void aLeadingDotIsRelativeToThePackage() {
        assertArrayEquals(new String[]{"com.cvte.tv.androidsetting", "com.cvte.tv.androidsetting.TvSettingActivity"},
                ActivitySpec.split("com.cvte.tv.androidsetting/.TvSettingActivity"));
    }

    @Test public void aFullClassNameIsKept() {
        assertArrayEquals(new String[]{"a.pkg", "b.other.Main"}, ActivitySpec.split("a.pkg/b.other.Main"));
    }

    @Test public void spacesAroundTheHalvesAreTrimmed() {
        assertArrayEquals(new String[]{"a.pkg", "a.pkg.Main"}, ActivitySpec.split(" a.pkg / .Main "));
    }

    @Test public void aPlainPackageOrActionIsNotAnActivity() {
        assertNull(ActivitySpec.split("com.mediatek.wwtv.tvcenter"));
        assertNull(ActivitySpec.split("com.xgimi.action.hdmiPlayer"));
        assertNull(ActivitySpec.split(""));
        assertNull(ActivitySpec.split(null));
    }

    @Test public void aMissingHalfIsNotAnActivity() {
        assertNull(ActivitySpec.split("/.Main"));
        assertNull(ActivitySpec.split("a.pkg/"));
        assertNull(ActivitySpec.split("a.pkg/ "));
    }
}
