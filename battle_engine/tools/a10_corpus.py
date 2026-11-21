"""Inventory actual vanilla SaveState captures and freeze disjoint A10 cohorts."""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path

from battle_engine.tools.balanced_ab import ROLES, ROOMS, case_data, digest, manifest_cases, write


def inventory(roots, excluded_seeds=()):
    # Multiple battle frames or encounters in the same role/room/run may not
    # inflate a stratum. Use the first observed encounter on that seed.
    groups = defaultdict(dict)
    for root in roots:
        paths = [root] if root.is_file() else root.rglob("start.txt")
        for path in sorted(paths):
            try:
                role, room, seed = case_data(path)
            except (KeyError, ValueError, OSError, TypeError):
                continue
            if seed in excluded_seeds:
                continue
            groups[role, room].setdefault(seed, path.resolve())
    return groups


def coverage(groups):
    return {role: {room: len(groups[role, room]) for room in ROOMS} for role in ROLES}


def seed_order(seed):
    return hashlib.sha256(f"a10-balanced-v1:{seed}".encode()).digest()


def freeze(groups, holdout=20):
    if type(holdout) is not int or holdout<10: raise ValueError("Need at least 10 holdouts per cell")
    keys = [(role, room) for role in ROLES for room in ROOMS]
    counts = {key:len(groups[key]) for key in keys}
    deficits = {f"{r}/{m}":3+holdout-counts[r,m] for r,m in keys if counts[r,m]<3+holdout}
    if deficits:
        raise ValueError(f"Insufficient unique seeds: {deficits}")
    memberships = defaultdict(set)
    for key in keys:
        for seed in groups[key]:
            memberships[seed].add(key)
    pilot = set()
    needed = dict.fromkeys(keys, 3)
    while max(needed.values())>0:
        available = [s for s, members in memberships.items() if s not in pilot
                     and all(counts[k]>holdout for k in members)
                     and any(needed[k]>0 for k in members)]
        if not available:
            raise ValueError("Need more disjoint seeds to construct the two cohorts")
        seed = min(available, key=lambda s:(
            -sum(needed[k]>0 for k in memberships[s]),
            -sum(needed[k]/counts[k] for k in memberships[s]), seed_order(s)))
        pilot.add(seed)
        for k in memberships[seed]:
            counts[k] -= 1
            needed[k] = max(0, needed[k]-1)
    rows=[]
    for role,room in keys:
        source=groups[role,room]
        for label,limit in (("pilot",3),("holdout",holdout)):
            seeds=sorted((s for s in source if (s in pilot)==(label=="pilot")), key=seed_order)[:limit]
            for seed in seeds:
                path=source[seed]
                rows.append(dict(path=str(path),sha256=digest(path),role=role,room=room,seed=seed,split=label))
    return {"holdout_per_cell":holdout,"cases":rows}


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="+", type=Path, help="Vanilla startstates directories or individual SaveStates")
    parser.add_argument("--holdout", type=int, default=20, help="Held-out seeds per cell, at least 10")
    parser.add_argument("--freeze", type=Path, help="Write a new formal manifest if coverage permits")
    parser.add_argument("--exclude-manifest", type=Path, action="append", default=[],
                        help="Discard seed groups already used to debug or tune the candidate")
    args=parser.parse_args(argv)
    excluded={row["seed"] for path in args.exclude_manifest
              for row in json.loads(path.read_text(encoding="utf-8-sig"))["cases"]}
    groups=inventory(args.roots,excluded)
    counts=coverage(groups)
    print(json.dumps({"unique_seeds":counts,
                      "missing_to_target":{r:{m:max(0,3+args.holdout-n) for m,n in rooms.items()}
                                       for r,rooms in counts.items()}}, indent=2))
    if args.freeze:
        manifest=freeze(groups,args.holdout)
        write(args.freeze,manifest)
        manifest_cases(args.freeze,"formal")
    return 0


if __name__=="__main__":
    raise SystemExit(main())
