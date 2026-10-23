"""tools/disable.sh must not disable setupwraith while it owns the saved Wi-Fi network.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SETUP = "com.google.android.tungsten.setupwraith"
LIST = f"# My TV\ncom.example.before\n{SETUP}  # wizard\ncom.example.after\n"


class SetupwraithGuardTest(unittest.TestCase):
    def run_disable(self, wifi_owner, extra_env=None, dumpsys=None):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
            client = tools / "venv" / "bin" / "python"
            client.parent.mkdir(parents=True)
            # Fake tv.py client: answers `dumpsys wifi` with the given owner, records --batch input.
            answer = dumpsys or f'echo " cuid=10031 cname={wifi_owner} allowAutojoin=true"'
            client.write_text(
                '#!/bin/sh\n'
                'case "$*" in\n'
                f'  *dumpsys*) {answer} ;;\n'
                # The Home guard asks which app owns Home and which ones answer it.
                '  *resolve-activity*) echo "com.google.android.tvlauncher/.MainActivity" ;;\n'
                '  *query-activities*) echo "  com.google.android.tvlauncher/.MainActivity" ;;\n'
                '  *) cat > "$CAPTURE" ;;\n'
                'esac\n')
            client.chmod(0o755)
            (repo / "debloat.txt").write_text(LIST)
            capture = Path(tmp) / "commands.txt"
            env = dict(os.environ, CAPTURE=str(capture), **(extra_env or {}))
            # The fake tv.py reads its stdin, so an inherited terminal would leave it waiting.
            result = subprocess.run(["bash", str(tools / "disable.sh"), "debloat.txt"], cwd=repo,
                                    env=env, capture_output=True, text=True, stdin=subprocess.DEVNULL)
            self.assertEqual(result.returncode, 0, result.stderr)
            return capture.read_text().splitlines(), result.stderr

    def test_skipped_when_it_owns_wifi(self):
        commands, stderr = self.run_disable(f"{SETUP}:10031")
        self.assertNotIn(f"pm disable-user --user 0 {SETUP}", commands)
        self.assertEqual(commands[:2], ["pm disable-user --user 0 com.example.before",
                                        "pm disable-user --user 0 com.example.after"])
        self.assertIn("Skipping", stderr)

    def test_disabled_when_system_owns_wifi(self):
        commands, _ = self.run_disable("android.uid.system:1000")
        self.assertEqual(commands[:3], ["pm disable-user --user 0 com.example.before",
                                        f"pm disable-user --user 0 {SETUP}",
                                        "pm disable-user --user 0 com.example.after"])

    def test_skipped_when_dumpsys_fails(self):
        # for example tv.py stopping in the middle (a console encoding error, a timeout)
        commands, stderr = self.run_disable(None, dumpsys='echo "partial output"; exit 1')
        self.assertNotIn(f"pm disable-user --user 0 {SETUP}", commands)
        self.assertIn("could not check", stderr)

    def test_skipped_when_no_network_listed(self):
        commands, stderr = self.run_disable(None, dumpsys='echo "Wi-Fi is off"')
        self.assertNotIn(f"pm disable-user --user 0 {SETUP}", commands)
        self.assertIn("could not check", stderr)

    # Two saved networks, the TV on "Home" (Android starts its line with "* "): only its owner decides.
    @staticmethod
    def two_networks(home_owner, other_owner, ssid='"Home"'):
        return ("printf '%s\\n' "
                f"'mWifiInfo SSID: {ssid}, BSSID: 02:00:00:00:00:00, Supplicant state: COMPLETED' "
                "'WifiConfigManager - Configured networks Begin ----' "
                "'ID: 0 SSID: \"Neighbour\" PROVIDER-NAME: null' "
                f"' cuid=10031 cname={other_owner} allowAutojoin=true' "
                "'* ID: 1 SSID: \"Home\" PROVIDER-NAME: null' "
                f"' cuid=1000 cname={home_owner} allowAutojoin=true' "
                "'WifiConfigManager - Configured networks End ----' "
                f"' cuid=10031 cname={other_owner} (network history)'")

    def test_disabled_when_only_another_saved_network_is_its(self):
        dump = self.two_networks("android.uid.system:1000", f"{SETUP}:10031")
        commands, _ = self.run_disable(None, dumpsys=dump)
        self.assertIn(f"pm disable-user --user 0 {SETUP}", commands)

    def test_skipped_when_the_network_in_use_is_its(self):
        dump = self.two_networks(f"{SETUP}:10031", "android.uid.system:1000")
        commands, stderr = self.run_disable(None, dumpsys=dump)
        self.assertNotIn(f"pm disable-user --user 0 {SETUP}", commands)
        self.assertIn("Skipping", stderr)

    def test_every_network_counts_when_not_on_wifi(self):
        # "<unknown ssid>" (no quotes) is what an Ethernet or disconnected TV reports
        dump = self.two_networks("android.uid.system:1000", f"{SETUP}:10031", ssid="<unknown ssid>")
        commands, stderr = self.run_disable(None, dumpsys=dump)
        self.assertNotIn(f"pm disable-user --user 0 {SETUP}", commands)
        self.assertIn("Skipping", stderr)

    def test_force(self):
        commands, _ = self.run_disable(f"{SETUP}:10031", {"FORCE_SETUPWRAITH": "1"})
        self.assertIn(f"pm disable-user --user 0 {SETUP}", commands)


if __name__ == "__main__":
    unittest.main()
