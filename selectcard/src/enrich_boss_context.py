"""Pure rules used by the sidecar enrichment job.

The resolver/audit job supplies ``resolved_context``.  This module never reads
combat outcomes, including damage_taken, victory, killed_by, or floor_reached.
"""

from boss_context import canonicalize_boss_name


def visible_boss_for_sample(floor, decision_type, ascension, resolved_context):
    """Return the player-visible boss for an already reconstructed snapshot."""
    if int(ascension) == 0 or int(floor) == 0 or decision_type == "boss_relic":
        return "NO_BOSS"
    if decision_type in {"boss_card_reward", "boss_reward"}:
        return "NO_BOSS"
    floor = int(floor)
    if floor <= 16:
        return canonicalize_boss_name(resolved_context["act1_boss"])
    if floor <= 33:
        return canonicalize_boss_name(resolved_context["act2_boss"])
    if floor <= 50:
        return canonicalize_boss_name(resolved_context["act3_boss"])
    return "Corrupt Heart"
