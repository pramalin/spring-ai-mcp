# Adding another MCP tool

The filesystem tools are examples. The intended reuse path is to replace or
extend them with your own domain while preserving the testing layers.

## 1. Implement domain logic outside MCP

Put filesystem, database, network, or business behavior in a normal Spring
service. Give it ordinary Java inputs and outputs so it can be tested without an
MCP runtime.

Avoid placing substantive logic directly in an annotated MCP method.

## 2. Add focused unit tests

Test success cases, boundary conditions, validation, and security behavior with
JUnit.

Run:

```bash
./scripts/test-unit.sh
```

## 3. Add a thin MCP adapter

Expose the service through a small method annotated with `@McpTool` and
`@McpToolParam`.

The adapter should primarily:

- define the tool name and description;
- map MCP parameters to domain inputs;
- call the service;
- return a stable result shape.

## 4. Extend the direct MCP test

Update `mcp_smoke.py` so the new tool:

- appears during tool discovery;
- has the expected schema;
- can be invoked with representative arguments;
- returns the expected real result.

Run:

```bash
./scripts/test-mcp.sh
```

## 5. Add a focused LLMSim scenario

Create or update a project-owned `ScriptSource` when the agent loop should use
the tool.

A scenario should test one expected flow, for example:

```text
model tool call
  → MCP invocation
  → tool result
  → second model request
  → final response
```

Useful failure scenarios include:

- malformed arguments;
- an unknown tool name;
- a domain validation error;
- a server error or timeout;
- an unexpected extra model call;
- streaming tool-call arguments.

## 6. Assert stable orchestration facts

Assert the tool name, arguments, result, tool-call ID, request order, and
journal outcome. Do not rely on a real model's generated wording for CI.

Run:

```bash
./scripts/test-sim.sh
```

## 7. Verify with a real local model

After deterministic tests pass:

```bash
./scripts/local-mcp.sh
```

Use realistic prompts and check whether the model chooses the tool and handles
its result. This is an acceptance check, not a replacement for the earlier
layers.

## Completion checklist

- [ ] Domain logic is separate from the MCP adapter.
- [ ] Domain success and failure cases have unit tests.
- [ ] The tool appears in direct MCP discovery.
- [ ] The direct MCP test invokes the real tool.
- [ ] A focused LLMSim scenario covers the intended agent flow.
- [ ] The call journal assertions validate stable behavior.
- [ ] Real-model acceptance has been run for user-facing changes.
