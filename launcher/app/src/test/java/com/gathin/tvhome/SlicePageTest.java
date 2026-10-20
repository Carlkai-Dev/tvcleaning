package com.gathin.tvhome;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
/** Trees mirror the Realtek menu slice dumps (MainMenu_Picture, MainMenu_Sound, MainMenu_Screen). */
public class SlicePageTest {
    private static final class N implements SlicePage.Node {
        final String format, subType; final List<String> hints; final CharSequence text; final int value; final Object action;
        final List<SlicePage.Node> children;
        N(String format, String subType, List<String> hints, CharSequence text, int value, Object action, SlicePage.Node... kids) {
            this.format = format; this.subType = subType; this.hints = hints; this.text = text; this.value = value;
            this.action = action; this.children = Arrays.asList(kids);
        }
        public String format() { return format; }
        public String subType() { return subType; }
        public List<String> hints() { return hints; }
        public CharSequence text() { return text; }
        public int intValue() { return value; }
        public Object action() { return action; }
        public List<SlicePage.Node> children() { return children; }
    }
    private static List<String> h(String... s) { return Arrays.asList(s); }
    private static N text(String sub, List<String> hints, String t) { return new N("text", sub, hints, t, 0, null); }
    private static N num(String sub, int v) { return new N("int", sub, h(), null, v, null); }
    private static N shortcut(String sub, String label, boolean selected, Object pi) {
        return new N("slice", sub, h("shortcut"), null, 0, null,
                new N("action", "toggle", selected ? h("selected") : h(), null, 0, pi, text(null, h("title"), label)));
    }
    private static N item(String type, String title, String key, List<SlicePage.Node> extra) {
        List<SlicePage.Node> kids = new ArrayList<>();
        kids.add(new N("slice", null, h("title"), null, 0, null, new N("image", null, h(), null, 0, null)));
        kids.add(text(null, h("title"), title));
        if (key != null) kids.add(text("TAG_KEY", h("keywords"), key));
        kids.addAll(extra);
        return new N("slice", type, h("list_item"), null, 0, null, kids.toArray(new SlicePage.Node[0]));
    }
    private static N screenTitle(String t) {
        return new N("slice", "TYPE_PREFERENCE_SCREEN_TITLE", h("list_item"), null, 0, null,
                text(null, h("title"), t), num("SUBTYPE_IS_ENABLED", 1));
    }
    private static N picker(String title, String key, String selected, String... labels) {
        List<SlicePage.Node> extra = new ArrayList<>();
        extra.add(num("SUBTYPE_IS_ENABLED", 1));
        extra.add(text("SUBTYPE_PICKER_SELECTED", h(), selected));
        for (String l : labels) extra.add(shortcut(null, l, l.equals(selected), "pi:" + l));
        return item("TYPE_PICKER_CONTROL", title, key, extra);
    }
    private static N seekbar(String title, String key, int enabled, int min, int max, int value) {
        return item("TYPE_SEEKBAR_CONTROL", title, key, Arrays.asList(num("SUBTYPE_IS_ENABLED", enabled),
                num("SUBTYPE_SEEKBAR_MIN", min), num("SUBTYPE_SEEKBAR_MAX", max), num("SUBTYPE_SEEKBAR_VALUE", value),
                shortcut(null, "", false, "pi:" + key)));
    }
    private static N link(String title, String key, String uri) {
        return item("TYPE_BASIC_CONTROL", title, key, Arrays.asList(
                text("TAG_TARGET_URI", h("actions"), uri), num("SUBTYPE_IS_ENABLED", 1)));
    }
    private static N reset() {
        return item("TYPE_BASIC_CENTERED_CONTROL", "Reset to default", "reset_to_default", Arrays.asList(
                shortcut("SUBTYPE_INTENT", "", false, "pi:reset"), shortcut("SUBTYPE_FOLLOWUP_INTENT", "", false, "pi:follow")));
    }
    private static N root(SlicePage.Node... kids) {
        List<SlicePage.Node> all = new ArrayList<>();
        all.add(new N("long", "millis", h("ttl"), null, 0, null));
        all.addAll(Arrays.asList(kids));
        return new N("slice", null, h(), null, 0, null, all.toArray(new SlicePage.Node[0]));
    }
    private static N pictureRoot() {
        return root(screenTitle("Display & Picture"), screenTitle("Picture mode"),
                picker("Picture mode", "Pic_Picture_Mode", "Movie",
                        "Standard", "Dynamic", "Movie", "ECO", "Gentle", "Vivid", "Sport", "Game", "Personal"),
                screenTitle("WCG"), picker("WCG", "Pic_Wcg", "Auto", "Standard", "WCG", "Auto"),
                link("Screen brightness", "Pic_Backlight", "content://com.realtek.menuslices/MainMenu_Picture/Pic_Backlight"),
                link("Advanced settings", "Pic_Adv_Settings", "content://com.realtek.menuslices/MainMenu_Picture/Pic_Adv_Settings"),
                reset());
    }
    private static N soundRoot() {
        return root(screenTitle("Sound"), screenTitle("Sound style"),
                picker("Sound style", "Sound_Item_Sound_Style", "Standard", "Standard", "Cinema", "Music", "News", "Personal"),
                seekbar("Balance", "Sound_AdSetting_Actioner_Balance", 1, -15, 15, 0),
                seekbar("Bass", "Sound_Item_Sound_Bass", 0, -20, 20, 0),
                seekbar("Treble", "Sound_Item_Sound_Treble", 0, -20, 20, 3),
                link("Advanced settings", "Sound_Item_Advanced_Settings", "content://x/Sound_Item_Advanced_Settings"),
                reset());
    }
    private static List<String> titles(SlicePage.Page p) {
        List<String> out = new ArrayList<>();
        for (SlicePage.Row r : p.rows) out.add(r.title);
        return out;
    }
    @Test public void picturePageMatchesTheTvsOwnPanel() {
        SlicePage.Page p = SlicePage.parse(pictureRoot());
        assertEquals("Display & Picture", p.title);
        assertEquals(Arrays.asList("Picture mode", "WCG", "Screen brightness", "Advanced settings", "Reset to default"), titles(p));
        SlicePage.Row mode = p.rows.get(0);
        assertEquals(SlicePage.PICKER, mode.kind);
        assertEquals(9, mode.choices.size());
        assertEquals(2, mode.selected);
        assertEquals(2, mode.shown);
        assertEquals("pi:Movie", mode.choices.get(2).action);
        assertEquals("Pic_Picture_Mode", mode.key);
        assertEquals(2, p.rows.get(1).selected);
        assertEquals(SlicePage.LINK, p.rows.get(2).kind);
        assertEquals("content://com.realtek.menuslices/MainMenu_Picture/Pic_Backlight", p.rows.get(2).target);
        assertEquals(SlicePage.CENTERED, p.rows.get(4).kind);
        assertEquals("pi:reset", p.rows.get(4).action);
    }
    @Test public void soundPageHasSeekbarsWithRangeAndEnabledState() {
        SlicePage.Page p = SlicePage.parse(soundRoot());
        assertEquals("Sound", p.title);
        assertEquals(Arrays.asList("Sound style", "Balance", "Bass", "Treble", "Advanced settings", "Reset to default"), titles(p));
        SlicePage.Row balance = p.rows.get(1);
        assertEquals(SlicePage.SEEKBAR, balance.kind);
        assertEquals(-15, balance.min);
        assertEquals(15, balance.max);
        assertEquals(0, balance.value);
        assertTrue(balance.enabled);
        assertEquals("pi:Sound_AdSetting_Actioner_Balance", balance.action);
        assertFalse(p.rows.get(2).enabled);
        assertEquals(3, p.rows.get(3).value);
    }
    @Test public void switchReadsCheckedFromSelectedHint() {
        N sw = item("TYPE_SWITCH_CONTROL", "Overscan", "Dias_Pic_Overscan",
                Arrays.asList(num("SUBTYPE_IS_ENABLED", 0), shortcut(null, "", true, "pi:o")));
        SlicePage.Row r = SlicePage.parse(root(screenTitle("Screen"), sw)).rows.get(0);
        assertEquals(SlicePage.SWITCH, r.kind);
        assertTrue(r.checked);
        assertFalse(r.enabled);
    }
    @Test public void pickerFallsBackToPickerSelectedText() {
        List<SlicePage.Node> extra = Arrays.asList(text("SUBTYPE_PICKER_SELECTED", h(), "B"),
                shortcut(null, "A", false, "a"), shortcut(null, "B", false, "b"));
        SlicePage.Row r = SlicePage.parse(root(item("TYPE_PICKER_CONTROL", "P", "K", extra))).rows.get(0);
        assertEquals(1, r.selected);
    }
    @Test public void unusableRowsAndEmptyPagesAreSkipped() {
        assertNull(SlicePage.parse(null));
        assertNull(SlicePage.parse(root(screenTitle("Only a title"))));
        N emptyPicker = item("TYPE_PICKER_CONTROL", "P", "K", Arrays.asList(shortcut(null, "", false, "x")));
        N badSeek = seekbar("S", "K", 1, 5, 5, 5);
        N untitled = item("TYPE_BASIC_CONTROL", "", "K", Arrays.asList(shortcut(null, "", false, "x")));
        N noAction = item("TYPE_BASIC_CONTROL", "Info", "K", Arrays.asList(num("SUBTYPE_IS_ENABLED", 1)));
        assertNull(SlicePage.parse(root(emptyPicker, badSeek, untitled, noAction)));
    }
    @Test public void stepsClampAtBothEnds() {
        assertEquals(0, SlicePage.nextIndex(0, 9, false));
        assertEquals(8, SlicePage.nextIndex(8, 9, true));
        assertEquals(3, SlicePage.nextIndex(2, 9, true));
        assertEquals(3, SlicePage.nextIndex(3, 0, true));
        assertEquals(15, SlicePage.step(15, -15, 15, true));
        assertEquals(-15, SlicePage.step(-15, -15, 15, false));
        assertEquals(1, SlicePage.step(0, -15, 15, true));
        assertEquals(0.5f, SlicePage.fraction(0, -15, 15), 1e-6);
        assertEquals(0f, SlicePage.fraction(3, 5, 5), 1e-6);
    }
    @Test public void okOnlyMatchesLabelsIgnoringCase() {
        String[] labels = {"Personal"};
        assertTrue(SlicePage.okOnly("Personal", labels));
        assertTrue(SlicePage.okOnly(" personal ", labels));
        assertFalse(SlicePage.okOnly("Movie", labels));
        assertFalse(SlicePage.okOnly(null, labels));
    }
}
