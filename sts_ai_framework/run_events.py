"""结构化运行事件 (JSONL) 与楼层摘要。

与 run_log.py 的 TeeWriter/人类可读日志正交:
- run_log.py 负责 debug/run_*.log (终端双写, 人类阅读);
- 本模块负责 debug/run_*.jsonl (每行一个结构化事件, 机器解析), 通过独立裸文件句柄
  写入, 绕开 logging/TeeWriter, 避免两个通道互相污染。

事件 schema: "sts-ai-run/v2"。战斗内部决策由外部 BattleAiMod 执行，本模块仅记录
战斗始末；非战斗动作在生命周期结束时写成一条 decision。
"""

import hashlib
import json
import os
import time
from collections import Counter
from typing import Any, Dict, List, Optional, Tuple

from .models import ActionType, GameAction, GameState
from .run_log import _strip_ansi

SCHEMA = "sts-ai-run/v2"

# 地图节点符号 -> 房间类型 (Slay the Spire 地图图例)
ROOM_TYPES = {
    "M": "monster",
    "E": "elite",
    "?": "event",
    "$": "shop",
    "R": "rest",
    "T": "treasure",
    "B": "boss",
}

# 各幕 Boss 所在楼层 (act1/act2/act3 + 心脏)
BOSS_FLOORS = {16, 33, 50, 54}


def _norm_str(value: Any) -> Optional[str]:
    """字符串归一化: 去 ANSI、去首尾空白; 非字符串转字符串; 空值返回 None。"""
    if value is None:
        return None
    text = _strip_ansi(str(value)).strip()
    return text or None


def _ts_iso(ts: float) -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(ts))


class RunEvents:
    """JSONL 事件通道: 每行一个 JSON 对象, 立即 flush, 一行一事件。"""

    def __init__(self, run_dir: str, run_id: str, **start_fields) -> None:
        os.makedirs(run_dir, exist_ok=True)
        self.run_id = run_id
        self.path = os.path.join(run_dir, run_id + ".jsonl")
        self._fh = open(self.path, "a", encoding="utf-8")
        self._ended = False
        self.emit("run_start", **start_fields)

    def emit(self, event: str, **fields) -> None:
        record = {"schema": SCHEMA, "run_id": self.run_id, "event": event, **fields}
        self._fh.write(json.dumps(record, ensure_ascii=False, default=str) + "\n")
        self._fh.flush()

    def run_end_once(self, **fields) -> None:
        """run_end 事件恰好写一次 (断线/中断/异常只走一次收尾)。"""
        if self._ended:
            return
        self._ended = True
        self.emit("run_end", **fields)

    def close(self) -> None:
        self._fh.close()


def map_symbol_to_room(symbol: str) -> Optional[str]:
    sym = (_norm_str(symbol) or "").strip().upper()
    return ROOM_TYPES.get(sym)


def state_fingerprint(state: GameState) -> Optional[str]:
    """语义关键字段的稳定哈希, 用于检测实质状态变化 (替代逐个字段比较)。"""
    if state is None:
        return None
    fp = {
        "player": {
            "hp": state.player.current_hp,
            "max_hp": state.player.max_hp,
            "block": state.player.block,
            "energy": state.player.energy,
            "gold": state.player.gold,
        },
        "hand": [
            {"uuid": c.uuid, "id": c.id, "cost": c.cost, "upgrades": c.upgrades, "type": c.type}
            for c in state.hand
        ],
        "potions": [{"index": p.index, "id": p.id, "is_empty": p.is_empty} for p in state.potions],
        "monsters": [
            {"index": m.index, "name": m.name, "id": m.id, "hp": m.current_hp, "block": m.block,
             "intent": _norm_str(m.intent),
             "move": {"damage": m.move.damage, "hits": m.move.hits} if m.move else None}
            for m in state.monsters
        ],
        "draw_pile_size": state.draw_pile_size,
        "discard_pile_size": state.discard_pile_size,
        "exhaust_pile_size": state.exhaust_pile_size,
        "floor": state.floor,
        "act": state.act,
        "room_phase": state.room_phase,
        "screen_type": state.screen_type,
        "choice_list": [_norm_str(x) for x in (state.choice_list or [])] or None,
        "reward_card_ids": state.reward_card_ids or None,
        "reward_cards": [c.model_dump() for c in state.reward_cards] or None,
        "can_proceed": state.can_proceed,
        "can_cancel": state.can_cancel,
        "grid_selected_count": state.grid_selected_count,
        "grid_num_cards": state.grid_num_cards,
        "grid_purpose": _norm_str(state.grid_purpose),
        "grid_cards": [c.model_dump() for c in state.grid_cards] or None,
        "is_end_turn_button_enabled": state.is_end_turn_button_enabled,
        "event": (
            {
                "id": state.event.id,
                "class_name": state.event.class_name,
                "phase": state.event.phase,
                "decision_kind": state.event.decision_kind,
            }
            if state.event is not None
            else None
        ),
        "current_map_choices": [
            {
                "choice_index": c.choice_index,
                "x": c.x,
                "y": c.y,
                "symbol": _norm_str(c.symbol),
                "human_label": _norm_str(c.human_label),
                "lane": c.lane_index_from_left,
                "winged": c.winged,
            }
            for c in state.current_map_choices
        ],
        "map_position": (
            {
                "floor": state.map_position.floor,
                "lane": state.map_position.lane_index_from_left,
                "symbol": _norm_str(state.map_position.symbol),
                "human_label": _norm_str(state.map_position.human_label),
            }
            if state.map_position is not None
            else None
        ),
    }
    digest = hashlib.sha256(
        json.dumps(fp, sort_keys=True, ensure_ascii=False).encode("utf-8")
    ).hexdigest()
    return digest[:16]  # 64bit 足够日志区分


def state_changed(pre: GameState, post: GameState) -> bool:
    pre_fp = state_fingerprint(pre)
    return pre_fp is not None and pre_fp != state_fingerprint(post)


def state_diff(pre: GameState, post: GameState) -> dict:
    """两个状态间的可观测变化 (按卡牌 uuid 判定增删/升级)。"""
    pre_deck = {c.uuid: c for c in pre.deck}
    post_deck = {c.uuid: c for c in post.deck}
    upgraded = [
        u for u in pre_deck
        if u in post_deck and post_deck[u].upgrades > pre_deck[u].upgrades
    ]
    pre_relic_ids = {r.id for r in pre.relics}
    return {
        "hp": post.player.current_hp - pre.player.current_hp,
        "gold": post.player.gold - pre.player.gold,
        "deck_added": [post_deck[u].id for u in post_deck if u not in pre_deck],
        "deck_removed": [pre_deck[u].id for u in pre_deck if u not in post_deck],
        "upgraded": upgraded,
        "relics_added": [r.id for r in post.relics if r.id not in pre_relic_ids],
        "potions": len(post.potions) - len(pre.potions),
    }


def _player_summary(state: GameState) -> dict:
    return {
        "hp": state.player.current_hp,
        "max_hp": state.player.max_hp,
        "energy": state.player.energy,
        "gold": state.player.gold,
        "hand": len(state.hand),
    }


def extract_candidates(state: GameState) -> Tuple[str, List[dict], dict]:
    """提取当前屏幕的所有候选项语义。

    返回 (screen_kind, candidates, screen_semantics)。
    candidate = {"index", "stable_id", "name", "kind", "enabled", "score", "semantics"}
    score 由决策插桩 (meta) 回填, 此处恒为 None。
    """
    screen = _norm_str(state.screen_type) or "NONE"
    candidates: List[dict] = []

    # EVENT: 结构化事件语义最丰富, 直接来自 EventState.choices
    if screen == "EVENT" and state.event is not None:
        for c in state.event.choices:
            candidates.append(
                {
                    "index": c.button_index,
                    "stable_id": c.action_index,
                    "name": _norm_str(c.label),
                    "kind": c.kind,
                    "enabled": c.enabled,
                    "score": None,
                    "semantics": {
                        "followup": c.followup,
                        "outcomes": [
                            o.model_dump(exclude_none=True) for o in c.outcomes
                        ],
                    },
                }
            )
        return (
            "event",
            candidates,
            {
                "event_id": state.event.id,
                "class_name": state.event.class_name,
                "decision_kind": state.event.decision_kind,
            },
        )

    # MAP: 地图选点, 稳定 id 为 "x_y"
    if state.current_map_choices:
        for c in state.current_map_choices:
            symbol = _norm_str(c.symbol) or ""
            candidates.append(
                {
                    "index": c.choice_index,
                    "stable_id": f"{c.x}_{c.y}",
                    "name": _norm_str(c.human_label),
                    "kind": None,
                    "enabled": True,
                    "score": None,
                    "semantics": {
                        "symbol": symbol,
                        "room_type": ROOM_TYPES.get(symbol.upper()),
                        "lane": c.lane_index_from_left,
                        "winged": c.winged,
                    },
                }
            )
        if candidates:
            node = state.current_map_node
            return (
                "map",
                candidates,
                {
                    "room_symbol": _norm_str(node.symbol) if node else None,
                    "room_type": map_symbol_to_room(node.symbol) if node else None,
                },
            )

    # CARD_REWARD: structured candidates preserve UUID and upgrade level.
    if screen == "CARD_REWARD" and state.reward_cards:
        for card in state.reward_cards:
            candidates.append(
                {
                    "index": card.choice_index,
                    "stable_id": card.uuid,
                    "name": card.name,
                    "kind": "card",
                    "enabled": True,
                    "score": None,
                    "semantics": {"id": card.id, "upgrades": card.upgrades},
                }
            )
        for i in range(len(state.reward_cards), len(state.choice_list or [])):
            candidates.append(
                {"index": i, "stable_id": i, "name": _norm_str(state.choice_list[i]),
                 "kind": None, "enabled": True, "score": None, "semantics": None}
            )
        return "card_reward", candidates, {}

    # Legacy CARD_REWARD payload.
    if screen == "CARD_REWARD" and state.reward_card_ids:
        names = {}
        for c in state.deck:
            names.setdefault(c.id, c.name)
        for i, card_id in enumerate(state.reward_card_ids):
            candidates.append(
                {
                    "index": i,
                    "stable_id": card_id,
                    "name": names.get(card_id),
                    "kind": None,
                    "enabled": True,
                    "score": None,
                    "semantics": None,
                }
            )
        # choice_list 中可能有 skip/leave 之类额外选项
        for i in range(len(state.reward_card_ids or []), len(state.choice_list or [])):
            candidates.append(
                {
                    "index": i,
                    "stable_id": i,
                    "name": _norm_str(state.choice_list[i]),
                    "kind": None,
                    "enabled": True,
                    "score": None,
                    "semantics": None,
                }
            )
        return "card_reward", candidates, {}

    # GRID: 通用卡牌选择
    if screen == "GRID":
        by_index = {card.choice_index: card for card in state.grid_cards}
        for i, text in enumerate(state.choice_list or []):
            card = by_index.get(i)
            candidates.append(
                {
                    "index": i,
                    "stable_id": card.uuid if card else i,
                    "name": card.name if card else _norm_str(text),
                    "kind": "card" if card else "confirm",
                    "enabled": True,
                    "score": None,
                    "semantics": ({"id": card.id, "upgrades": card.upgrades,
                                   "can_upgrade": card.can_upgrade} if card else None),
                }
            )
        return (
            "grid",
            candidates,
            {"purpose": _norm_str(state.grid_purpose), "num": state.grid_num_cards},
        )

    # 其余屏幕 (REST/SHOP_SCREEN/BOSS_REWARD/CHEST/COMBAT_REWARD/按钮态): choice_list 兜底
    for i, text in enumerate(state.choice_list or []):
        candidates.append(
            {
                "index": i,
                "stable_id": i,
                "name": _norm_str(text),
                "kind": None,
                "enabled": True,
                "score": None,
                "semantics": None,
            }
        )
    kind_map = {
        "REST": "rest",
        "SHOP_SCREEN": "shop",
        "BOSS_REWARD": "boss_reward",
        "CHEST": "chest",
        "COMBAT_REWARD": "combat_reward",
    }
    return kind_map.get(screen, "other"), candidates, {}


def _chosen_from_action(action: GameAction, candidates: List[dict]) -> dict:
    """从动作索引定位候选语义; 无索引动作 (proceed/cancel/end_turn...) 只记动作类型。"""
    if action.type == ActionType.PLAY:
        idx = action.card_index
    elif action.type == ActionType.POTION:
        idx = action.potion_index
    elif action.type == ActionType.CHOOSE:
        idx = action.choice_index
    else:
        return {"action": action.type.value, "index": None, "stable_id": None,
                "name": None, "kind": None}
    cand = next((c for c in candidates if c["index"] == idx), None)
    return {
        "action": action.type.value,
        "index": idx,
        "stable_id": cand["stable_id"] if cand else None,
        "name": cand["name"] if cand else None,
        "kind": cand["kind"] if cand else None,
    }


class PendingTracker:
    """Serial non-combat action lifecycle writer for the v2 decision event."""

    def __init__(self, events: RunEvents, deadline_s: float = 20.0,
                 on_confirmed=None) -> None:
        self.events = events
        self.deadline_s = deadline_s
        self._seq = 0
        self._pending: Dict[str, dict] = {}
        self._on_confirmed = on_confirmed

    def can_register(self) -> bool:
        return not self._pending

    def register(
        self,
        state: GameState,
        action: GameAction,
        source: str,
        ts: float,
        meta: Optional[dict] = None,
    ) -> Tuple[str, str, dict]:
        """Create one in-memory lifecycle. Only final decisions are written."""
        if self._pending:
            existing_id, existing = next(iter(self._pending.items()))
            return existing_id, existing["screen_kind"], existing["chosen"]
        self._seq += 1
        screen = _norm_str(state.screen_type) or "none"
        decision_id = f"f{state.floor}-{screen.lower()}-{self._seq}"
        screen_kind, candidates, screen_semantics = extract_candidates(state)
        chosen = _chosen_from_action(action, candidates)
        pre_fp = state_fingerprint(state)
        self._pending[decision_id] = {
            "pre_state": state,
            "pre_fp": pre_fp,
            "submit_ts": ts,
            "screen_kind": screen_kind,
            "chosen": chosen,
            "record": {
                "decision_id": decision_id, "floor": state.floor, "act": state.act,
                "screen": screen, "screen_kind": screen_kind, "phase": state.room_phase,
                "source": source, "meta": meta,
                "action": {"type": action.type.value, "card_index": action.card_index,
                           "potion_index": action.potion_index, "target_index": action.target_index,
                           "choice_index": action.choice_index},
                "chosen": chosen, "candidates": candidates,
                "screen_semantics": screen_semantics,
                "pre": {"fingerprint": pre_fp, **_player_summary(state)},
                "started_ts": _ts_iso(ts), "started_t": round(ts, 3),
            },
        }
        return decision_id, screen_kind, chosen

    def on_submit(self, decision_id: str, submitted: bool, resp: Optional[dict],
                  err: Optional[str], latency_ms: float, ts: float) -> None:
        entry = self._pending.get(decision_id)
        if entry is None:
            return
        entry["submit"] = {"ok": submitted, "status_code": resp.get("status") if resp else None,
                           "err": err, "latency_ms": round(latency_ms, 1)}
        if not submitted:
            self._finish(decision_id, "submit_failed", "immediate",
                         entry["pre_state"], latency_ms, ts)

    def confirm_immediate(self, decision_id: str, post_state: Optional[GameState],
                          latency_ms: float, ts: float) -> str:
        """提交后即时快确认: post_state 有实质变化 -> confirmed; 否则留在 pending。

        返回 "confirmed"/"pending" 供主循环打印单行摘要。
        """
        entry = self._pending.get(decision_id)
        if entry is None or post_state is None:
            return "pending"
        if self._is_effective(entry, post_state):
            self._finish(decision_id, "confirmed", "immediate", post_state, latency_ms, ts)
            return "confirmed"
        return "pending"

    def check(self, state: GameState, ts: float) -> None:
        """主循环每轮对 pending 动作做确认/超时检查。"""
        for decision_id in list(self._pending):
            entry = self._pending[decision_id]
            if self._is_effective(entry, state):
                self._finish(decision_id, "confirmed", "poll", state, 0.0, ts)
            elif ts - entry["submit_ts"] > self.deadline_s:
                self._finish(decision_id, "rejected_timeout", "poll",
                             entry["pre_state"], 0.0, ts)

    def flush(self, status: str, ts: float) -> None:
        """run 收尾: 未决动作统一标记 (默认 interrupted, 避免误标超时)。"""
        for decision_id in list(self._pending):
            entry = self._pending[decision_id]
            self._finish(decision_id, status, "poll", entry["pre_state"], 0.0, ts)

    def pending_count(self) -> int:
        return len(self._pending)

    def _is_effective(self, entry: dict, post: GameState) -> bool:
        pre = entry["pre_state"]
        chosen = entry["chosen"]
        action = entry["record"]["action"]
        if pre.floor != post.floor:
            return True
        if pre.screen_type == "GRID":
            if chosen.get("kind") == "confirm" or chosen.get("name") == "confirm":
                return post.screen_type != "GRID"
            return (post.screen_type != "GRID" or
                    post.grid_selected_count > pre.grid_selected_count)
        if pre.screen_type == "CARD_REWARD":
            target_uuid = chosen.get("stable_id") if chosen.get("kind") == "card" else None
            return (post.screen_type != "CARD_REWARD" or
                    (target_uuid is not None and any(c.uuid == target_uuid for c in post.deck)))
        if pre.screen_type == "EVENT":
            pre_phase = pre.event.phase if pre.event else None
            post_phase = post.event.phase if post.event else None
            return (post.screen_type != "EVENT" or pre_phase != post_phase or
                    pre.choice_list != post.choice_list)
        if pre.screen_type in {"REST", "COMBAT_REWARD"}:
            return post.screen_type != pre.screen_type or pre.choice_list != post.choice_list
        if action["type"] in {ActionType.PROCEED.value, ActionType.CANCEL.value}:
            return post.screen_type != pre.screen_type or pre.choice_list != post.choice_list
        return state_fingerprint(post) != entry["pre_fp"]

    def _finish(self, decision_id: str, status: str, method: str,
                post: GameState, latency_ms: float, ts: float) -> None:
        entry = self._pending.pop(decision_id)
        pre = entry["pre_state"]
        delta = state_diff(pre, post) if status == "confirmed" else state_diff(pre, pre)
        self.events.emit(
            "decision", **entry["record"], submit=entry.get("submit"), status=status,
            confirm_method=method, latency_ms=round(latency_ms, 1), delta=delta,
            post={"fingerprint": state_fingerprint(post), **_player_summary(post)},
            ts=_ts_iso(ts), t=round(ts, 3),
        )
        if status == "confirmed" and self._on_confirmed is not None:
            self._on_confirmed(decision_id, entry["screen_kind"], entry["chosen"], delta)


class BattleTracker:
    """战斗状态记录: 进入/实质变化/结束/无变化心跳。只记可观测变化, 不轮询刷屏。"""

    def __init__(self, events: RunEvents, heartbeat_s: float = 30.0,
                 on_battle_end=None) -> None:
        self.events = events
        self.heartbeat_s = heartbeat_s
        self._on_battle_end = on_battle_end  # 回调(battle_id, result, enemies), 供楼层聚合
        self.in_combat = False
        self.battle_id: Optional[str] = None
        self.battle_seq = 0
        self._last_fp = None
        self._last_change_ts = 0.0
        self._enter_ts = 0.0
        self._enter_floor = None
        self._last_monsters: List[dict] = []
        self._last_player_hp = None
        self._enter_player_hp = None
        self.last_battle_result: Optional[str] = None  # 最近一场战斗结果 (victory/unknown)

    def update(self, state: GameState, ts: float) -> Optional[str]:
        """Track only battle entry and exit; combat internals belong to BattleAiMod."""
        in_combat_now = state.screen_type == "NONE" and state.room_phase == "COMBAT"
        if in_combat_now:
            if not self.in_combat:
                self._start(state, ts)
                return "changed"
            self._last_monsters = [
                {"index": m.index, "name": m.name, "id": m.id, "hp": m.current_hp,
                 "max_hp": m.max_hp, "block": m.block, "intent": _norm_str(m.intent)}
                for m in state.monsters
            ]
            self._last_player_hp = state.player.current_hp
            return None
        if self.in_combat:
            self._end(state, ts, reason="screen_changed")
            return "changed"
        return None

    def _start(self, state: GameState, ts: float) -> None:
        self.battle_seq += 1
        self.battle_id = f"b{self.battle_seq}"
        self.in_combat = True
        self._enter_ts = ts
        self._enter_floor = state.floor
        self._last_fp = state_fingerprint(state)
        self._last_change_ts = ts
        self._last_monsters = [
            {"index": m.index, "name": m.name, "id": m.id, "hp": m.current_hp,
             "max_hp": m.max_hp, "block": m.block, "intent": _norm_str(m.intent)}
            for m in state.monsters
        ]
        self._last_player_hp = state.player.current_hp
        self._enter_player_hp = state.player.current_hp
        self.events.emit(
            "battle_start",
            battle_id=self.battle_id,
            floor=state.floor,
            act=state.act,
            monsters=self._last_monsters,
            player={"hp": state.player.current_hp, "max_hp": state.player.max_hp,
                    "block": state.player.block, "hand": len(state.hand)},
            elite=(state.map_position is not None
                   and map_symbol_to_room(state.map_position.symbol) == "elite"),
            ts=_ts_iso(ts),
            t=round(ts, 3),
        )
        names = [m["name"] for m in self._last_monsters if m["hp"] > 0]
        print(f"[战斗 {self.battle_id} 第{state.floor}层] 开始 | 敌人: {'、'.join(names) or '无'} | HP {state.player.current_hp}/{state.player.max_hp}")

    def _end(self, state: GameState, ts: float, reason: str) -> None:
        reached_reward = state.screen_type in {"COMBAT_REWARD", "CARD_REWARD", "BOSS_REWARD"}
        alive = [] if reached_reward else [m for m in self._last_monsters if m["hp"] > 0]
        result = "victory" if reached_reward or (not alive and self._last_monsters) else "unknown"
        self.last_battle_result = result
        self.in_combat = False
        ended_id = self.battle_id
        self.events.emit(
            "battle_end",
            battle_id=ended_id,
            floor=self._enter_floor,
            result=result,
            monsters_alive=[m["name"] for m in alive],
            monsters=self._last_monsters,
            player={"hp": state.player.current_hp, "max_hp": state.player.max_hp,
                    "block": state.player.block},
            duration_s=round(ts - self._enter_ts, 1),
            player_hp_delta=state.player.current_hp - self._enter_player_hp,
            reason=reason,
            ts=_ts_iso(ts),
            t=round(ts, 3),
        )
        self.battle_id = None
        if self._on_battle_end is not None:
            self._on_battle_end(ended_id, result, [m["name"] for m in alive])
        print(f"[战斗结束] 第{self._enter_floor}层 | 结果: {result} | 耗时 {ts - self._enter_ts:.1f}s")

    def end_forced(self, ts: float) -> None:
        """run 收尾兜底: 战斗中异常结束时补一条 battle_end(unknown)。"""
        if not self.in_combat:
            return
        self.in_combat = False
        ended_id = self.battle_id
        self.events.emit(
            "battle_end",
            battle_id=ended_id,
            floor=self._enter_floor,
            result="unknown",
            monsters_alive=[m["name"] for m in self._last_monsters if m["hp"] > 0],
            duration_s=round(ts - self._enter_ts, 1),
            player_hp_delta=0,
            reason="interrupted",
            ts=_ts_iso(ts),
            t=round(ts, 3),
        )
        if self._on_battle_end is not None:
            self._on_battle_end(
                ended_id, "unknown",
                [m["name"] for m in self._last_monsters if m["hp"] > 0],
            )
        self.battle_id = None


class FloorTracker:
    """楼层级跟踪: 进出层事件 + 决策/战斗聚合 + run 结束时的楼层摘要表。"""

    def __init__(self, events: RunEvents) -> None:
        self.events = events
        self.cur_floor: Optional[int] = None
        self.cur_act: Optional[int] = None
        self._entry_ts = 0.0
        self._entry_snapshot: Optional[dict] = None
        self._entry_room: Optional[str] = None
        self._decisions: List[str] = []  # 本层决策简述
        self._rewards: List[str] = []  # 本层累计奖励变化
        self._enemies: List[str] = []
        self.rows: List[dict] = []
        self._last_state: Optional[GameState] = None  # 最近一次轮询状态 (退出层时取 HP)

    def update(self, state: GameState, ts: float) -> None:
        self._last_state = state
        if state.floor == self.cur_floor:
            return
        if self.cur_floor is not None:
            self._exit(ts)
        self._enter(state, ts)

    def _enter(self, state: GameState, ts: float) -> None:
        self.cur_floor = state.floor
        self.cur_act = state.act
        self._entry_ts = ts
        self._entry_snapshot = {
            "hp": state.player.current_hp,
            "max_hp": state.player.max_hp,
            "gold": state.player.gold,
            "deck_size": len(state.deck),
            "relics": [r.id for r in state.relics],
            "potions": len(state.potions),
        }
        self._decisions = []
        self._rewards = []
        self._enemies = []
        room = self._infer_room(state)
        self._entry_room = room
        self.events.emit(
            "floor_entry",
            floor=state.floor,
            act=state.act,
            room=room,
            snapshot=self._entry_snapshot,
            elapsed_s=round(ts - self._entry_ts, 1),
            ts=_ts_iso(ts),
            t=round(ts, 3),
        )

    def _infer_room(self, state: GameState) -> str:
        """从屏幕/阶段/地图节点推断房间类型, 用于摘要表。"""
        if state.room_phase == "COMBAT":
            return "Boss" if state.floor in BOSS_FLOORS else "战斗"
        screen = _norm_str(state.screen_type) or ""
        screen_map = {
            "REST": "篝火",
            "SHOP_SCREEN": "商店",
            "EVENT": "事件",
            "CHEST": "宝箱",
            "COMBAT_REWARD": "战斗奖励",
            "CARD_REWARD": "选卡",
            "GRID": "GRID",
            "BOSS_REWARD": "Boss遗物",
            "MAP": "地图",
        }
        if screen in screen_map:
            return screen_map[screen]
        node = state.current_map_node
        if node is not None:
            rt = map_symbol_to_room(node.symbol)
            if rt:
                return rt.capitalize()
        return screen or "?"

    def _exit(self, ts: float) -> None:
        hp0 = self._entry_snapshot["hp"]
        hp1 = self._last_state.player.current_hp if self._last_state else hp0
        hp_disp = f"{hp0}→{hp1}" if hp0 != hp1 else f"{hp0}"
        room = self._entry_room or "?"
        self.rows.append(
            {
                "floor": self.cur_floor,
                "act": self.cur_act,
                "room": room,
                "encounter": "、".join(dict.fromkeys(self._enemies)) or "—",
                "hp": hp_disp,
                "decisions": "、".join(self._decisions) or "—",
                "rewards": "、".join(self._rewards) or "—",
                "duration": f"{ts - self._entry_ts:.0f}s",
            }
        )
        self.events.emit(
            "floor_exit",
            floor=self.cur_floor,
            act=self.cur_act,
            room=room,
            duration_s=round(ts - self._entry_ts, 1),
            hp_delta=hp1 - hp0,
            rewards=self._rewards,
            decisions=self._decisions,
            enemies=self._enemies,
            ts=_ts_iso(ts),
            t=round(ts, 3),
        )
        self.cur_floor = None

    def observe_decision(self, screen_kind: str, chosen: dict) -> None:
        """记录本层的一个决策 (摘要表"决策"列最多保留 3 条)。"""
        if chosen and chosen.get("name"):
            label = chosen["name"]
        elif chosen and chosen.get("action"):
            label = chosen["action"]
        else:
            label = screen_kind
        if len(self._decisions) < 3:
            self._decisions.append(str(label))

    def observe_battle(self, battle_id: Optional[str], result: str,
                       enemies: List[str]) -> None:
        self._enemies.extend(e for e in enemies if e not in self._enemies)

    def observe_delta(self, delta: dict) -> None:
        """聚合决策确认后的奖励变化到当前楼层。"""
        for cid in delta.get("deck_added", []):
            self._rewards.append(f"+{cid}")
        for cid in delta.get("deck_removed", []):
            self._rewards.append(f"-{cid}")
        for uid in delta.get("upgraded", []):
            reward = f"升级卡:{uid}"
            if reward not in self._rewards:
                self._rewards.append(reward)
        for rid in delta.get("relics_added", []):
            self._rewards.append(f"遗物:{rid}")
        gold = delta.get("gold", 0)
        if gold:
            self._rewards.append(f"金币{gold:+d}")

    def finalize(self, ts: float) -> List[dict]:
        if self.cur_floor is not None:
            self._exit(ts)
        return self.rows

    def last_state_snapshot(self, state: Optional[GameState]) -> dict:
        if state is None:
            return {"floor": self.cur_floor, "in_combat": False}
        return {
            "floor": state.floor,
            "act": state.act,
            "screen": state.screen_type,
            "phase": state.room_phase,
            "hp": state.player.current_hp,
            "max_hp": state.player.max_hp,
            "in_combat": state.screen_type == "NONE" and state.room_phase == "COMBAT",
            "monsters_alive": [m.name for m in state.monsters if m.current_hp > 0],
        }


def classify_run_end(
    last_state: Optional[GameState],
    err_counts: Counter,
    last_battle_result: Optional[str],
    killed: bool = False,
) -> Tuple[str, str, str]:
    """终局分类 (status, confidence, reason)。

    死亡判定依赖启发式: BattleAiMod 禁用了死亡屏幕 (DisableDeathScreenpatch),
    死亡时 Mod 直接停止响应, Python 无法精确区分死亡与阻塞, 故带 confidence。
    """
    if killed:
        return "killed", "high", "手动停止 (KeyboardInterrupt)"
    n_conn = err_counts.get("CONNECTION_ERROR", 0)
    n_silent = err_counts.get("TIMEOUT", 0) + err_counts.get("MOD_BUSY", 0)
    if n_conn > 0 and n_conn >= n_silent:
        return "connection_lost", "high", f"HTTP 连接中断 {n_conn} 次后退出"
    if n_silent > 0 and last_state is not None and last_state.room_phase == "COMBAT":
        return "died", "high", (
            f"战斗中 Mod 静默 {n_silent} 次 (禁用死亡屏幕时战斗静默通常=死亡, 末态 HP "
            f"{last_state.player.current_hp}/{last_state.player.max_hp})"
        )
    if n_silent > 0:
        return "connection_lost", "low", f"Mod 在非战斗状态静默 {n_silent} 次"
    if last_battle_result == "victory" and last_state is not None and last_state.floor in BOSS_FLOORS:
        return "game_finished", "medium", f"第 {last_state.floor} 层 Boss 战后游戏结束"
    return "unknown", "low", "原因不明 (无失败记录且非 Boss 战后结束)"


def build_summary_markdown(run_id: str, rows: List[dict], totals: dict, run_end: dict) -> str:
    """生成便于人类复盘的楼层摘要表 (与 run_summary 事件同构)。"""
    lines = [
        f"# Run 摘要 {run_id}",
        "",
        f"**结果**: {run_end.get('status')} (置信度 {run_end.get('confidence')}) — {run_end.get('reason')}",
        f"**总计**: 时长 {totals.get('duration_s', 0):.0f}s | 决策 {totals.get('decisions', 0)} | "
        f"战斗 {totals.get('battles', 0)} | 到达层数 {totals.get('floors_reached', 0)}",
        "",
        "|层|房间|对手/事件|HP变化|决策|奖励/牌组变化|耗时|",
        "|--:|---|---|--:|---|---|--:|",
    ]
    for r in rows:
        lines.append(
            f"|{r['floor']}|{r['room']}|{r['encounter']}|{r['hp']}|{r['decisions']}|{r['rewards']}|{r['duration']}|"
        )
    lines += [
        "",
        "> 备注: 战斗内部决策由 BattleAiMod 执行, 本日志仅记录可观测状态变化; "
        "奖励列为前后状态推断, 跳过奖励等无状态变化的情况显示 —。",
    ]
    return "\n".join(lines)


class RunSession:
    """主循环的日志协调器: 各 tracker 的编排入口, 便于 __main__ 编排与测试驱动。"""

    def __init__(self, events: RunEvents, deadline_s: float = 20.0,
                 heartbeat_s: float = 30.0) -> None:
        self.events = events
        self.floors = FloorTracker(events)
        self.tracker = PendingTracker(
            events, deadline_s, on_confirmed=self._on_decision_confirmed,
        )
        self.battles = BattleTracker(
            events, heartbeat_s, on_battle_end=self._on_battle_ended,
        )
        self.err_counts: Counter = Counter()
        self.consecutive_failures = 0
        self.last_state: Optional[GameState] = None
        self.started = time.time()

    def _on_decision_confirmed(self, decision_id: str, screen_kind: str,
                               chosen: dict, delta: dict) -> None:
        self.floors.observe_decision(screen_kind, chosen)
        self.floors.observe_delta(delta)

    def _on_battle_ended(self, battle_id: Optional[str], result: str,
                         enemies: List[str]) -> None:
        self.floors.observe_battle(battle_id, result, enemies)

    def on_state_ok(self, state: GameState, ts: float) -> Optional[str]:
        """处理一次成功读取的状态。返回战斗进度信号 ("changed"/"heartbeat"/None)。"""
        self.last_state = state
        self.consecutive_failures = 0
        battle_signal = self.battles.update(state, ts)
        self.floors.update(state, ts)
        self.tracker.check(state, ts)
        return battle_signal

    def on_fetch_fail(self, err_kind: str, err_msg: str, ts: float) -> None:
        self.err_counts[err_kind] += 1
        self.consecutive_failures += 1

    def on_combat(self, state: GameState, ts: float) -> GameAction:
        self.battles.update(state, ts)
        return GameAction(type=ActionType.WAIT)

    def record_decision(self, state: GameState, action: GameAction, source: str,
                        ts: float, meta: Optional[dict] = None) -> str:
        decision_id, _, _ = self.tracker.register(
            state, action, source, ts, meta,
        )
        return decision_id

    def finish(self, status: str, confidence: str, reason: str, ts: float) -> str:
        """run 收尾: 未决动作标记 -> 楼层收尾 -> run_end -> 摘要。返回 markdown 文本。"""
        self.tracker.flush("interrupted", ts)
        self.battles.end_forced(ts)
        rows = self.floors.finalize(ts)
        last = self.floors.last_state_snapshot(self.last_state)
        duration_s = ts - self.started
        totals = {
            "duration_s": round(duration_s, 1),
            "decisions": self.tracker._seq,
            "battles": self.battles.battle_seq,
            "floors_reached": self.floors.cur_floor
            if self.floors.cur_floor is not None
            else (rows[-1]["floor"] if rows else 0),
        }
        run_end = {
            "status": status,
            "confidence": confidence,
            "reason": reason,
            "last_state": last,
            "last_state_hash": state_fingerprint(self.last_state),
            "error": {
                "kind": self.err_counts.most_common(1)[0][0] if self.err_counts else None,
                "consecutive_failures": self.consecutive_failures,
                "counts": dict(self.err_counts),
            },
            "duration_s": round(duration_s, 1),
            "decisions": totals["decisions"],
            "battles": totals["battles"],
            "floors_reached": totals["floors_reached"],
        }
        self.events.run_end_once(
            **run_end, ts=_ts_iso(ts), t=round(ts, 3),
        )
        self.events.emit("run_summary", rows=rows, totals=totals,
                         ts=_ts_iso(ts), t=round(ts, 3))
        return build_summary_markdown(self.events.run_id, rows, totals, run_end)
