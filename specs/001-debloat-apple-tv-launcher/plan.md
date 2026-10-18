# Implementation Plan: Android TV Debloat & Apple TV Style Custom Launcher

**Branch**: `001-debloat-apple-tv-launcher` | **Date**: 2026-10-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from [`specs/001-debloat-apple-tv-launcher/spec.md`](./spec.md)

---

## Summary

The objective of this feature is to safely connect to a OnePlus Y1 43" Android TV (running Android 11), capture a baseline state backup, eliminate OEM bloatware (telemetry, recommendation rows, ad engines, duplicate background utilities) using non-destructive user-level disabling (`pm disable-user --user 0`), and replace the stock Android TV launcher with an ultra-lightweight, native Apple TV 4K inspired custom launcher built with Java and AndroidX. The entire implementation is verified on the real physical device using automated remote key event injection and pulled screenshots.

---

## Technical Context

**Language/Version**: Java 17 (Android Launcher), Python 3.10+ (Host Administration Tools), Bash / POSIX Shell

**Primary Dependencies**: 
- `androidx.recyclerview:recyclerview:1.3.2`
- `androidx.dynamicanimation:dynamicanimation:1.0.0`
- `adb-shell` (Python ADB over TCP client)
- `cryptography` & `rsa` (Python RSA key auth)

**Storage**: Local backup filesystem archives (`backup/*.txt`), Android XML configuration (`res/values/config.xml`), and local disk screenshots (`screenshots/*.png`)

**Testing**: Real-device automated validation loop via ADB key injection (`tools/tv.py 'input keyevent <key>'`) and screenshot capture (`screencap -p` pulled via `tv.py --pull`)

**Target Platform**: OnePlus Y1 43" Android TV (Android 11, API 30, compileSdk 34, minSdk 26, targetSdk 29, ~1 GB RAM, MediaTek SoC)

**Project Type**: Native Android TV Application (`launcher/`) + Device Management CLI Suite (`tools/`)

**Performance Goals**:
- Launcher RAM usage < 50 MB
- Zero frame drops during D-pad navigation at 60 fps
- Sub-second (< 1.0s) cold home launch and return latency
- Sub-60s full system restoration via rollback script

**Constraints**:
- Low memory hardware ceiling (~1 GB total RAM; system services consume ~650 MB)
- Strict prohibition against uninstalling system packages (`pm uninstall` forbidden; only `pm disable-user --user 0`)
- Never disable critical hardware services (HDMI/tuner playback, Bluetooth remote pairing, on-screen keyboard, Google Play)
- All user-preference items (dock app selection, discretionary debloat) require explicit user selection

**Scale/Scope**: 1 target TV device, ~20-30 candidate system packages categorized, 4 dock applications, 4-column application grid

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Non-Destructive Operations**: PASS. All package modifications use `pm disable-user --user 0` and are 100% reversible via generated `restore.sh`.
- **Hardware Preservation**: PASS. Critical input frameworks, HDMI services, TV tuner, and remote control Bluetooth stacks are strictly protected.
- **Hardware-Tailored Efficiency**: PASS. Tailored native Android app without background services or memory-heavy blur shaders respects the 1 GB RAM constraint.
- **Verifiability**: PASS. Every milestone produces on-device screenshot artifacts and device probe reports.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-debloat-apple-tv-launcher/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: transport, debloat categorization, launcher architecture
├── data-model.md        # Phase 1 output: entities, schemas, focus state machine
├── quickstart.md        # Phase 1 output: step-by-step runnable validation guide
├── contracts/           # Phase 1 output: interface contracts
│   ├── cli-tools-contract.md
│   ├── launcher-config-contract.md
│   └── tv-input-contract.md
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository layout)

```text
tv-vibehack/
├── launcher/                            # Native Android TV Launcher project
│   ├── app/
│   │   ├── build.gradle                 # compileSdk 34, minSdk 26, targetSdk 29
│   │   └── src/main/
│   │       ├── AndroidManifest.xml      # Home intent filter, permissions
│   │       ├── java/com/gathin/tvhome/
│   │       │   ├── MainActivity.java    # Core UI, D-pad navigation, input menu
│   │       │   ├── AppAdapter.java      # 4-column grid + dock adapter
│   │       │   ├── GlassView.java       # Translucent spring focus indicator
│   │       │   ├── DockDecoration.java  # Persistent bottom dock container
│   │       │   └── ClockDream.java      # Ambient screen saver / clock
│   │       └── res/
│   │           └── values/
│   │               ├── config.xml       # User dock apps, hidden apps, live tv package
│   │               ├── strings.xml
│   │               └── styles.xml
│   ├── build-and-install.sh             # Build debug APK, push via base64, set default
│   └── build-and-install.ps1            # Windows PowerShell equivalent
├── tools/                               # Python / Bash device automation
│   ├── tv.py                            # ADB-over-TCP client via adb-shell
│   ├── b64push.py                       # Chunked base64 push with MD5 hash check
│   ├── disable.sh                       # Batch pm disable-user executor
│   ├── restore.sh                       # Batch pm enable restoration script
│   └── requirements.txt                 # adb-shell, cryptography, rsa
├── examples/
│   └── oneplus-tv-dubai/
│       └── debloat.txt                  # Reference debloat list for OnePlus Android TV
├── backup/                              # Local device state backup directory
└── screenshots/                         # Verification captures from real device
```

**Structure Decision**:
Utilizes the existing modular repository layout with `launcher/` handling the native Android TV client, `tools/` providing the isolated ADB-over-TCP automation suite, and `specs/` containing all architectural specifications and validation plans.

---

## Complexity Tracking

> No constitution violations or unjustified architectural complexity detected.
