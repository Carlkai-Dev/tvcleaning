#!/usr/bin/env python3
"""Read or set the launcher's Home tile order from a computer, instead of carrying tiles one by one.

  TV_HOST=<tv ip> tools/order.py get > order.txt     # the saved order, then every other TV app, one per line
  (edit order.txt: put the lines in the order you want, delete the ones you don't care about)
  TV_HOST=<tv ip> tools/order.py set order.txt       # Home shows those first, in that order
  TV_HOST=<tv ip> tools/order.py clear               # back to dock_apps and A to Z

A line is a package name (com.netflix.ninja) or a component (ar.tvplayer.tv/.ui.MainActivity); # starts a
comment. Apps you leave out keep following the usual rule after the listed ones. The order is saved as the
reorder button saves it, so it is also kept until dock_apps changes (a rebuild or a theme push with another dock).
`get > file` before a factory reset or a reinstall keeps a copy you can `set` again.
"""
import os
import re
import sys

PACKAGE = "com.gathin.tvhome"
URI = "content://" + PACKAGE + ".order"
ENTRY = re.compile(r"[A-Za-z0-9_.]+(/[A-Za-z0-9_.$]+)?")


def shell(dev, command):
    # adb_shell does not return the remote exit code: a final marker tells a failed command from an empty answer.
    output = dev.shell(command + "; rc=$?; printf '\\n__CYT_ORDER_RC__%s\\n' \"$rc\"", read_timeout_s=30)
    result = re.search(r"\n__CYT_ORDER_RC__(\d+)\s*$", output)
    if not result or result[1] != "0":
        raise RuntimeError("TV command failed or returned an incomplete response")
    text = output[:result.start()].strip()
    if re.search(r"(?mi)^\s*(Error|Exception|Permission Denial|Security exception)", text):
        raise RuntimeError(text.splitlines()[0] if text else "TV refused the command")
    return text


def call(dev, method, arg=None):
    """content call on OrderProvider; the Bundle comes back as {key=value, ...} text."""
    command = f"content call --uri {URI} --method {method}"
    if arg is not None:
        command += f" --arg '{arg}'"
    text = shell(dev, command)
    body = re.search(r"Bundle\[\{(.*)\}\]", text, re.S)
    if not body:
        raise RuntimeError("the launcher did not answer; is it installed and up to date? " + text[:200])
    return body[1]


def field(bundle, key):
    # Values are the last keys' text up to the next ", key=" (keys are fixed, values hold no "=").
    m = re.search(r"(?:^|, )" + key + r"=(.*?)(?=, [a-z]+=|$)", bundle, re.S)
    return m[1] if m else ""


def tv_apps(dev):
    """Packages with a TV launcher activity, in the TV's own order (the launcher's tiles come from these)."""
    text = shell(dev, "cmd package query-activities --brief -a android.intent.action.MAIN"
                      " -c android.intent.category.LEANBACK_LAUNCHER")
    seen = []
    for comp in re.findall(r"(?m)^\s*([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+\s*$", text):
        if comp != PACKAGE and comp not in seen:
            seen.append(comp)
    return seen


def entries(text):
    """Same rule as the launcher's Order.entries: lines or commas, # comments, blanks and repeats left out."""
    out = []
    for line in text.splitlines():
        for part in line.split("#", 1)[0].split(","):
            e = part.strip()
            if e and e not in out:
                out.append(e)
    return out


def main():
    args = sys.argv[1:]
    if not args or args[0] not in ("get", "set", "clear") or (args[0] == "set") != (len(args) == 2):
        print((__doc__ or "").strip(), file=sys.stderr)
        return 2
    wanted = []
    if args[0] == "set":
        with open(args[1], encoding="utf-8") as f:
            wanted = entries(f.read())
        bad = [e for e in wanted if not ENTRY.fullmatch(e)]
        if bad:
            print("Not a package or component: " + ", ".join(bad), file=sys.stderr)
            return 1
        if not wanted:
            print("The file lists no app.", file=sys.stderr)
            return 1
    # The caller may use Python -I, which leaves the script directory out of sys.path.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    dev = None
    try:
        from tv import connect
        dev = connect()
        if args[0] == "get":
            saved = [k for k in field(call(dev, "get"), "order").split("\n") if k]
            listed = {k.split("/", 1)[0] for k in saved}
            print("# Home tile order: first line = first tile. Packages or components, one per line.")
            for k in saved:
                print(k)
            rest = [p for p in tv_apps(dev) if p not in listed]
            if rest:
                print("# Not in the saved order (they follow by the usual rule):")
                for p in rest:
                    print(p)
        elif args[0] == "set":
            bundle = call(dev, "set", ",".join(wanted))
            unknown = [u for u in field(bundle, "unknown").split("\n") if u]
            print(f"Saved {field(bundle, 'saved')} tiles; Home shows them first.")
            if unknown:
                print("Not installed, left out: " + ", ".join(unknown))
        else:
            call(dev, "clear")
            print("Saved order removed: Home is back to dock_apps and A to Z.")
    except Exception as error:
        print(f"order.py failed: {error}", file=sys.stderr)
        return 1
    finally:
        if dev is not None:
            dev.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
