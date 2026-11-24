import json
from collections import Counter
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from sts_ai_framework.headless.batch160 import full_schedule, make_plan, read_followup_schedule, retry_schedule, selected_seeds
from sts_ai_framework.headless.batch160_report import summarize
from sts_ai_framework.headless.flow import TerminalReceipt
from sts_ai_framework.headless.release2 import save_pilot_receipt


class UnfinishedBatchTests(unittest.TestCase):
    def test_29_schedule_distinguishes_same_case_id_in_both_suites(self):
        with TemporaryDirectory() as tmp:
            root=Path(tmp)
            old = full_schedule()[:16]
            new = full_schedule(5050401)[:8] + full_schedule(5050401)[8:11] + full_schedule(5050401)[12:14]
            rows = retry_schedule({c['seed'] for c in old+new})
            self.assertEqual(len(rows),len({c['case_id'] for c in rows}))
            self.assertEqual({c['case_id'] for c in rows if c['seed'] in {5050201,5050401}},
                             {'recheck44-IRONCLAD-01','fresh40-IRONCLAD-01'})
            s=root/'schedule.json'
            s.write_text(json.dumps(dict(schema='headless-batch63-followup-plan-v1',scope='retry29',
                                         ascension=10,max_actions=3000,cases=rows)))
            plans=[make_plan(root,i,schedule=read_followup_schedule(s)) for i in range(2)]
            self.assertEqual([p['cases_per_lane'] for p in plans],[15,14])

    def test_followup_cohorts_keep_original_identity_and_balance_fresh_roles(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp); s = root/'schedule.json'
            source = full_schedule()
            old = [c for c in source if c['round']<=10] + [c for c in source
                   if c['round']==11 and c['role']=='IRONCLAD' or c['round']==12 and c['role']!='IRONCLAD']
            cases = {'recheck44':old,'fresh40':full_schedule(5050401)[:40]}
            for scope, rows in cases.items():
                data=dict(schema='headless-batch63-followup-plan-v1',scope=scope,ascension=10,max_actions=3000,cases=rows)
                s.write_text(json.dumps(data))
                schedule=read_followup_schedule(s)
                plans=[make_plan(root,i,schedule=schedule) for i in range(2)]
                self.assertEqual(plans[0]['global_cases'],rows)
                self.assertEqual([p['cases_per_lane'] for p in plans],[21,23] if scope=='recheck44' else [20,20])
                if scope=='fresh40':
                    self.assertEqual(plans[0]['seed_base'],5050401)
                    for p in plans:
                        self.assertEqual(set(Counter(c['role'] for c in p['cases']).values()),{5})
                corrupted=[{**rows[0],'seed':5050361},*rows[1:]]
                s.write_text(json.dumps(dict(data,cases=corrupted)))
                with self.assertRaises(ValueError):read_followup_schedule(s)

    def test_two_reported_lanes_contain_only_selected_cases(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            all_cases = full_schedule()
            seeds = sorted(c['seed'] for c in all_cases if c['seed'] % 2 == 0)[:63]
            file = root/'selection.json'
            file.write_text(json.dumps({'schema':'headless-unfinished-63-v1','seeds':seeds}))
            selected = selected_seeds(file)
            seen = []
            for lane in range(2):
                directory = root/f'lane{lane}'
                (directory/'reports').mkdir(parents=True)
                p = make_plan(directory,lane,seeds=selected)
                self.assertEqual(p['total_cases'],63)
                self.assertTrue(p['cases'])
                self.assertTrue(all(c['lane']==lane for c in p['cases']))
                seen.extend(c['seed'] for c in p['cases'])
                (directory/'reports/batch-status.json').write_text(json.dumps({'plan':p,'attempts':[]}))
            self.assertEqual(sorted(seen),seeds)
            report = summarize(root,seeds=selected)
            self.assertEqual(report['planned'],63)
            self.assertEqual(sorted(c['seed'] for c in report['cases']),seeds)
            self.assertEqual(make_plan(root,0)['cases_per_lane'],80)

    def test_duplicate_and_non_original_seeds_are_rejected(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp)/'selection.json'
            seeds = list(range(5050201,5050201+63))
            for altered in (seeds[:-1]+[seeds[-2]], seeds[:-1]+[5050361]):
                path.write_text(json.dumps({'schema':'headless-unfinished-63-v1','seeds':altered}))
                with self.assertRaisesRegex(ValueError,'Invalid unfinished'):
                    selected_seeds(path)

    def test_bad_pilot_payload_keeps_diagnostic_and_next_receipt_writes(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            receipt = TerminalReceipt('id','run','WATCHER',10,22,2,'defeat',False,'state')
            first = root/'pilot-5050272.json'
            bad = save_pilot_receipt(first,5050272,{'terminal':receipt,'trajectory':{'path':'attempt.gz'}})
            self.assertEqual(json.loads(first.read_bytes()),bad)
            self.assertEqual(bad['status'],'unresolved')
            self.assertIn('TerminalReceipt',bad['error'])
            self.assertEqual(bad['trajectory_path'],'attempt.gz')
            self.assertFalse(first.with_suffix('.tmp').exists())
            second = root/'pilot-5050278.json'
            good = {'seed':5050278,'status':'original_stop_crossed'}
            self.assertEqual(save_pilot_receipt(second,5050278,good),good)
            self.assertEqual(json.loads(second.read_bytes()),good)
