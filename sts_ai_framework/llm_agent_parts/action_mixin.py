import json
import sys

from colorama import Fore, Style

from ..models import ActionType, GameAction, GameState


class ActionMixin:
    def _set_decision(self, source: str, **meta) -> None:
        """决策插桩: 记录决策来源与分值供运行日志 (JSONL) 使用, 不改变决策行为。

        source: auto(自动处理) / heuristic(规则) / value_network(价值网络) / llm / fallback
        """
        meta["source"] = source
        self.last_decision = meta

    def choose_action(self, state: GameState) -> GameAction:
        if not hasattr(self, "skipped_card_rewards_count"):
            self.skipped_card_rewards_count = 0

        self.last_decision = None

        self._sync_pending_event_state(state)

        if self.last_screen_type not in ["COMBAT_REWARD", "CARD_REWARD"] and state.screen_type == "COMBAT_REWARD":
            self.skipped_card_rewards_count = 0

        # 强制逻辑：从商店购买界面返回房间后，自动前进
        if self.last_screen_type == "SHOP_SCREEN" and state.screen_type == "SHOP_ROOM" and state.can_proceed:
            print(Fore.YELLOW + "检测到刚离开商店购买界面，自动前进..." + Style.RESET_ALL)
            self.last_screen_type = state.screen_type
            self._set_decision("auto")
            return GameAction(type=ActionType.PROCEED)

        self.last_screen_type = state.screen_type

        if state.screen_type == "REST":
            return self._handle_rest_room(state)

        # Prefer locale-independent structured event semantics. Text parsing remains
        # available only for older servers which do not expose state.event.
        if state.screen_type == "EVENT" and state.event is not None:
            action = self._get_structured_event_decision(state)
            if action is not None:
                return action
            if state.event.semantics_status == "KNOWN" and not any(
                choice.enabled and choice.action_index is not None for choice in state.event.choices
            ):
                return GameAction(type=ActionType.WAIT)

        if state.screen_type == "EVENT" and getattr(state, "choice_list", None):
            if state.event is None and self.value_engine is not None:
                print(Fore.MAGENTA + "旧版服务端事件：使用文字解析和本地价值网络..." + Style.RESET_ALL)
                action = self._get_model_event_decision(state)
                if action is not None:
                    return action

        # 强制拦截：GRID 界面 — 通用卡牌选择处理 (purge/upgrade/transform/duplicate)
        if state.screen_type == "GRID":
            # Neow 开场 talk 硬编码
            if state.floor == 0:
                choice_list = getattr(state, "choice_list", None)
                if choice_list and "talk" in str(choice_list[0]).lower():
                    print(Fore.MAGENTA + "自动跳过 Neow 开场对话..." + Style.RESET_ALL)
                    self._set_decision("auto")
                    return GameAction(type=ActionType.CHOOSE, choice_index=0)

            grid_action = self._handle_grid_selection(state)
            if grid_action is not None:
                self._set_decision("heuristic")
                return grid_action

        # 强制拦截：COMBAT_REWARD 直接由本地固定规则处理
        if state.screen_type == "COMBAT_REWARD":
            return self._handle_combat_reward(state)

        # Auto-handle CHEST: just open it, no LLM needed
        if state.screen_type == "CHEST":
            print(Fore.MAGENTA + "自动打开宝箱..." + Style.RESET_ALL)
            self._set_decision("auto")
            unified_choices = self._build_unified_choices(state)
            if unified_choices:
                return unified_choices[0][1]
            if state.can_proceed:
                return GameAction(type=ActionType.PROCEED)
            return GameAction(type=ActionType.WAIT)

        # === COMBAT MODULE DISABLED - outsourced to masterspire BattleAiMod.jar ===
        if state.screen_type == "NONE" and state.room_phase == "COMBAT":
            # 战斗进度显示由主循环独占 (状态变化/心跳时更新), 此处不再打印
            self._set_decision("auto")
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

        if state.screen_type == "BOSS_REWARD" and self.value_engine is not None and getattr(state, "choice_list", None):
            print(Fore.MAGENTA + "正在使用本地价值网络 (Value Network) 进行Boss遗物决策..." + Style.RESET_ALL)
            action = self._get_model_boss_reward_decision(state)
            if action is not None:
                return action
        # ==================================

        prompt = self._format_state_for_prompt(state)
        self._write_debug_prompt(state, prompt)

        print(Fore.CYAN + "正在思考..." + Style.RESET_ALL)

        try:
            # Normalize model name: strip provider prefix (e.g. "deepseek/deepseek-v4-flash" -> "deepseek-v4-flash")
            model = self.model_name.split("/", 1)[1] if "/" in self.model_name else self.model_name
            try:
                response = self.llm_client.chat.completions.create(
                    model=model,
                    messages=[
                        {"role": "system", "content": "你是一个《杀戮尖塔》专家 AI。你会为了胜利而进行最优操作。请只输出 JSON。"},
                        {"role": "user", "content": prompt},
                    ],
                    response_format={"type": "json_object"},
                )
            except Exception:
                response = self.llm_client.chat.completions.create(
                    model=model,
                    messages=[
                        {"role": "system", "content": "你是一个《杀戮尖塔》专家 AI。你会为了胜利而进行最优操作。请只输出 JSON，不要包含Markdown代码块。"},
                        {"role": "user", "content": prompt},
                    ],
                )

            content = response.choices[0].message.content
            print(Fore.GREEN + f"LLM 响应: {content}" + Style.RESET_ALL)
            self._set_decision("llm", raw=content)

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
                    requested_index = action_dict["choice_index"]
                    event_safe = (
                        state.screen_type != "EVENT"
                        or self._is_safe_event_action_index(state, requested_index)
                    )
                    mapped_action = self._map_unified_choice_to_action(state, requested_index) if event_safe else None
                    if mapped_action:
                        if state.screen_type == "EVENT":
                            self._remember_event_followup(state, requested_index)
                        return mapped_action

                print(Fore.YELLOW + "选择态返回无效动作，回退到 choice_list 的第一个选项。" + Style.RESET_ALL)
                self._set_decision("fallback", reason="invalid_choice_from_llm")
                if state.screen_type == "EVENT" and state.event is not None:
                    safe_choice = next(
                        (choice for choice in state.event.choices
                         if choice.enabled and choice.action_index is not None
                         and self._is_safe_event_action_index(state, choice.action_index)),
                        None,
                    )
                    if safe_choice is not None:
                        self._remember_event_followup(state, safe_choice.action_index)
                        return GameAction(type=ActionType.CHOOSE, choice_index=safe_choice.action_index)
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
                    self._set_decision("fallback", reason="invalid_button_action_from_llm")
                    return GameAction(type=ActionType.PROCEED)
                if state.can_cancel:
                    print(Fore.YELLOW + "按钮态返回无效动作，回退为 cancel。" + Style.RESET_ALL)
                    self._set_decision("fallback", reason="invalid_button_action_from_llm")
                    return GameAction(type=ActionType.CANCEL)

            return GameAction(**action_dict)

        except Exception as e:
            print(Fore.RED + f"生成行动时出错: {e}" + Style.RESET_ALL)
            self._set_decision("fallback", reason=str(e))
            return self._build_safe_fallback_action(state)

    def _sync_pending_event_state(self, state: GameState) -> None:
        pending = getattr(self, "_pending_event", None)
        if not pending:
            return
        if state.floor != pending.get("floor"):
            self._pending_event = None
            self._pending_grid = None
            return
        if state.screen_type == "EVENT" and state.event is not None:
            if state.event.id != pending.get("event_id"):
                self._pending_event = None
                self._pending_grid = None
            return
        if state.screen_type in ("GRID", "CARD_REWARD", "COMBAT_REWARD"):
            return
        if state.room_phase == "COMBAT":
            return
        self._pending_event = None
        if state.screen_type != "GRID":
            self._pending_grid = None

    def _handle_grid_selection(self, state: GameState):
        """Universal GRID card selection handler.

        Supports purge, upgrade, transform, duplicate with multi-card selection.
        Returns a GameAction or None (falls through to LLM).
        """
        choice_list = getattr(state, "choice_list", None)
        if not choice_list:
            return None

        # Preview/confirm mode (upgrade / transform / single-card purge): the target
        # card was picked and the game shows the confirm screen. Card clicks are
        # ignored there, so finalize via the confirm button.
        if getattr(state, "grid_confirm_up", False):
            if str(choice_list[0]).lower() == "confirm":
                print(Fore.MAGENTA + "GRID 预览确认模式, 点击确认..." + Style.RESET_ALL)
                self._pending_grid = None
                self.intended_smith_card = None
                self.intended_purge_card = None
                return GameAction(type=ActionType.CHOOSE, choice_index=0)
            return GameAction(type=ActionType.WAIT)

        # Step 1: Determine grid purpose and num_to_select
        purpose, target_uuids, num_to_select, selected_count = self._prepare_grid_targets(state)
        if not purpose or not target_uuids:
            if purpose and state.grid_selected_count < num_to_select:
                return GameAction(type=ActionType.WAIT)
            return None

        confirm_available = str(choice_list[0]).lower() == "confirm"
        all_selected = state.grid_selected_count >= num_to_select

        # Step 2: State machine for card selection
        if confirm_available and all_selected:
            # All cards selected, click confirm to finalize
            print(Fore.MAGENTA +
                f"GRID操作完成 ({purpose}), 点击确认..." +
                Style.RESET_ALL)
            self._pending_grid = None
            self.intended_purge_card = None
            self.intended_smith_card = None
            return GameAction(type=ActionType.CHOOSE, choice_index=0)

        # Still need to select more cards
        if selected_count < num_to_select and target_uuids:
            target_offset = selected_count if len(target_uuids) >= num_to_select else 0
            target_uuid = target_uuids[target_offset]
            target = next((card for card in state.grid_cards if card.uuid == target_uuid), None)
            if target is not None:
                print(Fore.MAGENTA +
                    f"GRID选择 [{selected_count + 1}/{num_to_select}] ({purpose}): {target.name}" +
                    Style.RESET_ALL)
                return GameAction(type=ActionType.CHOOSE, choice_index=target.choice_index)

            # GRID may have changed after the intent was computed. Re-rank current legal cards.
            self._pending_grid = None
            reranked_purpose, reranked_uuids, reranked_num, selected_count = self._prepare_grid_targets(state)
            if reranked_uuids:
                purpose, target_uuids, num_to_select = reranked_purpose, reranked_uuids, reranked_num
                target = next((card for card in state.grid_cards if card.uuid == target_uuids[0]), None)
                if target is not None:
                    return GameAction(type=ActionType.CHOOSE, choice_index=target.choice_index)
            if state.grid_selected_count < num_to_select:
                return GameAction(type=ActionType.WAIT)

        # Never confirm until the game reports that the requested number was selected.
        if confirm_available and state.grid_selected_count < num_to_select:
            return GameAction(type=ActionType.WAIT)

        return None

    def _prepare_grid_targets(self, state: GameState):
        """Determine grid purpose and target card IDs.

        Priority chain:
        1. Java metadata (state.grid_purpose, state.grid_num_cards)
        2. Python context (_pending_grid from prior decision)
        3. Old intended_purge_card / intended_smith_card flags (backward compat)
        4. On-the-fly evaluation via value_engine.rank_cards_for_purpose()

        Returns (purpose, target_ids, num_to_select, selected_count) or (None, None, 0, 0).
        """
        # Determine purpose
        purpose = getattr(state, "grid_purpose", None) or ""
        if not purpose:
            pending = getattr(self, "_pending_grid", None)
            if pending:
                purpose = pending.get("purpose", "")
            elif getattr(self, "intended_smith_card", None):
                purpose = "upgrade"
            elif getattr(self, "intended_purge_card", None):
                purpose = "purge"

        if not purpose or purpose == "unknown":
            return None, None, 0, 0

        num_to_select = getattr(state, "grid_num_cards", None) or 1

        # Get or compute target UUIDs
        target_uuids = None
        selected_count = getattr(state, "grid_selected_count", 0)

        pending = getattr(self, "_pending_grid", None)
        if pending and pending.get("purpose") == purpose:
            target_uuids = pending.get("target_uuids", [])
            if not target_uuids and pending.get("target_ids"):
                remaining = list(state.grid_cards)
                target_uuids = []
                for target_id in pending["target_ids"]:
                    match = next((c for c in remaining if
                                  (f"{c.id}+{c.upgrades}" if c.upgrades else c.id) == target_id and
                                  (purpose != "upgrade" or c.can_upgrade)), None)
                    if match is not None:
                        target_uuids.append(match.uuid)
                        remaining.remove(match)
                pending["target_uuids"] = target_uuids
            # num_to_select from pending takes priority if set
            if pending.get("num_to_select"):
                num_to_select = pending["num_to_select"]

        # Backward compat: old single-card flags
        if not target_uuids:
            if purpose == "upgrade" and getattr(self, "intended_smith_card", None):
                direct = next((c for c in state.grid_cards
                               if c.uuid == self.intended_smith_card and c.can_upgrade), None)
                if direct is None:
                    direct = next((c for c in state.grid_cards
                                   if c.id == self.intended_smith_card and c.can_upgrade), None)
                target_uuids = [direct.uuid] if direct else []
                self._pending_grid = {
                    "purpose": purpose,
                    "target_uuids": target_uuids,
                    "num_to_select": num_to_select,
                    "selected_count": 0,
                }
            elif purpose == "purge" and getattr(self, "intended_purge_card", None):
                matching = [c.uuid for c in state.grid_cards if c.id == self.intended_purge_card]
                target_uuids = matching[:num_to_select]
                self._pending_grid = {
                    "purpose": purpose,
                    "target_uuids": target_uuids,
                    "num_to_select": num_to_select,
                    "selected_count": 0,
                }

        # On-the-fly evaluation via value network (relic-triggered grids etc.)
        if not target_uuids and self.value_engine is not None and state.grid_cards:
            legal_cards = [c for c in state.grid_cards if purpose != "upgrade" or c.can_upgrade]
            current_state = {
                "hp": state.player.current_hp,
                "max_hp": state.player.max_hp,
                "gold": state.player.gold,
                "floor": state.floor,
                "ascension": 20,
                "deck": [self._card_model_id(card) for card in state.deck] if hasattr(state, "deck") else [],
                "relics": [relic.id for relic in state.relics] if hasattr(state, "relics") else [],
                "relic_states": self._build_relic_state_payload(state) if hasattr(self, "_build_relic_state_payload") else [],
            }

            # For transform, exclude curse cards
            exclude_ids = None
            if purpose == "transform":
                exclude_ids = {card.id for card in state.deck if card.type == "CURSE"}

            remaining_count = max(1, num_to_select - selected_count)
            ranked_ids = self.value_engine.rank_cards_for_purpose(
                current_state, purpose, remaining_count, exclude_ids=exclude_ids
            )
            remaining = list(legal_cards)
            target_uuids = []
            for ranked_id in ranked_ids:
                match = next((c for c in remaining if
                              (f"{c.id}+{c.upgrades}" if c.upgrades else c.id) == ranked_id), None)
                if match is not None:
                    target_uuids.append(match.uuid)
                    remaining.remove(match)

            if target_uuids:
                self._pending_grid = {
                    "purpose": purpose,
                    "target_uuids": target_uuids,
                    "num_to_select": num_to_select,
                    "selected_count": 0,
                }
                print(Fore.MAGENTA +
                    f"GRID自动评估 ({purpose}): 选定目标={target_uuids}" +
                    Style.RESET_ALL)

        if not target_uuids:
            return None, None, 0, 0

        return purpose, target_uuids, num_to_select, selected_count

    def _clean_json_string(self, content: str) -> str:
        """清理 LLM 返回的字符串，尝试提取 JSON"""
        content = content.strip()
        if content.startswith("```"):
            start = content.find("{")
            end = content.rfind("}")
            if start != -1 and end != -1:
                return content[start : end + 1]
        return content
