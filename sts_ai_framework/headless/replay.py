"""Independently recheck recorded transitions; never starts a game or collector."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path

from battle_engine.worker import Worker
from .collection import validate_episode
from .combat import HeadlessCombatBridge, verify_combat_effect, is_native_combat_selection
from .interaction import IroncladInteractionBridge, verify_effect

ROLES = ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")


def command(method, args):
    if method == "endTurn" and not args: return {"type": "END"}
    if method == 'chooseHandCard' and len(args)==1:return {'type':'HAND_SELECT','card_index':args[0]}
    if method == 'chooseGridCard' and len(args)==1:return {'type':'GRID_SELECT','card_index':args[0]}
    if method == 'confirmHandSelect' and not args:return {'type':'HAND_SELECT_CONFIRM'}
    if method in ("playCard", "usePotion") and len(args) == 2:
        return {"type": "CARD" if method == "playCard" else "POTION",
                "card_index" if method == "playCard" else "potion_index": args[0], "monster_index": args[1]}
    raise ValueError("Unknown combat transition")


def verify_replay(path, native, *, require_complete=True):
    checked = validate_episode(path, require_complete=require_complete)
    cpp = Worker(str(native)); combat = HeadlessCombatBridge(); menus = IroncladInteractionBridge(characters=ROLES)
    header = before = transition = None; counts = {"combat": 0, "menu": 0}
    try:
        with gzip.open(path, "rt", encoding="utf-8") as source:
            for line in source:
                row = json.loads(line)["record"]
                if row["type"] == "header": header = row
                elif row["type"] == "transition": transition = row
                elif row["type"] == "observation":
                    if transition is not None:
                        run = header["episode_id"]; method = transition["native_method"]; args = transition["native_args"]
                        receipt = row["state"].get("driver_receipt", {})
                        if (receipt.get("source_state_token"), receipt.get("sequence"), receipt.get("method"), receipt.get("args")) != (before["state_token"], row["sequence"], method, args):
                            raise ValueError("Replay driver binding mismatch")
                        if before["state"]["phase"] == "PLAYER_TURN" or is_native_combat_selection(before['state']):
                            frame = combat.observe(before["state"], run_id=run, sequence=before["sequence"])
                            predicted = cpp.execute({"operation": "predict", "snapshot": frame.snapshot,
                                "command": command(method, args), "timeout_ms": 1000}, 1000)
                            plan = combat.prepare(frame, predicted)
                            if (plan.native_method, list(plan.native_args)) != (method, args): raise ValueError("Replay combat command mismatch")
                            after = combat.observe(row["state"], run_id=run, sequence=row["sequence"])
                            fingerprint = cpp.execute({"operation": "fingerprint", "snapshot": after.snapshot}, 1000) if after.snapshot is not None else {}
                            effect = verify_combat_effect(plan, after, fingerprint); family = "combat"
                        else:
                            frame = menus.observe(before["state"], run_id=run, sequence=before["sequence"])
                            matches = [a for a in frame.actions if a.native_method == method and list(a.native_args) == args]
                            if len(matches) != 1: raise ValueError("Replay action is not uniquely legal")
                            plan = menus.prepare(frame, matches[0].api_action)
                            after = menus.observe_outcome(row["state"], run_id=run, sequence=row["sequence"],combat=combat,
                                import_snapshot=lambda s:cpp.execute({'operation':'fingerprint','snapshot':s},1000))
                            effect = verify_effect(plan, after).status; family = "menu"
                        if effect != "APPLIED": raise ValueError(f"Replay effect failed at sequence {row['sequence']}: {method}: {effect}")
                        counts[family] += 1
                    before = row; transition = None
        if sum(counts.values()) != checked["transitions"]: raise ValueError("Replay transition count mismatch")
    finally:
        cpp.close()
    return {**checked, "replay_verified": True, "checks": counts,
            "episode_sha256": hashlib.sha256(Path(path).read_bytes()).hexdigest(),
            "verifier_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            "native_sha256": hashlib.sha256(Path(native).read_bytes()).hexdigest()}


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--native", type=Path, required=True)
    p.add_argument("--allow-censored", action="store_true")
    p.add_argument("episodes", type=Path, nargs="+")
    args = p.parse_args(argv)
    for path in args.episodes:
        print(json.dumps({"path": str(path), **verify_replay(path, args.native, require_complete=not args.allow_censored)}, ensure_ascii=False), flush=True)


if __name__ == "__main__": main()
