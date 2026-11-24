"""Export confirmed live-learning logs to new format-12 shards and room labels."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path

import pandas as pd

from selectcard.policy.contracts import split_for
from .live_contract import TRACE_SCHEMA, check_public, file_sha256, fingerprint, value_state, value_fingerprint
from .data_contract import (FILTER_VERSION, PREPROCESSING_VERSION, VALUE_TARGET_SCHEMA,
    HAZARD_ENDPOINTS, TARGET_COLUMNS, MASK_COLUMNS, ascension_band)
from .data_pipeline import hazard_targets
from .encoding import aggregate_items
from .map_context import MAP_CONTEXT_VERSION, inference_context
from .outcome_statistics import OutcomeAccumulator

SUPPORTED = {"CARD_REWARD", "SHOP_SCREEN", "REST", "GRID"}


def read_run(path):
    rows = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                # A killed writer may leave one incomplete final record. Preserve
                # the proven prefix as censored; malformed complete lines are errors.
                if (not line.endswith("\n") and not handle.read()
                        and not any(r["record_type"] == "run_end" for r in rows)):
                    break
                raise
            if row.get("schema") != TRACE_SCHEMA or row.get("sequence") != len(rows) + 1:
                raise ValueError("invalid learning schema/sequence: " + str(path))
            rows.append(row)
    if not rows or rows[0]["record_type"] != "run_start":
        raise ValueError("missing run_start: " + str(path))
    if any(r.get("run_id") != rows[0]["run_id"] for r in rows):
        raise ValueError("mixed run identities")
    ends = [r for r in rows if r["record_type"] == "run_end"]
    if len(ends) > 1 or ends and rows[-1] is not ends[0]:
        raise ValueError("invalid run_end ordering")
    if not rows[0].get("seed_group"):
        raise ValueError("missing seed_group")
    return rows


def outcome_for(rows):
    end = rows[-1]
    if end["record_type"] == "run_end":
        result = end["outcome"]
    else:
        floors = [r.get("after", {}).get("floor") for r in rows if isinstance(r.get("after"), dict)]
        floors += [rows[0].get("attached_floor", 0)]
        result = dict(terminal_known=False, terminal_floor=max(f for f in floors if f is not None),
                      heart_victory=None, termination_reason="unclosed_trace")
    known, heart, floor = result.get("terminal_known"), result.get("heart_victory"), result.get("terminal_floor")
    if type(known) is not bool or (known and type(heart) is not bool) or (not known and heart is not None):
        raise ValueError("invalid/censored outcome")
    if isinstance(floor, bool) or not isinstance(floor, (int, float)) or not 0 <= floor <= 57:
        raise ValueError("invalid terminal floor")
    if heart and (not known or floor != 57 or result.get("victory") is not True):
        raise ValueError("inconsistent Heart outcome")
    if known:
        reason, raw = result.get("termination_reason"), result.get("terminal_floor_raw")
        if (reason not in ("victory", "defeat") or type(raw) is not int or not 0 <= raw <= 57
                or result.get("victory") is not (reason == "victory")
                or floor != (57 if heart else raw)
                or heart and (result.get("terminal_act") != 4 or raw < 55)):
            raise ValueError("inconsistent terminal evidence")
    return result


def value_row(start, decision, outcome, state):
    state = value_state(state)
    if state["character"] != start["character"] or state["ascension"] != start["ascension"]:
        raise ValueError("snapshot disagrees with run identity")
    if state["floor"] > outcome["terminal_floor"]:
        raise ValueError("snapshot beyond observed progress")
    if len(aggregate_items(state["deck"], state["relics"])) > 73:
        raise ValueError("item_cap_exceeded")
    terminal = outcome["terminal_floor"]
    known, heart = outcome["terminal_known"], outcome["heart_victory"] is True
    targets, masks = hazard_targets(state["floor"], terminal, known, heart)
    packed = inference_context(state)
    return {**{k: state[k] for k in ("hp", "max_hp", "gold", "floor", "ascension", "character", "visible_boss", "deck", "relics")},
        **packed, **dict(zip(TARGET_COLUMNS, targets)), **dict(zip(MASK_COLUMNS, masks)),
        "run_id": start["run_id"], "seed_group": start["seed_group"], "split": split_for(start["seed_group"]),
        "decision_id": decision["decision_id"], "intent_id": decision["intent_id"],
        "preprocessing_version": PREPROCESSING_VERSION, "filter_version": FILTER_VERSION,
        "map_context_version": MAP_CONTEXT_VERSION, "state_timing": "post_action",
        "ascension_band": ascension_band(state["ascension"]),
        "terminal_known": known, "terminal_floor": terminal, "heart_victory": heart}


def records_for(rows, exclusions):
    start, outcome = rows[0], outcome_for(rows)
    queries, submitted, decisions, rooms = {}, {}, set(), set()
    for row in rows[1:]:
        kind = row["record_type"]
        if kind == "value_query":
            value_state(row["state"])
            if row["query_id"] in queries:
                raise ValueError("duplicate query ID")
            queries[row["query_id"]] = row
        elif kind == "decision_submitted":
            if row["decision_id"] in submitted:
                raise ValueError("duplicate submitted decision")
            submitted[row["decision_id"]] = row
        elif kind == "decision":
            identifier = row["decision_id"]
            if identifier in decisions or identifier not in submitted:
                raise ValueError("duplicate/unsubmitted confirmed decision")
            decisions.add(identifier)
            request = submitted[identifier]
            for field in ("intent_id", "command", "chosen_action_id", "pre_state_hash", "query_ids", "before", "candidates"):
                if row[field] != request[field]:
                    raise ValueError("confirmation disagrees with submitted decision: " + field)
            if fingerprint(row["before"]) != row["pre_state_hash"]:
                raise ValueError("pre-state fingerprint mismatch")
            selected = [c for c in row["candidates"] if c["action_id"] == row["chosen_action_id"] and c.get("legal")]
            if row["decision_type"] not in SUPPORTED or not selected or not row.get("stable") or not row.get("confirmed"):
                exclusions["unsupported_or_unstable_decision"] += 1
                continue
            actual = row["after"].get("value_state")
            if actual is None or not row["matched_query_ids"]:
                exclusions["no_verified_value_query"] += 1
                continue
            for query_id in row["matched_query_ids"]:
                query = queries.get(query_id)
                if query is None or query_id not in row["query_ids"]:
                    raise ValueError("unbound value query")
                owner = submitted.get(row["intent_id"])
                hashes = {row["pre_state_hash"], owner["pre_state_hash"] if owner else None}
                if query["pre_state_hash"] not in hashes or value_fingerprint(query["state"]) != value_fingerprint(actual):
                    raise ValueError("unverified value-state match")
            # A decision emits one observed state even if duplicate scoring queries matched.
            try:
                yield "value", value_row(start, row, outcome, actual)
            except ValueError as error:
                if str(error) != "item_cap_exceeded":
                    raise
                exclusions[str(error)] += 1
        elif kind == "room_transition":
            if row["decision_id"] in rooms:
                raise ValueError("duplicate room transition")
            rooms.add(row["decision_id"])
            request = submitted.get(row["decision_id"])
            if (request is None or request["before"] != row["before"]
                    or request["before"]["screen"] != "MAP" or request["command"].get("type") != "choose"
                    or request["command"].get("choice_index") != row["target"].get("choice_index")):
                raise ValueError("unsubmitted room transition")
            check_public(row["before"])
            if row["status"] not in ("survived", "died") or row["target"]["symbol"] not in ("M", "E"):
                exclusions["unsupported_or_incomplete_room"] += 1
                continue
            before, after = row["before"], row["after"]
            if after["act"] != before["act"] or after["floor"] != before["floor"] + 1:
                raise ValueError("room boundary mismatch")
            died = row["status"] == "died"
            if died and (after["screen"] != "GAME_OVER" or row.get("terminal_reason") != "defeat"):
                raise ValueError("death without terminal observation")
            if not died and after["screen"] != "MAP":
                raise ValueError("survival without room exit")
            yield "room", dict(run_id=start["run_id"], seed_group=start["seed_group"],
                split=split_for(start["seed_group"]), decision_id=row["decision_id"],
                before=before, target=row["target"], died=died,
                net_hp_loss=None if died else before["resources"]["hp"] - after["resources"]["hp"],
                max_hp_delta=None if died else after["resources"]["max_hp"] - before["resources"]["max_hp"],
                regression_valid=not died, combat_end_observed=row.get("combat_end") is not None)
        elif kind == "coverage_gap":
            exclusions["trace:" + row["reason"]] += row.get("dropped", 1)


def export_live(trace_dir, output_dir, *, chunk_size=256):
    if chunk_size <= 0:
        raise ValueError("positive chunk size required")
    paths = sorted(Path(trace_dir).glob("*.jsonl"))
    if not paths:
        raise ValueError("no learning traces")
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=False)
    buffers, chunks = defaultdict(list), Counter()
    counts, exclusions, runs, run_coverage = Counter(), Counter(), {}, Counter()
    valid_by_split, bands = Counter(), Counter()
    target_valid, target_positive = Counter(), Counter()
    outcomes = OutcomeAccumulator()
    sources = []

    def flush(partition):
        if not buffers[partition]:
            return
        frame = pd.DataFrame(buffers[partition])
        frame.to_parquet(output / f"{partition}_chunk_{chunks[partition]:05d}.parquet", index=False)
        if partition == "train_valid":
            outcomes.add(frame)
        chunks[partition] += 1
        buffers[partition] = []

    with (output / "rooms.jsonl").open("x", encoding="utf-8") as room_file:
        for path in paths:
            digest = file_sha256(path)
            sources.append({"path": str(path.resolve()), "sha256": digest})
            rows = read_run(path)
            start = rows[0]
            if start["run_id"] in runs:
                if runs[start["run_id"]] != digest:
                    raise ValueError("conflicting copies of run")
                exclusions["duplicate_run"] += 1
                continue
            runs[start["run_id"]] = digest
            outcome = outcome_for(rows)
            run_coverage[f"{split_for(start['seed_group'])}:{start['character']}:A{start['ascension']}:"
                         + ("heart" if outcome["heart_victory"] else "completed" if outcome["terminal_known"] else "censored")] += 1
            mode = start.get("run_mode")
            flags = ("is_daily", "is_endless", "is_trial", "is_beta", "chose_seed")
            if (not isinstance(mode, dict) or any(mode.get(flag) is not False for flag in flags)
                    or mode.get("special_seed") != 0):
                exclusions["unsupported_or_unknown_run_mode"] += 1
                continue
            for kind, record in records_for(rows, exclusions):
                counts[kind + ":" + record["split"]] += 1
                if kind == "room":
                    room_file.write(json.dumps(record, allow_nan=False) + "\n")
                    continue
                valid = any(record[name] for name in MASK_COLUMNS)
                if valid:
                    valid_by_split[record["split"]] += 1
                    if record["split"] == "train":
                        bands[str(record["ascension_band"])] += 1
                        for target, mask in zip(TARGET_COLUMNS, MASK_COLUMNS):
                            name = target.removeprefix("target_")
                            target_valid[name] += int(record[mask])
                            target_positive[name] += int(record[target] * record[mask])
                partition = record["split"] + ("_valid" if valid else "_censored")
                buffers[partition].append(record)
                if len(buffers[partition]) >= chunk_size:
                    flush(partition)
            if file_sha256(path) != digest:
                raise ValueError("source trace changed during export")
    for partition in list(buffers):
        flush(partition)
    manifest = dict(source_schema=TRACE_SCHEMA, preprocessing_version=PREPROCESSING_VERSION,
        filter_version=FILTER_VERSION, map_context_version=MAP_CONTEXT_VERSION,
        state_timing="post_action", value_target_schema=VALUE_TARGET_SCHEMA,
        hazard_endpoints=list(HAZARD_ENDPOINTS), split_contract="seed-group-80-10-10-v1",
        sources=sources, training_outcome_counts=outcomes.manifest_counts(),
        distributions=dict(valid_samples_by_split=dict(valid_by_split),
            train_valid_samples_by_ascension_band=dict(bands),
            train_valid_samples_by_target=dict(target_valid), train_positive_samples_by_target=dict(target_positive)))
    manifest["files"] = {p.name: file_sha256(p) for p in sorted(output.iterdir()) if p.is_file()}
    coverage = dict(runs=len(runs), samples=dict(counts), exclusions=dict(exclusions),
                    run_coverage=dict(run_coverage), training_outcome_counts=outcomes.manifest_counts())
    for name, content in (("dataset_manifest.json", manifest), ("coverage.json", coverage)):
        (output / name).write_text(json.dumps(content, indent=2, allow_nan=False), encoding="utf-8")
    return coverage


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    print(json.dumps(export_live(args.trace_dir, args.output_dir)))


if __name__ == "__main__":
    main()
