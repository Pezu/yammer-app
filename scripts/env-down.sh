#!/usr/bin/env bash
# Tear down the stack scripts/env-up.sh started for this worktree, including its volumes.
#
#   scripts/env-down.sh 123
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <issue-number|develop>" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
# shellcheck source=lib/ports.sh
source "$SCRIPT_DIR/lib/ports.sh"
compute_ports "$1"

docker compose \
    --project-directory "$REPO_ROOT" \
    -f "$REPO_ROOT/docker-compose.local.yml" \
    -p "$PROJECT_NAME" \
    down -v
