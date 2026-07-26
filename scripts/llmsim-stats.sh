#!/usr/bin/env sh
set -eu
port="${LLMSIM_PORT:-8089}"

printf '%s\n' '--- Dashboard summary ---'
curl --fail --silent "http://localhost:${port}/_llmsim/dashboard"
printf '\n\n%s\n' '--- Captured calls ---'
curl --fail --silent "http://localhost:${port}/_llmsim/calls"
echo
