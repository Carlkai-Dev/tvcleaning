# Contract: Model Contribution & PR Hygiene

**Feature Branch**: `002-segregate-oneplus-contributions`  
**Date**: 2026-10-20

---

## 1. Debloat Profile Format Contract (`examples/<model>/debloat.txt`)

Every model debloat profile MUST conform to the standard structure defined in `CONTRIBUTING.md`:

```text
# <Manufacturer> TV (<Model Name>, codename <Codename>), <Android Version>, firmware <Build ID>
# Live TV app: <Live TV package>. Stock launcher: <Stock launcher package>.
#
# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/<model>/debloat.txt
# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/<model>/debloat.txt
# Everything is DISABLED, not uninstalled. Comment out any line you want to keep.
#
# <Notes on quirks, hardware buttons, or preserved packages>

# <Category 1 Heading>
package.name.one
package.name.two                          # <Inline explanatory comment>

# <Category 2 Heading>
package.name.three
```

### Validation Rules:
1. Must not contain `pm uninstall` commands or rm/delete commands.
2. Must include Live TV and stock launcher package identifiers in the header.
3. Every package entry must be verifiable via `pm list packages`.
4. Trailing inline comments must explain any non-obvious package functions.

---

## 2. Model Documentation Guide Contract (`examples/<model>/README.md`)

The model guide acts as a self-contained instruction manual for consumers owning this hardware:

### Required Sections:
1. **Title & Hardware Specifications**: Model name, screen size, marketing name, board/codename, Android version, build ID.
2. **Quick Apply Instructions**: Clear copy-paste commands to connect over ADB and apply `debloat.txt` using `tools/disable.sh`.
3. **Restoration Instructions**: Instructions to run the dedicated `restore.sh` (or `tools/restore.sh`) to re-enable everything and restore the stock launcher.
4. **Launcher Configuration**: Specific package parameters needed in `launcher/app/src/main/res/values/config.xml` (such as `live_tv_package = com.mediatek.wwtv.tvcenter`).
5. **Preserved Features Summary**: Explicit list of verified working functions (HDMI input switching, Bluetooth remote pairing, voice assistant, Chromecast, Gboard).

---

## 3. Catalog Entry Contract (`examples/README.md`)

The entry added to `examples/README.md` must adhere to the table schema:

| Column | Type | Content | Example |
|---|---|---|---|
| `Folder` | Markdown code link | Relative directory name | `oneplus-tv-y1/` |
| `TV` | String | Brand, marketing name, model, codename | `OnePlus Y1 43" (Y Series, shibuya)` |
| `OS` | String | Operating system & version | `Android TV 11` |
| `Notes` | String | Summary of key disabled bloat & preserved features | `Skyworth/HeyTap telemetry, partner ads, retail demos; voice & chromecast preserved` |

---

## 4. Pull Request Hygiene Contract

A pull request submitted upstream for a new TV model must strictly satisfy:

1. **Isolation**: Contains only `examples/<brand>-<model>/` (including `debloat.txt`, `README.md`, `restore.sh`, `screenshot.png`) and corresponding catalog updates in `examples/README.md` and root `README.md`.
2. **Generic Tooling**: Does not introduce model-specific scripts into root `tools/`.
3. **No Unwanted Artifacts**:
   - Zero `.pyc` files or `__pycache__` directories.
   - Zero private device serial numbers or `getprop` / settings dumps.
   - Zero local test video or ad-hoc test binaries.
