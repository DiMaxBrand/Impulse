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

| Item | Notes |
|---|---|
| **Copy link** | If body contains URLs — copy each distinct link |
| **Copy URL** | Remote download URL of a file attachment (≠ Copy link) |
| **Share with** | Files + eligible text messages via Android share sheet |
| **Retry P2P** | Failed send, file not yet uploaded, peer online |
| **Retry decryption** | `ENCRYPTION_DECRYPTION_FAILED` messages |
| **Download file** | File deleted locally but still on remote host |
| **Cancel transmission** | In-progress upload or download |
| **Save file** | File received, not yet in shared storage |
| **Delete file** | Cherry-pick from `allow-deleting-messages` (XEP-0424 retraction + local delete) |
| **Moderation delete** | MUC moderator + server msg ID present |
| **Report & block** | Received from stranger, server has spam reporting |
| **Pin / Unpin** | Cherry-pick from `allow-deleting-messages` (split hide vs unpin on banner, commit `8f212ad`) |
| **Open with** | Geo URIs / audio files when OsmAnd is installed |
