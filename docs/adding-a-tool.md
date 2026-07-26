# Adding another MCP tool

1. Put domain logic in a normal Spring service that can be unit tested without MCP.
2. Add a small method to an MCP tool component and annotate it with `@McpTool` and `@McpToolParam`.
3. Add focused JUnit tests for the domain logic.
4. Extend `mcp_smoke.py` so the tool must appear in discovery and is invoked directly.
5. Add or update an LLMSim scripted scenario when the agent loop should exercise the tool.
6. Verify the scenario with LLMSim before trying a real local model.

Avoid putting filesystem, database, or network logic directly in the annotated method. Keeping the adapter thin makes failures easier to locate.
