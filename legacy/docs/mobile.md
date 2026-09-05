# memo-poc: Native Mobile Path

Design doc for taking the existing static web app (vanilla HTML/CSS/JS, GitHub Pages) to iOS and Android stores. Verified against current 2026 SDK pricing and store rules. Single-developer project; the user already owns a Cloudflare domain.

## Path comparison

Effort estimates are **solo-dev calendar weeks** assuming part-time work (~15 hrs/week), starting from the current six working web modes.

### 1. PWA only (manifest + service worker)
- **Effort**: ~1 week. Add `manifest.json`, service worker, install prompt, iOS splash/icons.
- **Gain**: Android home-screen install, offline cache, Android push. Zero fees, zero review.
- **Lose**: **Not allowed in the App Store.** Guideline 4.2 ("Minimum Functionality") rejects "repackaged websites." iOS PWAs also lose: real push (web push on 16.4+ only when home-screened, caveats), background audio, share targets, native haptics (`navigator.vibrate` ignored on Safari), and STT. `content-visibility: auto` is still flaky on iOS Safari for the long Hobbit text.
- **Verdict**: Necessary first phase, insufficient endpoint.

### 2. Capacitor (wrap existing web app)
- **Effort**: ~2-3 weeks to first TestFlight + Play internal build. Existing HTML/CSS/JS runs unchanged inside `WKWebView` / `WebView`. Capacitor 7.6.2 is the latest stable (March 2026); 8 exists but 7 is the safe pick.
- **Works out of the box**: All six modes, pagination, reader options panel, `.md`/`.txt` upload (via `@capacitor/filesystem`).
- **Needs plugins**: `@capacitor/haptics`, `@capacitor/share`, `@capacitor/local-notifications`, `@capacitor/push-notifications`, `@capacitor-community/text-to-speech`, `@capgo/capacitor-native-audio`, `@capacitor-community/sqlite`, `@capacitor/speech-recognition` (see `speech.md`).
- **App Store risk**: Real but manageable. The 4.2 trap is solved by genuine native features (mic STT, TTS, haptics, background audio - this app needs all of them) plus native splash, status bar, and Info.plist descriptions. Six bespoke interaction modes + mic recitation puts it well past "repackaged website."
- **Bundle**: ~8-12 MB IPA, ~5-8 MB APK. Samples (~1.2 MB) bundled as assets.
- **Reuse**: ~95%. CSS variables, mode HTML files, the TR char-equivalence matcher, stratified inhibition sampler - all lift unchanged.

### 3. React Native (rewrite UI, keep logic)
- **Effort**: ~8-10 weeks. UI rewritten as RN components; deterministic logic (sampler, char-equivalence, ORP, bionic) ports as pure JS. Expo SDK + EAS Build free tier (15 iOS + 15 Android/mo, $0). SQLite via `op-sqlite` (JSI, active 2026) or `expo-sqlite`.
- **Gain**: Native rendering, no webview review risk, large JS hiring pool.
- **Lose**: All existing CSS (rewrite to `StyleSheet`), pagination (`content-visibility` has no RN equivalent - swap to `FlashList`), the reader-options live-preview model.
- **Reuse**: ~20% (logic only).
- **Popularity (2026)**: Stack Overflow 2024 - Flutter 46% vs RN 35% cross-platform; RN still #1 among *professional* devs (13.6% vs 12.6%).

### 4. Flutter
- **Effort**: ~10-12 weeks. Full Dart rewrite. Logic re-implemented (no JS reuse).
- **Gain**: Best raw rendering perf, Cupertino/Material widgets, excellent text layout primitives.
- **Lose**: Zero repo reuse. Second language to maintain forever.
- **Reuse**: ~0%. 2026 popularity: 46% share, 170k stars; some post-Google-restructure community noise from 2024-25.

### 5. Native iOS (SwiftUI) + Android (Compose)
- **Effort**: ~16-20 weeks. Two codebases, two languages, ~2x maintenance.
- **Verdict**: Wrong tool for a solo dev shipping a memorization app.

### Summary table

| Option        | Weeks to stores | Reuse | iOS store risk | Maint burden | Native API access |
|---------------|-----------------|-------|----------------|--------------|-------------------|
| PWA only      | 1               | 100%  | Not eligible   | Trivial      | Poor on iOS       |
| **Capacitor** | **2-3**         | **95%** | **Low w/ mitigations** | **Low** | **Full (via plugins)** |
| React Native  | 8-10            | 20%   | None           | Medium       | Full              |
| Flutter       | 10-12           | 0%    | None           | Medium       | Full              |
| Native x2     | 16-20           | 0%    | None           | High         | Full              |

## Recommended path: **Capacitor**

1. **~3x faster than React Native** (~3 weeks vs ~9), 95% reuse vs 20%.
2. **Apple 4.2 is solvable by features the app already needs.** Six bespoke modes + mic STT + native TTS clears the bar.
3. **Solo dev already owns the entire web codebase.** No new language, no new framework idioms.
4. **Free**. No EAS or build-SaaS dependency.

Fallback: if Apple rejects on 4.2 after mitigations, the logic modules are already JS - only the UI shell would migrate to RN.

## Architecture (Capacitor)

### Project structure

```
memo/
  www/                    # existing static site, lives at repo root today
    index.html, *.html    # one file per mode (unchanged)
    style.css, *.js
    samples.js, data.js
  ios/                    # generated by `npx cap add ios`
  android/                # generated by `npx cap add android`
  capacitor.config.ts     # bundle id, splash, plugins
  package.json            # capacitor deps only
  docs/
  .github/workflows/      # ci.yml - lint + cap sync + EAS-free fastlane
```

`www/` is the existing site, untouched. `npm run build` is a no-op copy; `cap sync` injects assets into the iOS/Android projects.

### State management

Continue with the existing vanilla-JS module pattern - no Redux, no Zustand. The app's state is already simple: `currentText`, `currentMode`, `readerPrefs`. Move from `sessionStorage` to `@capacitor/preferences` (key-value, native-backed, survives app kill) for prefs, and to SQLite for the library.

### Local storage

- **Library texts**: `@capacitor-community/sqlite`. Schema: `texts(id, title, body, source, created_at, last_opened_at, position)`. Active in 2026 (Capawesome Feb 2026 update).
- **Reader prefs**: `@capacitor/preferences`.
- **Per-text progress**: same SQLite, or a `progress` table once spaced-repetition lands.

PWA fallback uses OPFS + IndexedDB via the SQLite plugin's web shim - same query layer.

### Navigation

Each mode is its own HTML file - keep that. `@capacitor/app` handles back-button intercept. No SPA router. Shared top-bar with text title, mode switcher, options gear.

### How the 6 modes map

```
+-------------------------------------------+
| < Library     [Peri Beni v]    [Mode v] # |   <- shared top bar
+-------------------------------------------+
|                                           |
|  spread.html / focus.html / etc.          |
|  rendered as-is inside WKWebView          |
|                                           |
+-------------------------------------------+
|   spread | focus | 1st-let | typ | rsvp | bionic   <- bottom tab
+-------------------------------------------+
```

Modes are bottom-tab destinations. Each tab is the existing HTML file. Reader options gear opens a native sheet (`@capacitor/dialog` or a slide-up `<dialog>` styled to look native) which writes to Preferences; all modes re-read on focus.

### Reuse plan

All six mode HTML files, `reader.js`, `samples.js`, `data.js` lift unchanged. `style.css` lifts with added `env(safe-area-inset-*)` padding. All `sessionStorage` calls move behind a `storage.js` adapter pointing at Preferences + SQLite. Lifted-vs-rewritten: ~95% / ~5%.

### Font / theme system

Current CSS-variable approach (`--bg`, `--fg`, `--font-serif`, `--line-height`) ports directly. Add `@media (prefers-color-scheme: dark)` and `env(safe-area-inset-*)`.

**Cross-platform serif**: Iowan Old Style is iOS-only. Bundle **Source Serif 4** (OFL, Adobe, full Latin Extended-A including `ı İ ğ Ğ ş Ş ç Ç ö Ö ü Ü`, variable weight). Fallback to **Noto Serif** (OFL, full Unicode incl. Turkish). For the typing-mode input use **Inter** (full TR coverage).

```css
--font-serif: "Source Serif 4", "Iowan Old Style", Charter, "Noto Serif", Georgia, serif;
--font-sans:  Inter, -apple-system, Roboto, system-ui, sans-serif;
```

System fonts (`-apple-system` = SF Pro on iOS, Roboto on Android) cover Turkish natively - the bundled serif is the only extra weight.

## Native features beyond the web POC

- **Microphone for speech mode**: `@capacitor/speech-recognition` (native on-device STT - Apple Speech Framework on iOS, `SpeechRecognizer` on Android). Full design in `speech.md`.
- **Native TTS for audio recite**: `@capacitor-community/text-to-speech` wraps `AVSpeechSynthesizer` (iOS) and `TextToSpeech` (Android). Web fallback: `window.speechSynthesis`. Turkish voices exist on both platforms.
- **Push notifications** for spaced-repetition: `@capacitor/local-notifications` for scheduled reminders (no server needed initially); `@capacitor/push-notifications` (APNs / FCM) when cloud sync lands.
- **Haptic feedback** in typing mode: `@capacitor/haptics` - `impact({ style: ImpactStyle.Light })` on each correct keystroke, `ImpactStyle.Heavy` on a miss. Web fallback: `navigator.vibrate(10)` (Android Chrome only).
- **Background audio** during TTS recite: configure `UIBackgroundModes: audio` (iOS) + foreground service (Android via `@capgo/capacitor-native-audio`).
- **Share sheet to import text**: `@capacitor/share` and an iOS Share Extension target so users can send selected text from Safari/Books/Notes into memo. On Android, declare `intent-filter` for `text/plain` and `text/markdown`.

## Offline-first design

- **Library lives in SQLite** (`@capacitor-community/sqlite`). All reads/writes are local. No network required for any mode.
- **Sample texts ship in the bundle**: `www/samples.js` stays as today. On first launch the seed routine inserts Peri Beni and Hobbit rows into SQLite if absent. Total bundle weight from samples: ~1.2 MB - acceptable.
- **User uploads** (`.md` / `.txt`) go straight to SQLite after the existing Markdown/HTML strip step.
- **Conflict resolution** (when cloud sync is added later): last-write-wins keyed on `updated_at` (UTC ms). Acceptable for a single-user app. Store the user's library in CloudKit (iOS) and Drive App Folder (Android), or skip and use a single Cloudflare R2 bucket behind a Worker on the user's existing domain.

## Deployment

- **TestFlight**: free, included with Apple Developer Program. Up to 10k external testers per build, 90-day expiry.
- **Google Play Internal Testing**: free, included with Play Console account. Up to 100 testers, instant rollout.
- **CI/CD**: GitHub Actions for lint + `npx cap sync`. **Fastlane** for store uploads (free, OSS) - `fastlane match` for cert sync, `fastlane pilot` for TestFlight, `fastlane supply` for Play. EAS (Expo's cloud build) is unnecessary for Capacitor; macOS runners on GitHub Actions (`macos-14`) handle iOS builds. 2000 free minutes/month covers this volume.
- **Costs (verified May 2026)**:
  - Apple Developer Program: **$99/year** (Apple official; nonprofit/edu waivers exist but don't apply).
  - Google Play Console: **$25 one-time** (unchanged for 2026; pay once, lifetime).
  - GitHub Actions: free for public repos.
  - Fastlane: free.
  - Signing certs: free (Apple includes them; Android self-signs via keystore).
  - Total Year 1: **$124**. Year 2+: **$99/year**.
- **Time from "PWA today" to "in both stores"**: 5-6 weeks part-time for a solo dev (see migration plan).

## Migration plan

| Phase | Weeks | Output |
|-------|-------|--------|
| **1. PWA-ify** | Week 1 | `manifest.json`, service worker (Workbox), install prompt, iOS splash assets, offline cache for all six modes. Ship to Pages. Done. |
| **2. Capacitor scaffold** | Week 2 | `npm init`, `npx cap init memo poc.erkantaylan.memo --web-dir=.`, add iOS + Android projects, get Hello-Memo running on simulator + emulator. Wire `@capacitor/preferences`, swap all `sessionStorage` calls behind a `storage.js` adapter. |
| **3. Native plugins** | Week 3 | Add haptics to typing mode, share-receive for `.txt`/`.md`, native TTS for bionic+RSVP "play" button, local-notifications scaffold (no schedules yet), splash/icons/status-bar styling. |
| **4. SQLite migration** | Week 4 | `@capacitor-community/sqlite` schema + seed migration; move library off in-memory `data.js`. Persist last-opened text and per-text position. |
| **5. Polish + Turkish QA** | Week 5 | Source Serif 4 bundling, dark/sepia/light theme audit against safe-area insets, Turkish char keyboard test on real iOS+Android devices, accessibility pass (Dynamic Type / Android font scale). |
| **6. Store submission** | Week 6 | Apple Developer + Play Console enrollment ($99 + $25), Fastlane setup, App Store Connect metadata, Play Console store listing, privacy nutrition labels (`NSMicrophoneUsageDescription` even before speech mode), screenshots (6.7" + 6.1" + Android phone + 7" tablet), TestFlight internal build, Play internal track. |
| **7. Review + launch** | Week 7-8 | Apple review (5-7 days median in 2026), Play review (1-3 days). Handle any 4.2 callout by emphasizing the six interaction modes + native features in the review-notes field. Public release. |
| **Phase N+ (post-launch)** | ongoing | Speech mode (`speech.md`), spaced-repetition scheduler, cloud sync via Cloudflare Workers on user's domain, watchOS companion. |

Total realistic timeline: **7-8 calendar weeks part-time** from today to "live in both stores", $124 cash outlay.

---

Sources:
- [Apple Developer Program](https://developer.apple.com/programs/) - $99/yr verified
- [Get started with Play Console](https://support.google.com/googleplay/android-developer/answer/6112435) - $25 one-time verified
- [App Store Review Guidelines 4.2](https://developer.apple.com/app-store/review/guidelines/) - PWA / WebView rejection criteria
- [Publishing a PWA to App Store - Mobiloud 2026](https://www.mobiloud.com/blog/publishing-pwa-app-store)
- [Capacitor 7 docs](https://capacitorjs.com/docs/updating/7-0) - 7.6.2 March 2026
- [Capawesome Feb 2026 update](https://capawesome.io/blog/2026-february-update/) - SQLite plugin status
- [Expo EAS pricing](https://expo.dev/pricing) - Free tier 15+15 builds/mo
- [op-sqlite (OP-Engineering)](https://github.com/OP-Engineering/op-sqlite) - active 2026
- [react-native-tts](https://github.com/ak1394/react-native-tts) - maintained 2026
- [Source Serif 4 - Google Fonts](https://fonts.google.com/specimen/Source+Serif+4) - OFL, Turkish coverage
- [Stack Overflow 2024 Developer Survey via TechAhead](https://www.techaheadcorp.com/blog/flutter-vs-react-native-in-2026-the-ultimate-showdown-for-app-development-dominance/) - Flutter 46% vs RN 35%
