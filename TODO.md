# Impulse — To-Do

## Branding

- [ ] README rebrand — remove upstream store links, update issue tracker URL, add roadmap section.

## Typography & UI refresh — future branch, not started

Transition the app's font to **Google Sans Flex**.

- [ ] **Developer Options font demo** — same spirit as the shape catalog: a screen to preview Google Sans Flex, switch between it and other options live, with a few presets and something interactive to play with the variable-font axes.
- [ ] **New Welcome Screen** — full Expressive rebuild using the new font, morphing shapes animating in the background.

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
Currently implemented: reset update-sheet pause timer; shape catalog
screen. Backlog:

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

## Progressive blur-up media loading — future, not a patch-sized fix

Telegram-style: show a pixelated/blurry placeholder instantly, stream the
full-res version in behind it, blur the low-quality intermediate render so
the pixelation itself never shows (blur, not just downscale). Applies both
directions: the in-app viewer should show the blurry thumbnail (or nothing,
if nothing's loaded yet) while downloading, and the full/blurry version
while uploading — same progress indicator either way, direction doesn't
matter to the visual. Open question of a ChatGPT-image-gen-style dynamic
reveal (sharp region growing in behind the blur) considered but not settled
on. Decided: **not gated to Emergency Mode** — apply everywhere, since
Emergency Mode is about reducing data use and blur-up costs nothing extra
(the bytes are already streaming in; this only changes how intermediate
frames render). Animation note: when progress arrives in big discontinuous
jumps (e.g. 0% → 50% in one tick, not a smooth stream) the blur/reveal
transition should ease via a bouncy spring rather than snapping instantly —
same spirit as the app's other spring-based status transitions (see
`MessageStatusIcon.kt`'s bounce-to-green), not a plain tween.

## General

- [ ] **Polls — graceful degradation for clients that haven't updated yet.** Not started; logged
  now because the question came up before any poll code exists. Confirmed: the mechanism already
  exists and needs no new infrastructure — XEP-0428 Fallback Indication, already used for OMEMO,
  PGP, and message retraction (`MessageGenerator.kt`'s `OMEMO_FALLBACK_MESSAGE`/
  `PGP_FALLBACK_MESSAGE`/`RETRACTION_FALLBACK_MESSAGE`, parsed back out in `MessageParser.java` via
  `Fallback.get(...)`). A poll message should ship a plain-text summary body ("X started a poll:
  '...' — update Impulse to vote.") alongside the structured poll extension, marked with the same
  `<fallback for="...">` element other clients (old Impulse or a different XMPP client entirely)
  already know to fall back to. No version negotiation needed — it's per-message, client-side, and
  works today for the three existing cases.
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
| **Retry decryption** | N/A | Dropped — PGP-only feature, not used in this app |
| **Retry P2P** | [ ] | Failed send, file not yet uploaded, peer online |
| **Report & block** | [ ] | Received from stranger, server has spam reporting |
| **Open with** | [ ] | Geo URIs / audio files when OsmAnd is installed |

## Message resend: timestamp handling — plan only, not started

Two existing resend paths already disagree on this, apparently by accident
rather than design:

- `XmppConnectionService.resendMessage()` (used by `sendUnsentMessages()`,
  the automatic retry-on-reconnect path) never touches the timestamp —
  preserves the original compose time.
- `XmppConnectionService.resendFailedMessages()` (the manual "tap to retry"
  action, both `ConversationFragment` and `ConversationComposeFragment`)
  explicitly does `message.setTime(System.currentTimeMillis())` then
  `conversation.sort()` — bumps the message to "now" and re-sorts it to
  wherever that lands, discarding when it was actually composed.

- [ ] **Plain failed → manual retry: keep the original timestamp, don't
  re-sort.** Matches what auto-retry-on-reconnect already does, and matches
  mainstream messenger conventions (WhatsApp/Signal/Telegram all keep a
  retried message in its original position) — the failed state itself
  already communicates "this didn't go out yet" without also needing to
  move it. Remove the `setTime`/`sort()` call from `resendFailedMessages()`
  for this case.
- [ ] **Cancelled (`ERROR_MESSAGE_CANCELLED`) → manual resend: reset to the
  current time.** Cancelling is a deliberate user act, not a transient
  failure — resending it later is closer to composing a new message than
  retrying an old one. Anti-abuse angle: without this, a message cancelled
  and resent hours later would otherwise sit at its original (now
  misleading) position. Needs the two cases distinguished in
  `resendFailedMessages()` by checking `message.getErrorMessage() ==
  Message.ERROR_MESSAGE_CANCELLED` rather than one blanket behavior for all
  of `STATUS_SEND_FAILED`.
- [ ] Open question, not resolved in discussion: whether there's a time
  cutoff even for the plain-failed case (e.g. cancelled-and-resent 1 hour
  later vs. 6 hours later treated differently) — needs a real decision
  before implementing, not just "probably."

## Automatic message resend — plan only, not started

Not all `STATUS_SEND_FAILED` causes are the same kind of failure — auto-retry
must not treat them uniformly. Sources found in the codebase:

- **Server-rejected stanza** (`XmppConnection.java`) — could be transient
  (`service-unavailable`, `remote-server-timeout`) or permanent
  (`item-not-found`, `forbidden`, `policy-violation`).
- **HTTP upload failed** (`HttpUploadConnection.java`) — network hiccup, or
  the endpoint genuinely rejecting the file.
- **File transfer failed** (`JingleFileTransferConnection.java`) — includes
  a distinct `ERROR_MESSAGE_CANCELLED` case: the user *deliberately*
  cancelled an upload. Auto-retrying this would re-send something the user
  explicitly stopped — not an edge case, actively wrong if not excluded.
- **OMEMO encryption failure** (`AxolotlService.java`) — usually a real
  trust/device-list problem, not something a plain retry fixes.
- **Carbon/receipt parsing errors** (`MessageParser.java`).

Discussed design, user's stated preference:
- [ ] Only retry causes that are actually transient — exclude
  `ERROR_MESSAGE_CANCELLED` and permanent policy/trust errors outright, per
  the list above.
- [ ] Retry cadence: **every 5 minutes while the account is online/connected**
  — not on a tighter loop, and not at all while offline (the existing
  reconnect-triggered `sendUnsentMessages()` path already covers "connection
  just came back," so this is specifically for "connection is up but this
  particular send keeps failing").
- [ ] No UI "retrying…" state — user explicitly doesn't think one is needed;
  the message should just look like a normal failed message until it either
  succeeds (see the scroll-button indicator below) or exhausts retries and
  reverts to today's plain manual-retry state.
- [ ] Needs a cap on attempts (not yet specified) so a permanently-failing
  message doesn't retry forever every 5 minutes.

## Scroll-to-bottom button: multifunctional redesign — plan only, not started

Current behavior (`ConversationScreen.kt`): a single down-arrow FAB, appears
once `listState.firstVisibleItemIndex > 2` (i.e. once the nearest 3 messages
have scrolled out of view), scrolls to the newest message on tap. Confirmed
correct via code + user's own on-device test.

Discussed direction: same button, same position, but its icon and tap
behavior change depending on what's happened off-screen while scrolled up —
"button" per the user's own preference (spoke "indicator" out loud via
speech-to-text but explicitly wants "button" in writing). All icons from
**Material Symbols Rounded**, matching the rest of the app.

- [ ] **New reaction off-screen** → icon becomes a heart outline (Material
  Symbols Rounded), replacing the plain down-arrow.
- [ ] **A message that failed to send, then got auto-resent successfully
  (see automatic-resend above), sitting off-screen** → icon becomes an
  exclamation mark in a circle (not the red/error-toned one used for the
  earlier permission-warning modal — same shape, but a neutral/attention
  tone, "nothing bad"). Tapping it does two things at once: scrolls to the
  resent message's position, **and** — via the same shared-bounds container
  transform already built for the notification-setup skip-warning modal
  (scrim + centered elevated card, `SharedTransitionLayout`) — expands into
  a centered card. Reuses that exact established component/pattern, not a
  new one.
  - [ ] Card wording: use the contact's actual display name, not a
    generic "they/it" placeholder — e.g. for a contact named Dima:
    something like "Dima's connection may have been unstable — one of
    their messages didn't send at first, but it went through now." Exact
    phrasing not finalized (workshopped live in conversation, no version
    the user was happy with yet) — needs a real pass when this gets built,
    not the placeholder draft above.
  - [ ] Open question: is this scenario about the *other* contact's
    message failing to send to them (delivery status) or about *my own*
    outgoing message failing and being auto-resent — the discussion used
    "Dima" in the example wording as if narrating the other person's
    connectivity, but the triggering condition (auto-resend, described
    just above) is specifically about outgoing messages *I* sent that
    failed. Needs clarifying before implementation — the wording and the
    mechanism as discussed don't obviously match.
- [ ] Interaction with the existing plain "new messages below, tap to
  scroll" behavior not yet specified — do these icon states take priority
  over the plain arrow, coexist, or need their own separate indicator.

## Double-tap quick reactions — plan only, not started

Deliberately different from WhatsApp/Telegram's silent double-tap-for-heart:
double-tapping a message should **ask** what reaction to apply — every time,
unless the user has explicitly opted out via "remember my decision." Never a
one-shot dismiss-and-forget.

- [ ] **Trigger**: double-tap on a message bubble (chat list gesture
  handling — needs a real double-tap detector, not just two quick single
  taps, to avoid colliding with existing tap-to-view/long-press-for-menu
  gestures already on the bubble).
- [ ] **The dialog/card** (expressive design required — matches the app's
  own stated standard that new/edited surfaces get Expressive treatment,
  not an exception here):
  - [ ] Just two emoji choices: heart and thumbs-up. Confirmed — not a
    wider set.
  - [ ] **Layout/ordering**: reuse the arrangement already used by the
    existing add-reaction dialog's default suggested-reactions row (the
    part visible before its "..." three-dot overflow) — user explicitly
    likes that existing ordering and wants it carried over, not redesigned.
    The existing dialog is `AddReactionDialog.java` (plain Java) launching
    `AddReactionActivity.kt` (legacy `DataBindingUtil`/XML layout,
    `R.layout.activity_add_reaction`) — **confirmed not yet ported to
    Compose**, so "port the existing dialog to Compose too" (raised in the
    same discussion) is itself real, unstarted work, not a prerequisite
    that's already done.
  - [ ] **Native shape-morphing** on the emoji elements themselves —
    confirmed via live check that the *current* add-reaction dialog does
    NOT already morph; this needs to be added fresh, using the same
    `Morph`/`RoundedPolygon` techniques already used elsewhere in the app
    (`AutoMorphingShape`, the Developer Options shape catalog, the chat
    list's presence-shaped avatar frame).
  - [ ] Long-pressing either emoji opens skin-tone variants — reuse the
    skin-tone-variant mechanism already built into `androidx.emoji2
    :emoji2-emojipicker`'s `EmojiPickerView` (confirmed: this is Google's
    own official Jetpack emoji picker, already a dependency, already used
    in `activity_add_reaction.xml` — not a third-party library), rather
    than reimplementing variant selection from scratch.
  - [ ] A single **"Remember"** checkbox underneath the emoji choices —
    **not** a separate "always ask" toggle; "always ask" is simply what
    happens when "Remember" is off. Defaults to **checked** in the
    dialog's own UI. Checked when a choice is made → that choice is
    remembered, future double-taps skip the dialog entirely and apply it
    directly. Unchecked → the dialog keeps asking every time, until the
    user deliberately re-opens it via Settings (below) and re-checks it.
- [ ] **Persistence**: a single "remembered choice" (emoji + skin tone,
  nullable/absent = no decision made yet, first double-tap should still
  show the dialog) plus the "Remember" boolean itself. Even once
  remembered, the user must be able to get back into the dialog
  deliberately (Settings entry below) to change the choice or flip back to
  always-ask — "remembered" must never mean "locked in with no way back."
- [ ] **Settings entry**: a new row under Settings → Interface (the natural
  home — no other existing section governs message-level behavior like
  this), labeled **"Quick Reactions"**, opening the *exact same* dialog
  used for the first double-tap — architecturally, the same fragment shown
  both ways, not a separate settings-only variant, so there's only one
  implementation of the picker UI to maintain. Lets the user change the
  emoji/skin-tone and toggle "Remember" back on or off.
