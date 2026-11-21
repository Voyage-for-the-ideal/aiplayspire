"""Paired offline latency measurements. Does not launch Java or the game."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from battle_engine.profiles import PROFILES
from battle_engine.native_api import execute_worker, ROOT
from battle_engine.state import load
from battle_engine.worker import Worker


def summary(rows):
    values = sorted(r["wall_ms"] for r in rows)
    return dict(samples=len(rows), p50_ms=round(statistics.median(values), 3),
                p95_ms=round(values[min(len(values)-1, int(len(values)*.95))], 3), rows=rows)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--snapshot", required=True, type=Path)
    p.add_argument("--baseline", type=Path)
    p.add_argument("--worker", type=Path, default=ROOT / "bin/battle-engine-worker.exe")
    p.add_argument("--profile", choices=PROFILES, default="BALANCED")
    p.add_argument("--repeats", type=int, default=5)
    p.add_argument("--warmup", type=int, default=1)
    p.add_argument("--output", type=Path, default=ROOT / "out/native-benchmark.json")
    args = p.parse_args()
    if args.repeats < 1 or args.warmup < 0:
        p.error("repeats must be positive and warmup nonnegative")
    state = load(args.snapshot)
    nodes, ms = PROFILES[args.profile]
    report = dict(snapshot=str(args.snapshot.resolve()), snapshot_sha256=hashlib.sha256(args.snapshot.read_bytes()).hexdigest(),
                  profile=args.profile, max_expansions=nodes, timeout_ms=ms, real_engine_checked=False,
                  comparison="Latency only: MCTS simulations and turn-search node expansions are different units", groups={})
    groups = [("candidate_cold", args.worker, False), ("candidate_warm", args.worker, True)]
    if args.baseline:
        groups.insert(0, ("baseline_cold", args.baseline, False))
    for name, executable, persistent in groups:
        worker = Worker(executable) if persistent else None
        rows = []
        try:
            for i in range(args.repeats + args.warmup):
                start = time.perf_counter()
                # No session ID: every repeated root really searches, rather
                # than benchmarking a retransmission/cache hit as a new search.
                result = execute_worker(executable, state, nodes, ms, "search", worker,
                                        dict(search_profile=args.profile))
                elapsed = (time.perf_counter() - start) * 1000
                if not result["supported"]:
                    raise RuntimeError(result.get("reason", "unsupported"))
                if i >= args.warmup:
                    rows.append(dict(wall_ms=round(elapsed, 3), metrics=result.get("metrics", {})))
        finally:
            if worker:
                worker.close()
        report["groups"][name] = dict(executable=str(executable.resolve()),
            sha256=hashlib.sha256(executable.read_bytes()).hexdigest(), **summary(rows))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: {q: v[q] for q in ("samples", "p50_ms", "p95_ms")} for k, v in report["groups"].items()}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
