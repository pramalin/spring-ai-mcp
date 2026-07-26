# Testing strategy

## Principle

Do not use a real LLM to prove facts that can be tested deterministically.

A real model should be used to verify model behavior—not as a substitute for
unit, protocol, or orchestration tests.

## Test matrix

| Layer | Primary question | Stable in CI | Uses real MCP server | Uses real LLM |
|---|---|---:|---:|---:|
| Java logic tests | Is the domain behavior correct and safe? | Yes | No | No |
| Direct MCP test | Is the tool correctly exposed and invokable? | Yes | Yes | No |
| LLMSim agent-loop test | Does the expected model/tool orchestration work? | Yes | Yes | No |
| Goose acceptance | Can this model use the tool effectively? | No | Yes | Yes |

## Layer 1: Java logic tests

`WorkspaceFileServiceTests` creates a temporary filesystem and checks counting,
bounded reads, case-insensitive search, and path-traversal rejection.

Run:

```bash
./scripts/test-unit.sh
```

This layer proves:

- the domain result is correct;
- validation and security rules work;
- failures are independent of Spring, MCP, Docker networking, and model output.

It does not prove that the service is exposed correctly through MCP.

## Layer 2: direct MCP protocol test

The Python MCP SDK connects to the running Spring server over Streamable HTTP,
initializes a session, lists tools, calls `workspace_summary`, and asserts the
fixture counts.

Run:

```bash
./scripts/test-mcp.sh
```

This layer proves that:

- the MCP server starts;
- tool annotations and schemas are published;
- Streamable HTTP negotiation works;
- arguments and results serialize correctly;
- the container sees the mounted fixture;
- the real tool can be invoked by an MCP client.

It does not prove an LLM will select the tool.

## Layer 3: deterministic agent-loop test with LLMSim

LLMSim returns an exact OpenAI tool call for `workspace_summary`. The harness
executes that call over MCP, validates the result, returns it as a tool message,
and verifies the scripted final response.

Run:

```bash
./scripts/test-sim.sh
```

This layer proves that:

- the application sends the expected OpenAI request shape;
- a tool call is decoded correctly;
- the requested MCP tool is executed;
- the real result is returned in the next model request;
- the expected number and order of model calls occur;
- the simulator journal contains the expected interaction.

It does not prove that LLMSim understood the prompt. The tool choice is scripted
on purpose.

### What to assert

Prefer assertions on stable behavior:

- tool name;
- arguments;
- call order;
- MCP result values;
- tool-call ID correlation;
- request count;
- success or error outcome;
- script exhaustion;
- relevant headers and streaming mode.

Avoid asserting incidental formatting unless formatting is part of the contract.

## Layer 4: real local-model acceptance

Goose connects Docker Model Runner to the same Spring MCP endpoint. A local
model must decide whether and how to call the filesystem tools.

Run:

```bash
./scripts/local-mcp.sh
```

This layer evaluates:

- natural-language understanding;
- tool selection;
- argument quality;
- recovery from imperfect results;
- usefulness of the final response.

Treat this as acceptance verification rather than a stable automated test.
Results may change with the model, quantization, prompt, context size, and
available compute.

## Recommended development workflow

1. Write or change domain logic.
2. Run the Java tests.
3. Update the MCP adapter and direct MCP test.
4. Add or update one focused LLMSim scenario.
5. Run all deterministic tests in CI.
6. Inspect the LLMSim console when a scenario fails.
7. Run Goose acceptance before a release or after a meaningful tool/prompt
   change.

## Failure localization

Use the lowest failing layer to guide investigation:

```text
unit test
  → domain logic or validation

direct MCP test
  → MCP annotation, schema, serialization, transport, or mount

LLMSim scenario
  → OpenAI request handling, tool-call mapping, agent loop, or call sequence

Goose only
  → model capability, prompt, context, or inference environment
```
