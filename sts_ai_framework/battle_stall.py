"""Battle-stall watchdog: detect when the battle-owned game state stops changing.

Combat decisions belong to BattleAiMod, so when a fight freezes (search gave
up, replay diverged twice, mod crash, ...) the framework cannot fix it — its
only job is to notice and say so. That is why this module is log-only by
design: `StallWatcher.observe()` reports the stall, nothing more.
"""
from typing import Optional, Tuple

from .models import GameState


def battle_progress_signature(state: GameState) -> Tuple:
    """Deterministic snapshot of everything that moves while a fight plays out.

    Two consecutive polls with the same signature mean the game has not
    advanced at all: same screen, same HP/energy/block, same monster states,
    same choices. Deliberately excludes volatile cosmetic fields.
    """
    player = state.player
    return (
        state.screen_type,
        state.room_phase,
        state.floor,
        state.act,
        state.is_end_turn_button_enabled,
        tuple(state.choice_list),
        None if player is None else (player.current_hp, player.max_hp, player.energy, player.block),
        tuple((monster.id, monster.current_hp, monster.block, monster.intent)
              for monster in state.monsters),
        len(state.hand),
    )


class StallWatcher:
    """Fires exactly once per unchanged-state episode, threshold_seconds after
    the last change. Inject `now` for tests; pass real time.monotonic() in prod."""

    def __init__(self, threshold_seconds: float):
        self.threshold_seconds = threshold_seconds
        self._signature = None
        self._since = None
        self._reported = False

    def observe(self, state: GameState, now: float, in_battle: bool) -> Optional[float]:
        """Feed one poll sample. Returns the stall duration once when the
        battle state has been frozen for the threshold; None otherwise (and
        keeps returning None until the state changes or the battle ends)."""
        if self.threshold_seconds <= 0:
            return None
        if not in_battle:
            self._reset()
            return None

        signature = battle_progress_signature(state)
        if signature != self._signature:
            self._signature = signature
            self._since = now
            self._reported = False
            return None

        if self._reported or self._since is None:
            return None
        stalled_for = now - self._since
        if stalled_for >= self.threshold_seconds:
            self._reported = True
            return stalled_for
        return None

    def _reset(self) -> None:
        self._signature = None
        self._since = None
        self._reported = False
