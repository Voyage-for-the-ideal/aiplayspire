"""Faithful Python port of Slay the Spire map generation (seed -> act map).

Every constant, branch and RNG call order below was verified against the
shipped game code (lib/desktop-1.0.jar) via javap disassembly on 2026-09-12:

- com.badlogic.gdx.math.RandomXS128  (xorshift128+; NOT java.util.Random)
- com.megacrit.cardcrawl.random.Random (counter wrapper; random(n)=nextInt(n+1))
- com.megacrit.cardcrawl.map.MapGenerator / RoomTypeAssigner / MapEdge
- com.megacrit.cardcrawl.dungeons.{Exordium,TheCity,TheBeyond} (mapRng seeds)
- com.megacrit.cardcrawl.dungeons.AbstractDungeon.generateMap /
  generateRoomTypes / setEmeraldElite

Notable quirks kept on purpose (they ship in the game):
- MapGenerator.getCommonAncestor compares node1.x < node2.y (x against the
  shared row index), not node1.x < node2.x.
- MapRoomNode.addEdge silently drops an edge whose (dstX, dstY) already exists.
- Boss edges all point at (dstX=3, dstY=16).
- distributeRoomsAcrossMap pads with MonsterRoom using the count taken AFTER
  rows 0/8/14 were pre-assigned (i.e. row 13 nodes are included in the count).
- Room list build order is shop, rest, elite, event (no treasure entries;
  treasureRoomChance is 0.0 in all three acts and row 8 is fixed treasure).

Unit tests (test_sts_mapgen.py) pin the port against ground truth produced by
running the real jar classes; validate_map_reconstruction.py checks
reconstructed maps against path_per_floor of archived human runs.
"""

from __future__ import annotations

import math
import struct

_MASK64 = (1 << 64) - 1
_I64 = 1 << 63
_I32 = 1 << 31

ACT_RNG_OFFSETS = {1: 1, 2: 200, 3: 600}  # Exordium: +actNum; City: +actNum*100; Beyond: +actNum*200

MAP_HEIGHT = 15
MAP_WIDTH = 7
MAP_DENSITY = 6

SHOP_ROOM_CHANCE = 0.05
REST_ROOM_CHANCE = 0.12
TREASURE_ROOM_CHANCE = 0.0
EVENT_ROOM_CHANCE = 0.22
ELITE_ROOM_CHANCE = 0.08

# Room identifiers (symbol mapping identical to spirelogs path_per_floor)
MONSTER = "monster"
ELITE = "elite"
REST = "rest"
SHOP = "shop"
TREASURE = "treasure"
EVENT = "event"

ROOM_SYMBOLS = {
    MONSTER: "M",
    ELITE: "E",
    REST: "R",
    SHOP: "$",
    TREASURE: "T",
    EVENT: "?",
}


def _u64(value: int) -> int:
    return value & _MASK64


def _s64(value: int) -> int:
    value &= _MASK64
    return value - (1 << 64) if value >= _I64 else value


def _s32(value: int) -> int:
    value &= 0xFFFFFFFF
    return value - (1 << 32) if value >= _I32 else value


def _f32(value: float) -> float:
    """Round a Python float to float32 precision (Java float arithmetic)."""
    return struct.unpack("<f", struct.pack("<f", value))[0]


def java_round_float(value: float) -> int:
    """java.lang.Math.round(float): closest int, ties toward +inf."""
    return int(math.floor(_f32(value + _f32(0.5))))


class RandomXS128:
    """libGDX xorshift128+ generator, bit-exact with the game's copy."""

    def __init__(self, seed: int | None = None, seed1: int | None = None):
        if seed1 is None:
            self.set_seed(0 if seed is None else seed)
        else:
            self.set_state(_u64(seed), _u64(seed1))

    @staticmethod
    def murmur_hash3(value: int) -> int:
        x = _u64(value)
        x ^= x >> 33
        x = _u64(x * 0xFF51AFD7ED558CCD)
        x ^= x >> 33
        x = _u64(x * 0xC4CEB9FE1A85EC53)
        x ^= x >> 33
        return x

    def set_seed(self, seed: int) -> None:
        seed = _u64(seed)
        seed0 = self.murmur_hash3(0x8000000000000000 if seed == 0 else seed)
        self.set_state(seed0, self.murmur_hash3(seed0))

    def set_state(self, seed0: int, seed1: int) -> None:
        if seed0 == 0 and seed1 == 0:
            raise ValueError("Internal state cannot be 0.")
        self.seed0 = _u64(seed0)
        self.seed1 = _u64(seed1)

    def get_state(self, index: int) -> int:
        return self.seed0 if index == 0 else self.seed1

    def next_long(self) -> int:
        """Signed 64-bit next value (Java long semantics)."""
        s1 = self.seed0
        s0 = self.seed1
        self.seed0 = s0
        s1 = _u64(s1 ^ ((s1 << 23) & _MASK64))
        new_state = _u64(s1 ^ s0 ^ (s1 >> 17) ^ (s0 >> 26))
        self.seed1 = new_state
        return _s64(_u64(new_state + s0))

    def next_long_bounded(self, n: int) -> int:
        if n <= 0:
            raise ValueError("n must be positive")
        while True:
            bits = _u64(self.next_long()) >> 1  # Java: nextLong() >>> 1
            value = bits % n
            # Java: if (bits - value + (n - 1) >= 0) return value;  (long compare)
            if _s64(bits - value + (n - 1)) >= 0:
                return value

    def next_int_bounded(self, n: int) -> int:
        return _s32(self.next_long_bounded(n))

    def next(self, bits: int) -> int:
        return _s32(_u64(self.next_long()) & ((1 << bits) - 1))

    def next_boolean(self) -> bool:
        return (_u64(self.next_long()) & 1) != 0

    def next_float(self) -> float:
        return _f32((_u64(self.next_long()) >> 40) * (2.0**-24))

    def next_double(self) -> float:
        return ((_u64(self.next_long()) >> 11) * 2.0**-53)


class StSRandom:
    """com.megacrit.cardcrawl.random.Random wrapper (counter is cosmetic)."""

    def __init__(self, seed: int):
        self.xs = RandomXS128(seed)
        self.counter = 0

    def random(self, limit: int) -> int:
        """random(int range) -> nextInt(range + 1), i.e. 0..range inclusive."""
        self.counter += 1
        return self.xs.next_int_bounded(limit + 1)

    def random_range(self, start: int, end: int) -> int:
        """random(int start, int end) -> start + nextInt(end - start + 1)."""
        self.counter += 1
        return start + self.xs.next_int_bounded(end - start + 1)

    def random_boolean(self) -> bool:
        self.counter += 1
        return self.xs.next_boolean()


class MapEdge:
    __slots__ = ("src_x", "src_y", "dst_x", "dst_y")

    def __init__(self, src_x: int, src_y: int, dst_x: int, dst_y: int):
        self.src_x = src_x
        self.src_y = src_y
        self.dst_x = dst_x
        self.dst_y = dst_y

    def sort_key(self) -> tuple[int, int]:
        return (self.dst_x, self.dst_y)


class MapRoomNode:
    __slots__ = ("x", "y", "room", "edges", "parents", "burning")

    def __init__(self, x: int, y: int):
        self.x = x
        self.y = y
        self.room: str | None = None
        self.edges: list[MapEdge] = []
        self.parents: list["MapRoomNode"] = []
        self.burning = False

    @property
    def has_edges(self) -> bool:
        return bool(self.edges)

    def add_edge(self, edge: MapEdge) -> None:
        # Game behaviour: drop the edge if one with the same destination exists.
        for existing in self.edges:
            if existing.dst_x == edge.dst_x and existing.dst_y == edge.dst_y:
                return
        self.edges.append(edge)

    def del_edge(self, edge: MapEdge) -> None:
        for index, existing in enumerate(self.edges):
            if existing is edge:
                del self.edges[index]
                return

    def add_parent(self, parent: "MapRoomNode") -> None:
        self.parents.append(parent)

    @property
    def symbol(self) -> str:
        return "." if self.room is None else ROOM_SYMBOLS[self.room]


def _sort_edges(node: MapRoomNode) -> None:
    # Collections.sort on the node's edge list (stable; key = compareTo).
    node.edges.sort(key=MapEdge.sort_key)


def _rand_range(rng: StSRandom, lo: int, hi: int) -> int:
    return rng.random(hi - lo) + lo


def _get_node(nodes: list[list[MapRoomNode]], x: int, y: int) -> MapRoomNode:
    return nodes[y][x]


def _node_with_max_x(nodes: list[MapRoomNode]) -> MapRoomNode:
    best = nodes[0]
    for node in nodes:
        if node.x > best.x:
            best = node
    return best


def _node_with_min_x(nodes: list[MapRoomNode]) -> MapRoomNode:
    best = nodes[0]
    for node in nodes:
        if node.x < best.x:
            best = node
    return best


def _get_max_edge(edges: list[MapEdge]) -> MapEdge:
    edges.sort(key=MapEdge.sort_key)  # in place, like Collections.sort
    return edges[-1]


def _get_min_edge(edges: list[MapEdge]) -> MapEdge:
    edges.sort(key=MapEdge.sort_key)
    return edges[0]


def _get_common_ancestor(node1: MapRoomNode, node2: MapRoomNode, max_depth: int) -> MapRoomNode | None:
    # Quirk kept: the game compares node1.x against node2.y (both nodes share y).
    if node1.x < node2.y:
        left, right = node1, node2
    else:
        left, right = node2, node1
    current_y = node1.y
    while current_y >= 0 and current_y >= node1.y - max_depth:
        if not left.parents or not right.parents:
            return None
        left = _node_with_max_x(left.parents)
        right = _node_with_min_x(right.parents)
        if left is right:
            return left
        current_y -= 1
    return None


def _create_paths(nodes: list[list[MapRoomNode]], edge: MapEdge, rng: StSRandom) -> None:
    current = _get_node(nodes, edge.dst_x, edge.dst_y)
    if edge.dst_y + 1 >= len(nodes):
        # Boss edge: every connected row-14 node points at (3, dstY+2).
        current.add_edge(MapEdge(edge.dst_x, edge.dst_y, 3, edge.dst_y + 2))
        _sort_edges(current)
        return
    row_end = len(nodes[edge.dst_y]) - 1
    if edge.dst_x == 0:
        lo, hi = 0, 1
    elif edge.dst_x == row_end:
        lo, hi = -1, 0
    else:
        lo, hi = -1, 1
    new_x = edge.dst_x + _rand_range(rng, lo, hi)
    new_y = edge.dst_y + 1
    candidate = _get_node(nodes, new_x, new_y)
    parents = candidate.parents
    if parents:
        for parent in parents:
            if parent is current:
                continue
            ancestor = _get_common_ancestor(parent, current, 5)
            if ancestor is None:
                continue
            ancestor_gap = new_y - ancestor.y
            if ancestor_gap < 3:
                if candidate.x > current.x:
                    new_x = edge.dst_x + _rand_range(rng, -1, 0)
                    if new_x < 0:
                        new_x = edge.dst_x
                elif candidate.x == current.x:
                    new_x = edge.dst_x + _rand_range(rng, -1, 1)
                    if new_x > row_end:
                        new_x = edge.dst_x - 1
                    elif new_x < 0:
                        new_x = edge.dst_x + 1
                else:
                    new_x = edge.dst_x + _rand_range(rng, 0, 1)
                    if new_x > row_end:
                        new_x = edge.dst_x
                candidate = _get_node(nodes, new_x, new_y)
    if edge.dst_x != 0:
        left_node = nodes[edge.dst_y][edge.dst_x - 1]
        if left_node.has_edges:
            max_edge = _get_max_edge(left_node.edges)
            if max_edge.dst_x > new_x:
                new_x = max_edge.dst_x
    if edge.dst_x < row_end:
        right_node = nodes[edge.dst_y][edge.dst_x + 1]
        if right_node.has_edges:
            min_edge = _get_min_edge(right_node.edges)
            if min_edge.dst_x < new_x:
                new_x = min_edge.dst_x
    target = _get_node(nodes, new_x, new_y)
    new_edge = MapEdge(edge.dst_x, edge.dst_y, new_x, new_y)
    current.add_edge(new_edge)
    _sort_edges(current)
    target.add_parent(current)
    _create_paths(nodes, new_edge, rng)


def _create_paths_entry(nodes: list[list[MapRoomNode]], path_density: int, rng: StSRandom) -> None:
    row_size = len(nodes[0]) - 1
    first_starting_node = -1
    for i in range(path_density):
        starting_node = _rand_range(rng, 0, row_size)
        if i == 0:
            first_starting_node = starting_node
        while starting_node == first_starting_node and i == 1:
            starting_node = _rand_range(rng, 0, row_size)
        _create_paths(nodes, MapEdge(starting_node, -1, starting_node, 0), rng)


def _filter_redundant_edges_from_row(nodes: list[list[MapRoomNode]]) -> None:
    existing_edges: list[MapEdge] = []
    delete_list: list[MapEdge] = []
    for node in nodes[0]:
        if node.has_edges:
            for edge in node.edges:
                for prev_edge in existing_edges:
                    if edge.dst_x == prev_edge.dst_x and edge.dst_y == prev_edge.dst_y:
                        delete_list.append(edge)
                existing_edges.append(edge)
            for edge in delete_list:
                node.del_edge(edge)
            delete_list = []


def generate_dungeon(rng: StSRandom, height: int = MAP_HEIGHT, width: int = MAP_WIDTH,
                     path_density: int = MAP_DENSITY) -> list[list[MapRoomNode]]:
    nodes = [[MapRoomNode(x, y) for x in range(width)] for y in range(height)]
    _create_paths_entry(nodes, path_density, rng)
    _filter_redundant_edges_from_row(nodes)
    return nodes


def _assign_row_as_room_type(row: list[MapRoomNode], room: str) -> None:
    for node in row:
        if node.room is None:
            node.room = room


def _rule_assignable_to_row(node: MapRoomNode, room: str) -> bool:
    if node.y <= 4 and room in (REST, ELITE):
        return False
    if node.y < 13 or room != REST:
        return True
    return False


_PARENT_APPLICABLE = frozenset((REST, TREASURE, SHOP, ELITE))
_SIBLING_APPLICABLE = frozenset((REST, MONSTER, EVENT, ELITE, SHOP))


def _rule_parent_matches(parents: list[MapRoomNode], room: str) -> bool:
    if room not in _PARENT_APPLICABLE:
        return False
    return any(parent.room == room for parent in parents)


def _rule_sibling_matches(siblings: list[MapRoomNode], room: str) -> bool:
    if room not in _SIBLING_APPLICABLE:
        return False
    return any(sibling.room == room for sibling in siblings)


def _get_siblings(nodes: list[list[MapRoomNode]], parents: list[MapRoomNode],
                  node: MapRoomNode) -> list[MapRoomNode]:
    siblings: list[MapRoomNode] = []
    for parent in parents:
        for edge in parent.edges:
            sibling = nodes[edge.dst_y][edge.dst_x]
            if sibling is not node:
                siblings.append(sibling)
    return siblings


def _next_room_type_according_to_rules(nodes: list[list[MapRoomNode]], node: MapRoomNode,
                                       room_list: list[str]) -> str | None:
    parents = node.parents
    siblings = _get_siblings(nodes, parents, node)
    for room in room_list:
        if _rule_assignable_to_row(node, room):
            if not _rule_parent_matches(parents, room) and not _rule_sibling_matches(siblings, room):
                return room
            if node.y == 0:
                return room
    return None


def _assign_rooms_to_nodes(nodes: list[list[MapRoomNode]], room_list: list[str]) -> None:
    for row in nodes:
        for node in row:
            if node.has_edges and node.room is None:
                room = _next_room_type_according_to_rules(nodes, node, room_list)
                if room is not None:
                    room_list.remove(room)
                    node.room = room


def _last_minute_node_checker(nodes: list[list[MapRoomNode]]) -> None:
    for row in nodes:
        for node in row:
            if node.has_edges and node.room is None:
                node.room = MONSTER


def _collections_shuffle(items: list, xs128: RandomXS128) -> None:
    # java.util.Collections.shuffle for RandomAccess lists (Java 8).
    for i in range(len(items), 1, -1):
        j = xs128.next_int_bounded(i)
        items[i - 1], items[j] = items[j], items[i - 1]


def _distribute_rooms_across_map(nodes: list[list[MapRoomNode]], rng: StSRandom,
                                 room_list: list[str]) -> None:
    node_count = sum(1 for row in nodes for node in row if node.has_edges and node.room is None)
    while len(room_list) < node_count:
        room_list.append(MONSTER)
    _collections_shuffle(room_list, rng.xs)
    _assign_rooms_to_nodes(nodes, room_list)
    _last_minute_node_checker(nodes)


def set_emerald_elite(nodes: list[list[MapRoomNode]], rng: StSRandom, enabled: bool) -> MapRoomNode | None:
    """AbstractDungeon.setEmeraldElite: pick one elite node to carry the key."""
    if not enabled:
        return None
    elite_nodes = [node for row in nodes for node in row if node.room == ELITE]
    if not elite_nodes:
        return None
    chosen = elite_nodes[rng.random_range(0, len(elite_nodes) - 1)]
    chosen.burning = True
    return chosen


def generate_act_map(seed: int, act: int, ascension_level: int = 0,
                     emerald_elite_enabled: bool = True,
                     elite_swarm: bool = False) -> list[list[MapRoomNode]]:
    """Reconstruct one act's map exactly as AbstractDungeon.generateMap does."""
    rng = StSRandom(seed + ACT_RNG_OFFSETS[act])
    nodes = generate_dungeon(rng)
    node_count = sum(1 for row in nodes for node in row if node.has_edges and node.y != 13)

    shop_count = java_round_float(_f32(node_count * SHOP_ROOM_CHANCE))
    rest_count = java_round_float(_f32(node_count * REST_ROOM_CHANCE))
    treasure_count = java_round_float(_f32(node_count * TREASURE_ROOM_CHANCE))
    if elite_swarm:
        elite_multiplier = 2.5
    elif ascension_level >= 1:
        elite_multiplier = 1.6
    else:
        elite_multiplier = 1.0
    elite_count = java_round_float(_f32(_f32(_f32(node_count) * ELITE_ROOM_CHANCE) * elite_multiplier))
    event_count = java_round_float(_f32(node_count * EVENT_ROOM_CHANCE))

    room_list: list[str] = []
    room_list.extend([SHOP] * shop_count)
    room_list.extend([REST] * rest_count)
    room_list.extend([TREASURE] * treasure_count)
    room_list.extend([ELITE] * elite_count)
    room_list.extend([EVENT] * event_count)

    _assign_row_as_room_type(nodes[len(nodes) - 1], REST)
    _assign_row_as_room_type(nodes[0], MONSTER)
    _assign_row_as_room_type(nodes[8], TREASURE)

    _distribute_rooms_across_map(nodes, rng, room_list)
    set_emerald_elite(nodes, rng, emerald_elite_enabled)
    return nodes


def act_path_symbols(nodes: list[list[MapRoomNode]]) -> list[list[str]]:
    """Row-major symbol grid ('.' for unconnected rooms) for debugging/tests."""
    grid = []
    for row in nodes:
        line = []
        for node in row:
            connected = node.has_edges or bool(node.parents)
            line.append(node.symbol if connected else ".")
        grid.append(line)
    return grid


def path_is_legal(nodes: list[list[MapRoomNode]], symbols: list[str],
                  wing_from_y: int | None = None,
                  wing_charges: int = 0) -> tuple[bool, int | None, str]:
    """Check that a room-symbol sequence is a legal path through the act map.

    ``symbols`` covers rows y=0..len-1 (a trailing "B" boss entry must be
    removed by the caller; every connected row-14 node links to the boss).
    Returns (ok, first_bad_y, reachable_symbols_at_failure).

    EventHelper quirk: a '?' node resolves at enter time via EventHelper.roll()
    (independent eventRng) and can be recorded in path_per_floor as
    '?', 'M', '$' or 'T' (elite only under the DeadlyEvents daily mod).

    WingedGreaves quirk: wing charges allow moving to a non-adjacent node on
    the next row; jumps become available from row ``wing_from_y`` (the relic
    pickup floor) and the budget resets each act.
    """
    def matches(node: MapRoomNode, wanted: str) -> bool:
        if node.room is None:
            return False
        if node.room == EVENT:
            return wanted in ("?", "M", "$", "T")
        return ROOM_SYMBOLS[node.room] == wanted

    def reachable_symbols(row_y: int, from_states: dict) -> str:
        syms = set()
        for x, charges in from_states.items():
            for edge in nodes[row_y - 1][x].edges if row_y > 0 else ():
                if nodes[row_y][edge.dst_x].room is not None:
                    syms.add(nodes[row_y][edge.dst_x].symbol)
            if charges > 0 and wing_from_y is not None and row_y >= wing_from_y:
                for x2 in range(MAP_WIDTH):
                    if nodes[row_y][x2].room is not None:
                        syms.add(nodes[row_y][x2].symbol)
        return "".join(sorted(syms))

    states: dict[int, int] = {}
    for x in range(MAP_WIDTH):
        node = nodes[0][x]
        if node.has_edges and matches(node, symbols[0]):
            states[x] = wing_charges if (wing_from_y is not None and wing_from_y <= 0) else 0
    if not states:
        return False, 0, ""
    for y in range(1, len(symbols)):
        wanted = symbols[y]
        nxt: dict[int, int] = {}
        # The relic's charges arrive at its pickup row (grant once there).
        if wing_from_y is not None and wing_charges > 0 and y >= wing_from_y:
            for x in states:
                states[x] = max(states[x], wing_charges)
        wing_ok = wing_charges > 0 and wing_from_y is not None and y >= wing_from_y
        for x, charges in states.items():
            for edge in nodes[y - 1][x].edges:
                if matches(nodes[y][edge.dst_x], wanted):
                    nxt[edge.dst_x] = max(nxt.get(edge.dst_x, 0), charges)
            if wing_ok and charges > 0:
                for x2 in range(MAP_WIDTH):
                    if matches(nodes[y][x2], wanted):
                        nxt[x2] = max(nxt.get(x2, 0), charges - 1)
        if not nxt:
            return False, y, reachable_symbols(y, states)
        states = nxt
    return True, None, ""
