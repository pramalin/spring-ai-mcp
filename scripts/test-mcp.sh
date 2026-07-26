#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

compose up --build -d spring-ai-mcp-server
wait_for_health spring-ai-mcp-server
compose --profile test run --rm --build mcp-smoke-test
