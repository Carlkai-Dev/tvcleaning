# Acer R4G (Google TV 14, Realtek): a boot job re-enables 60 packages, setupwraith among them

Checked on one TV in India from 2026-10-19 to 2026-10-23 ([#1106](https://github.com/Gathin23/cleanyourtv/issues/1106)).
The debloat was done by hand following [`PROMPT.md`](../../PROMPT.md), with an `adb-shell` client like
`tools/tv.py`, and Home was replaced by the owner's own launcher rather than `launcher/`. The boot job below acts
on packages, not on a particular launcher, so it applies to any replacement Home. There is no list in
`examples/` because the fix needs a user-0 uninstall, which the lists there never do.

## Identify the firmware

| Field | Observed value |
|---|---|
| Retail name | Acer 50" Google TV, model R4G |
| `ro.product.manufacturer` | `MOKA` |
| `ro.product.brand` / `ro.product.model` | `ACER` / `R4G` |
| `ro.product.name` / `ro.product.device` | `ACER_NFF` / `R4_GTV` |
| `ro.board.platform` / `ro.hardware` | `rtd2851m` / `rtd2851m` (Realtek) |
| Android / SDK | `14` / `34` |
| `ro.build.display.id` | `UKR1.260308` |
| `ro.build.fingerprint` | `ACER/ACER_NFF/R4_GTV:14/UKR1.260308/5597:user/release-keys` |
| Security patch | `2026-04-05` |
| `ro.product.cpu.abilist` | `armeabi-v7a,armeabi` |
| RAM | 2 GB |
| `ro.adb.secure` | `1` |

## Symptom: Home is black after the first reboot

The usual Google TV handover works at first. With a replacement Home installed, `launcherx` and `setupwraith` both
disabled (`setupwraith`'s `.RecoveryActivity` claims HOME at priority 1), Home is the new launcher. That lasts until
the TV restarts. On this unit the first restart was a power cut four days later. After it, Home was black, while
the remote's volume, HDMI and app keys still worked, and `logcat` showed the setup wizard being started as Home
every ~130 ms:

```
I/ActivityTaskManager(  905): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000100 cmp=com.google.android.tungsten.setupwraith/.RecoveryActivity (has extras)} with LAUNCH_SINGLE_TOP from uid 0 (BAL_ALLOW_ALLOWLISTED_UID) result code=0
```

## Cause: `SetPmodServiceJob` in the vendor TV Settings

This firmware's `com.android.tv.settings` is modified by Acer/Realtek. Its `.system.FallbackHome` schedules
`SetPmodServiceJob` on every boot, and the job calls `disableOrEnableApk(1)`. That re-enables every package on a
hard-coded list of 60 that is disabled, unless the factory `P_MODE` flag is set, which needs root. Twenty of the
31 packages disabled on this unit are on the list, among them `setupwraith`, Assistant, the screensaver and the
firmware updaters. Once `setupwraith` is enabled again it outranks any third-party Home, which Android caps at
priority 0, and loops trying to bring back the disabled `launcherx`.

So the generic advice for firmware that re-enables packages at boot, to note them and leave them alone, does not
work here. One of them is `setupwraith`, and while it is enabled it keeps Home.

## What was tried

| Approach | Result |
|---|---|
| `pm disable-user --user 0` | Re-enabled at the next boot by `disableOrEnableApk(1)` |
| `pm hide --user 0` | Refused: `SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_USERS` |
| Disable only `.RecoveryActivity` | Refused: `Shell cannot change component state` |
| Clear `P_MODE` | Needs root |
| `pm uninstall -k --user 0` | Works. The job skips packages that are not installed for the user. After a power-socket restart, HOME resolves only to the replacement launcher and `com.android.tv.settings/.system.FallbackHome` |

A user-0 uninstall keeps the APK on the system partition and the app's data (`-k`). `cmd package install-existing`
brings a package back, and a factory reset restores everything.

## Packages on Acer's list (uninstalled for user 0)

These 20 were among the packages disabled on this unit and were all re-enabled at boot. Other packages on the
60-entry list were never disabled here, so they are not listed.

```
com.google.android.tungsten.setupwraith  # setup wizard; its RecoveryActivity claims HOME at priority 1
com.google.android.katniss               # Google Assistant
com.google.android.apps.tv.dreamx        # Google ambient screensaver
com.google.android.videos                # Google TV / Play Movies
com.google.android.play.games            # Play Games
com.google.android.feedback              # Google feedback upload
com.apps.ota                             # firmware updater
com.apps.authota                         # firmware updater
com.realsil.ota                          # firmware updater (Realtek)
com.google.android.tv.dfuservice         # remote firmware updater
com.apps.biz.mt                          # Meeting Mode
com.mk.tv.meeting.service                # Meeting Mode service
com.apps.function.audiolink              # Audio Link
com.mk.tv.timeclock                      # World Clock
com.apps.tvmanager                       # cleaner
com.apps.esticker                        # shop-demo overlay
com.apps.esticker3                       # shop-demo overlay
com.apps.function.gdpr                   # GDPR consent
com.apps.scast                           # signage/cast duplicate
com.creative.fastscreen.tv               # Miracast
```

The firmware updaters were the owner's choice. Leaving them installed is the safer default.

## Packages not on the list (disabled, stay disabled)

```
com.google.android.apps.tv.launcherx             # stock launcher, only once another Home is in place
android.autoinstalls.config.scbc.device          # Play auto-installs of partner apps
com.realtek.leanbacklauncher.partnercustomizer   # partner rows for the stock launcher
com.apps.mk.wd                                   # S-Channel ads
com.android.tv.feedbackconsent                   # feedback consent screens
com.mk.tv.systemserver                           # Firebase-only stub
com.apps.wifihotspot                             # hotspot
com.alphainventor.filemanager                    # File Manager+
com.mxtech.videoplayer.television                # MX Player
com.jio.media.stb.ondemand                       # JioCinema
com.yupptv.scope.tvapp                           # YuppTV
```

Kept: Live TV (`com.realtek.tv`) and every input service, the source menu, `com.mk.service.tvmidware` (talks to
the TV board), the remote, Bluetooth and key handlers, Gboard, Play Store, Play Services, TV Settings, Chromecast
and the USB media player.

## Apply

Install the replacement Home and make it the default first. Before `setupwraith` goes, check whether it owns the
saved Wi-Fi network (`dumpsys wifi | grep -o 'cname=[^ ]*'`): if it does, forget the network and join it again in
Settings, or the TV drops off Wi-Fi and ADB with it. Then run one command per line, in small batches with a
screenshot between them:

```bash
export TV_HOST=<tv ip>
printf '%s\n' \
  "pm uninstall -k --user 0 com.google.android.tungsten.setupwraith" \
  "pm disable-user --user 0 com.google.android.apps.tv.launcherx" \
  | tools/venv/bin/python -I tools/tv.py --batch
```

Do the other packages above the same way: `pm uninstall -k --user 0` for the first list, `pm disable-user --user 0`
for the second. Then restart the TV from the power socket and check that Home comes back before anything else:

```bash
tools/venv/bin/python -I tools/tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
tools/venv/bin/python -I tools/tv.py 'logcat -d | grep RecoveryActivity'
```

## Undo

Enable `launcherx` **first**: `setupwraith` on its own, while `launcherx` is disabled, is the black-screen loop again.

```bash
printf '%s\n' \
  "pm enable com.google.android.apps.tv.launcherx" \
  "cmd package install-existing --user 0 com.google.android.tungsten.setupwraith" \
  "cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity" \
  | tools/venv/bin/python -I tools/tv.py --batch
```

Then `cmd package install-existing --user 0 <package>` for the rest of the first list and `pm enable <package>`
for the second.
