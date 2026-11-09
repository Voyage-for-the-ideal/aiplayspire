import json
from pathlib import Path
import tempfile
import unittest
from sts_ai_framework.models import GameState, GameAction, ActionType
from sts_ai_framework.policy_runtime import candidates_for, chosen_id, PolicyAgent
from sts_ai_framework.policy_trace import TraceRecorder, TracingGameClient
from selectcard.policy.data import build_examples


def state(**updates):
    values = dict(character="IRONCLAD", ascension_level=20, seed_group="seed-group", act=1, floor=6,
                  player=dict(current_hp=50, max_hp=80, energy=3, gold=50, block=0),
                  room_phase="COMPLETE", screen_type="SHOP_SCREEN", choice_list=["buy", "leave"], can_cancel=True,
                  shop_inventory=[dict(slot_id="colored:0", kind="card", id="Bash", price=40, upgrades=0, sold_out=False, legal=True, choice_index=0),
                                  dict(slot_id="relic:0", kind="relic", id="Anchor", price=150, upgrades=0, sold_out=False, legal=False, choice_index=None)],
                  shop_purge_cost=75, shop_purge_available=False)
    values.update(updates)
    return GameState(**values)


class PolicyTraceTests(unittest.TestCase):
    def test_unaffordable_items_visible_but_illegal(self):
        candidates = candidates_for(state())
        relic = next(c for c in candidates if c.get("entity_id") == "Anchor")
        self.assertFalse(relic["legal"])

    def test_ack_and_retry_are_not_labels_confirmed_purchase_is_one(self):
        with tempfile.TemporaryDirectory() as temp:
            recorder = TraceRecorder(temp)
            before = state()
            recorder.observe(before, 0)
            action = GameAction(type=ActionType.CHOOSE, choice_index=0)
            recorder.begin(before, action, 0)
            recorder.begin(before, action, 1)
            recorder.observe(before, 1)
            self.assertNotIn('"record_type": "decision"', next(Path(temp).glob("*.jsonl")).read_text(encoding="utf-8"))
            after = state(shop_inventory=[], choice_list=["leave"], player=dict(current_hp=50, max_hp=80, energy=3, gold=10, block=0))
            recorder.observe(after, 2)
            recorder.close()
            rows = [json.loads(line) for line in next(Path(temp).glob("*.jsonl")).read_text(encoding="utf-8").splitlines()]
            self.assertEqual(sum(r["record_type"] == "decision" for r in rows), 1)
            examples, _ = build_examples(Path(temp).glob("*.jsonl"))
            self.assertEqual(examples[0]["pre_state"]["gold"], 50)
            self.assertFalse(examples[0]["outcome"]["terminal_known"])

    def test_timeout_and_combat_never_produce_decision(self):
        with tempfile.TemporaryDirectory() as temp:
            recorder = TraceRecorder(temp)
            before = state()
            recorder.observe(before, 0)
            recorder.begin(before, GameAction(type=ActionType.CHOOSE, choice_index=0), 0)
            recorder.observe(before, 16)
            recorder.close()
            rows = [json.loads(line) for line in next(Path(temp).glob("*.jsonl")).read_text(encoding="utf-8").splitlines()]
            self.assertEqual(sum(r["record_type"] == "decision" for r in rows), 0)
        self.assertIsNone(candidates_for(state(room_phase="COMBAT")))

    def test_internal_macro_submission_uses_fresh_pre_state(self):
        class Client:
            current = state()
            def get_state(self):
                return self.current
            def submit_action(self, action):
                return True, {}, None
        with tempfile.TemporaryDirectory() as temp:
            recorder = TraceRecorder(temp)
            original = Client()
            client = TracingGameClient(original, recorder)
            # Simulate an agent doing its own network submission, without the
            # main loop's choose_action/submit instrumentation.
            client.submit_action(GameAction(type=ActionType.CHOOSE, choice_index=0))
            original.current = state(screen_type="MAP", shop_inventory=[], choice_list=[])
            client.get_state()
            recorder.close()
            examples, _ = build_examples(Path(temp).glob("*.jsonl"))
            self.assertEqual(len(examples), 1)
            self.assertEqual(examples[0]["pre_state"]["screen_type"], "SHOP_SCREEN")

    def test_policy_fallback_called_once_and_baseline_exception_propagates(self):
        class Baseline:
            calls = 0
            def choose_action(self, state):
                self.calls += 1
                raise RuntimeError("baseline failure")
        policy = object.__new__(PolicyAgent)
        policy.baseline = Baseline()
        policy.mode = "shadow"
        policy._score = lambda state: None
        with self.assertRaisesRegex(RuntimeError, "baseline failure"):
            policy.choose_action(state())
        self.assertEqual(policy.baseline.calls, 1)

    def test_grid_multiselect_confirm_cancel_and_reward_skip(self):
        card = dict(index=0, choice_index=1, uuid="a", id="Strike_R", name="Strike", cost=1, cost_for_turn=1)
        other = dict(card, index=1, choice_index=2, uuid="b", misc=9)
        before = state(screen_type="GRID", grid_purpose="purge", grid_num_cards=2, grid_selected_count=1,
                       grid_cards=[card, other], grid_selected_cards=[card], can_proceed=True,
                       choice_list=["confirm", "Strike", "Strike"], shop_inventory=[])
        candidates = candidates_for(before)
        by_id = {c["action_id"]: c for c in candidates}
        self.assertEqual(by_id["target:a"]["kind"], "deselect_card")
        self.assertEqual(by_id["target:b"]["kind"], "select_card")
        self.assertEqual(by_id["confirm"]["command"], {"type": "proceed"})
        self.assertEqual(by_id["leave"]["command"], {"type": "cancel"})
        with tempfile.TemporaryDirectory() as temp:
            recorder = TraceRecorder(temp)
            recorder.observe(before, 0)
            recorder.begin(before, GameAction(type=ActionType.CHOOSE, choice_index=2), 0)
            after = before.model_copy(update={"grid_selected_count": 2, "grid_selected_cards": before.grid_cards})
            recorder.observe(after, 1)
            recorder.begin(after, GameAction(type=ActionType.PROCEED), 1)
            recorder.observe(state(screen_type="MAP"), 2)
            recorder.close()
            rows, _ = build_examples(Path(temp).glob("*.jsonl"))
        self.assertEqual([r["chosen_action_id"] for r in rows], ["target:b", "confirm"])
        reward = state(screen_type="CARD_REWARD", card_reward_cards=[card], choice_list=["Strike"])
        self.assertEqual(chosen_id(reward, GameAction(type=ActionType.SKIP), candidates_for(reward)), "leave")


if __name__ == "__main__":
    unittest.main()
