# Implementation Research: Segregating OnePlus Y Series Contributions per CONTRIBUTING.md

**Feature Branch**: `002-segregate-oneplus-contributions`  
**Date**: 2026-10-20  
**Context**: Reorganizing local OnePlus Y1 changes for upstream compliance without data loss.

---

## 1. Upstream Contribution Standard Analysis (`CONTRIBUTING.md`)

### Requirements for Adding a TV Model
According to `CONTRIBUTING.md`:
1. **Directory Naming**: Must reside under `examples/<brand>-<model>/` (e.g., `examples/oneplus-tv-y1/`).
2. **File Structure**:
   - `debloat.txt`: The core debloat file.
   - `screenshot.png`: Visual verification evidence of the clean home screen on the TV.
3. **Header Metadata**:
   - Exact product model: `OnePlus TV (Y Series 43", codename shibuya)`
   - Android version: `Android TV 11`
   - Firmware display ID: `RTO9.231122.002`
   - Live TV tuner app: `com.mediatek.wwtv.tvcenter`
   - Stock launcher: `com.google.android.tvlauncher`
   - Usage commands:
     - `TV_HOST=<tv ip> tools/disable.sh examples/oneplus-tv-y1/debloat.txt`
     - `TV_HOST=<tv ip> tools/restore.sh examples/oneplus-tv-y1/debloat.txt`
4. **Package List Organization**:
   - Strictly one package per line.
   - Grouped with clear comment headers (Telemetry, Content/Ad Rows, Demos/Factory, Background Bloat, Preloaded Apps).
   - Trailing inline comments explaining purpose or recovery notes.
   - Never include `uninstall` commands; only `pm disable-user`.
5. **Documentation Catalogs**:
   - Add a row to `examples/README.md`.
   - Add a row to `README.md` (Supported TVs table).

---

## 2. File Segregation Classification

To prevent regressions or conflicts when merging with `origin/main`, every modified or created file is classified into one of three tiers:

### Tier 1: Upstream PR Candidates (Brand-Neutral & New Model Assets)
These files belong in the upstream pull request and benefit all users or cleanly add the OnePlus Y1 model:
- `examples/oneplus-tv-y1/debloat.txt` (New model debloat profile)
- `examples/oneplus-tv-y1/screenshot.png` (Model screenshot evidence copied from `screenshots/01_home.png`)
- `examples/README.md` (New table row for OnePlus Y1)
- `README.md` (Supported TVs table row for OnePlus Y1)
- `launcher/build-and-install.sh` (Generic Linux detection for `JAVA_HOME` `/usr/lib/jvm/java-17-openjdk-amd64` and `ANDROID_HOME` `/opt/android-sdk`; completely non-destructive to macOS/Windows)
- `launcher/gradle/wrapper/gradle-wrapper.properties` (Generic increase of network timeout and retries to prevent connection drops)

### Tier 2: Fork-Specific Helper Tools & Automation (Preserved in Fork)
These files are valuable workflow tools for this fork and local testing, but must not pollute root `tools/` with hardcoded brand names:
- `tools/restore_oneplus_y1.sh` -> Move to `scripts/restore_oneplus_y1.sh` or `examples/oneplus-tv-y1/restore.sh` (since generic `tools/restore.sh` already handles any debloat file via `tools/restore.sh examples/oneplus-tv-y1/debloat.txt`).
- `tools/classify_packages.py` -> Move to `scripts/classify_packages.py` (useful development helper).
- `tools/discover_tv.py` -> Can remain in `tools/` as a generic helper or `scripts/`.
- `scripts/backup_baseline.sh`, `scripts/generate_restore_script.sh`, `scripts/prompt_debloat_preferences.sh`, `scripts/verify_ui_screenshots.sh` -> Remain safely in `scripts/`.

### Tier 3: Local Private & Ephemeral Data (Never Committed Upstream)
- `backup/` -> Device-specific serial numbers, settings tables, private package dumps. Kept strictly local (already in `.gitignore`).
- `screenshots/` -> Full local capture sequences (`01_debloat_verify.png`, `01_home.png`, `02_grid.png`, `03_inputs.png`). Kept locally; only the single clean reference screenshot is copied to `examples/oneplus-tv-y1/screenshot.png`.
- `.specify/scripts/python/__pycache__/*.pyc` -> Python bytecode cache. Must be purged from git index with `git rm --cached` and ignored in `.gitignore`.

---

## 3. Upstream Merging / Rebasing Safety

### Conflict Prevention Strategy
Upstream `main` (`origin/main`) has introduced:
- Theming support (`themes/`)
- Test suites (`tests/`)
- Key services and settings activities in `launcher/`

To ensure our fork does not create destructive conflicts:
1. **Config XML**: Keep `launcher/app/src/main/res/values/config.xml` non-destructive. Upstream expects `config.xml` to be the user configuration point.
2. **Generic Tools**: Root `tools/disable.sh` and `tools/restore.sh` must remain unaltered from upstream or maintain pure backward compatibility.
3. **No Deletions of Working Code**: All custom scripts and tests remain available on the branch under `scripts/` or `examples/oneplus-tv-y1/`.
