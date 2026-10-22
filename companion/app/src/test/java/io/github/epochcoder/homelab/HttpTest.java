package io.github.epochcoder.homelab;

import static io.github.epochcoder.homelab.FakeServer.failure;
import static io.github.epochcoder.homelab.FakeServer.ok;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Http against a scripted server: the key goes where it should and nowhere else, and failures read
 * well.
 */
public class HttpTest {
    @Test
    public void sendsTheKeyAndReturnsJson() throws Exception {
        try (FakeServer s = new FakeServer(r -> ok("[1]"))) {
            assertEquals("[1]", new Http(null, "X-Api-Key", "secret").get(s.url() + "/api/v3/x"));
            assertEquals("secret", s.requests.get(0).headers.get("x-api-key"));
        }
    }

    @Test
    public void aRedirectIsRefusedAndTheKeyStaysHome() throws Exception {
        try (FakeServer s =
                new FakeServer(
                        r ->
                                new FakeServer.Reply(302, "")
                                        .header("Location", "https://auth.lan/login"))) {
            String m = failure(() -> new Http(null, "X-Api-Key", "secret").get(s.url() + "/api"));
            assertTrue(m, m.startsWith("redirected to auth.lan"));
            assertEquals("the redirect was not followed", 1, s.requests.size());
        }
    }

    @Test
    public void aWebPageIsNotJson() throws Exception {
        try (FakeServer s = new FakeServer(r -> ok("<!doctype html><html>login</html>"))) {
            String m = failure(() -> new Http(null, "k", "v").get(s.url() + "/api"));
            assertTrue(m, m.contains("web page"));
        }
    }

    @Test
    public void errorsAreShortEnoughForTheScreen() throws Exception {
        String longBody = new String(new char[2000]).replace('\0', 'x');
        try (FakeServer s = new FakeServer(r -> new FakeServer.Reply(500, longBody))) {
            String m = failure(() -> new Http(null, "k", "v").get(s.url() + "/api"));
            assertTrue(m, m.startsWith("HTTP 500, "));
            assertTrue(m.length() < 200);
        }
    }

    @Test
    public void anonymousRequestsFollowRedirects() throws Exception {
        try (FakeServer s =
                new FakeServer(
                        r ->
                                r.path.equals("/a")
                                        ? new FakeServer.Reply(302, "").header("Location", "/b")
                                        : ok("img"))) {
            assertArrayEquals(
                    "img".getBytes(StandardCharsets.UTF_8), Http.anonymous().bytes(s.url() + "/a"));
            assertEquals(2, s.requests.size());
            assertNull(s.requests.get(0).headers.get("x-api-key"));
        }
    }
}
