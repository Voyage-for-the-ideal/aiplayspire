"""python -m selectcard.policy.audit --input-dir <archive> --output <new.json>"""
import argparse
from collections import Counter, defaultdict
import gzip
import json
from pathlib import Path
from .contracts import digest
from .reconstruction import recover_historical, validate_fixtures


def iter_runs(directory):
    for path in sorted(Path(directory).rglob("*")):
        if not path.is_file() or not (path.name.endswith((".run", ".json", ".json.gz"))):
            continue
        opener = gzip.open if path.suffix == ".gz" else open
        with opener(path, "rt", encoding="utf-8-sig") as handle:
            value = json.load(handle)
        for entry in value if isinstance(value, list) else [value]:
            run = entry.get("event", entry) if isinstance(entry, dict) else None
            if isinstance(run, dict) and "seed_played" in run and "card_choices" in run:
                yield run


def audit_runs(runs):
    counts, characters, gaps, map_counts = Counter(), Counter(), defaultdict(Counter), Counter()
    seen = set()
    for run in runs:
        identity = digest(run)
        if identity in seen:
            counts["duplicates"] += 1
            continue
        seen.add(identity)
        counts["runs"] += 1
        counts["victories"] += bool(run.get("victory"))
        characters[str(run.get("character_chosen", "UNKNOWN"))] += 1
        for field, decision in (("card_choices", "CARD_REWARD"), ("boss_relics", "BOSS_REWARD"),
                                ("event_choices", "EVENT"), ("campfire_choices", "REST")):
            for item in run.get(field) or []:
                counts[field] += 1
                assessment = recover_historical(run, decision, item.get("floor"))
                gaps[decision][assessment["status"] + ":" + assessment["reason"]] += 1
        for index, room in enumerate(run.get("path_per_floor") or []):
            decision = "SHOP_SCREEN" if room == "$" else "MAP"
            if room is not None:
                assessment = recover_historical(run, decision, index + 1)
                gaps["MAP_PREFIX" if decision == "MAP" else decision][assessment["status"] + ":" + assessment["reason"]] += 1
                if decision == "MAP" and "prefix_only_coordinate_candidates_without_wings" in assessment:
                    map_counts[str(assessment["prefix_only_coordinate_candidates_without_wings"])] += 1
            if room == "$":
                counts["shop_visits"] += 1
        for field in ("items_purchased", "items_purged", "potions_obtained"):
            counts[field] += len(run.get(field) or [])
        assessment = recover_historical(run, "NEOW", 0)
        gaps["NEOW"][assessment["status"] + ":" + assessment["reason"]] += 1
    return {"counts": dict(counts), "characters": dict(characters),
            "recovery": {k: dict(v) for k, v in gaps.items()},
            "map_prefix_coordinate_counts_without_wing_proof": dict(map_counts), "exact_policy_samples": 0,
            "note": "Raw .run audit only. Candidate IDs alone do not certify pre-state or complete legal menus."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path)
    parser.add_argument("--fixtures", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.input_dir and not args.fixtures:
        parser.error("provide --input-dir and/or --fixtures")
    report = {"format": "policy-reconstruction-audit-v1"}
    if args.input_dir:
        if not args.input_dir.is_dir():
            parser.error("input directory does not exist")
        report["historical"] = audit_runs(iter_runs(args.input_dir))
    if args.fixtures:
        manifest_path = args.fixtures.parent / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
        def fixtures(handle):
            for line in handle:
                if line.strip():
                    fixture = json.loads(line)
                    # Bind original engine-exported snapshots to the collector's
                    # actual copied-jar hash, without changing the source JSONL.
                    fixture["before"].setdefault("engine_sha256", manifest.get("hashes", {}).get("desktop-1.0.jar"))
                    yield fixture
        with args.fixtures.open(encoding="utf-8") as handle:
            report["generation"] = validate_fixtures(fixtures(handle))
        report["generation"]["runtime_hashes"] = manifest.get("hashes", {})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    print(json.dumps({k: v for k, v in report.items() if k != "generation"}, ensure_ascii=False, indent=2))
    if "generation" in report:
        print(json.dumps({k: v for k, v in report["generation"].items() if k != "cases"}, indent=2))
        if not report["generation"]["generation_gate_passed"] or report["generation"]["purchase_cases"] and not report["generation"]["purchase_gate_passed"]:
            raise SystemExit(2)


if __name__ == "__main__":
    main()
