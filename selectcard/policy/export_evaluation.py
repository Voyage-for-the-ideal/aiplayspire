"""Export complete, whole-run evaluation evidence from confirmed JSONL traces."""
import argparse
import json
from pathlib import Path
from .data import read_jsonl


def export_runs(paths):
    rows = []
    for path in paths:
        records = list(read_jsonl(path))
        starts = [r for r in records if r.get("record_type") == "run_start"]
        ends = [r for r in records if r.get("record_type") == "run_end"]
        if len(starts) != 1 or len(ends) != 1 or not ends[0]["outcome"].get("terminal_known"):
            raise ValueError(f"incomplete or ambiguous evaluation trajectory: {path}")
        start, end = starts[0], ends[0]
        runtime = start.get("metadata", {}).get("runtime", {})
        if start.get("attached_floor") != 0:
            raise ValueError("paired evaluation must capture the actual floor-0 start, not attach mid-run")
        if not runtime.get("runtime_fingerprint") or not runtime.get("combat_budget"):
            raise ValueError("missing actual runtime/combat budget manifest")
        rows.append({"character": start["character"], "ascension": start["ascension"], "seed_group": start["seed_group"],
                     "start_fingerprint": start["start_fingerprint"], "runtime_fingerprint": runtime["runtime_fingerprint"],
                     "combat_budget": runtime["combat_budget"], **end["outcome"]})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = export_runs(sorted(args.trace_dir.glob("*.jsonl")))
    if not rows:
        parser.error("no evaluation runs")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(rows, handle, indent=2)


if __name__ == "__main__":
    main()
