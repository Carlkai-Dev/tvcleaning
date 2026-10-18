# Quickstart Validation Guide: Android TV Debloat & Custom Launcher

**Feature Branch**: `001-debloat-apple-tv-launcher`  
**Date**: 2026-10-18  
**Feature Spec**: [spec.md](./spec.md)  
**Data Model**: [data-model.md](./data-model.md)  
**Contracts**: [contracts/](./contracts/)

---

## 1. Prerequisites & Environment Setup

### 1.1 Developer Machine Setup
Ensure Python 3, OpenJDK 17, and Android SDK (platforms;android-34, build-tools;34.0.0) are ready:

```bash
# 1. Create Python virtual environment and install ADB tools
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt

# 2. Verify Android SDK & Java 17 availability
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export ANDROID_HOME="/opt/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
java -version
```

### 1.2 TV Device Setup (OnePlus Y1 43")
On the TV:
1. Navigate to **Settings** → **Device Preferences** → **About**.
2. Scroll to **Build** and press **OK** on the remote 7 times until "You are now a developer!" appears.
3. Return to **Device Preferences** → **Developer options**.
4. Enable **USB debugging** (and **Network debugging** if available).
5. Note the TV's IP address under **Settings** → **Network & Internet** (or check router ARP table).

---

## 2. Validation Scenarios

### Scenario 1: Device Discovery & Connectivity Probe
Verify that the host machine can authenticate and read device telemetry from the TV.

```bash
# Locate TV IP and probe device
export TV_HOST="<TV_IP_ADDRESS>"

# Alert user to accept the prompt on TV screen:
# "Allow USB debugging from this computer? -> Check Always Allow -> OK"
tools/venv/bin/python tools/tv.py --probe
```
**Expected Outcome**:  
Prints device properties:
```text
ro.product.model = OnePlus TV
ro.product.device = ...
ro.build.version.release = 11
ro.build.version.sdk = 30
ro.product.manufacturer = OnePlus
```

---

### Scenario 2: Baseline Snapshot Backup
Capture complete device configuration state before any modifications are applied.

```bash
mkdir -p backup
tools/venv/bin/python tools/tv.py 'pm list packages -f -u' > backup/packages_all.txt
tools/venv/bin/python tools/tv.py 'pm list packages -d' > backup/packages_disabled.txt
tools/venv/bin/python tools/tv.py 'getprop' > backup/getprop.txt
tools/venv/bin/python tools/tv.py 'settings list global' > backup/settings_global.txt
tools/venv/bin/python tools/tv.py 'settings list secure' > backup/settings_secure.txt
tools/venv/bin/python tools/tv.py 'settings list system' > backup/settings_system.txt
```
**Expected Outcome**:  
Files in `backup/` contain non-empty inventories of packages and properties.

---

### Scenario 3: Categorized Debloat Execution & Safety Verification
Apply OnePlus Y1 debloat profile while preserving critical TV inputs, remote pairing, and user-confirmed apps.

```bash
# Review debloat list tailored to the OnePlus Y1 (refer to examples/oneplus-tv-dubai/debloat.txt)
# Execute safe disablement (disables for user 0, never uninstalls)
TV_HOST=$TV_HOST tools/disable.sh examples/oneplus-tv-dubai/debloat.txt
```
**Expected Outcome**:  
Command output logs each disabled package and finishes with `pm list packages -d` showing disabled status for telemetry, recommendation services, and unneeded OnePlus background services. HDMI inputs, remote control, and keyboard remain 100% operational.

---

### Scenario 4: Building & Deploying the Apple TV Custom Launcher
Compile the lightweight native launcher APK, transfer via base64 pipeline, install, and set as default home.

```bash
# 1. Ensure dock apps in launcher/app/src/main/res/values/config.xml match user preference
# 2. Compile and sideload launcher
TV_HOST=$TV_HOST launcher/build-and-install.sh
```
**Expected Outcome**:  
- Gradle compiles `app-debug.apk` without errors.
- `b64push.py` transfers the APK to `/data/local/tmp/tvhome.apk` with verified MD5 hash.
- APK installs cleanly, home activity is set to `com.gathin.tvhome/.MainActivity`, and the stock OEM launcher is disabled.

---

### Scenario 5: Visual Verification via Remote Key Injection & Screenshots
Inject navigation keys and pull real-device screenshots to inspect UI quality.

```bash
mkdir -p screenshots

# Press Home
tools/venv/bin/python tools/tv.py 'input keyevent 3'

# Capture baseline home screen
tools/venv/bin/python tools/tv.py 'screencap -p /sdcard/home.png'
tools/venv/bin/python tools/tv.py --pull /sdcard/home.png screenshots/01_home.png

# Navigate D-pad Down to scroll into grid
tools/venv/bin/python tools/tv.py 'input keyevent 20'
tools/venv/bin/python tools/tv.py 'screencap -p /sdcard/grid.png'
tools/venv/bin/python tools/tv.py --pull /sdcard/grid.png screenshots/02_grid.png

# Navigate Up to Top Bar and open Inputs
tools/venv/bin/python tools/tv.py 'input keyevent 19'
tools/venv/bin/python tools/tv.py 'input keyevent 19'
tools/venv/bin/python tools/tv.py 'input keyevent 23'
tools/venv/bin/python tools/tv.py 'screencap -p /sdcard/inputs.png'
tools/venv/bin/python tools/tv.py --pull /sdcard/inputs.png screenshots/03_inputs.png
```
**Expected Outcome**:  
Screenshots confirm:
1. `01_home.png`: Clean pitch-black screen, bottom dock band with 4 apps, focused app title with ambient glow, top status bar with clock, date, and frosted pills.
2. `02_grid.png`: 4-column application grid scrolled into view with translucent glass spring focus.
3. `03_inputs.png`: Inputs dropdown displaying HDMI 1, HDMI 2, HDMI 3, AV, and Live TV.

---

### Scenario 6: Full Device Rollback Verification
Verify that running the restore script re-enables all packages.

```bash
# Run restore script
TV_HOST=$TV_HOST tools/restore.sh examples/oneplus-tv-dubai/debloat.txt
```
**Expected Outcome**:  
All packages previously disabled are enabled via `pm enable <pkg>`. Device returns to baseline state.
