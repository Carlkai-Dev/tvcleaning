# Contributing

Thanks for helping more TVs lose their ads. Three kinds of contributions are especially welcome.

## 1. Add your TV model

Every brand ships different bloat. If you ran the prompt (or did it by hand) on a TV that isn't in
`examples/`, add it:

1. Create `examples/<brand>-<model>/debloat.txt`. Copy the format from
   `examples/xiaomi-mi-tv-4a/debloat.txt`: one package per line, grouped with a comment that says
   what the group is, and a trailing comment on anything non-obvious ("re-enable for firmware
   updates", "needed for HDMI-CEC", etc.).
2. Put the exact model name, Android version and firmware build in the header so people can tell
   whether it matches theirs. `tools/tv.py --probe` prints all three.
3. Only list packages you have actually disabled and lived with for a few days. Note anything that
   broke and what you re-enabled.
4. Add a row to `examples/README.md`.
5. If your TV's Live TV app or stock launcher has a different package name, say so in the header;
   `tools/restore.sh` and `launcher/build-and-install.sh` accept them as arguments.

Never list packages to *uninstall*. Everything in this repo disables with `pm disable-user`, which
is reversible from the TV's own settings if the tools are gone.

## 2. Improve the launcher

The launcher lives in `launcher/` and is deliberately small: plain Java, AndroidX RecyclerView and
DynamicAnimation, no Kotlin, no Compose, no network. It targets TVs with 1 GB of RAM, so:

- No blur, no video backgrounds, no polling, no background services.
- Anything that depends on a specific TV goes in `app/src/main/res/values/config.xml`, not in code.
- Keep D-pad navigation deterministic. Test Up/Down/Left/Right from every edge.
- Verify on a real TV, not only the emulator. The easiest way is remote key injection plus a
  screenshot: `tools/tv.py 'input keyevent KEYCODE_DPAD_DOWN; screencap -p /sdcard/s.png'` then
  `tools/tv.py --pull /sdcard/s.png s.png`. Include a screenshot in your pull request.

Build with `TV_HOST=<ip> launcher/build-and-install.sh` (JDK 17, Android SDK platform 34 and
build-tools 34.0.0; on macOS `brew install openjdk@17 android-commandlinetools` then
`sdkmanager "platforms;android-34" "build-tools;34.0.0"`).

Ideas that fit the direction: an idle screensaver with clock and ambient colour, per-TV icon packs
for apps without banners, a settings screen to edit the dock from the TV, localisation.
Ideas that don't: recommendation rows, content feeds, ads, telemetry.

## 3. Improve the prompt and tools

`PROMPT.md` is what most people will actually use. If your assistant got stuck somewhere, the fix
usually belongs in the prompt as one extra sentence, not in a script. Keep it brand-neutral; put
brand-specific knowledge in `examples/`.

`tools/tv.py` and `tools/b64push.py` exist because the stock `adb` is blocked on recent macOS. If
you find a cleaner workaround, open an issue first. Note that this is no longer the whole story on
recent hardware — see the TODO below.

### TODO: STLS/TLS support in `tools/tv.py`

`tools/tv.py` **cannot connect to an Android 11+ TV at all.** `adb_shell` 0.4.4, the latest
release, implements neither the `STLS` handshake nor the pairing flow, so the client fails with
`InvalidCommandError: Unknown command: 'STLS'`. Until this is fixed, use the `adb` binary on those
sets: pair once from Developer options → Wireless debugging → Pair device with pairing code, then
connect on the port it displays. `README.md` has the commands.

An implementation got as far as the TLS handshake and stalled. The pieces below are what actually
got worked out, so nobody repeats the dead ends. Nothing from it was merged because it could not
be verified against real hardware.

- **The `STLS` wire constant is `0x534C5453`, not `0x45544841`.** The four bytes on the wire are
  the ASCII letters `STLS`, so the little-endian uint32 you compare against is `0x534C5453`. The
  big-endian reading looks right and matches nothing, and the device just resets the connection.
  Same convention as `CNXN` (`0x4e584e43`).
- **Subclass the device, don't patch the transport afterwards.** `AdbDeviceTcp` captures its
  transport in `_io_manager` during construction, so assigning `dev._transport` after the fact is
  silently ignored and you keep hitting the raw `STLS` error.
- **Wrap the socket once the `STLS` reply is consumed**, then let the existing AUTH token exchange
  run unchanged over the encrypted socket. No need to reimplement ADB auth.
- **AOSP wants an X.509 certificate for the ADB key** before it will finish the handshake. A
  self-signed cert over the public key in `tools/adbkey`, generated with `cryptography` (already a
  transitive dependency) and cached as `adbkey-cert.pem`, is the likely shape. No new dependency
  needed: stdlib `ssl` plus `cryptography`.
- **Settle the key question first — this is the part with real risk.** A device only trusts a key
  it has paired. `tools/adbkey` is generated locally and has never been paired with any TV, so
  testing against real hardware means pairing the Python client's *own* key. Borrowing the key
  that the `adb` binary already paired makes tests pass while telling you nothing, and leaves the
  two tools sharing one identity.
- **Still unresolved:** the handshake reached `ssl.SSLError: UNEXPECTED_EOF_WHILE_READING` — the
  device closes the connection mid-handshake. Next things to try are forcing TLS 1.2 (what AOSP's
  BoringSSL usually negotiates) and testing with and without SNI.

Verified against a TCL Smart TV Pro on Google TV / Android 14.

## Pull requests

- One topic per PR. Small is fine.
- Say which TV you tested on.
- No new dependencies in the launcher without discussing it in an issue.
- Be kind in reviews. Most contributors here are doing this for their parents.

By contributing you agree your work is released under the repo's MIT licence.
