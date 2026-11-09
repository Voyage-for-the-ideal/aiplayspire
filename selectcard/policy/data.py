"""Build a new policy JSONL from confirmed trajectories, never value afterstates."""
import argparse
from collections import Counter
import json
from pathlib import Path
from .contracts import FORMAT, digest, split_for, validate_example


def read_jsonl(path):
    with Path(path).open(encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            if line.strip():
                try:
                    yield json.loads(line)
                except ValueError as exc:
                    raise ValueError(f"{path}:{number}: {exc}") from exc


def build_examples(paths):
    stats, decisions, outcomes, groups, modes, started = Counter(), {}, {}, {}, {}, {}
    for path in paths:
        for record in read_jsonl(path):
            kind = record.get("record_type")
            if kind == "run_start":
                run_id = record["run_id"]
                group = record.get("seed_group") or run_id
                if run_id in groups and groups[run_id] != group:
                    raise ValueError("conflicting run identity")
                groups[run_id] = group
                modes[run_id] = record.get("run_mode")
                started[run_id] = record.get("started_at", 0)
            elif kind == "run_end":
                outcome = record["outcome"]
                previous = outcomes.get(record["run_id"])
                if previous is not None and previous != outcome:
                    raise ValueError("conflicting terminal outcomes")
                outcomes[record["run_id"]] = outcome
            elif kind == "decision":
                if not record.get("confirmed"):
                    stats["unconfirmed"] += 1
                    continue
                key = (record["run_id"], record["decision_id"])
                if key in decisions:
                    if decisions[key] != record:
                        raise ValueError("conflicting duplicate decision")
                    stats["duplicates"] += 1
                    continue
                decisions[key] = record
            elif kind == "coverage_gap":
                stats["unsupported_submissions"] += 1
                stats["unsupported:" + str(record.get("screen"))] += 1
            elif kind == "unconfirmed":
                stats["unconfirmed_submissions"] += 1
    examples = []
    for record in decisions.values():
        run_id = record["run_id"]
        if run_id not in groups:
            raise ValueError("decision without run_start")
        outcome = outcomes.get(run_id, {"terminal_known": False, "victory": None, "heart_victory": None})
        example = {"format": FORMAT, "seed_group": groups[run_id], **{k: record[k] for k in ("run_id", "decision_id", "decision_type", "pre_state", "candidates", "chosen_action_id", "provenance")},
                   "outcome": outcome, "run_mode": modes.get(run_id), "run_started_at": started.get(run_id, 0), "split": split_for(groups[run_id])}
        if "behavior" in record:
            example["behavior"] = record["behavior"]
        validate_example(example)
        examples.append(example)
        stats["accepted"] += 1
        stats[example["split"]] += 1
        if not outcome.get("terminal_known"):
            stats["censored"] += 1
    # Whole-run deduplication: UUIDs and local command indices are transport
    # references, not evidence that an otherwise identical replay is new data.
    by_run = {}
    for row in examples:
        by_run.setdefault(row["run_id"], []).append(row)
    unique, seen_runs = [], set()
    def semantic(candidate):
        return {k: v for k, v in candidate.items() if k not in ("action_id", "command")}
    for run_id, rows in by_run.items():
        observations = []
        for row in rows:
            selected = next(c for c in row["candidates"] if c["action_id"] == row["chosen_action_id"])
            observations.append([row["pre_state"], sorted((semantic(c) for c in row["candidates"]), key=digest), semantic(selected)])
        fingerprint = digest([groups[run_id], rows[0]["outcome"], observations])
        if fingerprint in seen_runs:
            stats["duplicate_runs"] += 1
            continue
        seen_runs.add(fingerprint)
        unique.extend(rows)
    unique.sort(key=lambda row: (row["run_started_at"], row["run_id"], (0, int(row["decision_id"])) if str(row["decision_id"]).isdigit() else (1, str(row["decision_id"]))))
    stats["confirmed_before_run_dedup"] = len(examples)
    stats["accepted"] = len(unique)
    for split in ("train", "val", "test"):
        stats[split] = sum(row["split"] == split for row in unique)
    stats["censored"] = sum(not row["outcome"].get("terminal_known") for row in unique)
    return unique, dict(stats)


def load_examples(path):
    examples = []
    seen = set()
    groups = {}
    for example in read_jsonl(path):
        validate_example(example)
        key = (example["run_id"], example["decision_id"])
        if key in seen:
            raise ValueError("duplicate policy example")
        seen.add(key)
        split = example.get("split")
        if split not in ("train", "val", "test"):
            raise ValueError("missing/invalid split")
        previous = groups.setdefault(example["run_id"], split)
        if previous != split:
            raise ValueError("run crosses splits")
        examples.append(example)
    return examples


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    paths = sorted(args.trace_dir.glob("*.jsonl"))
    if not paths:
        parser.error("no trajectory JSONL files found")
    examples, stats = build_examples(paths)
    args.output_dir.mkdir(parents=True, exist_ok=False)
    with (args.output_dir / "examples.jsonl").open("x", encoding="utf-8") as handle:
        for example in examples:
            handle.write(json.dumps(example, ensure_ascii=False) + "\n")
    manifest = {"format": FORMAT, "stats": stats, "sources": {str(p.resolve()): digest(p.read_text(encoding="utf-8")) for p in paths}}
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(stats, indent=2))


if __name__ == "__main__":
    main()
