"""Continue real A10 capture and automatically run both local acceptance tiers."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

from battle_engine.tools.a10_corpus import coverage, freeze, inventory
from battle_engine.tools.balanced_ab import ROOMS, ROLES, digest, manifest_cases, write

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = 23
MIN_FREE_BYTES = 10 * 1024 ** 3


def pending_roles(counts):
    return [role for role in ROLES if any(counts[role][room] < REQUIRED for room in ROOMS)]


def snapshot_status(root, status):
    tmp=root/"status.next"
    tmp.write_text(json.dumps(status,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    tmp.replace(root/"status.json")


def read_report(path):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else None


def run_child(root, status, name, module, args, heartbeat=None):
    env=dict(os.environ,PYTHONIOENCODING="utf-8",PYTHONDONTWRITEBYTECODE="1")
    with (root/f"{name}.log").open("x",encoding="utf-8") as log:
        proc=subprocess.Popen([sys.executable,"-u","-m",module,*args],cwd=ROOT,env=env,
                              stdout=log,stderr=subprocess.STDOUT)
        try:
            status["current"]={"name":name,"pid":proc.pid}
            snapshot_status(root,status)
            while True:
                try:
                    code=proc.wait(timeout=60 if heartbeat else None)
                    break
                except subprocess.TimeoutExpired:
                    heartbeat()
        except BaseException:
            # Graceful termination is owned by a10_capture/oracle for normal
            # bounds. Avoid silently launching another child on interruption.
            status["interrupted_child"]=proc.pid
            raise
        finally:
            status.pop("current",None)
    return code


def campaign(args):
    root=args.output.resolve()
    parent=(ROOT/"battle_engine"/"out").resolve()
    if root==parent or not root.is_relative_to(parent):
        raise ValueError("Campaign output must be a new directory under battle_engine/out")
    sources=[p.resolve(strict=True) for p in args.source]
    for name in ("game_dir","worker","checkpoint"):
        setattr(args,name,getattr(args,name).resolve(strict=True))
    identity={key:{ "path":str(path), "sha256":digest(path) }
              for key,path in (("worker",args.worker),("checkpoint",args.checkpoint),
                               ("java_jar",args.game_dir/"mods"/"BattleAiMod.jar"))}
    excluded={r["seed"] for p in args.exclude_manifest
              for r in json.loads(p.read_text(encoding="utf-8-sig"))["cases"]}
    root.mkdir(parents=True)
    status={"stage":"capture","pid":os.getpid(),"identity":identity,"captures":[],
            "started":time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime())}

    def check_inputs():
        if shutil.disk_usage(root).free < MIN_FREE_BYTES:
            raise RuntimeError("Less than 10 GiB free; capture stopped before filling the disk")
        for item in identity.values():
            if digest(Path(item["path"]))!=item["sha256"]:
                raise RuntimeError(f"Campaign input changed: {item['path']}")

    def refresh():
        groups=inventory(sources,excluded)
        status["coverage"]=coverage(groups)
        snapshot_status(root,status)
        return groups

    try:
        groups=refresh()
        stalled=dict.fromkeys(ROLES,0)
        for round_no in range(1,args.rounds+1):
            if not pending_roles(status["coverage"]):
                break
            for role in pending_roles(status["coverage"]):
                check_inputs()
                name=f"capture-{round_no:02d}-{role.lower()}"
                output=root/name
                old=status["coverage"][role]
                sources.append(output/"client"/"startstates")
                code=run_child(root,status,name,"battle_engine.tools.a10_capture",[
                    "--game-dir",str(args.game_dir),"--checkpoint",str(args.checkpoint),
                    "--output",str(output),"--role",role,"--runs",str(args.runs),
                    "--timeout-seconds",str(args.chunk_seconds),
                ],heartbeat=refresh)
                child=read_report(output/"capture.json")
                status["captures"].append({"name":name,"role":role,"exit":code,"result":child})
                groups=refresh()
                if code!=0:
                    raise RuntimeError(f"{name} stopped before its normal limit; see its log")
                shallow=all(status["coverage"][role][r]<=old[r] or old[r]>=REQUIRED for r in ROOMS)
                stalled[role]=stalled[role]+1 if shallow else 0
                if stalled[role]>=3:
                    raise RuntimeError(f"{role}: three normal captures yielded no samples in its missing room types")
        missing=pending_roles(status["coverage"])
        if missing:
            raise RuntimeError(f"Capture window exhausted; still missing strata for {missing}")
        check_inputs()
        manifest=root/"frozen-a10.json"
        write(manifest,freeze(groups))
        manifest_cases(manifest,"formal")
        status["manifest"]={"path":str(manifest),"sha256":digest(manifest)}
        for phase in ("pilot","formal"):
            check_inputs()
            gate_dir=root/phase
            status["stage"]=phase
            code=run_child(root,status,phase,"battle_engine.tools.oracle",[
                "--game-dir",str(args.game_dir),"--worker",str(args.worker),"--manifest",str(manifest),
                "--port","15129","--runtime-dir",str(gate_dir),"--balanced-ab",phase,
            ])
            report_path=gate_dir/"balanced-ab"/"report.json"
            report=read_report(report_path)
            status[f"{phase}_report"]=str(report_path)
            if not report:
                raise RuntimeError(f"{phase} did not produce a report")
            if phase=="pilot" and (code!=0 or any(
                gate["cpp_invalid"] or gate.get("java_invalid",0) for gate in report["gates"].values()
            )):
                raise RuntimeError("Pilot found an invalid real-engine replay. Holdout remains unused.")
            if phase=="formal":
                status["gates"]=report["gates"]
                if code!=0 or not report["passed"]:
                    status["stage"]="formal_not_passed"
                    return 1
        status["stage"]="formal_passed"
        return 0
    except Exception as error:
        status["stage"]="needs_review"
        status["error"]=str(error)
        return 1
    finally:
        snapshot_status(root,status)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("game-dir","worker","checkpoint","output"):
        parser.add_argument("--"+name,required=True,type=Path)
    parser.add_argument("--source",type=Path,action="append",required=True)
    parser.add_argument("--exclude-manifest",type=Path,action="append",default=[])
    parser.add_argument("--runs",type=int,default=6,help="Completed runs before rotating to the next role")
    parser.add_argument("--rounds",type=int,default=12,help="Maximum passes over the four roles")
    parser.add_argument("--chunk-seconds",type=int,default=1800)
    args=parser.parse_args(argv)
    if min(args.runs,args.rounds,args.chunk_seconds)<=0:
        parser.error("Capture limits must be positive")
    return campaign(args)


if __name__=="__main__":
    raise SystemExit(main())
