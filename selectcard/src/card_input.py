"""Normalization for structured card data received from Communication Mod."""

from typing import Any


def normalize_card_reference(card: Any) -> str:
    """Turn a Communication Mod card payload into the model's ID+upgrade form."""
    if isinstance(card, str):
        return card
    if not isinstance(card, dict):
        raise ValueError("card must be a string or an object containing id and upgrades")

    card_id = card.get("id")
    if not isinstance(card_id, str) or not card_id:
        raise ValueError("structured card input must contain a non-empty id")

    upgrades = card.get("upgrades", 0)
    if isinstance(upgrades, bool) or not isinstance(upgrades, int) or upgrades < 0:
        raise ValueError("structured card upgrades must be a non-negative integer")
    return card_id if upgrades == 0 else f"{card_id}+{upgrades}"
