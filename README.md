# Kitaplık

Personal library reader for Android. Kotlin + Jetpack Compose, no cross-platform
runtime. Fetches a catalog describing every book in the library, downloads what
you want to read, and reads EPUB and markdown in the app; PDFs go out to
whatever reader you already use.

Architecture and the reasoning behind it: **[docs/architecture.md](docs/architecture.md)**.
Drive setup: **[docs/drive-setup.md](docs/drive-setup.md)**.

## What it does

- **Home** — what you are in the middle of, what you opened recently, and every
  bookmark, newest first. Tapping any of them resumes where you left off.
- **Cloud** — the whole library. Search by title, author or shelf; filter by
  format. Tap to download, tap again to read.
- **On device** — the same list narrowed to what has been downloaded.
- **Reader** — EPUB and markdown, with A−/A+, live words-per-minute and time
  remaining, and long-press to bookmark the word under your finger.
- **Settings** — where the library is coming from, counts, and disconnect.

## Dev loop

```bash
./dev.sh              # serve the local library, open the tunnel, build, install, launch
./dev.sh --logs       # ...and tail logcat
```

`dev.sh` reconnects the phone if it has dropped off Wi-Fi. Builds are a few
seconds; the first one after a clean is a couple of minutes.

The app talks to Drive in normal use. The dev server exists for working without
credentials: the Connect screen offers "Use the dev server instead", which
points at `../library` over an `adb reverse` tunnel on port 8090, so the device
talks to `localhost` and nothing depends on the machine's LAN address.

## Driving the app from a shell

`./kctl` addresses elements by test tag or visible text, reading the
accessibility tree rather than guessing coordinates. Compose test tags are
exposed to UiAutomator as resource ids, so targets survive layout changes.

```bash
./kctl status                 # device, focused app, tunnel
./kctl ui                     # what is on screen, with tap points
./kctl tap base-pdf           # by tag substring or visible text
./kctl long base-md           # long press
./kctl tap search             # then: ./kctl type "murakami"
./kctl scroll down 5
./kctl shot out.png
./kctl files                  # what has been downloaded
./kctl logs -n 100
```

Screenshots and taps **refuse to run unless Kitaplık is the focused app**, so
tooling can never capture or disturb anything else on the phone.

The same commands are exposed over MCP by `tools/mcp_server.py`, registered in
`../.mcp.json` (stdlib only; screenshots come back as images). MCP servers load
at session start, so it appears after a restart.

## The library pipeline

```bash
cd ../books

# 1. Read metadata out of the files, see what needs a human, correct
#    metadata.json, then write it back into the files and rename them.
python3 scripts/metadata.py bootstrap
python3 scripts/metadata.py review
python3 scripts/metadata.py apply
python3 scripts/metadata.py rename
python3 scripts/metadata.py dupes        # byte-identical copies

# 2. Build the catalog. One entry per file; documents and misc are not books.
node scripts/build-catalog.mjs --root . --out catalog.json --exclude documents,misc

# 3. Push to Drive. Writes each Drive id back into the catalog and uploads the
#    catalog last, so it never describes files that are not there yet.
cd ../kitaplik
python3 tools/drive_sync.py --library ../books --dry-run
python3 tools/drive_sync.py --library ../books --prune
```

`--prune` deletes Drive files the catalog no longer references. Uploads retry
with backoff and a failure is reported rather than aborting the run.

To give the app credentials:

```bash
python3 tools/make_app_credentials.py --push   # types the blob into the Connect screen
```

## Layout

```
app/src/main/java/com/erkantaylan/kitaplik/
├── catalog/     models + CatalogSource (HTTP and Drive)
├── auth/        credentials, encrypted storage, token refresh
├── download/    resumable downloader with size + MD5 verification
├── storage/     where downloaded files live
├── text/        EPUB and markdown to prose, paragraphs with offsets
├── reader/      reading position, bookmarks, speed
├── open/        hand-off to external apps
└── ui/          screens, tab bar, theme
```
