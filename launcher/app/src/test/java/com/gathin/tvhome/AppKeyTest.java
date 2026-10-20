package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Plain JVM tests for the remote app buttons (config.xml app_key_keycodes / app_key_packages). */
public class AppKeyTest {

    private static final int[] CODES = {200, 191, 190};
    private static final String[] PACKAGES = {"in.example.tldr", " com.netflix.ninja ", "com.google.android.youtube.tv"};

    @Test
    public void matchesByKeycodeAndTrims() {
        assertEquals("in.example.tldr", HomeKeyService.appForKeycode(200, CODES, PACKAGES));
        assertEquals("com.netflix.ninja", HomeKeyService.appForKeycode(191, CODES, PACKAGES));
        assertEquals("com.google.android.youtube.tv", HomeKeyService.appForKeycode(190, CODES, PACKAGES));
    }

    @Test
    public void unlistedKeycodeIsNotConsumed() {
        assertNull(HomeKeyService.appForKeycode(195, CODES, PACKAGES));
        assertNull(HomeKeyService.appForKeycode(191, new int[0], new String[0]));
    }

    @Test
    public void unpairedEntriesAreIgnored() {
        assertNull(HomeKeyService.appForKeycode(190, CODES, new String[] {"a", "b"}));
        assertEquals("b", HomeKeyService.appForKeycode(191, CODES, new String[] {"a", "b"}));
        assertNull(HomeKeyService.appForKeycode(191, new int[] {200}, PACKAGES));
    }

    @Test
    public void blankPackageStillClaimsTheKey() {
        // A listed keycode with an empty package swallows the button instead of passing it on.
        assertEquals("", HomeKeyService.appForKeycode(200, new int[] {200}, new String[] {"  "}));
    }
}
