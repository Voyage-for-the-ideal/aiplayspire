"""Merge existing historical shards and confirmed live traces into a new BC dataset.

Neither source is modified. Historical exports must already have the requested
pilot/confirm stage; live traces are sampled once by the same run-hash rule.
"""
import argparse
from collections import Counter
import json
import hashlib
from pathlib import Path
import sqlite3
import uuid

from .contracts import DECISIONS, digest, split_for, validate_example
from .data import build_examples, read_jsonl
from .encoding import state_parts
from .profiles import MIXED_PROFILE, RUN_PROFILE, OPTIONAL_FIELDS, project_example
from .shards import DATA_FORMAT, ShardDataset, check_coverage, file_hash, group_key, verify_dataset
from .training import is_bc_example, pilot_member


def mixed_row(row, historical=False):
    row = dict(row)
    if historical:
        # CommunicationMod publishes Java nameUUIDFromBytes("seed:" + seed).
        # Historical replay uses the decimal seed: normalize before splitting
        # so the same seed can never land in two partitions across sources.
        seed = str(int(row["seed_group"]))
        row["seed_group"] = str(uuid.UUID(bytes=hashlib.md5(("seed:" + seed).encode("utf-8")).digest(), version=3))
        row["split"] = split_for(row["seed_group"])
    state = dict(row["pre_state"])
    state["known"] = dict(state.get("known") or {})
    if historical:
        # The run-context projection intentionally cleared these values. Empty
        # does not prove that the player had no potions, keys, or inventory.
        for field in OPTIONAL_FIELDS:
            state["known"][field] = False
    else:
        # Old trace files predate presence masks. Empty defaults are not proof
        # that the bridge actually supplied the corresponding field.
        for field in OPTIONAL_FIELDS:
            value = state.get(field)
            if field == "keys":
                value = any(v is not None for v in (value or {}).values())
            presence = (state.get("field_presence") or {}).get(field)
            state["known"].setdefault(field, presence if presence is not None else bool(value) if field != "purge_cost" else value is not None)
    row["pre_state"] = state
    row["input_profile"] = MIXED_PROFILE
    if row["decision_type"] == "BOSS_REWARD":
        row["candidates"] = [dict(c, kind="leave") if c["kind"] == "cancel" else dict(c) for c in row["candidates"]]
    row = project_example(row, MIXED_PROFILE)
    validate_example(row)
    state_parts(row["pre_state"])
    return row


def export_mixed(historical_dir, trace_dir, output_dir, *, stage="pilot", shard_size=10000):
    if stage not in ("pilot", "confirm") or shard_size < 1:
        raise ValueError("invalid stage or shard size")
    historical_dir, trace_dir, output_dir = map(lambda p: Path(p).resolve(), (historical_dir, trace_dir, output_dir))
    if output_dir.exists() or any(output_dir == p or p in output_dir.parents for p in (historical_dir, trace_dir)):
        raise ValueError("output must be a new directory outside the sources")
    historical = verify_dataset(historical_dir)
    if historical["stage"] != stage or historical["input_profile"] != RUN_PROFILE:
        raise ValueError("historical stage mismatch")
    if not trace_dir.is_dir():
        raise ValueError("trace directory does not exist")
    traces = sorted(trace_dir.glob("*.jsonl"))
    source_files = {p.name: file_hash(p) for p in traces}
    spec = dict(format=DATA_FORMAT, input_profile=MIXED_PROFILE, stage=stage,
                cohort_fraction=.01 if stage == "pilot" else 1., pilot_sampling="run-hash-once-at-export",
                split="seed-group-80-10-10", shard_size=shard_size,
                historical_fingerprint=historical["fingerprint"], trace_sources=source_files,
                code=file_hash(Path(__file__)))
    output_dir.mkdir(parents=True)
    db = sqlite3.connect(output_dir / "index.sqlite")
    db.executescript("""CREATE TABLE examples(split TEXT,ordinal INTEGER,group_key TEXT,run_id TEXT,character TEXT,task TEXT,seed_group TEXT,shard TEXT,offset INTEGER,length INTEGER,PRIMARY KEY(split,ordinal));
      CREATE INDEX sampling ON examples(split,group_key,run_id,ordinal);
      CREATE TABLE identities(run_id TEXT, decision_id TEXT, PRIMARY KEY(run_id, decision_id));""")
    counts, coverage, reasons, task_counts = Counter(), Counter(), Counter(), Counter()
    shard, shard_no, in_shard, name = None, -1, 0, None

    def add(row, source):
        nonlocal shard, shard_no, in_shard, name
        if not is_bc_example(row):
            reasons[source + ":not_ordinary_completed_victory"] += 1
            return
        try:
            row = mixed_row(row, historical=source == "historical")
        except (ValueError, KeyError, TypeError) as exc:
            reasons[source + ":invalid:" + str(exc)] += 1
            return
        if not db.execute("INSERT OR IGNORE INTO identities VALUES(?,?)", (row["run_id"], str(row["decision_id"]))).rowcount:
            reasons[source + ":duplicate_decision"] += 1
            return
        if shard is None or in_shard >= shard_size:
            if shard:
                shard.close()
            shard_no += 1
            name = f"shard-{shard_no:05d}.jsonl"
            shard = (output_dir / name).open("wb")
            in_shard = 0
        encoded = (json.dumps(row, separators=(",", ":"), ensure_ascii=True) + "\n").encode()
        offset = shard.tell()
        shard.write(encoded)
        split, state = row["split"], row["pre_state"]
        db.execute("INSERT INTO examples VALUES(?,?,?,?,?,?,?,?,?,?)", (split, counts[split], group_key(row), row["run_id"], state["character"], row["decision_type"], row["seed_group"], name, offset, len(encoded)))
        counts[split] += 1
        coverage[f'{split}|{state["character"]}|A{state["ascension"]}|{row["decision_type"]}'] += 1
        task_counts[(split, row["decision_type"])] += 1
        reasons[source + ":accepted"] += 1
        in_shard += 1

    try:
        for split in ("train", "val", "test"):
            dataset = ShardDataset(historical_dir, split)
            try:
                for row in dataset:
                    add(row, "historical")
            finally:
                dataset.close()
        for path in traces:
            # One file is one bounded run, preserving existing trace validation.
            try:
                starts = [r for r in read_jsonl(path) if r.get("record_type") == "run_start"]
                if len(starts) != 1 or not starts[0].get("seed_group"):
                    reasons["trace:missing_run_start_or_seed_group"] += 1
                    continue
                parsed_seed = uuid.UUID(starts[0]["seed_group"])
                if parsed_seed.version != 3 or str(parsed_seed) != starts[0]["seed_group"].lower():
                    raise ValueError("noncanonical live seed group")
                rows, stats = build_examples([path])
                reasons.update({"trace:" + k: v for k, v in stats.items() if k.startswith(("unsupported", "unconfirmed"))})
                for row in rows:
                    if stage == "pilot" and not pilot_member(row["run_id"]):
                        reasons["trace:outside_pilot"] += 1
                        continue
                    add(row, "trace")
            except (ValueError, KeyError, TypeError) as exc:
                reasons["trace:invalid:" + str(exc)] += 1
        db.commit()
    finally:
        if shard:
            shard.close()
        db.close()
    report = dict(samples=sum(counts.values()), splits=dict(counts), coverage=dict(coverage), reasons=dict(reasons),
                  task_counts={split: {task: task_counts[(split, task)] for task in DECISIONS} for split in ("train", "val", "test")},
                  untrained_types=[task for task in DECISIONS if not task_counts[("train", task)]],
                  historical_audit={key: historical["report"].get(key, {}) for key in ("reasons", "prefix_stops", "unknown_context")},
                  promotion="offline BC only; paired real-game evaluation required")
    (output_dir / "coverage.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    files = {p.name: file_hash(p) for p in sorted(output_dir.iterdir()) if p.is_file()}
    source_fingerprint = digest(spec)
    manifest = dict(spec, source_fingerprint=source_fingerprint, fingerprint=digest([source_fingerprint, files]), report=report, files=files)
    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    if stage == "confirm":
        check_coverage(output_dir)
    elif not counts["train"] or not counts["val"]:
        raise ValueError("empty pilot train/val; report saved")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--historical-dir", type=Path, required=True)
    parser.add_argument("--trace-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--stage", choices=("pilot", "confirm"), default="pilot")
    parser.add_argument("--shard-size", type=int, default=10000)
    args = parser.parse_args()
    result = export_mixed(args.historical_dir, args.trace_dir, args.output_dir, stage=args.stage, shard_size=args.shard_size)
    print(json.dumps(result["report"], ensure_ascii=True))


if __name__ == "__main__":
    main()
