# Architecture

The repository intentionally separates the MCP server from both the simulator and the real model.

```text
Deterministic test lane

agent-sim-test ──OpenAI HTTP──> LLMSim scripted mode
      │
      └──Streamable HTTP MCP──> Spring AI MCP server ──> read-only workspace

Real-model verification lane

Goose ──OpenAI-compatible HTTP──> Docker Model Runner ──> local tool-capable model
  │
  └──Streamable HTTP MCP────────> Spring AI MCP server ──> read-only workspace
```

## Why two lanes?

LLMSim is deterministic and inexpensive. It is suitable for asserting protocol behavior, tool selection fixtures, error handling, streaming, latency, and metrics. It does not reason about the prompt or the tool output.

A local LLM is nondeterministic and slower. It verifies that a real model can understand the prompt, choose a suitable MCP tool, interpret the result, and produce a useful answer.

Keeping the lanes separate prevents model variability from making ordinary development tests flaky.

## Spring application boundary

The Spring Boot application is only an MCP server. It does not call an LLM. It publishes six read-only filesystem tools at `/mcp` and mounts one host directory at `/workspace` inside the container.
