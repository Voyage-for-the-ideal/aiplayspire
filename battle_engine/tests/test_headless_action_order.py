"""Reduced live-parity regressions for ordered headless card continuations."""
import copy
import os
from pathlib import Path
import unittest
from uuid import UUID

from battle_engine.worker import Worker
from sts_ai_framework.headless.event_receipts import Stream, _shuffle
from sts_ai_framework.tests.test_headless_combat import combat_raw


EXE = Path(os.environ.get('BATTLE_ENGINE_WORKER', Path(__file__).resolve().parents[1] / 'bin/battle-engine-worker.exe'))


def card(identity, uid, cost=1, **extra):
    return dict(card_id=identity, uuid=str(UUID(int=uid)), cost=cost, cost_for_turn=cost,
                times_upgraded=0, **extra)


@unittest.skipUnless(EXE.is_file(), 'Build the native worker')
class HeadlessActionOrderTests(unittest.TestCase):
    def setUp(self):
        self.s = combat_raw(role='DEFECT')['combat']['snapshot']
        self.p = self.s['player_state']
        self.enemies = self.s['cur_map_node_state']['monster_data']
        self.worker = Worker(EXE)
        self.addCleanup(self.worker.close)

    def predict(self):
        result = self.worker.execute({'operation': 'predict', 'snapshot': self.s,
            'command': {'type': 'CARD', 'card_index': 0, 'monster_index': -1}}, 1000)
        self.assertTrue(result['supported'], result)
        return result

    def predict_end(self):
        result = self.worker.execute({'operation': 'predict', 'snapshot': self.s,
            'command': {'type': 'END'}}, 1000)
        self.assertTrue(result['supported'], result)
        return result

    def test_glacier_evokes_oldest_dark_first_before_selecting_the_next_target(self):
        self.p.update(hand=[card('Glacier', 101, 2)], max_orbs=2, orbs=[
            {'lookup_key': 'Dark', 'evoke_amount': 12},
            {'lookup_key': 'Dark', 'evoke_amount': 6}])
        self.enemies.append(copy.deepcopy(self.enemies[0]))
        for monster, hp in zip(self.enemies, (10, 20), strict=True):
            monster['creature'].update(current_health=hp, current_block=0, powers=[])
        result = self.predict()
        self.assertEqual([m['hp'] for m in result['expected_after']['monsters']], [0, 14])

    def test_tempest_hex_draws_before_lightning_target_rng(self):
        self.p.update(hand=[card('Tempest', 101, -1, exhaust=True)], energy_panel_total_energy=1,
                      max_orbs=1, orbs=[{'lookup_key': 'Lightning', 'evoke_amount': 8}])
        self.p['creature']['powers'] = [{'power_id': 'Hex', 'amount': 1}]
        self.enemies[0]['creature'].update(current_health=40, current_block=0, powers=[])
        names = ['Strike_B', 'Defend_B', 'Zap', 'Dualcast', 'Glacier', 'Rainbow']
        self.p['draw_pile'] = [card(name, 200+i) for i, name in enumerate(names)]
        rng = Stream(self.s['_headless_rng']['streams']['card_random'])
        index = rng.bounded(len(names))
        rng.bounded(1)
        names.insert(index, 'Dazed')
        result = self.predict()
        self.assertEqual([c['id'] for c in result['expected_after']['draw_pile']], names)
        self.assertEqual(result['expected_exact_rng']['card_random'], rng.state())

    def test_reboot_draws_first_then_hex_inserts_into_remaining_deck(self):
        self.p.update(hand=[card('Reboot', 101, 0, exhaust=True)], discard_pile=[])
        self.p['creature']['powers'] = [{'power_id': 'Hex', 'amount': 1}]
        names = ['Strike_B', 'Defend_B', 'Zap', 'Dualcast', 'Glacier', 'Rainbow', 'Fusion', 'Loop']
        self.p['draw_pile'] = [card(name, 200+i) for i, name in enumerate(names)]
        rng = Stream(self.s['_headless_rng']['streams']['shuffle'])
        rng.long()  # ShuffleAll's discard shuffle; even an empty pile consumes the call.
        deck = _shuffle(rng.long(), names)
        draw = deck[-4:][::-1]
        remaining = deck[:-4]
        source = self.s['_headless_rng']['streams']['card_random']
        words = Stream(source)
        for _ in range(32):
            # Distinguish the bad order: insertion into the full pile would
            # put Dazed among the cards drawn, rather than leave it in the deck.
            if Stream(words.state()).bounded(len(deck)) >= len(remaining):
                source.update(words.state())
                break
            words.word()
        else:
            self.fail('Fixture needs a discriminating RNG state')
        cards = Stream(source)
        remaining.insert(cards.bounded(len(remaining)), 'Dazed')
        result = self.predict()
        self.assertEqual([c['id'] for c in result['expected_after']['hand']], draw)
        self.assertEqual([c['id'] for c in result['expected_after']['draw_pile']], remaining)
        self.assertEqual(result['expected_exact_rng']['card_random'], cards.state())
 
    def test_reboot_returned_hand_card_clears_temporary_cost(self):
        discounted = card('Defend_B', 102)
        discounted['cost_for_turn'] = 0
        self.p.update(hand=[card('Reboot', 101, 0, exhaust=True), discounted], discard_pile=[])
        result = self.predict()
        returned = next(c for pile in ('hand', 'draw_pile')
                        for c in result['expected_after'][pile] if c['id'] == 'Defend_B')
        self.assertEqual((returned['cost'], returned['turn_cost']), (1, 1))

    def test_unload_discard_soul_clears_temporary_cost(self):
        discounted = card('Defend_G', 102)
        discounted['cost_for_turn'] = 0
        self.p.update(chosen_class_name='THE_SILENT', hand=[card('Unload', 101), discounted])
        result = self.worker.execute({'operation': 'predict', 'snapshot': self.s,
            'command': {'type': 'CARD', 'card_index': 0, 'monster_index': 0}}, 1000)
        self.assertTrue(result['supported'], result)
        found = next(c for c in result['expected_after']['discard_pile']
                     if c['id'] == 'Defend_G')
        self.assertEqual((found['cost'], found['turn_cost']), (1, 1))

    def test_vault_extra_turn_keeps_existing_exhaust_temporary_cost(self):
        omni = card('Omniscience', 102, 3, exhaust=True)
        omni['cost_for_turn'] = 0
        self.p.update(chosen_class_name='WATCHER', hand=[card('Vault', 101, 0, exhaust=True)],
                      exhaust_pile=[omni])
        result = self.predict()
        found = next(c for c in result['expected_after']['exhaust_pile']
                     if c['id'] == 'Omniscience')
        self.assertEqual((found['cost'], found['turn_cost']), (3, 0))

    def test_power_callbacks_follow_storm_before_hex_source_order(self):
        self.p.update(hand=[card('Buffer', 101, 0)], max_orbs=1,
                      orbs=[{'lookup_key': 'Lightning', 'evoke_amount': 8}])
        self.p['creature']['powers'] = [
            {'power_id': 'Storm', 'amount': 1}, {'power_id': 'Hex', 'amount': 1}]
        self.enemies.append(copy.deepcopy(self.enemies[0]))
        for monster in self.enemies:
            monster['creature'].update(current_health=40, current_block=0, powers=[])
        source = self.s['_headless_rng']['streams']['card_random']
        rng = Stream(source)
        target = rng.bounded(2)
        insert = rng.bounded(len(self.p['draw_pile']) + 1)
        result = self.predict()
        self.assertEqual(result['expected_exact_rng']['card_random'], rng.state())
        self.assertEqual([m['hp'] for m in result['expected_after']['monsters']],
                         [32 if i == target else 40 for i in range(2)])
        self.assertEqual(result['expected_after']['draw_pile'][insert]['id'], 'Dazed')

    def test_start_turn_random_card_powers_follow_source_order(self):
        self.p.update(hand=[], discard_pile=[])
        self.p['draw_pile'] = [card('Strike_B', 200+i) for i in range(8)]
        pools={k:['Strike_B'] for k in ('any','attacks','skills','powers','colorless','common','uncommon','rare')}
        pools.update(powers=['Buffer'],colorless=['Panache'],live_common=['Leap'])
        self.s['_combat_card_pools']=pools
        def outcome(powers):
            self.p['creature']['powers'] = [{'power_id':identity,'amount':1} for identity in powers]
            return [c['id'] for c in self.predict_end()['expected_after']['hand']]
        self.assertNotEqual(outcome(('Magnetism','Hello')), outcome(('Hello','Magnetism')))
        self.assertNotEqual(outcome(('Hello','Creative AI')), outcome(('Creative AI','Hello')))

    def test_mayhem_wrapper_plays_the_post_draw_top_card(self):
        self.p.update(chosen_class_name='IRONCLAD', hand=[], discard_pile=[])
        self.p['draw_pile'] = [card('Bash', 200, 2)] + [card('Defend_R', 201+i) for i in range(4)] + [card('Strike_R', 205)]
        self.p['creature']['powers'] = [{'power_id':'Mayhem','amount':1}]
        result = self.predict_end()
        self.assertEqual([c['id'] for c in result['expected_after']['discard_pile']], ['Bash'])

    def test_loop_captures_focus_before_queued_bias_reduction(self):
        self.p.update(hand=[], discard_pile=[], max_orbs=1,
                      orbs=[{'lookup_key':'Frost','evoke_amount':7}])
        self.p['draw_pile'] = [card('Strike_B', 200+i) for i in range(8)]
        self.p['creature']['powers'] = [
            {'power_id':'Focus','amount':4}, {'power_id':'Bias','amount':1}, {'power_id':'Loop','amount':1}]
        self.assertEqual(self.predict_end()['expected_after']['block'], 6)

    def test_corruption_settles_zero_cost_skill_before_end_turn_discard(self):
        discounted=card('Defend_R',101,2);discounted['cost_for_turn']=0
        self.p.update(chosen_class_name='IRONCLAD',hand=[discounted],discard_pile=[])
        self.p['draw_pile']=[card('Strike_R',200+i) for i in range(8)]
        self.p['creature']['powers']=[{'power_id':'Corruption','amount':-1}]
        found=next(c for c in self.predict_end()['expected_after']['discard_pile'] if c['id']=='Defend_R')
        self.assertEqual((found['cost'],found['turn_cost']),(0,0))

    def test_glass_knife_replay_observes_first_copy_damage_decay(self):
        knife=card('Glass Knife',101,base_damage=8)
        self.p.update(chosen_class_name='THE_SILENT',hand=[knife])
        self.p['creature']['powers']=[{'power_id':'DuplicationPower','amount':1}]
        self.enemies[0]['creature'].update(current_health=100,max_health=100,current_block=0,powers=[])
        result=self.worker.execute({'operation':'predict','snapshot':self.s,
            'command':{'type':'CARD','card_index':0,'monster_index':0}},1000)
        self.assertTrue(result['supported'],result)
        self.assertEqual(result['expected_after']['monsters'][0]['hp'],72)

    def test_foreign_influence_plus_zeroes_fixed_cost_but_keeps_x(self):
        # Build a vanilla reward snapshot so there is no dependence on which
        # three attacks the current profile has unlocked.
        self.s.pop('_engine'); self.s.pop('_headless_rng')
        self.s.update(screen_name='CARD_REWARD', is_screen_up=True)
        played = card('ForeignInfluence', 101, 0, exhaust=True)
        self.p.update(chosen_class_name='WATCHER', limbo=[played], card_in_use=played)
        def typed(name, **state):
            return {'state_class': 'savestate.actions.' + name, 'state': state}
        self.s['card_reward_screen_state'] = dict(
            reward_group=[card('Whirlwind', 102, -1), card('Bash', 103, 2), card('Strike_R', 104)],
            skippable=True, current_action_state=typed('ForeignInfluenceActionState', upgraded=True),
            action_queue=[typed('UseCardActionState', card=played, exhaustCard=True, targetIndex=-2)],
            card_queue_state=[])
        for index, identity, cost, turn_cost in ((0, 'Whirlwind', -1, -1), (1, 'Bash', 2, 0)):
            with self.subTest(identity=identity):
                result = self.worker.execute({'operation': 'sequence', 'snapshot': self.s,
                    'actions': [{'action_type': 'SELECT', 'select_index': index}]}, 1000)
                self.assertTrue(result['supported'], result)
                found = result['projection']['hand'][-1]
                self.assertEqual((found['id'], found['cost'], found['turn_cost']), (identity, cost, turn_cost))


if __name__ == '__main__':
    unittest.main()
