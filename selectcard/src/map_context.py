"""Map / key / history context features for the map-conditioned value network.

Two producers share one packed feature layout so that training samples and
live inference feed the model identically:

- archived human runs: reconstruct each act's true map from ``seed_played``
  (:mod:`sts_mapgen`) and derive ``[HISTORY]`` tokens (rooms already walked
  this act) from ``path_per_floor``;
- live framework states: encode the current act map, the current node, the
  key state, and path history directly from a normalized state dictionary.

Packed layout (one act, fixed size, no ragged lists):

- ``map_rooms``: 105 ints (7 columns x 15 rows, row-major index ``y*7+x``);
  0 = no room (unconnected node), otherwise a :data:`ROOM_IDS` symbol id.
- ``map_flags``: 105 ints, per-cell bit mask:
    bit 0   valid      the cell is a connected room used by this sample
    bit 1   burning    elite marked with the emerald flame
    bit 2   current_candidate  possible current position (all zero = unknown)
    bit 3   reachable  reachable from the current position in a future row
    bits 4-5 out_deg   outgoing edge count (0-3)
    bits 6-7 in_deg    incoming parent count (0-3)
- ``history_rooms``: :data:`HISTORY_HORIZON` room ids of the current act's
  realized path before the current floor, most-recent-first (index 0 = the
  room just left), right-padded with 0; no future information.  Earlier
  acts are not spliced in.
- ``key_states``: ``[ruby, emerald, sapphire]`` flags (0/1).
- ``key_known_mask``: whether each corresponding key flag is observable from
  the source.  Archived ``.run`` files do not identify Sapphire acquisition,
  so their pre-Act-IV Sapphire value is unknown rather than a fabricated 0.

Approximations (documented, deliberately conservative):

- Current candidates come from a forward DP over the observed prefix only.
  Multiple candidates express uncertainty, never a fabricated exact position.
- ``map_outgoing_mask`` holds three bits per cell: left/straight/right edges
  to the next row. Row 14 connects to one virtual Boss (out-degree one).
- Emerald keys are marked as collected on floors after the burning elite's
  floor when that node lies on a consistent path.  Sapphire keys are not
  recoverable from archived logs before Act IV and are masked as unknown.
- ``emerald_elite_enabled`` marks one reconstructed burning elite per act.
  Whether the 2020-era player had account-wide Act 4 unlocked is unknown;
  a marked-but-actually-absent flame is mild feature noise that never
  fabricates a collected key.
"""

from __future__ import annotations

from functools import lru_cache

try:
    from . import sts_mapgen
except ImportError:  # pragma: no cover - package vs script import
    import sts_mapgen

MAP_CONTEXT_VERSION = "map-context-v5-current-edges"
MAP_GRID_CELLS = sts_mapgen.MAP_HEIGHT * sts_mapgen.MAP_WIDTH  # 105
HISTORY_HORIZON = 8
MAP_FLAG_FEATURES = 7


def context_schema():
    """Stamped into v3 checkpoints and validated on load."""
    return {
        "map_context_version": MAP_CONTEXT_VERSION,
        "history_horizon": HISTORY_HORIZON,
        "map_grid_cells": MAP_GRID_CELLS,
        "map_flag_features": MAP_FLAG_FEATURES,
        "key_feature_count": 9,
        "key_observability": "value-plus-known-mask-v1",
        "coordinate_order": "xy-normalized",
        "position_semantics": "current-candidates-v1",
        "edge_encoding": "next-row-left-straight-right-v1",
    }

ROOM_SYMBOL_TO_ID = {"M": 1, "E": 2, "R": 3, "$": 4, "T": 5, "?": 6, "B": 7}
ROOM_ID_TO_SYMBOL = {value: key for key, value in ROOM_SYMBOL_TO_ID.items()}
KEY_ROOM_IDS = frozenset(ROOM_SYMBOL_TO_ID.values())

FLAG_VALID = 1 << 0
FLAG_BURNING = 1 << 1
FLAG_CURRENT_CANDIDATE = 1 << 2
FLAG_REACHABLE = 1 << 3
FLAG_OUT_DEG = 1 << 4   # two bits
FLAG_IN_DEG = 1 << 6    # two bits


def normalize_graph(map_nodes):
    """Normalize both producers to unique, valid next-row grid edges.

    Ignore dangling destinations; reject in-grid non-vanilla edges rather
    than silently encoding an incorrect graph. Boss coordinates are external
    to the 105-cell grid and canonicalized separately.
    """
    grid = {}
    for raw in map_nodes:
        x, y = int(raw["x"]), int(raw["y"])
        if not (0 <= x < 7 and 0 <= y < 15):
            continue
        symbol = str(raw.get("symbol", "?")).upper()
        if symbol not in ROOM_SYMBOL_TO_ID:
            continue
        xy = (x, y)
        if xy in grid:
            raise ValueError(f"Duplicate map node: {xy}")
        grid[xy] = {**raw, "symbol": symbol, "children": list(raw.get("children") or [])}
    edges = {xy: set() for xy in grid}
    for (x, y), node in grid.items():
        for child in node["children"]:
            dst = (int(child["x"]), int(child["y"]))
            if dst not in grid:
                continue
            if y == 14 or dst[1] != y + 1 or abs(dst[0] - x) > 1:
                raise ValueError(f"Unsupported map edge: {(x, y)} -> {dst}")
            edges[(x, y)].add(dst)
    return grid, edges


def pack_graph(map_nodes, current_candidates=()):
    """Shared graph packing; degrees and reachability use final unique edges."""
    grid, edges = normalize_graph(map_nodes)
    current = {tuple(xy) for xy in current_candidates if tuple(xy) in grid}
    incoming = {xy: 0 for xy in grid}
    for destinations in edges.values():
        for dst in destinations:
            incoming[dst] += 1
    reachable = set()
    frontier = set(current)
    while frontier:
        frontier = {dst for src in frontier for dst in edges[src]} - reachable
        reachable.update(frontier)
    rooms, flags, outgoing = ([0] * MAP_GRID_CELLS for _ in range(3))
    for (x, y), node in grid.items():
        index = y * 7 + x
        rooms[index] = ROOM_SYMBOL_TO_ID[node["symbol"]]
        bits = FLAG_VALID
        if node.get("burning"):
            bits |= FLAG_BURNING
        if (x, y) in current:
            bits |= FLAG_CURRENT_CANDIDATE
        if (x, y) in reachable:
            bits |= FLAG_REACHABLE
        bits |= (1 if y == 14 else len(edges[(x, y)])) << 4
        bits |= incoming[(x, y)] << 6
        flags[index] = bits
        for dx, _ in edges[(x, y)]:
            outgoing[index] |= 1 << (dx - x + 1)
    return rooms, flags, outgoing


def nodes_as_payload(nodes):
    """Adapt immutable mapgen nodes without mutating its game-faithful data."""
    return [
        {"x": n.x, "y": n.y, "symbol": _node_symbol(n), "burning": n.burning,
         "children": [{"x": e.dst_x, "y": e.dst_y} for e in n.edges]}
        for row in nodes for n in row
        if n.room is not None and (n.has_edges or n.parents)
    ]

WINGED_GREAVES_KEYS = ("wingedgreaves", "wingboots")
WING_CHARGES = 3

# EventHelper can turn a '?' node into any of these on entry.
_EVENT_ROOM_ALIASES = {"?": ("?", "M", "$", "T")}


def _node_symbol(node) -> str:
    return sts_mapgen.ROOM_SYMBOLS.get(node.room, "?")


def _matches_symbol(node, wanted: str) -> bool:
    if node.room is None:
        return False
    if node.room == sts_mapgen.EVENT:
        return wanted in _EVENT_ROOM_ALIASES["?"]
    return sts_mapgen.ROOM_SYMBOLS[node.room] == wanted


def split_act_segments(path_per_floor) -> list[tuple[int, list[str]]]:
    """Split ``path_per_floor`` into ``(start_index, symbols)`` act segments.

    ``start_index`` is the 0-based index into the flat list (floor 1 is
    index 0).  Inter-act ``None`` separators are skipped; the trailing ``B``
    closes each segment.
    """
    segments: list[tuple[int, list[str]]] = []
    current: list[str] = []
    current_start = 0
    for index, value in enumerate(path_per_floor or []):
        if value is None:
            if current:
                segments.append((current_start, current))
                current = []
            continue
        if value == "B":
            current.append(value)
            segments.append((current_start, current))
            current = []
            continue
        if not current:
            current_start = index
        current.append(value)
    if current:
        segments.append((current_start, current))
    return segments


def segment_of_floor(segments, floor: int):
    """Return ``(act_number, segment_index, row)`` for a 1-based floor."""
    global_index = floor - 1
    for segment_index, (start, symbols) in enumerate(segments):
        row = global_index - start
        if 0 <= row < len(symbols):
            return segment_index + 1, segment_index, row
    return None


def history_symbols(segments, segment_index: int, row: int) -> list[str]:
    """Current-act realized rooms before ``row``, most-recent-first.

    Returns at most :data:`HISTORY_HORIZON` symbols.  Earlier acts are
    deliberately not spliced in.
    """
    _, symbols = segments[segment_index]
    window = symbols[max(0, row - HISTORY_HORIZON):row]
    return list(reversed(window))


def wing_pickup_floor(event_data) -> int | None:
    """Global floor on which WingedGreaves was obtained, if ever."""
    pickup = None
    for item in event_data.get("relics_obtained") or []:
        key = str(item.get("key", "")).replace(" ", "").lower() if isinstance(item, dict) else ""
        if key in WINGED_GREAVES_KEYS:
            floor = item.get("floor")
            if isinstance(floor, (int, float)):
                value = int(floor)
                pickup = value if pickup is None else min(pickup, value)
    if pickup is None:
        relics = {str(r).replace(" ", "").lower() for r in event_data.get("relics") or []}
        if relics & set(WINGED_GREAVES_KEYS):
            pickup = 0  # obtained before logging started
    return pickup


@lru_cache(maxsize=8192)
def _cached_act_map(seed: int, act: int, ascension: int):
    # MapRoomNode objects are treated as immutable by every consumer here.
    return sts_mapgen.generate_act_map(seed, act, ascension_level=ascension)


def forward_consistent_positions(nodes, symbols, wing_from_row=None):
    """Row-by-row node columns consistent with a realized symbol sequence.

    Returns a list of ``set[int]`` (one per row, len == len(symbols)); an
    empty set means the sequence cannot be placed on this map.  Wing jumps
    are modelled like :func:`sts_mapgen.path_is_legal`: charges are granted
    from the pickup row and every non-adjacent move spends one.
    """
    states: dict[int, int] = {}
    rows: list[set[int]] = []
    for x in range(sts_mapgen.MAP_WIDTH):
        node = nodes[0][x]
        if node.has_edges and _matches_symbol(node, symbols[0]):
            states[x] = WING_CHARGES if (wing_from_row is not None and wing_from_row <= 0) else 0
    rows.append(set(states))
    for y in range(1, len(symbols)):
        if wing_from_row is not None and y >= wing_from_row:
            for x in states:
                states[x] = max(states[x], WING_CHARGES)
        wanted = symbols[y]
        nxt: dict[int, int] = {}
        for x, charges in states.items():
            for edge in nodes[y - 1][x].edges:
                if _matches_symbol(nodes[y][edge.dst_x], wanted):
                    nxt[edge.dst_x] = max(nxt.get(edge.dst_x, 0), charges)
            if charges > 0 and wing_from_row is not None and y >= wing_from_row:
                for x2 in range(sts_mapgen.MAP_WIDTH):
                    if _matches_symbol(nodes[y][x2], wanted):
                        nxt[x2] = max(nxt.get(x2, 0), charges - 1)
        rows.append(set(nxt))
        states = nxt
    return rows


def suffix_feasible_from(nodes, row: int, x: int, symbols) -> bool:
    """Can ``symbols`` (starting at ``row``) be walked starting from column x?"""
    states: dict[int, int] = {x: WING_CHARGES}
    for y in range(row + 1, row + len(symbols)):
        if y >= sts_mapgen.MAP_HEIGHT:
            return True
        wanted = symbols[y - row]
        nxt: dict[int, int] = {}
        for sx, charges in states.items():
            for edge in nodes[y - 1][sx].edges:
                if _matches_symbol(nodes[y][edge.dst_x], wanted):
                    nxt[edge.dst_x] = max(nxt.get(edge.dst_x, 0), charges)
        if not nxt:
            return False
        states = nxt
    return True


def reachable_rows(nodes, current_row: int, current_xs) -> list[set[int]]:
    """Graph reachability per future row from the current positions (no wings)."""
    reach: list[set[int]] = [set() for _ in range(sts_mapgen.MAP_HEIGHT)]
    frontier = set(current_xs)
    for y in range(current_row + 1, sts_mapgen.MAP_HEIGHT):
        nxt: set[int] = set()
        for x in frontier:
            for edge in nodes[y - 1][x].edges:
                if nodes[y][edge.dst_x].room is not None:
                    nxt.add(edge.dst_x)
        reach[y] = nxt
        frontier = nxt
        if not frontier:
            break
    return reach


def recall_floor(event_data) -> int | None:
    """First campfire floor on which RECALL was chosen (Ruby key)."""
    for choice in event_data.get("campfire_choices") or []:
        if isinstance(choice, dict) and str(choice.get("key", "")).upper() == "RECALL":
            floor = choice.get("floor")
            if isinstance(floor, (int, float)):
                return int(floor)
    return None


def burning_elite_floor(nodes, segments, segment_index: int) -> int | None:
    """Global floor of the burning elite if it lies on a consistent path."""
    start, symbols = segments[segment_index]
    burning = [
        (node.x, node.y)
        for row in nodes
        for node in row
        if node.room == sts_mapgen.ELITE and node.burning
    ]
    if not burning:
        return None
    bx, by = burning[0]
    if by >= len(symbols):
        return None
    rows = forward_consistent_positions(nodes, symbols[: by + 1])
    if not rows[by] or bx not in rows[by]:
        return None
    if not suffix_feasible_from(nodes, by, bx, symbols[by:]):
        return None
    return start + by + 1  # 1-based global floor of that row


class RunMapContextBuilder:
    """Builds per-snapshot packed map context for one archived run."""

    def __init__(self, event_data):
        self.seed = self._standard_seed(event_data)
        self.segments = split_act_segments(event_data.get("path_per_floor"))
        self.ascension = int(event_data.get("ascension_level", 0) or 0)
        self.wing_floor = wing_pickup_floor(event_data)
        self.recall = recall_floor(event_data)
        self.act_maps: dict[int, list] = {}
        self.emerald_floors: dict[int, int] = {}
        if self.seed is not None:
            for act in (1, 2, 3):
                try:
                    nodes = _cached_act_map(self.seed, act, self.ascension)
                except Exception:  # noqa: BLE001 - missing context beats a crash
                    continue
                self.act_maps[act] = nodes
                if act - 1 < len(self.segments):
                    floor = burning_elite_floor(nodes, self.segments, act - 1)
                    if floor is not None:
                        self.emerald_floors[act] = floor
        self._row_cache: dict[tuple, list[set[int]]] = {}

    @staticmethod
    def _standard_seed(event_data):
        if any(event_data.get(flag) for flag in ("is_daily", "is_trial", "is_endless", "chose_seed", "is_beta")):
            return None
        if event_data.get("special_seed", 0):
            return None
        seed = event_data.get("seed_played")
        try:
            return int(seed)
        except (TypeError, ValueError):
            return None

    def _positions(self, segment_index: int, prefix_len: int):
        key = (segment_index, prefix_len)
        if key not in self._row_cache:
            start, symbols = self.segments[segment_index]
            nodes = self.act_maps.get(segment_index + 1)
            if nodes is None or prefix_len <= 0 or prefix_len > len(symbols):
                self._row_cache[key] = None
            else:
                wing_row = None
                if self.wing_floor is not None:
                    wing_row = max(0, self.wing_floor - 1 - start)
                self._row_cache[key] = forward_consistent_positions(
                    nodes, symbols[:prefix_len], wing_from_row=wing_row
                )
        return self._row_cache[key]

    def context_for_floor(self, floor: int):
        """Packed context dict for one snapshot floor, or ``None``."""
        located = segment_of_floor(self.segments, floor)
        if located is None:
            return None
        act, segment_index, row = located
        start, symbols = self.segments[segment_index]
        nodes = self.act_maps.get(act)
        walked = history_symbols(self.segments, segment_index, row)
        key_states, key_known_mask = self._key_context(act, floor)

        candidates = self._positions(segment_index, row + 1) or []
        current = [(x, row) for x in candidates[row]] if row < len(candidates) else []
        map_rooms, map_flags, outgoing = pack_graph(
            nodes_as_payload(nodes) if nodes is not None else [], current
        )

        return {
            "act": act,
            "map_rooms": map_rooms,
            "map_flags": map_flags,
            "map_outgoing_mask": outgoing,
            "history_rooms": [ROOM_SYMBOL_TO_ID.get(s, 0) for s in walked]
            + [0] * (HISTORY_HORIZON - len(walked)),
            "key_states": key_states,
            "key_known_mask": key_known_mask,
        }

    def _key_context(self, act: int, floor: int):
        # Reaching Act IV strictly proves that all three keys were collected.
        if act >= 4:
            return [1, 1, 1], [1, 1, 1]
        ruby = 1 if self.recall is not None and floor > self.recall else 0
        emerald_floor = self.emerald_floors.get(act)
        emerald = 1 if emerald_floor is not None and floor > emerald_floor else 0
        emerald_known = 1 if act in self.act_maps else 0
        # A missing relic on a T floor is ambiguous: it may be an unopened
        # chest, an abandoned reward screen, or a Sapphire pick.  Never infer
        # Sapphire from that absence.
        return [ruby, emerald, 0], [1, emerald_known, 0]


def build_run_contexts(event_data, snapshot_floors):
    """Convenience wrapper: contexts for every snapshot floor of one run."""
    builder = RunMapContextBuilder(event_data)
    return [builder.context_for_floor(floor) for floor in snapshot_floors]


# ---------------------------------------------------------------------------
# Live-inference side: encode a framework state into the same packed layout.
# ---------------------------------------------------------------------------

def inference_context(state: dict):
    """Pack a framework-style state dict into model-ready context arrays.

    Expected optional keys (all missing keys degrade gracefully):
      ``act``:                act number (1-3), required for map packing
      ``floor_in_act``:       0-based row of the current node
      ``current_node``:       ``(x, y)`` of the current map node or None
      ``map_nodes``:          list of ``{x, y, symbol, burning, children:[{x,y}]}``
      ``history``:            current-act walked room symbols, oldest first
      ``keys``:               ``{ruby, emerald, sapphire}`` booleans
    """
    act = state.get("act")
    floor_in_act = state.get("floor_in_act")
    map_nodes = state.get("map_nodes") or []
    walked = [
        s for s in (state.get("history") or []) if s in ROOM_SYMBOL_TO_ID
    ][-HISTORY_HORIZON:][::-1]
    raw_keys = state.get("keys")
    keys = raw_keys if isinstance(raw_keys, dict) else {}

    current = state.get("current_node")
    map_rooms, map_flags, outgoing = pack_graph(
        map_nodes if act in (1, 2, 3) else [],
        [current] if current is not None else [],
    )

    return {
        "act": act if isinstance(act, int) else 0,
        "map_rooms": map_rooms,
        "map_flags": map_flags,
        "map_outgoing_mask": outgoing,
        "history_rooms": [ROOM_SYMBOL_TO_ID.get(s, 0) for s in walked]
        + [0] * (HISTORY_HORIZON - len(walked)),
        "key_states": [
            int(bool(keys.get("ruby"))),
            int(bool(keys.get("emerald"))),
            int(bool(keys.get("sapphire"))),
        ],
        # Framework payloads include explicit key fields even if the map is
        # unavailable. Generic callers that omit them stay unknown.
        "key_known_mask": [
            int(isinstance(raw_keys, dict) and name in raw_keys)
            for name in ("ruby", "emerald", "sapphire")
        ],
    }


def reachable_rows_from_children(grid, current_row, current):
    rows: dict[int, set[int]] = {}
    if current is None:
        return rows
    frontier = {tuple(current)}
    for y in range(int(current[1]) + 1, sts_mapgen.MAP_HEIGHT):
        nxt: set[int] = set()
        for x, cy in frontier:
            node = grid.get((x, cy))
            for child in (node or {}).get("children") or []:
                cx, cy2 = int(child.get("x", -1)), int(child.get("y", -1))
                if (cx, cy2) in grid:
                    nxt.add((cx, cy2))
        rows[y] = {x for x, _ in nxt}
        frontier = nxt
        if not frontier:
            break
    return rows


def decode_map_features(map_rooms, map_flags):
    """Decode packed columns into ``(room_ids, flag_matrix)`` float tensors data.

    Returns ``(rooms: list[int], flags: list[list[float]])`` where each flag
    row is ``[burning, current_candidate, reachable, x/6, y/14, out_deg/3, in_deg/3]``.
    """
    flags_out = []
    for index in range(MAP_GRID_CELLS):
        bits = int(map_flags[index]) if map_flags is not None else 0
        y, x = divmod(index, sts_mapgen.MAP_WIDTH)
        flags_out.append([
            float(bool(bits & FLAG_BURNING)),
            float(bool(bits & FLAG_CURRENT_CANDIDATE)),
            float(bool(bits & FLAG_REACHABLE)),
            x / (sts_mapgen.MAP_WIDTH - 1),
            y / (sts_mapgen.MAP_HEIGHT - 1),
            ((bits >> 4) & 3) / 3.0,
            ((bits >> 6) & 3) / 3.0,
        ])
    return list(map_rooms), flags_out


def keys_token_features(key_states, map_flags, act: int, key_known_mask=None):
    """Feature vector for the ``[KEYS]`` context token.

    ``[ruby, emerald, sapphire, ruby_known, emerald_known, sapphire_known,
    burning_pending, known_collected/3, act/3]``
    """
    ruby, emerald, sapphire = (list(key_states) + [0, 0, 0])[:3]
    known = (list(key_known_mask) + [0, 0, 0])[:3] if key_known_mask is not None else [1, 1, 1]
    burning_pending = 0
    if known[1] and not emerald:
        burning_pending = int(any(
            bits & FLAG_BURNING for bits in (map_flags or [])
        ))
    collected = sum(value for value, is_known in zip((ruby, emerald, sapphire), known) if is_known)
    return [
        float(ruby), float(emerald), float(sapphire),
        *(float(value) for value in known),
        float(burning_pending), collected / 3.0, min(max(act, 0), 4) / 4.0,
    ]


__all__ = [
    "MAP_CONTEXT_VERSION", "MAP_GRID_CELLS", "HISTORY_HORIZON", "MAP_FLAG_FEATURES",
    "context_schema", "normalize_graph", "pack_graph", "nodes_as_payload",
    "ROOM_SYMBOL_TO_ID", "ROOM_ID_TO_SYMBOL", "KEY_ROOM_IDS",
    "FLAG_VALID", "FLAG_BURNING", "FLAG_CURRENT_CANDIDATE", "FLAG_REACHABLE",
    "FLAG_OUT_DEG", "FLAG_IN_DEG",
    "split_act_segments", "segment_of_floor", "history_symbols",
    "wing_pickup_floor",
    "forward_consistent_positions", "suffix_feasible_from",
    "reachable_rows", "recall_floor", "burning_elite_floor",
    "RunMapContextBuilder", "build_run_contexts",
    "inference_context", "decode_map_features", "keys_token_features",
]
