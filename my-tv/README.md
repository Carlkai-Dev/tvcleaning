# Xiaomi MiTV-AXSO0 (Android 9): what was done and how to undo it

Set up on 2026-10-18 over ADB (TV at `192.168.1.104`, port 5555). Nothing was uninstalled; every
package was disabled for user 0, and `backup/` (local only) holds the pre-change package list,
`getprop` and all settings.

All commands need the TV's IP, and the Python venv from the repo README:
`export TV_HOST=192.168.1.104`

## What was disabled (`debloat.txt`, 25 packages)
Telemetry (miui analytics, xiaomi statistic, Google feedback and bugreport), PatchWall and the
sponsored/recommendation services, the TV Manager cleaner, handbook, firmware update service,
preloaded music/games/movies/gallery, unused streaming apps (YouTube Music, Sony LIV, ZEE5,
India TV, Journey), the voice assistant and Chromecast built-in. Google's stock launcher is
disabled as well (by `build-and-install.sh`).

Kept on purpose: Netflix, Prime Video, YouTube, JioHotstar, NetMirror, SmartTube, Send Files to TV,
Video Manager, Mi Cast/Miracast, Play Store/Services, the keyboard, the TV app and every HDMI/AV/tuner
input service, remote and Bluetooth services.

## Re-enable one app
```
tools/venv/bin/python -I tools/tv.py "pm enable com.google.android.katniss"
```
(voice assistant; use any package name from `debloat.txt`). Or delete its line from `debloat.txt`
so it stays enabled next time. Firmware updates: `pm enable com.xiaomi.mitv.updateservice`.

## Undo everything
`my-tv/restore.sh` re-enables every package in the list, brings back the stock launcher as home
and resets animation scales.

## Change the dock from the remote
Press the remote's **Menu** button (or hold **OK**) on any tile:

- Dock app: Move left, Move right, Remove from dock.
- Other app: Add to dock (the dock has four slots; remove one first when it is full).
- Any app: Hide app, App info (uninstall, permissions). "Show N hidden" appears once you have hidden something.

Your choices are saved on the TV and win over `config.xml`. To go back to the `config.xml` defaults:
`tools/venv/bin/python -I tools/tv.py "pm clear com.gathin.tvhome"`.

## Change the dock defaults
Edit `launcher/app/src/main/res/values/config.xml`, the `dock_apps` list (left to right, four
slots): currently SmartTube, YouTube, Prime Video, JioHotstar. Use package names from
`tools/venv/bin/python -I tools/tv.py "pm list packages"`. Use `hidden_apps` to drop tiles from the
grid (e.g. `com.mitv.gallery`). Then run `my-tv/build-and-install.sh`.

## Change the wallpaper
The launcher background is `launcher/app/src/main/res/drawable-nodpi/wallpaper.jpg`, a generated dark
aurora (indigo to teal, darker at the bottom so the dock stays readable). To change it, replace that
file with your own 1920x1080 JPG (keep it under about 300 KB; the TV has about 1 GB of RAM), then
run `my-tv/build-and-install.sh`. Dark images work best, since the text is white. For plain black
again, set `android:background="#FF000000"` on the root view in
`launcher/app/src/main/res/layout/activity_main.xml`.

## Screensaver options
Open the **Screensaver** tile in the app grid (or the gear next to the screensaver in the TV's own settings):

- **Start after**: 5, 10, 15, 30 or 60 minutes of idle time. Press OK to step to the next value. This changes the TV's
  screen-off timeout, so `launcher/build-and-install.sh` grants the `WRITE_SETTINGS` permission over adb once.
- **Clock**: follow the TV, 12-hour or 24-hour.
- **Dim at night**: darkens the picture and the clock between 22:00 and 06:00.
- **Preview now**: starts the screensaver right away.

## Screensaver
The launcher includes an "Aurora Clock" screensaver: four aurora scenes (`dream_1.jpg` to `dream_4.jpg`
in `drawable-nodpi/`) crossfade every 16 s with a slow pan and zoom, under a thin clock and date that move
every minute. It is selected on this TV with
`settings put secure screensaver_components com.gathin.tvhome/.ClockDream`. It starts after the TV's idle
timeout (5 minutes here; Settings > Device Preferences > Screen saver > When to start). Any remote key
wakes it. To preview it now: `am start -n com.android.systemui/.Somnambulator`. To go back to Xiaomi's:
`settings put secure screensaver_components com.mitv.dream/com.mitv.dream.MiDream`. To change the scenes,
replace the four JPGs (1280x720, under about 60 KB each) and run `my-tv/build-and-install.sh`.

## Performance pass (2026-10-20)
JioHotstar stalled (ALSA underruns) because the 1 GB TV was starved by Play Store/Play Services updates.
Added 22 idle packages to `debloat.txt` (gallery, camera, TalkBack/TTS, printing, screensavers,
calendar/backup stubs, setup leftovers) and denied Play Store background running. To let the Play Store
update again: `tools/venv/bin/python -I tools/tv.py "cmd appops set com.android.vending RUN_IN_BACKGROUND allow; cmd appops set com.android.vending RUN_ANY_IN_BACKGROUND allow"`
