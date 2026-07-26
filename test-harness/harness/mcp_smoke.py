from __future__ import annotations

import asyncio
import json
import os

from .common import assert_workspace_counts, mcp_session, model_dump


async def main() -> None:
    mcp_url = os.getenv("MCP_URL", "http://spring-ai-mcp-server:8080/mcp")

    async with mcp_session(mcp_url) as session:
        listed = await session.list_tools()
        tools = list(listed.tools)
        names = {tool.name for tool in tools}
        required = {
            "workspace_summary",
            "count_files",
            "list_files",
            "find_files",
            "file_metadata",
            "read_text_file",
        }
        missing = required - names
        if missing:
            raise AssertionError(f"Missing MCP tools: {sorted(missing)}")

        result = await session.call_tool("workspace_summary", {})
        assert_workspace_counts(result)

        print("PASS: MCP initialized and exposed all expected tools.")
        print("PASS: workspace_summary returned the expected sample counts.")
        print(json.dumps(model_dump(result), indent=2, default=str))


if __name__ == "__main__":
    asyncio.run(main())
