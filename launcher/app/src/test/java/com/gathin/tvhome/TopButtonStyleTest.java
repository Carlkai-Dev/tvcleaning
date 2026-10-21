package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** inputs_button / wifi_button / settings_button and their _width keys. */
public class TopButtonStyleTest {

    @Test
    public void knownValuesAnyCase() {
        assertEquals(TopButtonStyle.ICON, TopButtonStyle.show(" Icon "));
        assertEquals(TopButtonStyle.TEXT, TopButtonStyle.show("TEXT"));
        assertEquals(TopButtonStyle.BOTH, TopButtonStyle.show("both"));
    }

    @Test
    public void anythingElseShowsBoth() {
        assertEquals(TopButtonStyle.BOTH, TopButtonStyle.show(null));
        assertEquals(TopButtonStyle.BOTH, TopButtonStyle.show(""));
        assertEquals(TopButtonStyle.BOTH, TopButtonStyle.show("label"));
    }

    @Test
    public void whatEachShows() {
        TopButtonStyle both = new TopButtonStyle("both", 0), icon = new TopButtonStyle("icon", 0),
                text = new TopButtonStyle("text", 0);
        assertTrue(both.showsIcon() && both.showsText());
        assertTrue(icon.showsIcon());
        assertFalse(icon.showsText());
        assertFalse(text.showsIcon());
        assertTrue(text.showsText());
    }

    @Test
    public void widthOutOfRangeKeepsTheLayoutsOwn() {
        assertEquals(150, new TopButtonStyle("both", 150).textWidthDp);
        assertEquals(0, new TopButtonStyle("both", -5).textWidthDp);
        assertEquals(0, new TopButtonStyle("both", TopButtonStyle.MAX_TEXT_WIDTH_DP + 1).textWidthDp);
    }

    @Test
    public void unsetPillsFollowIconOnlyButtons() {
        TopButtonStyle wifi = TopButtonStyle.wifi("", 0, true, 0);
        assertEquals(TopButtonStyle.ICON, wifi.show);
        TopButtonStyle named = TopButtonStyle.wifi("", 0, true, 120);
        assertEquals(TopButtonStyle.BOTH, named.show);
        assertEquals(120, named.textWidthDp);
        assertEquals(TopButtonStyle.ICON, TopButtonStyle.settings("", 0, true).show);
        assertEquals(TopButtonStyle.BOTH, TopButtonStyle.settings("", 0, false).show);
        assertEquals(TopButtonStyle.BOTH, TopButtonStyle.wifi(null, 0, false, 120).show);
        assertEquals(0, TopButtonStyle.wifi(null, 0, false, 120).textWidthDp);
    }

    @Test
    public void perPillKeysWinOverIconOnlyButtons() {
        assertEquals(TopButtonStyle.TEXT, TopButtonStyle.settings("text", 0, true).show);
        TopButtonStyle wifi = TopButtonStyle.wifi("both", 200, true, 0);
        assertEquals(TopButtonStyle.BOTH, wifi.show);
        assertEquals(200, wifi.textWidthDp);
    }
}
