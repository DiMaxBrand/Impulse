#!/bin/bash
set -euo pipefail

TODO_FILE="${CLAUDE_PROJECT_DIR:-/home/user/Impulse}/TODO.md"
if [ -f "$TODO_FILE" ]; then
    printf '%s' "$(cat "$TODO_FILE")" | jq -Rs '{"systemMessage": ("Current TODO.md (preserve this context across compaction):\n\n" + .)}'
fi
