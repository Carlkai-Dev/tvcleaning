#!/usr/bin/env python3
"""Host-side release updater. See update-launcher.md. Default: report latest, no TV changes."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.request

REPO = "https://github.com/Gathin23/cleanyourtv.git"
API = "https://api.github.com/repos/Gathin23/cleanyourtv/releases/latest"
PACKAGE = "com.gathin.tvhome"
TOOLS = Path(__file__).resolve().parent


def run(args, **kwargs):
    return subprocess.check_output([str(a) for a in args], text=True, **kwargs).strip()


def release_tag(value):
    if not re.fullmatch(r"v\d+\.\d+\.\d+", value):
        raise ValueError("expected a stable release tag such as v2.7.0")
    return value


def latest():
    request = urllib.request.Request(API, headers={"User-Agent": "cleanyourtv-host-updater", "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.load(response)
    if data.get("draft") or data.get("prerelease"):
        raise ValueError("not a stable published release")
    return release_tag(data["tag_name"])


def apk_info(aapt, apk):
    badging = run([aapt, "dump", "badging", apk])
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", badging, re.M)
    sdk = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    if not package or not sdk:
        raise ValueError("cannot inspect APK package/version/minSdk")
    permissions = run([aapt, "dump", "permissions", apk])
    if re.search(r"name='android.permission.INTERNET'", permissions):
        raise ValueError("refusing an APK with INTERNET permission")
    return package[1], int(package[2]), int(sdk[1])


def certificates(apksigner, apk):
    output = run([apksigner, "verify", "--print-certs", apk])
    values = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", output, re.M)
    if not values:
        raise ValueError("cannot verify APK signing certificate")
    return sorted(v.lower() for v in values)


def validate_update(candidate, installed, sdk, candidate_keys, installed_keys):
    if candidate[0] != PACKAGE or installed[0] != PACKAGE:
        raise ValueError("this tool updates only an already installed com.gathin.tvhome")
    if candidate[1] < installed[1]:
        raise ValueError("release is older than the installed version; no downgrade attempted")
    if sdk < candidate[2]:
        raise ValueError("TV Android version is below the APK minSdk")
    if candidate_keys != installed_keys:
        raise ValueError("signing key differs: use the original build key; never uninstall to work around it")


def remote_apk(output):
    paths = [line[8:] for line in output.splitlines() if line.startswith("package:")]
    if len(paths) != 1 or not re.fullmatch(r"/data/app/[A-Za-z0-9_~=/+.-]+\.apk", paths[0]) or ".." in paths[0]:
        raise ValueError("expected one installed base APK; split APK installs are not supported")
    return paths[0]


def install(dev, apk, backup, info, signer, aapt):
    installed_path = remote_apk(dev.shell("pm path " + PACKAGE))
    previous = backup / "previous.apk"
    dev.pull(installed_path, str(previous), transport_timeout_s=300, read_timeout_s=300)
    old = apk_info(aapt, previous)
    sdk = int(dev.shell("getprop ro.build.version.sdk").strip())
    validate_update(info, old, sdk, certificates(signer, apk), certificates(signer, previous))
    # Data and preferences stay in place. Back up external config when it exists; do not rewrite it.
    config = "/sdcard/Android/data/" + PACKAGE + "/files/config.json"
    config_exists = dev.shell("test -f " + config + " && echo yes").strip() == "yes"
    if config_exists:
        dev.pull(config, str(backup / "config.json"))
    before = {
        "accessibility": dev.shell("settings get secure enabled_accessibility_services").strip(),
        "screensaver": dev.shell("settings get secure screensaver_components").strip(),
    }
    (backup / "settings-before.json").write_text(json.dumps(before, indent=2), encoding="utf-8")
    remote = "/data/local/tmp/cleanyourtv-release-update.apk"
    env = dict(os.environ)
    # Existing uploader handles old adbd payload limits and verifies the transfer before renaming it.
    subprocess.run([sys.executable, "-I", str(TOOLS / "b64push.py"), str(apk), remote], env=env, check=True)
    try:
        result = dev.shell("pm install -r " + remote, read_timeout_s=180).strip()
        if "Success" not in result.splitlines():
            raise RuntimeError("install refused; no uninstall/reset attempted: " + result)
    finally:
        dev.shell("rm -f " + remote)
    current = dev.shell("dumpsys package " + PACKAGE)
    match = re.search(r"versionCode=(\d+)", current)
    if not match or int(match[1]) != info[1]:
        raise RuntimeError("installation version could not be verified; backup retained")
    if config_exists:
        dev.pull(config, str(backup / "config-after.json"))
        if (backup / "config.json").read_bytes() != (backup / "config-after.json").read_bytes():
            raise RuntimeError("external configuration changed; review saved copies before continuing")
    after = {
        "accessibility": dev.shell("settings get secure enabled_accessibility_services").strip(),
        "screensaver": dev.shell("settings get secure screensaver_components").strip(),
    }
    if after != before:
        print("Settings changed during install; compare with settings-before.json. No settings were rewritten.")
    print("Installed versionCode", info[1], "without clearing data or changing Home. Backup:", backup)
    print("Press Home and test your physical remote helpers. Close debugging when finished.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", type=release_tag, help="pin a stable release instead of latest")
    parser.add_argument("--install", action="store_true", help="build on this computer, back up and update the existing TV app")
    parser.add_argument("--work-dir", type=Path, help="new, empty output directory (source, APK and private backup)")
    parser.add_argument("--patch", type=Path, action="append", default=[], help="trusted local customization patch; conflicts stop before installation")
    args = parser.parse_args(argv)
    tag = args.release or latest()
    print("Release:", tag)
    if not args.install:
        print("Read-only check. Use --install to build and update. No TV connection was made.")
        return 0
    if not os.environ.get("TV_HOST"):
        parser.error("--install needs TV_HOST")
    if not os.environ.get("JAVA_HOME") or not (os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")):
        parser.error("set JAVA_HOME (JDK 17) and ANDROID_HOME (SDK 34/build-tools 34.0.0)")
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    suffix = ".bat" if os.name == "nt" else ""
    aapt = sdk / "build-tools/34.0.0" / ("aapt.exe" if os.name == "nt" else "aapt")
    signer = sdk / "build-tools/34.0.0" / ("apksigner" + suffix)
    if not aapt.is_file() or not signer.is_file():
        parser.error("Android build-tools 34.0.0 are required")
    workspace = args.work_dir or Path.home() / ".cleanyourtv/updates" / datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    workspace = workspace.resolve()
    workspace.mkdir(parents=True, exist_ok=False, mode=0o700)
    source = workspace / "source"
    subprocess.run(["git", "clone", "--depth", "1", "--branch", tag, "--", REPO, str(source)], check=True)
    for patch in args.patch:
        subprocess.run(["git", "-C", str(source), "apply", "--check", str(patch.resolve())], check=True)
        subprocess.run(["git", "-C", str(source), "apply", str(patch.resolve())], check=True)
    launcher = source / "launcher"
    wrapper = launcher / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)]
    subprocess.run(command + ["--no-daemon", "-p", str(launcher), "testReleaseUnitTest", "lintRelease", "assembleRelease"], check=True)
    apk = launcher / "app/build/outputs/apk/release/app-release.apk"
    info = apk_info(aapt, apk)
    (workspace / "build.json").write_text(json.dumps({"release": tag, "commit": run(["git", "-C", source, "rev-parse", "HEAD"]), "patch_sha256": [hashlib.sha256(p.read_bytes()).hexdigest() for p in args.patch], "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest()}, indent=2), encoding="utf-8")
    backup = workspace / "backup"
    backup.mkdir(mode=0o700)
    sys.path.insert(0, str(TOOLS))
    import tv
    dev = tv.connect()
    try:
        install(dev, apk, backup, info, signer, aapt)
    finally:
        dev.close()
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
