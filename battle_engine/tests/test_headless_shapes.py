"""Ancient Shapes: paired turn, private counter, and gameplay RNG checks."""
import copy
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.tests.test_headless_combat import combat_raw


EXE = Path(os.environ.get('BATTLE_ENGINE_WORKER', Path(__file__).resolve().parents[1] / 'bin/battle-engine-worker.exe'))


@unittest.skipUnless(EXE.is_file(), 'Build the native worker')
class HeadlessShapesTest(unittest.TestCase):
    def setUp(self):
        self.worker = Worker(EXE)
        self.addCleanup(self.worker.close)

    def snapshot(self, turns=0):
        s = combat_raw()['combat']['snapshot']
        s['ascension_level'] = 10
        s['turn'] = turns+1
        p = s['player_state']['creature']
        p['current_health'] = p['max_health'] = 80
        p['current_block'] = 0
        monsters = s['cur_map_node_state']['monster_data']
        exploder = monsters[0]
        spiker = copy.deepcopy(exploder)
        monsters.append(spiker)
        exploder['creature'].update(id='Exploder',current_health=35,max_health=35,current_block=0,
                                  powers=[{'power_id':'Explosive','amount':3-turns}])
        exploder.update(next_move=2 if turns==2 else 1, intent_name='UNKNOWN' if turns==2 else 'ATTACK',
                        move_history=[1,1,2][:turns+1],_private={'Exploder.turnCount':turns,'Exploder.attackDmg':11})
        spiker['creature'].update(id='Spiker',current_health=55,max_health=55,current_block=0,
                                 powers=[{'power_id':'Thorns','amount':4}])
        spiker.update(next_move=2,intent_name='BUFF',move_history=[2],
                      _private={'Spiker.thornsCount':0,'Spiker.attackDmg':9,'Spiker.startingThorns':4})
        return s

    def predict_end(self,s):
        result=self.worker.execute({'operation':'predict','snapshot':s,'command':{'type':'END'}},1000)
        self.assertTrue(result['supported'],result)
        self.assertEqual(result['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+2)
        return result

    @staticmethod
    def monsters(result):
        # Full native key ends in one 30-field row per active monster.
        return [row for row in result['expected_native_key'] if isinstance(row,list) and len(row)==30]

    def test_two_attacks_advance_countdown_and_switch_to_explosion(self):
        first=self.predict_end(self.snapshot())
        second=self.predict_end(self.snapshot(1))
        a,b=self.monsters(first)[0],self.monsters(second)[0]
        self.assertEqual((a[26],a[27],b[26],b[27]),(2,1,1,2))
        self.assertEqual(a[8],a[9])
        self.assertNotEqual(b[8],b[9])
        self.assertEqual(first['expected_after']['hp'],69)
        self.assertEqual(second['expected_after']['hp'],69)

    def test_explosion_self_kills_and_deals_thorns_damage(self):
        result=self.predict_end(self.snapshot(2))
        self.assertEqual([m['hp'] for m in result['expected_after']['monsters']],[0,55])
        self.assertEqual(result['expected_after']['hp'],50)

    def test_explosive_owner_and_countdown_are_bound(self):
        s=self.snapshot()
        s['cur_map_node_state']['monster_data'][0]['creature']['powers'][0]['amount']=1
        fp=self.worker.execute({'operation':'fingerprint','snapshot':s},1000)
        self.assertEqual(fp['reason'],'conflicting_explosive_counter')
        s=self.snapshot()
        s['cur_map_node_state']['monster_data'][1]['creature']['powers']=[{'power_id':'Explosive','amount':3}]
        fp=self.worker.execute({'operation':'fingerprint','snapshot':s},1000)
        self.assertEqual(fp['reason'],'invalid_explosive_power')

    def test_spiker_imports_thorns_count_independent_of_history(self):
        s=self.snapshot()
        s['cur_map_node_state']['monster_data'][1]['_private']['Spiker.thornsCount']=5
        result=self.predict_end(s)
        spiker=self.monsters(result)[1]
        self.assertEqual((spiker[25],spiker[27]),(6,6))
        self.assertNotEqual(spiker[8],spiker[9])

    def test_search_with_unmapped_stance_potion_can_still_make_a_legal_plan(self):
        import json
        s = self.snapshot()
        s['player_state']['potions'][0]['id'] = 'StancePotion'
        result = self.worker.execute({'snapshot':s,'search_profile':'FAST','max_expansions':100,
                                      'timeout_ms':100},1000)
        self.assertTrue(result['supported'],result)
        move=json.loads(result['commands'][0]['command'])
        self.assertFalse(move['type']=='POTION' and move['potion_index']==0)
