#!/bin/bash
set -euo pipefail

PROJECT_DIR="${AGENT_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$PROJECT_DIR"
exec bash ./gradlew spotlessJavaApply "$@"
