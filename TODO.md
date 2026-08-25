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

- [x] Replace the current listening badge with a headphone icon — folded directly into the checkmark's own morph family in `MessageStatusIcon.kt` (new `LISTENING`/`LISTENED`/`LISTEN_UNKNOWN` `CheckmarkPhase` values, `voiceCheckmarkPhase()` override, headband+ear-cup stroke geometry at `CHECK_STROKE_WIDTH` — the headband drawn as a real curve through 3 points via `quadraticBezierTo`, not two straight segments meeting at a point, which read as a peaked roof mid-morph — for the in-flight morph only) rather than a separate icon shown alongside it — the double-check morphs on into the headphone in the same slot: dots → check → double-check → headphone. The settled/rest look at either end of any transition is always the real bundled asset — the checkmark's own drawable beforehand, `ic_headphones_24dp` once it arrives: the exact Google Material Symbols Rounded "headphones" (filled) path pasted verbatim from `fonts.gstatic.com` inside a `translateY` group (no hand-shifted coordinates), verified byte-for-byte against a fresh fetch — not the older, visually-similar-but-numerically-different outline glyph `ic_headphones_48dp` (a separate, untouched icon used elsewhere for the audio-file/media-browser icon). The hand-drawn stroke geometry only ever renders mid-morph. `ListenStatusManager.State` has five values — mapping per state, confirmed:
  - **`LISTENING`** (active listening): black headphone icon, animated/moving to read as "live" rather than static. Appears once someone starts listening to a message you sent — usually after its checkmark has already gone green (read).
  - **`LISTENED`**: icon turns green and bounces, matching the checkmark's own bounce-to-green animation (spatial spring for the bounce, effect spring for the color — see the checkmark's own `MessageStatusIcon.kt` for the pattern).
  - **`UNKNOWN`**: headphone icon appears, one-time scale-in-then-back with a bouncy spring (tune the damping ratio) — not a continuous/looping pulse — and turns red; color transition uses an effect spring (not a tween), same reasoning as `LISTENED`.
  - **`NOT_LISTENED`**: no headphone icon at all — falls back to the plain checkmark, same as any other message.
  - **`PAUSED`**: unchanged — keep the existing text label, no headphone icon treatment for this state.

## Bug-report tracking ID + fix notification — same category as headphones, needs stable to exist first

Only makes sense once stable releases (and the post-stable hotfix branching
rule in `CLAUDE.md`) are real — a report's fix might land as a hotfix on the
stable channel specifically, not through the reporter's next regular update,
so "did this ship yet" can't just mean "is there a newer version."

- [ ] Every report sent via `ExceptionHelper.checkForCrash()` /
  `reportCaughtException()` gets a short random tracking ID generated at
  send time (format: `BUG-XXXX`, e.g. `BUG-7F3K` — short enough to hand-type
  into a release description, distinctive enough not to false-match
  unrelated numbers already in release notes like version/RC numbers).
  - [ ] Report text includes the ID plus a line aimed at whoever reads it in
    the support chat (the dev, not the reporter): "Include this ID in the
    release description to notify the reporting user when it ships."
  - [ ] Report text also includes the reporter's current update channel
    (`UpdatePreferences.selectedChannel` or equivalent) — the fix needs to
    be targeted and verified against the right channel.
  - [ ] Both report dialogs (`crash_report_message` and
    `error_report_message`) get an added line telling the *reporter*:
    something like "If fixed, you'll be notified here — please don't switch
    update channels until then," since the fix ships to whichever channel
    they were on when they reported it.
  - [ ] App persists every pending ID locally — DB table vs. a
    `SharedPreferences`-backed class matching the existing
    `UpdatePreferences`/`OnboardingPreferences` pattern is an open
    implementation choice, not decided yet. Either way: store the ID,
    channel, and timestamp sent; drop it once matched (or after the
    long-pending cutoff below, whichever comes first) so this doesn't grow
    unbounded.
  - [ ] After each update check (already fetches release notes/changelog
    text), scan the new release's notes for any locally-stored ID. On a
    match, fire a notification: the reported issue was fixed in this
    release, and drop the ID from local storage.
  - [ ] Updates screen (`UpdatesScreen.kt`/`UpdatesActivity` — there's a lot
    of empty space below the "Check Now" button already) gets a card,
    visible only while at least one pending ID exists: "Waiting for a fix
    from the developer" + the ID(s). If a report has been pending longer
    than some reasonable cutoff (14 days? — pick a real number when this
    gets built) without a matching release, the card's wording shifts to
    suggest reaching out to support manually instead, in case the automatic
    match never fires (dev forgot to include the ID, or it's a rarer case
    that genuinely needs more attention than a routine hotfix).

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

## Known material3 library bugs to revisit

- [ ] **`material3` `ButtonGroup`'s `toggleableItem`/`clickableItem` render empty content** on our pinned `1.5.0-alpha21` (hit on the delete-message sheet's action row and the Developer Options > Feature flags picker; worked around both times with `customItem` + a hand-styled `Button`, see `DeleteMessageSheet` in `ConversationScreen.kt` and `FeatureFlagsActivity.kt`). Checked androidx release notes through `1.5.0-alpha26` (Aug 2026, the latest published at the time of checking) — no stable/beta of `1.5.0` exists yet, and no changelog entry confirms this specific bug fixed. `alpha22` promoted `ButtonGroup` to stable and dropped the old alpha22-deprecated experimental APIs (possibly including whatever `toggleableItem`/`clickableItem` looked like on `alpha21`); `alpha25` changed `ButtonGroupScope` to a `sealed interface` and reshaped `animateWidth`'s `compressionLimit` param (`PaddingValues` → `Dp`) — both would require rewriting our two call sites regardless. Revisit once `material3` `1.5.0` reaches stable/RC, not before — the API kept breaking release to release with no confirmation the actual bug we hit is gone.

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

- [ ] **In-app emoji picker — after the first stable release ships, not before.** Same idea as
  WhatsApp/Telegram/most native Android and iOS messaging apps: tapping a trigger dismisses the
  system keyboard and an in-app emoji grid takes over that same screen space instead — genuinely
  useful on keyboards with weak/no native emoji support (people still generally prefer their
  messaging app's own picker over the system IME's when the option exists). Emoji rendering
  itself is already handled (`androidx.emoji2`/`emoji2-emojipicker` are existing dependencies —
  worth checking whether `emoji2-emojipicker`'s own `EmojiPickerView` can be reused directly
  rather than building a picker grid from scratch). Placement brainstorm, not decided yet:
  - **Leading icon inside the text input pill** (WhatsApp/Telegram convention) — a smiley sits
    inside the rounded `Surface` before the `EditText` starts, rather than as another icon in the
    outer row. Most recognizable placement, keeps the composer's current 3-slot rhythm (attach /
    field / mic-send) visually intact since it doesn't add a 4th top-level icon.
  - **A dedicated icon in the outer composer row**, alongside attach and mic/send — simpler to
    wire up structurally (no change to the text-field `Surface`'s internal layout) but crowds an
    already-3-icon row, more of a problem on narrow screens.
  - Swap-on-tap with the existing attach button (long-press or a secondary state) — rejected as a
    starting idea, too undiscoverable for a first version.
- [ ] **Emergency mode, part 2** — `FeatureFlag.EMERGENCY_MODE` (see `XmppConnectionService.refreshEmergencyMode()`/`isEmergencyModeActive()`) currently only reuses `isDataSaverDisabled()` to skip new avatar fetches and disable media auto-download while any account is `SERVER_NOT_FOUND`/`CONNECTION_TIMEOUT`. Confirmed-but-not-yet-built for a second pass:
  - [ ] Limit/skip MAM catch-up history sync while active
  - [ ] Disable message carbons (XEP-0280) while active — less XML per message
  - [ ] Longer connection/read timeouts while active, so a slow-but-working link doesn't get treated as dead and retried repeatedly
  - [ ] Some visible indicator that it's active (a banner? — nothing currently surfaces this to the user beyond the behavior itself)
- [ ] **Container transform on the rest of Start Chat's + menu items** — "Invite" now grows into
  its destination via a shared-bounds transform inline in `StartConversationScreen.kt` (see
  `inviteExpanded` there). The user wants every item in that FAB menu doing the same, including
  Discover Channels (its own full-screen `ChannelDiscoveryActivity`, a real cross-Activity case —
  true Compose shared-element continuity doesn't cross Activity boundaries, so this one specifically
  either needs merging into `StartConversationActivity` too or a fallback like a clip-reveal
  `ActivityOptions` transition) and Add Contact/Join Public Channel/Create Public Channel/Create
  Private Group Chat (currently legacy `DialogFragment`s, not Compose — each needs individual
  conversion before it can participate in a shared transform). Scoped out of the Invite work
  itself since converting four more flows, one of them cross-Activity, is a much bigger job than
  one flag-gated screen.
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
