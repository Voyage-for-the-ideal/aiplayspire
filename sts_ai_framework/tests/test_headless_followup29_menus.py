"""Read-only fixtures for the original followup29 menu stops."""
from copy import deepcopy
import json
import os
from pathlib import Path
import unittest

from sts_ai_framework.headless.event_receipts import Stream, astrolabe_selection_receipt, verify_followup29_landing
from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.headless.live_confirmation import SeededMatcher
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_flow import FlowCase, boss, next_act


class DeclinedBossRelic(FlowCase):
    def test_closed_sentinel_followed_by_fresh_act3_map_and_a10_heal(self):
        source=boss(act=2,treasure=True)
        source['interaction']['flow']['path']=[{'x':4,'y':14},{'x':-1,'y':15}]
        source['interaction']['chest']={'boss':True,'open':False,'gold_reward':False,'gold_amount':0,'relic_tier':None}
        after=next_act(source)
        after.update(room='TreasureRoomBoss')
        after['map']['current']={'x':-1,'y':15}
        after['interaction']['chest']=deepcopy(source['interaction']['chest'])
        plan=self.plan(source)
        self.assertEqual(self.effect(plan,after),'APPLIED')
        wrong=deepcopy(after)
        wrong['player']['hp']-=1
        self.assertEqual(self.effect(plan,wrong,2),'UNVERIFIED')
        wrong=deepcopy(after)
        wrong['interaction'].pop('chest')
        self.assertEqual(self.effect(plan,wrong,3),'UNVERIFIED')


class ArchivedMenuStops(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=Path(os.environ.get('HEADLESS_R4_ARCHIVE',Path(__file__).resolve().parents[2]/'debug/headless-batch160-fixes/r4-followup'))
        cls.cases={}
        for p in path.rglob('failure-*.json'):
            case=json.loads(p.read_bytes())
            cls.cases[case['seed']]=case
        if not cls.cases:raise unittest.SkipTest('optional r4 followup archive not mounted')

    def test_original_dialog_and_act_transitions_reject_lookalikes(self):
        acts=(5050300,5050323,5050401)
        dialogs=(5050224,5050265,5050402,5050417,5050420,5050435,5050407)
        for seed in (*acts,*dialogs):
            with self.subTest(seed=seed):
                case=self.cases[seed]
                a=case['before']['state'];b=case['after']['state']
                bridge=IroncladInteractionBridge(characters=(a['player']['className'],))
                initial=bridge.observe(a,run_id='r4-archive',sequence=0)
                action=next(x for x in initial.actions if (x.native_method,x.native_args)==(case['method'],tuple(case['args'])))
                plan=bridge.prepare(initial,action.api_action)
                self.assertEqual(verify_effect(plan,bridge.observe(b,run_id='r4-archive',sequence=1)).status,'APPLIED')
                wrong=deepcopy(b)
                if seed in acts:wrong['player']['hp']-=1
                else:
                    streams=wrong['combat_receipt']['rng']['streams']
                    rng=Stream(streams['event']);rng.long();streams['event']=rng.state()
                try: accepted=verify_effect(plan,bridge.observe(wrong,run_id='r4-archive',sequence=2)).status=='APPLIED'
                except HeadlessContractError:accepted=False
                self.assertFalse(accepted)

    def test_astrolabe_late_landing_requires_three_source_uuids_and_exact_rng(self):
        case=self.cases[5050406];a=deepcopy(case['before']['state']);old=case['after']['state']
        index=case['args'][0];targets=[*a['cardSelection']['selected'],a['cardSelection']['options'][index]]
        ids={c['uuid'] for c in targets}
        # Small fixture catalog; the live probe exports the actual pool order.
        pool=['Backflip','Footwork','Adrenaline']
        a['interaction']['astrolabe']={'catalog':{'common':pool[:1],'uncommon':pool[1:2],'rare':pool[2:]},
                                    'colors':{uid:'GREEN' for uid in ids}}
        b=deepcopy(old)
        rng=Stream(a['combat_receipt']['rng']['streams']['misc'])
        kept=[c for c in b['player']['masterDeck'] if c['uuid'] not in ids]
        for i in range(3):
            uuid=f'cafebabe-aaaa-4aaa-8aaa-{i:012}'
            kept.append(dict(targets[i],index=len(kept),id=pool[rng.bounded(3)],uuid=uuid,
                             upgraded=True,timesUpgraded=1))
        b['player']['masterDeck']=kept
        b['combat_receipt']['rng']['streams']['misc']=rng.state()
        self.assertIs(astrolabe_selection_receipt(a,b,index),True)
        self.assertTrue(verify_followup29_landing(SeededMatcher(),case,b,before=a))
        self.assertIs(astrolabe_selection_receipt(a,old,index),False)
        wrong=deepcopy(b);wrong['player']['masterDeck'][-1]['uuid']=targets[0]['uuid']
        self.assertFalse(verify_followup29_landing(SeededMatcher(),case,wrong,before=a))
        wrong=deepcopy(b);rng.long();wrong['combat_receipt']['rng']['streams']['misc']=rng.state()
        self.assertFalse(verify_followup29_landing(SeededMatcher(),case,wrong,before=a))
