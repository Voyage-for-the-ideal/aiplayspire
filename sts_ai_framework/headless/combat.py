"""Offline native-combat planning/verification; no client, worker, or executor."""
from __future__ import annotations

import copy
from dataclasses import dataclass
import hashlib
import json
import math
import re
from uuid import UUID

from battle_engine.state import projection, context
from .state import HeadlessStateMapper, HeadlessContractError, _int, _text
from ..models import GameAction, GameState

SCHEMA = "headless-combat-v1"
RNG_POLICY = "headless-b354a9ee-v1"
STREAMS = ("monster", "map", "event", "merchant", "card", "treasure", "relic", "potion", "monster_hp", "ai", "shuffle", "card_random", "misc", "headless")
MODELLED = ("ai", "card_random", "misc", "monster_hp", "potion", "shuffle", "card", "headless")


def _json(v): return json.dumps(v, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def validate_rng(snapshot):
    r = snapshot.get("_headless_rng")
    if snapshot.get("_engine") != "headless" or not isinstance(r, dict) or r.get("schema") != "headless-rng-v1" or r.get("policy") != RNG_POLICY:
        raise HeadlessContractError("Missing exact headless RNG policy")
    if set(r.get("streams", {})) != set(STREAMS): raise HeadlessContractError("Expected 14 exact run RNG streams")
    for name, stream in [*r["streams"].items(), ("math", r.get("math", {}))]:
        for word in ("state0", "state1"):
            if not isinstance(stream.get(word), str) or re.fullmatch("[0-9a-f]{16}", stream[word]) is None:
                raise HeadlessContractError("RNG words must be fixed-width hexadecimal strings")
        if int(stream["state0"], 16) | int(stream["state1"], 16) == 0: raise HeadlessContractError("Zero RNG state")
        if name != "math":
            count = _int(stream.get("counter"), "rng counter", 0, 10000000)
            if _int(snapshot["rng_state"].get(name + "_rng_counter"), "counter alias", 0, 10000000) != count: raise HeadlessContractError("RNG counter mismatch")
    return r


def _api(command):
    kind = command.get("type")
    if kind == "END": return {"type": "end_turn"}
    if kind == "HAND_SELECT_CONFIRM": return {"type": "proceed"}
    if kind in ("HAND_SELECT","GRID_SELECT"): return {"type": "choose", "choice_index": _int(command.get("card_index"), "selection index", 0)}
    if kind not in ("CARD", "POTION"): raise HeadlessContractError("Unsupported native combat command")
    index_name = "card_index" if kind == "CARD" else "potion_index"
    index = _int(command.get(index_name), index_name, 0)
    target = _int(command.get("monster_index"), "monster index", -1)
    result = {"type": "play" if kind == "CARD" else "potion", index_name: index}
    if target >= 0: result["target_index"] = target
    return result


def is_gambler_selection(raw):
    snapshot=raw.get('combat',{}).get('snapshot',{})
    return (raw.get('phase')=='CARD_SELECT' and snapshot.get('screen_name')=='HAND_SELECT'
            and snapshot.get('hand_select_screen_state',{}).get('current_action_state',{}).get('state_class')=='savestate.actions.GamblingChipActionState')


def is_liquid_selection(raw):
    snapshot=raw.get('combat',{}).get('snapshot',{})
    return (raw.get('phase')=='CARD_SELECT' and snapshot.get('screen_name')=='GRID'
            and snapshot.get('grid_card_select_screen_state',{}).get('current_action_state',{}).get('state_class')=='savestate.actions.BetterDiscardPileToHandActionState')


def is_fetch_selection(raw):
    s=raw.get('combat',{}).get('snapshot',{})
    return (raw.get('phase')=='CARD_SELECT' and s.get('screen_name')=='GRID'
            and s.get('grid_card_select_screen_state',{}).get('current_action_state',{}).get('state_class')=='savestate.actions.FetchCardGridActionState')


def is_headbutt_selection(raw):
    snapshot=raw.get('combat',{}).get('snapshot',{})
    return (raw.get('phase')=='CARD_SELECT' and snapshot.get('screen_name')=='GRID'
            and snapshot.get('grid_card_select_screen_state',{}).get('current_action_state',{}).get('state_class')=='savestate.actions.DiscardPileToTopOfDeckActionState')


def is_elixir_selection(raw):
    s=raw.get('combat',{}).get('snapshot',{})
    return (raw.get('phase')=='CARD_SELECT' and s.get('screen_name')=='HAND_SELECT'
            and s.get('hand_select_screen_state',{}).get('current_action_state',{}).get('state_class')=='savestate.actions.ElixirExhaustActionState')


def is_single_hand(raw):
    s=raw.get('combat',{}).get('snapshot',{})
    return (raw.get('phase')=='CARD_SELECT' and s.get('screen_name')=='HAND_SELECT' and
            s.get('hand_select_screen_state',{}).get('current_action_state',{}).get('state_class')=='savestate.actions.SingleHandActionState')


def is_native_combat_selection(raw): return is_single_hand(raw) or is_fetch_selection(raw) or is_gambler_selection(raw) or is_elixir_selection(raw) or is_liquid_selection(raw) or is_headbutt_selection(raw)


def _liquid_potion(snapshot,command):
    return (command.get('type')=='POTION' and snapshot['player_state']['potions'][command['potion_index']]['id']=='LiquidMemories')


def _selection_queue(view,allow_wait,expect_heal=False):
    queue=view.get('action_queue')
    if not isinstance(queue,list) or len(queue)>3 or view.get('card_queue_state')!=[]:
        raise HeadlessContractError('Unexpected executable selection continuation')
    refresh=wait=heal=False
    for action in queue:
        if not isinstance(action,dict): raise HeadlessContractError('Invalid selection continuation row')
        kind=action.get('state_class')
        if kind=='savestate.actions.HandCheckActionState':
            state=action.get('state',{})
            if (refresh or wait or not isinstance(state,dict) or type(state.get('owner_index')) is not int
                    or state['owner_index']!=-1 or state.get('derived_refresh') is not True):
                raise HeadlessContractError('Invalid derived hand refresh')
            refresh=True
        elif kind=='savestate.actions.OrnithopterHealActionState':
            s=action.get('state')
            if (not refresh or wait or heal or not expect_heal or not isinstance(s,dict)
                    or s!={'source_index':-1,'target_index':-1,'amount':5}
                    or any(type(v) is not int for v in s.values())):
                raise HeadlessContractError('Unexpected Ornithopter heal')
            heal=True
        elif kind=='savestate.actions.WaitActionState':
            if not allow_wait or wait: raise HeadlessContractError('Unexpected selection presentation wait')
            wait=True
        else: raise HeadlessContractError('Unexpected executable selection continuation')
    if heal!=expect_heal:raise HeadlessContractError('Missing Ornithopter heal')


def _gambler_evidence(snapshot, *, elixir=False):
    h=snapshot['hand_select_screen_state'];current=h['current_action_state']
    if elixir:
        if (current.get('state_class')!='savestate.actions.ElixirExhaustActionState'
                or _json(current.get('state'))!=_json({'owner_index':-1,'amount':99,'is_random':False,'any_number':True,'can_pick_zero':True})
                or not h.get('action_queue') or h['action_queue'][0].get('state_class')!='savestate.actions.HandCheckActionState'):
            raise HeadlessContractError('Invalid Elixir exhaust continuation')
    elif current.get('state_class')!='savestate.actions.GamblingChipActionState' or type(current.get('state',{}).get('notchip')) is not bool:
        raise HeadlessContractError('Invalid Gambler selection controls')
    if (h.get('num_cards_to_select')!=99 or h.get('any_number') is not True
            or h.get('can_pick_zero') is not True or h.get('were_cards_retrieved') is not False):
        raise HeadlessContractError('Invalid Gambler selection controls')
    _selection_queue(h,not elixir,(elixir or current['state']['notchip']) and
                     any(r['relic_id']=='Toy Ornithopter' for r in snapshot['player_state']['relics']))
    selected=[c['uuid'] for c in h['selected_cards']];remaining=[c['uuid'] for c in snapshot['player_state']['hand']]
    if len(set(selected+remaining))!=len(selected+remaining): raise HeadlessContractError('Duplicate Gambler selection UUID')
    return {'schema':'headless-elixir-selection-v1' if elixir else 'headless-gambler-selection-v1','selected':selected,'remaining':remaining,
            'count':99,'any_number':True,'can_pick_zero':True}


def _liquid_evidence(snapshot):
    view=snapshot['grid_card_select_screen_state'];current=view['current_action_state'];data=current.get('state',{})
    count=_int(data.get('number_of_cards'),'Liquid Memories amount',1,2)
    if (current.get('state_class')!='savestate.actions.BetterDiscardPileToHandActionState'
            or data.get('owner_index')!=-1 or data.get('optional') is not False
            or data.get('set_cost') is not True or data.get('new_cost')!=0 or view.get('num_cards')!=count
            or any(view.get(k) is not False for k in ('any_number','for_clarity','for_upgrade','for_transform','for_purge','confirm_screen_up','is_just_for_confirming'))):
        raise HeadlessContractError('Invalid Liquid Memories controls')
    p=snapshot['player_state'];options=view.get('group_cards');selected=view.get('selected_cards')
    if not isinstance(options,list) or not isinstance(selected,list) or options!=p['discard_pile'] or len(options)<=count or len(selected)>=count:
        raise HeadlessContractError('Liquid Memories discard/partial selection mismatch')
    if count!=(2 if any(r['relic_id']=='SacredBark' for r in p['relics']) else 1):
        raise HeadlessContractError('Liquid Memories potency mismatch')
    ids=[c['uuid'] for c in options];chosen=[c['uuid'] for c in selected]
    if len(set(ids))!=len(ids) or len(set(chosen))!=len(chosen) or any(c not in options for c in selected):
        raise HeadlessContractError('Liquid Memories selected card identity mismatch')
    _selection_queue(view,False,any(r['relic_id']=='Toy Ornithopter' for r in p['relics']))
    return {'schema':'headless-liquid-selection-v1','options':ids,'selected':chosen,'count':count,'new_cost':0}


def _fetch_evidence(s):
    p=s['player_state'];v=s['grid_card_select_screen_state']
    a=v['current_action_state']['state'];source=a.get('source_card');seek=source=='Seek';omni=source=='Omniscience'
    secret=source in ('Secret Weapon','Secret Technique')
    n=_int(a.get('number_of_cards'),'fetch count',1,2);c=p.get('card_in_use')
    if (not (seek or omni or secret or source=='Hologram') or not isinstance(c,dict) or c.get('card_id')!=source
            or c['times_upgraded'] not in (0,1) or n!=(1+c['times_upgraded'] if seek else 1)
            or a!={'source_card':source,'owner_index':-1,'number_of_cards':n} or v['num_cards']!=n
            or any(v.get(k) is not False for k in ('any_number','for_clarity','for_upgrade','for_transform','for_purge','confirm_screen_up','is_just_for_confirming'))):
        raise HeadlessContractError('Invalid fetch controls')
    pile=p['draw_pile' if seek or omni or secret else 'discard_pile'];group=v['group_cards'];chosen=v['selected_cards']
    ids=[r['uuid'] for r in group];selected=[r['uuid'] for r in chosen]
    if (not secret and len(group)!=len(pile) or len(group)<n+int(not omni) or any(row not in pile for row in group)
            or not (seek or omni or secret) and group!=pile or len(set(ids))!=len(ids)
            or len(selected)>=n or len(set(selected))!=len(selected) or any(row not in group for row in chosen)):
        raise HeadlessContractError('Invalid fetch source pile or selected UUID')
    q=v['action_queue'];exhaust=seek or omni or c['times_upgraded']==0
    autoplay=v['card_queue_state'];purge=c.get('purge_on_use',False)
    active={x['uuid'] for k in _BATTLE_PILES for x in p[k]}
    if (not isinstance(autoplay,list) or len(autoplay)>12 or not omni and (autoplay or purge)
            or p['limbo'] or q!=_skill_selection_queue(p,c,exhaust) or c.get('_private',{})!={}
            or c['exhaust'] is not exhaust or c.get('dont_trigger_on_use_card') or c['uuid'] in active):
        raise HeadlessContractError('Invalid fetch cleanup or pending UUID')
    def view(card):
        return {'uuid':card['uuid'],'id':card['card_id'],'upgrades':card['times_upgraded'],'cost':card['cost'],
                'turn_cost':card['cost_for_turn'],'free':card['free_to_play_once'],'retain':card['retain'],'exhaust':card['exhaust']}
    pending=view(c)
    if purge:pending['purge']=True
    seen=active|{c['uuid']}
    queued=[]
    for row in autoplay:
        next_card=row['card']
        if (row.get('autoplay_card') is not True or row.get('ignore_energy_total') is not False
                or row.get('random_target') is not True or row['monster_index'] is not None
                or next_card['dont_trigger_on_use_card'] is not False or next_card['uuid'] in seen
                or not (next_card['exhaust'] is True or next_card['purge_on_use'] is True)):
            raise HeadlessContractError('Invalid nested Omniscience queue')
        seen.add(next_card['uuid'])
        queued.append(dict(view(next_card),purge=next_card['purge_on_use'],
                           energy=_int(row['energy_on_use'],'Omniscience queue energy',0)))
    evidence={'schema':'headless-fetch-grid-v1','source':source,'options':ids,'selected':selected,'count':n,
              'pending_card':pending}
    if queued:evidence['autoplay_queue']=queued
    return evidence

def _skill_selection_queue(player, card, exhaust=False):
    """Bind all known effects and their order around use-card cleanup."""
    queue=[]
    def append(name, **state):
        queue.append({'state_class':'savestate.actions.'+name,'state':state})
    if card['card_id']=='Burning Pact':
        append('SelectionDrawActionState',kind='burning_pact',amount=2+card['times_upgraded'])
    hex_power=next((pw for pw in player['creature']['powers'] if pw['power_id']=='Hex'),None)
    if hex_power is not None:
        append('SelectionHexActionState',amount=_int(hex_power['amount'],'Hex Dazed count',1))
    if any(row['relic_id']=='InkBottle' and row['counter']==0 for row in player['relics']):
        append('SelectionDrawActionState',kind='ink_bottle',amount=1)
    if card['card_id']=='Hologram' and any(row['relic_id']=='Letter Opener' and row['counter']==0 for row in player['relics']):
        append('SelectionLetterOpenerActionState',amount=5)
    append('UseCardActionState',card=card,targetIndex=-2,exhaustCard=exhaust,returnToHand=False,reboundCard=False)
    powers={power['power_id']:power['amount'] for power in player['creature']['powers']}
    if card['card_id']=='True Grit' and card.get('block',0)>0 and 'NoBlockPower' not in powers and powers.get('Juggernaut',0)>0:
        append('SelectionJuggernautActionState',amount=_int(powers['Juggernaut'],'Juggernaut damage',1))
    return queue


def _native_selection_evidence(snapshot):
    if snapshot['screen_name']=='HAND_SELECT' and snapshot['hand_select_screen_state']['current_action_state']['state_class']=='savestate.actions.SingleHandActionState':
        return _single_hand_evidence(snapshot)
    if snapshot['screen_name']=='HAND_SELECT':return _gambler_evidence(snapshot,elixir=snapshot['hand_select_screen_state']['current_action_state']['state_class']=='savestate.actions.ElixirExhaustActionState')
    if snapshot['grid_card_select_screen_state']['current_action_state']['state_class']=='savestate.actions.FetchCardGridActionState':return _fetch_evidence(snapshot)
    if snapshot['grid_card_select_screen_state']['current_action_state']['state_class']=='savestate.actions.DiscardPileToTopOfDeckActionState':return _headbutt_evidence(snapshot)
    return _liquid_evidence(snapshot)


def _single_hand_evidence(s):
    h=s['hand_select_screen_state'];a=h['current_action_state']['state'];p=s['player_state']
    c=p['card_in_use'];source=a['source'];up=c['times_upgraded'];copies=a['copies']
    forethought=source=='Forethought';any_number=forethought and up==1
    count=99 if any_number else 1
    if (set(a)!={'source','copies','owner_index','hidden'} or source not in ('Armaments','True Grit','Dual Wield','Burning Pact','Forethought') or c['card_id']!=source or up not in (0,1)
            or up and source=='Armaments' or not up and source=='True Grit' or a['owner_index']!=-1
            or copies!=(1+up if source=='Dual Wield' else 1) or h['num_cards_to_select']!=count
            or any(h.get(k) is not False for k in ('were_cards_retrieved','up_to'))
            or h.get('any_number') is not any_number or h.get('can_pick_zero') is not any_number
            or c['exhaust'] or c.get('purge_on_use') or c.get('dont_trigger_on_use_card') or c.get('_private') or p['limbo']
            or h['action_queue']!=_skill_selection_queue(p,c)):
        raise HeadlessContractError('Invalid single-hand source, controls or cleanup')
    hidden=[x['uuid'] for x in a['hidden']];selected=[x['uuid'] for x in h['selected_cards']];remaining=[x['uuid'] for x in p['hand']]
    ids=hidden+selected+remaining+[c['uuid']]+[x['uuid'] for pile in ('draw_pile','discard_pile','exhaust_pile') for x in p[pile]]
    autoplay=h['card_queue_state']
    if not isinstance(autoplay,list) or len(autoplay)>5:
        raise HeadlessContractError('Invalid single-hand autoplay queue')
    queued=[]
    for item in autoplay:
        card=item['card']
        if (item['autoplay_card'] is not True or item['ignore_energy_total'] is not False or item['random_target'] is not False
                or card['purge_on_use'] is not False or card['dont_trigger_on_use_card'] is not False):
            raise HeadlessContractError('Invalid single-hand autoplay item')
        target=_int(item['monster_index'],'single-hand autoplay target',0)
        if target>=len(s['cur_map_node_state']['monster_data']):
            raise HeadlessContractError('Invalid single-hand autoplay target')
        ids.append(card['uuid'])
        queued.append({'uuid':card['uuid'],'id':card['card_id'],'upgrades':card['times_upgraded'],'cost':card['cost'],
                       'turn_cost':card['cost_for_turn'],'free':card['free_to_play_once'],'retain':card['retain'],
                       'target':target,'energy':_int(item['energy_on_use'],'single-hand autoplay energy',0)})
    if (len(set(ids))!=len(ids) or len(selected)>(10 if any_number else 1)
            or len(selected)+len(remaining)<(1 if any_number else 2)
            or source in ('True Grit','Burning Pact','Forethought') and hidden or forethought and autoplay):
        raise HeadlessContractError('Invalid single-hand UUID lineage')
    return {'schema':'headless-single-hand-v1','remaining':remaining,'hidden':hidden,'selected':selected,'copies':copies,
            'pending_card':{'uuid':c['uuid'],'id':source,'upgrades':up,'cost':c['cost'],'turn_cost':c['cost_for_turn'],
                            'free':c['free_to_play_once'],'retain':c['retain']},
            **({'autoplay_queue':queued} if queued else {}),
            **({'count':count,'any_number':any_number} if forethought else {})}


def _headbutt_evidence(snapshot):
    view=snapshot['grid_card_select_screen_state'];p=snapshot['player_state'];current=view['current_action_state']
    if (current.get('state_class')!='savestate.actions.DiscardPileToTopOfDeckActionState'
            or current.get('state')!={'owner_index':-1} or view.get('num_cards')!=1
            or any(view.get(k) is not False for k in ('any_number','for_clarity','for_upgrade','for_transform','for_purge','confirm_screen_up','is_just_for_confirming'))
            or view.get('selected_cards')!=[] or view.get('group_cards')!=p['discard_pile'] or len(p['discard_pile'])<=1
            or not isinstance(view.get('card_queue_state'),list) or not isinstance(p.get('limbo'),list)):
        raise HeadlessContractError('Invalid Headbutt selection controls')
    queue=view.get('action_queue')
    copies=view['card_queue_state'];limbo=p['limbo']
    first=next(iter(snapshot['cur_map_node_state']['monster_data']),None)
    sharp=next((pw for pw in first['creature']['powers'] if pw['power_id']=='Sharp Hide'),None) if first else None
    if sharp is not None:
        expected={'state_class':'savestate.actions.HeadbuttSharpHideActionState',
                  'state':{'owner_index':0,'target_index':-1,'amount':_int(sharp['amount'],'Sharp Hide damage',1)}}
        if first['creature']['id']!='TheGuardian' or copies or not isinstance(queue,list) or not queue or queue[0]!=expected:
            raise HeadlessContractError('Invalid pending Headbutt Sharp Hide damage')
        queue=queue[1:]
    if copies:
        expected={'state_class':'savestate.actions.RemoveSpecificPowerActionState','state':{'ownerIndex':-1,'powerId':'Double Tap'}}
        if (len(copies)!=1 or not isinstance(queue,list) or not queue or queue[0]!=expected or
                not any(v['power_id']=='Double Tap' and v['amount']==0 for v in p['creature']['powers'])):
            raise HeadlessContractError('Invalid pending Headbutt replay order or Double Tap owner')
        queue=queue[1:]
    rage=next((pw['amount'] for pw in p['creature']['powers'] if pw['power_id']=='Rage'),0)
    if rage:
        expected={'state_class':'savestate.actions.HeadbuttRageBlockActionState',
                  'state':{'owner_index':-1,'amount':_int(rage,'Rage block',1)}}
        if copies or not isinstance(queue,list) or not queue or queue[0]!=expected:
            raise HeadlessContractError('Invalid pending Headbutt Rage block')
        queue=queue[1:]
    attack_relics={'savestate.actions.HeadbuttShurikenActionState':'Shuriken',
                   'savestate.actions.HeadbuttKunaiActionState':'Kunai'}
    prior_relic=-1
    while isinstance(queue,list) and queue and queue[0].get('state_class') in attack_relics:
        relic=attack_relics[queue[0]['state_class']]
        order=next((i for i,r in enumerate(p['relics']) if r['relic_id']==relic and r['counter']==0),-1)
        if (queue[0]['state']!={'owner_index':-1,'amount':1}
                or order<=prior_relic):
            raise HeadlessContractError('Invalid pending Headbutt attack relic buff')
        prior_relic=order
        queue=queue[1:]
    if not isinstance(queue,list) or len(queue) not in (1,2) or queue[0].get('state_class')!='savestate.actions.UseCardActionState':
        raise HeadlessContractError('Unexpected Headbutt executable continuation')
    data=queue[0].get('state',{});card=p.get('card_in_use')
    if (not isinstance(card,dict) or card.get('card_id')!='Headbutt' or data.get('card')!=card
            or data.get('exhaustCard') is not False or data.get('returnToHand') is not False or data.get('reboundCard') is not False
            or card.get('exhaust') is not False or type(card.get('purge_on_use')) is not bool
            or card.get('dont_trigger_on_use_card') is not False or card.get('_private',{})!={}):
        raise HeadlessContractError('Invalid Headbutt cleanup card')
    target=_int(data.get('targetIndex'),'Headbutt source target',0)
    if target>=len(snapshot['cur_map_node_state']['monster_data']):raise HeadlessContractError('Invalid Headbutt cleanup target')
    if copies:
        item=copies[0];copy=item.get('card',{})
        if (card['purge_on_use'] or copy.get('purge_on_use') is not True or copy.get('dont_trigger_on_use_card') is not False
                or copy.get('exhaust') is not False or limbo!=[copy] or item.get('monster_index')!=target or type(item.get('monster_index')) is not int
                or type(item.get('energy_on_use')) is not int or item['energy_on_use']<0 or item.get('ignore_energy_total') is not True
                or item.get('autoplay_card') is not True or item.get('random_target') is not False
                or any(copy.get(k)!=card[k] for k in ('uuid','card_id','cost','cost_for_turn','times_upgraded','base_damage'))):
            raise HeadlessContractError('Invalid pending Headbutt replay copy or target')
    elif limbo and not (card['purge_on_use'] and limbo==[card]):
        raise HeadlessContractError('Invalid Headbutt limbo state')
    if len(queue)==2:
        tail=queue[1];facts=tail.get('state',{});m=snapshot['cur_map_node_state']['monster_data'][target]
        powers={pw['power_id']:pw['amount'] for pw in m['creature']['powers']}
        if (tail.get('state_class')!='savestate.actions.HeadbuttMonsterTailState' or type(facts.get('owner_index')) is not int
                or facts['owner_index']!=target or not (
                    facts.get('kind')=='plated_armor_minus_one' and powers.get('Plated Armor',0)>0
                    or facts.get('kind')=='flight_minus_one' and m['creature']['id']=='Byrd' and powers.get('Flight',0)>0
                    or facts.get('kind')=='malleable_block' and m['creature']['id']=='SnakePlant' and powers.get('Malleable',0)>3
                    or facts.get('kind')=='guardian_defensive_mode' and m['creature']['id']=='TheGuardian'
                    and 'Mode Shift' in powers and powers['Mode Shift']<=0 and m['_private'].get('TheGuardian.closeUpTriggered') is True)):
            raise HeadlessContractError('Invalid pending Headbutt monster effect')
    options=[c['uuid'] for c in p['discard_pile']]
    matches=[(pile,c) for pile in ('hand','draw_pile','discard_pile','exhaust_pile') for c in p[pile] if c['uuid']==card['uuid']]
    if (len(set(options))!=len(options) or (not card['purge_on_use'] and bool(matches)) or
            card['purge_on_use'] and (len(matches)!=1 or matches[0][0]!='discard_pile' or matches[0][1].get('purge_on_use',False) is not False or
                                       any(matches[0][1].get(k)!=card[k] for k in ('card_id','cost','times_upgraded')))):
        raise HeadlessContractError('Duplicate Headbutt selection UUID')
    return {'schema':'headless-headbutt-selection-v1','options':options,'selected':[],'count':1,
            'pending_card':{'uuid':card['uuid'],'id':'Headbutt','upgrades':card['times_upgraded'],'cost':card['cost'],
                'turn_cost':card['cost_for_turn'],'free':card['free_to_play_once'],'retain':card['retain'],'target':target}}


def _private_invariants(snapshot):
    """Keep unmapped primitive source facts stable until an explicit adapter exists."""
    out = {}; p = snapshot["player_state"]
    def add(key, row, *, owner_hp=None, owner_slot=None):
        values = dict(row.get("_private", {}))
        if key == 'relic:OrangePellets':
            keys=['OrangePellets.'+kind for kind in ('ATTACK','SKILL','POWER')]
            if any(type(values.get(k)) is not bool for k in keys) or all(values[k] for k in keys):
                raise HeadlessContractError('Invalid Orange Pellets latches')
            for k in keys: del values[k]  # Native full key tracks all three mutable flags.
        if key.startswith('player-power:') and re.fullmatch(r'TheBomb(?:0|[1-9][0-9]*)',row.get('power_id','')):
            _int(int(row['power_id'][7:]),'TheBomb instance',0,2147483647)
            _int(row.get('amount'),'TheBomb countdown',1,3)
            _int(values.get('TheBombPower.damage'),'TheBombPower.damage',1,127)
            del values['TheBombPower.damage']
        if key == 'player-power:Combust':
            _int(values.get('CombustPower.hpLoss'), 'CombustPower.hpLoss', 1, 127)
            # Imported exactly as native combustHpLoss and compared in the full
            # key, so stacking may change it. Other private fields stay frozen.
            del values['CombustPower.hpLoss']
        if key == 'player-power:Mantra':
            # Source constant; the actual amount/presence and resulting stance
            # are compared in the native state key, not frozen as a latch.
            if type(values.get('MantraPower.PRAYER_REQUIRED')) is not int or values['MantraPower.PRAYER_REQUIRED'] != 10:
                raise HeadlessContractError('Invalid Mantra threshold')
            del values['MantraPower.PRAYER_REQUIRED']
        if key=='player-power:Panache':
            _int(values.get('PanachePower.damage'),'PanachePower.damage',1,32767)
            _int(row['amount'],'Panache countdown',1,5)
            del values['PanachePower.damage']
        if key.startswith('monster:') and key.endswith(':GremlinThief'):
            if values.get('GremlinThief.thiefDamage') != (10 if snapshot['ascension_level'] >= 2 else 9):
                raise HeadlessContractError('Gremlin Thief damage conflicts with ascension')
            del values['GremlinThief.thiefDamage']  # Constructor constant, including on fresh summons.
        if key.startswith('monster:') and key.endswith(':GremlinTsundere'):
            asc = snapshot['ascension_level']
            for field, amount in (('bashDmg', 8 if asc >= 2 else 6),
                                  ('blockAmt', 11 if asc >= 17 else 8 if asc >= 7 else 7)):
                name = 'GremlinTsundere.' + field
                if type(values.get(name)) is not int or values[name] != amount:
                    raise HeadlessContractError('Shield Gremlin amount conflicts with ascension: ' + field)
                del values[name]  # Constructor constants already used by native, including on births.
        if key.startswith('monster:') and key.rsplit(':',1)[-1] in {'Exploder','Spiker'}:
            kind=key.rsplit(':',1)[-1]
            field,limit=('Exploder.turnCount',3) if kind=='Exploder' else ('Spiker.thornsCount',6)
            _int(values.get(field), field, 0, limit)
            del values[field]  # Source counter imported into the compared native miscInfo.
        if key.startswith('monster:') and key.endswith(':Transient'):
            _int(values.get('Transient.count'), 'Transient.count', 0, 6)
            del values['Transient.count']  # Bound to intent and exact native state by the importer.
        if key.startswith('monster:') and key.endswith(':GiantHead'):
            _int(values.get('GiantHead.count'), 'GiantHead.count', -6, 4)
            del values['GiantHead.count']  # The exact counter is advanced and compared in native miscInfo.
        if key.startswith('monster:') and key.endswith(':Nemesis'):
            values.pop('Nemesis.fireTimer', None)  # Particle-emission clock, not a combat timer.
            _int(values.get('Nemesis.scytheCooldown'), 'Nemesis.scytheCooldown', -1000000, 2)
            del values['Nemesis.scytheCooldown']  # Advances in the exact native key.
        if key.startswith('monster:') and key.endswith(':Maw'):
            _int(values.get('Maw.turnCount'), 'Maw.turnCount', 2, 1000000)
            if type(values.get('Maw.roared')) is not bool:
                raise HeadlessContractError('Invalid Maw roar latch')
            # Both fields are advanced and compared in native miscInfo.
            del values['Maw.turnCount'], values['Maw.roared']
        if key.startswith('monster:') and key.endswith(':WrithingMass'):
            if type(values.get('WrithingMass.usedMegaDebuff')) is not bool:
                raise HeadlessContractError('Invalid Writhing Mass curse latch')
            del values['WrithingMass.usedMegaDebuff']  # Compared in native miscInfo.
        if key.startswith('monster:') and key.endswith(':AwakenedOne'):
            for field in ('form1', 'firstTurn', 'animateParticles', 'saidPower'):
                name = 'AwakenedOne.' + field
                if type(values.get(name)) is not bool:
                    raise HeadlessContractError('Invalid Awakened One flag: ' + field)
                del values[name]  # Phase/first turn are compared natively; the other latches are visual/audio only.
            timer = values.get('AwakenedOne.fireTimer')
            if type(timer) not in (int, float) or not math.isfinite(timer):
                raise HeadlessContractError('Invalid Awakened One particle timer')
            del values['AwakenedOne.fireTimer']
        for name in list(values):
            field = name.rsplit(".", 1)[-1]
            if key.startswith("monster:") and key.endswith(":GremlinWizard") and field == "currentCharge":
                _int(values[name], "GremlinWizard.currentCharge", 0, 3)
                del values[name]; continue  # Imported exactly into the compared native miscInfo, not inferred from history.
            if key.startswith('monster:') and key.endswith(':SlaverRed') and field == 'usedEntangle':
                if type(values[name]) is not bool: raise HeadlessContractError('Invalid red slaver entangle latch')
                del values[name]; continue  # Imported into, advanced, and compared in native miscInfo.
            if key.startswith("monster:") and key.endswith(":BookOfStabbing") and field == "stabCount":
                count = _int(values[name], "BookOfStabbing.stabCount", 1)
                if row["creature"]["current_health"] > 0 and row["next_move"] == 1 and row.get("move_hits") != count:
                    raise HeadlessContractError("Book of Stabbing private count conflicts with multi-stab intent")
                del values[name]; continue  # Imported exactly into native miscInfo; prediction/key compares its progression.
            if key.startswith("monster:") and key.endswith(":Champ"):
                if field in ("forgeTimes", "numTurns"):
                    _int(values[name], "Champ."+field, 0, 2 if field == "forgeTimes" else 1000000)
                    del values[name]; continue  # Exact counters occupy the compared native miscInfo.
                if field in ("thresholdReached", "firstTurn"):
                    if type(values[name]) is not bool: raise HeadlessContractError("Invalid Champ flag: "+field)
                    del values[name]; continue  # Phase is modeled; first-turn speech latch has explicit progression below.
                if field == "forgeThreshold" and values[name] != 2:
                    raise HeadlessContractError("Invalid Champ forge threshold")
            if key.startswith("monster:") and key.endswith(":Byrd") and field == "isFlying":
                if row["creature"]["current_health"] > 0 and values[name] is not any(v["power_id"] == "Flight" for v in row["creature"]["powers"]):
                    raise HeadlessContractError("Byrd flight latch conflicts with Flight power")
                del values[name]; continue  # Death clears powers but leaves this now-unused latch unchanged.
            if row.get("power_id") == "Flight" and field == "storedAmount":
                if values[name] != (4 if snapshot["ascension_level"] >= 17 else 3): raise HeadlessContractError("Flight stored amount conflicts with ascension")
                del values[name]; continue
            if key.startswith("monster:") and key.endswith(":SphericGuardian") and field == "secondMove":
                if values[name] is not (row["next_move"] == 2): raise HeadlessContractError("Spheric Guardian second-move latch conflicts with move")
                del values[name]; continue
            if key.startswith("monster:") and key.endswith(":Chosen") and field == "usedHex":
                if values[name] is not (4 in row["move_history"]): raise HeadlessContractError("Chosen Hex latch conflicts with source history")
                del values[name]; continue
            if key.startswith("monster:") and key.rsplit(":", 1)[-1] in {"SpikeSlime_L", "AcidSlime_L"} and field == "splitTriggered":
                if row["creature"]["current_health"] > 0 and values[name] is not (row["next_move"] == 3): raise HeadlessContractError("Large slime split latch conflicts with move")
                del values[name]; continue  # The split move fully determines its pending behavior.
            if key.startswith("monster:") and key.endswith(":Hexaghost") and field in {"orbActiveCount", "burnUpgraded"}:
                explicit = "active_orbs" if field == "orbActiveCount" else "burn_upgraded"
                if row.get(explicit) != values[name]: raise HeadlessContractError("Hexaghost explicit/private state conflict")
                del values[name]; continue  # Both are restored into the native monster key.
            if key.startswith("monster:") and key.endswith(":TheGuardian") and field in {"dmgTaken", "dmgThreshold", "isOpen", "closeUpTriggered"}:
                private = row["_private"]; threshold = private["TheGuardian.dmgThreshold"]
                power = next((v for v in row["creature"]["powers"] if v["power_id"] == "Mode Shift"), None)
                opened = power is not None
                tail=snapshot.get('grid_card_select_screen_state',{}).get('action_queue',[])
                pending=(snapshot.get('screen_name')=='GRID' and len(tail)>=2 and tail[-1].get('state_class')=='savestate.actions.HeadbuttMonsterTailState'
                         and tail[-1].get('state')=={'kind':'guardian_defensive_mode','owner_index':owner_slot})
                if (private["TheGuardian.isOpen"] is not opened or private["TheGuardian.closeUpTriggered"] is not (pending or not opened)
                        or pending and (not opened or power['amount']>0)
                        or private["TheGuardian.dmgTaken"] != (threshold-power["amount"] if opened and not pending else 0)):
                    raise HeadlessContractError("Guardian private threshold conflicts with modeled Mode Shift")
                del values[name]; continue  # Threshold is imported into miscInfo; remaining damage is a native power.
            if key == "relic:Red Skull" and field == "isActive":
                creature = p["creature"]
                if values[name] is not (creature["current_health"] <= creature["max_health"]/2):
                    raise HeadlessContractError("Red Skull activation conflicts with HP")
                del values[name]; continue  # Strength and HP are both restored in the native key.
            if key == "relic:Centennial Puzzle" and name == "CentennialPuzzle.usedThisCombat":
                if type(values[name]) is not bool: raise HeadlessContractError("Invalid Centennial Puzzle latch")
                del values[name]; continue  # Restored as native once-per-combat relic availability, in the compared relic bits.
            if key == "relic:Necronomicon" and name == "Necronomicon.activated":
                if type(values[name]) is not bool:raise HeadlessContractError("Invalid Necronomicon latch")
                del values[name];continue # Turn latch is restored in the native key.
            if key.startswith("monster:") and key.endswith(":Dagger") and name=="SnakeDagger.firstMove":
                if values[name] is not False:raise HeadlessContractError("Unsettled Dagger first move")
                del values[name];continue # First roll is finished before a stable combat boundary.
            if key.startswith("monster:") and key.rsplit(":", 1)[-1] in {"Looter", "Mugger"}:
                if field in ("slashCount", "stolenGold"):
                    _int(values[name], field, 0)
                    del values[name]; continue  # Exact gold is imported; slash progression is checked below.
            if key.startswith("monster:") and key.endswith(":Lagavulin"):
                if field in ("idleCount", "debuffTurnCount"):
                    _int(values[name], "Lagavulin."+field, 0, 3 if field == "idleCount" else 2)
                    del values[name]; continue  # Exact per-action progression is checked separately below.
                if field in ("isOut", "isOutTriggered"):
                    expected = row["next_move"] != 5
                    if values[name] is not expected: raise HeadlessContractError("Lagavulin wake flag conflicts with current move")
                    del values[name]; continue  # Current move and ASLEEP power are in the native state key.
            if key.startswith("monster:") and key.endswith(":Cultist") and name in {"Cultist.saidPower", "Cultist.talky"}:
                del values[name]; continue  # Speech/SFX only; no gameplay branch.
            if key.startswith("monster:") and name in {"LouseNormal.isOpen", "LouseDefensive.isOpen"}:
                del values[name]; continue  # Animation posture, not Curl Up gameplay status.
            if row.get("power_id") == "Curl Up" and field == "triggered":
                if type(values[name]) is not bool: raise HeadlessContractError("Invalid Curl Up latch")
                # A multi-hit can trigger the latch, then kill its owner before
                # the queued RemoveSpecificPowerAction executes. The dead
                # monster cannot use that latch again; an alive triggered owner
                # still represents an unsupported in-flight state.
                if values[name] and (owner_hp is None or owner_hp > 0):
                    raise HeadlessContractError("Live monster has triggered Curl Up latch")
                del values[name]; continue  # Power presence/amount and owner HP are in the native key.
            if row.get("power_id") == "Ritual":
                if field == "onPlayer" and values[name] == key.startswith("player-power:"):
                    del values[name]; continue
                if field == "skipFirst" and values[name] == row.get("just_applied", False):
                    del values[name]; continue
            explicit = {"justApplied": "just_applied", "energyGainAmount": "energy_gain_amount"}.get(field)
            if explicit is not None and explicit in row:
                if values[name] != row[explicit]: raise HeadlessContractError("Conflicting explicit/private power state")
                del values[name]  # Already restored and compared in the native key.
        if values:
            if key in out and out[key] != values: raise HeadlessContractError("Conflicting source private state")
            out[key] = values
    for pile in ("master_deck", "hand", "draw_pile", "discard_pile", "exhaust_pile"):
        for c in p[pile]: add(("master:" if pile == "master_deck" else "combat:") + c["uuid"], c)
    for c in snapshot.get('hand_select_screen_state',{}).get('selected_cards',[]):
        add('combat:'+c['uuid'],c)
    for c in snapshot.get('grid_card_select_screen_state',{}).get('selected_cards',[]):
        add('combat:'+c['uuid'],c)
    for c in snapshot.get('hand_select_screen_state',{}).get('selected_cards',[]): add('combat:'+c['uuid'],c)
    for r in p["relics"]: add("relic:" + r["relic_id"], r)
    for power in p["creature"]["powers"]: add("player-power:" + power["power_id"], power)
    for i, m in enumerate(snapshot["cur_map_node_state"]["monster_data"]):
        position = m["creature"].get("draw_x", i)
        if m["creature"]["id"] in {"Looter","Mugger"}:
            position=f"slot{i}"  # Escaping animates draw_x; unchanged source slot and identity are bound by the native key.
        add(f'monster:{position}:{m["creature"]["id"]}', m,owner_slot=i)
        for power in m["creature"]["powers"]:
            add(f'monster-power:{position}:' + power["power_id"], power, owner_hp=m['creature']['current_health'])
    return out


def _private_match(expected, snapshot):
    actual = _private_invariants(snapshot)
    if any(actual.get(key) != value for key, value in expected.items()): return False
    for key in actual.keys()-expected.keys():
        # The checked native projection/roster/RNG proves these exact new slime
        # births. Their only unmapped fields are stable spawn coordinates and
        # the not-yet-triggered split latch; freeze these on subsequent actions.
        identity = key.rsplit(":", 1)[-1]
        if not key.startswith("monster:") or identity not in {"SpikeSlime_L", "AcidSlime_L"}: return False
        values = actual[key]
        if set(values) != {identity+".saveX", identity+".saveY"}: return False
        if any(type(values[identity+"."+axis]) not in (int, float) or not math.isfinite(values[identity+"."+axis]) for axis in ("saveX", "saveY")): return False
    return True


def _monster_turn_ran(before, after, command):
    if after['turn']!=before['turn']+1:return False
    if command['type']=='END':return True
    if command['type']!='CARD':return False
    hand = before.get('player_state', {}).get('hand', [])
    index = command.get('card_index')
    return type(index) is int and 0 <= index < len(hand) and hand[index]['card_id'] in {'Conclude', 'Meditate'}


def _lagavulin_progress(before, after, command):
    left = before["cur_map_node_state"]["monster_data"]; right = after["cur_map_node_state"]["monster_data"]
    for a, b in zip(left, right):
        if a["creature"]["id"] != "Lagavulin" or b["creature"]["current_health"] <= 0: continue
        x, y = a.get("_private", {}), b.get("_private", {})
        ended = _monster_turn_ran(before,after,command)
        for field in ("idleCount", "debuffTurnCount"):
            key = "Lagavulin."+field
            if key not in x or key not in y: return False
            expected = x[key]
            if ended:
                # Poison/orbs can wake it before takeTurn(), changing Sleep to STUN.
                # The actual executed move is bound by the compared native history.
                if len(b["move_history"]) < 2: return False
                executed = b["move_history"][-2]
                if field == "idleCount" and executed == 5: expected += 1
                if field == "debuffTurnCount" and executed == 3: expected += 1
                if field == "debuffTurnCount" and executed == 1: expected = 0
            if y[key] != expected: return False
    return True


def _thief_progress(before, after, command):
    for a, b in zip(before["cur_map_node_state"]["monster_data"], after["cur_map_node_state"]["monster_data"]):
        identity = a["creature"]["id"]
        if identity not in {"Looter", "Mugger"} or b["creature"]["current_health"] <= 0: continue
        key = identity+".slashCount"; x, y = a.get("_private", {}), b.get("_private", {})
        if key not in x or key not in y: return False
        expected = x[key]
        if _monster_turn_ran(before,after,command):
            if len(b["move_history"]) < 2: return False
            expected += b["move_history"][-2] in (1, 4)
        if y[key] != expected: return False
    return True


def _champ_progress(before, after, command):
    for a, b in zip(before["cur_map_node_state"]["monster_data"], after["cur_map_node_state"]["monster_data"]):
        if a["creature"]["id"] != "Champ" or b["creature"]["current_health"] <= 0: continue
        x, y = a.get("_private", {}), b.get("_private", {})
        key = "Champ.firstTurn"
        if key not in x or key not in y: return False
        took_turn = (_monster_turn_ran(before, after, command)
                     and y.get("Champ.numTurns") != x.get("Champ.numTurns"))
        if y[key] is not (False if took_turn else x[key]): return False
    return True


@dataclass(frozen=True)
class CombatFrame:
    run_id: str
    sequence: int
    raw_json: str
    state_json: str
    snapshot_json: str | None
    legal_json: tuple[str, ...]

    @property
    def state(self): return GameState.model_validate_json(self.state_json)
    @property
    def snapshot(self): return json.loads(self.snapshot_json) if self.snapshot_json is not None else None
    @property
    def token(self): return hashlib.sha256((self.run_id + "\n" + self.raw_json).encode()).hexdigest()
    @property
    def legal_actions(self): return tuple(GameAction.model_validate_json(s) for s in self.legal_json)


@dataclass(frozen=True)
class CombatPlan:
    before: CombatFrame
    action_json: str
    command_json: str
    prediction_json: str

    @property
    def api_action(self): return GameAction.model_validate_json(self.action_json)
    @property
    def native_method(self): return {"CARD": "playCard", "POTION": "usePotion", "END": "endTurn", "HAND_SELECT":"chooseHandCard", "GRID_SELECT":"chooseGridCard", "HAND_SELECT_CONFIRM":"confirmHandSelect"}[json.loads(self.command_json)["type"]]
    @property
    def native_args(self):
        c = json.loads(self.command_json)
        if c['type'] in ('HAND_SELECT','GRID_SELECT'): return (c['card_index'],)
        if c['type']=='HAND_SELECT_CONFIRM': return ()
        return () if c["type"] == "END" else (c["card_index"] if c["type"] == "CARD" else c["potion_index"], c["monster_index"])


class HeadlessCombatBridge:
    def __init__(self): self._mapper = HeadlessStateMapper()

    def observe(self, raw, *, run_id, sequence):
        _text(run_id, "combat run/branch"); _int(sequence, "combat sequence", 0)
        frozen = _json(raw); raw = json.loads(frozen)
        trial = copy.deepcopy(self._mapper)
        state = trial.map_state(raw, run_id=run_id, sequence=sequence)
        snapshot, legal = None, []
        elixir=is_elixir_selection(raw);gambler=is_gambler_selection(raw) or elixir;liquid=is_liquid_selection(raw);headbutt=is_headbutt_selection(raw);fetch=is_fetch_selection(raw);single=is_single_hand(raw);selection=single or gambler or liquid or headbutt or fetch
        if raw["phase"] == "PLAYER_TURN" or selection:
            combat = raw.get("combat", {})
            if combat.get("schema") != SCHEMA: raise HeadlessContractError("Missing structured combat snapshot")
            snapshot = combat["snapshot"]; validate_rng(snapshot)
            p = snapshot["player_state"]
            if (snapshot["floor_num"], snapshot["act_num"], snapshot["ascension_level"], p["chosen_class_name"]) != (state.floor, state.act, state.ascension_level, state.character):
                raise HeadlessContractError("Snapshot identity mismatch")
            _int(snapshot["turn"], "combat turn", 1)
            if (snapshot['screen_name']!=('HAND_SELECT' if gambler or single else 'GRID' if liquid or headbutt or fetch else 'NONE') or snapshot['is_screen_up'] is not selection
                    or snapshot["cur_map_node_state"]["phase_name"] != "COMBAT" or state.room_phase != "COMBAT"):
                raise HeadlessContractError("Snapshot is not a normal combat decision")
            if single:
                evidence=_single_hand_evidence(snapshot);choice=evidence['pending_card']['id']
                purpose={'True Grit':'exhaust','Burning Pact':'exhaust','Armaments':'upgrade','Dual Wield':'duplicate_combat','Forethought':'forethought'}[choice]
                controls=raw['interaction']['selection'];ui=raw['cardSelection']
                any_number=evidence.get('any_number',False)
                single_confirm=any_number or bool(evidence['selected'])
                if (controls.get('purpose')!=purpose or ui.get('kind')!='hand' or controls.get('kind')!='hand'
                        or controls.get('count')!=evidence.get('count',1) or controls.get('locked') is not False
                        or controls.get('any_number') is not any_number or controls.get('can_pick_zero') is not any_number
                        or controls.get('can_confirm') is not single_confirm
                        or [c['uuid'] for c in ui['options']]!=evidence['remaining']
                        or [c['uuid'] for c in ui['selected']]!=evidence['selected']
                        or choice=='Dual Wield' and controls.get('copies')!=evidence['copies']):
                    raise HeadlessContractError('Single-hand visible/hidden controls or UUID mismatch')
            if gambler:
                evidence=_gambler_evidence(snapshot,elixir=elixir)
                controls=raw.get('interaction',{}).get('selection',{})
                ui=raw.get('cardSelection',{})
                if (controls.get('purpose')!=('exhaust' if elixir else 'gamble') or ui.get('kind')!='hand'
                        or [c['uuid'] for c in ui.get('options',[])]!=evidence['remaining']
                        or [c['uuid'] for c in ui.get('selected',[])]!=evidence['selected']):
                    raise HeadlessContractError('Gambler UI/snapshot identity mismatch')
                expected_controls={'kind':'hand','count':99,'locked':False,'can_confirm':True,'any_number':True,'can_pick_zero':True,'auto_confirm':False}
                if any(type(controls.get(k)) is not type(v) or controls[k]!=v for k,v in expected_controls.items()):
                    raise HeadlessContractError('Gambler UI control mismatch')
                if [(c['uuid'],c['card_id'],c['times_upgraded'],c['cost'],c['cost_for_turn']) for c in snapshot['hand_select_screen_state']['selected_cards']]!=[(c.uuid,c.id,c.upgrades,c.cost,c.cost_for_turn) for c in state.grid_selected_cards]:
                    raise HeadlessContractError('Gambler selected card attributes mismatch')
            if liquid or headbutt or fetch:
                evidence=_fetch_evidence(snapshot) if fetch else _headbutt_evidence(snapshot) if headbutt else _liquid_evidence(snapshot);controls=raw.get('interaction',{}).get('selection',{});ui=raw.get('cardSelection',{})
                if (controls.get('purpose')!=(evidence['source'].lower().replace(' ','_') if fetch else 'topdeck' if headbutt else 'liquid_memories') or ui.get('kind')!='grid'
                        or [c['uuid'] for c in ui.get('options',[])]!=evidence['options']
                        or [c['uuid'] for c in ui.get('selected',[])]!=evidence['selected']):
                    raise HeadlessContractError('Liquid Memories UI/snapshot identity mismatch')
                expected_controls={'kind':'grid','count':evidence['count'],'locked':False,'can_confirm':False,
                                   'any_number':False,'can_pick_zero':False,'auto_confirm':True}
                if any(type(controls.get(k)) is not type(v) or controls[k]!=v for k,v in expected_controls.items()):
                    raise HeadlessContractError('Liquid Memories UI controls mismatch')
                if fetch and [(c['uuid'],c['card_id'],c['times_upgraded'],c['cost'],c['cost_for_turn']) for c in snapshot['grid_card_select_screen_state']['group_cards']]!=[(c.uuid,c.id,c.upgrades,c.cost,c.cost_for_turn) for c in state.grid_cards]:
                    raise HeadlessContractError('Fetch option attributes do not match the source cards')
                if [(c['uuid'],c['card_id'],c['times_upgraded'],c['cost'],c['cost_for_turn']) for c in snapshot['grid_card_select_screen_state']['selected_cards']]!=[(c.uuid,c.id,c.upgrades,c.cost,c.cost_for_turn) for c in state.grid_selected_cards]:
                    raise HeadlessContractError('Liquid Memories selected card attributes mismatch')
            if (p["card_in_use"] is not None and not headbutt and not fetch and not single) or (p["limbo"] and not headbutt) or p["is_ending_turn"] is not False or snapshot["end_turn_queued"] is not False:
                raise HeadlessContractError("In-flight combat state")
            for pile, observed in (("master_deck", state.deck), ("hand", state.hand), ("draw_pile", state.draw_pile), ("discard_pile", state.discard_pile), ("exhaust_pile", state.exhaust_pile)):
                rows = p[pile]
                if [(c["uuid"], c["card_id"], c["times_upgraded"], c["cost"], c["cost_for_turn"]) for c in rows] != [(c.uuid, c.id, c.upgrades, c.cost, c.cost_for_turn) for c in observed]:
                    raise HeadlessContractError("Observation/snapshot card identity mismatch")
            # Public-state equality also verifies energy, HP, all monster slots, and potions.
            view = projection(snapshot)
            if (view["hp"], view["max_hp"], view["block"], view["energy"], p["creature"]["gold"]) != (state.player.current_hp, state.player.max_hp, state.player.block, state.player.energy, state.player.gold):
                raise HeadlessContractError("Snapshot player mismatch")
            if [(r["relic_id"], r["counter"]) for r in p["relics"]] != [(r.id, r.counter) for r in state.relics]:
                raise HeadlessContractError("Relic state differs")
            if view["monsters"] != [{"id": m.id, "hp": m.current_hp, "max_hp": m.max_hp, "block": m.block} for m in state.monsters]:
                raise HeadlessContractError("Original monster target slots differ")
            if view["potions"] != [p.id for p in state.potions]: raise HeadlessContractError("Potion slots differ")
            for c in combat["legal_actions"]:
                api = _api(c)
                if c["type"] == "CARD":
                    i = c["card_index"]
                    if i >= len(p["hand"]) or c.get("uuid") != p["hand"][i]["uuid"]: raise HeadlessContractError("Stale legal card UUID")
                    target = state.hand[i].target in ("ENEMY", "SELF_AND_ENEMY")
                    if target != (c["monster_index"] >= 0): raise HeadlessContractError("Card target type mismatch")
                elif c["type"] == "POTION":
                    i = c["potion_index"]
                    if i >= len(state.potions) or state.potions[i].is_empty or state.potions[i].id != c.get("id"): raise HeadlessContractError("Stale legal potion slot")
                    if state.potions[i].requires_target != (c["monster_index"] >= 0): raise HeadlessContractError("Potion target type mismatch")
                elif c['type']=='HAND_SELECT':
                    i=c['card_index']
                    if not (single or gambler) or i>=len(p['hand']) or c.get('uuid')!=p['hand'][i]['uuid']: raise HeadlessContractError('Stale Gambler legal UUID')
                elif c['type']=='HAND_SELECT_CONFIRM' and not (gambler or single and single_confirm): raise HeadlessContractError('Unexpected hand confirmation')
                elif c['type']=='GRID_SELECT':
                    i=c['card_index']
                    if not (liquid or headbutt or fetch) or i>=len(evidence['options']) or c.get('uuid')!=evidence['options'][i]:
                        raise HeadlessContractError('Stale Liquid Memories legal UUID')
                if c.get("monster_index", -1) >= 0:
                    i = c["monster_index"]
                    monsters = snapshot["cur_map_node_state"]["monster_data"]
                    if i >= len(monsters) or monsters[i]["creature"].get("is_dead") or monsters[i].get("escaped") or monsters[i]["creature"].get("is_escaping"):
                        raise HeadlessContractError("Illegal monster target")
                encoded = _json(api)
                if encoded in legal: raise HeadlessContractError("Duplicate legal combat action")
                legal.append(encoded)
            if single:
                required={_json({'type':'choose','choice_index':i}) for i in range(len(p['hand']))}
                if single_confirm:required.add(_json({'type':'proceed'}))
                if set(legal)!=required:raise HeadlessContractError('Incomplete single-hand legal actions')
            if gambler:
                required={_json({'type':'choose','choice_index':i}) for i in range(len(p['hand']))}|{_json({'type':'proceed'})}
                if set(legal)!=required: raise HeadlessContractError('Incomplete Gambler legal controls')
            if liquid or headbutt or fetch:
                required={_json({'type':'choose','choice_index':i}) for i in range(len(evidence['options']))}
                if set(legal)!=required: raise HeadlessContractError('Incomplete Liquid Memories legal controls')
            data = state.model_dump(exclude_unset=True)
            for i, c in enumerate(data["hand"]): c["is_playable"] = any(a.get("type") == "play" and a["card_index"] == i for a in map(json.loads, legal))
            for i, potion in enumerate(data["potions"]): potion["can_use"] = any(a.get("type") == "potion" and a["potion_index"] == i for a in map(json.loads, legal))
            data["is_end_turn_button_enabled"] = _json({"type": "end_turn"}) in legal
            if single:data.update(can_proceed=single_confirm,grid_purpose=purpose,grid_num_cards=evidence.get('count',1),choice_list=[c.name for c in state.hand])
            if gambler:data.update(can_proceed=True,grid_purpose='exhaust' if elixir else 'gamble',grid_num_cards=99,choice_list=[c.name for c in state.hand])
            if liquid or fetch:data.update(can_proceed=False,grid_purpose=evidence['source'].lower() if fetch else 'liquid_memories',grid_num_cards=evidence['count'],choice_list=[c.name for c in state.grid_cards])
            state = GameState(**data)
        result = CombatFrame(run_id, sequence, frozen, _json(state.model_dump(exclude_unset=True)), _json(snapshot) if snapshot is not None else None, tuple(legal))
        self._mapper = trial
        return result

    @staticmethod
    def prepare(frame, response):
        if frame.snapshot is None or response.get("supported") is not True or response.get("rng_restore_mode") != "exact_state":
            raise HeadlessContractError("Expected exact-state native search response")
        commands = response.get("commands", [])
        if len(commands) != 1: raise HeadlessContractError("Expected one native command")
        row = commands[0]; command = json.loads(row["command"]); api = _json(_api(command))
        if api not in frame.legal_json: raise HeadlessContractError("Search command is not natively legal")
        raw=json.loads(frame.raw_json)
        if is_liquid_selection(raw) and command.get('type')!='GRID_SELECT': raise HeadlessContractError('Wrong Liquid Memories command family')
        if is_fetch_selection(raw) and command.get('type')!='GRID_SELECT': raise HeadlessContractError('Wrong fetch command')
        if is_headbutt_selection(raw) and command.get('type')!='GRID_SELECT': raise HeadlessContractError('Wrong Headbutt command family')
        if (is_gambler_selection(raw) or is_elixir_selection(raw) or is_single_hand(raw)) and command.get('type') not in ('HAND_SELECT','HAND_SELECT_CONFIRM'): raise HeadlessContractError('Wrong native hand selection command family')
        if row.get("expected_before") != projection(frame.snapshot) or row.get("expected_context") != context(frame.snapshot):
            raise HeadlessContractError("Search response refers to another snapshot")
        for key in ("expected_after", "expected_screen", "expected_native_key", "expected_exact_rng", "unmodelled_rng"):
            if key not in response: raise HeadlessContractError("Missing prediction evidence: " + key)
        if response["expected_native_key"] is None and response.get("terminal_prediction") not in ("victory", "defeat") and response.get("expected_screen") not in ("GRID", "HAND_SELECT", "CARD_REWARD"):
            raise HeadlessContractError("Native boundary requires a dedicated continuation contract")
        if is_native_combat_selection(raw) or response.get('expected_selection') is not None or _liquid_potion(frame.snapshot,command):
            uuids=response.get('expected_card_uuids')
            if not isinstance(uuids,dict) or set(uuids)!={'hand','draw_pile','discard_pile','exhaust_pile'}:
                raise HeadlessContractError('Gambler prediction needs exact card UUIDs')
            opens_menu=_omniscience_scry_source(raw,command,response) is not None
            if response.get('expected_screen') in ('HAND_SELECT','GRID') and not opens_menu and not isinstance(response.get('expected_selection'),dict):
                raise HeadlessContractError('Gambler prediction needs selection evidence')
            guard=response.get('expected_selection_native_key')
            if response.get('expected_screen') in ('HAND_SELECT','GRID') and not opens_menu and (not isinstance(guard,list) or not guard):
                raise HeadlessContractError('Gambler prediction needs full native guard key')
        prediction = copy.deepcopy(response)
        prediction["source_private_invariants"] = _private_invariants(frame.snapshot)
        return CombatPlan(frame, api, _json(command), _json(prediction))

    @staticmethod
    def validate_before(plan, current):
        return current.run_id == plan.before.run_id and current.sequence >= plan.before.sequence and current.token == plan.before.token


def validate_selection_import(frame, fingerprint):
    """Bind a passive selection handoff to a native import of this exact after-frame."""
    raw=json.loads(frame.raw_json);s=frame.snapshot
    if s is None or not is_native_combat_selection(raw):
        raise HeadlessContractError('Expected a native combat selection after-frame')
    validate_rng(s);_private_invariants(s)
    if not isinstance(fingerprint,dict):raise HeadlessContractError('Missing native selection import evidence')
    guard=fingerprint.get('selection_native_key')
    if (fingerprint.get('supported') is not True or fingerprint.get('rng_restore_mode')!='exact_state'
            or fingerprint.get('projection')!=projection(s)
            or fingerprint.get('selection')!=_native_selection_evidence(s)
            or fingerprint.get('exact_rng')!={k:s['_headless_rng']['streams'][k] for k in MODELLED}
            or not isinstance(guard,list) or not guard
            or 'native_key' not in fingerprint or fingerprint['native_key'] is not None):
        raise HeadlessContractError('Native selection after-frame import evidence mismatch')


_BATTLE_PILES = ('hand','draw_pile','discard_pile','exhaust_pile')


def _known_combat_uuids(snapshot):
    p=snapshot['player_state']
    seen={c['uuid'] for pile in (*_BATTLE_PILES,'master_deck') for c in p[pile]}
    pending=p.get('card_in_use')
    if pending:seen.add(pending['uuid'])
    for key in ('grid_card_select_screen_state','hand_select_screen_state'):
        view=snapshot.get(key) or {}
        for row in view.get('card_queue_state',[]):
            if row.get('card'):seen.add(row['card']['uuid'])
        seen.update(c['uuid'] for c in view.get('selected_cards',[]))
        seen.update(c['uuid'] for c in view.get('current_action_state',{}).get('state',{}).get('hidden',[]))
    return seen


def _selection_lineage(before, after, expected, actual):
    if actual==expected:return True
    if not (isinstance(expected,dict) and isinstance(actual,dict)
            and expected.get('source')==actual.get('source')=='Omniscience'):
        return False
    want=copy.deepcopy(expected)
    seen=_known_combat_uuids(before)|{c['uuid'] for pile in _BATTLE_PILES for c in after['player_state'][pile]}
    for predicted,observed in zip(
            [want['pending_card'],*want.get('autoplay_queue',[])],
            [actual['pending_card'],*actual.get('autoplay_queue',[])]):
        uid=observed['uuid']
        if predicted['uuid'] is None:
            if not predicted.get('purge') or not isinstance(uid,str) or uid in seen:return False
            predicted['uuid']=uid
        seen.add(uid)
    return want==actual


def _normal_card_lineage(before, after, expected):
    """Existing cards keep their exact UUID; a generated slot must have a fresh identity."""
    if not isinstance(expected,dict) or set(expected)!=set(_BATTLE_PILES): return False
    source=_known_combat_uuids(before)
    seen=set()
    for pile in _BATTLE_PILES:
        observed=[c['uuid'] for c in after['player_state'][pile]]
        slots=expected[pile]
        if not isinstance(slots,list) or len(observed)!=len(slots): return False
        for uid,want in zip(observed,slots):
            if uid in seen or (uid in source if want is None else uid!=want): return False
            seen.add(uid)
    return True


def _omniscience_scry_source(raw, command, predicted):
    source=raw.get('interaction',{}).get('selection',{})
    target=predicted.get('expected_menu_picker')
    if (source.get('purpose')!='omniscience' or command['type']!='GRID_SELECT'
            or predicted.get('expected_screen')!='GRID' or not isinstance(target,dict)
            or (target.get('kind'),target.get('purpose'))!=('grid','scry')):
        return None
    options=raw['cardSelection']['options']
    idx=command['card_index']
    if 0<=idx<len(options) and options[idx]['id']=='CutThroughFate':
        card=options[idx]
        player=raw['player']
        count=2+card['timesUpgraded']+2*any(r['id']=='GoldenEye' for r in player['relics'])
        draw=[c for c in reversed(player['drawPile']) if c['uuid']!=card['uuid']]
        specs=[{'id':c['id'],'upgrades':c['timesUpgraded'],'uuid':c['uuid']} for c in draw[:count]]
        if specs and type(target.get('count')) is int and target['count']==count and target.get('options')==specs:
            return card['uuid']
    return None


def _omniscience_victory(before, after, command, predicted, sequence):
    # Rewards clear combat history. Bind the grid request to the chosen card
    # before cleanup and check the surviving deck, player and combat RNG.
    if (before.get('interaction',{}).get('selection',{}).get('purpose')!='omniscience'
            or command['type']!='GRID_SELECT' or predicted.get('terminal_prediction')!='victory'
            or after.get('phase')!='REWARD'):return False
    idx=command['card_index']
    options=before['cardSelection']['options']
    if not 0<=idx<len(options):return False
    selected=options[idx]
    if selected['type']!='ATTACK':return False
    lesson=selected['id']=='LessonLearned'
    previous=before['player'];player=after['player']
    receipt=after.get('driver_receipt',{})
    forecast=predicted['expected_after']
    decks=(previous['masterDeck'],player['masterDeck'])
    bound=receipt.get('card_uuid')==selected['uuid']
    if (receipt.get('method')!='chooseGridCard' or receipt.get('args')!=[idx] or receipt.get('sequence')!=sequence
            or not bound and not (lesson and receipt.get('card_uuid') is None)
            or [c['uuid'] for c in decks[0]]!=[c['uuid'] for c in decks[1]]
            or any(c['id']!=d['id'] or c['misc']!=d['misc'] for c,d in zip(*decks))
            or sum(c['timesUpgraded']!=d['timesUpgraded'] for c,d in zip(*decks))!=int(lesson)
            or not lesson and (decks[0]!=decks[1] or player['gold']!=previous['gold'])
            or (player['hp'],player['maxHp'])!=(forecast['hp'],forecast['max_hp'])
            or [p['id'] for p in player['potions']]!=forecast['potions']):return False
    actual=after['combat_receipt']['rng']['streams']
    exact=predicted['expected_exact_rng']
    if any(actual[k]!=exact[k] for k in ('ai','card_random','headless','misc','monster_hp','shuffle')):return False
    extras=predicted['unmodelled_rng']
    if any(actual[k]!=extras[k] for k in ('monster','map','event','merchant')):return False
    return [(c['id'],c['timesUpgraded'],c['cost'],c['costForTurn']) for c in decks[1]]==[
        (c['id'],c['upgrades'],c['cost'],c['turn_cost']) for c in predicted['expected_master_deck']]


def _menu_picker_match(plan, raw, predicted):
    """Bind a menu-routed picker to its predicted options and stable public state."""
    target=predicted['expected_menu_picker']; view=raw.get('cardSelection',{})
    controls=raw.get('interaction',{}).get('selection',{})
    if (any(controls.get(k)!=target[k] for k in ('kind','purpose','count'))
            or controls.get('locked') is not False or controls.get('can_confirm') is not True
            or controls.get('can_pick_zero') is not True or controls.get('any_number') is not True
            or view.get('kind')!=target['kind'] or view.get('selected')!=[]):return False
    p=raw['player'];forecast=predicted['expected_after']
    if (raw['combat_receipt']['turn']!=forecast['turn']
            or any(p[key]!=forecast[k] for key,k in
                (('hp','hp'),('maxHp','max_hp'),('block','block'),('energy','energy')))
            or [x['id'] for x in p['potions']]!=forecast['potions']):return False
    if [(m['id'],m['hp'],m['maxHp'],m['block']) for m in raw['monsters'] if m['hp']>0]!=[
            (m['id'],m['hp'],m['max_hp'],m['block']) for m in forecast['monsters'] if m['hp']>0]:return False
    raw_piles=dict(zip(_BATTLE_PILES,('hand','drawPile','discardPile','exhaustPile')))
    cards={k:p[v] for k,v in raw_piles.items()}
    if not _normal_card_lineage(plan.before.snapshot,{'player_state':cards},predicted.get('expected_card_uuids')):
        return False
    for pile,actual in cards.items():
        if [(c['id'],c['timesUpgraded'],c['cost'],c['costForTurn']) for c in actual]!=[
              (c['id'],c['upgrades'],c['cost'],c['turn_cost']) for c in forecast[pile]]:return False
    expected=target['options'];options=view.get('options',[])
    pile=cards['hand'] if target['kind']=='hand' else cards['draw_pile'][::-1][:len(expected)]
    return (len(expected)==len(options)==len(pile) and all(
        choice['uuid']==card['uuid'] and (choice['id'],choice['timesUpgraded'])==(spec['id'],spec['upgrades'])
        and (spec['uuid'] is None or spec['uuid']==choice['uuid'])
        for spec,choice,card in zip(expected,options,pile)))


def verify_combat_effect(plan, after, fingerprint):
    """Fingerprint must come from importing this exact observed snapshot, not an ACK."""
    if after.run_id != plan.before.run_id or after.sequence <= plan.before.sequence: return "REJECTED"
    if after.token == plan.before.token: return "PENDING"
    predicted = json.loads(plan.prediction_json)
    gambler=(is_native_combat_selection(json.loads(plan.before.raw_json)) or predicted.get('expected_selection') is not None
             or _liquid_potion(plan.before.snapshot,json.loads(plan.command_json)))
    if after.snapshot is None:
        original=json.loads(plan.before.raw_json)
        command=json.loads(plan.command_json)
        source=_omniscience_scry_source(original,command,predicted)
        raw = json.loads(after.raw_json); before = plan.before.state; state = after.state
        if gambler and source is None:
            try:
                if not _omniscience_victory(original,raw,command,predicted,after.sequence):return 'UNVERIFIED'
            except (KeyError,TypeError,ValueError):return 'UNVERIFIED'
        receipt = raw.get("combat_receipt", {})
        if (before.floor, before.act, before.character) != (state.floor, state.act, state.character): return "REJECTED"
        outcome = predicted.get("terminal_prediction")
        if predicted.get("expected_screen") in ("GRID", "HAND_SELECT", "CARD_REWARD") and state.screen_type == predicted["expected_screen"] and state.room_phase == "COMBAT":
            cause = command["type"] != "CARD" or plan.before.snapshot["player_state"]["hand"][command["card_index"]]["uuid"] in receipt.get("played_card_uuids", [])
            if source is not None:
                previous=original['combat_receipt']
                cause=(receipt.get('played_card_uuids')==previous['played_card_uuids']+[source]
                       and receipt.get('room_class')==previous['room_class'] and receipt.get('battle_over') is False)
            streams = receipt.get("rng", {}).get("streams", {})
            exact = {k: streams.get(k) for k in MODELLED}
            if predicted.get('expected_menu_picker') is not None:
                extras={k:streams.get(k) for k in STREAMS if k not in MODELLED}
                try:matched=_menu_picker_match(plan,raw,predicted)
                except (KeyError,TypeError,ValueError):matched=False
                return "APPLIED" if cause and exact==predicted["expected_exact_rng"] and extras==predicted['unmodelled_rng'] and matched else "UNVERIFIED"
            if predicted.get('expected_discovery') is not None:
                d=predicted['expected_discovery'];reward=raw.get('interaction',{}).get('card_reward',{})
                cards=raw.get('cardRewardOptions',[])
                if (reward.get('source_action')!='DiscoveryAction' or reward.get('free_for_turn') is not True
                        or reward.get('copies')!=d['copies'] or [c.get('id') for c in cards]!=d['offers']
                        or len({c.get('uuid') for c in cards})!=3 or any(not c.get('uuid') for c in cards)
                        or {k:streams.get(k) for k in STREAMS if k not in MODELLED}!=predicted['unmodelled_rng']):return 'UNVERIFIED'
            return "APPLIED" if cause and exact == predicted["expected_exact_rng"] else "UNVERIFIED"
        if outcome == "victory":
            original = json.loads(plan.before.raw_json)
            causal = receipt.get("room_class") == original.get("room") and receipt.get("battle_over") is True and receipt.get("room_phase") == "COMPLETE" and receipt.get("active_monsters") == 0
            command = json.loads(plan.command_json)
            if command["type"] == "CARD":
                uid = plan.before.snapshot["player_state"]["hand"][command["card_index"]]["uuid"]
                driver = raw.get("driver_receipt", {})
                card_bound = uid in receipt.get("played_card_uuids", []) or (driver.get("method") == "playCard" and driver.get("card_uuid") == uid and driver.get("sequence") == after.sequence)
                causal = causal and card_bound
            else:card_bound = True
            # Elite reward generation advances card/potion/relic/treasure RNG,
            # but Reptomancer's death and the reward screen do not advance misc.
            if causal and receipt.get('room_class')=='MonsterRoomElite' and any(
                    m['creature']['id']=='Reptomancer' for m in plan.before.snapshot['cur_map_node_state']['monster_data']):
                causal=receipt.get('rng',{}).get('streams',{}).get('misc')==predicted['expected_exact_rng']['misc']
            if not causal and card_bound and raw.get('phase')=='EVENT':
                from .event_receipts import colosseum_slavers_return
                played=(plan.before.snapshot['player_state']['hand'][command['card_index']]['card_id']
                        if command['type']=='CARD' else None)
                try: return 'APPLIED' if colosseum_slavers_return(original,raw,predicted,played) else 'UNVERIFIED'
                except (KeyError,TypeError,ValueError,AttributeError): return 'UNVERIFIED'
            return "APPLIED" if causal and state.player.current_hp > 0 and receipt.get("player_dead") is False else "UNVERIFIED"
        if outcome == "defeat":
            terminal = raw.get("interaction", {}).get("flow", {}).get("terminal", {}) or {}
            return "APPLIED" if terminal.get("outcome") == "defeat" and receipt.get("player_dead") is True else "UNVERIFIED"
        return "UNVERIFIED"
    s = after.snapshot
    if not gambler and not _normal_card_lineage(plan.before.snapshot, s, predicted.get('expected_card_uuids')):
        return 'UNVERIFIED'
    if gambler:
        try: actual_selection=_native_selection_evidence(s) if s['screen_name'] in ('HAND_SELECT','GRID') else None
        except (KeyError,TypeError,HeadlessContractError): return 'UNVERIFIED'
        actual_guard=fingerprint.get('selection_native_key')
        expected_guard=predicted.get('expected_selection_native_key')
        if (not _selection_lineage(plan.before.snapshot,s,predicted.get('expected_selection'),actual_selection)
                or fingerprint.get('selection')!=actual_selection
                or actual_selection is not None and (not isinstance(actual_guard,list) or not actual_guard
                    or not isinstance(expected_guard,list) or not expected_guard or actual_guard!=expected_guard)
                or not _normal_card_lineage(plan.before.snapshot,s,predicted.get('expected_card_uuids'))):
            return 'UNVERIFIED'
    actual_rng = {k: s["_headless_rng"]["streams"][k] for k in MODELLED}
    extras = {k: v for k, v in s["_headless_rng"]["streams"].items() if k not in MODELLED}
    matched = (fingerprint.get("supported") is True and fingerprint.get("rng_restore_mode") == "exact_state"
        and fingerprint.get("projection") == projection(s) == predicted["expected_after"]
        and fingerprint.get("exact_rng") == actual_rng == predicted["expected_exact_rng"]
        and fingerprint.get("native_key") == predicted["expected_native_key"]
        and extras == predicted["unmodelled_rng"] and _private_match(predicted["source_private_invariants"], s)
        and _lagavulin_progress(plan.before.snapshot, s, json.loads(plan.command_json))
        and _thief_progress(plan.before.snapshot, s, json.loads(plan.command_json))
        and _champ_progress(plan.before.snapshot, s, json.loads(plan.command_json))
        and after.state.screen_type == predicted["expected_screen"])
    return "APPLIED" if matched else "UNVERIFIED"
