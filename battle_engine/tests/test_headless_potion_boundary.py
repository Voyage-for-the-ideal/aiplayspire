"""Independent predictions must agree with an imported paused potion and next settled decision."""
import copy
import unittest

from battle_engine.tests import test_headless_rng as helpers
from sts_ai_framework.tests.test_headless_potion_boundary import REFRESH, HEAL, ELIXIR


@unittest.skipUnless(helpers.EXE.is_file(), 'Build the native worker')
class PotionContinuationTests(unittest.TestCase):
    setUp=helpers.HeadlessRngTests.setUp
    request=helpers.HeadlessRngTests.request
    gambler_snapshot=helpers.HeadlessRngTests.gambler_snapshot
    liquid_base=helpers.HeadlessRngTests.liquid_base
    liquid_snapshot=helpers.HeadlessRngTests.liquid_snapshot

    def predict(self,s,kind,**args):
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':dict(type=kind,**args)},1000)
        self.assertTrue(r['supported'],r);return r

    def boundary(self,potion,selected=()):
        if potion=='LiquidMemories':
            return self.liquid_snapshot(1,selected)
        s=self.gambler_snapshot(selected);h=s['hand_select_screen_state']
        if potion=='ElixirPotion':
            h.update(current_action_state=copy.deepcopy(ELIXIR),action_queue=[copy.deepcopy(REFRESH)])
        else:h['action_queue'].insert(0,copy.deepcopy(REFRESH))
        return s

    @staticmethod
    def toy(s):
        p=s['player_state'];p['creature']['current_health']=23
        p['relics'].append({'relic_id':'Toy Ornithopter','counter':-1,'_private':{}})
        view=s.get('hand_select_screen_state') or s.get('grid_card_select_screen_state')
        if view:view['action_queue'].insert(1,copy.deepcopy(HEAL))

    def test_pause_then_heal_exactly_once_on_commit(self):
        for potion in ('ElixirPotion','GamblersBrew','LiquidMemories'):
            with self.subTest(potion=potion):
                pre=self.liquid_base() if potion=='LiquidMemories' else copy.deepcopy(self.snapshot)
                pre['player_state']['potions'][0]['id']=potion;self.toy(pre)
                predicted=self.predict(pre,'POTION',potion_index=0,monster_index=-1)
                s=self.boundary(potion);self.toy(s);bound=self.request(s,'fingerprint')
                self.assertTrue(bound['supported'],bound)
                for expected,observed in (('expected_after','projection'),('expected_exact_rng','exact_rng'),
                         ('expected_selection_native_key','selection_native_key'),('expected_selection','selection')):
                    self.assertEqual(predicted[expected],bound[observed],expected)
                self.assertEqual(predicted['expected_after']['hp'],23)
                cmd='GRID_SELECT' if potion=='LiquidMemories' else 'HAND_SELECT_CONFIRM'
                result=self.predict(s,cmd,**({'card_index':0} if cmd=='GRID_SELECT' else {}))
                self.assertEqual(result['expected_screen'],'NONE')
                self.assertEqual(result['expected_after']['hp'],28)
                self.assertEqual(result['expected_exact_rng'],bound['exact_rng'])
                view=s.get('hand_select_screen_state') or s['grid_card_select_screen_state']
                for q in ([REFRESH],[HEAL,REFRESH],[REFRESH,HEAL,HEAL],
                          [REFRESH,dict(HEAL,state=dict(HEAL['state'],source_index=0))]):
                    view['action_queue']=q
                    self.assertFalse(self.request(s,'fingerprint')['supported'])

    def test_elixir_partial_selection_confirms_the_exact_uuids_without_discard_or_draw(self):
        s=self.boundary('ElixirPotion')
        predicted=self.predict(s,'HAND_SELECT',card_index=1)
        partial=self.boundary('ElixirPotion',(1,));actual=self.request(partial,'fingerprint')
        self.assertTrue(actual['supported'],actual)
        self.assertEqual(predicted['expected_after'],actual['projection'])
        self.assertEqual(predicted['expected_selection_native_key'],actual['selection_native_key'])
        self.assertEqual(predicted['expected_selection'],actual['selection'])
        uuid=s['player_state']['hand'][1]['uuid']
        self.assertEqual(actual['selection']['selected'],[uuid])
        done=self.predict(partial,'HAND_SELECT_CONFIRM')
        self.assertEqual(done['expected_card_uuids']['exhaust_pile'],[uuid])
        self.assertEqual(done['expected_card_uuids']['discard_pile'],[])
        self.assertEqual(done['expected_card_uuids']['hand'],[s['player_state']['hand'][0]['uuid']])
        self.assertEqual(done['expected_card_uuids']['draw_pile'],[c['uuid'] for c in s['player_state']['draw_pile']])
        self.assertEqual(done['expected_exact_rng'],actual['exact_rng'])
        view=partial['hand_select_screen_state']
        view['current_action_state']['state']['is_random']=True
        self.assertFalse(self.request(partial,'fingerprint')['supported'])

    def test_pellets_mask_and_trigger_are_part_of_state_key(self):
        s=copy.deepcopy(self.snapshot);p=s['player_state']
        flags={};p['relics'].append({'relic_id':'OrangePellets','counter':-1,'_private':flags})
        for mask in range(8):
            flags.update({f'OrangePellets.{k}':bool(mask & (1<<i)) for i,k in enumerate(('ATTACK','SKILL','POWER'))})
            f=self.request(s,'fingerprint')
            if mask==7:
                self.assertFalse(f['supported']);continue
            self.assertTrue(f['supported'],f);self.assertEqual(f['native_key'][4][38],mask)
            attack=self.predict(s,'CARD',card_index=0,monster_index=0)
            self.assertEqual(attack['expected_native_key'][4][38],0 if (mask|1)==7 else mask|1)
        flags.pop('OrangePellets.SKILL')
        self.assertFalse(self.request(s,'fingerprint')['supported'])

    def test_specimen_single_survivor_consumes_one_roll_only_when_victim_was_poisoned(self):
        for poisoned in (False,True):
            s=copy.deepcopy(self.snapshot);p=s['player_state'];p['relics'].append({'relic_id':'The Specimen','counter':-1,'_private':{}})
            monsters=s['cur_map_node_state']['monster_data'];monsters.append(copy.deepcopy(monsters[0]))
            victim=monsters[0]['creature'];victim['current_health']=1
            if poisoned:victim['powers'].append({'power_id':'Poison','amount':4})
            done=self.predict(s,'CARD',card_index=0,monster_index=0)
            self.assertEqual(done['expected_screen'],'NONE')
            living=next(row for row in done['expected_native_key'] if isinstance(row,list) and len(row)==30)
            self.assertEqual((living[1],living[19]),(1,4 if poisoned else 0))
            before=s['_headless_rng']['streams']['card_random']['counter']
            self.assertEqual(done['expected_exact_rng']['card_random']['counter'],before+int(poisoned))

    def test_top_finishes_shuffle_and_draw_before_next_legal_decision(self):
        s=copy.deepcopy(self.snapshot);p=s['player_state']
        p['relics'].append({'relic_id':'Unceasing Top','counter':-1,'_private':{'UnceasingTop.canDraw':True,'UnceasingTop.disabledUntilEndOfTurn':False}})
        p['hand']=p['hand'][1:];p['discard_pile']=p['draw_pile'];p['draw_pile']=[]
        done=self.predict(s,'CARD',card_index=0,monster_index=-1)
        self.assertEqual(done['expected_screen'],'NONE');self.assertIsInstance(done['expected_native_key'],list)
        self.assertEqual(len(done['expected_after']['hand']),1);self.assertEqual(done['expected_after']['discard_pile'],[])
        self.assertNotEqual(done['expected_exact_rng']['shuffle'],s['_headless_rng']['streams']['shuffle'])
        p['relics'][-1]['_private']['UnceasingTop.disabledUntilEndOfTurn']=True
        self.assertFalse(self.request(s,'fingerprint')['supported'])
