import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from sts_ai_framework.headless.batch160 import make_plan
from sts_ai_framework.headless.batch160_report import summarize, markdown


class BatchReportTest(unittest.TestCase):
    def fixture(self, root, aborted=False):
        (root/'reports').mkdir()
        (root/'reports/preservation-report.json').write_text(json.dumps({'passed': True}))
        (root/'reports/supervisor-result.json').write_text(json.dumps({'lane_exit_codes': [0, 0], 'scoped_permits_closed': True}))
        for lane in (0, 1):
            w = root/f'lane{lane}'; (w/'reports').mkdir(parents=True); (w/'episodes').mkdir()
            (w/'value-format12.pth').write_bytes(b'model')
            (w/'profile-baseline-manifest.json').write_text('{}')
            plan = make_plan(w, lane)
            attempts = []
            for index, case in enumerate(plan['cases']):
                status = 'aborted' if aborted and index == 0 else 'complete'
                header = {'record': {'role': case['role'], 'seed': case['seed'], 'policy': {
                    'ascension': 10, 'model_sha256': hashlib.sha256(b'model').hexdigest(),
                    'profile_manifest_sha256': hashlib.sha256(b'{}').hexdigest(), 'runtime_sha256': {}}}}
                path = w/'episodes'/f"{case['case_id']}.jsonl.gz"
                with gzip.open(path, 'wt') as stream: stream.write(json.dumps(header)+'\n')
                result = {'status': status, 'role': case['role'], 'seed': case['seed'], 'path': str(path),
                          'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'final_chain': 'chain', 'transitions': 3}
                if status == 'aborted':
                    diagnostic = w/'reports'/f"failure-{case['case_id']}.json"; diagnostic.write_text('{}')
                    result['diagnostic'] = str(diagnostic)
                attempts.append({'case_id': case['case_id'], 'result': result})
            (w/'reports/batch-status.json').write_text(json.dumps({'plan': plan, 'attempts': attempts,
                'status': 'finished_with_differences' if aborted else 'passed'}))

    @staticmethod
    def replay(path, native, *, require_complete):
        with gzip.open(path, 'rt') as source: header = json.loads(next(source))['record']
        return {'final_chain': 'chain', 'transitions': 3, 'role': header['role'],
                'status': 'complete' if require_complete else 'aborted'}

    def test_all_160_are_independently_replayed_and_roles_balanced(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); self.fixture(root)
            result = summarize(root, replay=self.replay)
            self.assertEqual((result['status'], result['attempted'], result['completed'], result['independently_replayed']),
                             ('passed', 160, 160, 160))
            self.assertEqual(set(result['attempted_by_role'].values()), {40})
            self.assertIn('160/160', markdown(result))

    def test_differences_are_censored_not_counted_as_terminal_and_prefixes_replayed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); self.fixture(root, aborted=True)
            result = summarize(root, replay=self.replay)
            self.assertEqual((result['status'], result['aborted'], result['completed']), ('finished_with_differences', 2, 158))
            self.assertEqual(result['independently_replayed'], 160)
            self.assertEqual(sum('diagnostic_sha256' in row for row in result['cases']), 2)

    def test_failed_replay_does_not_trust_recorded_success(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); self.fixture(root)
            def broken(*a, **kw): raise ValueError('RNG differs')
            result = summarize(root, replay=broken)
            self.assertEqual((result['status'], result['verification_errors']), ('incomplete_or_report_error', 160))

    def test_missing_lane_and_failed_preservation_report_are_not_passed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); self.fixture(root)
            (root/'lane1/reports/batch-status.json').unlink()
            (root/'reports/preservation-report.json').write_text('{"passed":false}')
            result = summarize(root, replay=self.replay)
            self.assertEqual((result['status'], result['attempted']), ('incomplete_or_report_error', 80))
            self.assertTrue(result['errors'])

    def test_tampered_episode_is_rejected_before_replay(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); self.fixture(root)
            path = next((root/'lane0/episodes').iterdir()); path.write_bytes(b'tampered')
            result = summarize(root, replay=self.replay)
            self.assertEqual(result['verification_errors'], 1)
            self.assertEqual(result['status'], 'incomplete_or_report_error')
