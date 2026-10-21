package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImagesTest {
    @Test
    public void tmdbImagesAreFetchedAtAboutTheirShownWidth() {
        String original = "https://image.tmdb.org/t/p/original/abc.jpg";
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", Images.sized(original, 280));
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", Images.sized(original, 480));
        assertEquals("https://image.tmdb.org/t/p/w1280/abc.jpg", Images.sized(original, 1280));
        assertEquals("https://artworks.thetvdb.com/banners/p.jpg", Images.sized("https://artworks.thetvdb.com/banners/p.jpg", 280));
        assertEquals("", Images.sized("", 280));
    }

    @Test
    public void cachedPerWidthSoASmallPosterIsNotStretched() {
        assertEquals(false, Images.key("u", 280).equals(Images.key("u", 480)));
        assertEquals(Images.key("u", 280), Images.key("u", 280));
    }
}
