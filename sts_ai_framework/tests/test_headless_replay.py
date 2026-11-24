import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from sts_ai_framework.headless.collection import EpisodeWriter
from sts_ai_framework.headless.replay import verify_replay
from sts_ai_framework.tests.test_headless_interaction import shop, card


class ReplayTest(unittest.TestCase):
    def test_menu_to_gambling_chip_replay_reimports_guard_and_rechecks_path(self):
        from sts_ai_framework.tests.test_headless_flow import map_frame, gambling_chip_outcome, handoff_fingerprint
        for broken in ('none','guard','path'):
            with self.subTest(broken=broken),tempfile.TemporaryDirectory() as temp:
                root=Path(temp);(root/'ALLOW_COLLECTION').write_text('fixture')
                native=root/'fixture-worker';native.write_bytes(b'fake worker; no executable')
                writer=EpisodeWriter(root,role='IRONCLAD',seed=111,policy={})
                before=map_frame(first=False);after=gambling_chip_outcome(before,map_target=(1,0))
                before['seed']=111;after['seed']=111
                after['driver_receipt']={'source_state_token':'before','sequence':2,'method':'chooseMapNode','args':[1,0]}
                if broken=='path':after['interaction']['flow']['path']=[]
                proof=handoff_fingerprint(after['combat']['snapshot'])
                if broken=='guard':proof.pop('selection_native_key')
                writer.observation({'sequence':1,'state_token':'before','state':before})
                writer.transition(before_sequence=1,after_sequence=2,method='chooseMapNode',args=[1,0],effect='APPLIED',policy={})
                writer.observation({'sequence':2,'state_token':'after','state':after})
                path=writer.finish(error='fixture prefix')['path']
                with patch('sts_ai_framework.headless.replay.Worker') as worker:
                    worker.return_value.execute.return_value=proof
                    if broken=='none':self.assertEqual(verify_replay(path,native,require_complete=False)['checks'],{'combat':0,'menu':1})
                    else:
                        with self.assertRaises(ValueError):verify_replay(path,native,require_complete=False)
                    request=worker.return_value.execute.call_args.args[0]
                    self.assertEqual(request,{'operation':'fingerprint','snapshot':after['combat']['snapshot']})

    def test_native_selection_replay_reimports_state_and_requires_full_guard(self):
        from sts_ai_framework.headless.combat import HeadlessCombatBridge, _native_selection_evidence
        from sts_ai_framework.tests.test_headless_combat import gambler_raw, liquid_raw, response, selection_fingerprint
        for liquid in (False,True):
            for missing_guard in (False,True):
                with self.subTest(liquid=liquid,missing_guard=missing_guard), tempfile.TemporaryDirectory() as temp:
                    root=Path(temp);(root/'ALLOW_COLLECTION').write_text('fixture')
                    native=root/'fixture-worker';native.write_bytes(b'fake worker; no executable')
                    writer=EpisodeWriter(root,role='IRONCLAD',seed=111,policy={})
                    before=liquid_raw(2) if liquid else gambler_raw()
                    after=liquid_raw(2,(1,)) if liquid else gambler_raw((0,))
                    before['seed']=111;after['seed']=111
                    method='chooseGridCard' if liquid else 'chooseHandCard';index=1 if liquid else 0
                    after['driver_receipt']={'source_state_token':'before','sequence':2,'method':method,'args':[index]}
                    bridge=HeadlessCombatBridge();a=bridge.observe(before,run_id='fixture',sequence=1);b=bridge.observe(after,run_id='fixture',sequence=2)
                    predicted=response(a,b)
                    predicted['commands'][0]['command']=json.dumps({'type':'GRID_SELECT' if liquid else 'HAND_SELECT','card_index':index})
                    predicted.update(expected_screen=b.snapshot['screen_name'],expected_native_key=None,
                        expected_selection=_native_selection_evidence(b.snapshot),expected_selection_native_key=['full-state-guard'],
                        expected_card_uuids={pile:[c['uuid'] for c in b.snapshot['player_state'][pile]] for pile in ('hand','draw_pile','discard_pile','exhaust_pile')})
                    fingerprint=selection_fingerprint(b,predicted)
                    if missing_guard:fingerprint.pop('selection_native_key')
                    writer.observation({'sequence':1,'state_token':'before','state':before})
                    writer.transition(before_sequence=1,after_sequence=2,method=method,args=[index],effect='APPLIED',policy={})
                    writer.observation({'sequence':2,'state_token':'after','state':after})
                    path=writer.finish(error='fixture prefix')['path']
                    with patch('sts_ai_framework.headless.replay.Worker') as worker:
                        worker.return_value.execute.side_effect=[predicted,fingerprint]
                        if missing_guard:
                            with self.assertRaisesRegex(ValueError,'Replay effect failed'):verify_replay(path,native,require_complete=False)
                        else:
                            self.assertEqual(verify_replay(path,native,require_complete=False)['checks'],{'combat':1,'menu':0})
                        self.assertEqual(worker.return_value.execute.call_args_list[1].args[0]['operation'],'fingerprint')
                        self.assertEqual(worker.return_value.execute.call_args_list[1].args[0]['snapshot'],b.snapshot)

    def episode(self, root, *, wrong_method=False, wrong_gold=False):
        (root / "ALLOW_COLLECTION").write_text("fixture")
        writer = EpisodeWriter(root, role="IRONCLAD", seed=111, policy={})
        before = shop(); before["seed"] = 111
        after = copy.deepcopy(before); after["player"]["gold"] -= 19 if wrong_gold else 20
        after["player"]["masterDeck"].append(card(id="Bash", uuid="00000000-0000-0000-0000-000000000112", timesUpgraded=0, upgraded=False))
        after["interaction"]["shop"]["items"][0]["sold_out"] = True
        method = "openChest" if wrong_method else "buyCard"
        after["driver_receipt"] = {"source_state_token": "before", "sequence": 2, "method": method, "args": [2]}
        writer.observation({"sequence": 1, "state_token": "before", "state": before})
        writer.transition(before_sequence=1, after_sequence=2, method=method, args=[2], effect="APPLIED", policy={})
        writer.observation({"sequence": 2, "state_token": "after", "state": after})
        return writer.finish(error="fixture prefix")["path"]

    def test_replay_rechecks_effect_not_only_recorded_applied_string(self):
        for wrong_method, wrong_gold in ((False, False), (True, False), (False, True)):
            with self.subTest(wrong_method=wrong_method, wrong_gold=wrong_gold), tempfile.TemporaryDirectory() as temp:
                root = Path(temp); native = root / "unused-worker"; native.write_bytes(b"not executed for menu transitions")
                path = self.episode(root, wrong_method=wrong_method, wrong_gold=wrong_gold)
                if wrong_method or wrong_gold:
                    with self.assertRaises(ValueError): verify_replay(path, native, require_complete=False)
                else:
                    result = verify_replay(path, native, require_complete=False)
                    self.assertEqual(result["checks"], {"combat": 0, "menu": 1})
                    self.assertTrue(result["replay_verified"])
