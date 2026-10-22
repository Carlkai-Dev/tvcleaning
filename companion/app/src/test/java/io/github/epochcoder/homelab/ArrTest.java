package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ArrTest {
    private static final String SEVERANCE =
            "{\"title\":\"Severance\",\"year\":2022,\"network\":\"Apple TV+\","
                + "\"seasons\":[{\"seasonNumber\":0},{\"seasonNumber\":1},{\"seasonNumber\":2}],\"tvdbId\":371980,\"genres\":[\"Drama\",\"Mystery\",\"Science"
                + " Fiction\",\"Thriller\"],\"certification\":\"TV-MA\","
                + "\"ratings\":{\"votes\":1200,\"value\":8.7},\"status\":\"continuing\","
                + "\"images\":[{\"coverType\":\"fanart\",\"remoteUrl\":\"https://artworks.thetvdb.com/f.jpg\"}],"
                + "\"remotePoster\":\"https://artworks.thetvdb.com/p.jpg\"}";
    private static final String DUNE =
            "{\"title\":\"Dune\",\"year\":2021,\"runtime\":155,\"id\":7,\"hasFile\":true,\"overview\":\"Spice\\n"
                + " must  flow\",\"certification\":null,\"genres\":[\"Science Fiction\"],"
                + "\"ratings\":{\"imdb\":{\"value\":8.0},\"tmdb\":{\"value\":7.8},\"rottenTomatoes\":{\"value\":83}},"
                + "\"images\":[{\"coverType\":\"poster\",\"remoteUrl\":\"https://image.tmdb.org/t/p/original/d.jpg\"}]}";

    @Test
    public void parsesShowLookups() throws Exception {
        List<Arr.Item> items =
                Arr.parseLookup(Arr.Kind.SERIES, "[" + SEVERANCE + ",{\"year\":1999}]");
        assertEquals(1, items.size());
        Arr.Item s = items.get(0);
        assertEquals("Severance (2022)", s.label());
        assertEquals(2, s.seasons);
        assertEquals(
                "2022  ·  Apple TV+  ·  2 seasons  ·  TV-MA  ·  Drama, Mystery, Science Fiction",
                s.meta());
        assertEquals("★ 8.7", s.rating);
        assertEquals("https://artworks.thetvdb.com/p.jpg", s.poster);
        assertEquals("https://artworks.thetvdb.com/f.jpg", s.fanart);
        assertFalse(s.inLibrary());
        assertEquals(371980, s.raw.getInt("tvdbId"));
    }

    @Test
    public void parsesFilmLookups() throws Exception {
        Arr.Item d = Arr.parseLookup(Arr.Kind.MOVIE, "[" + DUNE + "]").get(0);
        assertEquals("Spice must flow", d.overview);
        assertEquals("2021  ·  2 h 35 min  ·  Science Fiction", d.meta());
        assertEquals("IMDb 8.0  ·  83% RT", d.rating);
        assertEquals("https://image.tmdb.org/t/p/original/d.jpg", d.poster);
        assertEquals("", d.fanart);
        assertTrue(d.inLibrary());
        assertTrue(d.hasFile);
    }

    @Test
    public void mergeInterleavesWithExactMatchesFirst() throws Exception {
        List<Arr.Item> movies =
                Arr.parseLookup(
                        Arr.Kind.MOVIE,
                        "[{\"title\":\"Dune: Part"
                            + " Two\"},{\"title\":\"Dune\"},{\"title\":\"Jodorowsky's Dune\"}]");
        List<Arr.Item> shows =
                Arr.parseLookup(
                        Arr.Kind.SERIES,
                        "[{\"title\":\"Dune: Prophecy\"},{\"title\":\"Frank Herbert's Dune\"}]");
        List<String> order = new ArrayList<>();
        for (Arr.Item i : Arr.merge(movies, shows, "dune")) {
            order.add(i.title);
        }
        assertEquals(
                Arrays.asList(
                        "Dune",
                        "Dune: Part Two",
                        "Dune: Prophecy",
                        "Frank Herbert's Dune",
                        "Jodorowsky's Dune"),
                order);
        assertTrue(Arr.merge(Collections.emptyList(), Collections.emptyList(), "x").isEmpty());
    }

    @Test
    public void picksConfiguredOrFirst() throws Exception {
        JSONArray profiles =
                new JSONArray("[{\"id\":1,\"name\":\"Any\"},{\"id\":4,\"name\":\"HD-1080p\"}]");
        assertEquals(4, Arr.pick(profiles, "name", "hd-1080P").getInt("id"));
        assertEquals(1, Arr.pick(profiles, "name", "").getInt("id"));
        assertEquals(1, Arr.pick(profiles, "name", "Missing").getInt("id"));
        assertNull(Arr.pick(new JSONArray(), "name", ""));
    }

    @Test
    public void addBodyKeepsLookupAndAddsOptions() throws Exception {
        JSONObject lookup = new JSONObject("{\"title\":\"Dune\",\"tmdbId\":438631}");
        Arr.Defaults d = new Arr.Defaults(4, "HD-1080p", "/movies");

        JSONObject movie = Arr.addBody(Arr.Kind.MOVIE, lookup, d, null);
        assertEquals(438631, movie.getInt("tmdbId"));
        assertEquals(4, movie.getInt("qualityProfileId"));
        assertEquals("/movies", movie.getString("rootFolderPath"));
        assertTrue(movie.getBoolean("monitored"));
        assertTrue(movie.getJSONObject("addOptions").getBoolean("searchForMovie"));
        assertFalse("the lookup object itself is left alone", lookup.has("addOptions"));

        JSONObject all = Arr.addBody(Arr.Kind.SERIES, lookup, d, "all");
        assertTrue(all.getBoolean("seasonFolder"));
        assertEquals("all", all.getJSONObject("addOptions").getString("monitor"));
        assertTrue(all.getJSONObject("addOptions").getBoolean("searchForMissingEpisodes"));

        JSONObject future = Arr.addBody(Arr.Kind.SERIES, lookup, d, "future");
        assertFalse(
                "nothing has aired to search for",
                future.getJSONObject("addOptions").getBoolean("searchForMissingEpisodes"));
        JSONObject none = Arr.addBody(Arr.Kind.SERIES, lookup, d, "none");
        assertFalse(none.getBoolean("monitored"));
    }

    @Test
    public void searchCommands() throws Exception {
        JSONObject m = Arr.searchCommand(Arr.Kind.MOVIE, 7);
        assertEquals("MoviesSearch", m.getString("name"));
        assertEquals(7, m.getJSONArray("movieIds").getInt(0));
        JSONObject s = Arr.searchCommand(Arr.Kind.SERIES, 9);
        assertEquals("SeriesSearch", s.getString("name"));
        assertEquals(9, s.getInt("seriesId"));
    }

    @Test
    public void parsesQueue() throws Exception {
        List<Arr.QueueEntry> q =
                Arr.parseQueue(
                        Arr.Kind.SERIES,
                        "{\"page\":1,\"records\":[{\"title\":\"release.name\","
                            + "\"size\":1000,\"sizeleft\":250,\"timeleft\":\"00:12:00\",\"trackedDownloadState\":\"downloading\","
                            + "\"downloadId\":\"ABCDEF\",\"series\":{\"title\":\"Severance\"},\"episode\":{\"seasonNumber\":2,\"episodeNumber\":3}},"
                            + "{\"title\":\"other.release\",\"status\":\"queued\",\"errorMessage\":null}]}");
        assertEquals(2, q.size());
        assertEquals("Severance S02E03", q.get(0).title);
        assertEquals("release.name", q.get(0).release);
        assertEquals(0.75, q.get(0).progress, 1e-9);
        assertEquals(720, q.get(0).timeLeft);
        assertEquals("ABCDEF", q.get(0).downloadId);
        assertEquals("other.release", q.get(1).title);
        assertEquals("queued", q.get(1).state);
        assertEquals("", q.get(1).error);
    }

    @Test
    public void parsesCalendars() throws Exception {
        List<Arr.Upcoming> eps =
                Arr.parseCalendar(
                        Arr.Kind.SERIES,
                        "[{\"seasonNumber\":2,\"episodeNumber\":4,\"title\":\"Woe's"
                            + " Hollow\",\"airDateUtc\":\"2026-10-24T01:00:00Z\",\"hasFile\":false,"
                            + "\"series\":{\"title\":\"Severance\"}}]",
                        "2026-10-21",
                        "2026-11-04");
        assertEquals("Severance", eps.get(0).title);
        assertEquals("S02E04  ·  Woe's Hollow", eps.get(0).subtitle);
        assertEquals("2026-10-24T01:00:00Z", eps.get(0).when);

        String start = "2026-10-21T00:00:00.000Z";
        String end = "2026-11-04T00:00:00.000Z";
        List<Arr.Upcoming> films =
                Arr.parseCalendar(
                        Arr.Kind.MOVIE,
                        "[{\"title\":\"A\",\"year\":2026,\"inCinemas\":\"2026-08-01T00:00:00Z\",\"digitalRelease\":\"2026-10-28T00:00:00Z\"},"
                            + "{\"title\":\"B\",\"inCinemas\":\"2026-10-30T00:00:00Z\",\"digitalRelease\":null,\"hasFile\":false},"
                            + "{\"title\":\"C\",\"physicalRelease\":\"2027-01-01T00:00:00Z\"}]",
                        start,
                        end);
        assertEquals(2, films.size());
        assertEquals("A (2026)", films.get(0).title);
        assertEquals("Digital release", films.get(0).subtitle);
        assertEquals("In cinemas", films.get(1).subtitle);
    }

    @Test
    public void healthAndDisk() throws Exception {
        List<Arr.Health> h =
                Arr.parseHealth(
                        "[{\"type\":\"ok\",\"message\":\"fine\"},{\"type\":\"warning\",\"message\":\"Indexer"
                            + " down\"},{\"type\":\"notice\",\"message\":\"update\"}]");
        assertEquals(1, h.size());
        assertEquals("Indexer down", h.get(0).message);
        long[] disk =
                Arr.largestDisk(
                        new JSONArray(
                                "[{\"path\":\"/\",\"freeSpace\":5,\"totalSpace\":10},"
                                    + "{\"path\":\"/share\",\"freeSpace\":2000,\"totalSpace\":8000}]"));
        assertEquals(2000, disk[0]);
        assertEquals(8000, disk[1]);
        assertNull(Arr.largestDisk(new JSONArray()));
    }
}
