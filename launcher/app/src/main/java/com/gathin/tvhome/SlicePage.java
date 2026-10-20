package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of a TV menu slice (Realtek com.realtek.menuslices, TvSettings slice format) as rows for
 * {@link PictureSoundActivity}: pickers ("Picture mode  ‹ Standard ›"), seekbars (Balance, Bass), switches,
 * links to a sub-page (TAG_TARGET_URI) and plain actions (Reset to default). Works on a tiny {@link Node} view
 * of the slice so it runs in plain JVM tests.
 */
final class SlicePage {
    private SlicePage() {
    }

    static final int PICKER = 0;
    static final int SEEKBAR = 1;
    static final int SWITCH = 2;
    static final int LINK = 3;
    static final int ACTION = 4;
    static final int CENTERED = 5;

    /** A slice item: format is "slice", "action", "text", "int" or another SliceItem.FORMAT_* string. */
    interface Node {
        String format();

        String subType();

        List<String> hints();

        CharSequence text();

        /** The value of an int item, else 0. */
        int intValue();

        /** The PendingIntent of an action item, else null. */
        Object action();

        /** The inner slice's items for slice and action items, else empty. */
        List<Node> children();
    }

    static final class Choice {
        final String label;
        final Object action;

        Choice(String label, Object action) {
            this.label = label;
            this.action = action;
        }
    }

    static final class Row {
        final int kind;
        final String title;
        /** TAG_KEY, sent back as extra_preference_key; may be null. */
        final String key;
        final boolean enabled;
        /** PICKER: the choices; selected is the TV's current one (-1 if unknown), shown the one on screen. */
        final List<Choice> choices;
        int selected;
        int shown;
        /** SEEKBAR. */
        final int min;
        final int max;
        int value;
        /** SWITCH. */
        boolean checked;
        /** LINK: the sub-page's slice URI. */
        final String target;
        /** SEEKBAR, SWITCH, ACTION, CENTERED: the PendingIntent to send. */
        final Object action;

        Row(int kind, String title, String key, boolean enabled, List<Choice> choices, int selected,
                int min, int max, int value, boolean checked, String target, Object action) {
            this.kind = kind;
            this.title = title;
            this.key = key;
            this.enabled = enabled;
            this.choices = choices;
            this.selected = selected;
            this.shown = Math.max(0, selected);
            this.min = min;
            this.max = max;
            this.value = value;
            this.checked = checked;
            this.target = target;
            this.action = action;
        }
    }

    static final class Page {
        /** The slice's own page title ("Display & Picture"); may be null. */
        final String title;
        final List<Row> rows;

        Page(String title, List<Row> rows) {
            this.title = title;
            this.rows = rows;
        }
    }

    /** The page's rows, or null if root is null or has no row this panel can show. */
    static Page parse(Node root) {
        if (root == null) return null;
        String pageTitle = null;
        List<Row> rows = new ArrayList<>();
        for (Node item : root.children()) {
            if (!"slice".equals(item.format()) || !item.hints().contains("list_item")) continue;
            String type = item.subType();
            String title = null, key = null, target = null, pickerSelected = null;
            boolean enabled = true;
            int min = 0, max = 0, value = 0;
            Node intent = null;
            List<Node> shortcuts = new ArrayList<>();
            for (Node c : item.children()) {
                String f = c.format();
                String st = c.subType();
                if ("text".equals(f)) {
                    if (st == null && title == null && c.hints().contains("title")) title = str(c.text());
                    else if ("TAG_KEY".equals(st)) key = str(c.text());
                    else if ("TAG_TARGET_URI".equals(st)) target = str(c.text());
                    else if ("SUBTYPE_PICKER_SELECTED".equals(st)) pickerSelected = str(c.text());
                } else if ("int".equals(f)) {
                    if ("SUBTYPE_IS_ENABLED".equals(st)) enabled = c.intValue() != 0;
                    else if ("SUBTYPE_SEEKBAR_MIN".equals(st)) min = c.intValue();
                    else if ("SUBTYPE_SEEKBAR_MAX".equals(st)) max = c.intValue();
                    else if ("SUBTYPE_SEEKBAR_VALUE".equals(st)) value = c.intValue();
                } else if ("slice".equals(f) && c.hints().contains("shortcut")) {
                    Node a = firstAction(c);
                    if (a == null) continue;
                    if ("SUBTYPE_INTENT".equals(st)) intent = a;
                    else if (!"SUBTYPE_FOLLOWUP_INTENT".equals(st)) shortcuts.add(a);
                }
            }
            if ("TYPE_PREFERENCE_SCREEN_TITLE".equals(type)) {
                if (pageTitle == null && !blank(title)) pageTitle = title;
                continue;
            }
            if (blank(title)) continue;
            Row row;
            if ("TYPE_PICKER_CONTROL".equals(type)) {
                List<Choice> choices = new ArrayList<>();
                int selected = -1;
                for (Node a : shortcuts) {
                    String label = titleOf(a);
                    if (blank(label) || a.action() == null) continue;
                    if (selected < 0 && a.hints().contains("selected")) selected = choices.size();
                    choices.add(new Choice(label, a.action()));
                }
                if (choices.isEmpty()) continue;
                if (selected < 0 && pickerSelected != null) {
                    for (int i = 0; i < choices.size(); i++) {
                        if (choices.get(i).label.equals(pickerSelected)) {
                            selected = i;
                            break;
                        }
                    }
                }
                row = new Row(PICKER, title, key, enabled, choices, selected, 0, 0, 0, false, null, null);
            } else if ("TYPE_SEEKBAR_CONTROL".equals(type)) {
                if (shortcuts.isEmpty() || shortcuts.get(0).action() == null || max <= min) continue;
                row = new Row(SEEKBAR, title, key, enabled, null, -1, min, max,
                        Math.max(min, Math.min(max, value)), false, null, shortcuts.get(0).action());
            } else if ("TYPE_SWITCH_CONTROL".equals(type)) {
                if (shortcuts.isEmpty() || shortcuts.get(0).action() == null) continue;
                row = new Row(SWITCH, title, key, enabled, null, -1, 0, 0, 0,
                        shortcuts.get(0).hints().contains("selected"), null, shortcuts.get(0).action());
            } else if (!blank(target)) {
                row = new Row(LINK, title, key, enabled, null, -1, 0, 0, 0, false, target, null);
            } else {
                Node a = intent != null ? intent : shortcuts.isEmpty() ? null : shortcuts.get(0);
                if (a == null || a.action() == null) continue;
                row = new Row("TYPE_BASIC_CENTERED_CONTROL".equals(type) ? CENTERED : ACTION, title, key, enabled,
                        null, -1, 0, 0, 0, false, null, a.action());
            }
            rows.add(row);
        }
        return rows.isEmpty() ? null : new Page(pageTitle, rows);
    }

    /** One row (or choice, or seekbar step) up or down in a list of count, stopping at both ends. */
    static int nextIndex(int current, int count, boolean down) {
        if (count <= 0) return current;
        int next = down ? current + 1 : current - 1;
        return Math.max(0, Math.min(count - 1, next));
    }

    /** A seekbar value one step on, kept within min..max. */
    static int step(int value, int min, int max, boolean up) {
        return Math.max(min, Math.min(max, up ? value + 1 : value - 1));
    }

    /** How far along the bar a seekbar value sits, 0 to 1. */
    static float fraction(int value, int min, int max) {
        if (max <= min) return 0f;
        return Math.max(0f, Math.min(1f, (value - min) / (float) (max - min)));
    }

    /** Whether this choice is applied only on OK (it opens the TV's own screen, e.g. Personal). */
    static boolean okOnly(String label, String[] labels) {
        if (label == null || labels == null) return false;
        for (String l : labels) if (l != null && label.trim().equalsIgnoreCase(l.trim())) return true;
        return false;
    }

    private static Node firstAction(Node slice) {
        for (Node a : slice.children()) if ("action".equals(a.format())) return a;
        return null;
    }

    private static String titleOf(Node action) {
        for (Node t : action.children()) {
            if ("text".equals(t.format()) && t.hints().contains("title")) return str(t.text());
        }
        return null;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String str(CharSequence s) {
        return s == null ? null : s.toString();
    }
}
