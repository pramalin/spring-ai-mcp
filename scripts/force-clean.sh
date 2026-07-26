#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

project_name=spring-ai-mcp
docker ps -aq --filter "label=com.docker.compose.project=${project_name}" | xargs -r docker rm -f
compose --profile sim --profile test down --remove-orphans || true
compose -f compose.yaml -f compose.local.yaml --profile local down --remove-orphans >/dev/null 2>&1 || true
