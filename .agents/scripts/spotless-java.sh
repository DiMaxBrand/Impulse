#!/bin/bash
set -euo pipefail

FILE=$(jq -r '.tool_input.file_path // empty' 2>/dev/null)
if [[ "$FILE" == *.java ]]; then
    cd "${CLAUDE_PROJECT_DIR:-/home/user/Impulse}"
    ./gradlew spotlessApply 2>/dev/null || true
fi
