package io.github.epochcoder.homelab;

import static io.github.epochcoder.homelab.FakeServer.ok;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/** Arr against a scripted Sonarr or Radarr: what lookup, add and search again send. */
public class ArrClientTest {
    private static Arr arr(FakeServer s, Arr.Kind kind) throws Exception {
        return new Arr(
                kind,
                new Config.Arr(
                        new JSONObject()
                                .put("url", s.url())
                                .put("api_key", "k")
                                .put("quality_profile", "HD-1080p")),
                null);
    }

    @Test
    public void lookupEncodesTheTerm() throws Exception {
        try (FakeServer s = new FakeServer(r -> ok("[{\"title\":\"The Bear\",\"year\":2022}]"))) {
            assertEquals("The Bear", arr(s, Arr.Kind.SERIES).lookup("the bear & co").get(0).title);
            assertEquals("/api/v3/series/lookup?term=the+bear+%26+co", s.requests.get(0).path);
            assertEquals("k", s.requests.get(0).headers.get("x-api-key"));
        }
    }

    @Test
    public void addSendsTheLookupWithProfileFolderAndSearch() throws Exception {
        try (FakeServer s =
                new FakeServer(
                        r -> {
                            if (r.path.equals("/api/v3/qualityprofile")) {
                                return ok(
                                        "[{\"id\":1,\"name\":\"Any\"},{\"id\":4,\"name\":\"HD-1080p\"}]");
                            }
                            if (r.path.equals("/api/v3/rootfolder")) {
                                return ok("[{\"id\":1,\"path\":\"/movies\"}]");
                            }
                            if (r.path.equals("/api/v3/movie")) {
                                return ok("{\"id\":12,\"title\":\"Dune\",\"year\":2021}");
                            }
                            return new FakeServer.Reply(404, "");
                        })) {
            Arr radarr = arr(s, Arr.Kind.MOVIE);
            Arr.Item lookup =
                    new Arr.Item(
                            Arr.Kind.MOVIE,
                            new JSONObject("{\"title\":\"Dune\",\"tmdbId\":438631}"));
            Arr.Defaults d = radarr.defaults();
            assertEquals("HD-1080p", d.profileName);
            Arr.Item added = radarr.add(lookup, d, null);
            assertTrue(added.inLibrary());
            assertEquals(12, added.id);
            JSONObject body = new JSONObject(s.to("/api/v3/movie").get(0).body);
            assertEquals(438631, body.getInt("tmdbId"));
            assertEquals(4, body.getInt("qualityProfileId"));
            assertEquals("/movies", body.getString("rootFolderPath"));
            assertTrue(body.getJSONObject("addOptions").getBoolean("searchForMovie"));
            radarr.defaults();
            assertEquals("the defaults are asked once", 1, s.to("/api/v3/qualityprofile").size());
        }
    }

    @Test
    public void searchAgainSendsTheCommand() throws Exception {
        try (FakeServer s = new FakeServer(r -> ok("{}"))) {
            Arr.Item show =
                    new Arr.Item(
                            Arr.Kind.SERIES, new JSONObject("{\"title\":\"Severance\",\"id\":9}"));
            arr(s, Arr.Kind.SERIES).searchNow(show);
            FakeServer.Request r = s.requests.get(0);
            assertEquals("POST", r.method);
            assertEquals("/api/v3/command", r.path);
            assertEquals("SeriesSearch", new JSONObject(r.body).getString("name"));
            assertEquals(9, new JSONObject(r.body).getInt("seriesId"));
            assertFalse(r.headers.get("content-type").isEmpty());
        }
    }
}
