# Skyworth HP44H / `HP4417-Telecom` — Telecom Argentina "FlowBox-F2" (Android TV 14)

A carrier set-top box sold in Argentina, also given away with Telecom/Fibertel subscriptions. The
firmware is branded `FLOW` and ships the operator's launcher-backed apps, remote management and
telemetry. This profile is about the one thing that makes it different from the other 214 examples:
**you cannot disable or uninstall anything the vendor signed.** `pm suspend` is the way through.

## Tested models

| Model | Tested | SoC / RAM | Network | Groups applied |
|---|---|---|---|---|
| FlowBox-F2 `HP44H` / `HP4417-Telecom` | 2026-10-21 | Amlogic S905X4 / 2 GB | Wi-Fi 6 | 1, 2, 3 (`debloat.txt`), plus the suspend list below |

Details: Android TV 14 / SDK 34, build `C5.3.3_20260721`, `ro.product.cpu.abilist`
`armeabi-v7a,armeabi` with `ro.zygote` `zygote32` — a 64-bit SoC running a 32-bit userspace, so
anything with arm64 native code will not install. Bootloader locked, verified boot `green`,
`ro.boot.veritymode` `enforcing`, no `su` binary: there is no root, and no software path to it
(FastbootD ignores commands and the board has no power button).

Screen is 1920×1080 at 320 dpi. Measured free RAM before/after: 84 MB → 193 MB.

## The whitelist

Every package the vendor signed is protected by a check patched into `services.jar`, which answers
each of these with `Info: <package> is in whitelist, there is no permission to uninstall.`:

```bash
pm uninstall --user 0 <package>
pm disable-user --user 0 <package>
pm hide --user 0 <package>          # also fails, differently: needs MANAGE_USERS
```

So `tools/disable.sh` cannot help with them, and they are deliberately absent from `debloat.txt`.
Packages that are **not** signed by the vendor (Google's, and anything sideloaded) behave normally.

`pm suspend --user 0 <package>` is not checked by that whitelist. It also survives a reboot, which
is what makes it usable:

```bash
pm suspend --user 0 ar.com.flow.androidtv_stb        # then, to undo:
pm unsuspend --user 0 ar.com.flow.androidtv_stb
```

## What to suspend

Three groups. The first two are what a buyer without a subscription wants gone; the third is a
choice (see the warning).

| Package | What it is |
|---|---|
| `ar.com.flow.androidtv_stb` | The Flow app |
| `ar.com.flow.provisioncheck` | Flow's provisioning check, launched after a network change |
| `ar.com.flow.radiosymusica` | Flow radio and music |
| `com.skyworth.android.tr069` | TR-069 CWMP, the operator's management agent |
| `com.sdt.android.tr369` | TR-369 USP, pointed at `tms-api.app.flow.com.ar` |
| `com.skyworth.xmppservice` | XMPP command channel to `xmpp.fibertel.com.ar` |
| `com.skyworth.skylogcatservice` | Collects logcat |
| `com.skyworthdigital.diagnose` | Diagnostics, reports through TR-069 |
| `com.sdt.producttest` | Factory test app |
| `com.skyworth.update`, `com.sdt.ota`, `com.sdt.upgrade` | OTA. Suspending these is what stops an update bringing the rest back |

**Warning about the OTA group:** suspending the three update packages freezes the box on the build it
is running. That is the point for a box you have just cleaned, but it also means no security patches.
Leave them out if you would rather keep updates and accept that a new firmware may undo the rest.

## What suspend does not stop

The packages above run as `android.uid.system` (uid 1000) — `com.skyworth.xmppservice`,
`com.sdt.android.tr369` and `com.skyworth.skylogcatservice` among them. Package suspension is not
enforced against the system uid, so those three start again on every boot even with
`suspended=true` recorded. Two attempts that do not survive a reboot either:

- `cmd appops set <package> RUN_IN_BACKGROUND ignore` plus `am force-stop` does stop them and frees
  memory, but the app-op is back to `allow` after a reboot.
- Rewriting the TR-369 endpoint (`settings put global sdt.standby.url`) is undone at boot: the native
  TR-369 stack pushes `Device.X_Skyworth.Standby.Url` back to the operator's value.

Without root, the only durable control is the network. These are the hosts this firmware talks to:

| Host | Seen from |
|---|---|
| `xmpp.fibertel.com.ar` | `com.skyworth.xmppservice`, device identity `<serial>FlowBoxF2@xmpp.fibertel.com.ar` |
| `tms-api.app.flow.com.ar:18369` | `com.sdt.android.tr369` |
| `atv-matrix.cvattv.com.ar:7032/metrics` | metrics POST, `SkyNetworkAccess` |
| `lb-01-hor.cvattv.com.ar` | TR-069 diagnostics ping |

`/system/etc/skyscripts/tr369_script.sh` is worth knowing about: it reads
`/mnt/skyinfo/tr369_command` and, when told `command_name=catch_log`, starts piping the whole of
logcat to a file. On the tested unit that was running.

`com.skyworth.platform.skyplatformservice` (uid 1000) binds `com.skyworthdigital.diagnose` as a
client, so that one process comes back even when nothing else does. Leave the display/audio services
alone: `com.sdt.cert.manager` (`SdtCoreService`) may be involved in Widevine, `com.sdt.rcupair` and
`com.sdt.remotecontrolota` are the Bluetooth remote's pairing and firmware, and `com.sdt.globalkey`
handles remote key mapping.

## Launcher

`config.json` next to this file hides the three Flow packages from Home. The stock launcher was
replaced with a third-party one before this profile was written; the subscribe-free path that needs
no build is in the main README.

The cinematic shelf with no title and no dots — the look this profile was built with — is
`themes/cinematic-bare`.
