# Impulse — To-Do

## Avatar

- [x] **Avatar upload quality** — investigated. Current caps: 1280 px max dimension, 9 400 byte thumbnail limit, quality 90→50 auto-reducing. No user-facing setting. Raising to 2048 px / 15 000 bytes and adding an optional quality preference in Settings > Attachments is a future improvement; not blocking anything now.

## Branding

- [ ] README rebrand — remove upstream store links, update issue tracker URL, add roadmap section.

## Refactoring

- [ ] Merge `java-to-kotlin` branch into `dev` (277 Java → Kotlin conversions).

## General

- [ ] **Remove cache** — add a "Clear cached files" action in Settings (or under Settings → Storage) that deletes downloaded/cached media from the app's private cache directory. "Automatically save to gallery" is now on by default, so cached copies are redundant once files are saved to shared storage.

## Voice Message Transcription

- [ ] **On-device voice message transcription** using ML Kit Speech-to-Text (we already ship the ML Kit native runtime).
  - **Transcribe button** — shown on each received voice message bubble. On first use (before the user has ever tapped it), the button glows to draw attention.
  - **Onboarding bottom sheet** — the first time the user taps the button, show a bottom-sheet popup (same slide-up-to-center style as the delete-message sheet) with the text:
    - EN: "Try out voice message transcription. The feature is currently available with unlimited use."
    - RU: "Попробуйте транскрибацию голосовых сообщений. Функция сейчас доступна без ограничений."
  - After dismissing the sheet (or on subsequent taps), transcription runs immediately and the result is shown inline below the audio waveform inside the bubble.
  - Store a `transcribed_text` column in the messages table so the result persists across restarts.
  - Use a `pref_transcription_onboarded` SharedPreferences flag to control the glow and whether the onboarding sheet shows.
