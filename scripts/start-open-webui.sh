#!/usr/bin/env bash
set -euo pipefail
exec docker compose --profile webui up --build -d spring-ai-mcp-server open-webui
