"""Cross-language menu/command/effect checks only; no executor or HTTP client."""
import argparse
import copy
import json
from pathlib import Path

from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.models import GameAction


def check(source: Path, output: Path):
    fixtures = json.loads(source.read_text(encoding="utf-8"))
    bridge = IroncladInteractionBridge()
    before = bridge.observe(fixtures["event_before"], run_id="java-event", sequence=0)
    plan = bridge.prepare(before, GameAction(type="choose", choice_index=0))
    assert plan.action.native_method == "chooseDialogOption" and plan.action.native_args == (0,)
    assert len(before.state.event.choices) == 3 and before.state.event.choices[1].action_index is None
    after = bridge.observe(fixtures["event_after"], run_id="java-event", sequence=1)
    assert verify_effect(plan, after).status == "APPLIED"
    descriptions = {}
    for name, index, method, args in (("shop", 1, "buyCard", (3,)), ("reward", 0, "collectReward", (0,)),
                                     ("grid", 0, "chooseGridCard", (0,))):
        local = IroncladInteractionBridge()
        frame = local.observe(fixtures[name], run_id="java-" + name, sequence=0)
        command = local.prepare(frame, GameAction(type="choose", choice_index=index))
        assert (command.action.native_method, command.action.native_args) == (method, args)
        descriptions[name] = {"state": frame.state.model_dump(exclude_unset=True),
                              "native_method": method, "native_args": args}
    neow = copy.deepcopy(fixtures["event_before"])
    neow.update(phase="NEOW", floor=0, map=None, firstRoomChosen=False, observedKeys={})
    neow["interaction"]["event"].update(id="Neow", class_name="NeowEvent", phase="3", choices=[{
        "button_index": 0, "action_index": 0, "enabled": True, "label": "reward", **fixtures["neow_reward"]}])
    neow["interaction"]["event_transitions"] = {"0": {"phases": ["99"]}}
    local = IroncladInteractionBridge()
    frame = local.observe(neow, run_id="java-neow", sequence=0)
    command = local.prepare(frame, GameAction(type="choose", choice_index=0))
    next_neow = copy.deepcopy(neow)
    next_neow["interaction"]["event"]["phase"] = "99"
    next_neow["player"]["maxHp"] += 8; next_neow["player"]["hp"] += 8
    assert verify_effect(command, local.observe(next_neow, run_id="java-neow", sequence=1)).status == "APPLIED"
    output.write_text(json.dumps({"status": "passed", "scope": "offline-synthetic-ironclad-interactions",
                                  "event_effect": "APPLIED", "neow_effect": "APPLIED", "menus": descriptions,
                                  "native_commands_executed": 0}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("JAVA_TO_INTERACTION_CONTRACT_OK executed=0")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(); check(args.fixture, args.output)
