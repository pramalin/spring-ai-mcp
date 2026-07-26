#!/usr/bin/env bash
set -euo pipefail

required_commands=(docker curl)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: $command_name" >&2
    exit 1
  fi
done

echo "Docker: $(docker --version)"
echo "Compose: $(docker compose version)"

if docker model version >/dev/null 2>&1; then
  echo "Docker Model Runner: $(docker model version | head -n 1)"
else
  echo "Docker Model Runner is unavailable. Install/enable it before starting the primary Compose stack." >&2
  exit 1
fi

echo "Environment check completed."
