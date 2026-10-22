"""tools/tcl_keys.py: remote buttons handed to TCL's own key settings, without taking system_server down.

TCL Google TV's system_server reads the global settings this tool writes. A CustomKeyList that is not a JSON
list of keycodes crashes it at every boot, and a change to CustomKey_<n> while n is not listed crashes it on
the spot, so the order of the writes is the safety of the tool. The script it sends to the TV runs here
against stand-ins for `settings`, `pm` and `cmd` that journal every write with the list as it stood, and
each test checks the journal against those two rules, plus the shape of every behaviour the firmware
dereferences (an intent each, no package).

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import json
import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("tcl_keys", ROOT / "tools" / "tcl_keys.py")
tcl_keys = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tcl_keys)

FAKE_SETTINGS = r"""#!/bin/sh
[ "$2" = global ] || { echo "not a global setting: $*" >&2; exit 2; }
key=$3
before=$(cat "$FAKE_STORE/CustomKeyList" 2>/dev/null || echo null)
case $1 in
  get) if [ -f "$FAKE_STORE/$key" ]; then cat "$FAKE_STORE/$key"; echo; else echo null; fi ;;
  put) printf '%s' "$4" > "$FAKE_STORE/$key"; printf 'put\t%s\t%s\t%s\n' "$key" "$before" "$4" >> "$FAKE_JOURNAL" ;;
  delete) rm -f "$FAKE_STORE/$key"; printf 'delete\t%s\t%s\t\n' "$key" "$before" >> "$FAKE_JOURNAL" ;;
esac
"""
FAKE_PM = """#!/bin/sh
echo feature:android.software.leanback
[ "$FAKE_TCL" = 1 ] && echo feature:com.tcl.software.tinput_shortcut_config
exit 0
"""
FAKE_CMD = """#!/bin/sh
if [ "$FAKE_INSTALLED" = 1 ]; then
  echo 'priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false'
  echo "$5"
else
  echo 'No activity found'
fi
"""
LIST = re.compile(r'\[("[0-9]+")(,"[0-9]+")*\]')
FOREIGN = '{"keycode":4149,"behavior":[{"keyaction":1,"type":1,"intent":"#Intent;component=com.tcl.x/.Y;end"}]}'


@unittest.skipIf(shutil.which("sh") is None, "needs a POSIX sh")
class ScriptOnTheTvTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        base = Path(tmp.name)
        self.bin = base / "bin"
        self.store = base / "store"
        self.journal_path = base / "journal"
        self.bin.mkdir()
        self.store.mkdir()
        for name, text in (("settings", FAKE_SETTINGS), ("pm", FAKE_PM), ("cmd", FAKE_CMD), ("sleep", "#!/bin/sh\n")):
            path = self.bin / name
            path.write_text(text, newline="\n")
            path.chmod(0o755)

    def run_tv(self, codes, tcl=True, installed=True):
        self.journal_path.write_text("")
        env = dict(os.environ, FAKE_STORE=str(self.store), FAKE_JOURNAL=str(self.journal_path),
                   FAKE_TCL="1" if tcl else "0", FAKE_INSTALLED="1" if installed else "0",
                   PATH=str(self.bin) + os.pathsep + os.environ.get("PATH", ""))
        result = subprocess.run(["sh", "-c", tcl_keys.script(tcl_keys.keycodes(codes))], env=env,
                                capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        journal = [line.split("\t") for line in self.journal_path.read_text().splitlines()]
        self.check_safe(journal)
        return result.stdout, journal

    def setting(self, key):
        path = self.store / key
        return path.read_text() if path.exists() else None

    def check_safe(self, journal):
        for op, key, before, value in journal:
            if key == "CustomKeyList":
                if op == "put":
                    self.assertRegex(value, LIST, "the list system_server parses at every boot")
                continue
            keycode = key[len("CustomKey_"):]
            listed = json.loads(before) if before != "null" else []
            self.assertIn(keycode, listed, f"{op} {key} while it is not in CustomKeyList crashes system_server")
            if op == "put":
                self.check_behaviours(int(keycode), json.loads(value))

    def check_behaviours(self, keycode, value):
        self.assertEqual(value["keycode"], keycode)
        actions = {b["keyaction"]: b for b in value["behavior"]}
        self.assertEqual(sorted(actions), [1, 2, 3], "down, long press and release, so TCL's own handling sees none")
        for b in value["behavior"]:
            self.assertTrue(b["intent"], "the firmware dereferences every behaviour's intent")
            self.assertNotIn("package=", b["intent"], "a package would also need an appnotinstall entry")
        self.assertEqual(actions[1]["type"], 1)
        self.assertIn(f"component={tcl_keys.ACTIVITY};", actions[1]["intent"])
        self.assertIn(f";i.keycode={keycode};", actions[1]["intent"])
        self.assertEqual((actions[2]["type"], actions[3]["type"]), (0, 0), "long press and release do nothing")

    def test_hands_the_buttons_over(self):
        out, _ = self.run_tv(["4062", "4065", "83"])
        self.assertEqual(self.setting("CustomKeyList"), '["4062","4065","83"]')
        for code in (4062, 4065, 83):
            self.check_behaviours(code, json.loads(self.setting(f"CustomKey_{code}")))
        self.assertIn("tcl-keys: done", out)

    def test_a_smaller_set_drops_the_rest_before_the_list_shrinks(self):
        self.run_tv(["4062", "4065", "83"])
        _, journal = self.run_tv(["4062"])
        self.assertEqual(self.setting("CustomKeyList"), '["4062"]')
        self.assertIsNone(self.setting("CustomKey_4065"))
        self.assertIsNone(self.setting("CustomKey_83"))
        self.assertEqual([(op, key) for op, key, _, _ in journal][:2],
                         [("delete", "CustomKey_4065"), ("delete", "CustomKey_83")])

    def test_entries_other_apps_wrote_stay(self):
        (self.store / "CustomKeyList").write_text('["4149"]')
        (self.store / "CustomKey_4149").write_text(FOREIGN)
        self.run_tv(["4062"])
        self.assertEqual(self.setting("CustomKeyList"), '["4149","4062"]')
        self.run_tv([])
        self.assertEqual(self.setting("CustomKeyList"), '["4149"]')
        self.assertEqual(self.setting("CustomKey_4149"), FOREIGN)
        self.assertIsNone(self.setting("CustomKey_4062"))

    def test_off_takes_everything_back(self):
        self.run_tv(["4062", "83"])
        out, _ = self.run_tv([])
        self.assertIsNone(self.setting("CustomKeyList"))
        self.assertEqual(sorted(p.name for p in self.store.iterdir()), [])
        self.assertIn("CustomKeyList is now null", out)

    def test_other_tvs_are_left_alone(self):
        out, journal = self.run_tv(["4062"], tcl=False)
        self.assertEqual(journal, [])
        self.assertIn("no TCL key settings", out)
        self.assertNotIn("tcl-keys: done", out)

    def test_no_launcher_no_dead_buttons(self):
        out, journal = self.run_tv(["4062"], installed=False)
        self.assertEqual(journal, [])
        self.assertIn("install the launcher first", out)


class KeycodesTest(unittest.TestCase):
    def test_vendor_and_settings_buttons_pass(self):
        self.assertEqual(tcl_keys.keycodes(["4062", "83", "176", "4062", "289"]), [4062, 83, 176, 289])

    def test_keys_the_tv_must_keep_are_refused(self):
        # Home, Back, D-pad, OK, volume, power, mute, media play/pause, the Input button, a typo, a negative.
        for arg in ("3", "4", "19", "23", "24", "25", "26", "164", "85", "178", "288", "40x", "", "-1", "1234567"):
            with self.subTest(arg=arg), self.assertRaises(ValueError):
                tcl_keys.keycodes([arg])

    def test_the_script_fits_one_adb_command(self):
        codes = list(range(4000, 4100))
        self.assertLess(len(tcl_keys.script(codes).encode()), tcl_keys.MAX_SCRIPT)

    def test_usage_errors_reach_nothing(self):
        for argv in ([], ["--on"], ["4062", "--off"], ["24"]):
            with self.subTest(argv=argv):
                with contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(tcl_keys.main(argv), 2)


class LauncherSideTest(unittest.TestCase):
    """The keycode the TV passes, the activity it starts and the check Home makes must name the same things."""

    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_the_activity_the_tool_names_is_declared_and_reads_the_keycode(self):
        manifest = self.read("launcher/app/src/main/AndroidManifest.xml")
        block = manifest[manifest.index('android:name=".RemoteKeyActivity"'):]
        block = block[:block.index("/>")]
        self.assertIn('android:exported="false"', block)
        self.assertIn("Theme.NoDisplay", block)
        self.assertEqual(tcl_keys.ACTIVITY, "com.gathin.tvhome/.RemoteKeyActivity")
        self.assertIn("getOrElse('com.gathin.tvhome')", self.read("launcher/app/build.gradle"),
                      "the package tools/ assumes, as theme.sh does")
        activity = self.read("launcher/app/src/main/java/com/gathin/tvhome/RemoteKeyActivity.java")
        self.assertIn('getIntExtra("keycode", 0)', activity)
        self.assertIn("finish();", activity, "Theme.NoDisplay must finish before onResume")

    def test_home_leaves_a_settings_press_the_tv_answers(self):
        main = self.read("launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java")
        keys = self.read("launcher/app/src/main/java/com/gathin/tvhome/RemoteKeys.java")
        self.assertIn("RemoteKeys.answeredByTv(this, code)", main)
        self.assertIn('"CustomKeyList"', keys)
        self.assertIn('"CustomKey_" + keyCode', keys)
        self.assertIn('"/.RemoteKeyActivity"', keys)

    def test_docs_and_twins(self):
        self.assertTrue((ROOT / "tools" / "tcl-keys.ps1").is_file(), "every script has a PowerShell twin")
        self.assertIn("tools/tcl-keys.sh", self.read("README.md"))
        g09 = self.read("examples/tcl-g09-google-tv/README.md")
        self.assertIn("tools/tcl-keys.sh 4062 4065 4099 83", g09)
        self.assertIn("tools/tcl-keys.sh --off", g09)
        self.assertIn("tcl-keys.sh", self.read("docs/TROUBLESHOOTING.md"))


if __name__ == "__main__":
    unittest.main()
