# Impulse — Claude Code Notes

> **New session?** Read `TODO.md` first — it has the current backlog.

## Versioning

Uses **semantic versioning** (`MAJOR.MINOR.PATCH`).

- Version name and code live in `build.gradle.kts`:
  ```kotlin
  val baseVersionCode = 42195   // increment by 1 on every release
  versionName = "1.7.0+2.20.0" // SemVer; the +build part is used by automated systems — do NOT treat it as upstream tracking metadata or change it when bumping the version
  archivesName.set("Impulse")   // release APKs are named Impulse_arm64.apk, Impulse_universal.apk, etc.
  ```
- For small fixes increment PATCH (3rd component): `1.7.1+2.20.0`, `1.7.2+2.20.0`, etc.
- Release versionCode = `100 * baseVersionCode + abiCode` (arm64-v8a = 4, universal = 0).
- Git tags follow `MAJOR.MINOR.PATCH` (e.g. `1.7.1`).
- Bump `baseVersionCode` by at least 1 for every release, more for significant jumps.
- **When to bump**: after completing a meaningful chapter of work (feature, set of fixes), before the user triggers a GitHub release. Analyze commits since the last tag — new user-facing features → MINOR, fixes/polish → PATCH, breaking changes → MAJOR. Do this proactively; don't wait to be asked.
- **Beta versioning**: while in beta (`-beta.N`), increment only the beta number (`beta.4` → `beta.5`). Do NOT jump the MINOR or PATCH component during beta — that only happens on final release.

### Release notes (GitHub Release title/description)

- **Every release gets a real title**, not just stable — the in-app update sheet displays the GitHub release title verbatim as its hero text (see `releaseTitle` in `UpdateInfo`/`UpdatesScreen.kt`), so leaving RC/beta/alpha releases on the workflow's bare `Impulse <version>` default means RC-channel users never see what's actually in a build until they read the changelog separately. Write a short real title for every release, RC included.
- **Alpha/beta releases**: a description is optional — skip it unless there's something worth calling out. Title still applies.
- **Release candidates**: more important to include a description too, since RC is the last stop before stable.
- **RC → Stable promotion**: must be a pure version bump, no code changes bundled in. If something still needs fixing, ship it on another RC first, get it tested, and only promote to stable once verified — never mix code changes into the stable-promotion commit.
- **Stable release approval gate (standing policy)**: never trigger a stable release without the user's explicit sign-off first, every time, no exceptions for "we already discussed this" or being told to "just go for it" on a *previous* release — that authorization does not carry forward to the next one. Before triggering: (1) give a short opinion on whether publishing now is actually a good idea, with a brief why (anything still unverified? any RC fixes that haven't had real-world testing yet?); (2) give the *exact* title + description text that will be published, verbatim, not a summary of it; (3) wait for the user to actually agree to that text. Only after agreement does the stable release get triggered. If the user asks for changes to the text, revise and show the revised version — don't publish on a version they haven't seen.
- **Post-stable hotfix branching (dormant until the first stable release ships, then permanent standing policy — not a one-time thing to do and forget)**: create a branch pinned to each stable release (e.g. `stable-1.12.0`) the moment it ships, and never delete or advance it — a new one gets created at the *next* stable release, but every previous one stays frozen exactly where it was. This matters because development moves on to the next alpha/RC cycle immediately after a stable release, so "current HEAD" and "what stable users are actually running" diverge within days. If a stable-channel user reports a bug after that divergence, the fix must come from the frozen stable branch, not from whatever's currently in development — branch off it, apply *only* that specific fix, ship it as a patch on the stable channel. Stable users get the surgical fix and nothing else; none of the newer unstable work leaks in. Same spirit as the RC → Stable promotion rule above: a clean, isolated commit history, not a grab-bag.
- **Title format, every channel**: `Импульс MAJOR.MINOR.PATCH[-prerelease.N]: <short description>` — the app-name prefix is the Russian brand name **Импульс** (matching `app_name` in `values-ru/strings.xml`, which is what the update-sheet hero already falls back to for RU users when no title is set — a Latin "Impulse" prefix in front of a Russian sentence would be inconsistent with that), and the `<short description>` part is **in Russian**. The prefix is always present — same reasoning as before, the update-sheet hero shows this title verbatim with no other branding fallback once a title is set. E.g. `Импульс 1.12.0: Улучшены голосовые сообщения, ускорена синхронизация` or `Импульс 1.12.0-rc.45: Заметки об обновлении прямо в приложении`.
- **Stable release description**: summarize everything shipped since the **previous stable release** (not just the diff since the last RC). Exception: the **first-ever stable release** has no previous stable to diff against, so it collapses everything since the **first-ever alpha this app has cut** — the earliest alpha in git/release history, regardless of its version number.
- **Language**: this applies to the title (see above) as well as the description, on every channel, not just stable. The description's user-facing highlights section goes **Russian first, then English** — both are written for regular (non-developer) users, since most people opening a release land on it via Russian. Only Russian and English — see "Localization" below. If technical detail is worth including for developers, put it in a separate **English-only "Developer notes"** section after the two user-facing ones.

## Branches

| Branch | Purpose |
|---|---|
| `dev` | Integration branch — all features merge here |
| `reimagine-conversation-screen` | Active: Compose chat screen (see handoff below) |
| `allow-deleting-messages` | XEP-0424 retraction, local delete, pin/unpin rework — cherry-pick source |
| `material-3-expressive` | Compose UI, avatar 3D effect, morphing shapes |
| `java-to-kotlin` | Incremental Java → Kotlin migration |
| `material-icons` | Material Icons Rounded icon set |
| `allow-sending-videos` | Future: custom Compose media picker |

## Session handoff: reimagine-conversation-screen (2026-06-15)

- The chat screen is now `ConversationComposeFragment` + `ConversationScreen.kt`
  (Compose, `MaterialExpressiveTheme`, dynamic colors). `ConversationsActivity`
  hosts it in both main and secondary (tablet) containers; the old
  `ConversationFragment` is still in the tree but no longer instantiated.
- Entity accessors (`getUuid()`, `getMode()`, `getAccount()`, …) are Kotlin
  functions, NOT properties — property syntax does not compile.
- Visual decisions (user-approved): Expressive grouped bubbles 20dp/5dp, no
  tails; outgoing = primaryContainer, incoming = surfaceContainerHigh; morphing
  send button; newest-message spring pop; date pills; scroll-to-bottom FAB with
  unread badge; typing bubble + typing subtitle in the top bar.
- All `fix-avatar-quality` commits are now on `auto-updater` (avatar quality
  caps, 1440 px chat list loading, vCard/PEP hash guard, DB v58 avatar_vcard
  migration, DefaultEmojiCompatConfig, XEP-0461 replies). DB is at v61; all
  migration guards through v61 are in place.
- Ported into Compose: reply cards (tap scrolls + highlights), reply banner,
  message correction (edit banner + edited icon, `getLastEditableMessage()`
  rule), Expressive grouped-list context sheet (reply/copy/edit/open/download),
  voice recording (mic button → `RecordingActivity` → attach), XEP-0444
  reaction chips (below bubbles), `/me` command, large emoji, MUC nick
  highlight, private message banner.
- Not yet ported from the old fragment: PGP send, camera capture, location
  sharing, in-bubble audio player. (MUC private messages ARE ported:
  `privateMessageWith()` + EXTRA_NICK handling + tertiary PM banner.)
- `Message.replaceUuid()` exists because Kotlin cannot resolve `setUuid()`
  (collides with the protected `uuid` property of `AbstractEntity.kt`).

### Message text rendering — CRITICAL

**Do NOT use `AndroidView`/`TextView` for message body text.** `LazyColumn`
recycles composition slots; an `AndroidView`-hosted `TextView` retains stale
content from its previous occupant — blank messages on scroll, every time.

The correct approach (currently in place): `buildAnnotatedBody()` builds a
`SpannableStringBuilder` via the same pipeline as the old screen (StylingHelper,
`de.gultsch.common.Linkify`, emoji sizing, `/me`, nick highlight), then
`spannableToAnnotated()` converts every span to `SpanStyle`/`LinkAnnotation`
and renders with `BasicText`. `remember(uuid, revision)` caches per message.
XMPP URIs get `tertiaryContainer` chip styling; regular links get primary +
underline. URL click routing replicates `FixedURLSpan` logic (xmpp → in-app,
geo → `ShowLocationActivity`, web+ap → handler or HTTPS fallback).

**Quoting is intentionally not ported** — XEP-0461 replies cover that use case.

### Long-press / context sheet

- The full row width (`MessageRow` outer `Column`, `fillMaxWidth()`) is a
  no-ripple `combinedClickable` for long-press — users can long-press anywhere,
  not just on the bubble.
- Image thumbnails use `combinedClickable` so long-press opens the sheet;
  short tap still opens the viewer.
- Context sheet (`MessageContextSheet`) currently implements: reply, copy text,
  correct, open file, download file, add reaction. See TODO.md for the backlog.

## Session handoff: predictive back custom transitions (2026-08-30)

Current version: `1.15.0-beta.2` (baseVersionCode 42465), confirmed green/published.
Stable is at `1.14.1`. Nothing pending in CI.

- **beta.1** migrated the 5 legacy View-based screens that had opted out of
  predictive back (`android:enableOnBackInvokedCallback="false"` in the manifest)
  because they overrode the deprecated `onBackPressed()`: `ScanQrCodeActivity`,
  `EditAccountActivity`, `ConferenceDetailsActivity`, `RtpSessionActivity`,
  `ContactDetailsActivity`. Each now registers an `androidx.activity.OnBackPressedCallback`
  via `getOnBackPressedDispatcher().addCallback(...)` in `onCreate()` instead, and the
  manifest opt-outs are gone — every Activity now inherits the app-level
  `enableOnBackInvokedCallback="true"`. The Compose screens (`ConversationScreen`,
  `MediaViewerActivity`, `StartConversationScreen`, `ConversationComposeFragment`)
  already used Compose's `BackHandler` and needed no change.
- **beta.2** fixed an unrelated bug found while testing beta.1 on device: `UpdateChecker`
  silently reported "Up to date" when the check itself failed (no internet, timeout,
  GitHub erroring). New `CheckResult.CheckFailed`/`CheckStatus.CHECK_FAILED` with its own
  status text, EN+RU. Unrelated to predictive back, just landed in the same beta cycle.
- **What's NOT done yet — this is the actual next task**: none of the 5 migrated screens
  (or the Compose ones) have a *custom* predictive-back transition. Every screen currently
  gets whatever the system draws by default — which on the user's own device is nothing
  visible at all, since the OS-level predictive-back visual is gated behind a hidden,
  inconsistent per-device/OEM toggle (Settings → System → Developer options → "Predictive
  back animations"), separate from the app-level opt-in. Confirmed via direct testing.
- **Researched two real open-source apps to see how they get a reliable, custom
  transition regardless of that toggle** (both confirmed via their actual public source,
  not guessed):
  - [Econ01/HydroTracker](https://github.com/Econ01/HydroTracker) migrated to Jetpack
    **Navigation3** (`androidx.navigation3`) and uses `NavDisplay`'s
    `predictivePopTransitionSpec = { swipeEdge -> ... }`, which hands you the live swipe
    edge (`NavigationEvent.EDGE_LEFT`/`EDGE_RIGHT`) and lets you build a fully custom
    `ContentTransform` (scale + slide + `TransformOrigin` anchored to the swiped edge) —
    real per-frame gesture tracking via `androidx.navigationevent`.
  - [1372Slash/Zenith](https://github.com/1372Slash/Zenith) stays on plain
    `androidx.navigation.compose.NavHost`, just pinned to a recent enough version
    (`navigation-compose 2.8.9`) — since ~2.8.0, `NavHost` automatically scrubs its
    existing `popEnterTransition`/`popExitTransition` through live predictive-back gesture
    progress, no custom gesture code needed at all.
  - **Neither technique transfers directly**: Impulse doesn't use `NavHost`/Navigation
    Compose anywhere (confirmed via repo grep) — navigation is legacy Fragment/Activity
    based for cross-screen nav (`ConversationsActivity` hosting fragments, separate
    Activities for the viewer/start-conversation/settings/etc.), with each Compose screen
    self-contained rather than nodes in one shared nav graph.
- **Explicitly decided against migrating the whole app to Navigation3/NavHost "from
  scratch"** to get this, after discussing tradeoffs with the user — they agreed. ~30+
  Activities, several with real platform integration unrelated to navigation (deep links
  for `xmpp:`/`https:`/`imto:` schemes, notification `PendingIntent`s, app shortcuts, the
  tablet dual-pane layout, PiP for calls, QR scanning, image cropping) — a full rewrite is
  realistically months of regression-prone work for a purely cosmetic payoff, and doesn't
  match this codebase's established incremental-migration style (see Java → Kotlin
  migration notes below).
- **Agreed plan instead, not yet started**: build custom transitions *per screen*,
  incrementally, using Compose's own `PredictiveBackHandler` composable directly
  (exposes live gesture progress without any navigation library) — no `NavHost`, no
  Navigation3 migration needed. Proposed starting point: the conversation list ↔ chat
  transition.
- **Release sequencing decision**: hold off on promoting to stable until the custom
  transitions actually land and are device-tested — beta.1/beta.2 alone are low-visibility
  (5 secondary screens, an error-message wording fix). Land the custom-transition work as
  further betas in the *same* `1.15.0` cycle, then cut one coherent stable release for the
  whole predictive-back story, rather than a barely-noticeable stable bump now plus another
  one later.
- **Deferred, explicitly not bundled into this work**: the "Bug-report tracking ID + fix
  notification" TODO.md item (unblocked now that stable releases are real, but unrelated
  to predictive back — full spec already in TODO.md, do it as its own dedicated session).
- **Confirmed final decision, not just tentative**: do not cut a stable release for
  `1.15.0-beta.2`. Keep building on this same minor version instead (see the notification
  sound / font relaunch handoff below, which is what's actually happening next — this
  predictive-back thread is parked, not the active one).

## Session handoff: notification sound fixes + Google Sans Flex relaunch (2026-08-30)

A message from the session that wrote this: I don't have the conversation that produced
the work below — it happened in a session before this one, and wasn't summarized into my
context. Everything here is reconstructed from `git log`/`git diff` on the branch, not
first-hand memory. Treat the branch's own commits as the primary source of truth, not this
note. If you need to ask me something about the codebase, just give the prompt to me — but
don't forget about chatting with Dima too, he'll be bored otherwise. He's copy-pasting
between this session and the other one by hand.

- **Target branch: `notification-setup-screen`** (remote, unmerged into `listening_status`).
  It forked at `d5c141418` ("Isolate update APKs in their own subfolder") — a long way back,
  before the Compose chat-screen rewrite, the whole grouped-photo pipeline, save/download
  cards, and the predictive-back work above. Expect real rebase/merge conflicts; don't
  assume a clean fast-forward.
- **What's on it**, per its commit history:
  - Notification channel sound workarounds for OEMs that strip channel sounds — HyperOS/Xiaomi
    detection (`ro.mi.os.version.name`), ringtone-picker wiring for both message and call
    channels, a fix treating `Uri.EMPTY` as "no sound," a missed-calls-channel sound fix.
  - An adaptive "notification setup screen" shown on first launch and re-triggered by a
    version-number bump, with current-ringtone-name display on its cards.
  - Google Sans Flex variable-font integration — bundled locally first, then switched to the
    official `fonts.gstatic.com` build, used on the welcome-screen title. This is the same
    font work TODO.md's "Typography & UI refresh" section describes (Developer Options font
    demo, new Welcome Screen) — that TODO entry and this branch are the same thread.
  - Version on that branch was last bumped to `1.12.0-alpha.1` — long stale, ignore it;
    re-derive the real next version from wherever `listening_status` actually is once this
    lands, per the normal versioning rules above.
- **Don't confuse with**: `claude/per-contact-notification-sounds-g05xp7`, a separate,
  more recent branch — per-contact notification sound *selection*, a different feature from
  the OEM-channel-sound-stripping workaround above.
- **Versioning**: user explicitly confirmed this ships as a **MINOR** bump when ready — a
  big visible relaunch (new font, fixed notification sounds, refreshed welcome screen), not
  a real breaking change, despite "major" being used loosely in conversation at first.
- Not blocked on, and not blocking, the predictive-back thread above — parallel, unrelated
  work. `listening_status` currently sits at `1.15.0-beta.2` with no stable cut planned for
  that beta; this thread continues on the same minor version rather than starting a new one.

## Localization

- Only **Russian and English** are actively maintained (`values/strings.xml`
  and `values-ru/strings.xml`). Do not add or auto-translate into any other
  locale unless explicitly asked — the user can't verify translations they
  don't speak, and stale/wrong translations are worse than a missing one.
  This applies to `strings.xml` as well as GitHub release notes.

## Signing

Signing credentials are in `signing.properties` (not committed). The build reads this file automatically — no manual keystore entry needed in Android Studio.

```
keystore=<absolute path to .jks>
keystore.password=<password>
keystore.alias=key0
```

`build.gradle.kts` loads `signing.properties` and wires it into the `signingConfigs` block, so `assembleRelease` (or Shift+F10 in Android Studio) signs automatically.

## Java → Kotlin migration

Converted files so far (29 total):
- All single-method interfaces → `fun interface`
- Simple exception classes → `class Foo : Exception`
- Utility singletons → `object` with `@JvmField` / `@JvmStatic`
- Multi-method interfaces → regular `interface`
- Abstract base classes → `abstract class`

Pattern: write `.kt`, delete `.java`, keep the same package.

## Build

**Local verification is compile-only — never run a full assemble locally.**

```bash
./gradlew compileConversationsFreeDebugKotlin   # local check: confirms it compiles, nothing else
```

Actual APK builds (`assembleConversationsFreeRelease`/`assembleConversationsFreeDebug`,
signing included) only happen via the GitHub Actions release workflow
(`.github/workflows/release.yml`), triggered explicitly. Do not run
`./gradlew assemble*` locally — that's the CI/CD step, not a local one, even
to "double-check" something the compile step already covers. If something
needs verifying beyond compilation, trigger the workflow and check its result
instead of building locally.

Output APKs from the CI build land in `build/outputs/apk/conversationsFree/release/`
inside that workflow's run.

AGP 9.2.1 has built-in Kotlin support — no separate Kotlin plugin needed.
