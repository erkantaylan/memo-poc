#!/usr/bin/env python3
"""Produce the credential blob the Android app pastes into its Connect screen.

Combines the OAuth client (id + secret) with the refresh token gog already
holds, into one base64 line.

  make_app_credentials.py                    print the blob
  make_app_credentials.py --push             type it straight into the app over adb
  make_app_credentials.py --out creds.txt    write it to a file

The blob is a bearer credential for the library folder. It is never printed to
logs, and --push types it directly into the device rather than leaving it in
shell history.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile

ACCOUNT = os.environ.get("GOG_ACCOUNT", "etaylan13@gmail.com")
GOG = os.path.expanduser("~/go/bin/gog")
if not os.path.exists(GOG):
    GOG = "gog"
ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
if not os.path.exists(ADB):
    ADB = "adb"
APP_ID = "com.erkantaylan.kitaplik.debug"


def die(msg: str):
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(1)


def run(cmd: list[str]) -> str:
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        die(f"{' '.join(cmd[:3])}…\n{(proc.stderr or proc.stdout).strip()}")
    return proc.stdout


def find(node, *keys):
    """Depth-first search for the first of `keys` present in nested dicts."""
    if isinstance(node, dict):
        for k in keys:
            if isinstance(node.get(k), str) and node[k]:
                return node[k]
        for v in node.values():
            found = find(v, *keys)
            if found:
                return found
    elif isinstance(node, list):
        for v in node:
            found = find(v, *keys)
            if found:
                return found
    return None


def load_client_json(explicit: str | None, wanted_id: str | None) -> dict:
    """Find the downloaded Cloud Console client JSON that carries the secret."""
    candidates = []
    if explicit:
        candidates = [explicit]
    else:
        import glob
        for pattern in ("~/Downloads/client_secret*.json",
                        "~/Downloads/*/client_secret*.json"):
            candidates += glob.glob(os.path.expanduser(pattern))
        candidates.sort(key=lambda p: os.path.getmtime(p), reverse=True)

    if not candidates:
        die("no client_secret_*.json found in ~/Downloads — pass one with --client")

    for path in candidates:
        try:
            with open(path) as fh:
                blob = json.load(fh)
        except (OSError, json.JSONDecodeError):
            continue
        client = blob.get("installed") or blob.get("web") or blob
        cid, secret = client.get("client_id"), client.get("client_secret")
        if not cid or not secret:
            continue
        # Prefer the file whose secret is actually current: newest wins, and if
        # we know the id gog uses, it must match.
        if wanted_id and cid != wanted_id:
            continue
        return {"client_id": cid, "client_secret": secret, "_path": path}

    die("none of the client_secret_*.json files match the client gog is using "
        f"({wanted_id}) — pass the right one with --client")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--account", default=ACCOUNT)
    ap.add_argument("--client", help="path to the downloaded client_secret_*.json "
                                     "(gog keeps the secret in the keyring, not on disk)")
    ap.add_argument("--push", action="store_true",
                    help="type the blob into the app's Connect screen over adb")
    ap.add_argument("--out", help="write the blob to this file")
    args = ap.parse_args()

    # 1. Which OAuth client is gog using? Its own file holds only the id —
    #    the secret is in the keyring, so the secret comes from the client JSON
    #    that was downloaded from Cloud Console.
    listed = json.loads(run([GOG, "auth", "credentials", "list", "--json"]))
    gog_path = find(listed, "path")
    if not gog_path or not os.path.isfile(gog_path):
        die("cannot locate gog's credentials.json — run `gog auth credentials set` first")
    with open(gog_path) as fh:
        wanted_id = find(json.load(fh), "client_id")

    client = load_client_json(args.client, wanted_id)
    client_id = client["client_id"]
    client_secret = client["client_secret"]
    if client_id != wanted_id:
        die(f"that client JSON is for a different client than gog is using")

    # 2. The refresh token gog holds for this account.
    with tempfile.TemporaryDirectory() as tmp:
        token_file = os.path.join(tmp, "token.json")
        run([GOG, "auth", "tokens", "export", args.account, "--out", token_file])
        if not os.path.isfile(token_file):
            die("gog auth tokens export produced no file")
        with open(token_file) as fh:
            exported = json.load(fh)
    refresh_token = find(exported, "refresh_token", "refreshToken")
    if not refresh_token:
        die("no refresh_token in the exported token file")

    payload = {
        "client_id": client_id,
        "client_secret": client_secret,
        "refresh_token": refresh_token,
    }
    blob = base64.b64encode(json.dumps(payload).encode()).decode()

    if args.out:
        with open(args.out, "w") as fh:
            fh.write(blob + "\n")
        os.chmod(args.out, 0o600)
        print(f"wrote {args.out} ({len(blob)} chars, mode 600)")
    elif args.push:
        focus = run([ADB, "shell", "dumpsys window | grep -m1 mCurrentFocus"])
        if APP_ID not in focus:
            die(f"{APP_ID} is not focused — open the Connect screen first.\n"
                f"Focused: {focus.strip().split('mCurrentFocus=')[-1]}")
        # adb input text needs %s for spaces; base64 has none, but it does use
        # '+' and '/', which are safe, and '=' padding, which is not escaped.
        run([ADB, "shell", "input", "text", blob])
        print(f"typed {len(blob)} chars into {APP_ID}")
    else:
        print(blob)


if __name__ == "__main__":
    main()
