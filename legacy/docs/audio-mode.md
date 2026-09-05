# Audio Recite Mode

A seventh mode for `memo-poc`. The app reads the target text aloud via
TTS, the user listens and recites along — classic shadowing. It mirrors
the planned `speak-mode.md` (user speaks, app listens). Especially
valuable for songs (rhythm), poetry (cadence) and language drills.

---

## 1. The flow

1. Pick a text on `index.html`, open the **Audio Recite** card.
2. Text renders line by line, controls dock at the bottom.
3. Tap **Play** — TTS starts from the top.
4. A highlight tracks the current word; the active line is brightest,
   neighbours dim to ~40% opacity.
5. End-of-line: continue (**Read along**) or pause *N* seconds (**Echo**).
6. The user can scrub by line, change rate, swap voice, or push the
   **Drop-out** slider to progressively silence TTS and carry more of
   the recitation themselves.

### Controls

```
[<< line]  [<< 5s]  [PLAY/PAUSE]  [5s >>]  [line >>]  [RESTART]
Rate: 0.5x ----[1.0x]---- 2.0x       Voice: Yelda (tr-TR) v
Pause between lines: [3.0 s]         Drop-out: [0% ......... 100%]
Mode: ( ) Read along  (o) Echo  ( ) Drop-out
```

### Sub-modes

- **Read along** — continuous TTS, highlight scrolls.
- **Echo** — TTS reads one line, pauses `pauseBetweenLines`, advances.
- **Drop-out** — TTS coverage shrinks per pass, forcing recall (§6).

---

## 2. Web Speech API: what's actually shippable

The free-tier engine is `window.speechSynthesis` and
`SpeechSynthesisUtterance`. The relevant surface area:

```js
const u = new SpeechSynthesisUtterance(text);
u.voice  = voice;       // one of speechSynthesis.getVoices()
u.lang   = 'tr-TR';
u.rate   = 1.0;         // 0.1 – 10, browsers clamp to ~0.5–2
u.pitch  = 1.0;         // 0 – 2
u.volume = 1.0;         // 0 – 1
u.onboundary = (e) => { /* e.charIndex, e.charLength, e.name */ };
u.onend      = () => { /* advance to next line */ };
speechSynthesis.speak(u);
```

### Turkish voice availability (May 2026)

- **macOS / iOS / iPadOS**: `Yelda` (female, tr-TR) is the headline Apple
  voice — natural cadence, strongest default in the Apple ecosystem.
- **Windows 10/11**: Microsoft `Tolga` (male) is the bundled SAPI voice.
  Edge also exposes online Azure Neural voices on recent versions
  (`Microsoft AhmetNeural`, `Microsoft EmelNeural`) — Edge-only, online.
- **Android**: Google's Turkish TTS engine ships with most devices.
  Quality varies — Pixel and recent Samsungs sound natural, older
  mid-range phones are robotic.
- **Linux / Firefox**: Turkish coverage is poor; espeak fallback is
  barely usable. Surface a warning when no `tr-*` voice is found.

### Voice discovery

`speechSynthesis.getVoices()` returns synchronously in Safari and Firefox
but is asynchronous in Chromium — listen for `voiceschanged`:

```js
function getVoicesAsync() {
  return new Promise(resolve => {
    let v = speechSynthesis.getVoices();
    if (v.length) return resolve(v);
    speechSynthesis.addEventListener('voiceschanged', () => {
      resolve(speechSynthesis.getVoices());
    }, { once: true });
  });
}
```

Filter to `voice.lang.startsWith('tr')` and prepend the system default.

### `onboundary` reliability

The MDN compatibility table and field reports confirm `boundary` fires
reasonably on macOS Safari, Windows Chrome and Windows Edge. It is **not
fired on Android Chrome** and is **unreliable on Linux**. Plan for a
fallback (§4).

---

## 3. Higher-quality cloud TTS (optional / premium tier)

Web Speech is free and offline but quality is uneven. For a future paid
tier, the credible options (verified May 2026):

| Provider | Turkish voices | Headline price | Free tier |
|---|---|---|---|
| **ElevenLabs Multilingual v2** | Supported (29+ langs, native Turkish quality) | ~$5/30k credits Starter, then $0.18 / 1k chars | 10,000 credits/mo, non-commercial |
| **Google Cloud TTS – Neural2** | tr-TR voices A–E (male/female) | **$16 / 1M chars** | 1M chars/mo free (Neural2) |
| **Google Cloud TTS – Chirp 3 HD** | Turkish on Chirp 3 | **$30 / 1M chars** | included in free tier with limits |
| **Azure Speech – Neural** | `tr-TR-AhmetNeural`, `tr-TR-EmelNeural` | **$15 / 1M chars** | 0.5M chars/mo free |
| **Azure Speech – Neural HD** | same Turkish voices | **$22 / 1M chars** (down from $30 in March 2026) | same |
| **AWS Polly Neural** | `Filiz` (female, tr-TR, neural) | **$16 / 1M chars** | 1M chars/mo for 12 months |
| **OpenAI tts-1 / tts-1-hd** | Turkish supported (Whisper coverage) | **$15 / 1M chars** (`tts-1`), **$30 / 1M chars** (`tts-1-hd`) | none |
| **Piper TTS (in-browser WASM)** | Community Turkish models available via Rhasspy registry | Free, MIT | n/a (≈75 MB per voice download) |

Azure's `EmelNeural` and Google Neural2 `tr-TR-Wavenet-E` give the most
human-sounding Turkish for verse. ElevenLabs is the most expressive but
priciest at volume.

**POC recommendation:** ship with `SpeechSynthesis` only — useful out of
the box on macOS/iOS (Yelda) and Edge (Tolga). Add a provider dropdown
stub so a cloud path can be wired later behind a user-supplied key.
Piper-WASM is the most attractive next step: offline and free.

---

## 4. Word-level highlight synchronization

We already tokenize the body via `data.js` `tokenize()`. The output
gives us a flat `tokens[]` array with `text`, `start`, `end` character
offsets, and a `lineMap`. The highlighter needs to map the current
spoken character index back to a token index.

### Primary path: `onboundary`

`event.charIndex` is the offset **within the utterance string** of the
current word's first character. Per spec, `event.charLength` is also
available (Chromium, Safari ≥16) and gives the length of that word.

```js
function speakLine(lineText, lineStartOffset) {
  const u = new SpeechSynthesisUtterance(lineText);
  u.voice = currentVoice;
  u.rate  = state.rate;
  u.onboundary = (e) => {
    if (e.name && e.name !== 'word') return;     // skip 'sentence'
    const absChar = lineStartOffset + e.charIndex;
    const tokenIdx = tokenIndexAtChar(absChar);
    highlight(tokenIdx);
  };
  u.onend = () => onLineComplete();
  speechSynthesis.speak(u);
}

function tokenIndexAtChar(charOffset) {
  // binary search over tokens[].start
  let lo = 0, hi = tokens.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (tokens[mid].start <= charOffset) lo = mid;
    else hi = mid - 1;
  }
  return lo;
}
```

### Gotcha: byte vs. character indices

Some Chromium builds return `charIndex` as UTF-16 units — fine for
ASCII but matters for Turkish diacritics in NFC vs NFD form. Normalise
with `text.normalize('NFC')` before tokenising **and** before speaking
so indices line up.

### Fallback: time-based interpolation

When `onboundary` never fires (Android Chrome, some Linux Firefox),
estimate token position from elapsed time and an empirical chars/sec:

```js
// cps is calibrated: Turkish averages ~14 chars/s at rate=1.0
const cps = 14 * state.rate;
const startedAt = performance.now();
const tick = () => {
  if (!state.speaking) return;
  const elapsed = (performance.now() - startedAt) / 1000;
  const charNow = Math.floor(elapsed * cps);
  highlight(tokenIndexAtChar(lineStartOffset + charNow));
  requestAnimationFrame(tick);
};
requestAnimationFrame(tick);
```

Drift is ±200 ms over a long line — readable. Re-sync per line at
`onend`.

---

## 5. Echo / pause mode design

The most useful mode for memorisation. State machine:

```
PLAY_LINE -> WAIT_USER -> [optional CONFIRM_LINE] -> next line
```

- `PLAY_LINE`: TTS speaks line *i*; transition on `onend`.
- `WAIT_USER`: `pauseBetweenLines` timer (default 4 s, 0–10 s). Line
  pulses, a "your turn" affordance shows, optional countdown ring.
- `CONFIRM_LINE` (toggle, off by default): TTS re-reads the line.
- Advance to line *i+1*.

The pause duration auto-scales with line length when "auto" is checked:
`pause = clamp(lineCharCount / cps * 1.1, 2, 10)`.

**Synergy with `speak-mode.md`**: during `WAIT_USER`, hand off to its
SpeechRecognition engine. Score the utterance against the line — pass
to advance, fail to replay. Closes the loop into a real trainer.

---

## 6. Drop-out mode (progressive challenge)

Inspired by Spaced Recall ladders. The same text is replayed in passes;
each pass the TTS reads less of every line, the user fills the rest.

| Pass | TTS coverage | What the user does |
|------|--------------|--------------------|
| 1 | 100 % | Listen only |
| 2 | 80 % | Recite the last 20 % aloud |
| 3 | 50 % | Recite the second half |
| 4 | 0 %  | Whole line from memory |

Implementation is a **fade slider** `dropoutPct` (0–100). For each line:

```js
const cutoff = Math.floor(line.text.length * (1 - dropoutPct / 100));
const spoken = line.text.slice(0, cutoff);
const silent = line.text.slice(cutoff);
speak(spoken).then(() => waitFor(silentDurationEstimate(silent)));
```

The "silent duration estimate" uses the same `cps` calibration so the
visual highlight continues to scroll across the muted tail, keeping
rhythm. The dimmed-tail visual cue tells the user *what* to fill in.

---

## 7. Settings panel

Extend `installReaderOptions()` in `reader.js` (`include` array) with:

| Key | Type | Range | Default |
|---|---|---|---|
| `audioVoice` | select | populated from `speechSynthesis.getVoices()` filtered to `tr-*` plus system | first `tr-TR` voice |
| `audioRate` | slider | 0.5 – 2.0, step 0.1 | 1.0 |
| `audioPitch` | slider | 0.5 – 2.0, step 0.1 | 1.0 |
| `audioPauseBetweenLines` | slider | 0 – 10 s, step 0.5 | 3.0 |
| `audioDropoutPct` | slider | 0 – 100 %, step 5 | 0 |
| `audioProvider` | select | `web-speech`, `cloud (soon)` | `web-speech` |
| `audioSubmode` | radio | `read-along`, `echo`, `dropout` | `echo` |

All persist to `localStorage` under the existing reader-options keys so
the choice carries across sessions and modes.

---

## 8. Visual design (ASCII mock)

```
+---------------------------------------------------------------+
|  memo-poc  •  Audio Recite                       [ Settings ] |
|---------------------------------------------------------------|
|                                                               |
|     dimmed     Peri beni, dağlara at beni                    |
|                                                               |
|   FOCUS  >>  Sevdiğim yardan ayır beni  <<                    |
|                                                               |
|     dimmed     Yandım allı turnam yandım                      |
|     dimmed     Sevdiğin yâre ulaşamadım                       |
|                                                               |
|---------------------------------------------------------------|
|  [<<L] [<<5] (  PLAY  ) [5>>] [L>>]  Restart   Voice: Yelda   |
|  Rate o------|---o   1.0x    Echo pause: 3.0 s   Drop-out:0%  |
|  [=================>------------------------------]  37%      |
+---------------------------------------------------------------+
```

The current line is full opacity (`#f7f4ec`), other lines fade to
`opacity: 0.4`. The active word inside the current line gets a soft
underline plus +6% font-size. Progress bar at the bottom is character
position in the full text. Big tap target on the PLAY pill (60×60 on
mobile).

---

## 9. Code architecture

New files (mirroring existing mode pages, no edits to others in this
phase):

- `audio.html` — skeleton clone of `rsvp.html`: pulls `data.js`,
  `reader.js`, `audio.js`. Hosts the line-rendered DOM, controls bar,
  progress bar.
- `audio.js` — controller with the TTS state machine, voice loader,
  boundary handler, highlighter, dropout logic. Exports nothing global
  except a `bootAudioMode()` called once `DOMContentLoaded` fires.

Reused:

- `data.js` `getActiveText()` and `tokenize()` — same contract as
  `spread.html`/`focus.html`.
- `reader.js` `installReaderOptions({ include: [...], onChange })` —
  add new `include` entries listed in §7.

`index.html` work (deferred to the implementation step): add an Audio
Recite card inside the MEMORIZE group, between *Focus* and *RSVP*. Not
modifying any files in this spec.

---

## 10. Browser quirks and pitfalls

- **User gesture required.** Chrome and Safari block
  `speechSynthesis.speak()` without a prior user gesture. The Play
  button is that gesture; never auto-play.
- **`getVoices()` async on Chromium.** Race against `voiceschanged`
  (§2).
- **Long-text choke.** Chrome stops around ~15,000 chars, Safari around
  ~32,000. Workaround: feed per line (which we already do) and re-queue
  on `onend`.
- **iOS Silent switch.** Hardware mute kills TTS with no API override.
  Show a one-time hint.
- **Speed change mid-utterance.** Most browsers ignore a new `rate`
  until the next utterance. Cancel current and re-speak from the
  current token; ~150 ms stutter is acceptable.
- **Tab backgrounding.** Chromium pauses TTS on hidden tabs. Listen for
  `visibilitychange` and sync UI state.
- **Locale fallback.** Missing `tr-*` voice causes English phonetics —
  detect and block with a banner suggesting install or cloud provider.

---

## 11. Stretch features

- **Pre-render and cache.** Generate cloud TTS into IndexedDB for
  instant replay, offline, and accurate scrubbing.
- **Karaoke mode.** Syllable-level timing via forced alignment
  (WhisperX, Montreal Forced Aligner) over pre-rendered audio. Useful
  for `peri-beni.txt`.
- **Multi-voice dialogue.** Tag spans with `data-speaker` and assign
  per-tag voices (Yelda for narrator, Tolga for Gandalf in Hobbit).
- **A/B-loop.** Mark two anchors, replay between them N times.
- **Export practice MP3.** Downloadable pre-rendered audio for off-app
  practice.

---

Sources: [MDN onboundary](https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesisUtterance/boundary_event),
[web-speech-recommended-voices/tr](https://github.com/HadrienGardeur/web-speech-recommended-voices/blob/main/json/tr.json),
[Google Cloud TTS pricing](https://cloud.google.com/text-to-speech/pricing),
[Azure Speech pricing](https://azure.microsoft.com/en-us/pricing/details/speech/),
[Amazon Polly pricing](https://aws.amazon.com/polly/pricing/),
[OpenAI TTS](https://developers.openai.com/api/docs/guides/text-to-speech),
[ElevenLabs API](https://elevenlabs.io/pricing/api),
[Rhasspy Piper](https://github.com/rhasspy/piper),
[piper-tts-web](https://www.npmjs.com/package/@mintplex-labs/piper-tts-web).
