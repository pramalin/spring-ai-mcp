#!/usr/bin/env bash
set -euo pipefail
exec docker compose --profile cli run --rm goose-chat \
  run --no-session --text "Reply with exactly: chat works"
