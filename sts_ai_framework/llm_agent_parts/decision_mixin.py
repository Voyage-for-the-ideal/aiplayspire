# -*- coding: utf-8 -*-
import re
from typing import Optional

from colorama import Fore, Style

from ..models import ActionType, GameAction, GameState


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

    def _build_relic_state_payload(self, state: GameState):
        relic_payload = []
        for relic in getattr(state, "relics", []) or []:
            relic_id = getattr(relic, "id", "")
            relic_name = getattr(relic, "name", relic_id)
            counter = getattr(relic, "counter", -1)
            if (relic_id == "Omamori" or relic_name == "Omamori") and (counter is None or counter < 0):
                counter = 2
            relic_payload.append({
                "id": relic_id,
                "name": relic_name,
                "counter": counter,
            })
        return relic_payload

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
                continue

            if "heal" in clause_lower or "restore" in clause_lower or "regain" in clause_lower:
                amount = self._extract_first_int(clause)
                if amount is not None:
                    effects.append({"type": "gain_hp", "amount": amount})
                continue

            if "damage" in clause_lower or "lose hp" in clause_lower or "lose life" in clause_lower:
                amount = self._extract_first_int(clause)
                if amount is not None:
                    effects.append({"type": "lose_hp", "amount": amount})
                continue

            if "gold" in clause_lower or "gold" in choice_text_lower:
                amount = self._extract_first_int(clause)
                if amount is not None:
                    if "lose" in clause_lower or "pay" in clause_lower or "spend" in clause_lower:
                        effects.append({"type": "lose_gold", "amount": amount})
                    else:
                        effects.append({"type": "gain_gold", "amount": amount})
                continue

            if "relic" in clause_lower or "obtain" in clause_lower:
                if "random" in clause_lower:
                    effects.append({"type": "obtain_relic", "relic_id": "Anchor"})
                else:
                    relic_id = self._extract_bracket_label(clause)
                    if relic_id:
                        effects.append({"type": "obtain_relic", "relic_id": relic_id})
                continue

            if "curse" in clause_lower or "cursed" in clause_lower:
                curse_name = self._extract_curse_name(clause_lower)
                effects.append({"type": "add_card", "card_id": curse_name or "Curse"})
                continue

            if "card" in clause_lower:
                if "remove" in clause_lower or "purge" in clause_lower:
                    matched_card = self._find_card_for_choice(state, choice_text) if state else None
                    if matched_card:
                        effects.append({"type": "remove_card", "card_id": matched_card.id})
                    else:
                        effects.append({"type": "remove_card", "card_id": "unknown_card"})
                elif "transform" in clause_lower:
                    matched_card = self._find_card_for_choice(state, choice_text) if state else None
                    if matched_card:
                        effects.append({"type": "remove_card", "card_id": matched_card.id})
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
                            effects.append({"type": "upgrade_card", "card_id": matched_card.id})
                else:
                    card_id = self._extract_bracket_label(clause)
                    if card_id:
                        effects.append({"type": "add_card", "card_id": card_id})

        return choice_text_clean, effects

    def _get_model_shop_decision(self, state: GameState) -> Optional[GameAction]:
        current_state = {
            "hp": state.player.current_hp,
            "max_hp": state.player.max_hp,
            "gold": state.player.gold,
            "floor": state.floor,
            "ascension": 20,
            "deck": [card.id for card in state.deck] if hasattr(state, "deck") else [],
            "relics": [relic.id for relic in state.relics] if hasattr(state, "relics") else [],
        }

        choices = []
        unified_choices = self._build_unified_choices(state)
        for i, (choice_text, _) in enumerate(unified_choices):
            choice_text_clean, effects = self._parse_event_effects(choice_text, state)
            choice_text_lower = choice_text_clean.lower()

            if "skip" in choice_text_lower or "leave" in choice_text_lower or "cancel" in choice_text_lower:
                choices.append({"action": "skip", "target": None, "index": i, "cost": 0})
            elif len(effects) > 0:
                choices.append({"action": "composite_event", "effects": effects, "index": i, "raw_text": choice_text_clean})
            else:
                choices.append({"action": "skip", "target": None, "index": i, "cost": 0})

        if len(choices) == 0:
            return None

        best = self.value_engine.recommend_choice(current_state, choices)
        if best:
            if (best.get("action") == "composite_event" or best.get("action") == "remove_card") and "_purge_intent_id" in best:
                self.intended_purge_card = best["_purge_intent_id"]
                print(Fore.MAGENTA + f"事件评估结果：预定移除或变化卡牌: {self.intended_purge_card}" + Style.RESET_ALL)

            idx = best.get("index")
            if idx == -1:
                return GameAction(type=ActionType.CANCEL)
            return self._map_unified_choice_to_action(state, idx)

        return None

    def _get_model_card_decision(self, state: GameState) -> Optional[GameAction]:
        current_state = {
            "hp": state.player.current_hp,
            "max_hp": state.player.max_hp,
            "gold": state.player.gold,
            "floor": state.floor,
            "ascension": 20,
            "deck": [card.id for card in state.deck] if hasattr(state, "deck") else [],
            "relics": [relic.id for relic in state.relics] if hasattr(state, "relics") else [],
            "relic_states": self._build_relic_state_payload(state),
        }

        choices = []
        unified_choices = self._build_unified_choices(state)
        for i, (choice_text, _) in enumerate(unified_choices):
            if "skip" in choice_text.lower() or "cancel" in choice_text.lower() or "leave" in choice_text.lower():
                choices.append({"action": "skip", "target": None, "index": i})
            elif "bowl" in choice_text.lower() or "singing bowl" in choice_text.lower():
                choices.append({"action": "skip", "target": None, "index": i})
            else:
                card_id = choice_text
                if hasattr(state, "reward_card_ids") and state.reward_card_ids and i < len(state.reward_card_ids):
                    card_id = state.reward_card_ids[i]
                else:
                    matched_card = self._find_card_for_choice(state, choice_text)
                    if matched_card:
                        card_id = matched_card.id

                choices.append({"action": "pick_card", "target": card_id, "index": i})

        if getattr(state, "screen_type", "") == "CARD_REWARD" or getattr(state, "can_cancel", False) or getattr(state, "can_proceed", False):
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

    def _get_model_event_decision(self, state: GameState) -> Optional[GameAction]:
        current_state = {
            "hp": state.player.current_hp,
            "max_hp": state.player.max_hp,
            "gold": state.player.gold,
            "floor": state.floor,
            "ascension": 20,
            "deck": [card.id for card in state.deck] if hasattr(state, "deck") else [],
            "relics": [relic.id for relic in state.relics] if hasattr(state, "relics") else [],
        }

        choices = []
        unified_choices = self._build_unified_choices(state)
        for i, (choice_text, _) in enumerate(unified_choices):
            choice_text_clean, effects = self._parse_event_effects(choice_text, state)
            choice_text_lower = choice_text_clean.lower()

            if "skip" in choice_text_lower or "leave" in choice_text_lower or "cancel" in choice_text_lower:
                choices.append({"action": "skip", "target": None, "index": i, "cost": 0})
            elif len(effects) > 0:
                choices.append({"action": "composite_event", "effects": effects, "index": i, "raw_text": choice_text_clean})
            else:
                choices.append({"action": "skip", "target": None, "index": i, "cost": 0})

        if len(choices) == 0:
            return None

        best = self.value_engine.recommend_choice(current_state, choices)
        if best:
            idx = best.get("index")
            return self._map_unified_choice_to_action(state, idx)

        return GameAction(type=ActionType.WAIT)
