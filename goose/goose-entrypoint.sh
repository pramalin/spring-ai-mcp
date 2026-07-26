#!/usr/bin/env sh
set -eu

# Compose injects MODEL_RUNNER_URL and GOOSE_MODEL from the service's model binding.
# Goose expects the endpoint split into an origin and a chat-completions base path.
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
  echo "GOOSE_MODEL was not injected by Docker Compose." >&2
  exit 1
fi

exec goose "$@"
