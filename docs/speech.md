# Speech-to-Text for `memo`: a Turkish-first memorization app

*Last updated: 2026-05-11. Prices are current as of mid-2026 and quoted in USD; verify before signing contracts.*

This document compares speech-to-text (STT) options for a memorization app whose primary use case is reciting Turkish text (rap lyrics, prose like Hobbit Türkçe çevirisi) back at the screen and having the app score it word-by-word in real time. Final platform targets are web + iOS + Android.

The product question is narrower than "transcribe anything." We already know the target text. We need:

1. **Low-latency streaming** with interim results so the UI can light up words as they are spoken.
2. **Good Turkish acoustic modeling**, including the eight vowels and `ı/İ/i/I`, `ş`, `ğ`, `ç`, `ö`, `ü`.
3. **Cheap** — ideally free at hobbyist volumes — because users will recite for many minutes per session.
4. **Permissive on partial / mispronounced words** — we will do the matching ourselves.

---

## 1. Free / native browser and OS APIs

### 1.1 Web Speech API (`window.SpeechRecognition`)

The W3C draft `Web Speech API` exposes `SpeechRecognition` (with the `webkit` prefix on most engines) directly in the browser. See the [MDN reference](https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API) and the editor's draft at [webaudio.github.io/web-speech-api](https://webaudio.github.io/web-speech-api/).

Browser support per [caniuse.com/speech-recognition](https://caniuse.com/speech-recognition) (May 2026):

| Browser | Status | Notes |
| --- | --- | --- |
| Chrome / Chromium (desktop & Android) | Supported since 25 | Audio is streamed to Google's servers. Requires network. |
| Edge | Reported **not supported** by caniuse. In practice Edge Chromium inherits a flag-gated build; do not rely on it. |
| Safari (macOS 14.1+ / iOS 14.5+) | "Partial" | Prefixed as `webkitSpeechRecognition`. Prompts the user before streaming audio to Apple. |
| Firefox | Disabled by default since 22 | Behind `dom.webspeech.recognition.enable` in `about:config`. Treat as unsupported for end users. |

Turkish (`tr-TR`) is accepted as a `lang` value on Chrome and Safari. There is no published WER number from either vendor for Turkish, but anecdotally Chrome's recognizer is the same model that backs Google Cloud Speech-to-Text, so quality is good for spoken Turkish prose (less so for fast or sung delivery — see §4).

Key feature notes:

- `interimResults = true` yields partial hypotheses every few hundred ms — exactly what we need for live highlighting.
- `continuous = true` lets the recognizer keep listening, but **Chrome drops the session after ~60 seconds of silence** and Safari is even more aggressive. You must auto-restart on `onend`.
- No formal rate limit, but Chrome will start returning `network` errors if you hammer it.
- **Privacy**: Chrome and Safari send raw audio to vendor servers. There is no on-device mode in any browser. This is fine for a memorization app, but it must be disclosed.

### 1.2 iOS: `SFSpeechRecognizer` (Speech framework)

The native Speech framework is [documented here](https://developer.apple.com/documentation/speech/sfspeechrecognizer). Key facts from Apple's own [QA1951](https://developer.apple.com/library/archive/qa/qa1951/_index.html):

- **1000 requests per hour per device**, shared across all apps. A "request" is one `SFSpeechRecognitionRequest`.
- **1 minute of audio per request** when using the server-side path. For a 4-minute song you must chunk and stitch.
- Exceeding the limit returns error code `203` ("Quota limit reached for resource: speech_api").
- **On-device mode** removes the audio-duration and per-hour caps. You enable it with `request.requiresOnDeviceRecognition = true`, but you must first check `recognizer.supportsOnDeviceRecognition` for the locale. See [supportsOnDeviceRecognition](https://developer.apple.com/documentation/Speech/SFSpeechRecognizer/supportsOnDeviceRecognition).
- On-device support has historically lagged. Older iOS versions shipped on-device for ~10 languages; newer iOS versions (iOS 17+) ship Turkish on-device on most modern devices, but you must runtime-check rather than assume.
- Free, no API key, no quota beyond the above.
- Partial results via `shouldReportPartialResults = true`.
- iOS 26 ships `SpeechAnalyzer`, the modern replacement that is on-device by design — worth tracking ([Apple's iOS 26 SpeechAnalyzer guide](https://antongubarenko.substack.com/p/ios-26-speechanalyzer-guide)).

### 1.3 Android: `android.speech.SpeechRecognizer`

[Android API reference](https://developer.android.com/reference/android/speech/SpeechRecognizer). Three relevant facts:

- Free. Backed by whichever recognition service is installed; on most phones that is Google's `com.google.android.googlequicksearchbox`.
- **Offline mode** works only after the user manually downloads the language pack from system settings ("Voice Input" / "Offline speech recognition"). Turkish *is* in that list, but adoption among users is unpredictable.
- **Pixel-only** on-device "Live Caption / Recorder" uses a much better model (the same one behind Gboard dictation). Third-party apps cannot directly use that model via `SpeechRecognizer`; you get whatever the OEM ships.
- Returns partial results via `EXTRA_PARTIAL_RESULTS`.

For a high-quality cross-vendor offline experience on Android, the canonical alternative is [Vosk](https://alphacephei.com/vosk/) (Kaldi-based, ~50 MB per language, Turkish supported, streaming).

### 1.4 React Native and Flutter wrappers

- [`@react-native-voice/voice`](https://github.com/react-native-voice/voice) wraps `SFSpeechRecognizer` on iOS and `SpeechRecognizer` on Android. Exposes `onSpeechPartialResults` and `onSpeechResults`. Pitfalls: requires the Google quick-search app on Android; no confidence scores; the project has historically been under-maintained and you may want the [`expo-speech-recognition`](https://github.com/jamsch/expo-speech-recognition) fork for Expo projects.
- Flutter [`speech_to_text`](https://pub.dev/packages/speech_to_text) wraps the same OS APIs. Exposes `partialResults: true` and a `localeId` parameter; pass `"tr_TR"` (or `"tr-TR"` on iOS) once you confirm with `speech.locales()` that the device has Turkish installed. No streaming on web; falls back to Web Speech API in a Flutter Web build, with all the caveats above.

Both wrappers leak the OS-level rate limits and quirks straight through. Treat them as thin shims, not abstractions.

---

## 2. Paid cloud STT

Pricing verified from each provider's own page in May 2026. Numbers are the cheapest standard real-time rate for a single language. Per-minute / per-hour figures rounded.

| Provider | Free tier | Real-time price | Turkish streaming? | Word timestamps | Latency tier | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| Google Cloud Speech-to-Text v2 (`chirp_2`) | 60 min/month | **$0.016/min** ($0.96/hr); Dynamic Batch $0.004/min | Yes | Yes | ~300–600 ms | [pricing](https://cloud.google.com/speech-to-text/pricing), [languages](https://docs.cloud.google.com/speech-to-text/v2/docs/speech-to-text-supported-languages). Turkish supported on `chirp` and `chirp_2` with punctuation and word-level confidence. |
| AWS Transcribe | 60 min/mo for 12 months | $0.024/min Tier 1 | **No — batch only for `tr-TR`** | Yes | n/a (batch) | [pricing](https://aws.amazon.com/transcribe/pricing/), [language matrix](https://docs.aws.amazon.com/transcribe/latest/dg/supported-languages.html). The supported-languages table explicitly lists Turkish as `batch` only. Disqualified for our use case. |
| Azure AI Speech | 5 hr/mo free | $1.00/hr standard ($0.0167/min); $0.50/hr at 50k-hr commitment | Yes (100+ langs including `tr-TR`) | Yes | ~300 ms | [pricing](https://azure.microsoft.com/en-us/pricing/details/speech/). Mature streaming SDK in Swift/Kotlin/JS. |
| Deepgram Nova-3 | $200 credit | **$0.0077/min** streaming ($0.46/hr); $0.0065/min on Growth plan | Yes (added Feb 2026) | Yes | ~300 ms | [pricing](https://deepgram.com/pricing), [Turkish announcement](https://deepgram.com/learn/deepgram-expands-nova-3-with-italian-turkish-norwegian-and-indonesian-support). "Double-digit relative WER reductions" vs Nova-2; streaming explicitly highlighted as the bigger gain for Turkish. |
| AssemblyAI Universal-Streaming | Free credits | $0.15/hr ($0.0025/min) | **No** for streaming. Multilingual streaming covers EN/ES/FR/DE/IT/PT only; Turkish is on the roadmap. | Yes | <300 ms | [streaming language FAQ](https://www.assemblyai.com/docs/faq/language-support-for-real-time-transcription), [pricing](https://www.assemblyai.com/pricing). Disqualified for streaming until they ship Turkish. |
| OpenAI Whisper API (`whisper-1`) | None | $0.006/min ($0.36/hr) | Batch only (no streaming endpoint) | Yes (segment) | n/a | [model card](https://developers.openai.com/api/docs/models/whisper-1). Turkish in the 99-language list; quality is solid for prose, weaker for music. |
| OpenAI `gpt-4o-transcribe` / `gpt-4o-mini-transcribe` | None | $0.006/min and $0.003/min respectively | Batch + websocket Realtime | Yes | ~500 ms | Modern alternative to `whisper-1`. Realtime API streams transcripts and is what to actually use if you commit to OpenAI. |
| Groq Whisper Large v3 | Generous free tier | $0.111/hr ($0.00185/min) Large v3, **$0.04/hr Turbo** ($0.00067/min) | Batch | Segment | 200×+ real-time *batch* (not true streaming) | [Whisper Large v3 on Groq](https://console.groq.com/docs/model/whisper-large-v3), [Turbo](https://console.groq.com/docs/model/whisper-large-v3-turbo). Cheapest hosted Whisper by a wide margin, but you submit complete clips, you do not stream. |
| Self-hosted whisper.cpp / faster-whisper | Free | $0 | Yes (model is multilingual) | Segment | Depends on hardware | [whisper.cpp](https://github.com/ggml-org/whisper.cpp), [faster-whisper](https://github.com/SYSTRAN/faster-whisper). Realistic on M-series Macs and modern desktops; marginal on low-end Android. Use `tiny`/`base` for phones (WER suffers on Turkish), `small`/`medium` on desktop. |

**Key takeaways for Turkish, May 2026:**

- The two cloud providers that combine genuine streaming + Turkish + low price are **Google Cloud (`chirp_2`)** and **Deepgram (Nova-3)**.
- Deepgram is roughly **2× cheaper than Google** for streaming and explicitly markets Turkish as a recent improvement target.
- AssemblyAI and AWS Transcribe are out for real-time Turkish today.
- Whisper-family options are batch only — useful as a server-side fallback for "score this entire recitation" mode, not for live highlighting.

---

## 3. Comparison algorithms for memorization-style correction

We always know the target text. We do not need full free-form transcription; we need a fast streaming aligner.

### 3.1 Word-level diff (Myers / LCS)

[Myers 1986](https://link.springer.com/article/10.1007/BF01840446) (`O(ND)`) is the standard. It runs at word granularity in microseconds for stanza-length inputs. Output: a script of `match | insert | delete` ops between hypothesis and target. Excellent for the post-recitation "scorecard" view.

### 3.2 Levenshtein per word

Once the diff has aligned word `i` of the hypothesis to word `j` of the target, run Levenshtein on that *pair* of words. A normalized distance below ~0.25 counts as a "fuzzy match" — covers the recognizer dropping a final consonant or hearing `çiçek` as `cicek`. Cheap: each word is short.

### 3.3 Phonetic similarity (Turkish-specific)

Turkish is nearly phonetic — one grapheme to one phoneme with minor exceptions (`ğ` lengthens the preceding vowel, devoicing at word ends) — so a soundex variant is genuinely useful. Options:

- **Beider-Morse Phonetic Matching** ships a Turkish ruleset and is built into [Apache Solr](https://solr.apache.org/guide/solr/latest/indexing-guide/phonetic-matching.html). Heavyweight but accurate.
- **Roll your own Turkish soundex**: collapse vowel-harmony pairs (`a/e`, `ı/i/u/ü`, `o/ö`), drop `ğ`, and treat `k/g`, `t/d`, `p/b` as voiced/voiceless equivalents. ~30 lines of code; good enough for memorization.

This composes nicely with the normalization the app already does (`ı/İ/i/I`, `ö/o`, `ü/u`, `ş/s`, `ğ/g`, `ç/c` collapsed). Reuse that same normalizer on the STT hypothesis stream before comparing — the recognizer occasionally outputs ASCII for proper nouns, and you want to match anyway.

### 3.4 Real-time partial-match loop

Suggested algorithm:

1. Maintain a `cursor` index into the target text, starting at 0.
2. On every interim STT result, take the *new* word(s) since the last frame.
3. For each new word, in order:
   - Normalize it (your existing Turkish-equivalent collapsing).
   - Compare against `target[cursor]`. If exact: green, advance cursor.
   - Otherwise compare against `target[cursor .. cursor+3]` with Levenshtein and the Turkish soundex. If any match within window: green, advance cursor past it (the user skipped one or more words).
   - Otherwise mark red and *don't* advance — wait one frame to see if the recognizer's interim revises itself (it often does).
4. After ~1.5 seconds of red with no progress, lock the red state, advance cursor by one, and resume.

### 3.5 Edge cases

- **Filler ("uh", "ee")** — maintain a small stoplist and skip silently.
- **Repeated words ("the the")** — collapse consecutive duplicates *after* matching, since the user might have intentionally repeated a phrase.
- **Paraphrase** — out of scope; show as a "miss" and rely on the user to recite the canonical text.
- **STT interim flicker** — never penalize a red until it has survived two consecutive interim frames; otherwise you will flash red on every recognizer revision.

---

## 4. Applicability to songs

Honest answer: **STT models are trained on speech, not song.** Sung melodies stretch vowels, flatten consonants, and ride on top of background music if the user records with anything playing. Across the board, sung audio produces WERs 3–5× worse than spoken audio in published benchmarks.

Per-style expectation:

- **Fast rap delivery (Ezhel, Ceza, Sagopa)** — modern models handle it surprisingly well *when delivered a cappella into a clean mic*. [Alibaba Qwen3-ASR-Flash](https://eu.36kr.com/en/p/3458910909699459) reports <8% WER on rap including English lyrics. Deepgram Nova-3 and Google `chirp_2` are not benchmarked specifically against Turkish rap, but their general-Turkish improvements transfer.
- **Sung melodies** — much worse. Vibrato and held notes confuse phoneme boundaries.
- **Ad-libs ("yeah", "ay", "skrt")** — will be transcribed as noise or hallucinated; treat them as fillers and skip.

**Product recommendation:** ship a "rhythm-recite" mode for songs. Show the lyric, let the user recite *in rhythm* but not *in pitch*, with the backing track muted. This is how rappers themselves practice memorization and it lets us reuse the prose pipeline unchanged. Singing-while-scoring should be an explicit v2 feature, not the default.

---

## 5. Recommendation per platform

### Web POC (ships today, free)

**Use the Web Speech API with `lang = "tr-TR"`, `interimResults = true`, `continuous = true`.** It is free, has good Turkish quality on Chrome and Safari, and gives us interim results. Accept that Firefox users are unsupported and that audio goes to Google/Apple. Auto-restart on `onend` to dodge the 60-second cap. Ship a one-line privacy disclosure.

### iOS native

**`SFSpeechRecognizer` with `requiresOnDeviceRecognition = true` when `supportsOnDeviceRecognition` is true for `tr_TR`, server fallback otherwise.** Free, no API key, partial results, and the 1000/hr device cap is irrelevant once on-device is active. Track the iOS 26 `SpeechAnalyzer` migration for a future rewrite.

### Android native

**`SpeechRecognizer` for the default path, Vosk (Turkish 50 MB model) as the offline fallback.** Google's recognizer is best-in-class but requires network and Google services. Vosk gives us a deterministic offline story and works on Huawei or any AOSP device without GMS. Ship both and let the device choose.

### Cross-platform (React Native / Flutter)

**React Native: `@react-native-voice/voice` (or `expo-speech-recognition` if you are in Expo).** Flutter: `speech_to_text`. Both pass through the OS layer, so the iOS and Android stories above hold. Plan one custom native module per platform for confidence scores or to substitute Vosk on Android.

### Backend fallback for a hobbyist (15 hr / month)

**Deepgram Nova-3 streaming.** At $0.0077/min that is **$6.93/month for 15 hours** — well within hobby budget, and the $200 starting credit covers ~26 months of that volume before any card is charged. Pick Google `chirp_2` instead if you already have GCP billing and want one less vendor; expect **$14.40/month** for the same volume.

For one-shot "score the whole recitation" replays (no streaming needed), **Groq Whisper Large v3 Turbo at $0.04/hr** is the cheapest hosted Whisper in the market: 15 hours is **$0.60/month**.

---

## 6. Pitfalls and constraints

- **Web Speech API is not really "in the browser."** Chrome streams audio to Google's servers; Safari to Apple's. Offline does not work.
- **Chrome's recognizer silently quits after ~60 seconds.** You get `onend` with no result. Auto-restart or chunk into 30 s windows.
- **iOS server-side limits.** 1000 reqs/hr/*device* (not per app) and 1 min audio per request. Use on-device whenever `supportsOnDeviceRecognition` is true for `tr_TR`; assume it is false on older iPhones.
- **Android's `SpeechRecognizer` requires Google Mobile Services.** Huawei / GrapheneOS / many CN ROMs do not have GMS. Vosk is the fallback.
- **Offline Whisper on a phone is slow.** `tiny` on an iPhone 13 is real-time-ish; `small` is not. On a Snapdragon 6-series with 4 GB even `tiny` lags. Reserve Whisper-on-device for desktop and Pro phones.
- **AWS Transcribe does not stream Turkish** — verified from their [language matrix](https://docs.aws.amazon.com/transcribe/latest/dg/supported-languages.html): `tr-TR` is `batch` only.
- **AssemblyAI streaming does not yet cover Turkish.** On their roadmap but not shipping as of May 2026.
- **STT does not transcribe singing well.** Lean on phonetic similarity heavily or pivot to rhythm-recite as the default mode.
- **Interim results flicker.** Require two consecutive frames of disagreement before showing red. Strip punctuation on both sides before word-diffing.
- **Privacy and consent.** Every cloud option (Web Speech API included) sends raw audio off-device. Disclose it. For minors, prefer on-device only.

---

## TL;DR

Web Speech API on web, `SFSpeechRecognizer` on iOS, `SpeechRecognizer` + Vosk on Android — all free. Deepgram Nova-3 (~$7/mo for 15 hr) as the streaming cloud fallback. Myers word-diff + Levenshtein + Turkish soundex, reusing the existing equivalence normalizer. Ship songs as rhythm-recite; singing-while-scoring is v2.
