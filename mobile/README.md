# Memo — Mobile (Expo + React Native)

Native Android/iOS build of the `memo-poc` memorization app. Built with Expo SDK 53 and Expo Router. Source-of-truth content (`peri-beni.txt`, `hobbit.txt`) lives in the parent web repo and is re-baked into `src/data/samples.ts` by a PowerShell snippet (see below).

This is **separate from** the static `memo-poc` web app at the repo root — that one stays as the rapid prototype for trying out new concepts. Mobile reuses the same algorithms (tokenization, Turkish canonicalization, inhibition sampling, pagination).

## Quick start

```bash
cd mobile
npm install
npx expo start
```

Press `a` to launch the Android emulator, `i` for iOS Simulator (macOS only), or scan the QR code with the **Expo Go** app on a real device.

## Build an installable APK (Android)

You don't need Android Studio on your machine. EAS Build runs in the cloud.

1. Create a free Expo account at https://expo.dev (one-time).
2. From this `mobile/` folder:
   ```bash
   npx eas-cli login              # one-time
   npx eas-cli build:configure    # one-time, scaffolds project on Expo
   npm run build:android          # builds an APK (preview profile)
   ```
3. When the build finishes, Expo emails you (and prints) a download link to a `.apk` you can sideload onto your phone or upload as a GitHub release asset.

Free EAS tier currently allows 15 Android builds per month — enough for a hobby project.

## Build for the Play Store

```bash
npm run build:android-production   # produces an .aab (Android App Bundle)
```

Then upload the `.aab` via the Play Console.

## Build for iOS

```bash
npx eas-cli build --platform ios --profile production
```

Requires an Apple Developer Program account ($99/yr).

## Regenerate samples from the parent .txt files

Whenever `peri-beni.txt` or `hobbit.txt` change in the parent web project, regenerate `src/data/samples.ts`:

```powershell
$peri   = [IO.File]::ReadAllText("..\peri-beni.txt", [System.Text.Encoding]::UTF8).TrimEnd("`r","`n")
$hobbit = [IO.File]::ReadAllText("..\hobbit.txt",   [System.Text.Encoding]::UTF8).TrimEnd("`r","`n")
$periJson   = $peri   | ConvertTo-Json -Compress
$hobbitJson = $hobbit | ConvertTo-Json -Compress
$nl = [Environment]::NewLine
$out = @"
// AUTO-GENERATED from ../../peri-beni.txt + ../../hobbit.txt.
export type Sample = { id: string; title: string; body: string; };
export const PERI_BENI: string = $periJson;
export const HOBBIT:    string = $hobbitJson;
export const SAMPLE_TEXTS: Sample[] = [
  { id: 'peri-beni', title: "Peri Beni Nerelere Götürüyo'", body: PERI_BENI },
  { id: 'hobbit',    title: 'Hobbit (Resimli) — J. R. R. Tolkien', body: HOBBIT },
];
"@
[IO.File]::WriteAllText("src\data\samples.ts", $out, [System.Text.UTF8Encoding]::new($false))
```

## Modes shipped in v0.1

- **Home** — list of texts + per-text mode buttons + Add/Delete user texts
- **First letters** — every word reduced to its first letter; tap to reveal
- **Type it out** — type the text with Turkish-character-variant tolerance (`ı/İ/i/I`, `ö/o`, `ü/u`, `ş/s`, `ğ/g`, `ç/c`)
- **Bionic reading** — first 30/50/70 % of each word bolded; paginated for long texts

Roadmap (parity with the web POC, then beyond):

- Focus mode (4 lines centered)
- Spread blanking (% words hidden via per-line stratified inhibition sampling)
- RSVP speed read with ORP
- Speech-mode (recite aloud, STT scores)
- Audio recite (TTS shadowing)
- Spaced-repetition scheduling (FSRS)
- Cloud sync (optional, anonymous-first)

See parent repo's `docs/` folder for design docs (`features.md`, `mobile.md`, `speak-mode.md`, `srs.md`, etc.).
