package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One test per config.json section, so each reads on its own. */
public class ConfigTest {
    @Test
    public void emptyOrBrokenConfigConfiguresNothing() {
        for (String json : new String[]{"{}", "not json", "[]", ""}) {
            Config c = Config.parse(json);
            assertFalse(json, c.homepage.configured());
            assertEquals("", c.caPem);
        }
    }

    @Test
    public void caPemComesFromConfigAndTheSeparateFile() {
        assertEquals("PEM", Config.parse("{\"ca_pem\":\" PEM \"}").caPem);
        assertEquals("FILE", Config.parse("{}", " FILE\n").caPem);
        assertEquals("JSON\nFILE", Config.parse("{\"ca_pem\":\"JSON\"}", "FILE").caPem);
        assertEquals("FILE", Config.parse("not json", "FILE").caPem);
        assertEquals("", Config.parse("{}", "").caPem);
    }

    @Test
    public void onlyHttpUrls() {
        assertEquals("", Config.baseUrl("file:///sdcard/x"));
        assertEquals("", Config.baseUrl("javascript:alert(1)"));
        assertEquals("", Config.baseUrl("photos.lan"));
        assertEquals("HTTPS://Photos.lan:8443/immich", Config.baseUrl(" HTTPS://Photos.lan:8443/immich// "));
        assertFalse(Config.parse("{\"homepage\":{\"url\":\"ftp://x\"}}").homepage.configured());
    }

    @Test
    public void homepage() {
        Config.Homepage h = Config.parse("{\"homepage\":{\"url\":\"https://home.lan/\",\"text_zoom\":125,\"keep_awake\":true}}").homepage;
        assertTrue(h.configured());
        assertEquals("https://home.lan", h.url);
        assertEquals(125, h.textZoom);
        assertTrue(h.keepAwake);
        Config.Homepage d = Config.parse("{\"homepage\":{\"text_zoom\":1000}}").homepage;
        assertEquals(300, d.textZoom);
        assertFalse(d.keepAwake);
    }

}
