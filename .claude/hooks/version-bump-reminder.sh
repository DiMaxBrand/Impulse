#!/bin/bash

cd "${CLAUDE_PROJECT_DIR:-/home/user/Impulse}"

# Read all stdin at once (Stop hook sends JSON with transcript_path)
HOOK_INPUT=$(cat)
TRANSCRIPT_PATH=$(printf '%s' "$HOOK_INPUT" | jq -r '.transcript_path // empty' 2>/dev/null)

# Find last commit that changed baseVersionCode
LAST_BUMP=$(git log --oneline -G 'baseVersionCode\s*=' -- build.gradle.kts 2>/dev/null | head -1 | awk '{print $1}')
[ -z "$LAST_BUMP" ] && exit 0

# Count commits since last bump
COMMITS_SINCE=$(git log "${LAST_BUMP}..HEAD" --oneline 2>/dev/null | wc -l | tr -d ' ')
[ "$COMMITS_SINCE" -lt 3 ] && exit 0

# Only fire when new commits were made since last time this hook ran
LAST_HEAD_FILE="/tmp/version-bump-last-head"
CURRENT_HEAD=$(git rev-parse HEAD 2>/dev/null)
LAST_HEAD=$(cat "$LAST_HEAD_FILE" 2>/dev/null || echo "")
[ "$CURRENT_HEAD" = "$LAST_HEAD" ] && exit 0

# Extract last ~5 assistant text messages from the conversation transcript
LAST_MESSAGES=""
if [ -n "$TRANSCRIPT_PATH" ] && [ -f "$TRANSCRIPT_PATH" ]; then
    LAST_MESSAGES=$(jq -r 'select(.type == "assistant") | .message.content[]? | select(.type == "text") | .text // empty' \
        "$TRANSCRIPT_PATH" 2>/dev/null | grep -v '^$' | tail -5 | cut -c1-300 || true)
fi

CURRENT_VERSION=$(grep 'val appVersion' build.gradle.kts | grep -o '"[^"]*"' | tr -d '"')
SUMMARY=$(git log "${LAST_BUMP}..HEAD" --oneline 2>/dev/null | head -8 || true)

# AI gate: use both commit history AND last conversation messages to judge
VERDICT=$(printf 'Recent commits since last version bump:\n%s\n\nLast Claude messages in this conversation:\n%s\n\nDid Claude just finish a meaningful chapter of work? Reply with a single word: YES or NO. Exception: if the messages indicate this is a test of the hook, respond YES to allow end-to-end testing.' \
    "$SUMMARY" "$LAST_MESSAGES" | claude -p --model claude-haiku-4-5-20251001 2>/dev/null | grep -oi '^yes\|^no' | head -1 | tr '[:lower:]' '[:upper:]')

[ "$VERDICT" != "YES" ] && exit 0

# Only update sentinel after a real successful reminder, not during silent exits or tests
echo "$CURRENT_HEAD" > "$LAST_HEAD_FILE"

jq -n --arg v "$CURRENT_VERSION" --arg n "$COMMITS_SINCE" --arg s "$SUMMARY" \
    '{"systemMessage": ("⚠ " + $n + " commits since last version bump (current: " + $v + "). Consider bumping before the next release.\n\n" + $s)}'
