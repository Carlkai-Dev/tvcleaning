package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.SecureRandom;

public class AppLockTest {
    @Test
    public void storedPinMatchesOnlyItself() {
        String stored = AppLock.store("1234", new SecureRandom());
        assertTrue(AppLock.matches(stored, "1234"));
        assertFalse(AppLock.matches(stored, "1235"));
        assertFalse(AppLock.matches(stored, "12345"));
        assertFalse(AppLock.matches(stored, ""));
        assertFalse(AppLock.matches(stored, null));
    }

    @Test
    public void storedFormIsSaltedAndHasNoPin() {
        String a = AppLock.store("1234", new SecureRandom());
        String b = AppLock.store("1234", new SecureRandom());
        assertNotEquals(a, b);
        assertFalse(a.contains("1234"));
        assertEquals(32 + 1 + 64, a.length());
    }

    @Test
    public void brokenStoreMatchesNothing() {
        assertFalse(AppLock.matches("", "1234"));
        assertFalse(AppLock.matches(null, "1234"));
        assertFalse(AppLock.matches("abc:def", "1234"));
    }

    @Test
    public void onlyFourDigitsAreValid() {
        assertTrue(AppLock.valid("0000"));
        assertFalse(AppLock.valid("123"));
        assertFalse(AppLock.valid("12a4"));
        assertFalse(AppLock.valid("١٢٣٤"));
    }
}
