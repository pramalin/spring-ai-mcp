#!/usr/bin/env bash
set -euo pipefail
exec docker compose --profile cli run --rm goose-mcp
