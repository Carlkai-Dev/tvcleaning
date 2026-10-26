package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Wi-Fi drop-down's logic that needs no TV (wifi_menu): which networks to list and how each one is
 * shown. MainActivity turns Android's scan results into {@link Network}s and draws what this returns.
 */
final class WifiMenu {

    static final String OPEN = "open";
    /** WPA/WPA2 personal, and WEP: a password. */
    static final String PSK = "psk";
    /** WPA3 personal: a password. */
    static final String SAE = "sae";
    /** Enterprise (802.1X): needs more than a password, so it opens Android's Wi-Fi settings. */
    static final String EAP = "eap";

    private WifiMenu() {
    }

    /** One access point from a scan. */
    static final class Network {
        final String ssid;
        final int rssi;
        final String capabilities;

        Network(String ssid, int rssi, String capabilities) {
            this.ssid = ssid;
            this.rssi = rssi;
            this.capabilities = capabilities;
        }
    }

    /**
     * The security of a network from ScanResult.capabilities, e.g. "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]".
     * A network offering both WPA2 and WPA3 counts as WPA2, which every TV can join.
     */
    static String security(String capabilities) {
        String caps = capabilities == null ? "" : capabilities;
        if (caps.contains("EAP")) return EAP;
        if (caps.contains("PSK") || caps.contains("WEP")) return PSK;
        if (caps.contains("SAE")) return SAE;
        return OPEN;
    }

    /** Bars on the icon, 1 to 3, from Android's 0-4 signal level (WifiManager.calculateSignalLevel(rssi, 5)). */
    static int bars(int level) {
        return Math.max(1, Math.min(3, level));
    }

    /**
     * The networks to list: one per name, using its strongest access point, strongest first, hidden networks
     * (no name) left out, at most {@code max}.
     */
    static List<Network> strongestFirst(List<Network> scan, int max) {
        Map<String, Network> best = new HashMap<>();
        for (Network n : scan) {
            String ssid = n.ssid == null ? "" : n.ssid.trim();
            if (ssid.isEmpty()) continue;
            Network seen = best.get(ssid);
            if (seen == null || n.rssi > seen.rssi) best.put(ssid, new Network(ssid, n.rssi, n.capabilities));
        }
        List<Network> out = new ArrayList<>(best.values());
        Collections.sort(out, (a, b) -> {
            int byStrength = Integer.compare(b.rssi, a.rssi);
            return byStrength != 0 ? byStrength : a.ssid.compareToIgnoreCase(b.ssid);
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
