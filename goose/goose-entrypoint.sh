#!/usr/bin/env sh
set -eu

# Docker Model Runner compatibility remains available when MODEL_RUNNER_URL is
# provided, but LLMSim supplies OPENAI_HOST and OPENAI_BASE_PATH directly.
if [ -n "${MODEL_RUNNER_URL:-}" ]; then
  endpoint="${MODEL_RUNNER_URL%/}"
  origin=$(printf '%s' "$endpoint" | sed -E 's#^(https?://[^/]+).*$#\1#')
  path=${endpoint#"$origin"}
  path=${path#/}

  export OPENAI_HOST="${OPENAI_HOST:-$origin}"
  export OPENAI_BASE_PATH="${OPENAI_BASE_PATH:-${path}/chat/completions}"
fi

: "${OPENAI_API_KEY:=not-needed}"
export OPENAI_API_KEY

if [ -z "${GOOSE_MODEL:-}" ]; then
  echo "GOOSE_MODEL is required." >&2
  exit 1
fi
if [ -z "${OPENAI_HOST:-}" ]; then
  echo "OPENAI_HOST or MODEL_RUNNER_URL is required." >&2
  exit 1
fi
if [ -z "${OPENAI_BASE_PATH:-}" ]; then
  echo "OPENAI_BASE_PATH or MODEL_RUNNER_URL is required." >&2
  exit 1
fi

exec goose "$@"
