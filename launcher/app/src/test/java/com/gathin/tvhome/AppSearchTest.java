package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class AppSearchTest {
    private List<Integer> find(Locale locale, String query, String... labels) {
        List<String> keys = Arrays.stream(labels).map(s -> AppSearch.key(s, locale)).collect(Collectors.toList());
        return AppSearch.indices(keys, query, locale);
    }
    @Test public void findsAPartialAppNameWithoutChangingTheExistingOrder() {
        assertEquals(Arrays.asList(0, 2), find(Locale.ENGLISH, "net", "Netflix", "YouTube", "NET Radio"));
    }
    @Test public void blankQueryRestoresEveryEntry() {
        assertEquals(Arrays.asList(0, 1, 2), find(Locale.ENGLISH, " \t\n", "Netflix", "YouTube", "AirPlay"));
    }
    @Test public void noMatchesIsAnEmptyList() {
        assertEquals(Collections.emptyList(), find(Locale.ENGLISH, "missing", "Netflix", "YouTube"));
    }
    @Test public void emptyCatalogueRemainsEmpty() {
        assertEquals(Collections.emptyList(), find(Locale.ENGLISH, "net"));
    }
    @Test public void queryWordsMayOccurInEitherOrder() {
        assertEquals(Arrays.asList(0, 2), find(Locale.ENGLISH, "video prime", "Prime Video", "Video", "Video / Prime"));
    }
    @Test public void everyQueryWordMustMatch() {
        assertEquals(Collections.singletonList(0), find(Locale.ENGLISH, "youtube music", "YouTube Music", "YouTube", "Music"));
    }
    @Test public void composedAndDecomposedAccentsMatch() {
        assertEquals(Arrays.asList(0, 1), find(Locale.FRENCH, "cafe", "Café TV", "Cafe\u0301 TV", "Coffee TV"));
        assertEquals(Collections.singletonList(0), find(Locale.FRENCH, "CAFÉ", "Cafe TV", "Tea"));
    }
    @Test public void turkishCaseUsesTheRequestedLocale() {
        Locale tr = Locale.forLanguageTag("tr");
        assertEquals(Collections.singletonList(0), find(tr, "iz", "İzle", "IRMAK"));
        assertEquals(Collections.singletonList(1), find(tr, "ır", "İzle", "IRMAK"));
    }
    @Test public void germanSharpSAndGreekFinalSigmaFoldForSearch() {
        assertEquals(Collections.singletonList(0), find(Locale.GERMAN, "STRASSE", "Straße TV"));
        assertEquals(Collections.singletonList(0), find(Locale.forLanguageTag("el"), "κοσμοσ", "Κόσμος"));
    }
    @Test public void hebrewAndArabicNamesRemainSearchable() {
        assertEquals(Collections.singletonList(1), find(Locale.forLanguageTag("he"), "כאן", "YouTube", "כאן"));
        assertEquals(Collections.singletonList(1), find(Locale.forLanguageTag("ar"), "شاهد", "YouTube", "شَاهِد"));
    }
    @Test public void cjkAndEmojiTextIsPreserved() {
        assertEquals(Collections.singletonList(0), find(Locale.JAPANESE, "テレビ", "テレビ番組", "YouTube"));
        assertEquals(Collections.singletonList(1), find(Locale.ENGLISH, "📺", "YouTube", "TV 📺"));
    }
    @Test public void unicodeWhitespaceAndNullLabelsAreHandled() {
        assertEquals(Collections.singletonList(0), find(Locale.ENGLISH, " prime\u00a0video ", "Prime Video", null));
        assertEquals("", AppSearch.key(null, Locale.ROOT));
    }
    @Test public void inputCatalogAndKeysAreNotMutated() {
        List<String> keys = Arrays.asList("youtube", "netflix");
        assertEquals(Collections.singletonList(1), AppSearch.indices(keys, "NET", Locale.ENGLISH));
        assertEquals(Arrays.asList("youtube", "netflix"), keys);
    }
    @Test public void selectionFollowsPackageRatherThanItsPreviousPosition() {
        assertEquals(1, AppSearch.focusIndex(Arrays.asList("com.a", "com.b"), "com.b", 0));
        assertEquals(0, AppSearch.focusIndex(Arrays.asList("com.b", "com.a"), "com.b", 1));
    }
    @Test public void removingTheSelectedAppChoosesAnExistingNeighbour() {
        assertEquals(1, AppSearch.focusIndex(Arrays.asList("com.a", "com.c"), "com.b", 9));
        assertEquals(0, AppSearch.focusIndex(Arrays.asList("com.a", "com.c"), "com.b", -1));
    }
    @Test public void emptyResultsHaveNoSelectedCard() {
        assertEquals(-1, AppSearch.focusIndex(Collections.emptyList(), "com.a", 0));
    }
}
