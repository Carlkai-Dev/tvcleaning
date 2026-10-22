#!/usr/bin/env python3
"""Hand remote buttons to the TV's own key settings on TCL Google TV, so they work with no key helper bound.

TCL's firmware (feature com.tcl.software.tinput_shortcut_config) answers the keycodes listed in the global
setting CustomKeyList with what CustomKey_<keycode> describes, before its fixed button handling and in any
app. This points each given keycode at the launcher's RemoteKeyActivity, which looks the button up in the
launcher's config at press time (app_keys, settings_key_keycodes, input_key_keycodes), so a theme push
changes what a button opens without running this again. No key-filtering accessibility service is involved,
so a held volume key keeps ramping (#763).

Usage, with TV_HOST set and the launcher installed (tools/tcl-keys.sh and tcl-keys.ps1 call this):
    tcl_keys.py 4062 4065 4099 83     the TV answers these buttons from now on, in place of the earlier set
    tcl_keys.py --off                 take back every button handed to the launcher
    tcl_keys.py --print 4062          print the script for the TV instead of running it

system_server parses these settings, and a bad value takes it down: CustomKeyList at every boot (it must
stay a JSON list of keycodes as strings), and a change to CustomKey_<n> while n is not in that list. So the
script on the TV removes the launcher's dropped entries first, then writes the list, and only then each
CustomKey_<n>; entries that name another app stay as they are, and a requested button that another app already
answers is refused before anything is written, so --off always puts back exactly what was there. Only vendor keycodes (above 288, where TCL's own
buttons live) and the Settings buttons (83 and 176) are accepted: a navigation, volume, power or media key
handed to the TV would stop working as that key.
"""
import os
import re
import sys

PACKAGE = "com.gathin.tvhome"
ACTIVITY = PACKAGE + "/.RemoteKeyActivity"
FEATURE = "com.tcl.software.tinput_shortcut_config"
# KEYCODE_NOTIFICATION, which TCL remotes send for the gear button, and KEYCODE_SETTINGS.
SETTINGS_KEYS = (83, 176)
# KEYCODE_PROFILE_SWITCH, the last keycode Android 12 defines; TCL's own buttons are 4000 and up.
LAST_ANDROID_KEYCODE = 288
# Older adbd takes the shell command in a single 4 KB message.
MAX_SCRIPT = 4000

# For each button: start RemoteKeyActivity with the keycode when it goes down; the long press and the release
# do nothing, so TCL's fixed handling never sees them either. Every behaviour needs an intent (the firmware
# dereferences it) and none names a package (that would need an "appnotinstall" entry as well).
SCRIPT = r"""want='@WANT@'
pkg=@PACKAGE@
act=$pkg/.RemoteKeyActivity
none='#Intent;end'
pm list features | grep -q '@FEATURE@' || { echo 'tcl-keys: this TV has no TCL key settings (@FEATURE@); nothing changed'; exit 0; }
if [ -n "$want" ] && ! cmd package resolve-activity --brief -n $act | grep -q "^$act\$"; then
  echo "tcl-keys: $act is not on the TV, so the buttons would go dead; install the launcher first. Nothing changed"
  exit 0
fi
for n in $want; do
  case "$(settings get global CustomKey_$n)" in
    null|*"$pkg/"*) ;;
    *) echo "tcl-keys: button $n already opens another app on this TV; nothing changed"; exit 0 ;;
  esac
done
old=$(settings get global CustomKeyList)
[ "$old" = null ] && old=
keep=
for n in $(echo "$old" | tr -c '0-9' ' '); do
  case " $want " in *" $n "*) continue ;; esac
  case "$(settings get global CustomKey_$n)" in
    *"$pkg/"*) settings delete global CustomKey_$n >/dev/null ;;
    *) keep="$keep $n" ;;
  esac
done
list=
for n in $keep $want; do list="$list,\"$n\""; done
if [ -n "$list" ]; then
  settings put global CustomKeyList "[${list#,}]"
elif [ -n "$old" ]; then
  settings delete global CustomKeyList >/dev/null
fi
sleep 1
for n in $want; do
  settings put global CustomKey_$n "{\"keycode\":$n,\"behavior\":[{\"keyaction\":1,\"type\":1,\"intent\":\"#Intent;component=$act;launchFlags=0x10000000;i.keycode=$n;end\"},{\"keyaction\":2,\"type\":0,\"intent\":\"$none\"},{\"keyaction\":3,\"type\":0,\"intent\":\"$none\"}]}"
done
echo "tcl-keys: done; CustomKeyList is now $(settings get global CustomKeyList)"
"""

USAGE = """usage: tcl_keys.py KEYCODE [KEYCODE ...]   hand these buttons to the TV, in place of the earlier set
       tcl_keys.py --off                     take back every button handed to the launcher
       tcl_keys.py --print KEYCODE ...       print the script for the TV instead of running it
"""


def keycodes(args):
    """The keycodes to hand over, checked and without repeats; ValueError says which one is refused."""
    codes = []
    for arg in args:
        if not re.fullmatch(r"[0-9]{1,6}", arg):
            raise ValueError(f"not a keycode: {arg!r}")
        code = int(arg)
        if code not in SETTINGS_KEYS and code <= LAST_ANDROID_KEYCODE:
            raise ValueError(f"keycode {code} is a standard Android key (navigation, volume, power, media...), "
                             "which would stop working as one; only vendor keycodes above "
                             f"{LAST_ANDROID_KEYCODE} and the Settings buttons {SETTINGS_KEYS} are accepted")
        if code not in codes:
            codes.append(code)
    return codes


def script(codes):
    """The shell script that makes the TV answer exactly these keycodes (none: take every one back)."""
    text = (SCRIPT.replace("@WANT@", " ".join(str(c) for c in codes))
            .replace("@PACKAGE@", PACKAGE).replace("@FEATURE@", FEATURE))
    if len(text.encode()) > MAX_SCRIPT:
        raise ValueError("too many keycodes for one adb command")
    return text


def main(argv):
    printing = bool(argv) and argv[0] == "--print"
    args = argv[1:] if printing else argv
    if args == ["--off"]:
        args = []
    elif not args or any(a.startswith("-") for a in args):
        sys.stderr.write(USAGE)
        return 2
    try:
        text = script(keycodes(args))
    except ValueError as e:
        sys.stderr.write(f"{e}\n{USAGE}")
        return 2
    if printing:
        sys.stdout.write(text)
        return 0
    # Imported here: tv.py wants TV_HOST and adb_shell, which --print and the tests do without.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tv
    dev = tv.connect()
    out = dev.shell(text, read_timeout_s=120.0)
    dev.close()
    sys.stdout.write(out if out.endswith("\n") else out + "\n")
    return 0 if "tcl-keys: done" in out else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
