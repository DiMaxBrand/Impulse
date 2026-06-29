#!/bin/bash

SESSION_ID=$(jq -r '.session_id // empty' 2>/dev/null)
SENTINEL="/tmp/version-bump-reminded-${SESSION_ID}"

# Only remind once per session
[ -f "$SENTINEL" ] && exit 0

cd "${CLAUDE_PROJECT_DIR:-/home/user/Impulse}"

# Find last commit that actually changed baseVersionCode
LAST_BUMP=$(git log --oneline -G 'baseVersionCode\s*=' -- build.gradle.kts 2>/dev/null | head -1 | awk '{print $1}')
[ -z "$LAST_BUMP" ] && exit 0

# Count non-trivial commits since that bump
COMMITS_SINCE=$(git log "${LAST_BUMP}..HEAD" --oneline 2>/dev/null | wc -l | tr -d ' ')
[ "$COMMITS_SINCE" -lt 3 ] && exit 0

CURRENT_VERSION=$(grep 'val appVersion' build.gradle.kts | grep -o '"[^"]*"' | tr -d '"')
SUMMARY=$(git log "${LAST_BUMP}..HEAD" --oneline 2>/dev/null | head -8 || true)

touch "$SENTINEL"

jq -n --arg v "$CURRENT_VERSION" --arg n "$COMMITS_SINCE" --arg s "$SUMMARY" \
    '{"systemMessage": ("⚠ " + $n + " commits since last version bump (current: " + $v + "). Consider bumping before the next release.\n\n" + $s)}'
