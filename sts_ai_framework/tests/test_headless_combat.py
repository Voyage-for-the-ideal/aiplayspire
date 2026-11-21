"""Pure normal-combat contracts. Actual Java/native integration has a separate fixture runner."""
import copy
import json
from pathlib import Path
import unittest
from uuid import UUID

from battle_engine.state import projection, context
from sts_ai_framework.headless.combat import (HeadlessCombatBridge, verify_combat_effect,
    SCHEMA, STREAMS, MODELLED, RNG_POLICY, _private_invariants, _lagavulin_progress, _thief_progress, _champ_progress,
    is_gambler_selection, _gambler_evidence, is_liquid_selection, is_native_combat_selection, _liquid_evidence)
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.headless.event_receipts import _colosseum_wave_deck
from sts_ai_framework.tests.test_headless_state import observation, card


class SlimeLatchTest(unittest.TestCase):
    def test_nemesis_cosmetic_clock_is_not_a_combat_invariant(self):
        s=combat_raw()['combat']['snapshot'];m=s['cur_map_node_state']['monster_data'][0]
        m['creature']['id']='Nemesis';m['_private']={'Nemesis.fireTimer':0.05,'Nemesis.scytheCooldown':2,'Nemesis.fireDmg':6}
        before=_private_invariants(s)
        m['_private']['Nemesis.fireTimer']=0.016666664
        self.assertEqual(before,_private_invariants(s))
        m['_private']['Nemesis.scytheCooldown']=1
        self.assertEqual(before,_private_invariants(s))  # Compared in the native successor instead.
        m['_private']['Nemesis.fireDmg']=7
        self.assertNotEqual(before,_private_invariants(s))

    def test_colosseum_wave_tracks_the_native_lesson_learned_upgrade(self):
        old=[dict(index=i,uuid=str(UUID(int=i+1)),id='WindmillStrike',name='Windmill Strike',cost=2,costForTurn=2,
                  timesUpgraded=0,upgraded=False,canUpgrade=True,type='ATTACK',rarity='UNCOMMON',target='ENEMY',
                  misc=0,bottled=False,exhaust=False) for i in range(2)]
        new=copy.deepcopy(old);new[1].update(timesUpgraded=1,upgraded=True,canUpgrade=False,name='Windmill Strike+')
        forecast=[dict(id='WindmillStrike',upgrades=i,cost=2,turn_cost=2) for i in range(2)]
        self.assertTrue(_colosseum_wave_deck(old,new,forecast,'LessonLearned'))
        self.assertFalse(_colosseum_wave_deck(old,new,forecast[::-1],'LessonLearned'))
        self.assertFalse(_colosseum_wave_deck(old,new,forecast,'Bash'))
        self.assertFalse(_colosseum_wave_deck(old,old,forecast,'LessonLearned'))
        new[1]['uuid']=old[0]['uuid']
        self.assertFalse(_colosseum_wave_deck(old,new,forecast,'LessonLearned'))

    def test_dead_split_parent_may_have_a_later_unused_move(self):
        s=combat_raw()['combat']['snapshot'];m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='SpikeSlime_L',current_health=0)
        m.update(next_move=4,_private={'SpikeSlime_L.splitTriggered':True})
        _private_invariants(s)
        m['creature']['current_health']=1
        with self.assertRaisesRegex(HeadlessContractError,'split latch'): _private_invariants(s)


def combat_raw(role="IRONCLAD"):
    path = Path(__file__).resolve().parents[2] / "battle_engine/tests/fixtures/ironclad.json"
    s = json.loads(path.read_text(encoding="utf-8"))
    s.update(_engine="headless", act_num=1, end_turn_queued=False, cards_played_this_turn=[])
    p = s["player_state"]; p["chosen_class_name"] = role
    for i, c in enumerate(p["hand"] + p["draw_pile"]): c["uuid"] = str(UUID(int=i+1))
    p["master_deck"] = copy.deepcopy(p["hand"] + p["draw_pile"])
    streams = {}
    for i, name in enumerate(STREAMS):
        count = s["rng_state"].setdefault(name + "_rng_counter", 0)
        streams[name] = {"state0": f'{0xf123456789abcdef + i:016x}', "state1": "8123456789abcdef", "counter": count}
    s["_headless_rng"] = {"schema": "headless-rng-v1", "policy": RNG_POLICY, "streams": streams,
                          "math": {"state0": "9123456789abcdef", "state1": "0123456789abcdef"}}
    out = observation(phase="PLAYER_TURN", map=None, floor=1, ascension=0, roomPhase="COMBAT")
    def adapt(c):
        return card(int(UUID(c["uuid"])), id=c["card_id"], timesUpgraded=c["times_upgraded"], cost=c["cost"], costForTurn=c["cost_for_turn"],
                    target="NONE" if c["card_id"] == "Defend_R" else "ENEMY")
    creature = p["creature"]
    out["player"].update(className=role, hp=creature["current_health"], maxHp=creature["max_health"], gold=creature["gold"], energy=p["energy_panel_total_energy"], block=creature["current_block"])
    for old, new in (("master_deck", "masterDeck"), ("hand", "hand"), ("draw_pile", "drawPile"), ("discard_pile", "discardPile"), ("exhaust_pile", "exhaustPile")):
        out["player"][new] = [adapt(c) for c in p[old]]
    out["player"]["relics"] = [{"id": r["relic_id"], "name": r["relic_id"], "counter": r["counter"]} for r in p["relics"]]
    out["player"]["potions"] = [{"id": x["id"], "name": x["id"], "empty": x["id"] == "Potion Slot", "targetRequired": x["id"] == "Fire Potion"} for x in p["potions"]]
    out["monsters"] = [{"id": m["creature"]["id"], "name": "fixture", "hp": m["creature"]["current_health"], "maxHp": m["creature"]["max_health"], "block": m["creature"]["current_block"], "intent": m["intent_name"]} for m in s["cur_map_node_state"]["monster_data"]]
    out["combat"] = {"schema": SCHEMA, "snapshot": s, "legal_actions": [
        {"type": "CARD", "card_index": 0, "monster_index": 0, "uuid": p["hand"][0]["uuid"]},
        {"type": "CARD", "card_index": 1, "monster_index": -1, "uuid": p["hand"][1]["uuid"]},
        {"type": "POTION", "potion_index": 0, "monster_index": 0, "id": "Fire Potion"}, {"type": "END"}]}
    return out


def response(frame, after=None):
    s = (after or frame).snapshot
    return {"supported": True, "rng_restore_mode": "exact_state", "commands": [{"command": json.dumps({"type": "CARD", "card_index": 0, "monster_index": 0}),
        "expected_before": projection(frame.snapshot), "expected_context": context(frame.snapshot)}],
        "expected_card_uuids": {pile:[c['uuid'] for c in s['player_state'][pile]] for pile in ('hand','draw_pile','discard_pile','exhaust_pile')},
        "expected_after": projection(s), "expected_screen": "NONE", "expected_native_key": ["typed-fixture-key"],
        "expected_exact_rng": {k: v for k, v in s["_headless_rng"]["streams"].items() if k in MODELLED},
        "unmodelled_rng": {k: v for k, v in s["_headless_rng"]["streams"].items() if k not in MODELLED}}


def gambler_raw(selected=()):
    raw=combat_raw();s=raw['combat']['snapshot'];p=s['player_state']
    original=p['hand'];visible=raw['player']['hand']
    chosen=[original[i] for i in selected];chosen_visible=[visible[i] for i in selected]
    p['hand']=[c for i,c in enumerate(original) if i not in selected]
    raw['player']['hand']=[c for i,c in enumerate(visible) if i not in selected]
    raw['phase']='CARD_SELECT';raw['cardSelection']={'kind':'hand','options':raw['player']['hand'],'selected':chosen_visible}
    raw['interaction']={'schema':'headless-interaction-v1','selection':{'kind':'hand','purpose':'gamble','count':99,
        'locked':False,'can_confirm':True,'any_number':True,'can_pick_zero':True,'auto_confirm':False}}
    s.update(screen_name='HAND_SELECT',is_screen_up=True)
    s['hand_select_screen_state']={'num_cards_to_select':99,'selected_cards':chosen,'any_number':True,
        'can_pick_zero':True,'were_cards_retrieved':False,'current_action_state':{
            'state_class':'savestate.actions.GamblingChipActionState','state':{'notchip':True}},
        'action_queue':[],'card_queue_state':[]}
    raw['combat']['legal_actions']=[{'type':'HAND_SELECT','card_index':i,'uuid':c['uuid']} for i,c in enumerate(p['hand'])]+[{'type':'HAND_SELECT_CONFIRM'}]
    return raw


def liquid_raw(count=1, selected=(), *, grid=True, cards=3):
    raw=combat_raw();s=raw['combat']['snapshot'];p=s['player_state']
    originals=p['draw_pile']+[p['hand'][0]]
    p['discard_pile']=[copy.deepcopy(c) for c in originals[:cards]]
    for i,c in enumerate(p['discard_pile']): c['uuid']=str(UUID(int=100+i))
    raw['player']['discardPile']=[card(int(UUID(c['uuid'])),id=c['card_id'],timesUpgraded=c['times_upgraded'],
        cost=c['cost'],costForTurn=c['cost_for_turn']) for c in p['discard_pile']]
    if count==2:
        p['relics'].append({'relic_id':'SacredBark','counter':-1})
        raw['player']['relics'].append({'id':'SacredBark','name':'SacredBark','counter':-1})
    p['potions'][0]['id']='Potion Slot' if grid else 'LiquidMemories'
    raw['player']['potions'][0].update(id=p['potions'][0]['id'],name=p['potions'][0]['id'],empty=grid,targetRequired=False)
    if not grid:
        raw['combat']['legal_actions'][2].update(id='LiquidMemories',monster_index=-1)
        return raw
    raw['phase']='CARD_SELECT'
    raw['cardSelection']={'kind':'grid','options':copy.deepcopy(raw['player']['discardPile']),
        'selected':[copy.deepcopy(raw['player']['discardPile'][i]) for i in selected]}
    raw['interaction']={'schema':'headless-interaction-v1','selection':{'kind':'grid','purpose':'liquid_memories','count':count,
        'locked':False,'can_confirm':False,'any_number':False,'can_pick_zero':False,'auto_confirm':True}}
    s.update(screen_name='GRID',is_screen_up=True)
    s['grid_card_select_screen_state']={'num_cards':count,'group_cards':copy.deepcopy(p['discard_pile']),
        'selected_cards':[copy.deepcopy(p['discard_pile'][i]) for i in selected],
        'any_number':False,'for_clarity':False,'for_upgrade':False,'for_transform':False,'for_purge':False,
        'confirm_screen_up':False,'is_just_for_confirming':False,
        'current_action_state':{'state_class':'savestate.actions.BetterDiscardPileToHandActionState',
            'state':{'number_of_cards':count,'owner_index':-1,'set_cost':True,'optional':False,'new_cost':0}},
        'action_queue':[{'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':-1,'derived_refresh':True}}],
        'card_queue_state':[]}
    raw['combat']['legal_actions']=[{'type':'GRID_SELECT','card_index':i,'uuid':c['uuid']} for i,c in enumerate(p['discard_pile'])]
    return raw


def selection_prediction(before, after, command):
    predicted=response(before,after)
    predicted.update(commands=[{'command':json.dumps(command),'expected_before':projection(before.snapshot),'expected_context':context(before.snapshot)}],
        expected_screen=after.snapshot['screen_name'],expected_native_key=None,expected_selection=_liquid_evidence(after.snapshot),
        expected_selection_native_key=['full-liquid-state'],
        expected_card_uuids={p:[c['uuid'] for c in after.snapshot['player_state'][p]] for p in ('hand','draw_pile','discard_pile','exhaust_pile')})
    return predicted


def selection_fingerprint(after,predicted):
    return {'supported':True,'rng_restore_mode':'exact_state','projection':projection(after.snapshot),
        'exact_rng':copy.deepcopy(predicted['expected_exact_rng']),'native_key':predicted['expected_native_key'],
        'selection':copy.deepcopy(predicted.get('expected_selection')),'selection_native_key':predicted.get('expected_selection_native_key')}


class LiquidCombatContractTest(unittest.TestCase):
    def test_grid_selection_and_partial_cancel_are_native_only_and_uuid_bound(self):
        from sts_ai_framework.headless.replay import command
        from sts_ai_framework.headless.interaction import IroncladInteractionBridge
        bridge=HeadlessCombatBridge();raw=liquid_raw(2,(1,))
        before=bridge.observe(raw,run_id='liquid',sequence=0)
        after=bridge.observe(liquid_raw(2),run_id='liquid',sequence=1)
        self.assertTrue(is_liquid_selection(raw));self.assertTrue(is_native_combat_selection(raw))
        self.assertEqual(len(before.legal_json),3);self.assertFalse(before.state.can_proceed)
        self.assertEqual(before.state.grid_purpose,'liquid_memories')
        self.assertIn(json.dumps({'type':'choose','choice_index':1},sort_keys=True,separators=(',',':')),before.legal_json)
        self.assertEqual(command('chooseGridCard',[1]),{'type':'GRID_SELECT','card_index':1})
        predicted=selection_prediction(before,after,{'type':'GRID_SELECT','card_index':1})
        plan=bridge.prepare(before,predicted)
        self.assertEqual((plan.native_method,plan.native_args),('chooseGridCard',(1,)))
        self.assertEqual(verify_combat_effect(plan,after,selection_fingerprint(after,predicted)),'APPLIED')
        with self.assertRaisesRegex(HeadlessContractError,'native combat continuation'):
            IroncladInteractionBridge().observe(raw,run_id='liquid',sequence=0)

    def test_partial_grid_checks_fullguard_selected_properties_uuid_and_rng(self):
        bridge=HeadlessCombatBridge();before=bridge.observe(liquid_raw(2),run_id='liquid',sequence=0)
        after=bridge.observe(liquid_raw(2,(1,)),run_id='liquid',sequence=1)
        predicted=selection_prediction(before,after,{'type':'GRID_SELECT','card_index':1});plan=bridge.prepare(before,predicted)
        fingerprint=selection_fingerprint(after,predicted)
        self.assertEqual(verify_combat_effect(plan,after,fingerprint),'APPLIED')
        for mutate in (lambda r:r.pop('selection_native_key'),lambda r:r.update(selection_native_key=None),
                lambda r:r.update(selection_native_key=[]),lambda r:r.update(selection_native_key=['power-or-cost-drift']),
                lambda r:r['selection'].update(selected=[]),lambda r:r['exact_rng']['shuffle'].update(counter=99)):
            bad=copy.deepcopy(fingerprint);mutate(bad)
            self.assertEqual(verify_combat_effect(plan,after,bad),'UNVERIFIED')
        for mutate in (lambda r:r.pop('expected_selection_native_key'),lambda r:r.update(expected_selection_native_key=None),
                lambda r:r.update(expected_selection_native_key=[]),lambda r:r.pop('expected_card_uuids')):
            bad=copy.deepcopy(predicted);mutate(bad)
            with self.assertRaises(HeadlessContractError):bridge.prepare(before,bad)
        drift=liquid_raw(2,(1,));p=drift['combat']['snapshot']['player_state'];g=drift['combat']['snapshot']['grid_card_select_screen_state']
        p['discard_pile'][1]['cost_for_turn']=0;g['group_cards'][1]['cost_for_turn']=0;g['selected_cards'][0]['cost_for_turn']=0
        drift['player']['discardPile'][1]['costForTurn']=0;drift['cardSelection']['options'][1]['costForTurn']=0;drift['cardSelection']['selected'][0]['costForTurn']=0
        changed=bridge.observe(drift,run_id='liquid',sequence=2)
        self.assertEqual(verify_combat_effect(plan,changed,fingerprint),'UNVERIFIED')

    def test_potion_grid_boundary_requires_consumption_and_exact_pile_uuids(self):
        bridge=HeadlessCombatBridge();before=bridge.observe(liquid_raw(grid=False),run_id='liquid',sequence=0)
        after=bridge.observe(liquid_raw(),run_id='liquid',sequence=1)
        predicted=selection_prediction(before,after,{'type':'POTION','potion_index':0,'monster_index':-1})
        plan=bridge.prepare(before,predicted);fingerprint=selection_fingerprint(after,predicted)
        self.assertEqual(verify_combat_effect(plan,after,fingerprint),'APPLIED')
        for mutate in (lambda r:r.pop('expected_card_uuids'),lambda r:r.pop('expected_selection'),lambda r:r.pop('expected_selection_native_key')):
            bad=copy.deepcopy(predicted);mutate(bad)
            with self.assertRaises(HeadlessContractError):bridge.prepare(before,bad)
        wrong=liquid_raw();wrong['combat']['snapshot']['player_state']['potions'][0]['id']='LiquidMemories'
        wrong['player']['potions'][0].update(id='LiquidMemories',empty=False)
        frame=bridge.observe(wrong,run_id='liquid',sequence=2);fingerprint['projection']=projection(frame.snapshot)
        self.assertEqual(verify_combat_effect(plan,frame,fingerprint),'UNVERIFIED')

    def test_automatic_return_without_ui_still_requires_card_identities(self):
        bridge=HeadlessCombatBridge();source=liquid_raw(grid=False,cards=1)
        landed=copy.deepcopy(source);s=landed['combat']['snapshot'];p=s['player_state']
        returned=p['discard_pile'].pop();returned['cost_for_turn']=0;p['hand'].append(returned)
        visible=landed['player']['discardPile'].pop();visible['costForTurn']=0;landed['player']['hand'].append(visible)
        p['potions'][0]['id']='Potion Slot';landed['player']['potions'][0].update(id='Potion Slot',empty=True)
        landed['combat']['legal_actions']=[{'type':'END'}]
        before=bridge.observe(source,run_id='liquid-auto',sequence=0);after=bridge.observe(landed,run_id='liquid-auto',sequence=1)
        predicted=response(before,after);predicted['commands'][0]['command']=json.dumps({'type':'POTION','potion_index':0,'monster_index':-1})
        predicted.pop('expected_card_uuids')
        with self.assertRaisesRegex(HeadlessContractError,'UUID'):bridge.prepare(before,predicted)
        predicted['expected_card_uuids']={pile:[c['uuid'] for c in p[pile]] for pile in ('hand','draw_pile','discard_pile','exhaust_pile')}
        plan=bridge.prepare(before,predicted);fingerprint=selection_fingerprint(after,predicted)
        self.assertEqual(verify_combat_effect(plan,after,fingerprint),'APPLIED')
        changed=copy.deepcopy(landed);changed['combat']['snapshot']['player_state']['hand'][-1]['uuid']=str(UUID(int=999))
        changed['player']['hand'][-1]['uuid']=str(UUID(int=999))
        frame=bridge.observe(changed,run_id='liquid-auto',sequence=2)
        self.assertEqual(verify_combat_effect(plan,frame,fingerprint),'UNVERIFIED')

    def test_invalid_variants_missing_fields_queue_and_stale_ui_are_stopped(self):
        for mutate in (
                lambda r:r['combat']['snapshot']['grid_card_select_screen_state']['current_action_state']['state'].pop('new_cost'),
                lambda r:r['combat']['snapshot']['grid_card_select_screen_state']['current_action_state']['state'].update(new_cost=1),
                lambda r:r['combat']['snapshot']['grid_card_select_screen_state']['current_action_state']['state'].update(optional=True),
                lambda r:r['combat']['snapshot']['grid_card_select_screen_state']['group_cards'].reverse(),
                lambda r:r['combat']['snapshot']['grid_card_select_screen_state']['selected_cards'][0].update(cost_for_turn=0),
                lambda r:r['cardSelection']['options'].reverse(),lambda r:r['cardSelection']['selected'][0].update(costForTurn=0),
                lambda r:r['interaction']['selection'].update(can_confirm=True),
                lambda r:r['combat']['legal_actions'][0].update(uuid=str(UUID(int=999))),
                lambda r:r['combat']['legal_actions'].append({'type':'HAND_SELECT_CONFIRM'}),
                lambda r:r['combat']['legal_actions'].pop(),
                lambda r:r['combat']['snapshot']['grid_card_select_screen_state']['action_queue'].append({'state_class':'savestate.actions.WaitActionState','state':{'duration':.25}}),
                lambda r:r['combat']['snapshot']['grid_card_select_screen_state']['action_queue'].append({'state_class':'savestate.actions.GainEnergyActionState','state':{'energyGain':1}})):
            raw=liquid_raw(2,(0,));mutate(raw)
            with self.assertRaises(HeadlessContractError):HeadlessCombatBridge().observe(raw,run_id='liquid',sequence=0)


class GamblerCombatContractTest(unittest.TestCase):
    def test_potion_boundary_requires_real_slot_consumption_and_same_card_identities(self):
        source=combat_raw();source['combat']['snapshot']['player_state']['potions'][0]['id']='GamblersBrew'
        source['player']['potions'][0].update(id='GamblersBrew',name='brew',targetRequired=False)
        source['combat']['legal_actions'][2].update(id='GamblersBrew',monster_index=-1)
        landed=gambler_raw();landed['combat']['snapshot']['player_state']['potions'][0]['id']='Potion Slot'
        landed['player']['potions'][0].update(id='Potion Slot',name='slot',empty=True,targetRequired=False)
        bridge=HeadlessCombatBridge();before=bridge.observe(source,run_id='gamble',sequence=0)
        after=bridge.observe(landed,run_id='gamble',sequence=1)
        predicted=response(before,after)
        predicted.update(commands=[{'command':json.dumps({'type':'POTION','potion_index':0,'monster_index':-1}),
            'expected_before':projection(before.snapshot),'expected_context':context(before.snapshot)}],
            expected_screen='HAND_SELECT',expected_native_key=None,expected_selection=_gambler_evidence(after.snapshot),expected_selection_native_key=['full-gambler-state'],
            expected_card_uuids={p:[c['uuid'] for c in after.snapshot['player_state'][p]] for p in ('hand','draw_pile','discard_pile','exhaust_pile')})
        plan=bridge.prepare(before,predicted)
        fingerprint={'supported':True,'rng_restore_mode':'exact_state','projection':projection(after.snapshot),
            'exact_rng':predicted['expected_exact_rng'],'native_key':None,'selection':predicted['expected_selection'],'selection_native_key':predicted['expected_selection_native_key']}
        self.assertEqual(verify_combat_effect(plan,after,fingerprint),'APPLIED')
        wrong=copy.deepcopy(landed);wrong['combat']['snapshot']['player_state']['potions'][0]['id']='GamblersBrew'
        wrong['player']['potions'][0].update(id='GamblersBrew',empty=False,targetRequired=False)
        bad=bridge.observe(wrong,run_id='gamble',sequence=2);fingerprint['projection']=projection(bad.snapshot)
        self.assertEqual(verify_combat_effect(plan,bad,fingerprint),'UNVERIFIED')

    def test_selection_uuid_legal_controls_and_exact_native_evidence(self):
        bridge=HeadlessCombatBridge();before=bridge.observe(gambler_raw(),run_id='gamble',sequence=0)
        after=bridge.observe(gambler_raw((0,)),run_id='gamble',sequence=1)
        predicted=response(before,after)
        predicted.update(commands=[{'command':json.dumps({'type':'HAND_SELECT','card_index':0}),
            'expected_before':projection(before.snapshot),'expected_context':context(before.snapshot)}],
            expected_screen='HAND_SELECT',expected_native_key=None,expected_selection=_gambler_evidence(after.snapshot),expected_selection_native_key=['full-gambler-state'],
            expected_card_uuids={p:[c['uuid'] for c in after.snapshot['player_state'][p]] for p in ('hand','draw_pile','discard_pile','exhaust_pile')})
        plan=bridge.prepare(before,predicted)
        self.assertEqual((plan.native_method,plan.native_args),('chooseHandCard',(0,)))
        fingerprint={'supported':True,'rng_restore_mode':'exact_state','projection':projection(after.snapshot),
            'exact_rng':predicted['expected_exact_rng'],'native_key':None,'selection':predicted['expected_selection'],'selection_native_key':predicted['expected_selection_native_key']}
        self.assertEqual(verify_combat_effect(plan,after,fingerprint),'APPLIED')
        bad=copy.deepcopy(fingerprint);bad['selection']['selected']=[]
        self.assertEqual(verify_combat_effect(plan,after,bad),'UNVERIFIED')
        bad=copy.deepcopy(fingerprint);bad['selection_native_key']=['drifted-power-or-selected-card']
        self.assertEqual(verify_combat_effect(plan,after,bad),'UNVERIFIED')
        bad=copy.deepcopy(fingerprint);bad['exact_rng']['shuffle']['counter']+=1
        self.assertEqual(verify_combat_effect(plan,after,bad),'UNVERIFIED')
        bad=copy.deepcopy(predicted);bad.pop('expected_card_uuids')
        with self.assertRaisesRegex(HeadlessContractError,'UUID'):bridge.prepare(before,bad)
        for value in (None,[]):
            bad=copy.deepcopy(predicted);bad['expected_selection_native_key']=value
            with self.assertRaisesRegex(HeadlessContractError,'guard key'):bridge.prepare(before,bad)
        bad=copy.deepcopy(predicted);bad.pop('expected_selection_native_key')
        with self.assertRaisesRegex(HeadlessContractError,'guard key'):bridge.prepare(before,bad)
        for value in (None,[]):
            bad=copy.deepcopy(fingerprint);bad['selection_native_key']=value
            self.assertEqual(verify_combat_effect(plan,after,bad),'UNVERIFIED')
        bad=copy.deepcopy(fingerprint);bad.pop('selection_native_key')
        self.assertEqual(verify_combat_effect(plan,after,bad),'UNVERIFIED')

    def test_selected_private_fields_remain_bound_when_card_leaves_hand(self):
        before=gambler_raw();after=gambler_raw((0,))
        before['combat']['snapshot']['player_state']['hand'][0]['_private']={'FixtureCard.counter':2}
        selected=after['combat']['snapshot']['hand_select_screen_state']['selected_cards'][0]
        selected['_private']={'FixtureCard.counter':2}
        self.assertEqual(_private_invariants(before['combat']['snapshot']),_private_invariants(after['combat']['snapshot']))
        selected['_private']['FixtureCard.counter']=3
        self.assertNotEqual(_private_invariants(before['combat']['snapshot']),_private_invariants(after['combat']['snapshot']))

    def test_gambler_derived_hand_refresh_is_bound_and_unknown_actions_stay_closed(self):
        raw=gambler_raw();h=raw['combat']['snapshot']['hand_select_screen_state']
        refresh={'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':-1,'derived_refresh':True}}
        wait={'state_class':'savestate.actions.WaitActionState','state':{'duration':.25}}
        h['action_queue']=[refresh,wait]
        frame=HeadlessCombatBridge().observe(raw,run_id='refresh',sequence=0)
        self.assertIsNotNone(frame.snapshot)
        for queue in ([refresh,refresh],[wait,refresh],
                      [{'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':0,'derived_refresh':True}}],
                      [{'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':-1,'derived_refresh':False}}],
                      [refresh,{'state_class':'savestate.actions.GainEnergyActionState','state':{'energyGain':1}}]):
            bad=copy.deepcopy(raw);bad['combat']['snapshot']['hand_select_screen_state']['action_queue']=queue
            with self.assertRaises(HeadlessContractError):HeadlessCombatBridge().observe(bad,run_id='refresh',sequence=0)

    def test_mismatched_ui_or_controls_or_legal_uuids_are_stopped(self):
        for mutate in (lambda r:r['cardSelection']['options'].reverse(),
                       lambda r:r['combat']['snapshot']['hand_select_screen_state'].update(can_pick_zero=False),
                       lambda r:r['combat']['legal_actions'][0].update(uuid=str(UUID(int=999))),
                       lambda r:r['combat']['snapshot']['hand_select_screen_state']['action_queue'].append(
                           {'state_class':'savestate.actions.GainEnergyActionState','state':{'energyGain':1}}),
                       lambda r:r['combat']['legal_actions'].pop()):
            raw=gambler_raw();mutate(raw)
            with self.assertRaises(HeadlessContractError):HeadlessCombatBridge().observe(raw,run_id='gamble',sequence=0)

    def test_confirm_zero_is_a_real_legal_native_action_and_replay_binding(self):
        from sts_ai_framework.headless.replay import command
        from sts_ai_framework.headless.interaction import IroncladInteractionBridge
        raw=gambler_raw();bridge=HeadlessCombatBridge();frame=bridge.observe(raw,run_id='gamble',sequence=0)
        self.assertTrue(is_gambler_selection(raw))
        self.assertIn(json.dumps({'type':'proceed'},sort_keys=True,separators=(',',':')),frame.legal_json)
        self.assertEqual(command('chooseHandCard',[0]),{'type':'HAND_SELECT','card_index':0})
        self.assertEqual(command('confirmHandSelect',[]),{'type':'HAND_SELECT_CONFIRM'})
        self.assertTrue(frame.state.can_proceed)
        self.assertEqual(frame.state.grid_purpose,'gamble')
        with self.assertRaisesRegex(HeadlessContractError,'native combat continuation'):
            IroncladInteractionBridge().observe(raw,run_id='gamble',sequence=0)


class CombatContractTest(unittest.TestCase):
    def test_mantra_constant_is_validated_but_not_frozen_across_application(self):
        s=combat_raw()['combat']['snapshot'];powers=s['player_state']['creature']['powers']
        before=_private_invariants(s)
        powers.append({'power_id':'Mantra','amount':5,'_private':{'MantraPower.PRAYER_REQUIRED':10}})
        self.assertEqual(_private_invariants(s),before)
        powers[-1]['_private']['MantraPower.PRAYER_REQUIRED']=9
        with self.assertRaisesRegex(HeadlessContractError,'Mantra threshold'): _private_invariants(s)

    def test_curl_up_triggered_latch_is_inert_only_on_dead_owner(self):
        s=combat_raw()['combat']['snapshot'];monster=s['cur_map_node_state']['monster_data'][0]
        monster['creature']['id']='FuzzyLouseDefensive'
        power={'power_id':'Curl Up','amount':7,'_private':{'CurlUpPower.triggered':False}}
        monster['creature']['powers']=[power]
        before=_private_invariants(s)
        monster['creature']['current_health']=0;power['_private']['CurlUpPower.triggered']=True
        self.assertEqual(_private_invariants(s),before)
        monster['creature']['current_health']=1
        with self.assertRaisesRegex(HeadlessContractError,'Live monster'): _private_invariants(s)
        power['_private']['CurlUpPower.triggered']='true'
        with self.assertRaisesRegex(HeadlessContractError,'Invalid Curl Up'): _private_invariants(s)

    def test_combust_private_hp_loss_is_native_modelled_but_other_fields_remain_frozen(self):
        s=combat_raw()['combat']['snapshot']
        power={'power_id':'Combust','amount':12,'_private':{'CombustPower.hpLoss':3,'CombustPower.futureCounter':7}}
        s['player_state']['creature']['powers']=[power]
        before=_private_invariants(s)
        self.assertEqual(before['player-power:Combust'],{'CombustPower.futureCounter':7})
        power['_private']['CombustPower.hpLoss']=4
        self.assertEqual(_private_invariants(s),before)  # Full native key compares the imported hpLoss.
        power['_private']['CombustPower.futureCounter']=8
        self.assertNotEqual(_private_invariants(s),before)
        for value in (None,True,'1',1.0,0,-1,128):
            power['_private']['CombustPower.hpLoss']=value
            with self.assertRaisesRegex(HeadlessContractError,'CombustPower.hpLoss'):_private_invariants(s)
        power['_private'].pop('CombustPower.hpLoss')
        with self.assertRaisesRegex(HeadlessContractError,'CombustPower.hpLoss'):_private_invariants(s)
        power.pop('_private')
        with self.assertRaisesRegex(HeadlessContractError,'CombustPower.hpLoss'):_private_invariants(s)

    def test_combust_first_and_stacked_application_are_verified_by_native_state_not_history(self):
        for initial,hp_loss in ((0,0),(12,3)):
            bridge=HeadlessCombatBridge();raw=combat_raw();s=raw['combat']['snapshot'];p=s['player_state']
            p['hand'][0].update(card_id='Combust',cost=1,cost_for_turn=1)
            raw['player']['hand'][0].update(id='Combust',cost=1,costForTurn=1,target='SELF')
            raw['combat']['legal_actions']=[{'type':'CARD','card_index':0,'monster_index':-1,'uuid':p['hand'][0]['uuid']},{'type':'END'}]
            if initial:p['creature']['powers']=[{'power_id':'Combust','amount':initial,'_private':{'CombustPower.hpLoss':hp_loss}}]
            landed=copy.deepcopy(raw);a=landed['combat']['snapshot']['player_state']
            played=a['hand'].pop(0);landed['player']['hand'].pop(0)
            a['energy_panel_total_energy']-=1;landed['player']['energy']-=1
            a['creature']['powers']=[{'power_id':'Combust','amount':initial+5,'_private':{'CombustPower.hpLoss':hp_loss+1}}]
            landed['combat']['snapshot']['cards_played_this_combat']=[{'card_index':-1,'card_state':played}]
            landed['combat']['legal_actions']=[{'type':'END'}]
            before=bridge.observe(raw,run_id='combust',sequence=0);after=bridge.observe(landed,run_id='combust',sequence=1)
            predicted=response(before,after);predicted['commands'][0]['command']=json.dumps({'type':'CARD','card_index':0,'monster_index':-1})
            predicted['expected_native_key']=['bound-player-combustHpLoss',hp_loss+1]
            plan=bridge.prepare(before,predicted);fingerprint=selection_fingerprint(after,predicted)
            self.assertEqual(verify_combat_effect(plan,after,fingerprint),'APPLIED')
            fingerprint['native_key']=['bound-player-combustHpLoss',hp_loss+2]
            self.assertEqual(verify_combat_effect(plan,after,fingerprint),'UNVERIFIED')
            fingerprint['native_key']=predicted['expected_native_key']
            drift=copy.deepcopy(landed);drift['combat']['snapshot']['player_state']['creature']['powers'][0]['_private']['CombustPower.futureCounter']=1
            other=bridge.observe(drift,run_id='combust',sequence=2)
            self.assertEqual(verify_combat_effect(plan,other,fingerprint),'UNVERIFIED')

    def test_centennial_puzzle_explicit_static_latch_is_native_modeled(self):
        s=combat_raw()['combat']['snapshot']
        relic={'relic_id':'Centennial Puzzle','counter':-1,'_private':{'CentennialPuzzle.usedThisCombat':False}}
        s['player_state']['relics'].append(relic)
        before=_private_invariants(s)
        relic['_private']['CentennialPuzzle.usedThisCombat']=True
        self.assertEqual(_private_invariants(s),before)  # State changes are checked through native availability bits.
        relic['_private']['CentennialPuzzle.usedThisCombat']=1
        with self.assertRaisesRegex(HeadlessContractError,'Centennial Puzzle'): _private_invariants(s)

    def test_champ_modeled_counters_and_exact_first_turn_progress(self):
        s = combat_raw()["combat"]["snapshot"]; m = s["cur_map_node_state"]["monster_data"][0]
        m["creature"]["id"] = "Champ"
        m["_private"] = {"Champ.firstTurn": True, "Champ.numTurns": 1, "Champ.forgeTimes": 0,
                          "Champ.forgeThreshold": 2, "Champ.thresholdReached": False, "Champ.strAmt": 2}
        expected = _private_invariants(s)
        after = copy.deepcopy(s); after['turn'] += 1
        private = after['cur_map_node_state']['monster_data'][0]['_private']
        private.update({'Champ.firstTurn': False, 'Champ.numTurns': 2, 'Champ.forgeTimes': 1})
        self.assertEqual(_private_invariants(after), expected)
        self.assertTrue(_champ_progress(s, after, {'type': 'END'}))
        self.assertFalse(_champ_progress(s, after, {'type': 'CARD'}))
        private['Champ.firstTurn'] = True
        self.assertFalse(_champ_progress(s, after, {'type': 'END'}))
        private['Champ.numTurns'] = 1  # A skipped monster turn preserves its first-turn speech latch.
        self.assertTrue(_champ_progress(s, after, {'type': 'END'}))
        private['Champ.firstTurn'] = False
        self.assertFalse(_champ_progress(s, after, {'type': 'END'}))
        private['Champ.numTurns'] = -1
        with self.assertRaises(HeadlessContractError): _private_invariants(after)
        private['Champ.numTurns'] = 2; private['Champ.thresholdReached'] = 1
        with self.assertRaises(HeadlessContractError): _private_invariants(after)

    def test_book_stab_count_is_modeled_and_bound_to_live_multi_stab_intent(self):
        s = combat_raw()["combat"]["snapshot"]; m = s["cur_map_node_state"]["monster_data"][0]
        m["creature"]["id"] = "BookOfStabbing"
        m.update(next_move=1, move_hits=2, _private={"BookOfStabbing.stabCount": 2, "BookOfStabbing.stabDmg": 7})
        expected = _private_invariants(s)
        m.update(move_hits=3); m["_private"]["BookOfStabbing.stabCount"] = 3
        self.assertEqual(_private_invariants(s), expected)
        m["move_hits"] = 2
        with self.assertRaisesRegex(HeadlessContractError, "multi-stab intent"): _private_invariants(s)
        m["next_move"] = 2  # Single Stab has one hit; its private count still progresses independently.
        self.assertEqual(_private_invariants(s), expected)
        m["_private"]["BookOfStabbing.stabCount"] = 0
        with self.assertRaises(HeadlessContractError): _private_invariants(s)

    def test_byrd_flight_flag_and_restore_amount_are_not_free_mutable_fields(self):
        s = combat_raw()["combat"]["snapshot"]; m = s["cur_map_node_state"]["monster_data"][0]
        m["creature"].update(id="Byrd", powers=[{"power_id": "Flight", "amount": 1, "_private": {"FlightPower.storedAmount": 3}}])
        m["_private"] = {"Byrd.isFlying": True}; expected = _private_invariants(s)
        m["creature"]["powers"] = []; m["_private"]["Byrd.isFlying"] = False
        self.assertEqual(_private_invariants(s), expected)
        m["_private"]["Byrd.isFlying"] = True
        with self.assertRaises(HeadlessContractError): _private_invariants(s)
        m["creature"]["current_health"] = 0
        self.assertEqual(_private_invariants(s), expected)

    def test_spheric_and_chosen_latches_require_their_native_move_evidence(self):
        for identity, field, before_move, after_move in (("SphericGuardian", "secondMove", 2, 4), ("Chosen", "usedHex", 5, 4)):
            s = combat_raw()["combat"]["snapshot"]; m = s["cur_map_node_state"]["monster_data"][0]
            m["creature"]["id"] = identity; m.update(next_move=before_move, move_history=[before_move])
            m["_private"] = {identity+"."+field: identity=="SphericGuardian"}
            expected = _private_invariants(s)
            m.update(next_move=after_move, move_history=[before_move, after_move]); m["_private"][identity+"."+field] = identity=="Chosen"
            self.assertEqual(_private_invariants(s), expected)
            m["_private"][identity+"."+field] = identity!="Chosen"
            with self.assertRaises(HeadlessContractError): _private_invariants(s)

    def test_guardian_private_damage_must_equal_remaining_mode_shift(self):
        s = combat_raw()["combat"]["snapshot"]; m = s["cur_map_node_state"]["monster_data"][0]
        m["creature"].update(id="TheGuardian", powers=[{"power_id": "Mode Shift", "amount": 32}])
        m["_private"] = {"TheGuardian.dmgThreshold": 35, "TheGuardian.dmgTaken": 3,
                         "TheGuardian.isOpen": True, "TheGuardian.closeUpTriggered": False}
        expected = _private_invariants(s)
        m["_private"]["TheGuardian.dmgTaken"] = 4
        with self.assertRaises(HeadlessContractError): _private_invariants(s)
        m["creature"]["powers"] = []
        m["_private"].update({"TheGuardian.dmgThreshold": 45, "TheGuardian.dmgTaken": 0,
                             "TheGuardian.isOpen": False, "TheGuardian.closeUpTriggered": True})
        self.assertEqual(_private_invariants(s), expected)

    def test_lagavulin_counts_actual_sleep_not_poison_wakeup_turn(self):
        before = combat_raw()["combat"]["snapshot"]; before["turn"] = 2
        m = before["cur_map_node_state"]["monster_data"][0]
        m["creature"]["id"] = "Lagavulin"; m["next_move"] = 5; m["move_history"] = [5, 5]
        m["_private"] = {"Lagavulin.idleCount": 1, "Lagavulin.debuffTurnCount": 0,
                         "Lagavulin.isOut": False, "Lagavulin.isOutTriggered": False}
        after = copy.deepcopy(before); after["turn"] = 3; n = after["cur_map_node_state"]["monster_data"][0]
        n.update(next_move=3, move_history=[5, 5, 4, 3]); n["_private"].update({"Lagavulin.isOut": True, "Lagavulin.isOutTriggered": True})
        self.assertEqual(_private_invariants(before), _private_invariants(after))
        self.assertTrue(_lagavulin_progress(before, after, {"type": "END"}))
        n["_private"]["Lagavulin.idleCount"] = 2
        self.assertFalse(_lagavulin_progress(before, after, {"type": "END"}))
        n.update(next_move=5, move_history=[5, 5, 5]); n["_private"].update({"Lagavulin.isOut": False, "Lagavulin.isOutTriggered": False})
        self.assertTrue(_lagavulin_progress(before, after, {"type": "END"}))
        n["_private"]["Lagavulin.isOut"] = True
        with self.assertRaises(HeadlessContractError): _private_invariants(after)

    def test_thief_slash_counter_needs_a_real_executed_attack(self):
        before = combat_raw()["combat"]["snapshot"]; m = before["cur_map_node_state"]["monster_data"][0]
        m["creature"]["id"] = "Looter"; m["_private"] = {"Looter.slashCount": 0, "Looter.stolenGold": 0}
        after = copy.deepcopy(before); after["turn"] += 1; n = after["cur_map_node_state"]["monster_data"][0]
        n["move_history"] = [1, 1]; n["_private"].update({"Looter.slashCount": 1, "Looter.stolenGold": 14})
        self.assertTrue(_thief_progress(before, after, {"type": "END"}))
        self.assertFalse(_thief_progress(before, after, {"type": "CARD"}))
        n["move_history"] = [2, 3]
        self.assertFalse(_thief_progress(before, after, {"type": "END"}))

    def test_real_debuff_just_applied_is_modelled_not_an_opaque_invariant(self):
        s = combat_raw()["combat"]["snapshot"]; expected = _private_invariants(s)
        s["cur_map_node_state"]["monster_data"][0]["creature"]["powers"].append({
            "power_id": "Vulnerable", "amount": 2, "just_applied": False,
            "_private": {"VulnerablePower.justApplied": False}})
        self.assertEqual(_private_invariants(s), expected)
        s["cur_map_node_state"]["monster_data"][0]["creature"]["powers"][-1]["just_applied"] = True
        with self.assertRaises(HeadlessContractError): _private_invariants(s)

    def observe(self, source=None, sequence=0, run="fixture"):
        if not hasattr(self, "bridge"): self.bridge = HeadlessCombatBridge()
        return self.bridge.observe(source or combat_raw(), run_id=run, sequence=sequence)

    def test_all_four_roles_and_exact_card_target_api(self):
        for role in ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"):
            f = self.observe(combat_raw(role), run=role)
            self.assertEqual(len(f.legal_actions), 4)
            plan = self.bridge.prepare(f, response(f))
            self.assertEqual((plan.native_method, plan.native_args), ("playCard", (0, 0)))
            self.assertEqual(plan.api_action.target_index, 0)

    def test_mutable_views_do_not_change_guards(self):
        source = combat_raw(); f = self.observe(source); token = f.token
        source["combat"]["snapshot"]["turn"] = 500
        f.snapshot["turn"] = 300; f.state.player.energy = 100; f.legal_actions[0].target_index = 99
        self.assertEqual(f.token, token); self.assertEqual(f.snapshot["turn"], 1)
        self.assertEqual(f.legal_actions[0].target_index, 0)

    def test_rng_policy_is_explicit_and_all_streams_required(self):
        for mutate in (lambda s:s["_headless_rng"].update(policy="other"), lambda s:s.pop("_headless_rng"),
                       lambda s:s["_headless_rng"]["streams"].pop("headless"), lambda s:s["_headless_rng"]["streams"].update(extra={} )):
            raw = combat_raw(); mutate(raw["combat"]["snapshot"])
            with self.assertRaises(HeadlessContractError): self.observe(raw)

    def test_rng_words_are_lossless_strict_hex_not_json_numbers(self):
        for word in (123, "F123456789abcdef", "0x123456789abcdef", "123", "0"*17):
            raw = combat_raw(); raw["combat"]["snapshot"]["_headless_rng"]["streams"]["ai"]["state0"] = word
            with self.assertRaises(HeadlessContractError): self.observe(raw)
        raw = combat_raw(); r = raw["combat"]["snapshot"]["_headless_rng"]["streams"]["ai"]; r.update(state0="0"*16, state1="0"*16)
        with self.assertRaises(HeadlessContractError): self.observe(raw)

    def test_counter_alias_and_boolean_types(self):
        for value in (True, -1, 10000001, "1", 0):
            raw = combat_raw(); raw["combat"]["snapshot"]["rng_state"]["ai_rng_counter"] = value
            with self.assertRaises(HeadlessContractError): self.observe(raw)

    def test_card_slot_identity_and_original_monster_indices(self):
        for mutation in (lambda r:r["combat"]["legal_actions"][0].update(uuid=str(UUID(int=99))),
                         lambda r:r["combat"]["legal_actions"][0].update(monster_index=5),
                         lambda r:r["combat"]["legal_actions"][0].update(monster_index=-1),
                         lambda r:r["combat"]["snapshot"]["cur_map_node_state"]["monster_data"][0]["creature"].update(is_dead=True)):
            raw = combat_raw(); mutation(raw)
            with self.assertRaises(HeadlessContractError): self.observe(raw)

    def test_duplicate_actions_and_coerced_indices_are_rejected(self):
        raw = combat_raw(); raw["combat"]["legal_actions"].append(copy.deepcopy(raw["combat"]["legal_actions"][0]))
        with self.assertRaises(HeadlessContractError): self.observe(raw)
        raw = combat_raw(); raw["combat"]["legal_actions"][0]["card_index"] = True
        with self.assertRaises(HeadlessContractError): self.observe(raw)

    def test_inflight_or_wrong_role_or_observation_mismatch(self):
        for mutate in (lambda s:s["player_state"].update(card_in_use={}), lambda s:s.update(end_turn_queued=True),
                       lambda s:s["player_state"].update(chosen_class_name="DEFECT"),
                       lambda s:s["player_state"].update(energy_panel_total_energy=99),
                       lambda s:s["player_state"]["hand"][0].update(times_upgraded=2)):
            raw = combat_raw(); mutate(raw["combat"]["snapshot"])
            with self.assertRaises(HeadlessContractError): self.observe(raw)

    def test_noncombat_and_boot_keep_no_combat_bindings(self):
        f = self.observe({"phase": "BOOT", "floor": 0, "act": 0, "player": None, "map": None})
        self.assertIsNone(f.snapshot); self.assertEqual(f.legal_actions, ())

    def test_native_response_must_match_before_projection_and_context(self):
        f = self.observe()
        for field in ("expected_before", "expected_context"):
            r = response(f); r["commands"][0][field] = {}
            with self.assertRaises(HeadlessContractError): self.bridge.prepare(f, r)

    def test_unknown_multiple_or_illegal_search_commands(self):
        f = self.observe()
        for command in ({"type": "CARD", "card_index": 9, "monster_index": 0}, {"type": "NEW_RUN"}, {"type": "CARD", "card_index": 0, "monster_index": 9}):
            r = response(f); r["commands"][0]["command"] = json.dumps(command)
            with self.assertRaises(HeadlessContractError): self.bridge.prepare(f, r)
        r = response(f); r["commands"] *= 2
        with self.assertRaises(HeadlessContractError): self.bridge.prepare(f, r)

    def test_missing_prediction_queue_ack_and_unknown_boundary_are_rejected(self):
        f = self.observe()
        for key in ("expected_after", "expected_native_key", "expected_exact_rng", "unmodelled_rng"):
            r = response(f); del r[key]
            with self.assertRaises(HeadlessContractError): self.bridge.prepare(f, r)
        r = response(f); r["expected_native_key"] = None
        with self.assertRaises(HeadlessContractError): self.bridge.prepare(f, r)
        with self.assertRaises(HeadlessContractError): self.bridge.prepare(f, {"ack": True})

    def test_potion_and_end_native_signatures(self):
        f = self.observe()
        for command, method, args in (({"type": "END"}, "endTurn", ()), ({"type": "POTION", "potion_index": 0, "monster_index": 0}, "usePotion", (0, 0))):
            r = response(f); r["commands"][0]["command"] = json.dumps(command)
            p = self.bridge.prepare(f, r); self.assertEqual((p.native_method, p.native_args), (method, args))

    def test_stale_before_and_cross_run_are_rejected(self):
        f = self.observe(); p = self.bridge.prepare(f, response(f)); raw = combat_raw(); raw["player"]["gold"] += 1; raw["combat"]["snapshot"]["player_state"]["creature"]["gold"] += 1
        current = self.observe(raw, 1)
        self.assertFalse(self.bridge.validate_before(p, current))
        other = self.observe(combat_raw(), 0, "other")
        self.assertEqual(verify_combat_effect(p, other, {}), "REJECTED")

    def test_postcondition_requires_fresh_exact_native_evidence(self):
        f = self.observe(); raw = combat_raw(); raw["player"]["gold"] += 1; raw["combat"]["snapshot"]["player_state"]["creature"]["gold"] += 1
        after = self.observe(raw, 1); r = response(f, after); p = self.bridge.prepare(f, r)
        fp = {"supported": True, "rng_restore_mode": "exact_state", "projection": projection(after.snapshot),
              "native_key": r["expected_native_key"], "exact_rng": r["expected_exact_rng"]}
        self.assertEqual(verify_combat_effect(p, after, fp), "APPLIED")
        self.assertEqual(verify_combat_effect(p, self.observe(combat_raw(), 2), fp), "PENDING")
        for key in ("projection", "native_key", "exact_rng"):
            bad = copy.deepcopy(fp); bad[key] = {}
            self.assertEqual(verify_combat_effect(p, after, bad), "UNVERIFIED")

    def test_unmodelled_headless_rng_change_is_not_accepted(self):
        f = self.observe(); raw = combat_raw(); raw["combat"]["snapshot"]["_headless_rng"]["streams"]["headless"]["state0"] = "9123456789abcdef"
        after = self.observe(raw, 1); r = response(f); p = self.bridge.prepare(f, r)
        fp = {"supported": True, "rng_restore_mode": "exact_state", "projection": projection(after.snapshot), "native_key": r["expected_native_key"], "exact_rng": r["expected_exact_rng"]}
        self.assertEqual(verify_combat_effect(p, after, fp), "UNVERIFIED")

    def test_unmapped_source_private_state_change_is_not_accepted(self):
        raw = combat_raw(); raw["combat"]["snapshot"]["cur_map_node_state"]["monster_data"][0]["_private"] = {"Cultist.firstMove": True}
        f = self.observe(raw); r = response(f); plan = self.bridge.prepare(f, r)
        raw["combat"]["snapshot"]["cur_map_node_state"]["monster_data"][0]["_private"]["Cultist.firstMove"] = False
        after = self.observe(raw, 1)
        fp = {"supported": True, "rng_restore_mode": "exact_state", "projection": projection(after.snapshot), "native_key": r["expected_native_key"], "exact_rng": r["expected_exact_rng"]}
        self.assertEqual(verify_combat_effect(plan, after, fp), "UNVERIFIED")



class OmniscienceRewardReceiptTests(unittest.TestCase):
    def test_killing_attack_binds_the_chosen_uuid_and_stable_victory_facts(self):
        import copy
        from sts_ai_framework.headless.combat import _omniscience_victory
        streams=combat_raw()['combat']['snapshot']['_headless_rng']['streams']
        chosen=dict(id='SashWhip',uuid='selected-card',type='ATTACK')
        other=dict(id='Strike_P',uuid='master-card',timesUpgraded=0,misc=0,cost=1,costForTurn=1)
        player=dict(hp=33,maxHp=80,gold=10,masterDeck=[other],potions=[{'id':'Potion Slot'}])
        before=dict(player=player,cardSelection={'options':[chosen]},
                    interaction={'selection':{'purpose':'omniscience'}})
        after=dict(phase='REWARD',player=copy.deepcopy(player),combat_receipt={'rng':{'streams':streams}},
                   driver_receipt=dict(method='chooseGridCard',args=[0],sequence=42,card_uuid=chosen['uuid']))
        predicted=dict(terminal_prediction='victory',expected_after=dict(hp=33,max_hp=80,potions=['Potion Slot']),
                       expected_master_deck=[dict(id='Strike_P',upgrades=0,cost=1,turn_cost=1)],
                       expected_exact_rng=copy.deepcopy(streams),unmodelled_rng=copy.deepcopy(streams))
        command=dict(type='GRID_SELECT',card_index=0)
        def check(out):return _omniscience_victory(before,out,command,predicted,42)
        self.assertTrue(check(after))
        for change in (
            lambda x:x['driver_receipt'].update(card_uuid='different-card'),
            lambda x:x['driver_receipt'].update(card_uuid=None),
            lambda x:x['driver_receipt'].update(sequence=41),
            lambda x:x['player'].update(hp=32),
            lambda x:x['player']['masterDeck'][0].update(timesUpgraded=1),
            lambda x:x['combat_receipt']['rng']['streams']['card_random'].update(counter=999),
        ):
            altered=copy.deepcopy(after);change(altered)
            self.assertFalse(check(altered),change)


if __name__ == "__main__": unittest.main()
