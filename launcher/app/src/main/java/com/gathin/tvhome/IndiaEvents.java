package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Locale;

/**
 * Special-day wallpapers: Maharashtra festivals first, then India-wide days. Fixed Gregorian dates
 * are exact; lunar festivals use year windows for 2025–2027.
 */
final class IndiaEvents {

    static final class Event {
        final String id;
        final String title;
        final int colorTop;
        final int colorMid;
        final int colorBottom;
        final int accent;
        /** {@code true} when this is a Maharashtra-first match. */
        final boolean maharashtra;

        Event(String id, String title, int colorTop, int colorMid, int colorBottom, int accent,
              boolean maharashtra) {
            this.id = id;
            this.title = title;
            this.colorTop = colorTop;
            this.colorMid = colorMid;
            this.colorBottom = colorBottom;
            this.accent = accent;
            this.maharashtra = maharashtra;
        }
    }

    private IndiaEvents() {}

    /** Today's special day (MH first, then India), or null for an ordinary day. */
    static Event forDate(LocalDate day) {
        if (day == null) return null;
        Event mh = maharashtraForDate(day);
        if (mh != null) return mh;
        return indiaForDate(day);
    }

    private static Event maharashtraForDate(LocalDate day) {
        MonthDay md = MonthDay.from(day);
        int y = day.getYear();

        // Maharashtra Day / Maharashtra Din — 1 May
        if (md.equals(MonthDay.of(5, 1))) {
            return mh("mh_day", "Maharashtra Day", 0xFFFF6F00, 0xFFBF360C, 0xFF1A0A00, 0xFFFFD54F);
        }
        // Shivaji Jayanti — 19 Feb (observed)
        if (md.equals(MonthDay.of(2, 19))) {
            return mh("shivaji", "Shivaji Maharaj Jayanti", 0xFF8B0000, 0xFF3E1F00, 0xFF1A0A00, 0xFFFFB300);
        }
        // Bal Gangadhar Tilak Jayanti — 23 Jul
        if (md.equals(MonthDay.of(7, 23))) {
            return mh("tilak", "Lokmanya Tilak Jayanti", 0xFF1B5E20, 0xFF0D3B12, 0xFF001A0A, 0xFFFFD54F);
        }

        if (inWindow(day, lunar(y, "gudi_padwa"))) {
            return mh("gudi_padwa", "Gudi Padwa", 0xFFFF6F00, 0xFFC62828, 0xFF1A0033, 0xFFFFD54F);
        }
        if (inWindow(day, lunar(y, "ashadhi"))) {
            return mh("ashadhi", "Ashadhi Ekadashi", 0xFF0D47A1, 0xFF1565C0, 0xFFFFF8E1, 0xFFFFD700);
        }
        if (inWindow(day, lunar(y, "ganesh"))) {
            return mh("ganesh_mh", "Ganesh Chaturthi", 0xFFFF6F00, 0xFFBF360C, 0xFF1A0A00, 0xFFFFD54F);
        }
        if (inWindow(day, lunar(y, "pola"))) {
            return mh("pola", "Pola", 0xFF5D4037, 0xFF3E2723, 0xFF1B0000, 0xFFFFB74D);
        }
        if (inWindow(day, lunar(y, "narali"))) {
            return mh("narali", "Narali Purnima", 0xFF00695C, 0xFF004D40, 0xFF00251A, 0xFFFFD54F);
        }
        if (inWindow(day, lunar(y, "vat_purnima"))) {
            return mh("vat_purnima", "Vat Purnima", 0xFF2E7D32, 0xFF1B5E20, 0xFF0A1F0A, 0xFFA5D6A7);
        }
        return null;
    }

    private static Event indiaForDate(LocalDate day) {
        MonthDay md = MonthDay.from(day);
        int y = day.getYear();

        if (md.equals(MonthDay.of(1, 26))) {
            return in("republic", "Happy Republic Day", 0xFFFF9933, 0xFF1A1A2E, 0xFF138808, 0xFFFFD700);
        }
        if (md.equals(MonthDay.of(8, 15))) {
            return in("independence", "Happy Independence Day", 0xFFFF9933, 0xFFF5F5F5, 0xFF138808, 0xFFFF9933);
        }
        if (md.equals(MonthDay.of(10, 2))) {
            return in("gandhi", "Gandhi Jayanti", 0xFF2E5A3C, 0xFF1A3328, 0xFF0B1A14, 0xFFC5E1A5);
        }
        if (md.equals(MonthDay.of(11, 14))) {
            return in("children", "Happy Children's Day", 0xFF1565C0, 0xFF0D47A1, 0xFF001A33, 0xFFFFCA28);
        }
        if (md.equals(MonthDay.of(9, 5))) {
            return in("teachers", "Happy Teachers' Day", 0xFF6A1B9A, 0xFF4A148C, 0xFF1A0033, 0xFFFFD54F);
        }
        if (md.equals(MonthDay.of(1, 1))) {
            return in("newyear", "Happy New Year", 0xFF0D1B2A, 0xFF1B263B, 0xFF415A77, 0xFFFFD700);
        }
        if (md.equals(MonthDay.of(12, 25))) {
            return in("christmas", "Merry Christmas", 0xFF1B5E20, 0xFF0D3B12, 0xFF3E1F1F, 0xFFE53935);
        }

        if (inWindow(day, lunar(y, "holi"))) {
            return in("holi", "Happy Holi", 0xFFE91E63, 0xFFFF9800, 0xFF9C27B0, 0xFFFFEB3B);
        }
        if (inWindow(day, lunar(y, "diwali"))) {
            return in("diwali", "Happy Diwali", 0xFF4A148C, 0xFF1A0A2E, 0xFF3E2723, 0xFFFFB23C);
        }
        if (inWindow(day, lunar(y, "dussehra"))) {
            return in("dussehra", "Happy Dussehra", 0xFFBF360C, 0xFF3E2723, 0xFF1B0000, 0xFFFFB300);
        }
        if (inWindow(day, lunar(y, "rakhi"))) {
            return in("rakhi", "Happy Raksha Bandhan", 0xFFAD1457, 0xFF4A148C, 0xFF1A0033, 0xFFFF80AB);
        }
        // Ganesh already matched in MH first when in window; keep India fallback for other years.
        if (inWindow(day, lunar(y, "ganesh"))) {
            return in("ganesh", "Ganesh Chaturthi", 0xFFFF6F00, 0xFFBF360C, 0xFF1A0A00, 0xFFFFD54F);
        }
        if (inWindow(day, lunar(y, "eid"))) {
            return in("eid", "Eid Mubarak", 0xFF004D40, 0xFF00251A, 0xFF0A1F1A, 0xFFFFD700);
        }
        if (inWindow(day, lunar(y, "navratri"))) {
            return in("navratri", "Happy Navratri", 0xFF6A1B9A, 0xFFC62828, 0xFFFF6F00, 0xFFFFD54F);
        }
        return null;
    }

    /** Soft everyday MH/India-inspired backdrop when no named event matches. */
    static Event everyday() {
        return mh("mh_everyday", "Maharashtra", 0xFF1A1208, 0xFF0B0D12, 0xFF0A1A12, 0xFFFF6F00);
    }

    private static Event mh(String id, String title, int top, int mid, int bottom, int accent) {
        return new Event(id, title, top, mid, bottom, accent, true);
    }

    private static Event in(String id, String title, int top, int mid, int bottom, int accent) {
        return new Event(id, title, top, mid, bottom, accent, false);
    }

    private static boolean inWindow(LocalDate day, LocalDate[] window) {
        if (window == null || window.length < 2 || window[0] == null) return false;
        return !day.isBefore(window[0]) && !day.isAfter(window[1]);
    }

    private static LocalDate[] lunar(int year, String name) {
        switch (name + "@" + year) {
            case "gudi_padwa@2025": return range(2025, 3, 29, 3, 31);
            case "gudi_padwa@2026": return range(2026, 3, 18, 3, 20);
            case "gudi_padwa@2027": return range(2027, 4, 6, 4, 8);
            case "vat_purnima@2025": return range(2025, 6, 10, 6, 12);
            case "vat_purnima@2026": return range(2026, 5, 30, 6, 1);
            case "vat_purnima@2027": return range(2027, 6, 18, 6, 20);
            case "ashadhi@2025": return range(2025, 7, 5, 7, 7);
            case "ashadhi@2026": return range(2026, 7, 24, 7, 26);
            case "ashadhi@2027": return range(2027, 7, 13, 7, 15);
            case "narali@2025": return range(2025, 8, 8, 8, 10);
            case "narali@2026": return range(2026, 8, 27, 8, 29);
            case "narali@2027": return range(2027, 8, 16, 8, 18);
            case "pola@2025": return range(2025, 8, 26, 8, 28);
            case "pola@2026": return range(2026, 9, 14, 9, 16);
            case "pola@2027": return range(2027, 9, 3, 9, 5);
            case "holi@2025": return range(2025, 3, 13, 3, 15);
            case "holi@2026": return range(2026, 3, 2, 3, 5);
            case "holi@2027": return range(2027, 3, 21, 3, 24);
            case "rakhi@2025": return range(2025, 8, 8, 8, 10);
            case "rakhi@2026": return range(2026, 8, 27, 8, 29);
            case "rakhi@2027": return range(2027, 8, 16, 8, 18);
            case "ganesh@2025": return range(2025, 8, 26, 9, 6);
            case "ganesh@2026": return range(2026, 9, 14, 9, 24);
            case "ganesh@2027": return range(2027, 9, 4, 9, 14);
            case "navratri@2025": return range(2025, 9, 22, 10, 2);
            case "navratri@2026": return range(2026, 10, 11, 10, 20);
            case "navratri@2027": return range(2027, 10, 1, 10, 10);
            case "dussehra@2025": return range(2025, 10, 1, 10, 3);
            case "dussehra@2026": return range(2026, 10, 19, 10, 21);
            case "dussehra@2027": return range(2027, 10, 8, 10, 11);
            case "diwali@2025": return range(2025, 10, 18, 10, 23);
            case "diwali@2026": return range(2026, 11, 6, 11, 12);
            case "diwali@2027": return range(2027, 10, 26, 11, 1);
            case "eid@2025": return range(2025, 3, 30, 4, 1);
            case "eid@2026": return range(2026, 3, 19, 3, 22);
            case "eid@2027": return range(2027, 3, 9, 3, 11);
            default:
                return null;
        }
    }

    private static LocalDate[] range(int y, int m1, int d1, int m2, int d2) {
        return new LocalDate[]{LocalDate.of(y, m1, d1), LocalDate.of(y, m2, d2)};
    }

    static String modeLabel(String mode, LocalDate today) {
        if ("custom".equals(mode)) return "Custom";
        if ("india".equals(mode)) {
            Event e = forDate(today);
            if (e == null) return "MH then India calendar";
            return e.maharashtra ? ("MH · " + e.title) : e.title;
        }
        return "Default";
    }

    static String normalizeMode(String mode) {
        if (mode == null) return "default";
        String m = mode.trim().toLowerCase(Locale.US);
        if ("custom".equals(m) || "india".equals(m) || "default".equals(m)) return m;
        return "default";
    }
}
