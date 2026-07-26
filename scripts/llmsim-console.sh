#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

compose --profile sim up --build -d spring-ai-mcp-server llmsim
wait_for_health spring-ai-mcp-server
wait_for_health llmsim

port="${LLMSIM_PORT:-8089}"
url="http://localhost:${port}/_llmsim/console"
printf '\nLLMSim console is ready:\n  %s\n\n' "$url"
printf 'Run ./scripts/test-sim-console.sh in another terminal to populate it.\n'
printf 'Run ./scripts/down.sh when finished.\n'
