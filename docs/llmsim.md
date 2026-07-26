# LLMSim integration

## Role in this project

This project uses [`pramalin/llmsim`](https://github.com/pramalin/llmsim)
v0.10.1 to make model-facing MCP integration tests deterministic.

LLMSim is the enabling mechanism for the CI-safe agent-loop test. It replaces
model reasoning with a project-owned script so the test can assert an exact
tool-call sequence while still invoking the real Spring MCP server.

LLMSim is not the MCP client and does not execute tools. The Python harness
remains responsible for MCP discovery and invocation.

## Downstream extension model

`llmsim/Dockerfile` starts from:

```dockerfile
FROM ghcr.io/pramalin/llmsim-build:0.10.1 AS build
```

The build image contains the simulator API, resolved dependencies, and compiled
cache. This project adds `WorkspaceSummaryFlow.scala` and runs `sbt assembly` to
create a simulator image containing the project-specific scenario.

Keeping the script in this repository makes the simulated model behavior part
of the application test suite rather than a global LLMSim configuration.

## Scripted MCP round trip

The custom script emits a `workspace_summary` OpenAI tool call. The Python
harness invokes the real Spring MCP tool and sends the result back as a tool
message. `replyFromToolResult` builds the second LLMSim response from that real
value.

```text
Python harness → LLMSim: user request + tool definitions
LLMSim → harness: workspace_summary tool call
harness → Spring MCP: tools/call
Spring MCP → harness: real workspace result
harness → LLMSim: OpenAI tool-result message
LLMSim → harness: final scripted response
```

This verifies the orchestration path without asking a model to make a
non-deterministic tool-selection decision.

## Captured-call assertions

The harness resets LLMSim before a scenario:

```text
POST /_llmsim/reset
```

It then reads:

```text
GET /_llmsim/calls
GET /_llmsim/dashboard
```

The journal can be used to assert:

- provider and model;
- normalized messages and raw request;
- selected script step;
- tool call and arguments;
- response outcome;
- timing and streaming state;
- request count and script exhaustion.

Prefer assertions on protocol and business facts. Avoid coupling tests to
incidental text formatting.

## Browser console

Start Spring and LLMSim:

```bash
./scripts/llmsim-console.sh
```

Open:

```text
http://localhost:8089/_llmsim/console
```

Run `./scripts/test-sim-console.sh` in another terminal to reset the script and
populate the console with the two-turn workspace scenario.

The console is useful for diagnosing failed request shapes, incorrect tool-call
IDs, unexpected extra calls, and script-state problems.

## Adding scenarios

Keep each scenario focused on one expected interaction or one failure mode.
Examples:

- `find_files` followed by `read_text_file`;
- malformed tool arguments;
- unknown tool name;
- model-provider error;
- delayed or streaming responses;
- client timeout;
- script overrun.

Use LLMSim for deterministic orchestration tests. Use Goose with a real local
model for semantic tool-selection acceptance.

## Version pinning

The default is:

```dotenv
LLMSIM_VERSION=0.10.1
```

Upgrade deliberately, rebuild, and rerun all deterministic tests:

```bash
docker compose --profile sim build --no-cache llmsim
./scripts/test-all.sh
```
