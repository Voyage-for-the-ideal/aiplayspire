"""Deterministic routing for structured map states.

The route policy deliberately uses only the map graph supplied by the
Communication Mod.  If that graph is not complete, callers receive ``None``
and retain the existing LLM route fallback.
"""

from typing import Optional

from colorama import Fore, Style

from ..models import ActionType, GameAction, GameState


class RouteMixin:
    """Small, explainable scorer for the next legal map node."""

    _ROOM_SCORES = {
        "R": 1.0,
        "E": 2.0,
        "$": 1.0,
        "?": 1.5,
        "M": 0.5,
        "T": 0.5,
        "B": 0.0,
    }

    def _get_deterministic_map_action(self, state: GameState) -> Optional[GameAction]:
        """Return a validated map choice, or None when structured data is absent.

        ``choice_index`` is the server action index, rather than an inferred
        position in the map graph.  This keeps the action valid when the game
        exposes a non-standard legal-node ordering.
        """
        choices = getattr(state, "current_map_choices", None) or []
        nodes = getattr(state, "map_nodes", None) or []
        choice_list = getattr(state, "choice_list", None) or []
        if not choices or not choice_list:
            return None

        # Boss transitions use a synthetic node which is not part of the
        # serialized map graph. A sole legal server choice is still safe and
        # should not pay a remote LLM round trip.
        if len(choices) == 1 and len(choice_list) == 1:
            only_index = getattr(choices[0], "choice_index", None)
            if only_index == 0:
                return GameAction(type=ActionType.CHOOSE, choice_index=0)

        if not nodes:
            return None

        node_by_position = {(node.x, node.y): node for node in nodes}
        valid_choices = []
        seen_indices = set()
        for choice in choices:
            index = getattr(choice, "choice_index", None)
            position = (getattr(choice, "x", None), getattr(choice, "y", None))
            if (
                not isinstance(index, int)
                or index < 0
                or index >= len(choice_list)
                or index in seen_indices
                or position not in node_by_position
            ):
                return None
            seen_indices.add(index)
            valid_choices.append((choice, node_by_position[position]))

        hp_ratio = state.player.current_hp / max(1, state.player.max_hp)
        scores = [
            (self._score_map_choice(choice, node, node_by_position, hp_ratio, state), choice.choice_index)
            for choice, node in valid_choices
        ]
        # Stable tie-breaker makes equal routes deterministic and legal.
        best_score, best_index = max(scores, key=lambda item: (item[0], -item[1]))
        print(
            Fore.MAGENTA
            + f"结构化地图评分: 选择索引 {best_index} (score={best_score:.2f})"
            + Style.RESET_ALL
        )
        return GameAction(type=ActionType.CHOOSE, choice_index=best_index)

    def _score_map_choice(self, choice, node, node_by_position, hp_ratio: float, state: GameState) -> float:
        symbol = (getattr(choice, "symbol", "?") or "?").upper()
        score = self._ROOM_SCORES.get(symbol, 0.0)

        if symbol == "R":
            score += (1.0 - hp_ratio) * 12.0
            if not self._has_key(state, "ruby"):
                score += 0.75
        elif symbol == "E":
            score += 6.0 * hp_ratio - 4.0
        elif symbol == "$":
            score += 4.0 if state.player.gold >= 75 else -3.0
        elif symbol == "T" and not self._has_key(state, "sapphire"):
            score += 2.5

        # A short look-ahead rewards routes that retain access to useful next
        # rooms, while keeping the immediate choice dominant.
        child_scores = []
        for edge in getattr(node, "children", []) or []:
            child = node_by_position.get((edge.x, edge.y))
            if child is not None:
                child_symbol = (getattr(child, "symbol", "?") or "?").upper()
                child_scores.append(self._ROOM_SCORES.get(child_symbol, 0.0))
        if child_scores:
            score += 0.35 * max(child_scores)

        # Act II elites are more punishing; require a healthier buffer.
        if state.act == 2 and symbol == "E" and hp_ratio < 0.55:
            score -= 3.0
        return score

    @staticmethod
    def _has_key(state: GameState, color: str) -> bool:
        keys = getattr(state, "keys", None)
        if keys is not None:
            return bool(getattr(keys, color, False))
        return bool(getattr(state, f"has_{color}_key", False))
