#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

# Base stack cleanup does not require Docker Model Runner support.
compose --profile sim --profile test down --remove-orphans || true

# Also clean the optional local-model overlay when the installed Compose
# implementation supports the top-level models feature.
compose -f compose.yaml -f compose.local.yaml --profile local down --remove-orphans >/dev/null 2>&1 || true
