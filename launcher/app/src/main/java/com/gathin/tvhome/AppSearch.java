package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local matching prototype: display names only; no app discovery, persistence or UI mutation. */
final class AppSearch {
    private AppSearch() { }

    /** Build once when labels load, using the activity's current resource locale. Never change display text. */
    static String key(String text, Locale locale) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text.toLowerCase(locale), Normalizer.Form.NFD)
                .replace("ß", "ss").replace('ς', 'σ');
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < decomposed.length();) {
            int c = decomposed.codePointAt(i);
            i += Character.charCount(c);
            int type = Character.getType(c);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) continue;
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                space = out.length() > 0;
            } else {
                if (space) out.append(' ');
                out.appendCodePoint(c);
                space = false;
            }
        }
        return out.toString();
    }

    /** Indices into the existing sorted catalogue; every query word must occur, and order stays unchanged. */
    static List<Integer> indices(List<String> labelKeys, String query, Locale locale) {
        String normalized = key(query, locale);
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split(" ");
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < labelKeys.size(); i++) {
            boolean match = true;
            for (String word : words) {
                if (!labelKeys.get(i).contains(word)) { match = false; break; }
            }
            if (match) result.add(i);
        }
        return result;
    }

    /** Keep selection by stable package identity after filtering/refresh; otherwise retain a nearby position. */
    static int focusIndex(List<String> packages, String selected, int previous) {
        if (packages.isEmpty()) return -1;
        int found = packages.indexOf(selected);
        return found >= 0 ? found : Math.max(0, Math.min(previous, packages.size() - 1));
    }
}
