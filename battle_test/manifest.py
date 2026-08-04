"""Manifest parsing and response invariants for battle fixtures."""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any


class ManifestError(ValueError):
    pass


@dataclass(frozen=True)
class Fixture:
    id: str
    file: Path
    character: str
    tags: tuple[str, ...]
    max_expansions: int
    timeout_seconds: float
    expected_result: str
    known_failure: str | None = None


def load_manifest(path: Path) -> list[Fixture]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ManifestError("cannot read manifest %s: %s" % (path, exc)) from exc
    if not isinstance(raw, dict) or not isinstance(raw.get("fixtures"), list):
        raise ManifestError("manifest must contain a fixtures array")

    fixtures: list[Fixture] = []
    seen: set[str] = set()
    for item in raw["fixtures"]:
        if not isinstance(item, dict):
            raise ManifestError("every fixture must be an object")
        missing = {"id", "file", "character", "tags", "max_expansions", "timeout_seconds", "expected_result"} - set(item)
        if missing:
            raise ManifestError("fixture missing fields: %s" % ", ".join(sorted(missing)))
        fixture_id = item["id"]
        if not isinstance(fixture_id, str) or not fixture_id or fixture_id in seen:
            raise ManifestError("fixture IDs must be unique non-empty strings")
        seen.add(fixture_id)
        if item["character"] not in {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"}:
            raise ManifestError("%s has unsupported character" % fixture_id)
        if item["expected_result"] not in {"pass", "known_failure"}:
            raise ManifestError("%s has invalid expected_result" % fixture_id)
        known_failure = item.get("known_failure")
        if item["expected_result"] == "known_failure" and not isinstance(known_failure, str):
            raise ManifestError("%s needs known_failure details" % fixture_id)
        if not isinstance(item["max_expansions"], int) or item["max_expansions"] <= 0:
            raise ManifestError("%s max_expansions must be positive" % fixture_id)
        if not isinstance(item["timeout_seconds"], (int, float)) or item["timeout_seconds"] <= 0:
            raise ManifestError("%s timeout_seconds must be positive" % fixture_id)
        fixtures.append(Fixture(
            id=fixture_id,
            file=(path.parent / item["file"]).resolve(),
            character=item["character"],
            tags=tuple(item["tags"]),
            max_expansions=item["max_expansions"],
            timeout_seconds=float(item["timeout_seconds"]),
            expected_result=item["expected_result"],
            known_failure=known_failure,
        ))
    if len(fixtures) != 12:
        raise ManifestError("manifest must contain exactly 12 fixtures")
    return fixtures


def validate_search_response(response: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if response.get("type") != "COMMAND_LIST":
        errors.append("response type must be COMMAND_LIST")
    if not isinstance(response.get("commands"), list):
        errors.append("commands must be an array")
    if response.get("stop_reason") not in {"VICTORY", "TIMEOUT", "EXPANSION_LIMIT", "SEARCH_EXHAUSTED"}:
        errors.append("invalid stop_reason")
    if not isinstance(response.get("battle_complete"), bool):
        errors.append("battle_complete must be a boolean")
    if not isinstance(response.get("should_replan"), bool):
        errors.append("should_replan must be a boolean")
    commands = response.get("commands")
    has_progress = isinstance(commands, list) and any(command is not None for command in commands)
    if response.get("battle_complete") is False:
        if has_progress and response.get("should_replan") is not True:
            errors.append("incomplete response with command progress must request replanning")
        if not has_progress and response.get("should_replan") is True:
            errors.append("incomplete response without command progress must not request replanning")
    if not isinstance(response.get("final_state_key"), str) or len(response["final_state_key"]) != 64:
        errors.append("final_state_key must be a SHA-256 hex digest")
    metrics = response.get("metrics")
    if not isinstance(metrics, dict):
        return errors + ["metrics must be an object"]
    generated = metrics.get("generated_turn_states")
    unique = metrics.get("unique_turn_states")
    duplicate = metrics.get("duplicate_turn_states")
    if not all(isinstance(value, int) and value >= 0 for value in (generated, unique, duplicate)):
        errors.append("turn-state metrics must be non-negative integers")
    elif unique + duplicate != generated:
        errors.append("unique_turn_states + duplicate_turn_states must equal generated_turn_states")
    return errors
