import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from sts_ai_framework.headless.suite import run_suite, schedule


class SuiteTest(unittest.TestCase):
    def test_default_plan_is_fixed_ten_unique_seeds_per_role(self):
        plan=schedule(); self.assertEqual(len(plan),40)
        self.assertEqual(len({c['seed'] for c in plan}),40)
        for role in ('IRONCLAD','THE_SILENT','DEFECT','WATCHER'):
            self.assertEqual(sum(c['role']==role for c in plan),10)
        self.assertEqual(schedule(), plan)

    @patch('sts_ai_framework.headless.suite.check_permits')
    def test_difference_stops_batch_and_requires_explicit_retry(self, permits):
        with tempfile.TemporaryDirectory() as temp:
            calls=[]
            def episode(role,seed,*,attempt_id):
                marker=json.loads(path.read_text())['attempts'][-1]
                self.assertEqual((marker['attempt_id'],marker['result']['status']), (attempt_id,'running'))
                calls.append((role,seed)); return {'status':'aborted','last_floor':3,'transitions':4}
            collector=SimpleNamespace(root=Path(temp),ascension=10,max_actions=3000,episode=episode)
            path=Path(temp)/'status.json'
            self.assertEqual(run_suite(collector,path,per_role=1),1)
            with self.assertRaisesRegex(ValueError,'inspect/fix'): run_suite(collector,path,per_role=1)
            self.assertEqual(len(calls),1)
            self.assertEqual(run_suite(collector,path,per_role=1,retry_failed=True),1)
            self.assertEqual(calls[0],calls[1])
            self.assertEqual(len(json.loads(path.read_text())['attempts']),2)

    @patch('sts_ai_framework.headless.suite.check_permits')
    def test_resume_skips_completed_cases_and_rejects_plan_change(self, permits):
        with tempfile.TemporaryDirectory() as temp:
            calls=[]
            def episode(role,seed,*,attempt_id):
                calls.append((role,seed)); return {'status':'complete','last_floor':3,'transitions':4}
            collector=SimpleNamespace(root=Path(temp),ascension=10,max_actions=3000,episode=episode)
            path=Path(temp)/'status.json'
            self.assertEqual(run_suite(collector,path,per_role=1),0)
            self.assertEqual(run_suite(collector,path,per_role=1),0)
            self.assertEqual(len(calls),4)
            legacy=json.loads(path.read_text())
            for attempt in legacy['attempts']:
                for key in ('attempt_id','started_unix','ended_unix'): attempt.pop(key)
            path.write_text(json.dumps(legacy))
            self.assertEqual(run_suite(collector,path,per_role=1),0)
            self.assertEqual(len(calls),4)
            with self.assertRaisesRegex(ValueError,'plan changed'): run_suite(collector,path,per_role=2)

    @patch('sts_ai_framework.headless.suite.check_permits')
    def test_unexpected_start_failure_is_recorded_and_retried_with_new_uuid(self, permits):
        with tempfile.TemporaryDirectory() as temp:
            calls=[]; path=Path(temp)/'status.json'
            def episode(role,seed,*,attempt_id):
                calls.append((role,seed,attempt_id))
                marker=json.loads(path.read_text())['attempts'][-1]
                self.assertEqual(marker['result']['status'],'running')
                raise RuntimeError('launch failed')
            collector=SimpleNamespace(root=Path(temp),ascension=10,max_actions=3000,episode=episode)
            self.assertEqual(run_suite(collector,path,per_role=1),1)
            first=json.loads(path.read_text())['attempts'][0]
            self.assertEqual(first['result']['status'],'aborted')
            self.assertTrue(Path(first['result']['diagnostic']).is_file())
            with self.assertRaisesRegex(ValueError,'inspect/fix'): run_suite(collector,path,per_role=1)
            self.assertEqual(len(calls),1)
            self.assertEqual(run_suite(collector,path,per_role=1,retry_failed=True),1)
            self.assertEqual(calls[0][:2],calls[1][:2]); self.assertNotEqual(calls[0][2],calls[1][2])
            attempts=json.loads(path.read_text())['attempts']
            self.assertEqual(len(attempts),2)
            self.assertNotEqual(attempts[0]['result']['diagnostic'],attempts[1]['result']['diagnostic'])
            self.assertTrue(all(Path(a['result']['diagnostic']).is_file() for a in attempts))

    @patch('sts_ai_framework.headless.suite.check_permits')
    def test_interrupted_reservation_is_censored_before_explicit_same_seed_retry(self, permits):
        with tempfile.TemporaryDirectory() as temp:
            calls=[]; path=Path(temp)/'status.json'
            def episode(role,seed,*,attempt_id):
                calls.append((role,seed,attempt_id))
                if len(calls)==1: raise KeyboardInterrupt()
                return {'status':'aborted','last_floor':3,'transitions':4}
            collector=SimpleNamespace(root=Path(temp),ascension=10,max_actions=3000,episode=episode)
            with self.assertRaises(KeyboardInterrupt): run_suite(collector,path,per_role=1)
            self.assertEqual(json.loads(path.read_text())['attempts'][0]['result']['status'],'running')
            with self.assertRaisesRegex(ValueError,'inspect/fix'): run_suite(collector,path,per_role=1)
            state=json.loads(path.read_text())
            self.assertEqual(state['status'],'stopped_on_difference')
            self.assertEqual(state['attempts'][0]['result']['status'],'aborted')
            self.assertTrue(Path(state['attempts'][0]['result']['diagnostic']).is_file())
            self.assertEqual(len(calls),1)
            self.assertEqual(run_suite(collector,path,per_role=1,retry_failed=True),1)
            self.assertEqual(calls[0][:2],calls[1][:2]); self.assertNotEqual(calls[0][2],calls[1][2])
