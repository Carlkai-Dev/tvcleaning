package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinkTilesTest {
    @Test
    public void anEntryNamesItsTileAfterTheLabel() {
        LinkTiles.Link l = LinkTiles.parse("VTV 1|vn.vtv.vtvgotv|vtvgo://play?mediaType=CHANNEL&contentId=1|#D71920");
        assertEquals("VTV 1", l.label);
        assertEquals("vn.vtv.vtvgotv", l.target);
        assertEquals("vtvgo://play?mediaType=CHANNEL&contentId=1", l.uri);
        assertEquals("link.tile.vtv1", l.pkg);
        assertEquals(0xFFD71920, l.color);
    }

    @Test
    public void theColourIsOptional() {
        assertEquals(0xFF3A3A44, LinkTiles.parse("News|com.example.tv|https://example.com/live").color);
        assertEquals(0xFF3A3A44, LinkTiles.parse("News|com.example.tv|https://example.com/live|red").color);
    }

    @Test
    public void malformedEntriesAreSkipped() {
        assertNull(LinkTiles.parse(null));
        assertNull(LinkTiles.parse("News|com.example.tv"));
        assertNull(LinkTiles.parse("|com.example.tv|https://example.com"));
        assertNull(LinkTiles.parse("News|com.example.tv|no scheme"));
        assertNull(LinkTiles.parse("News|com example|https://example.com"));
        assertNull(LinkTiles.parse("++|com.example.tv|https://example.com"));
        assertNull(LinkTiles.parse("A label that is far too long to fit|com.example.tv|https://example.com"));
    }

    @Test
    public void onlyLinkPicturesAreThemeFiles() {
        assertTrue(LinkTiles.isPictureName("link-vtv1.jpg"));
        assertFalse(LinkTiles.isPictureName("link-../x.jpg"));
        assertFalse(LinkTiles.isPictureName("link-VTV1.jpg"));
        assertFalse(LinkTiles.isPictureName("vtv1.jpg"));
    }
}
