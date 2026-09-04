#!/usr/bin/env python3
"""kctl — drive the Kitaplık app on a connected Android device.

Targets UI elements by test tag or visible text rather than pixel
coordinates, by reading the accessibility tree, so commands keep working when
the layout changes.

  kctl status                  device, focused app, library server
  kctl launch|stop|restart     app lifecycle
  kctl ui [--all]              list addressable elements
  kctl shot [path]             screenshot (refuses unless the app is focused)
  kctl tap <target>            tap by tag substring or visible text
  kctl long <target>           long press
  kctl type <text>             type into the focused field
  kctl key <KEYCODE>           e.g. BACK, ENTER, DEL
  kctl scroll up|down [n]
  kctl logs [pattern] [-n N]   app logcat
  kctl files                   what the app has downloaded
  kctl wait <target> [--timeout S]

Exit codes: 0 ok, 1 not found / refused, 2 usage.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APP_ID = "com.erkantaylan.kitaplik.debug"
ACTIVITY = "com.erkantaylan.kitaplik.MainActivity"
LIBRARY_PORT = 8090
ADB = os.environ.get("ADB", os.path.expanduser("~/Android/Sdk/platform-tools/adb"))
if not os.path.exists(ADB):
    ADB = "adb"


def adb(*args: str, binary: bool = False, check: bool = True):
    proc = subprocess.run(
        [ADB, *args],
        capture_output=True,
        text=not binary,
        check=False,
    )
    if check and proc.returncode != 0:
        err = proc.stderr if not binary else proc.stderr.decode("utf-8", "replace")
        die(f"adb {' '.join(args)} failed: {err.strip()}")
    return proc.stdout


def shell(cmd: str) -> str:
    return adb("shell", cmd)


def die(msg: str, code: int = 1):
    print(msg, file=sys.stderr)
    sys.exit(code)


def focused_window() -> str:
    out = shell("dumpsys window | grep -m1 mCurrentFocus")
    return out.strip()


def require_focus():
    win = focused_window()
    if APP_ID not in win:
        die(
            "Refusing: Kitaplık is not the focused app.\n"
            f"Focused: {win.split('mCurrentFocus=')[-1]}\n"
            "Run `kctl launch` first, or wait until the phone is free."
        )


# --------------------------------------------------------------- UI hierarchy

class Node:
    __slots__ = ("tag", "text", "desc", "cls", "bounds", "clickable", "scrollable")

    def __init__(self, el: ET.Element):
        self.tag = el.get("resource-id", "") or ""
        self.text = el.get("text", "") or ""
        self.desc = el.get("content-desc", "") or ""
        self.cls = (el.get("class", "") or "").split(".")[-1]
        self.clickable = el.get("clickable") == "true"
        self.scrollable = el.get("scrollable") == "true"
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", el.get("bounds", "") or "")
        self.bounds = tuple(int(g) for g in m.groups()) if m else (0, 0, 0, 0)

    @property
    def centre(self) -> tuple[int, int]:
        x1, y1, x2, y2 = self.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    @property
    def label(self) -> str:
        return self.tag or self.text or self.desc or self.cls

    def matches(self, needle: str) -> bool:
        n = needle.lower()
        return n in self.tag.lower() or n in self.text.lower() or n in self.desc.lower()

    def __str__(self) -> str:
        x, y = self.centre
        bits = []
        if self.tag:
            bits.append(f"#{self.tag}")
        if self.text:
            bits.append(repr(self.text[:60]))
        if self.desc and self.desc != self.text:
            bits.append(f"desc={self.desc[:40]!r}")
        flags = "".join(c for c, on in (("C", self.clickable), ("S", self.scrollable)) if on)
        return f"  [{flags:<2}] ({x:>4},{y:>4}) {' '.join(bits)}"


def dump_ui() -> list[Node]:
    """Read the accessibility tree. Retries: the dump races with animations."""
    for attempt in range(4):
        out = shell("uiautomator dump /sdcard/.kctl.xml >/dev/null 2>&1; cat /sdcard/.kctl.xml")
        xml = out[out.find("<?xml"):] if "<?xml" in out else ""
        if xml.strip():
            try:
                root = ET.fromstring(xml)
            except ET.ParseError:
                time.sleep(0.4)
                continue
            return [Node(el) for el in root.iter("node")]
        time.sleep(0.4)
    die("Could not read the UI hierarchy (uiautomator dump returned nothing).")


def find(needle: str, clickable_only: bool = False) -> Node:
    nodes = dump_ui()
    matches = [n for n in nodes if n.matches(needle)]
    if clickable_only:
        clickable = [n for n in matches if n.clickable]
        # A Compose row is clickable at the container, with the text inside it.
        matches = clickable or matches
    if not matches:
        die(f"No element matching {needle!r}. Try `kctl ui` to see what is on screen.")
    return matches[0]


# ------------------------------------------------------------------- commands

def cmd_status(args):
    devices = [l for l in adb("devices").splitlines()[1:] if l.strip()]
    print("device:  " + (devices[0] if devices else "NONE CONNECTED"))
    print("focused: " + (focused_window().split("mCurrentFocus=")[-1] or "?"))
    pid = shell(f"pidof {APP_ID}").strip()
    print(f"app:     {'running pid ' + pid if pid else 'not running'}")
    reverse = adb("reverse", "--list", check=False) or ""
    print(f"tunnel:  {'tcp:%d ok' % LIBRARY_PORT if str(LIBRARY_PORT) in reverse else 'NOT SET'}")


def cmd_launch(args):
    shell(f"monkey -p {APP_ID} -c android.intent.category.LAUNCHER 1")
    time.sleep(1.5)
    print(f"launched {APP_ID}")


def cmd_stop(args):
    shell(f"am force-stop {APP_ID}")
    print(f"stopped {APP_ID}")


def cmd_restart(args):
    cmd_stop(args)
    time.sleep(0.5)
    cmd_launch(args)


def cmd_ui(args):
    require_focus()
    nodes = dump_ui()
    if not args.all:
        nodes = [n for n in nodes if n.tag or n.text or n.desc]
    print(f"{len(nodes)} elements (C=clickable S=scrollable):")
    for n in nodes:
        print(n)


def cmd_shot(args):
    require_focus()
    path = args.path or "/tmp/kitaplik-shot.png"
    data = adb("exec-out", "screencap", "-p", binary=True)
    with open(path, "wb") as fh:
        fh.write(data)
    print(f"{path} ({len(data)} bytes)")


def cmd_tap(args):
    require_focus()
    node = find(args.target, clickable_only=True)
    x, y = node.centre
    shell(f"input tap {x} {y}")
    print(f"tapped {node.label} at ({x},{y})")


def cmd_long(args):
    require_focus()
    node = find(args.target, clickable_only=True)
    x, y = node.centre
    shell(f"input swipe {x} {y} {x} {y} 800")
    print(f"long pressed {node.label} at ({x},{y})")


def cmd_type(args):
    require_focus()
    escaped = args.text.replace(" ", "%s").replace("'", r"\'")
    shell(f"input text '{escaped}'")
    print(f"typed {args.text!r}")


def cmd_key(args):
    code = args.keycode.upper()
    if not code.startswith("KEYCODE_"):
        code = "KEYCODE_" + code
    shell(f"input keyevent {code}")
    print(f"sent {code}")


def cmd_scroll(args):
    require_focus()
    size = shell("wm size").strip().split(":")[-1].strip()
    w, h = (int(v) for v in size.split("x"))
    x = w // 2
    for _ in range(args.times):
        if args.direction == "down":
            shell(f"input swipe {x} {int(h * 0.72)} {x} {int(h * 0.28)} 250")
        else:
            shell(f"input swipe {x} {int(h * 0.28)} {x} {int(h * 0.72)} 250")
        time.sleep(0.3)
    print(f"scrolled {args.direction} x{args.times}")


def cmd_logs(args):
    pid = shell(f"pidof {APP_ID}").strip().split(" ")[0]
    if not pid:
        die("App is not running.")
    out = adb("logcat", "-d", f"--pid={pid}", "-t", str(args.lines), check=False)
    if args.pattern:
        out = "\n".join(l for l in out.splitlines() if re.search(args.pattern, l, re.I))
    print(out or "(no matching log lines)")


def cmd_files(args):
    out = shell(f"run-as {APP_ID} ls -l files/library 2>/dev/null")
    print(out.strip() or "(nothing downloaded, or run-as is unavailable on this build)")


def cmd_wait(args):
    deadline = time.time() + args.timeout
    while time.time() < deadline:
        if any(n.matches(args.target) for n in dump_ui()):
            print(f"found {args.target!r}")
            return
        time.sleep(1)
    die(f"Timed out after {args.timeout}s waiting for {args.target!r}")


def main():
    p = argparse.ArgumentParser(prog="kctl", description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("status").set_defaults(fn=cmd_status)
    sub.add_parser("launch").set_defaults(fn=cmd_launch)
    sub.add_parser("stop").set_defaults(fn=cmd_stop)
    sub.add_parser("restart").set_defaults(fn=cmd_restart)

    ui = sub.add_parser("ui"); ui.add_argument("--all", action="store_true")
    ui.set_defaults(fn=cmd_ui)

    shot = sub.add_parser("shot"); shot.add_argument("path", nargs="?")
    shot.set_defaults(fn=cmd_shot)

    tap = sub.add_parser("tap"); tap.add_argument("target"); tap.set_defaults(fn=cmd_tap)
    lp = sub.add_parser("long"); lp.add_argument("target"); lp.set_defaults(fn=cmd_long)
    ty = sub.add_parser("type"); ty.add_argument("text"); ty.set_defaults(fn=cmd_type)
    ky = sub.add_parser("key"); ky.add_argument("keycode"); ky.set_defaults(fn=cmd_key)

    sc = sub.add_parser("scroll")
    sc.add_argument("direction", choices=["up", "down"])
    sc.add_argument("times", nargs="?", type=int, default=1)
    sc.set_defaults(fn=cmd_scroll)

    lg = sub.add_parser("logs")
    lg.add_argument("pattern", nargs="?")
    lg.add_argument("-n", "--lines", type=int, default=200)
    lg.set_defaults(fn=cmd_logs)

    sub.add_parser("files").set_defaults(fn=cmd_files)

    wt = sub.add_parser("wait")
    wt.add_argument("target")
    wt.add_argument("--timeout", type=float, default=30)
    wt.set_defaults(fn=cmd_wait)

    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
