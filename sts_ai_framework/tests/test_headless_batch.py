"""No game/worker/model is launched: durable batch orchestration fixtures only."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock,patch
import uuid

from sts_ai_framework.headless import agent as agent_module
from sts_ai_framework.headless.batch import full_schedule,lane_schedule,lane_lock,main,run_batch


class BatchTest(unittest.TestCase):
    def setUp(self):
        self.stop=patch.object(agent_module,'_STOP_REQUESTED',False);self.stop.start();self.addCleanup(self.stop.stop)
        self.output=contextlib.redirect_stdout(io.StringIO());self.output.__enter__();self.addCleanup(self.output.__exit__,None,None,None)
        self.metrics=patch('sts_ai_framework.headless.batch.resource_sample',return_value={
            'rss_bytes':123,'pss_bytes':100,'cpu_seconds':4.,'os_threads':2,'cgroup_memory_events':{'oom':0},'cgroup_cpu_seconds':5.})
        self.metrics.start();self.addCleanup(self.metrics.stop)

    def root(self,temp):
        root=Path(temp)
        for name in ('ALLOW_RUN_SMOKE','ALLOW_COLLECTION'):(root/name).write_text('fixture')
        return root

    def collector(self,root,episode):
        return SimpleNamespace(root=root,ascension=10,max_actions=3000,episode=episode)

    def state(self,root):return json.loads((root/'reports/batch-status.json').read_text(encoding='utf-8'))

    def test_global_schedule_has_24_disjoint_seeds_and_each_lane_three_per_role(self):
        schedule=full_schedule();self.assertEqual([c['seed'] for c in schedule],list(range(5050101,5050125)))
        self.assertEqual(len({c['case_id'] for c in schedule}),24)
        lanes=[lane_schedule(i) for i in (0,1)]
        self.assertFalse({c['seed'] for c in lanes[0]} & {c['seed'] for c in lanes[1]})
        for lane,cases in enumerate(lanes):
            self.assertEqual(len(cases),12);self.assertTrue(all((c['round']-1)%2==lane for c in cases))
            for role in agent_module.ROLES:self.assertEqual(sum(c['role']==role for c in cases),3)
        for lane in (-1,2,True):
            with self.assertRaises(ValueError):lane_schedule(lane)

    def test_all_twelve_reservations_are_durable_before_episode_and_metrics_are_kept(self):
        with tempfile.TemporaryDirectory() as temp:
            root=self.root(temp);calls=[]
            def episode(role,seed,*,attempt_id):
                state=self.state(root);reservation=state['attempts'][-1]
                self.assertEqual(reservation['result']['status'],'running')
                self.assertEqual(reservation['attempt_id'],attempt_id);self.assertEqual(uuid.UUID(attempt_id).hex,attempt_id)
                self.assertEqual(state['status'],'running');calls.append((role,seed))
                return {'status':'complete','last_floor':57,'transitions':10}
            self.assertEqual(run_batch(self.collector(root,episode),lane=0),0)
            state=self.state(root);self.assertEqual(state['status'],'passed');self.assertTrue(state['finished'])
            self.assertEqual(len(calls),12);self.assertEqual(len(state['attempts']),12)
            self.assertEqual(state['summary']['remaining'],0);self.assertEqual(len(state['plan']['global_cases']),24)
            for attempt in state['attempts']:
                sample=attempt['collector_after_close'];self.assertEqual(sample['rss_bytes'],123)
                self.assertEqual(sample['cgroup_memory_events'],{'oom':0});self.assertIn('threads',sample);self.assertIn('open_fds',sample)

    def test_aborted_and_ordinary_exception_keep_diagnostics_and_continue(self):
        with tempfile.TemporaryDirectory() as temp:
            root=self.root(temp);calls=[]
            def episode(role,seed,*,attempt_id):
                calls.append(seed)
                if len(calls)==1:return {'status':'aborted','error':'RNG mismatch','last_floor':3,'transitions':2}
                if len(calls)==2:raise RuntimeError('fixture native failure')
                return {'status':'complete','last_floor':57,'transitions':10}
            self.assertEqual(run_batch(self.collector(root,episode),lane=1),0)
            state=self.state(root);self.assertEqual(len(calls),12);self.assertEqual(state['status'],'finished_with_differences')
            self.assertEqual(state['summary']['differences'],2)
            for attempt in state['attempts'][:2]:
                self.assertTrue(attempt['result']['censored']);self.assertTrue(Path(attempt['result']['diagnostic']).is_file())
            self.assertNotEqual(state['attempts'][0]['result']['diagnostic'],state['attempts'][1]['result']['diagnostic'])

    def test_resume_censors_running_reservation_and_never_retries_any_attempt(self):
        with tempfile.TemporaryDirectory() as temp:
            root=self.root(temp);calls=[]
            def crash(role,seed,*,attempt_id):
                calls.append(seed)
                if len(calls)==3:raise SystemExit(7)  # Model a killed interpreter after its durable reservation.
                return {'status':'complete','last_floor':57,'transitions':10}
            collector=self.collector(root,crash)
            with self.assertRaises(SystemExit):run_batch(collector,lane=0)
            self.assertEqual(self.state(root)['attempts'][-1]['result']['status'],'running')
            first=list(calls)
            def finish(role,seed,*,attempt_id):
                calls.append(seed);return {'status':'complete','last_floor':57,'transitions':10}
            collector.episode=finish
            self.assertEqual(run_batch(collector,lane=0),0)
            state=self.state(root);self.assertTrue(state['attempts'][2]['interrupted'])
            self.assertEqual(state['attempts'][2]['result']['status'],'aborted');self.assertEqual(len(calls),12)
            self.assertEqual(calls[:3],first);self.assertEqual(len(set(calls)),12)
            self.assertEqual(run_batch(collector,lane=0),0);self.assertEqual(len(calls),12)

    def test_signal_stops_following_case_without_retrying_current(self):
        with tempfile.TemporaryDirectory() as temp:
            root=self.root(temp);episode=Mock()
            def stop(role,seed,*,attempt_id):
                agent_module.request_stop(None,None);return {'status':'aborted','error':'stop requested'}
            episode.side_effect=stop
            self.assertEqual(run_batch(self.collector(root,episode),lane=0),130)
            state=self.state(root);self.assertEqual(state['status'],'stopped');self.assertFalse(state['finished'])
            self.assertEqual(episode.call_count,1);self.assertEqual(state['summary']['remaining'],11)

    def test_revoked_permit_stops_following_case_and_remains_stopped_on_resume(self):
        with tempfile.TemporaryDirectory() as temp:
            root=self.root(temp);calls=[]
            def revoke(role,seed,*,attempt_id):
                calls.append(seed);(root/'ALLOW_COLLECTION').unlink();raise RuntimeError('interrupted fixture')
            collector=self.collector(root,revoke)
            self.assertEqual(run_batch(collector,lane=1),130)
            self.assertEqual(run_batch(collector,lane=1),130);self.assertEqual(len(calls),1)
            self.assertEqual(self.state(root)['status'],'stopped')

    def test_plan_change_and_duplicate_case_reservation_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root=self.root(temp);collector=self.collector(root,lambda *a,**k:{'status':'complete'})
            run_batch(collector,lane=0);original=self.state(root)
            for kwargs in ({'lane':1},{'lane':0,'seed_base':123}):
                with self.assertRaisesRegex(ValueError,'plan changed'):run_batch(collector,**kwargs)
            self.assertEqual(self.state(root),original)
            original['attempts'].append(original['attempts'][0]);(root/'reports/batch-status.json').write_text(json.dumps(original))
            with self.assertRaisesRegex(ValueError,'duplicate'):run_batch(collector,lane=0)

    def test_os_file_lock_excludes_a_second_process_entry(self):
        with tempfile.TemporaryDirectory() as temp:
            root=self.root(temp);episode=Mock()
            with lane_lock(root):
                with self.assertRaisesRegex(RuntimeError,'Another batch process'):run_batch(self.collector(root,episode),lane=0)
            episode.assert_not_called()
            with lane_lock(root):pass

    @patch('sts_ai_framework.headless.batch.signal.signal')
    def test_model_initialization_or_permit_failure_is_durable_and_not_finished(self,signals):
        for permit in (True,False):
            with self.subTest(permit=permit),tempfile.TemporaryDirectory() as temp:
                root=self.root(temp)
                if not permit:(root/'ALLOW_RUN_SMOKE').unlink()
                with patch('sts_ai_framework.headless.batch.HeadlessCollector',side_effect=RuntimeError('model init failed')) as init:
                    code=main(['--lane','0','--root',str(root),'--launcher','fixture','--native','fixture','--checkpoint','fixture'])
                self.assertEqual(code,1);state=self.state(root)
                self.assertEqual(state['status'],'stopped');self.assertFalse(state['finished']);self.assertTrue(state['failure'])
                self.assertEqual(state['attempts'],[]);self.assertEqual(state['stop_stage'],'initializing')
                self.assertEqual(init.call_count,int(permit))


if __name__=='__main__':unittest.main()
