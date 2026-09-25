# -*- coding: utf-8 -*-
"""事件 per-choice 期望值打分（方案 A）。

替代"按事件整体分类（DETERMINISTIC/COMPLEX）"的粗粒度处理：对每个 enabled 选项
独立判定可模拟性，构造各 outcome 的局部模拟状态 S′ 并用价值网络打分，
score(choice) = Σ pᵢ·V(S′ᵢ)。随机效果（random_relic / random_upgrade 等）通过
枚举 realization 取加权平均消解，随机性不进入价值网络本身。

战斗选项（start_combat）本期不做精确打分：单独分类为 COMBAT，有其他可打分选项时
被 argmax/leave 基线自然避开，是唯一选项时直接进入（强制战斗）。
战斗评估器（scumthespire :5125）留待 v3 MAP expectimax 阶段统一接入。

打分近似（均不改变候选间相对比较的方向性）：
- random_relic 池枚举不模拟运行时"生成后从池中移除"，只剔除已拥有。
- random_upgrade 按最优 n 张升级近似期望（略高估，保守方向待 A/B 验证）。
- random_transform 按 remove_card 计（沿用既有 transform 打分近似）。
- random_potion / random_card_reward / colorless_card_reward 对 V 无对应输入特征，
  按 0 收益处理，只计同一选项中的确定性代价。
"""

from typing import Dict, List, Optional, Tuple

from ..event_pools import relic_pool
from ..models import ActionType, GameAction, GameState
from .value_payload import relic_state_payload, value_state_payload

# inference.py _apply_choice 的 composite_event 分支支持的效果类型
SUPPORTED_EFFECT_TYPES = {
    "lose_hp", "gain_hp", "lose_max_hp", "gain_max_hp",
    "gain_gold", "lose_gold", "lose_all_gold", "remove_card",
    "remove_curses", "upgrade_card", "upgrade_matching",
    "upgrade_all_cards", "duplicate", "add_card", "obtain_relic",
    "lose_relic", "full_heal", "replace_starter_strikes",
}

# 由本模块展开为 realization 分支的随机效果
RANDOM_EFFECT_TYPES = {
    "random_relic", "random_upgrade", "random_transform", "select_cards",
}

# V 无对应输入特征、按 0 收益近似的效果
ZERO_VALUE_EFFECT_TYPES = {
    "random_potion", "random_card_reward", "colorless_card_reward",
    "choose_generated_card",
}

# 本期不做精确打分：战斗走保守规则
COMBAT_EFFECT_TYPES = {"start_combat"}

# 已有专门决策器（CursedTome/MatchGame 等）或无法模拟的效果
UNSUPPORTED_EFFECT_TYPES = {
    "mini_game", "reveal_match_card", "spin_wheel",
    "commit_reading", "future_cost_choice", "jump_to_boss", "chance",
}

NEVER_PURGEABLE_IDS = {"AscendersBane", "Necronomicurse", "CurseOfTheBell"}

SCOREABLE = "SCOREABLE"
COMBAT = "COMBAT"
UNSUPPORTED = "UNSUPPORTED"


def event_state_payload(state: GameState) -> dict:
    """事件打分用的价值网络输入状态（与 DecisionMixin._event_current_state 一致）。"""
    return value_state_payload(state)


def guaranteed_hp_loss(choice) -> Optional[int]:
    """确定性单 outcome 选项的保证 HP 损失；多 outcome / 非确定返回 None（视为未知）。"""
    if len(choice.outcomes) != 1 or choice.outcomes[0].probability not in (None, 1, 1.0):
        return None
    return sum(
        effect.amount or 0
        for effect in choice.outcomes[0].effects
        if effect.type == "lose_hp"
    )


def classify_choice(choice) -> str:
    """按选项全部 outcome 的效果类型集合判定可模拟性。"""
    effect_types = {
        effect.type
        for outcome in choice.outcomes
        for effect in outcome.effects
    }
    if effect_types & COMBAT_EFFECT_TYPES:
        return COMBAT
    if effect_types & UNSUPPORTED_EFFECT_TYPES:
        return UNSUPPORTED
    if effect_types <= SUPPORTED_EFFECT_TYPES | RANDOM_EFFECT_TYPES | ZERO_VALUE_EFFECT_TYPES:
        return SCOREABLE
    return UNSUPPORTED


def _purge_exclusion_ids(state: GameState) -> set:
    """purge 目标必须排除：游戏规则禁删的卡与已瓶装的卡。"""
    excluded = set(NEVER_PURGEABLE_IDS)
    for card in state.deck:
        if getattr(card, "is_bottled", False):
            excluded.add(card.reference)
    return excluded


def _select_card_effects(state: GameState, engine, effect, current: dict) -> Optional[Tuple[List[dict], dict]]:
    """select_cards / random_transform → 用 rank_cards_for_purpose 的最优目标落地为引擎效果。

    返回 (engine_effects, grid_intent)；purpose 未知返回 None。
    """
    purpose = (effect.purpose or "").lower()
    count = effect.count or 1
    if purpose == "purge":
        targets = engine.rank_cards_for_purpose(
            current, "purge", count, exclude_ids=_purge_exclusion_ids(state))
        engine_effects = [{"type": "remove_card", "card_id": targets[0], "amount": count}] if targets else []
        intent = {"_purge_intent_id": targets[0]} if targets else {}
        return engine_effects, intent
    if purpose == "transform":
        curse_ids = {card.reference for card in state.deck if card.type == "CURSE"}
        targets = engine.rank_cards_for_purpose(
            current, "transform", count, exclude_ids=curse_ids)
        engine_effects = [{"type": "remove_card", "card_id": targets[0], "amount": count}] if targets else []
        intent = {"_purge_intent_id": targets[0]} if targets else {}
        if targets:
            intent["_is_transform"] = True
        return engine_effects, intent
    if purpose == "upgrade":
        targets = engine.rank_cards_for_purpose(current, "upgrade", count)
        engine_effects = [{"type": "upgrade_card", "card_id": targets[0]}] if targets else []
        intent = {"_smith_intent_id": targets[0]} if targets else {}
        return engine_effects, intent
    if purpose == "duplicate":
        targets = engine.rank_cards_for_purpose(current, "duplicate", count)
        engine_effects = [{"type": "duplicate", "card_id": targets[0]}] if targets else []
        intent = {"_duplicate_intent_id": targets[0]} if targets else {}
        return engine_effects, intent
    return None


def _expand_effects(state: GameState, engine, effects, current: dict
                    ) -> Optional[Tuple[List[Tuple[List[dict], float]], dict]]:
    """把一组 EventEffect 展开为 realization 分支列表 [(engine_effects, weight)] 与 GRID intent。

    权重和为 1；任何效果无法模拟时返回 None（调用方放弃该选项）。
    """
    branches: List[Tuple[List[dict], float]] = [([], 1.0)]
    intent: dict = {}
    for effect in effects:
        effect_type = effect.type
        if effect_type in ZERO_VALUE_EFFECT_TYPES:
            continue
        if effect_type in ("select_cards", "random_transform"):
            expanded = _select_card_effects(state, engine, effect, current)
            if expanded is None:
                return None
            engine_effects, select_intent = expanded
            intent.update(select_intent)
            branches = [(effects_list + engine_effects, weight) for effects_list, weight in branches]
            continue
        if effect_type == "random_relic":
            pool = relic_pool(effect.pool, getattr(state, "character", None),
                              [relic.id for relic in state.relics])
            if not pool:
                return None
            branches = [
                (effects_list + [{"type": "obtain_relic", "relic_id": relic_id}], weight * relic_weight)
                for effects_list, weight in branches
                for relic_id, relic_weight in pool
            ]
            continue
        if effect_type == "random_upgrade":
            targets = engine.rank_cards_for_purpose(current, "upgrade", effect.count or 1)
            upgrade_effects = [{"type": "upgrade_card", "card_id": target} for target in targets]
            branches = [(effects_list + upgrade_effects, weight) for effects_list, weight in branches]
            continue
        if effect_type in SUPPORTED_EFFECT_TYPES:
            effect_dict = effect.model_dump(exclude_none=True)
            branches = [(effects_list + [effect_dict], weight) for effects_list, weight in branches]
            continue
        return None

    total_weight = sum(weight for _, weight in branches)
    if total_weight <= 0:
        return None
    branches = [(effects_list, weight / total_weight) for effects_list, weight in branches]
    return branches, intent


def score_choice(state: GameState, choice, engine, current: dict) -> Optional[Tuple[float, dict, List[dict]]]:
    """期望值打分：score = Σ pᵢ·V(S′ᵢ)。

    返回 (expected_value, grid_intent, 首个分支的引擎效果列表)；任何 outcome 不可模拟
    或概率和为 0 时返回 None。
    """
    if not choice.outcomes:
        return None
    weighted_scores = []
    first_intent: dict = {}
    first_effects: List[dict] = []
    for outcome in choice.outcomes:
        expanded = _expand_effects(state, engine, outcome.effects, current)
        if expanded is None:
            return None
        branches, intent = expanded
        if intent and not first_intent:
            first_intent = intent
        branch_score = sum(
            weight * engine.evaluate_state(
                engine._apply_choice(current, {"action": "composite_event", "effects": effects_list}))
            for effects_list, weight in branches
        )
        probability = outcome.probability if outcome.probability is not None else 1.0
        if probability <= 0:
            probability = 1.0
        weighted_scores.append(probability * branch_score)
        if not first_effects:
            first_effects = branches[0][0]

    expected = sum(weighted_scores)
    return expected, first_intent, first_effects


def _is_leave_choice(choice) -> bool:
    if getattr(choice, "kind", "") == "LEAVE":
        return True
    return (
        len(choice.outcomes) == 1
        and choice.outcomes[0].probability in (None, 1, 1.0)
        and not choice.outcomes[0].effects
    )


def decide_event(state: GameState, value_engine
                 ) -> Optional[Tuple[GameAction, dict]]:
    """事件决策入口。返回 (GameAction, engine_best_choice_dict) 或 None（调用方走回退）。

    决策链：
    1. 剔除保证致死（确定性 HP 损失 ≥ 当前 HP）的选项；
    2. 全部非战斗选项可打分 → expectimax argmax；
    3. 存在战斗/不可模拟选项（部分覆盖）时，argmax 必须严格优于 no-op 基线 V(S)，
       否则改选 leave/no-op；也没有 leave 时返回 None 走调用方回退；
    4. 无可打分选项但战斗是唯一非致死选项 → 直接进入（强制战斗）。
    """
    event = getattr(state, "event", None)
    if event is None or value_engine is None:
        return None
    enabled = [
        choice for choice in event.choices
        if choice.enabled and choice.action_index is not None
    ]
    if not enabled:
        return None

    current = event_state_payload(state)
    noop_score = value_engine.evaluate_state(current)

    candidates = []  # (choice, score, intent, effects)
    combat_choices = []
    for choice in enabled:
        lethal = guaranteed_hp_loss(choice)
        if lethal is not None and lethal >= state.player.current_hp:
            continue
        kind = classify_choice(choice)
        if kind == COMBAT:
            combat_choices.append(choice)
            continue
        if kind != SCOREABLE:
            continue
        scored = score_choice(state, choice, value_engine, current)
        if scored is None:
            continue
        score, intent, effects = scored
        candidates.append((choice, score, intent, effects))

    if not candidates:
        if len(enabled) == 1 and len(combat_choices) == 1:
            choice = combat_choices[0]
            return GameAction(type=ActionType.CHOOSE, choice_index=choice.action_index), {}
        return None

    best_choice, best_score, best_intent, best_effects = max(candidates, key=lambda item: item[1])

    unscored_exists = len(candidates) + len(combat_choices) < len(enabled)
    if unscored_exists and best_score <= noop_score:
        leave = next((choice for choice in enabled if _is_leave_choice(choice)), None)
        if leave is not None and leave is not best_choice:
            return GameAction(type=ActionType.CHOOSE, choice_index=leave.action_index), {}
        if leave is None:
            return None

    best = {
        "action": "composite_event",
        "effects": best_effects,
        "index": best_choice.action_index,
        "raw_text": best_choice.label,
        "_score": best_score,
        "_noop_score": noop_score,
    }
    best.update(best_intent)
    return GameAction(type=ActionType.CHOOSE, choice_index=best_choice.action_index), best
