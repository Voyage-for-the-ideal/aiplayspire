import unittest

from sts_ai_framework.__main__ import _is_action_effective
from sts_ai_framework.models import ActionType, GameAction, GameState
from sts_ai_framework.run_lifecycle import (
    LIFECYCLE_SCREENS,
    get_lifecycle_action,
    is_lifecycle_state,
)


def make_menu_state(*, screen_type="MAIN_MENU", choice_list=None, selected_character=None,
                    ascension_mode=False, ascension_level=0, can_proceed=False,
                    game_over_reason=None, floor=None):
    return GameState(
        screen_type=screen_type, choice_list=choice_list or [],
        can_proceed=can_proceed, game_over_reason=game_over_reason,
        selected_character=selected_character, ascension_mode=ascension_mode,
        ascension_level=ascension_level, floor=floor,
    )


def make_game_over_state(*, choice_list=None, reason="defeat", floor=32):
    return make_menu_state(screen_type="GAME_OVER", choice_list=choice_list,
                           game_over_reason=reason, floor=floor)


CHAR_CHOICES = ["IRONCLAD", "SILENT", "DEFECT", "WATCHER"]


class LifecycleStateTests(unittest.TestCase):
    def test_lifecycle_screens_detected(self):
        for screen in ("GAME_OVER", "MAIN_MENU", "CHAR_SELECT"):
            self.assertTrue(is_lifecycle_state(make_menu_state(screen_type=screen)))
        self.assertFalse(is_lifecycle_state(make_menu_state(screen_type="NONE")))
        self.assertEqual(LIFECYCLE_SCREENS, {"GAME_OVER", "MAIN_MENU", "CHAR_SELECT"})


class GameOverActionTests(unittest.TestCase):
    def test_death_screen_returns_to_menu_when_auto_restart_on(self):
        action = get_lifecycle_action(make_game_over_state(choice_list=["return_to_menu"]),
                                      character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.CHOOSE)
        self.assertEqual(action.choice_index, 0)

    def test_unlock_screen_confirms(self):
        action = get_lifecycle_action(make_game_over_state(choice_list=["confirm"], reason="unlock"),
                                      character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.CHOOSE)
        self.assertEqual(action.choice_index, 0)

    def test_auto_restart_off_waits_instead(self):
        action = get_lifecycle_action(make_game_over_state(choice_list=["return_to_menu"]),
                                      character="IRONCLAD", ascension=15, auto_restart=False)
        self.assertEqual(action.type, ActionType.WAIT)

    def test_empty_choices_waits(self):
        action = get_lifecycle_action(make_game_over_state(choice_list=[]),
                                      character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.WAIT)


class MainMenuActionTests(unittest.TestCase):
    def test_play_choice_submitted(self):
        action = get_lifecycle_action(make_menu_state(choice_list=["play"]),
                                      character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.CHOOSE)
        self.assertEqual(action.choice_index, 0)

    def test_transient_menu_waits(self):
        action = get_lifecycle_action(make_menu_state(choice_list=[]),
                                      character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.WAIT)

    def test_auto_restart_off_waits(self):
        action = get_lifecycle_action(make_menu_state(choice_list=["play"]),
                                      character="IRONCLAD", ascension=15, auto_restart=False)
        self.assertEqual(action.type, ActionType.WAIT)


class CharSelectActionTests(unittest.TestCase):
    def test_selects_configured_character_first(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES)
        action = get_lifecycle_action(state, character="DEFECT", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.CHOOSE)
        self.assertEqual(action.choice_index, CHAR_CHOICES.index("DEFECT"))

    def test_sets_ascension_after_character_selected(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                                selected_character="IRONCLAD", ascension_mode=False,
                                ascension_level=0)
        action = get_lifecycle_action(state, character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.SET_ASCENSION)
        self.assertEqual(action.level, 15)

    def test_corrects_wrong_character_selection(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                                selected_character="SILENT", ascension_mode=True,
                                ascension_level=15)
        action = get_lifecycle_action(state, character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.CHOOSE)
        self.assertEqual(action.choice_index, 0)

    def test_updates_mismatched_ascension(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                                selected_character="IRONCLAD", ascension_mode=True,
                                ascension_level=10)
        action = get_lifecycle_action(state, character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.SET_ASCENSION)
        self.assertEqual(action.level, 15)

    def test_begins_run_when_character_and_ascension_match(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                                selected_character="IRONCLAD", ascension_mode=True,
                                ascension_level=15)
        action = get_lifecycle_action(state, character="IRONCLAD", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.PROCEED)

    def test_ascension_zero_disables_ascension_mode(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                                selected_character="IRONCLAD", ascension_mode=True,
                                ascension_level=10)
        action = get_lifecycle_action(state, character="IRONCLAD", ascension=0, auto_restart=True)
        self.assertEqual(action.type, ActionType.SET_ASCENSION)
        self.assertEqual(action.level, 0)

    def test_ascension_zero_skips_command_when_already_plain(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                                selected_character="IRONCLAD", ascension_mode=False,
                                ascension_level=0)
        action = get_lifecycle_action(state, character="IRONCLAD", ascension=0, auto_restart=True)
        self.assertEqual(action.type, ActionType.PROCEED)

    def test_locked_character_waits(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=["IRONCLAD"])
        action = get_lifecycle_action(state, character="WATCHER", ascension=15, auto_restart=True)
        self.assertEqual(action.type, ActionType.WAIT)


class ActionEffectivenessTests(unittest.TestCase):
    def test_character_selection_is_effective(self):
        prev = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES)
        nxt = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                              selected_character="IRONCLAD")
        self.assertTrue(_is_action_effective(prev, nxt, GameAction(type=ActionType.CHOOSE, choice_index=0)))

    def test_ascension_change_is_effective(self):
        prev = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                               selected_character="IRONCLAD", ascension_mode=False, ascension_level=0)
        nxt = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                              selected_character="IRONCLAD", ascension_mode=True, ascension_level=15)
        self.assertTrue(_is_action_effective(prev, nxt, GameAction(type=ActionType.SET_ASCENSION, level=15)))

    def test_unchanged_char_select_is_not_effective(self):
        state = make_menu_state(screen_type="CHAR_SELECT", choice_list=CHAR_CHOICES,
                                selected_character="IRONCLAD", ascension_mode=True, ascension_level=15)
        self.assertFalse(_is_action_effective(state, state, GameAction(type=ActionType.PROCEED)))


if __name__ == "__main__":
    unittest.main()
