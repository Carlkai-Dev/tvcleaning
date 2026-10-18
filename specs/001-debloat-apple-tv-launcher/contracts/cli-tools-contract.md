# Contract: CLI Tools & Automation Interfaces

**Feature Branch**: `001-debloat-apple-tv-launcher`  
**Date**: 2026-10-18

---

## 1. Environment Variables

| Variable | Required | Default | Description | Example |
|----------|----------|---------|-------------|---------|
| `TV_HOST` | **Yes** | None | IP address of target TV | `192.168.1.50` |
| `TV_PORT` | No | `5555` | ADB TCP daemon port | `5555` |
| `JAVA_HOME` | No (build only) | Detected | JDK 17 root directory | `/usr/lib/jvm/java-17-openjdk-amd64` |
| `ANDROID_HOME` | No (build only) | `/opt/android-sdk` | Android SDK root directory | `/opt/android-sdk` |
| `STOCK_LAUNCHER`| No | `com.google.android.tvlauncher` | Stock launcher package to disable | `com.google.android.tvlauncher` |

---

## 2. Command Specifications

### 2.1 `tools/tv.py`
Core ADB-over-TCP abstraction layer.

#### Invocation Syntax:
```bash
python3 tools/tv.py [--probe]
python3 tools/tv.py <shell command string>
python3 tools/tv.py --push <local_path> <remote_path>
python3 tools/tv.py --pull <remote_path> <local_path>
python3 tools/tv.py --batch < commands.txt
```

#### Exit Codes:
- `0`: Success
- `1`: Missing `TV_HOST`, network unreachable, or ADB authentication rejected

#### Stdin/Stdout:
- `--probe`: Prints key device properties (`ro.product.model`, `ro.product.device`, `ro.build.version.release`, `ro.build.version.sdk`, `ro.build.display.id`, `ro.product.manufacturer`).
- `--batch`: Reads newline-separated shell commands from `stdin`, runs each sequentially over a single socket session, and writes `$ <cmd>\n<output>` to `stdout`.

---

### 2.2 `tools/b64push.py`
Resilient large file transfer mechanism utilizing base64 stream decoding and md5 verification.

#### Invocation Syntax:
```bash
python3 tools/b64push.py <local_file_path> <remote_file_path>
```

#### Contract:
- Computes MD5 of `<local_file_path>`.
- Splits binary payload into base64 encoded chunks.
- Pipes chunks through `tv.py` to `base64 -d > <remote_file_path>`.
- Executes `md5sum <remote_file_path>` on TV and compares against local hash.
- Exits with `0` on match; exits with non-zero and removes remote artifact on mismatch.

---

### 2.3 `tools/disable.sh`
Applies a debloat package list to the TV.

#### Invocation Syntax:
```bash
TV_HOST=<ip> tools/disable.sh <path_to_debloat_list.txt>
```

#### Contract:
- Ignores blank lines and `#` comments.
- Generates `pm disable-user --user 0 <package>` for each entry.
- Appends `pm list packages -d` to verify results.
- Executes commands through `tv.py --batch`.

---

### 2.4 `tools/restore.sh`
Reverses package disablement from a package list.

#### Invocation Syntax:
```bash
TV_HOST=<ip> tools/restore.sh <path_to_debloat_list.txt>
```

#### Contract:
- Parses package names from file.
- Executes `pm enable <package>` for each package through `tv.py --batch`.
- Never attempts to re-install or modify system partitions.

---

### 2.5 `launcher/build-and-install.sh`
Compiles, pushes, installs, and sets the custom launcher as default.

#### Invocation Syntax:
```bash
TV_HOST=<ip> launcher/build-and-install.sh
```

#### Execution Sequence Contract:
1. Validates `TV_HOST`, `JAVA_HOME`, and `ANDROID_HOME`.
2. Generates `local.properties` with `sdk.dir=$ANDROID_HOME`.
3. Runs `./gradlew assembleDebug -q --console=plain`.
4. Transfers `app-debug.apk` to `/data/local/tmp/tvhome.apk` via `b64push.py`.
5. Executes on TV:
   - `pm install -r /data/local/tmp/tvhome.apk`
   - `rm -f /data/local/tmp/tvhome.apk`
   - `pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null`
   - `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity`
   - `pm disable-user --user 0 $STOCK_LAUNCHER`
6. Reports completion status.
