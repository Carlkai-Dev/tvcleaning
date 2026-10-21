# Homelab companion

A small Android TV app that goes next to the CleanYourTV launcher and talks to the services on your own
network. The launcher itself has no network permission and keeps it that way; everything that needs
the network lives here instead.

| Part | What it does |
|---|---|
| **Immich screensaver** (`PhotoDream`) | Recent photos from Immich in the manner of Google Photos' ambient mode: crossfades and a slow zoom, two portraits side by side, a blurred copy of the photo instead of black bars, the album, date, place and people bottom left and the clock bottom right. Albums and tags can be hidden. Needs Immich 3.2 or later. |
| **Homelab** tile (`DashboardActivity`) | Your [Homepage](https://gethomepage.dev) dashboard full screen. Up/Down scroll it, Menu reloads, links to other hosts are blocked. A loading screen, an error screen that says what failed with Try again and Settings, a reload when you come back after a while, and optionally the screen kept on for a wall display. |
| **Media** tile (`MediaActivity`, `DetailActivity`) | **Search**: one field for films and shows; Radarr and Sonarr are asked at once as you type, and the results are one grid of posters, best match first, with a tick on what you already have. OK on a poster shows its backdrop, ratings and overview with **Add and search** focused, so adding is a single press; quality and (for a show) which episodes to monitor sit next to it. Something already in the library offers **Search again**. Voice search when the TV has a recogniser. **Downloads**: qBittorrent's torrents named the way Sonarr and Radarr know them, with speed, time left and size, refreshed every few seconds while on screen; OK stops or starts one. Downloads the apps track elsewhere (another client, Usenet) are listed too. **Coming up**: the next two weeks of both calendars, what is missing, free disk space and health warnings. |
| **Homelab Settings** tile (`SettingsActivity`) | Every setting, edited with the remote. Albums, tags, quality profiles and root folders are picked from lists your servers return, and each server has a Test button. Also the gear next to the screensaver in the TV's own settings. |

No runtime dependencies, about 80 KB installed. Plain Java like the launcher, minSdk 26.

## Permissions and data

- `INTERNET`, for the URLs in your `config.json`, and for the posters and backdrops the Sonarr and
  Radarr lookups point at, which come from the public image hosts they use (`image.tmdb.org`,
  `artworks.thetvdb.com`) without any key or account. No analytics, nothing else. The Homepage page can
  itself load icons from public CDNs if your Homepage config uses them.
- A package-visibility query for voice input (`RECOGNIZE_SPEECH`), so the Media screen shows its mic
  button only when something on the TV can take voice. With Google's apps disabled there may be none;
  the on-screen keyboard always works.
- Settings, API keys included, stay in the app's private storage (`files/config.json`). The Settings
  screen writes it, or `push-config.sh` through a provider that only the adb shell can use (it needs
  `WRITE_SECURE_SETTINGS`) and that is write-only.
- Backups are off.

## Set up

Install once, from the repo root, with `tools/venv` set up as in the main README and the TV reachable
over ADB:

```bash
TV_HOST=192.168.1.50 SCREENSAVER=1 companion/build-and-install.sh
```

Then open **Homelab Settings** on the TV, fill in each server's address and API key, press Test,
and pick what to hide and where to add. Changes apply the next time a tile opens or the screensaver
starts; nothing needs a rebuild. Long API keys are easiest to type from a phone: the Google TV app's
remote keyboard types into the TV's text fields.

Or write everything at once from the computer, which is also the only way to set `ca_pem`:

```bash
cp companion/config.example.json companion/config.json      # ignored by git: it holds your API keys
$EDITOR companion/config.json
TV_HOST=192.168.1.50 companion/push-config.sh companion/config.json
```

A push replaces the whole file, so settings changed on the TV since are replaced too.

Try the screensaver without waiting for it (or use Preview in Settings):

```bash
TV_HOST=192.168.1.50 tools/tv.py 'am start -n io.github.epochcoder.homelab/.SlideshowActivity'
TV_HOST=192.168.1.50 tools/tv.py 'logcat -d -s Homelab.Slideshow Homelab.Tls Homelab.Dashboard'
```

On Google TV, the stock screensaver is Google's Ambient mode (`com.google.android.apps.tv.dreamx`),
which also shows recommendations. Disable it with the rest of the debloat list so it cannot take the
screensaver back.

### API keys

- **Immich:** Account Settings > API Keys > New. It needs only `asset.read`, `asset.view`,
  `album.read` and `tag.read`. The Test button in Settings counts the albums the key can see.
- **Sonarr / Radarr:** Settings > General > API Key.
- **qBittorrent:** the WebUI user and password. Or leave the user empty and tick *Bypass authentication
  for clients in whitelisted IP subnets* in qBittorrent's WebUI options, with your LAN's subnet. Behind
  Caddy, qBittorrent's Host header validation needs the name you reach it by in its *Server domains*.
  Written against qBittorrent 5.1 (stop/start); 4.x's pause/resume is used when 5's calls are missing.

## Behind Caddy

- **Public certificates** (Let's Encrypt, including DNS challenge for internal names): nothing to do.
- **`tls internal`:** the TV does not trust Caddy's local CA, so give the companion Caddy's root
  certificate once. Copy it out of the Caddy container (on a QNAP, over SSH, or from Container
  Station's terminal; the container name may differ) and push it:

  ```bash
  docker cp caddy:/data/caddy/pki/authorities/local/root.crt .
  TV_HOST=192.168.1.50 companion/push-config.sh --ca root.crt
  ```

  The script prints the certificate's name, expiry and SHA-256 fingerprint, and refuses anything that
  is not a CA, including Caddy's intermediate, which Caddy replaces every few days. The root lasts ten
  years. It goes into its own file, so settings made on the TV stay as they are, and the app trusts it
  on top of the system CAs while still checking hostnames. `--reset-ca` removes it. (A `ca_pem` string
  in config.json works too, and both are trusted.)
- **A login in front (`forward_auth`, Authelia, Authentik):** the API calls carry an API key, not a
  browser session, so a login redirect breaks them. The app never follows a redirect with a key or a
  session cookie on it (that would hand them to whatever host the redirect names); you get an error on
  screen naming where it pointed, or "got a web page instead of JSON". Exempt `/api/*` on the Immich,
  Sonarr, Radarr and qBittorrent sites, or point the companion at the services' LAN ports.
- **Local DNS names** (`*.home.example` from your router, Pi-hole, AdGuard Home): leave the TV's
  Private DNS off, or set it to your own resolver. The ad-blocking Private DNS the main prompt offers
  sends every lookup to a public resolver, which does not know your internal names.

## config.json

What the Settings screen edits, key by key. Every section is optional; a part whose section is
missing shows "not set up" with a way into Settings.

| Key | Default | Meaning |
|---|---|---|
| `ca_pem` | `""` | Extra CA certificate(s) in PEM, on top of any pushed with `--ca`. |
| `immich.url`, `immich.api_key` | | Server and key. The URL is the address you open Immich at, without `/api`. |
| `immich.recent_days` | 90 | Photos taken in this many days. |
| `immich.max_photos` | 500 | At most this many per round, newest first. Max 5000. |
| `immich.exclude_albums` | `[]` | Album names (case-insensitive) or ids. |
| `immich.exclude_tags` | `[]` | Tag names, full tag paths (`People/Work`) or ids. |
| `immich.shuffle` | `true` | Random order; `false` shows newest first. |
| `immich.interval_seconds` | 20 | Seconds per photo, 5 to 3600. |
| `immich.crop` | `false` | Fill the screen with every landscape photo. Off, only photos within about 20% of the screen's shape (a 3:2 camera shot on 16:9) fill; the rest fit over a blurred copy of themselves. Portraits never crop. |
| `immich.show_clock` | `true` | Clock and date bottom right. |
| `immich.details` | `true` | Album (or place), date, place, the photo's description and named people bottom left. |
| `immich.motion` | `true` | A slow zoom across each photo. |
| `immich.pair_portraits` | `true` | Two portrait photos side by side. |
| `homepage.url` | | Your Homepage address. |
| `homepage.text_zoom` | 100 | Text size in percent, 50 to 300. |
| `homepage.keep_awake` | `false` | Keep the screen on while the dashboard is open. |
| `sonarr.*`, `radarr.*` | | `url`, `api_key`; `quality_profile` (name) and `root_folder` (path) to add with. Empty takes the first one the server lists. |
| `sonarr.monitor` | `all` | Episodes a new show monitors: `all`, `future`, `missing`, `firstSeason`, `latestSeason`, `pilot`, `none`. Can be changed per show on its detail screen. |
| `qbittorrent.url`, `.username`, `.password` | | qBittorrent's WebUI. No user means no login (whitelisted subnet). |

Archived, hidden and locked photos are never shown, and videos are skipped. Hidden albums and tags are
filtered out by Immich itself. The photo list is fetched again each time the slideshow has gone round,
so new photos show up overnight. The next photo is fetched while the current one is on screen, and the
album name is one small request per photo shown.

## Build and test

```bash
cd companion && ./gradlew testReleaseUnitTest lintRelease assembleRelease
```

The unit tests cover config parsing, captions, when a photo fills the screen, the Settings pickers'
lists, and the parsing of every server's answers. The clients themselves run against a scripted HTTP
server (`FakeServer`): the API key never follows a redirect, qBittorrent's login and retry (403 and
401, renamed session cookies, 5.x stop/start and 4.x pause/resume), Immich's cursor paging and
server-side hiding, and what Sonarr and Radarr are sent on add and search. TLS runs against a local
HTTPS server with a Caddy-shaped chain (fixtures in `app/src/test/resources/tls`). Every screen has
been rendered off-device; nothing has run on a TV or against real servers yet.
