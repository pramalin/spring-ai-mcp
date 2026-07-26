#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

cleanup() {
  compose --profile sim stop llmsim >/dev/null 2>&1 || true
  compose --profile sim rm -f llmsim >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

cleanup
compose --profile sim up --build -d spring-ai-mcp-server llmsim
wait_for_health spring-ai-mcp-server
wait_for_health llmsim
compose --profile test run --rm --build agent-sim-test

echo
echo "LLMSim captured-call journal:"
curl --fail --silent "http://localhost:${LLMSIM_PORT:-8089}/_llmsim/calls" || true
echo
