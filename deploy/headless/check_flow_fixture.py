"""Java -> Python map/campfire/act/result contracts, with zero command calls."""
import argparse
import copy
import json

from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.models import GameAction
from sts_ai_framework.tests.test_headless_flow import boss, next_act, terminal


def check(source, output):
    fixtures = json.loads(source.read_text(encoding="utf-8"))
    bridge = IroncladInteractionBridge()
    before = bridge.observe(fixtures["map"], run_id="java-flow-map", sequence=0)
    plan = bridge.prepare(before, GameAction(type="choose", choice_index=1))
    assert (plan.action.native_method, plan.action.native_args) == ("chooseMapNode", (3, 1))
    after = copy.deepcopy(fixtures["map"])
    after.update(phase="PLAYER_TURN", floor=2, roomPhase="COMBAT")
    after["map"]["current"] = {"x": 3, "y": 1}
    after["interaction"]["flow"]["path"].append({"x": 3, "y": 1})
    after["player"]["relics"][0]["counter"] = 1
    assert verify_effect(plan, bridge.observe(after, run_id="java-flow-map", sequence=1)).status == "APPLIED"
    bridge = IroncladInteractionBridge()
    before = bridge.observe(fixtures["campfire"], run_id="java-flow-camp", sequence=0)
    plan = bridge.prepare(before, GameAction(type="choose", choice_index=0))
    assert (plan.action.native_method, plan.action.native_args) == ("useCampfireOption", (1,))
    after = copy.deepcopy(fixtures["campfire"]); after.update(phase="PROCEED", roomPhase="COMPLETE")
    after["observedKeys"]["ruby"] = True
    after["interaction"].update(can_proceed=True)
    after["interaction"]["flow"].update(route="map", campfire_history=[{"floor": after["floor"], "key": "RECALL"}])
    assert verify_effect(plan, bridge.observe(after, run_id="java-flow-camp", sequence=1)).status == "APPLIED"
    bridge = IroncladInteractionBridge(); s = boss(treasure=True)
    s["interaction"]["flow"]["route"] = fixtures["routes"]["next_act"]
    before = bridge.observe(s, run_id="java-route", sequence=0)
    plan = bridge.prepare(before, GameAction(type="proceed"))
    assert verify_effect(plan, bridge.observe(next_act(s), run_id="java-route", sequence=1)).status == "APPLIED"
    bridge = IroncladInteractionBridge(); s = terminal("victory", True)
    s["interaction"]["flow"]["terminal"] = fixtures["heart_terminal"]
    before = bridge.observe(s, run_id="java-result", sequence=0)
    receipt = bridge.acknowledge_terminal(bridge.prepare(before, GameAction(type="proceed")), before)
    assert receipt.heart and receipt.outcome == "victory" and receipt.native_floor == 57
    output.write_text(json.dumps({"status": "passed", "scope": "offline-ironclad-flow", "native_actions_executed": 0,
        "map_native_args": [3, 1], "campfire_native_args": [1], "terminal_truth_cases": fixtures["terminal_truth_cases"],
        "result_receipt": receipt.__dict__}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("JAVA_TO_FLOW_CONTRACT_OK executed=0")


if __name__ == "__main__":
    from pathlib import Path
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(); check(args.fixture, args.output)
