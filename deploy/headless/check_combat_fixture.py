"""Java object facts -> native import/search -> synthetic Strike receipt. No game actions."""
import argparse
import copy
import json
from pathlib import Path

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import HeadlessCombatBridge, verify_combat_effect, MODELLED


def strike_after(raw):
    """Independent fixed Strike effect, not a copy of the native predicted result."""
    after = copy.deepcopy(raw); s = after["combat"]["snapshot"]; p = s["player_state"]
    card = p["hand"].pop(0); damage = card["base_damage"]
    assert card["card_id"] in {"Strike_R", "Strike_G", "Strike_B", "Strike_P"} and damage == 6
    p["energy_panel_total_energy"] -= card["cost_for_turn"]
    p["discard_pile"].append(card)
    s["cards_played_this_turn_backup"].append(copy.deepcopy(card))
    s["cards_played_this_combat"].append({"card_state": copy.deepcopy(card), "card_index": -1})
    s["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"] -= damage
    after["player"]["energy"] -= card["cost_for_turn"]
    after["player"]["discardPile"].append(after["player"]["hand"].pop(0))
    after["monsters"][0]["hp"] -= damage
    after["combat"]["legal_actions"] = [{"type": "END"}]
    return after


def check(fixture, executable, output):
    data = json.loads(fixture.read_text(encoding="utf-8"))
    worker = Worker(executable); rows = []
    try:
        for role, raw in data["roles"].items():
            bridge = HeadlessCombatBridge(); before = bridge.observe(raw, run_id="java-offline-" + role, sequence=0)
            probe = worker.execute({"operation": "rng_probe", "snapshot": before.snapshot}, 1000)
            assert probe["supported"] and probe["rng_restore_mode"] == "exact_state", probe
            order = ("ai", "card_random", "misc", "monster_hp", "potion", "shuffle")
            assert probe["samples"] == [data["rng_golden"][name] for name in order]
            assert probe["exact_rng"] == {name: before.snapshot["_headless_rng"]["streams"][name] for name in MODELLED}
            response = worker.execute({"snapshot": before.snapshot, "session_id": "java-offline-" + role,
                "search_profile": "FAST", "max_expansions": 500, "timeout_ms": 150}, 150)
            plan = bridge.prepare(before, response)
            assert plan.native_method == "playCard" and plan.native_args == (0, 0), response
            observed = bridge.observe(strike_after(raw), run_id=before.run_id, sequence=1)
            fp = worker.execute({"operation": "fingerprint", "snapshot": observed.snapshot}, 1000)
            result = verify_combat_effect(plan, observed, fp)
            if result != "APPLIED":
                output.with_name("native-key-mismatch.json").write_text(json.dumps({"role": role, "response": response, "fingerprint": fp}, indent=2), encoding="utf-8")
            assert result == "APPLIED", (role, result)
            assert not bridge.validate_before(plan, observed)
            tampered = copy.deepcopy(fp); tampered["exact_rng"]["ai"]["state0"] = "0000000000000001"
            assert verify_combat_effect(plan, observed, tampered) == "UNVERIFIED"
            rows.append({"role": role, "fixture_effect": result, "native_method": plan.native_method, "native_args": plan.native_args,
                         "rng_samples_matched": 96, "cache_hit": response["metrics"]["cache_hit"]})
    finally:
        worker.close()
    output.write_text(json.dumps({"status": "passed", "scope": "offline-synthetic-normal-combat", "game_actions_executed": 0,
        "engine_runs_started": 0, "roles": rows, "actual_end_to_end_roles_accepted": 0}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("JAVA_TO_NATIVE_COMBAT_OK synthetic_roles=4 rng_samples=384 game_actions=0")


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--fixture", type=Path, required=True); p.add_argument("--worker", type=Path, required=True); p.add_argument("--output", type=Path, required=True)
    a = p.parse_args(); check(a.fixture, a.worker, a.output)
