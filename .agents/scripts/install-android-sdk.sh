#!/bin/bash
# Idempotent Android SDK installer for fresh remote containers. No-op (instant) when the SDK
# pieces this project needs are already present. Called by session-start.sh; can also be run by
# hand. SDK_ROOT can be overridden for testing.
set -euo pipefail

SDK_ROOT="${SDK_ROOT:-/opt/android-sdk}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

# compileSdk = 37 (platform android-37.0); android-36 is targetSdk; build-tools 36.0.0 matches
# what AGP 9.x resolves.
if [ -d "$SDK_ROOT/platforms/android-37.0" ] \
  && [ -d "$SDK_ROOT/platforms/android-36" ] \
  && [ -d "$SDK_ROOT/build-tools/36.0.0" ] \
  && [ -d "$SDK_ROOT/platform-tools" ]; then
  exit 0
fi

echo "Installing Android SDK into $SDK_ROOT ..." >&2

if [ ! -x "$SDKMANAGER" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsS --retry 3 --max-time 600 -o "$tmp/cmdtools.zip" "$CMDLINE_TOOLS_URL"
  unzip -q -o "$tmp/cmdtools.zip" -d "$tmp/extract"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/extract/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

# `yes` gets SIGPIPE when sdkmanager stops reading, which pipefail would otherwise treat as failure.
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true

"$SDKMANAGER" --sdk_root="$SDK_ROOT" \
  "platforms;android-37.0" \
  "platforms;android-36" \
  "build-tools;36.0.0" \
  "platform-tools" >/dev/null

echo "Android SDK ready at $SDK_ROOT" >&2
