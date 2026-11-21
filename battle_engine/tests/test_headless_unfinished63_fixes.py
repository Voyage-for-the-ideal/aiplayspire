"""Native boundaries found by the focused 63-case rerun."""
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.tests.test_headless_combat import combat_raw


EXE = Path(os.environ.get('BATTLE_ENGINE_WORKER', Path(__file__).resolve().parents[1] / 'bin/battle-engine-worker.exe'))


@unittest.skipUnless(EXE.is_file(), 'Build the native worker')
class Unfinished63CombatTests(unittest.TestCase):
    def setUp(self):
        self.worker=Worker(EXE)
        self.addCleanup(self.worker.close)

    def snapshot(self, role='IRONCLAD', **monster):
        s=combat_raw(role)['combat']['snapshot']
        m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id=monster.pop('id'),current_health=50,max_health=50)
        m.update(monster)
        return s

    def predict(self,s,cmd):
        response=self.worker.execute(dict(operation='predict',snapshot=s,command=cmd),1000)
        self.assertTrue(response['supported'],response)
        return response

    def test_chosen_applies_hex_without_an_unbacked_presence_bit(self):
        s=self.snapshot(id='Chosen',next_move=4,intent_name='STRONG_DEBUFF',move_history=[5,4])
        # Import is legal, then executing Chosen Hex must retain its amount.
        result=self.predict(s,{'type':'END'})
        self.assertEqual(result['expected_screen'],'NONE')
        self.assertEqual(result['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+1)

    def test_transient_advances_fixed_intent_without_ai_roll(self):
        s=self.snapshot(id='Transient',next_move=1,intent_name='ATTACK',move_history=[1],
                        move_base_damage=30,_private={'Transient.count':0,'Transient.startingDeathDmg':30})
        m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(max_health=999,current_health=999,powers=[{'power_id':'Fading','amount':5},
                                                                   {'power_id':'Shifting','amount':-1}])
        p=self.predict(s,{'type':'END'})
        self.assertEqual(p['expected_exact_rng']['ai'],s['_headless_rng']['streams']['ai'])
        row=next(x for x in p['expected_native_key'] if isinstance(x,list) and len(x)==30)
        self.assertEqual((row[25],row[27],row[8]==row[9]),(4,1,True))

    def test_zero_slow_still_stacks_on_a_card_play(self):
        s=self.snapshot(id='GiantHead',next_move=3,intent_name='ATTACK',move_history=[3],
                        move_base_damage=13,_private={'GiantHead.count':4,'GiantHead.startingDeathDmg':30})
        s['cur_map_node_state']['monster_data'][0]['creature']['powers']=[{'power_id':'Slow','amount':0}]
        p=self.predict(s,{'type':'CARD','card_index':1,'monster_index':-1})
        row=next(x for x in p['expected_native_key'] if isinstance(x,list) and len(x)==30)
        self.assertEqual(row[25],1)

    def test_giant_head_countdown_and_zero_slow_survive_end_turn(self):
        s=self.snapshot(id='GiantHead',next_move=1,intent_name='DEBUFF',move_history=[1],
                        _private={'GiantHead.count':1,'GiantHead.startingDeathDmg':30})
        s['cur_map_node_state']['monster_data'][0]['creature']['powers']=[{'power_id':'Slow','amount':2}]
        p=self.predict(s,{'type':'END'})
        row=next(x for x in p['expected_native_key'] if isinstance(x,list) and len(x)==30)
        self.assertEqual((row[25],row[27]),(0,0))
        self.assertNotEqual(row[10],0)
        self.assertEqual(p['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+1)

    def test_defect_energized_blue_imports_and_recharges(self):
        s=self.snapshot('DEFECT',id='Cultist',next_move=3,intent_name='BUFF',move_history=[3])
        p=s['player_state'];p['creature']['powers']=[{'power_id':'EnergizedBlue','amount':1}]
        result=self.predict(s,{'type':'END'})
        self.assertEqual(result['expected_after']['energy'],p['energy_manager_energy']+1)

    def test_acid_slime_retaliation_reapplies_split_after_its_move_roll(self):
        s=self.snapshot(id='AcidSlime_L',next_move=2,intent_name='ATTACK_DEBUFF',move_history=[2],move_base_damage=11,
                        _private={'AcidSlime_L.splitTriggered':False})
        s['cur_map_node_state']['monster_data'][0]['creature'].update(max_health=70,current_health=38)
        s['player_state']['creature']['powers']=[{'power_id':'Thorns','amount':3}]
        result=self.predict(s,{'type':'END'})
        row=next(x for x in result['expected_native_key'] if isinstance(x,list) and len(x)==30)
        self.assertEqual((row[2],row[8],row[9]),(35,5,0))  # ACID_SLIME_L_SPLIT neutralizes prior intent.
        self.assertEqual(result['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+1)

    def test_hello_world_uses_live_common_not_source_catalog(self):
        s=self.snapshot('DEFECT',id='Cultist',next_move=3,intent_name='BUFF',move_history=[3])
        s['player_state']['creature']['powers']=[{'power_id':'Hello','amount':1}]
        s['_combat_card_pools']={k:['Leap'] for k in ('any','attacks','skills','powers','colorless','common','uncommon','rare')}
        s['_combat_card_pools']['live_common']=['Streamline']
        p=self.predict(s,{'type':'END'})
        self.assertIn('Streamline',[card['id'] for card in p['expected_after']['hand']])
        self.assertEqual(p['expected_exact_rng']['card_random']['counter'],s['rng_state']['card_random_rng_counter']+1)
