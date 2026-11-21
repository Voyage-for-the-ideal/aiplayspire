"""Map/campfire/act/result contracts. Pure observations and receipts, no executor."""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json

from .state import HeadlessContractError, _bool, _int, _text

FLOW_SCHEMA = "headless-flow-v1"
_DUNGEONS = {1: "Exordium", 2: "TheCity", 3: "TheBeyond", 4: "TheEnding"}
_OPTIONS = {"RestOption": "REST", "SmithOption": "SMITH", "RecallOption": "RECALL",
            "LiftOption": "LIFT", "DigOption": "DIG", "TokeOption": "PURGE"}


def _flow(raw):
    flow = raw.get("interaction", {}).get("flow")
    if not isinstance(flow, dict) or flow.get("schema") != FLOW_SCHEMA:
        raise HeadlessContractError("Requires structured " + FLOW_SCHEMA)
    if flow.get("dungeon") != _DUNGEONS[raw["act"]]:
        raise HeadlessContractError("Dungeon/act mismatch")
    for name in ("flight", "final_act_available", "player_dead", "victory"):
        _bool(flow.get(name), "flow." + name)
    _int(flow.get("bosses_remaining"), "bosses_remaining", 0)
    if flow.get("result_screen") not in ("NONE", "DEATH", "VICTORY"):
        raise HeadlessContractError("Unknown result screen")
    return flow


def _counter(data, identity):
    relics = data["relics"] if isinstance(data, dict) else [r.model_dump() for r in data.relics]
    return next((r["counter"] for r in relics if r["id"] == identity), None)


def _current(raw):
    node = (raw.get("map") or {}).get("current")
    if not node or node["y"] == -1: return None
    return (-1, 15) if node["y"] == 15 else (node["x"], node["y"])


def _path(flow):
    path = flow.get("path")
    if not isinstance(path, list): raise HeadlessContractError("Missing per-act path")
    return [(_int(n["x"], "path.x", -1, 6), _int(n["y"], "path.y", 0, 15)) for n in path]


def _receipt_rows(flow):
    rows = flow.get("campfire_history")
    if not isinstance(rows, list): raise HeadlessContractError("Missing campfire history")
    for r in rows:
        _int(r.get("floor"), "campfire receipt floor", 1, 57)
        _text(r.get("key"), "campfire receipt key")
    return rows


def _shop_return_rng(raw):
    """Validate the source receipt; compare gameplay streams, not cosmetics."""
    from .combat import validate_rng
    try:
        receipt=raw['combat_receipt']['rng']
        streams=receipt['streams']
        aliases={name+'_rng_counter':row['counter'] for name,row in streams.items()}
        return validate_rng({'_engine':'headless','_headless_rng':receipt,'rng_state':aliases})['streams']
    except (KeyError,TypeError,AttributeError) as error:
        raise HeadlessContractError('Shop return requires exact source RNG evidence') from error


def project_map(raw, menu, data, choose):
    flow = _flow(raw)
    _path(flow)
    flight = _bool(flow.get("flight"), "flight")
    nodes = {(n["x"], n["y"]): n for n in data["map_nodes"]}
    current = _current(raw)
    physical = set()
    if data["first_room_chosen"]:
        if current not in nodes: raise HeadlessContractError("Map current node missing")
        physical = {(e["x"], e["y"]) for e in nodes[current]["children"]}
    rows = menu.get("map_choices")
    if not isinstance(rows, list): raise HeadlessContractError("Missing native map choices")
    seen, choices = set(), []
    expected = set()
    for xy, n in nodes.items():
        if not data["first_room_chosen"]:
            legal = xy[1] == 0 and bool(n["children"])
        else:
            legal = xy in physical or (any(y == xy[1] for x, y in physical) and xy[1] == current[1] + 1 and xy[1] < 15
                     and (flight or (_counter(data, "WingedGreaves") or 0) > 0))
        if legal: expected.add(xy)
    # The vanilla synthetic boss exit need not be present as a physical edge.
    if data["first_room_chosen"] and current[1] == 14 and data["act"] < 4:
        expected.add((-1, 15))
    for r in rows:
        xy = (_int(r.get("x"), "choice.x", -1, 6), _int(r.get("y"), "choice.y", 0, 15))
        if xy in seen or xy not in expected: raise HeadlessContractError("Invalid/duplicate reachable map node")
        seen.add(xy)
        winged = _bool(r.get("winged"), "choice.winged")
        expected_winged = data["first_room_chosen"] and xy not in physical and xy != (-1, 15)
        if winged != expected_winged: raise HeadlessContractError("Physical/winged reachability mismatch")
        node = nodes.get(xy, {"symbol": "B", "burning": False, "lane_index_from_left": 1})
        if r.get("symbol") != node["symbol"]: raise HeadlessContractError("Map symbol mismatch")
        i = choose(f'{xy[0]},{xy[1]}', f'map:{xy[0]}:{xy[1]}', "chooseMapNode", xy,
                   kind="map_move", target=list(xy), winged=winged, flight=flight)
        choices.append({"choice_index": i, "x": xy[0], "y": xy[1], "symbol": node["symbol"],
                        "burning": node["burning"], "lane_index_from_left": node["lane_index_from_left"], "winged": winged})
    if seen != expected: raise HeadlessContractError("Incomplete native map choices")
    data["current_map_choices"] = choices


def project_campfire(raw, menu, data, choose):
    flow = _flow(raw)
    _receipt_rows(flow)
    camp = menu["campfire"]
    selected = _bool(camp.get("selected"), "campfire.selected")
    rows = camp.get("options")
    if not isinstance(rows, list): raise HeadlessContractError("Missing campfire options")
    seen = set()
    for i, r in enumerate(rows):
        if _int(r.get("native_index"), "campfire index", 0) != i: raise HeadlessContractError("Campfire index mismatch")
        usable = _bool(r.get("usable"), "campfire.usable")
        name = _text(r.get("class_name"), "campfire class")
        if name in seen: raise HeadlessContractError("Duplicate campfire option")
        seen.add(name)
        if selected or not usable or name not in _OPTIONS: continue
        ids = {r["id"] for r in data["relics"]}
        if name == "RestOption" and "Coffee Dripper" in ids: continue
        if name == "SmithOption" and ("Fusion Hammer" in ids or not any(c["can_upgrade"] for c in data["deck"])): continue
        if name == "RecallOption" and (not _bool(flow.get("final_act_available"), "final act") or data["keys"].get("ruby")): continue
        if name == "LiftOption" and not 0 <= (_counter(data, "Girya") if _counter(data, "Girya") is not None else -1) < 3: continue
        if name == "DigOption" and "Shovel" not in ids: continue
        if name == "TokeOption" and ("Peace Pipe" not in ids or not any(not c["is_bottled"] and c["id"] not in {"AscendersBane", "CurseOfTheBell", "Necronomicurse"} for c in data["deck"])): continue
        choose(name, name, "useCampfireOption", (i,), kind="campfire", option=name, receipt=_OPTIONS[name])


def project_proceed(raw, menu, actions, spec):
    flow = menu.get("flow")
    if flow is None: return False  # Older menu fixtures retain their same-room semantics.
    flow = _flow(raw)
    if not _bool(menu.get("can_proceed"), "can_proceed"): return True
    route = flow.get("route")
    allowed = {"map", "boss_chest", "second_boss", "heart_event", "true_victory", "next_act", "event_resume", "shop_return"}
    if route not in allowed: return True
    room = raw["room"]
    if route == 'shop_return' or menu.get('return_shop') is True:
        if not (raw['phase']=='REWARD' and room=='ShopRoom' and raw.get('screen')=='COMBAT_REWARD'
                and menu.get('return_shop') is True and flow.get('previous_screen')=='SHOP'
                and flow.get('cancel_visible') is True):
            raise HeadlessContractError('Shop return requires the visible native cancel context')
        _shop_return_rng(raw)
        expected = 'shop_return'
    elif room == "TreasureRoomBoss": expected = "next_act"
    elif room == "MonsterRoomBoss":
        expected = "second_boss" if raw["act"] == 3 and raw["ascension"] == 20 and flow["bosses_remaining"] == 2 else "true_victory" if raw["act"] == 4 else "heart_event" if raw["act"] == 3 else "boss_chest"
    elif raw["phase"] == "REWARD" and room == "EventRoom" and flow.get("event_class", "") not in {"Mushrooms", "MaskedBandits", "DeadAdventurer", "Lab", "Colosseum", "MysteriousSphere", "MindBloom"}: expected = "event_resume"
    else: expected = "map"
    if route != expected: raise HeadlessContractError("Proceed route contradicts native room facts")
    if route == "next_act" and raw["act"] not in (1, 2): return True
    actions.append(spec("proceed", {"type": "proceed"}, "proceed", kind="flow_proceed", route=route))
    return True


def project_heart(raw, data, choose):
    event = raw["interaction"].get("event", {})
    if event.get("class_name") != "SpireHeart": return False
    flow = _flow(raw)
    if raw["act"] != 3 or raw.get("room") != "VictoryRoom": raise HeadlessContractError("Heart event outside Act 3 victory room")
    phases = {"INTRO": "MIDDLE", "MIDDLE": "MIDDLE_2", "MIDDLE_2": "GO_TO_ENDING" if flow["final_act_available"] and all(data["keys"].get(k) for k in ("ruby", "emerald", "sapphire")) else "DEATH",
              "GO_TO_ENDING": "ACT4", "DEATH": "VICTORY"}
    at = event.get("phase")
    if at not in phases: raise HeadlessContractError("Unknown heart event phase")
    enabled = [r for r in event["choices"] if _bool(r.get("enabled"), "heart.enabled")]
    if len(enabled) != 1 or enabled[0].get("button_index") != 0 or enabled[0].get("action_index") != 0:
        raise HeadlessContractError("Ambiguous heart dialog control")
    data["event"] = event
    choose(enabled[0]["label"], "heart:" + at, "chooseDialogOption", (0,), kind="heart_step", target=phases[at])
    return True


def terminal_facts(raw):
    flow = _flow(raw)
    terminal = flow.get("terminal")
    if terminal is None: return None
    if not isinstance(terminal, dict): raise HeadlessContractError("Invalid terminal facts")
    outcome, heart = terminal.get("outcome"), _bool(terminal.get("heart"), "terminal.heart")
    if raw["phase"] not in ("GAME_OVER", "RUN_COMPLETE"): raise HeadlessContractError("Terminal evidence on active screen")
    if outcome == "defeat":
        valid = flow.get("player_dead") is True and flow.get("result_screen") == "DEATH" and not flow.get("victory") and not heart
    elif outcome == "victory":
        valid = flow.get("victory") is True and ((heart and raw["act"] == 4 and raw["room"] == "TrueVictoryRoom" and flow.get("result_screen") == "VICTORY") or (not heart and raw["act"] == 3 and raw["room"] == "VictoryRoom" and flow.get("result_screen") == "DEATH" and flow.get("player_dead") is True))
    else: valid = False
    if not valid: raise HeadlessContractError("Contradictory terminal evidence")
    return terminal


def project_terminal(raw, data, actions, spec):
    terminal = terminal_facts(raw)
    data["can_proceed"] = False
    if terminal is None: return
    data.update(game_over_reason=terminal["outcome"], can_proceed=True)
    actions.append(spec("ack_terminal", {"type": "proceed"}, "LOCAL_ACK_TERMINAL", kind="ack_terminal"))


@dataclass(frozen=True)
class TerminalReceipt:
    receipt_id: str
    run_id: str
    character: str
    ascension: int
    native_floor: int
    act: int
    outcome: str
    heart: bool
    snapshot_token: str


def terminal_receipt(frame):
    raw = json.loads(frame.raw_json)
    terminal = terminal_facts(raw)
    if terminal is None: raise HeadlessContractError("Result evidence is incomplete")
    values = dict(run_id=frame.run_id, character=frame.state.character, ascension=frame.state.ascension_level,
                  native_floor=frame.state.floor, act=frame.state.act, outcome=terminal["outcome"], heart=terminal["heart"])
    identity = hashlib.sha256(json.dumps(values, sort_keys=True).encode()).hexdigest()
    return TerminalReceipt(identity, **values, snapshot_token=frame.native_token)


def _same_inventory(left, right):
    return left.deck == right.deck and [(r.id, r.counter) for r in left.relics] == [(r.id, r.counter) for r in right.relics] and left.potions == right.potions


def _keys_preserved(left, right):
    return all(k in right.keys.model_fields_set and getattr(right.keys, k) for k in left.keys.model_fields_set if getattr(left.keys, k))


def _next_act(left, right, a, b, f, g):
    # startNextAct creates the dungeon then opens its map. Before the first
    # click the previous boss chest can still own the sentinel current node.
    chest = a.get('interaction', {}).get('chest') or {}
    skipped = (a.get('phase') == 'PROCEED' and chest.get('boss') is True and chest.get('open') is False
               and chest == b.get('interaction', {}).get('chest')
               and _path(f)[-1:] == [(-1, 15)] and f.get('bosses_remaining') == 1
               and f.get('dungeon') == _DUNGEONS[left.act]
               and g.get('dungeon') == _DUNGEONS[right.act])
    boss_placeholder = (a.get('room') == b.get('room') == 'TreasureRoomBoss'
                        and a.get('roomPhase') == b.get('roomPhase') == 'COMPLETE'
                        and (a.get('map') or {}).get('current') == (b.get('map') or {}).get('current') == {'x':-1,'y':15}
                        and f.get('route')=='next_act' and g.get('route')=='none'
                        and (skipped or chest.get('open') is True
                             or a.get('phase')=='REWARD' and a.get('interaction',{}).get('boss_bonus_owner')=='Tiny House'
                             and f.get('cancel_visible') is True and 'Tiny House' in {r.id for r in left.relics}))
    if not (right.act == left.act + 1 and right.screen_type == "MAP" and right.floor == left.floor
            and not right.first_room_chosen and (_current(b) is None or boss_placeholder or (right.act == 4 and _current(b) == _current(a))) and _path(g) == []
            and bool(right.current_map_choices) and g["dungeon"] == _DUNGEONS[right.act]
            and _keys_preserved(left, right) and _same_inventory(left, right)
            and right.player.gold == left.player.gold and right.player.max_hp == left.player.max_hp): return False
    ids = {r.id for r in left.relics}
    heal = (int((left.player.max_hp - left.player.current_hp) * .75 + .5) if left.ascension_level >= 5 else left.player.max_hp)
    hp = left.player.current_hp if "Mark of the Bloom" in ids else min(left.player.max_hp, left.player.current_hp + heal)
    return right.player.current_hp == hp


def verify_flow(plan, after, evidence):
    """Return a verdict for lifecycle actions, None for other action families."""
    kind = evidence["kind"]
    if kind not in ("map_move", "campfire", "flow_proceed", "heart_step"): return None
    a, b = json.loads(plan.before.raw_json), json.loads(after.raw_json)
    left, right = plan.before.state, after.state
    f, g = _flow(a), _flow(b)
    if right.character != left.character or right.ascension_level != left.ascension_level or not _keys_preserved(left, right): return False
    same_room = (left.floor, left.act, _current(a)) == (right.floor, right.act, _current(b))
    if kind == "map_move":
        target = tuple(evidence["target"])
        before_charge, after_charge = _counter(left, "WingedGreaves"), _counter(right, "WingedGreaves")
        expected = (-2 if before_charge <= 1 else before_charge - 1) if evidence["winged"] and before_charge is not None else before_charge
        return (right.act == left.act and right.floor == left.floor + 1 and right.first_room_chosen
                and _current(b) == target and _path(g) == _path(f) + [target]
                and right.screen_type != "MAP" and bool(b.get("room")) and after_charge == expected)
    if kind == "campfire":
        if not same_room or left.deck != right.deck or right.player.gold != left.player.gold: return False
        option = evidence["option"]
        if option in ("SmithOption", "TokeOption"):
            return (right.screen_type == "GRID" and right.grid_purpose == ("upgrade" if option == "SmithOption" else "purge")
                    and right.grid_num_cards == 1 and bool(right.grid_cards) and right.player == left.player)
        old, new = _receipt_rows(f), _receipt_rows(g)
        receipt = len(new) == len(old) + 1 and new[:-1] == old and new[-1]["floor"] == left.floor and new[-1]["key"] == evidence["receipt"]
        if not receipt or right.room_phase != "COMPLETE": return False
        if option == "RestOption":
            ids = {r.id for r in left.relics}
            hp = left.player.current_hp if "Mark of the Bloom" in ids else min(left.player.max_hp, left.player.current_hp + int(left.player.max_hp * .3) + (15 if "Regal Pillow" in ids else 0))
            return right.player.current_hp == hp and right.player.max_hp == left.player.max_hp and right.screen_type == ("CARD_REWARD" if "Dream Catcher" in ids else "NONE")
        if right.player != left.player: return False
        if option == "RecallOption": return "ruby" in right.keys.model_fields_set and right.keys.ruby and right.screen_type == "NONE"
        if option == "LiftOption": return _counter(right, "Girya") == _counter(left, "Girya") + 1 and right.screen_type == "NONE"
        if option == "DigOption":
            rows = b["interaction"].get("rewards", [])
            return right.screen_type == "COMBAT_REWARD" and sum(r["type"] == "RELIC" for r in rows) == 1
        return False
    if kind == "heart_step":
        target = evidence["target"]
        if target == "ACT4": return left.act == 3 and all(getattr(left.keys, k) for k in ("ruby", "emerald", "sapphire")) and _next_act(left, right, a, b, f, g)
        if target == "VICTORY":
            t = terminal_facts(b)
            return same_room and t is not None and t["outcome"] == "victory" and not t["heart"] and left.deck == right.deck
        return same_room and right.event is not None and right.event.class_name == "SpireHeart" and right.event.phase == target and _same_inventory(left, right) and right.player == left.player
    route = evidence["route"]
    if route == "next_act": return left.act in (1, 2) and _next_act(left, right, a, b, f, g)
    if route == 'shop_return':
        try:
            rng_unchanged=_shop_return_rng(a)==_shop_return_rng(b)
        except HeadlessContractError:
            return False
        return (same_room and a.get('room')=='ShopRoom' and b.get('room')=='ShopRoom'
            and a.get('screen')=='COMBAT_REWARD' and b.get('screen')=='SHOP'
            and right.screen_type=='SHOP_SCREEN' and _path(g)==_path(f) and _same_inventory(left,right)
            and right.player==left.player and right.keys==left.keys
            and right.keys.model_fields_set==left.keys.model_fields_set and right.room_phase==left.room_phase
            and rng_unchanged)
    if route == "map" and a.get('interaction',{}).get('neow_bonus_owner')=='Tiny House':
        return (a['room']==b['room']=='NeowRoom' and a['roomPhase']=='EVENT' and b['roomPhase']=='COMPLETE'
                and a['floor']==b['floor']==0 and f.get('event_class')=='NeowEvent' and f.get('cancel_visible') is True
                and 'Tiny House' in {r.id for r in left.relics} and same_room and right.screen_type=='MAP'
                and _path(g)==_path(f)==[] and _same_inventory(left,right) and right.player==left.player
                and _shop_return_rng(a)==_shop_return_rng(b))
    if route == "map": return same_room and right.screen_type == "MAP" and _path(g) == _path(f) and _same_inventory(left, right) and right.player == left.player
    if route == "event_resume": return (same_room and right.screen_type == "EVENT" and left.deck == right.deck
        and b.get("room") == "EventRoom" and right.event is not None and right.event.class_name == f.get("event_class"))
    # GameKernel.proceed creates the off-map room without appending pathX/pathY.
    if right.act != left.act or right.floor != left.floor + 1 or _current(b) != (-1, 15) or _path(g) != _path(f): return False
    if route == "second_boss": return left.act == 3 and left.ascension_level == 20 and f.get("bosses_remaining") == 2 and g.get("bosses_remaining") == 1 and b["room"] == "MonsterRoomBoss" and right.room_phase == "COMBAT"
    if route == "boss_chest": return b["room"] == "TreasureRoomBoss" and b["phase"] == "BOSS_CHEST"
    if route == "heart_event": return left.act == 3 and b["room"] == "VictoryRoom" and right.event is not None and right.event.class_name == "SpireHeart"
    if route == "true_victory":
        t = terminal_facts(b)
        return left.act == 4 and t is not None and t["heart"] and t["outcome"] == "victory"
    return False
