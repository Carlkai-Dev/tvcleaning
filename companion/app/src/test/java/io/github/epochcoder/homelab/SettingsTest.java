package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.json.JSONArray;
import org.junit.Test;

public class SettingsTest {
    @Test
    public void pickerNamesAreSortedUniqueAndNonEmpty() throws Exception {
        JSONArray albums =
                new JSONArray(
                        "[{\"albumName\":\"work\"},{\"albumName\":\"Holiday\"},{\"albumName\":\""
                                + " \"},{\"id\":\"x\"},{\"albumName\":\"Holiday\"},\"junk\"]");
        assertEquals(Arrays.asList("Holiday", "work"), SettingsPage.names(albums, "albumName"));
    }

    @Test
    public void maskShowsOnlyTheLastFourCharacters() {
        assertEquals("••••••cdef", SettingsPage.mask("0123456789abcdef"));
        assertEquals("••••", SettingsPage.mask("abcd"));
    }
}
