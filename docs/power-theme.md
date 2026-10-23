# Power theme: modes and a welcome that asks for your mood

A power theme is an ordinary theme with a list of **modes**: Entertain, Movies, Games, Kids, News, Markets or any
you make up. Each mode is a set of theme keys laid over the theme's own: its dock, columns, colours, motion,
layout. When the TV comes on, Home greets you for the time of day and asks how you feel; the mood you pick
loads its mode. The remote's colour keys switch modes from Home.

Nothing changes for a theme without `"power_theme": true` and a `modes` list.

## What happens on the TV

1. The first Home after the TV is switched on opens the welcome: "Good evening, Somesh. How are you feeling
   tonight?" on a sky for the time of day. The name is `greeting_name`.
2. One card per mode, showing its mood ("Relaxed", "Put something good on"). The mode for this hour is marked
   **Suggested** and starts by itself after 10 seconds; any key stops the countdown.
   - 05:00-10:00: `news` · 10:00-17:00: `entertain`, or `games` at weekends · 17:00-21:00: `entertain` ·
     later: `movies`. A theme without that mode suggests the last one chosen.
3. OK loads the mode and Home rebuilds with it. Back keeps the last mode.
4. On Home, a colour key with a mode switches to it; a colour key without one opens the same screen as a mode
   picker, with no countdown.

The welcome never leaves the TV: it uses the TV's clock and what was chosen before, with no camera, microphone
or network.

## Keys

| Key | Type | Meaning |
|---|---|---|
| `power_theme` | bool | Turns modes and the welcome on. Off unless a theme sets it |
| `power_welcome` | `boot`, `daily` or `never` | When the welcome asks: the first Home after each power-on (default), the first of each day, or never (colour keys only) |
| `modes` | list | The modes, in the order the welcome shows them, three per row |

Each mode:

| Field | Meaning |
|---|---|
| `id` | Short name, unique. `entertain`, `movies`, `games`, `news` are the ones the welcome suggests by hour |
| `name` | The mode's name |
| `mood` | The feeling the welcome offers for it; the name if empty |
| `line` | The small line under the mood |
| `color` | `#RRGGBB`, the card's colour |
| `key` | `red`, `green`, `yellow` or `blue`: the remote key that switches to it; empty for none |
| `theme` | Theme keys from [themes/README.md](../themes/README.md#keys) laid over the theme's own while the mode is on. `power_theme`, `power_welcome` and `modes` are ignored here |

A key a mode doesn't set keeps the theme's value. An unknown key is ignored, so `"layout": "honeycomb"` below is
harmless on a launcher without the Honeycomb layout.

## A complete example

Save it as `themes/power/config.json` (any folder name works) and push it like any theme:

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/power
```

```json
{
  "power_theme": true,
  "power_welcome": "boot",
  "launcher_settings_button": true,
  "modes": [
    {
      "id": "entertain", "name": "Entertain", "mood": "Just chilling", "line": "A bit of everything",
      "color": "#2BC3C9", "key": "",
      "theme": {
        "dock_apps": ["com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv"],
        "cinematic_home": true, "app_glow": true, "background_color": "#04060B"
      }
    },
    {
      "id": "movies", "name": "Movies", "mood": "Relaxed", "line": "Put something good on",
      "color": "#F6C98B", "key": "red",
      "theme": {
        "dock_apps": ["com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv", "com.plexapp.android"],
        "background_color": "#0A0705", "ambient_glow": false, "reduced_motion": true, "sky_intensity": 40,
        "home_idle_clock_minutes": 10
      }
    },
    {
      "id": "games", "name": "Games", "mood": "Playful", "line": "Let's play a game",
      "color": "#9B6BFF", "key": "",
      "theme": {
        "dock_apps": ["com.valvesoftware.steamlink", "com.nvidia.geforcenow", "com.playstation.remoteplay", "tv.twitch.android.app"],
        "background_color": "#07040F", "app_glow": true, "glow_style": "horizon", "focus_overshoot": true,
        "layout": "honeycomb"
      }
    },
    {
      "id": "kids", "name": "Kids", "mood": "Family time", "line": "The kids are watching too",
      "color": "#F2C230", "key": "yellow",
      "theme": {
        "dock_apps": ["com.google.android.youtube.tvkids", "com.netflix.ninja"], "fill_dock": false,
        "columns": 3, "always_show_names": true, "show_network_pill": false, "show_inputs_pill": false
      }
    },
    {
      "id": "news", "name": "News", "mood": "Curious", "line": "What's happening today?",
      "color": "#E0475A", "key": "blue",
      "theme": {
        "dock_apps": ["com.google.android.youtube.tv"], "background_color": "#0D0F12", "dock_title": true,
        "sky_intensity": 60
      }
    },
    {
      "id": "markets", "name": "Markets", "mood": "Focused", "line": "Check the markets",
      "color": "#4BFF7A", "key": "green",
      "theme": {
        "dock_apps": ["com.tradingview.tradingviewapp", "com.zerodha.kite3", "com.google.android.youtube.tv"],
        "background_color": "#040806", "ambient_glow": false, "icon_only_buttons": true, "show_stats": true
      }
    }
  ]
}
```

Add `"greeting_name": "Your name"` at the top level for a personal greeting. Apps a mode lists but the TV
doesn't have are skipped, as with any `dock_apps`.

## Not yet

- Who's watching: one set of modes per person, and a Kids profile that skips the question.
- Apps picked by Android category (games, video, news) instead of a dock list, and an allowlist for Kids.
- The mood in the top bar, and a Mode row in Launcher settings.
