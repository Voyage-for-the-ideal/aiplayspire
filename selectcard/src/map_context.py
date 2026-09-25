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
    bit 2   visited    on the realized path before the current floor
    bit 3   reachable  reachable from the current position in a future row
    bits 4-5 out_deg   outgoing edge count (0-3)
    bits 6-7 in_deg    incoming parent count (0-3)
- ``history_rooms``: :data:`HISTORY_HORIZON` room ids of the current act's
  realized path before the current floor, most-recent-first (index 0 = the
  room just left), right-padded with 0; no future information.  Earlier
  acts are not spliced in.
- ``key_states``: ``[ruby, emerald, sapphire]`` flags (0/1).

Approximations (documented, deliberately conservative):

- "visited" positions come from a forward DP over the realized
  symbol sequence.  When several same-symbol nodes are consistent with the
  log, all of them are marked; ambiguity is rare (the reconstruction
  validator measures the model as >=99.9% path-consistent).
- Emerald keys are marked as collected on floors after the burning elite's
  floor when that node lies on a consistent path.  Sapphire keys are not
  recoverable from archived logs and stay 0.
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

MAP_CONTEXT_VERSION = "map-context-v3"
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
    }

ROOM_SYMBOL_TO_ID = {"M": 1, "E": 2, "R": 3, "$": 4, "T": 5, "?": 6, "B": 7}
ROOM_ID_TO_SYMBOL = {value: key for key, value in ROOM_SYMBOL_TO_ID.items()}
KEY_ROOM_IDS = frozenset(ROOM_SYMBOL_TO_ID.values())

FLAG_VALID = 1 << 0
FLAG_BURNING = 1 << 1
FLAG_VISITED = 1 << 2
FLAG_REACHABLE = 1 << 3
FLAG_OUT_DEG = 1 << 4   # two bits
FLAG_IN_DEG = 1 << 6    # two bits

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
        key_states = self._key_states(act, floor)

        map_rooms = [0] * MAP_GRID_CELLS
        map_flags = [0] * MAP_GRID_CELLS

        if nodes is not None:
            visited_rows = self._positions(segment_index, row + 1) or []
            reach = reachable_rows(nodes, row, visited_rows[row] if visited_rows else set())
            for y in range(sts_mapgen.MAP_HEIGHT):
                for x in range(sts_mapgen.MAP_WIDTH):
                    node = nodes[y][x]
                    if node.room is None:
                        continue
                    if not (node.has_edges or node.parents):
                        continue  # unconnected fixed-row cells carry no signal
                    index = y * sts_mapgen.MAP_WIDTH + x
                    map_rooms[index] = ROOM_SYMBOL_TO_ID.get(_node_symbol(node), 0)
                    flags = FLAG_VALID
                    if node.burning:
                        flags |= FLAG_BURNING
                    if y <= row and visited_rows and x in visited_rows[y]:
                        flags |= FLAG_VISITED
                    if y > row and x in reach[y]:
                        flags |= FLAG_REACHABLE
                    flags |= min(len(node.edges), 3) << 4
                    flags |= min(len(node.parents), 3) << 6
                    map_flags[index] = flags

        return {
            "act": act,
            "map_rooms": map_rooms,
            "map_flags": map_flags,
            "history_rooms": [ROOM_SYMBOL_TO_ID.get(s, 0) for s in walked]
            + [0] * (HISTORY_HORIZON - len(walked)),
            "key_states": key_states,
        }

    def _key_states(self, act: int, floor: int):
        ruby = 1 if self.recall is not None and floor > self.recall else 0
        emerald_floor = self.emerald_floors.get(act)
        emerald = 1 if emerald_floor is not None and floor > emerald_floor else 0
        return [ruby, emerald, 0]


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
    keys = state.get("keys") or {}

    map_rooms = [0] * MAP_GRID_CELLS
    map_flags = [0] * MAP_GRID_CELLS
    current = state.get("current_node")

    grid: dict[tuple[int, int], dict] = {}
    for node in map_nodes:
        x, y = int(node.get("x", 0)), int(node.get("y", 0))
        if 0 <= x < sts_mapgen.MAP_WIDTH and 0 <= y < sts_mapgen.MAP_HEIGHT:
            grid[(x, y)] = node
    parent_counts: dict[tuple[int, int], int] = {}
    for node in grid.values():
        for child in node.get("children") or []:
            child_xy = (int(child.get("x", -1)), int(child.get("y", -1)))
            if child_xy in grid:
                parent_counts[child_xy] = parent_counts.get(child_xy, 0) + 1

    if act is not None and grid:
        row = (
            int(current[1]) if current is not None
            else (int(floor_in_act) if floor_in_act is not None else None)
        )
        reach = reachable_rows_from_children(grid, row, current)
        for (x, y), node in grid.items():
            symbol = str(node.get("symbol", "?")).upper()
            room_id = ROOM_SYMBOL_TO_ID.get(symbol, 0)
            if room_id == 0:
                continue
            index = y * sts_mapgen.MAP_WIDTH + x
            map_rooms[index] = room_id
            flags = FLAG_VALID
            if node.get("burning"):
                flags |= FLAG_BURNING
            if current is not None and (x, y) == tuple(current):
                flags |= FLAG_VISITED
            if y in reach and x in reach[y]:
                flags |= FLAG_REACHABLE
            flags |= min(len(node.get("children") or []), 3) << 4
            flags |= min(parent_counts.get((x, y), 0), 3) << 6
            map_flags[index] = flags

    return {
        "act": act if isinstance(act, int) else 0,
        "map_rooms": map_rooms,
        "map_flags": map_flags,
        "history_rooms": [ROOM_SYMBOL_TO_ID.get(s, 0) for s in walked]
        + [0] * (HISTORY_HORIZON - len(walked)),
        "key_states": [
            int(bool(keys.get("ruby"))),
            int(bool(keys.get("emerald"))),
            int(bool(keys.get("sapphire"))),
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
    row is ``[burning, visited, reachable, x/6, y/14, out_deg/3, in_deg/3]``.
    """
    flags_out = []
    for index in range(MAP_GRID_CELLS):
        bits = int(map_flags[index]) if map_flags is not None else 0
        y, x = divmod(index, sts_mapgen.MAP_WIDTH)
        flags_out.append([
            float(bool(bits & FLAG_BURNING)),
            float(bool(bits & FLAG_VISITED)),
            float(bool(bits & FLAG_REACHABLE)),
            x / (sts_mapgen.MAP_WIDTH - 1),
            y / (sts_mapgen.MAP_HEIGHT - 1),
            ((bits >> 4) & 3) / 3.0,
            ((bits >> 6) & 3) / 3.0,
        ])
    return list(map_rooms), flags_out


def keys_token_features(key_states, map_flags, act: int):
    """Feature vector for the ``[KEYS]`` context token.

    ``[ruby, emerald, sapphire, burning_pending, collected/3, act/3]``
    """
    ruby, emerald, sapphire = (list(key_states) + [0, 0, 0])[:3]
    burning_pending = 0
    if not emerald:
        burning_pending = int(any(
            bits & FLAG_BURNING for bits in (map_flags or [])
        ))
    collected = ruby + emerald + sapphire
    return [
        float(ruby), float(emerald), float(sapphire),
        float(burning_pending), collected / 3.0, min(max(act, 0), 3) / 3.0,
    ]


__all__ = [
    "MAP_CONTEXT_VERSION", "MAP_GRID_CELLS", "HISTORY_HORIZON", "MAP_FLAG_FEATURES",
    "context_schema",
    "ROOM_SYMBOL_TO_ID", "ROOM_ID_TO_SYMBOL", "KEY_ROOM_IDS",
    "FLAG_VALID", "FLAG_BURNING", "FLAG_VISITED", "FLAG_REACHABLE",
    "FLAG_OUT_DEG", "FLAG_IN_DEG",
    "split_act_segments", "segment_of_floor", "history_symbols",
    "wing_pickup_floor",
    "forward_consistent_positions", "suffix_feasible_from",
    "reachable_rows", "recall_floor", "burning_elite_floor",
    "RunMapContextBuilder", "build_run_contexts",
    "inference_context", "decode_map_features", "keys_token_features",
]
