"""Native exact-state restore keeps all 64 bits and never guesses from counters."""
import copy
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.tests.test_headless_combat import combat_raw

ROOT = Path(__file__).resolve().parents[1]
EXE = Path(os.environ.get("BATTLE_ENGINE_WORKER", ROOT / "bin/battle-engine-worker.exe"))


@unittest.skipUnless(EXE.is_file(), "Build the native worker")
class HeadlessRngTests(unittest.TestCase):
    def setUp(self):
        self.worker = Worker(EXE); self.addCleanup(self.worker.close)
        self.snapshot = combat_raw()["combat"]["snapshot"]

    def request(self, s=None, operation="rng_probe"):
        return self.worker.execute({"snapshot": s or self.snapshot, "operation": operation}, 1000)

    def gambler_snapshot(self, selected=()):
        s=copy.deepcopy(self.snapshot);p=s['player_state']
        cards=p['hand'];chosen=[cards[i] for i in selected]
        p['hand']=[c for i,c in enumerate(cards) if i not in selected]
        p['potions'][0]['id']='Potion Slot'
        s.update(screen_name='HAND_SELECT',is_screen_up=True)
        s['hand_select_screen_state']={'num_cards_to_select':99,'selected_cards':chosen,'any_number':True,
            'can_pick_zero':True,'were_cards_retrieved':False,'up_to':False,
            'current_action_state':{'state_class':'savestate.actions.GamblingChipActionState','state':{'notchip':True}},
            'action_queue':[{'state_class':'savestate.actions.WaitActionState','state':{'duration':0.25}}],
            'card_queue_state':[]}
        return s

    def liquid_base(self, count=1, cards=3):
        s=copy.deepcopy(self.snapshot);p=s['player_state']
        originals=p['draw_pile']+[p['hand'][0]]
        p['discard_pile']=[copy.deepcopy(originals[i]) for i in range(cards)]
        for i,c in enumerate(p['discard_pile']): c['uuid']=f'00000000-0000-0000-0000-{100+i:012d}'
        p['potions'][0]['id']='LiquidMemories'
        if count==2:p['relics'].append({'relic_id':'SacredBark','counter':-1,'_private':{}})
        return s

    def liquid_snapshot(self, count=1, selected=()):
        s=self.liquid_base(count);p=s['player_state'];p['potions'][0]['id']='Potion Slot'
        s.update(screen_name='GRID',is_screen_up=True)
        s['grid_card_select_screen_state']={'num_cards':count,'group_cards':copy.deepcopy(p['discard_pile']),
            'selected_cards':[copy.deepcopy(p['discard_pile'][i]) for i in selected],
            'any_number':False,'for_clarity':False,'for_upgrade':False,'for_transform':False,'for_purge':False,
            'confirm_screen_up':False,'is_just_for_confirming':False,
            'current_action_state':{'state_class':'savestate.actions.BetterDiscardPileToHandActionState',
                'state':{'number_of_cards':count,'owner_index':-1,'set_cost':True,'optional':False,'new_cost':0}},
            'action_queue':[{'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':-1,'derived_refresh':True}}],
            'card_queue_state':[]}
        return s

    def test_exact_words_survive_64_bit_roundtrip(self):
        r = self.request(); self.assertTrue(r["supported"], r)
        self.assertEqual(r["rng_restore_mode"], "exact_state")
        self.assertEqual(set(r["exact_rng"]), {"ai", "card_random", "misc", "monster_hp", "potion", "shuffle", "card", "headless"})
        for name, row in r["exact_rng"].items(): self.assertEqual(row, self.snapshot["_headless_rng"]["streams"][name])

    def test_seed_and_counter_reconstruction_is_not_silently_used(self):
        exact = self.request(); legacy = copy.deepcopy(self.snapshot); legacy.pop("_headless_rng"); legacy.pop("_engine")
        rebuilt = self.request(legacy)
        self.assertTrue(rebuilt["supported"], rebuilt)
        self.assertEqual(rebuilt["rng_restore_mode"], "legacy_counter")
        self.assertNotEqual(exact["samples"], rebuilt["samples"])

    def test_headless_source_requires_exact_state(self):
        s = copy.deepcopy(self.snapshot); s.pop("_headless_rng")
        r = self.request(s); self.assertFalse(r["supported"]); self.assertIn("headless_rng_missing", r["reason"])

    def test_word_format_stream_count_zero_and_policy_guards(self):
        changes = [lambda s:s["_headless_rng"].update(policy="unknown"),
                   lambda s:s["_headless_rng"]["streams"].pop("headless"),
                   lambda s:s["_headless_rng"]["streams"]["ai"].update(state0=1),
                   lambda s:s["_headless_rng"]["streams"]["ai"].update(state0="F123456789abcdef"),
                   lambda s:s["_headless_rng"]["streams"]["ai"].update(state0="0"*16, state1="0"*16)]
        for mutate in changes:
            with self.subTest(mutate=mutate):
                s = copy.deepcopy(self.snapshot); mutate(s)
                self.assertFalse(self.request(s)["supported"])

    def test_redundant_counter_mismatch_is_rejected(self):
        s = copy.deepcopy(self.snapshot); s["rng_state"]["ai_rng_counter"] += 1
        r = self.request(s); self.assertFalse(r["supported"]); self.assertIn("counter_mismatch", r["reason"])

    def test_fingerprint_exposes_imported_native_state(self):
        r = self.request(operation="fingerprint"); self.assertTrue(r["supported"], r)
        self.assertIsInstance(r["native_key"], list); self.assertEqual(r["rng_restore_mode"], "exact_state")

    def test_headless_thief_restores_actual_gold_not_turn_times_thievery(self):
        s = copy.deepcopy(self.snapshot); m = s["cur_map_node_state"]["monster_data"][0]
        m["creature"].update(id="Looter", powers=[{"power_id": "Thievery", "amount": 15}])
        m.update(next_move=1, intent_name="ATTACK", move_history=[1, 1], _private={"Looter.stolenGold": 14})
        r = self.request(s, "fingerprint"); self.assertTrue(r["supported"], r)
        self.assertEqual(r["native_key"][0][16], 1)
        monsters = [row for row in r["native_key"] if isinstance(row, list) and len(row) == 30]
        self.assertEqual(monsters[0][27], 14)

    def test_logical_key_keeps_live_actor_state_and_public_dead_ledger_separate(self):
        a = copy.deepcopy(self.snapshot); original = a["cur_map_node_state"]["monster_data"][0]
        dead = copy.deepcopy(original); dead["creature"].update(current_health=0, is_dead=True, is_dying=True, powers=[])
        a["cur_map_node_state"]["monster_data"] = [dead, original]
        b = copy.deepcopy(a); b["cur_map_node_state"]["monster_data"][0]["creature"]["max_health"] += 10
        x, y = self.request(a, "fingerprint"), self.request(b, "fingerprint")
        self.assertTrue(x["supported"], x); self.assertTrue(y["supported"], y)
        self.assertEqual(x["native_key"], y["native_key"])
        self.assertNotEqual(x["projection"], y["projection"])  # Both checks are required by the bridge.

    def test_mugger_death_audio_advances_pinned_headless_ai_stream(self):
        s = copy.deepcopy(self.snapshot); m = s["cur_map_node_state"]["monster_data"][0]
        m["creature"].update(id="Looter", current_health=47, powers=[{"power_id": "Thievery", "amount": 15}])
        m.update(next_move=1, move_history=[1], _private={"Looter.stolenGold": 0})
        mugger = copy.deepcopy(m); mugger["creature"].update(id="Mugger", current_health=18)
        mugger["_private"] = {"Mugger.stolenGold": 0}; s["cur_map_node_state"]["monster_data"].append(mugger)
        s["player_state"]["hand"][0].update(card_id="Bludgeon", cost=3, cost_for_turn=3)
        r = self.worker.execute({"snapshot": s, "operation": "predict", "command": {"type": "CARD", "card_index": 0, "monster_index": 1}}, 1000)
        self.assertTrue(r["supported"], r)
        self.assertEqual(r["expected_exact_rng"]["ai"]["counter"], s["rng_state"]["ai_rng_counter"]+1)

    def test_centurion_defend_rolls_even_for_one_ally_and_mystic_caps_heals(self):
        s = copy.deepcopy(self.snapshot); centurion = s["cur_map_node_state"]["monster_data"][0]
        centurion["creature"].update(id="Centurion", current_health=30, max_health=80, powers=[])
        centurion.update(next_move=2, move_history=[1, 1, 2])
        healer = copy.deepcopy(centurion); healer["creature"].update(id="Healer", current_health=8)
        healer["move_history"] = [1, 2, 2]; s["cur_map_node_state"]["monster_data"].append(healer)
        old = self.request(s, "fingerprint")
        r = self.worker.execute({"snapshot": s, "operation": "predict", "command": {"type": "END"}}, 1000)
        self.assertTrue(r["supported"], r)
        self.assertEqual(r["expected_exact_rng"]["ai"]["counter"], s["rng_state"]["ai_rng_counter"]+3)
        before = [row for row in old["native_key"] if isinstance(row, list) and len(row) == 30]
        after = [row for row in r["expected_native_key"] if isinstance(row, list) and len(row) == 30]
        self.assertNotEqual(before[1][8], after[1][8])  # A third consecutive Heal is forbidden.

    def test_centurion_without_a_living_ally_blocks_itself(self):
        s = copy.deepcopy(self.snapshot); m = s["cur_map_node_state"]["monster_data"][0]
        m["creature"].update(id="Centurion", powers=[]); m.update(next_move=2, move_history=[2])
        r = self.worker.execute({"snapshot": s, "operation": "predict", "command": {"type": "END"}}, 1000)
        self.assertTrue(r["supported"], r)
        self.assertEqual(r["expected_after"]["monsters"][0]["block"], 15)
        self.assertEqual(r["expected_exact_rng"]["ai"]["counter"], s["rng_state"]["ai_rng_counter"]+1)

    def test_gremlin_setmove_history_and_wizard_charge_without_extra_rng(self):
        for identity,move,private,expected_rng in [('GremlinWarrior',1,{},0),('GremlinThief',1,{},0),
                ('GremlinFat',2,{},1),('GremlinWizard',2,{'GremlinWizard.currentCharge':1},0)]:
            with self.subTest(identity=identity):
                s=copy.deepcopy(self.snapshot);m=s['cur_map_node_state']['monster_data'][0]
                m['creature'].update(id=identity,powers=[]);m.update(next_move=move,move_history=[move],_private=private)
                r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
                self.assertTrue(r['supported'],r)
                self.assertEqual(r['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+expected_rng)
                monster=next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30)
                self.assertEqual(monster[8],monster[9])
                if identity=='GremlinWizard':self.assertEqual(monster[27],2)

    def test_parasite_fell_reroll_uses_the_new_roll_not_original_low_roll(self):
        s=copy.deepcopy(self.snapshot);m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='Shelled Parasite',powers=[])
        m.update(next_move=1,move_history=[3,2,2,3,1])
        s['rng_state']['ai_rng_counter']=6
        s['_headless_rng']['streams']['ai']={'counter':6,'state0':'a61685c9f11ea370','state1':'0eef08e66500648a'}
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'],r)
        expected=copy.deepcopy(s);expected['cur_map_node_state']['monster_data'][0]['next_move']=3
        native=self.request(expected,'fingerprint')
        actual_monster=next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30)
        expected_monster=next(row for row in native['native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(actual_monster[8],expected_monster[8])

    def test_champ_private_roll_counter_forges_phase_and_source_gloat(self):
        s = copy.deepcopy(self.snapshot); m = s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='Champ', powers=[])
        m.update(next_move=1, move_history=[1, 1], _private={'Champ.numTurns':3,'Champ.forgeTimes':2,
                         'Champ.forgeThreshold':2,'Champ.thresholdReached':False})
        imported = self.request(s, 'fingerprint'); self.assertTrue(imported['supported'], imported)
        monster = next(row for row in imported['native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(monster[27], 3*8+2)
        r = self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'], r)
        monster = next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(monster[27], 2)  # Fourth selected move Taunts and resets source numTurns.
        m.update(next_move=6, move_history=[1,6]); m['_private']['Champ.numTurns'] = 0
        taunt = self.request(s,'fingerprint')
        expected = next(row for row in taunt['native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(monster[8], expected[8])
        # Independent source counter survives phase two and uses the source
        # Gloat buff (A0: +2), not the legacy +3 approximation.
        m.update(next_move=5, move_history=[3,5]); m['_private'].update({'Champ.numTurns':7,'Champ.thresholdReached':True})
        r = self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'], r)
        monster = next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(monster[27], 8*8+4+2)
        self.assertEqual(monster[22], 2)

    def test_book_stab_count_restores_exact_private_value_and_advances(self):
        s = copy.deepcopy(self.snapshot); m = s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='BookOfStabbing', powers=[{'power_id':'Painful Stabs','amount':-1}])
        m.update(next_move=1, move_history=[1], move_hits=2, _private={'BookOfStabbing.stabCount':2})
        imported = self.request(s, 'fingerprint'); self.assertTrue(imported['supported'], imported)
        monster = next(row for row in imported['native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(monster[27], 2)
        r = self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'], r)
        # With no block/powers, both 6-damage stabs land and each generates a Wound.
        self.assertEqual(r['expected_after']['hp'], s['player_state']['creature']['current_health'] - 12)
        self.assertEqual(sum(c['id']=='Wound' for pile in ('hand','draw_pile','discard_pile','exhaust_pile')
                             for c in r['expected_after'][pile]), 2)
        next_monster = next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(next_monster[27], 3)
        self.assertEqual(r['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+1)
        # A Single Stab does not reveal this counter through its intent. Keep
        # the exact private value instead of resetting to the legacy lower bound.
        m.update(next_move=2, move_history=[2], move_hits=1)
        m['_private']['BookOfStabbing.stabCount'] = 5
        imported = self.request(s, 'fingerprint'); self.assertTrue(imported['supported'], imported)
        monster = next(row for row in imported['native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(monster[27], 5)
        m['_private']['BookOfStabbing.stabCount'] = 0
        self.assertFalse(self.request(s, 'fingerprint')['supported'])


    def test_taskmaster_roll_updates_repeated_move_history(self):
        s=copy.deepcopy(self.snapshot); m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='SlaverBoss', powers=[])
        m.update(next_move=2, move_history=[2], _private={})
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'],r)
        monster=next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30)
        self.assertEqual(monster[8],monster[9])
        self.assertEqual(r['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+1)

    def test_small_spike_slime_roll_records_repeated_tackle_and_one_ai_draw(self):
        for history in ([1], [1, 1]):
            with self.subTest(history=history):
                s=copy.deepcopy(self.snapshot);m=s['cur_map_node_state']['monster_data'][0]
                m['creature'].update(id='SpikeSlime_S',current_health=2,max_health=14,powers=[])
                m.update(next_move=1,move_history=history,_private={})
                before=self.request(s,'fingerprint');self.assertTrue(before['supported'],before)
                previous=next(row for row in before['native_key'] if isinstance(row,list) and len(row)==30)
                r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
                self.assertTrue(r['supported'],r)
                monster=next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30)
                self.assertEqual(monster[8:10],[previous[8],previous[8]])
                self.assertEqual(r['expected_exact_rng']['ai']['counter'],s['rng_state']['ai_rng_counter']+1)

    def test_small_spike_slime_headless_history_guard_does_not_expand_legacy_contract(self):
        s=copy.deepcopy(self.snapshot);m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='SpikeSlime_S',current_health=2,max_health=14,powers=[])
        m.update(next_move=1,move_history=[1],_private={})
        s.pop('_engine');s.pop('_headless_rng')
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'],r)
        self.assertNotIn('rng_restore_mode',r)
        self.assertNotIn('expected_native_key',r)
        self.assertEqual(r['expected_after']['hp'],s['player_state']['creature']['current_health']-5)

    def test_watcher_combo_reads_full_combat_history_across_turn_boundary(self):
        cases = [('FollowUp','Strike_P',3,None),('FollowUp','Defend_P',2,None),
                 ('FollowUp',None,2,None),('SashWhip','Strike_P',2,(24,1)),
                 ('CrushJoints','Defend_P',2,(23,1))]
        for identity, previous, energy, debuff in cases:
            with self.subTest(identity=identity,previous=previous):
                s=copy.deepcopy(self.snapshot);p=s['player_state'];s['turn']=2
                p['chosen_class_name']='WATCHER';p['hand'][0].update(card_id=identity,cost=1,cost_for_turn=1)
                s['cards_played_this_turn']=[];s['cards_played_this_turn_backup']=[]
                s['cards_played_this_combat']=[] if previous is None else [
                    {'card_index':-1,'card_state':{'card_id':previous}}]
                p['energy_panel_total_energy']=3
                r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'CARD','card_index':0,'monster_index':0}},1000)
                self.assertTrue(r['supported'],r)
                self.assertEqual(r['expected_after']['energy'],energy)
                if debuff is not None:
                    monster=next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30)
                    self.assertEqual(monster[debuff[0]],debuff[1])

    def test_headless_previous_card_type_persists_on_end_and_requires_full_history(self):
        s=copy.deepcopy(self.snapshot);s['turn']=2
        s['cards_played_this_turn']=[];s['cards_played_this_turn_backup']=[]
        s['cards_played_this_combat']=[{'card_index':0,'card_state':None}]
        imported=self.request(s,'fingerprint');self.assertTrue(imported['supported'],imported)
        self.assertEqual(imported['native_key'][0][15],0)  # ATTACK from indexed master-deck card.
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'],r)
        self.assertEqual(r['expected_native_key'][0][15],0)
        # Non-Headless keeps its original per-turn import/reset behavior.
        legacy=copy.deepcopy(s);legacy.pop('_headless_rng');legacy.pop('_engine')
        legacy_imported=self.request(legacy,'fingerprint');self.assertTrue(legacy_imported['supported'],legacy_imported)
        self.assertEqual(legacy_imported['native_key'][0][15],5)  # INVALID
        s.pop('cards_played_this_combat')
        self.assertFalse(self.request(s,'fingerprint')['supported'])

    def test_sanctity_draws_from_a_skill_played_on_an_earlier_turn(self):
        for previous, expected_hand in [('Defend_P',3),('Strike_P',1)]:
            s=copy.deepcopy(self.snapshot);s['turn']=2;p=s['player_state']
            p['chosen_class_name']='WATCHER';p['hand'][0].update(card_id='Sanctity',cost=1,cost_for_turn=1)
            s['cards_played_this_turn']=[];s['cards_played_this_turn_backup']=[]
            s['cards_played_this_combat']=[{'card_index':-1,'card_state':{'card_id':previous}}]
            r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'CARD','card_index':0,'monster_index':-1}},1000)
            self.assertTrue(r['supported'],r)
            self.assertEqual(len(r['expected_after']['hand']),expected_hand)

    def test_centennial_puzzle_explicit_latch_restores_once_per_combat_draw(self):
        fingerprints={};predictions={}
        for used,hand_size in [(False,4),(True,1)]:
            s=copy.deepcopy(self.snapshot);p=s['player_state']
            p['relics'].append({'relic_id':'Centennial Puzzle','counter':-1,
                               '_private':{'CentennialPuzzle.usedThisCombat':used}})
            p['damaged_this_combat']=0  # Deliberately equal: restore the latch, never infer it from damage.
            p['hand'][0].update(card_id='Bloodletting',cost=0,cost_for_turn=0)
            extra=copy.deepcopy(p['draw_pile'][1]);p['draw_pile'].append(extra)
            extra['uuid']='00000000-0000-0000-0000-000000000005'
            fingerprints[used]=self.request(s,'fingerprint');self.assertTrue(fingerprints[used]['supported'],fingerprints[used])
            r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'CARD','card_index':0,'monster_index':-1}},1000)
            self.assertTrue(r['supported'],r);predictions[used]=r
            self.assertEqual(len(r['expected_after']['hand']),hand_size)
            self.assertEqual(r['expected_after']['hp'],67)
        self.assertNotEqual(fingerprints[False]['native_key'][4][17:20],fingerprints[True]['native_key'][4][17:20])
        self.assertEqual(predictions[False]['expected_native_key'][4][17:20],fingerprints[True]['native_key'][4][17:20])
        # Both missing/wrongly typed static state and legacy imports stay guarded.
        p['relics'][-1]['_private']={}
        self.assertFalse(self.request(s,'fingerprint')['supported'])
        p['relics'][-1]['_private']={'CentennialPuzzle.usedThisCombat':1}
        self.assertFalse(self.request(s,'fingerprint')['supported'])
        p['relics'][-1]['_private']={'CentennialPuzzle.usedThisCombat':False}
        s.pop('_headless_rng');s.pop('_engine')
        result=self.request(s,'fingerprint');self.assertFalse(result['supported'])
        self.assertIn('relic_private_state:Centennial Puzzle',result['reason'])

    def test_cultist_potion_restores_player_ritual_latch_and_preserves_stacking(self):
        for initial, first, has_bark in [(0,True,False),(0,True,True),(1,False,False),(1,True,False)]:
            with self.subTest(initial=initial,first=first,has_bark=has_bark):
                s=copy.deepcopy(self.snapshot);p=s['player_state']
                p['potions'][0]['id']='CultistPotion'
                if has_bark:p['relics'].append({'relic_id':'SacredBark','counter':-1})
                if initial:p['creature']['powers']=[{'power_id':'Ritual','amount':initial,'just_applied':first}]
                r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':-1}},1000)
                self.assertTrue(r['supported'],r)
                self.assertEqual(r['expected_native_key'][4][14],512 if first else 0)
                after=copy.deepcopy(s);after['player_state']['potions'][0]['id']='Potion Slot'
                after['player_state']['creature']['powers']=[{'power_id':'Ritual','amount':initial+(2 if has_bark else 1),'just_applied':first}]
                imported=self.request(after,'fingerprint');self.assertTrue(imported['supported'],imported)
                self.assertEqual(r['expected_native_key'][4][14:17],imported['native_key'][4][14:17])
        legacy=copy.deepcopy(self.snapshot);legacy.pop('_headless_rng');legacy.pop('_engine')
        legacy['player_state']['potions'][0]['id']='CultistPotion'
        r=self.worker.execute({'snapshot':legacy,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':-1}},1000)
        self.assertTrue(r['supported'],r)
        self.assertNotIn('expected_native_key',r)  # Legacy predict keeps its existing public response contract.
        self.assertEqual(r['expected_after']['potions'][0],'Potion Slot')

    def test_player_ritual_gains_strength_on_first_and_later_end_turns(self):
        for strength,turn in [(0,1),(1,2)]:
            s=copy.deepcopy(self.snapshot);s['turn']=turn;p=s['player_state']
            p['creature']['powers']=[{'power_id':'Ritual','amount':1,'just_applied':True}]
            if strength:p['creature']['powers'].append({'power_id':'Strength','amount':strength})
            r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
            self.assertTrue(r['supported'],r)
            self.assertEqual(r['expected_native_key'][4][13],strength+1)
            self.assertEqual(r['expected_native_key'][4][14],512)  # Player skipFirst stays true; it does not delay Strength.

    def test_gamblers_brew_opens_exact_hand_boundary_and_consumes_only_potion(self):
        s=copy.deepcopy(self.snapshot);s['player_state']['potions'][0]['id']='GamblersBrew'
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':-1}},1000)
        self.assertTrue(r['supported'],r);self.assertEqual(r['expected_screen'],'HAND_SELECT')
        self.assertEqual(r['expected_after']['potions'][0],'Potion Slot')
        actual=self.request(self.gambler_snapshot(),'fingerprint');self.assertTrue(actual['supported'],actual)
        self.assertEqual(r['expected_after'],actual['projection'])
        self.assertEqual(r['expected_selection'],actual['selection'])
        self.assertIsInstance(r['expected_selection_native_key'],list)
        self.assertTrue(r['expected_selection_native_key'])
        self.assertEqual(r['expected_selection_native_key'],actual['selection_native_key'])
        self.assertEqual(r['expected_exact_rng'],actual['exact_rng'])
        self.assertEqual(r['expected_selection']['remaining'],[c['uuid'] for c in s['player_state']['hand']])
        self.assertEqual(r['expected_selection']['count'],99)

    def test_gambler_zero_confirm_and_partial_selection_preserve_uuid_rng(self):
        s=self.gambler_snapshot()
        zero=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'HAND_SELECT_CONFIRM'}},1000)
        self.assertTrue(zero['supported'],zero);self.assertEqual(zero['expected_screen'],'NONE')
        baseline=copy.deepcopy(s);baseline.update(screen_name='NONE',is_screen_up=False);baseline.pop('hand_select_screen_state')
        normal=self.request(baseline,'fingerprint');self.assertTrue(normal['supported'],normal)
        self.assertEqual(zero['expected_native_key'],normal['native_key'])
        self.assertEqual(zero['expected_after'],normal['projection'])
        click=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'HAND_SELECT','card_index':0}},1000)
        self.assertTrue(click['supported'],click);selected=self.gambler_snapshot((0,))
        partial=self.request(selected,'fingerprint');self.assertTrue(partial['supported'],partial)
        self.assertEqual(click['expected_selection'],partial['selection'])
        self.assertEqual(click['expected_selection_native_key'],partial['selection_native_key'])
        self.assertEqual(click['expected_after'],partial['projection'])
        self.assertEqual(click['expected_exact_rng'],partial['exact_rng'])
        confirm=self.worker.execute({'snapshot':selected,'operation':'predict','command':{'type':'HAND_SELECT_CONFIRM'}},1000)
        self.assertTrue(confirm['supported'],confirm);self.assertEqual(confirm['expected_screen'],'NONE')
        self.assertEqual(confirm['expected_card_uuids']['hand'],[
            selected['player_state']['hand'][0]['uuid'],selected['player_state']['draw_pile'][-1]['uuid']])
        self.assertEqual(confirm['expected_card_uuids']['discard_pile'],[selected['hand_select_screen_state']['selected_cards'][0]['uuid']])
        self.assertEqual(confirm['expected_native_key'][4][39],1)  # Manual discard count.

    def test_gambler_confirm_runs_manual_discard_callbacks_and_rejects_bad_controls(self):
        s=self.gambler_snapshot((0,));s['player_state']['chosen_class_name']='THE_SILENT'
        chosen=s['hand_select_screen_state']['selected_cards'][0]
        chosen.update(card_id='Tactician',cost=-2,cost_for_turn=-2)
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'HAND_SELECT_CONFIRM'}},1000)
        self.assertTrue(r['supported'],r);self.assertEqual(r['expected_after']['energy'],4)
        bad=self.gambler_snapshot();bad['hand_select_screen_state']['any_number']=False
        self.assertFalse(self.request(bad,'fingerprint')['supported'])
        bad=self.gambler_snapshot();bad['hand_select_screen_state']['action_queue']=[
            {'state_class':'savestate.actions.GainEnergyActionState','state':{'energyGain':1}}]
        self.assertFalse(self.request(bad,'fingerprint')['supported'])

    def test_gambler_guard_covers_powers_and_attributes_of_selected_cards(self):
        s=self.gambler_snapshot((0,));base=self.request(s,'fingerprint')
        self.assertTrue(base['supported'],base)
        self.assertIsInstance(base['selection_native_key'],list)
        self.assertTrue(base['selection_native_key'])
        for mutate in (
                lambda t:t['player_state']['creature']['powers'].append({'power_id':'Strength','amount':2}),
                lambda t:t['hand_select_screen_state']['selected_cards'][0].update(cost_for_turn=0)):
            changed=copy.deepcopy(s);mutate(changed);other=self.request(changed,'fingerprint')
            self.assertTrue(other['supported'],other)
            self.assertEqual(base['projection'],other['projection'])  # Neither change is in the public projection.
            self.assertEqual(base['selection'],other['selection'])
            self.assertNotEqual(base['selection_native_key'],other['selection_native_key'])

    def test_gambler_settled_discard_resets_temporary_costs(self):
        s=self.gambler_snapshot((0,));c=s['hand_select_screen_state']['selected_cards'][0]
        c['cost_for_turn']=0
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'HAND_SELECT_CONFIRM'}},1000)
        self.assertTrue(r['supported'],r)
        self.assertEqual(r['expected_after']['discard_pile'][0]['turn_cost'],c['cost'])
        bad=self.gambler_snapshot();bad['hand_select_screen_state']['action_queue'].append(
            {'state_class':'savestate.actions.UnknownExecutableActionState','state':{}})
        self.assertFalse(self.request(bad,'fingerprint')['supported'])

    def test_gambler_binds_source_hand_refresh_without_executing_unknown_queue(self):
        source=self.gambler_snapshot();baseline=self.request(source,'fingerprint')
        refresh={'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':-1,'derived_refresh':True}}
        source['hand_select_screen_state']['action_queue'].insert(0,copy.deepcopy(refresh))
        actual=self.request(source,'fingerprint');self.assertTrue(actual['supported'],actual)
        self.assertEqual(actual['selection_native_key'],baseline['selection_native_key'])
        self.assertEqual(actual['exact_rng'],baseline['exact_rng'])
        self.assertEqual(actual['projection'],baseline['projection'])
        changed=copy.deepcopy(source);changed['player_state']['creature']['powers'].append({'power_id':'Strength','amount':2})
        dependent=self.request(changed,'fingerprint');self.assertTrue(dependent['supported'],dependent)
        self.assertEqual(dependent['projection'],actual['projection'])
        self.assertNotEqual(dependent['selection_native_key'],actual['selection_native_key'])
        for queue in ([refresh,refresh],list(reversed(source['hand_select_screen_state']['action_queue'])),
                      [{'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':0,'derived_refresh':True}}],
                      [{'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':-1,'derived_refresh':False}}],
                      [refresh,{'state_class':'savestate.actions.GainEnergyActionState','state':{'energyGain':1}}]):
            bad=copy.deepcopy(source);bad['hand_select_screen_state']['action_queue']=queue
            self.assertFalse(self.request(bad,'fingerprint')['supported'])


    def exhaust_cost_snapshot(self, cost=2, turn_cost=0):
        s=copy.deepcopy(self.snapshot);p=s['player_state'];p['chosen_class_name']='THE_SILENT'
        c=copy.deepcopy(p['hand'][0]);c.update(card_id='Crippling Poison',cost=cost,cost_for_turn=turn_cost,
            uuid='00000000-0000-0000-0000-000000000099',exhaust=True)
        p['exhaust_pile']=[c]
        return s

    def test_headless_exhaust_temporary_cost_settles_only_across_turn_boundary(self):
        s=self.exhaust_cost_snapshot();before=self.request(s,'fingerprint');self.assertTrue(before['supported'],before)
        self.assertEqual(before['projection']['exhaust_pile'][0]['turn_cost'],0)
        same_turn=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':0}},1000)
        self.assertTrue(same_turn['supported'],same_turn);self.assertEqual(same_turn['expected_after']['exhaust_pile'][0]['turn_cost'],0)
        end=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(end['supported'],end);self.assertEqual(end['expected_after']['turn'],2)
        self.assertEqual(end['expected_after']['exhaust_pile'][0]['turn_cost'],2)
        baseline=self.exhaust_cost_snapshot(turn_cost=2)
        normal=self.worker.execute({'snapshot':baseline,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(normal['supported'],normal)
        self.assertEqual(end['expected_after'],normal['expected_after'])
        self.assertEqual(end['expected_native_key'],normal['expected_native_key'])
        self.assertEqual(end['expected_exact_rng'],normal['expected_exact_rng'])

    def test_exhaust_turn_settlement_preserves_combat_costs_and_retained_hand(self):
        for cost,turn_cost in ((0,0),(1,0),(2,-1),(-1,0),(-2,0)):
            s=self.exhaust_cost_snapshot(cost,turn_cost);p=s['player_state']
            if cost<0:p['exhaust_pile'][0].update(card_id='Whirlwind' if cost==-1 else 'AscendersBane',exhaust=cost==-2)
            p['hand'][0]['retain']=True
            p['hand'][0]['cost_for_turn']=0
            baseline=copy.deepcopy(s);baseline['player_state']['exhaust_pile'][0]['cost_for_turn']=cost
            end=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
            normal=self.worker.execute({'snapshot':baseline,'operation':'predict','command':{'type':'END'}},1000)
            self.assertTrue(end['supported'],end);self.assertTrue(normal['supported'],normal)
            self.assertEqual(end['expected_after']['exhaust_pile'][0]['turn_cost'],cost)
            self.assertEqual(end['expected_after']['exhaust_pile'][0]['cost'],cost)
            self.assertEqual(end['expected_after'],normal['expected_after'])
            self.assertEqual(end['expected_native_key'],normal['expected_native_key'])
            self.assertEqual(end['expected_exact_rng'],normal['expected_exact_rng'])

    def test_exhaust_turn_cost_legacy_prediction_keeps_existing_behavior(self):
        s=self.exhaust_cost_snapshot();s.pop('_engine');s.pop('_headless_rng')
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'],r);self.assertEqual(r['expected_after']['exhaust_pile'][0]['turn_cost'],0)

    def test_exhaust_is_not_reset_when_end_stops_before_monster_turn_at_codex(self):
        s=self.exhaust_cost_snapshot();s['player_state']['relics'].append({'relic_id':"Nilry's Codex",'counter':-1})
        s['_combat_card_pools']={k:['Anger','Cleave','Clothesline'] for k in ('any','attacks','skills','powers','colorless','common','uncommon','rare')}
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
        self.assertTrue(r['supported'],r);self.assertEqual(r['expected_screen'],'CARD_REWARD')
        self.assertEqual(r['expected_after']['turn'],1)
        self.assertEqual(r['expected_after']['exhaust_pile'][0]['turn_cost'],0)

    def angry_snapshot(self, block=0, hp=25):
        s=copy.deepcopy(self.snapshot);p=s['player_state'];p['chosen_class_name']='THE_SILENT'
        p['hand'][0].update(card_id='Neutralize',cost=0,cost_for_turn=0)
        m=s['cur_map_node_state']['monster_data'][0]
        m['creature'].update(id='GremlinWarrior',current_health=hp,max_health=25,current_block=block,
            powers=[{'power_id':'Angry','amount':2}])
        m.update(next_move=1,intent_name='ATTACK',move_history=[1],move_base_damage=4,move_adjusted_damage=4,move_hits=1)
        return s

    def test_angry_requires_positive_final_normal_damage_after_block(self):
        for block,zero,expected_hp,expected_block,expected_strength in (
                (5,False,25,2,0),(3,False,25,0,0),(2,False,24,0,2),(0,False,22,0,2),(0,True,25,0,0)):
            s=self.angry_snapshot(block)
            if zero:s['player_state']['creature']['powers']=[{'power_id':'Strength','amount':-3}]
            r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'CARD','card_index':0,'monster_index':0}},1000)
            self.assertTrue(r['supported'],r)
            self.assertEqual((r['expected_after']['monsters'][0]['hp'],r['expected_after']['monsters'][0]['block']),(expected_hp,expected_block))
            monster=next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30 and row[1]==0)
            self.assertEqual(monster[22],expected_strength)
            self.assertEqual(r['expected_exact_rng'],{k:v for k,v in s['_headless_rng']['streams'].items() if k in r['expected_exact_rng']})
        # Legacy imports retain their existing behavior outside the Headless scope.
        legacy=self.angry_snapshot(5);legacy.pop('_engine');legacy.pop('_headless_rng')
        self.assertTrue(self.worker.execute({'snapshot':legacy,'operation':'predict','command':{'type':'CARD','card_index':0,'monster_index':0}},1000)['supported'])

    def test_angry_multihit_counts_only_hits_that_reach_hp(self):
        for block,expected_hp,expected_strength in ((6,23,2),(4,21,2),(0,17,4)):
            s=self.angry_snapshot(block);s['player_state']['hand'][0].update(card_id='Dagger Spray',cost=1,cost_for_turn=1)
            r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'CARD','card_index':0,'monster_index':-1}},1000)
            self.assertTrue(r['supported'],r);self.assertEqual(r['expected_after']['monsters'][0]['hp'],expected_hp)
            monster=next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30 and row[1]==0)
            self.assertEqual(monster[22],expected_strength)
            self.assertEqual(r['expected_exact_rng'],{k:v for k,v in s['_headless_rng']['streams'].items() if k in r['expected_exact_rng']})

    def test_angry_lethal_hit_keeps_death_and_other_target_state(self):
        s=self.angry_snapshot(hp=3)
        s['cur_map_node_state']['monster_data'].append(copy.deepcopy(self.snapshot['cur_map_node_state']['monster_data'][0]))
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'CARD','card_index':0,'monster_index':0}},1000)
        self.assertTrue(r['supported'],r);self.assertEqual([m['hp'] for m in r['expected_after']['monsters']],[0,50])
        monsters=[row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30]
        self.assertEqual(len(monsters),1);self.assertEqual((monsters[0][1],monsters[0][22]),(1,0))
        self.assertEqual(r['expected_exact_rng'],{k:v for k,v in s['_headless_rng']['streams'].items() if k in r['expected_exact_rng']})

    def test_angry_does_not_trigger_for_potion_thorns_or_poison(self):
        s=self.angry_snapshot()
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':0}},1000)
        self.assertTrue(r['supported'],r);self.assertEqual(r['expected_after']['monsters'][0]['hp'],5)
        monster=next(row for row in r['expected_native_key'] if isinstance(row,list) and len(row)==30 and row[1]==0)
        self.assertEqual(monster[22],0)
        for poison in (False,True):
            s=self.angry_snapshot();baseline=copy.deepcopy(s)
            if poison:s['cur_map_node_state']['monster_data'][0]['creature']['powers'].append({'power_id':'Poison','amount':3})
            else:s['player_state']['creature']['powers']=[{'power_id':'Thorns','amount':2}]
            base=self.worker.execute({'snapshot':baseline,'operation':'predict','command':{'type':'END'}},1000)
            end=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
            self.assertTrue(base['supported'],base);self.assertTrue(end['supported'],end)
            self.assertEqual(end['expected_after']['monsters'][0]['hp'],22 if poison else 23)
            monster=next(row for row in end['expected_native_key'] if isinstance(row,list) and len(row)==30 and row[1]==0)
            self.assertEqual(monster[22],0)
            if poison:self.assertEqual(monster[19],2)
            self.assertEqual(end['expected_exact_rng'],base['expected_exact_rng'])

    def test_combust_hp_loss_import_is_explicit_and_bound_in_full_keys(self):
        for factory,key in ((lambda:copy.deepcopy(self.snapshot),'native_key'),(self.gambler_snapshot,'selection_native_key'),
                            (self.liquid_snapshot,'selection_native_key')):
            results=[]
            for hp_loss in (1,4):
                s=factory();s['player_state']['creature']['powers']=[
                    {'power_id':'Combust','amount':12,'_private':{'CombustPower.hpLoss':hp_loss}}]
                # Deliberately empty: private hpLoss is independent of played-card history.
                s['cards_played_this_combat']=[]
                r=self.request(s,'fingerprint');self.assertTrue(r['supported'],r)
                self.assertEqual(r[key][4][28],hp_loss);results.append(r)
            self.assertEqual(results[0]['projection'],results[1]['projection'])
            self.assertNotEqual(results[0][key],results[1][key])

    def test_combust_first_application_and_stacking_match_exact_imported_state(self):
        for initial,hp_loss,upgraded in ((0,0,False),(5,1,False),(12,3,True)):
            s=copy.deepcopy(self.snapshot);p=s['player_state'];played=p['hand'][0]
            played.update(card_id='Combust',times_upgraded=int(upgraded),cost=1,cost_for_turn=1)
            if initial:p['creature']['powers']=[{'power_id':'Combust','amount':initial,'_private':{'CombustPower.hpLoss':hp_loss}}]
            predicted=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'CARD','card_index':0,'monster_index':-1}},1000)
            self.assertTrue(predicted['supported'],predicted)
            self.assertEqual(predicted['expected_native_key'][4][28],hp_loss+1)
            after=copy.deepcopy(s);a=after['player_state'];a['hand'].pop(0);a['energy_panel_total_energy']-=1
            a['creature']['powers']=[{'power_id':'Combust','amount':initial+(7 if upgraded else 5),'_private':{'CombustPower.hpLoss':hp_loss+1}}]
            after['cards_played_this_turn_backup']=[copy.deepcopy(played)]
            after['cards_played_this_combat']=[{'card_index':-1,'card_state':copy.deepcopy(played)}]
            actual=self.request(after,'fingerprint');self.assertTrue(actual['supported'],actual)
            self.assertEqual(predicted['expected_native_key'],actual['native_key'])
            self.assertEqual(predicted['expected_after'],actual['projection'])
            self.assertEqual(predicted['expected_exact_rng'],actual['exact_rng'])

    def test_combust_end_uses_hp_loss_and_aoe_without_extra_rng(self):
        for hp_loss,damage in ((1,5),(3,12)):
            s=copy.deepcopy(self.snapshot);p=s['player_state'];p['creature']['current_block']=9
            extra=copy.deepcopy(s['cur_map_node_state']['monster_data'][0]);s['cur_map_node_state']['monster_data'].append(extra)
            base=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
            self.assertTrue(base['supported'],base)
            p['creature']['powers']=[{'power_id':'Combust','amount':damage,'_private':{'CombustPower.hpLoss':hp_loss}}]
            end=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'END'}},1000)
            self.assertTrue(end['supported'],end);self.assertEqual(end['expected_after']['hp'],base['expected_after']['hp']-hp_loss)
            self.assertEqual([m['hp'] for m in end['expected_after']['monsters']],[m['hp']-damage for m in base['expected_after']['monsters']])
            self.assertEqual(end['expected_native_key'][4][28],hp_loss)
            self.assertEqual(end['expected_exact_rng'],base['expected_exact_rng'])

    def test_combust_missing_or_bad_private_state_and_legacy_stay_guarded(self):
        s=copy.deepcopy(self.snapshot);power={'power_id':'Combust','amount':5,'_private':{'CombustPower.hpLoss':1}}
        s['player_state']['creature']['powers']=[power]
        for value in (None,True,'1',1.0,0,-1,128):
            bad=copy.deepcopy(s);bad['player_state']['creature']['powers'][0]['_private']['CombustPower.hpLoss']=value
            self.assertFalse(self.request(bad,'fingerprint')['supported'])
        for field in ('_private','CombustPower.hpLoss'):
            bad=copy.deepcopy(s);p=bad['player_state']['creature']['powers'][0]
            if field=='_private':p.pop(field)
            else:p['_private'].pop(field)
            self.assertFalse(self.request(bad,'fingerprint')['supported'])
        legacy=copy.deepcopy(s);legacy.pop('_engine');legacy.pop('_headless_rng')
        r=self.request(legacy,'fingerprint');self.assertFalse(r['supported']);self.assertIn('power_private_state:Combust',r['reason'])
        other=copy.deepcopy(s);other['player_state']['creature']['powers'][0].update(power_id='Echo Form')
        r=self.request(other,'fingerprint');self.assertFalse(r['supported']);self.assertIn('power_private_state:Echo Form',r['reason'])

    def test_liquid_memories_opens_exact_grid_and_consumes_potion(self):
        for count in (1,2):
            before=self.liquid_base(count)
            predicted=self.worker.execute({'snapshot':before,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':-1}},1000)
            self.assertTrue(predicted['supported'],predicted);self.assertEqual(predicted['expected_screen'],'GRID')
            after=self.request(self.liquid_snapshot(count),'fingerprint');self.assertTrue(after['supported'],after)
            self.assertEqual(predicted['expected_after'],after['projection'])
            self.assertEqual(predicted['expected_selection'],after['selection'])
            self.assertIsInstance(predicted['expected_selection_native_key'],list);self.assertTrue(predicted['expected_selection_native_key'])
            self.assertEqual(predicted['expected_selection_native_key'],after['selection_native_key'])
            self.assertEqual(predicted['expected_exact_rng'],after['exact_rng'])

    def test_liquid_bark_partial_selection_can_cancel_and_only_full_selection_moves(self):
        s=self.liquid_snapshot(2)
        first=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'GRID_SELECT','card_index':1}},1000)
        partial=self.liquid_snapshot(2,(1,));actual=self.request(partial,'fingerprint')
        self.assertTrue(first['supported'],first);self.assertTrue(actual['supported'],actual)
        self.assertEqual(first['expected_after'],actual['projection']);self.assertEqual(first['expected_selection'],actual['selection'])
        self.assertEqual(first['expected_selection_native_key'],actual['selection_native_key'])
        cancel=self.worker.execute({'snapshot':partial,'operation':'predict','command':{'type':'GRID_SELECT','card_index':1}},1000)
        baseline=self.request(s,'fingerprint');self.assertTrue(cancel['supported'],cancel)
        self.assertEqual(cancel['expected_selection'],baseline['selection']);self.assertEqual(cancel['expected_selection_native_key'],baseline['selection_native_key'])
        done=self.worker.execute({'snapshot':partial,'operation':'predict','command':{'type':'GRID_SELECT','card_index':0}},1000)
        self.assertTrue(done['supported'],done);self.assertEqual(done['expected_screen'],'NONE')
        normal=copy.deepcopy(partial);normal.update(screen_name='NONE',is_screen_up=False);normal.pop('grid_card_select_screen_state')
        p=normal['player_state'];chosen=[p['discard_pile'][1],p['discard_pile'][0]]
        p['discard_pile']=[p['discard_pile'][2]]
        for c in chosen:c['cost_for_turn']=0
        p['hand'].extend(chosen)
        imported=self.request(normal,'fingerprint');self.assertTrue(imported['supported'],imported)
        self.assertEqual(done['expected_after'],imported['projection']);self.assertEqual(done['expected_native_key'],imported['native_key'])
        self.assertEqual(done['expected_card_uuids']['hand'],[c['uuid'] for c in p['hand']])
        self.assertEqual(done['expected_exact_rng'],imported['exact_rng'])

    def test_liquid_automatic_return_and_negative_costs_do_not_draw_or_create(self):
        for count,n in ((1,0),(1,1),(2,2)):
            s=self.liquid_base(count,n)
            s['player_state']['creature']['powers']=[{'power_id':'Confusion','amount':-1},{'power_id':'No Draw','amount':-1},{'power_id':'MasterRealityPower','amount':-1},{'power_id':'Corruption','amount':-1}]
            r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':-1}},1000)
            self.assertTrue(r['supported'],r);self.assertEqual(r['expected_screen'],'NONE')
            self.assertEqual(len(r['expected_after']['hand']),2+n);self.assertEqual(r['expected_after']['discard_pile'],[])
            self.assertTrue(all(c['turn_cost']==0 for c in r['expected_after']['hand'][2:]))
            self.assertEqual(r['expected_exact_rng'],{k:v for k,v in s['_headless_rng']['streams'].items() if k in r['expected_exact_rng']})
        for identity,cost in (('Whirlwind',-1),('AscendersBane',-2)):
            s=self.liquid_base(1,1);c=s['player_state']['discard_pile'][0]
            c.update(card_id=identity,cost=cost,cost_for_turn=cost,is_ethereal=cost==-2,base_damage=5 if cost==-1 else -1)
            r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':-1}},1000)
            self.assertTrue(r['supported'],r);self.assertEqual(r['expected_after']['hand'][-1]['turn_cost'],cost)

    def test_liquid_capacity_and_returned_free_card_soul_cost_reset(self):
        s=self.liquid_base(2,2);p=s['player_state']
        for i in range(7):
            c=copy.deepcopy(p['hand'][0]);c['uuid']=f'00000000-0000-0000-0000-{200+i:012d}';p['hand'].append(c)
        r=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'POTION','potion_index':0,'monster_index':-1}},1000)
        self.assertTrue(r['supported'],r);self.assertEqual(len(r['expected_after']['hand']),10)
        self.assertEqual(len(r['expected_after']['discard_pile']),1);self.assertEqual(r['expected_after']['discard_pile'][0]['turn_cost'],1)
        s=self.liquid_base(1,1);p=s['player_state'];p['potions'][0]['id']='Potion Slot'
        c=p['discard_pile'].pop();c['cost_for_turn']=0;p['hand'].append(c)
        played=self.worker.execute({'snapshot':s,'operation':'predict','command':{'type':'CARD','card_index':2,'monster_index':0}},1000)
        self.assertTrue(played['supported'],played);self.assertEqual(played['expected_after']['energy'],p['energy_panel_total_energy'])
        self.assertEqual(played['expected_after']['discard_pile'][-1]['turn_cost'],c['cost'])

    def test_liquid_full_guard_and_bad_grid_variants_are_rejected(self):
        s=self.liquid_snapshot(2,(0,));baseline=self.request(s,'fingerprint');self.assertTrue(baseline['supported'],baseline)
        changed=copy.deepcopy(s);changed['player_state']['creature']['powers'].append({'power_id':'Strength','amount':2})
        other=self.request(changed,'fingerprint');self.assertTrue(other['supported'],other)
        self.assertEqual(baseline['projection'],other['projection']);self.assertNotEqual(baseline['selection_native_key'],other['selection_native_key'])
        for mutate in (
                lambda t:t['grid_card_select_screen_state']['group_cards'].reverse(),
                lambda t:t['grid_card_select_screen_state']['current_action_state']['state'].update(new_cost=1),
                lambda t:t['grid_card_select_screen_state']['selected_cards'][0].update(cost_for_turn=0),
                lambda t:t['grid_card_select_screen_state']['action_queue'].append({'state_class':'savestate.actions.GainEnergyActionState','state':{'energyGain':1}})):
            bad=copy.deepcopy(s);mutate(bad);self.assertFalse(self.request(bad,'fingerprint')['supported'])


if __name__ == "__main__": unittest.main()
