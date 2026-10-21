# HDMI visibility in the Inputs menu

By default the launcher lists all non-hidden HDMI inputs, including inactive ports.
To hide bare HDMI ports when the TV reports them as standby or disconnected, add
this optional key to your theme's `config.json`:

```json
{
  "hide_inactive_hdmi": true
}
```

Apply the theme as usual with `tools/theme.sh themes/<name>`. Set the key to `false`
or remove it to restore the default list. A bundled configuration can also set the
`hide_inactive_hdmi` boolean in `launcher/app/src/main/res/values/config.xml`.

This is **firmware-reported input activity**, not a physical cable detector:

- Bare HDMI ports reported as connected remain visible.
- Bare HDMI ports reported as connected/standby or disconnected are hidden when enabled.
  A plugged-in source that is off can therefore disappear too. Disable the option
  if you need to select such a port before turning its source on.
- Known HDMI-CEC devices remain selectable, even in standby, because selecting their
  input can wake them. Their device names and parent-port grouping are preserved.
- Unknown status stays visible. The existing workaround for TVs that report every
  HDMI port as connected still suppresses misleading status without hiding the ports.
- AV, tuner/Live TV rows, and the configured manufacturer source panel are unchanged.
  Inputs hidden in the TV's own settings remain hidden.

While the menu is open, Android input callbacks refresh its rows and status after a
short delay to combine discovery events. Focus follows the input ID rather than
the row number or display name. If that input disappears, focus moves to a nearby
remaining row. There is no background polling; pending refreshes are cancelled
when the menu closes. If the last row disappears, a “No inputs found” placeholder keeps
the menu open for a reconnect (press Back or OK to dismiss it).

The launcher cannot correct a missing or inaccurate report from the TV's firmware.
See Android's [input states](https://developer.android.com/reference/android/media/tv/TvInputManager)
and [input callbacks](https://developer.android.com/reference/android/media/tv/TvInputManager.TvInputCallback).

## Device check: HAAM HM4K75S11TND

Tested on Android 11 / R4 (Realtek rtd2851m), firmware V8-T851MGL-0001103,
with an awake laptop connected directly to HDMI 2. The test used a separate
launcher package so the installed Home configuration was preserved.

| Check | Result |
| --- | --- |
| All HDMI ports physically empty, option enabled | All three reported standby (`1`), with empty stream configurations; no HDMI rows shown |
| Connect laptop to HDMI 2 while menu stays open | HDMI 2 changed to connected (`0`), with a stream configuration; its row appeared automatically |
| Keep another row focused during connection | Focus stayed on that same input despite its new row position |
| Select HDMI 2 with D-pad Up and OK | The owner confirmed the laptop picture was visible |
| Unplug HDMI 2 while its row is focused | HDMI 2 returned to standby (`1`), stream configuration emptied, row disappeared automatically, focus moved to the remaining row |
| Cold launch with option `false`, or key omitted | HDMI 1, 2 and 3 all remained selectable |
| Cold launch with option `true` again | All empty HDMI ports were hidden again |

The AV row's existing firmware-reported status was not changed by this feature.
This test did not exercise CEC wake-up, powered-off sources, other TV brands, or
a popup losing every last row. CEC behavior is retained from the existing code.
Do not interpret these results as universal physical cable detection.

![HDMI 2 appears while the existing AV selection is preserved](images/hdmi-visibility-connected.png)
