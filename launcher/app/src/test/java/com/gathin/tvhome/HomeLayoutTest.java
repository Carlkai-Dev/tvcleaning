package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class HomeLayoutTest {
    private static List<String> keys(String... keys) { return Arrays.asList(keys); }
    private static HomeLayout home(String... apps) {
        HomeLayout layout = new HomeLayout(); layout.reconcile(keys(apps)); return layout;
    }

    @Test public void migrationRetainsCustomOrderAndAppendsDiscoveredApps() {
        HomeLayout layout = home("youtube", "netflix", "hidden");
        layout.reconcile(keys("netflix", "youtube", "new"));
        assertEquals(keys("youtube", "netflix", "new"), layout.visible(null, keys("netflix", "youtube", "new")));
        assertTrue(layout.home.contains("hidden"));
    }

    @Test public void folderReplacesAppAtItsHomePosition() {
        HomeLayout layout = home("a", "b", "c");
        String id = layout.createFolder("b", "Streaming");
        assertEquals(keys("a", id, "c"), layout.home);
        assertEquals(keys("b"), layout.folders.get(id).apps);
        assertEquals(keys("a", id, "c"), layout.visible(null, keys("a", "b", "c")));
    }

    @Test public void movingBetweenFoldersNeverDuplicatesAnAppAndRemovesEmptyFolder() {
        HomeLayout layout = home("a", "b", "c");
        String first = layout.createFolder("a", "One"), second = layout.createFolder("b", "Two");
        layout.moveTo("a", second);
        assertFalse(layout.folders.containsKey(first));
        assertFalse(layout.home.contains(first));
        assertEquals(keys("b", "a"), layout.folders.get(second).apps);
        assertEquals(keys(second, "c"), layout.home);
    }

    @Test public void lastAppMovedHomeDeletesFolderButKeepsApp() {
        HomeLayout layout = home("a", "b");
        String id = layout.createFolder("a", "One");
        layout.moveTo("a", null);
        assertTrue(layout.folders.isEmpty());
        assertEquals(keys("b", "a"), layout.home);
        assertFalse(layout.home.contains(id));
    }

    @Test public void hiddenMembersKeepTheirFolderAndReturnToTheSamePlace() {
        HomeLayout layout = home("a", "b", "c");
        String id = layout.createFolder("a", "One");
        layout.moveTo("b", id);
        layout.reconcile(keys("c"));
        assertEquals(keys("c"), layout.visible(null, keys("c")));
        assertTrue(layout.folders.containsKey(id));
        assertEquals(keys(id, "c"), layout.visible(null, keys("a", "c")));
        assertEquals(keys("a", "b"), layout.visible(id, keys("a", "b", "c")));
    }

    @Test public void reorderInsideFolderRetainsUnavailableMemberRanks() {
        HomeLayout layout = home("a", "b", "c", "d");
        String id = layout.createFolder("a", "One");
        layout.moveTo("b", id); layout.moveTo("c", id);
        layout.reorder(id, keys("c", "a"));
        assertEquals(keys("c", "b", "a"), layout.folders.get(id).apps);
        assertEquals(keys(id, "d"), layout.home);
    }

    @Test public void snapshotIsIndependentForCancelAndFailedSave() {
        HomeLayout layout = home("a", "b", "c");
        String id = layout.createFolder("a", "Original");
        HomeLayout draft = layout.copy();
        draft.folders.get(id).name = "Changed";
        draft.moveTo("b", id);
        draft.reorder(null, keys("c", id));
        assertEquals("Original", layout.folders.get(id).name);
        assertEquals(keys("a"), layout.folders.get(id).apps);
        assertEquals(keys(id, "b", "c"), layout.home);
    }

    @Test public void normalizeDuplicateAssignmentsAndOrphanFolderReferences() {
        HomeLayout layout = home("a", "b");
        String id = layout.createFolder("a", "One");
        layout.home.add("a"); layout.home.add(id); layout.home.add("folder:missing");
        layout.folders.get(id).apps.add("a");
        layout.reconcile(keys("a", "b"));
        assertEquals(keys(id, "b"), layout.home);
        assertEquals(keys("a"), layout.folders.get(id).apps);
    }

    @Test public void foldersCannotBeNestedAndUnknownDestinationsAreHarmless() {
        HomeLayout layout = home("a", "b");
        String id = layout.createFolder("a", "One");
        layout.moveTo("b", "folder:missing");
        layout.moveTo(id, null);
        assertNull(layout.createFolder(id, "Nested"));
        assertEquals(keys(id, "b"), layout.home);
        assertEquals(Collections.emptyList(), layout.visible("folder:missing", keys("a", "b")));
    }
}
