"""Exact Headless monster wake-up and temporary-power boundaries."""
import copy
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.tests.test_headless_combat import combat_raw


ROOT = Path(__file__).resolve().parents[1]
EXE = Path(os.environ.get('BATTLE_ENGINE_WORKER', ROOT / 'bin/battle-engine-worker.exe'))


@unittest.skipUnless(EXE.is_file(), 'Build the native worker')
class HeadlessMonsterParityTests(unittest.TestCase):
    def setUp(self):
        self.worker = Worker(EXE)
        self.addCleanup(self.worker.close)

    def snapshot(self, *, awake=False, block=1):
        s = copy.deepcopy(combat_raw()['combat']['snapshot'])
        m = s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='Lagavulin', current_health=50, max_health=50,
            current_block=block, powers=[] if awake else [{'power_id':'Metallicize','amount':8}])
        m.update(next_move=4 if awake else 5, intent_name='STUN' if awake else 'SLEEP',
                 move_history=[3,4] if awake else [5], _private={})
        return s

    @staticmethod
    def monster(key):
        return next(row for row in key if isinstance(row,list) and len(row)==30)

    def fingerprint(self, s):
        r = self.worker.execute({'operation':'fingerprint','snapshot':s},1000)
        self.assertTrue(r['supported'],r)
        return r

    def predict(self, s, command):
        r = self.worker.execute({'operation':'predict','snapshot':s,'command':command},1000)
        self.assertTrue(r['supported'],r)
        return r

    def assert_rng_unchanged(self, before, prediction):
        self.assertEqual(prediction['expected_exact_rng'],
            {k:before['_headless_rng']['streams'][k] for k in prediction['expected_exact_rng']})

    def test_lagavulin_normal_damage_records_wake_move_without_roll(self):
        s = self.snapshot()
        old = self.monster(self.fingerprint(s)['native_key'])
        p = self.predict(s,{'type':'CARD','card_index':0,'monster_index':0})
        new = self.monster(p['expected_native_key'])
        self.assertEqual(new[8:10],[old[8],old[8]])
        self.assertEqual((new[2],new[10],new[17]),(45,0,0))
        self.assert_rng_unchanged(s,p)

    def test_lagavulin_potion_damage_also_records_wake_move_without_roll(self):
        s = self.snapshot()
        old = self.monster(self.fingerprint(s)['native_key'])
        p = self.predict(s,{'type':'POTION','potion_index':0,'monster_index':0})
        new = self.monster(p['expected_native_key'])
        self.assertEqual(new[8:10],[old[8],old[8]])
        self.assertEqual((new[2],new[10],new[17]),(31,0,0))
        self.assert_rng_unchanged(s,p)

    def test_lagavulin_fully_blocked_damage_preserves_sleep_history_and_metallicize(self):
        s = self.snapshot(block=10)
        old = self.monster(self.fingerprint(s)['native_key'])
        p = self.predict(s,{'type':'CARD','card_index':0,'monster_index':0})
        new = self.monster(p['expected_native_key'])
        self.assertEqual(new[8:11],old[8:11])
        self.assertEqual((new[2],new[17]),(50,8))
        self.assert_rng_unchanged(s,p)

    def test_lagavulin_already_awake_damage_does_not_append_another_wake_move(self):
        s = self.snapshot(awake=True)
        old = self.monster(self.fingerprint(s)['native_key'])
        self.assertNotEqual(old[8],old[9])
        p = self.predict(s,{'type':'CARD','card_index':0,'monster_index':0})
        new = self.monster(p['expected_native_key'])
        self.assertEqual(new[8:11],old[8:11])
        self.assert_rng_unchanged(s,p)

    def choke_snapshot(self):
        s = copy.deepcopy(combat_raw()['combat']['snapshot'])
        m = s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='SlaverBlue',current_health=50,max_health=50,
            current_block=0,powers=[{'power_id':'Choked','amount':3}])
        m.update(next_move=1,intent_name='ATTACK',move_history=[1],_private={})
        return s

    def test_choked_amount_is_removed_at_monster_start_of_turn(self):
        s = self.choke_snapshot()
        self.assertEqual(self.monster(self.fingerprint(s)['native_key'])[13],3)
        p = self.predict(s,{'type':'END'})
        self.assertEqual(self.monster(p['expected_native_key'])[13],0)
        self.assertEqual(p['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+1)

    def test_choked_still_applies_during_player_turn_before_removal(self):
        s = self.choke_snapshot()
        p = self.predict(s,{'type':'CARD','card_index':1,'monster_index':-1})
        new = self.monster(p['expected_native_key'])
        self.assertEqual((new[2],new[13]),(47,3))
        self.assert_rng_unchanged(s,p)

    def test_red_slaver_imports_and_advances_entangle_latch(self):
        s=copy.deepcopy(combat_raw()['combat']['snapshot'])
        m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='SlaverRed',current_health=50,max_health=50,current_block=0,powers=[])
        m.update(next_move=2,intent_name='STRONG_DEBUFF',move_history=[1,2],
                 _private={'SlaverRed.usedEntangle':False})
        self.assertEqual(self.monster(self.fingerprint(s)['native_key'])[27],0)
        predicted=self.predict(s,{'type':'END'})
        self.assertEqual(self.monster(predicted['expected_native_key'])[27],1)
        m['_private']['SlaverRed.usedEntangle']=True
        self.assertEqual(self.monster(self.fingerprint(s)['native_key'])[27],1)
        m['_private']['SlaverRed.usedEntangle']='true'
        self.assertFalse(self.worker.execute({'operation':'fingerprint','snapshot':s},1000)['supported'])

    def test_red_slaver_used_entangle_roll_boundary_is_55(self):
        # Restore exact XS128 states that make the next random(99) roll each
        # boundary value; no random seed guessing or statistical assertion.
        mask=(1<<64)-1
        def state_for(roll):
            for n in range(1,5000):
                x=(n*0x9e3779b97f4a7c15)&mask;b=(n*0xbf58476d1ce4e5b9)&mask
                a=x ^ ((x<<23)&mask)
                next_state=(a ^ b ^ (a>>17) ^ (b>>26))&mask
                if (((next_state+b)&mask)>>1)%100 == roll:
                    return {'state0':f'{x:016x}','state1':f'{b:016x}'}
            self.fail('No RNG fixture for roll')
        moves={}
        for roll in (49,50,54,55):
            s=copy.deepcopy(combat_raw()['combat']['snapshot'])
            m=s['cur_map_node_state']['monster_data'][0]
            m['creature'].update(id='SlaverRed',current_health=50,max_health=50,current_block=0,powers=[])
            m.update(next_move=1,intent_name='ATTACK',move_history=[3,1],
                     _private={'SlaverRed.usedEntangle':True})
            s['_headless_rng']['streams']['ai'].update(state_for(roll))
            moves[roll]=self.monster(self.predict(s,{'type':'END'})['expected_native_key'])[8]
        self.assertEqual(moves[49],moves[50]);self.assertEqual(moves[50],moves[54])
        self.assertNotEqual(moves[54],moves[55])

    def test_legacy_wake_and_choke_imports_keep_their_existing_contract(self):
        for s,command in ((self.snapshot(),{'type':'CARD','card_index':0,'monster_index':0}),
                          (self.choke_snapshot(),{'type':'END'})):
            s.pop('_engine');s.pop('_headless_rng')
            p = self.predict(s,command)
            self.assertNotIn('expected_native_key',p)
            self.assertNotIn('rng_restore_mode',p)


if __name__ == '__main__':
    unittest.main()
