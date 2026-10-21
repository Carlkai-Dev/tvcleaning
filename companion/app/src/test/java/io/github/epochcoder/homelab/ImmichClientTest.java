package io.github.epochcoder.homelab;

import static io.github.epochcoder.homelab.FakeServer.ok;
import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Immich against a scripted server: cursor paging, hidden albums and tags, album names. */
public class ImmichClientTest {
    private static Immich immich(FakeServer s, String extra) throws Exception {
        JSONObject cfg = new JSONObject("{\"api_key\":\"k\",\"max_photos\":3" + extra + "}").put("url", s.url());
        return new Immich(new Config.Immich(cfg), null);
    }

    @Test
    public void immichPagesWithTheCursorUpToMaxPhotos() throws Exception {
        try (FakeServer s = new FakeServer(r -> {
            boolean second = r.body.contains("\"cursor\":\"c2\"");
            return ok("{\"assets\":{\"items\":[{\"id\":\"" + (second ? "c" : "a") + "\"},{\"id\":\"" + (second ? "d" : "b")
                    + "\"}],\"nextCursor\":\"" + (second ? "c3" : "c2") + "\"}}");
        })) {
            List<Immich.Photo> photos = immich(s, "").recentPhotos(1792584000000L);
            assertEquals(3, photos.size());
            assertEquals(Arrays.asList("a", "b", "c"), Arrays.asList(photos.get(0).id, photos.get(1).id, photos.get(2).id));
            assertEquals(2, s.requests.size());
            JSONObject second = new JSONObject(s.requests.get(1).body);
            assertEquals("asks only for what is still missing", 1, second.getInt("size"));
            assertEquals("k", s.requests.get(0).headers.get("x-api-key"));
        }
    }

    @Test
    public void immichHidesAlbumsAndTagsOnTheServer() throws Exception {
        try (FakeServer s = new FakeServer(r -> {
            if (r.path.equals("/api/albums")) return ok("[{\"id\":\"al1\",\"albumName\":\"Screenshots\"},{\"id\":\"al2\",\"albumName\":\"Trip\"}]");
            if (r.path.equals("/api/tags")) return ok("[{\"id\":\"t1\",\"name\":\"Work\",\"value\":\"People/Work\"}]");
            return ok("{\"assets\":{\"items\":[],\"nextCursor\":null}}");
        })) {
            immich(s, ",\"exclude_albums\":[\"screenshots\"],\"exclude_tags\":[\"People/Work\"]").recentPhotos(1792584000000L);
            JSONObject filter = new JSONObject(s.to("/api/search/metadata").get(0).body).getJSONObject("filter");
            assertEquals(new JSONArray("[\"al1\"]").toString(), filter.getJSONObject("albumIds").getJSONArray("none").toString());
            assertEquals(new JSONArray("[\"t1\"]").toString(), filter.getJSONObject("tagIds").getJSONArray("none").toString());
        }
    }

    @Test
    public void immichAlbumOfAPhoto() throws Exception {
        try (FakeServer s = new FakeServer(r -> ok(r.path.equals("/api/albums?assetId=p1")
                ? "[{\"id\":\"x\",\"albumName\":\" \"},{\"id\":\"y\",\"albumName\":\"Summer\"}]" : "[]"))) {
            Immich i = immich(s, "");
            assertEquals("Summer", i.albumOf("p1"));
            assertEquals("", i.albumOf("p2"));
        }
    }
}
