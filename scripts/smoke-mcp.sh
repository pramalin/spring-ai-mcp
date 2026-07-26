#!/usr/bin/env bash
set -euo pipefail
exec docker compose --profile cli run --rm goose-mcp \
  run --no-session \
  --with-streamable-http-extension http://spring-ai-mcp-server:8080/mcp \
  --text "Use workspace_summary and report only the file count and directory count."
