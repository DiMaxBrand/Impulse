# Impulse — Claude Code Notes

## Versioning

Uses **semantic versioning** (`MAJOR.MINOR.PATCH`).

- Version name and code live in `build.gradle.kts`:
  ```kotlin
  val baseVersionCode = 42195   // increment by 1 on every release
  versionName = "1.7.0+2.20.0" // SemVer; the +build part is used by automated systems — do NOT treat it as upstream tracking metadata or change it when bumping the version
  archivesName.set("com.dimax.impulse-1.7.0+2.20.0")
  ```
- Release versionCode = `100 * baseVersionCode + abiCode` (arm64-v8a = 4, universal = 0).
- Git tags follow `MAJOR.MINOR.PATCH` (e.g. `2.21.0`).
- Bump `baseVersionCode` by at least 1 for every release, more for significant jumps.

## Branches

| Branch | Purpose |
|---|---|
| `dev` | Integration branch — all features merge here |
| `material-3-expressive` | Active development: Compose UI, avatar 3D effect, morphing shapes |
| `java-to-kotlin` | Incremental Java → Kotlin migration |
| `material-icons` | Material Icons Rounded icon set |
| `allow-sending-videos` | Future: custom Compose media picker |

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
