"""Interrupted Headbutt: restore real post-cleanup effects, including a same-UUID replay."""
import copy
import unittest

from battle_engine.tests import test_headless_rng as helpers
from sts_ai_framework.tests.test_headless_headbutt import normal_headbutt_raw, opened_headbutt_raw
from sts_ai_framework.headless.combat import _headbutt_evidence, _private_invariants
from sts_ai_framework.headless.state import HeadlessContractError


def double_boundary():
    raw=normal_headbutt_raw();p=raw['combat']['snapshot']['player_state']
    p['creature']['powers'].append({'power_id':'Double Tap','amount':1})
    s=opened_headbutt_raw(raw)['combat']['snapshot'];p=s['player_state'];v=s['grid_card_select_screen_state']
    p['creature']['powers'][-1]['amount']=0
    clone=copy.deepcopy(p['card_in_use']);clone['purge_on_use']=True;p['limbo']=[clone]
    v['action_queue'].insert(0,{'state_class':'savestate.actions.RemoveSpecificPowerActionState','state':{'ownerIndex':-1,'powerId':'Double Tap'}})
    v['card_queue_state']=[{'card':copy.deepcopy(clone),'monster_index':0,'energy_on_use':3,'ignore_energy_total':True,'autoplay_card':True,'random_target':False}]
    return raw['combat']['snapshot'],s


def second_double_boundary(first):
    s=copy.deepcopy(first);p=s['player_state'];v=s['grid_card_select_screen_state']
    original=p['card_in_use'];clone=p['limbo'][0];p['card_in_use']=clone
    s['cur_map_node_state']['monster_data'][0]['creature']['current_health']-=9
    p['creature']['powers']=[];p['draw_pile'].append(p['discard_pile'].pop(0));p['discard_pile'].append(original)
    v['group_cards']=copy.deepcopy(p['discard_pile']);v['card_queue_state']=[];v['action_queue'].pop(0)
    v['action_queue'][0]['state']['card']=copy.deepcopy(clone)
    s['cards_played_this_turn_backup'].append(copy.deepcopy(clone))
    s['cards_played_this_combat'].append({'card_index':-1,'card_state':copy.deepcopy(clone)})
    return s


class HeadbuttQueueGuardTest(unittest.TestCase):
    def test_wrong_replay_queue_order_or_target_or_copy_uuid_rejected(self):
        _,s=double_boundary();_headbutt_evidence(s);_headbutt_evidence(second_double_boundary(s))
        for change in (
            lambda v:v['action_queue'].reverse(),
            lambda v:v['card_queue_state'][0].update(monster_index=1),
            lambda v:v['card_queue_state'][0]['card'].update(uuid='stale-copy'),
            lambda v:v['card_queue_state'][0].update(random_target=True)):
            bad=copy.deepcopy(s);change(bad['grid_card_select_screen_state'])
            with self.assertRaises(HeadlessContractError):_headbutt_evidence(bad)

    def test_stacked_double_tap_keeps_remaining_charge_without_removal_action(self):
        _,s=double_boundary();p=s['player_state'];v=s['grid_card_select_screen_state']
        p['creature']['powers'][-1]['amount']=1
        v['action_queue'].pop(0)
        _headbutt_evidence(s)
        v['action_queue'].insert(0,{'state_class':'savestate.actions.RemoveSpecificPowerActionState',
            'state':{'ownerIndex':-1,'powerId':'Double Tap'}})
        with self.assertRaises(HeadlessContractError):_headbutt_evidence(s)

    def test_guardian_pending_transition_keys_on_slot_not_pixel_x(self):
        s=opened_headbutt_raw(normal_headbutt_raw())['combat']['snapshot'];m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='TheGuardian',draw_x=926.6667,powers=[{'power_id':'Mode Shift','amount':-8}])
        m['_private']={'TheGuardian.isOpen':True,'TheGuardian.closeUpTriggered':True,'TheGuardian.dmgTaken':0,'TheGuardian.dmgThreshold':40}
        tail={'state_class':'savestate.actions.HeadbuttMonsterTailState','state':{'kind':'guardian_defensive_mode','owner_index':0}}
        s['grid_card_select_screen_state']['action_queue'].append(tail)
        _headbutt_evidence(s);_private_invariants(s)
        tail['state']['owner_index']=1
        with self.assertRaises(HeadlessContractError):_private_invariants(s)


@unittest.skipUnless(helpers.EXE.is_file(),'Build native worker')
class NativeHeadbuttQueueTest(unittest.TestCase):
    setUp=helpers.HeadlessRngTests.setUp
    request=helpers.HeadlessRngTests.request

    def run_action(self,s,c):
        r=self.worker.execute(dict(operation='predict',snapshot=s,command=c),1000);self.assertTrue(r['supported'],r);return r

    def compare(self,pred,actual):
        f=self.request(actual,'fingerprint');self.assertTrue(f['supported'],f)
        for expected,observed in (('expected_after','projection'),('expected_selection','selection'),('expected_exact_rng','exact_rng'),
                                  ('expected_selection_native_key','selection_native_key')):
            self.assertEqual(pred[expected],f[observed],expected)

    def test_cleanup_keeps_the_defeated_external_target_slot(self):
        s=opened_headbutt_raw(normal_headbutt_raw())['combat']['snapshot']
        monsters=s['cur_map_node_state']['monster_data']
        defeated=copy.deepcopy(monsters[0])
        defeated['creature'].update(current_health=0,is_dead=True,is_dying=True)
        monsters.insert(0,defeated)
        probe=self.request(s,'fingerprint')
        self.assertTrue(probe['supported'],probe)
        self.assertEqual(probe['selection']['pending_card']['target'],0)
        picked=self.run_action(s,{'type':'GRID_SELECT','card_index':0})
        self.assertEqual(picked['expected_screen'],'NONE')
        s['grid_card_select_screen_state']['action_queue'][-1]['state']['targetIndex']=2
        self.assertFalse(self.request(s,'fingerprint')['supported'])

    def test_rage_block_waits_for_choice_and_binds_power_amount(self):
        raw=normal_headbutt_raw();s=raw['combat']['snapshot'];p=s['player_state']
        p['creature']['powers'].append({'power_id':'Rage','amount':5})
        first=self.run_action(s,{'type':'CARD','card_index':0,'monster_index':0})
        paused=opened_headbutt_raw(raw)['combat']['snapshot']
        q=paused['grid_card_select_screen_state']['action_queue']
        q.insert(0,{'state_class':'savestate.actions.HeadbuttRageBlockActionState',
                    'state':{'owner_index':-1,'amount':5}})
        _headbutt_evidence(paused)
        self.compare(first,paused)
        done=self.run_action(paused,{'type':'GRID_SELECT','card_index':0})
        self.assertEqual(done['expected_after']['block'],first['expected_after']['block']+5)
        q[0]['state']['amount']=4
        with self.assertRaises(HeadlessContractError):_headbutt_evidence(paused)
        self.assertFalse(self.request(paused,'fingerprint')['supported'])

    def test_plated_reduction_executes_after_choice_not_on_first_hit(self):
        raw=normal_headbutt_raw();s=raw['combat']['snapshot'];m=s['cur_map_node_state']['monster_data'][0]
        m['creature']['powers'].append({'power_id':'Plated Armor','amount':5})
        first=self.run_action(s,{'type':'CARD','card_index':0,'monster_index':0})
        paused=opened_headbutt_raw(raw)['combat']['snapshot'];v=paused['grid_card_select_screen_state']
        tail={'state_class':'savestate.actions.HeadbuttMonsterTailState','state':{'kind':'plated_armor_minus_one','owner_index':0}}
        v['action_queue'].append(tail);self.compare(first,paused)
        done=self.run_action(paused,{'type':'GRID_SELECT','card_index':0})
        mkey=next(r for r in done['expected_native_key'] if isinstance(r,list) and len(r)==30 and r[1]==0)
        self.assertEqual(mkey[18],4)
        tail['state']['owner_index']=1
        self.assertFalse(self.request(paused,'fingerprint')['supported'])

    def test_darkling_queued_regrow_repeats_move_history_after_choice(self):
        paused=opened_headbutt_raw(normal_headbutt_raw())['combat']['snapshot'];m=paused['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='Darkling',current_health=0,is_dead=False,is_dying=False,half_dead=True)
        m.update(next_move=4,move_history=[2,4],intent_name='UNKNOWN',move_base_damage=-1,
                 move_adjusted_damage=-1,move_hits=1,_private={
                     'Darkling.chompDmg':9,'Darkling.firstMove':False,'Darkling.nipDmg':10})
        tail={'state_class':'savestate.actions.HeadbuttMonsterTailState',
              'state':{'kind':'darkling_repeat_regrow','owner_index':0}}
        mqueue=paused['grid_card_select_screen_state']['action_queue'];mqueue.append(tail)
        self.assertTrue(self.request(paused,'fingerprint')['supported'])
        done=self.run_action(paused,{'type':'GRID_SELECT','card_index':0})
        darkling=next(row for row in done['expected_native_key'] if isinstance(row,list) and len(row)==30 and row[1]==0)
        self.assertEqual(darkling[8],darkling[9])
        tail['state']['kind']='darkling_repeat_unknown'
        self.assertFalse(self.request(paused,'fingerprint')['supported'])

    def test_shuriken_and_kunai_wait_for_grid_with_exact_effects(self):
        raw=normal_headbutt_raw();s=raw['combat']['snapshot'];p=s['player_state']
        p['relics'].extend([{'relic_id':relic,'counter':2} for relic in ('Shuriken','Kunai')])
        prior=copy.deepcopy(p['discard_pile'][:2])
        for c in prior:c['card_id']='Strike_R'
        s['cards_played_this_turn_backup']=prior
        first=self.run_action(s,{'type':'CARD','card_index':0,'monster_index':0})
        paused=opened_headbutt_raw(raw)['combat']['snapshot'];p=paused['player_state']
        paused['cards_played_this_turn_backup']=prior+[p['card_in_use']]
        for relic in p['relics'][-2:]:relic['counter']=0
        q=paused['grid_card_select_screen_state']['action_queue']
        q[:0]=[{'state_class':f'savestate.actions.Headbutt{relic}ActionState','state':{'owner_index':-1,'amount':1}}
               for relic in ('Shuriken','Kunai')]
        self.compare(first,paused)
        done=self.run_action(paused,{'type':'GRID_SELECT','card_index':0})
        self.assertEqual(done['expected_screen'],'NONE')
        q[0],q[1]=q[1],q[0]
        self.assertTrue(self.request(paused,'fingerprint')['supported'])
        q[0]['state']['amount']=2
        self.assertFalse(self.request(paused,'fingerprint')['supported'])

    def test_pen_nib_remove_and_attack_relic_followups_are_bound(self):
        raw=normal_headbutt_raw();s=raw['combat']['snapshot'];p=s['player_state']
        prior=copy.deepcopy(p['discard_pile'][:2])
        for c in prior:c['card_id']='Strike_R'
        s['cards_played_this_turn_backup']=prior
        p['relics'].extend([
            {'relic_id':'Pen Nib','counter':8},
            {'relic_id':'Nunchaku','counter':9},
            {'relic_id':'Ornamental Fan','counter':2},
        ])
        first=self.run_action(s,{'type':'CARD','card_index':0,'monster_index':0})
        paused=opened_headbutt_raw(raw)['combat']['snapshot'];pp=paused['player_state']
        paused['cards_played_this_turn_backup']=prior+[pp['card_in_use']]
        for relic,counter in zip(pp['relics'][-3:],(9,0,0)):relic['counter']=counter
        q=paused['grid_card_select_screen_state']['action_queue']
        q[:0]=[
            {'state_class':'savestate.actions.HeadbuttPenNibActionState','state':{'owner_index':-1,'amount':1}},
            {'state_class':'savestate.actions.HeadbuttNunchakuActionState','state':{'amount':1}},
            {'state_class':'savestate.actions.HeadbuttOrnamentalFanActionState','state':{'amount':4}},
        ]
        self.compare(first,paused)
        done=self.run_action(paused,{'type':'GRID_SELECT','card_index':0})
        self.assertEqual(done['expected_after']['energy'],first['expected_after']['energy']+1)
        self.assertEqual(done['expected_after']['block'],first['expected_after']['block']+4)
        q[0]['state']['amount']=2
        self.assertFalse(self.request(paused,'fingerprint')['supported'])

        active=opened_headbutt_raw(normal_headbutt_raw())['combat']['snapshot'];ap=active['player_state']
        ap['relics'].append({'relic_id':'Pen Nib','counter':9})
        ap['creature']['powers'].append({'power_id':'Pen Nib','amount':1})
        active['grid_card_select_screen_state']['action_queue'].insert(0,{
            'state_class':'savestate.actions.RemoveSpecificPowerActionState',
            'state':{'ownerIndex':-1,'powerId':'Pen Nib'}})
        self.assertTrue(self.request(active,'fingerprint')['supported'])
        active['grid_card_select_screen_state']['action_queue'][0]['state']['powerId']='Vigor'
        self.assertFalse(self.request(active,'fingerprint')['supported'])

    def test_double_tap_binds_two_real_choices_and_does_not_discard_the_copy(self):
        s,opened=double_boundary()
        first=self.run_action(s,{'type':'CARD','card_index':0,'monster_index':0});self.compare(first,opened)
        playing=second_double_boundary(opened)
        second=self.run_action(opened,{'type':'GRID_SELECT','card_index':0});self.compare(second,playing)
        done=self.run_action(playing,{'type':'GRID_SELECT','card_index':0})
        self.assertEqual(done['expected_screen'],'NONE')
        self.assertEqual(done['expected_card_uuids']['discard_pile'],[opened['player_state']['card_in_use']['uuid']])
        self.assertEqual(done['expected_exact_rng'],second['expected_exact_rng'])
        bad=copy.deepcopy(opened);bad['grid_card_select_screen_state']['action_queue'].reverse()
        self.assertFalse(self.request(bad,'fingerprint')['supported'])

    def test_stacked_double_tap_keeps_one_charge_at_the_first_grid(self):
        s,opened=double_boundary()
        s['player_state']['creature']['powers'][-1]['amount']=2
        opened['player_state']['creature']['powers'][-1]['amount']=1
        opened['grid_card_select_screen_state']['action_queue'].pop(0)
        first=self.run_action(s,{'type':'CARD','card_index':0,'monster_index':0})
        self.compare(first,opened)

    def test_flight_reduction_waits_for_grid_then_grounds_byrd(self):
        raw=normal_headbutt_raw();s=raw['combat']['snapshot'];m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='Byrd',powers=[{'power_id':'Flight','amount':1,'_private':{'FlightPower.storedAmount':3}}])
        m.update(next_move=1,move_history=[1],intent_name='ATTACK',move_base_damage=1,move_adjusted_damage=1,move_hits=6)
        first=self.run_action(s,{'type':'CARD','card_index':0,'monster_index':0})
        paused=opened_headbutt_raw(raw)['combat']['snapshot'];v=paused['grid_card_select_screen_state']
        paused['cur_map_node_state']['monster_data'][0]['creature']['current_health']+=5 # 9 damage halves to 4
        tail={'state_class':'savestate.actions.HeadbuttMonsterTailState','state':{'kind':'flight_minus_one','owner_index':0}}
        v['action_queue'].append(tail);self.compare(first,paused)
        done=self.run_action(paused,{'type':'GRID_SELECT','card_index':0})
        self.assertEqual(done['expected_screen'],'NONE')
        grounded=copy.deepcopy(paused);gm=grounded['cur_map_node_state']['monster_data'][0]
        gm.update(next_move=4,move_history=[1,4],intent_name='STUN',move_base_damage=-1,move_adjusted_damage=-1,move_hits=1)
        gm['creature']['powers']=[]
        grounded['screen_name']='NONE';grounded['is_screen_up']=False
        p=grounded['player_state'];p['discard_pile'].append(p['card_in_use']);p['card_in_use']=None
        p['draw_pile'].append(p['discard_pile'].pop(0))
        fp=self.request(grounded,'fingerprint');self.assertTrue(fp['supported'],fp)
        self.assertEqual(done['expected_exact_rng'],fp['exact_rng'])
        self.assertEqual(done['expected_native_key'],fp['native_key'])
        tail['state']['owner_index']=1
        self.assertFalse(self.request(paused,'fingerprint')['supported'])

    def test_sharp_hide_damage_waits_for_grid_then_hits_once(self):
        raw=normal_headbutt_raw();s=raw['combat']['snapshot'];m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='TheGuardian',powers=[{'power_id':'Sharp Hide','amount':3}])
        m.update(next_move=3,move_history=[1],intent_name='ATTACK',move_base_damage=10,move_adjusted_damage=10,
                 _private={'TheGuardian.dmgThreshold':40})
        first=self.run_action(s,{'type':'CARD','card_index':0,'monster_index':0})
        paused=opened_headbutt_raw(raw)['combat']['snapshot'];v=paused['grid_card_select_screen_state']
        sharp={'state_class':'savestate.actions.HeadbuttSharpHideActionState','state':{'owner_index':0,'target_index':-1,'amount':3}}
        v['action_queue'].insert(0,sharp);self.compare(first,paused)
        done=self.run_action(paused,{'type':'GRID_SELECT','card_index':0})
        self.assertEqual(done['expected_after']['hp'],first['expected_after']['hp']-3)
        self.assertEqual(done['expected_exact_rng'],first['expected_exact_rng'])
        sharp['state']['amount']=4
        self.assertFalse(self.request(paused,'fingerprint')['supported'])
