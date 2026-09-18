"""Route-structure features independent from scoring weights."""

from dataclasses import dataclass
from typing import Tuple, Union

from ..models import MapNodeState
from .context import RouteContext
from .map_graph import MapGraph, RoutePath
from .policy import RoutePolicyConfig


def _symbol(node: MapNodeState) -> str:
    return (node.symbol or "?").upper()


def _combat_streak(nodes, stop_at_rest=False):
    longest = current = 0
    for node in nodes:
        symbol = _symbol(node)
        if stop_at_rest and symbol == "R":
            break
        if symbol in ("M", "E"):
            current += 1
            longest = max(longest, current)
        else:
            current = 0
    return longest


@dataclass(frozen=True)
class RouteFeatures:
    path_count: int
    first_rest_distance: int
    first_shop_distance: int
    first_elite_distance: int
    forced_monsters: int
    forced_elites: int
    max_combat_streak: int
    forced_elite_within_horizon: bool
    forced_three_combats_before_rest: bool
    optionality_pivots: int
    has_safe_branch: bool
    has_aggressive_branch: bool
    reaches_rest: bool
    reaches_burning_elite: bool
    ruby_key_missing: bool
    emerald_key_missing: bool
    sapphire_key_missing: bool
    hp_ratio: float
    hp_state: str
    gold: int
    gold_state: str
    event_prob_elite: float
    event_prob_monster: float
    event_prob_shop: float
    event_prob_treasure: float
    event_prob_event: float


class RouteFeatureExtractor:
    SAFE = frozenset(("R", "$", "?", "T"))
    AGGRESSIVE = frozenset(("E",))

    def __init__(self, graph: MapGraph, policy: RoutePolicyConfig = None):
        self.graph = graph
        self.policy = policy or RoutePolicyConfig()

    def extract(
        self,
        candidate: Union[MapNodeState, RoutePath],
        context: RouteContext,
    ) -> RouteFeatures:
        start = candidate.nodes[0] if isinstance(candidate, RoutePath) else candidate
        paths = self.graph.paths_from(
            start, self.policy.lookahead_depth - 1, context.wing_charges
        )
        if not paths:
            paths = [RoutePath((start,), (), context.wing_charges)]

        def first_distance(target):
            distances = [
                index + 1
                for path in paths
                for index, node in enumerate(path.nodes)
                if _symbol(node) == target
            ]
            return min(distances) if distances else -1

        monsters = [sum(_symbol(node) == "M" for node in path.nodes) for path in paths]
        elites = [sum(_symbol(node) == "E" for node in path.nodes) for path in paths]
        forced_elite_near = all(
            any(_symbol(node) == "E" for node in path.nodes[:self.policy.unsafe_elite_horizon])
            for path in paths
        )
        forced_streak = all(
            _combat_streak(path.nodes, stop_at_rest=True) >= self.policy.unsafe_combat_streak
            for path in paths
        )

        pivot_positions = set()
        has_safe = has_aggressive = False
        for path in paths:
            for index, node in enumerate(path.nodes):
                spent = sum(edge.winged for edge in path.edges[:index])
                remaining_charges = max(0, context.wing_charges - spent)
                child_symbols = {
                    _symbol(child) for child in self.graph.children(node, remaining_charges)
                }
                safe = bool(child_symbols & self.SAFE)
                aggressive = bool(child_symbols & self.AGGRESSIVE)
                has_safe = has_safe or safe
                has_aggressive = has_aggressive or aggressive
                if safe and aggressive:
                    pivot_positions.add((node.x, node.y))

        return RouteFeatures(
            path_count=len(paths),
            first_rest_distance=first_distance("R"),
            first_shop_distance=first_distance("$"),
            first_elite_distance=first_distance("E"),
            forced_monsters=min(monsters),
            forced_elites=min(elites),
            max_combat_streak=max(_combat_streak(path.nodes) for path in paths),
            forced_elite_within_horizon=forced_elite_near,
            forced_three_combats_before_rest=forced_streak,
            optionality_pivots=len(pivot_positions),
            has_safe_branch=has_safe,
            has_aggressive_branch=has_aggressive,
            reaches_rest=self.graph.can_reach(
                start, lambda node: _symbol(node) == "R", context.wing_charges
            ),
            reaches_burning_elite=self.graph.can_reach(
                start,
                lambda node: _symbol(node) == "E" and bool(node.burning),
                context.wing_charges,
            ),
            ruby_key_missing=not context.ruby_key,
            emerald_key_missing=not context.emerald_key,
            sapphire_key_missing=not context.sapphire_key,
            hp_ratio=context.hp_ratio,
            hp_state=context.hp_state.value,
            gold=context.gold,
            gold_state=context.gold_state.value,
            event_prob_elite=context.chance("elite"),
            event_prob_monster=context.chance("monster"),
            event_prob_shop=context.chance("shop"),
            event_prob_treasure=context.chance("treasure"),
            event_prob_event=context.chance("event"),
        )
