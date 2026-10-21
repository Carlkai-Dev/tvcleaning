package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherTest {

    @Test
    public void urlRoundsCoordinatesToATenth() {
        String url = Weather.url(54.6872, 25.2797);
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=54.7&longitude=25.3&"));
    }

    @Test
    public void urlIgnoresTheDefaultLocale() {
        java.util.Locale before = java.util.Locale.getDefault();
        java.util.Locale.setDefault(new java.util.Locale("es", "ES"));
        try {
            assertTrue(Weather.url(40.4168, -3.7038).contains("latitude=40.4&longitude=-3.7&"));
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    public void kindFollowsWmoCodes() {
        assertEquals(Weather.Kind.CLEAR, Weather.kind(0));
        assertEquals(Weather.Kind.PARTLY_CLOUDY, Weather.kind(2));
        assertEquals(Weather.Kind.CLOUDY, Weather.kind(3));
        assertEquals(Weather.Kind.FOG, Weather.kind(45));
        assertEquals(Weather.Kind.RAIN, Weather.kind(53));
        assertEquals(Weather.Kind.RAIN, Weather.kind(81));
        assertEquals(Weather.Kind.SNOW, Weather.kind(75));
        assertEquals(Weather.Kind.SNOW, Weather.kind(86));
        assertEquals(Weather.Kind.STORM, Weather.kind(95));
    }

    @Test
    public void descriptionNamesTheSky() {
        assertEquals(R.string.weather_clear, Weather.description(0));
        assertEquals(R.string.weather_drizzle, Weather.description(51));
        assertEquals(R.string.weather_heavy_rain, Weather.description(65));
        assertEquals(R.string.weather_rain, Weather.description(61));
        assertEquals(R.string.weather_showers, Weather.description(80));
        assertEquals(R.string.weather_storm, Weather.description(99));
    }
}
