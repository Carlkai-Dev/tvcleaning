# Implementation Research: Android TV Debloat & Apple TV Style Custom Launcher

**Feature Branch**: `001-debloat-apple-tv-launcher`  
**Date**: 2026-10-18  
**Target Hardware**: OnePlus Y1 43" Android TV (Android 11, Model/Board: MediaTek/HeyTap, ~1 GB RAM)

---

## 1. Device Communication Protocol & Connection Resilience

### Decision
Use a dual-strategy connection approach: standard Android Debug Bridge (`adb`) over TCP port 5555 with fallback to a pure Python implementation using `adb-shell` (`tools/tv.py`) authenticated via auto-generated RSA keypairs. Transfer files using base64 chunking (`tools/b64push.py`) with MD5 hash validation.

### Rationale
- Stock ADB binaries on Unix/macOS environments often fail silently or with "No route to host" due to OS local network privacy sandboxing or binding issues.
- `adb-shell` runs entirely within a Python virtualenv without system daemon dependencies, talking directly to `TV_HOST:5555` via TCP sockets.
- The `adb-shell` raw stream push occasionally hangs on files > 5 MB on low-end Android TV hardware. Transferring in base64 chunks through shell standard input into `/data/local/tmp/` followed by an on-device `md5sum` check guarantees 100% transfer completion.

### Alternatives Considered
- Direct stock `adb connect`: Fast when available, but brittle in locked-down environments. Kept as primary CLI check with instant fallback to `tv.py`.
- Third-party side-loading apps (e.g., Send Files to TV): Requires user to manually install and navigate another UI on the TV, defeating automated setup.

---

## 2. Device Discovery and Authorization

### Decision
Discover device via:
1. Checking existing ARP table (`ip neigh` / `arp -n`).
2. Scanning local `/24` subnet on port 5555 using a rapid socket probe.
3. Prompting the user for the IP address if multiple candidates or zero responses are found.
4. Emitting a clear, proactive warning: "Please look at your TV screen now — the 'Allow USB debugging?' prompt will appear. Check 'Always allow' and press OK."

### Rationale
Automated discovery saves user effort, but local Wi-Fi router isolation can prevent ARP broadcast detection. Giving clear guidance before sending the RSA auth token ensures the user is ready with their physical TV remote.

---

## 3. Safe System Baseline Backup & Non-Destructive Debloat

### Decision
Before any modification, execute:
- `pm list packages -f -u > backup/packages_all.txt`
- `pm list packages -d > backup/packages_disabled.txt`
- `getprop > backup/getprop.txt`
- `settings list system > backup/settings_system.txt`
- `settings list global > backup/settings_global.txt`
- `settings list secure > backup/settings_secure.txt`
- Generate an idempotent `restore.sh` that re-enables all disabled packages using `pm enable <package>`.

Debloat operations use `pm disable-user --user 0 <package>` only. Never run `pm uninstall`. Execute `pm` commands individually per shell call or newline-separated batch to prevent silent truncation.

### Rationale
- `pm disable-user --user 0` deactivates the package for the primary user, hides its launcher icons, and stops all background services without deleting `/system/app` or `/system/priv-app` APK binaries.
- If a mistake occurs, running `pm enable <package>` restores the package instantly without a factory reset.

---

## 4. Package Classification for OnePlus Y1 (Android 11)

### Decision
Classify packages into strictly defined categories:

1. **Definite Bloat (Disable)**:
   - Manufacturer Telemetry: `com.google.android.feedback`, `com.heytap.olink` (OnePlus Connect)
   - Ad & Recommendation Rows: `com.google.android.tvrecommendations`, `com.oneplus.tv.launcher`, `com.tcl.browser`
   - Redundant Background Utilities: `com.heytap.tv.btspeaker`, `com.heytap.tv.filebrowser`, `oneplus.smarthome`
2. **Discretionary Packages (Prompt User Checklist)**:
   - Voice Assistant: `com.google.android.katniss` (Google Assistant), `com.heytap.tv.assistant.oneplus`
   - Cast/Miracast: `com.google.android.apps.mediashell` (Chromecast built-in)
   - System OTA Updates: `com.heytap.oprcupgradeservice`
   - Streaming Services: Preloaded apps such as Netflix (`com.netflix.ninja`), Prime Video (`com.amazon.amazonvideo.livingroom`), Disney+ Hotstar (`in.startv.hotstar`), YouTube (`com.google.android.youtube.tv`)
3. **Protected Core (Never Touch)**:
   - Live TV & HDMI Input Services: `com.mediatek.wwtv.tvcenter` / `com.heytap.tv.livetv`
   - Remote Control & Bluetooth: `com.android.bluetooth`, remote firmware update services
   - On-Screen Keyboard: `com.google.android.inputmethod.leanbackkeyboard`
   - Play Store & Services: `com.android.vending`, `com.google.android.gms`

### Rationale
OnePlus TVs run OxygenOS/ColorOS for TV built on MediaTek SoC platforms (`wwtv`). Disabling MediaTek TV Center or HeyTap LiveTV breaks HDMI video switching and tuner functionality. Disabling Bluetooth bricks the wireless remote.

---

## 5. Apple TV 4K Style Native Launcher Architecture

### Decision
Build a lightweight native Android TV launcher using Java and AndroidX (`androidx.recyclerview:recyclerview:1.3.2` and `androidx.dynamicanimation:dynamicanimation:1.0.0`):
- **Base Layout**: Pitch black background (`#000000`).
- **Dock Row**: Persistent 4-item horizontal band at bottom (`#1A1A1A` with rounded corners `16dp`).
- **Application Grid**: 4-column scrollable `RecyclerView` below the dock that scrolls up into view when navigating down.
- **Glass Spring Highlight**: Single custom overlay view (`GlassView`) animated across tile positions using physics-based `SpringAnimation` with damping ratio `0.85` and stiffness `350f`.
- **Dock Focus Typography & Glow**: Samples bitmap palette from focused banner/icon and renders app title in light typography with a radial gradient glow behind the text.
- **Top Bar Controls**:
  - Left: Outfit Light font clock (12/24h), current date, and subtle greeting ("Good evening").
  - Right: Frosted pill buttons (`#26FFFFFF` background) for `Inputs`, `Wi-Fi` (displaying active SSID), and `Settings` (`android.settings.SETTINGS`).
- **Inputs Dropdown**: Glass popup menu displaying HDMI 1, HDMI 2, HDMI 3, AV, and Live TV, tuning to inputs using `TvInputManager` or passthrough URI intents (`content://android.media.tv/passthrough/...`).
- **Memory Optimization**: Zero background services, zero polling loops, hardware-accelerated draw passes, downsampled icon caching in RAM (< 40 MB footprint).

### Rationale
The OnePlus Y1 has only 1 GB RAM total, with system processes already consuming ~650 MB. Third-party commercial launchers (Wolf, Projectivy, FLauncher) often use webviews, background scrapers, or heavy layouts that trigger Android's Low Memory Killer (LMK). A tailored ~350 KB native APK provides instant 60fps response.

---

## 6. Remote D-Pad Navigation Model

### Decision
Implement explicit, deterministic focus traversal:
- `Up` from Dock row: Focus moves directly to the Top Bar (Inputs pill).
- `Down` from Dock row: Focus scrolls into the first row of the Application Grid.
- `Up` from first row of Grid: Focus returns to the Dock row.
- `Up` from Top Bar: Focus is clamped (no off-screen loss).
- `Left`/`Right` at boundaries: Clamped within the current row.
- Focus memory: On return from an opened app, focus re-selects the previously active tile.

### Rationale
Default Android focus search algorithms (`FocusFinder.findNextFocus`) often hop diagonally across complex overlapping layouts or fall back to invisible parent views. Hardcoded focus listeners guarantee zero focus trapping.

---

## 7. Verification & Visual Validation Pipeline

### Decision
Automate step-by-step verification using ADB remote key injection and frame captures:
1. `tools/tv.py 'input keyevent 3'` (Home)
2. `tools/tv.py 'screencap -p /sdcard/step.png' && tools/tv.py --pull /sdcard/step.png ./screenshots/step.png`
3. Inject D-pad inputs: Keycode 19 (Up), 20 (Down), 21 (Left), 22 (Right), 23 (Center/OK).
4. Verify layout, contrast, and focus states locally before completing handoff.

### Rationale
Validating every phase on real hardware prevents regressions and guarantees the UI looks crisp on the 43" 1080p panel.
