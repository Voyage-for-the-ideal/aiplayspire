from collections import Counter
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from sts_ai_framework.headless.batch160 import (fresh80_schedule, full_schedule, lane_schedule,
                                                read_followup_schedule, retry13_schedule, retry16_schedule,
                                                retry25_schedule, run_batch)


class Batch160Test(unittest.TestCase):
    def test_schedule_has_160_unique_seeds_and_20_each_role_per_process(self):
        cases=full_schedule();self.assertEqual(len(cases),160)
        self.assertEqual({c['seed'] for c in cases},set(range(5050201,5050361)))
        for lane in (0,1):
            plan=lane_schedule(lane);self.assertEqual(len(plan),80)
            self.assertEqual(set(Counter(c['role'] for c in plan).values()),{20})
        self.assertFalse({c['seed'] for c in lane_schedule(0)} & {c['seed'] for c in lane_schedule(1)})

    def test_fresh80_keeps_20_total_per_role_and_rejects_edited_coordinates(self):
        seeds=list(range(1<<42,(1<<42)+80))
        cases=fresh80_schedule(seeds)
        self.assertEqual(set(Counter((c['lane'],c['role']) for c in cases).values()),{10})
        self.assertEqual(set(Counter(c['role'] for c in cases).values()),{20})
        with self.assertRaises(ValueError):
            fresh80_schedule([seeds[0],*seeds[:-1]])
        with self.assertRaises(ValueError):
            fresh80_schedule([5050243,*seeds[1:]])
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'schedule.json'
            plan={'schema':'headless-batch63-followup-plan-v1','scope':'fresh80','ascension':10,
                  'max_actions':3000,'cases':cases}
            path.write_text(json.dumps(plan))
            self.assertEqual(read_followup_schedule(path),plan)
            cases[0]['lane']=1
            path.write_text(json.dumps(plan))
            with self.assertRaises(ValueError):
                read_followup_schedule(path)

    def test_retry13_keeps_original_roles_and_split(self):
        seeds={5050224,5050243,5050265,5050274,5050299,5050317,5050333,5050336,
               5050401,5050406,5050410,5050437,5050439}
        cases=retry13_schedule(seeds)
        self.assertEqual({c['seed'] for c in cases},seeds)
        self.assertEqual(Counter(c['lane'] for c in cases),{0:6,1:7})
        for c in cases:
            self.assertTrue(c['case_id'].startswith(c['source_scope']+'-'))

    def test_retry25_keeps_both_source_cohorts_and_their_lanes(self):
        early=retry13_schedule({5050224,5050243,5050265,5050274,5050299,5050317,5050333,5050336,
                               5050401,5050406,5050410,5050437,5050439})
        again=[dict(c,source_scope='retry13') for c in early if c['seed'] in (5050265,5050410,5050437,5050439)]
        fresh=fresh80_schedule(list(range(1<<42,(1<<42)+80)))
        again.extend(dict(c,source_scope='fresh80') for lane,n in ((0,12),(1,9))
                     for c in [v for v in fresh if v['lane']==lane][:n])
        plan=retry25_schedule(again)
        self.assertEqual(Counter(c['lane'] for c in plan),{0:14,1:11})
        self.assertEqual(retry25_schedule(plan,frozen=True),plan)
        self.assertEqual([c['seed'] for c in plan],[c['seed'] for c in again])
        plan[0]['role']='WATCHER'
        with self.assertRaises(ValueError):retry25_schedule(plan,frozen=True)

    def test_retry16_preserves_seed_role_lane_and_both_source_scopes(self):
        early=retry13_schedule({5050224,5050243,5050265,5050274,5050299,5050317,5050333,5050336,
                                5050401,5050406,5050410,5050437,5050439})
        repaired=[dict(c,case_id='retry13-'+c['case_id'],source_scope='retry25')
                  for c in early if c['seed'] in {5050265,5050410,5050437,5050439}]
        for base,scope,counts in ((1<<42,'retry25',(2,1)),(1<<43,'fresh80',(5,4))):
            fresh=fresh80_schedule(list(range(base,base+80)))
            for lane,count in enumerate(counts):
                for row in [c for c in fresh if c['lane']==lane][:count]:
                    identity=('fresh80-' if scope=='retry25' else '')+row['case_id']
                    repaired.append(dict(row,case_id=identity,source_scope=scope))
        plan=retry16_schedule(repaired)
        self.assertEqual(Counter(c['lane'] for c in plan),{0:9,1:7})
        self.assertEqual([(c['seed'],c['role'],c['lane']) for c in plan],
                         [(c['seed'],c['role'],c['lane']) for c in repaired])
        self.assertEqual(retry16_schedule(plan,frozen=True),plan)
        for bad in (plan[:-1],[dict(plan[0],role='WATCHER'),*plan[1:]],
                    [*plan[:-1],dict(plan[-1],seed=plan[-2]['seed'])]):
            with self.assertRaises(ValueError):retry16_schedule(bad,frozen=True)

    @patch('sts_ai_framework.headless.batch160.check_permits')
    def test_failed_case_is_censored_and_remaining_79_cases_continue(self,permits):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);(root/'reports').mkdir();calls=[]
            def episode(role,seed,*,attempt_id):
                calls.append(seed)
                return {'status':'aborted' if len(calls)==1 else 'complete','last_floor':1,'transitions':0}
            collector=SimpleNamespace(root=root,ascension=10,max_actions=3000,episode=episode)
            self.assertEqual(run_batch(collector,lane=0),0)
            data=json.loads((root/'reports/batch-status.json').read_text())
            self.assertEqual((data['status'],len(data['attempts'])),('finished_with_differences',80))
            self.assertTrue(data['attempts'][0]['result']['censored'])
            self.assertTrue(Path(data['attempts'][0]['result']['diagnostic']).is_file())
            self.assertEqual(run_batch(collector,lane=0),0)
            self.assertEqual(len(calls),80)
