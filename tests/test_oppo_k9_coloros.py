"""OPPO K9 55 (OPPOCNM632) ColorOS notes stay true of this tree.

ColorOS boot only promotes a HOME that declares com.heytap.tv.launcher.category.MODERN_HOME, and a
HOME install is accepted only for three package names. The working identity on the tested set is
com.oneplus.tv.launcher2. com.heytap.tv.launcher is the stock home: the list does not disable it,
and build-and-install.sh does not treat it as a launcher it may turn off.

Run from the repository root: python3 -m unittest tests.test_oppo_k9_coloros -v
"""
import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"
NOTE = ROOT / "docs" / "devices" / "oppo-k9-55-oppocnm632.md"
LIST = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "debloat.txt"
EXTRA = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "debloat-extra.txt"
TV_JSON = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "tv.json"
INSTALL = ROOT / "launcher" / "build-and-install.sh"
TROUBLESHOOTING = ROOT / "docs" / "TROUBLESHOOTING.md"

MODERN_HOME = "com.heytap.tv.launcher.category.MODERN_HOME"
HOME = "android.intent.category.HOME"
STOCK = "com.heytap.tv.launcher"
ALLOWLIST = (STOCK, "com.oneplus.tv.launcher2", "com.oppo.tv.launcher2")
FILTER = re.compile(r"<intent-filter\b[^>]*>(.*?)</intent-filter>", re.S)
CATEGORY = re.compile(r'<category\s+android:name="([^"]+)"')


def active_packages(path):
    packages = []
    for line in path.read_text(encoding="utf-8").splitlines():
        body = line.split("#", 1)[0].strip()
        if body:
            packages.append(body)
    return packages


class OppoK9ColorOsTest(unittest.TestCase):
    def test_home_filter_declares_modern_home(self):
        blocks = FILTER.findall(MANIFEST.read_text(encoding="utf-8"))
        homes = [CATEGORY.findall(block) for block in blocks if HOME in CATEGORY.findall(block)]
        self.assertTrue(homes, "MainActivity's HOME intent-filter was not found")
        self.assertIn(MODERN_HOME, homes[0])

    def test_the_note_names_the_allowlist_and_the_build(self):
        text = NOTE.read_text(encoding="utf-8")
        for package in ALLOWLIST:
            self.assertIn(package, text)
        self.assertIn("-PlauncherApplicationId=com.oneplus.tv.launcher2", text)
        self.assertIn(MODERN_HOME, text)
        self.assertIn("examples/oppo-k9-55-oppocnm632/debloat.txt", text)
        self.assertIn("do not build over it", text.lower())

    def test_the_stock_home_is_not_disabled(self):
        self.assertNotIn(STOCK, active_packages(LIST))
        self.assertNotIn(STOCK, active_packages(EXTRA))
        self.assertNotIn(STOCK, INSTALL.read_text(encoding="utf-8").split("KNOWN_STOCK_LAUNCHERS=", 1)[1].split("\n", 1)[0])

    def test_the_row_links_the_note(self):
        notes = json.loads(TV_JSON.read_text(encoding="utf-8"))["notes"]
        self.assertIn("../docs/devices/oppo-k9-55-oppocnm632.md", notes)

    def test_troubleshooting_points_at_the_note(self):
        self.assertIn("devices/oppo-k9-55-oppocnm632.md", TROUBLESHOOTING.read_text(encoding="utf-8"))
        self.assertIn(MODERN_HOME, TROUBLESHOOTING.read_text(encoding="utf-8"))
