#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

docker version --format 'Docker Engine: {{.Server.Version}}'
docker compose version
compose config >/dev/null
echo "PASS: base MCP + LLMSim Compose configuration validates."
echo "LLMSim release requested: ${LLMSIM_VERSION:-0.10.1}"
echo "LLMSim console: http://localhost:${LLMSIM_PORT:-8089}/_llmsim/console"

if compose -f compose.yaml -f compose.local.yaml config >/dev/null 2>&1; then
  echo "PASS: optional Docker Model Runner overlay validates."
else
  echo "WARN: optional local-model overlay did not validate." >&2
  echo "      It requires Docker Compose 2.38+ and Docker Model Runner support." >&2
fi
