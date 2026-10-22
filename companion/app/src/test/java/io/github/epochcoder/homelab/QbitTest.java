package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class QbitTest {
    @Test
    public void parsesTorrents() throws Exception {
        List<Qbit.Torrent> t =
                Qbit.parseTorrents(
                        "[{\"hash\":\"ABC\",\"name\":\"Dune.2021.2160p\",\"progress\":0.5,"
                            + "\"size\":1000,\"dlspeed\":2048,\"upspeed\":0,\"eta\":600,\"state\":\"downloading\",\"category\":\"radarr\"},{\"name\":\"no"
                            + " hash\"},{\"hash\":\"def\",\"progress\":1,\"state\":\"stoppedUP\"}]");
        assertEquals(2, t.size());
        assertEquals("abc", t.get(0).hash);
        assertEquals("Downloading", t.get(0).stateLabel());
        assertFalse(t.get(0).stopped());
        assertTrue(t.get(1).stopped());
        assertTrue(t.get(1).complete());
        assertEquals("Done", t.get(1).stateLabel());
    }

    @Test
    public void stoppedCoversQbittorrent4And5() throws Exception {
        for (String s : new String[] {"pausedDL", "pausedUP", "stoppedDL", "stoppedUP"}) {
            assertTrue(
                    s,
                    Qbit.parseTorrents("[{\"hash\":\"a\",\"state\":\"" + s + "\"}]")
                            .get(0)
                            .stopped());
        }
    }

    @Test
    public void downloadsAreNamedByTheAppsAndOrdered() throws Exception {
        List<Qbit.Torrent> torrents =
                Qbit.parseTorrents(
                        "[{\"hash\":\"aaa\",\"name\":\"Severance.S02E03.1080p\",\"progress\":0.25,\"size\":2147483648,"
                            + "\"dlspeed\":5242880,\"eta\":720,\"state\":\"downloading\"},"
                            + "{\"hash\":\"bbb\",\"name\":\"Old.Movie\",\"progress\":1,\"size\":1024,\"state\":\"stalledUP\",\"category\":\"radarr\"},"
                            + "{\"hash\":\"ccc\",\"name\":\"Linux.iso\",\"progress\":0.1,\"size\":1024,\"eta\":8640000,\"state\":\"stalledDL\"}]");
        List<Arr.QueueEntry> queue =
                Arrays.asList(
                        new Arr.QueueEntry(
                                Arr.Kind.SERIES,
                                "Severance S02E03",
                                "Severance.S02E03.1080p",
                                "AAA",
                                0.25,
                                2147483648L,
                                720,
                                "downloading",
                                ""),
                        new Arr.QueueEntry(
                                Arr.Kind.MOVIE,
                                "Dune",
                                "Dune.Usenet",
                                "SABnzbd_123",
                                0.5,
                                4096,
                                -1,
                                "downloading",
                                ""));
        List<DownloadsPage.Download> rows = DownloadsPage.downloads(torrents, queue, 8);
        assertEquals(4, rows.size());
        assertEquals("Severance S02E03", rows.get(0).title);
        assertEquals(
                "Downloading  ·  5.0 MB/s  ·  12 min left  ·  2.0 GB  ·  Sonarr",
                rows.get(0).detail);
        assertEquals("Linux.iso", rows.get(1).title);
        assertEquals("Stalled  ·  1 KB", rows.get(1).detail);
        assertEquals("Dune", rows.get(2).title);
        assertFalse(rows.get(2).finished);
        assertEquals("Old.Movie", rows.get(3).title);
        assertTrue(rows.get(3).finished);

        assertEquals(3, DownloadsPage.downloads(torrents, queue, 0).size());
        assertTrue(
                DownloadsPage.downloads(Collections.emptyList(), Collections.emptyList(), 8)
                        .isEmpty());
    }
}
