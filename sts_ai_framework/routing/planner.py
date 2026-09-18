"""Deterministic receding-horizon route planner."""

from dataclasses import replace
from typing import List, Optional, Sequence, Tuple

from ..models import GameState, MapNodeState
from .context import HpState, RouteContext
from .decision import CandidateAssessment, RouteDecision, ScoreBreakdown
from .evaluator import RouteEvaluator
from .map_graph import MapGraph, MapGraphError
from .policy import RoutePolicyConfig


class RoutePlanner:
    def __init__(self, policy: RoutePolicyConfig = None):
        self.policy = policy or RoutePolicyConfig()

    @staticmethod
    def _valid_choices(state: GameState):
        choice_list = state.choice_list or []
        choices = state.current_map_choices or []
        if not choice_list or not choices:
            return None
        indices = [choice.choice_index for choice in choices]
        if (
            len(indices) != len(set(indices))
            or any(not isinstance(index, int) or index < 0 or index >= len(choice_list) for index in indices)
            or set(indices) != set(range(len(choice_list)))
        ):
            return None
        return tuple(sorted(choices, key=lambda choice: choice.choice_index))

    @staticmethod
    def _simple_decision(mode: str, choice_index: Optional[int], explanation: str):
        return RouteDecision(
            choice_index=choice_index,
            score=0.0,
            mode=mode,
            explanation=explanation,
        )

    def choose(self, state: GameState) -> RouteDecision:
        if not (state.choice_list or []):
            return self._simple_decision("WAIT", None, "MAP has no legal choice yet")
        choices = self._valid_choices(state)
        if choices is None:
            return self._simple_decision(
                "FIRST_LEGAL", 0, "structured map choices are missing or invalid"
            )

        try:
            graph = MapGraph(state.map_nodes or [])
        except (MapGraphError, TypeError, ValueError):
            return self._choose_immediate(state, choices)
        if any(graph.get(choice.x, choice.y) is None for choice in choices):
            return self._choose_immediate(state, choices)

        try:
            context = RouteContext.from_game_state(state, self.policy)
        except (MapGraphError, TypeError, ValueError, AttributeError):
            return self._choose_immediate(state, choices)

        evaluator = RouteEvaluator(graph, self.policy)
        assessments = []
        for choice in choices:
            node = graph.get(choice.x, choice.y)
            candidate_context = replace(
                context,
                wing_charges=max(0, context.wing_charges - (1 if choice.winged else 0)),
            )
            assessment, _ = evaluator.evaluate_candidate(
                node, candidate_context, choice.choice_index
            )
            assessments.append(assessment)

        assessments = self._apply_key_feasibility(tuple(assessments), context)
        chosen = self._select(assessments)
        explanation = self._explain(chosen, context)
        return RouteDecision(
            choice_index=chosen.choice_index,
            score=chosen.score,
            mode="FULL_GRAPH",
            breakdown=chosen.breakdown,
            candidates=assessments,
            explanation=explanation,
        )

    def _immediate_context(self, state: GameState, choices) -> Optional[RouteContext]:
        nodes = [
            MapNodeState(
                x=choice.x,
                y=choice.y,
                symbol=choice.symbol,
                burning=choice.burning,
                children=[],
            )
            for choice in choices
        ]
        try:
            temporary = state.model_copy(update={
                "map_nodes": nodes,
                "current_map_node": None,
            })
            return RouteContext.from_game_state(temporary, self.policy)
        except (TypeError, ValueError, AttributeError):
            return None

    def _choose_immediate(self, state: GameState, choices) -> RouteDecision:
        context = self._immediate_context(state, choices)
        if context is None:
            return self._simple_decision("FIRST_LEGAL", 0, "MAP player context is incomplete")
        evaluator = RouteEvaluator(context.graph, self.policy)
        assessments = []
        for choice in choices:
            node = context.graph.get(choice.x, choice.y)
            parts = evaluator.local_room_parts(choice.symbol, choice.burning, context)
            breakdown = ScoreBreakdown(
                immediate=parts.value,
                key=parts.key,
                risk=-parts.risk,
            )
            unsafe = context.hp_state == HpState.CRITICAL and (choice.symbol or "?").upper() == "E"
            assessments.append(CandidateAssessment(
                choice_index=choice.choice_index,
                x=choice.x,
                y=choice.y,
                symbol=(choice.symbol or "?").upper(),
                score=breakdown.total,
                breakdown=breakdown,
                safe=not unsafe,
                risk_rank=parts.risk + (100.0 if unsafe else 0.0),
                reasons=("critical_hp_elite",) if unsafe else (),
            ))
        chosen = self._select(tuple(assessments))
        return RouteDecision(
            choice_index=chosen.choice_index,
            score=chosen.score,
            mode="IMMEDIATE_ONLY",
            breakdown=chosen.breakdown,
            candidates=tuple(assessments),
            explanation="map graph unavailable; compared immediate room utility only",
        )

    @staticmethod
    def _apply_key_feasibility(
        assessments: Tuple[CandidateAssessment, ...], context: RouteContext
    ) -> Tuple[CandidateAssessment, ...]:
        require_emerald = (
            context.act >= 3
            and not context.emerald_key
            and any(candidate.reaches_burning_elite for candidate in assessments)
        )
        require_ruby = (
            context.act >= 3
            and not context.ruby_key
            and any(candidate.reaches_rest for candidate in assessments)
        )
        result = []
        for candidate in assessments:
            reasons = list(candidate.reasons)
            feasible = True
            if require_emerald and not candidate.reaches_burning_elite:
                feasible = False
                reasons.append("loses_emerald_key_route")
            if require_ruby and not candidate.reaches_rest:
                feasible = False
                reasons.append("loses_ruby_key_route")
            result.append(replace(candidate, key_feasible=feasible, reasons=tuple(reasons)))
        return tuple(result)

    @staticmethod
    def _select(assessments: Sequence[CandidateAssessment]) -> CandidateAssessment:
        key_pool = [candidate for candidate in assessments if candidate.key_feasible]
        pool = key_pool or list(assessments)
        safe_pool = [candidate for candidate in pool if candidate.safe]
        if safe_pool:
            return max(safe_pool, key=lambda candidate: (candidate.score, -candidate.choice_index))
        return min(
            pool,
            key=lambda candidate: (candidate.risk_rank, -candidate.score, candidate.choice_index),
        )

    @staticmethod
    def _explain(candidate: CandidateAssessment, context: RouteContext) -> str:
        facts = [f"hp={context.hp_state.value}", f"room={candidate.symbol}"]
        if candidate.reaches_rest:
            facts.append("preserves_rest")
        if candidate.reaches_burning_elite and not context.emerald_key:
            facts.append("preserves_emerald")
        facts.extend(candidate.reasons)
        return ", ".join(facts)


__all__ = ["RoutePlanner"]
