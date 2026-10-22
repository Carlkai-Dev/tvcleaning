package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UpdaterTest {
    @Test public void theTagEndsInTheVersionCode() {
        assertEquals(1001, Updater.versionOf("launcher-1001"));
        assertEquals(8, Updater.versionOf("8"));
    }

    @Test public void aTagWithoutANumberIsNeverNewer() {
        assertEquals(-1, Updater.versionOf("v2.6-beta"));
        assertEquals(-1, Updater.versionOf(""));
        assertEquals(-1, Updater.versionOf("launcher-"));
        assertEquals(-1, Updater.versionOf("launcher-99999999999999999999"));
        // 18 digits is the most it reads, so parseLong can never overflow.
        assertEquals(999999999999999999L, Updater.versionOf("launcher-999999999999999999"));
        assertEquals(-1, Updater.versionOf("launcher-9999999999999999999"));
    }

    @Test public void onlyTheReleaseApkForThatVersionIsInstalled() {
        assertEquals("tvhome-1004.apk", Updater.apkName(1004));
    }
}
