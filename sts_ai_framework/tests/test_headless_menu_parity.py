"""Pinned-source event and reward regressions; no game process is started."""
import copy
import json
from types import SimpleNamespace
import unittest

from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.headless.policy import HeadlessValuePolicy
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_interaction import event, selection, reward, card


class MenuParityTest(unittest.TestCase):
    def bridge(self): return IroncladInteractionBridge()

    def apply(self, before, after, method='chooseDialogOption', args=(0,)):
        bridge=self.bridge();left=bridge.observe(before,run_id='fixture',sequence=0)
        action=next(a for a in left.actions if a.native_method==method and a.native_args==args)
        plan=bridge.prepare(left,action.api_action)
        right=bridge.observe(after,run_id='fixture',sequence=1)
        return verify_effect(plan,right).status

    def source_event(self, identity, name, phase, next_phase):
        before=event(effects=[],phase=phase,followup='EVENT',kind='CONTINUE')
        before['room']='EventRoom'
        before['interaction']['event'].update(id=identity,class_name=name,decision_kind='FORCED')
        before['interaction']['event_transitions']={}
        after=copy.deepcopy(before);after['interaction']['event']['phase']=next_phase
        return before,after

    def test_bonfire_intro_and_skull_intro_need_exact_source_stages(self):
        for facts in [('Bonfire Elementals','Bonfire','INTRO','CHOOSE'),('Knowing Skull','KnowingSkull','INTRO_1','ASK')]:
            with self.subTest(facts=facts):
                before,after=self.source_event(*facts)
                self.assertEqual(self.apply(before,after),'APPLIED')
                after['interaction']['event']['phase']='UNRELATED'
                self.assertEqual(self.apply(before,after),'UNVERIFIED')

    def test_effect_free_stage_change_does_not_accept_inventory_mutation(self):
        before,after=self.source_event('Bonfire Elementals','Bonfire','INTRO','CHOOSE')
        after['player']['masterDeck'].append(card(8))
        self.assertEqual(self.apply(before,after),'UNVERIFIED')

    def test_source_stage_declaration_conflict_is_rejected(self):
        before,_=self.source_event('Knowing Skull','KnowingSkull','INTRO_1','ASK')
        before['interaction']['event_transitions']={'0':{'phases':['COMPLETE']}}
        with self.assertRaises(HeadlessContractError): self.bridge().observe(before,run_id='fixture',sequence=0)

    def test_knowing_skull_adds_colorless_card_without_reward_screen(self):
        before=event(effects=[{'type':'lose_hp','amount':6},{'type':'random_card_reward','count':1}],
                     phase='ASK',followup='EVENT',kind='CARD_REWARD')
        before['room']='EventRoom';before['interaction']['event'].update(id='Knowing Skull',class_name='KnowingSkull')
        before['interaction']['event']['choices'][0]['button_index']=2
        before['interaction']['event_transitions']={}
        after=copy.deepcopy(before);after['player']['hp']-=6
        after['player']['masterDeck'].append(card(9,id='Panacea',type='SKILL',rarity='UNCOMMON',timesUpgraded=0))
        self.assertEqual(self.apply(before,after,args=(2,)),'APPLIED')
        frame=self.bridge().observe(before,run_id='fixture',sequence=0)
        self.assertEqual(frame.state.event.choices[0].outcomes[0].effects[1].pool,'COLORLESS_UNCOMMON')
        for change in ('card','cost','deck','screen','relic'):
            bad=copy.deepcopy(after)
            if change=='card':bad['player']['masterDeck'][-1]['id']='Bash'
            if change=='cost':bad['player']['hp']+=1
            if change=='deck':bad['player']['masterDeck'][0]['misc']=9
            if change=='screen':bad['interaction']['event']['phase']='COMPLETE'
            if change=='relic':bad['player']['relics'].append({'id':'Anchor','name':'Anchor','counter':-1})
            with self.subTest(change=change):self.assertEqual(self.apply(before,bad,args=(2,)),'UNVERIFIED')

    def test_knowing_skull_card_respects_toxic_egg(self):
        before=event(effects=[{'type':'lose_hp','amount':6},{'type':'random_card','pool':'COLORLESS_UNCOMMON','card_id':'Panacea'}],
                     phase='ASK',followup='EVENT',kind='RANDOM_REWARD')
        before['room']='EventRoom';before['interaction']['event'].update(id='Knowing Skull',class_name='KnowingSkull')
        before['interaction']['event']['choices'][0]['button_index']=2;before['interaction']['event_transitions']={}
        before['player']['relics'].append({'id':'Toxic Egg 2','name':'Toxic Egg','counter':-1})
        before['player']['relics'].append({'id':'CeramicFish','name':'Ceramic Fish','counter':-1})
        after=copy.deepcopy(before);after['player']['hp']-=6
        after['player']['gold']+=9
        after['player']['masterDeck'].append(card(9,id='Panacea',type='SKILL',rarity='UNCOMMON',timesUpgraded=1))
        self.assertEqual(self.apply(before,after,args=(2,)),'APPLIED')
        bad=copy.deepcopy(after)
        bad['player']['masterDeck'][-1]['id']='Purity' # Same rarity/type, wrong prediction.
        self.assertEqual(self.apply(before,bad,args=(2,)),'UNVERIFIED')
        after['player']['masterDeck'][-1]['timesUpgraded']=0
        self.assertEqual(self.apply(before,after,args=(2,)),'UNVERIFIED')

    def test_knowing_skull_policy_compares_the_predicted_card_with_other_actions(self):
        from selectcard.src.inference import STSInferenceEngine

        source=event(effects=[{'type':'lose_hp','amount':6},{'type':'gain_gold','amount':90}],
                     phase='ASK',followup='EVENT',kind='EFFECT')
        source['room']='EventRoom'
        view=source['interaction']['event']
        view.update(id='Knowing Skull',class_name='KnowingSkull',decision_kind='COMPLEX')
        source['interaction']['event_transitions']={}
        view['choices'][0]['button_index']=1
        draw={'type':'random_card','pool':'COLORLESS_UNCOMMON','card_id':'Panacea'}
        view['choices'].append(dict(view['choices'][0],button_index=2,action_index=1,kind='RANDOM_REWARD',
            outcomes=[{'probability':1.,'effects':[{'type':'lose_hp','amount':7},draw]}]))
        policy=HeadlessValuePolicy.__new__(HeadlessValuePolicy)
        policy.engine=STSInferenceEngine.__new__(STSInferenceEngine)
        policy.engine.evaluate_state=lambda state: state['hp']+state['gold']/10+20*('Panacea' in state['deck'])
        for card_id,button in [('Panacea',2),('Trip',1)]:
            with self.subTest(card_id=card_id):
                draw['card_id']=card_id
                frame=self.bridge().observe(source,run_id='fixture',sequence=0)
                self.assertEqual((button,),policy.choose(frame)[0].native_args)

    def test_random_curse_checks_exact_addition_and_all_survivors(self):
        before=event(effects=[{'type':'random_curse','count':1},{'type':'gain_gold','amount':250}],phase='3')
        before['interaction']['event'].update(id='Neow',class_name='NeowEvent')
        before['interaction']['event_transitions']={}
        after=copy.deepcopy(before);after['interaction']['event']['phase']='99'
        after['player']['gold']+=250
        after['player']['masterDeck'].append(card(9,id='Shame',type='CURSE',rarity='CURSE',timesUpgraded=0))
        self.assertEqual(self.apply(before,after),'APPLIED')
        bad=copy.deepcopy(after);bad['player']['masterDeck'][-1]['type']='SKILL'
        self.assertEqual(self.apply(before,bad),'UNVERIFIED')
        bad=copy.deepcopy(after);bad['player']['masterDeck'][0]['misc']=7
        self.assertEqual(self.apply(before,bad),'UNVERIFIED')

    def test_duplicator_generic_grid_requires_source_identity_and_whole_deck(self):
        before=selection(purpose='unknown',options=[card()],auto_confirm=True)
        before['room']='EventRoom';before['interaction']['flow']={'event_class':'Duplicator'}
        before['player']['masterDeck']=[card()]
        frame=self.bridge().observe(before,run_id='fixture',sequence=0)
        self.assertEqual(frame.state.grid_purpose,'duplicate')
        for change in ('class','count','target'):
            bad=copy.deepcopy(before)
            if change=='class':bad['interaction']['flow']['event_class']='BigFish'
            if change=='count':bad['interaction']['selection']['count']=2
            if change=='target':bad['cardSelection']['options']=[card(7)]
            with self.subTest(change=change), self.assertRaises(HeadlessContractError):
                self.bridge().observe(bad,run_id='fixture',sequence=0)

    def test_duplicate_preserves_originals_and_adds_fresh_stat_equivalent_unbottled_copy(self):
        target=card(0,id='Searing Blow',timesUpgraded=4,bottled=True,misc=9)
        before=selection(purpose='duplicate',options=[target],auto_confirm=True)
        before['player']['masterDeck']=[target]
        after=event(effects=[]);after['player']=copy.deepcopy(before['player'])
        added=copy.deepcopy(target);added.update(index=1,uuid=card(1)['uuid'],bottled=False)
        after['player']['masterDeck'].append(added)
        self.assertEqual(self.apply(before,after,'chooseGridCard',(0,)),'APPLIED')
        for field,value in [('bottled',True),('misc',8),('timesUpgraded',3)]:
            bad=copy.deepcopy(after);bad['player']['masterDeck'][-1][field]=value
            self.assertEqual(self.apply(before,bad,'chooseGridCard',(0,)),'UNVERIFIED')
        bad=copy.deepcopy(after);bad['player']['gold']-=1
        self.assertEqual(self.apply(before,bad,'chooseGridCard',(0,)),'UNVERIFIED')

    def bonfire_offer(self, rarity='COMMON', bloom=False):
        target=card(0,id='Offering',type='SKILL',rarity=rarity)
        before=selection(purpose='bonfire_offer',options=[target],locked=True,can_confirm=True,
                         preview_uuid=target['uuid'],source_event_class='Bonfire',source_event_phase='CHOOSE')
        before['room']='EventRoom';before['interaction']['flow']={'event_class':'Bonfire'}
        before['player']['masterDeck']=[target];before['player'].update(hp=20,maxHp=80)
        if bloom:before['player']['relics'].append({'id':'Mark of the Bloom','name':'Mark of the Bloom','counter':-1})
        after=event(effects=[],phase='COMPLETE');after['room']='EventRoom'
        after['interaction']['event'].update(id='Bonfire Elementals',class_name='Bonfire')
        after['interaction']['event_transitions']={}
        after['player']=copy.deepcopy(before['player']);after['player']['masterDeck']=[]
        return before,after

    def test_bonfire_offer_reward_uses_rarity_and_real_confirmation(self):
        for rarity,hp,maximum in [('BASIC',20,80),('COMMON',25,80),('SPECIAL',25,80),('UNCOMMON',80,80),('RARE',90,90)]:
            before,after=self.bonfire_offer(rarity)
            after['player'].update(hp=hp,maxHp=maximum)
            with self.subTest(rarity=rarity):
                self.assertEqual(self.apply(before,after,'confirmGrid',()),'APPLIED')
                after['player']['gold']+=1
                self.assertEqual(self.apply(before,after,'confirmGrid',()),'UNVERIFIED')

    def test_bonfire_bloom_blocks_heal_but_not_max_hp_gain(self):
        before,after=self.bonfire_offer('RARE',bloom=True);after['player']['maxHp']=90
        self.assertEqual(self.apply(before,after,'confirmGrid',()),'APPLIED')
        after['player']['hp']=30
        self.assertEqual(self.apply(before,after,'confirmGrid',()),'UNVERIFIED')

    def test_bonfire_curse_grants_exact_spirit_poop(self):
        before,after=self.bonfire_offer('CURSE')
        after['player']['relics'].append({'id':'Spirit Poop','name':'Spirit Poop','counter':-1})
        self.assertEqual(self.apply(before,after,'confirmGrid',()),'APPLIED')
        after['player']['relics'][-1]['id']='Anchor'
        self.assertEqual(self.apply(before,after,'confirmGrid',()),'UNVERIFIED')

    def test_bonfire_foreign_source_does_not_advertise_actions(self):
        before,_=self.bonfire_offer();before['interaction']['selection']['source_event_phase']='INTRO'
        with self.assertRaises(HeadlessContractError):self.bridge().observe(before,run_id='fixture',sequence=0)

    def test_declined_single_reward_without_exit_is_not_reopened(self):
        policy=HeadlessValuePolicy.__new__(HeadlessValuePolicy)
        policy._skipped_rewards={('fixture',(card()['uuid'],))};policy._match_memory={}
        source=reward([{'type':'CARD','card_uuids':[card()['uuid']]}]);source['interaction']['can_proceed']=False
        frame=self.bridge().observe(source,run_id='fixture',sequence=0)
        self.assertEqual(len(frame.actions),1)
        with self.assertRaisesRegex(ValueError,'declined'):policy.choose(frame)

    def test_bonfire_policy_scores_remove_plus_rarity_reward(self):
        target=card(0,id='Offering',rarity='RARE')
        source,_=self.bonfire_offer('RARE')
        source['interaction']['selection'].update(locked=False,can_confirm=False)
        source['interaction']['selection'].pop('preview_uuid')
        source['cardSelection']['options'].append(card(1,rarity='BASIC'))
        source['player']['masterDeck'].append(card(1,rarity='BASIC'))
        policy=HeadlessValuePolicy.__new__(HeadlessValuePolicy);policy._skipped_rewards=set();policy._match_memory={}
        seen=[]
        def apply(state,choice):seen.append(choice);return choice
        policy.engine=SimpleNamespace(_apply_choice=apply,evaluate_state=lambda _:0)
        frame=self.bridge().observe(source,run_id='fixture',sequence=0);policy.choose(frame)
        self.assertEqual(seen[0]['effects'],[{'type':'remove_card','card_id':target['id']+'+3'},
                         {'type':'gain_max_hp','amount':10},{'type':'full_heal'}])
