package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class WallpaperScheduleTest {
    private static final int MON = 1, TUE = 2, WED = 3, THU = 4, FRI = 5, SAT = 6, SUN = 7;

    private static int at(int hour, int minute) {
        return hour * 60 + minute;
    }

    /** The schedule Walter asked for: NHL Saturday evening, NFL Sunday evening, PSG Tuesday and Wednesday evening. */
    private static List<WallpaperSchedule.Rule> weekly() {
        List<WallpaperSchedule.Rule> rules = new ArrayList<>();
        rules.add(WallpaperSchedule.rule("wallpaper_nhl.jpg", Arrays.asList("sat"), "18:00", "04:00"));
        rules.add(WallpaperSchedule.rule("wallpaper_nfl.jpg", Arrays.asList("sun"), "18:00", "04:00"));
        rules.add(WallpaperSchedule.rule("wallpaper_psg.jpg", Arrays.asList("tue", "wed"), "18:00", "04:00"));
        return rules;
    }

    @Test public void saturdayEveningShowsTheNhlPicture() {
        assertEquals("wallpaper_nhl.jpg", WallpaperSchedule.pick(weekly(), SAT, at(18, 0)));
        assertEquals("wallpaper_nhl.jpg", WallpaperSchedule.pick(weekly(), SAT, at(23, 59)));
    }

    @Test public void theSmallHoursAfterTheMatchStillBelongToThatEvening() {
        assertEquals("wallpaper_nhl.jpg", WallpaperSchedule.pick(weekly(), SUN, at(0, 0)));
        assertEquals("wallpaper_nhl.jpg", WallpaperSchedule.pick(weekly(), SUN, at(3, 59)));
        assertEquals("wallpaper_nfl.jpg", WallpaperSchedule.pick(weekly(), MON, at(1, 30)));
    }

    @Test public void theWindowEndsAtFourInTheMorning() {
        assertNull(WallpaperSchedule.pick(weekly(), SUN, at(4, 0)));
        assertNull(WallpaperSchedule.pick(weekly(), SUN, at(12, 0)));
    }

    @Test public void theEveningStartsAtSix() {
        assertNull(WallpaperSchedule.pick(weekly(), SAT, at(17, 59)));
        assertNull(WallpaperSchedule.pick(weekly(), SAT, at(10, 0)));
    }

    @Test public void sundayEveningShowsTheNflPicture() {
        assertEquals("wallpaper_nfl.jpg", WallpaperSchedule.pick(weekly(), SUN, at(20, 0)));
    }

    @Test public void tuesdayAndWednesdayEveningShowThePsgPicture() {
        assertEquals("wallpaper_psg.jpg", WallpaperSchedule.pick(weekly(), TUE, at(21, 0)));
        assertEquals("wallpaper_psg.jpg", WallpaperSchedule.pick(weekly(), WED, at(21, 0)));
        assertEquals("wallpaper_psg.jpg", WallpaperSchedule.pick(weekly(), WED, at(2, 0)));   // after Tuesday's match
        assertEquals("wallpaper_psg.jpg", WallpaperSchedule.pick(weekly(), THU, at(2, 0)));   // after Wednesday's match
    }

    @Test public void otherEveningsKeepThePlainWallpaper() {
        assertNull(WallpaperSchedule.pick(weekly(), MON, at(21, 0)));
        assertNull(WallpaperSchedule.pick(weekly(), THU, at(21, 0)));
        assertNull(WallpaperSchedule.pick(weekly(), FRI, at(21, 0)));
        assertNull(WallpaperSchedule.pick(weekly(), FRI, at(3, 0)));   // Thursday has no rule
    }

    @Test public void theWeekWrapsFromSundayToMonday() {
        List<WallpaperSchedule.Rule> rules = Collections.singletonList(
                WallpaperSchedule.rule("a.jpg", Arrays.asList("sun"), "22:00", "02:00"));
        assertEquals("a.jpg", WallpaperSchedule.pick(rules, MON, at(1, 0)));
        assertNull(WallpaperSchedule.pick(rules, SUN, at(1, 0)));   // that is Saturday night's, which has no rule
    }

    @Test public void aWindowInsideOneDayDoesNotRunOver() {
        List<WallpaperSchedule.Rule> rules = Collections.singletonList(
                WallpaperSchedule.rule("noon.jpg", Arrays.asList("mon"), "12:00", "14:00"));
        assertEquals("noon.jpg", WallpaperSchedule.pick(rules, MON, at(12, 0)));
        assertEquals("noon.jpg", WallpaperSchedule.pick(rules, MON, at(13, 59)));
        assertNull(WallpaperSchedule.pick(rules, MON, at(14, 0)));
        assertNull(WallpaperSchedule.pick(rules, TUE, at(13, 0)));
    }

    @Test public void equalTimesMeanTheWholeDay() {
        List<WallpaperSchedule.Rule> rules = Collections.singletonList(
                WallpaperSchedule.rule("all.jpg", Arrays.asList("fri"), "00:00", "00:00"));
        assertEquals("all.jpg", WallpaperSchedule.pick(rules, FRI, at(0, 0)));
        assertEquals("all.jpg", WallpaperSchedule.pick(rules, FRI, at(23, 59)));
        assertNull(WallpaperSchedule.pick(rules, SAT, at(0, 0)));
    }

    @Test public void theFirstMatchingRuleWins() {
        List<WallpaperSchedule.Rule> rules = new ArrayList<>();
        rules.add(WallpaperSchedule.rule("first.jpg", Arrays.asList("sat"), "18:00", "23:00"));
        rules.add(WallpaperSchedule.rule("second.jpg", Arrays.asList("sat"), "18:00", "04:00"));
        assertEquals("first.jpg", WallpaperSchedule.pick(rules, SAT, at(19, 0)));
        assertEquals("second.jpg", WallpaperSchedule.pick(rules, SAT, at(23, 30)));
    }

    @Test public void dayNamesAreCaseInsensitiveAndMayBeSpelledOut() {
        assertNotNull(WallpaperSchedule.rule("a.jpg", Arrays.asList("SAT"), "18:00", "04:00"));
        assertNotNull(WallpaperSchedule.rule("a.jpg", Arrays.asList("Saturday"), "18:00", "04:00"));
        assertNotNull(WallpaperSchedule.rule("a.jpg", Arrays.asList(" sat "), "6:00", "4:00"));
    }

    @Test public void malformedRulesAreRefused() {
        assertNull(WallpaperSchedule.rule(null, Arrays.asList("sat"), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("a.jpg", null, "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("a.jpg", Collections.<String>emptyList(), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("a.jpg", Arrays.asList("samedi"), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("a.jpg", Arrays.asList("sat", "xyz"), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("a.jpg", Arrays.asList("sat"), "25:00", "04:00"));
        assertNull(WallpaperSchedule.rule("a.jpg", Arrays.asList("sat"), "18:60", "04:00"));
        assertNull(WallpaperSchedule.rule("a.jpg", Arrays.asList("sat"), "evening", "04:00"));
        assertNull(WallpaperSchedule.rule("a.jpg", Arrays.asList("sat"), "18:00", null));
    }

    @Test public void fileNamesCannotLeaveTheThemeFolder() {
        assertNull(WallpaperSchedule.rule("../x.jpg", Arrays.asList("sat"), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("sub/x.jpg", Arrays.asList("sat"), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("/sdcard/x.jpg", Arrays.asList("sat"), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("..jpg", Arrays.asList("sat"), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("x.png", Arrays.asList("sat"), "18:00", "04:00"));
        assertNull(WallpaperSchedule.rule("a..b.jpg", Arrays.asList("sat"), "18:00", "04:00"));
        assertNotNull(WallpaperSchedule.rule("wallpaper_psg-2.jpg", Arrays.asList("sat"), "18:00", "04:00"));
    }

    @Test public void noRulesMeansNoScheduledPicture() {
        assertNull(WallpaperSchedule.pick(null, SAT, at(20, 0)));
        assertNull(WallpaperSchedule.pick(Collections.<WallpaperSchedule.Rule>emptyList(), SAT, at(20, 0)));
    }
}
