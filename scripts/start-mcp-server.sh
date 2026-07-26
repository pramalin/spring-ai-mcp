#!/usr/bin/env bash
set -euo pipefail
exec docker compose up --build -d spring-ai-mcp-server
