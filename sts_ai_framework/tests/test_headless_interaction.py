"""Ironclad menus and receipts from synthetic snapshots; zero command execution."""
import copy
import json
import unittest

from sts_ai_framework.headless.interaction import INTERACTION_SCHEMA, IroncladInteractionBridge, verify_effect
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.models import GameAction
from sts_ai_framework.policy_runtime import candidates_for
from sts_ai_framework.tests.test_headless_state import observation, card


def raw(phase="SHOP", **changes):
    result = observation(phase=phase, map=None)
    result["interaction"] = {"schema": INTERACTION_SCHEMA}
    result.update(changes)
    return result


def shop(items=None, gold=99, **changes):
    result = raw("SHOP")
    result["player"]["gold"] = gold
    result["interaction"]["shop"] = {"purge_cost": 75, "purge_available": True,
        "purge_cards": [card()], "items": items if items is not None else [
            {"slot_id": "colored:2", "kind": "card", "id": "Bash", "native_index": 2,
             "price": 20, "upgrades": 0, "card_type": "ATTACK", "sold_out": False, "on_sale": True}]}
    result["interaction"]["shop"].update(changes)
    return result


def event(effects=None, phase="INTRO", followup="NONE", kind="EFFECT", status="KNOWN"):
    result = raw("EVENT")
    result["interaction"]["event"] = {"id": "Big Fish", "class_name": "BigFish", "phase": phase,
        "semantics_status": status, "decision_kind": "DETERMINISTIC", "choices": [
            {"button_index": 0, "action_index": 0, "enabled": True, "label": "任意本地化文本",
             "kind": kind, "followup": followup,
             "outcomes": [{"probability": 1.0, "effects": effects if effects is not None else [{"type": "gain_hp", "amount": 10}]}]}]}
    result["interaction"]["event_transitions"] = {"0": {"phases": ["RESULT"]}}
    return result


def reward(rows):
    result = raw("REWARD")
    result["interaction"].update(rewards=[{**row, "native_index": i} for i, row in enumerate(rows)], can_proceed=True)
    return result


def pick(cards=None, **controls):
    result = raw("CARD_PICK", cardRewardOptions=cards if cards is not None else [card(4, timesUpgraded=0)])
    result["interaction"]["card_reward"] = {"skip": True, "bowl": False, **controls}
    return result


def selection(purpose="upgrade", kind="grid", options=None, selected=None, **controls):
    result = raw("CARD_SELECT", cardSelection={"kind": kind, "options": options if options is not None else [card()],
                                              "selected": selected if selected is not None else []})
    result["interaction"]["selection"] = {"kind": kind, "purpose": purpose, "count": 1, "locked": False,
        "can_confirm": False, "any_number": False, "can_pick_zero": False, "auto_confirm": False, **controls}
    return result


def state_frame(bridge, snapshot, sequence=0, run="fixture-A"):
    return bridge.observe(snapshot, run_id=run, sequence=sequence)


def choose(bridge, frame, index=0):
    return bridge.prepare(frame, GameAction(type="choose", choice_index=index))


def wheel(stage='INTRO', result=None, *, purge=True):
    snapshot=event(effects=[],phase=stage,followup='EVENT',kind='CONTINUE')
    snapshot.update(room='EventRoom',roomPhase='EVENT')
    snapshot['interaction']['event'].update(id='Wheel of Change',class_name='GremlinWheelGame',decision_kind='FORCED')
    facts={'stage':stage,'native_phase':'INTRO' if stage=='SPIN' else stage,
           'start_spin':stage=='SPIN','finish_spin':False,'done_spinning':False,
           'button_pressed':stage in ('COMPLETE','LEAVE'),'gold_amount':200,'hp_loss':8,'purge_available':purge}
    if result is not None:facts['result']=result
    snapshot['interaction']['wheel']=facts
    return snapshot


def we_meet():
    snapshot=event(effects=[],followup='EVENT');snapshot['floor']=1
    snapshot['player']['masterDeck']=[card(1,timesUpgraded=0),card(2,timesUpgraded=1)]
    snapshot['player']['potions']=[{'id':'Fire Potion','name':'fire','empty':False,'targetRequired':True} for _ in range(2)]
    costs={'0':{'kind':'potion','slot':1,'id':'Fire Potion'},'1':{'kind':'gold','amount':50},
           '2':{'kind':'card','uuid':card(2)['uuid'],'id':'Searing Blow'}}
    snapshot['interaction']['we_meet_again']={'schema':'headless-wemeetagain-v1','phase':'INTRO','costs':costs}
    view=snapshot['interaction']['event'];view.update(id='WeMeetAgain',class_name='WeMeetAgain',decision_kind='COMPLEX')
    effects=[[{'type':'lose_potion','slot':1,'id':'Fire Potion'}],[{'type':'lose_gold','amount':50}],
             [{'type':'remove_card','card_id':'Searing Blow','id':card(2)['uuid']}],[]]
    view['choices']=[]
    for i in range(4):
        view['choices'].append({'button_index':i,'action_index':i,'enabled':True,'label':'localized',
            'kind':'LEAVE' if i==3 else 'EFFECT','followup':'EVENT','outcomes':[{'probability':1.0,
            'effects':effects[i]+([{'type':'random_relic','pool':'ANY','count':1}] if i<3 else [])}]})
    snapshot['interaction']['event_transitions']={str(i):{'phases':['COMPLETE']} for i in range(4)}
    return snapshot


def we_meet_after(source,button,relic='Anchor',counter=-1):
    snapshot=copy.deepcopy(source)
    snapshot['interaction']['we_meet_again']={'schema':'headless-wemeetagain-v1','phase':'COMPLETE',
        'reward_relic':None if button==3 else {'id':relic,'counter':counter}}
    snapshot['interaction']['event'].update(phase='COMPLETE',decision_kind='FORCED',choices=[{
        'button_index':0,'action_index':0,'enabled':True,'label':'localized','kind':'CONTINUE',
        'followup':'EVENT','outcomes':[{'probability':1.0,'effects':[]}]}])
    snapshot['interaction']['event_transitions']={'0':{'screens':['MAP']}}
    if button==3:return snapshot
    cost=source['interaction']['we_meet_again']['costs'][str(button)]
    if button==0:snapshot['player']['potions'][cost['slot']]={'id':'Potion Slot','name':'slot','empty':True,'targetRequired':False}
    elif button==1:snapshot['player']['gold']-=cost['amount']
    else:snapshot['player']['masterDeck']=[c for c in snapshot['player']['masterDeck'] if c['uuid']!=cost['uuid']]
    old={r['id'] for r in source['player']['relics']}
    if relic=='Circlet' and relic in old:
        next(r for r in snapshot['player']['relics'] if r['id']=='Circlet')['counter']+=1
    else:snapshot['player']['relics'].append({'id':relic,'name':relic,'counter':counter})
    if relic=='Old Coin' and 'Ectoplasm' not in old:
        snapshot['player']['gold']+=300
        if 'Bloody Idol' in old and 'Mark of the Bloom' not in old:
            snapshot['player']['hp']=min(snapshot['player']['maxHp'],snapshot['player']['hp']+5)
    gain={'Strawberry':7,'Pear':10,'Mango':14,"Lee's Waffle":7}.get(relic,0)
    if gain:
        snapshot['player']['maxHp']+=gain
        if 'Mark of the Bloom' not in old:
            snapshot['player']['hp']=snapshot['player']['maxHp'] if relic=="Lee's Waffle" else snapshot['player']['hp']+gain
    return snapshot


class EventBridgeTest(unittest.TestCase):
    def test_drug_dealer_transform_opens_generic_two_card_grid(self):
        source=event(effects=[{'type':'select_cards','purpose':'TRANSFORM','count':2},
                              {'type':'random_transform','count':2}],phase='0',followup='GRID')
        source['interaction']['event'].update(id='Drug Dealer',class_name='DrugDealer')
        source['interaction']['event_transitions']={'0':{'screens':['GRID']}}
        source['player']['masterDeck']=[card(1),card(2)]
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        after=selection('transform',options=source['player']['masterDeck'],count=2,auto_confirm=True)
        after['player']['masterDeck']=copy.deepcopy(source['player']['masterDeck'])
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        after['interaction']['selection']['purpose']='purge'
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')
        after['interaction']['selection'].update(purpose='transform',count=1)
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,3)).status,'UNVERIFIED')

    def test_we_meet_again_costs_are_source_selected_and_reward_is_deferred(self):
        for button in (0,1,2):
            with self.subTest(button=button):
                source=we_meet();bridge=IroncladInteractionBridge()
                plan=choose(bridge,state_frame(bridge,source),button)
                evidence=json.loads(plan.action.evidence_json)
                self.assertEqual(evidence['kind'],'event')
                self.assertEqual(evidence['we_meet_again']['cost'],source['interaction']['we_meet_again']['costs'][str(button)])
                self.assertEqual(plan.action.native_args,(button,))
                effects=evidence['option']['outcomes'][0]['effects']
                self.assertEqual(effects[-1],{'type':'random_relic','pool':'ANY','count':1})
                self.assertNotIn('obtain_relic',[e['type'] for e in effects])
                after=we_meet_after(source,button)
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')

    def test_we_meet_again_duplicate_potions_require_the_original_slot(self):
        source=we_meet();bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source),0)
        after=we_meet_after(source,0)
        wrong=copy.deepcopy(after)
        wrong['player']['potions']=list(reversed(wrong['player']['potions']))
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,1)).status,'UNVERIFIED')
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'APPLIED')
        wrong=copy.deepcopy(after);wrong['player']['potions'][0]['id']='Strength Potion'
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,3)).status,'UNVERIFIED')

    def test_we_meet_again_potion_belt_appends_empty_slots_and_draws_one_tier(self):
        from sts_ai_framework.headless.event_receipts import Stream
        from sts_ai_framework.headless.combat import STREAMS
        source=we_meet()
        start={'counter':0,'state0':'123456789abcdef1','state1':'edcba98765432109'}
        source['combat_receipt']={'rng':{'schema':'headless-rng-v1','policy':'headless-b354a9ee-v1',
            'streams':{k:dict(start) for k in STREAMS},'math':{k:start[k] for k in ('state0','state1')}}}
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source),0)
        after=we_meet_after(source,0,'Potion Belt')
        slots=after['player']['potions']
        slots.extend([dict(slots[1]),dict(slots[1])])
        rng=Stream(start);rng.bounded(100)
        after['combat_receipt']['rng']['streams']['relic']=rng.state()
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        slots[-1]=dict(slots[0])
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')
        slots[-1]=dict(slots[1])
        after['combat_receipt']['rng']['streams']['relic']=start
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,3)).status,'UNVERIFIED')

    def test_we_meet_again_duplicate_card_ids_require_exact_uuid_and_other_cards_unchanged(self):
        source=we_meet();bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source),2)
        after=we_meet_after(source,2)
        wrong=copy.deepcopy(after);wrong['player']['masterDeck']=[copy.deepcopy(source['player']['masterDeck'][1])]
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,1)).status,'UNVERIFIED')
        wrong=copy.deepcopy(after);wrong['player']['masterDeck'][0]['baseDamage']+=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,3)).status,'APPLIED')

    def test_we_meet_again_requires_exact_gold_and_one_bound_relic(self):
        source=we_meet();source['player']['relics']=[{'id':'Bag of Marbles','name':'marbles','counter':-1}]
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source),1)
        after=we_meet_after(source,1)
        wrong=copy.deepcopy(after);wrong['player']['gold']+=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,1)).status,'UNVERIFIED')
        wrong=copy.deepcopy(after);wrong['player']['relics'].append({'id':'Pear','name':'Pear','counter':-1})
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
        wrong=copy.deepcopy(after);wrong['player']['relics'][0]['counter']=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,3)).status,'UNVERIFIED')
        wrong=copy.deepcopy(after);wrong['interaction']['we_meet_again']['reward_relic']['id']='Pear'
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,4)).status,'UNVERIFIED')
        wrong=copy.deepcopy(after);wrong['player']['relics'][-1]['counter']=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,5)).status,'UNVERIFIED')
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,6)).status,'APPLIED')

    def test_we_meet_again_known_pickup_resources_and_circlet_are_exact(self):
        for relic,owned,counter in [('Mango',None,-1),('Mango','Mark of the Bloom',-1),
                                    ('Old Coin',None,-1),('Old Coin','Ectoplasm',-1),
                                    ('Old Coin','Bloody Idol',-1),('Circlet','Circlet',1)]:
            with self.subTest(relic=relic,owned=owned):
                source=we_meet()
                if owned:source['player']['relics']=[{'id':owned,'name':owned,'counter':3 if owned=='Circlet' else -1}]
                bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source),1)
                after=we_meet_after(source,1,relic,counter)
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
                after['player']['hp']-=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')

    def test_we_meet_again_decline_then_map_is_effect_free(self):
        from sts_ai_framework.tests.test_headless_flow import map_frame
        source=we_meet();bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source),3)
        complete=we_meet_after(source,3);complete_frame=state_frame(bridge,complete,1)
        self.assertEqual(verify_effect(plan,complete_frame).status,'APPLIED')
        wrong=we_meet_after(source,0)
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
        leave=choose(bridge,complete_frame)
        mapped=map_frame();mapped['player']=copy.deepcopy(complete['player'])
        mapped['interaction']['we_meet_again']=copy.deepcopy(complete['interaction']['we_meet_again'])
        mapped['interaction']['flow']['event_class']='WeMeetAgain'
        self.assertEqual(verify_effect(leave,state_frame(bridge,mapped,3)).status,'APPLIED')
        mapped['player']['gold']+=1
        self.assertEqual(verify_effect(leave,state_frame(bridge,mapped,4)).status,'UNVERIFIED')

    def test_we_meet_again_missing_source_facts_or_unknown_reward_changes_stay_closed(self):
        source=we_meet();source['interaction'].pop('we_meet_again')
        with self.assertRaisesRegex(HeadlessContractError,'Missing native'):state_frame(IroncladInteractionBridge(),source)
        source=we_meet();source['interaction']['we_meet_again']['reward_relic']=None
        with self.assertRaisesRegex(HeadlessContractError,'hidden'):state_frame(IroncladInteractionBridge(),source)
        source=we_meet();source['interaction']['we_meet_again']['costs']['2']['uuid']=card(90)['uuid']
        with self.assertRaisesRegex(HeadlessContractError,'original eligible UUID'):state_frame(IroncladInteractionBridge(),source)
        source=we_meet();bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source),1)
        after=we_meet_after(source,1,'War Paint')
        after['player']['masterDeck'][0]['timesUpgraded']+=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'UNVERIFIED')
        after=we_meet_after(source,1);after['interaction']['we_meet_again']['reward_relic']=None
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')

    def test_back_to_basics_native_tag_ids_upgrade_exactly_for_all_four_roles(self):
        for role,suffix in [('IRONCLAD','R'),('THE_SILENT','G'),('DEFECT','B'),('WATCHER','P')]:
            with self.subTest(role=role):
                ids=['Strike_'+suffix,'Defend_'+suffix]
                source=event(effects=[{'type':'upgrade_matching','pattern':'STARTER_STRIKE_DEFEND','card_ids':ids}],followup='EVENT')
                source['interaction']['event'].update(id='Back to Basics',class_name='BackToBasics')
                source['interaction']['event_transitions']={'0':{'phases':['COMPLETE']}}
                source['player']['className']=role
                source['player']['masterDeck']=[card(1,id=ids[0],timesUpgraded=0),card(2,id=ids[1],timesUpgraded=0),
                    card(3,id='Bash',timesUpgraded=0),card(4,id=ids[0],timesUpgraded=1,canUpgrade=False)]
                bridge=IroncladInteractionBridge(characters=(role,));plan=choose(bridge,state_frame(bridge,source))
                after=copy.deepcopy(source);after['interaction']['event']['phase']='COMPLETE'
                for c in after['player']['masterDeck'][:2]:c.update(timesUpgraded=1,canUpgrade=False)
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
                wrong=copy.deepcopy(after);wrong['player']['masterDeck'][2]['timesUpgraded']=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
                wrong=copy.deepcopy(after);wrong['player']['masterDeck'][0]['uuid']=card(90)['uuid']
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,3)).status,'UNVERIFIED')
                wrong=copy.deepcopy(after);wrong['player']['masterDeck'][0]['id']='Bash'
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,4)).status,'UNVERIFIED')
                wrong=copy.deepcopy(after);wrong['player']['masterDeck'][2]['baseDamage']+=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,5)).status,'UNVERIFIED')
                wrong=copy.deepcopy(after);wrong['player']['relics']=[{'id':'Anchor','name':'Anchor','counter':-1}]
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,6)).status,'UNVERIFIED')

    def test_upgrade_matching_preserves_legacy_filter_and_explicit_empty_set(self):
        for explicit in (False,True):
            with self.subTest(explicit=explicit):
                effect={'type':'upgrade_matching','pattern':'STARTER_STRIKE_DEFEND'}
                if explicit:effect['card_ids']=[]
                source=event(effects=[effect],followup='EVENT')
                source['player']['masterDeck']=[card(1,id='Strike_R',timesUpgraded=0),card(2,id='Strike_G',timesUpgraded=0)]
                bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
                encoded=json.loads(plan.action.evidence_json)['option']['outcomes'][0]['effects'][0]
                self.assertEqual('card_ids' in encoded,explicit)
                after=copy.deepcopy(source);after['interaction']['event']['phase']='RESULT'
                if not explicit:after['player']['masterDeck'][0]['timesUpgraded']=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
                after['player']['masterDeck'][1]['timesUpgraded']=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')

    def test_back_to_basics_purge_grid_uses_native_automatic_commit(self):
        source=event(effects=[{'type':'select_cards','purpose':'PURGE','count':1}],followup='GRID')
        source['interaction']['event'].update(id='Back to Basics',class_name='BackToBasics')
        source['interaction']['event_transitions']={'0':{'screens':['GRID']}}
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        grid=selection(purpose='purge',auto_confirm=True)
        grid_frame=state_frame(bridge,grid,1)
        self.assertEqual(verify_effect(plan,grid_frame).status,'APPLIED')
        selected=choose(bridge,grid_frame)
        after=event(effects=[],phase='COMPLETE',kind='CONTINUE',followup='EVENT')
        after['interaction']['event'].update(id='Back to Basics',class_name='BackToBasics')
        after['player']['masterDeck']=[]
        self.assertEqual(verify_effect(selected,state_frame(bridge,after,2)).status,'APPLIED')
        after['player']['masterDeck']=copy.deepcopy(source['player']['masterDeck'])
        self.assertEqual(verify_effect(selected,state_frame(bridge,after,3)).status,'UNVERIFIED')

    def test_back_to_basics_no_purge_candidates_still_has_complete_dialog(self):
        source=event(effects=[],kind='CONTINUE',followup='EVENT')
        source['interaction']['event'].update(id='Back to Basics',class_name='BackToBasics')
        source['interaction']['event_transitions']={'0':{'phases':['COMPLETE']}}
        source['player']['masterDeck']=[]
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        after=copy.deepcopy(source);after['interaction']['event']['phase']='COMPLETE'
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        after['player']['gold']+=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')

    def test_confirmed_leave_phases_for_shrines_vampires_and_zero_gold_goop(self):
        for name,start,button,landing,effects in [
                ('Vampires','0',1,'2',[]),('Vampires','0',2,'2',[]),
                ('UpgradeShrine','INTRO',1,'COMPLETE',[]),('GoldShrine','INTRO',2,'COMPLETE',[]),
                ('GoopPuddle','INTRO',1,'RESULT',[{'type':'lose_gold','amount':0}])]:
            with self.subTest(name=name,button=button):
                source=event(effects=effects,phase=start,kind='LEAVE',followup='EVENT')
                source['interaction']['event'].update(id=name,class_name=name)
                source['interaction']['event']['choices'][0]['button_index']=button
                source['interaction']['event_transitions']={str(button):{'phases':[landing]}}
                bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
                self.assertEqual(plan.action.native_args,(button,))
                after=copy.deepcopy(source);after['interaction']['event']['phase']=landing
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
                wrong=copy.deepcopy(after);wrong['player']['gold']+=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')

    def test_gold_shrine_and_goop_award_immediately_but_remain_on_result_dialog(self):
        for name,landing,gold,damage in [('GoldShrine','COMPLETE',100,0),('GoopPuddle','RESULT',75,11)]:
            with self.subTest(name=name):
                effects=[{'type':'gain_gold','amount':gold}]
                if damage:effects.append({'type':'lose_hp','amount':damage})
                source=event(effects=effects,followup='EVENT')
                source['interaction']['event'].update(id=name,class_name=name)
                source['interaction']['event_transitions']={'0':{'phases':[landing]}}
                bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
                after=copy.deepcopy(source);after['interaction']['event']['phase']=landing
                after['player']['gold']+=gold;after['player']['hp']-=damage
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
                wrong=copy.deepcopy(after);wrong['interaction']['event']['phase']='INTRO'
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
    def test_serpent_commit_defers_gold_and_curse_until_agree_button(self):
        source=event(effects=[],kind='COMMIT',followup='EVENT')
        source['interaction']['event'].update(id='Liars Game',class_name='Sssserpent')
        source['interaction']['event_transitions']={'0':{'phases':['AGREE']}}
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        agreed=copy.deepcopy(source);agreed['interaction']['event']['phase']='AGREE'
        agreed['interaction']['event']['choices'][0].update(kind='EFFECT',outcomes=[{'probability':1.0,'effects':[
            {'type':'gain_gold','amount':175},{'type':'add_card','card_id':'Doubt','amount':1}]}])
        agreed['interaction']['event_transitions']={'0':{'phases':['COMPLETE']}}
        agreed_frame=state_frame(bridge,agreed,1)
        self.assertEqual(verify_effect(plan,agreed_frame).status,'APPLIED')
        early=copy.deepcopy(agreed);early['player']['gold']+=175
        self.assertEqual(verify_effect(plan,state_frame(bridge,early,2)).status,'UNVERIFIED')
        reward_plan=choose(bridge,agreed_frame)
        after=copy.deepcopy(agreed);after['interaction']['event']['phase']='COMPLETE'
        after['player']['gold']+=175
        after['player']['masterDeck'].append(card(2,id='Doubt',type='CURSE',rarity='CURSE',timesUpgraded=0))
        self.assertEqual(verify_effect(reward_plan,state_frame(bridge,after,3)).status,'APPLIED')
        wrong=copy.deepcopy(after);wrong['player']['gold']-=1
        self.assertEqual(verify_effect(reward_plan,state_frame(bridge,wrong,4)).status,'UNVERIFIED')
        wrong=copy.deepcopy(after);wrong['player']['masterDeck'].pop()
        self.assertEqual(verify_effect(reward_plan,state_frame(bridge,wrong,5)).status,'UNVERIFIED')

    def test_beggar_payment_precedes_separate_purge_grid(self):
        source=event(effects=[{'type':'lose_gold','amount':75}],followup='EVENT')
        source['interaction']['event'].update(id='Beggar',class_name='Beggar')
        source['interaction']['event_transitions']={'0':{'phases':['GAVE_MONEY']}}
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        paid=copy.deepcopy(source);paid['player']['gold']-=75
        paid['interaction']['event']['phase']='GAVE_MONEY'
        paid['interaction']['event']['choices'][0].update(kind='REMOVE_CARD',followup='GRID',outcomes=[{
            'probability':1.0,'effects':[{'type':'select_cards','purpose':'PURGE','count':1}]}])
        paid['interaction']['event_transitions']={'0':{'screens':['GRID']}}
        paid_frame=state_frame(bridge,paid,1)
        self.assertEqual(verify_effect(plan,paid_frame).status,'APPLIED')
        wrong=copy.deepcopy(paid);wrong['player']['gold']+=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
        grid=selection(purpose='purge');grid['player']=copy.deepcopy(paid['player'])
        self.assertEqual(verify_effect(plan,state_frame(bridge,grid,3)).status,'UNVERIFIED')
        grid_plan=choose(bridge,paid_frame)
        self.assertEqual(verify_effect(grid_plan,state_frame(bridge,grid,4)).status,'APPLIED')
        wrong=copy.deepcopy(grid);wrong['player']['gold']=0
        self.assertEqual(verify_effect(grid_plan,state_frame(bridge,wrong,5)).status,'UNVERIFIED')

    def test_serpent_and_beggar_decline_need_a_second_leave_button(self):
        from sts_ai_framework.tests.test_headless_flow import map_frame
        for name,identity,landing in [('Sssserpent','Liars Game','DISAGREE'),('Beggar','Beggar','LEAVE')]:
            with self.subTest(event=name):
                source=event(effects=[],kind='LEAVE',followup='EVENT');source['floor']=1
                source['interaction']['event'].update(id=identity,class_name=name)
                source['interaction']['event']['choices'][0]['button_index']=1
                source['interaction']['event_transitions']={'1':{'phases':[landing]}}
                bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
                self.assertEqual(plan.action.native_args,(1,))
                after=copy.deepcopy(source);after['interaction']['event']['phase']=landing
                after['interaction']['event']['choices'][0].update(kind='CONTINUE',button_index=0,followup='EVENT')
                after['interaction']['event_transitions']={'0':{'screens':['MAP']}}
                after_frame=state_frame(bridge,after,1)
                self.assertEqual(verify_effect(plan,after_frame).status,'APPLIED')
                wrong=copy.deepcopy(after);wrong['player']['hp']-=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
                leave_plan=choose(bridge,after_frame)
                mapped=map_frame()
                self.assertEqual(verify_effect(leave_plan,state_frame(bridge,mapped,3)).status,'APPLIED')

    def test_explicit_empty_dialog_stages_preserve_all_resources(self):
        for name,start,landing in [('Nest','0','1'),('FaceTrader','INTRO','MAIN'),('Designer','INTRO','MAIN'),
                                    ('FaceTrader','MAIN','RESULT'),('ShiningLight','INTRO','COMPLETE'),
                                    ('PurificationShrine','INTRO','COMPLETE'),('AccursedBlacksmith','0','2')]:
            with self.subTest(name=name,start=start):
                source=event(effects=[],phase=start,kind='CONTINUE',followup='EVENT')
                source['interaction']['event'].update(id=name,class_name=name)
                source['interaction']['event_transitions']={'0':{'phases':[landing]}}
                bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
                after=copy.deepcopy(source);after['interaction']['event']['phase']=landing
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
                after['player']['gold']-=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')
                after=copy.deepcopy(source);after['interaction']['event']['choices'][0]['label']='changed only'
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,3)).status,'UNVERIFIED')

    def test_mushrooms_commit_then_separate_combat_entry(self):
        source=event(effects=[{'type':'commit_encounter','encounter':'The Mushroom Lair'}],phase='0',followup='EVENT')
        source['interaction']['event'].update(id='Mushrooms',class_name='Mushrooms')
        source['interaction']['event_transitions']={'0':{'phases':['2']}}
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        after=copy.deepcopy(source);after['interaction']['event']['phase']='2'
        after['interaction']['event']['choices'][0].update(followup='COMBAT',outcomes=[{
            'probability':1.0,'effects':[{'type':'start_combat','encounter':'The Mushroom Lair'}]}])
        after['interaction']['event_transitions']={}
        second=state_frame(bridge,after,1)
        self.assertEqual(verify_effect(plan,second).status,'APPLIED')
        wrong=copy.deepcopy(after);wrong['player']['hp']-=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
        wrong=copy.deepcopy(after);wrong['player']['masterDeck']=[]
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,3)).status,'UNVERIFIED')
        second_plan=choose(bridge,second)
        combat=raw('PLAYER_TURN',roomPhase='COMBAT')
        self.assertEqual(verify_effect(second_plan,state_frame(bridge,combat,4)).status,'APPLIED')
        wrong=raw('PROCEED',roomPhase='COMPLETE')
        wrong['interaction']['can_proceed']=False
        self.assertEqual(verify_effect(second_plan,state_frame(bridge,wrong,5)).status,'UNVERIFIED')

    def test_wheel_intro_starts_only_then_spin_preawards_only_gold(self):
        source=wheel();bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        spin=wheel('SPIN');spin_frame=state_frame(bridge,spin,1)
        self.assertEqual(plan.action.native_method,'chooseDialogOption')
        self.assertEqual(plan.action.native_args,(0,))
        self.assertEqual(verify_effect(plan,spin_frame).status,'APPLIED')
        bad=copy.deepcopy(spin);bad['player']['gold']+=200
        self.assertEqual(verify_effect(plan,state_frame(bridge,bad,2)).status,'UNVERIFIED')
        spin_plan=choose(bridge,spin_frame)
        for result in range(6):
            after=wheel('COMPLETE',result)
            if result==0:after['player']['gold']+=200
            self.assertEqual(verify_effect(spin_plan,state_frame(bridge,after,10+2*result)).status,'APPLIED')
            wrong=copy.deepcopy(after);wrong['player']['hp']-=1
            self.assertEqual(verify_effect(spin_plan,state_frame(bridge,wrong,11+2*result)).status,'UNVERIFIED')
        wrong=wheel('COMPLETE',0)
        self.assertEqual(verify_effect(spin_plan,state_frame(bridge,wrong,30)).status,'UNVERIFIED')
        wrong=wheel('COMPLETE',2);wrong['player']['hp']=80
        self.assertEqual(verify_effect(spin_plan,state_frame(bridge,wrong,31)).status,'UNVERIFIED')

    def test_wheel_result_is_hidden_and_spin_control_must_be_pressable(self):
        for stage in ('INTRO','SPIN'):
            with self.assertRaisesRegex(HeadlessContractError,'leaked'):
                state_frame(IroncladInteractionBridge(),wheel(stage,0))
        broken=wheel('SPIN');broken['interaction']['wheel']['button_pressed']=True
        with self.assertRaisesRegex(HeadlessContractError,'actionable'):
            state_frame(IroncladInteractionBridge(),broken)
        broken=wheel('SPIN');broken['interaction']['wheel']['native_phase']='SPIN'
        with self.assertRaisesRegex(HeadlessContractError,'phase mismatch'):
            state_frame(IroncladInteractionBridge(),broken)

    def test_wheel_claim_gold_is_noop_and_other_results_are_deferred(self):
        for result in (0,2,3,4,5):
            with self.subTest(result=result):
                source=wheel('COMPLETE',result,purge=False)
                bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
                after=wheel('LEAVE',result,purge=False)
                if result==2:after['player']['hp']=80
                if result==3:after['player']['masterDeck'].append(card(2,id='Decay',type='CURSE',rarity='CURSE',timesUpgraded=0))
                if result==5:after['player']['hp']-=8
                self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
                wrong=copy.deepcopy(after);wrong['player']['gold']+=1
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
                wrong=copy.deepcopy(after);wrong['interaction']['wheel']['result']=(result+1)%6
                self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,3)).status,'UNVERIFIED')
        source=wheel('COMPLETE',2);source['player']['hp']=80
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        after=wheel('LEAVE',2);after['player']['hp']=80
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')

    def test_wheel_relic_opens_one_reward_without_granting_it(self):
        source=wheel('COMPLETE',1);bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        after=reward([{'type':'RELIC','relic_id':'Anchor'}]);after['roomPhase']='COMPLETE'
        after['interaction']['wheel']=wheel('LEAVE',1)['interaction']['wheel']
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        wrong=copy.deepcopy(after);wrong['interaction']['rewards'].append({'native_index':1,'type':'GOLD','amount':10})
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
        wrong=copy.deepcopy(after);wrong['player']['relics']=[{'id':'Anchor','name':'Anchor','counter':-1}]
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,3)).status,'UNVERIFIED')

    def test_wheel_purge_opens_only_correct_grid_then_leave_closes_same_event(self):
        source=wheel('COMPLETE',4);bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        after=selection(purpose='purge');after['interaction']['wheel']=wheel('LEAVE',4)['interaction']['wheel']
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        wrong=copy.deepcopy(after);wrong['interaction']['selection']['purpose']='upgrade'
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,2)).status,'UNVERIFIED')
        from sts_ai_framework.tests.test_headless_flow import map_frame
        source=wheel('LEAVE',0);source['floor']=1
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        after=map_frame();after['interaction']['wheel']=source['interaction']['wheel']
        after['interaction']['flow']['event_class']='GremlinWheelGame'
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        after['interaction']['flow']['event_class']='OtherEvent'
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')

    def test_cleric_leave_stops_at_proceed_dialog_before_map(self):
        source = event(effects=[], phase='0', kind='LEAVE', followup='EVENT')
        source['interaction']['event'].update(id='The Cleric', class_name='Cleric')
        source['interaction']['event_transitions'] = {'0': {'phases': ['99']}}
        bridge=IroncladInteractionBridge(); plan=choose(bridge,state_frame(bridge,source))
        after=copy.deepcopy(source); after['interaction']['event']['phase']='99'
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        after['player']['gold']-=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')

    def test_golden_wing_inclusive_random_gold_and_declared_map_dialog(self):
        source = event(effects=[], followup="EVENT")
        source['interaction']['event'].update(id='Golden Wing', class_name='GoldenWing', decision_kind='STOCHASTIC')
        source['interaction']['event']['choices'][0]['outcomes'] = [
            {'probability': 1/31, 'effects': [{'type': 'gain_gold', 'amount': amount}]}
            for amount in range(50, 81)]
        source['interaction']['event_transitions'] = {'0': {'phases': ['MAP']}}
        bridge = IroncladInteractionBridge(); plan = choose(bridge, state_frame(bridge, source))
        for sequence, amount in enumerate((50, 65, 80), 1):
            after = copy.deepcopy(source); after['interaction']['event']['phase'] = 'MAP'
            after['player']['gold'] += amount
            self.assertEqual(verify_effect(plan, state_frame(bridge, after, sequence)).status, 'APPLIED')
        for sequence, amount in enumerate((0, 49, 81), 4):
            after = copy.deepcopy(source); after['interaction']['event']['phase'] = 'MAP'
            after['player']['gold'] += amount
            self.assertEqual(verify_effect(plan, state_frame(bridge, after, sequence)).status, 'UNVERIFIED')
        after = copy.deepcopy(source); after['player']['gold'] += 65
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 100)).status, 'UNVERIFIED')
        after['interaction']['event']['phase'] = 'MAP'
        after['player']['masterDeck'].append(card(999, id='Bash'))
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 101)).status, 'UNVERIFIED')

    def test_golden_wing_hp_payment_stages_before_purge_grid(self):
        source = event(effects=[{'type': 'lose_hp', 'amount': 7}], followup='EVENT')
        source['interaction']['event'].update(id='Golden Wing', class_name='GoldenWing')
        source['interaction']['event_transitions'] = {'0': {'phases': ['PURGE']}}
        bridge = IroncladInteractionBridge(); plan = choose(bridge, state_frame(bridge, source))
        after = copy.deepcopy(source); after['interaction']['event']['phase'] = 'PURGE'
        after['player']['hp'] -= 7
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, 'APPLIED')
        after['player']['hp'] += 1
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, 'UNVERIFIED')

    def test_woman_in_blue_leave_has_result_dialog_before_map(self):
        source=event(effects=[],kind='LEAVE',followup='EVENT')
        source['interaction']['event'].update(id='The Woman in Blue',class_name='WomanInBlue')
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,source))
        after=copy.deepcopy(source);after['interaction']['event']['phase']='RESULT'
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        after['player']['gold']-=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')

    def test_random_upgrade_amount_requires_exact_count_and_same_card_ids(self):
        source = event(effects=[{"type":"random_upgrade","amount":2}])
        source['player']['masterDeck'] = [card(1,timesUpgraded=0),card(2,timesUpgraded=0)]
        bridge=IroncladInteractionBridge(); plan=choose(bridge,state_frame(bridge,source))
        after=copy.deepcopy(source); after['interaction']['event']['phase']='RESULT'
        for c in after['player']['masterDeck']: c['timesUpgraded']=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        after['player']['masterDeck'][1]['timesUpgraded']=0
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')
        after['player']['masterDeck'][1].update(timesUpgraded=1,id='Bash')
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,3)).status,'UNVERIFIED')

    def test_forced_event_close_requires_same_native_event_and_inventory(self):
        from sts_ai_framework.tests.test_headless_flow import map_frame
        source = map_frame(); source.update(phase="EVENT", room="EventRoom", roomPhase="EVENT")
        source["interaction"]["event"] = event(effects=[], phase="1", kind="CONTINUE", followup="EVENT")["interaction"]["event"]
        source["interaction"]["event"].update(class_name="DrugDealer", decision_kind="FORCED")
        source["interaction"]["event_transitions"] = {}
        source["interaction"]["flow"]["event_class"] = "DrugDealer"
        bridge = IroncladInteractionBridge(); plan = choose(bridge, state_frame(bridge, source))
        after = copy.deepcopy(source); after.update(phase="MAP", roomPhase="COMPLETE"); after["interaction"].pop("event")
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        wrong = copy.deepcopy(after); wrong["interaction"]["flow"]["event_class"] = "OtherEvent"
        self.assertEqual(verify_effect(plan, state_frame(bridge, wrong, 2)).status, "UNVERIFIED")
        wrong = copy.deepcopy(after); wrong["player"]["masterDeck"] = []
        self.assertEqual(verify_effect(plan, state_frame(bridge, wrong, 3)).status, "UNVERIFIED")

    def test_book_reward_and_automatic_fairy_revival_are_separate_effects(self):
        source = event(effects=[{"type": "lose_hp", "amount": 10}, {"type": "relic_reward", "pool": "BOOK"}],
                       phase="LAST_PAGE", followup="COMBAT_REWARD")
        source["interaction"]["event"].update(id="Cursed Tome", class_name="CursedTome")
        source["interaction"]["event_transitions"] = {"0": {"screens": ["COMBAT_REWARD"]}}
        source["player"]["hp"] = 4
        source["player"]["potions"] = [{"id": "FairyPotion", "name": "fairy", "empty": False, "targetRequired": False}]
        bridge = IroncladInteractionBridge(); plan = choose(bridge, state_frame(bridge, source))
        after = reward([{"type": "RELIC", "relic_id": "Nilry's Codex"}]); after["player"]["hp"] = 24
        after["player"]["potions"] = [{"id": "Potion Slot", "name": "slot", "empty": True, "targetRequired": False}]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["potions"] = copy.deepcopy(source["player"]["potions"])
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")
        after["player"]["potions"] = [{"id": "Potion Slot", "name": "slot", "empty": True, "targetRequired": False}]
        after["interaction"]["rewards"][0]["relic_id"] = "Vajra"
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 3)).status, "UNVERIFIED")

    def test_known_fatal_event_cost_requires_real_defeat_facts(self):
        from sts_ai_framework.tests.test_headless_flow import terminal
        source = event(effects=[{"type": "lose_hp", "amount": 10}], followup="MAP")
        source.update(act=3, floor=52); source["player"]["hp"] = 4
        bridge = IroncladInteractionBridge(); plan = choose(bridge, state_frame(bridge, source))
        after = terminal(); after["player"]["gold"] = source["player"]["gold"]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")

    def test_match_flip_requires_source_reveal_and_exact_pair_resolution(self):
        def source(face=None, attempts=5, matched=0, slots=(0, 1, 2, 3), last=None):
            r = event(effects=[], phase="PLAY", kind="MINI_GAME_CARD")
            r["interaction"]["event"].update(id="Match and Keep!", class_name="GremlinMatchGame")
            r["interaction"]["event"]["choices"] = []
            n = 0
            rows = []
            for slot in slots:
                up = slot == face
                rows.append({"slot": slot, "face_up": up, "card_id": "Bash" if up else None})
                r["interaction"]["event"]["choices"].append({"button_index": slot, "action_index": None if up else n,
                    "enabled": not up, "label": "slot", "kind": "MINI_GAME_CARD", "followup": "EVENT", "outcomes": []})
                n += not up
            r["interaction"]["match_game"] = {"screen": "PLAY", "attempts": attempts, "matched": matched, "cards": rows, "last_flip": last}
            return r
        bridge = IroncladInteractionBridge(); before = state_frame(bridge, source())
        plan = choose(bridge, before)
        after = source(face=0, last={"slot": 0, "card_id": "Bash"})
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        invalid = copy.deepcopy(after); invalid["interaction"]["match_game"]["last_flip"]["slot"] = 1
        self.assertEqual(verify_effect(plan, state_frame(bridge, invalid, 2)).status, "UNVERIFIED")
        before = state_frame(bridge, after, 3); plan = choose(bridge, before)
        mismatch = source(attempts=4, last={"slot": 1, "card_id": "Defend_R"})
        self.assertEqual(verify_effect(plan, state_frame(bridge, mismatch, 4)).status, "APPLIED")
        match = source(attempts=4, matched=1, slots=(2, 3), last={"slot": 1, "card_id": "Bash"})
        match["player"]["masterDeck"].append(card(id="Bash", uuid="00000000-0000-0000-0000-000000000125"))
        self.assertEqual(verify_effect(plan, state_frame(bridge, match, 5)).status, "APPLIED")
        match["interaction"]["match_game"]["attempts"] = 5
        self.assertEqual(verify_effect(plan, state_frame(bridge, match, 6)).status, "UNVERIFIED")

    def test_declared_result_to_map_is_not_a_generic_screen_bypass(self):
        source = event(effects=[], phase="RESULT", followup="EVENT", kind="CONTINUE")
        source["interaction"]["event_transitions"] = {"0": {"screens": ["MAP"]}}
        bridge = IroncladInteractionBridge(); plan = choose(bridge, state_frame(bridge, source))
        after = raw("MAP")
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["gold"] += 1
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")
        other = raw("PROCEED"); other["interaction"]["can_proceed"] = True
        self.assertEqual(verify_effect(plan, state_frame(bridge, other, 3)).status, "UNVERIFIED")

    def test_combat_discovery_does_not_add_a_permanent_card(self):
        source = pick([card(id="Bash")]); source["roomPhase"] = "COMBAT"
        source["interaction"]["card_reward"].update(discovery=True, source_action="ForeignInfluenceAction", copies=1, free_for_turn=True)
        bridge = IroncladInteractionBridge(); frame = state_frame(bridge, source); plan = choose(bridge, frame)
        self.assertEqual(json.loads(plan.action.evidence_json)["kind"], "take_combat_card")
        after = raw("PLAYER_TURN", roomPhase="COMBAT")
        after["player"]["hand"] = [card(id="Bash", uuid="00000000-0000-0000-0000-000000000123", costForTurn=0)]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["masterDeck"].append(card(id="Bash", uuid="00000000-0000-0000-0000-000000000124"))
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")

    def test_codex_proves_insertion_before_the_turn_clears_combat_piles(self):
        source=pick([card(4,id='Bash',timesUpgraded=0)],discovery=True,source_action='CodexAction',destination='draw_pile')
        source['roomPhase']='COMBAT'
        bridge=IroncladInteractionBridge(); plan=choose(bridge,state_frame(bridge,source))
        after=reward([])
        result=lambda seq=1:verify_effect(plan,state_frame(IroncladInteractionBridge(),after,seq)).status
        self.assertEqual(result(),'UNVERIFIED')
        observed=dict(source_action='CodexAction',index=0,offered_uuid=card(4)['uuid'],created_uuid=card(20)['uuid'],
                      card_id='Bash',upgrades=0,destination='draw_pile')
        receipt=dict(method='chooseRewardCard',args=[0],sequence=1,combat_card_insertion=observed)
        after['driver_receipt']=receipt
        self.assertEqual(result(),'APPLIED')
        for wrong in (dict(offered_uuid=card(3)['uuid']),dict(created_uuid=card(4)['uuid']),dict(card_id='Strike_R'),
                      dict(upgrades=1),dict(destination='hand'),dict(index=1),dict(source_action='DiscoveryAction')):
            with self.subTest(wrong=wrong):
                receipt['combat_card_insertion']=dict(observed,**wrong)
                self.assertEqual(result(),'UNVERIFIED')
        receipt['combat_card_insertion']=observed
        self.assertEqual(result(2),'UNVERIFIED')

    def test_combat_selection_witness_binds_source_cards_and_turn_before_the_next_picker(self):
        for purpose,kind,src,method,destination,chosen in (
                ('retain','hand','RetainCardsAction','confirmHandSelect','retained_hand',[]),
                ('discard','hand','DiscardAction','confirmHandSelect','discard_pile',[card(8)]),
                ('scry','grid','ScryAction','confirmGrid','discard_pile',[card(8)])):
            with self.subTest(purpose=purpose):
                source=selection(purpose,kind,selected=chosen,can_confirm=True,
                                 any_number=purpose=='retain',can_pick_zero=purpose=='retain')
                source.update(roomPhase='COMBAT',combat_receipt={'turn':4})
                bridge=IroncladInteractionBridge(); before=state_frame(bridge,source)
                action,=[spec for spec in before.actions if spec.native_method==method]
                plan=bridge.prepare(before,action.api_action)
                after=selection('discard','hand',options=[card(11)])
                after['roomPhase']='COMBAT'
                result=lambda seq=1:verify_effect(plan,state_frame(IroncladInteractionBridge(),after,seq)).status
                observed=dict(source_action=src,selected_uuids=[c['uuid'] for c in chosen],destination=destination,turn=4)
                receipt=dict(method=method,args=[],sequence=1,combat_selection_commit=observed)
                after['driver_receipt']=receipt
                self.assertEqual(result(),'APPLIED')
                for wrong in (dict(source_action='ExhaustAction'),dict(selected_uuids=[card(9)['uuid']]),
                              dict(destination='exhaust_pile'),dict(turn=5)):
                    receipt['combat_selection_commit']=dict(observed,**wrong)
                    self.assertEqual(result(),'UNVERIFIED')
                receipt['combat_selection_commit']=observed
                self.assertEqual(result(2),'UNVERIFIED')

    def test_boolean_probability_and_string_enabled_are_rejected(self):
        source = event(); source["interaction"]["event"]["choices"][0]["outcomes"][0]["probability"] = True
        with self.assertRaises(HeadlessContractError): state_frame(IroncladInteractionBridge(), source)
        source = event(); source["interaction"]["event"]["choices"][0]["enabled"] = "true"
        with self.assertRaises(HeadlessContractError): state_frame(IroncladInteractionBridge(), source)

    def test_native_button_holes_and_existing_policy_candidates(self):
        bridge = IroncladInteractionBridge(); source = event()
        enabled = source["interaction"]["event"]["choices"][0]
        enabled["button_index"] = 2
        source["interaction"]["event"]["choices"].insert(0, {**copy.deepcopy(enabled), "enabled": False, "button_index": 0, "action_index": None})
        frame = state_frame(bridge, source)
        self.assertEqual(choose(bridge, frame).action.native_args, (2,))
        self.assertEqual(candidates_for(frame.state)[0]["command"], {"type": "choose", "choice_index": 0})

    def test_disabled_and_unknown_events_never_create_legal_plans(self):
        frame = state_frame(IroncladInteractionBridge(), event(status="UNKNOWN"))
        self.assertEqual(frame.actions, ())
        with self.assertRaises(HeadlessContractError): choose(IroncladInteractionBridge(), frame)
        bad = event(); bad["interaction"]["event"]["choices"][0]["enabled"] = False
        with self.assertRaises(HeadlessContractError): state_frame(IroncladInteractionBridge(), bad)

    def test_cost_and_declared_landing_both_required(self):
        bridge = IroncladInteractionBridge(); before = state_frame(bridge, event())
        plan = choose(bridge, before)
        after = event(phase="RESULT"); after["player"]["hp"] += 10
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["hp"] -= 1
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")
        after["player"]["hp"] += 1; after["interaction"]["event"]["phase"] = "UNRELATED"
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 3)).status, "UNVERIFIED")

    def test_queue_ack_and_label_change_are_not_effects(self):
        bridge = IroncladInteractionBridge(); source = event(); frame = state_frame(bridge, source)
        plan = choose(bridge, frame)
        self.assertEqual(verify_effect(plan, state_frame(bridge, source, 1)).status, "PENDING")
        source["interaction"]["event"]["choices"][0]["label"] = "queued"
        self.assertEqual(verify_effect(plan, state_frame(bridge, source, 2)).status, "UNVERIFIED")

    def test_probability_and_target_selection_contract(self):
        bad = event(); bad["interaction"]["event"]["choices"][0]["outcomes"][0]["probability"] = 0.3
        with self.assertRaises(HeadlessContractError): state_frame(IroncladInteractionBridge(), bad)
        bridge = IroncladInteractionBridge()
        before = event(effects=[{"type": "select_cards", "purpose": "PURGE", "count": 1}], followup="GRID")
        before["interaction"].pop("event_transitions")
        plan = choose(bridge, state_frame(bridge, before))
        self.assertEqual(verify_effect(plan, state_frame(bridge, selection("purge"), 1)).status, "APPLIED")
        self.assertEqual(verify_effect(plan, state_frame(bridge, selection("upgrade"), 2)).status, "UNVERIFIED")

    def test_cursed_tome_step_is_not_whole_book_reward(self):
        bridge = IroncladInteractionBridge()
        source = event(effects=[{"type": "lose_hp", "amount": 2}], phase="PAGE_2", followup="EVENT", kind="CONTINUE")
        source["interaction"]["event"].update(id="Cursed Tome", class_name="CursedTome")
        source["interaction"]["event_transitions"] = {"0": {"phases": ["PAGE_3"]}}
        plan = choose(bridge, state_frame(bridge, source))
        after = copy.deepcopy(source); after["player"]["hp"] -= 2; after["interaction"]["event"]["phase"] = "PAGE_3"
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        self.assertEqual(state_frame(bridge, after, 2).state.relics, [])

    def test_stochastic_outcome_must_match_a_possible_resource_vector(self):
        bridge = IroncladInteractionBridge(); source = event()
        source["interaction"]["event"]["choices"][0]["outcomes"] = [
            {"probability": .5, "effects": [{"type": "gain_gold", "amount": 20}]},
            {"probability": .5, "effects": [{"type": "lose_hp", "amount": 3}]}]
        plan = choose(bridge, state_frame(bridge, source))
        after = copy.deepcopy(source); after["interaction"]["event"]["phase"] = "RESULT"; after["player"]["gold"] += 20
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["hp"] -= 3
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")

    def test_unknown_effect_is_not_confirmed_by_a_screen_change(self):
        bridge = IroncladInteractionBridge(); source = event(effects=[{"type": "not_ported"}])
        plan = choose(bridge, state_frame(bridge, source))
        after = event(phase="RESULT")
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "UNVERIFIED")


class ShopBridgeTest(unittest.TestCase):
    def test_local_purge_cancel_has_no_native_action(self):
        bridge = IroncladInteractionBridge(); before = state_frame(bridge, shop())
        virtual = bridge.enter_purge_intent(choose(bridge, before), before)
        cancel = bridge.prepare(virtual, GameAction(type="cancel"))
        self.assertEqual(verify_effect(cancel, before).status, "LOCAL_ONLY")
        restored = bridge.cancel_purge_intent(cancel, virtual)
        self.assertEqual(restored.state.screen_type, "SHOP_SCREEN")
        self.assertEqual(restored.native_token, before.native_token)

    def test_compact_choices_native_index_and_price_guard(self):
        bridge = IroncladInteractionBridge(); source = shop()
        before = state_frame(bridge, source)
        self.assertEqual(choose(bridge, before, 1).action.native_args, (2,))  # purge is choice 0.
        self.assertEqual(len(candidates_for(before.state)), 3)
        plan = choose(bridge, before, 1)
        source["interaction"]["shop"]["items"][0]["price"] = 19
        self.assertFalse(bridge.validate_before(plan, state_frame(bridge, source, 1)))

    def test_unaffordable_sold_out_and_zero_price(self):
        for changes in ({"price": 100}, {"sold_out": True}):
            source = shop(purge_available=False); source["interaction"]["shop"]["items"][0].update(changes)
            frame = state_frame(IroncladInteractionBridge(), source)
            self.assertEqual([a.native_method for a in frame.actions], ["leaveShop"])
            self.assertIsNone(frame.state.shop_inventory[0]["choice_index"])
        source = shop(purge_available=False); source["interaction"]["shop"]["items"][0]["price"] = 0
        self.assertEqual(len(state_frame(IroncladInteractionBridge(), source).actions), 2)

    def test_potion_capacity_and_sozu(self):
        item = {"slot_id": "potion:0", "kind": "potion", "id": "Fire Potion", "native_index": 0,
                "price": 10, "upgrades": 0, "on_sale": False, "sold_out": False}
        for empty, sozu, expected in ((False, False, False), (True, True, False), (True, False, True)):
            source = shop([item], purge_available=False)
            source["player"]["potions"] = [{"id": "Potion Slot" if empty else "Fire Potion", "name": "slot", "empty": empty, "targetRequired": False}]
            if sozu: source["player"]["relics"] = [{"id": "Sozu", "name": "Sozu", "counter": -1}]
            frame = state_frame(IroncladInteractionBridge(), source)
            self.assertEqual(frame.state.shop_inventory[0]["legal"], expected)

    def test_purchase_needs_receipt_debit_and_consumed_slot(self):
        bridge = IroncladInteractionBridge(); before = state_frame(bridge, shop())
        plan = choose(bridge, before, 1)
        after = shop(items=[], gold=79)
        after["player"]["masterDeck"].append(card(5, id="Bash", timesUpgraded=0))
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["gold"] = 80
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")
        after["player"]["gold"] = 79; after["player"]["masterDeck"].pop()
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 3)).status, "UNVERIFIED")

    def test_purge_is_local_intent_then_one_native_commit(self):
        bridge = IroncladInteractionBridge(); before = state_frame(bridge, shop())
        intent = choose(bridge, before, 0)
        self.assertEqual(verify_effect(intent, before).status, "LOCAL_ONLY")
        virtual = bridge.enter_purge_intent(intent, before)
        self.assertEqual(virtual.state.screen_type, "GRID")
        plan = choose(bridge, virtual)
        self.assertEqual((plan.action.native_method, plan.action.native_args), ("purgeCard", (0,)))
        self.assertTrue(bridge.validate_before(plan, before))
        after = shop(gold=24, purge_available=False, purge_cards=[])
        after["player"]["masterDeck"] = []
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")

    def test_bottled_purge_candidate_rejected(self):
        source = shop(purge_cards=[card(bottled=True)])
        with self.assertRaises(HeadlessContractError): state_frame(IroncladInteractionBridge(), source)


class RewardBridgeTest(unittest.TestCase):
    def test_definitive_boss_skip_waits_for_real_next_act_proceed(self):
        from sts_ai_framework.tests.test_headless_flow import boss
        after=boss(treasure=True);before=copy.deepcopy(after);before['phase']='BOSS_RELIC'
        before['interaction'].update(boss_relics=[{'native_index':0,'id':'Black Blood'}],can_skip_boss=True)
        bridge=IroncladInteractionBridge();plan=bridge.prepare(state_frame(bridge,before),GameAction(type='cancel'))
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,1)).status,'APPLIED')
        after['player']['gold']-=1
        self.assertEqual(verify_effect(plan,state_frame(bridge,after,2)).status,'UNVERIFIED')

    def test_boss_relic_pick_and_skip_have_distinct_postconditions(self):
        for skip in (False, True):
            bridge = IroncladInteractionBridge(); source = raw("BOSS_RELIC")
            source["interaction"].update(boss_relics=[{"native_index": 0, "id": "Black Blood"}], can_skip_boss=True)
            before = state_frame(bridge, source)
            plan = bridge.prepare(before, GameAction(type="cancel")) if skip else choose(bridge, before)
            after = raw("BOSS_CHEST" if skip else "PROCEED")
            if skip: after["interaction"]["chest"] = {"boss": True, "open": False, "gold_reward": False, "gold_amount": 0, "relic_tier": None}
            else:
                after["interaction"]["can_proceed"] = True
                after["player"]["relics"] = [{"id": "Black Blood", "name": "Black Blood", "counter": -1}]
            self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")

    def test_chest_needs_an_open_marker_not_just_new_reward_screen(self):
        bridge = IroncladInteractionBridge(); source = raw("CHEST")
        source["interaction"]["chest"] = {"boss": False, "open": False, "gold_reward": True, "gold_amount": 25, "relic_tier": "common"}
        plan = choose(bridge, state_frame(bridge, source))
        after = reward([{"type": "GOLD", "amount": 25}])
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "UNVERIFIED")
        after["interaction"]["chest"] = {**source["interaction"]["chest"], "open": True}
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "APPLIED")

    def test_overlay_needs_explicit_parent_reward_evidence(self):
        bridge = IroncladInteractionBridge()
        before = reward([{"type": "RELIC", "relic_id": "Anchor"}, {"type": "GOLD", "amount": 10}])
        plan = choose(bridge, state_frame(bridge, before))
        after = selection("bottle", auto_confirm=True)
        after["player"]["relics"] = [{"id": "Anchor", "name": "Anchor", "counter": -1}]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "UNVERIFIED")
        after["interaction"]["background_rewards"] = [{"type": "GOLD", "native_index": 0, "amount": 10}]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "APPLIED")

    def test_skip_alias_is_only_legal_when_card_reward_can_cancel(self):
        bridge = IroncladInteractionBridge(); frame = state_frame(bridge, pick())
        self.assertEqual(bridge.prepare(frame, GameAction(type="skip")).action.native_method, "skipRewardCard")
        frame = state_frame(bridge, pick(skip=False), 1)
        with self.assertRaises(HeadlessContractError): bridge.prepare(frame, GameAction(type="skip"))

    def test_gold_claim_requires_correct_reward_removal(self):
        bridge = IroncladInteractionBridge()
        source = reward([{"type": "GOLD", "amount": 25}, {"type": "GOLD", "amount": 10}])
        plan = choose(bridge, state_frame(bridge, source))
        after = reward([{"type": "GOLD", "amount": 10}]); after["player"]["gold"] += 25
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        wrong = reward([{"type": "GOLD", "amount": 25}]); wrong["player"]["gold"] += 25
        self.assertEqual(verify_effect(plan, state_frame(bridge, wrong, 2)).status, "UNVERIFIED")

    def test_linked_key_or_relic_consumes_both(self):
        for index in (0, 1):
            bridge = IroncladInteractionBridge()
            source = reward([{"type": "RELIC", "relic_id": "Anchor", "linked_reward_index": 1},
                             {"type": "SAPPHIRE_KEY", "linked_reward_index": 0}])
            plan = choose(bridge, state_frame(bridge, source), index)
            after = reward([])
            if index == 0: after["player"]["relics"] = [{"id": "Anchor", "name": "Anchor", "counter": -1}]
            else: after["observedKeys"] = {"sapphire": True}
            self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")

    def test_bad_reward_link_and_full_belt(self):
        bad = reward([{"type": "RELIC", "relic_id": "Anchor", "linked_reward_index": 1}, {"type": "SAPPHIRE_KEY"}])
        with self.assertRaises(HeadlessContractError): state_frame(IroncladInteractionBridge(), bad)
        frame = state_frame(IroncladInteractionBridge(), reward([{"type": "POTION", "potion_id": "Fire Potion"}]))
        with self.assertRaises(HeadlessContractError): choose(IroncladInteractionBridge(), frame)

    def test_open_card_reward_checks_offer_uuids(self):
        bridge = IroncladInteractionBridge()
        plan = choose(bridge, state_frame(bridge, reward([{"type": "CARD", "card_uuids": [card(4)["uuid"]]}])))
        self.assertEqual(verify_effect(plan, state_frame(bridge, pick(), 1)).status, "APPLIED")
        self.assertEqual(verify_effect(plan, state_frame(bridge, pick([card(5)]), 2)).status, "UNVERIFIED")

    def test_take_skip_bowl_and_egg_upgrade(self):
        bridge = IroncladInteractionBridge(); source = pick()
        source["player"]["relics"] = [{"id": "Molten Egg 2", "name": "egg", "counter": -1}]
        plan = choose(bridge, state_frame(bridge, source))
        after = raw("MAP"); after["player"]["masterDeck"].append(card(20, timesUpgraded=1))
        after["player"]["relics"] = source["player"]["relics"]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        for use_bowl in (False, True):
            bridge = IroncladInteractionBridge(); before = state_frame(bridge, pick(bowl=use_bowl))
            plan = choose(bridge, before, 1) if use_bowl else bridge.prepare(before, GameAction(type="cancel"))
            after = raw("MAP")
            if use_bowl: after["player"]["maxHp"] += 2
            self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")

    def test_mandatory_card_reward_has_no_skip(self):
        frame = state_frame(IroncladInteractionBridge(), pick(skip=False))
        self.assertFalse(frame.state.can_cancel)
        with self.assertRaises(HeadlessContractError): IroncladInteractionBridge().prepare(frame, GameAction(type="cancel"))


class SelectionBridgeTest(unittest.TestCase):
    def test_multi_transform_checks_every_selected_uuid(self):
        bridge = IroncladInteractionBridge()
        source = selection("transform", options=[card(1), card(2), card(3)], selected=[card(1), card(2)], count=3, auto_confirm=True)
        source["player"]["masterDeck"] = [card(1), card(2), card(3)]
        before = state_frame(bridge, source); plan = choose(bridge, before, 2)
        after = raw("MAP"); after["player"]["masterDeck"] = [card(4), card(5), card(6)]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")

    def test_headbutt_warcry_and_exhume_use_pile_uuid_and_order(self):
        for purpose, kind in (("topdeck", "grid"), ("topdeck", "hand"), ("retrieve_exhaust", "grid")):
            bridge = IroncladInteractionBridge()
            source = selection(purpose, kind=kind, auto_confirm=True); source["roomPhase"] = "COMBAT"
            plan = choose(bridge, state_frame(bridge, source))
            after = raw("PLAYER_TURN", roomPhase="COMBAT")
            after["player"]["drawPile" if purpose == "topdeck" else "hand"] = [card()]
            self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
            if purpose == "topdeck":
                after["player"]["drawPile"].append(card(8))
                self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")

    def test_dual_wield_receipt_does_not_upgrade_or_duplicate_permanent_deck(self):
        bridge = IroncladInteractionBridge()
        source = selection("duplicate_combat", kind="hand", auto_confirm=True, copies=2); source["roomPhase"] = "COMBAT"
        source["player"]["hand"] = [card()]
        plan = choose(bridge, state_frame(bridge, source))
        after = raw("PLAYER_TURN", roomPhase="COMBAT"); after["player"]["hand"] = [card(), card(4), card(5)]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["masterDeck"].append(card(6))
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")

    def test_bottle_selector_commits_the_original_card_uuid(self):
        bridge = IroncladInteractionBridge(); frame = state_frame(bridge, selection("bottle", auto_confirm=True))
        plan = choose(bridge, frame)
        after = raw("MAP"); after["player"]["masterDeck"][0]["bottled"] = True
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")

    def test_armaments_changes_combat_copy_not_permanent_deck(self):
        bridge = IroncladInteractionBridge()
        source = selection("upgrade", kind="hand", selected=[card()], can_confirm=True)
        source["roomPhase"] = "COMBAT"
        before = state_frame(bridge, source)
        plan = bridge.prepare(before, GameAction(type="proceed"))
        after = raw("PLAYER_TURN", roomPhase="COMBAT"); after["player"]["hand"] = [card(timesUpgraded=4)]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["masterDeck"][0]["timesUpgraded"] = 4
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")

    def test_upgrade_preview_uses_original_uuid_and_separate_confirm(self):
        bridge = IroncladInteractionBridge(); before = state_frame(bridge, selection())
        click = choose(bridge, before)
        preview = selection(locked=True, can_confirm=True, preview_uuid=card()["uuid"])
        frame = state_frame(bridge, preview, 1)
        self.assertEqual(verify_effect(click, frame).status, "APPLIED")
        self.assertEqual(frame.state.grid_selected_count, 0)  # Engine clears this list for its preview.
        self.assertEqual(frame.state.grid_cards, [])
        confirm = bridge.prepare(frame, GameAction(type="proceed"))
        after = raw("MAP"); after["player"]["masterDeck"][0]["timesUpgraded"] += 1
        self.assertEqual(verify_effect(confirm, state_frame(bridge, after, 2)).status, "APPLIED")

    def test_confirm_rejects_wrong_card_upgrade(self):
        with self.assertRaises(HeadlessContractError):
            state_frame(IroncladInteractionBridge(), selection(locked=True, can_confirm=True, preview_uuid=card(9)["uuid"]))
        bridge = IroncladInteractionBridge()
        before = state_frame(bridge, selection(locked=True, can_confirm=True, preview_uuid=card()["uuid"]))
        plan = bridge.prepare(before, GameAction(type="proceed"))
        after = raw("MAP"); after["player"]["masterDeck"].append(card(8, timesUpgraded=4))
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "UNVERIFIED")

    def test_disabled_upgrade_target_compacts_api_not_native_index(self):
        bridge = IroncladInteractionBridge()
        frame = state_frame(bridge, selection(options=[card(1, canUpgrade=False), card(2)]))
        self.assertEqual(choose(bridge, frame).action.native_args, (1,))
        self.assertEqual(frame.state.grid_cards[0].choice_index, 0)

    def test_count_confirm_zero_and_hand_discard(self):
        with self.assertRaises(HeadlessContractError): state_frame(IroncladInteractionBridge(), selection(can_confirm=True))
        frame = state_frame(IroncladInteractionBridge(), selection("discard", kind="hand", any_number=True, can_confirm=True))
        self.assertTrue(frame.state.can_proceed)
        bridge = IroncladInteractionBridge(); source = selection("discard", kind="hand", auto_confirm=True)
        source["player"]["hand"] = [card()]
        plan = choose(bridge, state_frame(bridge, source))
        after = raw("PLAYER_TURN"); after["player"]["discardPile"] = [card()]
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")

    def test_selection_only_requires_exact_uuid_and_no_extra_resource_change(self):
        bridge = IroncladInteractionBridge(); before = state_frame(bridge, selection("discard", kind="hand", count=2))
        plan = choose(bridge, before)
        after = selection("discard", kind="hand", selected=[card()], count=2)
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")
        after["player"]["gold"] -= 1
        self.assertEqual(verify_effect(plan, state_frame(bridge, after, 2)).status, "UNVERIFIED")

    def test_grid_transform_and_duplicate_commit(self):
        for purpose in ("transform", "duplicate"):
            bridge = IroncladInteractionBridge()
            before = state_frame(bridge, selection(purpose, selected=[card()], can_confirm=True))
            plan = bridge.prepare(before, GameAction(type="proceed"))
            after = raw("MAP")
            if purpose == "transform": after["player"]["masterDeck"] = [card(15, id="Bash")]
            else: after["player"]["masterDeck"].append(card(15))
            self.assertEqual(verify_effect(plan, state_frame(bridge, after, 1)).status, "APPLIED")


class GuardBridgeTest(unittest.TestCase):
    def test_stale_menu_cross_run_and_ack(self):
        bridge = IroncladInteractionBridge(); before = state_frame(bridge, shop())
        plan = choose(bridge, before, 1)
        fresh = state_frame(bridge, shop(), 1)
        self.assertTrue(bridge.validate_before(plan, fresh))
        foreign = state_frame(bridge, shop(), 0, run="B")
        self.assertFalse(bridge.validate_before(plan, foreign))
        self.assertEqual(verify_effect(plan, foreign).status, "REJECTED")

    def test_only_ironclad_no_combat_or_new_run_bindings(self):
        source = raw(); source["player"]["className"] = "DEFECT"
        with self.assertRaises(HeadlessContractError): state_frame(IroncladInteractionBridge(), source)
        frame = state_frame(IroncladInteractionBridge(), raw("PLAYER_TURN"))
        self.assertEqual(frame.actions, ())
        with self.assertRaises(HeadlessContractError): choose(IroncladInteractionBridge(), frame)

    def test_public_state_mutation_does_not_modify_guard_or_action(self):
        bridge = IroncladInteractionBridge(); frame = state_frame(bridge, shop())
        token = frame.token; frame.state.player.gold = 10000
        action = frame.actions[0].api_action; action.choice_index = 99
        self.assertEqual(frame.token, token)
        self.assertEqual(frame.state.player.gold, 99)
        self.assertNotEqual(frame.actions[0].api_action.choice_index, 99)

    def test_bad_menu_does_not_commit_key_memory(self):
        bridge = IroncladInteractionBridge(); state_frame(bridge, shop())
        bad = shop(); bad["observedKeys"] = {"emerald": True}; bad["interaction"]["shop"]["purge_cost"] = -1
        with self.assertRaises(HeadlessContractError): state_frame(bridge, bad, 1)
        self.assertEqual(state_frame(bridge, shop(), 1).state.keys.model_fields_set, set())


if __name__ == "__main__": unittest.main()
