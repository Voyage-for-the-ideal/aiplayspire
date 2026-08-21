"""Stable, training-facing encoding for the boss currently visible to a player.

This module intentionally has no access to combat telemetry.  In particular,
``damage_taken`` is audit-only data and must never be used to create this feature.
"""

BOSS_SCHEMA_VERSION = "visible-boss-v1"
BOSS_RESOLVER_VERSION = "vanilla-seed-boss-v1"

NO_BOSS = 0
UNKNOWN_BOSS = 1

_BOSS_NAMES = (
    "NO_BOSS", "UNKNOWN_BOSS", "Slime Boss", "Hexaghost", "The Guardian",
    "Champ", "The Collector", "Bronze Automaton", "Time Eater",
    "Awakened One", "Donu and Deca", "Corrupt Heart",
)
BOSS_VOCABULARY = {name: index for index, name in enumerate(_BOSS_NAMES)}
NUM_BOSS_IDS = len(BOSS_VOCABULARY)

_ALIASES = {
    "no_boss": "NO_BOSS", "none": "NO_BOSS", "": "NO_BOSS",
    "unknown": "UNKNOWN_BOSS", "unknown_boss": "UNKNOWN_BOSS",
    "slimeboss": "Slime Boss", "slime boss": "Slime Boss",
    "hexaghost": "Hexaghost", "theguardian": "The Guardian", "the guardian": "The Guardian",
    "champ": "Champ", "the champ": "Champ", "the collector": "The Collector", "collector": "The Collector",
    "bronze automaton": "Bronze Automaton", "automaton": "Bronze Automaton",
    "time eater": "Time Eater", "timeeater": "Time Eater",
    "awakened one": "Awakened One", "awakenedone": "Awakened One",
    "donu and deca": "Donu and Deca", "donu&deca": "Donu and Deca", "donu and deca": "Donu and Deca",
    "corrupt heart": "Corrupt Heart", "the corrupt heart": "Corrupt Heart", "corruptheart": "Corrupt Heart",
}


def canonicalize_boss_name(name):
    if name is None:
        return "NO_BOSS"
    normalized = " ".join(str(name).replace("_", " ").strip().lower().split())
    return _ALIASES.get(normalized, "UNKNOWN_BOSS")


def boss_id(name):
    return BOSS_VOCABULARY[canonicalize_boss_name(name)]


def boss_name(identifier):
    if isinstance(identifier, bool) or not isinstance(identifier, int) or not 0 <= identifier < NUM_BOSS_IDS:
        raise ValueError(f"Unknown boss ID: {identifier}")
    return _BOSS_NAMES[identifier]


def get_visible_boss(state):
    """Prefer the formal field; only use the legacy field for old clients."""
    if not state:
        return "NO_BOSS"
    value = state.get("visible_boss")
    if value:
        return value
    return state.get("next_boss") or "NO_BOSS"
