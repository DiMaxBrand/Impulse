#!/bin/bash
set -euo pipefail

# Only run in remote (Claude Code on the web) environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# Ensure local.properties points to the Android SDK
if [ ! -f local.properties ]; then
  echo "sdk.dir=/opt/android-sdk" > local.properties
fi

# Download Gradle wrapper and resolve all Maven dependencies without compiling.
# This warms the Gradle/Maven cache so builds, linters, and tests start fast.
./gradlew dependencies --no-daemon -q
