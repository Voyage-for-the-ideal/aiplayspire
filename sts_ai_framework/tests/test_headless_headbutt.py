"""Headbutt's dedicated in-flight discard-to-top boundary, without an engine."""
import copy
import json
import unittest
from uuid import UUID

from sts_ai_framework.headless.combat import (HeadlessCombatBridge, _headbutt_evidence,
    is_headbutt_selection, is_native_combat_selection, validate_selection_import, MODELLED)
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_combat import liquid_raw, combat_raw


def headbutt_raw():
    raw=liquid_raw();s=raw['combat']['snapshot'];p=s['player_state']
    pending=copy.deepcopy(p['hand'][0])
    pending.update(card_id='Headbutt',uuid=str(UUID(int=999)),cost=1,cost_for_turn=1,
                   base_damage=9,base_magic_number=-1,base_block=-1,target_enum_ordinal=0,
                   exhaust=False,purge_on_use=False,dont_trigger_on_use_card=False,
                   free_to_play_once=False,retain=False,_private={})
    p['card_in_use']=pending;p['limbo']=[]
    s['cards_played_this_turn_backup']=[copy.deepcopy(pending)]
    s['cards_played_this_combat']=[{'card_index':-1,'card_state':copy.deepcopy(pending)}]
    view=s['grid_card_select_screen_state']
    view['current_action_state']={'state_class':'savestate.actions.DiscardPileToTopOfDeckActionState','state':{'owner_index':-1}}
    view['action_queue']=[{'state_class':'savestate.actions.UseCardActionState','state':{
        'card':copy.deepcopy(pending),'targetIndex':0,'exhaustCard':False,'returnToHand':False,'reboundCard':False}}]
    raw['interaction']['selection']['purpose']='topdeck'
    return raw


def normal_headbutt_raw():
    raw=combat_raw();p=raw['combat']['snapshot']['player_state'];card=p['hand'][0]
    card.update(card_id='Headbutt',base_damage=9,base_block=-1,base_magic_number=-1,target_enum_ordinal=0,
                exhaust=False,purge_on_use=False,dont_trigger_on_use_card=False,free_to_play_once=False,retain=False,_private={})
    raw['player']['hand'][0].update(id='Headbutt',target='ENEMY')
    p['discard_pile']=p['draw_pile'];p['draw_pile']=[]
    raw['player']['discardPile']=raw['player']['drawPile'];raw['player']['drawPile']=[]
    return raw


def opened_headbutt_raw(before):
    """Synthetic one-card attack fixture; no native prediction is used as truth."""
    raw=copy.deepcopy(before);s=raw['combat']['snapshot'];p=s['player_state'];pending=p['hand'].pop(0)
    raw['player']['hand'].pop(0);p['energy_panel_total_energy']-=1;raw['player']['energy']-=1
    s['cur_map_node_state']['monster_data'][0]['creature']['current_health']-=9;raw['monsters'][0]['hp']-=9
    raw['phase']='CARD_SELECT';s.update(screen_name='GRID',is_screen_up=True)
    s['cards_played_this_turn_backup']=[copy.deepcopy(pending)]
    s['cards_played_this_combat']=[{'card_index':-1,'card_state':copy.deepcopy(pending)}]
    p['card_in_use']=pending
    template=headbutt_raw()
    raw['interaction']=template['interaction'];raw['cardSelection']={'kind':'grid','options':copy.deepcopy(raw['player']['discardPile']),'selected':[]}
    view=copy.deepcopy(template['combat']['snapshot']['grid_card_select_screen_state'])
    view['group_cards']=copy.deepcopy(p['discard_pile']);view['action_queue'][0]['state']['card']=copy.deepcopy(pending)
    s['grid_card_select_screen_state']=view
    raw['combat']['legal_actions']=[{'type':'GRID_SELECT','card_index':i,'uuid':c['uuid']} for i,c in enumerate(p['discard_pile'])]
    return raw


class HeadbuttBridgeTest(unittest.TestCase):
    def test_generic_menu_does_not_enumerate_native_headbutt(self):
        from sts_ai_framework.headless.interaction import IroncladInteractionBridge
        with self.assertRaisesRegex(HeadlessContractError,'Headbutt.*native combat'):
            IroncladInteractionBridge().observe(headbutt_raw(),run_id='fixture',sequence=2)

    def test_headbutt_is_a_native_boundary_with_exact_all_options(self):
        raw=headbutt_raw();self.assertTrue(is_headbutt_selection(raw));self.assertTrue(is_native_combat_selection(raw))
        frame=HeadlessCombatBridge().observe(raw,run_id='fixture',sequence=2)
        self.assertEqual(frame.state.screen_type,'GRID')
        self.assertEqual(len(frame.legal_json),3)
        self.assertEqual(_headbutt_evidence(frame.snapshot)['pending_card']['uuid'],str(UUID(int=999)))

    def test_unknown_action_queue_and_control_variants_are_rejected(self):
        for mutation in ('extra_action','wrong_action','card_queue','extra_current_field','wrong_owner','wrong_count','already_selected'):
            with self.subTest(mutation=mutation):
                raw=headbutt_raw();view=raw['combat']['snapshot']['grid_card_select_screen_state']
                if mutation=='extra_action':view['action_queue'].append({'state_class':'savestate.actions.GainEnergyActionState','state':{'energyGain':1}})
                if mutation=='wrong_action':view['action_queue'][0]['state_class']='savestate.actions.GainEnergyActionState'
                if mutation=='card_queue':view['card_queue_state']=[{}]
                if mutation=='extra_current_field':view['current_action_state']['state']['amount']=1
                if mutation=='wrong_owner':view['current_action_state']['state']['owner_index']=0
                if mutation=='wrong_count':view['num_cards']=2
                if mutation=='already_selected':view['selected_cards']=[view['group_cards'][0]]
                with self.assertRaises((HeadlessContractError,KeyError)):HeadlessCombatBridge().observe(raw,run_id='fixture',sequence=2)

    def test_pending_card_state_uuid_and_cleanup_flags_stay_bound(self):
        for mutation in ('stale_card','alias_uuid','exhaust','return','rebound','unknown_private','target'):
            with self.subTest(mutation=mutation):
                raw=headbutt_raw();s=raw['combat']['snapshot'];view=s['grid_card_select_screen_state'];data=view['action_queue'][0]['state']
                if mutation=='stale_card':data['card']['cost_for_turn']=0
                if mutation=='alias_uuid':
                    data['card']['uuid']=s['player_state']['discard_pile'][0]['uuid'];s['player_state']['card_in_use']['uuid']=data['card']['uuid']
                if mutation=='exhaust':data['exhaustCard']=True
                if mutation=='return':data['returnToHand']=True
                if mutation=='rebound':data['reboundCard']=True
                if mutation=='unknown_private':
                    data['card']['_private']={'Headbutt.unmapped':1};s['player_state']['card_in_use']['_private']={'Headbutt.unmapped':1}
                if mutation=='target':data['targetIndex']=9
                with self.assertRaises(HeadlessContractError):HeadlessCombatBridge().observe(raw,run_id='fixture',sequence=2)

    def test_ui_snapshot_uuid_or_legal_option_mismatch_is_rejected(self):
        for mutation in ('ui','legal'):
            raw=headbutt_raw()
            if mutation=='ui':raw['cardSelection']['options'][0]['uuid']=str(UUID(int=321))
            else:raw['combat']['legal_actions'].pop()
            with self.assertRaises(HeadlessContractError):HeadlessCombatBridge().observe(raw,run_id='fixture',sequence=2)

    def test_native_selection_import_binds_exact_rng_and_full_guard(self):
        from battle_engine.state import projection
        frame=HeadlessCombatBridge().observe(headbutt_raw(),run_id='fixture',sequence=2);s=frame.snapshot
        proof={'supported':True,'rng_restore_mode':'exact_state','projection':projection(s),'selection':_headbutt_evidence(s),
               'exact_rng':{k:s['_headless_rng']['streams'][k] for k in MODELLED},'selection_native_key':['full-state-fixture'],'native_key':None}
        validate_selection_import(frame,proof)
        for field in ('selection_native_key','selection','exact_rng'):
            invalid=copy.deepcopy(proof);invalid[field]=[]
            with self.assertRaises(HeadlessContractError):validate_selection_import(frame,invalid)
