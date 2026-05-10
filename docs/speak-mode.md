# memo-poc — "Speak it" mode

_Last updated: 2026-05-11_

A new memorization mode. The screen shows the full target passage with every word `pending` (like first-letter mode); the user **recites aloud**, live STT lights each word green on match / red on mismatch, and a final scoreboard summarises the run.

This doc specifies UX, DOM, and the scoring engine. STT plumbing — API choice, language tag, browser fallbacks — lives in `docs/speech.md`. Everything here is API-agnostic: it consumes a stream of interim + final transcript tokens.

---

## 1. User flow (happy path)

1. From `index.html` the user clicks the **"Speak it"** card (Memorize group). Loads `speak.html?id=...`.
2. Full text renders, every word `pending` gray, blue cursor on the first scoreable word.
3. User clicks **"Start speaking"** — mic permission prompt first time.
4. Pulsing mic icon + thin waveform appear in the header.
5. As the user recites, interim STT tokens stream in: matched words flip green, cursor advances.
6. On mismatch (wrong word, paraphrase, mispronunciation) the current word turns red, cursor pauses. User can retry, click **"Mark correct"** (false-negative escape), or **"Skip word"** (orange).
7. **"Pause"** stops STT without losing state. **"Resume"** restarts it.
8. End condition: cursor passes the last scoreable token **or** user clicks **"Finish"**.
9. Results screen: total / correct / mistakes / skipped / time, word-level diff with hover tooltips showing what STT actually heard for each red word. CTAs: **Practice again**, **Listen (TTS)**, **Back to text**.

---

## 2. Visual / DOM design

Layout mirrors `typing.html` / `first-letter.html` — same header / text / options skeleton, same `installReaderOptions` panel.

```
+--------------------------------------------------------------+
| ← Home   "Peri Beni..."   [Start][Pause][Finish][Mark OK]    |
|          [Skip word]      mic ~waveform~       12/87 · 2err  |
+--------------------------------------------------------------+
| ▾ Reader options (font, theme, size, lh, width, stripMd)     |
+--------------------------------------------------------------+
|   Bu  şehirde  bir  peri  beni  nerelere  götürüyo'          |
|   GRN GRN      GRN  GRN   CURS  pending   pending            |
|                                                              |
|   Geceleri  sokakta  yıldızlar  altında                      |
|   pending   pending  pending    pending                      |
+--------------------------------------------------------------+
|  Tip: Recite aloud. Tab = reveal letter, Enter = skip word.  |
+--------------------------------------------------------------+
```

### Word states (CSS classes on each `.sw-word` span)

| State      | Class       | Visual                                           |
|------------|-------------|--------------------------------------------------|
| `pending`  | `.pending`  | `color: #555` (matches typing's pending gray)    |
| `current`  | `.current`  | `background: var(--accent); color: #000; blink`  |
| `correct`  | `.correct`  | `color: var(--revealed)` (existing green token)  |
| `mismatch` | `.mismatch` | `color: #fff; background: #b03a3a`               |
| `skipped`  | `.skipped`  | `color: #d49a3a; text-decoration: underline dotted` |

`current` reuses typing.html's `.cursor` blink. `correct` and `mismatch` reuse `--revealed` and bad-flash so visual language stays consistent.

### Cursor + scroll

The cursor is whichever `.sw-word` currently has `.current`. After every state mutation, `cursorEl.scrollIntoView({ block: 'center', behavior: 'smooth' })` — identical to typing.html line 182.

### Mic indicator

Small SVG mic in the header. Idle = gray, no animation. Listening = green fill + CSS `box-shadow` pulse (0.9s loop). Optional stretch: 16-bar waveform via `AudioContext.createAnalyser()`; falls back to the pulse if unavailable.

### Controls (header buttons)

| Button        | Behaviour                                                          |
|---------------|--------------------------------------------------------------------|
| Start / Pause | Toggles STT recognition. Label and icon swap.                      |
| Finish        | Forces end-of-session, jumps to results screen.                    |
| Mark correct  | Forces the current red word to `correct` and advances the cursor.  |
| Skip word     | Marks current word `skipped`, advances cursor. (Enter shortcut.)   |
| Reveal letter | Reveals the next letter of the current word as a hint. (Tab shortcut.) |

Tab / Enter mirror typing.html muscle memory: Tab nudges, Enter gives up.

### Reader options

```js
installReaderOptions({
  textEl: document.getElementById('text'),
  include: ['font', 'theme', 'size', 'lh', 'width', 'stripMd'],
});
```

Speak-only controls (sensitivity, auto-advance, lang, mic) extend the same panel — see section 7.

---

## 3. Scoring engine

Lives in `speak.js` as a pure module — consumes STT events, mutates a state object, calls a single `paint(i)` to update one span. Pure = unit-testable under Node + jsdom later.

### Tokenization

Reuse `tokenize()` from `data.js`. Build a **scoreable index** — the subsequence of token indices where `type === 'token' && isWord === true`. The cursor moves through `scoreable[]`, not `tokens[]`. Spaces, punctuation, line breaks, parens auto-skipped.

For Hobbit's `(D)oğu, (G)üney` markers: parens stay in the surrounding token's text, but `canonicalize()` strips non-letters before comparing — so only letter content is scored.

### Canonicalization

Lift `TR_GROUPS` + `CANON_MAP` + `canon()` out of `typing.html` (lines 96–114) into `data.js` as `canonicalize(word)`:

```js
// data.js — new helper, shared by typing.html and speak.js
const TR_GROUPS = ['aAâÂ','cCçÇ','eE','gGğĞ','iIıİîÎ','oOöÖ','sSşŞ','uUüÜûÛ'];
const CANON_MAP = {};
for (const g of TR_GROUPS) {
  const key = g[0].toLowerCase();
  for (const ch of g) CANON_MAP[ch] = key;
}
function canonicalize(word) {
  return [...word.toLowerCase()]
    .map(ch => CANON_MAP[ch] ?? ch)
    .filter(ch => /\p{L}/u.test(ch))
    .join('');
}
```

`typing.html` keeps its per-character `canon()` shim alongside; `speak.js` uses `canonicalize` for whole-word comparison.

### Match algorithm

For every STT token `heard`, run this cascade:

```
canonicalize(heard) vs canonicalize(target[cursor])
  ├── exact match at cursor → CORRECT, cursor++
  ├── exact match at cursor+1..+3 → intermediates SKIPPED, match CORRECT, cursor = match+1
  ├── Levenshtein ≤ threshold (1 strict / 2 forgiving / 3 lyrics) → CORRECT, cursor++
  └── nothing matches → MISMATCH on cursor, 300ms cooldown, await retry / skip
```

### Pseudocode

```js
// speak.js — scoring core
const COOLDOWN_MS = 300;
const LOOKAHEAD   = 3;
const FUZZY_MAX   = { strict: 1, forgiving: 2, lyrics: 3 };

const state = {
  scoreable: [], cursor: 0, wordState: [], heardFor: [],
  lastMismatchAt: 0, mistakes: 0, skipped: 0, correct: 0,
  startedAt: 0, mode: 'strict',
};

function onSttToken(heard, isFinal) {
  if (state.cursor >= state.scoreable.length) return;
  const now = performance.now();
  if (state.wordState[state.cursor] === 'mismatch'
      && now - state.lastMismatchAt < COOLDOWN_MS) return;

  const h = canonicalize(heard);
  if (!h) return;                              // STT artifact ("uh")
  const fuzzyMax = FUZZY_MAX[state.mode];

  // 1) exact match at cursor
  if (h === canonAt(state.cursor)) {
    commit(state.cursor, 'correct', heard); advance(); return;
  }

  // 2) lookahead — user skipped fillers
  for (let k = 1; k <= LOOKAHEAD; k++) {
    if (h === canonAt(state.cursor + k)) {
      for (let j = 0; j < k; j++) {
        commit(state.cursor + j, 'skipped', null); state.skipped++;
      }
      commit(state.cursor + k, 'correct', heard);
      state.cursor = state.cursor + k + 1; paintCurrent(); return;
    }
  }

  // 3) fuzzy at cursor
  if (levenshtein(h, canonAt(state.cursor)) <= fuzzyMax) {
    commit(state.cursor, 'correct', heard); advance(); return;
  }

  // 4) no match
  if (state.mode === 'forgiving' && !isFinal) return;
  commit(state.cursor, 'mismatch', heard);
  state.mistakes++; state.lastMismatchAt = now;
}

function canonAt(i)         { return i < state.scoreable.length ? canonicalize(tokens[state.scoreable[i]].text) : ''; }
function commit(i, s, heard){ state.wordState[i] = s; if (heard !== null) state.heardFor[i] = heard; if (s === 'correct') state.correct++; paint(i); }
function advance()          { state.cursor++; paintCurrent(); if (state.cursor >= state.scoreable.length) finish(); }
```

### Interim vs final transcripts

- **Interim** results paint live highlighting (~150ms latency).
- **Final** results commit the segment. If an interim guess was wrong but final is correct we want to rewrite history: re-run `onSttToken(token, true)` per final token so the matcher can flip `mismatch` back to `correct`. Only count `mistakes` once `isFinal === true`.

### Levenshtein

Standard DP, capped at the fuzzy threshold for early exit:

```js
function levenshtein(a, b, max = 2) {
  if (Math.abs(a.length - b.length) > max) return max + 1;
  const dp = Array(b.length + 1).fill(0).map((_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let prev = dp[0]; dp[0] = i;
    let rowMin = dp[0];
    for (let j = 1; j <= b.length; j++) {
      const tmp = dp[j];
      dp[j] = a[i-1] === b[j-1]
        ? prev
        : 1 + Math.min(prev, dp[j], dp[j-1]);
      prev = tmp;
      if (dp[j] < rowMin) rowMin = dp[j];
    }
    if (rowMin > max) return max + 1;
  }
  return dp[b.length];
}
```

### Cursor state machine

```
  ┌─────────┐  STT match  ┌─────────┐  next     ┌─────────┐
  │ PENDING │ ──────────► │ CORRECT │ ────────► │ PENDING │
  └────┬────┘             └─────────┘           └─────────┘
       │ STT no-match
       ▼
  ┌──────────┐  retry match ─────────────────────► CORRECT
  │ MISMATCH │  Mark correct ────────────────────► CORRECT
  └────┬─────┘  Skip word ──────────────► SKIPPED ──► PENDING (next)
       │ cooldown <300ms
       └─ ignore further STT tokens until cooldown clears
```

---

## 4. Modes within the mode

Sensitivity toggle in the options panel, stored under `speakMode` via `getPref`/`setPref`.

- **Strict** *(default)*: fuzzy ≤ 1. Every mismatch logs, cursor pauses. Right for verification once you know the text cold.
- **Forgiving**: fuzzy ≤ 2, commits a mismatch only on a final transcript — gives interim STT a chance to self-correct.
- **Lyrics**: fuzzy ≤ 3, lookahead grows to 5, accepts prefix / substring matches. For songs where user tempo and STT tokenization disagree.

---

## 5. Edge cases

| Case                          | Handling                                                                                                |
|-------------------------------|---------------------------------------------------------------------------------------------------------|
| No `SpeechRecognition`        | Detect at load (`window.SpeechRecognition || window.webkitSpeechRecognition`). If absent, show "Speech mode needs Chrome, Edge, or Safari" with a link to `typing.html?id=...`. Header controls hidden. |
| Mic permission denied         | `error === 'not-allowed'`. Replace mic indicator with a red badge + "Mic access blocked — click to retry" button that re-invokes `recognition.start()`. |
| Network drop (Chrome STT)     | `error === 'network'`. Pause the session, show "Lost connection. Retry?" — clicking restarts STT without resetting word state. |
| STT misheard (false negative) | Red word stays selected; **"Mark correct"** button (or click the red word) flips it to `correct`. Heard text stored in `state.heardFor[i]` for the diff. |
| Long silence                  | Chrome's STT auto-ends after ~5s silence and fires `end`. Auto-restart unless user clicked Pause (gate on a `wasPaused` flag). |
| Same word mispronounced twice | 300ms cooldown blocks the same audio chunk from logging two mistakes against one target word. |

---

## 6. Result screen

Overlay replacing the text area when `cursor >= scoreable.length` or Finish is clicked.

```
+----------------------------------------------------------+
|                  Session complete                        |
|   87 words · 81 correct (93%) · 4 mistakes · 2 skipped   |
|   Time: 1:42                                             |
|   [Practice again]  [Listen]  [Back to text]             |
|                                                          |
|   Diff:                                                  |
|   Bu şehirde bir peri beni nerelere götürüyo'            |
|                          (skipped)                       |
|   Geceleri sokakta yıldızlar altında ...                 |
|            ^^^^^^^ heard: "sokağa"                       |
+----------------------------------------------------------+
```

- Word colours match the in-session palette.
- Hovering a red word shows a tooltip with `state.heardFor[i]`.
- **Listen** = `SpeechSynthesisUtterance` overlay reading the canonical text in the detected language.
- **Practice again** = reset state, re-render, re-arm STT.

---

## 7. Settings panel additions

Four speak-only controls appended to the panel:

| Pref key              | Type   | Default     | Options                                |
|-----------------------|--------|-------------|----------------------------------------|
| `speakMode`           | select | `strict`    | Strict / Forgiving / Lyrics            |
| `speakAutoAdvance`    | check  | `true`      | Auto-skip after 2s silence on a word   |
| `speakLang`           | select | auto-detect | `tr-TR`, `en-US`, `en-GB`, etc.        |
| `speakMicDeviceId`    | select | default     | From `navigator.mediaDevices.enumerateDevices()` filtered to `audioinput` |

Extend `READER_DEFS` in `reader.js` with the four keys and add them to `speak.html`'s `include`. Lang auto-detect heuristic: if `>30%` of the text characters fall in the Turkish-specific set `[ıİğĞşŞçÇöÖüÜ]`, default `tr-TR`; otherwise `en-US`.

---

## 8. Code architecture

```
speak.html        — page shell, mirrors typing.html structure
speak.js          — scoring engine + STT wiring (kept separate for testability)
data.js           — gains canonicalize() (extracted from typing.html)
reader.js         — gains 4 new READER_DEFS entries for speak-mode prefs
index.html        — MEMORIZE group gains a new card (Speak it)
docs/speech.md    — STT API choice / browser matrix (companion doc)
docs/speak-mode.md — this file
```

### speak.js main loop pseudocode

```js
// speak.js
const recognition = new (window.SpeechRecognition || window.webkitSpeechRecognition)();
recognition.lang = resolveLang(activeText.body);
recognition.continuous = true;
recognition.interimResults = true;

recognition.onresult = (ev) => {
  for (let i = ev.resultIndex; i < ev.results.length; i++) {
    const res = ev.results[i];
    const tokens = res[0].transcript.trim().split(/\s+/);
    for (const tok of tokens) onSttToken(tok, res.isFinal);
  }
};
recognition.onerror = (ev) => handleSttError(ev.error);
recognition.onend = () => { if (!state.paused) recognition.start(); };

document.getElementById('start').addEventListener('click', () => {
  state.startedAt ||= performance.now();
  state.paused = false;
  recognition.start();
});
document.getElementById('pause').addEventListener('click', () => {
  state.paused = true; recognition.stop();
});
```

Matcher (`onSttToken`, `commit`, `advance`) is pure over `state` and `tokens`; DOM coupling is one `paint(i)` that mutates a span's `className`. Cheap to unit-test.

---

## 9. Card on home page

Add to the **Memorize** group in `MODE_GROUPS` (index.html). _Implementation step — do not write code in this doc._

```
{
  label: 'Speak it',
  file: 'speak.html',
  diff: 'expert',
  diffLabel: 'Hard',
  desc: 'Recite the text aloud. Live speech-to-text scores each word as you speak. Chrome / Edge / Safari only.'
}
```

(146 chars, under the 150 cap.) Slot it after **Type it out** — speaking-from-memory is at least as demanding; the user can't see their own output and must commit before the word lands.

---

## 10. Stretch features (not in v1)

- **Pitch / rhythm scoring** — pair STT timestamps with a beat reference; needs a recorded track per song.
- **Echo mode** — TTS reads a line, mic listens for the user repeating it, scores per-line. Reuses the engine.
- **Recording playback** — `MediaRecorder` captures mic; result screen offers `<audio>` so the user hears their own delivery.
- **Per-line scoring for poetry** — matcher resets per line, accuracy reported per stanza, weak lines flagged for targeted drill.
- **SRS integration** — feed accuracy into a future stats system so weak lines surface in tomorrow's session.
