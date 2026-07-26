#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

container_id=$(compose --profile sim ps -q llmsim 2>/dev/null | head -1 || true)
if [ -z "$container_id" ]; then
  echo "LLMSim is not running." >&2
  echo "Start ./scripts/llmsim-console.sh first." >&2
  exit 1
fi

wait_for_health spring-ai-mcp-server
wait_for_health llmsim
compose --profile test run --rm --build agent-sim-test

port="${LLMSIM_PORT:-8089}"
printf '\nInspect the captured calls at:\n  http://localhost:%s/_llmsim/console\n' "$port"
