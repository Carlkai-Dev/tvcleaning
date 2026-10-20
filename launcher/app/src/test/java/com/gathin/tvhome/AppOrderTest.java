package com.gathin.tvhome;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class AppOrderTest {
    private static List<String> keys(String... keys) { return Arrays.asList(keys); }

    @Test public void firstRunRetainsThemeOrder() {
        assertEquals(keys("netflix", "prime", "youtube", "kodi"),
                AppOrder.reconcile(keys("netflix", "prime", "youtube", "kodi"), Collections.emptyList()));
    }

    @Test public void reloadRetainsCustomOrderAndAppendsNewApps() {
        assertEquals(keys("youtube", "netflix", "newA", "newB"),
                AppOrder.reconcile(keys("netflix", "newA", "newB", "youtube"),
                        keys("youtube", "removed", "netflix")));
    }

    @Test public void duplicateKeysNeverDuplicateVisibleApps() {
        assertEquals(keys("b", "a"), AppOrder.reconcile(keys("a", "a", "b"), keys("b", "b", "a")));
    }

    @Test public void activitiesInOnePackageKeepSeparatePositions() {
        assertEquals(keys("pkg/.Second", "pkg/.First"), AppOrder.reconcile(
                keys("pkg/.First", "pkg/.Second"), keys("pkg/.Second", "pkg/.First")));
    }

    @Test public void savingPreservesMissingAndHiddenRanks() {
        List<String> merged = AppOrder.merge(keys("a", "hidden", "b", "missing", "c"), keys("c", "a", "b", "new"));
        assertEquals(keys("c", "hidden", "a", "missing", "b", "new"), merged);
        assertEquals(keys("c", "hidden", "a", "b", "new"),
                AppOrder.reconcile(keys("a", "b", "c", "hidden", "new"), merged));
    }

    @Test public void saveDeduplicatesOlderSavedKeys() {
        assertEquals(keys("b", "a"), AppOrder.merge(keys("a", "a", "b"), keys("b", "a")));
    }

    @Test public void movesCrossDockByInsertionAndSnapshotCancelsWholeMove() {
        List<String> snapshot = keys("a", "b", "c", "d", "e", "f", "g");
        List<String> draft = new ArrayList<>(snapshot);
        int to = AppOrder.target(1, draft.size(), 4, AppOrder.Direction.DOWN);
        draft.add(to, draft.remove(1));
        assertEquals(keys("a", "c", "d", "e", "f", "b", "g"), draft);
        draft.add(0, draft.remove(5));
        assertEquals(keys("b", "a", "c", "d", "e", "f", "g"), draft);
        assertEquals(keys("a", "b", "c", "d", "e", "f", "g"), snapshot);
    }

    @Test public void partialFinalRowClampsVerticallyWithoutHorizontalWrap() {
        assertEquals(5, AppOrder.target(3, 6, 4, AppOrder.Direction.DOWN));
        assertEquals(1, AppOrder.target(5, 6, 4, AppOrder.Direction.UP));
        assertEquals(3, AppOrder.target(3, 6, 4, AppOrder.Direction.RIGHT));
        assertEquals(4, AppOrder.target(4, 6, 4, AppOrder.Direction.LEFT));
        assertEquals(5, AppOrder.target(5, 6, 4, AppOrder.Direction.DOWN));
    }

    @Test public void boundariesHoldForEveryConfiguredColumnCount() {
        for (int columns = 1; columns <= 8; columns++) {
            for (int count = 1; count <= 25; count++) {
                for (int pos = 0; pos < count; pos++) {
                    for (AppOrder.Direction direction : AppOrder.Direction.values()) {
                        int target = AppOrder.target(pos, count, columns, direction);
                        assertTrue(target >= 0 && target < count);
                        if (direction == AppOrder.Direction.LEFT || direction == AppOrder.Direction.RIGHT)
                            assertEquals(pos / columns, target / columns);
                        if (direction == AppOrder.Direction.UP && pos < columns) assertEquals(pos, target);
                    }
                }
            }
        }
    }

    @Test public void centeredFolderRowsNavigateToTheVisuallyNearestApp() {
        assertEquals(4, AppOrder.centeredTarget(0, 6, 4, AppOrder.Direction.DOWN));
        assertEquals(4, AppOrder.centeredTarget(1, 6, 4, AppOrder.Direction.DOWN));
        assertEquals(5, AppOrder.centeredTarget(2, 6, 4, AppOrder.Direction.DOWN));
        assertEquals(5, AppOrder.centeredTarget(3, 6, 4, AppOrder.Direction.DOWN));
        assertEquals(1, AppOrder.centeredTarget(4, 6, 4, AppOrder.Direction.UP));
        assertEquals(2, AppOrder.centeredTarget(5, 6, 4, AppOrder.Direction.UP));
        assertEquals(4, AppOrder.centeredTarget(4, 6, 4, AppOrder.Direction.LEFT));
        assertEquals(5, AppOrder.centeredTarget(5, 6, 4, AppOrder.Direction.DOWN));
        assertEquals(0, AppOrder.centeredTarget(0, 1, 4, AppOrder.Direction.UP));
        assertEquals(0, AppOrder.centeredTarget(4, 8, 4, AppOrder.Direction.UP));
    }

    @Test public void emptyInventoryAndInvalidPositionsAreHarmless() {
        assertEquals(Collections.emptyList(), AppOrder.reconcile(Collections.emptyList(), keys("missing")));
        assertEquals(-1, AppOrder.target(-1, 0, 4, AppOrder.Direction.DOWN));
        assertEquals(0, AppOrder.target(0, 1, 0, AppOrder.Direction.DOWN));
    }
}
