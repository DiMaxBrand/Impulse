# Impulse — Claude Code Notes

> **New session?** Read `TODO.md` first — it has the current backlog.

## Versioning

Uses **semantic versioning** (`MAJOR.MINOR.PATCH`).

- Version name and code live in `build.gradle.kts`:
  ```kotlin
  val baseVersionCode = 42195   // increment by 1 on every release
  versionName = "1.7.0+2.20.0" // SemVer; the +build part is used by automated systems — do NOT treat it as upstream tracking metadata or change it when bumping the version
  archivesName.set("com.dimax.impulse-1.7.0+2.20.0")
  ```
- For small fixes increment PATCH (3rd component): `1.7.1+2.20.0`, `1.7.2+2.20.0`, etc.
- Release versionCode = `100 * baseVersionCode + abiCode` (arm64-v8a = 4, universal = 0).
- Git tags follow `MAJOR.MINOR.PATCH` (e.g. `1.7.1`).
- Bump `baseVersionCode` by at least 1 for every release, more for significant jumps.
- **When to bump**: after completing a meaningful chapter of work (feature, set of fixes), before the user triggers a GitHub release. Analyze commits since the last tag — new user-facing features → MINOR, fixes/polish → PATCH, breaking changes → MAJOR. Do this proactively; don't wait to be asked.
- **Beta versioning**: while in beta (`-beta.N`), increment only the beta number (`beta.4` → `beta.5`). Do NOT jump the MINOR or PATCH component during beta — that only happens on final release.

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

```bash
./gradlew assembleConversationsFreeRelease   # signed release APK
./gradlew assembleConversationsFreeDebug     # debug APK
```

Output APKs land in `build/outputs/apk/conversationsFree/release/`.

AGP 9.2.1 has built-in Kotlin support — no separate Kotlin plugin needed.
