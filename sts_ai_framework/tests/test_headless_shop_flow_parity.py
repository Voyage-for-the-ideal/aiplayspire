"""Shop-overlay return and Dolly ownership contracts, no engine or fresh run."""
import copy
import json
import unittest

from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.models import GameAction
from sts_ai_framework.tests.test_headless_flow import enrich
from sts_ai_framework.tests.test_headless_interaction import raw, shop, selection
from sts_ai_framework.tests.test_headless_state import card
from sts_ai_framework.tests.test_headless_combat import combat_raw


def shop_rewards():
    s = enrich(raw('REWARD',room='ShopRoom',screen='COMBAT_REWARD'),route='shop_return',
        previous_screen='SHOP',cancel_visible=True)
    s['interaction'].update(rewards=[],can_proceed=True,return_shop=True)
    s['combat_receipt']={'rng':copy.deepcopy(combat_raw()['combat']['snapshot']['_headless_rng'])}
    return s


def shop_landing(source):
    s = enrich(shop(items=[]),route='none',previous_screen='NONE',cancel_visible=True)
    for key in ('act','ascension','floor','map','room','roomPhase','observedKeys','player'):
        s[key] = copy.deepcopy(source[key])
    s.update(phase='SHOP',screen='SHOP')
    s['interaction']['flow']['dungeon'] = source['interaction']['flow']['dungeon']
    s['interaction']['flow']['path'] = copy.deepcopy(source['interaction']['flow']['path'])
    s['combat_receipt'] = copy.deepcopy(source['combat_receipt'])
    return s


class ShopFlowParityTest(unittest.TestCase):
    def test_nested_reward_parent_must_be_restored_by_the_source_not_guessed(self):
        for parent in ('COMBAT_REWARD','NONE'):
            with self.subTest(parent=parent):
                source=shop_rewards();source['interaction']['flow']['previous_screen']=parent
                with self.assertRaises(HeadlessContractError):
                    IroncladInteractionBridge().observe(source,run_id='shop',sequence=0)

    def test_restored_parent_does_not_claim_the_declined_card_reward(self):
        source=shop_rewards()
        source['interaction']['rewards']=[{'native_index':0,'type':'CARD','card_uuids':[card(1)['uuid']]}]
        b=IroncladInteractionBridge();before=b.observe(source,run_id='shop',sequence=0)
        plan=b.prepare(before,GameAction(type='proceed'))
        after=shop_landing(source)
        self.assertEqual(verify_effect(plan,b.observe(after,run_id='shop',sequence=1)).status,'APPLIED')
        after['player']['masterDeck'].append(card(2))
        self.assertEqual(verify_effect(plan,b.observe(after,run_id='shop',sequence=2)).status,'UNVERIFIED')

    def test_visible_shop_cancel_is_an_explicit_proceed_not_map(self):
        b = IroncladInteractionBridge();s = shop_rewards()
        before = b.observe(s,run_id='shop',sequence=0)
        plan = b.prepare(before,GameAction(type='proceed'))
        self.assertEqual(json.loads(plan.action.evidence_json),{'kind':'flow_proceed','route':'shop_return'})
        self.assertEqual(verify_effect(plan,b.observe(shop_landing(s),run_id='shop',sequence=1)).status,'APPLIED')

    def test_shop_return_requires_all_source_screen_stack_and_cancel_facts(self):
        for mutate in (lambda s:s.update(room='MonsterRoom'),lambda s:s.update(screen='CARD_REWARD'),
                lambda s:s['interaction'].update(return_shop=False),
                lambda s:s['interaction']['flow'].update(previous_screen='MAP'),
                lambda s:s['interaction']['flow'].update(cancel_visible=False),
                lambda s:s['interaction']['flow'].update(route='map')):
            with self.subTest(mutate=mutate):
                s = shop_rewards();mutate(s)
                with self.assertRaises(HeadlessContractError):
                    IroncladInteractionBridge().observe(s,run_id='shop',sequence=0)

    def test_hidden_cancel_does_not_advertise_shop_return(self):
        s = shop_rewards();s['interaction'].update(can_proceed=False,return_shop=False)
        s['interaction']['flow'].update(route='map',cancel_visible=False)
        f = IroncladInteractionBridge().observe(s,run_id='shop',sequence=0)
        self.assertFalse(f.actions);self.assertFalse(f.state.can_proceed)

    def test_shop_return_rejects_every_gameplay_rng_stream_change(self):
        for name in shop_rewards()['combat_receipt']['rng']['streams']:
            with self.subTest(stream=name):
                b=IroncladInteractionBridge();source=shop_rewards()
                before=b.observe(source,run_id='shop',sequence=0);plan=b.prepare(before,GameAction(type='proceed'))
                after=shop_landing(source);after['combat_receipt']['rng']['streams'][name]['counter']+=1
                self.assertEqual(verify_effect(plan,b.observe(after,run_id='shop',sequence=1)).status,'UNVERIFIED')

    def test_shop_return_requires_valid_complete_source_rng_on_both_frames(self):
        for mutate in (lambda s:s.pop('combat_receipt'),
                lambda s:s['combat_receipt']['rng']['streams'].pop('map'),
                lambda s:s['combat_receipt']['rng'].update(policy='unknown'),
                lambda s:s['combat_receipt']['rng']['streams']['ai'].update(state0='bad'),
                lambda s:s['combat_receipt']['rng']['streams']['ai'].update(counter=True)):
            with self.subTest(mutate=mutate):
                source=shop_rewards();mutate(source)
                with self.assertRaises(HeadlessContractError):
                    IroncladInteractionBridge().observe(source,run_id='shop',sequence=0)
                b=IroncladInteractionBridge();source=shop_rewards()
                before=b.observe(source,run_id='shop',sequence=0);plan=b.prepare(before,GameAction(type='proceed'))
                after=shop_landing(source);mutate(after)
                self.assertEqual(verify_effect(plan,b.observe(after,run_id='shop',sequence=1)).status,'UNVERIFIED')

    def test_shop_return_does_not_compare_the_cosmetic_math_stream(self):
        b=IroncladInteractionBridge();source=shop_rewards()
        before=b.observe(source,run_id='shop',sequence=0);plan=b.prepare(before,GameAction(type='proceed'))
        after=shop_landing(source);after['combat_receipt']['rng']['math']['state0']='0000000000000001'
        self.assertEqual(verify_effect(plan,b.observe(after,run_id='shop',sequence=1)).status,'APPLIED')

    def test_shop_return_preserves_player_inventory_path_and_known_keys(self):
        for mutate in (lambda s:s.update(floor=s['floor']+1),
                lambda s:s['interaction']['flow']['path'].append({'x':1,'y':1}),
                lambda s:s['player'].update(gold=s['player']['gold']+1),
                lambda s:s['player'].update(hp=s['player']['hp']-1),
                lambda s:s['player']['masterDeck'].append(card(90)),
                lambda s:s['player']['relics'].append({'id':'Anchor','name':'anchor','counter':-1}),
                lambda s:s['observedKeys'].update(ruby=False),
                lambda s:s.update(room='EventRoom'),lambda s:s.update(roomPhase='INCOMPLETE')):
            with self.subTest(mutate=mutate):
                b = IroncladInteractionBridge();source=shop_rewards()
                before = b.observe(source,run_id='shop',sequence=0);plan=b.prepare(before,GameAction(type='proceed'))
                after=shop_landing(source);mutate(after)
                self.assertNotEqual(verify_effect(plan,b.observe(after,run_id='shop',sequence=1)).status,'APPLIED')

    def test_shop_return_does_not_accept_map_or_reward_loop_landing(self):
        for phase,screen in (('REWARD','COMBAT_REWARD'),('PROCEED','NONE'),('MAP','MAP')):
            b = IroncladInteractionBridge();source=shop_rewards()
            before=b.observe(source,run_id='shop',sequence=0);plan=b.prepare(before,GameAction(type='proceed'))
            after=shop_landing(source);after.update(phase=phase,screen=screen)
            # Use the original reward frame for a no-effect repeated overlay.
            if phase=='REWARD':after=copy.deepcopy(source)
            if phase=='MAP':
                # A passive invalid landing need not pass MAP choice enumeration.
                observed=b._mapper.map_state(after,run_id='shop',sequence=1)
                from sts_ai_framework.headless.flow import verify_flow
                from types import SimpleNamespace
                f=SimpleNamespace(raw_json=json.dumps(after),state=observed)
                self.assertFalse(verify_flow(plan,f,{'kind':'flow_proceed','route':'shop_return'}))
            else:
                if phase=='PROCEED':after['interaction']['can_proceed']=False
                self.assertNotEqual(verify_effect(plan,b.observe(after,run_id='shop',sequence=1)).status,'APPLIED')

    def test_dolly_old_purge_and_gold_loss_remain_unverified(self):
        b = IroncladInteractionBridge()
        target=card(1,id='Finisher',timesUpgraded=1)
        source=selection('duplicate',options=[target],auto_confirm=True)
        source.update(room='ShopRoom');source['player'].update(gold=6,masterDeck=[target])
        before=b.observe(source,run_id='mirror',sequence=0)
        plan=b.prepare(before,GameAction(type='choose',choice_index=0))
        bad=shop(items=[],gold=0);bad.update(room='ShopRoom');bad['player']['masterDeck']=[]
        bad['interaction']['shop']['purge_cards']=[]
        self.assertNotEqual(verify_effect(plan,b.observe(bad,run_id='mirror',sequence=1)).status,'APPLIED')


if __name__=='__main__':unittest.main()
