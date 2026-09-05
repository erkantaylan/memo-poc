# memo-poc — Feature Roadmap

_Last updated: 2026-05-11_

Six modes ship today (spread, first-letters, focus, typing, RSVP, bionic) with a sessionStorage library, two Turkish samples, full reader options, pagination, and Markdown+HTML import cleaning. PWA wrap is the obvious next big rock. What follows is the forward plan, sliced by deployment model.

---

## 1. POC features still doable in pure static client

No backend, no native shell. All ship as HTML/JS/CSS to GitHub Pages.

- **Cumulative recall (Cull 2000)** — Walk line-by-line: recite line 1, then 1+2, then 1+2+3. Strongest verbatim-recall protocol in the literature. **Value: high. Effort: M.** New `cumulative.html`, slotted in `MODE_GROUPS` between First-letters and Focus.
- **Backward chaining** — Same as cumulative, but build from the last line backward; user always lands on familiar ground. **Value: high. Effort: S** (variant). Share the page via a direction toggle.
- **Copy-recite-check** — Free typing into a textarea, word-level diff on submit. Complements typing.html which blocks per-error. **Value: medium-high. Effort: M.** New `recite.html` + LCS diff util.
- **Audio TTS recite** — `SpeechSynthesis` reads aloud with rate/voice controls; sentence-chunked listen-and-repeat. **Value: high** (eyes-free practice). **Effort: S.** "Speak" button in reader-options; dedicated `tts.html`.
- **Speech-based memorization** — STT compares spoken recitation to target, matched words green, mismatched red, scoreboard at end. See `docs/speech.md`. **Value: very high. Effort: L.** Chromium/Edge only; document the caveat.
- **Auto chapter detection** — Detect `BÖLÜM`, `Chapter`, roman numerals, all-caps short lines and bias page breaks there so the Hobbit reads naturally. **Value: medium. Effort: S.** Patch `paginate()` in `data.js`.
- **Level self-assessment → mode recommendation** — Three-question prompt ("Read once? Recite half? Know cold?") emits a rule-based mode order. **Value: medium. Effort: S.**
- **PWA wrap** — `manifest.json` + service worker caching the app shell + samples. Installable, full-screen, offline. **Value: very high. Effort: S.** Prerequisite for credible mobile use.
- **Export / import JSON** — Download library + prefs as JSON, paste-to-import elsewhere. Stop-gap for sessionStorage volatility. **Value: medium. Effort: S.**
- **Search-within-text** — In-page find for long texts. **Value: medium. Effort: S.**
- **Bookmarks / resume** — Per-text last-page + last-mode in `localStorage`. **Value: high. Effort: S.** Promote just the bookmark slice; texts stay session-scoped.
- **Dark-mode auto-detection** — Honour `prefers-color-scheme` until user picks. **Value: low-medium. Effort: S.**
- **Keyboard navigation polish** — Unify shortcuts (space=reveal, R=reset, arrows=nav, Esc=back) + `?` overlay. **Value: medium. Effort: S.**
- **Accessibility pass** — ARIA labels, focus rings, tab order, `aria-live` reveal regions. **Value: medium. Effort: M.**
- **Stats per text** — localStorage: words-revealed (failed), last-practiced, session count; render on text card. **Value: high. Effort: M.** Substrate for any future spaced-repetition feature.
- **Reset-progress button** — Wipes stats per text. **Value: low. Effort: S.**
- **Typing-mode tolerance toggles** — Optional skip-punctuation, ignore-case, ignore-diacritics. **Value: medium. Effort: S.**
- **Diff replay** — Show last typing/recite attempt with red strikes / green hits; persist in localStorage. **Value: medium-high. Effort: M** (shares diff util).

---

## 2. Mobile-native-only features

Require a Capacitor (recommended — lightest), React Native, or Flutter shell.

- **Push notifications** for spaced-repetition reminders ("Practice Sonnet 18 — due today")
- **Background audio playback** so TTS recite keeps playing with the screen off / app backgrounded
- **Native mic capture** with on-device or platform STT (better than `webkitSpeechRecognition`, especially for Turkish)
- **Native TTS** — Apple's Siri voices and Google Neural voices sound dramatically better than the Web Speech defaults
- **Share-extension** so the user can send text from any app ("Share to memo")
- **Haptic feedback** on typing-mode correctness (subtle tick on right key, buzz on error)
- **Offline-first sample bundling** beyond service-worker caching — texts shipped in the binary

---

## 3. Backend-required features

A backend (Supabase / Cloudflare D1 / similar lightweight option) is only justified once two-device sync is genuinely needed.

- Spaced repetition with **progress synced across devices** (the SR algorithm itself is client-side; only the schedule needs to sync)
- Multi-device library
- Accounts (anonymous-first — magic-link upgrade later)
- Shared texts / public links
- Classroom / multi-user features
- Server-side analytics: streaks, retention curves over weeks

---

## 4. Prioritized roadmap

Ordered by value/effort and the heuristics: validate static first, science-backed beats polish, no backend until 2-device-sync is real.

1. **PWA wrap** — static / S — Unlocks daily mobile use without any native work; prerequisite to claiming the app is usable on a phone.
2. **Bookmarks + resume** — static / S — Cheapest motivation feature; uncomfortable not having it once PWA is on a home screen.
3. **Cumulative recall mode** — static / M — Memorization-science-backed; the missing third pillar between first-letters and focus.
4. **Audio TTS recite** — static / S — Massive UX leap (eyes-free) for tiny code; free input for the speech mode that follows.
5. **Stats per text + last-practiced** — static / M — Required substrate for any future spaced-repetition feature; visible value on day one.
6. **Speech-based memorization mode** — static / L — Highest-ceiling new mode. _Depends on:_ `docs/speech.md` design output, TTS work (#4) for shared audio plumbing.
7. **Backward chaining mode** — static / S — Cheap variant of cumulative (#3) once that scaffolding exists. _Depends on #3._
8. **Copy-recite-check + diff replay** — static / M — Pairs naturally; ships the diff util once.
9. **Spaced repetition (client-only, localStorage)** — static / M — Builds on stats (#5). Defer cross-device sync until proven needed.
10. **Auto chapter detection** — static / S — Quality-of-life for the Hobbit; ships in an afternoon.
11. **Search-within-text** — static / S — Same shipping window as #10.
12. **Typing-mode tolerance toggles** — static / S — Removes the main friction complaint about typing mode.
13. **Keyboard navigation polish + shortcuts overlay** — static / S — Power-user retention; do alongside accessibility (#14).
14. **Accessibility pass (ARIA, focus, prefers-color-scheme)** — static / M — Right thing to do; opens the app to more users.
15. **Export / import JSON** — static / S — Stop-gap until backend sync exists.
16. **Level self-assessment → mode recommendation** — static / S — Nice onboarding once there are more modes to choose between.
17. **Reset-progress button** — static / S — Trivial once stats exist.
18. **Native shell (Capacitor) + native TTS + haptics + share-extension** — native / L — Only after the static PWA has been used daily for weeks and the limitations are concrete.
19. **Push notifications for SR reminders** — native / M — Depends on #18 and #9; the killer reason to leave the browser.
20. **Background audio playback** — native / S — Depends on #18; cheap unlock once shell exists.
21. **Backend: account + cross-device sync** — backend / L — Only triggered by a real "I use this on two devices" pain point. Anonymous-first.
22. **Shared texts / public links** — backend / M — Depends on #21.
23. **Classroom / multi-user** — backend / L — Only if there's demand; out of scope for a personal POC.

---

## 5. Quick wins for the next 1-2 sessions

Ship-tomorrow tasks, high impact, low effort:

1. **PWA manifest + service worker** — 50 lines of JS + a manifest + six icon sizes. Instantly installable on phone.
2. **Bookmarks: last text + last page in localStorage** — Read on `index.html` render, write on mode-page unload. ~30 lines.
3. **TTS "Speak" button in reader-options** — `speechSynthesis.speak(new SpeechSynthesisUtterance(text))` plus rate/voice selects. ~40 lines.
4. **Auto chapter detection in `paginate()`** — Regex for `^BÖLÜM`, `^Chapter`, all-caps short lines; bias page breaks to those boundaries.
5. **Honour `prefers-color-scheme`** — Default theme from `matchMedia('(prefers-color-scheme: dark)')` until user picks one.

---

## 6. Out of scope (won't build, with reason)

- **Method of loci / memory palace builder** — Fascinating but UX-heavy: needs 3D-ish spatial UI, image library, drag-drop placement. Doesn't fit "static page per mode".
- **OCR text import (photo → text)** — Tesseract.js is a 10MB+ download, struggles with Turkish diacritics, and the user can already paste from any phone-side OCR. Rathole.
- **Translation / bilingual side-by-side mode** — Would require a translation API (= backend or a paid key in client), and the existing texts are already in the user's native language.
- **AI-generated cloze deletions / quiz questions** — Needs an LLM call per text. Out of POC scope; revisit if a backend appears.
- **Video lessons / instructional content** — Not a memorization mechanic; mission creep.
- **Gamification (XP, badges, leaderboards)** — Cheap to add, but the user is the only player; defer indefinitely.
- **Generic flashcards (Anki-style)** — Anki exists and is excellent. memo-poc's niche is _continuous text_, not Q/A pairs.
- **Handwriting input mode** — Touch-handwriting recognition is a native-only feature with poor cross-browser support; not worth the complexity for the marginal benefit over typing.
