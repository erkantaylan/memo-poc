#!/usr/bin/env python3
"""drive_sync — push the library to Google Drive and record the file ids.

Uploads every catalog entry that is missing or changed, writes the resulting
Drive file id back into catalog.json, then uploads the catalog itself so the
app can find everything from one fetch.

  drive_sync.py --library ../library            upload what has changed
  drive_sync.py --library ../library --dry-run  show what would happen
  drive_sync.py --library ../library --relink   re-read ids without uploading

Drive is kept FLAT: each file is stored as "<item-id>.<ext>" in a single
folder. The category tree lives in catalog.json, not in Drive, so there are no
subfolders to create, no paths to keep in sync, and mapping an entry to its
Drive object is a direct lookup.

Requires `gog` authorized for drive.file — see docs/drive-setup.md.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time

FOLDER_NAME = "kitaplik-library"
GOG = os.path.expanduser("~/go/bin/gog")
if not os.path.exists(GOG):
    GOG = "gog"


def die(msg: str, code: int = 1):
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(code)


def gog(*args: str, parse_json: bool = True):
    cmd = [GOG, *args]
    if parse_json and "--json" not in args:
        cmd.append("--json")
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        detail = (proc.stderr or proc.stdout).strip()
        die(f"{' '.join(cmd[1:])}\n{detail}")
    if not parse_json:
        return proc.stdout
    try:
        return json.loads(proc.stdout or "null")
    except json.JSONDecodeError:
        die(f"expected JSON from `gog {' '.join(args)}`, got:\n{proc.stdout[:400]}")


def dig_id(payload) -> str | None:
    """Pull a Drive file id out of whatever shape gog returned."""
    if isinstance(payload, str):
        return payload or None
    if isinstance(payload, dict):
        for key in ("id", "fileId", "driveId"):
            value = payload.get(key)
            if isinstance(value, str) and value:
                return value
        for key in ("file", "result", "data", "item"):
            if key in payload:
                found = dig_id(payload[key])
                if found:
                    return found
    if isinstance(payload, list) and payload:
        return dig_id(payload[0])
    return None


def as_list(payload) -> list:
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for key in ("files", "items", "results", "data", "accounts", "entries", "children"):
            value = payload.get(key)
            if isinstance(value, list):
                return value
    return []


def ensure_account():
    accounts = gog("auth", "list")
    entries = as_list(accounts)
    if not entries:
        die(
            "no authorized account. Run:\n"
            "  gog auth credentials set ~/Downloads/client_secret_*.json\n"
            "  gog auth add you@gmail.com --services drive --drive-scope file"
        )
    return entries


def find_folder() -> str | None:
    listing = gog("drive", "ls", "--max", "200")
    for entry in as_list(listing):
        name = entry.get("name") or entry.get("title")
        mime = entry.get("mimeType", "")
        if name == FOLDER_NAME and "folder" in mime:
            return entry.get("id")
    return None


def remote_index(folder_id: str) -> dict[str, str]:
    """name -> file id for everything already in the Drive folder."""
    listing = gog("drive", "ls", "--parent", folder_id, "--max", "1000")
    index = {}
    for entry in as_list(listing):
        name = entry.get("name") or entry.get("title")
        if name and entry.get("id"):
            index[name] = entry["id"]
    return index


def remote_name(item: dict) -> str:
    return f"{item['id']}.{item['format']}"


def upload_with_retry(local: str, folder_id: str, name: str, attempts: int = 3):
    """Uploads fail transiently on a long run (http2 header timeouts, 5xx).
    Retry a few times rather than abandoning a 500 MB sync mid-way."""
    delay = 3
    for attempt in range(1, attempts + 1):
        proc = subprocess.run(
            [GOG, "drive", "upload", local, "--parent", folder_id, "--name", name, "--json"],
            capture_output=True, text=True,
        )
        if proc.returncode == 0:
            try:
                return dig_id(json.loads(proc.stdout or "null")), None
            except json.JSONDecodeError:
                return None, "upload succeeded but returned unparseable JSON"
        detail = (proc.stderr or proc.stdout).strip().splitlines()[-1:] or ["unknown error"]
        if attempt == attempts:
            return None, detail[0][:160]
        time.sleep(delay)
        delay *= 2
    return None, "exhausted retries"


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--library", default="../library", help="directory holding catalog.json")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--relink", action="store_true",
                    help="only refresh drive ids from what is already uploaded")
    ap.add_argument("--prune", action="store_true",
                    help="delete Drive files the catalog no longer references")
    args = ap.parse_args()

    library = os.path.abspath(args.library)
    catalog_path = os.path.join(library, "catalog.json")
    if not os.path.isfile(catalog_path):
        die(f"no catalog at {catalog_path} — run build-catalog.mjs first")

    with open(catalog_path) as fh:
        catalog = json.load(fh)
    items = catalog["items"]

    ensure_account()

    folder_id = find_folder()
    if folder_id:
        print(f"folder:  {FOLDER_NAME} ({folder_id})")
    elif args.dry_run:
        print(f"folder:  {FOLDER_NAME} (would be created)")
        folder_id = "<new>"
    else:
        gog("drive", "mkdir", FOLDER_NAME)
        # Don't trust mkdir's response shape — ask Drive what now exists.
        folder_id = find_folder()
        if not folder_id:
            die(f"created {FOLDER_NAME} but cannot find it when listing")
        print(f"folder:  {FOLDER_NAME} ({folder_id}) [created]")

    existing = remote_index(folder_id) if folder_id != "<new>" else {}
    print(f"remote:  {len(existing)} file(s) already there\n")

    uploaded = skipped = 0
    failures: list[tuple[dict, str]] = []
    for item in items:
        name = remote_name(item)
        local = os.path.join(library, item["path"])

        if not os.path.isfile(local):
            print(f"  MISSING  {item['title'][:50]} ({item['path']})")
            continue

        if name in existing:
            item["drive_id"] = existing[name]
            skipped += 1
            print(f"  have     {item['format']:5} {item['title'][:52]}")
            continue

        if args.relink:
            print(f"  unlinked {item['format']:5} {item['title'][:52]}")
            continue

        size_mb = item["bytes"] / 1024 / 1024
        if args.dry_run:
            print(f"  UPLOAD   {item['format']:5} {size_mb:6.1f} MB  {item['title'][:40]}")
            uploaded += 1
            continue

        print(f"  upload   {item['format']:5} {size_mb:6.1f} MB  {item['title'][:40]} ... ",
              end="", flush=True)
        file_id, error = upload_with_retry(local, folder_id, name)
        if not file_id:
            print(f"FAILED: {error}")
            failures.append((item, error))
            continue
        item["drive_id"] = file_id
        uploaded += 1
        print(file_id)

    if args.dry_run:
        print(f"\ndry run: {uploaded} would upload, {skipped} already present")
        return

    with open(catalog_path, "w") as fh:
        json.dump(catalog, fh, indent=2)
        fh.write("\n")
    print(f"\ncatalog: wrote drive ids into {catalog_path}")

    # The catalog goes up last, so it always describes files that already exist.
    catalog_name = "catalog.json"
    if catalog_name in existing:
        gog("drive", "upload", catalog_path, "--replace", existing[catalog_name])
        print(f"catalog: replaced on Drive ({existing[catalog_name]})")
    else:
        cid = dig_id(gog("drive", "upload", catalog_path,
                         "--parent", folder_id, "--name", catalog_name))
        print(f"catalog: uploaded to Drive ({cid})")

    if args.prune:
        wanted = {remote_name(i) for i in items} | {"catalog.json"}
        fresh = remote_index(folder_id)
        orphans = {n: fid for n, fid in fresh.items() if n not in wanted}
        if orphans:
            print(f"\npruning {len(orphans)} file(s) no longer in the catalog:")
            for name, fid in sorted(orphans.items()):
                print(f"  delete {name[:70]}")
                subprocess.run([GOG, "drive", "delete", fid, "-y"],
                               capture_output=True, text=True)
        else:
            print("\nnothing to prune")

    linked = sum(1 for i in items if i.get("drive_id"))
    print(f"\ndone: {uploaded} uploaded, {skipped} already present, "
          f"{linked}/{len(items)} entries linked to Drive")

    if failures:
        print(f"\n{len(failures)} failed (re-run to retry just these):")
        for item, error in failures:
            print(f"  {item['format']:5} {item['title'][:44]}\n        {error}")


if __name__ == "__main__":
    main()
