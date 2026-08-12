import unittest

from sts_ai_framework.llm_agent_parts.action_mixin import ActionMixin
from sts_ai_framework.llm_agent_parts.choice_mixin import ChoiceMixin
from sts_ai_framework.llm_agent_parts.decision_mixin import DecisionMixin
from sts_ai_framework.models import ActionType, Card, GameState


def card(uuid, upgrades=0, can_upgrade=True, name="Clothesline"):
    return Card(index=0, uuid=uuid, id="Clothesline", name=name,
                cost=2, cost_for_turn=2, upgrades=upgrades,
                can_upgrade=can_upgrade)


def state(screen="REST", deck=None, choices=None, grid_cards=None,
          reward_cards=None, selected=0):
    return GameState(
        player={"current_hp": 70, "max_hp": 80, "block": 0, "energy": 0, "gold": 99},
        deck=deck or [], hand=[], monsters=[], floor=8, act=1,
        room_phase="INCOMPLETE", screen_type=screen,
        draw_pile_size=0, discard_pile_size=0, exhaust_pile_size=0,
        choice_list=choices or [], grid_cards=grid_cards or [],
        reward_cards=reward_cards or [], grid_purpose="upgrade",
        grid_num_cards=1, grid_selected_count=selected,
    )


class FakeValueEngine:
    def __init__(self):
        self.choices = None

    def _apply_choice(self, current, choice):
        return current

    def evaluate_state(self, current):
        return 1.0

    def recommend_choice(self, current, choices, exclude_purge_ids=None):
        self.choices = choices
        result = dict(choices[-1])
        result["_score"] = 1.0
        result["_all_scores"] = []
        return result

    def rank_cards_for_purpose(self, current, purpose, n, exclude_ids=None):
        return [item for item in current["deck"] if "+" not in item][:n]


class Harness(ActionMixin, DecisionMixin, ChoiceMixin):
    def __init__(self):
        self.value_engine = FakeValueEngine()
        self.last_decision = None
        self.intended_smith_card = None
        self.intended_purge_card = None
        self._pending_grid = None

    def _build_relic_state_payload(self, state):
        return []


class CardChoiceTests(unittest.TestCase):
    def test_rest_upgrade_uses_unupgraded_uuid(self):
        upgraded = card("upgraded", upgrades=1, can_upgrade=False, name="Clothesline+")
        plain = card("plain")
        agent = Harness()
        action = agent._handle_rest_room(
            state(deck=[upgraded, plain], choices=["RestOption", "SmithOption"]))
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)
        self.assertEqual(["plain"], agent._pending_grid["target_uuids"])

    def test_grid_uses_structured_choice_index_for_uuid(self):
        agent = Harness()
        agent._pending_grid = {"purpose": "upgrade", "target_uuids": ["plain"],
                               "num_to_select": 1}
        s = state(screen="GRID", choices=["confirm", "clothesline"],
                  grid_cards=[{"choice_index": 1, "uuid": "plain", "id": "Clothesline",
                               "name": "Clothesline", "upgrades": 0, "can_upgrade": True}])
        action = agent._handle_grid_selection(s)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)

    def test_grid_does_not_confirm_before_selection(self):
        agent = Harness()
        agent._pending_grid = {"purpose": "upgrade", "target_uuids": ["missing"],
                               "num_to_select": 1}
        s = state(screen="GRID", choices=["confirm"], grid_cards=[])
        action = agent._handle_grid_selection(s)
        self.assertEqual(ActionType.WAIT, action.type)

    def test_resumed_multi_grid_chooses_first_remaining_target(self):
        agent = Harness()
        agent._pending_grid = {"purpose": "upgrade", "target_uuids": ["remaining"],
                               "num_to_select": 2}
        s = state(screen="GRID", choices=["confirm", "clothesline"], selected=1,
                  grid_cards=[{"choice_index": 1, "uuid": "remaining", "id": "Clothesline",
                               "name": "Clothesline", "upgrades": 0, "can_upgrade": True}])
        s.grid_num_cards = 2
        action = agent._handle_grid_selection(s)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)

    def test_upgraded_reward_is_not_parsed_as_leave(self):
        agent = Harness()
        def choose_first(current, choices, exclude_purge_ids=None):
            agent.value_engine.choices = choices
            result = dict(choices[0])
            result.update({"_score": 1.0, "_all_scores": []})
            return result
        agent.value_engine.recommend_choice = choose_first
        s = state(
            screen="CARD_REWARD", choices=["[Cleave+] Cost: 1 ATTACK"],
            reward_cards=[{"choice_index": 0, "uuid": "reward", "id": "Cleave",
                           "name": "Cleave+", "upgrades": 1, "can_upgrade": False}],
        )
        action = agent._get_model_card_decision(s)
        self.assertEqual("pick_card", agent.value_engine.choices[0]["action"])
        self.assertEqual("Cleave+1", agent.value_engine.choices[0]["target"])
        self.assertEqual(0, action.choice_index)


if __name__ == "__main__":
    unittest.main()
