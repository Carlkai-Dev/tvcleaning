package io.github.epochcoder.homelab;

import static io.github.epochcoder.homelab.FakeServer.failure;
import static io.github.epochcoder.homelab.FakeServer.ok;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Qbit against a scripted qBittorrent 5.1 (and 4 where they differ): login, retry, stop and start.
 */
public class QbitClientTest {
    private static Qbit qbit(FakeServer s, String user, String password) throws Exception {
        return new Qbit(
                new Config.QBittorrent(
                        new JSONObject()
                                .put("url", s.url())
                                .put("username", user)
                                .put("password", password)),
                null);
    }

    /** qBittorrent as 5.1 behaves: 403 without the session cookie, a login that sets it. */
    private static FakeServer.Reply qbittorrent(
            FakeServer.Request r, String cookie, int unauthorized) {
        if (r.path.equals("/api/v2/auth/login")) {
            return r.body.equals("username=admin&password=p%26ss")
                    ? ok("Ok.").header("Set-Cookie", cookie + "; HttpOnly; SameSite=Strict; path=/")
                    : ok("Fails.");
        }
        if (!cookie.equals(r.headers.get("cookie"))) {
            return new FakeServer.Reply(unauthorized, "Forbidden");
        }
        if (r.path.startsWith("/api/v2/torrents/info")) {
            return ok(
                    "[{\"hash\":\"ABC\",\"name\":\"x\",\"progress\":0.5,\"state\":\"downloading\"}]");
        }
        if (r.path.equals("/api/v2/app/version")) {
            return ok("v5.1.4");
        }
        return new FakeServer.Reply(404, "Not Found");
    }

    @Test
    public void qbittorrentLogsInOnceAndRetries() throws Exception {
        try (FakeServer s = new FakeServer(r -> qbittorrent(r, "SID=abc", 403))) {
            Qbit q = qbit(s, "admin", "p&ss");
            List<Qbit.Torrent> t = q.torrents();
            assertEquals("abc", t.get(0).hash);
            assertEquals(1, s.to("/api/v2/auth/login").size());
            assertEquals(
                    "qBittorrent's CSRF check wants the Referer",
                    s.url(),
                    s.requests.get(0).headers.get("referer"));
            q.torrents();
            assertEquals("the session is reused", 1, s.to("/api/v2/auth/login").size());
            assertEquals("qBittorrent v5.1.4", q.version());
        }
    }

    @Test
    public void qbittorrent401AndRenamedCookiesWorkToo() throws Exception {
        try (FakeServer s = new FakeServer(r -> qbittorrent(r, "QBT_SID_8080=xyz", 401))) {
            assertEquals(1, qbit(s, "admin", "p&ss").torrents().size());
        }
    }

    @Test
    public void qbittorrentWrongPasswordOrNoUserSaysWhy() throws Exception {
        try (FakeServer s = new FakeServer(r -> qbittorrent(r, "SID=abc", 403))) {
            Qbit wrongPassword = qbit(s, "admin", "nope");
            String wrong = failure(wrongPassword::torrents);
            assertTrue(wrong, wrong.contains("check the user and password"));
            Qbit noUser = qbit(s, "", "");
            String none = failure(noUser::torrents);
            assertTrue(none, none.contains("whitelist"));
            assertEquals("no login is tried without a user", 1, s.to("/api/v2/auth/login").size());
        }
    }

    @Test
    public void qbittorrent5StopsAndStarts() throws Exception {
        try (FakeServer s =
                new FakeServer(
                        r ->
                                r.path.startsWith("/api/v2/torrents/")
                                        ? ok("")
                                        : new FakeServer.Reply(404, ""))) {
            Qbit q = qbit(s, "", "");
            q.toggle(Qbit.parseTorrents("[{\"hash\":\"a\",\"state\":\"downloading\"}]").get(0));
            q.toggle(Qbit.parseTorrents("[{\"hash\":\"b\",\"state\":\"stoppedDL\"}]").get(0));
            assertEquals("hashes=a", s.to("/api/v2/torrents/stop").get(0).body);
            assertEquals("hashes=b", s.to("/api/v2/torrents/start").get(0).body);
            assertTrue(s.to("/api/v2/torrents/pause").isEmpty());
        }
    }

    @Test
    public void qbittorrent4FallsBackToPauseAndResume() throws Exception {
        try (FakeServer s =
                new FakeServer(
                        r ->
                                r.path.endsWith("/pause") || r.path.endsWith("/resume")
                                        ? ok("")
                                        : new FakeServer.Reply(404, ""))) {
            Qbit q = qbit(s, "", "");
            q.toggle(Qbit.parseTorrents("[{\"hash\":\"a\",\"state\":\"downloading\"}]").get(0));
            q.toggle(Qbit.parseTorrents("[{\"hash\":\"b\",\"state\":\"pausedDL\"}]").get(0));
            assertEquals(1, s.to("/api/v2/torrents/pause").size());
            assertEquals(1, s.to("/api/v2/torrents/resume").size());
        }
    }

    @Test
    public void sessionCookies() {
        assertEquals("SID=a", Qbit.sessionCookie(Arrays.asList("SID=a", "other=b")));
        assertEquals("QBT_SID_8080=x", Qbit.sessionCookie(Arrays.asList("QBT_SID_8080=x")));
        assertEquals("", Qbit.sessionCookie(Arrays.asList("SID=", "lang=en")));
    }
}
