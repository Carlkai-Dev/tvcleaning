# One-session observations: China TCL / FFALCON MT5879

These observations come from one owner-authorized configuration session on
2026-10-22/23. They are not a several-day debloat list or a guarantee for another
TV. The retail model was not established; do not label the unit Q10G Pro simply
because it shares platform strings with that example.

| Public applicability field | Observed value |
|---|---|
| Platform / product | `tcl_mt5879_cn` / `tcl_mt5879_cn_db` |
| Android / API | Android 11 / 30 |
| Build / incremental | `RQ3A.210705.001` / `2026012200` |
| Userspace ABIs | `armeabi-v7a,armeabi` |
| Stock Home | `com.tcl.cyberui` |
| TV/input package retained | `com.tcl.tv` |
| Promotional art package | `com.tcl.appreciate.art` |

## ADB and installation

The owner enabled ADB from the device-information screen with the directional
sequence described in examples/tcl-q10g-pro/debloat.txt. The reachable ADB port
and the particular process able to connect were separate questions: a terminal
connection worked when another client context could not reach the TV. Reusing
tools/relay.py allowed the authorized client to operate over loopback.

Ordinary ADB/pm installs returned `INSTALL_FAILED_VERIFICATION_FAILURE`.
Changing the two generic verifier globals did not resolve the observed rejection;
the settings were restored. This is a firmware constraint, not an ABI error.

Related upstream discussions:

- [#376: installation and Home on the same platform](https://github.com/Gathin23/cleanyourtv/issues/376)
- [#743: separate InstallConfig proposal](https://github.com/Gathin23/cleanyourtv/issues/743)

Those issue proposals do not override this repository's protected-package rules
or authorize installation-policy changes. No bypass implementation is included
in this skill. Successful app installation later in the session does not prove
that unmodified upstream installation tools accept the same APK on this firmware.

## Home, boot and input are different acceptance checks

Selecting a preferred Home and opening the replacement were insufficient: a
subsequent cold boot initially landed on the vendor desktop. Disabling stock Home
caused a boot-progress failure during the session; restoring its enabled state
recovered operation. Keep it available and do not repeat that experiment.

A local direct-boot-aware Home entry plus a bounded boot receiver subsequently
returned to the custom desktop after a real reboot. This was a local adaptation,
not an upstream launcher feature or a general recommendation. Its source and
permissions are not proposed by this documentation PR. The owner separately
confirmed Home, volume, the source menu and a game console's HDMI picture/sound.
An app switching to the foreground through ADB would not have proved those keys.

## Art screensaver versus home wallpaper

The paid-art surface belonged to `com.tcl.appreciate.art`; merely installing a
landscape app or changing the desktop did not remove that surface. It was disabled
in this session at the owner's request. The selected screensaver was changed to
Aerial Views, `com.neilturner.aerialviews/.ui.screensaver.DreamActivity`, through the
standard screensaver settings. Check actual settings and idle behavior separately.

The owner also requested a moving home background. A local launcher adaptation
used a muted local video and released the decoder when leaving Home or turning
the screen off. Leaving for another app and returning resumed its position. This
is a recorded custom behavior only: upstream deliberately excludes video and
background services. Neither that fork nor Apple video assets are in this PR.

## Netflix: partial success, not verified playback

Tracked in [#1008](https://github.com/Gathin23/cleanyourtv/issues/1008).

| Client and observation | Outcome |
|---|---|
| Original-signed TV 13.1.3, build 26073 | Owner reported UI-800-3 (307006) |
| Original-signed TV 10.1.0, build 18316 | Same error reported |
| Original-signed TV 11.0.8, code 19823, from a TCL tutorial | Same error reported |
| TV 8.3.10, code 12018 | Installed; no owner-confirmed playback result |
| Original-signed mobile/tablet 8.143.3, code 52204 | Launched and reached login |
| Mobile client with normal D-pad | Owner could not operate the interface |
| MATVT 1.0.6 | Pointer appeared, but owner reported confirmation did not click |
| Session-specific, Netflix-scoped pointer helper | Owner confirmed clicking and entering login |
| Account login and video picture/sound | **Not verified** |

The mobile package is `com.netflix.mediaclient`; TV is `com.netflix.ninja`, so
these clients can coexist. The mobile test used base, ARMv7, density and language
splits. Every APK was verified against the original Netflix signer, SHA-256:
`363863596ea99241eb71b1a985553aa604de3ea3c5f0c546742390e682164e6b`.
This is a public signing-certificate fingerprint, not a credential; it is evidence
for these files, not an assertion that future certificate rotation is invalid.

Zero-byte screenshots and empty UI dumps were inconclusive. The full numeric
suffix mattered: Netflix's [307006 help page](https://help.netflix.com/en/node/112756)
describes a hardware-related troubleshooting path. That does not establish that
this unit is physically damaged, prove missing certification, or justify a
factory reset. The repeated failure on TV clients is an observation, not proof
that every mobile client will fail or that another TV version cannot work.

The pointer helper used direct accessibility-node clicks without requiring
pre-existing input focus, with a touch fallback that removed its overlay during
the gesture. It was scoped to the mobile Netflix package and passed Home/input
keys through. A physical confirmation, rather than injected input, established
that the login button could be clicked. Its code is not part of the reference
launcher and is not supplied by this skill; do not claim the upstream launcher
provides this behavior. Keyboard use, playback and long-term helper stability
remain outside the confirmed result.
