#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

compose -f compose.yaml -f compose.local.yaml --profile local run --rm --build goose-local-chat
