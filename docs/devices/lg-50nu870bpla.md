# LG NANO NU87B 50" (50NU870BPLA, webOS 26): a clean Home through Developer Mode, no root

Owner report, India. This is a note, not a list: the set runs webOS, which has no `adb`, so there is
no `pm disable-user` and no `examples/` folder for it. Nothing in `tools/` speaks webOS.

| Field | Observed value |
|---|---|
| Retail model | LG NANO NU87B 50" |
| Model number | 50NU870BPLA |
| System | webOS 26 |
| Firmware | 43.04.26 |

## What worked

- LG Developer Mode, with the computer connected over SSH using the key from the TV's Key Server.
- A clean Home installed through Developer Mode, no root: a locally customized build of the launcher
  and on-TV Home helper from [Clean Your TV — LG webOS](https://github.com/Razdva122/clean-your-tv-lg-webos)
  (see [SHOWCASE.md](../../SHOWCASE.md#other-platforms-fire-tv-and-lg-webos)).
- A short press of Home on the remote returns to that launcher.

## Not tested

- Holding (or triple-pressing) Home to hand control back to LG Home.
- Coming back to the launcher after standby, and after a full cold boot.
- Back from an app returning to the launcher.

Developer Mode has to stay active for the launcher and helper to keep running.
