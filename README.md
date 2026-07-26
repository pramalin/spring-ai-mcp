# Spring AI MCP server development and testing example

This repository is a small example for developing and testing a Spring AI MCP
server without requiring a real LLM for every test.

It deliberately separates two concerns:

- **Deterministic verification:** `pramalin/llmsim` v0.10.1 supplies scripted
  OpenAI-compatible model responses, captured-call inspection, and a browser
  console.
- **Real-model acceptance:** Goose and Docker Model Runner exercise the same MCP
  server with a local model after the deterministic tests pass.

Open WebUI is intentionally not included.

## Architecture

```text
Deterministic lane

Python test harness ──OpenAI HTTP──> LLMSim WorkspaceSummaryFlow
        │                              │
        │                              └── captured-call journal + console
        │
        └──MCP Streamable HTTP────> Spring MCP server ──> mounted files

Real-model lane

Goose CLI ──OpenAI-compatible HTTP──> Docker Model Runner ──> local model
    │
    └──MCP Streamable HTTP──────────> Spring MCP server ──> mounted files
```

The Spring application does not own or call an LLM. It only implements tool
logic and exposes the tools through MCP. The deterministic harness acts as the
agent host: it asks LLMSim for the next model response, executes requested MCP
tools, and sends the real tool result back to LLMSim.

## Filesystem MCP tools

The server exposes these read-only tools at `http://localhost:8080/mcp`:

| Tool | Purpose |
|---|---|
| `workspace_summary` | Count the complete mounted workspace and group files by extension |
| `count_files` | Count entries below a relative directory |
| `list_files` | Return a bounded directory listing |
| `find_files` | Search names using a case-insensitive fragment |
| `file_metadata` | Inspect one relative path |
| `read_text_file` | Read bounded UTF-8 text |

Compose mounts `${FILES_HOST_DIR}` at `/workspace` read-only. The Java service
rejects absolute paths, traversal, symlinks escaping the root, binary reads,
oversized reads, and excessively large listings.

## Repository layout

```text
compose.yaml                  Spring MCP, LLMSim, and deterministic test services
compose.local.yaml            Optional Goose + Docker Model Runner overlay
src/main                      Spring MCP server
src/test                      Java service tests
llmsim/Dockerfile             Extends ghcr.io/pramalin/llmsim-build:0.10.1
llmsim/WorkspaceSummaryFlow.scala
                              Project-owned deterministic model script
test-harness/                 MCP client and two-turn agent-loop assertions
goose/                        Containerized Goose CLI
scripts/                      Development workflows
docs/                         Architecture and extension notes
sample-files/                 Mounted test fixture
.github/workflows/ci.yml      Deterministic CI workflow
```

## Prerequisites

Deterministic tests require:

- Docker Engine
- Docker Compose
- `curl`
- access to GitHub Container Registry for the first LLMSim image pull

The optional real-model lane additionally requires:

- Docker Compose 2.38 or later
- Docker Model Runner
- enough RAM for the selected local model

No host Java, Maven, Python, Scala, sbt, Node, Goose, or LLMSim installation is
required.

## Initial setup

```bash
cp .env.example .env
./scripts/verify-environment.sh
```

The bundled fixture contains three files and two directories. To inspect a
different safe directory, edit `.env`:

```dotenv
FILES_HOST_DIR=/home/pramalin/some-test-directory
```

Avoid mounting your complete home directory for a test project.

## Test layers

### 1. Java logic tests

```bash
./scripts/test-unit.sh
# or
make unit
```

These tests create temporary directories and verify counting, searching,
bounded text reads, and traversal rejection without Spring, MCP, LLMSim, or an
LLM.

### 2. Direct MCP protocol test

```bash
./scripts/test-mcp.sh
# or
make mcp
```

A Python MCP client initializes a Streamable HTTP session, discovers all six
tools, invokes `workspace_summary`, and checks the mounted fixture counts.

### 3. Deterministic LLMSim agent-loop test

```bash
./scripts/test-sim.sh
# or
make sim
```

`llmsim/WorkspaceSummaryFlow.scala` has exactly two scripted steps:

1. Return an OpenAI `workspace_summary` tool call.
2. Build the final response from the real MCP tool result sent back by the
   harness.

Before the test, the harness calls `POST /_llmsim/reset`. After the two model
requests, it asserts:

- the expected MCP tool was requested;
- the real MCP result contains the expected fixture counts;
- the tool result was included in the second model request;
- the LLMSim journal contains exactly two successful OpenAI calls;
- script step indexes are `0` and `1`;
- the dashboard reports two responded calls and an exhausted exact script.

The test prints the captured call journal before stopping LLMSim.

Run every deterministic layer:

```bash
./scripts/test-all.sh
# or
make test
```

## LLMSim browser console

Start the MCP server and LLMSim:

```bash
./scripts/llmsim-console.sh
# or
make console
```

Open:

```text
http://localhost:8089/_llmsim/console
```

In another terminal, populate the console with the deterministic scenario:

```bash
./scripts/test-sim-console.sh
# or
make console-test
```

The console shows the call journal, provider/outcome/streaming/model filters,
script state, messages, raw request, response outcome, tool-call arguments, and
headers. The service remains running so the captured calls can be inspected.

A compact JSON view is also available:

```bash
./scripts/llmsim-stats.sh
# or
make stats
```

That prints:

```text
GET /_llmsim/dashboard
GET /_llmsim/calls
```

Reset both the script and journal without restarting the container:

```bash
curl -X POST http://localhost:8089/_llmsim/reset
```

## How the project-specific LLMSim image works

The Dockerfile pins the reusable build image:

```dockerfile
ARG LLMSIM_VERSION=0.10.1
FROM ghcr.io/pramalin/llmsim-build:${LLMSIM_VERSION} AS build
```

It copies only this repository's script into the prepared build environment and
runs `sbt assembly`. The runtime image contains LLMSim, the custom script, and
the packaged browser console.

The script belongs to this project rather than to the LLMSim repository:

```scala
object WorkspaceSummaryFlow extends ScriptSource {
  val script: Script = Script.exactly(
    toolCall(
      id = "workspace-summary-1",
      name = "workspace_summary",
      arguments = "{}"
    ),
    replyFromToolResult("workspace-summary-1") { result =>
      s"Workspace inspection completed successfully. MCP tool result: $result"
    }
  )
}
```

To change the deterministic orchestration, edit
`llmsim/WorkspaceSummaryFlow.scala`, then rebuild:

```bash
docker compose --profile sim build llmsim
```

Keep a script scenario narrow. Each scenario should test one expected agent
flow or one failure mode.

## Suggested additional LLMSim scenarios

Useful next examples include:

- `find_files` followed by `read_text_file`;
- malformed tool-call arguments;
- an unknown MCP tool name;
- rate-limit or server-error responses;
- fixed token usage near an application budget boundary;
- streaming tool-call arguments split across multiple events;
- delayed streaming and client timeout behavior;
- script overrun to detect unexpected extra model calls.

Create separate `ScriptSource` objects for these flows and select one with the
`LLMSIM_SCRIPT` environment variable, or create separate Compose services when
tests run in parallel.

## 4. Real local-model verification

After deterministic tests pass, start Goose with Docker Model Runner and the
same MCP endpoint:

```bash
./scripts/local-mcp.sh
# or
make local-mcp
```

Try:

```text
Use workspace_summary and tell me how many files and directories are mounted.
```

```text
Find files containing project, read the matching text files, and summarize them.
```

This lane verifies model reasoning and tool selection, so it is intentionally a
manual acceptance test rather than a deterministic CI assertion.

Test the model without MCP:

```bash
./scripts/local-chat.sh
# or
make local-chat
```

The default model is configured in `.env`:

```dotenv
LOCAL_LLM_MODEL=ai/qwen2.5:3B-Q4_K_M
```

## Adding an MCP tool

See [`docs/adding-a-tool.md`](docs/adding-a-tool.md). The intended sequence is:

1. Implement domain logic as an ordinary Java service.
2. Unit-test the service without MCP.
3. Add a thin MCP tool adapter.
4. Extend the direct MCP smoke test.
5. Add a focused LLMSim `ScriptSource` for orchestration behavior.
6. Assert the LLMSim call journal and application result.
7. Verify the same flow with a real local model.

## Stopping and cleanup

```bash
./scripts/down.sh
# or
make down
```

For a stranded one-off Goose container:

```bash
./scripts/force-clean.sh
# or
make clean
```

These commands do not remove Docker Model Runner's downloaded model cache.

## Useful endpoints

| Endpoint | Purpose |
|---|---|
| `http://localhost:8080/mcp` | Spring MCP Streamable HTTP endpoint |
| `http://localhost:8080/api/workspace/summary` | Non-MCP diagnostic endpoint |
| `http://localhost:8089/v1/chat/completions` | LLMSim OpenAI-compatible chat API |
| `http://localhost:8089/v1/models` | LLMSim model-list compatibility endpoint |
| `http://localhost:8089/_llmsim/status` | Quick captured-call count |
| `http://localhost:8089/_llmsim/calls` | Complete captured-call journal |
| `http://localhost:8089/_llmsim/dashboard` | Aggregated script/journal metrics |
| `http://localhost:8089/_llmsim/ui` | Minimal dashboard |
| `http://localhost:8089/_llmsim/console` | Full Tyrian console |

## Project references

- LLMSim: `https://github.com/pramalin/llmsim`
- Spring project: `https://github.com/pramalin/spring-ai-mcp`
