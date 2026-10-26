package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class WifiMenuTest {
    @Test
    public void securityFromCapabilities() {
        assertEquals(WifiMenu.PSK, WifiMenu.security("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"));
        assertEquals(WifiMenu.SAE, WifiMenu.security("[RSN-SAE-CCMP][MFPR][MFPC]"));
        assertEquals(WifiMenu.EAP, WifiMenu.security("[WPA2-EAP/SHA1-CCMP][RSN-EAP/SHA1-CCMP][ESS]"));
        assertEquals(WifiMenu.OPEN, WifiMenu.security("[ESS]"));
        assertEquals(WifiMenu.OPEN, WifiMenu.security(null));
    }

    @Test
    public void wpa2AndWpa3TransitionNetworksJoinAsWpa2() {
        assertEquals(WifiMenu.PSK, WifiMenu.security("[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS][MFPC]"));
    }

    @Test
    public void barsStayBetweenOneAndThree() {
        assertEquals(1, WifiMenu.bars(0));
        assertEquals(1, WifiMenu.bars(1));
        assertEquals(2, WifiMenu.bars(2));
        assertEquals(3, WifiMenu.bars(3));
        assertEquals(3, WifiMenu.bars(4));
    }

    @Test
    public void oneRowPerNameStrongestFirstHiddenLeftOut() {
        List<WifiMenu.Network> scan = Arrays.asList(
                new WifiMenu.Network("Home", -70, "[WPA2-PSK-CCMP]"),
                new WifiMenu.Network("Home", -50, "[WPA2-PSK-CCMP]"),
                new WifiMenu.Network("", -40, "[ESS]"),
                new WifiMenu.Network(null, -40, "[ESS]"),
                new WifiMenu.Network("Cafe", -60, "[ESS]"),
                new WifiMenu.Network("  Upstairs  ", -80, "[RSN-SAE-CCMP]"));
        List<WifiMenu.Network> list = WifiMenu.strongestFirst(scan, 6);
        assertEquals(3, list.size());
        assertEquals("Home", list.get(0).ssid);
        assertEquals(-50, list.get(0).rssi);
        assertEquals("Cafe", list.get(1).ssid);
        assertEquals("Upstairs", list.get(2).ssid);
    }

    @Test
    public void listIsCappedAtMax() {
        List<WifiMenu.Network> scan = Arrays.asList(
                new WifiMenu.Network("A", -40, ""), new WifiMenu.Network("B", -50, ""),
                new WifiMenu.Network("C", -60, ""));
        assertEquals(2, WifiMenu.strongestFirst(scan, 2).size());
    }
}
