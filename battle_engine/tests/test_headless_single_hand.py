import copy
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.headless.event_receipts import Stream
from sts_ai_framework.tests.test_headless_combat import combat_raw
from sts_ai_framework.tests.test_headless_single_hand import sample


EXE=Path(os.environ.get('BATTLE_ENGINE_WORKER',Path(__file__).resolve().parents[1]/'bin/battle-engine-worker.exe'))

@unittest.skipUnless(EXE.is_file(),'Build the native worker')
class NativeSingleHand(unittest.TestCase):
    def setUp(self):
        self.w=Worker(EXE);self.addCleanup(self.w.close)

    def call(self,s,command=None):
        return self.w.execute({'snapshot':s,'operation':'fingerprint' if command is None else 'predict',
                               **({'command':command} if command else {})},1000)

    def test_each_click_matches_an_independently_imported_selected_frame(self):
        for source in ('True Grit','Dual Wield','Armaments'):
            with self.subTest(source=source):
                normal=sample(source);chosen=sample(source,True)
                view=chosen['hand_select_screen_state'];p=chosen['player_state'];uid=view['selected_cards'][0]['uuid']
                prediction=self.call(normal,{'type':'HAND_SELECT','card_index':0})
                fp=self.call(chosen)
                self.assertTrue(prediction['supported'] and fp['supported'],(prediction,fp))
                for expected,actual in [('expected_after','projection'),('expected_selection','selection'),
                                        ('expected_selection_native_key','selection_native_key'),('expected_exact_rng','exact_rng')]:
                    self.assertEqual(prediction[expected],fp[actual])
                final=self.call(chosen,{'type':'HAND_SELECT_CONFIRM'})
                self.assertTrue(final['supported']);self.assertEqual(final['expected_screen'],'NONE')
                self.assertEqual(final['expected_exact_rng'],fp['exact_rng'])
                line=final['expected_card_uuids']
                self.assertEqual(line['discard_pile'],[p['card_in_use']['uuid']])
                if source=='True Grit':self.assertEqual(line['exhaust_pile'],[uid])
                if source=='Dual Wield':self.assertEqual(line['hand'],[p['hand'][0]['uuid'],view['current_action_state']['state']['hidden'][0]['uuid'],None,None])
                if source=='Armaments':self.assertEqual((line['hand'][-1],final['expected_after']['hand'][-1]['upgrades']),(uid,1))

    def test_corruption_exhausts_the_pending_skill_after_confirmation(self):
        s=sample('Armaments',True);p=s['player_state'];h=s['hand_select_screen_state']
        p['creature']['powers'].append({'power_id':'Corruption','amount':-1})
        h['action_queue'][0]['state']['exhaustCard']=True
        before=self.call(s);self.assertTrue(before['supported'],before)
        self.assertTrue(before['selection']['pending_card']['exhaust'])
        done=self.call(s,{'type':'HAND_SELECT_CONFIRM'});self.assertTrue(done['supported'],done)
        self.assertEqual(done['expected_card_uuids']['exhaust_pile'],[p['card_in_use']['uuid']])
        self.assertEqual(done['expected_card_uuids']['discard_pile'],[])

    def test_forethought_places_chosen_cards_at_bottom_in_click_order(self):
        s=sample('Forethought');p=s['player_state'];h=s['hand_select_screen_state']
        c=p['card_in_use'];c.update(times_upgraded=1,cost=0,cost_for_turn=0)
        h['action_queue'][0]['state']['card']=copy.deepcopy(c)
        h.update(any_number=True,can_pick_zero=True,num_cards_to_select=99)
        p['hand'][0]['cost_for_turn']=0
        self.assertEqual(self.call(s,{'type':'HAND_SELECT_CONFIRM'})['expected_card_uuids']['draw_pile'],[])
        for _ in range(2):
            selected=self.call(s,{'type':'HAND_SELECT','card_index':0})
            self.assertTrue(selected['supported'],selected)
            h['selected_cards'].append(p['hand'].pop(0))
            imported=self.call(s)
            self.assertEqual(selected['expected_selection'],imported['selection'])
            self.assertEqual(selected['expected_selection_native_key'],imported['selection_native_key'])
        done=self.call(s,{'type':'HAND_SELECT_CONFIRM'})
        self.assertTrue(done['supported'],done)
        self.assertEqual(done['expected_card_uuids']['draw_pile'],[c['uuid'] for c in reversed(h['selected_cards'])])
        self.assertTrue(all(c['free'] and c['cost']==c['turn_cost'] for c in done['expected_after']['draw_pile']))
        h['can_pick_zero']=False
        self.assertFalse(self.call(s)['supported'])

    def test_reject_wrong_hidden_card_or_out_of_order_cleanup(self):
        for mutation in ('hidden','queue','copy','early'):
            s=sample();h=s['hand_select_screen_state'];data=h['current_action_state']['state']
            if mutation=='hidden':data['hidden'][0]['card_id']='Strike_R'
            if mutation=='queue':h['action_queue'].insert(0,copy.deepcopy(h['action_queue'][0]))
            if mutation=='copy':data['copies']=2
            r=self.call(s,{'type':'HAND_SELECT_CONFIRM'}) if mutation=='early' else self.call(s)
            self.assertFalse(r['supported'],mutation)

    def test_autoplay_continuation_binds_order_and_card_identities(self):
        from uuid import UUID
        from sts_ai_framework.headless.combat import _single_hand_evidence
        s=sample('Armaments',True);p=s['player_state'];h=s['hand_select_screen_state']
        cards=[dict(p['hand'][0],card_id='Strike_R',uuid=str(UUID(int=i)),cost=1,cost_for_turn=1,
                    purge_on_use=False,dont_trigger_on_use_card=False,free_to_play_once=False,retain=False)
               for i in (950,951)]
        h['card_queue_state']=[dict(card=c,monster_index=0,energy_on_use=2,ignore_energy_total=False,
                                    autoplay_card=True,random_target=False) for c in cards]
        before=self.call(s);self.assertTrue(before['supported'],before)
        self.assertEqual(before['selection'],_single_hand_evidence(s))
        after=self.call(s,{'type':'HAND_SELECT_CONFIRM'})
        self.assertTrue(after['supported'],after)
        self.assertEqual(after['expected_card_uuids']['discard_pile'][-2:],[c['uuid'] for c in cards])
        h['card_queue_state'][1]['card']['uuid']=cards[0]['uuid']
        self.assertFalse(self.call(s)['supported'])

    def test_burning_pact_and_ink_bottle_draw_only_on_confirmation(self):
        from uuid import UUID
        for source,kind,amount in [('Burning Pact','burning_pact',2),('True Grit','ink_bottle',1)]:
            with self.subTest(source=source):
                s=sample(source,True);p=s['player_state'];h=s['hand_select_screen_state']
                p['draw_pile']=[dict(p['hand'][0],uuid=str(UUID(int=i))) for i in (851,852)]
                if kind=='ink_bottle':p['relics'].append({'relic_id':'InkBottle','counter':0,'_private':{}})
                h['action_queue'].insert(0,{'state_class':'savestate.actions.SelectionDrawActionState',
                                           'state':{'kind':kind,'amount':amount}})
                fp=self.call(s);self.assertTrue(fp['supported'],fp)
                drawn=[c['uuid'] for c in p['draw_pile']][-amount:][::-1]
                out=self.call(s,{'type':'HAND_SELECT_CONFIRM'});self.assertTrue(out['supported'],out)
                self.assertEqual(out['expected_card_uuids']['hand'][-amount:],drawn)
                self.assertEqual(out['expected_card_uuids']['exhaust_pile'],[h['selected_cards'][0]['uuid']])
                self.assertEqual(out['expected_exact_rng'],fp['exact_rng'])
                h['action_queue'][0]['state']['amount']+=1
                self.assertFalse(self.call(s)['supported'])

    def test_blocking_skill_keeps_queued_juggernaut_after_cleanup_until_confirmation(self):
        for source,block in (('True Grit',9),('Armaments',5)):
            with self.subTest(source=source):
                s=sample(source,True);p=s['player_state'];q=s['hand_select_screen_state']['action_queue']
                p['card_in_use']['block']=block;q[0]['state']['card']['block']=block
                p['creature']['powers'].append({'power_id':'Juggernaut','amount':5})
                q.append({'state_class':'savestate.actions.SelectionJuggernautActionState','state':{'amount':5}})
                fp=self.call(s);self.assertTrue(fp['supported'],fp)
                self.assertIn(['selection-juggernaut-v1',5],fp['selection_native_key'])
                out=self.call(s,{'type':'HAND_SELECT_CONFIRM'});self.assertTrue(out['supported'],out)
                rng=Stream(fp['exact_rng']['card_random']);rng.bounded(1)
                self.assertEqual(out['expected_exact_rng']['card_random'],rng.state())
                self.assertEqual(out['expected_after']['monsters'][0]['hp'],fp['projection']['monsters'][0]['hp']-5)
                q.reverse()
                self.assertFalse(self.call(s)['supported'])

    def test_orange_pellets_purge_waits_for_confirmation(self):
        s=sample('Armaments',True);p=s['player_state'];q=s['hand_select_screen_state']['action_queue']
        p['relics'].append({'relic_id':'OrangePellets','counter':-1,'_private':{
            'OrangePellets.ATTACK':False,'OrangePellets.SKILL':False,'OrangePellets.POWER':False}})
        p['creature']['powers'].append({'power_id':'Weakened','amount':2})
        q.insert(0,{'state_class':'savestate.actions.SelectionOrangePelletsActionState','state':{}})
        fp=self.call(s);self.assertTrue(fp['supported'],fp)
        self.assertIn(['selection-orange-pellets-v1'],fp['selection_native_key'])
        out=self.call(s,{'type':'HAND_SELECT_CONFIRM'});self.assertTrue(out['supported'],out)
        q.pop(0)
        self.assertFalse(self.call(s)['supported'])

    def test_discovery_uses_ordered_any_pool_not_potion_colorless_pool(self):
        s=combat_raw()['combat']['snapshot'];p=s['player_state'];c=p['hand'][0]
        c.update(card_id='Discovery',cost=1,cost_for_turn=1,base_damage=-1,base_block=-1,base_magic_number=-1,target_enum_ordinal=2,exhaust=True)
        pool=['Anger','Cleave','Clothesline','Battle Trance','Havoc']
        s['_combat_card_pools']={k:pool for k in ('any','attacks','skills','powers','common','uncommon','rare')}
        s['_combat_card_pools']['colorless']=['Flash of Steel','Discovery','Blind']
        r=self.call(s,{'type':'CARD','card_index':0,'monster_index':-1})
        self.assertTrue(r['supported'],r);self.assertEqual(r['expected_screen'],'CARD_REWARD')
        rng=Stream(s['_headless_rng']['streams']['card_random']);expected=[]
        while len(expected)<3:
            c=pool[rng.bounded(len(pool))]
            if c not in expected:expected.append(c)
        self.assertEqual(r['expected_discovery'],{'offers':expected,'copies':1})
        self.assertEqual(r['expected_exact_rng']['card_random'],rng.state())
