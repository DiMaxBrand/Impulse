# Impulse — To-Do

## Avatar

- [x] **Avatar upload quality** — investigated. Current caps: 1280 px max dimension, 9 400 byte thumbnail limit, quality 90→50 auto-reducing. No user-facing setting. Raising to 2048 px / 15 000 bytes and adding an optional quality preference in Settings > Attachments is a future improvement; not blocking anything now.

## Branding

- [ ] README rebrand — remove upstream store links, update issue tracker URL, add roadmap section.

## Refactoring

- [ ] Merge `java-to-kotlin` branch into `dev` (277 Java → Kotlin conversions).

## General

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
