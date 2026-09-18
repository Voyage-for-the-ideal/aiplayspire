"""Validate seed-based map reconstruction against archived human runs.

Reconstructs each act's map from a run's seed (sts_mapgen) and checks that
``path_per_floor`` is a legal path through it (DP feasibility per row).

Usage (from selectcard/src):
    python validate_map_reconstruction.py --data-dir "../STS Data" \
        --files-per-month 2 --seed 20260912 --out ../../debug/map_reconstruction_report.json

Exit code 0 always; the JSON report and stdout table carry the results.
"""

from __future__ import annotations

import argparse
import gzip
import json
import random
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

import sts_mapgen

STANDARD_FALSE_FLAGS = ("is_daily", "is_trial", "is_endless", "is_beta", "chose_seed")
MAX_FAILURE_EXAMPLES = 40


def run_is_standard(run: dict) -> bool:
    if any(run.get(flag) for flag in STANDARD_FALSE_FLAGS):
        return False
    if run.get("special_seed", 0):
        return False
    seed = run.get("seed_played")
    if seed in (None, ""):
        return False
    try:
        int(seed)
    except (TypeError, ValueError):
        return False
    return True


def split_act_segments(path_per_floor: list) -> tuple[list[tuple[int, list[str]]], int]:
    """Split the flat floor list into per-act symbol segments.

    Returns ((seg_start_floor, symbols) tuples, anomaly_count). Leading None
    entries (inter-act transition floors) are skipped; 'B' closes a segment.
    """
    segments: list[tuple[int, list[str]]] = []
    current: list[str] = []
    current_start = 0
    for index, value in enumerate(path_per_floor):
        if value is None:
            if current:
                segments.append((current_start, current))
                current = []
            continue
        if value == "B":
            current.append(value)
            segments.append((current_start, current))
            current = []
            continue
        if not current:
            current_start = index
        current.append(value)
    if current:
        segments.append((current_start, current))
    return segments, 0


def validate_segment(nodes, symbols: list[str], wing_from_y: int | None = None) -> tuple[bool, int | None, str | None]:
    """DP-check one act segment. Returns (ok, first_bad_y, detail)."""
    if not symbols:
        return True, None, None
    if symbols[-1] == "B":
        symbols = symbols[:-1]
    if not symbols:
        return True, None, None
    if len(symbols) > sts_mapgen.MAP_HEIGHT:
        return False, sts_mapgen.MAP_HEIGHT, f"segment too long: {len(symbols)}"
    ok, bad_y, available = sts_mapgen.path_is_legal(
        nodes, symbols, wing_from_y=wing_from_y, wing_charges=3 if wing_from_y is not None else 0)
    if ok:
        return True, None, None
    wanted = symbols[bad_y]
    return False, bad_y, f"want '{wanted}' at y={bad_y}, reachable: [{available or 'none'}]"


def wing_pickup_y(run: dict, seg_start_floor: int) -> int | None:
    """Segment-local y from which WingedGreaves jumps are available."""
    pickup_floor = None
    for item in run.get("relics_obtained") or []:
        key = str(item.get("key", "")).replace(" ", "").lower() if isinstance(item, dict) else ""
        if key in ("wingedgreaves", "wingboots"):
            floor = item.get("floor")
            if isinstance(floor, (int, float)):
                pickup_floor = int(floor) if pickup_floor is None else min(pickup_floor, int(floor))
    if pickup_floor is None:
        relics = {str(r).replace(" ", "").lower() for r in run.get("relics") or []}
        if relics & {"wingedgreaves", "wingboots"}:
            pickup_floor = 0
    if pickup_floor is None:
        return None
    return max(0, pickup_floor - seg_start_floor)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", default="../STS Data")
    parser.add_argument("--files-per-month", type=int, default=2)
    parser.add_argument("--seed", type=int, default=20260912)
    parser.add_argument("--out", default=None, help="write JSON report here")
    args = parser.parse_args(argv)

    data_dir = Path(args.data_dir)
    month_dirs = sorted(p for p in data_dir.iterdir() if p.is_dir() and p.name.startswith("Monthly"))
    if not month_dirs:
        print(f"no Monthly_* directories under {data_dir}", file=sys.stderr)
        return 1

    rng = random.Random(args.seed)
    stats = {}
    build_stats = defaultdict(lambda: [0, 0])  # build -> [ok, total]
    failures = []
    failure_symbols = Counter()
    failure_versions = Counter()
    anomalies_total = 0
    started = time.time()

    for month_dir in month_dirs:
        files = sorted(month_dir.glob("*.json.gz"))
        picked = rng.sample(files, min(args.files_per_month, len(files))) if files else []
        month_stats = defaultdict(int)
        act_stats = defaultdict(lambda: [0, 0])  # act -> [ok, total]
        for path in picked:
            try:
                with gzip.open(path, "rt", encoding="utf-8") as handle:
                    runs = json.load(handle)
            except (OSError, json.JSONDecodeError) as exc:
                month_stats["file_errors"] += 1
                print(f"  ! failed to read {path.name}: {exc}", file=sys.stderr)
                continue
            for wrapped in runs:
                run = wrapped.get("event") if isinstance(wrapped, dict) else None
                if not isinstance(run, dict):
                    continue
                month_stats["runs_parsed"] += 1
                if not run_is_standard(run):
                    month_stats["runs_nonstandard"] += 1
                    continue
                month_stats["runs_standard"] += 1
                seed = int(run["seed_played"])
                path_symbols = run.get("path_per_floor") or []
                segments, _anomalies = split_act_segments(path_symbols)
                ascension = int(run.get("ascension_level", 0) or 0)
                for act_index, (seg_start, segment) in enumerate(segments):
                    act = act_index + 1
                    if act > 3 or not segment:
                        continue
                    month_stats["segments_checked"] += 1
                    act_stats[act][1] += 1
                    build_stats[str(run.get("build_version"))][1] += 1
                    wing_y = wing_pickup_y(run, seg_start)
                    try:
                        nodes = sts_mapgen.generate_act_map(seed, act, ascension_level=ascension)
                        ok, bad_y, detail = validate_segment(nodes, list(segment), wing_from_y=wing_y)
                    except Exception as exc:  # noqa: BLE001 - report and continue
                        ok, bad_y, detail = False, None, f"exception: {exc!r}"
                    if ok:
                        act_stats[act][0] += 1
                        month_stats["segments_ok"] += 1
                        build_stats[str(run.get("build_version"))][0] += 1
                    else:
                        month_stats["segments_failed"] += 1
                        if len(failures) < MAX_FAILURE_EXAMPLES:
                            failures.append({
                                "month": month_dir.name,
                                "file": path.name,
                                "seed": seed,
                                "act": act,
                                "ascension": ascension,
                                "detail": detail,
                                "wing": wing_y,
                                "segment": "".join(s if s else "_" for s in segment),
                                "build_version": run.get("build_version"),
                            })
                        failure_symbols[f"act{act}:{detail.split(',')[0] if detail else '?'}"] += 1
                        failure_versions[str(run.get("build_version"))] += 1
        stats[month_dir.name] = dict(month_stats)
        stats[month_dir.name]["per_act"] = {f"act{a}": {"ok": v[0], "total": v[1]}
                                            for a, v in sorted(act_stats.items())}

    total_checked = sum(s.get("segments_checked", 0) for s in stats.values())
    total_ok = sum(s.get("segments_ok", 0) for s in stats.values())
    total_runs = sum(s.get("runs_standard", 0) for s in stats.values())

    print(f"\nMap reconstruction validation (files-per-month={args.files_per_month}, "
          f"sample-seed={args.seed})")
    print(f"{'month':<18}{'runs':>7}{'segments':>10}{'ok':>8}{'fail':>6}{'pass%':>8}")
    for month, s in stats.items():
        checked = s.get("segments_checked", 0)
        ok = s.get("segments_ok", 0)
        rate = 100.0 * ok / checked if checked else float("nan")
        print(f"{month:<18}{s.get('runs_standard', 0):>7}{checked:>10}{ok:>8}"
              f"{s.get('segments_failed', 0):>6}{rate:>7.2f}%")
    overall = 100.0 * total_ok / total_checked if total_checked else float("nan")
    print("-" * 57)
    print(f"{'TOTAL':<18}{total_runs:>7}{total_checked:>10}{total_ok:>8}"
          f"{total_checked - total_ok:>6}{overall:>7.2f}%")
    print(f"elapsed: {time.time() - started:.1f}s")

    if failure_symbols:
        print("\nFailure buckets (act:expected-symbol):")
        for key, count in failure_symbols.most_common(12):
            print(f"  {key}: {count}")
        print("\nTop build versions among failures:")
        for version, count in failure_versions.most_common(8):
            print(f"  {version}: {count}")
        print("\nFirst failure examples:")
        for failure in failures[:10]:
            print(f"  [{failure['month']} {failure['file']} act={failure['act']} "
                  f"seed={failure['seed']} asc={failure['ascension']}] {failure['detail']}")
            print(f"    path: {failure['segment']}")

    if args.out:
        out_path = Path(args.out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        report = {
            "config": vars(args),
            "overall": {"segments_checked": total_checked, "segments_ok": total_ok,
                        "standard_runs": total_runs,
                        "pass_rate": overall if total_checked else None},
            "months": stats,
            "failure_buckets": dict(failure_symbols),
            "per_build": {b: {"ok": v[0], "total": v[1]} for b, v in sorted(build_stats.items())},
            "failure_versions": dict(failure_versions),
            "failure_examples": failures,
        }
        out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(f"\nJSON report written to {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
