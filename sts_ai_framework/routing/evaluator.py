"""Rule utility evaluation for rooms, paths, and route candidates."""

from dataclasses import dataclass
from typing import Tuple

from ..models import MapNodeState
from .context import GoldState, HpState, RouteContext
from .decision import CandidateAssessment, ScoreBreakdown
from .features import RouteFeatureExtractor, RouteFeatures
from .map_graph import MapGraph, RoutePath
from .policy import RoutePolicyConfig


@dataclass(frozen=True)
class LocalRoomParts:
    value: float = 0.0
    key: float = 0.0
    risk: float = 0.0


class RouteEvaluator:
    def __init__(self, graph: MapGraph, policy: RoutePolicyConfig = None):
        self.graph = graph
        self.policy = policy or RoutePolicyConfig()
        self.features = RouteFeatureExtractor(graph, self.policy)

    def _hp_value(self, context, critical, low, normal, high):
        return {
            HpState.CRITICAL: critical,
            HpState.LOW: low,
            HpState.NORMAL: normal,
            HpState.HIGH: high,
        }[context.hp_state]

    def local_room_parts(
        self, symbol: str, burning: bool, context: RouteContext
    ) -> LocalRoomParts:
        symbol = (symbol or "?").upper()
        if symbol == "M":
            risk = self._hp_value(
                context,
                self.policy.monster_risk_critical,
                self.policy.monster_risk_low,
                self.policy.monster_risk_normal,
                self.policy.monster_risk_high,
            )
            return LocalRoomParts(self.policy.monster_growth, risk=risk)
        if symbol == "E":
            risk = self._hp_value(
                context,
                self.policy.elite_risk_critical,
                self.policy.elite_risk_low,
                self.policy.elite_risk_normal,
                self.policy.elite_risk_high,
            )
            if context.act == 2:
                risk *= self.policy.act_two_elite_multiplier
            risk -= min(context.potion_count, self.policy.max_buffered_potions) * self.policy.potion_elite_buffer
            risk = max(0.0, risk)
            key = 0.0
            if burning:
                risk += self.policy.burning_elite_extra_risk
                if not context.emerald_key:
                    key += self.policy.key_bonus("emerald", context.act)
            return LocalRoomParts(self.policy.elite_growth, key=key, risk=risk)
        if symbol == "R":
            recovery = self._hp_value(
                context,
                self.policy.rest_critical,
                self.policy.rest_low,
                self.policy.rest_normal,
                self.policy.rest_high,
            )
            upgrade = self.policy.upgrade_opportunity if context.upgradable_card_count else 0.0
            key = 0.0 if context.ruby_key else self.policy.key_bonus("ruby", context.act)
            return LocalRoomParts(recovery + upgrade, key=key)
        if symbol == "$":
            value = {
                GoldState.VERY_LOW: self.policy.shop_very_low,
                GoldState.LOW: self.policy.shop_low,
                GoldState.MEDIUM: self.policy.shop_medium,
                GoldState.HIGH: self.policy.shop_high,
            }[context.gold_state]
            return LocalRoomParts(value)
        if symbol == "T":
            key = 0.0 if context.sapphire_key else self.policy.key_bonus("sapphire", context.act)
            return LocalRoomParts(self.policy.treasure_value, key=key)
        if symbol == "?":
            outcomes = {
                "monster": self.local_room_parts("M", False, context),
                "elite": self.local_room_parts("E", False, context),
                "shop": self.local_room_parts("$", False, context),
                "treasure": self.local_room_parts("T", False, context),
                "event": LocalRoomParts(self.policy.event_value),
            }
            return LocalRoomParts(
                value=sum(context.chance(name) * parts.value for name, parts in outcomes.items()),
                key=sum(context.chance(name) * parts.key for name, parts in outcomes.items()),
                risk=sum(context.chance(name) * parts.risk for name, parts in outcomes.items()),
            )
        return LocalRoomParts()

    def _recovery_bonus(self, path: RoutePath, context: RouteContext) -> float:
        rest_index = next(
            (index for index, node in enumerate(path.nodes) if (node.symbol or "?").upper() == "R"),
            None,
        )
        bonus = 0.0
        if rest_index is not None:
            access = self._hp_value(
                context,
                self.policy.recovery_critical,
                self.policy.recovery_low,
                self.policy.recovery_normal,
                self.policy.recovery_high,
            )
            bonus += access / (rest_index + 1)
            if any((node.symbol or "?").upper() == "E" for node in path.nodes[:rest_index]):
                bonus += self.policy.elite_rest_bonus / (rest_index + 1)
        return bonus

    def _sequence_risk(self, path: RoutePath) -> float:
        risk = current = 0.0
        for node in path.nodes:
            if (node.symbol or "?").upper() in ("M", "E"):
                current += 1
                if current >= 3:
                    risk += self.policy.sequence_penalty * ((current - 2) ** 2)
            else:
                current = 0
        return risk

    def _evaluate_path(self, path: RoutePath, context: RouteContext) -> ScoreBreakdown:
        immediate = future = key = local_risk = 0.0
        for index, node in enumerate(path.nodes):
            parts = self.local_room_parts(node.symbol, bool(node.burning), context)
            discount = self.policy.discount ** index
            if index == 0:
                immediate += parts.value
            else:
                future += discount * parts.value
            key += discount * parts.key
            local_risk += discount * parts.risk
        risk = -(local_risk + self._sequence_risk(path))
        last = path.nodes[-1]
        dead_end = 0.0
        if last.y < self.graph.max_y and not self.graph.edges_from(last, path.wing_charges_remaining):
            dead_end = -self.policy.dead_end_penalty
        return ScoreBreakdown(
            immediate=immediate,
            future=future,
            recovery=self._recovery_bonus(path, context),
            key=key,
            risk=risk,
            dead_end=dead_end,
        )

    def evaluate_candidate(
        self, node: MapNodeState, context: RouteContext, choice_index: int
    ) -> Tuple[CandidateAssessment, RouteFeatures]:
        paths = self.graph.paths_from(
            node, self.policy.lookahead_depth - 1, context.wing_charges
        )
        features = self.features.extract(node, context)
        best_path = paths[0]
        best = self._evaluate_path(best_path, context)
        for path in paths[1:]:
            breakdown = self._evaluate_path(path, context)
            if breakdown.total > best.total:
                best_path, best = path, breakdown

        optionality = min(
            self.policy.optionality_cap,
            features.optionality_pivots * self.policy.optionality_per_pivot,
        )
        survival_multiplier = {
            HpState.CRITICAL: 2.0,
            HpState.LOW: 1.5,
            HpState.NORMAL: 1.0,
            HpState.HIGH: 0.75,
        }[context.hp_state]
        forced_penalty = survival_multiplier * (
            features.forced_monsters * self.policy.forced_monster_penalty
            + features.forced_elites * self.policy.forced_elite_penalty
        )
        breakdown = ScoreBreakdown(
            immediate=best.immediate,
            future=best.future,
            recovery=best.recovery,
            optionality=optionality,
            key=best.key,
            risk=best.risk - forced_penalty,
            dead_end=best.dead_end,
        )
        reasons = []
        safe = True
        if context.hp_state == HpState.CRITICAL and features.forced_elite_within_horizon:
            safe = False
            reasons.append("critical_hp_forced_elite")
        if context.hp_state in (HpState.CRITICAL, HpState.LOW) and features.forced_three_combats_before_rest:
            safe = False
            reasons.append("low_hp_forced_combat_streak")
        risk_rank = -breakdown.risk + (100.0 if not safe else 0.0)
        assessment = CandidateAssessment(
            choice_index=choice_index,
            x=node.x,
            y=node.y,
            symbol=(node.symbol or "?").upper(),
            score=breakdown.total,
            breakdown=breakdown,
            safe=safe,
            risk_rank=risk_rank,
            reasons=tuple(reasons),
            best_path=tuple((item.x, item.y) for item in best_path.nodes),
            reaches_rest=features.reaches_rest,
            reaches_burning_elite=features.reaches_burning_elite,
        )
        return assessment, features


# Backward-friendly concise name.
RouteRuleEvaluator = RouteEvaluator
