"""Helpers for routing tests with forward-compatible imports."""

from __future__ import annotations

from importlib import import_module
from typing import Any, Dict, Iterable, List

from sts_ai_framework.models import MapEdgeState, MapNodeState


ROUTING_PACKAGE_CANDIDATES = (
    "sts_ai_framework.routing",
    "sts_ai_framework.llm_agent_parts.routing",
)

MODULE_CANDIDATES = {
    "map_graph": (
        "sts_ai_framework.routing.map_graph",
        "sts_ai_framework.llm_agent_parts.routing.map_graph",
    ),
    "context": (
        "sts_ai_framework.routing.context",
        "sts_ai_framework.llm_agent_parts.routing.context",
    ),
    "features": (
        "sts_ai_framework.routing.features",
        "sts_ai_framework.llm_agent_parts.routing.features",
    ),
    "policy": (
        "sts_ai_framework.routing.policy",
        "sts_ai_framework.llm_agent_parts.routing.policy",
    ),
    "evaluator": (
        "sts_ai_framework.routing.evaluator",
        "sts_ai_framework.llm_agent_parts.routing.evaluator",
    ),
    "planner": (
        "sts_ai_framework.routing.planner",
        "sts_ai_framework.llm_agent_parts.routing.planner",
    ),
    "decision": (
        "sts_ai_framework.routing.decision",
        "sts_ai_framework.llm_agent_parts.routing.decision",
    ),
}


def import_first(module_names: Iterable[str]):
    """Import the first available module from ``module_names``."""
    attempts = []
    for module_name in module_names:
        try:
            return import_module(module_name)
        except ModuleNotFoundError as exc:
            attempts.append(f"{module_name}: {exc}")
    raise ModuleNotFoundError(" | ".join(attempts))


def load_symbol(module_key: str, symbol: str):
    """Load a routing symbol from candidate modules with clear diagnostics."""
    module_paths = MODULE_CANDIDATES[module_key]
    errors = []
    for module_name in module_paths:
        try:
            module = import_first((module_name,))
            symbol_value = getattr(module, symbol, None)
            if symbol_value is not None:
                return symbol_value
            errors.append(f"{module_name}: missing {symbol}")
        except ModuleNotFoundError as exc:
            errors.append(f"{module_name}: {exc}")
        except Exception as exc:  # pragma: no cover - surface unexpected import errors
            raise
    raise AssertionError(
        f"Unable to import {module_key}.{symbol}. Checked: " + "; ".join(errors)
    )


def load_root_symbol(symbol: str):
    """Load a symbol exported from routing package roots."""
    errors = []
    for module_name in ROUTING_PACKAGE_CANDIDATES:
        try:
            module = import_first((module_name,))
            if hasattr(module, symbol):
                return getattr(module, symbol)
            errors.append(f"{module_name}: missing {symbol}")
        except ModuleNotFoundError as exc:
            errors.append(f"{module_name}: {exc}")
    raise AssertionError(
        f"Unable to import {symbol} from routing root. Checked: " + "; ".join(errors)
    )


def make_node(
    x: int,
    y: int,
    symbol: str = "?",
    children: List[Dict[str, Any]] | None = None,
):
    edge_models = [MapEdgeState(**edge) for edge in (children or [])]
    return MapNodeState(x=x, y=y, symbol=symbol, children=edge_models)


def extract_path(candidate):
    if isinstance(candidate, dict):
        for key in ("path", "nodes", "route", "sequence"):
            if key in candidate:
                return candidate[key]
    if hasattr(candidate, "path"):
        return candidate.path
    if hasattr(candidate, "nodes"):
        return candidate.nodes
    if hasattr(candidate, "route"):
        return candidate.route
    if isinstance(candidate, (list, tuple)):
        return candidate
    raise AssertionError(f"Unable to extract path from candidate: {type(candidate)!r}")


def extract_choice_index(action):
    if isinstance(action, int):
        return action
    for key in ("choice_index", "index", "selected_index", "action_index"):
        if hasattr(action, key):
            return getattr(action, key)
        if isinstance(action, dict) and key in action:
            return action[key]
    raise AssertionError(f"Could not read choice index from {action!r}")


def coords_of(node):
    return (getattr(node, "x", None), getattr(node, "y", None))

