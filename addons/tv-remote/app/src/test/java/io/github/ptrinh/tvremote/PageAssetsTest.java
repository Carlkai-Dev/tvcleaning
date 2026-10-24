package io.github.ptrinh.tvremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** What the pageAssets Gradle task made from relay/src for LAN mode (build.gradle passes its folder). */
public class PageAssetsTest {
    private static File dir() {
        String d = System.getProperty("pageAssets");
        assertNotNull("run through Gradle: it passes -DpageAssets", d);
        return new File(d);
    }

    @Test
    public void thePageIsTheRelaysPage() throws Exception {
        String page = new String(Files.readAllBytes(new File(dir(), "page.html").toPath()), StandardCharsets.UTF_8);
        assertTrue(page.startsWith("<!doctype html>"));
        assertTrue(page.trim().endsWith("</html>"));
        assertFalse(page.contains("export const"));
        // LocalServer links the paired manifest by replacing this.
        assertTrue(page.contains("href=\"/manifest.webmanifest\""));
    }

    @Test
    public void iconsFontAndServiceWorker() throws Exception {
        for (String png : new String[]{"icon-192.png", "icon-512.png", "apple-touch-icon.png"}) {
            byte[] b = Files.readAllBytes(new File(dir(), png).toPath());
            assertTrue(png, b.length > 100);
            assertEquals(png, (byte) 0x89, b[0]);
            assertEquals(png, 'P', b[1]);
        }
        byte[] font = Files.readAllBytes(new File(dir(), "outfit.ttf").toPath());
        assertTrue(font.length > 10_000);
        assertEquals(0, font[0]);
        assertEquals(1, font[1]);
        String sw = new String(Files.readAllBytes(new File(dir(), "sw.js").toPath()), StandardCharsets.UTF_8);
        assertTrue(sw.contains("addEventListener(\"push\""));
        for (String path : new String[]{"/icon-192.png", "/icon-512.png", "/apple-touch-icon.png", "/favicon.ico", "/outfit.ttf", "/sw.js"}) {
            assertTrue(path, new File(dir(), LanHttp.asset(path)[0]).isFile());
        }
    }
}
