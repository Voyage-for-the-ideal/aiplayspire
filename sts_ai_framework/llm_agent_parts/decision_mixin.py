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
                        effects.append({"type": "remove_card", "card_id": matched_card.id, "amount": amount})
                    else:
                        effects.append({"type": "remove_card", "card_id": "unknown_card", "amount": amount})
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
                elif "duplicate" in clause_lower or "copy" in clause_lower:
                    matched_card = self._find_card_for_choice(state, choice_text) if state else None
                    if matched_card:
                        effects.append({"type": "duplicate", "card_id": matched_card.id})
                    else:
                        effects.append({"type": "duplicate", "card_id": "unknown_card"})
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

        # Detect transform choices and collect curse IDs for transform filtering
        transform_indices = set()
        curse_ids = {card.id for card in state.deck if card.type == "CURSE"}

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
            relic_id = str(choice_text).strip()
            choices.append({
                "action": "composite_event",
                "effects": [{"type": "obtain_relic", "relic_id": relic_id}],
                "index": i,
            })

        choices.append({"action": "skip", "target": None, "index": -1})

        best = self.value_engine.recommend_choice(current_state, choices)
        if best:
            idx = best.get("index")
            if idx == -1:
                return GameAction(type=ActionType.CANCEL)
            return self._map_unified_choice_to_action(state, idx)

        return None
