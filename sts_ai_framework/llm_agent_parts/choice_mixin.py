from typing import List, Optional, Tuple

from colorama import Fore, Style

from ..models import ActionType, GameAction, GameState


class ChoiceMixin:
    def _build_unified_choices(self, state: GameState) -> List[Tuple[str, GameAction]]:
        """构建统一选择列表：仅基于 choice_list，严格使用 choose/index。"""
        choices: List[Tuple[str, GameAction]] = []

        if state.choice_list:
            for i, choice_text in enumerate(state.choice_list):
                choices.append((str(choice_text), GameAction(type=ActionType.CHOOSE, choice_index=i)))

        return choices

    def _map_unified_choice_to_action(self, state: GameState, choice_index: int) -> Optional[GameAction]:
        """将统一 choice_index 映射回真实动作。"""
        unified_choices = self._build_unified_choices(state)
        if choice_index < 0 or choice_index >= len(unified_choices):
            return None
        return unified_choices[choice_index][1]

    def _is_choice_state(self, state: GameState) -> bool:
        return bool(state.choice_list)

    def _is_button_state(self, state: GameState) -> bool:
        """仅有按钮可点（无 choice_list）的状态，例如战后奖励页只剩前进按钮。"""
        return (not state.choice_list) and (state.can_proceed or state.can_cancel)

    def _handle_rest_room(self, state: GameState) -> GameAction:
        print(Fore.MAGENTA + "正在使用本地价值网络 (Value Network) 进行营火 (REST) 决策..." + Style.RESET_ALL)
        if not getattr(state, "choice_list", None):
            if state.can_proceed:
                return GameAction(type=ActionType.PROCEED)
            return GameAction(type=ActionType.WAIT)

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
        for i, choice_text in enumerate(state.choice_list):
            text_lower = str(choice_text).lower()
            if "restoption" in text_lower:
                choices.append({"action": "rest", "target": None, "index": i})
            elif "smithoption" in text_lower:
                best_upgrade_score = -9999.0
                best_upgrade_card = None
                unique_cards = set([c.id for c in state.deck])
                for card_id in unique_cards:
                    if "+1" not in card_id:
                        hypo_state = self.value_engine._apply_choice(current_state, {"action": "upgrade_card", "target": card_id})
                        score = self.value_engine.evaluate_state(hypo_state)
                        if score > best_upgrade_score:
                            best_upgrade_score = score
                            best_upgrade_card = card_id

                if best_upgrade_card:
                    choices.append(
                        {
                            "action": "upgrade_card",
                            "target": best_upgrade_card,
                            "index": i,
                            "_smith_intent_id": best_upgrade_card,
                        }
                    )
            elif "toshoption" in text_lower:
                best_tosh_score = -9999.0
                best_tosh_card = None
                unique_cards = set([c.id for c in state.deck])
                for card_id in unique_cards:
                    hypo_state = self.value_engine._apply_choice(current_state, {"action": "tosh", "target": card_id})
                    score = self.value_engine.evaluate_state(hypo_state)
                    if score > best_tosh_score:
                        best_tosh_score = score
                        best_tosh_card = card_id

                if best_tosh_card:
                    choices.append(
                        {
                            "action": "tosh",
                            "target": best_tosh_card,
                            "index": i,
                            "_purge_intent_id": best_tosh_card,
                        }
                    )
            elif "digoption" in text_lower:
                choices.append({"action": "buy_relic", "target": "Anchor", "index": i})
            elif "liftoption" in text_lower:
                choices.append({"action": "buy_relic", "target": "Vajra", "index": i})

        if not choices:
            if state.can_proceed:
                return GameAction(type=ActionType.PROCEED)
            return GameAction(type=ActionType.WAIT)

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

    def _handle_combat_reward(self, state: GameState) -> GameAction:
        print(Fore.MAGENTA + "自动处理 COMBAT_REWARD (优先遗物>金币>药水>卡牌)..." + Style.RESET_ALL)
        if not state.choice_list:
            if state.can_proceed:
                return GameAction(type=ActionType.PROCEED)
            return GameAction(type=ActionType.WAIT)

        relics = []
        golds = []
        potions = []
        cards = []
        others = []

        for i, choice_text in enumerate(state.choice_list):
            text_lower = str(choice_text).lower()
            if "relic" in text_lower:
                relics.append(i)
            elif "gold" in text_lower:
                golds.append(i)
            elif "potion" in text_lower:
                potions.append(i)
            elif "add card to deck" in text_lower:
                cards.append(i)
            else:
                others.append(i)

        if relics:
            return GameAction(type=ActionType.CHOOSE, choice_index=relics[0])

        if golds:
            return GameAction(type=ActionType.CHOOSE, choice_index=golds[0])

        if potions:
            has_empty_slot = False
            if getattr(state, "potions", None):
                for p in state.potions:
                    if getattr(p, "is_empty", False) or getattr(p, "id", "") == "Potion Slot" or getattr(p, "name", "") == "Potion Slot":
                        has_empty_slot = True
                        break
            else:
                has_empty_slot = True

            if has_empty_slot:
                return GameAction(type=ActionType.CHOOSE, choice_index=potions[0])

        if cards:
            if not hasattr(self, "skipped_card_rewards_count"):
                self.skipped_card_rewards_count = 0

            if self.skipped_card_rewards_count < len(cards):
                target_idx = cards[self.skipped_card_rewards_count]
                return GameAction(type=ActionType.CHOOSE, choice_index=target_idx)

        if state.can_proceed:
            return GameAction(type=ActionType.PROCEED)

        if others:
            return GameAction(type=ActionType.CHOOSE, choice_index=others[0])

        if getattr(state, "can_cancel", False):
            return GameAction(type=ActionType.CANCEL)

        return GameAction(type=ActionType.WAIT)

    def _choose_simple_combat_fallback(self, state: GameState) -> Optional[GameAction]:
        """在战斗中优先尝试一个低风险可执行动作，减少直接空过。"""
        if state.room_phase != "COMBAT" or state.screen_type != "NONE":
            return None

        if not state.is_end_turn_button_enabled:
            return GameAction(type=ActionType.WAIT)

        for card in state.hand:
            if not card.is_playable:
                continue

            # X 费卡 cost_for_turn 可能为 -1，交给游戏端判定是否可用
            if card.cost_for_turn >= 0 and card.cost_for_turn > state.player.energy:
                continue

            needs_target = card.target in {"ENEMY", "SELF_AND_ENEMY"}
            if needs_target:
                if state.monsters:
                    return GameAction(type=ActionType.PLAY, card_index=card.index, target_index=0)
                continue

            return GameAction(type=ActionType.PLAY, card_index=card.index)

        if state.is_end_turn_button_enabled:
            return GameAction(type=ActionType.END_TURN)

        return GameAction(type=ActionType.WAIT)

    def _build_safe_fallback_action(self, state: GameState) -> GameAction:
        """分层回退：选择态优先稳定映射；战斗态先 wait/可执行出牌，最后才 end_turn。"""
        if self._is_choice_state(state):
            unified_choices = self._build_unified_choices(state)
            if len(unified_choices) == 1:
                return unified_choices[0][1]

            if len(unified_choices) > 0:
                return unified_choices[0][1]

            return GameAction(type=ActionType.WAIT)

        if self._is_button_state(state):
            if state.can_proceed:
                return GameAction(type=ActionType.PROCEED)
            if state.can_cancel:
                return GameAction(type=ActionType.CANCEL)
            return GameAction(type=ActionType.WAIT)

        combat_fallback = self._choose_simple_combat_fallback(state)
        if combat_fallback is not None:
            return combat_fallback

        return GameAction(type=ActionType.WAIT)

    def _choose_map_node_after_shop(self, state: GameState) -> GameAction:
        """脚本逻辑：刚离开商店，自动选择下一个节点"""
        print(Fore.YELLOW + "检测到刚离开商店，自动选择下一个节点..." + Style.RESET_ALL)
        if state.choice_list:
            return GameAction(type=ActionType.CHOOSE, choice_index=0)
        elif state.can_proceed:
            return GameAction(type=ActionType.PROCEED)
        return GameAction(type=ActionType.WAIT)
