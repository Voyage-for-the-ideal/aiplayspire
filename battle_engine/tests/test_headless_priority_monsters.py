"""Exact A10 monster boundaries from the recheck44 and fresh40 ledgers."""
import copy
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import _private_invariants, _private_match
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_combat import combat_raw


EXE = Path(os.environ.get('BATTLE_ENGINE_WORKER', Path(__file__).resolve().parents[1] / 'bin/battle-engine-worker.exe'))


def snapshot(identity, move, intent, state, powers=(), *, hp=50, block=0):
    s = combat_raw()['combat']['snapshot']
    s['ascension_level'] = 10
    m = s['cur_map_node_state']['monster_data'][0]
    m['creature'].update(id=identity, current_health=hp, max_health=80,
                         current_block=block, powers=list(powers))
    m.update(next_move=move, intent_name=intent, move_history=[move], _private=dict(state))
    return s, m


def maw():
    return snapshot('Maw', 2, 'STRONG_DEBUFF', {'Maw.turnCount':2, 'Maw.roared':False,
                    'Maw.slamDmg':30, 'Maw.nomDmg':5, 'Maw.strUp':3, 'Maw.terrifyDur':3})


def awakened():
    return snapshot('AwakenedOne', 1, 'ATTACK', {'AwakenedOne.form1':True,
        'AwakenedOne.firstTurn':False, 'AwakenedOne.animateParticles':False,
        'AwakenedOne.saidPower':False, 'AwakenedOne.fireTimer':0.0},
        [{'power_id':'Regenerate','amount':10}, {'power_id':'Unawakened','amount':-1},
         {'power_id':'Curiosity','amount':1}])


class PriorityMonsterInvariantsTests(unittest.TestCase):
    def test_maw_dynamic_fields_are_not_frozen_but_source_constants_are(self):
        s, m = maw()
        before = _private_invariants(s)
        m['_private'].update({'Maw.turnCount':3, 'Maw.roared':True})
        self.assertTrue(_private_match(before, s))
        m['_private']['Maw.nomDmg'] = 7
        self.assertFalse(_private_match(before, s))
        m['_private']['Maw.turnCount'] = True
        with self.assertRaisesRegex(HeadlessContractError, 'Maw.turnCount'):
            _private_match(before, s)

    def test_awakened_cosmetic_latches_and_timer_can_advance(self):
        s, m = awakened()
        before = _private_invariants(s)
        m['_private'].update({'AwakenedOne.animateParticles':True, 'AwakenedOne.fireTimer':0.0625})
        self.assertTrue(_private_match(before, s))
        m['_private']['AwakenedOne.fireTimer'] = float('inf')
        with self.assertRaisesRegex(HeadlessContractError, 'particle timer'):
            _private_match(before, s)


@unittest.skipUnless(EXE.is_file(), 'Build the native worker')
class PriorityMonsterNativeTests(unittest.TestCase):
    def setUp(self):
        self.worker = Worker(EXE)
        self.addCleanup(self.worker.close)

    def execute(self, s, command=None):
        if command is None:
            request = {'operation':'fingerprint','snapshot':s}
        else:
            request = {'operation':'predict','snapshot':s,'command':command}
        r = self.worker.execute(request, 1000)
        self.assertTrue(r['supported'], r)
        return r

    @staticmethod
    def monster(key):
        return next(row for row in key if isinstance(row, list) and len(row) == 30)

    def test_maw_roar_advances_source_counter_and_nom_uses_it(self):
        s, m = maw()
        self.assertEqual(self.monster(self.execute(s)['native_key'])[27], 4)
        p = self.execute(s, {'type':'END'})
        self.assertEqual(self.monster(p['expected_native_key'])[27], 7)
        self.assertEqual(p['expected_exact_rng']['ai']['counter'], s['rng_state']['ai_rng_counter'] + 1)
        m.update(next_move=5, intent_name='ATTACK', move_history=[5], move_hits=3, move_base_damage=5)
        m['_private'].update({'Maw.turnCount':6,'Maw.roared':True})
        p = self.execute(s, {'type':'END'})
        self.assertEqual(self.monster(p['expected_native_key'])[27], 15)
        self.assertEqual(p['expected_after']['hp'], s['player_state']['creature']['current_health'] - 15)

    def test_awakened_regenerate_is_preserved_and_heals_at_end_of_turn(self):
        s, m = awakened()
        self.assertEqual(self.monster(self.execute(s)['native_key'])[20], 10)
        after = self.monster(self.execute(s, {'type':'END'})['expected_native_key'])
        self.assertEqual((after[2], after[20], after[27]), (60, 10, 0))
        m['creature']['powers'][1]['amount'] = 1
        r = self.worker.execute({'operation':'fingerprint','snapshot':s}, 1000)
        self.assertFalse(r['supported'], r)

    def test_awakened_rebirth_preserves_marker_removal_and_first_second_form_roll(self):
        s, m = awakened()
        m['creature']['current_health'] = 4
        down = self.execute(s, {'type':'CARD','card_index':0,'monster_index':0})
        row = self.monster(down['expected_native_key'])
        self.assertEqual((row[2], row[6], row[20], row[27]), (0, True, 10, 3))
        self.assertEqual(row[8], row[9])  # Immediate and queued source SetMove both record Rebirth.
        m['creature'].update(current_health=0, half_dead=True, powers=[{'power_id':'Regenerate','amount':10}])
        m.update(next_move=3, move_history=[1,3,3], intent_name='UNKNOWN')
        m['_private'].update({'AwakenedOne.form1':False, 'AwakenedOne.firstTurn':True})
        reborn = self.execute(s, {'type':'END'})
        new = self.monster(reborn['expected_native_key'])
        self.assertEqual((new[2], new[3], new[6], new[9], new[20], new[27]), (320,320,False,row[8],10,3))
        self.assertNotEqual(new[8], row[8])
        self.assertEqual(reborn['expected_exact_rng']['ai']['counter'], s['rng_state']['ai_rng_counter'] + 1)

    def test_writhing_mass_rolls_recursively_only_on_unblocked_nonlethal_hits(self):
        # Archived AI stream from 5050437: Multi-Strike rejects its first roll,
        # consumes a chance and a bounded roll, and lands on Strong Strike.
        ai = {'counter':1,'state0':'a615b5548647a7b8','state1':'f19f3bca45940911'}
        landing = {'counter':4,'state0':'e97450f574c7480c','state1':'b9a47c85951cdb25'}
        s, m = snapshot('WrithingMass', 1, 'ATTACK', {'WrithingMass.firstMove':False,
            'WrithingMass.normalDebuffAmt':2,'WrithingMass.usedMegaDebuff':False},
            [{'power_id':'Malleable','amount':3}, {'power_id':'Compulsive','amount':-1}])
        s['_headless_rng']['streams']['ai'] = copy.deepcopy(ai)
        s['rng_state']['ai_rng_counter'] = ai['counter']
        old = self.monster(self.execute(s)['native_key'])
        self.assertEqual(old[26], -1)
        action = {'type':'CARD','card_index':0,'monster_index':0}
        p = self.execute(s, action)
        new = self.monster(p['expected_native_key'])
        self.assertEqual(p['expected_exact_rng']['ai'], landing)
        self.assertEqual((new[4], new[9], new[25], new[26]), (3, old[8], 4, -1))
        self.assertNotEqual(new[8], old[8])
        m['creature']['current_block'] = 20
        self.assertEqual(self.execute(s, action)['expected_exact_rng']['ai'], ai)
        m['creature'].update(current_block=0, current_health=4)
        lethal = self.execute(s, action)
        self.assertEqual(lethal['terminal_prediction'], 'victory')
        self.assertEqual(lethal['expected_exact_rng']['ai'], ai)

    def test_nemesis_burn_count_uses_a18_and_cooldown_is_in_the_key(self):
        s,m=snapshot('Nemesis',4,'DEBUFF',{'Nemesis.fireDmg':7,'Nemesis.firstMove':False,'Nemesis.scytheCooldown':-1})
        s['_headless_rng']['streams']['ai']={'counter':1,'state0':'1f25893ec076ea73','state1':'b87c37b16a1d6616'}
        s['rng_state']['ai_rng_counter']=1
        for asc,burns in ((10,3),(18,5)):
            with self.subTest(ascension=asc):
                s['ascension_level']=asc
                self.assertEqual(self.monster(self.execute(s)['native_key'])[27],-1)
                result=self.execute(s,{'type':'END'})
                after=result['expected_after']
                self.assertEqual(sum(c['id']=='Burn' for pile in ('hand','draw_pile','discard_pile','exhaust_pile')
                                     for c in after[pile]),burns)
                self.assertEqual(self.monster(result['expected_native_key'])[27],-2)

    def test_writhing_implant_changes_the_master_deck_or_spends_omamori(self):
        s,_=snapshot('WrithingMass',4,'STRONG_DEBUFF',{'WrithingMass.firstMove':False,
            'WrithingMass.normalDebuffAmt':2,'WrithingMass.usedMegaDebuff':False})
        relics=s['player_state']['relics']
        for charges in (0,1,2):
            with self.subTest(charges=charges):
                relics[:]=[{'relic_id':'Omamori','counter':charges}]
                old=self.execute(s)['native_key']
                new=self.execute(s,{'type':'END'})['expected_native_key']
                self.assertEqual(len(new[3]),len(old[3])+int(charges==0))
                self.assertEqual(new[4][40], max(0,charges-1))
