# Sony BRAVIA BF1 (BRAVIA 2 II class), Android 12

Tested on one set: `BRAVIA_BF1_4K_A`, Android 12 (SDK 31), firmware `STT2.230811.001`, security patch
2026-02-05, MediaTek, 2.4 GB RAM, 32-bit ARM (`armeabi-v7a`), UI 1920×1080 at 320 dpi. IR-only remote.
Google TV home (`com.google.android.apps.tv.launcherx`). Applied on 2026-10-19.

## Order

Every batch: `pm disable-user` (nothing uninstalled), wait ~30 s, restart with `svc power reboot`, then
check Home, Netflix, YouTube, the Input button and AirPlay before the next one.

| Step | File | Result |
|---|---|---|
| 1 | `batch1-tracking.txt` | Samba TV, Sony promos and Select, Google feedback: fine |
| 2 | `batch2-demo-iot.txt` | Shop demo: fine. IoT can't stay disabled (see below) |
| 3 | `batch4-preloaded.txt` | Play Movies, YouTube Music, Play Games, Sony Pictures Core: fine |
| 4 | `home.txt` | Launcher installed, then made Home in safe steps |
| 5 | `batch5-screensaver.txt` | Ambient screensaver replaced by the launcher's clock |
| – | `batch3-voice.txt` | Optional, not applied (voice search kept) |

```bash
TV_HOST=<tv ip> tools/disable.sh examples/sony-bravia-2-ii-s25m2/batch1-tracking.txt
TV_HOST=<tv ip> tools/restore.sh examples/sony-bravia-2-ii-s25m2/batch1-tracking.txt   # undo
TV_HOST=<tv ip> tools/restore.sh examples/sony-bravia-2-ii-s25m2/home.txt              # stock Home back
TV_HOST=<tv ip> tools/theme.sh themes/sony-bravia-2-ii                                 # Picture/Sound guides
```

## Known quirks on this firmware

- **A plain `reboot` can lose a disable.** `pm disable-user` is saved a few seconds later; batch 1 came
  back enabled after an immediate `reboot`. Wait ~30 s and use `svc power reboot`.
- **Sony re-enables some packages at every boot.** `com.sony.dtv.applicationdisabler` keeps
  `m2.seeds.iot`, `m2.seeds.iot.remotestart`, `m2.seeds.auth`, `airplayapp` and `m2.devicecontrolservice`
  enabled (and the UK channel apps disabled). Leave them; disabling the disabler is not worth it.
- **Home, in steps.** `com.google.android.tungsten.setupwraith` also claims Home. Install with
  `STOCK_LAUNCHER=none`, disable setupwraith alone and watch the TV for 5 minutes, then disable launcherx
  and set our Home (`home.txt` has the commands). It boots straight into the launcher afterwards.
- **Remote.** Home sends `KEYCODE_HOME`; Input sends `KEYCODE_TV_INPUT`, which Sony's
  `com.sony.dtv.smartui` opens system-wide, so `INPUT_KEY_SERVICE` isn't needed. Keep smartui enabled.
  OK is never repeated while held, so long presses don't work; the launcher's Settings pill has a menu.
- **The ambient screensaver** shows "Unable to load content" once launcherx is disabled (its features
  provider lives there). Point the screensaver at `com.gathin.tvhome/.ClockDream` first, then disable it.
- **Picture and sound settings** live in a store only Sony's system apps may write; the Picture and
  Sound guides (theme `sony-bravia-2-ii`) show recommended values and open those pages.
- **`dumpsys meminfo`** without `-t 90` times out (10 s) on this set. Frame times flip between ~13/15 ms
  and ~23/26 ms (p50/p90) run to run with the same settings; compare janky frames instead.
- **The IP address changed after most restarts.** Reserve one in the router.
- **Not on Google Play here:** Stremio ("not available in your country" in India). Its official TV build
  (`armeabi-v7a`) from dl.strem.io installs fine.

## Numbers (same steps, ~12-17 min after boot)

| | Stock Google TV | After (batches 1, 2, 4, 5 + launcher) |
|---|---|---|
| Home memory (PSS) | 68.7 MB + 27.3 MB helper | 44-48 MB (4 per row); 86-91 MB with every extra on, 6 per row |
| Home p90 frame time | 31 ms | 15 ms |
| Home janky frames | 2.41% | 0.07% |
| MemAvailable | 1.09 GB | 1.14 GB (with Stremio's 245 MB running) |
| Swap in use | 314 MB | 310 MB |
| Processes | 369 | 359 |
| YouTube / Netflix cold start | 0.80-0.83 s / 0.54-0.66 s | 0.72-1.14 s / 0.50-0.74 s |

## Kept on purpose

Play Store and Play Services, Sony's update centre, TV inputs and tuners (`com.mediatek.dtv.tvinput.*`,
`com.sony.dtv.tvinput.dvbtuner`), `sonysystemservice`, `smartui` (Input menu), Sony and Android settings,
Dolby audio, AirPlay and Chromecast, voice search, Gboard. Before a factory reset nothing needs undoing:
the reset restores every package.
