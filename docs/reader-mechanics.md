# Reader mechanics: 25 proposals, and what they agree on

Five agents were given the same brief and the same context (`docs/architecture.md`,
`README.md`) and asked independently for five core reading mechanics each — three
running Fable, two running Opus. None saw another's answer. What follows is all 25,
grouped by how many of them arrived at the same idea, because the convergence is
more informative than the ideas taken one at a time.

"Core mechanic" was defined as how reading itself works — the loop, the interaction,
what the app does with your attention and your position in a book. Cosmetics,
theming and sync plumbing were explicitly out of scope, as were table stakes
(highlighting, night mode, a table of contents, page-turn animations).

---

## Tier 1 — every agent reached for these

Three mechanics were proposed by all five agents working independently. That is the
strongest signal in the batch.

### 1. Jump to the same passage in the other rendition — **5/5**

- [ ] Build

**What.** From your place in a book, one action lands you at the same passage in a
sibling entry — the markdown's garbled paragraph opened in the EPUB, or the reverse.
Match a 40–200 character window around the current offset against the other file's
extracted text. Positions stay per file; only the jump crosses.

**Why it fits.** One entry per file is a deliberate decision that currently leaves
you stranded in two or three positions for the same work. Every agent framed this the
same way: this is the mechanic that turns that decision into a payoff rather than a
cost. Both texts are already local plaintext in `cacheDir/text/<id>.txt`, so it needs
no server and no catalog change.

**Cost.** Medium. All five independently named the same hardest part: matching into
the degraded markdown conversions, where whitespace, hyphenation and letter-spacing
disagree with the EPUB, and one file doubles phrases. Needs an honest fallback to
text fraction when the match fails.

**Variants worth keeping.**
- Extend to PDFs: calibrate two anchor points by hand once, then hand the external
  opener a page number derived proportionally from the character offset. Alignment
  drifts on books with heavy front matter or footnotes.
- Offered, never forced — the jump is an action, not automatic behaviour.
- Trigger by long-press on the paragraph you are actually on, so the anchor is the
  passage rather than the scroll position.

### 2. Rewind on re-entry, scaled to how long you were away — **5/5**

- [ ] Build

**What.** Reopening a book after a gap does not drop you at your saved offset. It
drops you before it — a paragraph after a day, a screen after a week, the section
start after a month — with the already-read run-up dimmed to roughly 55% ink and the
true stopping point drawn as a hairline. Scrolling past it clears the dim permanently.

**Why it fits.** Progress is written continuously and silently, so it is always
precise and always slightly wrong about what you actually retained. A library of
forty half-read books is exactly where re-entry friction kills reading. The position
is a character offset, so "one screen earlier" is arithmetic on the text rather than
a page guess.

**Cost.** Small. The trap, named by one agent: **the progress writer must not advance
until you cross the hairline**, or the rewind overwrites the real position. Choosing
the gap-to-distance curve so it never feels like the app lost your place is the rest
of the work.

**Variants worth keeping.**
- Scale by `log(days away)` rather than in steps.
- Label the mark: "last here 12 days ago". Tap it to skip the ramp entirely.
- Rewind in *reading minutes* off the speed segments rather than in paragraphs —
  "back up 4 minutes" — which is proportional to how long you actually were away.
- For markdown, snap to the last heading instead of a paragraph count. Inconsistent
  heading levels make this the fiddly part.

### 3. Progress becomes ranges, not a cursor — **4/5**

- [ ] Build

**What.** Keep the reading segments the speed meter already computes and currently
throws away: start offset, end offset, wall time, wpm, appended per book. Progress
stops being one number and becomes coverage — what was actually on screen long enough
to be read.

**Why it fits.** Technical books get read out of order, and one character-offset
scheme makes ranges trivially mergeable. A single owner has no reason to fear a full
log of their own attention. The segment machinery exists and is already tuned to tell
scrolling apart from reading.

**Cost.** Medium, and it is the one proposal here that changes the data model.

**The four takes, which differ meaningfully.**
- *Coverage, not a cursor* — run-length character ranges, plus a deliberate "skip and
  owe" gesture; Home lists owed sections, the reader shows a thin coverage strip. The
  hard part is the dwell threshold that turns "on screen" into "read".
- *Skipped-span ledger* — the spans the meter already discards as scrubbing are kept
  as holes in the progress bar; time-remaining excludes them; chapters get flagged
  "skimmed, not read". One line from a discard branch to a record. Risk: a false skip
  is worse than a missed one, and that boundary is the meter's fuzziest.
- *Reading trace* — a shaded map on the scrubber (read / skimmed / never seen), and
  resume offers "last read" beside "furthest scrolled".
- *Segment ledger* — a margin ribbon where slow passages render dark and fast ones
  pale, turning speed from a gauge into a map of where the book was hard. Long-press
  a dark band to jump back to it.

---

## Tier 2 — more than one agent, but not all

### 4. Two-book interleave — **2/5, and both were Opus**

- [ ] Build

**What.** Declare a pair, typically one fiction and one technical. A gesture
(two-finger horizontal swipe) drops you straight into the other book's position
without going through Home. Each keeps its own offset, font scale and speed segment.
Home shows the pair as one unit with tonight's minutes split between them.

**Why it fits.** Reading fiction and technical books in the same sitting is the actual
pattern here, and progress is already per file, so a pair is two ids in a JSON file
rather than a feature with a management UI. It turns "I bounce between books" from a
failure state into the loop the app supports.

**Cost.** Small. Two hard parts: keeping both extracted texts warm so the swap has no
visible pause, and correctly ending the outgoing book's speed segment on every swap.

**Variant.** Timed interleave — after a set stretch in the technical book, the swap
affordance surfaces at the bottom of the screen.

### 5. Bookmarks come back — **2/5, one Fable and one Opus**

- [ ] Build

**What.** Bookmarks stop being a flat newest-first list and become a rotation. Home
surfaces one old bookmark a day, chosen by age and when it last appeared, showing its
stored context. Tap opens the book at that exact offset. "Keep" lengthens the
interval, "release" deletes it.

**Why it fits.** Word-level bookmarks already carry the word, its surrounding context
and a timestamp — everything a due date needs except the date — and they outlive the
book being on the device. Spaced repetition over your own marginalia, across years and
two languages. There is no shared-highlight social layer to compete with, so the
passages you kept can serve only you.

**Cost.** Small to medium. Two problems: a bookmark whose book is not downloaded
(fetch in the background, show the stored context until the file arrives), and
"due today" over a growing set, which is precisely the query `architecture.md` names
as the trigger for SQLite. See *Constraints* below.

---

## Tier 3 — one agent each

These are the ideas only one agent produced. All seven came from a single model's
run; none are worse for it, and several are the most distinctive things in the batch.

### 6. Excursions never move you *(Fable)*

- [ ] Build

Any jump that isn't reading — tapping a bookmark, a heading, scrubbing the edge —
opens as an *excursion*: a persistent chip tethers you to your real position, one tap
snaps back, and **progress is never written unless you dwell there for a full measured
segment**. Reuses the meter's existing scroll-versus-read distinction to make "where
was I" trustworthy, so bookmarks become safe to revisit mid-book instead of a way to
lose your place. Small. Hardest part: deciding when a long excursion has quietly
become the new frontier.

### 7. Hold-to-flash *(Fable)*

- [ ] Build

Press and hold a paragraph and it streams word by word at your measured session
wpm × 1.25; lift your thumb and you are back in scrolled text at exactly that
character offset. No mode switch, no separate screen. **This is the RSVP idea from
earlier, arrived at independently and made a gesture rather than a mode** — which
sidesteps what made the three-modes-in-one-reader plan awkward, since there is no
mode to switch out of and nothing to leave. Medium: needs one word-to-offset
tokenizer that also works for Turkish.

### 8. Your words follow you *(Fable)*

- [ ] Build

A word-level bookmark also enters a personal lexicon; every book opened afterwards
underlines occurrences of those words, and their Turkish inflections. Tap shows where
you first marked it and the context. All prose is extracted locally and owned, so
scanning it is free — a loop no cloud reader can offer without your whole library.
Medium. Hardest part: Turkish stemming, so *kitaplarımdan* matches a bookmark on
*kitap* without a flood of false positives.

### 9. Goalpost line *(Fable)*

- [ ] Build

Tell the reader "twenty minutes" and it draws a faint rule in the prose where the
current session wpm says you will be when the time is up; the rule creeps as the
estimate settles. The meter is deliberately session-only, so the goal is about this
sitting rather than a streak — a mark in the text to read towards instead of a clock
to glance at. Small. Hardest part: an offset-anchored marker that moves on each
five-second tick without jittering across paragraph boundaries.

### 10. The finger *(Fable)*

- [ ] Build

A second, ephemeral position per file — the diagram or code listing you keep flipping
back to. Two-finger tap swaps between finger and current position; the one you left
shows as a marker on the scrubber. Cleared when the book closes. A phone cannot show
two pages at once, and technical books on a large phone are exactly where that hurts.
One more character offset in the position JSON that already exists. Small. Hardest
part: a swap gesture that collides with neither scroll, nor long-press bookmark, nor
the system back edge.

### 11. Bookmark interrogation *(Opus)*

- [ ] Build

A bookmark's stored context expands on demand: pull down on it to widen the captured
passage paragraph by paragraph, straight from the cached prose. Bookmarks also become
a jump list *inside* the reader — a thin edge scrubber showing your own marks as
ticks, so a technical book you have worked through becomes navigable by the things you
flagged rather than by chapters. Small, since the full prose is already on disk.
Hardest part: invalidating the cached text file without orphaning bookmark offsets.

### 12. Turkish/English cadence *(Opus)*

- [ ] Build

The speed meter keeps a per-language baseline learned from your own segments and shows
the current sitting against it — "12% under your Turkish pace" — rather than a bare
wpm. Time remaining uses the matching baseline instead of one global average. Two
languages are read at genuinely different speeds, and catalog entries can carry the
language, so the split is nearly free. A commercial reader averages across a
population; here the only population is you, which is what makes the number mean
anything. Small. Hardest part: the meter is deliberately session-only, so persisting a
baseline needs a rule for what counts as a trustworthy segment.

---

## Constraints any of these must respect

These are decisions already made and recorded in `docs/architecture.md`. They are not
obstacles to route around silently; if a mechanic requires breaking one, that is the
thing to discuss first.

| Constraint | What it means here |
|---|---|
| **One entry per file** | Renditions are separate entries on purpose. Every rendition proposal above respects this — they *jump* between entries, they never merge them. |
| **No SQLite** | Both the bookmark rotation (#5) and the persisted segment log (#3) are exactly the append-only history and due-date query the doc names as the trigger for Room. The workaround, proposed by one agent in the same breath: per-book JSONL with range compaction. Adopt that, or accept the threshold has been crossed and say so. |
| **PDFs go out** | Anything touching PDFs is a hand-off with a page number, never in-app rendering. |
| **Position is a character offset** | This is what makes #1, #2, #7 and #10 arithmetic rather than guesswork. It was chosen for exactly this. |
| **Speed is not persisted** | #3 and #12 both need it to be. That is a deliberate reversal of a deliberate choice, worth making consciously. |

## Relationship to what was already decided

- **Pagination** was considered and declined. Nothing above reintroduces it; the
  18-second open it was meant to fix turned out to be a word-count regression, since
  fixed.
- **RSVP / bionic / regular merged into one reader** was asked for and deferred.
  #7 is that request, reshaped as a gesture instead of a mode.
- **Speed persistence** was explicitly not wanted yet ("you don't need to save
  anywhere yet"). #3 and #12 depend on it.

## Open question

Which of these gets built, and in what order. One reading of the batch:

- **#1** has unanimous agreement *and* pays off twice, because it makes the two badly
  degraded markdown renditions readable today without fixing the conversion.
- **#2** is the cheapest thing on the list and the one that most changes the daily
  feel of a library of half-read books.
- **#3** is the only proposal that changes the data model, so it should go last, if
  at all.

Nothing here is committed. This is a menu, not a plan.
