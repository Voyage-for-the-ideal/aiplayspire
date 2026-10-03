# -*- coding: utf-8 -*-
import re
from typing import Optional

from colorama import Fore, Style

from ..config import EVENT_DECISION_POLICY
from ..event_pools import CHEST_CURSE_POOL, chest_relic_pool
from ..models import ActionType, GameAction, GameState
from ..run_log import log_event
from .event_expectimax import decide_event, event_state_payload, relic_state_payload
from .value_payload import value_state_payload


class DecisionMixin:
    CURSE_NAMES = {
        "regret",
        "pain",
        "normality",
        "doubt",
        "shame",
        "clumsy",
        "injury",
        "writhe",
        "curse of the bell",
    }

    def _clean_effect_text(self, text: str) -> str:
        # """Clean Mod text and noise"""
        cleaned = str(text)
        # 去掉颜色标记，如 #g #r #b #y
        cleaned = re.sub(r"#[a-zA-Z]", " ", cleaned)
        cleaned = cleaned.replace("NL", " ")
        cleaned = re.sub(r"\s+", " ", cleaned)
        return cleaned.strip()

    def _split_event_clauses(self, text: str):
        clauses = re.split(r"[.;。；]", text)
        cleaned_clauses = []
        for clause in clauses:
            clause = clause.strip()
            if clause:
                cleaned_clauses.append(clause)
        return cleaned_clauses

    def _extract_first_int(self, text: str) -> Optional[int]:
        match = re.search(r"(\d+)", text)
        if not match:
            return None
        return int(match.group(1))

    def _extract_bracket_label(self, text: str) -> Optional[str]:
        match = re.search(r"\[(.*?)\]", text)
        if not match:
            return None
        return match.group(1).strip()

    def _extract_curse_name(self, text_lower: str) -> Optional[str]:
        # 常见事件文本：Become Cursed - Regret / Cursed: Regret / gain a Curse
        match = re.search(r"cursed\s*[-:]\s*([a-z_ ]+)", text_lower)
        if match:
            name = match.group(1).strip().title()
            return name
        # 兜底：常见诅咒名关键�?
        for k in self.CURSE_NAMES:
            if k in text_lower:
                return k.title()
        return None

    def _log_event_fallback(self, state: GameState, reason: str) -> None:
        """记录事件未能本地决策的原因（改造前的 LLM fall-through 基线 / 改造后应归零）。"""
        event = getattr(state, "event", None)
        log_event(
            event="event_fallback",
            event_id=getattr(event, "id", None),
            phase=getattr(event, "phase", None),
            decision_kind=getattr(event, "decision_kind", None),
            floor=getattr(state, "floor", None),
            reason=reason,
        )

    def _build_relic_state_payload(self, state: GameState):
        return relic_state_payload(state)

    def _has_omamori_charge(self, state: GameState) -> bool:
        for relic in getattr(state, "relics", []) or []:
            relic_id = getattr(relic, "id", "")
            relic_name = getattr(relic, "name", relic_id)
            if relic_id == "Omamori" or relic_name == "Omamori":
                counter = getattr(relic, "counter", -1)
                return counter is None or counter < 0 or counter > 0
        return False

    def _is_curse_card_id(self, card_id: Optional[str]) -> bool:
        if not card_id:
            return False
        normalized = str(card_id).strip().lower()
        if "curse" in normalized:
            return True
        return normalized in self.CURSE_NAMES

    def _parse_event_effects(self, choice_text: str, state: Optional[GameState] = None):
        """Parse event choice text into structured effects.

        Each clause is checked against ALL effect categories so that combined
        phrases like "[ Lose all Gold Remove 2 Cards ]" produce multiple effects.
        No continue-after-match — a single clause can yield 2+ effects.
        """
        choice_text_clean = self._clean_effect_text(choice_text)
        choice_text_lower = choice_text_clean.lower()
        effects = []

        for clause in self._split_event_clauses(choice_text_clean):
            clause_lower = clause.lower()

            if "max hp" in clause_lower or "maximum hp" in clause_lower:
                amount = self._extract_first_int(clause)
                if amount is not None:
                    if "lose" in clause_lower or "-" in clause_lower:
                        effects.append({"type": "lose_max_hp", "amount": amount})
                    else:
                        effects.append({"type": "gain_max_hp", "amount": amount})
                        effects.append({"type": "gain_hp", "amount": amount})

            if "damage" in clause_lower or "lose hp" in clause_lower or "lose life" in clause_lower:
                amount = self._extract_first_int(clause)
                if amount is not None:
                    effects.append({"type": "lose_hp", "amount": amount})

            elif "heal" in clause_lower or "restore" in clause_lower or "regain" in clause_lower:
                amount = self._extract_first_int(clause)
                if amount is not None:
                    effects.append({"type": "gain_hp", "amount": amount})

            if "gold" in clause_lower:
                amount = self._extract_first_int(clause)
                if amount is not None:
                    if "lose" in clause_lower or "pay" in clause_lower or "spend" in clause_lower:
                        effects.append({"type": "lose_gold", "amount": amount})
                    else:
                        effects.append({"type": "gain_gold", "amount": amount})

            if "relic" in clause_lower or "obtain" in clause_lower:
                if "potion" not in clause_lower:
                    if "random" in clause_lower:
                        effects.append({"type": "obtain_relic", "relic_id": "Anchor"})
                    else:
                        relic_id = self._extract_bracket_label(clause)
                        if relic_id:
                            effects.append({"type": "obtain_relic", "relic_id": relic_id})

            if "curse" in clause_lower or "cursed" in clause_lower:
                curse_name = self._extract_curse_name(clause_lower)
                effects.append({"type": "add_card", "card_id": curse_name or "Curse"})

            if "card" in clause_lower:
                if "remove" in clause_lower or "purge" in clause_lower:
                    amount = self._extract_first_int(clause) or 1
                    matched_card = self._find_card_for_choice(state, choice_text) if state else None
                    if matched_card:
                        effects.append({"type": "remove_card", "card_id": matched_card.reference, "amount": amount})
                    else:
                        effects.append({"type": "remove_card", "card_id": "unknown_card", "amount": amount})
                elif "transform" in clause_lower:
                    matched_card = self._find_card_for_choice(state, choice_text) if state else None
                    if matched_card:
                        effects.append({"type": "remove_card", "card_id": matched_card.reference})
                    else:
                        amount = self._extract_first_int(clause) or 1
                        effects.append({"type": "remove_card", "amount": amount})
                elif "upgrade" in clause_lower:
                    if "random" in clause_lower or "randomly" in clause_lower:
                        amount = self._extract_first_int(clause) or 1
                        effects.append({"type": "random_upgrade", "amount": amount})
                    else:
                        matched_card = self._find_card_for_choice(state, choice_text) if state else None
                        if matched_card:
                            effects.append({"type": "upgrade_card", "card_id": matched_card.reference})
                elif "duplicate" in clause_lower or "copy" in clause_lower:
                    matched_card = self._find_card_for_choice(state, choice_text) if state else None
                    if matched_card:
                        effects.append({"type": "duplicate", "card_id": matched_card.reference})
                    else:
                        effects.append({"type": "duplicate", "card_id": "unknown_card"})
                else:
                    card_id = self._extract_bracket_label(clause)
                    if card_id:
                        effects.append({"type": "add_card", "card_id": card_id})

        return choice_text_clean, effects

    @staticmethod
    def _has_empty_potion_slot(state: GameState) -> bool:
        potions = getattr(state, "potions", None)
        if not potions:
            # Preserve compatibility with older CommunicationMod payloads that
            # did not include potion slots.
            return True
        return any(
            potion.is_empty or potion.id == "Potion Slot" or potion.name == "Potion Slot"
            for potion in potions
        )

    def _shop_choice(self, choice_text: str, index: int, state: GameState):
        """Translate CommunicationMod's shop labels into value-model actions."""
        text = self._clean_effect_text(choice_text)
        lower = text.lower()
        price_match = re.search(r"\((\d+)\s+gold\)", lower)
        cost = int(price_match.group(1)) if price_match else 0
        target = self._extract_bracket_label(text)
        if lower == "leave":
            return {"action": "skip", "target": None, "index": index, "cost": 0}
        if lower.startswith("purge "):
            return {"action": "remove_card", "target": None, "index": index, "cost": cost}
        if lower.startswith("relic:") and target:
            return {"action": "buy_relic", "target": target, "index": index, "cost": cost}
        if lower.startswith("add potion:"):
            # A full potion belt opens a discard prompt instead of obtaining the
            # purchase.  Do not create an impossible action for the value model.
            if not self._has_empty_potion_slot(state):
                return None
            return {"action": "buy_potion", "target": target, "index": index, "cost": cost}
        if target and "cost:" in lower:
            return {"action": "buy_card", "target": target, "index": index, "cost": cost}
        return None

    def _get_model_shop_decision(self, state: GameState) -> Optional[GameAction]:
        current_state = value_state_payload(state)

        choices = []
        unified_choices = self._build_unified_choices(state)
        for i, (choice_text, _) in enumerate(unified_choices):
            choice = self._shop_choice(choice_text, i, state)
            if choice is not None:
                choices.append(choice)

        if len(choices) == 0:
            return None

        # Purge targets are constrained by the same rules as the Java shop
        # screen.  Keep these IDs out of value-network evaluation so a curse
        # (or a bottled card) cannot create an intent that GRID will reject.
        never_purgeable_ids = {
            "AscendersBane", "Necronomicurse", "CurseOfTheBell"
        }
        deck_by_id = {}
        for card in getattr(state, "deck", []):
            deck_by_id.setdefault(card.reference, []).append(card)
        legal_purge_ids = {
            card_id for card_id, copies in deck_by_id.items()
            if copies[0].id not in never_purgeable_ids
            and any(not card.is_bottled for card in copies)
        }
        purge_excluded_ids = set(deck_by_id) - legal_purge_ids
        if not legal_purge_ids:
            choices = [choice for choice in choices
                       if choice.get("action") != "remove_card"]
        if len(choices) == 0:
            return None

        best = self.value_engine.recommend_choice(
            current_state, choices, exclude_purge_ids=purge_excluded_ids
        )
        if best:
            # Store GRID intent via unified path
            self._store_grid_intent_from_choice(best, state)
            # Keep old flags for backward compatibility
            if "_purge_intent_id" in best:
                self.intended_purge_card = best["_purge_intent_id"]
            if "_smith_intent_id" in best:
                self.intended_smith_card = best["_smith_intent_id"]

            idx = best.get("index")
            if idx == -1:
                return GameAction(type=ActionType.CANCEL)
            return self._map_unified_choice_to_action(state, idx)

        return None

    def _get_model_card_decision(self, state: GameState) -> Optional[GameAction]:
        current_state = value_state_payload(state)

        choices = []
        reward_cards = getattr(state, "card_reward_cards", None) or []
        grid_card_ids = {
            card.choice_index: card.reference
            for card in (getattr(state, "grid_cards", None) or [])
            if card.choice_index is not None
        }
        unified_choices = self._build_unified_choices(state)
        for i, (choice_text, _) in enumerate(unified_choices):
            if "skip" in choice_text.lower() or "cancel" in choice_text.lower() or "leave" in choice_text.lower():
                choices.append({"action": "skip", "target": None, "index": i})
            elif "bowl" in choice_text.lower() or "singing bowl" in choice_text.lower():
                choices.append({"action": "singing_bowl", "target": None, "index": i})
            else:
                card_id = choice_text
                if i in grid_card_ids:
                    card_id = grid_card_ids[i]
                elif i < len(reward_cards):
                    card_id = reward_cards[i].reference
                elif state.reward_card_ids and i < len(state.reward_card_ids):
                    card_id = state.reward_card_ids[i]
                else:
                    matched_card = self._find_card_for_choice(state, choice_text)
                    if matched_card:
                        card_id = matched_card.reference

                choices.append({"action": "pick_card", "target": card_id, "index": i})

        # Ordinary card rewards are optional. Generated-card GRID choices such
        # as The Library are mandatory after entering the selection screen,
        # even though the vanilla GRID happens to expose a cancel button.
        if getattr(state, "screen_type", "") == "CARD_REWARD":
            choices.append({"action": "skip", "target": None, "index": -1})

        best = self.value_engine.recommend_choice(current_state, choices)
        if best:
            idx = best.get("index")
            if idx == -1:
                if getattr(state, "can_proceed", False):
                    return GameAction(type=ActionType.PROCEED)
                return GameAction(type=ActionType.CANCEL)
            return self._map_unified_choice_to_action(state, idx)

        return None

    def _event_current_state(self, state: GameState) -> dict:
        return event_state_payload(state)

    def _event_choice_effects(self, choice) -> list:
        """Flatten a deterministic event outcome into inference effects."""
        if len(choice.outcomes) != 1:
            return []
        outcome = choice.outcomes[0]
        if outcome.probability not in (None, 1, 1.0):
            return []

        effects = []
        for model in outcome.effects:
            effect = model.model_dump(exclude_none=True)
            if effect["type"] == "select_cards":
                purpose = effect.get("purpose", "").lower()
                count = effect.get("count", 1)
                if purpose in ("purge", "transform"):
                    effects.append({"type": "remove_card", "card_id": "unknown_card", "amount": count})
                elif purpose == "upgrade":
                    effects.append({"type": "upgrade_card", "card_id": "unknown_card", "amount": count})
                elif purpose == "duplicate":
                    effects.append({"type": "duplicate", "card_id": "unknown_card", "amount": count})
                else:
                    return []
            else:
                effects.append(effect)
        return effects

    def _effects_supported_by_value_model(self, effects: list) -> bool:
        supported = {
            "lose_hp", "gain_hp", "lose_max_hp", "gain_max_hp",
            "gain_gold", "lose_gold", "lose_all_gold", "remove_card",
            "remove_curses", "upgrade_card", "upgrade_matching",
            "upgrade_all_cards", "duplicate", "add_card", "obtain_relic",
            "lose_relic", "full_heal", "replace_starter_strikes",
        }
        return all(effect.get("type") in supported for effect in effects)

    def _remember_event_followup(self, state: GameState, action_index: int, best: Optional[dict] = None) -> None:
        event = getattr(state, "event", None)
        if event is None:
            return
        existing_pending = getattr(self, "_pending_event", {}) or {}
        cursed_book_path = (
            bool(existing_pending.get("cursed_tome_book_path"))
            and existing_pending.get("event_id") == event.id
        )
        choice = next(
            (item for item in event.choices if item.enabled and item.action_index == action_index),
            None,
        )
        if choice is None:
            return

        pending = {
            "event_id": event.id,
            "phase": event.phase,
            "followup": choice.followup,
            "floor": state.floor,
        }
        if cursed_book_path:
            for key in ("cursed_tome_book_path", "cursed_tome_final_dmg", "cursed_tome_book_relics"):
                if key in existing_pending:
                    pending[key] = existing_pending[key]
        self._pending_event = pending

        select_effect = None
        if len(choice.outcomes) == 1:
            select_effect = next(
                (effect for effect in choice.outcomes[0].effects if effect.type == "select_cards"),
                None,
            )
        if select_effect is None:
            return

        purpose = (select_effect.purpose or "").lower()
        requested_count = select_effect.count or 1

        # A queued event choice can remain visible for another polling frame.
        # Preserve the target selected by recommend_choice() instead of replacing
        # it with an unscoped whole-deck ranking from that transitional frame.
        existing_grid = getattr(self, "_pending_grid", None)
        if (
            existing_grid
            and existing_grid.get("purpose") == purpose
            and existing_grid.get("target_ids")
            and len(existing_grid["target_ids"]) >= requested_count
            and existing_grid.get("event_id") in (None, event.id)
        ):
            existing_grid.update({
                "event_id": event.id,
                "event_phase": event.phase,
                "num_to_select": requested_count,
            })
            return

        target_ids = []
        best = best or {}
        for key in ("_purge_intent_id", "_smith_intent_id", "_duplicate_intent_id"):
            if best.get(key):
                target_ids.append(best[key])
        if self.value_engine is not None and len(target_ids) < requested_count:
            if event.class_name == "Bonfire":
                target_ids = self._rank_bonfire_targets(state)
                purpose = "purge"
            elif purpose in ("purge", "transform", "upgrade", "duplicate"):
                excluded = None
                if purpose == "transform":
                    excluded = {card.reference for card in state.deck if card.type == "CURSE"}
                target_ids = self.value_engine.rank_cards_for_purpose(
                    self._event_current_state(state), purpose, requested_count, exclude_ids=excluded
                )
        self._pending_grid = {
            "event_id": event.id,
            "event_phase": event.phase,
            "purpose": purpose,
            "target_ids": target_ids,
            "num_to_select": requested_count,
            "selected_count": 0,
        }

    def _rank_bonfire_targets(self, state: GameState) -> list:
        """Score the exact rarity-dependent Bonfire reward for each legal card."""
        best_card = None
        best_score = float("-inf")
        current = self._event_current_state(state)
        for card in state.deck:
            if card.is_bottled:
                continue
            effects = [{"type": "remove_card", "card_id": card.reference}]
            rarity = card.rarity.upper()
            if rarity == "CURSE":
                relic_id = "Circlet" if "Spirit Poop" in current["relics"] else "Spirit Poop"
                effects.append({"type": "obtain_relic", "relic_id": relic_id})
            elif rarity in ("COMMON", "SPECIAL"):
                effects.append({"type": "gain_hp", "amount": 5})
            elif rarity == "UNCOMMON":
                effects.append({"type": "full_heal"})
            elif rarity == "RARE":
                effects.extend([{"type": "gain_max_hp", "amount": 10}, {"type": "full_heal"}])
            hypothetical = self.value_engine._apply_choice(
                current, {"action": "composite_event", "effects": effects}
            )
            score = self.value_engine.evaluate_state(hypothetical)
            if score > best_score:
                best_score = score
                best_card = card.reference
        return [best_card] if best_card else []

    # 遗物候选 × 10 诅咒的枚举预算上限；超出时按单独价值预筛遗物（见
    # _handle_chest 内注释：预筛会把 open 期望向上偏，方向已知且幅度有限）。
    CHEST_MAX_BRANCHES = 400
    CHEST_PREFILTER_KEEP = 24

    def _chest_auto_open(self, state: GameState) -> GameAction:
        unified_choices = self._build_unified_choices(state)
        if unified_choices:
            return unified_choices[0][1]
        if state.can_proceed:
            return GameAction(type=ActionType.PROCEED)
        return GameAction(type=ActionType.WAIT)

    def _handle_chest(self, state: GameState) -> GameAction:
        """CHEST 屏决策：默认开箱；持 Cursed Key 时权衡 开箱(结果期望) vs 离开。

        - Boss 箱直接开：必须打开才能看到 Boss 遗物，且 BossChest.open 对全部
          遗物硬编码 onChestOpen(true)，Cursed Key 不触发。
        - 无 Cursed Key 时开箱严格占优（免费遗物 + 事后把遗物换成蓝钥匙链接
          选项），无需打分。
        - 离开 = PROCEED（pressConfirmButton 的 TreasureRoom 分支点 ProceedButton
          开地图离房，不执行 AbstractChest.open，诅咒不结算）。
        """
        has_cursed_key = any(relic.id == "Cursed Key" for relic in state.relics or [])
        chest_known = state.chest_gold_reward is not None or state.chest_relic_tier is not None
        if state.chest_boss or not has_cursed_key or not chest_known or self.value_engine is None:
            return self._chest_auto_open(state)

        engine = self.value_engine
        current = event_state_payload(state)
        skip_score = engine.evaluate_state(current)

        base_effects = []
        if state.chest_gold_reward and (state.chest_gold_amount or 0) > 0:
            base_effects.append({"type": "gain_gold", "amount": state.chest_gold_amount})

        relic_candidates = [(None, 1.0)]
        if state.chest_relic_tier:
            pool = chest_relic_pool(
                state.chest_relic_tier, state.character, [relic.id for relic in state.relics or []])
            if pool:
                relic_candidates = pool

        prefiltered = len(relic_candidates) * len(CHEST_CURSE_POOL) > self.CHEST_MAX_BRANCHES
        if prefiltered:
            # 单独价值预筛：遗物按 V(S + 遗物) 取 top-K，被淘汰的是低价值遗物，
            # 因此下方 open 期望相对真值系统性偏高（保守方向应为开箱存疑时不开）。
            ranked = []
            for relic_id, weight in relic_candidates:
                if relic_id is None:
                    continue
                hypothetical = engine._apply_choice(current, {"action": "composite_event", "effects":
                    base_effects + [{"type": "obtain_relic", "relic_id": relic_id}]})
                ranked.append((engine.evaluate_state(hypothetical), relic_id, weight))
            ranked.sort(reverse=True)
            relic_candidates = [(relic_id, weight) for _, relic_id, weight in ranked[:self.CHEST_PREFILTER_KEEP]]

        curse_weight = 1.0 / len(CHEST_CURSE_POOL)
        branch_count = 0
        open_score = 0.0
        for relic_id, relic_weight in relic_candidates:
            for curse_id in CHEST_CURSE_POOL:
                effects = list(base_effects)
                if relic_id:
                    effects.append({"type": "obtain_relic", "relic_id": relic_id})
                effects.append({"type": "add_card", "card_id": curse_id})
                hypothetical = engine._apply_choice(
                    current, {"action": "composite_event", "effects": effects})
                open_score += relic_weight * curse_weight * engine.evaluate_state(hypothetical)
                branch_count += 1

        # 蓝钥匙链接选项：开箱后在奖励屏可弃遗物换 Sapphire Key，而 Cursed Key 的
        # 诅咒在 open() 开头就已结算，事后换钥匙照样吃。open 取两者价值上限；
        # 拾取时的最终取舍仍由 _should_take_sapphire_key 决定。
        if (not state.keys.sapphire
                and getattr(engine, "supports_key_value", False)):
            sapphire_payload = dict(current)
            keys = dict(current.get("keys") or {})
            keys["sapphire"] = True
            sapphire_payload["keys"] = keys
            sapphire_score = 0.0
            for curse_id in CHEST_CURSE_POOL:
                hypothetical = engine._apply_choice(sapphire_payload, {"action": "composite_event", "effects":
                    base_effects + [{"type": "add_card", "card_id": curse_id}]})
                sapphire_score += curse_weight * engine.evaluate_state(hypothetical)
                branch_count += 1
            open_score = max(open_score, sapphire_score)

        if open_score > skip_score:
            decision = "open"
            action = self._chest_auto_open(state)
        else:
            decision = "skip"
            if state.can_proceed:
                action = GameAction(type=ActionType.PROCEED)
            else:
                action = GameAction(type=ActionType.WAIT)

        print(Fore.MAGENTA +
              "宝箱决策 (Cursed Key): {} | open={:.2f} skip={:.2f} branches={}".format(
                  decision, open_score, skip_score, branch_count) +
              Style.RESET_ALL)
        log_event(
            event="chest_decided",
            floor=state.floor,
            act=state.act,
            relic_tier=state.chest_relic_tier,
            gold_reward=state.chest_gold_reward,
            choice=decision,
            value=round(open_score, 2),
            noop=round(skip_score, 2),
            branches=branch_count,
            prefiltered=prefiltered,
        )
        return action

    def _choice_guaranteed_hp_loss(self, choice) -> Optional[int]:
        if len(choice.outcomes) != 1 or choice.outcomes[0].probability not in (None, 1, 1.0):
            return None
        return sum(
            effect.amount or 0
            for effect in choice.outcomes[0].effects
            if effect.type == "lose_hp"
        )

    def _get_safe_complex_event_rule(self, state: GameState) -> Optional[GameAction]:
        enabled = [choice for choice in state.event.choices if choice.enabled and choice.action_index is not None]
        if len(enabled) < 2:
            return None

        safe = []
        for choice in enabled:
            hp_loss = self._choice_guaranteed_hp_loss(choice)
            if hp_loss is None or hp_loss < state.player.current_hp:
                safe.append(choice)
        if len(safe) == 1:
            choice = safe[0]
            self._remember_event_followup(state, choice.action_index)
            return GameAction(type=ActionType.CHOOSE, choice_index=choice.action_index)

        # A deterministic, cost-free positive effect strictly dominates a no-op.
        no_op = [item for item in safe if len(item.outcomes) == 1 and not item.outcomes[0].effects]
        positive = []
        for item in safe:
            if len(item.outcomes) != 1 or item.outcomes[0].probability not in (None, 1, 1.0):
                continue
            effects = item.outcomes[0].effects
            effect_types = {effect.type for effect in effects}
            strictly_positive = any(
                (effect.type == "gain_hp" and (effect.amount or 0) > 0
                 and state.player.current_hp < state.player.max_hp)
                or (effect.type in ("gain_max_hp", "gain_gold") and (effect.amount or 0) > 0)
                or (effect.type == "remove_curses" and bool(effect.card_ids))
                for effect in effects
            )
            if strictly_positive and effect_types.issubset(
                {"gain_hp", "gain_max_hp", "gain_gold", "remove_curses"}
            ):
                positive.append(item)
        if no_op and len(positive) == 1:
            choice = positive[0]
            self._remember_event_followup(state, choice.action_index)
            return GameAction(type=ActionType.CHOOSE, choice_index=choice.action_index)
        return None

    def _is_safe_event_action_index(self, state: GameState, action_index: int) -> bool:
        event = getattr(state, "event", None)
        if event is None:
            return True
        choice = next(
            (item for item in event.choices
             if item.enabled and item.action_index == action_index),
            None,
        )
        if choice is None:
            return False
        hp_loss = self._choice_guaranteed_hp_loss(choice)
        return hp_loss is None or hp_loss < state.player.current_hp

    def _get_structured_event_decision(self, state: GameState) -> Optional[GameAction]:
        event = getattr(state, "event", None)
        if event is None or event.semantics_status != "KNOWN":
            self._log_event_fallback(state, "unknown_semantics")
            return None

        if event.class_name == "GremlinMatchGame" and event.phase == "PLAY":
            return self._get_match_game_decision(state)
        if event.class_name == "CursedTome":
            action = self._get_cursed_tome_decision(state)
            if action is not None:
                return action

        enabled = [choice for choice in event.choices if choice.enabled and choice.action_index is not None]
        if event.decision_kind == "FORCED" and len(enabled) == 1:
            choice = enabled[0]
            self._remember_event_followup(state, choice.action_index)
            return GameAction(type=ActionType.CHOOSE, choice_index=choice.action_index)

        if self.value_engine is not None and EVENT_DECISION_POLICY == "expectimax":
            result = decide_event(state, self.value_engine)
            if result is not None:
                action, best = result
                self._store_grid_intent_from_choice(best, state)
                self._remember_event_followup(state, action.choice_index, best)
                log_event(
                    event="event_decided",
                    event_id=event.id,
                    phase=event.phase,
                    decision_kind=event.decision_kind,
                    floor=state.floor,
                    choice_index=action.choice_index,
                    value=round(best.get("_score", 0.0), 2),
                )
                return action
            self._log_event_fallback(state, "expectimax_no_candidate")

        if EVENT_DECISION_POLICY == "legacy" and self.value_engine is not None:
            # legacy 策略：v2 行为，仅作回滚 / A/B 对照
            if event.decision_kind == "DETERMINISTIC":
                candidates = []
                for choice in enabled:
                    hp_loss = self._choice_guaranteed_hp_loss(choice)
                    if hp_loss is not None and hp_loss >= state.player.current_hp:
                        continue
                    effects = self._event_choice_effects(choice)
                    if not self._effects_supported_by_value_model(effects):
                        # 不支持的效果只踢出该选项，不放弃整个事件
                        continue
                    candidate = {
                        "action": "composite_event",
                        "effects": effects,
                        "index": choice.action_index,
                        "raw_text": choice.label,
                    }
                    for effect in choice.outcomes[0].effects if choice.outcomes else []:
                        if effect.type == "select_cards" and (effect.purpose or "").lower() == "transform":
                            candidate["_is_transform"] = True
                    candidates.append(candidate)

                if candidates:
                    curse_ids = {card.reference for card in state.deck if card.type == "CURSE"}
                    best = self.value_engine.recommend_choice(
                        self._event_current_state(state),
                        candidates,
                        exclude_purge_ids=curse_ids,
                    )
                    if best is not None:
                        action_index = best["index"]
                        self._store_grid_intent_from_choice(best, state)
                        self._remember_event_followup(state, action_index, best)
                        return GameAction(type=ActionType.CHOOSE, choice_index=action_index)

        # COMPLEX 安全规则不依赖价值引擎：expectimax 无候选时也作为兜底
        if event.decision_kind == "COMPLEX":
            action = self._get_safe_complex_event_rule(state)
            if action is not None:
                return action

        self._log_event_fallback(state, "no_local_decision")
        return None

    def _get_cursed_tome_decision(self, state: GameState) -> Optional[GameAction]:
        if state.event is None or self.value_engine is None:
            return None
        event = state.event
        phase = event.phase
        pending = getattr(self, "_pending_event", {}) or {}
        if phase == "INTRO":
            read_choice = None
            leave_choice = None
            commit_effect = None
            for choice in event.choices:
                if not choice.enabled or choice.action_index is None:
                    continue
                if len(choice.outcomes) != 1:
                    continue
                effects = choice.outcomes[0].effects
                commit = next((effect for effect in effects if effect.type == "commit_reading"), None)
                if commit is not None:
                    read_choice = choice
                    commit_effect = commit
                elif any(effect.type == "gain_hp" for effect in effects) or not effects:
                    leave_choice = choice

            if read_choice is None or leave_choice is None:
                return None

            unavoidable = commit_effect.unavoidable_hp_loss
            if unavoidable is None:
                return None
            final_dmg = commit_effect.final_dmg or 0
            total_read_cost = unavoidable + final_dmg
            if total_read_cost >= state.player.current_hp:
                return GameAction(type=ActionType.CHOOSE, choice_index=leave_choice.action_index)

            current = self._event_current_state(state)
            mandatory_losses = [{"type": "lose_hp", "amount": total_read_cost}]
            leave_score = self.value_engine.evaluate_state(current)
            candidates = list(commit_effect.book_relics or [])
            if not candidates:
                candidates = ["Necronomicon", "Enchiridion", "Nilry's Codex", "Circlet"]

            read_scores = []
            for relic_id in candidates:
                effects = list(mandatory_losses)
                effects.extend([{"type": "obtain_relic", "relic_id": relic_id}])
                if relic_id == "Necronomicon":
                    effects.append({"type": "add_card", "card_id": "Necronomicurse", "amount": 1})
                continuation = self.value_engine._apply_choice(current, {"action": "composite_event", "effects": effects})
                read_scores.append(self.value_engine.evaluate_state(continuation))
            read_score = sum(read_scores) / max(1, len(read_scores))

            if read_score <= leave_score:
                self._remember_event_followup(state, leave_choice.action_index)
                return GameAction(type=ActionType.CHOOSE, choice_index=leave_choice.action_index)

            self._remember_event_followup(state, read_choice.action_index)
            pending = getattr(self, "_pending_event", {}) or {}
            pending["cursed_tome_book_path"] = True
            pending["cursed_tome_final_dmg"] = final_dmg
            pending["cursed_tome_book_relics"] = candidates
            self._pending_event = pending
            return GameAction(type=ActionType.CHOOSE, choice_index=read_choice.action_index)

        if phase in ("PAGE_1", "PAGE_2", "PAGE_3"):
            if not pending.get("cursed_tome_book_path"):
                return None
            for choice in event.choices:
                if choice.enabled and choice.action_index is not None and self._is_safe_event_action_index(
                        state, choice.action_index):
                    self._remember_event_followup(state, choice.action_index)
                    return GameAction(type=ActionType.CHOOSE, choice_index=choice.action_index)
            return None

        if phase == "LAST_PAGE":
            if pending.get("cursed_tome_book_path"):
                for choice in event.choices:
                    if choice.enabled and choice.action_index is not None and any(
                            effect.type == "random_relic" for effect in
                            (choice.outcomes[0].effects if choice.outcomes else [])):
                        self._remember_event_followup(state, choice.action_index)
                        return GameAction(type=ActionType.CHOOSE, choice_index=choice.action_index)
                return None

            current = self._event_current_state(state)
            leave_choice = None
            read_choice = None
            for choice in event.choices:
                if not choice.enabled or choice.action_index is None or not choice.outcomes:
                    continue
                outcome = choice.outcomes[0]
                if any(effect.type == "random_relic" for effect in outcome.effects):
                    read_choice = choice
                elif any(effect.type == "lose_hp" for effect in outcome.effects):
                    leave_choice = choice
            if leave_choice is None or read_choice is None:
                return None
            leave_effects = self._event_choice_effects(leave_choice)
            leave_state = self.value_engine._apply_choice(
                current, {"action": "composite_event", "effects": leave_effects}
            )
            leave_score = self.value_engine.evaluate_state(leave_state)

            read_effects = self._event_choice_effects(read_choice)
            final_dmg = next(
                (effect.get("amount", 0) or 0 for effect in read_effects if effect.get("type") == "lose_hp"),
                None,
            ) or 0
            if final_dmg >= state.player.current_hp:
                self._remember_event_followup(state, leave_choice.action_index)
                return GameAction(type=ActionType.CHOOSE, choice_index=leave_choice.action_index)

            candidates = pending.get("cursed_tome_book_relics")
            if not candidates:
                owned_relics = {relic.id for relic in getattr(state, "relics", [])}
                candidates = [relic for relic in ("Necronomicon", "Enchiridion", "Nilry's Codex") if relic not in owned_relics]
                if not candidates:
                    candidates = ["Circlet"]
            read_scores = []
            for relic_id in candidates:
                effects = [{"type": "lose_hp", "amount": final_dmg}, {"type": "obtain_relic", "relic_id": relic_id}]
                if relic_id == "Necronomicon":
                    effects.append({"type": "add_card", "card_id": "Necronomicurse", "amount": 1})
                simulated = self.value_engine._apply_choice(current, {"action": "composite_event", "effects": effects})
                read_scores.append(self.value_engine.evaluate_state(simulated))
            read_avg = sum(read_scores) / max(1, len(read_scores))

            if leave_score >= read_avg:
                self._remember_event_followup(state, leave_choice.action_index)
                return GameAction(type=ActionType.CHOOSE, choice_index=leave_choice.action_index)

            self._remember_event_followup(state, read_choice.action_index)
            pending = getattr(self, "_pending_event", {}) or {}
            pending["cursed_tome_book_path"] = True
            pending["cursed_tome_final_dmg"] = final_dmg
            pending["cursed_tome_book_relics"] = candidates
            self._pending_event = pending
            return GameAction(type=ActionType.CHOOSE, choice_index=read_choice.action_index)

        return None

    def _get_match_game_decision(self, state: GameState) -> GameAction:
        event = state.event
        memory_key = (state.floor, event.id)
        if getattr(self, "_match_game_memory_key", None) != memory_key:
            self._match_game_memory_key = memory_key
            self._match_game_memory = {}
        memory = self._match_game_memory

        enabled = []
        visible = []
        for choice in event.choices:
            effect = next(
                (item for outcome in choice.outcomes for item in outcome.effects
                 if item.type == "reveal_match_card"),
                None,
            )
            if effect is None or effect.slot is None:
                continue
            if effect.revealed_card_id:
                memory[effect.slot] = effect.revealed_card_id
                visible.append((effect.slot, effect.revealed_card_id))
            if choice.enabled and choice.action_index is not None:
                enabled.append((effect.slot, choice.action_index))

        if not enabled:
            return GameAction(type=ActionType.WAIT)

        enabled_by_slot = dict(enabled)
        if visible:
            open_slot, open_card = visible[0]
            matching_slots = [
                slot for slot, card_id in memory.items()
                if slot != open_slot and card_id == open_card and slot in enabled_by_slot
            ]
            if matching_slots:
                return GameAction(type=ActionType.CHOOSE,
                                  choice_index=enabled_by_slot[min(matching_slots)])

        by_card = {}
        for slot, action_index in enabled:
            card_id = memory.get(slot)
            if card_id is not None:
                by_card.setdefault(card_id, []).append((slot, action_index))
        known_pair = next((pairs for pairs in by_card.values() if len(pairs) >= 2), None)
        if known_pair:
            return GameAction(type=ActionType.CHOOSE, choice_index=min(known_pair)[1])

        unknown = [(slot, action_index) for slot, action_index in enabled if slot not in memory]
        target = min(unknown or enabled)
        return GameAction(type=ActionType.CHOOSE, choice_index=target[1])

    def _get_model_event_decision(self, state: GameState) -> Optional[GameAction]:
        current_state = value_state_payload(state)

        # Detect transform choices and collect curse IDs for transform filtering
        transform_indices = set()
        curse_ids = {card.reference for card in state.deck if card.type == "CURSE"}

        choices = []
        unified_choices = self._build_unified_choices(state)
        for i, (choice_text, _) in enumerate(unified_choices):
            choice_text_clean, effects = self._parse_event_effects(choice_text, state)
            choice_text_lower = choice_text_clean.lower()

            # Detect transform keywords in choice text
            if any(kw in choice_text_lower for kw in ("transform", "change", "mutate")):
                transform_indices.add(i)

            if "skip" in choice_text_lower or "leave" in choice_text_lower or "cancel" in choice_text_lower:
                choices.append({"action": "skip", "target": None, "index": i, "cost": 0})
            elif len(effects) > 0:
                choices.append({"action": "composite_event", "effects": effects, "index": i, "raw_text": choice_text_clean})
            else:
                choices.append({"action": "skip", "target": None, "index": i, "cost": 0})

        if len(choices) == 0:
            return None

        # For transform choices, skip curse cards (curse→curse is no benefit)
        exclude_ids = curse_ids if transform_indices else None

        best = self.value_engine.recommend_choice(current_state, choices, exclude_purge_ids=exclude_ids)
        if best:
            best_idx = best.get("index")
            if best_idx in transform_indices:
                best["_is_transform"] = True
            self._store_grid_intent_from_choice(best, state)
            return self._map_unified_choice_to_action(state, best_idx)

        return GameAction(type=ActionType.WAIT)

    def _store_grid_intent_from_choice(self, best_choice: dict, state) -> None:
        """Extract and store pending GRID card selection intent from a recommended choice.

        Called after recommend_choice() in event/shop decisions. If the chosen option
        will trigger a GRID card-selection screen (purge/upgrade/transform/duplicate),
        pre-compute the target card IDs and store them in self._pending_grid.
        """
        action = best_choice.get("action")
        effects = best_choice.get("effects", [])
        is_transform = best_choice.get("_is_transform", False)

        purpose = None
        num_to_select = 1
        target_ids = []

        # Detect purge/transform from _purge_intent_id
        if "_purge_intent_id" in best_choice:
            target_card = best_choice["_purge_intent_id"]
            if is_transform:
                purpose = "transform"
            elif action == "composite_event":
                has_add = any(ef.get("type") == "add_card" for ef in effects)
                if has_add:
                    purpose = "transform"
                else:
                    purpose = "purge"
                for ef in effects:
                    if ef.get("type") == "remove_card":
                        num_to_select = ef.get("amount", 1)
            elif action in ("remove_card", "tosh"):
                purpose = "purge"
            target_ids = [target_card]

        # Detect upgrade from _smith_intent_id
        if "_smith_intent_id" in best_choice:
            if not purpose:
                purpose = "upgrade"
                target_ids = [best_choice["_smith_intent_id"]]

        # Detect duplicate from _duplicate_intent_id
        if "_duplicate_intent_id" in best_choice:
            purpose = "duplicate"
            target_ids = [best_choice["_duplicate_intent_id"]]

        if purpose:
            self._pending_grid = {
                "purpose": purpose,
                "target_ids": target_ids,
                "num_to_select": num_to_select,
                "selected_count": 0,
            }
            print(Fore.MAGENTA +
                f"预定GRID操作: {purpose}, 目标={target_ids}, 数量={num_to_select}" +
                Style.RESET_ALL)

    def _get_model_boss_reward_decision(self, state: GameState) -> Optional[GameAction]:
        current_state = value_state_payload(state)

        choices = []
        unified_choices = self._build_unified_choices(state)
        for i, (choice_text, _) in enumerate(unified_choices):
            relic_id = str(choice_text).strip()
            choices.append({
                "action": "composite_event",
                "effects": [{"type": "obtain_relic", "relic_id": relic_id}],
                "index": i,
            })

        best = self.value_engine.recommend_choice(current_state, choices)
        if best:
            idx = best.get("index")
            if idx == -1:
                return GameAction(type=ActionType.CANCEL)
            return self._map_unified_choice_to_action(state, idx)

        return None
