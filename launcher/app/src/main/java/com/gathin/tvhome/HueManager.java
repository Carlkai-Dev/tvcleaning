package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Direct DAAK Smart Home / Philips Hue Bridge Controller for Android TV.
 * Communicates directly with Hue Bridge at 192.168.236.62 over the local router.
 */
public class HueManager {

    public static final String BRIDGE_IP = "192.168.236.62";
    public static final String HUE_USER = "Hn5l3sL8XtM4HSXtHGM1gchznGskKyP1bQCVYclq";
    public static final String BASE_URL = "http://" + BRIDGE_IP + "/api/" + HUE_USER;

    public static final String LIGHT_1_ID = "8";   // Şömine Üstü Işık 1
    public static final String LIGHT_2_ID = "10";  // Şömine Üstü Işık 2

    public interface HueCallback {
        void onSuccess(String response);
        void onError(Exception e);
    }

    public static class LightState {
        public boolean on = false;
        public int bri = 200;
        public String name = "";
        public boolean reachable = true;
    }

    public interface StatusCallback {
        void onStatus(boolean bridgeConnected, LightState light1, LightState light2);
    }

    private static final ExecutorService pool = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void fetchStatus(StatusCallback cb) {
        pool.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/lights");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject root = new JSONObject(sb.toString());
                    LightState l1 = parseLight(root, LIGHT_1_ID, "Şömine Üstü Sol (1)");
                    LightState l2 = parseLight(root, LIGHT_2_ID, "Şömine Üstü Sağ (2)");

                    mainHandler.post(() -> cb.onStatus(true, l1, l2));
                } else {
                    mainHandler.post(() -> cb.onStatus(false, null, null));
                }
                conn.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> cb.onStatus(false, null, null));
            }
        });
    }

    private static LightState parseLight(JSONObject root, String id, String defaultName) {
        LightState st = new LightState();
        st.name = defaultName;
        if (root.has(id)) {
            try {
                JSONObject obj = root.getJSONObject(id);
                st.name = obj.optString("name", defaultName);
                if (obj.has("state")) {
                    JSONObject state = obj.getJSONObject("state");
                    st.on = state.optBoolean("on", false);
                    st.bri = state.optInt("bri", 200);
                    st.reachable = state.optBoolean("reachable", true);
                }
            } catch (Exception ignored) {}
        }
        return st;
    }

    public static void toggleLight(String lightId, boolean currentState, HueCallback cb) {
        setLightState(lightId, !currentState, -1, cb);
    }

    public static void setLightState(String lightId, boolean on, int bri, HueCallback cb) {
        pool.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("on", on);
                if (bri > 0 && on) {
                    json.put("bri", Math.max(1, Math.min(254, bri)));
                }
                sendPut(BASE_URL + "/lights/" + lightId + "/state", json.toString(), cb);
            } catch (Exception e) {
                if (cb != null) mainHandler.post(() -> cb.onError(e));
            }
        });
    }

    public static void setAllLights(boolean on, int bri, HueCallback cb) {
        pool.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("on", on);
                if (bri > 0 && on) {
                    json.put("bri", Math.max(1, Math.min(254, bri)));
                }
                sendPut(BASE_URL + "/groups/0/action", json.toString(), cb);
            } catch (Exception e) {
                if (cb != null) mainHandler.post(() -> cb.onError(e));
            }
        });
    }

    public static void applyScene(String scene, HueCallback cb) {
        int bri;
        switch (scene.toLowerCase()) {
            case "cinema":
            case "sinema":
                bri = 35; // %15
                break;
            case "night":
            case "gece":
                bri = 12; // %5
                break;
            case "reading":
            case "okuma":
                bri = 180; // %70
                break;
            case "full":
            case "tam":
            default:
                bri = 254; // %100
                break;
        }
        setAllLights(true, bri, cb);
    }

    private static void sendPut(String endpoint, String jsonBody, HueCallback cb) {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                if (cb != null) {
                    mainHandler.post(() -> cb.onSuccess(sb.toString()));
                }
            } else {
                if (cb != null) {
                    mainHandler.post(() -> cb.onError(new RuntimeException("HTTP " + code)));
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            if (cb != null) {
                mainHandler.post(() -> cb.onError(e));
            }
        }
    }
}
