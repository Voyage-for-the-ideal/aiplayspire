"""Validated map DAG and resource-aware path traversal."""

from dataclasses import dataclass
from functools import lru_cache
from typing import Callable, Dict, Iterable, Optional, Sequence, Tuple, Union

from ..models import MapNodeState

Position = Tuple[int, int]


class MapGraphError(ValueError):
    pass


@dataclass(frozen=True)
class GraphEdge:
    source: Position
    target: Position
    winged: bool = False


@dataclass(frozen=True)
class RoutePath:
    nodes: Tuple[MapNodeState, ...]
    edges: Tuple[GraphEdge, ...] = ()
    wing_charges_remaining: int = 0


NodeRef = Union[MapNodeState, Position]


class MapGraph:
    def __init__(self, nodes: Sequence[MapNodeState]):
        if not nodes:
            raise MapGraphError("empty map graph")
        self._nodes: Dict[Position, MapNodeState] = {}
        for node in nodes:
            position = (node.x, node.y)
            if position in self._nodes:
                raise MapGraphError(f"duplicate map node {position}")
            self._nodes[position] = node

        edge_map = {}
        for position, node in self._nodes.items():
            edges = []
            seen = set()
            for child in node.children or []:
                target = (child.x, child.y)
                if target == position:
                    raise MapGraphError(f"self loop at {position}")
                if target not in self._nodes:
                    raise MapGraphError(f"missing child {target} from {position}")
                if child.y <= node.y:
                    raise MapGraphError(f"non-forward edge {position} -> {target}")
                identity = (target, bool(child.winged))
                if identity not in seen:
                    seen.add(identity)
                    edges.append(GraphEdge(position, target, bool(child.winged)))
            edge_map[position] = tuple(sorted(edges, key=lambda e: (e.target[1], e.target[0], e.winged)))
        self._edges: Dict[Position, Tuple[GraphEdge, ...]] = edge_map
        self.max_y = max(position[1] for position in self._nodes)
        first_position = min(self._nodes, key=lambda p: (p[1], p[0]))
        self.start_node = self._nodes[first_position]

    @property
    def nodes(self) -> Tuple[MapNodeState, ...]:
        return tuple(self._nodes[p] for p in sorted(self._nodes, key=lambda p: (p[1], p[0])))

    @staticmethod
    def _position(node: NodeRef) -> Position:
        return (node.x, node.y) if isinstance(node, MapNodeState) else node

    def get(self, x: int, y: int) -> Optional[MapNodeState]:
        return self._nodes.get((x, y))

    def edges_from(self, node: NodeRef, wing_charges: Optional[int] = None) -> Tuple[GraphEdge, ...]:
        edges = self._edges.get(self._position(node), ())
        if wing_charges is None:
            return edges
        return tuple(edge for edge in edges if not edge.winged or wing_charges > 0)

    def children(self, node: NodeRef, wing_charges: Optional[int] = None) -> Tuple[MapNodeState, ...]:
        return tuple(self._nodes[edge.target] for edge in self.edges_from(node, wing_charges))

    def paths_from(self, node: NodeRef, depth: int, wing_charges: int = 0):
        """Return deterministic paths containing the start plus ``depth`` edges."""
        if depth < 0:
            return []
        start = self._position(node)
        if start not in self._nodes:
            raise MapGraphError(f"unknown start node {start}")

        @lru_cache(maxsize=None)
        def walk(position: Position, remaining_depth: int, charges: int):
            current = self._nodes[position]
            if remaining_depth <= 0:
                return ((current,), (), charges),
            legal_edges = self.edges_from(position, charges)
            if not legal_edges:
                return ((current,), (), charges),
            results = []
            for edge in legal_edges:
                next_charges = charges - 1 if edge.winged else charges
                for child_nodes, child_edges, final_charges in walk(
                        edge.target, remaining_depth - 1, next_charges):
                    results.append(((current,) + child_nodes, (edge,) + child_edges, final_charges))
            return tuple(results)

        return [RoutePath(*parts) for parts in walk(start, depth, max(0, wing_charges))]

    def can_reach(
        self,
        node: NodeRef,
        predicate: Callable[[MapNodeState], bool],
        wing_charges: int = 0,
        include_start: bool = True,
    ) -> bool:
        start = self._position(node)
        if start not in self._nodes:
            return False
        pending = [(start, max(0, wing_charges))]
        visited = set()
        while pending:
            position, charges = pending.pop()
            marker = (position, charges)
            if marker in visited:
                continue
            visited.add(marker)
            current = self._nodes[position]
            if (include_start or position != start) and predicate(current):
                return True
            for edge in reversed(self.edges_from(position, charges)):
                pending.append((edge.target, charges - 1 if edge.winged else charges))
        return False
