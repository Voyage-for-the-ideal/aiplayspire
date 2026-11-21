"""Score held-out real battles against a frozen server C++ baseline (never deploy)."""
import argparse
from collections import defaultdict
import json
import math
from pathlib import Path
import random
import statistics

from .balanced_ab import manifest_cases


def compare(cases, baseline, candidate):
    """Reports contain one row per frozen snapshot; missing/aborted evidence blocks promotion."""
    keys = {c["sha256"] for c in cases}
    def index(report):
        if not report.get("native_build") or not report.get("budget_id") or not report.get("runtime_id"):
            raise ValueError("Missing native build/budget identity")
        rows = report["battles"]
        result = {r["sha256"]: r for r in rows}
        if len(rows) != len(result) or set(result) != keys: raise ValueError("Unpaired or duplicate battles")
        return result
    a, b = index(baseline), index(candidate)
    if baseline["budget_id"] != candidate["budget_id"] or baseline["runtime_id"] != candidate["runtime_id"]: raise ValueError("Different search budget or runtime")
    roles = defaultdict(lambda: defaultdict(list))
    hp, potions, invalid = [], [], []
    for case in cases:
        sha = case["sha256"]; left, right = a[sha], b[sha]
        if any(r.get("outcome") not in ("win","loss") or r.get("real_engine_checked") is not True for r in (left,right)):
            invalid.append(sha); continue
        roles[case["role"]][case["seed"]].append(int(right["outcome"]=="win") - int(left["outcome"]=="win"))
        if left["outcome"] == right["outcome"] == "win":
            for name, values in (("hp_lost",hp),("potions_used",potions)):
                if not all(type(r.get(name)) in (float,int) and math.isfinite(r[name]) for r in (left,right)): raise ValueError("Invalid resource measure")
                values.append(right[name]-left[name])
    report = dict(pairs=len(keys), invalid=invalid, quality_passed=False, common_wins=len(hp))
    if invalid or not roles or not hp: return report
    clusters = [[(sum(rows),len(rows)) for rows in seeds.values()] for seeds in roles.values()]
    def mean(group):
        return sum(x for x,n in group)/sum(n for x,n in group)
    delta = statistics.mean(mean(group) for group in clusters)
    rng = random.Random(42)
    samples = sorted(statistics.mean(mean(rng.choices(group,k=len(group))) for group in clusters)
                     for _ in range(5000))
    low,high = samples[124],samples[4874]
    # A degenerate paired bootstrap has zero width when every pair agrees.
    # Bound the probability of a previously unseen adverse discordance as well.
    if all(x==0 for groups in roles.values() for rows in groups.values() for x in rows):
        worst_n=min(len(seeds) for seeds in roles.values())
        # Equal-role estimand. Independent role/seed blocks; use the least-represented
        # role count for each role rather than counting battles on the same run twice.
        n = worst_n*len(roles)
        low,high = -(1-.025**(1/n)),1-.025**(1/n)
    report.update(win_delta=delta, ci95=[low,high], delta_hp_lost=statistics.mean(hp),
                  delta_potions=statistics.mean(potions))
    report["quality_passed"] = delta>=0 and low>=-.02 and sum(hp)<=0 and sum(potions)<=0
    return report


def throughput(batch):
    results=batch["results"]
    keys={(r["role"],r["seed"]) for r in results}
    if batch.get("expected_attempts",len(results))!=len(results): raise ValueError("Batch stopped before completing its schedule")
    if len(keys)!=len(results) or not results: raise ValueError("Duplicate or empty run list")
    elapsed=batch["ended_unix"]-batch["started_unix"]
    if not math.isfinite(elapsed) or elapsed<=0: raise ValueError("Invalid batch wall time")
    complete=[r for r in results if r["status"]=="complete" and not r.get("censored",True)
              and r.get("validation",{}).get("valid") is True and r["validation"].get("status")=="complete"
              and (r.get("terminal") or {}).get("outcome") in ("victory","defeat")]
    wins=sum(r["terminal"]["outcome"]=="victory" for r in complete)
    floors=[r["last_floor"] for r in complete]
    return keys,dict(run_win_rate=wins/len(complete) if complete else 0, mean_terminal_floor=statistics.mean(floors) if floors else 0, unfinished_rate=1-len(complete)/len(results), valid_runs_per_hour=3600*len(complete)/elapsed, aborted=len(results)-len(complete),
                     complete=len(complete),attempts=len(results))


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("manifest","baseline","candidate","output"): p.add_argument("--"+name,required=True,type=Path)
    p.add_argument("--baseline-batch",type=Path); p.add_argument("--candidate-batch",type=Path)
    args=p.parse_args(argv)
    load=lambda path: json.loads(path.read_text(encoding="utf-8-sig"))
    cases=[c for c in manifest_cases(args.manifest,"formal") if c["split"]=="holdout"]
    a,b=load(args.baseline),load(args.candidate)
    report=compare(cases,a,b)
    report.update(baseline_build=a["native_build"],candidate_build=b["native_build"],promotable=False)
    if bool(args.baseline_batch)!=bool(args.candidate_batch): p.error("Provide both batch reports")
    if args.baseline_batch:
        # This ID freezes non-combat model, game/runtime, seed policy, hardware and lane count.
        if not a.get("runtime_id") or a["runtime_id"]!=b.get("runtime_id"): raise ValueError("Unmatched runtime")
        left,right=load(args.baseline_batch),load(args.candidate_batch)
        for batch,arm in ((left,a),(right,b)):
            if batch.get("runtime_id")!=arm["runtime_id"] or batch.get("native_build")!=arm.get("server_worker_sha256",arm["native_build"]):
                raise ValueError("Batch identity differs from battle evidence")
            expected={"budget_id":arm["budget_id"],"model_sha256":arm["runtime"]["model_sha256"],"combat_policy":arm["policy"],
                      "dedup_mode":arm["dedup_mode"],"segment_length":arm["segment_length"],"tactical_ordering":arm["tactical_ordering"]}
            if any(batch.get(k)!=v for k,v in expected.items()): raise ValueError("Batch settings differ from frozen arm")
        ka,ta=throughput(left);kb,tb=throughput(right)
        if ka!=kb: raise ValueError("Unpaired batch seeds")
        report.update(baseline_throughput=ta,candidate_throughput=tb,
            promotable=report["quality_passed"] and tb["aborted"]<=ta["aborted"] and tb["run_win_rate"]>=ta["run_win_rate"] and tb["mean_terminal_floor"]>=ta["mean_terminal_floor"] and tb["valid_runs_per_hour"]>ta["valid_runs_per_hour"])
    with args.output.open("x",encoding="utf-8") as out: json.dump(report,out,indent=2)
    return 0 if report["promotable"] else 1

if __name__=="__main__": raise SystemExit(main())
