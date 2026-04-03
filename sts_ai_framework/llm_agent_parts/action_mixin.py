import json

from colorama import Fore, Style
from litellm import completion

from ..models import ActionType, GameAction, GameState


class ActionMixin:
    def choose_action(self, state: GameState) -> GameAction:
        if not hasattr(self, "skipped_card_rewards_count"):
            self.skipped_card_rewards_count = 0

        if self.last_screen_type not in ["COMBAT_REWARD", "CARD_REWARD"] and state.screen_type == "COMBAT_REWARD":
            self.skipped_card_rewards_count = 0

        # 强制逻辑：从商店购买界面返回房间后，自动前进
        if self.last_screen_type == "SHOP_SCREEN" and state.screen_type == "SHOP_ROOM" and state.can_proceed:
            print(Fore.YELLOW + "检测到刚离开商店购买界面，自动前进..." + Style.RESET_ALL)
            self.last_screen_type = state.screen_type
            return GameAction(type=ActionType.PROCEED)

        self.last_screen_type = state.screen_type

        if state.screen_type == "REST":
            return self._handle_rest_room(state)

        # 事件界面交给本地模型处理
        if state.screen_type == "EVENT" and self.value_engine is not None and getattr(state, "choice_list", None):
            print(Fore.MAGENTA + "正在使用本地价值网络 (Value Network) 进行事件决策..." + Style.RESET_ALL)
            action = self._get_model_event_decision(state)
            if action is not None:
                return action

        # 强制拦截：GRID 界面（商店购入删牌/和平烟斗删牌/营火敲牌 后）自动选择目标卡牌
        if state.screen_type == "GRID" and (getattr(self, "intended_purge_card", None) or getattr(self, "intended_smith_card", None)):
            is_smithing = getattr(self, "intended_smith_card", None) is not None
            target_id = self.intended_smith_card if is_smithing else self.intended_purge_card
            action_name = "Smith" if is_smithing else "Purge"

            print(Fore.MAGENTA + f"自动处理 GRID 选择，寻找目标卡牌 {action_name}: {target_id}" + Style.RESET_ALL)

            # 若第一项是 Confirm (代表选中了，可以确认)
            if getattr(state, "choice_list", None) and str(state.choice_list[0]).lower() == "confirm":
                if is_smithing:
                    self.intended_smith_card = None
                else:
                    self.intended_purge_card = None
                return GameAction(type=ActionType.CHOOSE, choice_index=0)

            target_name = None
            for card in state.deck:
                if card.id == target_id:
                    target_name = card.name.lower()
                    break

            if target_name and getattr(state, "choice_list", None):
                for i, choice_text in enumerate(state.choice_list):
                    if target_name in str(choice_text).lower():
                        # 点击对应卡牌，点击后游戏会更新GRID状态，下一回合再命中第一条的 confirm
                        return GameAction(type=ActionType.CHOOSE, choice_index=i)

            # 若没找到对应牌或发生异常，安全退出防死循环
            if is_smithing:
                self.intended_smith_card = None
            else:
                self.intended_purge_card = None

        # 强制拦截：COMBAT_REWARD 直接由本地固定规则处理
        if state.screen_type == "COMBAT_REWARD":
            return self._handle_combat_reward(state)

        if state.screen_type == "NONE" and state.room_phase == "COMBAT" and not state.is_end_turn_button_enabled:
            print(Fore.YELLOW + "等待玩家回合 (结束回合按钮不可用)..." + Style.RESET_ALL)
            return GameAction(type=ActionType.WAIT)

        # ====== 插入本地模型拦截 (例如选卡时) ======
        if state.screen_type == "SHOP_SCREEN" and self.value_engine is not None and getattr(state, "choice_list", None):
            print(Fore.MAGENTA + "正在使用本地价值网络 (Value Network) 进行商店购买决策..." + Style.RESET_ALL)
            action = self._get_model_shop_decision(state)
            if action is not None:
                return action

        if state.screen_type == "CARD_REWARD" and self.value_engine is not None and getattr(state, "choice_list", None):
            print(Fore.MAGENTA + "正在使用本地价值网络 (Value Network) 进行选卡..." + Style.RESET_ALL)
            action = self._get_model_card_decision(state)
            if action is not None:
                # 若选卡结果为跳过/取消/跳出，则卡牌奖励不会消失，我们需要记录以防止死循环
                if getattr(action, "type", None) in [ActionType.CANCEL, ActionType.PROCEED, ActionType.SKIP]:
                    if not hasattr(self, "skipped_card_rewards_count"):
                        self.skipped_card_rewards_count = 0
                    self.skipped_card_rewards_count += 1
                return action
        # ==================================

        prompt = self._format_state_for_prompt(state)
        self._write_debug_prompt(state, prompt)

        print(Fore.CYAN + "正在思考..." + Style.RESET_ALL)

        try:
            try:
                response = completion(
                    model=self.model_name,
                    messages=[
                        {"role": "system", "content": "你是一个《杀戮尖塔》专家 AI。你会为了胜利而进行最优操作。请只输出 JSON。"},
                        {"role": "user", "content": prompt},
                    ],
                    response_format={"type": "json_object"},
                    drop_params=True,
                )
            except Exception:
                response = completion(
                    model=self.model_name,
                    messages=[
                        {"role": "system", "content": "你是一个《杀戮尖塔》专家 AI。你会为了胜利而进行最优操作。请只输出 JSON，不要包含Markdown代码块。"},
                        {"role": "user", "content": prompt},
                    ],
                )

            content = response.choices[0].message.content
            print(Fore.GREEN + f"LLM 响应: {content}" + Style.RESET_ALL)

            content = self._clean_json_string(content)
            action_dict = json.loads(content)

            # Auto-correction for common LLM mistakes
            if "action" in action_dict and "type" not in action_dict:
                action_dict["type"] = action_dict["action"]
            if action_dict.get("type") == "use_potion":
                action_dict["type"] = "potion"

            # 选择态严格只接受 choose + choice_index
            if self._is_choice_state(state):
                if action_dict.get("type") == "choose" and isinstance(action_dict.get("choice_index"), int):
                    mapped_action = self._map_unified_choice_to_action(state, action_dict["choice_index"])
                    if mapped_action:
                        return mapped_action

                print(Fore.YELLOW + "选择态返回无效动作，回退到 choice_list 的第一个选项。" + Style.RESET_ALL)
                unified_choices = self._build_unified_choices(state)
                if unified_choices:
                    return unified_choices[0][1]

            if self._is_button_state(state):
                if action_dict.get("type") == "proceed" and state.can_proceed:
                    return GameAction(type=ActionType.PROCEED)
                if action_dict.get("type") == "cancel" and state.can_cancel:
                    return GameAction(type=ActionType.CANCEL)

                if state.can_proceed:
                    print(Fore.YELLOW + "按钮态返回无效动作，回退为 proceed。" + Style.RESET_ALL)
                    return GameAction(type=ActionType.PROCEED)
                if state.can_cancel:
                    print(Fore.YELLOW + "按钮态返回无效动作，回退为 cancel。" + Style.RESET_ALL)
                    return GameAction(type=ActionType.CANCEL)

            return GameAction(**action_dict)

        except Exception as e:
            print(Fore.RED + f"生成行动时出错: {e}" + Style.RESET_ALL)
            return self._build_safe_fallback_action(state)

    def _clean_json_string(self, content: str) -> str:
        """清理 LLM 返回的字符串，尝试提取 JSON"""
        content = content.strip()
        if content.startswith("```"):
            start = content.find("{")
            end = content.rfind("}")
            if start != -1 and end != -1:
                return content[start : end + 1]
        return content
