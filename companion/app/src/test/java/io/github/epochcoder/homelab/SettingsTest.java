package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.junit.Test;

import java.util.Arrays;

public class SettingsTest {
    @Test
    public void pickerNamesAreSortedUniqueAndNonEmpty() throws Exception {
        JSONArray albums = new JSONArray("[{\"albumName\":\"work\"},{\"albumName\":\"Holiday\"},"
                + "{\"albumName\":\" \"},{\"id\":\"x\"},{\"albumName\":\"Holiday\"},\"junk\"]");
        assertEquals(Arrays.asList("Holiday", "work"), SettingsActivity.names(albums, "albumName"));
    }
}
