#!/usr/bin/env bash
# Bring up one worktree's full local stack (db + fake GCS + api + web), per CLAUDE.md §7.
#
#   scripts/env-up.sh 123        # this worktree's story
#   scripts/env-up.sh develop    # the integration stack, from .worktrees/develop
#
# Never on the base ports (those are Radu's own dev servers / api/docker-compose.yml).
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

export DB_PORT GCS_PORT API_PORT WEB_PORT
docker compose \
    --project-directory "$REPO_ROOT" \
    -f "$REPO_ROOT/docker-compose.local.yml" \
    -p "$PROJECT_NAME" \
    up -d --build

cat <<EOF

$PROJECT_NAME is up:
  web  http://localhost:$WEB_PORT
  api  http://localhost:$API_PORT
  db   localhost:$DB_PORT
  gcs  http://localhost:$GCS_PORT

Seed a tenant:  scripts/seed-tenant.sh $1
Tear down:      scripts/env-down.sh $1
EOF
