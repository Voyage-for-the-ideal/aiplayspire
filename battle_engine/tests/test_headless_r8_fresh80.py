"""Recheck the ten original Java landings when the signed fresh80 archive is mounted."""
from copy import deepcopy
import json
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import HeadlessCombatBridge
from sts_ai_framework.headless.batch160_next import batch_stops
from sts_ai_framework.headless.repair_ledger import recorded
from sts_ai_framework.headless.state import HeadlessContractError


FRESH80_SHA256='f12a5a119a12a2e521a567e0f35a83b3b60317811413c8ebed55bbcaf6069ad7'


class Fresh80Regression(unittest.TestCase):
    def setUp(self):
        source=os.environ.get('HEADLESS_R8_FRESH80_SOURCE')
        native=os.environ.get('BATTLE_ENGINE_WORKER')
        if not source or not native:
            self.skipTest('requires HEADLESS_R8_FRESH80_SOURCE and BATTLE_ENGINE_WORKER')
        self.cases=list(batch_stops((Path(source)/'fresh80').resolve(strict=True),'fresh80',FRESH80_SHA256,80,14))
        self.worker=Worker(Path(native))
        self.addCleanup(self.worker.close)

    def test_recorded_landings_and_rng_drift(self):
        checked=0
        for case,diag in self.cases:
            if not diag.get('after'):continue
            checked+=1
            with self.subTest(seed=case['seed']):
                row=recorded(case,diag,self.worker)
                self.assertEqual(row['status'],'recorded_step_matches',row)
                bad=deepcopy(diag)
                raw=bad['after']['state']
                payloads=[raw['combat_receipt']['rng']]
                if raw.get('combat'):
                    payloads.append(raw['combat']['snapshot']['_headless_rng'])
                for rng in payloads:
                    misc=rng['streams']['misc']
                    misc['state0']=f"{int(misc['state0'],16)^1:016x}"
                self.assertEqual(recorded(case,bad,self.worker)['status'],'unresolved',
                                 'Modified gameplay RNG was accepted')
        self.assertEqual(checked,10)

    def test_omniscience_menu_prediction_is_bound_to_draw_order(self):
        d=next(d for case,d in self.cases if case['seed']==3628936265791312)
        bridge=HeadlessCombatBridge()
        before=bridge.observe(d['before']['state'],run_id=d['attempt_id'],sequence=d['before']['sequence'])
        command=json.loads(d['prediction']['commands'][0]['command'])
        pred=self.worker.execute({'snapshot':before.snapshot,'operation':'predict','command':command},1000)
        self.assertEqual(bridge.prepare(before,pred).native_method,'chooseGridCard')
        bad=deepcopy(pred)
        bad['expected_menu_picker']['options'].reverse()
        with self.assertRaises(HeadlessContractError):
            bridge.prepare(before,bad)

    def test_shuffle_hooks_execute_in_relic_order_before_scry(self):
        d=next(d for case,d in self.cases if case['seed']==2159501846222815)
        s=deepcopy(d['before']['state']['combat']['snapshot'])
        command=json.loads(d['prediction']['commands'][0]['command'])
        def predict():
            p=self.worker.execute({'snapshot':s,'operation':'predict','command':command},1000)
            self.assertTrue(p['supported'],p)
            self.assertEqual(p['expected_menu_picker']['purpose'],'scry')
            return p['expected_after']
        base=predict()
        relics=s['player_state']['relics']
        before_melange=next(i for i,r in enumerate(relics) if r['relic_id']=='Melange')
        relics.insert(before_melange,{'relic_id':'TheAbacus','counter':-1,'_private':{}})
        self.assertEqual(predict()['block'],base['block']+6)
        relics.append(relics.pop(before_melange))
        self.assertEqual(predict()['block'],base['block'])
