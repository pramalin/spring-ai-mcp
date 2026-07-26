#!/usr/bin/env bash
set -euo pipefail

base_url="${SPRING_APP_URL:-http://localhost:8080}"

echo "Application information:"
curl --fail --silent --show-error "${base_url}/api/info"
echo
echo
echo "Workspace summary:"
curl --fail --silent --show-error "${base_url}/api/workspace/summary"
echo
