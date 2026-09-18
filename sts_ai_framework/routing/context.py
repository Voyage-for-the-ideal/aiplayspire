"""Immutable planner context derived from the raw protocol model."""

from dataclasses import dataclass
from enum import Enum
from typing import Tuple

from ..models import GameState, PathHistoryEntry
from .map_graph import MapGraph
from .policy import RoutePolicyConfig


class HpState(str, Enum):
    CRITICAL = "CRITICAL"
    LOW = "LOW"
    NORMAL = "NORMAL"
    HIGH = "HIGH"


class GoldState(str, Enum):
    VERY_LOW = "VERY_LOW"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


@dataclass(frozen=True)
class RouteContext:
    act: int
    floor: int
    map_row: int
    hp: int
    max_hp: int
    hp_ratio: float
    hp_state: HpState
    gold: int
    gold_state: GoldState
    ruby_key: bool
    emerald_key: bool
    sapphire_key: bool
    potion_count: int
    upgradable_card_count: int
    boss_distance: int
    event_chances: Tuple[Tuple[str, float], ...]
    wing_charges: int
    path_history: Tuple[PathHistoryEntry, ...]
    character: str
    graph: MapGraph
    start_node: object

    @classmethod
    def from_game_state(cls, state: GameState, policy: RoutePolicyConfig = None):
        policy = policy or RoutePolicyConfig()
        if state.player is None:
            raise ValueError("MAP state has no player")
        hp_ratio = state.player.current_hp / max(1, state.player.max_hp)
        if hp_ratio < policy.critical_hp_ratio:
            hp_state = HpState.CRITICAL
        elif hp_ratio < policy.low_hp_ratio:
            hp_state = HpState.LOW
        elif hp_ratio < policy.high_hp_ratio:
            hp_state = HpState.NORMAL
        else:
            hp_state = HpState.HIGH

        gold = state.player.gold
        if gold < policy.gold_low:
            gold_state = GoldState.VERY_LOW
        elif gold < policy.gold_medium:
            gold_state = GoldState.LOW
        elif gold < policy.gold_high:
            gold_state = GoldState.MEDIUM
        else:
            gold_state = GoldState.HIGH

        raw_chances = state.event_chances
        chances = {
            "elite": max(0.0, float(raw_chances.elite)),
            "monster": max(0.0, float(raw_chances.monster)),
            "shop": max(0.0, float(raw_chances.shop)),
            "treasure": max(0.0, float(raw_chances.treasure)),
            "event": max(0.0, float(raw_chances.event)),
        }
        total = sum(chances.values())
        if total <= 0.0:
            chances = {"elite": 0.0, "monster": 0.10, "shop": 0.03,
                       "treasure": 0.02, "event": 0.85}
            total = 1.0
        normalized_chances = tuple((key, chances[key] / total) for key in
                                   ("elite", "monster", "shop", "treasure", "event"))

        wing_charges = 0
        for relic in state.relics or []:
            if relic.id == "WingedGreaves":
                wing_charges = max(0, relic.counter)
                break
        current = state.current_map_node
        map_row = current.y if current is not None else max(0, (state.map_position.floor if state.map_position else 0))
        graph = MapGraph(state.map_nodes)
        start_node = None
        if state.current_map_node is not None:
            start_node = graph.get(state.current_map_node.x, state.current_map_node.y)
        if start_node is None and state.current_map_choices:
            first = min(state.current_map_choices, key=lambda choice: choice.choice_index)
            start_node = graph.get(first.x, first.y)
        if start_node is None:
            raise ValueError("MAP state has no graph start node")

        return cls(
            act=max(1, state.act or 1),
            floor=max(0, state.floor or 0),
            map_row=map_row,
            hp=state.player.current_hp,
            max_hp=state.player.max_hp,
            hp_ratio=hp_ratio,
            hp_state=hp_state,
            gold=gold,
            gold_state=gold_state,
            ruby_key=bool(state.keys.ruby),
            emerald_key=bool(state.keys.emerald),
            sapphire_key=bool(state.keys.sapphire),
            potion_count=sum(1 for potion in state.potions or [] if not potion.is_empty),
            upgradable_card_count=sum(
                1 for card in state.deck or [] if card.can_upgrade and card.type != "CURSE"
            ),
            boss_distance=max(0, 15 - map_row),
            event_chances=normalized_chances,
            wing_charges=wing_charges,
            path_history=tuple(state.path_history or []),
            character=state.character or "UNKNOWN",
            graph=graph,
            start_node=start_node,
        )

    def chance(self, outcome: str) -> float:
        return dict(self.event_chances).get(outcome, 0.0)
