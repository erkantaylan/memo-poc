#!/usr/bin/env python3
"""MCP stdio server exposing Kitaplık device control.

A thin JSON-RPC front end over kctl.py, so the same commands are available
whether driven from a shell or from an MCP client. Standard library only —
no pip install, nothing to keep in sync.

Screenshots come back as real images rather than file paths, so the caller
can actually look at the screen.
"""

from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
import traceback

HERE = os.path.dirname(os.path.abspath(__file__))
KCTL = os.path.join(HERE, "kctl.py")
PROJECT = os.path.dirname(HERE)
DEFAULT_PROTOCOL = "2025-06-18"
SUPPORTED_PROTOCOLS = {"2024-11-05", "2025-03-26", "2025-06-18"}

TOOLS = [
    {
        "name": "kitaplik_status",
        "description": "Device connection, which app is focused, whether Kitaplık is "
                       "running, and whether the library server tunnel is up. Start here.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "kitaplik_ui",
        "description": "List the addressable elements on screen with their test tags, "
                       "visible text and tap coordinates. Use this to discover targets "
                       "before tapping.",
        "inputSchema": {
            "type": "object",
            "properties": {"all": {"type": "boolean", "description": "Include unlabelled nodes"}},
        },
    },
    {
        "name": "kitaplik_screenshot",
        "description": "Screenshot the device and return it as an image. Refuses unless "
                       "Kitaplık is the focused app, so it can never capture the user's "
                       "other apps.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "kitaplik_tap",
        "description": "Tap an element matched by test tag substring or visible text "
                       "(e.g. 'base-pdf', 'filter:epub', 'search').",
        "inputSchema": {
            "type": "object",
            "properties": {"target": {"type": "string"}},
            "required": ["target"],
        },
    },
    {
        "name": "kitaplik_long_press",
        "description": "Long press an element. On a library row this deletes the local copy.",
        "inputSchema": {
            "type": "object",
            "properties": {"target": {"type": "string"}},
            "required": ["target"],
        },
    },
    {
        "name": "kitaplik_type",
        "description": "Type text into the focused field. Tap 'search' first.",
        "inputSchema": {
            "type": "object",
            "properties": {"text": {"type": "string"}},
            "required": ["text"],
        },
    },
    {
        "name": "kitaplik_key",
        "description": "Send a key event, e.g. BACK, ENTER, DEL, HOME.",
        "inputSchema": {
            "type": "object",
            "properties": {"keycode": {"type": "string"}},
            "required": ["keycode"],
        },
    },
    {
        "name": "kitaplik_scroll",
        "description": "Scroll the list up or down.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "direction": {"type": "string", "enum": ["up", "down"]},
                "times": {"type": "integer", "default": 1},
            },
            "required": ["direction"],
        },
    },
    {
        "name": "kitaplik_lifecycle",
        "description": "Launch, stop or restart the app.",
        "inputSchema": {
            "type": "object",
            "properties": {"action": {"type": "string", "enum": ["launch", "stop", "restart"]}},
            "required": ["action"],
        },
    },
    {
        "name": "kitaplik_logs",
        "description": "Recent logcat lines for the app, optionally filtered by regex.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "pattern": {"type": "string"},
                "lines": {"type": "integer", "default": 200},
            },
        },
    },
    {
        "name": "kitaplik_files",
        "description": "List what the app has downloaded to internal storage.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "kitaplik_build",
        "description": "Rebuild, install and relaunch the app (runs dev.sh). Use after "
                       "changing Kotlin sources.",
        "inputSchema": {"type": "object", "properties": {}},
    },
]


def run_kctl(*args: str) -> tuple[int, str]:
    proc = subprocess.run(
        [sys.executable, KCTL, *args], capture_output=True, text=True, cwd=PROJECT
    )
    return proc.returncode, (proc.stdout + proc.stderr).strip()


def call_tool(name: str, args: dict) -> dict:
    if name == "kitaplik_screenshot":
        path = "/tmp/kitaplik-mcp-shot.png"
        code, out = run_kctl("shot", path)
        if code != 0:
            return {"content": [{"type": "text", "text": out}], "isError": True}
        with open(path, "rb") as fh:
            data = base64.b64encode(fh.read()).decode("ascii")
        return {
            "content": [
                {"type": "text", "text": out},
                {"type": "image", "data": data, "mimeType": "image/png"},
            ]
        }

    if name == "kitaplik_build":
        proc = subprocess.run(
            ["bash", os.path.join(PROJECT, "dev.sh")],
            capture_output=True, text=True, cwd=PROJECT,
        )
        out = (proc.stdout + proc.stderr).strip()
        # The interesting lines only; a full Gradle log is noise.
        keep = [l for l in out.splitlines()
                if any(k in l for k in ("BUILD", "Installed", "Launched", "==>", "e: ", "error:"))]
        return {
            "content": [{"type": "text", "text": "\n".join(keep) or out[-2000:]}],
            "isError": proc.returncode != 0,
        }

    argv = {
        "kitaplik_status": ["status"],
        "kitaplik_files": ["files"],
        "kitaplik_ui": ["ui"] + (["--all"] if args.get("all") else []),
        "kitaplik_tap": ["tap", str(args.get("target", ""))],
        "kitaplik_long_press": ["long", str(args.get("target", ""))],
        "kitaplik_type": ["type", str(args.get("text", ""))],
        "kitaplik_key": ["key", str(args.get("keycode", ""))],
        "kitaplik_scroll": ["scroll", str(args.get("direction", "down")),
                            str(args.get("times", 1))],
        "kitaplik_lifecycle": [str(args.get("action", "launch"))],
        "kitaplik_logs": ["logs"] + ([str(args["pattern"])] if args.get("pattern") else [])
                         + ["-n", str(args.get("lines", 200))],
    }.get(name)

    if argv is None:
        return {"content": [{"type": "text", "text": f"Unknown tool: {name}"}], "isError": True}

    code, out = run_kctl(*argv)
    return {"content": [{"type": "text", "text": out or "(no output)"}], "isError": code != 0}


def handle(msg: dict) -> dict | None:
    method = msg.get("method")
    mid = msg.get("id")

    if method == "initialize":
        requested = (msg.get("params") or {}).get("protocolVersion")
        version = requested if requested in SUPPORTED_PROTOCOLS else DEFAULT_PROTOCOL
        return {
            "jsonrpc": "2.0", "id": mid,
            "result": {
                "protocolVersion": version,
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "kitaplik", "version": "1.0.0"},
            },
        }

    if method in ("notifications/initialized", "notifications/cancelled"):
        return None

    if method == "tools/list":
        return {"jsonrpc": "2.0", "id": mid, "result": {"tools": TOOLS}}

    if method == "tools/call":
        params = msg.get("params") or {}
        try:
            result = call_tool(params.get("name", ""), params.get("arguments") or {})
        except Exception:
            result = {
                "content": [{"type": "text", "text": traceback.format_exc()}],
                "isError": True,
            }
        return {"jsonrpc": "2.0", "id": mid, "result": result}

    if mid is None:
        return None
    return {
        "jsonrpc": "2.0", "id": mid,
        "error": {"code": -32601, "message": f"Method not found: {method}"},
    }


def main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            continue
        response = handle(msg)
        if response is not None:
            sys.stdout.write(json.dumps(response) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
