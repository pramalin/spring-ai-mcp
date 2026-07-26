# Deterministic Testing for Spring AI MCP Servers

A reference project for Java and Spring developers who want fast, repeatable
MCP server tests before involving a real language model.

## Why this exists

Testing an MCP server against a real LLM mixes together two different
questions:

1. **Does the server work correctly?**
   Are the domain logic, MCP transport, tool schemas, serialization, mounted
   resources, and agent orchestration correct?
2. **Can this model use the server correctly?**
   Can the model understand the prompt, choose the appropriate tool, provide
   valid arguments, and interpret the result?

A real model is necessary for the second question, but it is a poor foundation
for routine development and CI tests. Model behavior is non-deterministic,
inference can be slow and resource-intensive, and exact tool-call sequences are
difficult to assert reliably.

This project separates those concerns into two lanes:

- **Deterministic development and CI testing:**
  [LLMSim](https://github.com/pramalin/llmsim) returns scripted
  OpenAI-compatible responses, including exact tool calls. The test harness
  executes those calls against the real Spring MCP server and asserts the
  complete interaction.
- **Real-model acceptance testing:** Goose and Docker Model Runner exercise the
  same MCP server with a local tool-capable model after the deterministic tests
  pass.

The read-only filesystem tools are the **example domain**. The reusable part is
the testing pattern:

```text
Java unit tests
      ↓
direct MCP protocol tests
      ↓
deterministic LLMSim agent-loop tests
      ↓
real-model acceptance with Goose
```

Open WebUI is intentionally not included.

## Who this is for

This repository is intended for Java and Spring developers building MCP servers
who want:

- CI-safe tests without downloading or running an LLM;
- exact assertions on tool names, arguments, results, and model-call order;
- a clear boundary between server correctness and model capability;
- a small reference project that can be adapted to another domain.

## What each test layer proves

| Layer | What it verifies | Real LLM required |
|---|---|---:|
| Java unit tests | Domain logic, validation, and security boundaries | No |
| Direct MCP test | Tool discovery, schemas, transport, serialization, and invocation | No |
| LLMSim agent-loop test | Model request, tool call, MCP execution, tool-result return, and final response sequence | No |
| Goose acceptance test | Prompt understanding, tool selection, arguments, and result interpretation | Yes |

The deterministic tests answer:

> Did we build the MCP server and orchestration correctly?

The Goose test answers:

> Can this model use the server successfully?

## Quick start

### Run all deterministic tests

```bash
cp .env.example .env
./scripts/test-all.sh
# or
make test
```

This runs:

1. Java service tests;
2. a direct MCP Streamable HTTP test;
3. a deterministic two-turn LLMSim tool-call scenario.

No real LLM is used.

### Inspect the simulated model interaction

Start Spring MCP and LLMSim:

```bash
./scripts/llmsim-console.sh
# or
make console
```

In another terminal, run the scenario without stopping the services:

```bash
./scripts/test-sim-console.sh
# or
make console-test
```

Open:

```text
http://localhost:8089/_llmsim/console
```

The console shows the captured requests, normalized messages, raw payloads,
tool-call arguments, script state, outcomes, timing, headers, and filters.

### Verify with a real local model

After the deterministic tests pass:

```bash
./scripts/local-mcp.sh
# or
make local-mcp
```

Try:

```text
Use workspace_summary and tell me how many files and directories are mounted.
```

This is a manual acceptance test. Its result depends on model quality, prompt,
context size, and available compute.

## Architecture

```text
Deterministic test lane

Python test harness ──OpenAI HTTP──> LLMSim scripted response
        │                              │
        │                              └── captured-call journal + console
        │
        └──MCP Streamable HTTP────> Spring MCP server ──> mounted files

Real-model acceptance lane

Goose CLI ──OpenAI-compatible HTTP──> Docker Model Runner ──> local model
    │
    └──MCP Streamable HTTP──────────> Spring MCP server ──> mounted files
```

The Spring application does not own or call an LLM. It implements domain logic
and exposes tools through MCP. In the deterministic lane, the Python harness is
the agent host: it asks LLMSim for the next model response, executes requested
MCP tools, and sends the real tool result back to LLMSim.

See [`docs/architecture.md`](docs/architecture.md) for the component boundaries
and design rationale.

## Example domain: read-only filesystem tools

The example MCP server exposes six tools at `http://localhost:8080/mcp`:

| Tool | Purpose |
|---|---|
| `workspace_summary` | Count the mounted workspace and group files by extension |
| `count_files` | Count entries below a relative directory |
| `list_files` | Return a bounded directory listing |
| `find_files` | Search names using a case-insensitive fragment |
| `file_metadata` | Inspect one relative path |
| `read_text_file` | Read bounded UTF-8 text |

Compose mounts `${FILES_HOST_DIR}` at `/workspace` read-only. The Java service
rejects absolute paths, traversal, symbolic links escaping the root, binary
reads, oversized reads, and excessively large listings.

The filesystem implementation is deliberately small. Replace it with your own
domain service while retaining the same testing layers.

## How LLMSim enables deterministic MCP testing

`llmsim/WorkspaceSummaryFlow.scala` defines an exact two-step model scenario:

1. Return an OpenAI tool call for `workspace_summary`.
2. Build the final response from the real MCP tool result returned by the test
   harness.

The test therefore exercises the real path:

```text
model request
  → scripted tool call
  → real MCP invocation
  → real domain result
  → OpenAI tool-result message
  → scripted final response
```

Before each run, the harness resets LLMSim with:

```text
POST /_llmsim/reset
```

After the run, it asserts the call journal and dashboard through:

```text
GET /_llmsim/calls
GET /_llmsim/dashboard
```

LLMSim does not execute MCP and does not reason about the prompt. It provides
controlled model behavior so the application and orchestration can be tested
without model variability.

See [`docs/llmsim.md`](docs/llmsim.md) for the custom image and script details.

## Test layers in detail

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

A Python MCP client initializes a Streamable HTTP session, discovers the tools,
invokes `workspace_summary`, and checks the mounted fixture counts.

### 3. Deterministic LLMSim agent-loop test

```bash
./scripts/test-sim.sh
# or
make sim
```

The harness asserts:

- the expected MCP tool was requested;
- the tool arguments are correct;
- the real MCP result contains the expected fixture counts;
- the tool result appears in the second model request;
- LLMSim captured exactly two successful OpenAI calls;
- the expected script steps were consumed in order.

### 4. Real local-model acceptance

```bash
./scripts/local-mcp.sh
# or
make local-mcp
```

Use this lane to check whether a real model can select and use the tools. Do not
use its generated wording as a stable CI assertion.

For the complete testing rationale, see
[`docs/testing-strategy.md`](docs/testing-strategy.md).

## Adapting this pattern to another MCP server

Replace the filesystem service with your domain logic while retaining the test
sequence:

1. Implement domain behavior in an ordinary Spring service.
2. Unit-test that service without MCP.
3. Add a thin `@McpTool` adapter.
4. Extend the direct MCP test to discover and invoke the tool.
5. Add a focused LLMSim `ScriptSource` for the expected agent flow.
6. Assert the captured model calls and the real tool result.
7. Run final acceptance with a real local model.

Keep each LLMSim scenario narrow: one expected flow or one failure mode. Useful
additional scenarios include invalid arguments, unknown tools, server failures,
timeouts, streaming tool arguments, and unexpected extra model calls.

See [`docs/adding-a-tool.md`](docs/adding-a-tool.md) for a practical checklist.

## Repository layout

```text
compose.yaml                  Spring MCP, LLMSim, and deterministic tests
compose.local.yaml            Optional Goose + Docker Model Runner overlay
src/main                      Spring MCP server
src/test                      Java domain-service tests
llmsim/Dockerfile             Extends pramalin/llmsim-build
llmsim/WorkspaceSummaryFlow.scala
                              Project-owned deterministic model script
test-harness/                 MCP client and agent-loop assertions
goose/                        Containerized Goose CLI
scripts/                      Development workflows
docs/                         Architecture and extension notes
sample-files/                 Mounted test fixture
.github/workflows/ci.yml      Deterministic CI workflow
```

## Prerequisites

Deterministic tests require:

- Docker Engine;
- Docker Compose;
- `curl`;
- access to GitHub Container Registry for the initial LLMSim image pull.

The optional real-model lane additionally requires:

- Docker Compose 2.38 or later;
- Docker Model Runner;
- enough RAM for the selected model.

No host Java, Maven, Python, Scala, sbt, Node, Goose, or LLMSim installation is
required.

## Initial setup and mounted files

```bash
cp .env.example .env
./scripts/verify-environment.sh
```

The bundled fixture contains three files and two directories. To inspect a
different safe directory, edit `.env`:

```dotenv
FILES_HOST_DIR=/home/pramalin/some-test-directory
```

Avoid mounting your complete home directory in a test setup.

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
| `http://localhost:8089/v1/chat/completions` | LLMSim OpenAI-compatible API |
| `http://localhost:8089/v1/models` | LLMSim model-list compatibility endpoint |
| `http://localhost:8089/_llmsim/status` | Captured-call count and status |
| `http://localhost:8089/_llmsim/calls` | Complete captured-call journal |
| `http://localhost:8089/_llmsim/dashboard` | Aggregated script and journal metrics |
| `http://localhost:8089/_llmsim/ui` | Minimal dashboard |
| `http://localhost:8089/_llmsim/console` | Full browser console |

## Detailed documentation

- [`docs/architecture.md`](docs/architecture.md) — boundaries and the two-lane design
- [`docs/testing-strategy.md`](docs/testing-strategy.md) — what each test proves and does not prove
- [`docs/llmsim.md`](docs/llmsim.md) — LLMSim image, script, journal, and console
- [`docs/adding-a-tool.md`](docs/adding-a-tool.md) — adapting the pattern to a new tool
- [`docs/github-repository.md`](docs/github-repository.md) — suggested GitHub description and topics

## Project references

- LLMSim: https://github.com/pramalin/llmsim
- Spring AI MCP example: https://github.com/pramalin/spring-ai-mcp
