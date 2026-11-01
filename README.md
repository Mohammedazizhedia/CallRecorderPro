<!-- BFB.AI — Built For Benefits, by AI -->

# Call Recorder Pro

Bilingual (Arabic/English, full RTL) call recorder for Android — native Kotlin +
Jetpack Compose, targeting Samsung Galaxy S25 Ultra (Android 15 / One UI 7, arm64).

## Setup

1. Install JDK 17 and Android Studio (SDK 35).
2. **Fonts**: bundled in `app/src/main/res/font/` - real Cairo (with Arabic) +
   IBM Plex Sans Arabic fallback; system fonts cover anything missing. Replace Cairo with the full family from
   [fonts.google.com/specimen/Cairo](https://fonts.google.com/specimen/Cairo) if needed.
   Extra user-provided fonts (Basseet, Year of the Camel, IBM Plex Arabic) are in `fonts/`.
3. **Gradle wrapper**: this repo intentionally excludes the binary wrapper jar.
   Generate it once with a system Gradle 8.9+:
   `gradle wrapper --gradle-version 8.9` then commit the generated
   `gradle-wrapper.jar` (or let CI generate it — see workflow).
4. Build: `./gradlew assembleRelease`

## Permissions (requested step-by-step in onboarding)

| Permission | Why | Optional? |
|---|---|---|
| RECORD_AUDIO | Core recording | No |
| READ_PHONE_STATE | Detect incoming calls | Strongly recommended |
| POST_NOTIFICATIONS | Recording notification (Android 13+) | Recommended |
| READ_CONTACTS | Caller names, per-contact rules | Yes |
| READ_CALL_LOG | Direction labeling, outgoing detection | Yes |

Foreground-service type `microphone` is declared as required by Android 14+.

## Features

- Automatic recording on call state (all / selected / except / manual)
- AAC 48 kHz mono (128/192/256 kbps) or lossless WAV, real-time MediaCodec encoding
- Noise suppressor / echo canceler / AGC (auto-detected availability)
- Optional auto-speakerphone with volume restore; consent beep at start
- Source Test: 5 s per source with RMS + noise-floor comparison and best-pick
- Library: search (number/name/notes/transcript), filters, favorites, markers with notes, A-B loop, 0.5–2× speed, waveform, share/delete
- Samsung built-in recordings import via SAF with SHA-256 dedup + periodic scan
- Post-processing (WAV): loudness normalization + silence trimming (WorkManager)
- Auto-delete after N days (favorites exempt), storage usage display
- ZIP backup/export/import (cloud upload via system share sheet — no Drive SDK)
- App lock via BiometricPrompt; Quick Settings tile for one-tap manual recording

## Known limitations (read before reporting bugs)

1. **Android 10+ blocks the mic during most cellular calls.** Samsung's own
   recorder works because it's preinstalled with privileged access. Third-party
   apps typically capture **one side or silence**. Source Test helps you pick the
   best source but cannot bypass this platform restriction. Behavior varies by
   region/firmware — test with Source Test before relying on it.
2. **Android 14+ background FGS restrictions**: if the app is swiped away from
   recents, the system may refuse to start the mic foreground service from the
   PHONE_STATE broadcast. Keep the app in recents (exclude from "swipe to kill"
   in One UI) for reliable auto-recording.
3. WAV-only post-processing (normalization/trim). AAC is encoded in real time
   and is not re-processed.
4. Opus encoding and on-device Whisper transcription are intentionally omitted
   to keep the release APK small; the transcript column and full-text search are
   ready if you add a transcription backend later.
5. Outgoing-call detection relies on the call log (no privileged outgoing-call
   broadcast on modern Android), so labeling may lag by a few seconds.

## Building a signed release APK

Add to `~/.gradle/gradle.properties`:

```
RELEASE_STORE_FILE=/path/to/keystore.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Without these properties the release build falls back to the debug signature.

## CI

`.github/workflows/build.yml` builds a signed release APK on push. Provide
base64-encoded keystore + passwords as repo secrets
(`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`). If secrets are absent, the workflow falls back to a
debug-signed build so CI never fails on forks.

## License & responsibility

Call-recording laws differ by jurisdiction. The app shows a legal disclaimer on
first run and can play a consent beep at recording start. **You** are
responsible for complying with local laws.

## Changelog
- v1.0-v18: Crash trap (§10) · BFB branding + About (§16) · DESIGN.md (§2) · in-app manual recorder with instant error display
- v1.0-v16: themes · real Bold (IBM Plex) · selected-numbers UI · tile label fix · receiver/tile crash-guards
- v1.0-v15: diagnostic log
- v1.0-v10: AccessibilityService auto-record · Quick Settings tile
- v1.0-v7..v9: manual FAB · APK naming · icon fix
- v1.0-v5: compile fixes · debug signing fallback
- v1.0-v1: initial

## Security & Privacy
- All data stays on-device (internal storage). No analytics, no network calls.
- Backup ZIP is created only by explicit user action.
- Source: auditable at this repository.
