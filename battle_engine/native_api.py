"""Offline native worker contract helpers; neither opens a port nor selects a backend."""
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent


def json_text(value):
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"))


def execute_worker(worker, state, expansions, timeout_ms, operation="search", manager=None, metadata=None):
    request = dict(snapshot=state, max_expansions=expansions, timeout_ms=timeout_ms, operation=operation)
    request.update(metadata or {})
    if manager is not None:
        return manager.execute(request, timeout_ms)
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    completed = subprocess.run([str(worker)], input=json_text(request).encode("utf-8"),
                               capture_output=True, timeout=timeout_ms / 1000 + 2,
                               creationflags=flags)
    if completed.returncode:
        raise RuntimeError(f"worker_exit:{completed.returncode} stderr={completed.stderr[-2000:]!r}")
    result = json.loads(completed.stdout)
    if not isinstance(result, dict) or not isinstance(result.get("supported"), bool):
        raise ValueError("invalid_worker_response")
    return result


def validate_response(response):
    if response.get("type") != "COMMAND_LIST" or len(response.get("commands", [])) != 1:
        raise ValueError("worker must return exactly one guarded action")
    envelope = response["commands"][0]
    if not isinstance(envelope.get("expected_before"), dict):
        raise ValueError("missing expected_before")
    command = json.loads(envelope["command"])
    kind = command.get("type")
    required = {"CARD": ("card_index", "monster_index", "display_string"),
                "POTION": ("potion_index", "monster_index"), "END": (),
                "HAND_SELECT": ("card_index",), "GRID_SELECT": ("card_index",),
                "HAND_SELECT_CONFIRM": (), "GRID_SELECT_CONFIRM": (), "CARD_REWARD_SELECT": ("card_index",)}
    if kind not in required or any(k not in command for k in required[kind]):
        raise ValueError("invalid command schema")
    if response.get("should_replan") is not True:
        raise ValueError("single-action response must request replanning")
