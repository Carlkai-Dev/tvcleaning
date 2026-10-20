# SBL – Slightly Better Launcher

A small, fast home screen for Android TV. Your favourite apps sit in one row, the app you are on shows what
you were watching, and everything else (inputs, weather, settings) is one press away. About 480 KB, no ads,
no account, no accessibility service needed.

![SBL home screen](docs/home.png)

Version 0.1 · Android 8.0+ (API 26) · Android TV and Google TV · English, Türkçe, Español, Français, Deutsch,
Русский, Українська, Português, Čeština, Polski

## What it does

**Home screen**
- **Favourites dock** – one band of large tiles. Focus one and the app's name and its Watch Next items show above it.
- **Watch Next** – shows what the focused favourite publishes: *Continue watching*, *Up next*, *New*, *Watchlist*
  and *Featured*, with title, episode, minutes left, progress bar, description and a poster that fades in from the
  right. TV episodes and things you are partway through come first. Press Up from the dock to take control
  (Left/Right change the item, OK opens it in its app); otherwise it turns by itself every 6, 10 or 30 seconds
  (or never, your choice). The button says *Continue*, *Watch*, *Listen* (music apps) or *Play* (games).
- **Connected inputs** – HDMI and AV inputs that have something plugged in, named the way the TV names them.
  The top-bar input button opens the full list including Live TV.
- **AirPlay name** – if the TV advertises an AirPlay receiver, its name is shown in the top bar (and the button
  opens the AirPlay app when one is installed).
- **Clock, date and weather** for where the TV is (°C or °F).
- **All apps** below the dock, hidden until you go down, so the home screen stays calm.

**Apps**
- **Sorting** – alphabetical, recently used, most used, or manual (hold OK on a tile and move it).
- **Hold OK on a tile** for *Move*, *App info*, *Hide from home* and *Add to / Remove from favourites*.

**Look**
- **Languages** – ten, picked automatically from the TV's language (English when SBL is not translated into it)
  or chosen by hand in *SBL settings → Home screen → Language*, whatever the TV itself is set to.
- **Dark and light** looks, switched in the settings.
- **Themes** – a folder with a `config.json` and an optional wallpaper. Install from a USB drive, the TV's storage
  or a web address, switch or delete them in the settings. See [`themes/`](themes/README.md).
- Everything is drawn for a TV: glass menus, a hollow focus frame, sharp artwork (banners and icons are loaded at
  xxhdpi or higher and only scaled down), a splash that looks like the home screen instead of a white flash.

**SBL settings** (gear button → *SBL settings*)
- *Home screen*: language, columns, inputs row, Watch Next on/off and turning time, weather on/off and unit, module cards.
- *Apps*: sort order, hidden apps, clear usage statistics.
- *Theme*: appearance (dark/light) and themes.
- *Permissions*: what SBL can use and why.
- *Backup, reset and about*: back up every setting, favourites, order and usage to the TV or a USB drive, restore
  it, reset the menu choices.

**Also inherited from the project SBL grew out of:** module cards (an app can offer its own labelled tile, see
[`launcher/CARDS.md`](launcher/CARDS.md)), a clock screensaver, and optional remote-key helpers.

## Install

### Without ADB

1. Get `SlightlyBetterLauncher.apk` from [`dist/`](dist/) (or build it, below) and put it on a USB drive, or
   open its download link in a TV browser/Downloader app.
2. On the TV open the APK from a file manager and allow installs from that app when Android asks.
3. Start **SBL**. On the first run Android asks whether SBL may read TV listings; say *Allow* to get Watch Next.
   Everything else works without it.
4. Optional: **SBL settings → Permissions** opens Android's *All files access* page. Only needed to save backups
   and read themes on the TV's storage or a USB drive.

SBL is a normal app until you make it your Home screen, so you can try it first and just press Home to leave.
To make it the Home screen without a computer, press Home on the remote: if your TV asks which launcher to use,
pick SBL and *Always*.

### With ADB

```bash
adb connect <tv-ip>:5555
adb install -r SlightlyBetterLauncher.apk
adb shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS      # Watch Next (optional, the app also asks)
adb shell appops set com.gathin.tvhome MANAGE_EXTERNAL_STORAGE allow          # backups and themes on storage (optional)
adb shell am start -n com.gathin.tvhome/.MainActivity
```

Updating works the same way, as long as the APK is signed with the same key (see *Signing* below).

## Build from source

You need JDK 17 or newer and the Android SDK (`ANDROID_HOME`).

```bash
cd launcher
./gradlew assembleRelease            # app/build/outputs/apk/release/SlightlyBetterLauncher.apk
TV_HOST=192.168.1.50 ./install.sh    # build, install and open on the TV (install.ps1 -TvHost … on Windows)
```

### Signing

A release build is signed with the key named in `launcher/keystore.properties` (never committed):

```properties
storeFile=/path/to/sbl-release.jks
storePassword=…
keyAlias=sbl
keyPassword=…
```

Without that file the release build falls back to the Android debug key, so anyone can still build and sideload it.
Create a key with:

```bash
keytool -genkeypair -keystore sbl-release.jks -alias sbl -keyalg RSA -keysize 4096 -validity 36500
```

**Keep the keystore safe.** A TV only accepts updates signed with the same key as the installed app; with a different
key you have to uninstall first, which also deletes SBL's settings (back them up in *SBL settings → Backup* first).

The APK in `dist/` is signed with SHA-256 certificate fingerprint
`5A:7B:1F:73:8F:3C:C0:99:5C:E6:6B:91:9D:64:20:E3:E1:76:E5:0C:8B:3F:60:08:BB:53:D4:ED:7D:B2:6E:CC`.

## Privacy

SBL has no account and no analytics. It uses the network for: the weather (Open-Meteo; the position comes from the
TV's last known location, or from your IP address through ipwho.is / geojs when there is none), posters that
apps publish as web addresses, and themes you choose to download. Watch Next data is read on the TV from the
system's TV provider and stays there. Usage counts for sorting stay on the TV.

## Notes

- Tested on a TCL Android TV (Android 11, 1920×1080 interface on a 4K panel). Other TVs should work, but
  Watch Next, input names and the Home-screen switch differ between makers.
- Android composes the interface of many TVs at 1080p even on a 4K panel (`adb shell wm size`). SBL cannot draw
  sharper than what the system gives it.
- The package name stays `com.gathin.tvhome`, so settings survive updates and an existing install of the original
  project can be replaced only after uninstalling it (the signing keys differ).

## Making SBL the Home screen with ADB

Only do this if pressing Home does not offer SBL as a choice. Disabling the TV maker's launcher can remove
things you may want (live TV shortcuts, the maker's settings entry points), so keep the last step handy.

1. See which app owns Home now:

   ```bash
   adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```

   Typical answers: `com.google.android.tvlauncher` (Android TV), `com.google.android.apps.tv.launcherx`
   (Google TV), or your maker's own launcher. If the answer is `android`, Android is showing a chooser: pick SBL
   there and the job is done.

2. Disable that launcher for your user (nothing is deleted):

   ```bash
   adb shell pm disable-user --user 0 <package-from-step-1>
   ```

3. Make SBL the Home app:

   ```bash
   adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
   adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```

   The second command should now name `com.gathin.tvhome`. On Google TV the setup wizard
   (`com.google.android.tungsten.setupwraith`) can also claim Home and keep winning; if the answer is not SBL,
   disable it the same way as in step 2.

4. **To go back to the original launcher at any time:**

   ```bash
   adb shell pm enable --user 0 <package-from-step-1>
   adb shell pm clear com.gathin.tvhome   # optional: forget SBL's own settings
   ```

   If the TV ever ends up with no Home screen, `adb shell pm enable --user 0 <package>` is always enough.

## Credits and licence

SBL started from the open-source Android TV launcher [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by
Gathin and is released under the same MIT licence, see [`LICENSE`](LICENSE). The Outfit typeface is licensed under
the SIL Open Font License 1.1 ([`launcher/FONT-LICENSE-OFL.txt`](launcher/FONT-LICENSE-OFL.txt)).
