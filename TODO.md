# Impulse — To-Do

## Avatar

- [x] **Avatar upload quality** — investigated. Current caps: 1280 px max dimension, 9 400 byte thumbnail limit, quality 90→50 auto-reducing. No user-facing setting. Raising to 2048 px / 15 000 bytes and adding an optional quality preference in Settings > Attachments is a future improvement; not blocking anything now.

## Branding

- [ ] README rebrand — remove upstream store links, update issue tracker URL, add roadmap section.

## Typography & UI refresh — future branch, not started

Transition the app's font to **Google Sans Flex**.

- [ ] **Developer Options font demo** — same spirit as the shape catalog: a screen to preview Google Sans Flex, switch between it and other options live, with a few presets and something interactive to play with the variable-font axes.
- [ ] **New Welcome Screen** — full Expressive rebuild using the new font, morphing shapes animating in the background.

## Headphones listening-status indicator — ship as its own stable-track RC, not a patch

Needs full testing given the scope, so bundle it into a dedicated RC once
we're going stable rather than slipping it into a patch release. Prep now:
download the **rounded** variant of the "headphones" Material Symbol.

- [ ] Replace the current listening badge with a headphone icon. `ListenStatusManager.State` has five values — mapping per state, confirmed:
  - **`LISTENING`** (active listening): black headphone icon, animated/moving to read as "live" rather than static. Appears once someone starts listening to a message you sent — usually after its checkmark has already gone green (read).
  - **`LISTENED`**: icon turns green and bounces, matching the checkmark's own bounce-to-green animation (spatial spring for the bounce, effect spring for the color — see the checkmark's own `MessageStatusIcon.kt` for the pattern).
  - **`UNKNOWN`**: headphone icon appears, one-time scale-in-then-back with a bouncy spring (tune the damping ratio) — not a continuous/looping pulse — and turns red; color transition uses an effect spring (not a tween), same reasoning as `LISTENED`.
  - **`NOT_LISTENED`**: no headphone icon at all — falls back to the plain checkmark, same as any other message.
  - **`PAUSED`**: unchanged — keep the existing text label, no headphone icon treatment for this state.

## Video circles (round video messages) — plan only, not started

`ic_videocam_24dp` (currently the video-*call* icon; visually it's
just a camera icon) reused as a new button inside `RecordingBar` —
the voice-recording bar — positioned between the pause and send
buttons. Tapping it switches from voice recording into an in-app,
`SharedTransitionLayout`-driven circular video recorder that expands
from that button's position, with the rest of the screen blurred
except the bottom bar (dedicated recording controls). Not the system
camera app.

- **Entry point**: a new icon added to `RecordingBar`, between the
  existing pause and send buttons — not a top-bar icon. Reuses the
  `ic_videocam_24dp` asset (currently used for the top bar's video
  *call* button, a separate WebRTC feature — don't touch that usage,
  just reuse the same drawable here). The existing "take photo" attach
  menu option launches the *system* camera via
  `MediaStore.ACTION_IMAGE_CAPTURE` (`ConversationComposeFragment.
  onTakePhoto()`) and isn't reusable — this feature needs in-app
  preview, not an external app hand-off.
- **Transition**: `SharedTransitionLayout` + `Modifier.sharedBounds`
  genuinely fits here (unlike the mic→recording-bar swap, or the
  mic→send icon morph — both plain `AnimatedContent` cases) — the
  button *is* the same visual circle that grows from its position in
  the recording bar into the centered recorder, a real position/size
  change between two different composables.
- **Background blur**: `Modifier.blur()` (native `RenderEffect`,
  unconditionally available — minSdk 33 is well past the API 31
  requirement) on the message list + top bar while the recorder is
  expanded. This is a general Compose graphics primitive, not a
  Material 3 Expressive API — Expressive only comes in for the
  transition's motion (spring specs), not the blur itself. Bottom bar
  is explicitly excluded from the blur and swaps to dedicated
  recording controls, the same pattern `RecordingBar` already uses to
  replace the composer during voice recording.
- **In-app camera preview + capture**: requires adding CameraX as a
  new dependency (`camera-core`, `camera-camera2`, `camera-lifecycle`,
  `camera-video`, `camera-view`) — not currently in the project, and
  the only reasonable choice for this (raw Camera2 is far more complex
  and CameraX exists specifically to abstract it away; the legacy
  `android.hardware.Camera` API has been deprecated since API 21). A
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
- [x] **Shape catalog screen** — browse the `MaterialShapes` set (`MaterialShapeHelpers.java` already exposes `circle`/`pill`/`semiCircle`/`diamond`/`gem`/`ghostish`/`softBurst`/`slanted`/`arrow`).
  - Big hero shape pinned at the top, ~1/3 of screen height; stays fixed, does not scroll away.
  - Below it, the rest of the shapes render as small selectable buttons (shape-as-icon). This catalog area scrolls **vertically only** — no horizontal scrolling — while the hero above it stays put; the activity itself doesn't scroll as a whole.
  - Tapping a catalog shape selects it with an expressive selection state.
  - The hero shape then **morphs** into the newly selected shape using `androidx.graphics.shapes.Morph` (the official shape-morph API), same mechanism already used for the morphing send button — reuse it, don't reimplement.

## General

- [ ] **Voice message transcription** — on-demand, on-device via ML Kit Speech Recognition (`com.google.mlkit:speech-recognition`). Tap a "transcribe" button on the audio bubble → POST the downloaded `.ogg`/`.m4a` to the on-device model → store result in a new `transcript TEXT` column on the message → display below the waveform. Model (~80 MB) is downloaded on demand, no API key needed. Same ML Kit family as subject segmentation already used for 3D avatars.
- [ ] **Remove cache** — add a "Clear cached files" action in Settings (or under Settings → Storage) that deletes downloaded/cached media from the app's private cache directory. "Automatically save to gallery" is now on by default, so cached copies are redundant once files are saved to shared storage.

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
| **Moderation delete** | ✅ Done | XEP-0425 IQ moderate; gated on public/anonymous channel + moderator rank + server msg ID; one-time disclaimer re-shown every 5 min |
| **Report & block** | [ ] | Received from stranger, server has spam reporting |
| **Open with** | [ ] | Geo URIs / audio files when OsmAnd is installed |
