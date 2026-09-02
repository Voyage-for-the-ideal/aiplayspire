import unittest

from sts_ai_framework.__main__ import _is_battle_owned_state
from sts_ai_framework.models import GameState


def make_state(*, room_phase="COMBAT", screen_type="NONE", choice_list=None, can_proceed=False):
    return GameState(
        player={"current_hp": 50, "max_hp": 80, "block": 0, "energy": 3, "gold": 100},
        deck=[], hand=[], draw_pile=[], discard_pile=[], exhaust_pile=[],
        draw_pile_size=0, discard_pile_size=0, exhaust_pile_size=0,
        monsters=[], floor=10, act=1, room_phase=room_phase,
        screen_type=screen_type, choice_list=choice_list or [],
        can_proceed=can_proceed,
    )


class MainLoopRegressionTests(unittest.TestCase):
    def test_combat_screens_are_handled_by_battle_mod(self):
        self.assertTrue(_is_battle_owned_state(make_state(screen_type="NONE")))
        self.assertTrue(_is_battle_owned_state(make_state(screen_type="HAND_SELECT")))
        self.assertTrue(_is_battle_owned_state(make_state(screen_type="GRID")))
        self.assertTrue(_is_battle_owned_state(make_state(screen_type="CARD_REWARD")))

    def test_non_combat_states_are_not_battle_owned(self):
        self.assertFalse(_is_battle_owned_state(make_state(room_phase="COMPLETE", screen_type="EVENT")))


if __name__ == "__main__":
    unittest.main()
