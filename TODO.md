# Impulse — To-Do

## Avatar

- [x] **Avatar upload quality** — investigated. Current caps: 1280 px max dimension, 9 400 byte thumbnail limit, quality 90→50 auto-reducing. No user-facing setting. Raising to 2048 px / 15 000 bytes and adding an optional quality preference in Settings > Attachments is a future improvement; not blocking anything now.

## Branding

- [ ] README rebrand — remove upstream store links, update issue tracker URL, add roadmap section.

## Video circles (round video messages) — plan only, not started

Camera icon in the top bar → `SharedTransitionLayout` expand into a
centered in-app circular recorder, rest of the screen blurred except
the bottom bar (which gets dedicated recording controls). Not the
system camera app.

- **Entry point**: needs a brand-new icon in the conversation top bar.
  There is no existing camera icon there today — `actions` currently
  has only `ic_call_24dp` (audio call) and `ic_videocam_24dp` (video
  *call*, WebRTC — unrelated feature, don't repurpose it). The only
  existing camera icon (`ic_camera_alt_24dp`) lives in the attach
  menu's "take photo" option and launches the *system* camera via
  `MediaStore.ACTION_IMAGE_CAPTURE` (`ConversationComposeFragment.
  onTakePhoto()`) — not reusable, since this feature needs in-app
  preview, not an external app hand-off.
- **Transition**: `SharedTransitionLayout` + `Modifier.sharedBounds`
  genuinely fits here (unlike the mic→recording-bar swap, which is a
  plain `AnimatedContent` case) — the icon *is* the same visual circle
  that grows from the top bar into the centered recorder, not a
  content swap between unrelated layouts.
- **Background blur**: `Modifier.blur()` (native `RenderEffect`,
  unconditionally available — minSdk 33 is well past the API 31
  requirement) on the message list + top bar while the recorder is
  expanded. Bottom bar is explicitly excluded from the blur and swaps
  to dedicated recording controls, the same pattern `RecordingBar`
  already uses to replace the composer during voice recording.
- **In-app camera preview + capture**: requires adding CameraX as a
  new dependency (`camera-core`, `camera-camera2`, `camera-lifecycle`,
  `camera-video`, `camera-view`) — not currently in the project. A
  `PreviewView` clipped to `CircleShape`, embedded via `AndroidView`
  (same pattern as the composer's `EditText`), with CameraX's
  `VideoCapture` use case doing the actual recording.
- **Transport**: reuse the existing video-attachment upload pipeline
  (HTTP upload), with a metadata flag marking it as a "circle" so
  supporting clients render it differently; non-supporting clients see
  a normal video attachment (graceful degradation) — same approach
  already used for XEP-0444 reactions/replies in this app.
- **Effort**: large — new dependency, new permission handling (camera
  + mic, on top of the existing mic-only voice recording), a new UI
  surface, and circular-video playback support in the message bubble.

## Refactoring

- [x] Merge `java-to-kotlin` branch into `dev` (277 Java → Kotlin conversions).
- [x] **Composer input: `BasicTextField` → native `EditText`** — fixed two related bugs: auto-space-after-period firing late/inconsistently across keyboard languages, and Samsung Keyboard's autocorrect-revert producing duplicated words. Root cause of both: Compose's plain-`String` `BasicTextField` infers edit positions from string diffing rather than tracking IME composing/selection state explicitly, which IME edit commands (autocorrect, revert-on-tap) don't always map onto cleanly. Replaced with an `AndroidView`-embedded `EditText` (`Editable`/`InputConnection` — the same mature widget WhatsApp/Signal/Telegram use for chat input) via a `TextWatcher` on the live `Editable`, scoped to the composer only (mic/attach buttons untouched). Needs live confirmation on an actual Samsung Keyboard device — not something verifiable from code alone.

## Developer options

Hidden behind a long-press on the version row in Settings > About.
Multi-finger taps (three, then two) and a custom 3-second hold were
all tried first but proved unreliable on the legacy Preference/ListView
row: the first finger's touchdown already arms the list's own click
detection before additional pointers register, and the list's own
long-press gesture detector cancels any custom-duration touch tracking
at the standard ~500ms threshold. Standard `setOnLongClickListener`
is the one gesture this widget is actually built to cooperate with.
Currently implemented: reset update-sheet pause timer. Backlog:

- [ ] **Update system**
  - [ ] Force a channel check right now, bypassing the weekly `UpdateCheckWorker` schedule
  - [ ] Simulate a paused/failed download state (fake `STATUS_PAUSED`/`STATUS_FAILED` + reason code) to preview status text without needing real bad network conditions
  - [ ] Read-only dump of all `UpdatePreferences` values (`activeDownloadId`, `downloadedApkPath`, `sheetDismissedUntil`, `pendingNoWifi`, etc.)
  - [ ] Force-run `ApkCleanupWorker` immediately instead of waiting for midnight
  - [ ] Show WorkManager's next-run time for `UpdateCheckWorker`/`ApkCleanupWorker` to confirm they're actually scheduled
- [ ] **Re-arm one-shot prompts** (currently only resettable via a full app-data wipe)
  - [ ] Reset the battery-optimization dialog flag
  - [ ] Reset the notification-permission request flag
  - [ ] Reset `hasInstalledUpdate` (the "first update" flag)
- [ ] **Crash/diagnostics**
  - [ ] Trigger a test crash, to confirm the crash-reporting pipeline reaches `support@on-chat.ru` end to end
  - [ ] One-tap "copy debug info" (version name/code, DB schema version, device model, Android version) for bug reports
- [ ] **Storage**
  - [ ] Cache/media size breakdown with a clear button per category (avatars, attachments, etc.)

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
