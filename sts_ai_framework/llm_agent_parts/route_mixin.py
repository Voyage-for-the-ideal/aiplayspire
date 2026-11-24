"""Deterministic routing for structured map states.

MAP 统一走本地 MAP 路由，不允许落回 LLM；当结构化数据不足时执行
FULL_GRAPH -> IMMEDIATE_ONLY -> FIRST_LEGAL -> WAIT 四级 fallback。
"""

from importlib import import_module
import json
from typing import Any, Dict, List, Mapping, Optional

from colorama import Fore, Style

from .. import config
from ..models import ActionType, GameAction, GameState
from ..run_log import log_event


RouteDecision = Any


class LegacyRouteScorer:
    """The former one-room-plus-one-child policy kept as an A/B baseline."""

    ROOM_SCORES = {
        "R": 1.0,
        "E": 2.0,
        "$": 1.0,
        "?": 1.5,
        "M": 0.5,
        "T": 0.5,
        "B": 0.0,
    }

    @staticmethod
    def has_key(state: GameState, color: str) -> bool:
        keys = getattr(state, "keys", None)
        if keys is not None:
            return bool(getattr(keys, color, False))
        return bool(getattr(state, f"has_{color}_key", False))

    @classmethod
    def score(cls, choice, node_by_position, node, hp_ratio, state):
        symbol = (
            getattr(choice, "symbol", None)
            or (choice.get("symbol") if isinstance(choice, Mapping) else None)
            or "?"
        ).upper()
        score = cls.ROOM_SCORES.get(symbol, 0.0)
        if symbol == "R":
            score += (1.0 - hp_ratio) * 12.0
            if not cls.has_key(state, "ruby"):
                score += 0.75
        elif symbol == "E":
            score += 6.0 * hp_ratio - 4.0
        elif symbol == "$":
            score += 4.0 if state.player.gold >= 75 else -3.0
        elif symbol == "T" and not cls.has_key(state, "sapphire"):
            score += 2.5

        child_scores = []
        edges = (getattr(node, "children", []) or []) if node is not None else []
        for edge in edges:
            child = node_by_position.get((edge.x, edge.y))
            if child is not None:
                child_symbol = (getattr(child, "symbol", "?") or "?").upper()
                child_scores.append(cls.ROOM_SCORES.get(child_symbol, 0.0))
        if child_scores:
            score += 0.35 * max(child_scores)
        if state.act == 2 and symbol == "E" and hp_ratio < 0.55:
            score -= 3.0
        return score


class RouteMixin:
    """Small, explainable scorer for the next legal map node."""

    _ROOM_SCORES = LegacyRouteScorer.ROOM_SCORES

    # Public entry used by ActionMixin.
    def _get_map_action(self, state: GameState) -> GameAction:
        if config.ROUTE_POLICY == "legacy":
            for mode, action in self._map_fallback_chain(state):
                self._log_fallback_decision(state, action, mode, "legacy")
                return action
            self._log_fallback_decision(
                state, GameAction(type=ActionType.WAIT), "WAIT", "legacy"
            )
            return GameAction(type=ActionType.WAIT)

        planner_action = self._get_planner_map_action(state)
        if planner_action is not None:
            return planner_action
        return self._get_fallback_map_action(state)

    def _get_fallback_map_action(self, state: GameState) -> GameAction:
        # 兼容策略：planner 不存在/返回无效时，回退到旧版打分器和固定策略。
        for mode, action in self._map_fallback_chain(state):
            self._log_fallback_decision(state, action, mode, "planner_fallback")
            return action
        print(Fore.YELLOW + "MAP 路由无可用合法选项，等待..." + Style.RESET_ALL)
        action = GameAction(type=ActionType.WAIT)
        self._log_fallback_decision(state, action, "WAIT", "planner_fallback")
        return action

    @staticmethod
    def _log_fallback_decision(state, action, mode, policy):
        log_event(
            event="route_decision",
            floor=state.floor,
            act=state.act,
            hp=getattr(state.player, "current_hp", None),
            max_hp=getattr(state.player, "max_hp", None),
            choice=action.choice_index,
            mode=mode,
            policy=policy,
            reason="deterministic_map_fallback",
        )

    def _map_fallback_chain(self, state: GameState):
        action = self._get_deterministic_map_action(state)
        if action is not None:
            yield "FULL_GRAPH", action
            return
        action = self._get_immediate_map_action(state)
        if action is not None:
            yield "IMMEDIATE_ONLY", action
            return
        action = self._get_first_legal_map_action(state)
        if action is not None:
            yield "FIRST_LEGAL", action

    # Existing legacy entry point kept for compatibility and tests.
    def _get_deterministic_map_action(self, state: GameState) -> Optional[GameAction]:
        return self._get_full_graph_map_action(state)

    def _get_full_graph_map_action(self, state: GameState) -> Optional[GameAction]:
        """Return a full-graph validated map choice.

        ``choice_index`` is the server action index, rather than an inferred
        position in the map graph.  This keeps the action valid when the game
        exposes a non-standard legal-node ordering.
        """
        choices = getattr(state, "current_map_choices", None) or []
        nodes = getattr(state, "map_nodes", None) or []
        choice_list = getattr(state, "choice_list", None) or []
        if not choices or not choice_list or not nodes or state.player is None:
            return None

        # Boss transitions use a synthetic node which is not part of the
        # serialized map graph. A sole legal server choice is still safe.
        if len(choices) == 1 and len(choice_list) == 1:
            only_index = self._get_choice_index(choices[0])
            if only_index == 0:
                return GameAction(type=ActionType.CHOOSE, choice_index=0)

        node_by_position = {(node.x, node.y): node for node in nodes}
        valid_choices = []
        seen_indices = set()
        for choice in choices:
            index = self._get_choice_index(choice)
            if not isinstance(index, int) or index < 0 or index >= len(choice_list) or index in seen_indices:
                return None
            seen_indices.add(index)

            position = (getattr(choice, "x", None), getattr(choice, "y", None))
            node = node_by_position.get(position)
            if node is None:
                return None
            valid_choices.append((choice, node))

        hp_ratio = self._safe_hp_ratio(state)
        scores = [
            (self._score_map_choice(choice, node_by_position, node, hp_ratio, state), index)
            for choice, index, node in [(choice, self._get_choice_index(choice), node) for choice, node in valid_choices]
        ]
        # Stable tie-breaker makes equal routes deterministic and legal.
        best_score, best_index = max(scores, key=lambda item: (item[0], -item[1]))
        print(
            Fore.MAGENTA
            + f"结构化地图评分 (FULL_GRAPH): 选择索引 {best_index} (score={best_score:.2f})"
            + Style.RESET_ALL
        )
        return GameAction(type=ActionType.CHOOSE, choice_index=best_index)

    def _get_immediate_map_action(self, state: GameState) -> Optional[GameAction]:
        """Use only current legal choices without map-node graph context."""
        choices = getattr(state, "current_map_choices", None) or []
        choice_list = getattr(state, "choice_list", None) or []
        if not choices or not choice_list or state.player is None:
            return None

        if len(choices) == 1 and len(choice_list) == 1:
            only_index = self._get_choice_index(choices[0])
            if only_index == 0:
                return GameAction(type=ActionType.CHOOSE, choice_index=0)

        legal_scores = []
        hp_ratio = self._safe_hp_ratio(state)
        for choice in choices:
            index = self._get_choice_index(choice)
            if not isinstance(index, int) or index < 0 or index >= len(choice_list):
                continue
            legal_scores.append((self._score_immediate_choice(choice, hp_ratio, state), index))

        if not legal_scores:
            return None

        best_score, best_index = max(legal_scores, key=lambda item: (item[0], -item[1]))
        print(
            Fore.MAGENTA
            + f"结构化地图评分 (IMMEDIATE_ONLY): 选择索引 {best_index} (score={best_score:.2f})"
            + Style.RESET_ALL
        )
        return GameAction(type=ActionType.CHOOSE, choice_index=best_index)

    def _get_first_legal_map_action(self, state: GameState) -> Optional[GameAction]:
        """Use the first visible legal index in choice_list."""
        choice_list = getattr(state, "choice_list", None) or []
        if choice_list:
            print(
                Fore.YELLOW
                + f"结构化地图评分 (FIRST_LEGAL): 选择索引 0 from {len(choice_list)} legal options"
                + Style.RESET_ALL
            )
            return GameAction(type=ActionType.CHOOSE, choice_index=0)
        return None

    def _get_planner_map_action(self, state: GameState) -> Optional[GameAction]:
        try:
            resource_mode = config.ROUTE_POLICY == "resource_v2"
            planner_module = import_module("sts_ai_framework.routing.resource" if resource_mode
                                           else "sts_ai_framework.routing.planner")
            PlannerClass = getattr(planner_module, "ResourceRoutePlanner" if resource_mode
                                   else "RoutePlanner", None)
        except ModuleNotFoundError:
            return None
        except Exception as exc:
            print(Fore.YELLOW + f"路由 planner 加载失败: {exc}" + Style.RESET_ALL)
            return None

        if PlannerClass is None:
            return None

        try:
            planner = PlannerClass()
            decision = planner.choose(state)
        except Exception as exc:
            print(Fore.YELLOW + f"路由 planner 调用失败: {exc}" + Style.RESET_ALL)
            log_event(event="route_planner_error", floor=state.floor, act=state.act,
                      error=type(exc).__name__)
            return None

        candidate = self._coerce_route_candidate(decision)
        if candidate is None:
            return None

        mode = candidate["mode"]
        choice_index = candidate["choice_index"]
        score = candidate["score"]
        self._pending_route_forecast = None
        # Keep the chosen route for router diagnostics only. Value-network
        # payloads deliberately do not consume this transient planner output.
        self._last_plan_symbols = (
            int(state.floor or 0),
            self._extract_plan_symbols(state, decision, choice_index),
        )
        for assessment in getattr(decision, "candidates", ()) or ():
            breakdown = getattr(assessment, "breakdown", None)
            components = breakdown.as_dict() if hasattr(breakdown, "as_dict") else breakdown
            diagnostics = getattr(assessment, "diagnostics", {}) or {}
            if getattr(assessment, "choice_index", None) == choice_index and diagnostics:
                self._pending_route_forecast = diagnostics
            log_event(
                event="route_candidate",
                floor=state.floor,
                act=state.act,
                hp=getattr(state.player, "current_hp", None),
                max_hp=getattr(state.player, "max_hp", None),
                choice=getattr(assessment, "choice_index", None),
                symbol=getattr(assessment, "symbol", None),
                mode=mode,
                score=round(getattr(assessment, "score", 0.0), 4),
                safe=getattr(assessment, "safe", None),
                key_feasible=getattr(assessment, "key_feasible", None),
                components=json.dumps(components, sort_keys=True, separators=(",", ":")),
                reasons=",".join(getattr(assessment, "reasons", ()) or ()),
                policy=config.ROUTE_POLICY,
                diagnostics=json.dumps(diagnostics, sort_keys=True, separators=(",", ":")),
            )
        print(
            f"[route_candidate] mode={mode} index={choice_index} score={score} "
            + f"components={candidate['components']} explanation={candidate['explanation']}"
        )

        action = self._dispatch_planner_decision(state, candidate)
        if action is None:
            return None

        log_event(
            event="route_decision",
            floor=state.floor,
            act=state.act,
            hp=getattr(state.player, "current_hp", None),
            max_hp=getattr(state.player, "max_hp", None),
            choice=choice_index,
            mode=mode,
            score=round(score, 4) if isinstance(score, (int, float)) else score,
            components=json.dumps(
                candidate["components"], sort_keys=True, separators=(",", ":")
            ) if candidate["components"] is not None else None,
            reason=candidate["explanation"],
            policy=config.ROUTE_POLICY,
        )
        print(f"[route_decision] mode={mode} index={choice_index} score={score}")
        return action

    @staticmethod
    def _extract_plan_symbols(state: GameState, decision: RouteDecision, choice_index) -> List[str]:
        """Upcoming room symbols along the chosen route for router diagnostics."""
        if choice_index is None:
            return []
        try:
            assessment = (getattr(decision, "candidates", None) or ())[choice_index]
        except (TypeError, IndexError):
            return []
        symbol_by_xy = {
            (node.x, node.y): str(node.symbol or "?").upper()
            for node in (state.map_nodes or [])
        }
        return [
            symbol_by_xy.get((x, y), "?")
            for x, y in (getattr(assessment, "best_path", None) or ())
        ][:8]

    def _dispatch_planner_decision(self, state: GameState, candidate: Dict[str, Any]) -> Optional[GameAction]:
        mode = str(candidate["mode"]).upper() if candidate.get("mode") else "FULL_GRAPH"
        choice_index = candidate.get("choice_index")
        if mode == "FULL_GRAPH":
            return self._resolve_map_choice_for_full_graph(state, choice_index)
        if mode == "IMMEDIATE_ONLY":
            return self._resolve_map_choice_for_immediate(state, choice_index)
        if mode == "FIRST_LEGAL":
            return self._resolve_map_choice_for_first_legal(state, choice_index)
        if mode == "WAIT":
            return GameAction(type=ActionType.WAIT)

        return None

    def _resolve_map_choice_for_full_graph(self, state: GameState, choice_index: int) -> Optional[GameAction]:
        choices = getattr(state, "current_map_choices", None) or []
        choice_list = getattr(state, "choice_list", None) or []
        nodes = getattr(state, "map_nodes", None) or []

        if not choices or not choice_list or not nodes:
            return None
        if choice_index not in self._legal_choice_indices(choices, choice_list):
            return None

        chosen_pos = None
        for choice in choices:
            if self._get_choice_index(choice) == choice_index:
                chosen_pos = (getattr(choice, "x", None), getattr(choice, "y", None))
                break

        if chosen_pos is not None:
            node_by_position = {(node.x, node.y): node for node in nodes}
            node = node_by_position.get(chosen_pos)
            if node is not None:
                return GameAction(type=ActionType.CHOOSE, choice_index=choice_index)
        return None

    def _resolve_map_choice_for_immediate(self, state: GameState, choice_index: int) -> Optional[GameAction]:
        if (
            isinstance(choice_index, int)
            and choice_index in self._legal_choice_indices(
                getattr(state, "current_map_choices", None) or [],
                getattr(state, "choice_list", None) or [],
            )
        ):
            return GameAction(type=ActionType.CHOOSE, choice_index=choice_index)
        return None

    def _resolve_map_choice_for_first_legal(self, state: GameState, choice_index: int) -> Optional[GameAction]:
        choice_list = getattr(state, "choice_list", None) or []
        if choice_list:
            return GameAction(type=ActionType.CHOOSE, choice_index=0)
        return None

    def _coerce_route_candidate(self, decision: RouteDecision) -> Optional[Dict[str, Any]]:
        if decision is None:
            return None

        candidate = {
            "choice_index": None,
            "mode": "FULL_GRAPH",
            "score": None,
            "components": None,
            "explanation": None,
        }

        if isinstance(decision, Mapping):
            choice_index = decision.get("choice_index")
            mode = decision.get("mode", "FULL_GRAPH")
            score = decision.get("score")
            components = decision.get("components")
            explanation = decision.get("explanation")
        else:
            choice_index = getattr(decision, "choice_index", None)
            mode = getattr(decision, "mode", "FULL_GRAPH")
            score = getattr(decision, "score", None)
            components = getattr(decision, "components", None)
            explanation = getattr(decision, "explanation", None)

        mode = str(mode).upper() if mode is not None else "FULL_GRAPH"
        if mode == "FIRST_LEGAL":
            choice_index = 0
        if mode != "WAIT" and not isinstance(choice_index, int):
            return None

        candidate["choice_index"] = choice_index
        candidate["mode"] = mode
        candidate["score"] = score
        if components is not None:
            candidate["components"] = components
        if explanation is not None:
            candidate["explanation"] = explanation
        return candidate

    @staticmethod
    def _legal_choice_indices(choices, choice_list) -> List[int]:
        legal = []
        for choice in choices:
            index = RouteMixin._get_choice_index(choice)
            if isinstance(index, int) and 0 <= index < len(choice_list):
                legal.append(index)
        return legal

    @staticmethod
    def _get_choice_index(choice) -> Optional[int]:
        if isinstance(choice, Mapping):
            return int(choice.get("choice_index")) if choice.get("choice_index") is not None else None
        return getattr(choice, "choice_index", None)

    def _score_map_choice(self, choice, node_by_position: Dict[Any, Any], node, hp_ratio: float, state: GameState) -> float:
        return LegacyRouteScorer.score(
            choice, node_by_position, node, hp_ratio, state
        )

    def _score_immediate_choice(self, choice, hp_ratio: float, state: GameState) -> float:
        return self._score_map_choice(choice, {}, None, hp_ratio, state)

    def _safe_hp_ratio(self, state: GameState) -> float:
        return state.player.current_hp / max(1, state.player.max_hp)

    @staticmethod
    def _has_key(state: GameState, color: str) -> bool:
        return LegacyRouteScorer.has_key(state, color)
