package com.dvopenlabs.weatheralerts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.*;

public class AlertsTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private JSONObject alert(String id, String severity) throws Exception {
        JSONObject p = new JSONObject().put("id", id).put("status", "Actual").put("messageType", "Alert")
                .put("headline", "Example warning").put("event", "Example event").put("areaDesc", "Example county")
                .put("severity", severity).put("description", "Example description")
                .put("instruction", "Example instructions").put("effective", "2026-01-01T06:00:00-05:00")
                .put("expires", "2026-01-01T08:00:00-05:00");
        return new JSONObject().put("type", "Feature").put("properties", p);
    }
    private String feed(JSONObject... entries) throws Exception {
        JSONArray features = new JSONArray();
        for (JSONObject entry : entries) features.put(entry);
        return new JSONObject().put("type", "FeatureCollection").put("features", features).toString();
    }
    private JSONObject props(JSONObject entry) throws Exception { return entry.getJSONObject("properties"); }

    @Test public void parsesOffsetTimesAndInstructions() throws Exception {
        Alerts.Alert parsed = Alerts.parse(feed(alert("a", "Severe")), NOW).get(0);
        assertEquals(Instant.parse("2026-01-01T13:00:00Z"), parsed.expires());
        assertEquals("Example instructions", parsed.instruction());
    }
    @Test public void sortsBySeverityThenExpiry() throws Exception {
        JSONObject early = alert("early", "Severe");
        props(early).put("expires", "2026-01-01T12:30:00Z");
        List<Alerts.Alert> list = Alerts.parse(feed(alert("minor", "Minor"), alert("late", "Severe"),
                alert("extreme", "Extreme"), early), NOW);
        assertEquals(List.of("extreme", "early", "late", "minor"), list.stream().map(Alerts.Alert::id).toList());
    }
    @Test public void emptyCollectionIsAValidEmptyCheck() throws Exception {
        assertTrue(Alerts.parse(feed(), NOW).isEmpty());
    }
    @Test public void excludesExpiredAtBoundary() throws Exception {
        JSONObject old = alert("old", "Severe"); props(old).put("expires", NOW.toString());
        assertTrue(Alerts.parse(feed(old), NOW).isEmpty());
    }
    @Test public void excludesFutureEffective() throws Exception {
        JSONObject future = alert("future", "Severe"); props(future).put("effective", "2026-01-01T12:30:00Z");
        assertTrue(Alerts.parse(feed(future), NOW).isEmpty());
    }
    @Test public void excludesTestAndCancelMessages() throws Exception {
        JSONObject test = alert("test", "Extreme"); props(test).put("status", "Test");
        JSONObject cancel = alert("cancel", "Extreme"); props(cancel).put("messageType", "Cancel");
        assertTrue(Alerts.parse(feed(test, cancel), NOW).isEmpty());
    }
    @Test public void acceptsUpdatesAndDeduplicatesIds() throws Exception {
        JSONObject update = alert("update", "Severe"); props(update).put("messageType", "Update");
        assertEquals(1, Alerts.parse(feed(update, update), NOW).size());
    }
    @Test public void nullOptionalFieldsAndHeadlineFallback() throws Exception {
        JSONObject entry = alert("a", "Unknown");
        props(entry).put("instruction", JSONObject.NULL).put("headline", JSONObject.NULL).put("effective", JSONObject.NULL);
        Alerts.Alert result = Alerts.parse(feed(entry), NOW).get(0);
        assertEquals("Example event", result.headline());
        assertEquals("", result.instruction());
        assertEquals(4, Alerts.severityRank(result.severity()));
    }
    @Test public void nullPaginationNextIsComplete() throws Exception {
        JSONObject root = new JSONObject(feed()).put("pagination", new JSONObject().put("next", JSONObject.NULL));
        assertTrue(Alerts.parse(root.toString(), NOW).isEmpty());
    }
    @Test(expected = JSONException.class) public void paginationCannotHideOtherAlerts() throws Exception {
        JSONObject root = new JSONObject(feed()).put("pagination", new JSONObject().put("next", "https://example.test/next"));
        Alerts.parse(root.toString(), NOW);
    }
    @Test(expected = JSONException.class) public void errorObjectIsNotEmptySuccess() throws Exception {
        Alerts.parse("{\"title\":\"Service unavailable\"}", NOW);
    }
    @Test(expected = JSONException.class) public void missingFeaturesIsNotEmptySuccess() throws Exception {
        Alerts.parse("{\"type\":\"FeatureCollection\"}", NOW);
    }
    @Test(expected = JSONException.class) public void nullStatusCannotLookLikeNoAlerts() throws Exception {
        JSONObject entry = alert("a", "Severe"); props(entry).put("status", JSONObject.NULL);
        Alerts.parse(feed(entry), NOW);
    }
    @Test(expected = JSONException.class) public void badExpiryCannotLookLikeNoAlerts() throws Exception {
        JSONObject entry = alert("a", "Severe"); props(entry).put("expires", "not a date");
        Alerts.parse(feed(entry), NOW);
    }
    @Test(expected = JSONException.class) public void oversizedTextIsRejected() throws Exception {
        JSONObject entry = alert("a", "Severe"); props(entry).put("description", "x".repeat(24001));
        Alerts.parse(feed(entry), NOW);
    }
    @Test public void removesBidiAndControlCharactersButKeepsLineBreaks() throws Exception {
        JSONObject entry = alert("a", "Severe"); props(entry).put("description", "A\u202eB\u0001\nC");
        assertEquals("AB\nC", Alerts.parse(feed(entry), NOW).get(0).description());
    }
    @Test public void areaChoicesAreUniqueAndPaired() {
        assertEquals(Areas.CODES.length, Areas.NAMES.length);
        assertEquals(Areas.CODES.length, new java.util.HashSet<>(List.of(Areas.CODES)).size());
        assertEquals(-1, Areas.index("VA&point=anything"));
        assertEquals(-1, Areas.index(""));
        assertEquals("Virginia", Areas.NAMES[Areas.index("VA")]);
    }
}
