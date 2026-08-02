# Impulse — To-Do

## Avatar

- [x] **Avatar upload quality** — investigated. Current caps: 1280 px max dimension, 9 400 byte thumbnail limit, quality 90→50 auto-reducing. No user-facing setting. Raising to 2048 px / 15 000 bytes and adding an optional quality preference in Settings > Attachments is a future improvement; not blocking anything now.

## Branding

- [ ] README rebrand — remove upstream store links, update issue tracker URL, add roadmap section.

## Refactoring

- [ ] Merge `java-to-kotlin` branch into `dev` (277 Java → Kotlin conversions).

## Developer Options

- [ ] **Shape catalog screen** — new button in Developer Options opens a screen for browsing the `MaterialShapes` set (`MaterialShapeHelpers.java` already exposes `circle`/`pill`/`semiCircle`/`diamond`/`gem`/`ghostish`/`softBurst`/`slanted`/`arrow`).
  - Big hero shape pinned at the top, ~1/3 of screen height; stays fixed, does not scroll away.
  - Below it, the rest of the shapes render as small selectable buttons (shape-as-icon). This catalog area scrolls **vertically only** — no horizontal scrolling — while the hero above it stays put; the activity itself doesn't scroll as a whole.
  - Tapping a catalog shape selects it with an expressive selection state.
  - The hero shape then **morphs** into the newly selected shape using `androidx.graphics.shapes.Morph` (the official shape-morph API), same mechanism already used for the morphing send button — reuse it, don't reimplement.

## General

- [ ] **Voice message transcription** — on-demand, on-device via ML Kit Speech Recognition (`com.google.mlkit:speech-recognition`). Tap a "transcribe" button on the audio bubble → POST the downloaded `.ogg`/`.m4a` to the on-device model → store result in a new `transcript TEXT` column on the message → display below the waveform. Model (~80 MB) is downloaded on demand, no API key needed. Same ML Kit family as subject segmentation already used for 3D avatars.

## reimagine-conversation-screen: context sheet backlog

Items missing from the long-press sheet vs the old XML screen.
Cherry-pick targets from `allow-deleting-messages` are noted.

| Item | Status | Notes |
|---|---|---|
| **Copy link** | ✅ Done | |
| **Copy URL** | ✅ Done | |
| **Share with** | ✅ Done | |
| **Save file** | ✅ Done | |
| **Cancel transmission** | ✅ Done | |
| **Pin / Unpin** | ✅ Done | Pinned banner refresh not yet wired on Compose screen |
| **Delete** | ✅ Done | XEP-0424 retraction (everyone) + local delete (myself); full infra cherry-picked from `allow-deleting-messages` |
| **Retry decryption** | N/A | Dropped — PGP-only feature, not used in this app |
| **Retry P2P** | [ ] | Failed send, file not yet uploaded, peer online |
| **Moderation delete** | [ ] | MUC moderator + server msg ID present |
| **Report & block** | [ ] | Received from stranger, server has spam reporting |
| **Open with** | [ ] | Geo URIs / audio files when OsmAnd is installed |
