"""Freeze a deployed C++ arm and assemble real, reconciled replay evidence."""
import argparse
import hashlib
import json
from pathlib import Path

from .balanced_ab import digest, manifest_cases, write


def freeze(args):
    load = lambda path: json.loads(path.read_text(encoding="utf-8-sig"))
    runtime, budgets = load(args.runtime), load(args.budgets)
    if not isinstance(runtime, dict) or not runtime or not isinstance(budgets, dict):
        raise ValueError("Expected non-empty runtime configuration and room budgets")
    for room in ("MONSTER","ELITE","BOSS"):
        cfg=budgets[room]
        for key in ("timeout_ms","max_expansions"):
            if type(cfg.get(key)) is not int or cfg[key] < (0 if key=="timeout_ms" and args.policy=="legacy" else 1): raise ValueError("Invalid room budget: "+room)
    key=lambda obj: hashlib.sha256(json.dumps(obj,sort_keys=True,separators=(",",":")).encode()).hexdigest()
    identity={"config":runtime,"model_sha256":digest(args.model)}
    return {"runtime_id":key(identity),"runtime":identity,"budget_id":key(budgets),"budgets":budgets,
            "server_worker_sha256":digest(args.worker),"jni_sha256":digest(args.dll),"policy":args.policy,"dedup_mode":args.dedup_mode,
            "tactical_ordering":args.tactical_ordering,"segment_length":args.segment_length}


def assemble(arm, cases, evidence):
    by_hash={row["sha256"]:row for row in cases if row["split"]=="holdout"}
    rows={}
    for row in evidence:
        sha=row["sha256"]
        if sha in rows or sha not in by_hash: raise ValueError("Unexpected or duplicate snapshot: "+sha)
        settings=row["settings"]
        expected=dict(arm["budgets"][by_hash[sha]["room"]],search_policy=arm["policy"],dedup_mode=arm["dedup_mode"],
                      segment_length=arm["segment_length"],tactical_ordering=arm["tactical_ordering"])
        if any(settings.get(k)!=v for k,v in expected.items()): raise ValueError("Replay configuration differs from frozen arm")
        rows[sha]=row
    if set(rows)!=set(by_hash): raise ValueError("Missing real holdout battle evidence")
    builds={r["native_build"] for r in rows.values()}
    if builds!={arm["jni_sha256"]}: raise ValueError("Mixed or missing native build")
    return dict(arm,native_build=builds.pop(),battles=list(rows.values()))


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    sub=p.add_subparsers(dest="command",required=True)
    f=sub.add_parser("freeze")
    for name in ("runtime","budgets","model","worker","dll","output"):
        f.add_argument("--"+name,type=Path,required=True)
    f.add_argument("--policy",choices=("legacy","layered-v1"),required=True)
    f.add_argument("--dedup-mode",choices=("default","new_turn"),required=True)
    f.add_argument("--segment-length",type=int,choices=(1,2,4),required=True)
    f.add_argument("--tactical-ordering",action=argparse.BooleanOptionalAction,required=True)
    a=sub.add_parser("assemble")
    for name in ("manifest","arm","output"):
        a.add_argument("--"+name,type=Path,required=True)
    a.add_argument("evidence",type=Path,nargs="+")
    args=p.parse_args(argv)
    if args.command=="freeze": result=freeze(args)
    else:
        load=lambda path: json.loads(path.read_text(encoding="utf-8-sig"))
        result=assemble(load(args.arm),manifest_cases(args.manifest,"formal"),map(load,args.evidence))
    write(args.output,result)


if __name__=="__main__": main()
