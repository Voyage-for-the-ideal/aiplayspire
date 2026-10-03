"""Audit signed value deltas for Eggs and keys on fixed state payloads."""

import argparse
import copy
import json
import statistics

try:
    from .inference import STSInferenceEngine
except ImportError:
    from inference import STSInferenceEngine


SAFE_RELICS = ("Molten Egg 2", "Frozen Egg 2", "Toxic Egg 2")
SAFE_KEYS = ("ruby", "emerald", "sapphire")


def _summary(values):
    if not values:
        return {"count": 0}
    return {
        "count": len(values),
        "mean": statistics.fmean(values),
        "median": statistics.median(values),
        "min": min(values),
        "max": max(values),
        "positive": sum(value > 0.0 for value in values),
        "negative": sum(value < 0.0 for value in values),
    }


def audit_states(engine, states):
    deltas = {name: [] for name in (*SAFE_RELICS, *SAFE_KEYS)}
    for state in states:
        base = copy.deepcopy(state)
        base_value = engine.evaluate_state(base)
        relics = list(base.get("relics") or [])
        for relic_id in SAFE_RELICS:
            if relic_id in relics:
                continue
            hypothetical = copy.deepcopy(base)
            hypothetical.setdefault("relics", []).append(relic_id)
            deltas[relic_id].append(engine.evaluate_state(hypothetical) - base_value)
        keys = dict(base.get("keys") or {})
        for key in SAFE_KEYS:
            if keys.get(key):
                continue
            hypothetical = copy.deepcopy(base)
            hypothetical.setdefault("keys", {})[key] = True
            deltas[key].append(engine.evaluate_state(hypothetical) - base_value)
    return {name: _summary(values) for name, values in deltas.items()}


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument(
        "--states-json",
        required=True,
        help="JSON list of value-state payloads, or an object with a states list.",
    )
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    with open(args.states_json, "r", encoding="utf-8") as handle:
        payload = json.load(handle)
    states = payload.get("states") if isinstance(payload, dict) else payload
    if not isinstance(states, list) or not states:
        raise ValueError("states JSON must contain a non-empty list")
    report = audit_states(STSInferenceEngine(args.checkpoint), states)
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
