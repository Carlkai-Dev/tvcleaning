package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class PicsumPhotosTest {

    @Test
    public void parseImageUrlsReadsIdEntries() {
        String json = "["
                + "{\"id\":\"0\",\"download_url\":\"https://picsum.photos/id/0/5000/3333\"},"
                + "{\"id\":\"1015\",\"download_url\":\"https://picsum.photos/id/1015/4000/3000\"}"
                + "]";
        List<String> urls = PicsumPhotos.parseImageUrls(json);
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).contains("/id/0/"));
        assertTrue(urls.get(1).contains("/id/1015/"));
    }

    @Test
    public void sizedUrlRebuildsAtTvResolution() {
        String base = "https://picsum.photos/id/1015/200/200";
        assertEquals("https://picsum.photos/id/1015/1920/1080",
                PicsumPhotos.sizedUrl(base, 1920, 1080));
        // Floor on tiny reported display
        assertEquals("https://picsum.photos/id/7/1920/1080",
                PicsumPhotos.sizedUrl("https://picsum.photos/id/7/50/50", 800, 600));
    }

    @Test
    public void parseCapsAtEight() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 12; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"").append(i).append("\",\"download_url\":\"https://picsum.photos/id/")
                    .append(i).append("/100/100\"}");
        }
        sb.append(']');
        assertEquals(8, PicsumPhotos.parseImageUrls(sb.toString()).size());
        assertTrue(PicsumPhotos.parseImageUrls("[]").isEmpty());
        assertTrue(PicsumPhotos.parseImageUrls(null).isEmpty());
    }

    @Test
    public void choiceConstantMatchesSettingsWiring() {
        assertEquals("picsum", PicsumPhotos.CHOICE);
        assertTrue(Config.isCachedWallpaperChoice(PicsumPhotos.CHOICE));
    }
}
