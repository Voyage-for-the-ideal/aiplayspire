"""Read-only event tests. With the original archive present, also reject one altered landing per seed."""
import copy
import json
import os
import unittest
from pathlib import Path

from sts_ai_framework.headless.event_receipts import Stream, _opening_cards, _SILENT_SRC, source_menu
from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_interaction import event


class EventReceipts(unittest.TestCase):
    def test_opening_card_stream_exact_state_order_and_catalog(self):
        initial={'state0':'ab6f9febdf43ec68','state1':'31a1ceefd5557b85','counter':0}
        rng=Stream(initial)
        pools={k:v.split('|')[::-1] for k,v in _SILENT_SRC.items()}
        self.assertEqual(_opening_cards(rng,pools),['Outmaneuver','Blur','Expertise'])
        self.assertEqual(rng.state(),{'state0':'bfe8024fb07b282c','state1':'f5449bf25a6e87d6','counter':9})
        for _ in range(9):rng.rewind_word()
        self.assertEqual((rng.a,rng.b),(int(initial['state0'],16),int(initial['state1'],16)))
        pools['uncommon'].reverse()
        self.assertNotEqual(_opening_cards(Stream(initial),pools),['Outmaneuver','Blur','Expertise'])

    def test_designer_keeps_deferred_full_service_upgrade_and_transform(self):
        s=event();e=s['interaction']['event'];e.update(id='Designer',class_name='Designer')
        e['choices']=[dict(e['choices'][0],button_index=i,action_index=i) for i in range(4)]
        s['interaction']['designer']={'phase':'MAIN','upgrade_one':True,'remove_one':False}
        choices=source_menu(s)['choices']
        self.assertEqual([x['type'] for x in choices[1]['outcomes'][0]['effects']],['lose_gold','select_cards','random_transform'])
        self.assertEqual(choices[2]['outcomes'][0]['effects'][-1],{'type':'random_upgrade','amount':1})
        s['interaction']['designer']['phase']='INTRO'
        with self.assertRaises(HeadlessContractError):source_menu(s)

    def test_original_18_and_counterexamples_if_archive_present(self):
        roots=Path(os.environ.get('HEADLESS_ORIGINAL_160_BATCH',
                                  Path(__file__).resolve().parents[2]/'debug/headless-batch160-fixes'))
        if not roots.is_dir():self.skipTest('optional original 160-run archive not mounted')
        seeds={5050265,5050283,5050204,5050281,5050219,5050275,5050236,5050202,5050320,
               5050288,5050256,5050360,5050303,5050311,5050344,5050359,5050326,5050240}
        seen=set()
        for path in roots.glob('lane*/reports/failure-*.json'):
            d=json.loads(path.read_bytes());seed=d['seed']
            if seed not in seeds:continue
            with self.subTest(seed=seed):
                seen.add(seed);a=d['before']['state'];b=d['after']['state'];bridge=IroncladInteractionBridge(characters=[d['role']])
                x=bridge.observe(a,run_id='archive',sequence=0)
                command=next(c.api_action for c in x.actions if c.native_args==tuple(d['args']))
                plan=bridge.prepare(x,command)
                def applied(raw):
                    try:return verify_effect(plan,bridge.observe(raw,run_id='archive',sequence=1)).status=='APPLIED'
                    except HeadlessContractError:return False
                self.assertTrue(applied(b))
                bad=copy.deepcopy(b);rng=bad['combat_receipt']['rng']['streams'];stream=Stream(rng['shuffle'])
                stream.long();rng['shuffle']=stream.state()
                self.assertFalse(applied(bad),'extra gameplay RNG draw accepted')
                bad=copy.deepcopy(b);name=x.state.event.class_name
                if name=='NeowEvent' and seed==5050326:bad['rewards'][2]['cards'].reverse()
                elif name=='NeowEvent':bad['player']['gold']-=300
                elif name=='ForgottenAltar':bad['player']['masterDeck'][-1]['id']='Injury'
                elif name=='DeadAdventurer':bad['interaction']['event']['phase']='FAIL'
                elif name=='MaskedBandits':bad['player']['hp']-=2
                elif name=='Designer':bad['interaction']['event']['choices'][0]['enabled']=not bad['interaction']['event']['choices'][0]['enabled']
                else:bad['player']['masterDeck'][0]['uuid']='11111111-2222-4333-8444-555555555555'
                self.assertFalse(applied(bad),'wrong event outcome accepted')
        self.assertEqual(seen,seeds)
