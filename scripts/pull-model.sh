#!/usr/bin/env sh
set -eu
MODEL="${LLM_MODEL:-ai/qwen2.5:3B-Q4_K_M}"
echo "Pulling ${MODEL} with Docker Model Runner..."
docker model pull "${MODEL}"
