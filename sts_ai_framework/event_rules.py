"""Explicit player rules for structured CommunicationMod events."""
from typing import Optional

from .models import GameState


# The non-combat button kind in each event's INTRO menu.
_NONCOMBAT_KIND = {"MaskedBandits": "EFFECT", "MysteriousSphere": "LEAVE"}


def fight_event_choice_index(state: GameState) -> Optional[int]:
    """Fight at or above 10% HP; otherwise pay the bandits or leave the sphere."""
    if state.screen_type != "EVENT" or state.room_phase == "COMBAT":
        return None
    event, player = state.event, state.player
    if (event is None or event.semantics_status != "KNOWN" or event.phase != "INTRO"
            or player is None or player.max_hp <= 0):
        return None
    noncombat_kind = _NONCOMBAT_KIND.get(event.class_name)
    if noncombat_kind is None:
        return None

    kind = "COMBAT" if 10 * player.current_hp >= player.max_hp else noncombat_kind
    return next((choice.action_index for choice in event.choices
                 if choice.enabled and choice.action_index is not None and choice.kind == kind), None)
