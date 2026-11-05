#!/bin/bash
set -euo pipefail

PROJECT_DIR="${AGENT_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cat "$PROJECT_DIR/AGENTS.md" "$PROJECT_DIR/TODO.md"
