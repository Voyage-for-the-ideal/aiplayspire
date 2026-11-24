"""Potion pauses are real decision boundaries, not discarded queue actions."""
import copy
import unittest

from sts_ai_framework.headless.combat import HeadlessCombatBridge, _private_invariants, is_native_combat_selection
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_combat import combat_raw, gambler_raw, liquid_raw


REFRESH={'state_class':'savestate.actions.HandCheckActionState','state':{'owner_index':-1,'derived_refresh':True}}
HEAL={'state_class':'savestate.actions.OrnithopterHealActionState','state':{'source_index':-1,'target_index':-1,'amount':5}}
ELIXIR={'state_class':'savestate.actions.ElixirExhaustActionState',
        'state':{'owner_index':-1,'amount':99,'is_random':False,'any_number':True,'can_pick_zero':True}}


def elixir_raw(selected=()):
    raw=gambler_raw(selected)
    view=raw['combat']['snapshot']['hand_select_screen_state']
    view.update(current_action_state=copy.deepcopy(ELIXIR),action_queue=[copy.deepcopy(REFRESH)])
    raw['interaction']['selection']['purpose']='exhaust'
    return raw


class PotionBoundaryTests(unittest.TestCase):
    def observe(self,raw):
        return HeadlessCombatBridge().observe(raw,run_id='potion-boundary',sequence=0)

    def test_elixir_binds_owner_rules_selection_and_remaining_uuids(self):
        for indices in ((),(0,),(1,0)):
            raw=elixir_raw(indices)
            self.assertTrue(is_native_combat_selection(raw));self.observe(raw)
        for mutate in (lambda r:r['cardSelection']['selected'][0].update(uuid='someone-else'),
                       lambda r:r['interaction']['selection'].update(purpose='gamble'),
                       lambda r:r['combat']['snapshot']['hand_select_screen_state']['current_action_state']['state'].update(is_random=True),
                       lambda r:r['combat']['snapshot']['hand_select_screen_state']['current_action_state']['state'].update(owner_index=0),
                       lambda r:r['combat']['snapshot']['hand_select_screen_state'].update(action_queue=[])):
            raw=elixir_raw((0,));mutate(raw)
            with self.assertRaises(HeadlessContractError):self.observe(raw)

    def test_heal_requires_exact_source_owner_target_order_and_relic(self):
        for raw in (elixir_raw(),gambler_raw(),liquid_raw()):
            s=raw['combat']['snapshot'];view=s.get('hand_select_screen_state') or s['grid_card_select_screen_state']
            view['action_queue']=[copy.deepcopy(REFRESH),copy.deepcopy(HEAL)]
            with self.assertRaises(HeadlessContractError):self.observe(raw)
            s['player_state']['relics'].append({'relic_id':'Toy Ornithopter','counter':-1,'_private':{}})
            raw['player']['relics'].append({'id':'Toy Ornithopter','name':'Toy Ornithopter','counter':-1})
            self.observe(raw)
            for q in ([REFRESH],[HEAL,REFRESH],[REFRESH,HEAL,HEAL],
                      [REFRESH,dict(HEAL,state=dict(HEAL['state'],amount=4))],
                      [REFRESH,dict(HEAL,state=dict(HEAL['state'],target_index=0))]):
                view['action_queue']=q
                with self.assertRaises(HeadlessContractError):self.observe(raw)

    def test_orange_requires_three_real_source_booleans_and_rejects_unresolved_trigger(self):
        s=combat_raw()['combat']['snapshot']
        flags={f'OrangePellets.{k}':False for k in ('ATTACK','SKILL','POWER')}
        s['player_state']['relics'].append({'relic_id':'OrangePellets','counter':-1,'_private':flags})
        reference=_private_invariants(s)
        flags['OrangePellets.POWER']=True
        self.assertEqual(reference,_private_invariants(s))
        for facts in ({},dict(flags,**{'OrangePellets.ATTACK':1}),{k:True for k in flags}):
            s['player_state']['relics'][-1]['_private']=facts
            with self.assertRaises(HeadlessContractError):_private_invariants(s)
