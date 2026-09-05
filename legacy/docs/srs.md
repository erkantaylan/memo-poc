# memo-poc Spaced-Repetition (SRS) Design

Forward-looking design for spaced repetition in `memo-poc`. SRS is
**future work** — the app is currently a stateless practice surface across
six modes. This doc exists so the implementation choice, when it lands, is
informed. Two wrinkles not covered by SRS textbooks:

1. The unit of memorization is an **entire text** (song, poem, chapter),
   not a flashcard.
2. The same text is practiced through **multiple modes**, each producing a
   different difficulty signal.

---

## 1. Why SRS matters for memorization

Ebbinghaus's 1885 forgetting curve showed retention decays roughly
exponentially without rehearsal
([Ebbinghaus](https://en.wikipedia.org/wiki/Forgetting_curve);
[Murre & Dros 2015 replication](https://pmc.ncbi.nlm.nih.gov/articles/PMC4492928/));
Roediger & Karpicke's **testing effect** showed retrieval beats re-reading
for long-term retention
([2006](http://psychnet.wustl.edu/memory/wp-content/uploads/2018/04/Roediger-Karpicke-2006_PPS.pdf));
and Bjork's **desirable difficulties** explains why — harder, more-spaced
retrievals produce stronger traces than easy massed ones
([Bjork & Bjork 2011](https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/04/EBjork_RBjork_2011.pdf)).
SRS turns this into a schedule: practice each item right before you'd
forget it, and retention rises sharply for the same total time spent.

---

## 2. Existing SRS algorithms

| Algorithm | Year | Notes |
|---|---|---|
| **Leitner boxes** | 1972 | 3–5 physical "boxes"; promote on correct, demote on wrong. Trivial; coarse-grained. |
| **SM-2** (Anki classic) | 1987 | Each card has `EF` (ease factor, ~1.3–2.5) and an interval. `interval := interval * EF`. Ratings 0–5. Well-understood, but [overtunes intervals](https://faqs.ankiweb.net/what-spaced-repetition-algorithm) and gets stuck in "low-interval hell" on lapses. |
| **Half-Life Regression (HLR)** | 2016 | Duolingo's [Settles & Meeder](https://research.duolingo.com/papers/settles.acl16.pdf) trainable model. ~45% lower error than SM-2 baselines, but needs labelled history and feature engineering per-word. |
| **FSRS** (v4 → v6) | 2022–2025 | The DSR model: every card has **stability** (S), **difficulty** (D), **retrievability** (R). Trained on >1.5B reviews. Adopted by Anki 23.10 (Nov 2023). Best published accuracy. |

**Recommendation: FSRS** via [`ts-fsrs`](https://github.com/open-spaced-repetition/ts-fsrs)
(MIT, pure TS, ESM/CJS/UMD on [npm](https://www.npmjs.com/package/ts-fsrs)).
Open-source, battle-tested in Anki, handles delayed reviews well (SM-2's
weak point — the regime casual users live in), and the UMD bundle drops
into our app via one `<script>` tag. The `next(card, now, rating)` API is
a clean boundary so we can swap later. **SM-2 stays as a Phase 1 fallback**
because it's ~50 lines and zero deps (§9). See [`awesome-fsrs`](https://github.com/open-spaced-repetition/awesome-fsrs)
for the ports list and [Ye's algorithm wiki](https://github.com/open-spaced-repetition/fsrs4anki/wiki/ABC-of-FSRS).

---

## 3. Adapting "card" = "entire text"

Anki cards are atomic — one fact, one binary correct/incorrect signal. A
40-line poem is not. After one practice pass you might know 85% of it, miss
two stanzas entirely, and stumble on three lines. Three plausible adaptations:

**A. Chunk into many cards** (one per stanza / sentence / line). Granular;
standard FSRS works unmodified. But card explosion (~50 cards for a 600-word
poem), the user's mental model is "the poem" not "card #37", and presenting
chunks out of sequence breaks the natural flow — half the retrieval cue.

**B. One card per text, continuous score → discrete rating.** The whole
text is one FSRS card; each session produces a `score ∈ [0,1]` mapped to
FSRS's 1–4 rating (thresholds: <0.60 Again, <0.80 Hard, <0.95 Good, else
Easy). Simple, matches user intent. Hides which parts are weak; one bad
stanza tanks the rating.

**C. Hybrid.** Default to B. Promote a chunk to its own card when it's been
>50% of errors across the last 3 sessions; demote when clean for 2. Cheap
by default, granular only where it pays. Extra state to track.

**Recommendation: B for v1, C for v2.** B ships in days and is enough to be
useful. C captures the "I always trip on the third verse" reality but
doesn't earn its complexity until users have hundreds of texts. Accepted
trade-off: under B, a user who knows 90% of a text but always flubs the
same 10% will see the whole text repeatedly until they fix that 10% —
fine, re-reading the easy parts is cheap and the interval still stretches
as the % climbs.

---

## 4. Fusing multiple modes into one schedule

The user practices the same text in spread on Monday, first-letter on
Wednesday, typing on Friday. Three difficulty signals.

### Options

1. **Naive**: every mode-session emits a rating on the single per-text card;
   mode is ignored.
2. **Weighted**: same single card, but the score-to-rating mapping is
   mode-aware. Easier modes need higher raw scores; harder modes are graded
   leniently. Justified by a difficulty hierarchy.
3. **Mode-specific cards**: every `(text, mode)` tuple is its own card.

### Recommendation: **Option 2** (weighted, single card per text)

The user's goal is *"I have this poem memorized,"* not *"…in typing mode."*
One schedule per text matches that. Option 3 inflates card count 6× for
marginal benefit; Option 1 under-weights typing's strong signal.

### Difficulty hierarchy

Ordered by retrieval effort (harder = stronger memory cue per Bjork):

```
typing       (1.00)   write every character from scratch
focus        (0.85)   only the current line is visible
first-letter (0.65)   first letters as scaffolding
spread       (0.45)   random words blanked; fill them in
rsvp/bionic   ( - )   reading aids — emit no rating
```

Weights are rounded; calibrate from session data later.

### Math

Let:

- `s ∈ [0,1]` = raw session score (% correct words / lines, as the mode
  defines it).
- `w` = mode weight from the table above.
- `s_eff = 1 − w · (1 − s)` = **effective score**: a typing-mode mistake
  costs full points; a spread-mode mistake costs 45% of that.

Then map `s_eff` to FSRS rating with the thresholds from §3:

```
s_eff < 0.60 -> Again
s_eff < 0.80 -> Hard
s_eff < 0.95 -> Good
else         -> Easy
```

Worked example: spread session, `s = 0.70` → `s_eff = 0.865` → **Good**.
Same `s = 0.70` in typing → `s_eff = 0.70` → **Hard**. Typing is graded
harshly because passing the strongest test means more.

---

## 5. Algorithm spec

We delegate the heavy math to `ts-fsrs`. Our job is the wrapper.

### State shape (per text)

```ts
type SrsState = {
  due: number;              // epoch ms
  stability: number;        // days; FSRS S
  difficulty: number;       // 1..10; FSRS D
  elapsed_days: number; scheduled_days: number;
  reps: number; lapses: number;
  state: 0 | 1 | 2 | 3;     // New | Learning | Review | Relearning
  last_review: number | null;
};
type TextSrs = {
  srs: SrsState;
  history: Array<{ ts: number; mode: string; score: number;
                   s_eff: number; rating: 1|2|3|4 }>;
};
```

### Update function

```ts
import { fsrs, createEmptyCard, Rating, State } from 'ts-fsrs';

const scheduler = fsrs({ request_retention: 0.9, enable_fuzz: true });

function initText(): TextSrs {
  const card = createEmptyCard();         // ts-fsrs helper
  return {
    srs: { ...card, due: Date.now() },    // due immediately
    history: [],
  };
}

const MODE_WEIGHT: Record<string, number> = {
  typing: 1.00, focus: 0.85,
  'first-letter': 0.65, spread: 0.45,
};

function scoreToRating(score: number, mode: string): Rating {
  const w = MODE_WEIGHT[mode] ?? 0;
  if (w === 0) return null;               // rsvp/bionic emit nothing
  const sEff = 1 - w * (1 - score);
  if (sEff < 0.60) return Rating.Again;
  if (sEff < 0.80) return Rating.Hard;
  if (sEff < 0.95) return Rating.Good;
  return Rating.Easy;
}

function recordSession(t: TextSrs, mode: string, score: number, now = new Date()) {
  const rating = scoreToRating(score, mode);
  if (rating == null) return t;           // reading-aid mode, no scheduling
  const { card } = scheduler.next(t.srs, now, rating);
  const s_eff = 1 - MODE_WEIGHT[mode] * (1 - score);
  return {
    srs: { ...card, due: card.due.getTime(), last_review: now.getTime() },
    history: [...t.history, { ts: now.getTime(), mode, score, s_eff, rating }],
  };
}
```

### "What's due today?" query

```ts
function dueToday(all: Record<string, TextSrs>, now = Date.now()) {
  return Object.entries(all)
    .filter(([, t]) => t.srs.due <= now)
    .sort(([, a], [, b]) => a.srs.due - b.srs.due);
}
```

O(n) is fine — a power user owns dozens of texts, not millions.

### Initialization

A new text starts as `createEmptyCard()` (State.New, stability ≈ 0), due
immediately. The first session's rating sets initial S and D via FSRS's
`w0..w3` parameters
([Expertium's algorithm explainer](https://expertium.github.io/Algorithm.html)).

### Reference libraries (all MIT)

- Browser: [`ts-fsrs`](https://github.com/open-spaced-repetition/ts-fsrs) /
  [npm](https://www.npmjs.com/package/ts-fsrs). UMD build drops into a
  `<script>` tag — no build step needed.
- Server (future): [`py-fsrs`](https://github.com/open-spaced-repetition/py-fsrs)
  ([PyPI `fsrs`](https://pypi.org/project/fsrs/)).
- Optimizer: [`fsrs-rs`](https://github.com/open-spaced-repetition/fsrs-rs)
  if we ever fit per-user weights.

---

## 6. Storage model

**Phase 1 (client-only):**

```
memo:texts        -> [{ id, title, byte_len, updated_at }, ...]
memo:text:<id>    -> { id, title, body, ... }
memo:srs:<id>     -> { srs: SrsState, history: [...] }
```

SRS lives in its own key — deleting a schedule shouldn't risk the text
body, and the two have different update cadences.

**Phase 3 (backend, see [backend.md](./backend.md)):** the schema already
has `progress.srs_state TEXT` (JSON blob). The `TextSrs` JSON above moves
there unmodified. Current PK is `(user_id, text_id, mode)`; since we
recommend one schedule per text (§4), the cleanest path is **dropping
`mode` from the SRS PK** (keep it on `sessions`). Tiny migration.

**Backup:** `Export JSON` dumps everything under `memo:*` into a single
file; shape matches `GET /export` in backend.md §7, so import is a no-op.

---

## 7. UI integration

- **Home page text cards** get a status badge: `Due now` (red), `Review in
  3d` (grey), `New` (blue).
- **"Today's review"** section above the library: ordered list of due
  texts, most-overdue first; tap → opens the user's default practice mode.
- **Session end**, two paths:
  - *Typing / focus*: auto-grade from correct/typed character counts. No
    prompt; toast "Next review in 3 days".
  - *Spread / first-letter*: prompt *"How well did you remember?"* → 4
    buttons (Again / Hard / Good / Easy). Bypass `scoreToRating`.
- **Notifications**: deferred until a PWA / mobile shell
  ([mobile.md](./mobile.md)). A `document.title` badge ("(3) memo") is
  enough until then.

---

## 8. Honest pitfalls

- **Forgetting curves were studied on atomic facts**, not memorized prose.
  Ebbinghaus used nonsense syllables; FSRS was trained on flashcards. Prose
  has internal cues (rhyme, meter, narrative) that bend the curve, usually
  toward slower forgetting past a fluency threshold. Expect intervals to
  feel *short* at first; the optimizer compensates after ~10 sessions.
- **Self-grading is biased.** Users overrate their recall. Prefer
  auto-graded modes (typing, focus) for scheduling events; weight
  self-graded sessions lower or (v2) record but don't feed them in.
- **FSRS needs ~5–10 reviews before predictions stabilize.** Show "due
  soon", not "due in 4d 7h", for the first month.
- **Long texts need a warm-up.** Cold-opening a poem you haven't seen in 3
  weeks isn't a forgetting signal, it's a cold-start signal. Recommend a
  quick **bionic or RSVP pass** before scheduled active-recall. Reading
  aids prime retrieval without being a test and emit no rating events.
- **One bad stanza tanks the rating** (Option B). Mitigate via Option C in
  v2; until then, accept it.
- **Pin `ts-fsrs`.** FSRS parameters change between majors (4→5→6); pin to
  `~major.minor` and upgrade explicitly. v5 state is forward-compatible
  with v6 but not vice versa.

---

## 9. Implementation roadmap

| Phase | Scope | Effort (solo dev) |
|---|---|---|
| **1. Minimal SM-2** | One schedule per text, single rating (`Did I remember it?` → `Yes/No/Hard`). `localStorage` only. No mode awareness. Badge on home page. | ~1 day |
| **2. FSRS + multi-mode auto-grading** | Swap algorithm to `ts-fsrs`. Implement weighted score-to-rating (§4). Auto-grade typing/focus, manual-grade spread/first-letter. Today's-review section. | ~3 days |
| **3. Backend sync** | `progress.srs_state` carries the same JSON. Drop `mode` from PK. Import-on-first-login. | ~1 day after backend |
| *(v2)* Hybrid chunk cards | §3 Option C. Promote troublesome stanzas. | ~2 days |
| *(v2)* Per-user parameter fit | `fsrs-rs` offline or WASM in-browser. Needs ≥50 reviews. | ~1 day |

Phase 1 is meant to be **disposable** — ship SM-2 to learn what UI works,
then throw the algorithm away and keep the UI.

---

## 10. Open questions for user input

1. **One schedule per text, or per (text, mode)?** Doc recommends per text
   (§4, Option 2). Per-`(text, mode)` suits performers tracking
   typing-readiness vs. spread-readiness separately. Confirm.
2. **Auto-grade vs. self-grade default for spread / first-letter?**
   Recommendation: self-grade (4 buttons), no robust auto-score. Alternative:
   auto-derive from time-spent and reveals used — cheaper UX, fuzzier.
3. **What counts as a session?** Proposed floor: ≥30 s and ≥50% of the
   text visited before a rating fires. Confirm.
4. **Target retention rate?** FSRS default 0.9. Higher → more reviews;
   lower → more lapses. Offer as a setting later.
5. **Empty "Today" list — what to show?** Suggestion to review the
   weakest-stability text, or a plain empty state? Empty state is simplest.

---

## Sources

- [Ebbinghaus forgetting curve — Wikipedia summary of *Über das Gedächtnis* (1885)](https://en.wikipedia.org/wiki/Forgetting_curve)
- [Murre & Dros (2015). Replication and Analysis of Ebbinghaus' Forgetting Curve. *PLOS ONE*.](https://pmc.ncbi.nlm.nih.gov/articles/PMC4492928/)
- [Roediger & Karpicke (2006). The Power of Testing Memory. *Perspectives on Psychological Science*.](http://psychnet.wustl.edu/memory/wp-content/uploads/2018/04/Roediger-Karpicke-2006_PPS.pdf)
- [Bjork & Bjork (2011). Creating Desirable Difficulties to Enhance Learning.](https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/04/EBjork_RBjork_2011.pdf)
- [Settles & Meeder (2016). A Trainable Spaced Repetition Model for Language Learning (HLR). *ACL*.](https://research.duolingo.com/papers/settles.acl16.pdf)
- [Duolingo `halflife-regression` reference implementation](https://github.com/duolingo/halflife-regression)
- [Anki FAQ — algorithm and SM-2 history](https://faqs.ankiweb.net/what-spaced-repetition-algorithm)
- [FSRS algorithm repository (`free-spaced-repetition-scheduler`)](https://github.com/open-spaced-repetition/free-spaced-repetition-scheduler)
- [`ts-fsrs` (TypeScript implementation, MIT)](https://github.com/open-spaced-repetition/ts-fsrs)
- [`ts-fsrs` on npm](https://www.npmjs.com/package/ts-fsrs)
- [`py-fsrs` (Python implementation, MIT)](https://github.com/open-spaced-repetition/py-fsrs)
- [`fsrs-rs` (Rust optimizer, MIT)](https://github.com/open-spaced-repetition/fsrs-rs)
- [`fsrs4anki` wiki — ABC of FSRS](https://github.com/open-spaced-repetition/fsrs4anki/wiki/ABC-of-FSRS)
- [Expertium's technical FSRS explainer (parameters, formulas)](https://expertium.github.io/Algorithm.html)
- [`awesome-fsrs` — curated implementations and papers](https://github.com/open-spaced-repetition/awesome-fsrs)
