# GitHub repository presentation

The repository header is the first place many visitors decide whether the
project is relevant. Use metadata that states the testing pattern rather than
the filesystem example.

## Suggested description

```text
Reference project for deterministic testing of Spring AI MCP servers with LLMSim, followed by real-model verification with Goose.
```

A shorter alternative:

```text
Deterministic Spring AI MCP testing with LLMSim and real-model acceptance with Goose.
```

## Suggested topics

```text
spring-ai
spring-boot
model-context-protocol
mcp-server
llm-testing
integration-testing
llmsim
goose
docker-compose
java
```

## Suggested website

Use the LLMSim repository only when you want to emphasize the simulator:

```text
https://github.com/pramalin/llmsim
```

Otherwise, leave the website field empty until the project has dedicated
published documentation.

## Suggested README social summary

```text
Build MCP server tests in layers: unit-test domain logic, verify the real MCP
transport, script exact agent loops with LLMSim, and reserve a local LLM for
final tool-selection acceptance.
```
