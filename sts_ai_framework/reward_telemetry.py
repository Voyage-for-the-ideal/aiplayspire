"""Confirmed chest and reward action telemetry.

The HTTP bridge acknowledges queue insertion before the game consumes an
action.  This module therefore separates classifying an intended action from
emitting its semantic event: callers keep the returned transition pending and
only log it after ``confirm`` observes the corresponding state change.
"""

from dataclasses import dataclass
from typing import Dict, Optional, Tuple

from .models import ActionType, GameAction, GameState


def _keys(state: GameState) -> Tuple[bool, bool, bool]:
    keys = state.keys
    return bool(keys.ruby), bool(keys.emerald), bool(keys.sapphire)


@dataclass(frozen=True)
class PendingRewardTransition:
    event: str
    floor: Optional[int]
    act: Optional[int]
    relic_id: Optional[str]
    keys_before: Tuple[bool, bool, bool]
    screen_before: Optional[str]
    relic_count_before: int
    submitted_at: float

    def confirm(self, state: Optional[GameState]) -> Optional[Dict[str, object]]:
        """Return log fields only after the intended transition is observable."""
        if state is None:
            return None

        keys_after = _keys(state)
        screen_changed = state.screen_type != self.screen_before or state.floor != self.floor

        if self.event == "take_sapphire_key":
            confirmed = not self.keys_before[2] and keys_after[2]
        elif self.event == "take_relic":
            relic_count_after = sum(
                1 for relic in (state.relics or []) if relic.id == self.relic_id
            )
            confirmed = relic_count_after > self.relic_count_before
        elif self.event in ("open_chest", "skip_chest"):
            confirmed = screen_changed
        elif self.event == "leave_rewards":
            confirmed = screen_changed
        else:
            confirmed = False

        if not confirmed:
            return None

        return {
            "event": self.event,
            "confirmed": True,
            "floor": self.floor,
            "act": self.act,
            "relic_id": self.relic_id,
            "ruby_before": self.keys_before[0],
            "emerald_before": self.keys_before[1],
            "sapphire_before": self.keys_before[2],
            "ruby_after": keys_after[0],
            "emerald_after": keys_after[1],
            "sapphire_after": keys_after[2],
        }


def classify_reward_transition(
        state: GameState,
        action: GameAction,
        submitted_at: float,
) -> Optional[PendingRewardTransition]:
    """Classify a chest/reward action without treating intent as an outcome."""
    event = None
    relic_id = None
    screen = state.screen_type

    if screen == "CHEST":
        if action.type == ActionType.CHOOSE:
            event = "open_chest"
        elif action.type in (ActionType.PROCEED, ActionType.SKIP):
            event = "skip_chest"
    elif screen == "COMBAT_REWARD":
        if action.type in (ActionType.PROCEED, ActionType.SKIP):
            event = "leave_rewards"
        elif action.type == ActionType.CHOOSE and action.choice_index is not None:
            reward = next(
                (
                    item for item in (state.reward_choices or [])
                    if item.choice_index == action.choice_index
                ),
                None,
            )
            if reward is not None and reward.type.upper() == "RELIC":
                event = "take_relic"
                relic_id = reward.relic_id
            elif reward is not None and reward.type.upper() == "SAPPHIRE_KEY":
                event = "take_sapphire_key"

    if event is None:
        return None

    return PendingRewardTransition(
        event=event,
        floor=state.floor,
        act=state.act,
        relic_id=relic_id,
        keys_before=_keys(state),
        screen_before=screen,
        relic_count_before=sum(
            1 for relic in (state.relics or []) if relic.id == relic_id
        ) if relic_id is not None else 0,
        submitted_at=submitted_at,
    )


class RewardTransitionTracker:
    """Holds at most one submitted chest/reward transition awaiting proof."""

    def __init__(self):
        self.pending: Optional[PendingRewardTransition] = None

    def begin(self, state: GameState, action: GameAction, submitted_at: float) -> None:
        candidate = classify_reward_transition(state, action, submitted_at)
        # A later unrelated action must not silently erase an earlier semantic
        # transition that is still waiting for proof from the game state.
        if candidate is not None and self.pending is None:
            self.pending = candidate

    def observe(self, state: Optional[GameState]) -> Optional[Dict[str, object]]:
        if self.pending is None:
            return None
        fields = self.pending.confirm(state)
        if fields is not None:
            self.pending = None
        return fields

    def discard_if_expired(self, now: float, timeout: float) -> Optional[str]:
        if self.pending is None or now - self.pending.submitted_at < timeout:
            return None
        event = self.pending.event
        self.pending = None
        return event
