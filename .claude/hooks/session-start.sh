#!/bin/bash
set -euo pipefail

# Only run in remote (Claude Code on the web) environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# Instant setup first (before the async marker) so it's in place immediately: local.properties
# and ANDROID_HOME point at /opt/android-sdk even while the SDK itself is still downloading.
if [ ! -f local.properties ]; then
  echo "sdk.dir=/opt/android-sdk" > local.properties
fi
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo 'export ANDROID_HOME=/opt/android-sdk' >> "$CLAUDE_ENV_FILE"
fi

# Emit async marker so the session starts while everything below runs in the background.
# Timeout is 15 min: a brand-new container downloads ~400 MB of SDK, then warms Gradle.
# Trade-off: a compile started before the install finishes fails with "SDK location not found"
# (or an SDK component missing) -- if that happens early in a session, just retry in a minute.
echo '{"async": true, "asyncTimeout": 900000}'

# spotless (build.gradle.kts: ratchetFrom("2.21.0")) resolves that git tag, and a fresh clone has
# none -- "No such reference '2.21.0'" fails spotlessCheck/Apply otherwise. Cheap and idempotent.
git fetch --tags --quiet origin >/dev/null 2>&1 || true

# Install the Android SDK if this container doesn't have one (instant no-op otherwise). Must
# finish before the Gradle warm-up below, which needs it.
"$CLAUDE_PROJECT_DIR/.claude/hooks/install-android-sdk.sh"

# Walk the full build task graph for the main variant without executing any tasks.
# This downloads the Gradle wrapper, all Gradle plugins (AGP, Kotlin, Spotless),
# Maven dependencies for all subprojects, and the Kotlin/dex compile toolchain —
# everything needed for builds, linters, and tests to start immediately.
./gradlew assembleConversationsFreeDebug --dry-run -q >/dev/null 2>&1 || true

# Resolve declared Maven dependencies explicitly as a fallback (covers any
# configurations the dry-run task graph doesn't touch).
./gradlew dependencies -q >/dev/null 2>&1 || true
