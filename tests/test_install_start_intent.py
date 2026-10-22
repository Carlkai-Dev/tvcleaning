"""Every place that starts the launcher starts it as a home launch, not as a bare component (#888).

`am start -W -n com.gathin.tvhome/.MainActivity` comes from the shell, so ActivityManager gives the
activity a task of type `standard`. The first Home press after that starts a home task of its own - a
home launch cannot reuse a standard task - and two MainActivity instances stay alive in one process:
the real Home, and a hidden copy that keeps the receivers its onCreate registered. Reported on a TCL
Smart TV Pro G09 (Google TV 12, STT2.230203.001.AS24) with the two Task/ActivityRecord blocks in the
issue; giving the same explicit component the MAIN action and the HOME category keeps -n and -W and
leaves one task of type `home`.

Both halves are pinned here: the command the install script really sends to a fake TV (and that its
"Status: ok" gate still stops the handover), and every shell line in the repo that starts this
launcher, so a device recipe copied later cannot bring the second task back. The scan is tested on
synthetic lines too, so it cannot pass by finding nothing.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
STOCK_HOME = "com.google.android.tvlauncher/.MainActivity"
NEW_HOME = "com.gathin.tvhome/.MainActivity"
MAIN_ACTION = "android.intent.action.MAIN"
HOME_CATEGORY = "android.intent.category.HOME"

# The launcher appears under two component names: its own package, and the Coocaa-friendly package of
# examples/skyworth-7s60-a5 (same Java namespace, different applicationId).
LAUNCHER_ACTIVITIES = ("com.gathin.tvhome/.MainActivity", "com.gathin.tvhome.MainActivity")
START_SUFFIXES = {".sh", ".ps1", ".md", ".bat"}
SKIP_DIRS = {".git", "venv", "node_modules", "build", ".gradle", "tests",
             "__pycache__", "site-packages"}

# Stands in for tools/tv.py: answers the Home query, the SDK level and the batch, and keeps every
# command the script sent so a test can read the start line the TV was given.
FAKE_TV = r'''
import json
import os
import sys


STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


state = load()


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


def run(command):
    if "getprop ro.build.version.sdk" in command:
        return state["sdk"] + "\n"
    if "pm install" in command:
        return state["install_output"]
    if "am start" in command:
        return state["start_output"]
    if "set-home-activity" in command:
        state["home"] = NEW
        return ""
    if "resolve-activity" in command:
        return state["home"] + "\n"
    return ""


def main():
    args = [a for a in sys.argv[1:] if a != "-I"]
    if any(a.endswith("b64push.py") for a in args):
        print("pushed")
        return 0
    if "--batch" in args:
        commands = [line.strip() for line in sys.stdin
                    if line.strip() and not line.strip().startswith("#")]
    elif args:
        commands = [args[-1]]
    else:
        commands = []
    for command in commands:
        state["commands"].append(command)
        sys.stdout.write(run(command))
        sys.stdout.flush()
    save()
    return 0


sys.exit(main())
'''.replace("NEW", repr(NEW_HOME))


def launcher_start(line):
    """True when a shell line starts this launcher, i.e. when it needs the HOME intent."""
    if "am start" not in line:
        return False
    if not any(activity in line for activity in LAUNCHER_ACTIVITIES):
        return False
    # A comment can quote the command to explain it; it is not run.
    return not line.lstrip().startswith("#")


def has_home_intent(line):
    """The two flags #888 turned into the requirement: a home launch needs an action and a category."""
    return ("-a " + MAIN_ACTION) in line and ("-c " + HOME_CATEGORY) in line


def start_lines(root=ROOT):
    """Every (file, line number, line) in the repo that starts the launcher, docs and scripts alike."""
    found = []
    for path in sorted(Path(root).rglob("*")):
        if not path.is_file() or path.suffix.lower() not in START_SUFFIXES:
            continue
        if SKIP_DIRS & set(path.relative_to(root).parts):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for number, line in enumerate(text.split("\n"), 1):
            if launcher_start(line):
                found.append((path.relative_to(root).as_posix(), number, line.strip()))
    return found


class StartCommandTest(unittest.TestCase):
    """The install script, run against a fake TV: what reaches the TV, and what still stops it."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.launcher = self.base / "launcher"
        (self.launcher / "app").mkdir(parents=True)
        shutil.copy2(ROOT / "launcher" / "build-and-install.sh", self.launcher)
        (self.launcher / "app" / "build.gradle").write_text(
            "android {\n    defaultConfig {\n        minSdk 26\n    }\n}\n")
        # The build only has to leave an APK behind: no JDK or Android SDK is involved here.
        gradlew = self.launcher / "gradlew"
        gradlew.write_text('#!/bin/sh\nmkdir -p app/build/outputs/apk/release\n'
                           'printf apk > app/build/outputs/apk/release/app-release.apk\n')
        gradlew.chmod(0o755)
        tools = self.base / "tools"
        (tools / "venv" / "bin").mkdir(parents=True)
        (tools / "b64push.py").write_text("")
        fake = self.base / "fake_tv.py"
        fake.write_text(FAKE_TV)
        client = tools / "venv" / "bin" / "python"
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "{fake}" "$@"\n')
        client.chmod(0o755)
        self.state_path = self.base / "tv-state.json"
        self.home = self.base / "home"
        self.home.mkdir()

    def install(self, **overrides):
        state = {
            "sdk": "34",
            "home": STOCK_HOME,
            "install_output": "Success\n",
            "start_output": "Status: ok\n",
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))
        env = {k: v for k, v in os.environ.items()
               if k not in ("STOCK_LAUNCHER", "INPUT_KEY_SERVICE", "HOME_KEY_SERVICE")}
        env.update(TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path),
                   HOME=str(self.home), JAVA_HOME=str(self.base / "java"),
                   ANDROID_HOME=str(self.base / "android-sdk"))
        result = subprocess.run(
            ["bash", str(self.launcher / "build-and-install.sh")],
            cwd=self.launcher, env=env, capture_output=True, text=True, timeout=180,
        )
        with open(self.state_path) as f:
            return result, json.load(f)

    def starts(self, state):
        return [c for c in state["commands"] if "am start" in c]

    def test_the_start_command_is_a_home_launch(self):
        result, state = self.install()
        self.assertEqual(result.returncode, 0, result.stderr)
        starts = self.starts(state)
        self.assertEqual(len(starts), 1, starts)
        tokens = shlex.split(starts[0])
        self.assertIn("-W", tokens, "the wait is what makes Status: ok mean anything")
        self.assertEqual(tokens[tokens.index("-a") + 1], MAIN_ACTION)
        self.assertEqual(tokens[tokens.index("-c") + 1], HOME_CATEGORY)
        self.assertEqual(tokens[tokens.index("-n") + 1], NEW_HOME)
        self.assertEqual(starts[0], "am start -W -a %s -c %s -n %s"
                         % (MAIN_ACTION, HOME_CATEGORY, NEW_HOME),
                         "one home task: the action and category are the whole fix, nothing else moved")

    def test_a_failed_start_still_leaves_the_stock_launcher_alone(self):
        # The flags must not weaken the guard #239 added: a start that did not come up has to stop the
        # handover before the stock Home is disabled.
        result, state = self.install(start_output="Error: Activity not started, unknown token\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("did not start", result.stderr)
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]),
                         state["commands"])

    def test_the_home_query_after_the_switch_is_unchanged(self):
        # The guard reads Home back; the start line change must not have touched that path.
        result, state = self.install()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Home now resolves to " + NEW_HOME, result.stdout)
        self.assertTrue(any("set-home-activity" in c for c in state["commands"]))
        self.assertTrue(any("pm disable-user" in c for c in state["commands"]))


class RepoStartLinesTest(unittest.TestCase):
    """Every copyable start of this launcher in the repo carries the HOME intent."""

    def test_no_launcher_start_is_missing_the_home_intent(self):
        offenders = [f"{name}:{number}: {line}"
                     for name, number, line in start_lines() if not has_home_intent(line)]
        self.assertEqual(offenders, [], "these would start a second, standard task (#888)")

    def test_the_scan_looks_at_the_real_install_paths(self):
        # Without this the test above could pass by matching nothing at all.
        found = {name for name, _, _ in start_lines()}
        self.assertGreaterEqual(len(start_lines()), 6, sorted(found))
        for expected in ("launcher/build-and-install.sh",
                         "launcher/build-and-install.ps1",
                         "examples/tcl-43c645/build-and-install.sh",
                         "docs/devices/sony-x9000h-cn.md",
                         "examples/skyworth-7s60-a5/README.md"):
            self.assertIn(expected, found)

    def test_the_comment_that_explains_the_flags_is_still_there(self):
        # The next editor has to be able to see why the two flags matter, or they read as noise.
        script = (ROOT / "launcher" / "build-and-install.sh").read_text(encoding="utf-8")
        self.assertTrue("#888" in script, "the reason for the two flags has to stay next to them")
        self.assertTrue("standard task" in script, "name what a bare component gets instead")


class StartLineCheckerTest(unittest.TestCase):
    """The scan is a few string rules; each one is checked on its own."""

    def test_the_old_command_is_an_offender(self):
        self.assertTrue(launcher_start(
            "START_OUT=$(\"$PY\" -I ../tools/tv.py 'am start -W -n com.gathin.tvhome/.MainActivity')"))
        self.assertFalse(has_home_intent(
            "am start -W -n com.gathin.tvhome/.MainActivity"))

    def test_the_new_command_is_clean(self):
        line = ("   \"$PY\" -I tools/tv.py 'am start -a android.intent.action.MAIN "
                "-c android.intent.category.HOME -n com.coocaa.tvhome/com.gathin.tvhome.MainActivity'")
        self.assertTrue(launcher_start(line))
        self.assertTrue(has_home_intent(line))

    def test_a_restart_after_force_stop_counts(self):
        line = ("am force-stop com.coocaa.tvhome; am start -n "
                "com.coocaa.tvhome/com.gathin.tvhome.MainActivity")
        self.assertTrue(launcher_start(line))
        self.assertFalse(has_home_intent(line))

    def test_the_stock_launcher_is_not_this_launcher(self):
        line = "am start -n com.google.android.tvlauncher/.MainActivity"
        self.assertFalse(launcher_start(line))

    def test_a_comment_that_quotes_the_command_is_not_run(self):
        line = "#   am start -W -n com.gathin.tvhome/.MainActivity   (what this used to be)"
        self.assertFalse(launcher_start(line))

    def test_a_query_that_mentions_the_component_is_not_a_start(self):
        line = "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity"
        self.assertFalse(launcher_start(line))


if __name__ == "__main__":
    unittest.main()
