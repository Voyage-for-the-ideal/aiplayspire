"""Paired A20H evidence from completed real runs; no proxy-win claims."""
import argparse
from collections import Counter
import json
from pathlib import Path
import random


def compare_runs(baseline, candidate, *, required_per_character=100, seed=42, samples=5000):
    roles = ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")
    def indexed(rows):
        result = {}
        for row in rows:
            key = (row["character"], str(row["seed_group"]))
            if key in result:
                raise ValueError("duplicate evaluation run")
            if row["character"] not in roles or row["ascension"] != 20 or not row.get("terminal_known"):
                raise ValueError("evaluation requires completed A20 runs")
            if not row.get("runtime_fingerprint") or not row.get("combat_budget"):
                raise ValueError("missing actual runtime/combat-budget evidence")
            result[key] = row
        return result
    left, right = indexed(baseline), indexed(candidate)
    if left.keys() != right.keys():
        raise ValueError("paired seed sets differ; incomplete runs are not losses")
    diffs, counts = [], Counter()
    for key, before in left.items():
        after = right[key]
        if any(before.get(k) != after.get(k) for k in ("runtime_fingerprint", "combat_budget", "start_fingerprint")):
            raise ValueError("unpaired runtime, budget, or initial state")
        if not before.get("start_fingerprint"):
            raise ValueError("seed alone does not certify identical starting conditions")
        if before.get("heart_victory") not in (True, False) or after.get("heart_victory") not in (True, False):
            raise ValueError("unknown Heart outcome")
        counts[key[0]] += 1
        diffs.append(int(after["heart_victory"]) - int(before["heart_victory"]))
    if not diffs:
        raise ValueError("no evaluation pairs")
    # Pair and cluster by seed: the same seed used across roles stays together.
    clusters = {}
    for key, delta in zip(left, diffs):
        clusters.setdefault(key[1], []).append(delta)
    rng, groups = random.Random(seed), list(clusters.values())
    boot = []
    for _ in range(samples):
        values = [v for _ in groups for v in rng.choice(groups)]
        boot.append(sum(values) / len(values))
    boot.sort()
    interval = [boot[int(.025 * samples)], boot[min(samples - 1, int(.975 * samples))]]
    complete = all(counts[c] >= required_per_character for c in roles) and len(set(counts.values())) == 1
    per_character = {}
    for character in roles:
        keys = [key for key in left if key[0] == character]
        if keys:
            before = sum(left[k]["heart_victory"] for k in keys) / len(keys)
            after = sum(right[k]["heart_victory"] for k in keys) / len(keys)
            per_character[character] = {"pairs": len(keys), "baseline_heart_rate": before, "candidate_heart_rate": after, "delta": after - before}
    return {"pairs": len(diffs), "counts": dict(counts), "complete": complete, "per_character": per_character,
            "heart_rate_delta": sum(diffs) / len(diffs), "paired_seed_cluster_ci95": interval,
            "promotable": complete and interval[0] > 0,
            "baseline_heart_rate": sum(r["heart_victory"] for r in left.values()) / len(left),
            "candidate_heart_rate": sum(r["heart_victory"] for r in right.values()) / len(right)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = compare_runs(json.loads(args.baseline.read_text(encoding="utf-8")), json.loads(args.candidate.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
