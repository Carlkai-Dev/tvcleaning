package io.github.gkudva96.debugswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DebuggingTest {
    @Test
    public void hints() {
        assertEquals(Debugging.Hint.NOT_GRANTED, Debugging.hint(true, false, true));
        assertEquals(Debugging.Hint.NOT_GRANTED, Debugging.hint(false, false, false));
        assertEquals(Debugging.Hint.OPEN, Debugging.hint(true, true, true));
        assertEquals(Debugging.Hint.CLOSED, Debugging.hint(false, true, true));
        assertEquals(Debugging.Hint.NEEDS_WIFI, Debugging.hint(false, true, false));
    }

    @Test
    public void ports() {
        assertEquals(37123, Debugging.port("37123"));
        assertEquals(37123, Debugging.port(" 37123\n"));
        assertEquals(-1, Debugging.port(""));
        assertEquals(-1, Debugging.port("0"));
        assertEquals(-1, Debugging.port("70000"));
        assertEquals(-1, Debugging.port(null));
    }

    @Test
    public void endpoints() {
        assertEquals("192.168.1.50:37123", Debugging.endpoint("192.168.1.50", 37123));
        assertNull(Debugging.endpoint("192.168.1.50", -1));
        assertNull(Debugging.endpoint(null, 37123));
    }
}
