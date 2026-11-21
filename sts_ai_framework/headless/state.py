"""Offline, read-only Headless observation -> existing GameState contract.

No client, engine, policy, or action imports. Identity/key memory is per mapper,
per run/branch, with explicit snapshot order; a failed conversion commits nothing.
"""
from __future__ import annotations

import hashlib
import json
from uuid import UUID

from ..models import GameState

BRIDGE_SCHEMA = "headless-gamestate-v1"
_COLORS = ("ruby", "emerald", "sapphire")
_SYMBOLS = frozenset(("M", "E", "R", "$", "T", "?", "B"))
_PHASES = {"NEOW": "EVENT", "EVENT": "EVENT", "MAP": "MAP", "PLAYER_TURN": "NONE",
           "MONSTER_TURN": "NONE", "REWARD": "COMBAT_REWARD", "CARD_PICK": "CARD_REWARD",
           "CAMPFIRE": "REST", "SHOP": "SHOP_SCREEN", "PROCEED": "NONE",
           "CHEST": "CHEST", "BOSS_CHEST": "CHEST", "BOSS_RELIC": "BOSS_REWARD",
           "GAME_OVER": "GAME_OVER", "RUN_COMPLETE": "GAME_OVER"}


class HeadlessContractError(ValueError):
    """Missing/ambiguous source facts, unsupported schema, or replay ordering."""


def _int(value, name, minimum=None, maximum=None):
    if type(value) is not int or (minimum is not None and value < minimum) or (maximum is not None and value > maximum):
        raise HeadlessContractError("Invalid integer: " + name)
    return value


def _bool(value, name):
    if type(value) is not bool:
        raise HeadlessContractError("Invalid boolean: " + name)
    return value


def _text(value, name):
    if not isinstance(value, str) or not value.strip():
        raise HeadlessContractError("Missing text: " + name)
    return value


def _list(value, name):
    if not isinstance(value, list):
        raise HeadlessContractError("Missing list: " + name)
    return value


def _cards(raw, name):
    cards, seen = [], set()
    for index, item in enumerate(_list(raw, name)):
        if not isinstance(item, dict):
            raise HeadlessContractError("Invalid card: " + name)
        try:
            identity = str(UUID(_text(item.get("uuid"), name + ".uuid")))
        except (ValueError, AttributeError) as error:
            raise HeadlessContractError("Missing/invalid engine card UUID: " + name) from error
        if identity in seen:
            raise HeadlessContractError("Duplicate UUID within " + name)
        seen.add(identity)
        if "index" in item and _int(item["index"], "card.index", 0) != index:
            raise HeadlessContractError("Card index must be zero-based group order")
        card = {"index": index, "uuid": identity, "id": _text(item.get("id"), "card.id"),
                "name": _text(item.get("name"), "card.name"),
                "cost": _int(item.get("cost"), "card.cost", -2),
                "cost_for_turn": _int(item.get("costForTurn"), "card.costForTurn", -2),
                # Count, not display name or the lossy upgraded flag, is authoritative.
                "upgrades": _int(item.get("timesUpgraded"), "card.timesUpgraded", 0),
                "misc": _int(item.get("misc"), "card.misc"), "is_playable": False,
                "is_bottled": _bool(item.get("bottled"), "card.bottled"),
                "can_upgrade": _bool(item.get("canUpgrade"), "card.canUpgrade")}
        for source, target in (("type", "type"), ("target", "target"), ("rarity", "rarity")):
            card[target] = _text(item.get(source), "card." + source)
        for source, target in (("damage", "damage"), ("block", "block"), ("magicNumber", "magic_number"),
                               ("baseDamage", "base_damage"), ("baseBlock", "base_block"),
                               ("baseMagicNumber", "base_magic_number")):
            if source in item and item[source] is not None:
                value = _int(item[source], "card." + source)
                if value >= 0:  # -1 is the engine's unavailable sentinel, not a measured zero.
                    card[target] = value
        if "exhaust" in item:
            card["exhausts"] = _bool(item["exhaust"], "card.exhaust")
        cards.append(card)
    return cards


def _xy(raw):
    return _int(raw.get("x"), "map.x"), _int(raw.get("y"), "map.y")


def _map(raw, *, room=None):
    if raw is None:
        return {"map_nodes": [], "current_map_choices": []}
    if not isinstance(raw, dict):
        raise HeadlessContractError("Invalid map object")
    grid, edges = {}, {}
    for item in _list(raw.get("nodes"), "map.nodes"):
        x, y = _xy(item)
        if not (0 <= x < 7 and 0 <= y < 15):
            raise HeadlessContractError("Unsupported map coordinate")
        if (x, y) in grid:
            raise HeadlessContractError("Duplicate map coordinate")
        symbol = item.get("symbol")
        if symbol not in _SYMBOLS:
            raise HeadlessContractError("Unknown public room symbol")
        grid[x, y] = {"x": x, "y": y, "symbol": symbol,
                      "burning": _bool(item.get("burning"), "map.burning"), "children": []}
        edges[x, y] = {_xy(edge) for edge in _list(item.get("children"), "map.children")}
        if any(edge.get("winged", False) for edge in item["children"]):
            raise HeadlessContractError("Winged candidates are not physical map edges")
    boss = False
    incoming = set()
    for (x, y), targets in edges.items():
        normalized = set()
        for dx, dy in targets:
            # The real map's UI boss edge is (3,16); GameKernel uses (-1,15).
            if y == 14 and ((dy == 15 and -1 <= dx < 7) or (dy == 16 and dx == 3)):
                normalized.add((-1, 15))
                boss = True
            elif (dx, dy) not in grid or dy != y + 1 or abs(dx - x) > 1:
                raise HeadlessContractError("Dangling or non-vanilla physical map edge")
            else:
                normalized.add((dx, dy))
                incoming.add((dx, dy))
        grid[x, y]["children"] = [{"x": a, "y": b, "winged": False} for a, b in sorted(normalized)]
    # Drop the game's unused 7x15 backing cells; never turn them into paths.
    grid = {xy: node for xy, node in grid.items() if node["children"] or xy in incoming}
    if boss:
        grid[-1, 15] = {"x": -1, "y": 15, "symbol": "B", "burning": False, "children": []}
    for y in {xy[1] for xy in grid}:
        for lane, xy in enumerate(sorted(xy for xy in grid if xy[1] == y), 1):
            grid[xy]["lane_index_from_left"] = lane
    current_raw = raw.get("current")
    current = None
    if current_raw:
        current = _xy(current_raw)
        if current[1] == 15:
            current = (-1, 15)
            if current not in grid and room in {"MonsterRoomBoss", "TreasureRoomBoss", "VictoryRoom", "TrueVictoryRoom"}:
                grid[current] = {"x": -1, "y": 15, "symbol": "B", "burning": False,
                                 "children": [], "lane_index_from_left": 1}
        if current[1] == -1:  # Neow sentinel is not a grid location.
            current = None
        elif current not in grid:
            raise HeadlessContractError("Current node not in observed map")
    for xy, node in grid.items():
        node["is_current"] = xy == current
    result = {"map_nodes": [grid[xy] for xy in sorted(grid, key=lambda p: (p[1], p[0]))],
              "current_map_choices": []}  # Action indices are deliberately not synthesized.
    if current is not None:
        node = grid[current]
        result["current_map_node"] = {key: node[key] for key in ("x", "y", "symbol", "lane_index_from_left")}
        result["map_position"] = {"floor": node["y"], "symbol": node["symbol"],
                                  "lane_index_from_left": node["lane_index_from_left"]}
    return result


def _history(raw, floor):
    result = {}
    for entry in _list(raw, "history"):
        at = _int(entry.get("floor"), "history.floor", 1)
        if at >= floor or entry.get("resolved_symbol") is None:
            continue
        if at in result:
            raise HeadlessContractError("Duplicate history floor")
        selected = entry.get("selected_symbol") or "?"
        resolved = entry["resolved_symbol"]
        if selected not in _SYMBOLS or resolved not in _SYMBOLS:
            raise HeadlessContractError("Invalid historical room symbol")
        result[at] = {"floor": at, "selected_symbol": selected, "resolved_symbol": resolved}
    return [result[at] for at in sorted(result)]


class HeadlessStateMapper:
    """One ordered run/branch per instance. Use reset() before replay rewinds."""
    def __init__(self):
        self.reset()

    def reset(self):
        self._run_id = self._sequence = self._digest = self._floor = None
        self._held_keys = {}

    def map_state(self, observation: dict, *, run_id: str | None = None, sequence: int | None = None) -> GameState:
        if not isinstance(observation, dict):
            raise HeadlessContractError("Observation must be an object")
        phase = observation.get("phase")
        if phase == "BOOT":
            if observation.get("player") is not None or observation.get("map") not in (None, {}) or any(
                    observation.get(name) not in (None, 0) for name in ("floor", "act")):
                raise HeadlessContractError("BOOT contains initialized run fields")
            self.reset()
            return GameState(screen_type="MAIN_MENU", choice_list=[], can_proceed=False, can_cancel=False)
        if observation.get("bridgeSchema") != BRIDGE_SCHEMA:
            raise HeadlessContractError("Requires enriched " + BRIDGE_SCHEMA + " observation")
        if phase not in _PHASES and phase != "CARD_SELECT":
            raise HeadlessContractError("Unknown or transient native phase")
        run_id = _text(run_id, "run/branch identity")
        sequence = _int(sequence, "snapshot sequence", 0)
        digest = hashlib.sha256(json.dumps(observation, sort_keys=True, allow_nan=False).encode()).hexdigest()
        same_run = run_id == self._run_id
        if same_run and (sequence < self._sequence or (sequence == self._sequence and digest != self._digest)):
            raise HeadlessContractError("Out-of-order or changed snapshot; use a fresh branch mapper")
        floor = _int(observation.get("floor"), "floor", 0, 57)
        act = _int(observation.get("act"), "act", 1, 4)
        if same_run and floor < self._floor:
            raise HeadlessContractError("Floor rewind requires reset or a fresh branch identity")
        player = observation.get("player")
        if not isinstance(player, dict):
            raise HeadlessContractError("Missing initialized player")
        character = player.get("className")
        if character not in ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"):
            raise HeadlessContractError("Missing/unknown character")
        maximum = _int(player.get("maxHp"), "player.maxHp", 1)
        result = {"screen_type": _PHASES.get(phase), "choice_list": [], "can_proceed": False,
                  "can_cancel": False, "is_end_turn_button_enabled": False,
                  "floor": floor, "act": act, "character": character,
                  "ascension_level": _int(observation.get("ascension"), "ascension", 0, 20),
                  "player": {"current_hp": _int(player.get("hp"), "player.hp", 0, maximum), "max_hp": maximum,
                             "gold": _int(player.get("gold"), "player.gold", 0),
                             "block": _int(player.get("block"), "player.block", 0),
                             "energy": _int(player.get("energy"), "player.energy", 0)},
                  "room_phase": observation.get("roomPhase"),
                  "first_room_chosen": _bool(observation.get("firstRoomChosen"), "firstRoomChosen")}
        for source, target in (("masterDeck", "deck"), ("hand", "hand"), ("drawPile", "draw_pile"),
                               ("discardPile", "discard_pile"), ("exhaustPile", "exhaust_pile")):
            result[target] = _cards(player.get(source), "player." + source)
        # Cross-group aliases are genuine (master/combat copies), not duplicate IDs.
        for target in ("draw_pile", "discard_pile", "exhaust_pile"):
            result[target + "_size"] = len(result[target])
        for source, target in (("handSize", "hand"), ("drawCount", "draw_pile"), ("drawPileCount", "draw_pile"),
                               ("discardCount", "discard_pile"), ("discardPileCount", "discard_pile"),
                               ("exhaustCount", "exhaust_pile")):
            if source in player and _int(player[source], source, 0) != len(result[target]):
                raise HeadlessContractError("Card group count differs from exported instances")
        result["relics"] = [{"id": _text(r.get("id"), "relic.id"), "name": _text(r.get("name"), "relic.name"),
                             "counter": _int(r.get("counter"), "relic.counter")}
                            for r in _list(player.get("relics"), "player.relics")]
        result["potions"] = [{"index": i, "id": _text(p.get("id"), "potion.id"),
                              "name": _text(p.get("name"), "potion.name"),
                              "is_empty": _bool(p.get("empty"), "potion.empty"),
                              "can_use": False, "can_discard": False,
                              "requires_target": _bool(p.get("targetRequired"), "potion.targetRequired")}
                             for i, p in enumerate(_list(player.get("potions"), "player.potions"))]
        result["monsters"] = [{"index": i, "id": _text(m.get("id"), "monster.id"),
                               "name": _text(m.get("name"), "monster.name"),
                               "current_hp": _int(m.get("hp"), "monster.hp", 0),
                               "max_hp": _int(m.get("maxHp"), "monster.maxHp", 1),
                               "block": _int(m.get("block"), "monster.block", 0),
                               "intent": _text(m.get("intent"), "monster.intent")}
                              for i, m in enumerate(_list(observation.get("monsters", []), "monsters"))]
        result.update(_map(observation.get("map"), room=observation.get("room")))
        current = result.get("current_map_node")
        if current is not None and act in (1, 2, 3) and current["y"] < 15:
            if floor != {1: 1, 2: 18, 3: 35}[act] + current["y"]:
                raise HeadlessContractError("Current map row disagrees with observed act/floor")
        result["path_history"] = _history(observation.get("history", []), floor)
        held = dict(self._held_keys) if same_run else {}
        raw_keys = observation.get("observedKeys", {})
        if not isinstance(raw_keys, dict) or set(raw_keys) - set(_COLORS):
            raise HeadlessContractError("Invalid observedKeys")
        keys = dict(held)
        for color, value in raw_keys.items():
            value = _bool(value, "key." + color)
            if held.get(color) and not value:
                raise HeadlessContractError("Confirmed held key regressed")
            keys[color] = value
            if value:
                held[color] = True
        result["keys"] = keys  # Omitted colors remain unknown in KeyState.model_fields_set.
        boss = observation.get("act_boss")
        hide_boss = floor == 0 or phase == "BOSS_RELIC" or (
            observation.get("room") == "MonsterRoomBoss" and observation.get("roomPhase") == "COMPLETE")
        result["visible_boss"] = "NO_BOSS" if hide_boss else (
            "Corrupt Heart" if act == 4 else boss if isinstance(boss, str) and boss else "NO_BOSS")
        if "runMode" in observation:
            mode = observation["runMode"]
            flags = ("is_daily", "is_endless", "is_trial", "is_beta", "chose_seed")
            result["run_mode"] = {name: _bool(mode.get(name), name) for name in flags}
            # Settings.specialSeed is a nullable Long in the real headless engine.
            special = mode.get("special_seed")
            result["run_mode"]["special_seed"] = None if special is None else _int(special, "special_seed")
        if phase == "CARD_PICK":
            result["card_reward_cards"] = _cards(observation.get("cardRewardOptions"), "cardRewardOptions")
            result["reward_card_ids"] = [c["id"] for c in result["card_reward_cards"]]
        if phase == "CARD_SELECT":
            selection = observation.get("cardSelection", {})
            kind = selection.get("kind")
            if kind not in ("grid", "hand"):
                raise HeadlessContractError("Unknown card selection kind")
            result["screen_type"] = "GRID" if kind == "grid" else "HAND_SELECT"
            result["grid_cards"] = _cards(selection.get("options"), "cardSelection.options")
            result["grid_selected_cards"] = _cards(selection.get("selected"), "cardSelection.selected")
            result["grid_selected_count"] = len(result["grid_selected_cards"])
        try:
            state = GameState(**result)
        except ValueError as error:
            raise HeadlessContractError("Mapped GameState failed its existing model contract") from error
        # Commit only after the entire payload and current GameState have validated.
        self._run_id, self._sequence, self._digest, self._floor = run_id, sequence, digest, floor
        self._held_keys = held
        return state

    def map_payload(self, observation: dict, **context) -> dict:
        # Nested unset key fields must stay absent across the JSON boundary.
        return self.map_state(observation, **context).model_dump(exclude_unset=True)


__all__ = ["BRIDGE_SCHEMA", "HeadlessContractError", "HeadlessStateMapper"]
