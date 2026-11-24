"""Exact-state checks for the event stops left by the release-2 run."""
import copy
import hashlib
import json
import os
from pathlib import Path
import unittest

from sts_ai_framework.headless.event_receipts import (
    Stream, _DEFECT_SRC, _library_offer_ids, verify_release2_landing,
)
from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.headless.live_confirmation import SeededMatcher
from sts_ai_framework.headless.state import HeadlessContractError


SEEDS = {
    5050211, 5050212, 5050216, 5050224, 5050236, 5050243, 5050244, 5050272,
    5050277, 5050278, 5050299, 5050313, 5050323, 5050333, 5050344, 5050359,
}


def _plans(diagnostic, method, args):
    bridge = IroncladInteractionBridge(characters=[diagnostic['role']])
    source = bridge.observe(diagnostic['before']['state'], run_id='release-2', sequence=0)
    action, = (a for a in source.actions if (a.native_method,a.native_args)==(method,tuple(args)))
    return bridge,bridge.prepare(source,action.api_action)


class EventBatch160Test(unittest.TestCase):
    def test_library_pool_order_duplicate_rerolls_and_exact_rng(self):
        start={'state0':'d4918c24c8c09813','state1':'4ee34a2a21e55228','counter':275}
        pools={tier:order.split('|')[::-1] for tier,order in _DEFECT_SRC.items()}
        rng=Stream(start)
        result=_library_offer_ids(rng,pools,4,3)
        self.assertEqual(result,['FTL','Heatsinks','Melter','Doom and Gloom','Capacitor','Skim',
            'Compile Driver','Ball Lightning','Fusion','Barrage','Blizzard','Self Repair','Hologram',
            'Beam Cell','Scrape','Gash','Force Field','Steam','Sweeping Beam','Conserve Battery'])
        self.assertEqual(rng.state(),{'state0':'581ee645c534ba67','state1':'3188ea7a25a8865e','counter':339})
        unflipped={tier:pool[::-1] for tier,pool in pools.items()}
        self.assertNotEqual(result,_library_offer_ids(Stream(start),unflipped,4,3))

    def test_actual_diagnostic_rng_and_result_falsification(self):
        root=Path(__file__).resolve().parents[2]/'debug'/'headless-batch160-fixes'
        archive=Path(os.environ.get('HEADLESS_RELEASE2_SOURCE_BATCH',root/'latest-v2'))
        if not archive.is_dir():self.skipTest('Optional latest 160-run diagnostic archive not mounted')
        report_path=os.environ.get('HEADLESS_RELEASE2_SOURCE_REPORT')
        path=Path(report_path) if report_path else next((root/'reports'/'release1-160').glob('*.json'))
        report=json.loads(path.read_bytes())
        seen=set()
        for case in report['cases']:
            if case['seed'] not in SEEDS:continue
            with self.subTest(seed=case['seed']):
                seen.add(case['seed'])
                name=case['diagnostic'].rsplit('/',1)[-1]
                encoded=(archive/f'lane{case["lane"]}'/'reports'/name).read_bytes()
                self.assertEqual(hashlib.sha256(encoded).hexdigest(),case['diagnostic_sha256'])
                diagnostic=json.loads(encoded)
                self.assertEqual((diagnostic['role'],diagnostic['seed']),(case['role'],case['seed']))
                command=case['coordinates']['new_action']
                bridge,plan=_plans(diagnostic,command['menu_method'],command['args'])
                actual=diagnostic['after']['state']
                def accepts(raw):
                    try:return verify_effect(plan,bridge.observe(raw,run_id='release-2',sequence=1)).status=='APPLIED'
                    except HeadlessContractError:return False
                self.assertTrue(accepts(actual))
                bad=copy.deepcopy(actual)
                streams=bad['combat_receipt']['rng']['streams']
                shifted=Stream(streams['shuffle']);shifted.long();streams['shuffle']=shifted.state()
                # A different observation uses its own mapper to avoid
                # violating the same-sequence immutability guard first.
                bridge,plan=_plans(diagnostic,command['menu_method'],command['args'])
                self.assertFalse(accepts(bad),'accepted extra RNG draw')
                bad=copy.deepcopy(actual)
                if case['event']=='Vampires':
                    bad['player']['masterDeck'][-1]['baseDamage']+=1
                elif case['event']=='Colosseum':
                    bad['monsters'][0]['maxHp']+=1
                    self.assertFalse(verify_release2_landing(SeededMatcher(),diagnostic,actual),
                                     'stale dialog accepted as the real live decision')
                elif case['event']=='The Library':
                    bad['cardSelection']['options'][0]['id']='Defend_B'
                elif case['event']=='Masked Bandits':
                    bad['cardRewardOptions'][0]['id']='Impatience'
                else:
                    bad['player']['gold']+=1
                bridge,plan=_plans(diagnostic,command['menu_method'],command['args'])
                self.assertFalse(accepts(bad),'accepted changed gameplay landing')
        self.assertEqual(seen,SEEDS)
