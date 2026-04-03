import re
from typing import Optional

from colorama import Fore, Style

from ..models import ActionType, GameAction, GameState


class DecisionMixin:
    def _clean_effect_text(self, text: str) -> str:
        """清洗 Mod 文本中的颜色标记与噪声，便于规则解析与日志展示。"""
        cleaned = str(text)
        # 去掉颜色标记，如 #g #r #b #y
        cleaned = re.sub(r"#[a-zA-Z]", " ", cleaned)
        cleaned = cleaned.replace("NL", " ")
        cleaned = re.sub(r"\s+", " ", cleaned)
        return cleaned.strip()

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
        # 常见事件文本：Become Cursed - Regret
        match = re.search(r"cursed\s*[-:]\s*([a-z_ ]+)", text_lower)
        if match:
            return match.group(1).strip().title()
        # 兜底：常见诅咒名关键词
        known = ["regret", "pain", "normality", "doubt", "shame", "clumsy", "injury", "writhe"]
        for k in known:
            if k in text_lower:
                return k.title()
        return None

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
            choice_text_clean = self._clean_effect_text(choice_text)
            choice_text_lower = choice_text_clean.lower()
            effects = []

            if "max hp" in choice_text_lower or "maximum hp" in choice_text_lower:
                amount = self._extract_first_int(choice_text_clean)
                if amount is not None:
                    if "lose" in choice_text_lower or "max hp -" in choice_text_lower:
                        effects.append({"type": "lose_max_hp", "amount": amount})
                    else:
                        effects.append({"type": "gain_max_hp", "amount": amount})
                        # 事件里的 Max HP 增加通常会同步抬升当前 HP
                        effects.append({"type": "gain_hp", "amount": amount})

            if "heal" in choice_text_lower:
                amount = self._extract_first_int(choice_text_clean)
                if amount is not None:
                    effects.append({"type": "gain_hp", "amount": amount})
            if "damage" in choice_text_lower or "lose hp" in choice_text_lower:
                amount = self._extract_first_int(choice_text_clean)
                if amount is not None:
                    effects.append({"type": "lose_hp", "amount": amount})

            if "gold" in choice_text_lower:
                amount = self._extract_first_int(choice_text_clean)
                if amount is not None:
                    if "lose" in choice_text_lower or "pay" in choice_text_lower:
                        effects.append({"type": "lose_gold", "amount": amount})
                    else:
                        effects.append({"type": "gain_gold", "amount": amount})

            if "relic" in choice_text_lower or "obtain" in choice_text_lower:
                if "random" in choice_text_lower:
                    effects.append({"type": "obtain_relic", "relic_id": "Anchor"})
                elif "relic" in choice_text_lower:
                    relic_id = self._extract_bracket_label(choice_text_clean)
                    if relic_id:
                        effects.append({"type": "obtain_relic", "relic_id": relic_id})

            if "card" in choice_text_lower or "curse" in choice_text_lower or "add card" in choice_text_lower or "obtain card" in choice_text_lower or "gain card" in choice_text_lower:
                if "remove" in choice_text_lower or "purge" in choice_text_lower:
                    matched_card = self._find_card_for_choice(state, choice_text)
                    if matched_card:
                        effects.append({"type": "remove_card", "card_id": matched_card.id})
                    else:
                        effects.append({"type": "remove_card", "card_id": "unknown_card"})
                elif "transform" in choice_text_lower:
                    matched_card = self._find_card_for_choice(state, choice_text)
                    if matched_card:
                        effects.append({"type": "remove_card", "card_id": matched_card.id})
                else:
                    if "curse" in choice_text_lower:
                        curse_name = self._extract_curse_name(choice_text_lower)
                        effects.append({"type": "add_card", "card_id": curse_name or "Curse"})
                    else:
                        card_id = self._extract_bracket_label(choice_text_clean)
                        if card_id:
                            effects.append({"type": "add_card", "card_id": card_id})

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
            if best.get("action") == "remove_card" and "_purge_intent_id" in best:
                self.intended_purge_card = best["_purge_intent_id"]
                print(Fore.MAGENTA + f"决定在商店购买删牌服务，预定删除: {self.intended_purge_card}" + Style.RESET_ALL)

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
            choice_text_clean = self._clean_effect_text(choice_text)
            choice_text_lower = choice_text_clean.lower()
            effects = []

            if "max hp" in choice_text_lower or "maximum hp" in choice_text_lower:
                amount = self._extract_first_int(choice_text_clean)
                if amount is not None:
                    if "lose" in choice_text_lower or "max hp -" in choice_text_lower:
                        effects.append({"type": "lose_max_hp", "amount": amount})
                    else:
                        effects.append({"type": "gain_max_hp", "amount": amount})
                        # 事件里的 Max HP 增加通常会同步抬升当前 HP
                        effects.append({"type": "gain_hp", "amount": amount})

            if "heal" in choice_text_lower:
                amount = self._extract_first_int(choice_text_clean)
                if amount is not None:
                    effects.append({"type": "gain_hp", "amount": amount})
            if "damage" in choice_text_lower or "lose hp" in choice_text_lower:
                amount = self._extract_first_int(choice_text_clean)
                if amount is not None:
                    effects.append({"type": "lose_hp", "amount": amount})

            if "gold" in choice_text_lower:
                amount = self._extract_first_int(choice_text_clean)
                if amount is not None:
                    if "lose" in choice_text_lower or "pay" in choice_text_lower:
                        effects.append({"type": "lose_gold", "amount": amount})
                    else:
                        effects.append({"type": "gain_gold", "amount": amount})

            if "relic" in choice_text_lower or "obtain" in choice_text_lower:
                if "random" in choice_text_lower:
                    effects.append({"type": "obtain_relic", "relic_id": "Anchor"})
                elif "relic" in choice_text_lower:
                    relic_id = self._extract_bracket_label(choice_text_clean)
                    if relic_id:
                        effects.append({"type": "obtain_relic", "relic_id": relic_id})
                    else:
                        effects.append({"type": "obtain_relic", "relic_id": "Unknown_Relic"})

            if "card" in choice_text_lower or "curse" in choice_text_lower or "add card" in choice_text_lower or "obtain card" in choice_text_lower or "gain card" in choice_text_lower:
                if "remove" in choice_text_lower or "purge" in choice_text_lower:
                    matched_card = self._find_card_for_choice(state, choice_text)
                    if matched_card:
                        effects.append({"type": "remove_card", "card_id": matched_card.id})
                    else:
                        effects.append({"type": "remove_card", "card_id": "unknown_card"})
                elif "transform" in choice_text_lower:
                    matched_card = self._find_card_for_choice(state, choice_text)
                    if matched_card:
                        effects.append({"type": "remove_card", "card_id": matched_card.id})
                else:
                    if "curse" in choice_text_lower:
                        curse_name = self._extract_curse_name(choice_text_lower)
                        effects.append({"type": "add_card", "card_id": curse_name or "Curse"})
                    else:
                        card_id = self._extract_bracket_label(choice_text_clean)
                        if card_id:
                            effects.append({"type": "add_card", "card_id": card_id})

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
            action_type = best.get("action")
            if action_type == "upgrade_card" and "_smith_intent_id" in best:
                self.intended_smith_card = best["_smith_intent_id"]
                print(Fore.MAGENTA + f"决定在营火敲牌打铁 (Smith)，预定升级: {self.intended_smith_card}" + Style.RESET_ALL)
            elif action_type == "tosh" and "_purge_intent_id" in best:
                self.intended_purge_card = best["_purge_intent_id"]
                print(Fore.MAGENTA + f"决定抽和平烟斗 (Tosh)，预定删除: {self.intended_purge_card}" + Style.RESET_ALL)
            elif action_type == "rest":
                print(Fore.MAGENTA + "决定在营火睡觉 (Rest) 回血" + Style.RESET_ALL)
            elif action_type == "buy_relic":
                print(Fore.MAGENTA + "决定使用营火遗物 (Shovel/Girya)" + Style.RESET_ALL)

            idx = best.get("index")
            return self._map_unified_choice_to_action(state, idx)

        return GameAction(type=ActionType.WAIT)
