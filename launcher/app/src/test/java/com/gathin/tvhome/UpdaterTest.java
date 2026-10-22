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
        assertEquals(-1, Updater.versionOf("launcher-99999999999999999999"));
    }
}
