---
name: tcl-tv-configuration
description: Record and troubleshoot owner-authorized TCL or FFALCON Android TV configuration, including Home and screensaver handover, sideload compatibility, and physical-remote verification. Use for these TVs or for preparing a sanitized configuration report; not a general firmware unlock or streaming certification method.
---

# TCL / FFALCON TV configuration

Use this workflow for a requested configuration change or a reproducible record of one.
Work in the CleanYourTV checkout and follow its AGENTS.md and PROMPT.md for live
operations. This skill adds device-specific decision points and reporting; it does
not change protected packages, installation policy, or the default cleanup flow.

Read [the observed session](references/device-session.md) when the device reports
`tcl_mt5879_cn`, or when investigating Home reverting at boot, art screensavers,
empty streaming screenshots, split APKs, or Netflix UI-800-3 (307006).

## Establish the target and preserve recovery

- Confirm the owner-provided target and authorized ADB connection. Keep addresses,
  connection keys, installed-app inventories and backups local. Use tools/tv.py and
  tools/relay.py as documented in tools/relay.md when the ordinary client cannot
  reach the TV. A shell's network failure is not proof that the TV is offline.
- Read the model/platform, API, firmware and supported userspace ABIs. A platform
  string is not a retail model. Android 11 or a 64-bit CPU does not imply arm64
  applications can run: the observed unit supports only 32-bit ARM userspace.
- Back up relevant package, Home, screensaver and setting states before changes,
  using the repository's tools. Record absent values as absent, not as zero.
- Confirm which apps and features to retain. Keep input/HDMI, volume, remote,
  keyboard, voice/casting and updates unless the owner's request says otherwise
  and the repository permits the particular change.
- Reuse an existing model's notes instead of assuming every TCL firmware behaves
  alike. The reference list is examples/tcl-q10g-pro/debloat.txt, not an automatic
  prescription for an unidentified retail model.

## Diagnose one layer at a time

1. **Installation:** verify source, package/version, minimum API, ABIs and signing
   certificate. A split package needs its base and compatible required splits;
   verify every split. Do not treat a same-package downgrade as a harmless data
   reset. Follow the repository's stop condition on verification rejection; do not
   disable the package installer or edit vendor installation policy as a retry.
2. **Home:** separate an explicit Activity launch, preferred HOME, a physical Home
   press, standby/wake and cold boot. Keep stock Home available on this platform.
   Do not disable it to compensate for a reboot landing on the vendor launcher.
3. **Screensaver:** check the selected Dream component and which package actually
   owns the promotion. An installed landscape screensaver does not establish that
   it is selected or that an ad carrier has stopped. Dismiss an active Dream before
   diagnosing an app apparently opening behind it.
4. **Streaming:** report installation, launch/login, physical navigation and actual
   playback independently. A protected/zero-byte screenshot is inconclusive; ask
   for the displayed error or an owner-confirmed picture-and-sound check. Search
   the full error including its numeric suffix before applying generic advice.
5. **Remote:** an injected key bypassing an accessibility helper is not acceptance
   evidence. Ask for a real remote press. Preserve existing accessibility services
   and original AppOps when inspecting or changing an explicitly requested helper.
   A mobile app reaching login is not proof of D-pad support or playback.

## Verify and report

- After each accepted change, check actual package/component and original-state
  recovery, then obtain the physical checks relevant to that change: Home,
  volume, source menu, HDMI picture/sound and any requested streaming playback.
- Do not reboot into an unresolved boot-path change or combine a new key helper
  with a Home handover before testing them separately.
- An installation timeout is not automatically a failed install. First inspect
  installed version and required splits; do not issue a second concurrent install.
- Leave a local rollback record covering each changed setting, component and
  helper. Restore the original value rather than a guessed default.
- At handoff, follow the repository's ADB-close procedure; if the owner explicitly
  wants a continued debugging session, record that remaining step privately.
- Before a public issue/PR, author a minimal summary in a clean checkout and review
  only the intended staged files. Do not stage a whole operational workspace.
  Exclude VPN/subscription/node content, accounts, controller secrets, app data,
  raw logs, screenshots, private addresses, device identifiers, local user paths,
  binaries, downloaded third-party documents and ADB keys. Public package names,
  platform/firmware and error codes are sufficient for this report.
- Check existing issues first. Link an existing installation/Home report rather
  than duplicating it. Distinguish a documentation request from an upstream bug,
  especially where the observed behavior belongs to a third-party app or fork.
