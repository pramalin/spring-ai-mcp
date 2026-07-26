# Architecture

## Design goal

The project demonstrates a reusable way to test an MCP server without making a
real LLM part of every development and CI run.

It separates two kinds of confidence:

- **server confidence** — the domain logic, MCP contract, transport, and agent
  orchestration are correct;
- **model confidence** — a particular model can understand a request and choose
  and use the tools successfully.

These are related, but they should not fail for the same reasons or be tested by
the same mechanism.

## Component view

```text
Deterministic test lane

Python agent harness ──OpenAI HTTP──> LLMSim scripted mode
        │                                  │
        │                                  └── call journal + browser console
        │
        └──Streamable HTTP MCP────────> Spring AI MCP server
                                                │
                                                └── read-only workspace

Real-model acceptance lane

Goose ──OpenAI-compatible HTTP──> Docker Model Runner ──> local model
  │
  └──Streamable HTTP MCP────────> Spring AI MCP server
                                          │
                                          └── read-only workspace
```

Both lanes use the same Spring application and the same MCP endpoint. Only the
model side changes.

## Responsibilities

### Spring MCP server

The Spring Boot application:

- owns the domain logic;
- validates tool inputs and security boundaries;
- exposes tools through Streamable HTTP at `/mcp`;
- does not call an LLM;
- does not contain agent-loop logic.

The filesystem implementation is an example domain, not the architectural goal.

### Deterministic agent harness

The Python harness acts as the MCP host for automated tests. It:

1. sends an OpenAI-compatible request to LLMSim;
2. receives a scripted tool call;
3. invokes the real Spring MCP tool;
4. sends the real tool result back as an OpenAI tool message;
5. receives the scripted final response;
6. asserts the entire interaction.

### LLMSim

LLMSim supplies controlled model behavior. It records calls and exposes the
journal and metrics through management endpoints and a browser console.

LLMSim does not execute MCP and does not decide which tool is semantically best.
That limitation is intentional: the deterministic lane tests orchestration, not
model reasoning.

### Goose and the local model

Goose is the real-model MCP host. It connects a local tool-capable model to the
same Spring MCP endpoint.

This lane verifies behavior that cannot be made fully deterministic:

- prompt understanding;
- tool selection;
- argument generation;
- interpretation of tool results;
- quality of the final answer.

## Why two lanes?

Using only a real model makes server tests slow and flaky. A failure might be
caused by the MCP server, the model, a prompt change, context pressure, model
availability, or inference performance.

Using only scripted responses proves the protocol path but says nothing about
whether a real model can use the tools.

The two-lane design makes failures easier to locate:

| Failure | Most likely area |
|---|---|
| Unit test fails | Domain logic or validation |
| Direct MCP test fails | MCP exposure, schema, serialization, transport, or mount |
| LLMSim scenario fails | Agent orchestration or expected request sequence |
| Only Goose acceptance fails | Model capability, prompt, context, or local inference |

## Security boundary in the example

The mounted directory is read-only. The service rejects absolute paths, `..`
traversal, symbolic links that escape the root, binary reads, oversized reads,
and excessive listing results.

These checks belong in the domain service and are tested without MCP so security
behavior remains fast and deterministic.
