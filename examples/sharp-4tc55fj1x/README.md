# SHARP 4TC55FJ1X (4K Sharp Google TV, Android 14)

SHARP 55" 4K Google TV (`4TC55FJ1X`), device `expo`, model "4K Sharp Google TV", manufacturer `MTC`, firmware
`UKNV.260716.001`, MediaTek `MSD9602PA` platform, 2 GB RAM.

| File | What it is |
|---|---|
| `debloat.txt` | The 23 packages disabled on this TV, grouped and commented |

## Testing and Verification

Applied, rebooted and verified on 2026-10-21:
- Saved complete backup of package lists, settings, and properties in `backup/4TC55FJ1X/`.
- Debloated in 4 batches with screenshots pulled from the TV between each batch.
- Sideloaded CleanYourTV launcher (`com.gathin.tvhome`) with the user's dock apps: YouTube, Netflix, Jellyfin, Stremio.
- Replaced Google TV Home (`launcherx` and `setupwraith` disabled).
- Survived a cold reboot: direct boot into CleanYourTV with zero package drift (`tools/check.py` reported 0 drift).
- Remote control hotkeys (Netflix, YouTube, Settings, Inputs) remain handled by the hardware vendor framework (`com.mediatek.tv.service`).

## Notes for this set

- **Wi-Fi network ownership (`setupwraith`)**: During first Google TV setup, the initial Wi-Fi network is saved
  under `com.google.android.tungsten.setupwraith`. Android's WifiConfigManager deletes networks created by an app
  the moment that app is disabled. Before disabling `setupwraith`, forget and reconnect the Wi-Fi in
  **Settings > Network & Internet** so ownership transfers to `android.uid.system:1000`.
- **Home claimants**: Both `launcherx` (priority 2) and `setupwraith` (`.RecoveryActivity`, priority 1) claim Home
  ahead of user launchers (priority 0). Disabling single components via shell is blocked by Android 14 security
  policy (`Shell cannot change component state`), so both packages are disabled at package level once Wi-Fi ownership
  is transferred.
- **Hardware TV Input & Live TV**: `com.mediatek.tvinput` and `com.mediatek.tv.service` drive the hardware HDMI,
  composite, and tuner layers, while `com.mediatek.wwtv.tvcenter` is the Live TV tuner application. All are kept.

## Quick Reference

### Re-enabling any disabled app
To re-enable an app (e.g. Google Assistant or Chromecast):
```bash
TV_HOST=192.168.1.40 tools/venv/bin/python tools/tv.py "pm enable --user 0 <package_name>"
```
Or use the automated restore script:
```bash
TV_HOST=192.168.1.40 tools/restore.sh examples/sharp-4tc55fj1x/debloat.txt com.google.android.apps.tv.launcherx
```

### Changing Dock Apps or Wallpapers
- **Dock Apps**: Edit `launcher/app/src/main/res/values/config.xml` under `<string-array name="dock_apps">`,
  then re-run:
  ```bash
  TV_HOST=192.168.1.40 launcher/build-and-install.sh
  ```
- **Wallpapers**: Push any PNG or JPG using `tools/theme.sh` or push directly:
  ```bash
  TV_HOST=192.168.1.40 tools/venv/bin/python tools/tv.py --push wallpaper.png /data/local/tmp/wallpaper.png
  ```

### Closing Network ADB
When you are done modifying your TV, close ADB to secure your device:
1. On the TV: **Settings → System → Developer options → Turn OFF USB debugging** (and Network/ADB debugging if present).
2. Under Developer options, select **Revoke USB debugging authorizations**.
3. Reboot the TV.
4. Verify port 5555 is closed from your computer:
   ```bash
   tools/venv/bin/python tools/adb-closed.py 192.168.1.40
   ```
