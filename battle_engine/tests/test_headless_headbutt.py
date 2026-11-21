"""Native continuation/RNG regression; no Java process or real game is started."""
import copy
import json
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.tests.test_headless_headbutt import headbutt_raw, normal_headbutt_raw, opened_headbutt_raw
from sts_ai_framework.tests.test_headless_combat import combat_raw

EXE=Path(os.environ.get('BATTLE_ENGINE_WORKER',Path(__file__).resolve().parents[1]/'bin/battle-engine-worker.exe'))


@unittest.skipUnless(EXE.is_file(),'Build native worker')
class NativeHeadbuttTest(unittest.TestCase):
    def setUp(self):
        self.worker=Worker(EXE);self.addCleanup(self.worker.close)

    def call(self,s,operation='fingerprint',**arguments):
        result=self.worker.execute({'snapshot':s,'operation':operation,**arguments},2000)
        return result

    def test_import_and_complete_headbutt_moves_exact_uuid_and_cleans_up_once(self):
        s=headbutt_raw()['combat']['snapshot'];p=s['player_state'];cards=p['discard_pile'];pending=p['card_in_use']
        fp=self.call(s);self.assertTrue(fp['supported'],fp)
        self.assertEqual(fp['selection']['schema'],'headless-headbutt-selection-v1')
        self.assertTrue(fp['selection_native_key']);self.assertIsNone(fp['native_key'])
        after=self.call(s,'predict',command={'type':'GRID_SELECT','card_index':1})
        self.assertTrue(after['supported'],after)
        self.assertEqual(after['expected_screen'],'NONE');self.assertIsNone(after['expected_selection'])
        self.assertEqual(after['expected_card_uuids']['draw_pile'][-1],cards[1]['uuid'])
        self.assertEqual(after['expected_card_uuids']['discard_pile'],[cards[0]['uuid'],cards[2]['uuid'],pending['uuid']])
        self.assertEqual(after['expected_exact_rng'],fp['exact_rng'])

    def test_headbutt_source_controls_and_unknown_cleanup_fail_import(self):
        for mutation in ('unknown_action','wrong_card','exhaust','duplicate_pending','missing_pending'):
            s=headbutt_raw()['combat']['snapshot'];q=s['grid_card_select_screen_state']['action_queue'][0]
            if mutation=='unknown_action':q['state_class']='savestate.actions.GainEnergyActionState'
            if mutation=='wrong_card':q['state']['card']['card_id']='Strike_R'
            if mutation=='exhaust':q['state']['exhaustCard']=True
            if mutation=='duplicate_pending':
                q['state']['card']['uuid']=s['player_state']['discard_pile'][0]['uuid'];s['player_state']['card_in_use']['uuid']=q['state']['card']['uuid']
            if mutation=='missing_pending':s['player_state']['card_in_use']=None
            result=self.call(s)
            self.assertFalse(result['supported'],(mutation,result))

    def test_pending_cleanup_cost_binds_guard_despite_identical_public_projection(self):
        first=headbutt_raw()['combat']['snapshot']
        third=copy.deepcopy(first);third['player_state']['card_in_use']['cost_for_turn']=0
        third['grid_card_select_screen_state']['action_queue'][0]['state']['card']['cost_for_turn']=0
        a=self.call(first);b=self.call(third)
        self.assertTrue(a['supported'] and b['supported'],(a,b))
        self.assertEqual(a['projection'],b['projection']);self.assertNotEqual(a['selection_native_key'],b['selection_native_key'])

    def test_normal_headbutt_opens_dedicated_grid_without_claiming_a_post_choice(self):
        raw=normal_headbutt_raw();s=raw['combat']['snapshot'];p=s['player_state'];card=p['hand'][0]
        result=self.call(s,'predict',command={'type':'CARD','card_index':0,'monster_index':0})
        self.assertTrue(result['supported'],result)
        self.assertEqual(result['expected_screen'],'GRID');self.assertIn('expected_after',result)
        self.assertEqual(result['expected_selection']['schema'],'headless-headbutt-selection-v1')
        self.assertEqual(result['expected_selection']['pending_card']['uuid'],card['uuid'])
        self.assertEqual(result['expected_card_uuids']['discard_pile'],[c['uuid'] for c in p['discard_pile']])
        self.assertIsNone(result['expected_native_key']);self.assertTrue(result['expected_selection_native_key'])

    def test_card_to_grid_effect_compares_native_full_guard_and_all_rng(self):
        from sts_ai_framework.headless.combat import HeadlessCombatBridge, verify_combat_effect
        raw=normal_headbutt_raw();bridge=HeadlessCombatBridge();left=bridge.observe(raw,run_id='synthetic',sequence=0)
        prediction=self.call(left.snapshot,'predict',command={'type':'CARD','card_index':0,'monster_index':0})
        plan=bridge.prepare(left,prediction)
        after=bridge.observe(opened_headbutt_raw(raw),run_id='synthetic',sequence=1);fp=self.call(after.snapshot)
        self.assertEqual(verify_combat_effect(plan,after,fp),'APPLIED')
        changed=copy.deepcopy(fp);changed['selection_native_key'][-1][-1]=True
        self.assertEqual(verify_combat_effect(plan,after,changed),'UNVERIFIED')

    def test_random_target_attack_emits_source_nontargeted_api_without_skipping_rng(self):
        s=combat_raw()['combat']['snapshot'];c=s['player_state']['hand'][0]
        c.update(card_id='Rip and Tear',target_enum_ordinal=1,base_damage=7,base_magic_number=2)
        result=self.call(s,'predict',command={'type':'CARD','card_index':0,'monster_index':-1})
        self.assertTrue(result['supported'],result)
        self.assertEqual(json.loads(result['commands'][0]['command'])['monster_index'],-1)
        before=s['_headless_rng']['streams']['card_random']['counter']
        self.assertEqual(result['expected_exact_rng']['card_random']['counter'],before+2)

    def test_headbutt_dead_original_target_keeps_exact_source_slot(self):
        s=headbutt_raw()['combat']['snapshot'];monsters=s['cur_map_node_state']['monster_data']
        living=copy.deepcopy(monsters[0]);monsters.append(living);monsters[0]['creature']['current_health']=0
        fp=self.call(s);self.assertTrue(fp['supported'],fp)
        self.assertEqual(fp['selection']['pending_card']['target'],0)
        result=self.call(s,'predict',command={'type':'GRID_SELECT','card_index':0})
        self.assertTrue(result['supported'],result);self.assertEqual(result['expected_screen'],'NONE')

    def test_one_card_discard_auto_moves_and_resets_temporary_cost(self):
        raw=normal_headbutt_raw();s=raw['combat']['snapshot'];p=s['player_state']
        p['discard_pile']=p['discard_pile'][:1];chosen=p['discard_pile'][0];chosen['cost_for_turn']=0
        result=self.call(s,'predict',command={'type':'CARD','card_index':0,'monster_index':0})
        self.assertTrue(result['supported'],result);self.assertEqual(result['expected_screen'],'NONE')
        self.assertEqual(result['expected_card_uuids']['draw_pile'],[chosen['uuid']])
        self.assertEqual(result['expected_after']['draw_pile'][-1]['turn_cost'],chosen['cost'])
