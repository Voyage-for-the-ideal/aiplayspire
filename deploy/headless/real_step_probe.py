"""Bounded real setup + one native-planned combat action per role, not collection."""
import argparse
import json
from pathlib import Path

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import HeadlessCombatBridge, verify_combat_effect


def act(engine, frame, method, args=()):
    reply = engine.execute({"operation": "action", "expected_state_token": frame["state_token"], "method": method, "args": args}, 15000)
    if not reply["supported"]: raise RuntimeError(reply)
    return reply


def setup(engine, role, seed):
    frame = engine.execute({"operation": "state"}, 15000)
    frame = engine.execute({"operation": "new_run", "expected_state_token": frame["state_token"], "character": role, "seed": seed, "ascension": 0}, 15000)
    if not frame["supported"]: raise RuntimeError(frame)
    for _ in range(12):
        state = frame["state"]; phase = state["phase"]
        if phase == "PLAYER_TURN": return frame
        if phase == "NEOW": frame = act(engine, frame, "chooseDialogOption", [0])
        elif phase == "CARD_PICK": frame = act(engine, frame, "chooseRewardCard", [0])
        elif phase == "REWARD":
            rewards = state["interaction"]["rewards"]
            frame = act(engine, frame, "proceed" if not rewards else "openCardReward" if rewards[0]["type"] == "CARD" else "collectReward", [] if not rewards else [0])
        elif phase == "MAP":
            target = state["interaction"]["map_choices"][0]
            frame = act(engine, frame, "chooseMapNode", [target["x"], target["y"]])
        elif phase == "CARD_SELECT":
            selection = state["interaction"]["selection"]
            frame = act(engine, frame, "confirmGrid" if selection["can_confirm"] else "chooseGridCard", [] if selection["can_confirm"] else [0])
        else: raise RuntimeError("Unexpected setup phase: " + phase)
    raise RuntimeError("Setup action limit")


def check(root, launcher, native, force_end=False):
    rows = []
    for n, role in enumerate(("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")):
        engine, cpp = Worker(launcher), Worker(native)
        row = {"role": role, "collection_started": False, "game_combat_actions": 0}
        try:
            before = setup(engine, role, 4040001+n)
            bridge = HeadlessCombatBridge(); run = "real-step-" + role
            frame = bridge.observe(before["state"], run_id=run, sequence=before["sequence"])
            request = {"snapshot": frame.snapshot, "session_id": run, "search_profile": "FAST", "max_expansions": 1000, "timeout_ms": 200}
            if force_end: request.update(operation="predict", command={"type": "END"})
            reply = cpp.execute(request, 200)
            plan = bridge.prepare(frame, reply)
            assert bridge.validate_before(plan, frame)
            after = act(engine, before, plan.native_method, plan.native_args); row["game_combat_actions"] = 1
            observed = bridge.observe(after["state"], run_id=run, sequence=after["sequence"])
            fp = cpp.execute({"operation": "fingerprint", "snapshot": observed.snapshot}, 1000) if observed.snapshot is not None else {}
            row.update(native_plan={"method": plan.native_method, "args": plan.native_args}, effect=verify_combat_effect(plan, observed, fp))
            for label, value in (("before", before), ("after", after), ("prediction", reply), ("fingerprint", fp)):
                (root / f"reports/real-step-{role}-{label}.json").write_text(json.dumps(value, indent=2), encoding="utf-8")
        except Exception as error:
            row["error"] = repr(error)
        finally:
            engine.close(); cpp.close()
        row["run_disposition"] = "bounded_smoke_stopped_not_a_training_episode"
        rows.append(row); print(json.dumps(row), flush=True)
    (root / "reports/real-step-summary.json").write_text(json.dumps({"scope": "real-first-native-action", "roles": rows, "training_collection_started": False}, indent=2), encoding="utf-8")
    return all(r.get("effect") == "APPLIED" for r in rows)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, required=True); p.add_argument("--launcher", type=Path, required=True); p.add_argument("--native", type=Path, required=True)
    p.add_argument("--force-end", action="store_true")
    a = p.parse_args(); raise SystemExit(not check(a.root, a.launcher, a.native, a.force_end))
