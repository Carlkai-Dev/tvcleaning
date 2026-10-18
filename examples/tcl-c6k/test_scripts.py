"""Offline safety checks: python3 -m unittest discover -s examples/tcl-c6k -p 'test_*.py'."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
FAKE_ADB = r'''
import json, os, pathlib, sys
state_path = pathlib.Path(os.environ['FAKE_STATE'])
state = json.loads(state_path.read_text())
command = sys.argv[-1]
state['commands'].append(command)
scenario = os.environ['FAKE_SCENARIO']
result = ''
if command == 'getprop ro.product.device':
    result = 'G10' if scenario == 'wrong-device' else 'G08'
elif command == 'getprop ro.build.version.sdk':
    result = '33' if scenario == 'wrong-sdk' else '34'
elif command == 'am get-current-user':
    result = '10' if scenario == 'wrong-user' else '0'
elif command.startswith('pm path '):
    result = '' if scenario == 'missing-launcher' and command.endswith('com.gathin.tvhome') else 'package:/data/app/base.apk'
elif command.startswith('am start -W '):
    result = 'Error: Activity not found' if scenario == 'preview-fail' else 'Status: ok'
    state['foreground'] = 'com.gathin.tvhome/.MainActivity'
elif command.startswith('pm disable-user '):
    package = command.split()[-1]
    if scenario == 'disable-error':
        result = 'Exception: permission denied'  # Android shell process may still exit 0.
    else:
        state['disabled'].append(package)
        result = 'Package ' + package + ' new state: disabled-user'
elif command.startswith('pm enable '):
    package = command.split()[-1]
    if scenario == 'restore-setup-fail' and package.endswith('setupwraith'):
        result = 'Error: setup enable failed'
    else:
        result = 'Package ' + package + ' new state: enabled'
elif command.startswith('cmd package set-home-activity '):
    state['home'] = command.split()[-1]
    result = 'Success'
elif command.startswith('cmd package resolve-activity '):
    result = state['home']
    if scenario == 'home-fail' and result.startswith('com.gathin.tvhome'):
        result = 'com.google.android.tungsten.setupwraith/.RecoveryActivity'
elif command == 'input keyevent KEYCODE_HOME':
    state['foreground'] = state['home']
elif command == 'dumpsys activity activities':
    result = 'topResumedActivity=ActivityRecord{ ' + state['foreground'] + ' }'
state_path.write_text(json.dumps(state))
print(result)
'''


class ScriptSafetyTests(unittest.TestCase):
    def run_script(self, scenario, script='activate.sh'):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            fake = directory / 'python-adb'
            fake.write_text(f'#!{sys.executable}\n' + FAKE_ADB)
            fake.chmod(0o755)
            # The tests exercise control flow, not real-time Android startup.
            sleep = directory / 'sleep'
            sleep.write_text('#!/bin/sh\nexit 0\n')
            sleep.chmod(0o755)
            state_path = directory / 'state.json'
            state_path.write_text(json.dumps({
                'commands': [], 'disabled': [],
                'home': 'com.google.android.apps.tv.launcherx/.home.HomeActivity',
                'foreground': 'com.google.android.apps.tv.launcherx/.home.HomeActivity',
            }))
            env = dict(os.environ, TV_HOST='offline-test', TV_PYTHON=str(fake),
                       FAKE_STATE=str(state_path), FAKE_SCENARIO=scenario,
                       PATH=str(directory) + os.pathsep + os.environ['PATH'])
            result = subprocess.run(['bash', str(ROOT / 'examples/tcl-c6k' / script)],
                                    cwd=ROOT, env=env, text=True, capture_output=True)
            return result, json.loads(state_path.read_text())

    def test_preflight_and_preview_failures_never_disable_packages(self):
        for scenario in ('wrong-device', 'wrong-sdk', 'wrong-user', 'missing-launcher', 'preview-fail'):
            with self.subTest(scenario=scenario):
                result, state = self.run_script(scenario)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(any(c.startswith('pm disable') for c in state['commands']))

    def test_success_only_disables_the_two_documented_packages(self):
        result, state = self.run_script('success')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(state['disabled'], ['com.google.android.tungsten.setupwraith',
                                            'com.google.android.apps.tv.launcherx'])
        self.assertEqual(state['foreground'], 'com.gathin.tvhome/.MainActivity')

    def test_android_error_text_triggers_recovery_despite_zero_exit_status(self):
        result, state = self.run_script('disable-error')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('pm enable --user 0 com.google.android.apps.tv.launcherx', state['commands'])
        self.assertIn('pm enable --user 0 com.google.android.tungsten.setupwraith', state['commands'])
        self.assertEqual(state['foreground'], 'com.google.android.apps.tv.launcherx/.home.HomeActivity')

    def test_wrong_home_resolution_rolls_back(self):
        result, state = self.run_script('home-fail')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(state['home'], 'com.google.android.apps.tv.launcherx/.home.HomeActivity')

    def test_setup_restore_failure_does_not_prevent_home_recovery(self):
        result, state = self.run_script('restore-setup-fail', 'restore.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(state['foreground'], 'com.google.android.apps.tv.launcherx/.home.HomeActivity')
        self.assertIn('cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity', state['commands'])


if __name__ == '__main__':
    unittest.main()
