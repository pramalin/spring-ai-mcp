# Testing strategy

## Layer 1: Java logic tests

`WorkspaceFileServiceTests` creates a temporary filesystem and checks counting, bounded reads, case-insensitive search, and path-traversal rejection.

Run:

```bash
./scripts/test-unit.sh
```

This layer does not require an LLM, MCP transport, or the Spring container.

## Layer 2: MCP protocol smoke test

The Python MCP SDK connects to the running Spring server over Streamable HTTP, initializes a session, lists tools, calls `workspace_summary`, and asserts the fixture counts.

Run:

```bash
./scripts/test-mcp.sh
```

This layer proves that annotations, transport configuration, tool schemas, serialization, and filesystem mounting work together.

## Layer 3: deterministic agent-loop test with LLMSim

LLMSim scripted mode returns a fixed OpenAI tool call for `workspace_summary`. The test harness executes the call over MCP, validates the tool result, returns it to LLMSim, and verifies the scripted final turn.

Run:

```bash
./scripts/test-sim.sh
```

This layer tests the complete orchestration path without model reasoning or nondeterminism.

## Layer 4: real local-model verification

Goose connects to Docker Model Runner and the same Spring MCP endpoint. A tool-capable local model must decide whether and how to call the filesystem tools.

Run:

```bash
./scripts/local-mcp.sh
```

This is verification rather than a stable automated test. Results depend on model quality, context size, prompt, and available compute.
