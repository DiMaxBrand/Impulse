# Impulse — Claude Code Notes

## Versioning

Uses **semantic versioning** (`MAJOR.MINOR.PATCH`).

- Version name and code live in `build.gradle.kts`:
  ```kotlin
  val baseVersionCode = 42201   // increment by 1 on every release
  versionName = "1.9.1+2.20.0" // SemVer; the +build part is used by automated systems — do NOT treat it as upstream tracking metadata or change it when bumping the version
  archivesName.set("com.dimax.impulse-1.9.1+2.20.0")
  ```
- For small fixes increment PATCH (3rd component): `1.7.1+2.20.0`, `1.7.2+2.20.0`, etc.
- Release versionCode = `100 * baseVersionCode + abiCode` (arm64-v8a = 4, universal = 0).
- Git tags follow `MAJOR.MINOR.PATCH` (e.g. `1.7.1`).
- Bump `baseVersionCode` by at least 1 for every release, more for significant jumps.

## Branches

| Branch | Purpose |
|---|---|
| `dev` | Integration branch — all features merge here |
| `allow-deleting-messages` | Message deletion + XEP-0424 retraction (PR #13, in progress) |
| `fix-avatar-quality` | XEP-0461 replies + avatar quality (PR #12, in progress) |
| `telemetry` | Opt-out anonymous diagnostics (PR #11, needs rebase onto dev) |
| `material-3-expressive` | Active development: Compose UI, avatar 3D effect, morphing shapes |
| `reimagine-conversation-screen` | Created off dev; future Compose + M3 Expressive port of the chat screen |
| `material-icons` | Material Icons Rounded icon set |
| `allow-sending-videos` | Future: custom Compose media picker |

PR #9 (`java-to-kotlin`) was closed — that migration already landed via the
material-3-expressive merge. PRs #11/#12/#13 are drafts in active development;
do not merge until the user says so.

## Session handoff (2026-06-10)

Hard-won knowledge from the message-deletion work — do not rediscover:

- **XEP-0424 retraction IDs**: for 1:1 chats the retract `id` must be the
  sender's packet-id (`message.getUuid()`), NOT `serverMsgId`. The receiver
  stores it as `remoteMsgId` and looks up via
  `findMessageWithUuidOrRemoteId()` / `getMessageWithUuidOrRemoteId()`.
  MUC retractions keep using `serverMsgId`.
- **MessageParser gotcha**: outgoing retractions carry an XEP-0428
  `<fallback>` hint, which makes `bodyIsFallback=true` on the receiving side —
  so the Retract dispatch must stay OUTSIDE the
  `if (body != null && !bodyIsFallback)` block in `MessageParser.java`.
  1:1 retractions are handled by `ModerationManager.handleDirectRetraction()`.
- **Delete UX**: one context-menu item with upstream's dynamic title
  ("Delete audio"/"Delete image"/… via `delete_x_file` +
  `UIHelper.getFileDescriptionString()`; "Delete message" for text). It opens
  `DeleteMessageBottomSheet` (Everyone / Myself / Cancel,
  `MaterialButtonGroup.Connected`). "Myself" on a file message soft-deletes:
  file removed, row kept as the "File deleted" placeholder (`isDeleted()`,
  string `file_deleted`), still re-downloadable. Long-press on the placeholder
  shows "Delete leftover message" → same sheet in leftover mode (title +
  explanation, full row delete). "Everyone" always deletes the row entirely.
- **Settings migrations** live in `AppSettings.migratePreferences()` and use
  per-setting boolean flags (`pref_migrated_<key>`), not the version counter.
  `use_shared_storage` (auto-save to gallery) is now default-on + migrated on.
- **Do NOT remove ML Kit / avatar segmentation** (`libxeno_native.so`,
  ~20 MB) — it's a feature the user spent over a week on. Emoji font was
  unbundled instead (`DefaultEmojiCompatConfig`), 56 → 47 MB.
- **Still untested**: 1:1 retraction end-to-end with the latest build
  (ID fix + dispatch fix + fallback hint all in place).
- Old stashes `stash@{0}`/`stash@{1}` are stale (42183-era), superseded by
  committed code — ignorable.
- `TODO.md` is tracked on `allow-deleting-messages` (it is gitignored only on
  `material-3-expressive`); it holds the roadmap incl. voice-message
  transcription (ML Kit, glowing button, onboarding sheet with EN/RU strings).

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
