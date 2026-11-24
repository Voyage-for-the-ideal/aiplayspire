"""Focused regression tests for original PLAYER_TURN stop classes, independent of archived runs."""
import copy
import os
import unittest
from pathlib import Path
from uuid import UUID

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import _normal_card_lineage
from sts_ai_framework.headless.event_receipts import Stream
from sts_ai_framework.tests.test_headless_combat import combat_raw

EXE = Path(os.environ.get('BATTLE_ENGINE_WORKER', Path(__file__).resolve().parents[1]/'bin/battle-engine-worker.exe'))


class CardLineageTests(unittest.TestCase):
    def test_distinct_same_type_cards_and_fresh_slot(self):
        source=combat_raw()['combat']['snapshot']
        after=copy.deepcopy(source)
        p=after['player_state']
        new=copy.deepcopy(p['hand'][0]);new['uuid']=str(UUID(int=111));p['hand'].append(new)
        expected={k:[c['uuid'] for c in p[k]] for k in ('hand','draw_pile','discard_pile','exhaust_pile')}
        expected['hand'][-1]=None
        self.assertTrue(_normal_card_lineage(source,after,expected))
        # Fresh may not reuse a known UUID, even one from another pile.
        new['uuid']=p['draw_pile'][0]['uuid']
        self.assertFalse(_normal_card_lineage(source,after,expected))
        new['uuid']=str(UUID(int=111))
        # Correct features but wrong identity of a known card.
        p['hand'][0]['uuid'],new['uuid']=new['uuid'],p['hand'][0]['uuid']
        self.assertFalse(_normal_card_lineage(source,after,expected))


@unittest.skipUnless(EXE.is_file(),'Build the native worker')
class CombatEffectsTests(unittest.TestCase):
    def setUp(self):
        self.w=Worker(EXE);self.addCleanup(self.w.close)
        self.s=combat_raw()['combat']['snapshot']
        self.p=self.s['player_state']
        self.pp=self.p['creature']['powers']
        self.pp.clear()

    def req(self,op,**kw):
        return self.w.execute(dict(operation=op,snapshot=self.s,**kw),1000)

    def predict(self,command):
        r=self.req('predict',command=command)
        self.assertTrue(r['supported'],r)
        return r

    def test_panache_private_damage_and_count_are_bound(self):
        row={'power_id':'Panache','amount':1,'_private':{'PanachePower.damage':10}}
        self.pp.append(row)
        r=self.predict({'type':'CARD','card_index':0,'monster_index':0})
        row['_private']['PanachePower.damage']=11
        r2=self.predict({'type':'CARD','card_index':0,'monster_index':0})
        self.assertEqual(r['expected_after']['monsters'][0]['hp']-1,r2['expected_after']['monsters'][0]['hp'])
        row['amount']=0;self.assertFalse(self.req('fingerprint')['supported'])
        row['amount']=6;self.assertFalse(self.req('fingerprint')['supported'])
        row['amount']=1;row['_private']={};self.assertFalse(self.req('fingerprint')['supported'])

    def test_all_out_attack_only_uses_target_rng_when_there_is_a_choice(self):
        c=self.p['hand'][0];c.update(card_id='All Out Attack',cost=1,cost_for_turn=1)
        cmd={'type':'CARD','card_index':0,'monster_index':-1}
        counter=self.s['_headless_rng']['streams']['card_random']['counter']
        r=self.predict(cmd)
        self.assertEqual(r['expected_exact_rng']['card_random']['counter'],counter)
        extra=copy.deepcopy(self.p['hand'][1]);extra['uuid']=str(UUID(int=54321));self.p['hand'].append(extra)
        r=self.predict(cmd)
        self.assertEqual(r['expected_exact_rng']['card_random']['counter'],counter+1)
        self.assertEqual(len(r['expected_after']['hand']),1)

    def test_source_louse_damage_cross_checks_private_and_visible_intent(self):
        m=self.s['cur_map_node_state']['monster_data'][0]
        m['creature']['id']='FuzzyLouseDefensive';m['creature']['powers']=[]
        m.update(next_move=3,intent_name='ATTACK',move_history=[3],move_base_damage=5,_private={'LouseDefensive.biteDamage':5})
        self.assertTrue(self.req('fingerprint')['supported'])
        m['move_base_damage']=6
        self.assertFalse(self.req('fingerprint')['supported'])
        m['move_base_damage']=4;m['_private']['LouseDefensive.biteDamage']=4
        self.assertFalse(self.req('fingerprint')['supported'])

    def test_settled_exhaust_cost_reset_preserves_current_hand_cost(self):
        exhausted=copy.deepcopy(self.p['hand'][0]);exhausted.update(card_id='Impervious',uuid=str(UUID(int=12345)),cost=2,cost_for_turn=0)
        exhausted['exhaust']=True
        self.p['exhaust_pile']=[exhausted]
        self.s['cards_played_this_combat']=[{'card_index':-1,'card_state':copy.deepcopy(exhausted)}]
        self.p['hand'][0].update(cost_for_turn=0)
        raw=self.req('fingerprint')['projection']
        self.assertEqual(raw['exhaust_pile'][0]['turn_cost'],0)
        r=self.predict({'type':'CARD','card_index':1,'monster_index':-1})
        self.assertEqual(r['expected_after']['exhaust_pile'][0]['turn_cost'],2)
        self.assertEqual(r['expected_after']['hand'][0]['turn_cost'],0)

    def test_fiend_fire_rolls_even_on_the_last_card_in_hand(self):
        hand=self.p['hand']
        hand[0].update(card_id='Fiend Fire',cost=2,cost_for_turn=2,exhaust=True)
        rng=Stream(self.s['_headless_rng']['streams']['card_random'])
        rest=hand[1:]
        order=[]
        while rest:order.append(rest.pop(rng.bounded(len(rest)))['uuid'])
        result=self.predict({'type':'CARD','card_index':0,'monster_index':0})
        self.assertEqual(result['expected_card_uuids']['exhaust_pile'],order+[hand[0]['uuid']])
        self.assertEqual(result['expected_exact_rng']['card_random'],rng.state())

    def test_magnetism_draws_from_the_live_colorless_pool(self):
        self.pp.append({'power_id':'Magnetism','amount':1})
        pools={k:['Finesse','Impatience','Madness'] for k in ('any','attacks','skills','powers','colorless','common','uncommon','rare')}
        self.s['_combat_card_pools']=pools
        rng=Stream(self.s['_headless_rng']['streams']['card_random'])
        chosen=pools['colorless'][rng.bounded(3)]
        result=self.predict({'type':'END'})
        self.assertIn(chosen,[c['id'] for c in result['expected_after']['hand']])
        self.assertEqual(result['expected_exact_rng']['card_random'],rng.state())
