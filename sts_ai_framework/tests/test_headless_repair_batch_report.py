"""The original-trigger count, terminal receipts and comparison outcomes are independent."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from sts_ai_framework.headless.batch160 import full_schedule
from sts_ai_framework.headless.batch160_next import scoped_inventory
from sts_ai_framework.headless.repair_batch_report import compare_stop, freeze_audit, main, summarize
from sts_ai_framework.tests.test_headless_combat import combat_raw


def write(path,obj):
    data=json.dumps(obj).encode();path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    return hashlib.sha256(data).hexdigest()


def diag(case):
    rng=combat_raw()['combat']['snapshot']['_headless_rng']
    raw={'seed':case['seed'],'phase':'EVENT','floor':7,'act':1,'room':'EventRoom',
         'player':{'deck':[{'uuid':'first','id':'Strike'},{'uuid':'second','id':'Defend'}]},'combat_receipt':{'rng':rng}}
    return {'role':case['role'],'seed':case['seed'],'attempt_id':case['attempt_id'],'before':{'state':raw},'method':'chooseDialogOption',
            'args':[0],'error':"ValueError('Unverified action effect: UNVERIFIED')"}


def fixture(root):
    cases=[]
    for i,c in enumerate(full_schedule()):
        aid=f'{i+1:032x}';eid=f'{i+200:032x}';h=f'{i+1:064x}'
        cases.append(dict(c,status='complete',attempt_id=aid,episode=dict(c,episode_id=eid,attempt_id=aid,sha256=h,
            transitions=5,final_chain=h,censored=False,terminal=dict(character=c['role'],ascension=10,run_id=eid,
            receipt_id='terminal-'+eid,outcome='defeat')),
            independent_replay={'valid':True,'replay_verified':True,'role':c['role'],'status':'complete','episode_sha256':h,
                                'final_chain':h,'transitions':5},collector_after_close={'cgroup_memory_events':{'oom':0}}))
    lanes=[dict(lane=i,attempted=80,started_unix=123,ended_unix=130,cgroup_cpu_seconds=6,oom_kill=0,
                peak_sampled_rss_bytes=24,peak_sampled_pss_bytes=22,cgroup_memory_peak_bytes=27,cgroup_swap_peak_bytes=0) for i in range(2)]
    old={'schema':'headless-batch160-report-v1','cases':copy.deepcopy(cases),'lanes':lanes};new=copy.deepcopy(old)
    def abort(r,c,d):
        c.update(status='aborted');c['episode'].update(censored=True,terminal=None)
        c['independent_replay']['status']='aborted'
        c['diagnostic_sha256']=write(r/f"lane{c['lane']}"/'reports'/f"failure-{c['attempt_id']}.json",d)
    sign=[]
    for c in old['cases'][:102]:
        d=diag(c);abort(root/'old',c,d)
        sign.append({'case_id':c['case_id'],'seed':c['seed'],'diagnostic_sha256':c['diagnostic_sha256'],'passed':True})
    for i in (0,1,2,104):
        c=new['cases'][i];d=diag(c)
        if i==1:d['args']=[1]
        if i==2:d.pop('method')
        abort(root/'new',c,d)
    # A file labeled complete without an independently verified chain isn't a confirmed terminal.
    new['cases'][4].pop('independent_replay')
    original=write(root/'old/reports/batch160-report.json',old);write(root/'new/reports/batch160-report.json',new)
    proof=root/'signoff.json';write(proof,{'schema':'headless-102-signoff-v1','original_report_sha256':original,
               'verified':102,'passed':True,'cases':sign})
    return proof


class ComparisonTest(unittest.TestCase):
    def test_followup_stop_inventory_requires_frozen_report_and_signed_prefix(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve()
            fixture(root)
            source=root/'new'
            case=json.loads((source/'reports/batch160-report.json').read_bytes())['cases'][0]
            path=source/'reports/new-report.json'
            report=dict(scope='new',ascension=10,planned=1,attempted=1,independently_replayed=1,
                        verification_errors=0,aborted=1,errors=[],preservation={'passed':True},cases=[case])
            sha=write(path,report)
            self.assertEqual(list(scoped_inventory(root,{'new':(sha,1,1)})),[(source,case,diag(case))])
            report['preservation']['passed']=False
            changed=write(path,report)
            with self.assertRaisesRegex(ValueError,'Unexpected source report'):
                list(scoped_inventory(root,{'new':(sha,1,1)}))
            with self.assertRaisesRegex(ValueError,'did not pass preservation'):
                list(scoped_inventory(root,{'new':(changed,1,1)}))
            report['preservation']['passed']=True
            case['independent_replay']['replay_verified']=False
            changed=write(path,report)
            with self.assertRaisesRegex(ValueError,'Source prefix is not signed'):
                list(scoped_inventory(root,{'new':(changed,1,1)}))

    def test_separate_counts_unknowns_and_non_overwriting_cli(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);s=fixture(root);r=summarize(root/'old',s,root/'new')
            self.assertEqual(r['original_triggers_passed'],102)
            self.assertEqual(sum(x['terminal'] for x in r['outcomes_by_role'].values()),155)
            self.assertEqual(r['counts'],{'repeated_old_difference':1,'new_difference':2,'insufficient_evidence':2})
            self.assertEqual((r['lane_resources'][0]['elapsed_seconds'],r['lane_resources'][0]['oom_events']),(7,0))
            from unittest.mock import patch
            cmd=['--original',str(root/'old'),'--signoff',str(s),'--rerun',str(root/'new')]
            with patch('builtins.print'):
                self.assertEqual(main(cmd),0)
                with self.assertRaises(FileExistsError):main(cmd)
            self.assertFalse((root/'old/reports/repair160-comparison.md').exists())
            self.assertIn('经回放到终局',(root/'new/reports/repair160-comparison.md').read_text(encoding='utf8'))

    def test_rejects_unsigned_or_substituted_trigger_and_degrades_tampered_diagnostic(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);s=fixture(root);f=next((root/'new/lane0/reports').glob('failure-*.json'))
            f.write_text('{}',encoding='utf8');r=summarize(root/'old',s,root/'new')
            self.assertTrue(any('diagnostic identity' in x['reason'] for x in r['differences']['insufficient_evidence']))
            gate=json.loads(s.read_bytes())
            for bad in (dict(gate,passed=False),dict(gate,cases=gate['cases'][:-1]),
                        dict(gate,cases=[dict(gate['cases'][0],diagnostic_sha256='wrong')]+gate['cases'][1:])):
                write(s,bad)
                with self.assertRaises(ValueError):summarize(root/'old',s,root/'new')

    def test_repeat_requires_bijection_action_and_rng_not_the_same_error_text(self):
        c=dict(seed=5050201,role='IRONCLAD',attempt_id='1'*32);a=diag(c);b=copy.deepcopy(a)
        b['before']['state']['player']['deck'][0]['uuid']='other'
        self.assertEqual(compare_stop(a,b)[0],'repeated_old_difference')
        b['before']['state']['player']['deck'][1]['uuid']='other'
        self.assertEqual(compare_stop(a,b)[0],'insufficient_evidence')
        b=copy.deepcopy(a);b['before']['state']['combat_receipt']['rng']['streams']['ai']['counter']+=1
        self.assertEqual(compare_stop(a,b)[0],'new_difference')
        b=copy.deepcopy(a);b.pop('args')
        self.assertEqual(compare_stop(a,b)[0],'insufficient_evidence')
        b=copy.deepcopy(a);b['before']['state']['floor']+=1
        self.assertEqual(compare_stop(a,b)[0],'new_difference')

    def test_same_state_but_different_actual_landing_or_mismatch_path_is_not_a_repeat(self):
        c=dict(seed=5050201,role='IRONCLAD',attempt_id='1'*32);a=diag(c)
        a['after']=copy.deepcopy(a['before']);a['prediction']={'expected_after':{'hp':20,'block':1}}
        a['fingerprint']={'projection':{'hp':15,'block':1}};b=copy.deepcopy(a)
        b['fingerprint']['projection']={'hp':20,'block':2}
        self.assertEqual(compare_stop(a,b)[:2],('insufficient_evidence','same_trigger_but_mismatch_fields_changed'))
        b=copy.deepcopy(a);b['after']['state']['floor']+=1
        self.assertEqual(compare_stop(a,b)[0],'insufficient_evidence')

    def test_two_lane_freeze_includes_checkpoint_profile_signoff_and_each_runtime_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            gate={'runtime_sha256':{'native-bin/battle-engine-worker':'worker','repo/framework.py':'framework'},
                  'checkpoint':'model','profile':'profile'}
            files={'reports/repair-signoff.json':'signoff'}
            for n in (0,1):
                for k,v in dict(gate['runtime_sha256'],**{'value-format12.pth':'model','profile-baseline-manifest.json':'profile'}).items():
                    files[f'lane{n}/'+k]=v
            path=root/'frozen-runtime-manifest.json';write(path,{'files':files})
            self.assertEqual(freeze_audit(gate,'signoff',root)['status'],'manifest_matches')
            files['lane1/profile-baseline-manifest.json']='other';write(path,{'files':files})
            self.assertEqual(freeze_audit(gate,'signoff',root)['status'],'insufficient_or_mismatched_freeze')
