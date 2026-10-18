# tv-vibehack

**Your TV, minus the ads.** Debloat any Android TV / Google TV over ADB and give it a clean,
tvOS-inspired launcher. Hand the prompt to Claude Code or any coding assistant, answer a few
questions about which apps you use, and it does the rest on the real TV.

| Stock home screen | After |
|---|---|
| ![Stock Google TV home with a sponsored ad](docs/stock-home.jpg) | ![tvOS-style launcher](docs/new-home.jpg) |

Everything is reversible: packages are disabled, never uninstalled, and a restore script puts the
stock launcher back.

## Quick start

1. On the TV: Settings → Device Preferences → About → press OK on **Build** seven times → back →
   Developer options → turn on **USB debugging**. Keep the TV on and on the same Wi-Fi as your
   computer.
2. **If your TV runs Android 11 or newer**, you also need to pair, and the port is not 5555.
   Developer options → **Wireless debugging** → note the **IP address & port** it shows →
   **Pair device with pairing code** → run the pairing command below while that dialog is up.
   Android 11 removed the fixed 5555 port that Android 10 and older used.
3. Open Claude Code (or your assistant of choice) on your computer and paste the prompt from
   [`PROMPT.md`](PROMPT.md), filling in your TV model.
4. Accept the "Allow USB debugging?" prompt on the TV when told to, then answer the checklist
   questions (which apps you use, which four go in the dock).

That's it. The assistant backs up, debloats, builds and installs the launcher, and verifies each
step with screenshots from the TV. If you'd rather do it by hand, the pieces are below.

### Pairing on Android 11+

Pairing has to happen while the dialog is on screen, and the code expires in about a minute:

```bash
adb pair 192.168.1.50:37073 123456    # port and code from the pairing dialog
adb connect 192.168.1.50:38737         # the port from Wireless debugging, not the pairing port
```

The two ports are different and both are needed. Pairing registers your key so it persists
across reboots; the connect port changes every time you toggle Wireless debugging, so re-read
it from Developer options each session. `adb mdns services` will list it if you'd rather not
read it off the screen.

## What's in the repo

| Path | What it is |
|---|---|
| [`PROMPT.md`](PROMPT.md) | The prompt. Brand-neutral; the assistant discovers your TV's bloat. |
| [`launcher/`](launcher/) | The tvOS-style launcher. Edit `app/src/main/res/values/config.xml` for your dock, hidden apps and Live TV package, then run `build-and-install.sh`. |
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client (shell, batch, push, pull). Works where the stock `adb` is blocked. |
| [`tools/b64push.py`](tools/b64push.py) | Uploads larger files (APKs) as base64 chunks with an md5 check. |
| [`tools/disable.sh`](tools/disable.sh), [`tools/restore.sh`](tools/restore.sh) | Apply or undo a debloat list. |
| [`examples/`](examples/) | Per-TV debloat lists. Add yours, see [CONTRIBUTING.md](CONTRIBUTING.md). |

## The launcher

A small native Android TV home screen (`com.gathin.tvhome`), built for sets with 1 GB of RAM:

- Pitch-black background, like Apple TV 4K.
- A dock at the bottom in a lighter rounded band with the four apps you choose. The shelf above
  shows the focused dock app's name in large type with an ambient glow sampled from its banner.
  Press Down and the dock slides up to reveal the rest of your apps in a grid.
- One translucent glass highlight springs from tile to tile. The focused tile lifts and shows its
  name.
- Frosted pills top right: **Inputs** (a dropdown of HDMI ports, AV and Live TV), **Wi-Fi**
  (shows the network name) and **Settings**. Greeting, a large thin clock and the date top left,
  set in [Outfit](https://fonts.google.com/specimen/Outfit).
- Deterministic D-pad navigation, last focused tile remembered, staggered entrance animation.
  No blur, no video, no network, no background services.

Configure it in [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
dock order, hidden apps, Live TV package and columns. Build and install with

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). On macOS:
`brew install openjdk@17 android-commandlinetools && sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

## Talking to the TV by hand

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP
export TV_PORT=38737                                          # see "Gotchas" below
tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one command
printf 'cmd1\ncmd2\n' | tools/venv/bin/python -I tools/tv.py --batch
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, restore stock launcher
```

If `tv.py` reports `InvalidCommandError: Unknown command: 'STLS'`, your TV is Android 11+ and
this is a known limitation, not something you did wrong — see "Gotchas" below.

### Gotchas we hit

- **Android 11+ moved ADB off port 5555.** Wireless debugging binds a random port that changes
  every time you toggle it, and the device requires a TLS-wrapped transport: it replies `STLS`
  to your first handshake and closes the connection if you don't start TLS. So on any Android 11
  or newer TV, `tools/tv.py` will fail with `InvalidCommandError: Unknown command: 'STLS'`. This
  is a real gap in `adb-shell` 0.4.4, the latest release — it implements neither `STLS` nor the
  pairing handshake. **Until that's fixed, use the `adb` binary on Android 11+** and pair once as
  described in Quick start. On Android 10 and older, `tools/tv.py` works as before on 5555.
- **Which launcher to disable depends on the TV.** Check before you assume:
  `adb shell 'cmd package query-activities -c android.intent.category.HOME -a android.intent.action.MAIN'`.
  On the TCL Smart TV Pro both `com.google.android.apps.tv.launcherx` (Google TV) and
  `com.tcl.tv.tclhome_passive` claim HOME; on the Xiaomi it's `com.google.android.tvlauncher`.
- **macOS and `adb`: the advice here used to be the other way round.** Older notes said the stock
  `adb` is blocked by Local Network privacy and that `tv.py` works around it. In practice, on
  recent macOS with Android 11+ TVs, `adb` works fine and the Python client is the thing that
  can't get through. Keep both; reach for whichever the situation calls for.
- **On Android 14, `adb install` appears to hang.** It's waiting on an install-confirmation dialog
  on the TV that nothing on your side can answer. Push the APK and install it from local storage
  instead — both steps then return immediately:
  `adb push app.apk /data/local/tmp/` then `adb shell pm install -r /data/local/tmp/app.apk`.
- **Chained `pm` commands** (`pm disable a; pm disable b`) may silently stop after the first. Run
  one per shell call; `--batch` does this.
- **Large pushes hang** through the Python client. Use `tools/b64push.py` for APKs.
- **Disable the stock launcher only after the new one is installed**, or the Home button lands on
  a factory test app. Set the new one as home first, screenshot it, and only then disable stock —
  two commands put everything back (`pm enable <stock>` then `cmd package set-home-activity`).
- **Debloat in batches, not all at once.** On the TCL, disabling `com.tcl.providers.config`
  dropped the TV's Wi-Fi, and recovering needed a fresh pairing code — losing network access also
  costs you the channel you would use to undo it. Disable a few, screenshot, then continue.
- **Google TV's home feed can't be debloated.** The "Sponsored" slot on Android 12+ lives inside
  the launcher app itself; the `tvrecommendations` packages this repo disables don't exist there.
  Replacing the launcher is the only way to remove it.
- **Third-party launchers** often charge for basics like a custom wallpaper. Hence `launcher/`.
- **No on-screen keyboard** after switching launchers (seen on Xiaomi): the default input method
  pointed at a Baidu IME that isn't installed. Fix with
  `tools/tv.py 'ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME'`.

## Supported TVs

Anything running Android TV or Google TV: Xiaomi/Redmi, TCL, Hisense, Sony, OnePlus, Realme,
Nvidia Shield and most others. Not Samsung Tizen, LG webOS or Amazon Fire TV (different OS, no ADB
launcher replacement). Tested so far on the models in [`examples/`](examples/).

One caveat by OS version, because it changes the workflow rather than just the package names:
**Android 10 and older** behave as documented throughout. **Android 11 and newer** need wireless
debugging paired with the `adb` binary, on a port that changes per session — see the gotchas
above. The debloat lists themselves are unaffected.

## Contributing

Add your TV's debloat list, improve the launcher, or sharpen the prompt. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

MIT, see [`LICENSE`](LICENSE). The Outfit typeface is under the SIL Open Font License, see
`launcher/FONT-LICENSE-OFL.txt`.
