# Kitaplık — architecture

Kotlin + Jetpack Compose, Android only, single user. No cross-platform runtime,
no login screen, no server of our own.

## Shape

```
MainActivity            window insets, tab state, which screen is showing
  │
  ├── ConnectScreen     one-time paste of Drive credentials
  │
  └── AppShell          content above, tab bar below
        ├── HomeScreen        currently reading · recently opened · bookmarks
        ├── CatalogScreen     Cloud (everything) / On device (downloaded only)
        ├── SettingsScreen    source, counts, disconnect
        └── ReaderScreen      full screen, outside the tab bar
```

Source layout under `app/src/main/java/com/erkantaylan/kitaplik/`:

| package | what lives there |
|---|---|
| `catalog/` | `Models` (the catalog as data), `CatalogSource` (interface + HTTP), `DriveCatalogSource` |
| `auth/` | `DriveCredentials`, `CredentialStore` (encrypted), `TokenProvider` (refresh → access) |
| `download/` | `Downloader` — resumable, size- and MD5-verified |
| `storage/` | `LibraryStore` — where downloaded files live |
| `text/` | `TextExtractor` (EPUB/markdown → prose), `BookText` (paragraphs + offsets) |
| `reader/` | `ReaderViewModel`, `ReadingProgress`, `Bookmark`, `ReadingSpeed` |
| `open/` | `ExternalOpener` — hands a PDF to another app |
| `ui/` | screens, tab bar, hand-drawn nav glyphs, theme |

## The decisions that shape everything

### The catalog is the contract

`catalog.json` is generated from the library and describes every file: id,
title, author, year, category, format, bytes, md5, and its Drive file id. The
app fetches that one file and then fetches books by id.

Consequences: Drive holds a **flat** folder of `<item-id>.<ext>` — no folder
traversal, no path resolution, no second copy of the shelf structure to keep in
sync. `CatalogSource` has two methods, so replacing Drive means writing one
class and touching nothing else.

### One entry per file, not per book

A PDF, an EPUB and a markdown rendering of the same work are **separate
entries**. They are different artifacts with different handling — the PDF goes
out to another app, the EPUB is parsed, the markdown is read directly — and you
can legitimately be in different places in each. Nothing has to decide which
rendition you meant.

Ids carry an 8-character hash of the file path, because slugs truncate at 60
characters and this library contains near-identical filenames that would
otherwise collide into one Drive object and one crash-inducing duplicate list
key.

### Sources hand back requests, not URLs

`CatalogSource.fileRequest(item)` returns a fully-formed OkHttp `Request`.
Authentication belongs to the source; the downloader must not know whether a
backend needs a bearer token. This is why resumable downloads, `Range` resume
and MD5 verification work identically over HTTP and Drive with no branching.

### `drive.file`, and the same OAuth client on both ends

The scope is `drive.file`, which is **non-sensitive**: no verification, no CASA
security assessment. It only ever reaches files the app created, so a leaked
token gets the library folder and nothing else in the account. `drive.readonly`
would have meant a paid annual audit and access to everything.

The app carries the **same Desktop OAuth client** as the desktop uploader. Google
does not document whether a `drive.file` grant spans OAuth clients inside one
project; sharing one client removes the question. The consent screen must be
published **In production** — in *Testing* a refresh token expires after 7 days,
for every scope except basic identity.

There is no login screen because there is no account to log into: the
credential is pasted once and stored in `EncryptedSharedPreferences` under a
Keystore-backed key.

### Reading position is a character offset

Not a page, not a pixel, not a word index. A character offset points into the
*text*, so it survives a font-size change, and it is the one value every
reading mode can agree on — a future bionic or speed-reading mode renders the
same position differently rather than converting between index schemes. The
legacy app needed a bespoke word counter purely to hand off between two modes,
because it had three incompatible definitions of "word".

The fraction scrolled through the top visible paragraph is recorded too, and
converted back to pixels once that paragraph has been laid out at the current
font size. A paragraph can be several screens tall at 2× type.

### Progress and bookmarks answer different questions

**Progress** answers *where was I* — written continuously, without being asked,
one per file. **Bookmarks** answer *that passage again* — made deliberately on
the word under your finger, with the surrounding text kept as context and a
timestamp. Several can live in one paragraph.

### Reading speed is measured on segments, and not persisted

Rating each gap between position updates does not survive contact with how
people read on a phone: you scroll a chunk in half a second and then read it
for twenty seconds without touching the screen. Per gap, the scroll looks like
2000 wpm and gets discarded as scrubbing, and the reading looks like no
progress and gets discarded too — nothing is ever counted.

So a *segment* is measured instead: from when you start reading to when you
stop, against how far you got. Bursts and still stretches average out inside
it. A segment ends on 90 seconds of stillness, on backgrounding, or on closing
the reader — that last one explicitly, since the view model is keyed on the book
and outlives the screen.

The figure also ticks every five seconds rather than only when the position
moves. Otherwise it is computed the instant you arrive at a new screen,
crediting words you have not read yet, and reads roughly double.

An early figure appears around forty seconds in, prefixed with `~` to say it
is still an estimate, and the tilde drops once the session is long enough for
the one-screen scroll-ahead to have amortised. Hiding it entirely for the first
ninety seconds read as "the measurement is broken".

Nothing is stored: the number describes the sitting you are in. Long-press the
title to reset it.

### One theme

Warm ground (`#1C1A16`), warm ink (`#D8CFB8`), amber accent. Near-white on
near-black is about 21:1 and the text blooms; this lands near 11:1 — easy for a
long sitting, still well above the 4.5:1 floor. Body text sits a shade below UI
text. The hue is inherited from the app this replaces, whose default was sepia.

### Window insets are handled once

At the app root, so every screen — including the reader, which sits outside the
tab bar — draws inside the safe area. The phone keeps its system navigation
buttons on, and the tab bar has to sit above them.

## Storage on the device

| what | where | why |
|---|---|---|
| downloaded books | files in `filesDir/library` | bodies never go in a database |
| extracted prose | `cacheDir/text/<id>.txt` | unzipping a 5 MB EPUB is slow enough to notice |
| reading position | `SharedPreferences` JSON | a few hundred tiny records |
| bookmarks | `SharedPreferences` JSON | same |
| Drive credentials | `EncryptedSharedPreferences` | Keystore-backed |

**No SQLite, deliberately.** Everything on the device is a few hundred small
records sorted and filtered in memory. Room would buy indexed queries and
partial writes, neither of which is needed, and cost a schema and migrations.
Reach for it when one of these arrives: a per-session reading history
(append-only, grows forever), spaced repetition (needs a real "due today"
query), or full-text search inside books.

## Known gaps

- **PDFs are external only.** No in-app rendering, and none of the reading
  modes could use one — they are all text-based.
- **5 books have a failed markdown conversion.** The catalog excludes markdown
  with no prose after its front matter, so those are PDF-only.
- **Two markdown renditions are badly degraded** — *Head First Design Patterns*
  has systematic letter-spacing corruption, *Kankanın Av Rehberi* has every
  phrase doubled. Their PDFs are fine. Not worth fixing until it gets in the way.
- **Home's "recently opened" is read once per visit**, not observed, so it
  refreshes on tab change rather than live.
