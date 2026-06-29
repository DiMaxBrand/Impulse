#!/bin/bash

cd "${CLAUDE_PROJECT_DIR:-/home/user/Impulse}"

# Find last commit that changed baseVersionCode
LAST_BUMP=$(git log --oneline -G 'baseVersionCode\s*=' -- build.gradle.kts 2>/dev/null | head -1 | awk '{print $1}')
[ -z "$LAST_BUMP" ] && exit 0

# Count commits since last bump
COMMITS_SINCE=$(git log "${LAST_BUMP}..HEAD" --oneline 2>/dev/null | wc -l | tr -d ' ')
[ "$COMMITS_SINCE" -lt 3 ] && exit 0

# Only fire when new commits were made since the last time this hook ran
LAST_HEAD_FILE="/tmp/version-bump-last-head"
CURRENT_HEAD=$(git rev-parse HEAD 2>/dev/null)
LAST_HEAD=$(cat "$LAST_HEAD_FILE" 2>/dev/null || echo "")
echo "$CURRENT_HEAD" > "$LAST_HEAD_FILE"
[ "$CURRENT_HEAD" = "$LAST_HEAD" ] && exit 0

CURRENT_VERSION=$(grep 'val appVersion' build.gradle.kts | grep -o '"[^"]*"' | tr -d '"')
SUMMARY=$(git log "${LAST_BUMP}..HEAD" --oneline 2>/dev/null | head -8 || true)

# AI gate: skip if this looks like a mid-work pause rather than a chapter end
VERDICT=$(printf 'These are recent commits in an Android app since the last version bump:\n%s\n\nDoes this look like a completed chapter of work (feature or fix set) worth bumping the version for? Answer only YES or NO.' \
    "$SUMMARY" | claude -p --model claude-haiku-4-5-20251001 2>/dev/null | tr -d '[:space:]')

[ "$VERDICT" != "YES" ] && exit 0

jq -n --arg v "$CURRENT_VERSION" --arg n "$COMMITS_SINCE" --arg s "$SUMMARY" \
    '{"systemMessage": ("⚠ " + $n + " commits since last version bump (current: " + $v + "). Consider bumping before the next release.\n\n" + $s)}'
