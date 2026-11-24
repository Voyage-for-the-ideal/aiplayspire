"""Bind the 44 interrupted seeds, stage an independent 40-seed cohort, freeze both runtimes."""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from sts_ai_framework.headless.batch160 import full_schedule
from sts_ai_framework.headless.layout import module_path
from sts_ai_framework.headless.repair_ledger import digest
from sts_ai_framework.headless.repair_signoff import release_digest, require, runtime


BASE=Path('/home/ubuntu/headless-spire-poc')
SOURCE=BASE/'runtime-acceptance-batch160-v3-unfinished'
REPAIR=BASE/'runtime-acceptance-batch160-repairs-r4-targeted'
ROOT=BASE/'runtime-acceptance-batch63-r4-followup'
CONTENTS=('repo','tools','classes','native-bin','profile-baseline','profile-baseline-manifest.json','value-format12.pth')
PATCHES=('batch160','batch160_report')


def make_schedules(aborted, source_sha):
    seeds={c['seed'] for c in aborted}
    old=[c for c in full_schedule() if c['seed'] in seeds]
    fresh=full_schedule(5050401)[:40]
    require(len(aborted)==len(seeds)==len(old)==44 and not seeds & {c['seed'] for c in fresh},'Bad follow-up cohorts')
    require(Counter(c['lane'] for c in old)=={0:21,1:23},'Changed lane assignment')
    require({(c['seed'],c['case_id'],c['lane'],c['role']) for c in old}==
            {(c['seed'],c['case_id'],c['lane'],c['role']) for c in aborted},'Wrong original case identity')
    require(Counter((c['lane'],c['role']) for c in fresh)=={(i,c['role']):5 for i in (0,1) for c in fresh},
            'Fresh plan must have five of each role per lane')
    return {scope:dict(schema='headless-batch63-followup-plan-v1',scope=scope,source_report_sha256=source_sha,
                       ascension=10,max_actions=3000,cases=cases)
            for scope,cases in (('recheck44',old),('fresh40',fresh))}


def schedules():
    # This verifier belongs to the signed repair tree and may still import flat modules.
    script=("import json, runpy, sys; from pathlib import Path; "
            "module=runpy.run_path(sys.argv[1]); "
            "print(json.dumps([[case for case,_ in module['inventory'](Path(sys.argv[2]))],module['REPORT_SHA256']]))")
    repo=REPAIR/'repo'
    data=subprocess.check_output([sys.executable,'-B','-c',script,str(REPAIR/'tools/verify-unfinished63-stops.py'),
                                  str(SOURCE)],cwd=repo,env=dict(os.environ,PYTHONPATH=str(repo)))
    return make_schedules(*json.loads(data))


def read(path):
    return json.loads(path.read_bytes())


def write(path,data):
    with path.open('x',encoding='utf8') as f: json.dump(data,f,indent=2)


def repaired_identity():
    checks=read(REPAIR/'reports/verified-stops.json')
    suite=read(REPAIR/'reports/suites-targeted-r4.json')
    worker=digest(REPAIR/'native-bin/battle-engine-worker')
    require(checks['counts']=={'recorded_step_matches':12,'valid_command_no_archived_after':32}
            and checks['source_report_sha256']==digest(SOURCE/'reports/unfinished63-report.json')
            and checks['worker_sha256']==worker,'Missing r4 stop verification')
    require(suite['runtime_sha256']==runtime(REPAIR) and set(suite['suites'])=={'framework','native'} and all(
            s['exit_code']==0 and s['skips']==0 for s in suite['suites'].values()),'r4 runtime differs from preflight')
    return worker


def stage():
    require(ROOT.resolve(strict=True)==ROOT and not (ROOT/'freeze.json').exists(),'Require a fresh follow-up root')
    worker=repaired_identity()
    plans=schedules()
    identity={'native_sha256':worker,'profile_sha256':digest(REPAIR/'profile-baseline-manifest.json'),
              'checkpoint_sha256':digest(REPAIR/'value-format12.pth'),'runner_sha256':digest(ROOT/'tools/run-followup63.sh'),
              'preparer_sha256':digest(ROOT/'tools/prepare-followup63.py'),'scopes':{}}
    for scope,plan in plans.items():
        phase=ROOT/scope
        phase.mkdir();(phase/'reports').mkdir()
        write(phase/'schedule.json',plan)
        roots={}
        for i in range(2):
            lane=phase/f'lane{i}';lane.mkdir()
            for name in CONTENTS:
                src=REPAIR/name
                if src.is_dir():shutil.copytree(src,lane/name,ignore=shutil.ignore_patterns('__pycache__','*.pyc','.git','debug'))
                else:shutil.copy2(src,lane/name)
            for name in PATCHES:
                dest=module_path(lane/'repo',name)
                shutil.copy2(ROOT/'patch'/dest.name,dest)
            (lane/'reports').mkdir();(lane/'saves').mkdir()
            roots[lane.name]=release_digest(lane)
        require(len(set(roots.values()))==1,'Phase lanes differ')
        identity['scopes'][scope]={'schedule_sha256':digest(phase/'schedule.json'),'lanes':roots}
    require(len({v for scope in identity['scopes'].values() for v in scope['lanes'].values()})==1,
            'Follow-up phases must use the same isolated build')
    write(ROOT/'freeze.json',identity)
    verify()


def verify():
    f=read(ROOT/'freeze.json')
    require(f['runner_sha256']==digest(ROOT/'tools/run-followup63.sh')
            and f['preparer_sha256']==digest(ROOT/'tools/prepare-followup63.py'),'Supervisor code changed')
    for scope,plan in schedules().items():
        phase=ROOT/scope;proof=f['scopes'][scope]
        require(read(phase/'schedule.json')==plan and digest(phase/'schedule.json')==proof['schedule_sha256'],
                'Schedule changed')
        for i in range(2):
            lane=phase/f'lane{i}'
            require(lane.resolve(strict=True)==lane and release_digest(lane)==proof['lanes'][lane.name]
                    and digest(lane/'value-format12.pth')==f['checkpoint_sha256']
                    and digest(lane/'profile-baseline-manifest.json')==f['profile_sha256']
                    and digest(lane/'native-bin/battle-engine-worker')==f['native_sha256'],'Frozen inputs changed')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode',choices=('stage','verify'))
    a=p.parse_args()
    stage() if a.mode=='stage' else verify()
