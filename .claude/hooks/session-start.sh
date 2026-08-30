#!/bin/bash
set -euo pipefail

# Only run in remote (Claude Code on the web) environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Emit async marker immediately so the session starts while this runs in background
echo '{"async": true, "asyncTimeout": 300000}'

cd "$CLAUDE_PROJECT_DIR"

# Ensure local.properties points to the Android SDK
if [ ! -f local.properties ]; then
  echo "sdk.dir=/opt/android-sdk" > local.properties
fi

# Walk the full build task graph for the main variant without executing any tasks.
# This downloads the Gradle wrapper, all Gradle plugins (AGP, Kotlin, Spotless),
# Maven dependencies for all subprojects, and the Kotlin/dex compile toolchain —
# everything needed for builds, linters, and tests to start immediately.
./gradlew assembleConversationsFreeDebug --dry-run -q 2>/dev/null || true

# Resolve declared Maven dependencies explicitly as a fallback (covers any
# configurations the dry-run task graph doesn't touch).
./gradlew dependencies -q 2>/dev/null || true
