"""Exact Headless timer powers and FIFO autoplay boundaries."""
import copy
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.tests.test_headless_combat import combat_raw


EXE = Path(os.environ.get('BATTLE_ENGINE_WORKER', Path(__file__).resolve().parents[1]/'bin/battle-engine-worker.exe'))


@unittest.skipUnless(EXE.is_file(), 'Build the native worker')
class HeadlessPowerPotionParityTests(unittest.TestCase):
    def setUp(self):
        self.worker = Worker(EXE)
        self.addCleanup(self.worker.close)
        self.snapshot = copy.deepcopy(combat_raw()['combat']['snapshot'])
        self.snapshot['player_state']['creature']['powers'] = []

    def fingerprint(self, snapshot=None):
        result = self.worker.execute({'operation':'fingerprint','snapshot':snapshot or self.snapshot},1000)
        self.assertTrue(result['supported'],result)
        return result

    @staticmethod
    def bomb(turns=3, damage=40, identity='TheBomb0'):
        return {'power_id':identity,'amount':turns,'_private':{'TheBombPower.damage':damage}}

    def test_bomb_restores_damage_into_exact_countdown_not_turn_amount(self):
        for turns in (1,2,3):
            with self.subTest(turns=turns):
                self.snapshot['player_state']['creature']['powers'] = [self.bomb(turns,50)]
                result = self.fingerprint()
                expected = [0,0,0]; expected[turns-1] = 50
                self.assertEqual(result['native_key'][4][32:35],expected)
                self.assertEqual(result['exact_rng'],self.fingerprint()['exact_rng'])

    def test_pen_nib_java_counter_nine_imports_armed_native_sentinel(self):
        p=self.snapshot['player_state']
        p['relics'].append({'relic_id':'Pen Nib','counter':8})
        self.assertEqual(self.fingerprint()['native_key'][4][25],8)
        command={'type':'CARD','card_index':0,'monster_index':0}
        result=self.worker.execute({'operation':'predict','snapshot':self.snapshot,'command':command},1000)
        self.assertTrue(result['supported'],result)
        self.assertEqual(result['expected_native_key'][4][25],-1)
        p['relics'][-1]['counter']=9
        p['creature']['powers']=[{'power_id':'Pen Nib','amount':1}]
        self.assertEqual(self.fingerprint()['native_key'][4][25],-1)
        result=self.worker.execute({'operation':'predict','snapshot':self.snapshot,'command':command},1000)
        self.assertTrue(result['supported'],result)
        self.assertEqual(result['expected_native_key'][4][25],0)
        p['creature']['powers']=[]
        self.assertFalse(self.worker.execute({'operation':'fingerprint','snapshot':self.snapshot},1000)['supported'])
        p['relics'][-1]['counter']=10
        self.assertFalse(self.worker.execute({'operation':'fingerprint','snapshot':self.snapshot},1000)['supported'])

    def test_bombs_with_different_countdowns_remain_distinct(self):
        self.snapshot['player_state']['creature']['powers'] = [self.bomb(1,40),self.bomb(3,50,'TheBomb12')]
        self.assertEqual(self.fingerprint()['native_key'][4][32:35],[40,0,50])

    def test_bomb_requires_canonical_instance_id_and_bound_private_damage(self):
        bad = [self.bomb(identity='TheBombX'),self.bomb(identity='TheBomb01'),
               self.bomb(identity='TheBomb2147483648'),self.bomb(turns=0),self.bomb(turns=4),
               self.bomb(damage=0),self.bomb(damage=128),
               {'power_id':'TheBomb0','amount':3,'_private':{}},
               {'power_id':'TheBomb0','amount':3,'_private':{'TheBombPower.damage':True}}]
        for power in bad:
            with self.subTest(power=power):
                self.snapshot['player_state']['creature']['powers'] = [power]
                result = self.worker.execute({'operation':'fingerprint','snapshot':self.snapshot},1000)
                self.assertFalse(result['supported'],result)

    def test_same_countdown_instances_are_not_silently_merged(self):
        self.snapshot['player_state']['creature']['powers'] = [self.bomb(),self.bomb(identity='TheBomb1')]
        result = self.worker.execute({'operation':'fingerprint','snapshot':self.snapshot},1000)
        self.assertFalse(result['supported'],result)
        self.assertEqual(result['reason'],'multiple_headless_bombs_same_timer')

    def test_wrath_next_turn_is_a_strict_source_marker_with_native_presence(self):
        self.snapshot['player_state']['creature']['powers'] = [{'power_id':'WrathNextTurnPower','amount':-1,'_private':{}}]
        result = self.fingerprint()
        rows = [row for row in result['native_key'] if isinstance(row,list) and len(row)==2]
        self.assertTrue(result['native_key'][4][15] & (1<<25))
        self.assertFalse(any(row[0]==25 for row in rows))
        for amount in (0,1,True):
            self.snapshot['player_state']['creature']['powers'][0]['amount'] = amount
            result = self.worker.execute({'operation':'fingerprint','snapshot':self.snapshot},1000)
            self.assertFalse(result['supported'],result)

    def test_wrath_marker_presence_stays_bound_and_legacy_numeric_key_is_unchanged(self):
        absent=self.fingerprint()['native_key']
        self.snapshot['player_state']['creature']['powers'] = [{'power_id':'WrathNextTurnPower','amount':-1,'_private':{}}]
        present=self.fingerprint()['native_key']
        self.assertNotEqual(present,absent)
        self.assertEqual(present[4][15]^absent[4][15],1<<25)
        legacy=copy.deepcopy(self.snapshot);legacy.pop('_headless_rng');legacy.pop('_engine',None)
        rows=self.fingerprint(legacy)['native_key']
        self.assertIn([25,-1],rows)

    def test_repeated_wrath_next_turn_does_not_create_numeric_marker_semantics(self):
        self.snapshot['player_state']['creature']['powers'] = [{'power_id':'WrathNextTurnPower','amount':-1,'_private':{}}]
        card=self.snapshot['player_state']['hand'][0]
        card.update(card_id='Vengeance',cost=1,cost_for_turn=1)
        result=self.worker.execute({'operation':'predict','snapshot':self.snapshot,
            'command':{'type':'CARD','card_index':0,'monster_index':-1}},1000)
        self.assertTrue(result['supported'],result)
        key=result['expected_native_key']
        self.assertTrue(key[4][15] & (1<<25))
        self.assertFalse(any(isinstance(row,list) and len(row)==2 and row[0]==25 for row in key))

    def autoplay(self, *, headless):
        s = copy.deepcopy(self.snapshot); p = s['player_state']
        p['creature']['powers'] = []
        template = copy.deepcopy(p['hand'][0]); draw=[]
        for i, (name,cost) in enumerate((('Defend_R',1),('Strike_R',1),('Bash',2))):
            card=copy.deepcopy(template)
            card.update(card_id=name,cost=cost,cost_for_turn=cost,uuid=f'00000000-0000-0000-0000-{110+i:012d}')
            draw.append(card)
        p['draw_pile']=draw; p['discard_pile']=[]; p['exhaust_pile']=[]
        p['master_deck'] = copy.deepcopy(p['hand']+draw)
        p['potions'][0]['id']='DistilledChaos'
        m=s['cur_map_node_state']['monster_data'][0]['creature'];m.update(current_health=500,max_health=500)
        if not headless:
            s.pop('_headless_rng');s.pop('_engine',None)
        result=self.worker.execute({'operation':'predict','snapshot':s,'command':{'type':'POTION','potion_index':0,'monster_index':-1}},1000)
        self.assertTrue(result['supported'],result)
        return s,result

    def test_headless_play_top_cards_are_queued_fifo(self):
        before,result=self.autoplay(headless=True)
        self.assertEqual([c['id'] for c in result['expected_after']['discard_pile']],['Bash','Strike_R','Defend_R'])
        self.assertEqual(result['expected_exact_rng']['card_random']['counter'],
                         before['_headless_rng']['streams']['card_random']['counter']+3)

    def test_legacy_autoplay_queue_contract_is_unchanged(self):
        _,result=self.autoplay(headless=False)
        self.assertEqual([c['id'] for c in result['expected_after']['discard_pile']],['Defend_R','Strike_R','Bash'])
        self.assertNotIn('expected_native_key',result)
