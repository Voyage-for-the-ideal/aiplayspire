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


_MASK64 = (1 << 64) - 1


def _murmur_hash3(value):
    value ^= value >> 33; value = (value * 0xFF51AFD7ED558CCD) & _MASK64
    value ^= value >> 33; value = (value * 0xC4CEB9FE1A85EC53) & _MASK64
    return (value ^ (value >> 33)) & _MASK64


class _RandomXS128:
    """Byte-for-byte relevant port of LibGDX RandomXS128 used by STS Random."""
    def __init__(self, seed):
        seed &= _MASK64
        self.s0 = _murmur_hash3(seed if seed else 1 << 63)
        self.s1 = _murmur_hash3(self.s0)

    def next_long(self):
        s1, s0 = self.s0, self.s1
        self.s0 = s0
        s1 ^= (s1 << 23) & _MASK64
        self.s1 = (s1 ^ s0 ^ (s1 >> 17) ^ (s0 >> 26)) & _MASK64
        return (self.s1 + s0) & _MASK64

    def next_float(self):
        return (self.next_long() >> 40) * (1.0 / (1 << 24))


class _JavaRandom:
    def __init__(self, seed): self.seed = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)
    def _next(self, bits):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return self.seed >> (48 - bits)
    def next_int(self, bound):
        if bound & (bound - 1) == 0: return (bound * self._next(31)) >> 31
        while True:
            value = self._next(31); result = value % bound
            if value - result + (bound - 1) >= 0: return result


def _shuffle(values, seed):
    values = list(values); rng = _JavaRandom(seed)
    for index in range(len(values), 1, -1):
        swap = rng.next_int(index)
        values[index - 1], values[swap] = values[swap], values[index - 1]
    return values


_ACTS = (
    ((["Cultist", "Jaw Worm", "2 Louse", "Small Slimes"], [2]*4),
     (["Blue Slaver", "Gremlin Gang", "Looter", "Large Slime", "Lots of Slimes", "Exordium Thugs", "Exordium Wildlife", "Red Slaver", "3 Louse", "2 Fungi Beasts"], [2,1,2,2,1,1.5,1.5,1,2,2]),
     ["Gremlin Nob", "Lagavulin", "3 Sentries"], ["The Guardian", "Hexaghost", "Slime Boss"]),
    ((["Spheric Guardian", "Chosen", "Shell Parasite", "3 Byrds", "2 Thieves"], [2]*5),
     (["Chosen and Byrds", "Sentry and Sphere", "Snake Plant", "Snecko", "Centurion and Healer", "Cultist and Chosen", "3 Cultists", "Shelled Parasite and Fungi"], [2,2,6,4,6,3,3,3]),
     ["Gremlin Leader", "Slavers", "Book of Stabbing"], ["Automaton", "Collector", "Champ"]),
    ((["3 Darklings", "Orb Walker", "3 Shapes"], [2,2,2]),
     (["Spire Growth", "Transient", "4 Shapes", "Maw", "Sphere and 2 Shapes", "Jaw Worm Horde", "3 Darklings", "Writhing Mass"], [1]*8),
     ["Giant Head", "Nemesis", "Reptomancer"], ["Awakened One", "Time Eater", "Donu and Deca"]),
)


def _roll(rng, names, weights):
    roll = rng.next_float(); total = sum(weights); current = 0.0
    for name, weight in sorted(zip(names, weights), key=lambda item: item[1]):
        current += weight / total
        if roll < current: return name
    raise AssertionError("unreachable weighted roll")


def _populate(rng, names, weights, count, previous=()):
    result = list(previous)
    while len(result) < len(previous) + count:
        choice = _roll(rng, names, weights)
        if choice not in result[-2:]: result.append(choice)
    return result


class BossContextResolver:
    """Resolve visible vanilla bosses from seed; never inspects run outcomes."""
    def resolve_run(self, event_data):
        try: seed = int(event_data["seed_played"])
        except (KeyError, TypeError, ValueError) as exc: raise ValueError("seed_played is required") from exc
        rng = _RandomXS128(seed)
        bosses = []
        for weak, strong, elites, candidates in _ACTS:
            monster_list = _populate(rng, *weak, 3 if len(weak[0]) == 4 else 2)
            first = _roll(rng, *strong)
            monster_list.append(first)
            _populate(rng, *strong, 12, previous=monster_list)
            _populate(rng, elites, [1] * len(elites), 10)
            bosses.append(_shuffle(candidates, rng.next_long())[0])
        return {"act1_boss": bosses[0], "act2_boss": bosses[1], "act3_boss": bosses[2], "resolver_version": BOSS_RESOLVER_VERSION, "resolver_status": "resolved"}
