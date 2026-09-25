"""Shared construction of value-network state payloads."""

from typing import List

from ..models import GameState, visible_boss_of
from .map_payload import map_context_fields

VALID_CHARACTERS = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"}


def relic_state_payload(state: GameState) -> List[dict]:
    """Serialize relic counters needed by hypothetical choice simulation."""
    payload = []
    for relic in getattr(state, "relics", []) or []:
        relic_id = getattr(relic, "id", "")
        relic_name = getattr(relic, "name", relic_id)
        counter = getattr(relic, "counter", -1)
        if (relic_id == "Omamori" or relic_name == "Omamori") and (
            counter is None or counter < 0
        ):
            counter = 2
        payload.append({"id": relic_id, "name": relic_name, "counter": counter})
    return payload


def value_state_payload(state: GameState) -> dict:
    """Build the common state used by every value-network decision path."""
    if state.character not in VALID_CHARACTERS:
        raise ValueError(
            "GameState.character must be one of: "
            + ", ".join(sorted(VALID_CHARACTERS))
        )
    return {
        "hp": state.player.current_hp,
        "max_hp": state.player.max_hp,
        "gold": state.player.gold,
        "floor": state.floor,
        "ascension": 20,
        "visible_boss": visible_boss_of(state),
        "character": state.character,
        **map_context_fields(state),
        "deck": [card.reference for card in getattr(state, "deck", []) or []],
        "relics": [relic.id for relic in getattr(state, "relics", []) or []],
        "relic_states": relic_state_payload(state),
    }


__all__ = ["VALID_CHARACTERS", "relic_state_payload", "value_state_payload"]
