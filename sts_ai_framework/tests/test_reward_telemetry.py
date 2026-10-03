import unittest

from sts_ai_framework.models import (
    ActionType,
    GameAction,
    GameState,
    RewardChoice,
)
from sts_ai_framework.reward_telemetry import (
    RewardTransitionTracker,
    classify_reward_transition,
)


def make_state(screen, *, keys=None, rewards=None, relics=None, choices=None, floor=14):
    return GameState(
        player={"current_hp": 50, "max_hp": 80, "block": 0, "energy": 0, "gold": 100},
        floor=floor,
        act=2,
        room_phase="COMPLETE",
        screen_type=screen,
        choice_list=choices or [],
        reward_choices=rewards or [],
        relics=relics or [],
        keys=keys or {},
    )


class RewardTelemetryTests(unittest.TestCase):
    def test_open_chest_waits_for_screen_change(self):
        before = make_state("CHEST", choices=["open"])
        pending = classify_reward_transition(
            before, GameAction(type=ActionType.CHOOSE, choice_index=0), 10.0)

        self.assertEqual("open_chest", pending.event)
        self.assertIsNone(pending.confirm(before.model_copy(deep=True)))

        after = make_state("COMBAT_REWARD", rewards=[
            RewardChoice(choice_index=0, type="RELIC", relic_id="Anchor")
        ])
        event = pending.confirm(after)
        self.assertEqual("open_chest", event["event"])
        self.assertTrue(event["confirmed"])

    def test_skip_chest_is_distinct_from_opening(self):
        before = make_state("CHEST", choices=["open"])
        pending = classify_reward_transition(
            before, GameAction(type=ActionType.PROCEED), 10.0)
        after = make_state("NONE", floor=15)

        event = pending.confirm(after)
        self.assertEqual("skip_chest", event["event"])
        self.assertFalse(event["sapphire_before"])
        self.assertFalse(event["sapphire_after"])

    def test_tiny_chest_generated_chest_uses_normal_open_skip_semantics(self):
        tiny_chest = [{"id": "Tiny Chest", "name": "Tiny Chest"}]
        before = make_state(
            "CHEST", relics=tiny_chest, choices=["open"])

        open_pending = classify_reward_transition(
            before, GameAction(type=ActionType.CHOOSE, choice_index=0), 10.0)
        skip_pending = classify_reward_transition(
            before, GameAction(type=ActionType.PROCEED), 10.0)

        self.assertEqual("open_chest", open_pending.event)
        self.assertEqual("skip_chest", skip_pending.event)
        self.assertEqual(
            "open_chest",
            open_pending.confirm(make_state("COMBAT_REWARD", relics=tiny_chest))["event"],
        )
        self.assertEqual(
            "skip_chest",
            skip_pending.confirm(make_state("NONE", relics=tiny_chest, floor=15))["event"],
        )

    def test_relic_event_uses_structured_reward_id(self):
        before = make_state("COMBAT_REWARD", rewards=[
            RewardChoice(choice_index=3, type="RELIC", relic_id="Molten Egg 2")
        ], choices=["relic"])
        pending = classify_reward_transition(
            before, GameAction(type=ActionType.CHOOSE, choice_index=3), 10.0)

        self.assertEqual("take_relic", pending.event)
        self.assertEqual("Molten Egg 2", pending.relic_id)
        self.assertIsNone(pending.confirm(before.model_copy(deep=True)))

        after = make_state("COMBAT_REWARD", rewards=[], relics=[
            {"id": "Molten Egg 2", "name": "Molten Egg"}
        ])
        event = pending.confirm(after)
        self.assertEqual("Molten Egg 2", event["relic_id"])

    def test_relic_requires_target_relic_count_to_increase(self):
        before = make_state("COMBAT_REWARD", rewards=[
            RewardChoice(choice_index=3, type="RELIC", relic_id="Molten Egg 2")
        ], choices=["relic"])
        pending = classify_reward_transition(
            before, GameAction(type=ActionType.CHOOSE, choice_index=3), 10.0)

        changed_without_relic = make_state(
            "NONE", rewards=[], choices=[], floor=15)
        self.assertIsNone(pending.confirm(changed_without_relic))

        wrong_relic = make_state("NONE", relics=[
            {"id": "Anchor", "name": "Anchor"}
        ], floor=15)
        self.assertIsNone(pending.confirm(wrong_relic))

    def test_sapphire_requires_observed_key_transition(self):
        before = make_state("COMBAT_REWARD", rewards=[
            RewardChoice(choice_index=1, type="SAPPHIRE_KEY", linked_reward_index=0)
        ])
        pending = classify_reward_transition(
            before, GameAction(type=ActionType.CHOOSE, choice_index=1), 10.0)

        changed_screen_without_key = make_state("NONE")
        self.assertIsNone(pending.confirm(changed_screen_without_key))

        after = make_state("COMBAT_REWARD", keys={"sapphire": True})
        event = pending.confirm(after)
        self.assertEqual("take_sapphire_key", event["event"])
        self.assertFalse(event["sapphire_before"])
        self.assertTrue(event["sapphire_after"])

    def test_leave_rewards_records_all_key_bits(self):
        before = make_state(
            "COMBAT_REWARD",
            keys={"ruby": True, "emerald": True, "sapphire": False},
        )
        pending = classify_reward_transition(
            before, GameAction(type=ActionType.PROCEED), 10.0)
        event = pending.confirm(make_state(
            "NONE",
            keys={"ruby": True, "emerald": True, "sapphire": False},
        ))

        self.assertEqual("leave_rewards", event["event"])
        self.assertTrue(event["ruby_before"])
        self.assertTrue(event["emerald_after"])

    def test_unstructured_choice_does_not_guess_a_label(self):
        before = make_state("COMBAT_REWARD", choices=["relic"])
        pending = classify_reward_transition(
            before, GameAction(type=ActionType.CHOOSE, choice_index=0), 10.0)
        self.assertIsNone(pending)

    def test_expired_pending_transition_is_discarded(self):
        tracker = RewardTransitionTracker()
        before = make_state("CHEST", choices=["open"])
        tracker.begin(before, GameAction(type=ActionType.CHOOSE, choice_index=0), 10.0)

        self.assertIsNone(tracker.discard_if_expired(24.9, 15.0))
        self.assertEqual("open_chest", tracker.discard_if_expired(25.0, 15.0))
        self.assertIsNone(tracker.observe(make_state("COMBAT_REWARD")))

    def test_unrelated_action_does_not_erase_pending_transition(self):
        tracker = RewardTransitionTracker()
        before = make_state("CHEST", choices=["open"])
        tracker.begin(before, GameAction(type=ActionType.CHOOSE, choice_index=0), 10.0)

        unrelated = make_state("MAP", choices=["left", "right"])
        tracker.begin(unrelated, GameAction(type=ActionType.CHOOSE, choice_index=0), 11.0)

        event = tracker.observe(make_state("COMBAT_REWARD"))
        self.assertEqual("open_chest", event["event"])


if __name__ == "__main__":
    unittest.main()
