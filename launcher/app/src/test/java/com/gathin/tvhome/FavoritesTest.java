package com.gathin.tvhome;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class FavoritesTest {

    // ---- validPackage -----------------------------------------------------------------------

    @Test public void validPackageAcceptsTypicalNames() {
        assertTrue(Favorites.validPackage("com.example.app"));
        assertTrue(Favorites.validPackage("com.netflix.ninja"));
        assertTrue(Favorites.validPackage("org.xbmc.kodi"));
        assertTrue(Favorites.validPackage("a.b"));
        assertTrue(Favorites.validPackage("com.example.app123"));
        assertTrue(Favorites.validPackage("com.example._private"));
    }

    @Test public void validPackageRejectsInvalidNames() {
        assertFalse(Favorites.validPackage(null));
        assertFalse(Favorites.validPackage(""));
        assertFalse(Favorites.validPackage(".com.example"));      // leading dot
        assertFalse(Favorites.validPackage("com.example."));     // trailing dot
        assertFalse(Favorites.validPackage("com..example"));     // double dot
        assertFalse(Favorites.validPackage("9bad.pkg"));         // digit-first segment
        assertFalse(Favorites.validPackage("../evil"));          // path traversal
        assertFalse(Favorites.validPackage("com.example/bad")); // slash
        assertFalse(Favorites.validPackage("com"));             // no dot
    }

    // ---- change -----------------------------------------------------------------------------

    @Test public void addingAPackageIncludesItInTheResult() {
        Set<String> result = Favorites.change(Collections.emptySet(), "com.example.app", true);
        assertTrue(result.contains("com.example.app"));
        assertEquals(1, result.size());
    }

    @Test public void addingAnAlreadyPresentPackageIsIdempotent() {
        Set<String> start = new HashSet<>();
        start.add("com.example.app");
        Set<String> result = Favorites.change(start, "com.example.app", true);
        assertEquals(1, result.size());
    }

    @Test public void removingAPresentPackageExcludesItFromTheResult() {
        Set<String> start = new HashSet<>();
        start.add("com.example.app");
        start.add("com.example.other");
        Set<String> result = Favorites.change(start, "com.example.app", false);
        assertFalse(result.contains("com.example.app"));
        assertTrue(result.contains("com.example.other"));
    }

    @Test public void removingAnAbsentPackageReturnsUnchangedSet() {
        Set<String> start = new HashSet<>();
        start.add("com.example.other");
        Set<String> result = Favorites.change(start, "com.example.missing", false);
        assertEquals(1, result.size());
        assertTrue(result.contains("com.example.other"));
    }

    @Test public void changeDoesNotMutateTheInputSet() {
        Set<String> original = new HashSet<>();
        original.add("com.example.app");
        Favorites.change(original, "com.example.new", true);
        assertEquals(1, original.size());
    }

    @Test public void addingBeyondMaxThrows() {
        Set<String> full = new HashSet<>();
        for (int i = 0; i < Favorites.MAX_FAVORITES; i++) full.add("com.example.app" + i);
        try {
            Favorites.change(full, "com.example.extra", true);
            fail("Expected IllegalArgumentException for too many favorites");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void addingAnExistingEntryToAFullSetIsStillIdempotent() {
        Set<String> full = new HashSet<>();
        for (int i = 0; i < Favorites.MAX_FAVORITES; i++) full.add("com.example.app" + i);
        // Re-adding an already-present entry in a full set must not throw
        Set<String> result = Favorites.change(full, "com.example.app0", true);
        assertEquals(Favorites.MAX_FAVORITES, result.size());
    }

    @Test public void changeRejectsInvalidPackageNames() {
        try {
            Favorites.change(Collections.emptySet(), "bad..name", true);
            fail();
        } catch (IllegalArgumentException expected) { }
    }

    // ---- encode / decode round-trip ---------------------------------------------------------

    @Test public void emptySetEncodesToEmptyStringAndBack() {
        assertEquals("", Favorites.encode(Collections.emptySet()));
        assertEquals(Collections.emptySet(), Favorites.decode(""));
    }

    @Test public void encodeProducesSortedNewlineSeparatedLines() {
        Set<String> apps = new HashSet<>();
        apps.add("org.z.example");
        apps.add("com.a.example");
        assertEquals("com.a.example\norg.z.example\n", Favorites.encode(apps));
    }

    @Test public void encodedSetRoundTripsBackToSameContent() {
        Set<String> apps = new HashSet<>();
        apps.add("com.netflix.ninja");
        apps.add("org.xbmc.kodi");
        apps.add("com.google.android.youtube.tv");
        assertEquals(apps, Favorites.decode(Favorites.encode(apps)));
    }

    // ---- decode error cases -----------------------------------------------------------------

    @Test public void decodeRejectsDuplicateEntries() {
        try {
            Favorites.decode("com.example.app\ncom.example.app\n");
            fail();
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void decodeRejectsMalformedPackageNames() {
        for (String raw : new String[]{
                "com..example\n",
                "../file\n",
                "9bad.app\n",
                "com.example\ninvalid/name\n"
        }) {
            try {
                Favorites.decode(raw);
                fail("Should have rejected: " + raw);
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void decodeRejectsTooManyEntries() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= Favorites.MAX_FAVORITES; i++) sb.append("com.example.app").append(i).append('\n');
        try {
            Favorites.decode(sb.toString());
            fail();
        } catch (IllegalArgumentException expected) { }
    }

    // ---- stale package handling (filter logic) ----------------------------------------------

    /**
     * Callers filter stale packages when building the favorites row.  Verify the underlying
     * change() correctly removes packages so the file is cleaned up lazily.
     */
    @Test public void stalePackageIsRemovedViaChange() {
        Set<String> saved = new HashSet<>();
        saved.add("com.installed.app");
        saved.add("com.uninstalled.app");
        Set<String> cleaned = Favorites.change(saved, "com.uninstalled.app", false);
        assertFalse(cleaned.contains("com.uninstalled.app"));
        assertTrue(cleaned.contains("com.installed.app"));
    }

    // ---- boundary ---------------------------------------------------------------------------

    @Test public void fullListAllowsRemovalAndReAdditionButNotNewEntry() {
        Set<String> full = new HashSet<>();
        for (int i = 0; i < Favorites.MAX_FAVORITES; i++) full.add("com.example.app" + i);

        // removal OK
        assertEquals(Favorites.MAX_FAVORITES - 1, Favorites.change(full, "com.example.app0", false).size());
        // re-add of existing entry OK (idempotent)
        assertEquals(Favorites.MAX_FAVORITES, Favorites.change(full, "com.example.app0", true).size());
        // new entry beyond limit throws
        try {
            Favorites.change(full, "com.example.brandnew", true);
            fail();
        } catch (IllegalArgumentException expected) { }
        // original set is not mutated
        assertEquals(Favorites.MAX_FAVORITES, full.size());
    }
}
