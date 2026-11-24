import unittest

from sts_ai_framework.llm_agent_parts.action_mixin import ActionMixin
from sts_ai_framework.llm_agent_parts.choice_mixin import ChoiceMixin
from sts_ai_framework.llm_agent_parts.info_prompt_mixin import InfoPromptMixin
from sts_ai_framework.models import ActionType, GameState


class FailingCompletions:
    """A SHOP_ROOM decision is deterministic; reaching the LLM is a bug."""

    def __init__(self):
        self.calls = 0

    def create(self, **_kwargs):
        self.calls += 1
        raise AssertionError("SHOP_ROOM must not call the external LLM")


class ShopHarness(ActionMixin, ChoiceMixin, InfoPromptMixin):
    def __init__(self):
        self.value_engine = None
        self._pending_event = None
        self._pending_grid = None
        self.last_screen_type = None
        self.knowledge_base = type("KB", (), {"cards": {}})()
        self.game_client = None
        self.debug_prompt_file = None
        self.model_name = "test-model"
        self.intended_purge_card = None
        self.intended_smith_card = None
        self.completions = FailingCompletions()
        self.llm_client = type(
            "Client", (), {"chat": type("Chat", (), {"completions": self.completions})()}
        )()


def shop_state(choices, *, can_proceed=False, can_cancel=False):
    return GameState(
        player={"current_hp": 72, "max_hp": 80, "block": 0, "energy": 0, "gold": 200},
        deck=[], hand=[], draw_pile=[], discard_pile=[], exhaust_pile=[],
        monsters=[], floor=8, act=1, room_phase="COMPLETE",
        screen_type="SHOP_ROOM", choice_list=choices,
        can_proceed=can_proceed, can_cancel=can_cancel,
    )


class ShopRoomTests(unittest.TestCase):
    def test_entering_shop_room_chooses_shop_without_llm(self):
        agent = ShopHarness()
        action = agent.choose_action(shop_state(["shop"]))
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual(0, agent.completions.calls)

    def test_shop_room_remembers_screen_for_auto_proceed_on_leave(self):
        agent = ShopHarness()
        agent.choose_action(shop_state(["shop"]))
        self.assertEqual("SHOP_ROOM", agent.last_screen_type)

        # The framework polls the open ShopScreen first; leaving it bounces
        # back to SHOP_ROOM, where the existing auto-proceed branch runs.
        agent.last_screen_type = "SHOP_SCREEN"
        leave = shop_state(["shop"], can_proceed=True)
        action = agent.choose_action(leave)
        self.assertEqual(ActionType.PROCEED, action.type)
        self.assertEqual(0, agent.completions.calls)

    def test_shop_choice_follows_its_label_index(self):
        agent = ShopHarness()
        action = agent.choose_action(shop_state(["shop_preview", "shop"]))
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)

    def test_single_unknown_label_is_still_the_only_action(self):
        agent = ShopHarness()
        action = agent.choose_action(shop_state(["merchant"]))
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

    def test_shop_room_without_choices_uses_available_button(self):
        agent = ShopHarness()
        action = agent.choose_action(shop_state([], can_proceed=True))
        self.assertEqual(ActionType.PROCEED, action.type)

        agent = ShopHarness()
        action = agent.choose_action(shop_state([]))
        self.assertEqual(ActionType.WAIT, action.type)
        self.assertEqual(0, agent.completions.calls)


if __name__ == "__main__":
    unittest.main()
