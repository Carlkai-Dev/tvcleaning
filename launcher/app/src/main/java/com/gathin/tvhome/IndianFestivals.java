package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.Map;

/**
 * The festival greeting ("Happy Diwali") for a day, from a table of Indian festival dates. Most of them follow the
 * lunar calendar, so the table is per year and runs out: after 2028 only the fixed-date ones remain until it is
 * extended. Dates are as Mumbai keeps them, from public 2026-2028 holiday calendars; Eid moves a day with the moon.
 */
final class IndianFestivals {

    static final int LAST_YEAR = 2028;

    private static final Map<LocalDate, String> DAYS = new HashMap<>();

    static {
        // 2026
        add("2026-01-14", "Makar Sankranti");
        add("2026-03-04", "Holi");
        add("2026-03-19", "Gudi Padwa");
        add("2026-03-21", "Eid");
        add("2026-05-27", "Eid");
        add("2026-08-28", "Raksha Bandhan");
        add("2026-09-04", "Janmashtami");
        add("2026-09-14", "Ganesh Chaturthi");
        range("2026-10-11", "2026-10-19", "Navratri");
        add("2026-10-20", "Dussehra");
        add("2026-11-08", "Diwali");
        // 2027
        add("2027-01-15", "Makar Sankranti");
        add("2027-03-10", "Eid");
        add("2027-03-22", "Holi");
        add("2027-04-07", "Gudi Padwa");
        add("2027-05-17", "Eid");
        add("2027-08-17", "Raksha Bandhan");
        add("2027-08-25", "Janmashtami");
        add("2027-09-04", "Ganesh Chaturthi");
        range("2027-09-30", "2027-10-08", "Navratri");
        add("2027-10-09", "Dussehra");
        add("2027-10-29", "Diwali");
        // 2028
        add("2028-01-15", "Makar Sankranti");
        add("2028-02-27", "Eid");
        add("2028-03-11", "Holi");
        add("2028-03-27", "Gudi Padwa");
        add("2028-05-05", "Eid");
        add("2028-08-05", "Raksha Bandhan");
        add("2028-08-13", "Janmashtami");
        add("2028-08-23", "Ganesh Chaturthi");
        range("2028-09-19", "2028-09-26", "Navratri");
        add("2028-09-27", "Dussehra");
        add("2028-10-17", "Diwali");
    }

    private IndianFestivals() {
    }

    /** "Happy Diwali", or null on an ordinary day. */
    static String greeting(LocalDate day) {
        String name = DAYS.get(day);
        if (name == null) {
            MonthDay md = MonthDay.from(day);
            if (md.equals(MonthDay.of(1, 1))) return "Happy New Year";
            if (md.equals(MonthDay.of(12, 25))) return "Merry Christmas";
            return null;
        }
        return "Happy " + name;
    }

    private static void add(String day, String name) {
        DAYS.put(LocalDate.parse(day), name);
    }

    private static void range(String from, String to, String name) {
        for (LocalDate d = LocalDate.parse(from); !d.isAfter(LocalDate.parse(to)); d = d.plusDays(1)) DAYS.put(d, name);
    }
}
