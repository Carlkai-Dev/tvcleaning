# Xiaomi Mi Box S / Mi Box 4 (Android TV 9): debloated with Apple TV style dock and remote pairing wizard

Model `MIBOX4` (MDZ-22-AB), codename `oneday`, Amlogic S905X (`gxl`), 2 GB RAM, Android TV 9 (Pie, SDK 28), build `PI.3933`. Tested with network ADB over Wi-Fi (`tools/tv.py`) and verified with physical remote hardware.

## Highlights
- **+200 MB Free RAM:** Idle available memory increased from 836 MB to ~1.05 GB (and up to 1.23 GB on clean boot) by disabling 24 background bloat, telemetry, and promotional packages (`pm disable-user --user 0`, none uninstalled).
- **Core Functionality Preserved:** Google Assistant voice search (`katniss`) with 66.6 kbps BLE GATT audio pipeline intact, Chromecast built-in preserved.
- **Home Button Mapping:** Xiaomi remote's Apps button mapped to Home with `HomeKeyService`.
- **Remote Pairing Wizard (`prompt_remote_pairing`):**
  If no physical remote is paired (e.g. migrating remote between devices), enabling `"prompt_remote_pairing": true` in [`config.json`](config.json) automatically invokes Xiaomi's animated pairing activity (`com.google.android.tungsten.setupwraith/com.google.android.tvsetup.app.RemotePairingActivity` via action `com.google.android.tvsetup.app.REPAIR_REMOTE`), prompting the user to hold `Back + Home` to pair their remote.

## 1. Debloat
Apply the 24-package debloat profile:
```bash
tools/disable.sh examples/xiaomi-mi-box-s/debloat.txt
```
(Windows: `tools\disable.ps1 examples\xiaomi-mi-box-s\debloat.txt`)

## 2. Launcher Setup & Theme
1. Build and install the launcher:
   ```bash
   HOME_KEY_SERVICE=1 launcher/build-and-install.sh
   ```
   (Windows: `launcher\build-and-install.ps1 -HomeKeyService`)
2. Push the theme configuration:
   ```bash
   tools/theme.sh examples/xiaomi-mi-box-s
   ```
   (Windows: `tools\theme.ps1 examples\xiaomi-mi-box-s`)

[`config.json`](config.json) enables:
- 4-slot dock with smooth spring animations (`advanced_effects`)
- Translucent frosted glass dock band (`dock_glass`)
- Dock position pagination dots (`cinematic_dots`)
- Horizon ambient backlight glow (`app_glow`, `glow_style = "horizon"`)
- Tile context menu for moving and docking apps (`tile_menu`)
- Automatic remote pairing prompt when unbonded (`prompt_remote_pairing`)

## 3. Undo
To restore all disabled packages:
```bash
tools/restore.sh examples/xiaomi-mi-box-s/debloat.txt
```
To reset launcher theme:
```bash
tools/theme.sh --reset
```
