#!/usr/bin/env sh
set -eu
port="${SPRING_APP_PORT:-8080}"
curl --fail --silent "http://localhost:${port}/api/info"
echo
curl --fail --silent "http://localhost:${port}/api/workspace/summary"
echo
