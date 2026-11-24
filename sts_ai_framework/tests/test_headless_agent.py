import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from sts_ai_framework.headless.agent import HeadlessCollector, check_permits


class CollectorLifecycleTest(unittest.TestCase):
    @patch('sts_ai_framework.headless.agent.resource_sample',return_value={'rss_bytes':0,'pss_bytes':0,'cpu_seconds':0,'pids':[]})
    def test_menu_combat_start_records_only_after_validated_native_handoff(self,sample):
        from sts_ai_framework.tests.test_headless_flow import map_frame, gambling_chip_outcome, handoff_fingerprint
        for valid in (False,True):
            with self.subTest(valid=valid),tempfile.TemporaryDirectory() as temp:
                root=Path(temp)
                for name in ('ALLOW_RUN_SMOKE','ALLOW_COLLECTION'):(root/name).write_text('test')
                before=map_frame(first=False);after=gambling_chip_outcome(before,map_target=(1,0))
                before['seed']=111;after['seed']=111
                after['driver_receipt']={'source_state_token':'before','sequence':2,'request_id':3,'method':'chooseMapNode','args':[1,0]}
                engine=Mock(process=None);cpp=Mock(process=None)
                engine.execute.side_effect=[{'supported':True,'state_token':'boot'},
                    {'supported':True,'sequence':1,'state_token':'before','state':before},
                    {'supported':True,'request_id':3,'sequence':2,'state_token':'after','state':after}]
                proof=handoff_fingerprint(after['combat']['snapshot'])
                if not valid:proof.pop('selection_native_key')
                cpp.execute.return_value=proof
                collector=HeadlessCollector.__new__(HeadlessCollector)
                collector.root=root;collector.launcher='fixture-launcher';collector.native='fixture-native';collector.ascension=10
                collector.policy=SimpleNamespace(begin_episode=Mock(),choose=lambda frame:(frame.actions[0],{}))
                collector.max_actions=1;collector.model_sha='fixture';collector.runtime_hashes={};collector.profile_sha='fixture'
                with patch('sts_ai_framework.headless.agent.Worker',side_effect=[engine,cpp]):result=collector.episode('IRONCLAD',111)
                self.assertEqual(result['status'],'aborted')  # Intentional one-action fixture budget; no run is launched.
                self.assertEqual(result['transitions'],int(valid))
                if valid:self.assertIn('Episode action budget reached',result['error'])
                else:
                    self.assertIn('import evidence mismatch',result['error'])
                    diagnostic=json.loads(Path(result['diagnostic']).read_text(encoding='utf-8'));self.assertEqual(diagnostic['fingerprint'],proof)
                self.assertEqual(cpp.execute.call_args.args[0],{'operation':'fingerprint','snapshot':after['combat']['snapshot']})
                engine.close.assert_called_once();cpp.close.assert_called_once()

    def test_both_scoped_permits_are_required_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaises(ValueError): check_permits(root)
            (root / "ALLOW_RUN_SMOKE").write_text("test")
            with self.assertRaises(ValueError): check_permits(root)
            (root / "ALLOW_COLLECTION").write_text("test")
            check_permits(root)
            (root / "ALLOW_RUN_SMOKE").unlink()
            with self.assertRaises(ValueError): check_permits(root)

    def test_stop_request_prevents_next_episode(self):
        with tempfile.TemporaryDirectory() as temp, patch("sts_ai_framework.headless.agent._STOP_REQUESTED", True):
            root = Path(temp)
            for name in ("ALLOW_RUN_SMOKE", "ALLOW_COLLECTION"): (root / name).write_text("test")
            with self.assertRaisesRegex(ValueError, "stop requested"): check_permits(root)

    @patch('sts_ai_framework.headless.agent.resource_sample', return_value={
        'rss_bytes': 0, 'pss_bytes': 0, 'cpu_seconds': 0, 'pids': []})
    @patch('sts_ai_framework.headless.agent.EpisodeWriter')
    def test_boot_and_new_run_failures_keep_unique_diagnostics(self, writer, sample):
        for stage in ('boot', 'new_run'):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                for name in ('ALLOW_RUN_SMOKE', 'ALLOW_COLLECTION'): (root/name).write_text('test')
                collector = HeadlessCollector.__new__(HeadlessCollector)
                collector.root = root; collector.launcher = 'launcher'; collector.native = 'native'
                collector.policy = SimpleNamespace(begin_episode=Mock())
                collector.ascension = 10
                results = []
                for retry in range(2):
                    engine = Mock(process=None); cpp = Mock(process=None)
                    def execute(request, timeout):
                        markers = list((root/'reports').glob('attempt-*.json'))
                        running = [json.loads(p.read_text()) for p in markers if json.loads(p.read_text())['status']=='running']
                        self.assertEqual(len(running), 1)
                        self.assertEqual((running[0]['role'], running[0]['seed']), ('THE_SILENT', 5050102))
                        if stage == 'new_run' and request['operation']=='state':
                            return {'supported': True, 'state_token': 'boot'}
                        raise RuntimeError(stage+' failed')
                    engine.execute.side_effect = execute
                    with patch('sts_ai_framework.headless.agent.Worker', side_effect=[engine, cpp]):
                        result = collector.episode('THE_SILENT', 5050102)
                    results.append(result)
                    self.assertEqual((result['status'], result['censored'], result['transitions']), ('aborted', True, 0))
                    self.assertIsNone(result['episode_id'])
                    marker = json.loads((root/'reports'/f"attempt-{result['attempt_id']}.json").read_text())
                    self.assertEqual(marker['status'], 'aborted')
                    diagnostic = json.loads(Path(result['diagnostic']).read_text())
                    self.assertEqual(diagnostic['stage'], stage)
                    self.assertEqual(diagnostic['attempt_id'], result['attempt_id'])
                    engine.close.assert_called_once(); cpp.close.assert_called_once()
                self.assertNotEqual(results[0]['attempt_id'], results[1]['attempt_id'])
                self.assertNotEqual(results[0]['diagnostic'], results[1]['diagnostic'])
                self.assertTrue(all(Path(r['diagnostic']).is_file() for r in results))
        writer.assert_not_called()
