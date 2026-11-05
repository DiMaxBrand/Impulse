#!/bin/bash
# Portable, explicitly invoked setup helper. This is not an automatic Codex hook.
set -euo pipefail
PROJECT_DIR="${AGENT_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$PROJECT_DIR"
export SDK_ROOT="${SDK_ROOT:-${ANDROID_HOME:-$PROJECT_DIR/.local/android-sdk}}"
export ANDROID_HOME="$SDK_ROOT"
"$PROJECT_DIR/.agents/scripts/install-android-sdk.sh"
if [ ! -f local.properties ]; then
    printf 'sdk.dir=%s\n' "$SDK_ROOT" > local.properties
fi
bash ./gradlew compileConversationsFreeDebugKotlin --dry-run
