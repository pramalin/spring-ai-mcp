from __future__ import annotations

import asyncio
import json
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

import httpx
from mcp import ClientSession

try:
    from mcp.client.streamable_http import streamable_http_client
except ImportError:  # Compatibility with older 1.x SDK releases.
    from mcp.client.streamable_http import streamablehttp_client as streamable_http_client


def env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None:
        return default
    try:
        return int(value)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be an integer, got {value!r}") from exc


async def wait_for_http(url: str, *, timeout_seconds: float = 90.0) -> None:
    deadline = asyncio.get_running_loop().time() + timeout_seconds
    last_error: Exception | None = None
    async with httpx.AsyncClient(timeout=5.0) as client:
        while asyncio.get_running_loop().time() < deadline:
            try:
                response = await client.get(url)
                if response.status_code < 500:
                    return
            except Exception as exc:  # noqa: BLE001 - preserve final connection error.
                last_error = exc
            await asyncio.sleep(1.0)
    raise RuntimeError(f"Timed out waiting for {url}: {last_error}")


@asynccontextmanager
async def mcp_session(url: str) -> AsyncIterator[ClientSession]:
    async with streamable_http_client(url) as transport:
        read_stream, write_stream = transport[0], transport[1]
        async with ClientSession(read_stream, write_stream) as session:
            await session.initialize()
            yield session


def model_dump(value: Any) -> dict[str, Any]:
    if hasattr(value, "model_dump"):
        return value.model_dump(by_alias=True, exclude_none=True)
    if isinstance(value, dict):
        return value
    raise TypeError(f"Cannot serialize object of type {type(value)!r}")


def openai_tools_from_mcp(tools: list[Any]) -> list[dict[str, Any]]:
    converted: list[dict[str, Any]] = []
    for tool in tools:
        raw = model_dump(tool)
        schema = raw.get("inputSchema") or raw.get("input_schema") or {
            "type": "object",
            "properties": {},
        }
        converted.append(
            {
                "type": "function",
                "function": {
                    "name": raw["name"],
                    "description": raw.get("description", ""),
                    "parameters": schema,
                },
            }
        )
    return converted


def recursively_find_key(value: Any, key: str) -> Any | None:
    if isinstance(value, dict):
        if key in value:
            return value[key]
        for child in value.values():
            found = recursively_find_key(child, key)
            if found is not None:
                return found
    elif isinstance(value, list):
        for child in value:
            found = recursively_find_key(child, key)
            if found is not None:
                return found
    elif isinstance(value, str):
        stripped = value.strip()
        if stripped.startswith(("{", "[")):
            try:
                return recursively_find_key(json.loads(stripped), key)
            except json.JSONDecodeError:
                return None
    return None


def assert_workspace_counts(result: Any) -> None:
    raw = model_dump(result)
    expected_files = env_int("EXPECTED_FILE_COUNT", 3)
    expected_directories = env_int("EXPECTED_DIRECTORY_COUNT", 2)

    file_count = recursively_find_key(raw, "fileCount")
    directory_count = recursively_find_key(raw, "directoryCount")

    if file_count != expected_files or directory_count != expected_directories:
        pretty = json.dumps(raw, indent=2, default=str)
        raise AssertionError(
            "Unexpected workspace counts: "
            f"files={file_count!r} (expected {expected_files}), "
            f"directories={directory_count!r} (expected {expected_directories}).\n"
            f"MCP result:\n{pretty}"
        )
