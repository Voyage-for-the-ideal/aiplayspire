import json
import os
import re
from typing import Optional, List, Tuple
from litellm import completion
from pydantic import ValidationError
from .agent_base import Agent
from .models import GameState, GameAction, ActionType, Card
from .knowledge_base import KnowledgeBase
import colorama
from colorama import Fore, Style

colorama.init()

class LLMAgent(Agent):
    def __init__(self, model_name: str = "gpt-4o", knowledge_base: Optional[KnowledgeBase] = None, game_client = None):
        self.model_name = model_name
        self.knowledge_base = knowledge_base or KnowledgeBase()
        self.game_client = game_client
        self.history = [] # 如果需要上下文记忆，可以保存历史记录
        self.last_screen_type = None

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
             # 目前简单地选择第一个可用节点
             return GameAction(type=ActionType.CHOOSE, choice_index=0)
        elif state.can_proceed:
             return GameAction(type=ActionType.PROCEED)
        return GameAction(type=ActionType.WAIT)

    def _clean_card_description(self, description: str) -> str:
        description = description.replace("NL", " ").replace("*", " ")
        description = re.sub(r'#[a-z]', '', description)
        description = re.sub(r'\s+', ' ', description)
        return description.strip()

    def _cache_card_info(self, card_id: Optional[str], card_name: Optional[str], info: str) -> None:
        if not info or info == "未知卡牌效果。":
            return
        if card_id:
            self.knowledge_base.cards[card_id] = info
        if card_name:
            self.knowledge_base.cards[card_name] = info

    def _resolve_card_info(self, card_name: Optional[str] = None, card_id: Optional[str] = None) -> str:
        for key in (card_id, card_name):
            if key and key in self.knowledge_base.cards:
                return self.knowledge_base.cards[key]

        if self.game_client and card_id:
            details = self.game_client.get_card_info(card_id)
            if details and "description" in details:
                info = self._clean_card_description(details["description"])
                self._cache_card_info(card_id, card_name, info)
                return info

        return "未知卡牌效果。"

    def _iter_visible_cards(self, state: GameState) -> List[Card]:
        visible_cards: List[Card] = []
        visible_cards.extend(state.hand)
        visible_cards.extend(state.draw_pile)
        visible_cards.extend(state.discard_pile)
        visible_cards.extend(state.exhaust_pile)
        return visible_cards

    def _normalize_card_text(self, text: str) -> str:
        normalized = text.strip().lower()
        normalized = normalized.replace("+", "")
        normalized = re.sub(r'\[[^\]]*\]', '', normalized)
        normalized = re.sub(r'\([^\)]*\)', '', normalized)
        normalized = re.sub(r'\s+', '', normalized)
        return normalized

    def _find_card_for_choice(self, state: GameState, choice_text: str) -> Optional[Card]:
        normalized_choice = self._normalize_card_text(choice_text)
        if not normalized_choice:
            return None

        visible_cards = self._iter_visible_cards(state)
        for card in visible_cards:
            if self._normalize_card_text(card.name) == normalized_choice:
                return card

        for card in visible_cards:
            normalized_name = self._normalize_card_text(card.name)
            normalized_id = self._normalize_card_text(card.id)
            if normalized_name and (normalized_name in normalized_choice or normalized_choice in normalized_name):
                return card
            if normalized_id and (normalized_id == normalized_choice or normalized_id in normalized_choice):
                return card

        return None

    def _get_choice_card_info(self, state: GameState, choice_text: str) -> str:
        matched_card = self._find_card_for_choice(state, choice_text)
        if matched_card:
            return self._resolve_card_info(matched_card.name, matched_card.id)

        return self._resolve_card_info(choice_text, choice_text)


    def _format_state_for_prompt(self, state: GameState) -> str:
        # 构建人类可读的描述
        base_info = f"""
当前游戏状态 (Current Game State):
- 楼层 (Floor): {state.floor}, 阶段 (Act): {state.act}
- 玩家 (Player): HP {state.player.current_hp}/{state.player.max_hp}, 格挡 (Block): {state.player.block}, 能量 (Energy): {state.player.energy}, 金币 (Gold): {state.player.gold}
- 当前屏幕 (Screen Type): {state.screen_type}
- 房间阶段 (Room Phase): {state.room_phase}
"""
        
        specific_info = ""
        rules = ""
        schema_desc = ""

        # 战斗状态
        if state.screen_type == "NONE" and state.room_phase == "COMBAT":
            monster_knowledge = []
            for m in state.monsters:
                info = self.knowledge_base.get_monster_info(m.name)
                intent_info = self.knowledge_base.get_intent_info(m.intent)
                monster_knowledge.append(f"- {m.name} (HP: {m.current_hp}/{m.max_hp}, 格挡: {m.block}): 意图: {m.intent} ({intent_info})。已知行为: {info}")
            
            card_knowledge = []
            for card in state.hand:
                info = self._resolve_card_info(card.name, card.id)
                
                playable_str = ""
                if not card.is_playable:
                    playable_str = " [不可用/UNPLAYABLE]"

                card_knowledge.append(f"- [{card.index}] {card.name} (耗能: {card.cost}, 类型: {card.type}, 目标: {card.target}){playable_str}: {info}")

            potion_knowledge = []
            for potion in state.potions:
                if potion.is_empty:
                    potion_knowledge.append(f"- [{potion.index}] 空药水槽")
                    continue

                potion_flags = []
                potion_flags.append("可用" if potion.can_use else "不可用")
                if potion.requires_target:
                    potion_flags.append("需要目标")
                if potion.can_discard:
                    potion_flags.append("可丢弃")

                potion_knowledge.append(
                    f"- [{potion.index}] {potion.name} ({', '.join(potion_flags)})"
                )

            if not potion_knowledge:
                potion_knowledge.append("- 无药水信息")

            specific_info += f"""
- 怪物 (Monsters):
{chr(10).join(monster_knowledge)}
- 手牌 (Hand):
{chr(10).join(card_knowledge)}
- 药水 (Potions):
{chr(10).join(potion_knowledge)}
- 抽牌堆: {state.draw_pile_size}, 弃牌堆: {state.discard_pile_size}

目标: 赢得战斗。生存下来。击杀怪物。
"""
            rules = """
1. 只有当你有足够的能量时才能打出卡牌。
2. 只有当卡牌的目标是 ENEMY 时，你才能指定敌人为目标。
3. 你可以使用药水 (type="potion")。
4. 不要打出标记为 [不可用/UNPLAYABLE] 的卡牌
5. 只使用标记为“可用”的药水；空药水槽不可使用。
6. 若药水“需要目标”，则必须提供 target_index。
"""
            schema_desc = """
{
  "type": "play" | "potion" | "end_turn",
  "card_index": int (可选, 如果 type 是 play 则必须),
  "potion_index": int (可选, 如果 type 是 potion 则必须),
    "target_index": int (可选, 如果需要目标则必须；例如单体攻击卡或需要目标的药水)
}
"""
        
        # 选择状态 (地图、事件、商店、奖励等)
        elif state.choice_list and len(state.choice_list) > 0:
            unified_choices = self._build_unified_choices(state)
            choices_str = ""
            for i, (choice_text, _) in enumerate(unified_choices):
                choice_info = self._get_choice_card_info(state, choice_text)
                if choice_info != "未知卡牌效果。":
                    choices_str += f"  {i}: {choice_text} - {choice_info}\n"
                else:
                    choices_str += f"  {i}: {choice_text}\n"
            
            specific_info += f"""
- 可选列表 (Choices):
{choices_str}
"""
            if state.screen_type == "COMBAT_REWARD":
                specific_info += "\n- 这是一个战斗奖励界面 (COMBAT_REWARD)。\n"

            specific_info += """
目标: 根据当前情况做出最佳选择。
"""
            rules = """
1. 必须通过指定索引 (index) 来做出选择 (type: choose)。
2. 只允许输出 type="choose"，不要输出 proceed/confirm/skip/cancel 等类型。
3. choice_index 必须落在上方可选列表的有效索引范围内。
"""
            schema_desc = """
{
    "type": "choose",
    "choice_index": int (必须, 对应上方可选列表的索引)
}
"""
        # 按钮状态 (仅 proceed/cancel)
        elif self._is_button_state(state):
            specific_info += "\n- 当前是按钮操作界面（无可选列表）。\n"
            if state.can_proceed:
                specific_info += "- 可点击按钮: proceed\n"
            if state.can_cancel:
                specific_info += "- 可点击按钮: cancel\n"

            specific_info += "\n目标: 点击可用按钮继续流程。\n"
            rules = """
1. 这是无 choice_list 的按钮界面。
2. 只允许输出可用的按钮动作：proceed 或 cancel。
3. 如果 can_proceed 为 true，优先输出 proceed。
"""
            schema_desc = """
{
    "type": "proceed" | "cancel"
}
"""
        else:
            # 兜底状态
            specific_info += "\n当前没有特定的选择列表或战斗信息。\n"
            schema_desc = """
{
    "type": "end_turn"
}
"""

        prompt = f"""
{base_info}
{specific_info}

规则:
{rules}

请回复一个代表你行动的 JSON 对象。
不要包含任何Markdown格式，直接返回纯 JSON 字符串。
Schema 格式如下:
{schema_desc}
"""
        return prompt

    def choose_action(self, state: GameState) -> GameAction:
        # Check for SHOP -> MAP transition
        if self.last_screen_type == "SHOP" and state.screen_type == "MAP":
            self.last_screen_type = state.screen_type
            return self._choose_map_node_after_shop(state)
            
        # 强制逻辑：从商店购买界面返回房间后，自动前进
        if self.last_screen_type == "SHOP_SCREEN" and state.screen_type == "SHOP_ROOM" and state.can_proceed:
            print(Fore.YELLOW + "检测到刚离开商店购买界面，自动前进..." + Style.RESET_ALL)
            self.last_screen_type = state.screen_type
            return GameAction(type=ActionType.PROCEED)

        self.last_screen_type = state.screen_type

        if state.screen_type == "NONE" and state.room_phase == "COMBAT" and not state.is_end_turn_button_enabled:
            print(Fore.YELLOW + "等待玩家回合 (结束回合按钮不可用)..." + Style.RESET_ALL)
            return GameAction(type=ActionType.WAIT)

        prompt = self._format_state_for_prompt(state)
        
        print(Fore.CYAN + "正在思考..." + Style.RESET_ALL)
        
        # 某些模型不支持 response_format="json_object"，所以我们通过 Prompt 强制要求 JSON，并尝试在失败时手动解析
        # 可以在这里添加针对特定模型的特殊处理
        try:
            # 尝试使用 response_format (OpenAI, Moonshot 等支持)
            try:
                response = completion(
                    model=self.model_name,
                    messages=[
                        {"role": "system", "content": "你是一个《杀戮尖塔》专家 AI。你会为了胜利而进行最优操作。请只输出 JSON。"},
                        {"role": "user", "content": prompt}
                    ],
                    response_format={"type": "json_object"},
                    drop_params=True # 自动丢弃不支持的参数 (如 response_format)
                )
            except Exception as e:
                # 如果不支持 response_format，则重试不带该参数
                # print(Fore.YELLOW + f"警告: 模型可能不支持 json_object 模式，尝试普通模式。错误: {e}" + Style.RESET_ALL)
                response = completion(
                    model=self.model_name,
                    messages=[
                        {"role": "system", "content": "你是一个《杀戮尖塔》专家 AI。你会为了胜利而进行最优操作。请只输出 JSON，不要包含Markdown代码块。"},
                        {"role": "user", "content": prompt}
                    ]
                )

            content = response.choices[0].message.content
            print(Fore.GREEN + f"LLM 响应: {content}" + Style.RESET_ALL)
            
            # 清理 Markdown 代码块 (以防万一)
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
            
            # 使用 Pydantic 验证
            action = GameAction(**action_dict)
            return action

        except Exception as e:
            print(Fore.RED + f"生成行动时出错: {e}" + Style.RESET_ALL)
            return self._build_safe_fallback_action(state)

    def _clean_json_string(self, content: str) -> str:
        """清理 LLM 返回的字符串，尝试提取 JSON"""
        content = content.strip()
        # 移除 ```json ... ```
        if content.startswith("```"):
            # 找到第一个 {
            start = content.find("{")
            # 找到最后一个 }
            end = content.rfind("}")
            if start != -1 and end != -1:
                return content[start:end+1]
        return content
