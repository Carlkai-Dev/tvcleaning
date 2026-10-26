package com.dvopenlabs.weatheralerts;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parsing is all-or-error: a broken or incomplete feed must never look like an empty successful check. */
final class Alerts {
    static final int MAX_BYTES = 4 * 1024 * 1024;
    static final int MAX_SHOWN = 100;
    record Alert(String id, String headline, String severity, String area, String description,
                 String instruction, Instant expires) {}

    static List<Alert> parse(String json, Instant now) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!"FeatureCollection".equals(root.optString("type"))) throw new JSONException("Not a collection");
        JSONObject pagination = root.optJSONObject("pagination");
        if (pagination != null && !pagination.isNull("next") && !pagination.optString("next").isEmpty())
            throw new JSONException("Incomplete feed");
        JSONArray features = root.getJSONArray("features");
        if (features.length() > 500) throw new JSONException("Too many entries");
        List<Alert> alerts = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject p = features.getJSONObject(i).getJSONObject("properties");
            String status = required(p, "status", 16);
            if ("Exercise".equals(status) || "System".equals(status) || "Test".equals(status) || "Draft".equals(status)) continue;
            if (!"Actual".equals(status)) throw new JSONException("Unknown status");
            String messageType = p.getString("messageType");
            if ("Cancel".equals(messageType)) continue;
            if (!"Alert".equals(messageType) && !"Update".equals(messageType))
                throw new JSONException("Unknown message type");
            Instant expires = instant(p.getString("expires"));
            if (!expires.isAfter(now)) continue;
            if (!p.isNull("effective") && instant(p.getString("effective")).isAfter(now)) continue;
            String id = required(p, "id", 512);
            String headline = p.isNull("headline") ? required(p, "event", 240) : required(p, "headline", 240);
            String severity = required(p, "severity", 32);
            String area = required(p, "areaDesc", 4000);
            String description = required(p, "description", 24000);
            String instruction = p.isNull("instruction") ? "" : clean(p.getString("instruction"), 24000);
            if (ids.add(id)) alerts.add(new Alert(id, headline, severity, area, description, instruction, expires));
        }
        alerts.sort(Comparator.comparingInt((Alert a) -> severityRank(a.severity()))
                .thenComparing(Alert::expires).thenComparing(Alert::id));
        return alerts;
    }

    static int severityRank(String value) {
        return switch (value) { case "Extreme" -> 0; case "Severe" -> 1; case "Moderate" -> 2;
            case "Minor" -> 3; default -> 4; };
    }

    private static Instant instant(String value) throws JSONException {
        // Android's older ISO_INSTANT parser rejects NWS's numeric offsets, although desktop JDK 17 accepts them.
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeParseException e) { throw new JSONException("Invalid time"); }
    }

    private static String required(JSONObject p, String key, int limit) throws JSONException {
        if (p.isNull(key)) throw new JSONException("Missing " + key);
        String result = clean(p.getString(key), limit);
        if (result.trim().isEmpty()) throw new JSONException("Missing " + key);
        return result;
    }

    private static String clean(String value, int limit) throws JSONException {
        if (value.length() > limit) throw new JSONException("Text too long");
        return value.replaceAll("[\\p{Cc}&&[^\\n\\t]]|\\p{Cf}", "");
    }
}
