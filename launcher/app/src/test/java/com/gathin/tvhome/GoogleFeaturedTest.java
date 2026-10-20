package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

public class GoogleFeaturedTest {

    @Test
    public void parseImageUrlsReadsNestedImageUrlField() {
        // Image { image_url = "https://lh3.googleusercontent.com/proxy/abc" }
        byte[] image = encodeString(2, "https://lh3.googleusercontent.com/proxy/abc");
        // GetImagesInCollectionResponse { images = [image] }
        byte[] body = encodeBytes(1, image);
        List<String> urls = GoogleFeatured.parseImageUrls(body);
        assertEquals(1, urls.size());
        assertEquals("https://lh3.googleusercontent.com/proxy/abc", urls.get(0));
    }

    @Test
    public void parseImageUrlsSkipsNonGoogleUrls() {
        byte[] image = encodeString(2, "https://example.com/x.jpg");
        byte[] body = encodeBytes(1, image);
        assertTrue(GoogleFeatured.parseImageUrls(body).isEmpty());
    }

    @Test
    public void sizedUrlRequestsTwiceTheLongerEdgeLikeChromium() {
        String base = "https://lh3.googleusercontent.com/proxy/abc";
        assertEquals(base + "=w3840", GoogleFeatured.sizedUrl(base, 1920, 1080));
        assertEquals(base + "=w3840", GoogleFeatured.sizedUrl(base + "=s512", 1920, 1080));
        assertEquals(base + "=w1920", GoogleFeatured.sizedUrl(base, 800, 600)); // floor
    }

    private static byte[] encodeString(int field, String value) {
        byte[] utf = value.getBytes(StandardCharsets.UTF_8);
        return concat(encodeVarint((field << 3) | 2), encodeVarint(utf.length), utf);
    }

    private static byte[] encodeBytes(int field, byte[] value) {
        return concat(encodeVarint((field << 3) | 2), encodeVarint(value.length), value);
    }

    private static byte[] encodeVarint(int value) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(5);
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
