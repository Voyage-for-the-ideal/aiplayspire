from pathlib import Path
import unittest
from unittest.mock import Mock, patch

from sts_ai_framework.models import ActionType, GameAction
from sts_ai_framework.policy_player import PolicyPlayer, main
from sts_ai_framework.policy_runtime import PolicyAgent
from sts_ai_framework.tests.test_policy_trace import state


def reward(screen="CARD_REWARD", **updates):
    values = dict(screen_type=screen, choice_list=["Bash"], can_cancel=True,
                  card_reward_cards=[dict(index=0, uuid="a", id="Bash", name="Bash", cost=2, cost_for_turn=2)])
    values.update(updates)
    return state(**values)


class Client:
    def __init__(self, current):
        self.current = current
        self.actions = []
    def get_state(self):
        return self.current
    def submit_action(self, action):
        self.actions.append(action)
        return True, {}, None


class PolicyPlayerTests(unittest.TestCase):
    def setUp(self):
        self.policy = Mock()
        self.policy.artifact = {"supported_decisions": ["CARD_REWARD", "BOSS_REWARD"]}
        self.policy.last_comparison = {"selected": "test"}
        self.current = reward()
        self.client = Client(self.current)
        self.messages = []
        self.player = PolicyPlayer(self.client, self.policy, pending_timeout=1, output=self.messages.append)

    def test_selection_skip_and_boss_submitted(self):
        for screen, action in [("CARD_REWARD", GameAction(type=ActionType.CHOOSE, choice_index=0)),
                               ("CARD_REWARD", GameAction(type=ActionType.CANCEL)),
                               ("BOSS_REWARD", GameAction(type=ActionType.CHOOSE, choice_index=0))]:
            with self.subTest(screen=screen, action=action):
                current = reward(screen)
                client = Client(current)
                self.policy._score.return_value = (action, [])
                player = PolicyPlayer(client, self.policy, output=self.messages.append)
                player.step(current, 0)
                self.assertEqual(client.actions, [action])

    def test_manual_combat_and_resume(self):
        self.policy._score.return_value = (GameAction(type=ActionType.CANCEL), [])
        self.player.step(state(screen_type="MAP"), 0)
        self.player.step(reward(room_phase="COMBAT"), 1)
        self.assertEqual(self.client.actions, [])
        self.policy._score.assert_not_called()
        self.client.current = self.current
        self.player.step(self.current, 2)
        self.assertEqual(len(self.client.actions), 1)

    def test_unconfirmed_action_not_repeated_and_manual_transition(self):
        self.policy._score.return_value = (GameAction(type=ActionType.CHOOSE, choice_index=0), [])
        self.player.step(self.current, 0)
        self.player.step(self.current, 2)
        self.player.step(self.current, 4)
        self.assertEqual(len(self.client.actions), 1)
        self.assertTrue(any("尚未确认" in m for m in self.messages))
        changed = state(screen_type="MAP")
        self.player.step(changed, 5)
        self.client.current = reward(screen="BOSS_REWARD")
        self.player.step(self.client.current, 6)
        self.assertEqual(len(self.client.actions), 2)

    def test_failure_incomplete_and_stale_state_wait(self):
        for outcome in (None, ValueError("unseen candidate entity")):
            with self.subTest(outcome=outcome):
                policy = Mock(artifact=self.policy.artifact, last_comparison=None)
                if isinstance(outcome, Exception):
                    policy._score.side_effect = outcome
                else:
                    policy._score.return_value = outcome
                player = PolicyPlayer(self.client, policy, output=self.messages.append)
                player.step(self.current, 0)
                player.step(self.current, 1)
                self.assertEqual(policy._score.call_count, 1)
        self.assertTrue(any("unseen candidate" in m for m in self.messages))
        self.policy._score.return_value = (GameAction(type=ActionType.CANCEL), [])
        self.client.current = state(screen_type="MAP")
        self.player.step(self.current, 0)
        self.assertEqual(self.client.actions, [])

    def test_load_failure_exits_before_connecting(self):
        with patch("sts_ai_framework.policy_player.PolicyAgent", side_effect=ValueError("bad checkpoint")), \
             patch("sts_ai_framework.policy_player.GameClient") as client:
            self.assertEqual(main(["--policy-checkpoint", str(Path("missing.pt"))]), 1)
            client.assert_not_called()

    def test_real_adapter_rejects_unknown_entity(self):
        policy = object.__new__(PolicyAgent)
        policy.artifact = {"supported_decisions": ["CARD_REWARD"], "input_profile": "run-context-v1", "vocabulary": {}}
        with self.assertRaisesRegex(ValueError, "unseen candidate"):
            policy._score(self.current)

    def test_real_adapter_scores_reward_candidates(self):
        import torch
        from selectcard.policy.profiles import RUN_PROFILE

        policy = object.__new__(PolicyAgent)
        policy.artifact = {"supported_decisions": ["CARD_REWARD", "BOSS_REWARD"],
                           "input_profile": RUN_PROFILE,
                           "vocabulary": {"card:Bash": 0, "relic:Anchor": 1}}
        cases = [(reward(), [4.0, 0.0], ActionType.CHOOSE),
                 (reward(), [0.0, 4.0], ActionType.CANCEL),
                 (reward("BOSS_REWARD", choice_list=["Anchor"]), [4.0, 0.0], ActionType.CHOOSE)]
        with patch("selectcard.policy.encoding.encode_batch", return_value={}), \
             patch("selectcard.policy.profiles.project_example", side_effect=lambda row, profile: row):
            for current, logits, expected in cases:
                with self.subTest(screen=current.screen_type, expected=expected):
                    policy.model = Mock(return_value=torch.tensor([logits]))
                    action, candidates = policy._score(current)
                    self.assertEqual(len(candidates), 2)
                    self.assertEqual(action.type, expected)

    def test_forced_boss_choice_does_not_require_a_skip(self):
        import torch
        from selectcard.policy.profiles import RUN_PROFILE

        policy = object.__new__(PolicyAgent)
        policy.artifact = {"supported_decisions": ["CARD_REWARD", "BOSS_REWARD"],
                           "input_profile": RUN_PROFILE,
                           "vocabulary": {"relic:Anchor": 0, "relic:Burning Blood": 1}}
        policy.model = Mock(return_value=torch.tensor([[0.0, 4.0]]))
        current = reward("BOSS_REWARD", choice_list=["Anchor", "Burning Blood"], can_cancel=False)
        with patch("selectcard.policy.encoding.encode_batch", return_value={}), \
             patch("selectcard.policy.profiles.project_example", side_effect=lambda row, profile: row):
            action, candidates = policy._score(current)
        self.assertEqual(action.type, ActionType.CHOOSE)
        self.assertEqual(action.choice_index, 1)
        self.assertEqual([c["kind"] for c in candidates], ["take_relic", "take_relic"])

    def test_card_reward_without_visible_skip_waits(self):
        policy = object.__new__(PolicyAgent)
        policy.artifact = {"supported_decisions": ["CARD_REWARD"], "input_profile": "run-context-v1", "vocabulary": {}}
        self.assertIsNone(policy._score(reward(can_cancel=False)))


if __name__ == "__main__":
    unittest.main()
