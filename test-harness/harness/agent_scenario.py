from __future__ import annotations

import asyncio
import json
import os
from typing import Any

import httpx

from .common import (
    assert_workspace_counts,
    mcp_session,
    model_dump,
    openai_tools_from_mcp,
    wait_for_http,
)


async def post_completion(
    client: httpx.AsyncClient,
    endpoint: str,
    model: str,
    messages: list[dict[str, Any]],
    tools: list[dict[str, Any]],
) -> dict[str, Any]:
    response = await client.post(
        endpoint,
        json={
            "model": model,
            "messages": messages,
            "tools": tools,
            "tool_choice": "auto",
            "stream": False,
        },
    )
    response.raise_for_status()
    return response.json()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


async def main() -> None:
    mcp_url = os.getenv("MCP_URL", "http://spring-ai-mcp-server:8080/mcp")
    base_url = os.getenv("LLMSIM_BASE_URL", "http://llmsim:8089/v1").rstrip("/")
    status_url = os.getenv("LLMSIM_STATUS_URL", "http://llmsim:8089/_llmsim/status")
    calls_url = os.getenv("LLMSIM_CALLS_URL", "http://llmsim:8089/_llmsim/calls")
    reset_url = os.getenv("LLMSIM_RESET_URL", "http://llmsim:8089/_llmsim/reset")
    dashboard_url = os.getenv(
        "LLMSIM_DASHBOARD_URL", "http://llmsim:8089/_llmsim/dashboard"
    )
    model = os.getenv("LLMSIM_MODEL", "gpt-4o-mini")
    completion_url = f"{base_url}/chat/completions"

    await wait_for_http(status_url)

    async with httpx.AsyncClient(timeout=30.0) as client:
        reset_response = await client.post(reset_url)
        reset_response.raise_for_status()

        async with mcp_session(mcp_url) as session:
            listed = await session.list_tools()
            tools = openai_tools_from_mcp(list(listed.tools))
            messages: list[dict[str, Any]] = [
                {
                    "role": "user",
                    "content": "Use the MCP server to summarize the mounted workspace.",
                }
            ]

            first = await post_completion(client, completion_url, model, messages, tools)
            assistant = first["choices"][0]["message"]
            tool_calls = assistant.get("tool_calls") or []
            require(bool(tool_calls), f"LLMSim did not return a tool call: {first}")

            messages.append(assistant)
            called_names: list[str] = []
            for call in tool_calls:
                function = call["function"]
                name = function["name"]
                arguments_raw = function.get("arguments") or "{}"
                arguments = json.loads(arguments_raw)
                called_names.append(name)

                result = await session.call_tool(name, arguments)
                if name == "workspace_summary":
                    assert_workspace_counts(result)

                messages.append(
                    {
                        "role": "tool",
                        "tool_call_id": call["id"],
                        "name": name,
                        "content": json.dumps(model_dump(result), default=str),
                    }
                )

            require(
                called_names == ["workspace_summary"],
                f"Unexpected scripted tool calls: {called_names}",
            )

            second = await post_completion(client, completion_url, model, messages, tools)
            final_text = second["choices"][0]["message"].get("content") or ""
            require(
                "Workspace inspection completed successfully" in final_text,
                f"Unexpected final simulated response: {second}",
            )

        calls_response = await client.get(calls_url)
        calls_response.raise_for_status()
        calls = calls_response.json()
        require(isinstance(calls, list), f"Expected call journal list, got: {calls}")
        require(len(calls) == 2, f"Expected exactly two LLMSim calls, got: {calls}")
        require(
            [call.get("sequence") for call in calls] == [1, 2],
            f"Unexpected journal sequence numbers: {calls}",
        )
        require(
            [call.get("stepIndex") for call in calls] == [0, 1],
            f"Unexpected script step indexes: {calls}",
        )
        require(
            all(call.get("provider") == "openai" for call in calls),
            f"Unexpected provider in journal: {calls}",
        )
        require(
            all(call.get("model") == model for call in calls),
            f"Unexpected model in journal: {calls}",
        )
        require(
            all((call.get("outcome") or {}).get("type") == "responded" for call in calls),
            f"Unexpected LLMSim outcome: {calls}",
        )

        first_body = ((calls[0].get("outcome") or {}).get("body") or {})
        journal_tool_calls = (
            first_body.get("choices", [{}])[0].get("message", {}).get("tool_calls") or []
        )
        require(
            journal_tool_calls
            and journal_tool_calls[0].get("function", {}).get("name")
            == "workspace_summary",
            f"Journal did not capture workspace_summary: {calls[0]}",
        )

        second_messages = (calls[1].get("rawRequest") or {}).get("messages") or []
        require(
            any(
                message.get("role") == "tool"
                and message.get("tool_call_id") == "workspace-summary-1"
                for message in second_messages
            ),
            f"Second request did not carry the MCP tool result: {calls[1]}",
        )

        dashboard_response = await client.get(dashboard_url)
        dashboard_response.raise_for_status()
        dashboard = dashboard_response.json()
        require(
            dashboard.get("journal", {}).get("retainedCalls") == 2,
            f"Unexpected dashboard journal count: {dashboard}",
        )
        require(
            dashboard.get("calls", {}).get("byOutcome", {}).get("responded") == 2,
            f"Unexpected dashboard outcomes: {dashboard}",
        )
        require(
            dashboard.get("calls", {}).get("byProvider", {}).get("openai") == 2,
            f"Unexpected dashboard provider counts: {dashboard}",
        )
        require(
            str(dashboard.get("script", {}).get("name", "")).endswith(
                "WorkspaceSummaryFlow"
            ),
            f"Unexpected active LLMSim script: {dashboard}",
        )
        require(
            dashboard.get("script", {}).get("exhausted") is True,
            f"Expected the exact two-step script to be exhausted: {dashboard}",
        )

    print("PASS: LLMSim returned the deterministic workspace_summary tool call.")
    print("PASS: The harness executed it through Spring's MCP endpoint.")
    print("PASS: The real MCP result was returned to LLMSim for turn two.")
    print("PASS: The captured-call journal contains the expected two-turn flow.")
    print("LLMSim dashboard snapshot:")
    print(json.dumps(dashboard, indent=2, default=str))


if __name__ == "__main__":
    asyncio.run(main())
