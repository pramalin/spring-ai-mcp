# Spring AI Files MCP + Goose + Docker Model Runner

A Docker Compose lab for testing a local tool-capable LLM against a Spring AI MCP server.

The stack has three independent pieces:

```text
Goose terminal chat ──> Docker Model Runner ──> local LLM
         |
         └── Streamable HTTP MCP ──> Spring Boot ──> read-only mounted directory
```

## What the Spring application does

The Spring Boot application does **not** call the LLM. It is a model-independent MCP server.

It exposes Streamable HTTP MCP at:

```text
http://spring-ai-mcp-server:8080/mcp
```

It offers six read-only filesystem tools:

| Tool | Purpose |
|---|---|
| `workspace_summary` | Count all files/directories and group files by extension |
| `count_files` | Count entries below a selected relative directory |
| `list_files` | Return a bounded directory listing |
| `find_files` | Search entry names by case-insensitive text |
| `file_metadata` | Return type, size, extension, and modification time |
| `read_text_file` | Read a bounded amount of a UTF-8 text file |

The host directory is mounted read-only at `/workspace`. The service rejects absolute paths, traversal using `..`, escaping symbolic links, binary files, and excessive result sizes.

## Default model

The default is:

```text
ai/qwen2.5:3B-Q4_K_M
```

It is smaller than Bonsai 8B and is listed by Docker as supporting tool calling. The Compose context size is a literal integer (`8192`) because some Compose versions reject interpolated values for `context_size` as strings.

Bonsai is still available by changing `LLM_MODEL` in `.env`:

```dotenv
LLM_MODEL=hf.co/prism-ml/Bonsai-8B-gguf:Q1_0
```

A model that can chat but cannot produce OpenAI-style tool calls may work with `goose-chat` and fail with `goose-mcp`.

## Requirements

- Docker Engine or Docker Desktop
- Docker Compose 2.38 or later
- Docker Model Runner installed and enabled
- Enough RAM/VRAM for the selected model

Verify the environment:

```bash
./scripts/verify-environment.sh
```

## Initial setup

```bash
cp .env.example .env
```

The default mounted directory is the included `sample-files` folder. To expose a different safe directory, edit `.env`:

```dotenv
FILES_HOST_DIR=/home/pramalin/some-test-directory
```

Do not mount your entire home directory during initial testing.

Validate the Compose model before building:

```bash
docker compose config
```

## Test 1: plain terminal chat, without MCP

This is the first test. It answers whether the model, Docker Model Runner, and Goose can communicate without any MCP tool descriptions.

```bash
./scripts/chat.sh
```

Equivalent command:

```bash
docker compose --profile cli run --rm goose-chat
```

Try:

```text
hi
```

A noninteractive smoke test is also included:

```bash
./scripts/smoke-chat.sh
```

If this fails, do not test MCP yet. Inspect the model and Docker Model Runner first.

## Test 2: terminal chat with the Spring MCP server

```bash
./scripts/chat-mcp.sh
```

Equivalent command:

```bash
docker compose --profile cli run --rm goose-mcp
```

Compose starts the Spring server, waits for its health check, starts Goose, and connects Goose to:

```text
http://spring-ai-mcp-server:8080/mcp
```

Try these prompts:

```text
What MCP tools are available?
```

```text
Use workspace_summary and tell me how many files and directories exist.
```

```text
List all files recursively, then summarize what is in each text file.
```

Noninteractive MCP smoke test:

```bash
./scripts/smoke-mcp.sh
```

With the included sample directory, the workspace contains three files and two subdirectories.

## Test the Spring service without an LLM

Start only the MCP server:

```bash
./scripts/start-mcp-server.sh
```

Then call its ordinary diagnostic endpoint:

```bash
./scripts/test-api.sh
```

Or:

```bash
curl http://localhost:8080/api/workspace/summary
```

Application information:

```bash
curl http://localhost:8080/api/info
```

These REST endpoints are only diagnostics. MCP clients use `/mcp`.

## How model endpoint injection works

The `local-llm` Compose model is bound to the Goose services using long syntax:

```yaml
models:
  local-llm:
    endpoint_var: MODEL_RUNNER_URL
    model_var: GOOSE_MODEL
```

Docker Compose provisions the model and injects its endpoint and model name. The Goose image includes `goose-entrypoint.sh`, which converts the injected endpoint into the two settings required by Goose's OpenAI-compatible provider:

```text
OPENAI_HOST
OPENAI_BASE_PATH
```

This avoids assuming whether the Docker platform exposes Model Runner at a host port or an internal model endpoint.

## Compose services

| Service | Profile | Role |
|---|---|---|
| `spring-ai-mcp-server` | default | Spring AI read-only filesystem MCP server |
| `goose-chat` | `cli` | Minimal terminal chat without MCP |
| `goose-mcp` | `cli` | Terminal chat connected to Spring MCP |

## Useful commands

Build the two local images:

```bash
docker compose --profile cli build spring-ai-mcp-server goose-chat
```

See running services:

```bash
docker compose ps
```

See Spring logs:

```bash
docker compose logs -f spring-ai-mcp-server
```

Stop services:

Select Bonsai for one command without editing `.env`:

```bash
LLM_MODEL=hf.co/prism-ml/Bonsai-8B-gguf:Q1_0 \
  docker compose --profile cli run --rm goose-chat
```

## Troubleshooting sequence

### `context size exceeded` even for a short message

Run plain chat first:

```bash
./scripts/chat.sh
```

If plain chat works and MCP chat fails, the additional agent instructions and MCP schemas exceed the model's practical context or the model is weak at tool calling.

### Plain chat is very slow

Try the default Qwen 2.5 3B model rather than Bonsai. Also check whether Docker Model Runner is using CPU-only inference.

### Spring works but Goose cannot connect to MCP

Check:

```bash
docker compose ps
docker compose logs spring-ai-mcp-server
curl http://localhost:8080/actuator/health
```

Then rerun:

```bash
./scripts/chat-mcp.sh
```

### Goose reports provider configuration errors

Inspect the variables injected into a one-off container:

```bash
docker compose --profile cli run --rm --entrypoint /bin/sh goose-chat -lc \
  'env | sort | grep -E "GOOSE|MODEL_RUNNER|OPENAI"'
```

You should see `GOOSE_MODEL` and `MODEL_RUNNER_URL`. The entrypoint derives `OPENAI_HOST` and `OPENAI_BASE_PATH` at startup.

## Source references

- Docker Compose models: https://docs.docker.com/ai/compose/models-and-compose/
- Goose providers and Docker Model Runner: https://goose-docs.ai/docs/getting-started/providers/
- Goose Streamable HTTP extensions: https://goose-docs.ai/docs/getting-started/using-extensions/
- Goose in Docker: https://goose-docs.ai/docs/tutorials/goose-in-docker/
- Qwen 2.5 Docker model: https://hub.docker.com/r/ai/qwen2.5
