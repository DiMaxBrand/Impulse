#!/bin/bash
set -euo pipefail

# Only run in remote (Claude Code on the web) environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# Make sure the Android SDK exists BEFORE the async marker below, i.e. synchronously: a fresh
# container may not have one at all, and a compile started while it's still downloading would
# just fail with "SDK location not found". Instant no-op when it's already installed.
"$CLAUDE_PROJECT_DIR/.claude/hooks/install-android-sdk.sh"

# spotless (build.gradle.kts: ratchetFrom("2.21.0")) resolves that git tag, and a fresh clone has
# none -- "No such reference '2.21.0'" fails spotlessCheck/Apply otherwise. Cheap and idempotent.
git fetch --tags --quiet origin >/dev/null 2>&1 || true

# Ensure local.properties points to the Android SDK
if [ ! -f local.properties ]; then
  echo "sdk.dir=/opt/android-sdk" > local.properties
fi
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo 'export ANDROID_HOME=/opt/android-sdk' >> "$CLAUDE_ENV_FILE"
fi

# Emit async marker so the session starts while the Gradle warm-up below runs in background
echo '{"async": true, "asyncTimeout": 300000}'

# Walk the full build task graph for the main variant without executing any tasks.
# This downloads the Gradle wrapper, all Gradle plugins (AGP, Kotlin, Spotless),
# Maven dependencies for all subprojects, and the Kotlin/dex compile toolchain —
# everything needed for builds, linters, and tests to start immediately.
./gradlew assembleConversationsFreeDebug --dry-run -q >/dev/null 2>&1 || true

# Resolve declared Maven dependencies explicitly as a fallback (covers any
# configurations the dry-run task graph doesn't touch).
./gradlew dependencies -q >/dev/null 2>&1 || true
