# OnePlus TV Q1 / Q1 Pro (Android TV 11)

- **Device:** OnePlus TV Q1 / Q1 Pro 55" (model `Oneplus_Dosa_IN`, codename `dosa`), MediaTek MT5887, 2.5 GB RAM, India.
- **Firmware:** `RTM5.220609.299` (incremental `2312290405`), Android TV 11 (API 30).
- **Launcher:** Stock home is `com.google.android.tvlauncher`, safely replaced by `launcher/` (`com.gathin.tvhome`).
- **Live TV:** `com.oneplus.tv.android.livetv`.

| File | What it is |
|---|---|
| `debloat.txt` | The 14 packages that were disabled, grouped and commented |
| `config.json` | Dock order and columns; push it with `tools/theme.sh` |

All commands run from the repo root, with the TV's IP in `TV_HOST`.

## Enabling ADB

Settings → Device Preferences → About → press OK on **Build** 7 times to enable Developer options.
Then go to Device Preferences → Developer options → turn on **USB debugging**.

Port 5555 opens and stays open across reboots until switched off.

## Apply the debloat list and launcher

```bash
TV_HOST=<tv ip> tools/disable.sh examples/oneplus-tv-q1/debloat.txt
TV_HOST=<tv ip> launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/oneplus-tv-q1
```

## Remote and inputs

- **Input button:** Managed by `com.mediatek.hotkey.dispatcher`, seamlessly switches inputs with `launcher/`.
- **Live TV / Tuner:** `com.oneplus.tv.android.livetv` handles live TV channels and remains fully functional.
- **Dedicated keys:** Netflix, Prime Video, YouTube and Google Assistant mic work as expected.
