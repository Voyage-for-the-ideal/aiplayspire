"""Counterexamples for the 160-run repair boundaries (no game processes)."""
import copy
import json
import os
from pathlib import Path
import unittest

from sts_ai_framework.headless.agent import check_decision_progress
from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.headless.policy import HeadlessValuePolicy
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_flow import FlowCase, boss, next_act
from sts_ai_framework.tests.test_headless_interaction import pick, card, raw, choose, state_frame


class RepairProgressTests(unittest.TestCase):
    def test_only_skipped_visible_groups_count_as_progress(self):
        policy=HeadlessValuePolicy.__new__(HeadlessValuePolicy)
        policy._skipped_rewards=set()
        raw={'phase':'REWARD','interaction':{'rewards':[{'type':'CARD','card_uuids':[i]} for i in ('a','b','c')]}}
        seen={}
        for i in ('a','b','c'):
            check_decision_progress(policy,'same',raw,seen)
            policy._skipped_rewards.add(('same',(i,)))
        check_decision_progress(policy,'same',raw,seen)
        policy._skipped_rewards.add(('other',('a',)))
        raw['driver_receipt']={'sequence':999}
        check_decision_progress(policy,'same',raw,seen)
        policy._skipped_rewards.add(('same',('invisible',)))
        with self.assertRaisesRegex(ValueError,'identical'):
            check_decision_progress(policy,'same',raw,seen)


class BossCarryTest(FlowCase):
    def test_only_the_completed_open_boss_chest_can_survive_into_map(self):
        source=boss(treasure=True)
        source['interaction']['chest']={'open':True,'boss':True,'gold_reward':False,'gold_amount':0}
        target=next_act(source)
        target.update(room='TreasureRoomBoss',roomPhase='COMPLETE')
        target['map']['current']={'x':-1,'y':15}
        self.assertEqual(self.effect(self.plan(source),target),'APPLIED')
        for mutation in ('closed','wrong_node','wrong_room','still_chosen'):
            with self.subTest(mutation):
                a,b=copy.deepcopy(source),copy.deepcopy(target)
                if mutation=='closed':a['interaction']['chest']['open']=False
                if mutation=='wrong_node':b['map']['current']['y']=14
                if mutation=='wrong_room':b['room']='TreasureRoom'
                if mutation=='still_chosen':b['firstRoomChosen']=True
                try: verdict=self.effect(self.plan(a),b)
                except HeadlessContractError: continue
                self.assertEqual(verdict,'UNVERIFIED')


class DiscoveryTests(unittest.TestCase):
    def test_toolbox_rejects_wrong_source_and_noncolorless_offer(self):
        src=pick([card(i,id=id) for i,id in enumerate(('Finesse','Madness','Panacea'),12)],skip=False,bowl=False)
        src['roomPhase']='COMBAT'
        src['interaction']['card_reward'].update(discovery=True,source_action='ChooseOneColorless')
        src['player']['relics']=[{'id':'Toolbox','name':'tool','counter':-1}]
        bridge=IroncladInteractionBridge()
        self.assertEqual(len(state_frame(bridge,src).actions),3)
        for mutation in ('wrong_pool','healing','duplicate','missing_owner','skip'):
            with self.subTest(mutation):
                bad=copy.deepcopy(src)
                if mutation=='wrong_pool':bad['cardRewardOptions'][0]['id']='Bash'
                if mutation=='healing':bad['cardRewardOptions'][0]['id']='Bandage Up'
                if mutation=='duplicate':bad['cardRewardOptions'][0]['id']='Madness'
                if mutation=='missing_owner':bad['player']['relics']=[]
                if mutation=='skip':bad['interaction']['card_reward']['skip']=True
                with self.assertRaises(HeadlessContractError):state_frame(bridge,bad)

    def test_discovery_x_cost_is_not_zero(self):
        src=pick([card(12,id='Transmutation',cost=-1,costForTurn=-1)])
        src['roomPhase']='COMBAT'
        src['interaction']['card_reward'].update(discovery=True,source_action='DiscoveryAction',free_for_turn=True,copies=1)
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,src))
        new=raw('PLAYER_TURN',roomPhase='COMBAT')
        new['player']['hand']=[card(13,id='Transmutation',cost=-1,costForTurn=-1)]
        self.assertEqual(verify_effect(plan,state_frame(bridge,new,1)).status,'APPLIED')
        new['player']['hand'][0]['costForTurn']=0
        self.assertEqual(verify_effect(plan,state_frame(bridge,new,2)).status,'UNVERIFIED')

    def test_discovery_plus_binds_pending_cleanup_uuid_and_exact_rerolls(self):
        from sts_ai_framework.headless.event_receipts import Stream
        from sts_ai_framework.tests.test_headless_combat import combat_raw
        pool=['Omniscience','Finesse','Impatience','Madness']
        offered=[card(i,id=identity,cost=cost,costForTurn=cost,timesUpgraded=0)
                 for i,identity,cost in ((11,'Omniscience',4),(12,'Finesse',0),(13,'Impatience',0))]
        src=pick(offered,skip=False,discovery=True,source_action='DiscoveryAction',free_for_turn=True,copies=1)
        src.update(seed=123,room='MonsterRoom',roomPhase='COMBAT',history=[],observedKeys={})
        cleanup=card(20,id='Discovery',timesUpgraded=1,cost=1,costForTurn=1)
        src['driver_receipt']={'method':'playCard','card_uuid':cleanup['uuid']}
        rng=copy.deepcopy(combat_raw()['combat']['snapshot']['_headless_rng'])
        src['combat_receipt']={'played_card_uuids':[cleanup['uuid']],'turn':1,'rng':rng}
        for pile in ('hand','drawPile','discardPile','exhaustPile'):
            src['player'][pile]=[]
        dst=copy.deepcopy(src)
        dst.update(phase='PLAYER_TURN',interaction={'schema':src['interaction']['schema']})
        dst['player']['discardPile']=[cleanup]
        dst['player']['hand']=[card(21,id='Omniscience',cost=4,costForTurn=0,timesUpgraded=0)]
        stream=Stream(rng['streams']['card_random'])
        for _ in range(15):
            seen=set()
            while len(seen)<3:seen.add(pool[stream.bounded(len(pool))])
        result_rng=dst['combat_receipt']['rng']
        result_rng['streams']['card_random']=stream.state()
        dst['combat']={'snapshot':{'_combat_card_pools':{'any':pool},'_headless_rng':result_rng}}
        bridge=IroncladInteractionBridge();plan=choose(bridge,state_frame(bridge,src))
        self.assertEqual(verify_effect(plan,state_frame(bridge,dst,1)).status,'APPLIED')
        hex_src=copy.deepcopy(src);hex_dst=copy.deepcopy(dst)
        hex_src['player']['powers']=hex_dst['player']['powers']=[{'id':'Hex','amount':1}]
        existing=card(24,id='Defend_G',timesUpgraded=0,upgraded=False)
        hex_src['player']['drawPile']=[existing];hex_dst['player']['drawPile']=[copy.deepcopy(existing)]
        hex_stream=Stream(hex_dst['combat_receipt']['rng']['streams']['card_random'])
        position=hex_stream.bounded(1)
        hex_dst['combat_receipt']['rng']['streams']['card_random']=hex_stream.state()
        hex_dst['combat']['snapshot']['_headless_rng']=hex_dst['combat_receipt']['rng']
        hex_dst['player']['drawPile'].insert(position,card(23,id='Dazed',name='Dazed',cost=-2,costForTurn=-2,
            type='STATUS',rarity='COMMON',target='NONE',timesUpgraded=0,upgraded=False,misc=0,
            bottled=False,canUpgrade=False,baseDamage=-1,damage=-1,baseBlock=-1,block=-1,
            baseMagicNumber=-1,magicNumber=-1,exhaust=False))
        hex_plan=choose(bridge,state_frame(bridge,hex_src,2))
        self.assertEqual(verify_effect(hex_plan,state_frame(bridge,hex_dst,3)).status,'APPLIED')
        wrong=copy.deepcopy(hex_dst);wrong['player']['drawPile'][0]['costForTurn']=0
        self.assertEqual(verify_effect(hex_plan,state_frame(bridge,wrong,4)).status,'UNVERIFIED')
        wrong=copy.deepcopy(dst);wrong['player']['discardPile']=[dict(cleanup,uuid=card(22)['uuid'])]
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,5)).status,'UNVERIFIED')
        wrong=copy.deepcopy(dst);stream.bounded(len(pool))
        wrong['combat_receipt']['rng']['streams']['card_random']=stream.state()
        self.assertEqual(verify_effect(plan,state_frame(bridge,wrong,6)).status,'UNVERIFIED')


class DuplicateDiscardArchiveTests(unittest.TestCase):
    def test_both_prompts_bind_the_same_card_and_all_gameplay_rng(self):
        root=Path(os.environ.get('HEADLESS_ORIGINAL_160_BATCH',
                                  Path(__file__).resolve().parents[2]/'debug/headless-batch160-fixes'))
        archive=root/'lane1/reports/failure-db156a4737054d6cba641b83fa665723.json'
        if not archive.is_file(): self.skipTest('original Silent duplicate discard archive not mounted')
        data=json.loads(archive.read_bytes())
        for mutation in ('original','wrong_card','wrong_order','rng','block','wrong_selection'):
            with self.subTest(mutation):
                br=IroncladInteractionBridge(characters=('THE_SILENT',))
                b,a=(copy.deepcopy(data[k]['state']) for k in ('before','after'))
                frame=br.observe(b,run_id='dup',sequence=0)
                act=next(x for x in frame.actions if x.native_method=='confirmHandSelect')
                plan=br.prepare(frame,act.api_action)
                if mutation=='wrong_card':a['player']['discardPile'][-1]['uuid']='00000000-0000-4000-8000-000000000002'
                if mutation=='wrong_order':
                    pile=a['player']['discardPile'];pile[-2:]=reversed(pile[-2:])
                    for i,c in enumerate(pile):c['index']=i
                if mutation=='rng':a['combat_receipt']['rng']['streams']['card_random']['counter']+=1
                if mutation=='block':a['player']['block']-=1
                if mutation=='wrong_selection':a['interaction']['selection']['purpose']='exhaust'
                result=verify_effect(plan,br.observe(a,run_id='dup',sequence=1))
                self.assertEqual(result.status,'APPLIED' if mutation=='original' else 'UNVERIFIED')
