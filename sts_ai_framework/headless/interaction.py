"""Ironclad interaction contracts and command *plans*, with no transport/executor.

Menus come from structured engine facts. Queue acknowledgements are not effects;
validation compares action-specific evidence in a later ordered observation.
"""
from __future__ import annotations

import copy
from collections import Counter
from dataclasses import dataclass
import hashlib
import json
import math

from .state import HeadlessStateMapper, HeadlessContractError, _cards, _int, _bool, _text
from .picker import PickerPhase
from ..models import EventState, GameAction, GameState
from .flow import (project_map, project_campfire, project_proceed, project_heart,
                            project_terminal, terminal_receipt, verify_flow)

INTERACTION_SCHEMA = "headless-interaction-v1"
# srcColorlessCardPool, excluding HEALING-tagged cards (ChooseOneColorless).
_TOOLBOX_CARDS = frozenset(("Apotheosis|Blind|Chrysalis|Dark Shackles|Deep Breath|Discovery|Dramatic Entrance|"
    "Enlightenment|Finesse|Flash of Steel|Forethought|Good Instincts|HandOfGreed|Impatience|Jack Of All Trades|"
    "Madness|Magnetism|Master of Strategy|Mayhem|Metamorphosis|Mind Blast|Panacea|Panache|PanicButton|"
    "Purity|Sadistic Nature|Secret Technique|Secret Weapon|Swift Strike|The Bomb|Thinking Ahead|Transmutation|Trip|Violence").split("|"))



def _json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def _hash(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _objects(value, name):
    if not isinstance(value, list) or any(not isinstance(v, dict) for v in value):
        raise HeadlessContractError("Expected object list: " + name)
    return value


def _wheel_facts(menu):
    facts = menu.get('wheel')
    if not isinstance(facts, dict): raise HeadlessContractError('Missing native wheel facts')
    stage = facts.get('stage')
    if stage not in ('INTRO', 'SPIN', 'COMPLETE', 'LEAVE'):
        raise HeadlessContractError('Unknown wheel stage')
    expected_native = 'INTRO' if stage == 'SPIN' else stage
    if facts.get('native_phase') != expected_native:
        raise HeadlessContractError('Wheel/native phase mismatch')
    for key in ('start_spin','finish_spin','done_spinning','button_pressed','purge_available'):
        _bool(facts.get(key), 'wheel.'+key)
    if (facts['start_spin'], facts['finish_spin'], facts['done_spinning'], facts['button_pressed']) != (
            stage == 'SPIN', False, False, stage in ('COMPLETE','LEAVE')):
        raise HeadlessContractError('Wheel is not at an actionable native control')
    _int(facts.get('gold_amount'), 'wheel.gold_amount', 0)
    _int(facts.get('hp_loss'), 'wheel.hp_loss', 0)
    if stage in ('COMPLETE','LEAVE'): _int(facts.get('result'), 'wheel.result', 0, 5)
    elif 'result' in facts: raise HeadlessContractError('Hidden wheel result leaked before completion')
    return facts


def _we_meet_facts(menu):
    facts=menu.get('we_meet_again')
    if not isinstance(facts,dict) or facts.get('schema')!='headless-wemeetagain-v1':
        raise HeadlessContractError('Missing native WeMeetAgain facts')
    if facts.get('phase')=='INTRO':
        if 'reward_relic' in facts or not isinstance(facts.get('costs'),dict):
            raise HeadlessContractError('WeMeetAgain reward is hidden before activation')
    elif facts.get('phase')=='COMPLETE':
        if 'reward_relic' not in facts: raise HeadlessContractError('Missing completed WeMeetAgain reward fact')
        reward=facts['reward_relic']
        if reward is not None:
            if not isinstance(reward,dict): raise HeadlessContractError('Invalid completed relic fact')
            _text(reward.get('id'),'WeMeetAgain reward id')
            _int(reward.get('counter'),'WeMeetAgain reward counter')
    else: raise HeadlessContractError('Unknown WeMeetAgain phase')
    return facts


def _we_meet_receipt(raw,menu,event,option):
    facts=_we_meet_facts(menu);button=option.button_index
    if event.id!='WeMeetAgain' or event.phase!=facts['phase'] or event.semantics_status!='KNOWN':
        raise HeadlessContractError('WeMeetAgain event/native facts mismatch')
    receipt={'phase':facts['phase'],'button':button}
    if facts['phase']=='COMPLETE':
        if button!=0: raise HeadlessContractError('Unexpected WeMeetAgain completion button')
        receipt['reward_relic']=facts['reward_relic'];return receipt
    if button not in (0,1,2,3): raise HeadlessContractError('Unexpected WeMeetAgain offer button')
    if button==3:return receipt
    cost=facts['costs'].get(str(button))
    if not isinstance(cost,dict): raise HeadlessContractError('Missing native WeMeetAgain cost')
    player=raw['player']
    if button==0:
        slot=_int(cost.get('slot'),'WeMeetAgain potion slot',0)
        potions=player['potions']
        if (cost.get('kind')!='potion' or slot>=len(potions) or potions[slot]['empty']
                or cost.get('id')!=potions[slot]['id']):
            raise HeadlessContractError('WeMeetAgain potion offer is not the original slot')
    elif button==1:
        amount=_int(cost.get('amount'),'WeMeetAgain gold cost',50,150)
        if cost.get('kind')!='gold' or amount>player['gold']:
            raise HeadlessContractError('WeMeetAgain gold offer exceeds current funds')
    else:
        target=next((c for c in player['masterDeck'] if c['uuid']==cost.get('uuid')),None)
        if (cost.get('kind')!='card' or target is None or cost.get('id')!=target['id']
                or target['type']=='CURSE' or target['rarity']=='BASIC'):
            raise HeadlessContractError('WeMeetAgain card offer is not the original eligible UUID')
    receipt['cost']=cost
    return receipt


@dataclass(frozen=True)
class LegalAction:
    action_id: str
    api_json: str
    native_method: str
    native_args: tuple
    evidence_json: str

    @property
    def api_action(self):
        return GameAction.model_validate_json(self.api_json)


@dataclass(frozen=True)
class InteractionFrame:
    run_id: str
    sequence: int
    raw_json: str
    state_json: str
    actions: tuple[LegalAction, ...]
    virtual_purge: bool = False
    native_combat_handoff: bool = False

    @property
    def state(self):
        return GameState.model_validate_json(self.state_json)  # A fresh view, not mutable guard storage.

    @property
    def native_token(self):
        return _hash(self.run_id + "\n" + self.raw_json)

    @property
    def token(self):
        return _hash(self.native_token + "\n" + self.state_json + str(self.virtual_purge))


@dataclass(frozen=True)
class ActionPlan:
    before: InteractionFrame
    action: LegalAction


@dataclass(frozen=True)
class EffectCheck:
    status: str  # APPLIED, PENDING, REJECTED, UNVERIFIED, LOCAL_ONLY
    reason: str


def _spec(identity, api, method, args=(), **evidence):
    return LegalAction(identity, _json(api), method, tuple(args), _json(evidence))


class IroncladInteractionBridge:
    """Offline only. observe/prepare/verify never submit an action to an engine."""
    def __init__(self, *, characters=("IRONCLAD",)):
        self._characters = frozenset(characters)
        if not self._characters or self._characters - {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"}:
            raise HeadlessContractError("Unknown interaction character set")
        self._mapper = HeadlessStateMapper()
        self._terminal_receipts = {}

    def observe(self, raw: dict, *, run_id: str, sequence: int) -> InteractionFrame:
        _text(run_id, "run/branch identity")
        snapshot_json = _json(raw)
        raw = json.loads(snapshot_json)
        # Semantics failing late must not commit key memory in the underlying mapper.
        trial = copy.deepcopy(self._mapper)
        state = trial.map_state(raw, run_id=run_id, sequence=sequence)
        if state.player is None:
            actions = []
        else:
            if state.character not in self._characters:
                raise HeadlessContractError("Character outside configured interaction contract")
            menu = raw.get("interaction")
            if not isinstance(menu, dict) or menu.get("schema") != INTERACTION_SCHEMA:
                raise HeadlessContractError("Requires structured " + INTERACTION_SCHEMA)
            data = state.model_dump(exclude_unset=True)
            actions = self._menu(raw, menu, data)
            try:
                state = GameState(**data)
            except ValueError as error:
                raise HeadlessContractError("Invalid interaction GameState") from error
        frame = InteractionFrame(run_id, _int(sequence, "sequence", 0), snapshot_json,
                                 _json(state.model_dump(exclude_unset=True)), tuple(actions))
        self._mapper = trial
        return frame

    def observe_outcome(self, raw: dict, *, run_id: str, sequence: int, combat, import_snapshot) -> InteractionFrame:
        """Read-only menu outcome: a known combat-start selection has no menu actions."""
        from .combat import is_gambler_selection, validate_selection_import
        if not is_gambler_selection(raw):return self.observe(raw,run_id=run_id,sequence=sequence)
        if not isinstance(raw.get('interaction'),dict) or raw['interaction'].get('schema')!=INTERACTION_SCHEMA:
            raise HeadlessContractError('Requires structured '+INTERACTION_SCHEMA)
        # Only the relic's initial combat boundary belongs to a menu action.
        # Potion selections and arbitrary HAND_SELECT screens keep their own routes.
        s=raw['combat']['snapshot'];h=s['hand_select_screen_state'];p=s['player_state']
        relics=[r for r in p['relics'] if r['relic_id']=='Gambling Chip']
        if (h['current_action_state'].get('state',{}).get('notchip') is not False or s.get('turn')!=1
                or h.get('selected_cards')!=[] or len(relics)!=1
                or relics[0].get('_private',{}).get('GamblingChip.activated') is not True):
            raise HeadlessContractError('Unsupported menu-to-combat selection boundary')
        candidate=copy.deepcopy(combat).observe(raw,run_id=run_id,sequence=sequence)
        validate_selection_import(candidate,import_snapshot(candidate.snapshot))
        # Do not enumerate or prepare a native choice here. Preserve this
        # bridge's persistent map/key memory for the original flow verifier.
        frozen=_json(raw);trial=copy.deepcopy(self._mapper)
        state=trial.map_state(json.loads(frozen),run_id=run_id,sequence=sequence)
        if state.character not in self._characters:raise HeadlessContractError('Character outside configured interaction contract')
        data=state.model_dump(exclude_unset=True)
        data.update(can_proceed=False,choice_list=[],grid_purpose='gamble',grid_num_cards=99)
        frame=InteractionFrame(run_id,_int(sequence,'sequence',0),frozen,_json(GameState(**data).model_dump(exclude_unset=True)),(),
                               native_combat_handoff=True)
        self._mapper=trial
        return frame

    def _menu(self, raw, menu, data):
        phase = raw["phase"]
        choices, actions = [], []

        def choose(label, identity, method, args=(), **evidence):
            index = len(choices)
            choices.append(label)
            actions.append(_spec(identity, {"type": "choose", "choice_index": index}, method, args, **evidence))
            return index

        if "chest" in menu:
            chest = menu["chest"]
            data["chest_boss"] = _bool(chest.get("boss"), "chest.boss")
            data["chest_gold_reward"] = _bool(chest.get("gold_reward"), "chest.gold_reward")
            data["chest_gold_amount"] = _int(chest.get("gold_amount"), "chest.gold_amount", 0)
            data["chest_relic_tier"] = chest.get("relic_tier")
            _bool(chest.get("open"), "chest.open")
            if data["chest_boss"]: data["visible_boss"] = "NO_BOSS"

        if phase == "MAP":
            if "flow" in menu: project_map(raw, menu, data, choose)
        elif phase == "CAMPFIRE":
            if "flow" in menu: project_campfire(raw, menu, data, choose)
        elif phase in ("GAME_OVER", "RUN_COMPLETE"):
            if "flow" in menu: project_terminal(raw, data, actions, _spec)
        elif phase in ("EVENT", "NEOW") and project_heart(raw, data, choose):
            pass
        elif phase in ("EVENT", "NEOW"):
            for option in _objects(menu["event"].get("choices"), "event.choices"):
                _int(option.get("button_index"), "button_index", 0)
                _bool(option.get("enabled"), "event.enabled")
                if option.get("action_index") is not None: _int(option["action_index"], "action_index", 0)
                for outcome in _objects(option.get("outcomes"), "event.outcomes"):
                    probability = outcome.get("probability")
                    if probability is not None and type(probability) not in (int, float):
                        raise HeadlessContractError("Invalid event probability type")
            from .event_receipts import source_menu
            event = EventState(**source_menu(raw))
            seen_buttons, expected = set(), 0
            # Retain the distinction between an omitted legacy card filter and an
            # explicit empty native tag set (no starter cards remain).
            event_data = event.model_dump(exclude_none=True, exclude_unset=True)
            for option, encoded in zip(event.choices, event_data["choices"]):
                if option.button_index in seen_buttons or option.button_index < 0:
                    raise HeadlessContractError("Duplicate/invalid native event button")
                seen_buttons.add(option.button_index)
                if not option.enabled:
                    if option.action_index is not None:
                        raise HeadlessContractError("Disabled event button advertises an action")
                    continue
                if option.action_index != expected:
                    raise HeadlessContractError("Event action indices must compact enabled buttons")
                choices.append(option.label)
                expected += 1
                if event.class_name == 'GremlinWheelGame':
                    facts = _wheel_facts(menu)
                    if (event.id != 'Wheel of Change' or event.phase != facts['stage']
                            or event.semantics_status != 'KNOWN' or len(event.choices) != 1
                            or option.button_index != 0 or option.action_index != 0):
                        raise HeadlessContractError('Invalid native wheel control')
                    actions.append(_spec('wheel:'+facts['stage'], {'type':'choose','choice_index':0},
                                         'chooseDialogOption',(0,),kind='wheel',facts=facts))
                    continue
                if option.kind == "MINI_GAME_CARD" and event.class_name == "GremlinMatchGame" and event.phase == "PLAY":
                    facts = menu.get("match_game", {})
                    rows = _objects(facts.get("cards"), "match cards")
                    row = next((r for r in rows if r.get("slot") == option.button_index), None)
                    if facts.get("screen") != "PLAY" or row is None or row.get("face_up") is not False or row.get("card_id") is not None:
                        raise HeadlessContractError("Invalid match flip candidate")
                    actions.append(_spec("match:" + str(option.button_index), {"type": "choose", "choice_index": option.action_index},
                                         "chooseMatchCard", (option.button_index,), kind="match_flip", slot=option.button_index))
                    continue
                if event.semantics_status != "KNOWN" or option.kind == "UNKNOWN" or option.kind.startswith("MINI_GAME"):
                    continue
                probabilities = [o.probability for o in option.outcomes]
                if not probabilities or any(p is None or not math.isfinite(p) or p < 0 or p > 1 for p in probabilities) or not math.isclose(sum(probabilities), 1, abs_tol=1e-7):
                    raise HeadlessContractError("Known event outcomes need normalized finite probabilities")
                receipt={'we_meet_again':_we_meet_receipt(raw,menu,event,option)} if event.class_name=='WeMeetAgain' else {}
                if (event.id,event.class_name,event.phase,option.button_index)==('Knowing Skull','KnowingSkull','ASK',2):
                    # Compatibility with old headless traces which incorrectly advertised
                    # a reward screen. Do not infer the cost from localized button text.
                    outcomes=encoded['outcomes']
                    if (len(outcomes)==1 and outcomes[0].get('probability')==1.0
                            and len(outcomes[0]['effects'])==2 and outcomes[0]['effects'][0].get('type')=='lose_hp'
                            and outcomes[0]['effects'][1].get('type')=='random_card_reward'):
                        outcomes[0]['effects'][1]={'type':'random_card','pool':'COLORLESS_UNCOMMON'}
                        encoded['kind']='RANDOM_REWARD';encoded['followup']='EVENT'
                if ((event.id,event.class_name,event.phase,option.button_index) in {
                        ('Duplicator','Duplicator','0',1),('Knowing Skull','KnowingSkull','ASK',3)}):
                    encoded['followup']='EVENT'
                actions.append(_spec("event:" + str(option.button_index),
                                     {"type": "choose", "choice_index": option.action_index},
                                     "chooseDialogOption", (option.button_index,), kind="event",
                                     event_id=event.id, option=encoded,
                                     transition=_source_event_transition(event,option.button_index,
                                         menu.get("event_transitions", {}).get(str(option.button_index), {})),**receipt))
            data["event"] = event_data
        elif phase == "SHOP":
            shop = menu["shop"]
            purge = _bool(shop.get("purge_available"), "purge_available")
            price = _int(shop.get("purge_cost"), "purge_cost", 0)
            purge_cards = _cards(shop.get("purge_cards"), "purge_cards")
            deck_ids = {c["uuid"] for c in data["deck"]}
            if any(c["uuid"] not in deck_ids or c["is_bottled"] or c["id"] in {"AscendersBane", "CurseOfTheBell", "Necronomicurse"} for c in purge_cards):
                raise HeadlessContractError("Invalid native purge candidate")
            data.update(shop_purge_cost=price, shop_purge_available=purge, shop_inventory=[])
            if purge and data["player"]["gold"] >= price and purge_cards:
                choose("purge", "purge", "LOCAL_PURGE_INTENT", kind="purge_intent")
            slots, indices = set(), set()
            potion_ok = any(p["is_empty"] for p in data["potions"]) and not any(r["id"] == "Sozu" for r in data["relics"])
            for item in _objects(shop.get("items"), "shop.items"):
                kind = item["kind"]
                if kind not in ("card", "relic", "potion"):
                    raise HeadlessContractError("Unknown shop item kind")
                if kind == "card" and item.get("card_type") not in ("ATTACK", "SKILL", "POWER", "STATUS", "CURSE"):
                    raise HeadlessContractError("Missing shop card type")
                slot = _text(item.get("slot_id"), "slot_id")
                native = _int(item.get("native_index"), "shop native_index", 0)
                if slot in slots or (kind, native) in indices:
                    raise HeadlessContractError("Duplicate shop identity/index")
                slots.add(slot); indices.add((kind, native))
                cost = _int(item.get("price"), "shop price", 0)
                sold = _bool(item.get("sold_out"), "shop sold_out")
                row = {"slot_id": slot, "kind": kind, "id": _text(item.get("id"), "shop id"),
                       "price": cost, "upgrades": _int(item.get("upgrades"), "shop upgrades", 0),
                       "sold_out": sold, "on_sale": _bool(item.get("on_sale"), "on_sale"),
                       "affordable": data["player"]["gold"] >= cost,
                       "legal": not sold and data["player"]["gold"] >= cost and (kind != "potion" or potion_ok),
                       "choice_index": None}
                if row["legal"]:
                    row["choice_index"] = choose(slot, slot, {"card": "buyCard", "relic": "buyRelic", "potion": "buyPotion"}[kind],
                                                   (native,), kind="buy", item_kind=kind, id=row["id"],
                                                   upgrades=row["upgrades"], price=cost, slot_id=slot,
                                                   card_type=item.get("card_type"))
                data["shop_inventory"].append(row)
            data["can_cancel"] = True
            actions.append(_spec("leave", {"type": "cancel"}, "leaveShop", kind="leave_shop"))
        elif phase == "REWARD":
            rows = _objects(menu.get("rewards"), "rewards")
            reward_choices = []
            potion_ok = any(p["is_empty"] for p in data["potions"]) and not any(r["id"] == "Sozu" for r in data["relics"])
            for index, reward in enumerate(rows):
                if reward.get("native_index") != index:
                    raise HeadlessContractError("Reward indices differ from native list")
                kind = reward["type"]
                if kind not in ("GOLD", "STOLEN_GOLD", "CARD", "RELIC", "POTION", "SAPPHIRE_KEY", "EMERALD_KEY"):
                    raise HeadlessContractError("Unsupported reward kind")
                row = {"choice_index": index, "type": kind}
                for name in ("relic_id", "potion_id", "amount", "linked_reward_index"):
                    if name in reward: row[name] = reward[name]
                link = row.get("linked_reward_index")
                if link is not None and (type(link) is not int or not 0 <= link < len(rows) or link == index or rows[link].get("linked_reward_index") != index):
                    raise HeadlessContractError("Invalid linked reward pair")
                if link is not None and {kind, rows[link]["type"]} not in ({"RELIC", "SAPPHIRE_KEY"}, {"RELIC", "EMERALD_KEY"}):
                    raise HeadlessContractError("Linked rewards must be a relic/key pair")
                if kind.endswith("_KEY"): row["key_type"] = kind.split("_")[0].lower()
                if kind in ("GOLD", "STOLEN_GOLD"):
                    _int(row.get("amount"), "reward.amount", 0)
                if kind == "RELIC": _text(row.get("relic_id"), "reward.relic_id")
                if kind == "POTION": _text(row.get("potion_id"), "reward.potion_id")
                reward_choices.append(row)
                choices.append(kind + ":" + str(index))
                if kind != "POTION" or potion_ok:
                    actions.append(_spec("reward:" + str(index), {"type": "choose", "choice_index": index},
                                         "openCardReward" if kind == "CARD" else "collectReward", (index,),
                                         kind="open_card_reward" if kind == "CARD" else "reward", index=index, reward=row,
                                         offered_cards=reward.get("card_uuids", [])))
            data["reward_choices"] = reward_choices
            if _bool(menu.get("can_proceed"), "can_proceed"):
                data["can_proceed"] = True
                if not project_proceed(raw, menu, actions, _spec):
                    actions.append(_spec("proceed", {"type": "proceed"}, "proceed", kind="proceed"))
        elif phase in ("CHEST", "BOSS_CHEST"):
            if "chest" not in menu: raise HeadlessContractError("Missing chest facts")
            if not menu["chest"]["open"]:
                choose("open", "open_chest", "openChest", kind="open_chest")
        elif phase == "BOSS_RELIC":
            for index, item in enumerate(_objects(menu.get("boss_relics"), "boss relics")):
                if item.get("native_index") != index: raise HeadlessContractError("Boss relic index mismatch")
                identity = _text(item.get("id"), "boss relic id")
                choose(identity, "boss_relic:" + str(index), "chooseBossRelic", (index,), kind="take_boss_relic", id=identity)
            data["can_cancel"] = _bool(menu.get("can_skip_boss"), "can_skip_boss")
            if data["can_cancel"]: actions.append(_spec("skip", {"type": "cancel"}, "skipBossRelic", kind="skip_boss_relic"))
        elif phase == "CARD_PICK":
            controls = menu["card_reward"]
            discovery = controls.get("discovery", False)
            source = controls.get("source_action")
            if discovery and (data["room_phase"] != "COMBAT" or source not in {"ForeignInfluenceAction", "DiscoveryAction", "CodexAction", "ChooseOneColorless"}):
                raise HeadlessContractError("Unsupported combat reward source")
            if source == 'ChooseOneColorless':
                offers=data['card_reward_cards']
                if (discovery is not True or not any(r['id']=='Toolbox' for r in data['relics'])
                        or len(offers)!=3 or len({c['id'] for c in offers})!=3
                        or any(c['id'] not in _TOOLBOX_CARDS for c in offers)
                        or controls.get('copies',1)!=1 or controls.get('free_for_turn',False) is not False
                        or controls.get('skip') is not False or controls.get('bowl') is not False
                        or controls.get('destination','hand')!='hand'):
                    raise HeadlessContractError("Invalid Toolbox choice")
            for i, card in enumerate(data["card_reward_cards"]):
                choose(card["id"], "card:" + card["uuid"], "chooseRewardCard", (i,), kind="take_combat_card" if discovery else "take_card", card=card,
                       copies=controls.get("copies", 1), free_for_turn=controls.get("free_for_turn", False), destination=controls.get("destination", "hand"))
            if _bool(controls.get("bowl"), "bowl"):
                choose("bowl", "bowl", "chooseSingingBowl", kind="bowl")
            if _bool(controls.get("skip"), "skip"):
                data["can_cancel"] = True
                actions.append(_spec("skip", {"type": "cancel"}, "skipRewardCard", kind="skip_card"))
        elif phase == "CARD_SELECT":
            selection = menu["selection"]
            if selection.get("kind") not in ("grid", "hand") or selection["kind"] != raw["cardSelection"]["kind"]:
                raise HeadlessContractError("Selection kind mismatch")
            purpose = _text(selection.get("purpose"), "selection purpose")
            library = menu.get('flow',{}).get('event_class')=='TheLibrary'
            if library:
                from .event_receipts import library_grid
                if purpose not in ('unknown','obtain') or not library_grid(raw):
                    raise HeadlessContractError('Invalid Library draft grid')
                purpose='obtain'
            # The pinned Duplicator uses a generic one-card grid with all flags
            # false. Earlier exports lost that purpose, but retain its source
            # event identity and exact whole-deck target group.
            if purpose=='unknown' and raw.get('room')=='EventRoom' and menu.get('flow',{}).get('event_class')=='Duplicator':
                if (selection.get('kind')!='grid' or selection.get('count')!=1
                        or selection.get('any_number') is not False or selection.get('auto_confirm') is not True
                        or selection.get('source_event_phase','2')!='2'
                        or [c['uuid'] for c in data['grid_cards']]!=[c['uuid'] for c in data['deck']]):
                    raise HeadlessContractError('Invalid Duplicator source grid')
                purpose='duplicate'
            count = _int(selection.get("count"), "selection count", 0)
            locked = _bool(selection.get("locked"), "selection locked")
            confirm = _bool(selection.get("can_confirm"), "selection can_confirm")
            picker = PickerPhase.from_controls(locked=locked, can_confirm=confirm)
            auto = _bool(selection.get("auto_confirm"), "selection auto_confirm")
            any_number = _bool(selection.get("any_number"), "selection any_number")
            allow_zero = _bool(selection.get("can_pick_zero", False), "selection can_pick_zero")
            if purpose=='meditate' and not (
                    data['character']=='WATCHER' and raw['roomPhase']=='COMBAT' and
                    selection['kind']=='grid' and count in (1,2) and not any_number and not allow_zero and auto
                    and [c['uuid'] for c in raw['cardSelection']['options']] == [c['uuid'] for c in raw['player']['discardPile']]):
                raise HeadlessContractError('Invalid Meditate source discard selection')
            if purpose=='bonfire_offer':
                if (raw.get('room')!='EventRoom' or menu.get('flow',{}).get('event_class')!='Bonfire'
                        or selection.get('source_event_class')!='Bonfire' or selection.get('source_event_phase')!='CHOOSE'
                        or count!=1 or selection['kind']!='grid' or auto or any_number or allow_zero):
                    raise HeadlessContractError('Invalid Bonfire source selection')
            preview = selection.get("preview_uuid")
            if preview is not None and preview not in {c["uuid"] for c in data["grid_cards"]}:
                raise HeadlessContractError("Preview target is not an original option UUID")
            if purpose not in ("purge", "upgrade", "transform", "duplicate", "bottle", "discard", "exhaust", "retain", "obtain", "topdeck", "retrieve_exhaust", "duplicate_combat", "scry", "gamble", "liquid_memories", "bonfire_offer", "meditate"):
                raise HeadlessContractError("Unsupported selection purpose")
            if purpose=='gamble':raise HeadlessContractError('Gambler selection requires the native combat continuation contract')
            if purpose=='liquid_memories':raise HeadlessContractError('Liquid Memories selection requires the native combat continuation contract')
            from .combat import is_headbutt_selection, is_single_hand
            if is_single_hand(raw):raise HeadlessContractError('Single-hand selection requires the native combat continuation contract')
            if is_headbutt_selection(raw):raise HeadlessContractError('Headbutt selection requires the native combat continuation contract')
            copies = _int(selection.get("copies"), "combat duplicate count", 1) if purpose == "duplicate_combat" else None
            if not any_number and len(data["grid_selected_cards"]) > count:
                raise HeadlessContractError("Too many selected cards")
            if confirm and not locked and not any_number and len(data["grid_selected_cards"]) != count and not (allow_zero and not data["grid_selected_cards"]):
                raise HeadlessContractError("Premature selection confirmation")
            data.update(grid_purpose=purpose, grid_num_cards=count, can_proceed=picker.can_confirm)
            selected = {c["uuid"] for c in data["grid_selected_cards"]}
            offered = [c["id"] + ("+" + str(c["upgrades"]) if c["upgrades"] else "") for c in data["grid_cards"]]
            if not picker.can_select:
                data["grid_cards"] = []
            else:
                original = data["grid_cards"]
                data["grid_cards"] = []
                for i, card in enumerate(original):
                    already = card["uuid"] in selected
                    forbidden = purpose in ("purge", "transform", "bonfire_offer") and (card["is_bottled"] or card["id"] in {"AscendersBane", "CurseOfTheBell", "Necronomicurse"})
                    forbidden = forbidden or (purpose == "bottle" and card["is_bottled"])
                    if forbidden or (purpose == "upgrade" and not card["can_upgrade"] and not already): continue
                    if not any_number and len(selected) >= count and not already: continue
                    card["choice_index"] = len(choices)
                    data["grid_cards"].append(card)
                    choose(card["name"], "target:" + card["uuid"],
                           "chooseGridCard" if selection["kind"] == "grid" else "chooseHandCard", (i,),
                           kind="select", uuid=card["uuid"], selected=card["uuid"] in selected,
                           purpose=purpose, selection_kind=selection["kind"], auto=auto, copies=copies,
                           **({'library_draft':True} if library else {}))
            if picker.can_confirm:
                actions.append(_spec("confirm", {"type": "proceed"},
                                     "confirmGrid" if selection["kind"] == "grid" else "confirmHandSelect",
                                     kind="confirm_selection", purpose=purpose,
                                     uuids=picker.confirmation_targets(tuple(c["uuid"] for c in data["grid_selected_cards"]), preview),
                                     offered=offered, copies=copies))
        elif phase == "PROCEED":
            if raw.get("room") == "ShopRoom":
                data["screen_type"] = "SHOP_ROOM"
                choose("shop", "enter_shop", "enterShop", kind="enter_shop")
            if _bool(menu.get("can_proceed"), "can_proceed"):
                data["can_proceed"] = True
                if not project_proceed(raw, menu, actions, _spec):
                    actions.append(_spec("proceed", {"type": "proceed"}, "proceed", kind="proceed"))
        if data["can_proceed"] and not any(a.api_json == _json({"type": "proceed"}) for a in actions):
            data["can_proceed"] = False
        data["choice_list"] = choices
        return actions

    def prepare(self, frame: InteractionFrame, action: GameAction) -> ActionPlan:
        if not isinstance(action, GameAction):
            raise HeadlessContractError("Expected a typed GameAction")
        command = _json(action.model_dump(mode="json", exclude_none=True))
        if action.type.value == "skip" and frame.state.screen_type in ("CARD_REWARD", "BOSS_REWARD") and frame.state.can_cancel:
            command = _json({"type": "cancel"})
        matches = [entry for entry in frame.actions if entry.api_json == command]
        if len(matches) != 1:
            raise HeadlessContractError("Action is not uniquely legal in this observation")
        return ActionPlan(frame, matches[0])

    @staticmethod
    def validate_before(plan: ActionPlan, current: InteractionFrame) -> bool:
        if current.run_id != plan.before.run_id or current.sequence < plan.before.sequence:
            return False
        if plan.before.virtual_purge:
            return current.native_token == plan.before.native_token
        return current.token == plan.before.token

    def enter_purge_intent(self, plan: ActionPlan, current: InteractionFrame) -> InteractionFrame:
        if plan.action.native_method != "LOCAL_PURGE_INTENT" or not self.validate_before(plan, current):
            raise HeadlessContractError("Stale or invalid purge intent")
        raw = json.loads(current.raw_json)
        shop = raw["interaction"]["shop"]
        data = current.state.model_dump(exclude_unset=True)
        cards = _cards(shop["purge_cards"], "purge_cards")
        data.update(screen_type="GRID", choice_list=[c["name"] for c in cards], grid_cards=cards,
                    grid_selected_cards=[], grid_selected_count=0, grid_num_cards=1, grid_purpose="purge",
                    can_proceed=False, can_cancel=True)
        actions = []
        for i, card in enumerate(cards):
            card["choice_index"] = i
            actions.append(_spec("purge:" + card["uuid"], {"type": "choose", "choice_index": i}, "purgeCard", (i,),
                                 kind="purge", uuid=card["uuid"], price=shop["purge_cost"]))
        actions.append(_spec("cancel", {"type": "cancel"}, "LOCAL_CANCEL_PURGE", kind="cancel_purge"))
        return InteractionFrame(current.run_id, current.sequence, current.raw_json, _json(data), tuple(actions), True)

    def cancel_purge_intent(self, plan: ActionPlan, current: InteractionFrame) -> InteractionFrame:
        if plan.action.native_method != "LOCAL_CANCEL_PURGE" or not current.virtual_purge or not self.validate_before(plan, current):
            raise HeadlessContractError("Invalid purge cancellation")
        return self.observe(json.loads(current.raw_json), run_id=current.run_id, sequence=current.sequence)

    def acknowledge_terminal(self, plan: ActionPlan, current: InteractionFrame):
        """Idempotent in-memory result receipt; no file, restart, or engine call."""
        if plan.action.native_method != "LOCAL_ACK_TERMINAL" or not self.validate_before(plan, current):
            raise HeadlessContractError("Stale or invalid terminal acknowledgement")
        receipt = terminal_receipt(current)
        previous = self._terminal_receipts.get(current.run_id)
        if previous is not None and previous != receipt:
            raise HeadlessContractError("Terminal result changed after acknowledgement")
        self._terminal_receipts[current.run_id] = receipt
        return receipt


def _deck(state):
    return {c.uuid: c for c in state.deck}


def _reward_rows(frame):
    raw = json.loads(frame.raw_json)
    menu = raw.get("interaction", {})
    rows = menu.get("rewards", menu.get("background_rewards"))
    if rows is None: return None  # An overlay without parent evidence is not an empty reward list.
    return [{k: v for k, v in r.items() if k not in ("native_index", "linked_reward_index")}
            for r in rows]


def _selection_committed(before, after, purpose, uuids):
    left, right = _deck(before), _deck(after)
    selected = set(uuids)
    if not selected or not selected <= set(left): return False
    untouched = set(left) - selected
    if any(uid not in right or right[uid].reference != left[uid].reference for uid in untouched): return False
    if purpose == "purge": return set(right) == set(left) - selected
    if purpose == "upgrade": return set(right) == set(left) and all(right[u].id == left[u].id and right[u].upgrades == left[u].upgrades + 1 for u in selected)
    if purpose == "transform": return len(right) == len(left) and not selected & set(right) and len(set(right) - set(left)) == len(selected)
    if purpose == "duplicate":
        def facts(card):return card.model_dump(exclude={'index','choice_index'})
        def copied(card):return card.model_dump(exclude={'index','choice_index','uuid','is_bottled','description','is_playable'})
        added = [right[u] for u in set(right) - set(left)]
        def matches(source,copy):
            level=_acquired_upgrades(before,source.upgrades,source.type) if source.can_upgrade else source.upgrades
            if level==source.upgrades:return copied(source)==copied(copy)
            immutable=('id','type','rarity','target','misc')
            return (copy.upgrades==level and
                    all(getattr(copy,k)==getattr(source,k) for k in immutable))
        for uid in selected:
            match=next((i for i,c in enumerate(added) if matches(left[uid],c) and not c.is_bottled),None)
            if match is None:return False
            added.pop(match)
        return (set(left) <= set(right) and all(facts(left[u])==facts(right[u]) for u in left)
                and not added
                and before.player==after.player and before.relics==after.relics and before.potions==after.potions)
    if purpose == "bottle": return set(left) == set(right) and all(right[u].is_bottled and not left[u].is_bottled for u in selected)
    return False


def _next_survivor_discard(before, after, selected):
    """Duplication Potion or Burst replays Survivor and opens a new discard prompt."""
    from .event_receipts import Stream, _streams
    a, b = json.loads(before.raw_json), json.loads(after.raw_json)
    sel, nxt = a['interaction']['selection'], b['interaction']['selection']
    pa, pb = a['player'], b['player']
    ra, rb = a['combat_receipt'], b['combat_receipt']
    played = ra['played_card_uuids']
    replays = [p for p in pa['powers'] if p['id'] in ('DuplicationPower','Burst')]
    def omit(x, keys): return {k:v for k,v in x.items() if k not in keys}
    def cards(xs): return [omit(x, {'index'}) for x in xs]
    by_uid={c['uuid']:c for pile in ('masterDeck','hand','drawPile','discardPile','exhaustPile')
            for c in pa[pile]}
    # Attacks do not consume Burst. Require the closest preceding non-attack
    # to be a Burst in the discard, or the directly queued power above.
    previous=next((by_uid.get(u,{}) for u in reversed(played[:-1])
                   if by_uid.get(u,{}).get('type')!='ATTACK'),{})
    burst_already_removed=(not replays and previous.get('id')=='Burst'
                           and previous in pa['discardPile'])
    if (not played or len(selected)!=1 or len(pb['discardPile'])<2 or len(replays)>1
            or (not replays and not burst_already_removed)
            or any(p['amount']<0 or (p['id']=='DuplicationPower' and p['amount']!=0) for p in replays)
            or omit(ra,{'played_card_uuids','rng'})!=omit(rb,{'played_card_uuids','rng'})
            or rb['played_card_uuids']!=played+[played[-1]]
            or any(a[k]!=b[k] for k in ('floor','act','room','roomPhase','monsters','map','observedKeys','history'))
            or sel != dict(nxt, can_confirm=True) or nxt['can_confirm'] is not False
            or sel['kind']!='hand' or sel['purpose']!='discard' or sel['count']!=1
            or [c['uuid'] for c in a['cardSelection']['selected']] != selected or b['cardSelection']['selected']
            or omit(a['cardSelection'],{'selected'})!=omit(b['cardSelection'],{'selected'})): return False
    source = pb['discardPile'][-1]
    removed = {p['id'] for p in replays if p['amount']==0}
    relics=[dict(r,counter=r['counter']+1) if r['id'] in ('Pocketwatch','Velvet Choker') else r for r in pa['relics']]
    powers={p['id']:p['amount'] for p in pa['powers']}
    expected_rng=dict(_streams(a));expected_pile=pa['drawPile']
    if powers.get('Hex')==1:
        if not expected_pile or any(r['id']=='InkBottle' and r['counter']==0 for r in pa['relics']):return False
        rng=Stream(expected_rng['card_random']);slot=rng.bounded(len(expected_pile))
        expected_rng['card_random']=rng.state()
        if len(pb['drawPile'])!=len(expected_pile)+1:return False
        status=pb['drawPile'][slot]
        if (status['uuid'] in by_uid or status['uuid'] in selected or status['id']!='Dazed'
                or status['type']!='STATUS' or status['rarity']!='COMMON' or status['timesUpgraded']!=0
                or status['cost']!=-2 or status['costForTurn']!=-2 or status['exhaust']):return False
        expected_pile=expected_pile[:slot]+[status]+expected_pile[slot:]
    elif 'Hex' in powers:return False
    block=max(0,source['baseBlock']+powers.get('Dexterity',0))
    if powers.get('Frail',0)>0:block=block*3//4
    block+=powers.get('After Image',0)
    if 'No Block' in powers:block=0
    return (source['id']=='Survivor' and source['uuid']==played[-1] and source['block']>0
            and cards(pb['discardPile'][:-2])==cards(pa['discardPile'])
            and cards([pb['discardPile'][-2]])==cards([dict(c,block=c['baseBlock'],damage=c['baseDamage'],
                    magicNumber=c['baseMagicNumber']) for c in a['cardSelection']['selected']])
            and pb['block']==min(999,pa['block']+block)
            and all(pb[k]==pa[k]+2 for k in ('discardCount','discardPileCount'))
            and pb['powers']==[p for p in pa['powers'] if p['id'] not in removed]
            and pb['relics']==relics and expected_rng==_streams(b)
            and cards(pb['drawPile'])==cards(expected_pile)
            and pb['drawCount']==pb['drawPileCount']==len(expected_pile)
            and omit(pa,{'block','discardCount','discardPileCount','discardPile','powers','relics','drawPile','drawCount','drawPileCount'})
                ==omit(pb,{'block','discardCount','discardPileCount','discardPile','powers','relics','drawPile','drawCount','drawPileCount'}))


def _next_duplicated_discard(before, after, selected):
    """Finish one duplicated discard card and bind its next discard prompt."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player'];ra,rb=a['combat_receipt'],b['combat_receipt']
    sel,nxt=a['interaction']['selection'],b['interaction']['selection']
    played=ra['played_card_uuids']
    if (a['phase']!='CARD_SELECT' or b['phase']!='CARD_SELECT' or a['roomPhase']!='COMBAT'
            or len(selected)!=1 or [c['uuid'] for c in a['cardSelection']['selected']]!=selected
            or not played or rb['played_card_uuids']!=played+[played[-1]]
            or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','map','history','observedKeys'))
            or {k:v for k,v in ra.items() if k not in ('played_card_uuids','rng')}!=
               {k:v for k,v in rb.items() if k not in ('played_card_uuids','rng')}
            or sel!=dict(nxt,can_confirm=True) or nxt['can_confirm'] is not False
            or sel['kind']!='hand' or sel['purpose']!='discard' or sel['count']!=1
            or b['cardSelection']['selected'] or len(pb['discardPile'])<2):return False
    source=pb['discardPile'][-1]
    if source['uuid']!=played[-1] or source['id'] not in ('Acrobatics','Dagger Throw'):return False
    powers={p['id']:p['amount'] for p in pa['powers']}
    duplicate=powers.get('DuplicationPower')==0
    burst=source['id']=='Acrobatics' and powers.get('Burst',0)>0
    if not (duplicate or burst) or source['id']=='Dagger Throw' and not duplicate:return False
    amount=source['baseMagicNumber'] if source['id']=='Acrobatics' else 1
    if amount<1 or len(pa['drawPile'])<amount:return False
    drawn=pa['drawPile'][-amount:][::-1]
    streams=_streams(a);expected=dict(streams)
    confusion=powers.get('Confusion')==-1
    if confusion:
        rng=Stream(streams['card_random'])
        costs=[]
        for card in drawn:costs.append(rng.bounded(4) if card['cost']>=0 else card['cost'])
        expected['card_random']=rng.state()
    else:costs=[c['costForTurn'] for c in drawn]
    if expected!=_streams(b):return False
    def permanent(c):return (c['uuid'],c['id'],c['timesUpgraded'],c['misc'])
    observed_drawn=pb['hand'][len(pa['hand']):]
    if ([permanent(c) for c in pb['hand'][:len(pa['hand'])]]!=[permanent(c) for c in pa['hand']]
            or [permanent(c) for c in observed_drawn]!=[permanent(c) for c in drawn]
            or [(c['cost'],c['costForTurn']) for c in observed_drawn]!=[(n,n) for n in costs]
            or [permanent(c) for c in pb['drawPile']]!=[permanent(c) for c in pa['drawPile'][:-amount]]):return False
    chosen=dict(a['cardSelection']['selected'][0],index=pb['discardPile'][-2]['index'])
    for key in ('damage','block','magicNumber'):chosen[key]=chosen['base'+key[0].upper()+key[1:]]
    def no_index(c):return {k:v for k,v in c.items() if k!='index'}
    if ([no_index(c) for c in pb['discardPile'][:-2]]!=[no_index(c) for c in pa['discardPile']]
            or no_index(pb['discardPile'][-2])!=no_index(chosen)
            or [no_index(c) for c in pb['exhaustPile']]!=[no_index(c) for c in pa['exhaustPile']]
            or [c['uuid'] for c in b['cardSelection']['options']]!=[c['uuid'] for c in pb['hand']]):return False
    expected_powers=[p for p in pa['powers'] if not (p['id']=='DuplicationPower' and duplicate)]
    expected_relics=[]
    pending_pen=False
    for relic in pa['relics']:
        counter=relic['counter']
        if relic['id'] in ('Pocketwatch','Velvet Choker'):counter+=1
        elif relic['id']=='InkBottle':
            if not 0<=counter<10:return False
            counter=(counter+1)%10
        elif relic['id']=='Pen Nib' and source['id']=='Dagger Throw':
            if not 0<=counter<10:return False
            pending_pen=counter==9 and 'Pen Nib' not in powers
            counter=(counter+1)%10
        elif relic['id'] in ('Shuriken','Kunai','Nunchaku','Ornamental Fan') and source['id']=='Dagger Throw':
            period=10 if relic['id']=='Nunchaku' else 3
            if not 0<=counter<period:return False
            counter=(counter+1)%period
        expected_relics.append(dict(relic,counter=counter))
    if pending_pen:expected_powers.append(dict(id='Pen Nib',name='Pen Nib',amount=1,type='BUFF'))
    block=min(999,pa['block']+powers.get('After Image',0))
    monsters=copy.deepcopy(a['monsters'])
    if source['id']=='Dagger Throw':
        damage=max(0,source['baseDamage']+powers.get('Strength',0))
        if powers.get('Weakened',0)>0:damage=damage*3//4
        if powers.get('Double Damage',0)>0:damage*=2
        if pending_pen or powers.get('Pen Nib',0)>0:damage*=2
        changed=[]
        for i,(old,new) in enumerate(zip(a['monsters'],b['monsters'],strict=True)):
            target_damage=damage
            if any(p['id']=='Vulnerable' and p['amount']>0 for p in old['powers']):target_damage=target_damage*3//2
            hp=old['hp']-max(0,target_damage-old['block']);guard=max(0,old['block']-target_damage)
            if hp>0 and new==dict(old,hp=hp,block=guard) and new!=old:changed.append(i)
            elif new!=old:return False
        if len(changed)!=1:return False
        monsters[changed[0]]=b['monsters'][changed[0]]
    if b['monsters']!=monsters or pb['powers']!=expected_powers or pb['relics']!=expected_relics:return False
    changes={'hand','handSize','drawPile','drawCount','drawPileCount','discardPile','discardCount','discardPileCount',
             'block','powers','relics'}
    return (pb['block']==block and pb['handSize']==len(pb['hand'])
            and pb['drawCount']==pb['drawPileCount']==len(pb['drawPile'])
            and pb['discardCount']==pb['discardPileCount']==len(pb['discardPile'])
            and {k:v for k,v in pa.items() if k not in changes}=={k:v for k,v in pb.items() if k not in changes})


def _empty_retain_to_trade(before, after):
    """Decline Well-Laid Plans, settle Looter, then bind Tools of the Trade."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player'];ra,rb=a['combat_receipt'],b['combat_receipt']
    sel,nxt=a['interaction']['selection'],b['interaction']['selection']
    powers={p['id']:p['amount'] for p in pa['powers']}
    if (a['phase']!='CARD_SELECT' or b['phase']!='CARD_SELECT'
            or a['roomPhase']!='COMBAT' or b['roomPhase']!='COMBAT'
            or sel!={'kind':'hand','purpose':'retain','count':2,'locked':False,'can_confirm':True,
                     'any_number':True,'can_pick_zero':True,'auto_confirm':False}
            or a['cardSelection']['selected'] or powers.get('Retain Cards')!=2
            or powers.get('Tools Of The Trade')!=1 or len(a['monsters'])!=1 or len(b['monsters'])!=1
            or a['monsters'][0]['id']!='Looter' or a['monsters'][0]['intent']!='DEFEND'
            or a['monsters'][0]['hp']<=0 or a['monsters'][0]['block']!=0
            or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','map','history','observedKeys'))
            or {k:v for k,v in ra.items() if k not in ('turn','rng')}!=
               {k:v for k,v in rb.items() if k not in ('turn','rng')}
            or rb['turn']!=ra['turn']+1 or nxt!={'kind':'hand','purpose':'discard','count':1,'locked':False,
                     'can_confirm':False,'any_number':False,'can_pick_zero':False,'auto_confirm':True}
            or b['cardSelection']['selected']):return False
    streams=_streams(a);headless=Stream(streams['headless']);headless.long()
    if dict(streams,headless=headless.state())!=_streams(b):return False
    draw_count=min(6,len(pa['drawPile']))
    drawn=pa['drawPile'][-draw_count:][::-1]
    def facts(cards):return [(c['uuid'],c['id'],c['timesUpgraded'],c['misc'],c['cost']) for c in cards]
    if (facts(pb['hand'])!=facts(drawn) or facts(pb['drawPile'])!=facts(pa['drawPile'][:-draw_count])
            or facts(pb['discardPile'])!=facts(pa['discardPile']+pa['hand'])
            or facts(pb['exhaustPile'])!=facts(pa['exhaustPile'])
            or [c['uuid'] for c in b['cardSelection']['options']]!=[c['uuid'] for c in pb['hand']]):return False
    energy=3+sum(r['id'] in {'Ectoplasm','Cursed Key','Coffee Dripper','Fusion Hammer','Sozu','Runic Dome',
                              'Velvet Choker',"Philosopher's Stone",'Busted Crown','Mark of Pain'} for r in pa['relics'])
    expected_monster=dict(a['monsters'][0],block=6,intent='ESCAPE')
    if b['monsters']!=[expected_monster] or pb['powers']!=pa['powers'] or pb['relics']!=pa['relics']:return False
    changes={'hand','handSize','drawPile','drawCount','drawPileCount','discardPile','discardCount','discardPileCount',
             'exhaustPile','exhaustCount','block','energy'}
    return (pb['block']==0 and pb['energy']==energy and pb['handSize']==len(pb['hand'])
            and pb['drawCount']==pb['drawPileCount']==len(pb['drawPile'])
            and pb['discardCount']==pb['discardPileCount']==len(pb['discardPile'])
            and pb['exhaustCount']==len(pb['exhaustPile'])
            and {k:v for k,v in pa.items() if k not in changes}=={k:v for k,v in pb.items() if k not in changes})


def _tingsha_terminal_discard(before, after, selected):
    """Bind a selected discard to Tingsha's lethal hit and generated rewards."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    left,right=before.state,after.state;pa,pb=a['player'],b['player']
    ra,rb=a['combat_receipt'],b['combat_receipt']
    if (a['phase']!='CARD_SELECT' or b['phase']!='REWARD' or a['roomPhase']!='COMBAT'
            or b['roomPhase']!='COMPLETE' or right.screen_type!='COMBAT_REWARD'
            or len(selected)!=1 or [c['uuid'] for c in a['cardSelection']['selected']]!=selected
            or not any(r['id']=='Tingsha' for r in pa['relics']) or len(a['monsters'])!=1
            or a['monsters'][0]['hp'] not in (1,2,3) or a['monsters'][0]['block']!=0 or b.get('monsters',[])
            or any(a[k]!=b[k] for k in ('seed','floor','act','ascension','room','map','history','observedKeys'))
            or (rb['battle_over'],rb['room_class'],rb['room_phase'],rb['active_monsters'],rb['player_dead'],
                rb['played_card_uuids'],rb['turn'])!=(True,a['room'],'COMPLETE',0,False,[],1)
            or b['driver_receipt'].get('method')!='confirmHandSelect'
            or b['driver_receipt'].get('args')!=[] or b['driver_receipt'].get('sequence')!=after.sequence
            or left.deck!=right.deck or left.relics!=right.relics
            or [(p.id,p.is_empty) for p in left.potions]!=[(p.id,p.is_empty) for p in right.potions]
            or (right.player.current_hp,right.player.max_hp,right.player.gold,right.player.block,right.player.energy)!=
               (left.player.current_hp,left.player.max_hp,left.player.gold,0,left.player.energy)):
        return False
    rewards=b['interaction'].get('rewards');public=b.get('rewards')
    if (not isinstance(rewards,list) or not isinstance(public,list) or len(rewards)!=len(public)
            or [r.get('native_index') for r in rewards]!=list(range(len(rewards)))
            or [r.get('type') for r in rewards] not in (['GOLD','CARD'],['GOLD','POTION','CARD'])):return False
    gold=rewards[0].get('amount')
    if public[0].get('type')!='GOLD' or public[0].get('gold')!=gold:return False
    card_row=rewards[-1];visible=public[-1]
    uuids=card_row.get('card_uuids');cards=visible.get('cards')
    occupied={c['uuid'] for pile in ('masterDeck','hand','drawPile','discardPile','exhaustPile') for c in pa[pile]}
    if (visible.get('type')!='CARD' or not isinstance(cards,list) or len(cards)!=3
            or not isinstance(uuids,list) or len(uuids)!=3 or len(set(uuids))!=3
            or any(not isinstance(u,str) or u in occupied for u in uuids)
            or len(set(cards))!=3):return False
    if len(rewards)==3:
        if (public[1].get('type')!='POTION' or not isinstance(rewards[1].get('potion_id'),str)
                or not rewards[1]['potion_id'] or not isinstance(public[1].get('potion'),str)):return False
    streams=_streams(a);expected=dict(streams)
    random=Stream(streams['card_random']);random.bounded(1);expected['card_random']=random.state()
    if a['room']=='MonsterRoom':
        treasure=Stream(streams['treasure'])
        if gold!=10+treasure.bounded(11):return False
        expected['treasure']=treasure.state();allowed={'card','card_random','potion','treasure'}
    elif a['room']=='MonsterRoomBoss':
        if type(gold) is not int or not 75<=gold<=100:return False
        misc=Stream(streams['misc']);misc.long();expected['misc']=misc.state()
        allowed={'card','card_random','potion','misc'}
    else:return False
    observed=_streams(b)
    for name in ('card','potion'):
        delta=observed[name]['counter']-streams[name]['counter']
        if delta<1:return False
        rng=Stream(streams[name])
        for _ in range(delta):rng.long()
        expected[name]=rng.state()
    if any(streams[k]!=observed[k] for k in streams.keys()-allowed):return False
    return expected==observed


def _empty_scry_terminal(before, after):
    """Bind zero-card Scry to Just Lucky's queued lethal boss hit."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    left,right=before.state,after.state;pa,pb=a['player'],b['player']
    ra,rb=a['combat_receipt'],b['combat_receipt'];sel=a['interaction']['selection']
    played=ra['played_card_uuids']
    source=next((c for c in pa['masterDeck'] if played and c['uuid']==played[-1]),None)
    if (a['phase']!='CARD_SELECT' or b['phase']!='REWARD' or a['room']!='MonsterRoomBoss'
            or a['roomPhase']!='COMBAT' or b['roomPhase']!='COMPLETE'
            or sel!={'kind':'grid','purpose':'scry','count':2,'locked':False,'can_confirm':True,
                     'any_number':True,'can_pick_zero':True,'auto_confirm':True}
            or a['cardSelection']['selected'] or source is None or source['id']!='JustLucky'
            or source['timesUpgraded']!=1 or len(a['monsters'])!=1 or b.get('monsters',[])
            or any(a[k]!=b[k] for k in ('seed','floor','act','ascension','room','map','history','observedKeys'))
            or [c['uuid'] for c in a['cardSelection']['options']]!=
               [c['uuid'] for c in pa['drawPile'][::-1][:sel['count']]]
            or (rb['battle_over'],rb['room_class'],rb['room_phase'],rb['active_monsters'],rb['player_dead'],
                rb['played_card_uuids'],rb['turn'])!=(True,'MonsterRoomBoss','COMPLETE',0,False,[],1)
            or b['driver_receipt'].get('method')!='confirmGrid' or b['driver_receipt'].get('args')!=[]
            or b['driver_receipt'].get('sequence')!=after.sequence
            or left.deck!=right.deck or left.relics!=right.relics
            or [(p.id,p.is_empty) for p in left.potions]!=[(p.id,p.is_empty) for p in right.potions]
            or (right.player.current_hp,right.player.max_hp,right.player.gold,right.player.block,right.player.energy)!=
               (left.player.current_hp,left.player.max_hp,left.player.gold,0,left.player.energy)):
        return False
    powers={p['id']:p['amount'] for p in pa.get('powers',[])}
    damage=max(0,source['baseDamage']+powers.get('Strength',0))
    if powers.get('Weakened',0)>0:damage=damage*3//4
    if pa.get('stance')=='Wrath':damage*=2
    monster=a['monsters'][0]
    if damage<monster['hp']+monster['block']:return False
    rewards=b['interaction'].get('rewards');public=b.get('rewards')
    if (not isinstance(rewards,list) or not isinstance(public,list) or len(rewards)!=len(public)
            or [r.get('native_index') for r in rewards]!=list(range(len(rewards)))
            or [r.get('type') for r in rewards] not in (['GOLD','CARD'],['GOLD','POTION','CARD'])):return False
    gold=rewards[0].get('amount')
    if (type(gold) is not int or not 50<=gold<=100 or public[0].get('type')!='GOLD'
            or public[0].get('gold')!=gold):return False
    card_row=rewards[-1];visible=public[-1];uuids=card_row.get('card_uuids');cards=visible.get('cards')
    occupied={c['uuid'] for pile in ('masterDeck','hand','drawPile','discardPile','exhaustPile') for c in pa[pile]}
    if (visible.get('type')!='CARD' or not isinstance(cards,list) or len(cards)!=3 or len(set(cards))!=3
            or not isinstance(uuids,list) or len(uuids)!=3 or len(set(uuids))!=3
            or any(not isinstance(u,str) or u in occupied for u in uuids)):return False
    if len(rewards)==3 and (public[1].get('type')!='POTION'
            or not isinstance(rewards[1].get('potion_id'),str) or not rewards[1]['potion_id']):return False
    streams=_streams(a);observed=_streams(b);expected=dict(streams)
    misc=Stream(streams['misc']);misc.long();expected['misc']=misc.state()
    for name in ('card','potion'):
        delta=observed[name]['counter']-streams[name]['counter']
        if delta<1:return False
        rng=Stream(streams[name])
        for _ in range(delta):rng.long()
        expected[name]=rng.state()
    allowed={'card','potion','misc'}
    return all(streams[k]==observed[k] for k in streams.keys()-allowed) and expected==observed


def _queued_cut_through_fate_scry(before, after, selected):
    """Verify two consecutive Scry screens against the actual queued autoplay."""
    from .event_receipts import _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player']
    ra,rb=a['combat_receipt'],b['combat_receipt']
    first=a['interaction'].get('scry_replay')
    second=b['interaction'].get('scry_replay')
    if first is None or second is None:return False
    next_play=first['next']
    if (first['remaining']!=1 or not next_play or second['remaining']!=0 or second['next'] is not None
            or first['target']!=next_play['target'] or second['target']!=next_play['target']
            or second['autoplay'] is not True or len(a['monsters'])!=1 or len(b['monsters'])!=1
            or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','phase','map','history','observedKeys'))
            or a['phase']!='CARD_SELECT' or a['roomPhase']!='COMBAT'
            or _streams(a)!=_streams(b)
            or {k:v for k,v in ra.items() if k not in ('played_card_uuids','rng')}!=
               {k:v for k,v in rb.items() if k not in ('played_card_uuids','rng')}):
        return False
    played=ra['played_card_uuids']
    source,queued,current=first['source'],next_play['card'],second['source']
    fields=('uuid','card_id','times_upgraded','base_damage','base_magic_number','cost','cost_for_turn','purge_on_use','exhaust')
    if (any(queued[k]!=current[k] for k in fields)
            or any(c['card_id']!='CutThroughFate' or c['exhaust'] or c['times_upgraded'] not in (0,1)
                   or c['base_damage']!=7+2*c['times_upgraded'] or c['base_magic_number']!=2+c['times_upgraded']
                   for c in (source,queued))
            or source['purge_on_use'] or not played or played[-1]!=source['uuid']
            or rb['played_card_uuids']!=played+[queued['uuid']]):
        return False
    driver=a['driver_receipt'];powers=pa['powers'];relics=pa['relics']
    if next_play['ignore_energy_total'] is True:
        if (driver['method']!='playCard' or driver['card_uuid']!=source['uuid'] or first['autoplay'] is not False
                or queued['uuid']!=source['uuid'] or not queued['purge_on_use']
                or [(p['id'],p['amount']) for p in powers if p['id']=='DuplicationPower']!=[('DuplicationPower',0)]):
            return False
        powers=[p for p in powers if p['id']!='DuplicationPower']
    elif next_play['ignore_energy_total'] is False:
        if (driver['method']!='usePotion' or first['autoplay'] is not True or queued['purge_on_use']
                or queued['uuid'] in played
                or [c['uuid'] for c in pa['masterDeck'] if c['id']=='CutThroughFate'].count(queued['uuid'])!=1):
            return False
    else:return False
    amounts={p['id']:p['amount'] for p in powers}
    if (pb['powers']!=powers or amounts.keys()-{'Strength','Dexterity','BattleHymn','Thorns'}
            or pa['stance'] not in (None,'None','Neutral','Wrath') or not 0<=len(pa['hand'])<10):
        return False
    target=a['monsters'][0];target_powers=target['powers']
    if any(p['id'] not in ('Plated Armor','Artifact','Painful Stabs') for p in target_powers):return False
    damage=max(0,queued['base_damage']+amounts.get('Strength',0))*(2 if pa['stance']=='Wrath' else 1)
    hp=target['hp']-max(0,damage-target['block'])
    if hp<=0 or current['damage']!=damage:return False
    if hp<target['hp']:
        if any(p['id']=='Plated Armor' and p['amount']<=1 for p in target_powers):return False
        target_powers=[dict(p,amount=p['amount']-1) if p['id']=='Plated Armor' else p for p in target_powers]
    if b['monsters']!=[dict(target,hp=hp,block=max(0,target['block']-damage),powers=target_powers)]:return False
    expected_relics=[]
    attack_periods={'Kunai':3,'Shuriken':3,'Ornamental Fan':3,'Nunchaku':10}
    for r in relics:
        if r['id']=='Pen Nib':
            if not 0<=r['counter']<9:return False
            r=dict(r,counter=r['counter']+1)
        elif r['id'] in attack_periods:
            period=attack_periods[r['id']]
            if not 0<=r['counter']<period:return False
            r=dict(r,counter=(r['counter']+1)%period)
        expected_relics.append(r)
    if pb['relics']!=expected_relics:return False
    screens=(a['interaction']['selection'],b['interaction']['selection'])
    old,new=a['cardSelection'],b['cardSelection']
    for controls,c in zip(screens,(source,queued)):
        if controls!={'kind':'grid','purpose':'scry','count':c['base_magic_number'],'locked':False,
                      'can_confirm':True,'any_number':True,'can_pick_zero':True,'auto_confirm':True}:return False
    if len(selected)!=len(set(selected)) or [c['uuid'] for c in old['selected']]!=selected or new['selected']:
        return False
    def facts(cards):
        return [(c['uuid'],c['id'],c['timesUpgraded'],c['cost'],c['costForTurn'],c['misc'],c['exhaust']) for c in cards]
    def settled(card):return dict(card,costForTurn=card['cost'])
    draw=pa['drawPile']
    if (facts(old['options'])!=facts(draw[::-1][:source['base_magic_number']])
            or not set(selected)<={c['uuid'] for c in old['options']}
            or any(c['id']=='Weave' for k in ('drawPile','discardPile') for c in pa[k])):
        return False
    active={c['uuid'] for k in ('drawPile','discardPile','hand','exhaustPile') for c in pa[k]}
    if source['uuid'] in active or queued['uuid'] in active:return False
    draw=[c for c in draw if c['uuid'] not in selected]
    if len(draw)<2:return False
    drawn=draw.pop()
    discard=pa['discardPile']+[settled(c) for c in old['selected']]
    if not pb['discardPile']:return False
    cleanup=pb['discardPile'][-1]
    if (cleanup['uuid'],cleanup['id'],cleanup['timesUpgraded'],cleanup['cost'],cleanup['costForTurn'],cleanup['misc'],cleanup['exhaust'])!=(
            source['uuid'],'CutThroughFate',source['times_upgraded'],source['cost'],source['cost'],source['misc'],False):
        return False
    changes={'drawPile','drawCount','drawPileCount','hand','handSize','discardPile','discardCount','discardPileCount','powers','relics'}
    return (facts(new['options'])==facts(draw[::-1][:queued['base_magic_number']])
            and facts(pb['drawPile'])==facts(draw) and facts(pb['hand'])==facts(pa['hand']+[drawn])
            and facts(pb['discardPile'][:-1])==facts(discard)
            and pb['drawCount']==pb['drawPileCount']==len(draw)
            and pb['discardCount']==pb['discardPileCount']==len(discard)+1
            and pb['handSize']==len(pa['hand'])+1
            and {k:v for k,v in pa.items() if k not in changes}=={k:v for k,v in pb.items() if k not in changes})


def _next_cut_through_fate_scry(before, after, selected):
    """Prove a pending Duplication Potion or Omniscience play into a second Scry."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player']
    ra,rb=a['combat_receipt'],b['combat_receipt']
    old,new=a['cardSelection'],b['cardSelection']
    sel=a['interaction']['selection']
    played=ra['played_card_uuids']
    def omit(x, keys):return {k:v for k,v in x.items() if k not in keys}
    def cards(xs):return [omit(x,{'index'}) for x in xs]
    if (a['phase']!=b['phase'] or a['phase']!='CARD_SELECT' or a['roomPhase']!='COMBAT'
            or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','map','observedKeys','history'))
            or sel!=b['interaction']['selection'] or sel['kind']!='grid' or sel['purpose']!='scry'
            or sel['can_confirm'] is not True or new['selected'] or not played
            or [c['uuid'] for c in old['selected']]!=selected
            or pb['powers'] or pa['stance'] not in (None,'Neutral') or len(pa['hand'])>=10
            or omit(ra,{'played_card_uuids','rng','active_monsters'})!=omit(rb,{'played_card_uuids','rng','active_monsters'})):
        return False
    streams=_streams(a)
    target_states=list(a['monsters'])
    exhaust,discard=list(pa['exhaustPile']),[]
    if [(p['id'],p['amount']) for p in pa['powers']]==[('DuplicationPower',0)]:
        driver=a['driver_receipt']
        if (driver['method']!='playCard' or driver['card_uuid']!=played[-1]
                or rb['played_card_uuids']!=played+[played[-1]]):return False
        slot=driver['args'][1]
        if type(slot) is not int or not 0<=slot<len(target_states):return False
        target=target_states[slot]
        if target['id'] not in ('FuzzyLouseNormal','FuzzyLouseDefensive','Sentry') or any(
                p['id']!='Artifact' for p in target['powers']):return False
        source=pb['discardPile'][-1];discard=[source]
    elif not pa['powers'] and len(played)>=2 and len(target_states)==1:
        # Omniscience exhausts the first play, then auto-targets the only
        # surviving Snake Plant with a stat-equivalent copy and a fresh UUID.
        omni=pa['exhaustPile'][-1]
        copy=rb['played_card_uuids'][-1]
        seen=set(played)|{c['uuid'] for k in ('masterDeck','hand','drawPile','discardPile','exhaustPile') for c in pa[k]}
        if (omni['id']!='Omniscience' or omni['uuid']!=played[-2] or copy in seen
                or rb['played_card_uuids']!=played+[copy]):return False
        source=pb['exhaustPile'][-1]
        # Omniscience's just-exhausted instance settles its transient stats.
        exhaust[-1]=dict(omni,damage=omni['baseDamage'],block=omni['baseBlock'],magicNumber=omni['baseMagicNumber'])
        exhaust.append(source)
        if not source['exhaust']:return False
        slot=0;target=target_states[0]
        if (target['id']!='SnakePlant' or len(target['powers'])!=1
                or target['powers'][0]['id']!='Malleable' or target['powers'][0]['amount']<4):return False
        m=target['powers'][0]
        # Its first queued block runs before the second play, whose new
        # Malleable block remains queued at the next Scry boundary.
        target=dict(target,block=min(999,target['block']+m['amount']-1),powers=[dict(m,amount=m['amount']+1)])
        rng=Stream(streams['card_random']);rng.bounded(1);streams=dict(streams,card_random=rng.state())
    else:return False
    if streams!=_streams(b):return False
    if (source['id']!='CutThroughFate' or source['uuid']!=played[-1] or source['timesUpgraded'] not in (0,1)
            or sel['count']!=source['baseMagicNumber'] or source['baseDamage']!=7+2*source['timesUpgraded']
            or cards(old['options'])!=cards(pa['drawPile'][::-1][:sel['count']])):
        return False
    damage=source['baseDamage']
    hp=target['hp']-max(0,damage-target['block'])
    if hp<=0:target_states.pop(slot)
    else:target_states[slot]=dict(target,hp=hp,block=max(0,target['block']-damage))
    if b['monsters']!=target_states or rb['active_monsters']!=len(target_states):return False
    draw=[c for c in pa['drawPile'] if c['uuid'] not in selected]
    if len(draw)<2:return False
    drawn=draw.pop()
    discarded=[next(c for c in old['selected'] if c['uuid']==u) for u in selected]
    changes={'drawPile','drawCount','drawPileCount','hand','handSize','discardPile','discardCount','discardPileCount',
             'exhaustPile','exhaustCount','powers'}
    def hand_cards(xs):
        return [omit(x,{'index','damage','block','magicNumber'}) for x in xs]
    return (cards(new['options'])==cards(draw[::-1][:sel['count']])
            and cards(pb['drawPile'])==cards(draw) and hand_cards(pb['hand'])==hand_cards(pa['hand']+[drawn])
            and cards(pb['discardPile'])==cards(pa['discardPile']+discarded+discard)
            and cards(pb['exhaustPile'])==cards(exhaust) and pb['exhaustCount']==len(pb['exhaustPile'])
            and pb['drawCount']==pb['drawPileCount']==len(draw)
            and pb['discardCount']==pb['discardPileCount']==len(pb['discardPile'])
            and pb['handSize']==len(pa['hand'])+1 and omit(pa,changes)==omit(pb,changes))


def _next_omniscience_just_lucky_scry(before, after, selected):
    """Prove Omniscience's second Just Lucky play into its next Scry."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player'];ra,rb=a['combat_receipt'],b['combat_receipt']
    old,new=a['cardSelection'],b['cardSelection'];played=ra['played_card_uuids']
    controls={'kind':'grid','purpose':'scry','count':1,'locked':False,'can_confirm':True,
              'any_number':True,'can_pick_zero':True,'auto_confirm':True}
    omit=lambda x,keys:{k:v for k,v in x.items() if k not in keys}
    cards=lambda xs:[omit(x,{'index'}) for x in xs]
    stable={'player','monsters','cardSelection','combat_receipt','driver_receipt'}
    if (selected or a['phase']!='CARD_SELECT' or a['roomPhase']!='COMBAT'
            or omit(a,stable)!=omit(b,stable) or a['interaction']['selection']!=controls
            or b['interaction']['selection']!=controls or old['selected'] or new['selected']
            or cards(old['options'])!=cards(pa['drawPile'][::-1][:1]) or old!=new
            or len(played)<3 or len(rb['played_card_uuids'])!=len(played)+1
            or rb['played_card_uuids'][:-1]!=played
            or omit(ra,{'played_card_uuids','rng'})!=omit(rb,{'played_card_uuids','rng'})):
        return False
    driver=b['driver_receipt']
    if (driver.get('method')!='confirmGrid' or driver.get('args')!=[] or driver.get('card_uuid') is not None
            or driver.get('sequence')!=getattr(after,'sequence',driver.get('sequence'))
            or driver.get('request_id')!=getattr(after,'request_id',driver.get('request_id'))
            or driver.get('source_state_token')!=getattr(before,'state_token',driver.get('source_state_token'))):
        return False
    source=next((c for c in pa['masterDeck'] if c['uuid']==played[-1]),None)
    omni={c['uuid'] for c in pa['masterDeck'] if c['id']=='Omniscience'}
    active={c['uuid'] for k in ('hand','drawPile','discardPile','exhaustPile') for c in pa[k]}
    fresh=rb['played_card_uuids'][-1]
    seen=set(played)|{c['uuid'] for k in ('masterDeck','hand','drawPile','discardPile','exhaustPile') for c in pa[k]}
    if (source is None or source['id']!='JustLucky' or source['type']!='ATTACK' or source['uuid'] in active
            or source['timesUpgraded'] not in (0,1) or source['baseDamage']!=3+3*source['timesUpgraded']
            or source['baseBlock']!=2+source['timesUpgraded']
            or source['baseMagicNumber']!=1+source['timesUpgraded'] or controls['count']!=source['baseMagicNumber']
            or not any(u in omni for u in played[:-1]) or not pa['exhaustPile']
            or pa['exhaustPile'][-1]['id']!='Omniscience' or fresh in seen):
        return False
    powers={p['id']:p['amount'] for p in pa['powers']}
    if (set(powers)-{'Confusion','Dexterity','Strength','Thorns','Weak','Frail','No Block'}
            or powers.get('Confusion')!=-1 or pa.get('stance') not in (None,'None','Neutral','Calm')
            or powers.get('Weak',0)>0 or powers.get('Frail',0)>0 or 'No Block' in powers):
        return False
    relics={r['id']:r for r in pa['relics']}
    if ('Kunai' not in relics or relics['Kunai']['counter']!=0
            or 'Pen Nib' not in relics or not 0<=relics['Pen Nib']['counter']<9):
        return False
    expected_relics=[]
    for relic in pa['relics']:
        if relic['id']=='Kunai':relic=dict(relic,counter=1)
        elif relic['id']=='Pen Nib':relic=dict(relic,counter=relic['counter']+1)
        expected_relics.append(relic)
    expected_powers=[];dexterity=False
    for power in pa['powers']:
        if power['id']=='Dexterity':power=dict(power,amount=power['amount']+1);dexterity=True
        expected_powers.append(power)
    if not dexterity:return False
    damage=max(0,source['baseDamage']+powers.get('Strength',0))
    block=max(0,source['baseBlock']+powers.get('Dexterity',0))
    expected_monsters=[];changed=0
    for left,right in zip(a['monsters'],b['monsters']):
        hit=dict(left,block=max(0,left['block']-damage),
                 hp=left['hp']-max(0,damage-left['block']))
        if right==hit and right!=left:expected_monsters.append(hit);changed+=1
        elif right==left:expected_monsters.append(left)
        else:return False
    if len(a['monsters'])!=len(b['monsters']) or changed!=1:return False
    exhaust=[dict(c) for c in pa['exhaustPile']]
    exhaust[-1].update(damage=exhaust[-1]['baseDamage'],block=exhaust[-1]['baseBlock'],
                       magicNumber=exhaust[-1]['baseMagicNumber'])
    exhausted=dict(source,index=len(exhaust),damage=damage,block=block,
                   magicNumber=source['baseMagicNumber'],exhaust=True)
    exhaust.append(exhausted)
    hand=[dict(c,block=c['block']+1) for c in pa['hand']]
    expected_player=dict(pa,block=min(999,pa['block']+block),powers=expected_powers,
                         relics=expected_relics,hand=hand,exhaustPile=exhaust,
                         exhaustCount=pa['exhaustCount']+1)
    streams=_streams(a);rng=Stream(streams['card_random']);rng.bounded(1)
    return (expected_monsters==b['monsters'] and expected_player==pb
            and dict(streams,card_random=rng.state())==_streams(b))


def _omniscience_just_lucky_into_cut_scry(before, after, selected):
    """Prove the pending Just Lucky tail and next Omniscience Cut Through Fate."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player'];ra,rb=a['combat_receipt'],b['combat_receipt']
    old,new=a['cardSelection'],b['cardSelection'];played=ra['played_card_uuids']
    first={'kind':'grid','purpose':'scry','count':1,'locked':False,'can_confirm':True,
           'any_number':True,'can_pick_zero':True,'auto_confirm':True}
    second=dict(first,count=2)
    omit=lambda x,keys:{k:v for k,v in x.items() if k not in keys}
    cards=lambda xs:[omit(x,{'index'}) for x in xs]
    stable={'player','monsters','cardSelection','combat_receipt','driver_receipt','interaction'}
    if (selected or a['phase']!='CARD_SELECT' or a['roomPhase']!='COMBAT'
            or omit(a,stable)!=omit(b,stable) or a['interaction']['selection']!=first
            or b['interaction'].get('selection')!=second or old['selected'] or new['selected']
            or cards(old['options'])!=cards(pa['drawPile'][::-1][:1])
            or cards(new['options'])!=cards(pa['drawPile'][::-1][:2])
            or len(played)<3 or rb['played_card_uuids'][:-1]!=played
            or len(rb['played_card_uuids'])!=len(played)+1
            or omit(ra,{'played_card_uuids','rng'})!=omit(rb,{'played_card_uuids','rng'})):
        return False
    driver=b['driver_receipt']
    if (driver.get('method')!='confirmGrid' or driver.get('args')!=[] or driver.get('card_uuid') is not None
            or driver.get('sequence')!=getattr(after,'sequence',driver.get('sequence'))
            or driver.get('request_id')!=getattr(after,'request_id',driver.get('request_id'))
            or driver.get('source_state_token')!=getattr(before,'state_token',driver.get('source_state_token'))):
        return False
    replay=b['interaction'].get('scry_replay')
    if (not isinstance(replay,dict) or replay.get('autoplay') is not True or replay.get('remaining')!=1
            or replay.get('next') is not None or type(replay.get('target')) is not int or replay['target']<0
            or set(b['interaction'])!=set(a['interaction'])|{'scry_replay'}
            or omit(a['interaction'],{'selection'})!=omit(b['interaction'],{'selection','scry_replay'})):
        return False
    source=next((c for c in pa['masterDeck'] if c['uuid']==rb['played_card_uuids'][-1]),None)
    tail=pa['exhaustPile'][-1] if pa['exhaustPile'] else None
    active={c['uuid'] for k in ('hand','drawPile','discardPile','exhaustPile') for c in pa[k]}
    seen={c['uuid'] for k in ('masterDeck','hand','drawPile','discardPile','exhaustPile') for c in pa[k]}
    omni={c['uuid'] for c in pa['masterDeck'] if c['id']=='Omniscience'}
    if (source is None or source['id']!='CutThroughFate' or source['uuid'] in active
            or source['timesUpgraded'] not in (0,1) or source['baseDamage']!=7+2*source['timesUpgraded']
            or source['baseMagicNumber']!=2+source['timesUpgraded'] or source['baseBlock']!=-1
            or tail is None or tail['id']!='JustLucky' or tail['uuid']!=played[-2]
            or played[-1] in seen or not any(u in omni for u in played[:-2])):
        return False
    powers={p['id']:p['amount'] for p in pa['powers']}
    if (set(powers)-{'Confusion','Dexterity','Strength','Thorns'} or powers.get('Confusion')!=-1
            or pa.get('stance') not in (None,'None','Neutral','Calm')):
        return False
    just_damage=max(0,tail['baseDamage']+powers.get('Strength',0))
    just_block=max(0,tail['baseBlock']+powers.get('Dexterity',0))
    cut_damage=max(0,source['baseDamage']+powers.get('Strength',0))
    expected_source={'card_id':'CutThroughFate','uuid':source['uuid'],
                     'times_upgraded':source['timesUpgraded'],'upgraded':source['upgraded'],
                     'cost':source['cost'],'cost_for_turn':source['costForTurn'],
                     'base_damage':source['baseDamage'],'base_block':source['baseBlock'],
                     'base_magic_number':source['baseMagicNumber'],'damage':cut_damage,
                     'block':max(0,source['baseBlock']+powers.get('Dexterity',0)),
                     'magic_number':source['baseMagicNumber'],'misc':source['misc'],'exhaust':True,
                     'is_ethereal':False,'self_retain':False,'retain':False,'free_to_play_once':False,
                     'purge_on_use':False,'shuffle_back_into_draw_pile':False,
                     'dont_trigger_on_use_card':False,'target_enum_ordinal':0,'_private':{}}
    if replay.get('source')!=expected_source:return False
    relics={r['id']:r for r in pa['relics']}
    if ('Kunai' not in relics or relics['Kunai']['counter']!=1
            or 'Pen Nib' not in relics or not 0<=relics['Pen Nib']['counter']<8):
        return False
    expected_relics=[]
    for relic in pa['relics']:
        if relic['id'] in ('Kunai','Pen Nib'):relic=dict(relic,counter=relic['counter']+1)
        expected_relics.append(relic)
    total=just_damage+cut_damage;expected_monsters=[];changed=0
    for left,right in zip(a['monsters'],b['monsters']):
        hit=dict(left,block=max(0,left['block']-total),hp=left['hp']-max(0,total-left['block']))
        if right==hit and right!=left:expected_monsters.append(hit);changed+=1
        elif right==left:expected_monsters.append(left)
        else:return False
    if len(a['monsters'])!=len(b['monsters']) or changed!=1:return False
    exhaust=[dict(c) for c in pa['exhaustPile']]
    exhaust[-1].update(damage=tail['baseDamage'],block=tail['baseBlock'],magicNumber=tail['baseMagicNumber'])
    expected_player=dict(pa,block=min(999,pa['block']+just_block),relics=expected_relics,
                         exhaustPile=exhaust)
    streams=_streams(a);rng=Stream(streams['card_random']);rng.bounded(1)
    return (expected_monsters==b['monsters'] and expected_player==pb
            and dict(streams,card_random=rng.state())==_streams(b))


def _queued_omniscience_cut_through_fate_scry(before, after, selected):
    """Prove an omitted purge-on-use Omniscience copy between two CTF Scries."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player'];ra,rb=a['combat_receipt'],b['combat_receipt']
    old,new=a['cardSelection'],b['cardSelection'];played=ra['played_card_uuids']
    first=a['interaction'].get('scry_replay');second=b['interaction'].get('scry_replay')
    controls={'kind':'grid','purpose':'scry','count':2,'locked':False,'can_confirm':True,
              'any_number':True,'can_pick_zero':True,'auto_confirm':True}
    omit=lambda x,keys:{k:v for k,v in x.items() if k not in keys}
    cards=lambda xs:[omit(x,{'index'}) for x in xs]
    stable={'player','monsters','cardSelection','combat_receipt','driver_receipt','interaction'}
    if (selected or a['phase']!='CARD_SELECT' or a['roomPhase']!='COMBAT'
            or omit(a,stable)!=omit(b,stable) or a['interaction'].get('selection')!=controls
            or b['interaction'].get('selection')!=controls or old['selected'] or new['selected']
            or not isinstance(first,dict) or not isinstance(second,dict)
            or first.get('autoplay') is not True or first.get('remaining')!=1 or first.get('next') is not None
            or second.get('autoplay') is not True or second.get('remaining')!=0 or second.get('next') is not None
            or first.get('target')!=second.get('target') or type(first.get('target')) is not int or first['target']<0
            or len(played)<2 or len(rb['played_card_uuids'])!=len(played)+1
            or rb['played_card_uuids'][:-1]!=played
            or omit(ra,{'played_card_uuids','rng'})!=omit(rb,{'played_card_uuids','rng'})
            or omit(a['interaction'],{'scry_replay'})!=omit(b['interaction'],{'scry_replay'})):
        return False
    driver=b['driver_receipt']
    if (driver.get('method')!='confirmGrid' or driver.get('args')!=[] or driver.get('card_uuid') is not None
            or driver.get('sequence')!=getattr(after,'sequence',driver.get('sequence'))
            or driver.get('request_id')!=getattr(after,'request_id',driver.get('request_id'))
            or driver.get('source_state_token')!=getattr(before,'state_token',driver.get('source_state_token'))):
        return False
    source=first.get('source');copy_card=second.get('source')
    original=next((c for c in pa['masterDeck'] if c['uuid']==played[-1]),None)
    seen=set(played)|{c['uuid'] for k in ('masterDeck','hand','drawPile','discardPile','exhaustPile') for c in pa[k]}
    if (not isinstance(source,dict) or not isinstance(copy_card,dict) or original is None
            or source.get('uuid')!=played[-1] or source.get('card_id')!='CutThroughFate'
            or source.get('exhaust') is not True or source.get('purge_on_use') is not False
            or copy_card.get('uuid')!=rb['played_card_uuids'][-1] or copy_card['uuid'] in seen
            or omit(source,{'uuid','exhaust','purge_on_use'})!=omit(copy_card,{'uuid','exhaust','purge_on_use'})
            or copy_card.get('exhaust') is not False or copy_card.get('purge_on_use') is not True
            or original['id']!='CutThroughFate' or original['timesUpgraded'] not in (0,1)
            or original['baseDamage']!=7+2*original['timesUpgraded']
            or original['baseMagicNumber']!=2+original['timesUpgraded']
            or original['uuid'] in {c['uuid'] for k in ('hand','drawPile','discardPile','exhaustPile') for c in pa[k]}):
        return False
    powers={p['id']:p['amount'] for p in pa['powers']}
    if (set(powers)-{'Confusion','Dexterity','Strength','Thorns'} or powers.get('Confusion')!=-1
            or pa.get('stance') not in (None,'None','Neutral','Calm')
            or source.get('damage')!=max(0,original['baseDamage']+powers.get('Strength',0))):
        return False
    if (cards(old['options'])!=cards(pa['drawPile'][::-1][:2]) or len(pa['drawPile'])<3
            or len(pa['hand'])>=10):return False
    streams=_streams(a);rng=Stream(streams['card_random']);cost=rng.bounded(4);rng.bounded(1)
    drawn_card=pa['drawPile'][-1]
    drawn=dict(drawn_card,index=len(pa['hand']),cost=cost,costForTurn=cost,
               damage=max(0,drawn_card['baseDamage']+powers.get('Strength',0)),
               block=max(0,drawn_card['baseBlock']+powers.get('Dexterity',0)))
    draw=pa['drawPile'][:-1]
    if cards(new['options'])!=cards(draw[::-1][:2]):return False
    relics={r['id']:r for r in pa['relics']}
    if ('Kunai' not in relics or relics['Kunai']['counter']!=2
            or 'Pen Nib' not in relics or relics['Pen Nib']['counter']!=8):return False
    expected_relics=[]
    for relic in pa['relics']:
        if relic['id']=='Kunai':relic=dict(relic,counter=0)
        elif relic['id']=='Pen Nib':relic=dict(relic,counter=9)
        expected_relics.append(relic)
    damage=source['damage'];expected_monsters=[];changed=0
    for left,right in zip(a['monsters'],b['monsters']):
        hit=dict(left,block=max(0,left['block']-damage),hp=left['hp']-max(0,damage-left['block']))
        if right==hit and right!=left:expected_monsters.append(hit);changed+=1
        elif right==left:expected_monsters.append(left)
        else:return False
    if len(a['monsters'])!=len(b['monsters']) or changed!=1:return False
    exhausted=dict(original,index=len(pa['exhaustPile']),damage=source['damage'],block=source['block'],
                   magicNumber=source['magic_number'],exhaust=True)
    expected_player=dict(pa,drawPile=draw,drawCount=pa['drawCount']-1,
                         drawPileCount=pa['drawPileCount']-1,hand=pa['hand']+[drawn],
                         handSize=pa['handSize']+1,exhaustPile=pa['exhaustPile']+[exhausted],
                         exhaustCount=pa['exhaustCount']+1,relics=expected_relics)
    return (expected_monsters==b['monsters'] and expected_player==pb
            and dict(streams,card_random=rng.state())==_streams(b))


def _exhaust_presentation_matches(old,new):
    if len(old)!=len(new):return False
    for source,actual in zip(old,new):
        if source==actual:continue
        settled=dict(source,damage=source['baseDamage'],block=source['baseBlock'],
                     magicNumber=source['baseMagicNumber'],costForTurn=source['cost'])
        if settled!=actual:return False
    return True


def _warcry_dark_embrace(before, after, uuids):
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    old,new=a['player'],b['player']
    receipt=lambda raw:{k:v for k,v in raw['combat_receipt'].items() if k!='rng'}
    if (a['phase']!='CARD_SELECT' or b['phase']!='PLAYER_TURN' or len(uuids)!=1
            or any(a[k]!=b[k] for k in ('floor','act','room','roomPhase','map','observedKeys','history'))
            or receipt(a)!=receipt(b)):
        return False
    powers={p['id']:p['amount'] for p in old['powers']}
    draws=powers.get('Dark Embrace',0)+int(any(r['id']=='InkBottle' and r['counter']==0 for r in old['relics']))
    if draws!=1:return False
    monsters=b.get('combat',{}).get('snapshot',{}).get('cur_map_node_state',{}).get('monster_data')
    if monsters is None or len(monsters)!=len(b['monsters']):
        return False
    # Selection menus omit zero-HP slots; the authoritative combat snapshot
    # keeps them, including Darklings waiting to regrow.
    prior_live=[m for m in a['monsters'] if m['hp']>0]
    after_live=[(public,saved) for public,saved in zip(b['monsters'],monsters)
                if public['hp']>0]
    if len(prior_live)!=len(after_live):return False
    for prior,(public,saved) in zip(prior_live,after_live):
        creature=saved['creature']
        if (any(prior[k]!=public[k] for k in ('id','hp','maxHp','block','intent'))
                or prior['intentDamage']!=saved['move_adjusted_damage']
                or any(prior[k]!=creature[q] for k,q in
                       (('halfDead','half_dead'),('isDying','is_dying'),('isEscaping','is_escaping')))
                or [(p['id'],p['amount']) for p in prior['powers']]
                    !=[(p['power_id'],p['amount']) for p in creature['powers']]):
            return False
    chosen=a['cardSelection']['selected']
    played=a['combat_receipt']['played_card_uuids']
    exhausted=new['exhaustPile']
    if ([c['uuid'] for c in chosen]!=uuids or len(old['hand'])>=10 or not played
            or len(exhausted)!=len(old['exhaustPile'])+1 or exhausted[-1]['id'] not in ('Warcry','Thinking Ahead')
            or exhausted[-1]['uuid']!=played[-1] or not exhausted[-1]['exhaust']):
        return False
    def cards(xs):return [{k:v for k,v in c.items() if k!='index'} for c in xs]
    changes={'hand','handSize','exhaustPile','exhaustCount'}
    expected=dict(_streams(a))
    drawn=dict(chosen[0])
    if powers.get('Confusion')==-1 and drawn['cost']>=0:
        rng=Stream(expected['card_random']);value=rng.bounded(4)
        expected['card_random']=rng.state()
        if value!=drawn['cost']:drawn.update(cost=value,costForTurn=value)
    return (expected==_streams(b) and cards(new['hand'])==cards(old['hand']+[drawn])
            and len(exhausted)==len(old['exhaustPile'])+1
            and _exhaust_presentation_matches(cards(old['exhaustPile']),cards(exhausted[:-1]))
            and new['handSize']==old['handSize']+1 and new['exhaustCount']==old['exhaustCount']+1
            and {k:v for k,v in old.items() if k not in changes}=={k:v for k,v in new.items() if k not in changes})


def _duplicate_third_eye_scry(before,after,selected):
    from .event_receipts import _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    if selected or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','phase','map','history','monsters')):
        return False
    if (a['phase'],a['roomPhase'])!=('CARD_SELECT','COMBAT') or _streams(a)!=_streams(b):return False
    ra,rb=a['combat_receipt'],b['combat_receipt'];played=ra['played_card_uuids']
    first=a['driver_receipt']; receipt=b['driver_receipt']
    if (not played or first.get('method')!='playCard' or first.get('card_uuid')!=played[-1]
            or (receipt['method'],receipt['args'],receipt['sequence'])!=('confirmGrid',[],after.sequence)
            or rb['played_card_uuids']!=played+[played[-1]]
            or any(v!=rb.get(k) for k,v in ra.items() if k not in ('rng','played_card_uuids'))):
        return False
    pa,pb=a['player'],b['player']
    source=next((c for c in pa['masterDeck'] if c['uuid']==played[-1]),None)
    if source is None or source['id']!='ThirdEye' or source['timesUpgraded'] not in (0,1):return False
    powers={p['id']:p['amount'] for p in pa['powers']}
    if powers.pop('DuplicationPower',None)!=0 or set(powers)-{'Dexterity','Frail','Strength','Thorns'}:return False
    if any(r['id'] in ('InkBottle','Pocketwatch','Velvet Choker','Letter Opener','OrangePellets') for r in pa['relics']):
        return False
    count=3+2*source['timesUpgraded']+2*any(r['id']=='GoldenEye' for r in pa['relics'])
    controls=dict(kind='grid',purpose='scry',count=count,locked=False,can_confirm=True,any_number=True,
                  can_pick_zero=True,auto_confirm=True)
    first_view,new_view=a['cardSelection'],b['cardSelection']
    if a['interaction']['selection']!=controls or b['interaction']['selection']!=controls or first_view!=new_view:
        return False
    facts=lambda xs:[(c['uuid'],c['id'],c['timesUpgraded'],c['cost'],c['misc']) for c in xs]
    if first_view['selected'] or facts(first_view['options'])!=facts(pa['drawPile'][::-1][:count]):return False
    card=pb['discardPile'][-1]
    if (played[-1] in {c['uuid'] for name in ('discardPile','exhaustPile','hand','drawPile') for c in pa[name]}
            or facts([card])!=facts([source]) or card['exhaust'] or card['costForTurn']!=card['cost']):return False
    amount=max(0,source['baseBlock']+powers.get('Dexterity',0))
    if powers.get('Frail',0)>0:amount=amount*3//4
    fields={'block','powers','discardPile','discardCount','discardPileCount','exhaustPile'}
    return (pb['block']==min(999,pa['block']+amount)
            and pb['powers']==[p for p in pa['powers'] if p['id']!='DuplicationPower']
            and pb['discardPile'][:-1]==pa['discardPile']
            and _exhaust_presentation_matches(pa['exhaustPile'],pb['exhaustPile'])
            and pb['discardCount']==pb['discardPileCount']==len(pb['discardPile'])
            and {k:v for k,v in pa.items() if k not in fields}=={k:v for k,v in pb.items() if k not in fields})


def _omniscience_scry_retargets_clean(before,after,selected):
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player']; known_slots=a['monsters']
    excluded={'Pen Nib','Shuriken','Kunai','InkBottle',
              'Pocketwatch','Velvet Choker','OrangePellets','Paper Frog','Boot','Dead Branch','Strange Spoon'}
    passive={'Confusion','Adaptation','Thorns'}
    powers={row['id']:row['amount'] for row in pa['powers']}
    if (selected or set(powers)-passive or pa['powers']!=pb['powers'] or pa['stance'] not in (None,'None','Neutral','Calm','Wrath')
            or any(r['id'] in excluded for r in pa['relics'])
            or any(m['hp']<=0 or any(p['id']!='Strength' for p in m['powers']) for m in known_slots)
            or len(pa['hand'])>=10 or not pa['drawPile'] or not known_slots
            or (a['phase'],a['roomPhase'])!=('CARD_SELECT','COMBAT')
            or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','phase','map','history','observedKeys'))):
        return False
    first,second=(raw['interaction'].get('scry_replay') for raw in (a,b))
    if not first or not second:return False
    for data,n in ((first,1),(second,0)):
        if data['remaining']!=n or data['autoplay'] is not True or data['next'] is not None:return False
    source,copy=first['source'],second['source']
    ra,rb=a['combat_receipt'],b['combat_receipt'];played=ra['played_card_uuids']
    if (not played or source['uuid']!=played[-1] or source['card_id']!='CutThroughFate' or source['purge_on_use']
            or source['exhaust'] is not True or copy['exhaust'] or copy['purge_on_use'] is not True
            or copy['uuid'] in set(played)|{c['uuid'] for k in ('masterDeck','hand','drawPile','discardPile','exhaustPile') for c in pa[k]}
            or rb['played_card_uuids']!=played+[copy['uuid']]):return False
    stable=lambda x:{k:v for k,v in x.items() if k not in ('uuid','exhaust','purge_on_use')}
    if stable(source)!=stable(copy) or source['times_upgraded'] not in (0,1):return False
    controls=dict(kind='grid',purpose='scry',count=2+source['times_upgraded'],locked=False,can_confirm=True,
                  any_number=True,can_pick_zero=True,auto_confirm=True)
    old,new=a['cardSelection'],b['cardSelection']; count=controls['count']
    facts=lambda cs:[(c['uuid'],c['id'],c['timesUpgraded'],c['cost'],c['costForTurn'],c['misc']) for c in cs]
    draw=pa['drawPile']; remainder=draw[:-1]
    expected=dict(_streams(a)); rng=Stream(expected['card_random'])
    drawn=dict(draw[-1])
    if powers.get('Confusion')==-1 and drawn['cost']>=0:
        cost=rng.bounded(4)
        if cost!=drawn['cost']:drawn.update(cost=cost,costForTurn=cost)
    if (any(raw['interaction']['selection']!=controls for raw in (a,b)) or old['selected'] or new['selected']
            or facts(old['options'])!=facts(draw[::-1][:count]) or facts(new['options'])!=facts(remainder[::-1][:count])
            or facts(pb['drawPile'])!=facts(remainder) or facts(pb['hand'])!=facts(pa['hand']+[drawn])):
        return False
    target=rng.bounded(len(known_slots))
    if not (0<=first['target']<len(known_slots) and second['target']==target):return False
    if source['damage']!=(7+source['times_upgraded']*2)*(2 if pa['stance']=='Wrath' else 1):return False
    monster=known_slots[target];damage=source['damage']
    hp=monster['hp']-max(0,damage-monster['block'])
    updated=list(known_slots)
    if hp>0:updated[target]=dict(monster,hp=hp,block=max(0,monster['block']-damage))
    else:updated.pop(target)
    if updated!=b['monsters']:return False
    driver=b['driver_receipt']
    if (driver['method'],driver['args'],driver['sequence'])!=('confirmGrid',[],after.sequence):return False
    expected['card_random']=rng.state()
    unwrap=lambda row:{k:v for k,v in row.items() if k not in ('rng','played_card_uuids','active_monsters')}
    if (expected!=_streams(b) or unwrap(ra)!=unwrap(rb) or
            (ra['active_monsters'],rb['active_monsters'])!=(len(known_slots),len(updated))):return False
    exhaust=pb['exhaustPile']
    if (len(exhaust)!=len(pa['exhaustPile'])+1 or facts(pa['exhaustPile'])!=facts(exhaust[:-1])
            or (exhaust[-1]['uuid'],exhaust[-1]['id'],exhaust[-1]['timesUpgraded'],exhaust[-1]['cost'])!=
              (source['uuid'],'CutThroughFate',source['times_upgraded'],source['cost'])):return False
    relics=[];block=pa['block'];energy=pa['energy']
    for relic in pa['relics']:
        period={'Ornamental Fan':3,'Nunchaku':10}.get(relic['id'])
        if period:
            if not 0<=relic['counter']<period:return False
            if relic['counter']==0:
                if period==3:block=min(999,block+4)
                else:energy+=1
            relic=dict(relic,counter=(relic['counter']+1)%period)
        relics.append(relic)
    fields={'hand','handSize','exhaustPile','exhaustCount','drawPile','drawCount','drawPileCount','relics','block','energy'}
    return (pb['handSize']==len(pb['hand']) and pb['exhaustCount']==len(exhaust)
            and pb['block']==block and pb['energy']==energy and pb['relics']==relics
            and pb['drawCount']==pb['drawPileCount']==len(remainder)
            and {k:v for k,v in pa.items() if k not in fields}=={k:v for k,v in pb.items() if k not in fields})


def _dagger_throw_ink_shuffle(before,after,uuids):
    """Dagger Throw discards, then the pending Ink Bottle draw can reshuffle it."""
    from .event_receipts import Stream, _shuffle, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    old,new=a['player'],b['player']
    ra,rb=a['combat_receipt'],b['combat_receipt']
    chosen=a['cardSelection']['selected']
    if (a['phase']!='CARD_SELECT' or b['phase']!='PLAYER_TURN' or a['roomPhase']!='COMBAT'
            or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','map','observedKeys','history'))
            or len(chosen)!=1 or [c['uuid'] for c in chosen]!=uuids or old['drawPile']
            or not any(r['id']=='InkBottle' and r['counter']==0 for r in old['relics'])
            or not ra['played_card_uuids'] or len(new['discardPile'])!=1):
        return False
    source=new['discardPile'][0]
    if source['id']!='Dagger Throw' or source['uuid']!=ra['played_card_uuids'][-1]:
        return False
    streams=_streams(a)
    shuffle=Stream(streams['shuffle'])
    pile=_shuffle(shuffle.long(),old['discardPile']+chosen)
    drawn=pile.pop()
    # Preserve identity and permanent card properties. Draw/hand refresh
    # recomputes transient damage and Eviscerate's discard discount.
    def facts(cards):return [(c['uuid'],c['id'],c['timesUpgraded'],c['misc'],c['cost']) for c in cards]
    changes={'hand','handSize','drawPile','drawCount','drawPileCount','discardPile','discardCount','discardPileCount'}
    return (dict(streams,shuffle=shuffle.state())==_streams(b)
            and {k:v for k,v in ra.items() if k!='rng'}=={k:v for k,v in rb.items() if k!='rng'}
            and {k:v for k,v in old.items() if k not in changes}=={k:v for k,v in new.items() if k not in changes}
            and facts(new['hand'])==facts(old['hand']+[drawn]) and facts(new['drawPile'])==facts(pile)
            and new['handSize']==len(old['hand'])+1 and new['drawCount']==new['drawPileCount']==len(pile)
            and new['discardCount']==new['discardPileCount']==1)


def _acrobatics_reflex_shuffle(before,after,selected):
    from .event_receipts import Stream, _shuffle, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    pa,pb=a['player'],b['player'];ra,rb=a['combat_receipt'],b['combat_receipt']
    picked=a['cardSelection']['selected']
    if (len(picked)!=1 or [c['uuid'] for c in picked]!=selected or picked[0]['id']!='Reflex'
            or picked[0]['timesUpgraded'] not in (0,1) or a['phase']!='CARD_SELECT' or b['phase']!='PLAYER_TURN'
            or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','map','history','observedKeys'))
            or any(v!=rb.get(k) for k,v in ra.items() if k!='rng') or not ra['played_card_uuids']):
        return False
    powers={p['id']:p['amount'] for p in pa['powers']}
    relics={r['id'] for r in pa['relics']}
    if ('Confusion' in powers or 'No Draw' in powers or relics & {'Tingsha','Tough Bandages','Sundial','TheAbacus'}
            or any(r['id']=='InkBottle' and r['counter']==0 for r in pa['relics'])):return False
    source_id=ra['played_card_uuids'][-1]
    source=next((c for c in pa['masterDeck'] if c['uuid']==source_id),None)
    if source is None or source['id']!='Acrobatics':return False
    active={c['uuid'] for k in ('hand','drawPile','discardPile','exhaustPile') for c in pa[k]}
    if source_id in active or picked[0]['uuid'] in active:return False
    count=min(2+picked[0]['timesUpgraded'],10-len(pa['hand']))
    if not 0<len(pa['drawPile'])<count:return False
    expected=dict(_streams(a));rng=Stream(expected['shuffle'])
    pile=_shuffle(rng.long(),pa['discardPile']+picked+[source]);expected['shuffle']=rng.state()
    draw=count-len(pa['drawPile'])
    if len(pile)<draw:return False
    hand=pa['hand']+pa['drawPile'][::-1]+pile[-draw:][::-1]
    pile=pile[:-draw]
    facts=lambda xs:[(c['uuid'],c['id'],c['timesUpgraded'],c['cost'],c['misc']) for c in xs]
    return (expected==_streams(b) and not pb['discardPile'] and pb['discardCount']==pb['discardPileCount']==0
            and [(m['id'],m['hp'],m['maxHp'],m['block']) for m in a['monsters'] if m['hp']>0]==
                [(m['id'],m['hp'],m['maxHp'],m['block']) for m in b['monsters'] if m['hp']>0]
            and facts(pb['hand'])==facts(hand) and facts(pb['drawPile'])==facts(pile)
            and pb['block']==min(999,pa['block']+powers.get('After Image',0))
            and pb['handSize']==len(hand) and pb['drawCount']==pb['drawPileCount']==len(pile)
            and all(pa[k]==pb[k] for k in pa.keys()-{'block','hand','drawPile','discardPile','handSize','drawCount',
                                                     'drawPileCount','discardCount','discardPileCount'}))


def _discovery_with_cleanup(before, after, evidence):
    """A played Discovery is still in use while the reward picker is open."""
    from .event_receipts import Stream, _streams
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    controls=a['interaction']['card_reward']
    second_picker=b['phase']=='CARD_PICK'
    if (controls.get('source_action')!='DiscoveryAction' or evidence['copies']!=1
            or evidence['free_for_turn'] is not True or evidence['destination']!='hand'
            or b['phase'] not in ('PLAYER_TURN','CARD_PICK')
            or any(a[k]!=b[k] for k in ('seed','floor','act','room','roomPhase','map','history','observedKeys'))):
        return False
    left,right=before.state,after.state
    if (left.room_phase!='COMBAT' or left.player!=right.player or left.deck!=right.deck
            or left.relics!=right.relics or left.potions!=right.potions):
        return False
    def living(rows):
        return [(m.id,m.current_hp,m.max_hp,m.block) for m in rows if m.current_hp>0]
    expected_monsters=living(left.monsters)
    if any(r.id=='Letter Opener' and r.counter==0 for r in left.relics):
        if not expected_monsters or any(m['hp']<=5 or any(p['id'] not in ('Strength','Weakened','Vulnerable','Artifact','Poison')
                for p in m['powers']) for m in a['monsters'] if m['hp']>0):return False
        expected_monsters=[(identity,hp-max(0,5-block),max_hp,max(0,block-5))
                          for identity,hp,max_hp,block in expected_monsters]
    if expected_monsters!=living(right.monsters):return False
    driver=a['driver_receipt'];played=a['combat_receipt']['played_card_uuids']
    uid=driver.get('card_uuid')
    if driver.get('method')!='playCard' or not uid or not played or played[-1]!=uid:
        return False
    old,new=a['player'],b['player']
    pile_keys=('hand','drawPile','discardPile','exhaustPile')
    known={c['uuid'] for key in (*pile_keys,'masterDeck') for c in old[key]}
    active={c['uuid'] for key in pile_keys for c in old[key]}
    offers={c['uuid'] for c in a['cardRewardOptions']}
    gained=[(key,c) for key in pile_keys for c in new[key] if c['uuid'] not in active]
    hex_power=next((p for p in old.get('powers',[]) if p['id']=='Hex'),None)
    dazed=[(key,c) for key,c in gained if c['id']=='Dazed']
    if len(gained)!=2+int(hex_power is not None) or uid in active:
        return False
    if hex_power is not None:
        if (hex_power.get('amount')!=1 or len(dazed)!=1 or dazed[0][0]!='drawPile'
                or dazed[0][1]['uuid'] in known|offers):
            return False
        status=dazed[0][1]
        if any(status.get(k)!=v for k,v in {
                'id':'Dazed','cost':-2,'costForTurn':-2,'type':'STATUS','rarity':'COMMON',
                'target':'NONE','timesUpgraded':0,'upgraded':False,'misc':0,'bottled':False,
                'canUpgrade':False,'baseDamage':-1,'baseBlock':-1,'baseMagicNumber':-1,
                'damage':-1,'block':-1,'magicNumber':-1,'exhaust':False}.items()):
            return False
    elif dazed:
        return False
    cleanups=[(key,c) for key,c in gained if c['uuid']==uid and c['id']=='Discovery']
    if len(cleanups)!=1:
        return False
    cleanup_key,cleanup=cleanups[0]
    # Without Spoon/Corruption, base Discovery exhausts and Discovery+ discards.
    if (cleanup['timesUpgraded'] not in (0,1) or
            cleanup_key!=('discardPile' if cleanup['timesUpgraded'] else 'exhaustPile')
            or cleanup['uuid']!=new[cleanup_key][-1]['uuid']):
        return False
    destination='hand' if len(old['hand'])<10 else 'discardPile'
    taken_rows=[(key,c) for key,c in gained if c['uuid']!=uid and c['id']!='Dazed']
    if len(taken_rows)!=1:return False
    taken_key,taken=taken_rows[0]
    offered=evidence['card']
    perfected=any(p['id']=='MasterRealityPower' for p in old.get('powers',[]))
    if (taken_key!=destination or taken['uuid'] in known|offers
            or taken['id']!=offered['id'] or taken['timesUpgraded']!=max(int(perfected),offered['upgrades'])
            or taken['costForTurn']!=min(0,offered['cost_for_turn'])):
        return False
    def facts(cards):return [(c['uuid'],c['id'],c['timesUpgraded'],c['cost'],c['costForTurn'],c['misc']) for c in cards]
    if any(facts(old[key])!=facts([c for c in new[key] if c['uuid'] in active]) for key in pile_keys):
        return False
    ra,rb=a['combat_receipt'],b['combat_receipt']
    receipt_keys={'rng','played_card_uuids'} if second_picker else {'rng'}
    if ({k:v for k,v in ra.items() if k not in receipt_keys}!=
            {k:v for k,v in rb.items() if k not in receipt_keys}
            or second_picker and rb['played_card_uuids']!=ra['played_card_uuids']+[uid]):return False
    if second_picker:
        from .event_receipts import _SILENT_SRC
        if before.state.character!='THE_SILENT':return False
        pool=[]
        for tier in ('common','uncommon','rare'):pool.extend(_SILENT_SRC[tier].split('|'))
        pool.remove('Venomology')  # HEALING-tagged cards are absent from srcCardPool.
    else:
        snap=b['combat']['snapshot'];pool=snap['_combat_card_pools']['any']
        if snap['_headless_rng']!=rb['rng']:return False
    if len(set(pool))<3 or not {c['id'] for c in a['cardRewardOptions']}<=set(pool):return False
    streams=_streams(a);rng=Stream(streams['card_random'])
    # At 60 virtual Hz the Java .25F action rerolls 15 more sets of
    # three unique candidates after the opening tick (including duplicates).
    final=[]
    for _ in range(16 if second_picker else 15):
        ids=[]
        for _ in range(10000):
            identity=pool[rng.bounded(len(pool))]
            if identity not in ids:ids.append(identity)
            if len(ids)==3:break
        else:return False
        final=ids
    if hex_power is not None:
        position=rng.bounded(len(old['drawPile']))
        if new['drawPile'][position]['uuid']!=status['uuid']:return False
    if second_picker:
        reward=b['interaction'].get('card_reward',{});options=b.get('cardRewardOptions',[])
        fresh={c['uuid'] for key in (*pile_keys,'masterDeck') for c in new[key]}|offers
        if (reward.get('source_action')!='DiscoveryAction' or reward.get('copies')!=1
                or reward.get('free_for_turn') is not True or reward.get('skip') is not False
                or [c.get('id') for c in options]!=final or len({c.get('uuid') for c in options})!=3
                or any(not c.get('uuid') or c['uuid'] in fresh for c in options)):return False
    return dict(streams,card_random=rng.state())==_streams(b)


def _meditate_finished(before, after, targets):
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    old=a['player'];new=b['player']
    room_keys=('floor','act','room','roomPhase','map')
    # MeditateAction moves only the chosen instances into the hand, retaining
    # those that fit; ChangeStanceAction and PressEndTurnButtonAction then run.
    moved=targets[:max(0,10-len(old['hand']))]
    hand=[c['uuid'] for c in new['hand']]
    return (len(targets)==len(set(targets))==a['interaction']['selection']['count']
            and set(targets)<={c['uuid'] for c in old['discardPile']}
            and b['phase']=='PLAYER_TURN' and all(a[k]==b[k] for k in room_keys)
            and b['combat_receipt']['turn']==a['combat_receipt']['turn']+1 and new['stance']=='Calm'
            and all(hand.count(u)==1 for u in moved)
            and not set(moved)&{c['uuid'] for k in ('drawPile','discardPile','exhaustPile') for c in new[k]}
            and before.state.deck==after.state.deck)


def _source_event_transition(event, button, declared):
    """Pinned-source stage facts, including traces exported before these annotations."""
    stages = {('Bonfire Elementals','Bonfire','INTRO',0): {'phases':['CHOOSE']},
              ('Bonfire Elementals','Bonfire','COMPLETE',0): {'screens':['MAP']},
              ('Knowing Skull','KnowingSkull','INTRO_1',0): {'phases':['ASK']},
              ('Knowing Skull','KnowingSkull','ASK',3): {'phases':['COMPLETE']},
              ('Knowing Skull','KnowingSkull','COMPLETE',0): {'screens':['MAP']},
              ('Duplicator','Duplicator','0',1): {'phases':['2']},
              ('Duplicator','Duplicator','2',0): {'screens':['MAP']}}
    known = stages.get((event.id,event.class_name,event.phase,button))
    if known is None:return declared
    if declared and declared != known:raise HeadlessContractError('Event transition contradicts pinned source')
    return known


def _bonfire_offer(before, after, uuid):
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    left,right=before.state,after.state
    selection=a.get('interaction',{}).get('selection',{})
    event=b.get('interaction',{}).get('event',{})
    if (a.get('room')!=b.get('room') or a.get('room')!='EventRoom'
            or a.get('interaction',{}).get('flow',{}).get('event_class')!='Bonfire'
            or selection.get('purpose')!='bonfire_offer' or selection.get('count')!=1
            or selection.get('source_event_phase')!='CHOOSE'
            or event.get('id')!='Bonfire Elementals' or event.get('class_name')!='Bonfire' or event.get('phase')!='COMPLETE'
            or not _selection_committed(left,right,'purge',[uuid])):return False
    target=_deck(left)[uuid]
    def card_facts(card):return card.model_dump(exclude={'index','choice_index'})
    if any(card_facts(c)!=card_facts(_deck(right)[c.uuid]) for c in left.deck if c.uuid!=uuid):return False
    if target.is_bottled or target.id in {'AscendersBane','CurseOfTheBell','Necronomicurse'}:return False
    hp,maximum=left.player.current_hp,left.player.max_hp
    bloom=any(r.id=='Mark of the Bloom' for r in left.relics)
    if target.rarity=='RARE':maximum+=10;hp=hp if bloom else maximum
    elif target.rarity=='UNCOMMON' and not bloom:hp=maximum
    elif target.rarity in ('COMMON','SPECIAL') and not bloom:hp=min(maximum,hp+5)
    expected=left.player.model_copy(update={'current_hp':hp,'max_hp':maximum})
    if right.player!=expected or left.potions!=right.potions:return False
    if target.rarity!='CURSE':return right.relics==left.relics
    identity='Circlet' if any(r.id=='Spirit Poop' for r in left.relics) else 'Spirit Poop'
    if identity=='Circlet' and any(r.id=='Circlet' for r in left.relics):
        relics=[r.model_copy(deep=True) for r in left.relics]
        next(r for r in relics if r.id=='Circlet').counter+=1
        return right.relics==relics
    return (len(right.relics)==len(left.relics)+1 and right.relics[:-1]==left.relics
            and right.relics[-1].id==identity)


def _combat_upgrade(before, after, uuids):
    def groups(state):
        cards = state.hand + state.draw_pile + state.discard_pile + state.exhaust_pile + state.grid_selected_cards
        return {c.uuid: c for c in cards}
    left, right = groups(before), groups(after)
    return bool(uuids) and before.room_phase == after.room_phase == "COMBAT" and before.deck == after.deck and all(
        u in left and u in right and left[u].id == right[u].id and right[u].upgrades == left[u].upgrades + 1 for u in uuids)


def _combat_move(before, after, purpose, uuids, copies):
    if before.room_phase != "COMBAT" or after.room_phase != "COMBAT" or before.deck != after.deck or len(uuids) != 1:
        return False
    target = uuids[0]
    if purpose == "topdeck":
        return bool(after.draw_pile) and after.draw_pile[-1].uuid == target and target not in {c.uuid for c in after.hand + after.discard_pile}
    if purpose == "retrieve_exhaust":
        return target in {c.uuid for c in after.hand} and target not in {c.uuid for c in after.exhaust_pile}
    if purpose == "duplicate_combat":
        originals = before.hand + before.draw_pile + before.discard_pile + before.exhaust_pile + before.grid_cards + before.grid_selected_cards + before.deck
        original = next((c for c in originals if c.uuid == target), None)
        old = {c.uuid for c in originals}
        added = [c for c in after.hand if c.uuid not in old and original is not None and c.reference == original.reference]
        return len(added) == copies and target in {c.uuid for c in after.hand}
    return False


def _effects_match(before, after, effects, followup, transition, *, native_combat_handoff=False):
    # Pure resource rewards/costs do not also grant or mutate inventory.
    if all(e['type'] in {'gain_gold','lose_gold','lose_all_gold','gain_hp','full_heal','gain_max_hp','lose_max_hp'} for e in effects):
        if before.deck != after.deck or before.relics != after.relics or before.potions != after.potions:
            return False
    gold, hp, maximum = before.player.gold, before.player.current_hp, before.player.max_hp
    evidence, unknown = False, False
    ids = {r.id for r in before.relics}; potions = [p.id for p in before.potions]; revivals = {}; consumed = False; defeated = False
    health_relics = {'Strawberry':7,'Pear':10,'Mango':14}
    bloom = 'Mark of the Bloom' in ids
    def gain_max_health(amount):
        return maximum+amount,hp+(0 if bloom else amount)
    for effect in effects:
        kind, amount = effect["type"], effect.get("amount", 0)
        if kind == "gain_gold":
            if 'Ectoplasm' not in ids:
                gold+=amount
                if amount>0 and 'Bloody Idol' in ids and not bloom:hp=min(maximum,hp+5)
            evidence=True
        elif kind == "lose_gold": gold = max(0, gold - amount); evidence = True
        elif kind == "lose_all_gold": gold = 0; evidence = True
        elif kind == "gain_hp": hp = min(maximum, hp + (0 if any(r.id == "Mark of the Bloom" for r in before.relics) else amount)); evidence = True
        elif kind == "lose_hp":
            hp = max(0, hp - max(0, amount-int("TungstenRod" in ids))); evidence = True
            if hp == 0:
                fairy = next((i for i, identity in enumerate(potions) if identity == "FairyPotion"), None)
                tail = next((r for r in before.relics if r.id == "Lizard Tail" and r.counter == -1 and r.id not in revivals), None)
                if "Mark of the Bloom" not in ids and fairy is not None:
                    hp = int(maximum*(.6 if "SacredBark" in ids else .3)); potions[fairy] = "Potion Slot"; consumed = True
                elif "Mark of the Bloom" not in ids and tail is not None:
                    hp = max(1, maximum//2); revivals[tail.id] = -2
                else:
                    defeated = True; break  # A proven fatal HP loss ends the trajectory, before any future reward.
        elif kind == "gain_max_hp": maximum += amount; hp = min(maximum, hp + amount); evidence = True
        elif kind == "lose_max_hp": maximum = max(1, maximum - amount); hp = min(hp, maximum); evidence = True
        elif kind == "full_heal": hp = before.player.current_hp if any(r.id == "Mark of the Bloom" for r in before.relics) else maximum; evidence = True
        elif kind in ("select_cards", "target_rule"):
            if after.screen_type not in ("GRID", "HAND_SELECT"): return False
            if effect.get("purpose") and after.grid_purpose != effect["purpose"].lower(): return False
            if effect.get("count") is not None and after.grid_num_cards != effect["count"]: return False
            evidence = True
        elif kind == "obtain_relic":
            identity = effect.get("relic_id") or effect.get("id")
            if Counter(r.id for r in after.relics)[identity] <= Counter(r.id for r in before.relics)[identity]: return False
            if identity in health_relics:maximum,hp=gain_max_health(health_relics[identity])
            evidence = True
        elif kind == "random_relic":
            gained = Counter(r.id for r in after.relics) - Counter(r.id for r in before.relics)
            if sum(gained.values()) < effect.get("count", 1): return False
            if sum(gained.values())==effect.get("count",1):
                maximum,hp=gain_max_health(sum(health_relics.get(identity,0)*n for identity,n in gained.items()))
            evidence = True
        elif kind == "relic_reward":
            offers = [r.relic_id for r in after.reward_choices if r.type == "RELIC"]
            eligible = {"Necronomicon", "Enchiridion", "Nilry's Codex"}-ids
            if not eligible: eligible = {"Circlet"}
            if effect.get("pool") != "BOOK" or len(offers) != 1 or offers[0] not in eligible or after.relics != before.relics: return False
            evidence = True
        elif kind == "lose_relic":
            identity = effect.get("relic_id") or effect.get("id")
            if Counter(r.id for r in before.relics)[identity] - Counter(r.id for r in after.relics)[identity] != 1: return False
            evidence = True
        elif kind in ("random_upgrade", "upgrade_all_cards", "upgrade_matching"):
            if followup == "GRID" and after.screen_type == "GRID": continue
            left, right = _deck(before), _deck(after)
            if set(left) != set(right) or any(left[u].id != right[u].id for u in left): return False
            matching = set(effect['card_ids']) if 'card_ids' in effect else {'Strike_R','Defend_R'}
            eligible = {u for u, c in left.items() if c.can_upgrade and (kind != "upgrade_matching" or c.id in matching)}
            changed = {u for u in left if right[u].upgrades != left[u].upgrades}
            expected = min(effect.get("count", effect.get("amount", 1)), len(eligible)) if kind == "random_upgrade" else len(eligible)
            if not changed <= eligible or len(changed) != expected or any(right[u].upgrades != left[u].upgrades + 1 for u in changed): return False
            if kind == 'upgrade_matching':
                if any(right[u]!=left[u] for u in left if u not in changed): return False
                immutable=('index','uuid','id','type','target','rarity','is_bottled','misc')
                if any(getattr(right[u],k)!=getattr(left[u],k) for u in changed for k in immutable): return False
                if before.relics!=after.relics or before.potions!=after.potions: return False
            evidence = bool(changed)
        elif kind == "add_card":
            identity = effect.get("card_id")
            count=effect.get("count",effect.get("amount",1))
            if Counter(c.id for c in after.deck)[identity] - Counter(c.id for c in before.deck)[identity] != count:return False
            known=_deck(before)
            obtained=[c for c in after.deck if c.uuid not in known and c.id==identity]
            if len(obtained)!=count:return False
            if 'Darkstone Periapt' in ids:
                maximum,hp=gain_max_health(6*sum(c.type=='CURSE' for c in obtained))
            if count and 'CeramicFish' in ids and 'Ectoplasm' not in ids:
                gold+=9*count
                if 'Bloody Idol' in ids and not bloom:hp=min(maximum,hp+5*count)
            evidence = True
        elif kind == "start_combat":
            if after.room_phase != "COMBAT": return False
            evidence = True
        elif kind in ("random_card_reward", "colorless_card_reward"):
            if after.screen_type != "CARD_REWARD": return False
            evidence = True
        elif kind == "random_potion":
            gained = sum(not p.is_empty for p in after.potions) - sum(not p.is_empty for p in before.potions)
            if after.screen_type != "COMBAT_REWARD" and gained <= 0: return False
            evidence = True
        elif kind == "random_card":
            added = [c for c in after.deck if c.uuid not in _deck(before)]
            if len(added) != effect.get("count", 1): return False
            if effect.get("pool") == "RARE" and any(c.rarity != "RARE" for c in added): return False
            if effect.get('pool') == 'COLORLESS_UNCOMMON':
                # Pinned source: KnowingSkull.obtainReward(2) draws one uncommon
                # colorless card and adds it, including any egg on-obtain upgrade.
                eligible={'Bandage Up','Blind','Dark Shackles','Deep Breath','Discovery','Dramatic Entrance',
                          'Enlightenment','Finesse','Flash of Steel','Forethought','Good Instincts','Impatience',
                          'Jack Of All Trades','Madness','Mind Blast','Panacea','PanicButton','Purity','Swift Strike','Trip'}
                left,right=_deck(before),_deck(after)
                if (before.event is None or after.event is None or before.event.id!='Knowing Skull'
                        or before.event.class_name!='KnowingSkull' or before.event.phase!='ASK'
                        or after.event.id!=before.event.id or after.event.class_name!=before.event.class_name
                        or after.event.phase!='ASK' or after.screen_type!='EVENT'
                        or not left.keys()<=right.keys() or any(left[u]!=right[u] for u in left)
                        or before.relics!=after.relics or before.potions!=after.potions): return False
                c=added[0]
                if (c.id not in eligible or ('card_id' in effect and c.id != effect['card_id'])
                        or c.rarity!='UNCOMMON' or c.is_bottled
                        or c.upgrades!=_acquired_upgrades(before,0,c.type)): return False
                if "CeramicFish" in ids and "Ectoplasm" not in ids:
                    gold += 9
                    if "Bloody Idol" in ids and "Mark of the Bloom" not in ids:
                        hp = min(maximum, hp + 5)
            evidence = True
        elif kind == 'random_curse':
            left,right=_deck(before),_deck(after)
            added=[right[u] for u in right.keys()-left.keys()]
            if (not left.keys()<=right.keys() or any(left[u]!=right[u] for u in left)
                    or len(added)!=effect.get('count',1)
                    or any(c.type!='CURSE' or c.rarity!='CURSE' or c.upgrades!=0 or c.is_bottled for c in added)
                    or before.relics!=after.relics or before.potions!=after.potions):return False
            evidence=True
        elif kind == "random_transform" and followup == "GRID":
            if after.screen_type != "GRID": return False
            evidence = True  # Target selection opened; the transformation itself is a later action.
        elif kind in ("commit_reading", "commit_encounter"):
            # Commitment stages change controls/encounter setup, not owned inventory.
            if before.deck!=after.deck or before.relics!=after.relics or before.potions!=after.potions: return False
        else: unknown = True
    if (after.player.gold, after.player.current_hp, after.player.max_hp) != (gold, hp, maximum): return False
    if consumed and [p.id for p in after.potions] != potions: return False
    if any(next((r.counter for r in after.relics if r.id == identity), None) != value for identity, value in revivals.items()): return False
    if defeated: return after.screen_type == "GAME_OVER" and after.game_over_reason == "defeat"
    if not effects and (after.deck != before.deck or after.relics != before.relics or after.potions != before.potions): return False
    required = {"GRID": {"GRID"}, "HAND_SELECT": {"HAND_SELECT"}, "CARD_REWARD": {"CARD_REWARD"},
                "COMBAT_REWARD": {"COMBAT_REWARD"}, "COMBAT": {"NONE"}, "MAP": {"MAP"}}
    if native_combat_handoff:required['COMBAT'].add('HAND_SELECT')
    declared_screen = after.screen_type in transition.get("screens", [])
    if followup in required and after.screen_type not in required[followup] and not declared_screen: return False
    landing = ((after.event is not None and before.event is not None and after.event.id == before.event.id
                and after.event.phase in transition.get("phases", []) and after.event.phase != before.event.phase)
               or after.screen_type in transition.get("screens", []))
    # Resource no-ops and changed labels/menus are not execution evidence.
    resource_delta = (gold, hp, maximum) != (before.player.gold, before.player.current_hp, before.player.max_hp)
    concrete = evidence and (resource_delta or any(e["type"] not in {
        "gain_gold", "lose_gold", "lose_all_gold", "gain_hp", "lose_hp", "gain_max_hp", "lose_max_hp", "full_heal"} for e in effects))
    if followup in required:
        landing = landing or after.screen_type in required[followup]
    if transition and not landing: return False
    return not unknown and (concrete or landing)


def _acquired_upgrades(before, upgrades, card_type):
    eggs = {"ATTACK": "Molten Egg 2", "SKILL": "Toxic Egg 2", "POWER": "Frozen Egg 2"}
    return upgrades + int(upgrades == 0 and any(r.id == eggs.get(card_type) for r in before.relics))


def _wheel_effect(before, after, facts):
    """Bind source control stages and separately check pre-award and claim effects."""
    left, right = before.state, after.state
    raw = json.loads(after.raw_json)
    try: observed = _wheel_facts(raw.get('interaction', {}))
    except HeadlessContractError: return False
    if left.event is None or left.event.class_name != 'GremlinWheelGame': return False
    stage = facts['stage']
    relics={r.id for r in left.relics}
    darkstone_claim=(stage=='COMPLETE' and facts['result']==3 and 'Darkstone Periapt' in relics
                     and 'Omamori' not in relics and 'Mark of the Bloom' not in relics)
    if darkstone_claim:
        # The wheel UI recomputes this preview from live max HP even after
        # claiming a different result. Bind both values to the source formula.
        percent=15 if left.ascension_level>=15 else 10
        if (facts['hp_loss'],observed['hp_loss'])!=(left.player.max_hp*percent//100,right.player.max_hp*percent//100):return False
    elif observed['hp_loss']!=facts['hp_loss']:return False
    if observed['gold_amount']!=facts['gold_amount']:return False
    if stage not in ('INTRO','SPIN') and observed.get('result') != facts['result']: return False
    if (left.player.max_hp+(6 if darkstone_claim else 0),left.player.block,left.player.energy)!=(right.player.max_hp,right.player.block,right.player.energy): return False
    same_inventory = left.deck == right.deck and left.relics == right.relics and left.potions == right.potions
    if stage == 'LEAVE':
        return (observed['stage']=='LEAVE' and right.screen_type=='MAP' and same_inventory
                and left.player==right.player
                and raw.get('interaction',{}).get('flow',{}).get('event_class')=='GremlinWheelGame')
    if stage == 'INTRO':
        return (observed['stage']=='SPIN' and right.event is not None
                and right.event.class_name=='GremlinWheelGame' and right.event.phase=='SPIN'
                and same_inventory and left.player==right.player)
    if stage == 'SPIN':
        effects = [{'type':'gain_gold','amount':facts['gold_amount']}] if observed.get('result')==0 else []
        return (observed['stage']=='COMPLETE' and same_inventory
                and _effects_match(left,right,effects,'EVENT',{'phases':['COMPLETE']}))
    if stage != 'COMPLETE' or observed['stage'] != 'LEAVE': return False
    result = facts['result']
    if result == 1:
        rows = raw.get('interaction',{}).get('rewards',[])
        return (right.screen_type=='COMBAT_REWARD' and right.room_phase=='COMPLETE'
                and same_inventory and left.player==right.player and len(rows)==1
                and rows[0].get('type')=='RELIC' and rows[0].get('native_index')==0
                and isinstance(rows[0].get('relic_id'),str) and bool(rows[0]['relic_id']))
    effects = []
    followup, transition = 'EVENT', {'phases':['LEAVE']}
    if result == 2: effects = [{'type':'full_heal'}]
    elif result == 3:
        effects = [{'type':'add_card','card_id':'Decay','amount':1}]
        if darkstone_claim:effects.append({'type':'gain_max_hp','amount':6})
    elif result == 4 and facts['purge_available']:
        effects = [{'type':'select_cards','purpose':'PURGE','count':1}]
        followup, transition = 'GRID', {'screens':['GRID']}
    elif result == 5: effects = [{'type':'lose_hp','amount':facts['hp_loss']}]
    if not _effects_match(left,right,effects,followup,transition): return False
    if result == 3:
        old, new = _deck(left), _deck(right)
        added = [c for c in right.deck if c.uuid not in old]
        return (set(old)<=set(new) and all(new[u]==c for u,c in old.items()) and len(added)==1
                and added[0].id=='Decay' and added[0].upgrades==0
                and left.relics==right.relics and left.potions==right.potions)
    if result == 5:
        # The shared HP-loss checker validates revival amounts/counters. Preserve all
        # unrelated inventory while allowing only the consumed fairy/tail ledger.
        if left.deck != right.deck: return False
        if len(left.potions)!=len(right.potions) or len(left.relics)!=len(right.relics): return False
        for a,b in zip(left.potions,right.potions):
            if a!=b and not (a.id=='FairyPotion' and b.is_empty): return False
        for a,b in zip(left.relics,right.relics):
            if a!=b and not (a.id==b.id=='Lizard Tail' and a.counter==-1 and b.counter==-2): return False
        return True
    return same_inventory


def _we_meet_effect(before,after,receipt):
    """One source-selected cost, one actually drawn relic, and no unrelated changes."""
    left,right=before.state,after.state;raw=json.loads(after.raw_json)
    try: facts=_we_meet_facts(raw.get('interaction',{}))
    except HeadlessContractError:return False
    if left.event is None or left.event.class_name!='WeMeetAgain':return False
    if receipt['phase']=='COMPLETE':
        return (raw.get('phase')=='MAP' and facts['phase']=='COMPLETE'
                and facts['reward_relic']==receipt['reward_relic']
                and raw.get('interaction',{}).get('flow',{}).get('event_class')=='WeMeetAgain'
                and left.player==right.player and left.deck==right.deck
                and left.relics==right.relics and left.potions==right.potions)
    if (facts['phase']!='COMPLETE' or right.event is None or right.event.id!='WeMeetAgain'
            or right.event.class_name!='WeMeetAgain' or right.event.phase!='COMPLETE'
            or raw.get('phase')!='EVENT'):
        return False
    for name in ('hand','draw_pile','discard_pile','exhaust_pile'):
        if getattr(left,name)!=getattr(right,name):return False
    button=receipt['button'];cost=receipt.get('cost',{})
    # Reindexing after removal is expected; every surviving card keeps all other facts.
    def deck(cards):return [c.model_dump(exclude={'index','choice_index'}) for c in cards]
    expected_deck=[c for c in left.deck if not (button==2 and c.uuid==cost['uuid'])]
    reward=facts['reward_relic']
    upgrade_type={'War Paint':'SKILL','Whetstone':'ATTACK'}.get(reward['id']) if reward else None
    if upgrade_type:
        from .event_receipts import Stream, _shuffle, _streams
        try:
            streams=dict(_streams(json.loads(before.raw_json)))
            successor_streams=_streams(raw)
        except (KeyError,TypeError,ValueError):return False
        misc=Stream(streams['misc']);relic_rng=Stream(streams['relic'])
        relic_rng.bounded(100)
        eligible=[c.uuid for c in expected_deck if c.can_upgrade and c.type==upgrade_type]
        chosen=set(_shuffle(misc.long(),eligible)[:2])
        if dict(streams,misc=misc.state(),relic=relic_rng.state())!=successor_streams:return False
        if len(expected_deck)!=len(right.deck):return False
        immutable=('uuid','id','type','rarity','target','misc','is_bottled')
        for old_card,new_card in zip(expected_deck,right.deck,strict=True):
            if old_card.uuid not in chosen:
                if deck([old_card])!=deck([new_card]):return False
            elif (new_card.upgrades!=old_card.upgrades+1 or
                    any(getattr(old_card,k)!=getattr(new_card,k) for k in immutable)):return False
    elif deck(right.deck)!=deck(expected_deck):return False
    extra_slots=2 if reward is not None and reward['id']=='Potion Belt' else 0
    if len(right.potions)!=len(left.potions)+extra_slots:return False
    def empty_slot(potion,i):
        return potion.is_empty and potion.id=='Potion Slot' and potion.index==i and not potion.requires_target
    for i,(a,b) in enumerate(zip(left.potions,right.potions)):
        if button==0 and i==cost['slot']:
            if not empty_slot(b,i):return False
        elif a!=b:return False
    if not all(empty_slot(right.potions[i],i) for i in range(len(left.potions),len(right.potions))):return False
    if button==3:
        return reward is None and left.player==right.player and left.relics==right.relics
    if reward is None:return False
    identity=reward['id'];old=[r.id for r in left.relics]
    if identity=='Potion Belt':
        # Source draws one relic tier; Potion Belt has no gameplay RNG onEquip.
        from .event_receipts import Stream, _streams
        try:
            streams=_streams(json.loads(before.raw_json))
            relic_rng=Stream(streams['relic']);relic_rng.bounded(100)
            if dict(streams,relic=relic_rng.state())!=_streams(raw):return False
        except (KeyError,TypeError,ValueError):return False
    if identity=='Circlet' and identity in old:
        expected=[r.model_copy(deep=True) for r in left.relics]
        expected[old.index('Circlet')].counter+=1
        if right.relics!=expected:return False
    elif (identity in old or len(right.relics)!=len(left.relics)+1 or right.relics[:-1]!=left.relics
            or right.relics[-1].id!=identity or right.relics[-1].counter!=reward['counter']):
        return False
    # Inventory-neutral rewards need no guessed onEquip model. These source-proven
    # resource effects cover common pickups; any other resource/selection effect stops.
    gold=left.player.gold-(cost['amount'] if button==1 else 0)
    maximum,hp=left.player.max_hp,left.player.current_hp
    if identity=='Old Coin' and 'Ectoplasm' not in old:
        gold+=300
        if 'Bloody Idol' in old and 'Mark of the Bloom' not in old:hp=min(maximum,hp+5)
    hp_gain={'Strawberry':7,'Pear':10,'Mango':14,"Lee's Waffle":7}.get(identity,0)
    if hp_gain:
        maximum+=hp_gain
        if 'Mark of the Bloom' not in old:
            hp=maximum if identity=="Lee's Waffle" else min(maximum,hp+hp_gain)
    expected_player=left.player.model_copy(update={'gold':gold,'max_hp':maximum,'current_hp':hp})
    return right.player==expected_player


def _closed_forced_event(before, after, option):
    """Prove a known, effect-free dialog closed this same event into its map."""
    left, right = before.state, after.state
    if (left.event is None or left.event.semantics_status != "KNOWN" or left.event.decision_kind != "FORCED"
            or sum(c.enabled for c in left.event.choices) != 1 or option["kind"] != "CONTINUE"
            or any(o["effects"] for o in option["outcomes"])): return False
    a, b = json.loads(before.raw_json), json.loads(after.raw_json)
    f, g = a.get("interaction", {}).get("flow", {}), b.get("interaction", {}).get("flow", {})
    return (a.get("room") == b.get("room") == "EventRoom" and b.get("phase") == "MAP" and b.get("roomPhase") == "COMPLETE"
            and f.get("schema") == g.get("schema") == "headless-flow-v1"
            and f.get("event_class") == g.get("event_class") == left.event.class_name
            and isinstance(a.get("map"), dict) and isinstance(b.get("map"), dict) and a["map"].get("current") is not None
            and a.get("map", {}).get("current") == b.get("map", {}).get("current")
            and left.player == right.player and left.deck == right.deck and left.relics == right.relics and left.potions == right.potions
            and left.keys.model_dump(exclude_unset=True) == right.keys.model_dump(exclude_unset=True))


def _codex_insertion(plan, after, evidence, driver):
    before=plan.before
    effect=driver.get('combat_card_insertion')
    if not isinstance(effect,dict) or before.sequence+1!=after.sequence:
        return False
    if (driver.get('sequence'),driver.get('method'),driver.get('args'))!=(after.sequence,'chooseRewardCard',list(plan.action.native_args)):
        return False
    offer=evidence['card']; left=before.state
    old={c.uuid for c in left.deck+left.hand+left.draw_pile+left.discard_pile+left.exhaust_pile+left.card_reward_cards}
    created=effect.get('created_uuid')
    upgrades=offer['upgrades']
    if (offer['type'] not in ('CURSE','STATUS') and (not upgrades or offer['id']=='Searing Blow')
            and any(p.get('id')=='MasterRealityPower' for p in json.loads(before.raw_json)['player'].get('powers',[]))):
        upgrades+=1
    return (evidence['copies']==1 and evidence['destination']=='draw_pile' and evidence['free_for_turn'] is False
            and isinstance(created,str) and bool(created) and created not in old
            and type(effect.get('index')) is int and type(effect.get('upgrades')) is int
            and (effect.get('source_action'),effect.get('index'),effect.get('offered_uuid'),effect.get('card_id'),
                 effect.get('upgrades'),effect.get('destination'))
                ==('CodexAction',plan.action.native_args[0],offer['uuid'],offer['id'],upgrades,'draw_pile'))


_SELECTION_COMMITS={
    'scry':('ScryAction','confirmGrid','grid','discard_pile'),
    'retain':('RetainCardsAction','confirmHandSelect','hand','retained_hand'),
    'discard':('DiscardAction','confirmHandSelect','hand','discard_pile'),
}


def _combat_selection_commit(plan,after,evidence,driver):
    before=plan.before
    name,method,kind,destination=_SELECTION_COMMITS[evidence['purpose']]
    effect=driver['combat_selection_commit']
    a=json.loads(before.raw_json)
    selector=a.get('interaction',{}).get('selection') or {}
    turn=(a.get('combat_receipt') or {}).get('turn')
    return (isinstance(effect,dict) and before.sequence+1==after.sequence and a.get('roomPhase')=='COMBAT'
            and (selector.get('kind'),selector.get('purpose'),selector.get('can_confirm'))==(kind,evidence['purpose'],True)
            and plan.action.native_method==method and plan.action.native_args==()
            and (driver.get('method'),driver.get('args'),driver.get('sequence'))==(method,[],after.sequence)
            and type(turn) is int and type(effect.get('turn')) is int and effect['turn']==turn
            and (effect.get('source_action'),effect.get('destination'),effect.get('selected_uuids'))
                 ==(name,destination,evidence['uuids']))


def verify_effect(plan: ActionPlan, after: InteractionFrame) -> EffectCheck:
    """Evidence check only: no queue ACK, UI label, or unrelated screen change counts."""
    before = plan.before
    if plan.action.native_method.startswith("LOCAL_"):
        return EffectCheck("LOCAL_ONLY", "Local intent is not a game effect")
    if after.run_id != before.run_id or after.sequence <= before.sequence:
        return EffectCheck("REJECTED", "Wrong run or non-later snapshot")
    if after.native_token == before.native_token:
        return EffectCheck("PENDING", "No observed game-state change")
    left, right = before.state, after.state
    e = json.loads(plan.action.evidence_json)
    flow_result = verify_flow(plan, after, e)
    if flow_result is not None:
        return EffectCheck("APPLIED" if flow_result else "UNVERIFIED", "Lifecycle postconditions matched" if flow_result else "Lifecycle evidence incomplete")
    if (e["kind"]=='event' and left.event is not None and plan.action.native_args==(0,)
            and (left.event.id,left.event.class_name,left.event.phase)==('SecretPortal','SecretPortal','ACCEPT')):
        from .event_receipts import portal_boss_receipt
        try:applied=portal_boss_receipt(before,after)
        except (KeyError,TypeError,ValueError,AttributeError):applied=False
        return EffectCheck('APPLIED' if applied else 'UNVERIFIED','Secret Portal boss entry')
    if (left.floor, left.act, left.character) != (right.floor, right.act, right.character):
        return EffectCheck("REJECTED", "Unexpected room/run boundary")
    kind, applied = e["kind"], False
    if kind == "buy":
        row = next((r for r in right.shop_inventory if r["slot_id"] == e["slot_id"]), None)
        consumed = row is None or row.get("sold_out") or row.get("id") != e["id"]  # Courier restock.
        if e["item_kind"] == "card":
            added = [c for c in right.deck if c.uuid not in _deck(left)]
            received = any(c.id == e["id"] and c.upgrades == _acquired_upgrades(left, e["upgrades"], e.get("card_type")) for c in added)
        elif e["item_kind"] == "relic": received = Counter(r.id for r in right.relics)[e["id"]] > Counter(r.id for r in left.relics)[e["id"]]
        else: received = Counter(p.id for p in right.potions if not p.is_empty)[e["id"]] > Counter(p.id for p in left.potions if not p.is_empty)[e["id"]]
        applied = consumed and received and right.player.gold == left.player.gold - e["price"] and right.screen_type in ("SHOP_SCREEN", "GRID", "CARD_REWARD", "COMBAT_REWARD")
    elif kind == "purge":
        raw = json.loads(after.raw_json)
        applied = right.screen_type == "SHOP_SCREEN" and right.player.gold == left.player.gold - e["price"] and _selection_committed(left, right, "purge", [e["uuid"]]) and raw.get("interaction", {}).get("shop", {}).get("purge_available") is False
    elif kind == "leave_shop": applied = right.screen_type == "MAP" and right.player.gold == left.player.gold
    elif kind == "enter_shop": applied = right.screen_type == "SHOP_SCREEN"
    elif kind == "open_card_reward":
        applied = (right.screen_type == "CARD_REWARD" and bool(e["offered_cards"])
                   and [c.uuid for c in right.card_reward_cards] == e["offered_cards"])
    elif kind == "open_chest":
        chest = json.loads(after.raw_json).get("interaction", {}).get("chest", {})
        applied = chest.get("open") is True and right.screen_type in ("COMBAT_REWARD", "BOSS_REWARD", "NONE")
    elif kind == "take_boss_relic":
        applied = right.screen_type != "BOSS_REWARD" and Counter(r.id for r in right.relics)[e["id"]] > Counter(r.id for r in left.relics)[e["id"]]
    elif kind == "skip_boss_relic":
        raw=json.loads(after.raw_json);menu=raw.get('interaction',{})
        landing=(right.screen_type=='CHEST' or raw.get('phase')=='PROCEED' and raw.get('room')=='TreasureRoomBoss'
                 and menu.get('flow',{}).get('route')=='next_act' and menu.get('can_proceed') is True)
        applied=landing and right.relics==left.relics and right.deck==left.deck and right.potions==left.potions and right.player==left.player
    elif kind == "reward":
        rows = _reward_rows(before)
        removed = {e["index"]}
        link = e["reward"].get("linked_reward_index")
        if link is not None: removed.add(link)
        consumed = _reward_rows(after) == [r for i, r in enumerate(rows) if i not in removed]
        reward = e["reward"]; rtype = reward["type"]
        if rtype in ("GOLD", "STOLEN_GOLD"):
            gain = 0 if any(r.id == "Ectoplasm" for r in left.relics) else reward.get("amount", 0)
            applied = consumed and right.player.gold == left.player.gold + gain
        elif rtype.endswith("_KEY"):
            color = reward["key_type"]
            applied = consumed and color in right.keys.model_fields_set and getattr(right.keys, color)
        elif rtype == "RELIC": applied = consumed and Counter(r.id for r in right.relics)[reward["relic_id"]] > Counter(r.id for r in left.relics)[reward["relic_id"]]
        elif rtype == "POTION": applied = consumed and Counter(p.id for p in right.potions if not p.is_empty)[reward["potion_id"]] > Counter(p.id for p in left.potions if not p.is_empty)[reward["potion_id"]]
    elif kind == "take_card":
        added = [c for c in right.deck if c.uuid not in _deck(left)]
        applied = right.screen_type != "CARD_REWARD" and len(added) == 1 and added[0].id == e["card"]["id"] and added[0].upgrades == _acquired_upgrades(left, e["card"]["upgrades"], e["card"]["type"])
    elif kind == "take_combat_card":
        raw=json.loads(before.raw_json)
        source=raw['interaction']['card_reward'].get('source_action')
        driver=json.loads(after.raw_json).get('driver_receipt') or {}
        if source=='CodexAction' and 'combat_card_insertion' in driver:
            applied=_codex_insertion(plan,after,e,driver)
        elif source=='ChooseOneColorless':
            from .event_receipts import toolbox_event_pick_receipt
            try:applied=toolbox_event_pick_receipt(before,after,plan.action.native_args[0])
            except (KeyError,IndexError,TypeError,ValueError,AttributeError):applied=False
        else:
            old = {c.uuid for c in left.hand + left.draw_pile + left.discard_pile + left.exhaust_pile}
            piles = right.hand + right.discard_pile + (right.draw_pile + right.exhaust_pile if e.get("destination") == "draw_pile" else [])
            added = [c for c in piles if c.uuid not in old]
            if e.get("destination") == "draw_pile": added = [c for c in added if c.id == e["card"]["id"]]
            applied = (right.screen_type != "CARD_REWARD" and right.room_phase == "COMBAT" and left.deck == right.deck
                       and len(added) == e["copies"] and all(c.id == e["card"]["id"] and c.upgrades == e["card"]["upgrades"]
                       and (not e["free_for_turn"] or c.cost_for_turn == min(0,e["card"]["cost_for_turn"])) for c in added))
            if not applied:
                try:applied=_discovery_with_cleanup(before,after,e)
                except (KeyError,IndexError,TypeError,ValueError,AttributeError):applied=False
    elif kind == "skip_card": applied = right.screen_type != "CARD_REWARD" and right.deck == left.deck and right.player.max_hp == left.player.max_hp
    elif kind == "bowl": applied = right.screen_type != "CARD_REWARD" and right.deck == left.deck and right.player.max_hp == left.player.max_hp + 2
    elif kind == "select":
        if right.screen_type == left.screen_type:
            old = {c.uuid for c in left.grid_selected_cards}; new = {c.uuid for c in right.grid_selected_cards}
            expected = old - {e["uuid"]} if e["selected"] else old | {e["uuid"]}
            applied = new == expected and new != old
            raw_after = json.loads(after.raw_json).get("interaction", {}).get("selection", {})
            if e["selection_kind"] == "grid" and raw_after.get("locked") and raw_after.get("preview_uuid") == e["uuid"]:
                applied = True
            applied = applied and right.deck == left.deck and right.player == left.player
        elif e["auto"]:
            if e.get('library_draft') is True:
                from .event_receipts import library_selection_receipt
                applied=library_selection_receipt(before,after,e['uuid'])
            elif e['purpose']=='bonfire_offer':
                applied=_bonfire_offer(before,after,e['uuid'])
            elif e['purpose']=='meditate':
                applied=_meditate_finished(before,after,[c.uuid for c in left.grid_selected_cards]+[e['uuid']])
            elif e["purpose"] in ("topdeck", "retrieve_exhaust", "duplicate_combat"):
                applied = _combat_move(left, right, e["purpose"], [e["uuid"]], e.get("copies"))
            elif e["selection_kind"] == "grid":
                targets = [c.uuid for c in left.grid_selected_cards] + [e["uuid"]]
                applied = _selection_committed(left, right, e["purpose"], targets)
                if e["purpose"] == "purge":
                    from .event_receipts import neow_cage_receipt
                    verdict=neow_cage_receipt(json.loads(before.raw_json),json.loads(after.raw_json),
                                            plan.action.native_args[0])
                    if verdict is not None:applied=verdict
                if e["purpose"] == "transform":
                    from .event_receipts import astrolabe_selection_receipt
                    verdict=astrolabe_selection_receipt(json.loads(before.raw_json),json.loads(after.raw_json),
                                                       plan.action.native_args[0])
                    if verdict is not None:applied=verdict
            elif e["purpose"] in ("discard", "exhaust"):
                destination = right.discard_pile if e["purpose"] == "discard" else right.exhaust_pile
                applied = ({c.uuid for c in left.grid_selected_cards} | {e["uuid"]}) <= {c.uuid for c in destination}
    elif kind == "confirm_selection":
        driver=json.loads(after.raw_json).get('driver_receipt') or {}
        if e['purpose'] in _SELECTION_COMMITS and 'combat_selection_commit' in driver:
            applied=_combat_selection_commit(plan,after,e,driver)
            return EffectCheck('APPLIED' if applied else 'UNVERIFIED','Bound combat selection commit')
        applied = right.screen_type not in ("GRID", "HAND_SELECT") and _selection_committed(left, right, e["purpose"], e["uuids"])
        from .event_receipts import designer_purge_receipt
        designer_result=designer_purge_receipt(before,after,e["uuids"]) if e['purpose']=='purge' else None
        if designer_result is not None:
            applied=designer_result
        elif e['purpose']=='bonfire_offer':
            applied=len(e['uuids'])==1 and _bonfire_offer(before,after,e['uuids'][0])
        elif e["purpose"] == "obtain":
            added = [c.reference for c in right.deck if c.uuid not in _deck(left)]
            applied = right.screen_type not in ("GRID", "HAND_SELECT") and Counter(added) == Counter(e["offered"])
        elif not e["uuids"] and e['purpose']!='scry':
            applied = right.screen_type not in ("GRID", "HAND_SELECT") and right.deck == left.deck
            if e['purpose']=='retain' and right.screen_type=='HAND_SELECT':
                applied=_empty_retain_to_trade(before,after)
        elif left.screen_type == "HAND_SELECT" and e["purpose"] in ("discard", "exhaust"):
            destination = right.discard_pile if e["purpose"] == "discard" else right.exhaust_pile
            applied = right.screen_type != "HAND_SELECT" and set(e["uuids"]) <= {c.uuid for c in destination}
            if right.screen_type=='HAND_SELECT' and e['purpose']=='discard':
                applied=(_next_survivor_discard(before,after,e['uuids'])
                         or _next_duplicated_discard(before,after,e['uuids']))
            elif not applied and e['purpose']=='discard':
                try:applied=(_tingsha_terminal_discard(before,after,e['uuids'])
                             or _dagger_throw_ink_shuffle(before,after,e['uuids'])
                             or _acrobatics_reflex_shuffle(before,after,e['uuids']))
                except (KeyError,TypeError,ValueError,AttributeError):applied=False
        elif left.screen_type == "HAND_SELECT" and e["purpose"] == "upgrade":
            applied = right.screen_type != "HAND_SELECT" and _combat_upgrade(left, right, e["uuids"])
        elif e["purpose"] in ("topdeck", "retrieve_exhaust", "duplicate_combat"):
            applied = right.screen_type not in ("GRID", "HAND_SELECT") and _combat_move(left, right, e["purpose"], e["uuids"], e.get("copies"))
            if not applied and left.screen_type=="HAND_SELECT" and e["purpose"]=="topdeck":
                applied=_warcry_dark_embrace(before,after,e["uuids"])
        elif e["purpose"] == "scry":
            applied = (right.screen_type != "GRID" and left.room_phase == right.room_phase == "COMBAT" and left.deck == right.deck
                       and set(e["uuids"]) <= {c.uuid for c in right.discard_pile + right.hand})
            if not e['uuids'] and right.screen_type=='COMBAT_REWARD':
                applied=_empty_scry_terminal(before,after)
            if right.screen_type=='GRID':
                try:applied=(_queued_cut_through_fate_scry(before,after,e['uuids'])
                             or _next_cut_through_fate_scry(before,after,e['uuids'])
                             or _next_omniscience_just_lucky_scry(before,after,e['uuids'])
                             or _omniscience_just_lucky_into_cut_scry(before,after,e['uuids'])
                             or _queued_omniscience_cut_through_fate_scry(before,after,e['uuids'])
                             or _duplicate_third_eye_scry(before,after,e['uuids'])
                             or _omniscience_scry_retargets_clean(before,after,e['uuids']))
                except (KeyError,IndexError,TypeError,ValueError,StopIteration):applied=False
    elif kind == "event":
        if left.event is not None and left.event.id == e["event_id"]:
            from .event_receipts import event_receipt
            source_result=event_receipt(before,after,plan.action.native_args[0])
            if source_result is not None:applied=source_result
            elif 'we_meet_again' in e:applied=_we_meet_effect(before,after,e['we_meet_again'])
            else:
                option = e["option"]
                applied = any(o.get("probability", 0) > 0 and _effects_match(left, right, o["effects"], option["followup"], e["transition"],
                    native_combat_handoff=after.native_combat_handoff) for o in option["outcomes"])
                applied = applied or _closed_forced_event(before, after, option)
    elif kind == 'wheel': applied = _wheel_effect(before,after,e['facts'])
    elif kind == "match_flip":
        a = json.loads(before.raw_json)["interaction"].get("match_game", {})
        b = json.loads(after.raw_json)["interaction"].get("match_game", {})
        old = {r["slot"]: r for r in a.get("cards", [])}; new = {r["slot"]: r for r in b.get("cards", [])}
        face = [r for r in old.values() if r["face_up"]]
        receipt = b.get("last_flip") or {}; slot = e["slot"]
        same = (right.event is not None and right.event.class_name == "GremlinMatchGame"
                and left.relics == right.relics and left.potions == right.potions and receipt.get("slot") == slot
                and isinstance(receipt.get("card_id"), str) and slot in old and not old[slot]["face_up"])
        if not face:
            applied = (same and left.player == right.player and set(new) == set(old) and b["attempts"] == a["attempts"] and b["matched"] == a["matched"]
                       and new[slot]["face_up"] and new[slot]["card_id"] == receipt["card_id"] and left.deck == right.deck
                       and all(new[s] == old[s] for s in old if s != slot))
        elif len(face) == 1:
            matched = face[0]["card_id"] == receipt.get("card_id")
            removed = {face[0]["slot"], slot} if matched else set()
            added = [c for c in right.deck if c.uuid not in _deck(left)]
            obtained = (len(added) == 1 and added[0].id == receipt["card_id"] and _deck(left).keys() <= _deck(right).keys()) if matched else left.deck == right.deck
            expected_player=left.player
            relics={r.id for r in left.relics}
            if matched and obtained and added[0].type=='CURSE' and 'Darkstone Periapt' in relics:
                expected_player=left.player.model_copy(update={
                    'max_hp':left.player.max_hp+6,
                    'current_hp':left.player.current_hp+(0 if 'Mark of the Bloom' in relics else 6)})
            applied = (same and right.player==expected_player and b["attempts"] == a["attempts"]-1 and b["matched"] == a["matched"]+int(matched)
                       and set(new) == set(old)-removed and all(not r["face_up"] for r in new.values()) and obtained)
    elif kind == "proceed": applied = right.screen_type == "MAP" and right.player.gold == left.player.gold
    return EffectCheck("APPLIED" if applied else "UNVERIFIED", "Action-specific postconditions matched" if applied else "Observed change lacks the required evidence")


__all__ = ["INTERACTION_SCHEMA", "IroncladInteractionBridge", "InteractionFrame", "ActionPlan", "EffectCheck", "verify_effect"]
