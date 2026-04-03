import os
import re
import time
from typing import Dict, List, Optional, Set, Tuple

from colorama import Fore, Style

from ..models import Card, GameState, MapNodeState


class InfoPromptMixin:
    def _clean_card_description(self, description: str) -> str:
        description = description.replace("NL", " ").replace("*", " ")
        description = re.sub(r"#[a-z]", "", description)
        description = re.sub(r"\s+", " ", description)
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
        normalized = re.sub(r"\[[^\]]*\]", "", normalized)
        normalized = re.sub(r"\([^\)]*\)", "", normalized)
        normalized = re.sub(r"\s+", "", normalized)
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

    def _build_map_index(self, state: GameState) -> Dict[Tuple[int, int], MapNodeState]:
        index: Dict[Tuple[int, int], MapNodeState] = {}
        for node in state.map_nodes:
            index[(node.x, node.y)] = node
        return index

    def _get_map_choice_display_text(self, state: GameState, choice_index: int, choice_text: str) -> str:
        if state.screen_type != "MAP":
            return choice_text

        if choice_index < len(state.map_choices_human) and state.map_choices_human[choice_index]:
            return state.map_choices_human[choice_index]

        for item in state.current_map_choices:
            if item.choice_index == choice_index and item.human_label:
                return item.human_label

        return choice_text

    def _parse_map_choice_coords(self, choice_text: str) -> Optional[Tuple[int, int]]:
        match = re.search(r"x\s*=\s*(-?\d+)\s*,\s*y\s*=\s*(-?\d+)", choice_text)
        if not match:
            return None
        return int(match.group(1)), int(match.group(2))

    def _symbol_bucket(self, symbol: str) -> str:
        if not symbol:
            return "other"
        s = symbol.strip().upper()
        if s == "M":
            return "monster"
        if s == "E":
            return "elite"
        if s == "R":
            return "rest"
        if s == "$":
            return "shop"
        if s == "?":
            return "event"
        if s == "T":
            return "treasure"
        if s == "B":
            return "boss"
        return "other"

    def _summarize_map_from_node(self, state: GameState, start: Tuple[int, int]) -> str:
        node_index = self._build_map_index(state)
        if start not in node_index:
            return "无可用路线数据"

        queue: List[Tuple[Tuple[int, int], int]] = [(start, 0)]
        visited: Set[Tuple[int, int]] = set()
        min_to_rest: Optional[int] = None
        min_to_shop: Optional[int] = None
        min_to_elite: Optional[int] = None

        while queue:
            key, dist = queue.pop(0)
            if key in visited:
                continue
            visited.add(key)

            node = node_index.get(key)
            if not node:
                continue

            bucket = self._symbol_bucket(node.symbol)
            if bucket == "rest" and min_to_rest is None:
                min_to_rest = dist
            if bucket == "shop" and min_to_shop is None:
                min_to_shop = dist
            if bucket == "elite" and min_to_elite is None:
                min_to_elite = dist

            for edge in node.children:
                next_key = (edge.x, edge.y)
                if next_key not in visited:
                    queue.append((next_key, dist + 1))

        def _fmt(value: Optional[int]) -> str:
            return f"{value}步" if value is not None else "无"

        parts = []
        parts.append(f"最近火堆:{_fmt(min_to_rest)}")
        parts.append(f"商店:{_fmt(min_to_shop)}")
        parts.append(f"精英:{_fmt(min_to_elite)}")
        return " ".join(parts)

    def _build_map_choice_summary(self, state: GameState, choice_index: int, choice_text: str) -> Optional[str]:
        if state.screen_type != "MAP" or not state.map_nodes:
            return None

        start: Optional[Tuple[int, int]] = None
        for item in state.current_map_choices:
            if item.choice_index == choice_index:
                start = (item.x, item.y)
                break

        if start is None:
            start = self._parse_map_choice_coords(choice_text)

        if start is None:
            return None

        return self._summarize_map_from_node(state, start)

    def _write_debug_prompt(self, state: GameState, prompt: str) -> None:
        if not self.debug_prompt_file:
            return

        try:
            parent_dir = os.path.dirname(self.debug_prompt_file)
            if parent_dir:
                os.makedirs(parent_dir, exist_ok=True)

            timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
            content = (
                f"时间: {timestamp}\n"
                f"楼层: {state.floor}\n"
                f"阶段: {state.act}\n"
                f"屏幕: {state.screen_type}\n"
                f"房间阶段: {state.room_phase}\n"
                f"\n===== PROMPT BEGIN =====\n"
                f"{prompt}\n"
                f"===== PROMPT END =====\n"
            )

            tmp_path = self.debug_prompt_file + ".tmp"
            with open(tmp_path, "w", encoding="utf-8") as f:
                f.write(content)
            os.replace(tmp_path, self.debug_prompt_file)
        except Exception as exc:
            print(Fore.YELLOW + f"写入 Prompt 调试文件失败: {exc}" + Style.RESET_ALL)

    def _format_state_for_prompt(self, state: GameState) -> str:
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

        if state.screen_type == "NONE" and state.room_phase == "COMBAT":
            monster_knowledge = []
            for m in state.monsters:
                info = self.knowledge_base.get_monster_info(m.name, m.id)
                intent_info = self.knowledge_base.get_intent_info(m.intent)
                move_info = ""
                if m.move and m.move.damage is not None:
                    if m.move.hits and m.move.hits > 1:
                        move_info = f" 当前招式伤害: {m.move.damage}x{m.move.hits}。"
                    else:
                        move_info = f" 当前招式伤害: {m.move.damage}。"

                monster_knowledge.append(
                    f"- {m.name} (HP: {m.current_hp}/{m.max_hp}, 格挡: {m.block}): 意图: {m.intent} ({intent_info})。"
                    f"{move_info}已知行为: {info}"
                )

            card_knowledge = []
            for card in state.hand:
                info = self._resolve_card_info(card.name, card.id)
                playable_str = ""
                if not card.is_playable:
                    playable_str = " [不可用/UNPLAYABLE]"

                card_knowledge.append(
                    f"- [{card.index}] {card.name} (耗能: {card.cost}, 类型: {card.type}, 目标: {card.target}){playable_str}: {info}"
                )

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

                potion_knowledge.append(f"- [{potion.index}] {potion.name} ({', '.join(potion_flags)})")

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
3. 你可以使用药水 (type=\"potion\")。
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

        elif state.choice_list and len(state.choice_list) > 0:
            unified_choices = self._build_unified_choices(state)
            choices_str = ""
            for i, (choice_text, _) in enumerate(unified_choices):
                display_text = self._get_map_choice_display_text(state, i, choice_text)
                map_summary = self._build_map_choice_summary(state, i, choice_text)
                if map_summary:
                    choices_str += f"  {i}: {display_text} | 路线摘要: {map_summary}\n"
                else:
                    choice_info = self._get_choice_card_info(state, choice_text)
                    if choice_info != "未知卡牌效果。":
                        choices_str += f"  {i}: {display_text} - {choice_info}\n"
                    else:
                        choices_str += f"  {i}: {display_text}\n"

            specific_info += f"""
- 可选列表 (Choices):
{choices_str}
"""
            if state.screen_type == "COMBAT_REWARD":
                specific_info += "\n- 这是一个战斗奖励界面 (COMBAT_REWARD)。\n"
            if state.screen_type == "MAP":
                specific_info += "\n- 地图符号说明: M=普通战斗, E=精英战斗, R=火堆(休息/升级), ?=未知事件, $=商店, T=宝箱, B=Boss\n"
                if state.map_ascii:
                    specific_info += f"\n- 地图 (ASCII):\n{state.map_ascii}\n"
                if state.map_position is not None:
                    if not state.first_room_chosen:
                        specific_info += f"\n- 当前位置: {state.map_position.human_label}\n"
                    else:
                        specific_info += (
                            f"\n- 当前位置: 第{state.map_position.floor}层，"
                            f"从左往右第{state.map_position.lane_index_from_left}个房间"
                        )
                        if state.map_position.human_label:
                            specific_info += f"（{state.map_position.human_label}）"
                        specific_info += "\n"
                elif state.current_map_node is not None and state.current_map_node.lane_index_from_left > 0:
                    specific_info += (
                        f"\n- 当前位置: 第{state.current_map_node.y}层，"
                        f"从左往右第{state.current_map_node.lane_index_from_left}个房间"
                    )
                    if state.current_map_node.human_label:
                        specific_info += f"（{state.current_map_node.human_label}）"
                    specific_info += "\n"

            specific_info += """
目标: 根据当前情况做出最佳选择。
"""
            rules = """
1. 必须通过指定索引 (index) 来做出选择 (type: choose)。
2. 只允许输出 type=\"choose\"，不要输出 proceed/confirm/skip/cancel 等类型。
3. choice_index 必须落在上方可选列表的有效索引范围内。
"""
            schema_desc = """
{
    "type": "choose",
    "choice_index": int (必须, 对应上方可选列表的索引)
}
"""

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
