package io.github.epochcoder.homelab;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CaptionTest {
    private static final String PATTERN = "EEEE, d MMMM yyyy";

    private static Immich.Photo photo(String date, String city, String state, String country, String description,
                                      List<String> people) {
        return new Immich.Photo("id", date, city, state, country, description, people);
    }

    @Test
    public void albumLeadsThenDateAndPlace() {
        Caption c = Caption.of(photo("2025-06-14T15:32:10.000Z", "Cape Town", "Western Cape", "South Africa", "",
                Arrays.asList("Anna", "Ben")), "Summer 2025", PATTERN, Locale.UK);
        assertEquals("Summer 2025", c.title);
        assertEquals("Saturday, 14 June 2025  ·  Cape Town, South Africa", c.subtitle);
        assertEquals("Anna, Ben", c.people);
    }

    @Test
    public void placeLeadsWithoutAnAlbumAndDateWithoutEither() {
        Caption place = Caption.of(photo("2025-06-14T23:59:00.000Z", "", "Texas", "United States", "",
                Collections.emptyList()), "", PATTERN, Locale.UK);
        assertEquals("Texas, United States", place.title);
        assertEquals("Saturday, 14 June 2025", place.subtitle);
        assertEquals("", place.people);

        Caption bare = Caption.of(photo("2025-06-14T00:00:00.000Z", "", "", "", "", Collections.emptyList()),
                "", PATTERN, Locale.UK);
        assertEquals("Saturday, 14 June 2025", bare.title);
        assertEquals("", bare.subtitle);
    }

    @Test
    public void descriptionComesFirstUnderTheTitle() {
        Caption c = Caption.of(photo("2025-06-14T10:00:00.000Z", "Paris", "", "France", "First swim",
                Collections.emptyList()), "Holiday", PATTERN, Locale.UK);
        assertEquals("First swim  ·  Saturday, 14 June 2025  ·  Paris, France", c.subtitle);
    }

    @Test
    public void localDateIsNotShiftedByTimezone() {
        // 23:30 local on New Year's Eve stays New Year's Eve, whatever the TV's timezone.
        assertEquals("31 December 2024", Caption.date("2024-12-31T23:30:00.000Z", "d MMMM yyyy", Locale.UK));
        assertEquals("", Caption.date("", PATTERN, Locale.UK));
        assertEquals("", Caption.date("garbage", PATTERN, Locale.UK));
        assertEquals("", Caption.date("2024-13-01T00:00:00Z", PATTERN, Locale.UK));
    }

    @Test
    public void peopleBeyondThreeAreCounted() {
        assertEquals("", Caption.people(Collections.emptyList()));
        assertEquals("Anna", Caption.people(Collections.singletonList("Anna")));
        assertEquals("A, B, C", Caption.people(Arrays.asList("A", "B", "C")));
        assertEquals("A, B, C +2", Caption.people(Arrays.asList("A", "B", "C", "D", "E")));
    }
}
