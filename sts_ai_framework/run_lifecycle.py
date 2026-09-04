"""Run-lifecycle state machine: game over -> main menu -> character select -> next run.

Pure decision logic mapping a lifecycle GameState to the next GameAction. All
transitions are derived from Java-reported state fields (selected_character,
ascension_level, ...), so a failed click self-heals on the next poll instead of
relying on client-side bookkeeping.
"""
from typing import Optional

from .models import ActionType, GameAction, GameState

LIFECYCLE_SCREENS = {"GAME_OVER", "MAIN_MENU", "CHAR_SELECT"}


def is_lifecycle_state(state: GameState) -> bool:
    return state.screen_type in LIFECYCLE_SCREENS


def get_lifecycle_action(state: GameState, *, character: str, ascension: int,
                         auto_restart: bool) -> Optional[GameAction]:
    screen = state.screen_type
    if screen == "GAME_OVER":
        return _game_over_action(state, auto_restart)
    if screen == "MAIN_MENU":
        return _main_menu_action(state, auto_restart)
    if screen == "CHAR_SELECT":
        return _char_select_action(state, character, ascension)
    return None


def _game_over_action(state: GameState, auto_restart: bool) -> GameAction:
    if not auto_restart:
        return GameAction(type=ActionType.WAIT)
    # Death/victory advertise "return_to_menu"; the unlock screens reached
    # during the same return flow advertise "confirm" — both are index 0.
    if state.choice_list:
        return GameAction(type=ActionType.CHOOSE, choice_index=0)
    return GameAction(type=ActionType.WAIT)


def _main_menu_action(state: GameState, auto_restart: bool) -> GameAction:
    if not auto_restart:
        return GameAction(type=ActionType.WAIT)
    if state.choice_list and "play" in state.choice_list:
        return GameAction(type=ActionType.CHOOSE,
                          choice_index=state.choice_list.index("play"))
    # Transient menu screens (door cutscene, narration, ...) advertise no
    # choices; wait until the game resolves back to a controllable screen.
    return GameAction(type=ActionType.WAIT)


def _char_select_action(state: GameState, character: str, ascension: int) -> GameAction:
    choices = state.choice_list or []
    target = (character or "IRONCLAD").upper()
    if target not in choices:
        # Locked or unknown character: waiting beats picking blindly.
        return GameAction(type=ActionType.WAIT)
    if state.selected_character != target:
        return GameAction(type=ActionType.CHOOSE, choice_index=choices.index(target))
    if _ascension_needs_update(state, ascension):
        return GameAction(type=ActionType.SET_ASCENSION, level=ascension)
    return GameAction(type=ActionType.PROCEED)


def _ascension_needs_update(state: GameState, ascension: int) -> bool:
    if ascension <= 0:
        return bool(state.ascension_mode)
    if not state.ascension_mode:
        return True
    return state.ascension_level != ascension
