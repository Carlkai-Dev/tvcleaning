package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PaletteTest {

    private static final int NETFLIX = 0xFFE50914;

    @Test
    public void noKeysIsTheReferenceLook() {
        Palette p = Palette.parse(null, null, null, null);
        assertSame(Palette.NONE, p);
        assertEquals(0, p.accent);
        assertEquals(0, p.text);
        assertEquals(0, p.surface);
        assertEquals(NETFLIX, p.glowFor(NETFLIX));
        assertFalse(p.glowOff());
    }

    @Test
    public void coloursAreOpaqueHex() {
        Palette p = Palette.parse("#D9622B", " #f3e6d8 ", "#2A1711", "");
        assertEquals(0xFFD9622B, p.accent);
        assertEquals(0xFFF3E6D8, p.text);
        assertEquals(0xFF2A1711, p.surface);
    }

    @Test
    public void malformedColoursAreIgnoredOneByOne() {
        Palette p = Palette.parse("orange", "#FFF", "#2A1711", null);
        assertEquals(0, p.accent);
        assertEquals(0, p.text);
        assertEquals(0xFF2A1711, p.surface);
    }

    @Test
    public void glowFollowsTheAppByDefault() {
        assertEquals(NETFLIX, Palette.parse(null, null, null, "app").glowFor(NETFLIX));
        assertEquals(NETFLIX, Palette.parse(null, null, null, "APP").glowFor(NETFLIX));
        assertEquals(NETFLIX, Palette.parse(null, null, null, "sparkly").glowFor(NETFLIX));
    }

    @Test
    public void glowCanBeFixedOrOff() {
        Palette fixed = Palette.parse(null, null, null, "#2F6BFF");
        assertEquals(0xFF2F6BFF, fixed.glowFor(NETFLIX));
        assertFalse(fixed.glowOff());

        Palette off = Palette.parse(null, null, null, " off ");
        assertTrue(off.glowOff());
        assertEquals(0, off.glowFor(NETFLIX));
    }

    @Test
    public void textColourKeepsTheViewsOwnAlpha() {
        Palette p = Palette.parse(null, "#BFF5EE", null, null);
        assertEquals(0xA6BFF5EE, p.textWithAlphaOf(0xA6FFFFFF));
        assertEquals(0xFFBFF5EE, p.textWithAlphaOf(0xFFFFFFFF));
        assertEquals(0x8CFFFFFF, Palette.NONE.textWithAlphaOf(0x8CFFFFFF));
    }
}
