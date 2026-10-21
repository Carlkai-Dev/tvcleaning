package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

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

    @Test
    public void immich() {
        Config.Immich i = Config.parse("{\"immich\":{\"url\":\"https://photos.lan/\",\"api_key\":\"k\",\"recent_days\":30,"
                + "\"exclude_albums\":[\"Screenshots\",\" \",\"Work\"],\"exclude_tags\":[\"People/Work\"],"
                + "\"shuffle\":false,\"crop\":true,\"show_clock\":false}}").immich;
        assertTrue(i.configured());
        assertEquals("https://photos.lan", i.url);
        assertEquals(30, i.recentDays);
        assertEquals(Arrays.asList("Screenshots", "Work"), i.excludeAlbums);
        assertEquals(Arrays.asList("People/Work"), i.excludeTags);
        assertFalse(i.shuffle);
        assertTrue(i.crop);
        assertFalse(i.showClock);
        assertFalse("a url without a key is not enough", Config.parse("{\"immich\":{\"url\":\"http://x\"}}").immich.configured());
    }

    @Test
    public void immichDefaultsAndClamps() {
        Config.Immich i = Config.parse("{\"immich\":{\"url\":\"http://10.0.0.2:2283\",\"api_key\":\"k\","
                + "\"interval_seconds\":1,\"max_photos\":999999}}").immich;
        assertEquals(90, i.recentDays);
        assertEquals(5, i.intervalSeconds);
        assertEquals(5000, i.maxPhotos);
        assertTrue(i.shuffle);
        assertTrue(i.showClock);
        assertTrue(i.details);
        assertTrue(i.motion);
        assertTrue(i.pairPortraits);
        Config.Immich off = Config.parse("{\"immich\":{\"details\":false,\"motion\":false,\"pair_portraits\":false}}").immich;
        assertFalse(off.details);
        assertFalse(off.motion);
        assertFalse(off.pairPortraits);
    }

    @Test
    public void sonarrRadarrAndQbittorrent() {
        Config c = Config.parse("{"
                + "\"sonarr\":{\"url\":\"https://sonarr.lan\",\"api_key\":\"s\",\"root_folder\":\"/tv\",\"monitor\":\"future\"},"
                + "\"radarr\":{\"url\":\"https://radarr.lan\",\"api_key\":\"r\",\"quality_profile\":\"HD-1080p\"},"
                + "\"qbittorrent\":{\"url\":\"https://qbit.lan/\",\"username\":\" admin \",\"password\":\" p \"}}");
        assertTrue(c.sonarr.configured());
        assertEquals("/tv", c.sonarr.rootFolder);
        assertEquals("future", c.sonarr.monitor);
        assertEquals("HD-1080p", c.radarr.qualityProfile);
        assertEquals("all", c.radarr.monitor);
        assertTrue(c.qbittorrent.configured());
        assertEquals("https://qbit.lan", c.qbittorrent.url);
        assertEquals("admin", c.qbittorrent.username);
        assertEquals("passwords are kept exactly", " p ", c.qbittorrent.password);
        assertEquals("unknown monitor values fall back", "all",
                Config.parse("{\"sonarr\":{\"monitor\":\"everything\"}}").sonarr.monitor);
        assertFalse(Config.parse("{\"sonarr\":{\"url\":\"https://s\"}}").sonarr.configured());
        assertFalse(Config.parse("{}").qbittorrent.configured());
    }
}
