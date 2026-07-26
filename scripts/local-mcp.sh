#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

compose -f compose.yaml -f compose.local.yaml up --build -d spring-ai-mcp-server
wait_for_health spring-ai-mcp-server
compose -f compose.yaml -f compose.local.yaml --profile local run --rm --build goose-local-mcp
