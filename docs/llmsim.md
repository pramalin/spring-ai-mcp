# LLMSim integration

This project uses `pramalin/llmsim` v0.10.1 as a deterministic substitute for a
real model during integration tests.

## Downstream extension model

`llmsim/Dockerfile` starts from:

```dockerfile
FROM ghcr.io/pramalin/llmsim-build:0.10.1 AS build
```

The build image already contains the simulator API, resolved dependencies, and
compiled cache. The project adds `WorkspaceSummaryFlow.scala` and runs
`sbt assembly` to create its own simulator image.

## Scripted MCP round trip

The custom script emits a `workspace_summary` OpenAI tool call. The Python
harness invokes the real Spring MCP tool and sends its result back as a tool
message. `replyFromToolResult` builds the second LLMSim response from that real
value.

LLMSim does not execute MCP itself. It simulates model responses while the
application or test harness remains responsible for tool execution.

## Captured-call assertions

The harness resets LLMSim with:

```text
POST /_llmsim/reset
```

It then reads:

```text
GET /_llmsim/calls
GET /_llmsim/dashboard
```

The journal is used to assert provider, model, messages, raw request, selected
script step, outcome, response body, timing, and streaming state.

## Browser console

```bash
./scripts/llmsim-console.sh
```

Open:

```text
http://localhost:8089/_llmsim/console
```

Run `./scripts/test-sim-console.sh` in another terminal to reset the script and
populate the console with the two-turn workspace scenario.

The console is packaged in the release/build image and served by the same JVM
process as the simulated vendor endpoints.

## Version pinning

The default is:

```dotenv
LLMSIM_VERSION=0.10.1
```

The tag is intentionally pinned. Upgrade it deliberately, rebuild, and rerun
all deterministic tests:

```bash
docker compose --profile sim build --no-cache llmsim
./scripts/test-all.sh
```
