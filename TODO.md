# Impulse — To-Do

## Avatar

- [x] **Avatar upload quality** — investigated. Current caps: 1280 px max dimension, 9 400 byte thumbnail limit, quality 90→50 auto-reducing. No user-facing setting. Raising to 2048 px / 15 000 bytes and adding an optional quality preference in Settings > Attachments is a future improvement; not blocking anything now.

## Branding

- [ ] README rebrand — remove upstream store links, update issue tracker URL, add roadmap section.

## UI / Compose migration

- [ ] **Port the conversation (chat) screen to Jetpack Compose + Material 3 Expressive** — the individual chat screen with a contact or group (not the conversation list). Covers: message list, bubble rendering, reply card, input bar, attachment chooser, pinned-message banner. This unblocks native spring animations (e.g. Option E reply card morph) and M3 Expressive shape tokens.

## General

