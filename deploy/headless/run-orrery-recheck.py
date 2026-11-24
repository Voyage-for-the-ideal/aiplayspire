"""One finite original-seed confirmation, with independent replay and automatic closure."""
import json
import os
from pathlib import Path
import signal
import sys
import time

from sts_ai_framework.headless import agent as agent
from sts_ai_framework.headless.agent import HeadlessCollector,save_json
from sts_ai_framework.headless.live_confirmation import Confirmation,evidence,rows,SeededMatcher,goal_check,sha,orrery_ready
from sts_ai_framework.headless.replay import verify_replay

root=Path(sys.argv[1]).resolve(strict=True)
assert root==Path('/home/ubuntu/headless-spire-poc/runtime-acceptance-orrery-fix-v1')
w=root/'pilot';report={'status':'running','started_unix':time.time(),'seed':5050117,'legacy_training_admission':False}
signal.signal(signal.SIGTERM,agent.request_stop);signal.signal(signal.SIGINT,agent.request_stop)
output=root/'reports/orrery-live-report.json';save_json(output,report)
try:
    target=next(c for c in evidence(root) if c['case_id']=='IRONCLAD-05' and c['seed']==5050117)
    collector=HeadlessCollector(w,str(w/'tools/start-session.sh'),str(w/'native-bin/battle-engine-worker'),str(w/'value-format12.pth'))
    result=Confirmation(collector).episode(target,root);report['confirmation']=result
    if result['status']!='verified':raise ValueError(result.get('error','Live target not verified'))
    path=Path(result['trajectory']['path']).resolve(strict=True)
    assert path.is_relative_to(w/'episodes') and sha(path)==result['trajectory']['sha256']
    proof=verify_replay(path,w/'native-bin/battle-engine-worker',require_complete=False)
    assert (proof['transitions'],proof['final_chain'])==(result['trajectory']['transitions'],result['trajectory']['final_chain'])
    fresh=rows(path);original=rows(root/target['episode'])
    obs=[r for r in fresh if r['type']=='observation'];trans=[r for r in fresh if r['type']=='transition']
    old_obs=[r for r in original if r['type']=='observation'];old_trans=[r for r in original if r['type']=='transition']
    count=result['prefix_transitions'];matcher=SeededMatcher()
    assert len(trans)==count+1 and result['target_steps']==1
    for i in range(count+1):
        matcher.match(old_obs[i]['state'],obs[i]['state'])
        if i<count:assert all(trans[i][k]==old_trans[i][k] for k in ('native_method','native_args'))
    assert obs[count]['sequence']==result['target_start_sequence']
    assert orrery_ready(obs[count]['state'],trans[count-1]['native_method'])
    step={'method':trans[-1]['native_method'],'args':trans[-1]['native_args'],'effect':trans[-1]['effect'],'after':obs[-1]['state']}
    assert goal_check('orrery',obs[count]['state'],[step])
    assert fresh[-1]['error']=='targeted_live_confirmation_complete'
    header=fresh[0];policy=header['policy']
    assert (header['role'],header['seed'],policy['ascension'])==('IRONCLAD',5050117,10)
    assert policy['model_sha256']==sha(w/'value-format12.pth') and policy['profile_manifest_sha256']==sha(w/'profile-baseline-manifest.json')
    for name,digest in policy['runtime_sha256'].items():
        p=(w/name).resolve(strict=True);assert p.is_relative_to(w) and sha(p)==digest,name
    report.update(status='passed',independent_replay=proof,original_prefix_transitions=count,
                  actual_return_screen=obs[-1]['state']['screen'],gameplay_rng_streams_unchanged=14,
                  original_target='IRONCLAD-05',native_sha256=sha(w/'native-bin/battle-engine-worker'))
except Exception as error:
    report.update(status='failed',error=repr(error))
finally:
    archive=w/'reports/permit-archive';archive.mkdir(exist_ok=True)
    closed=[]
    for name in ('ALLOW_RUN_SMOKE','ALLOW_COLLECTION'):
        source=w/name;destination=archive/(name+'.orrery-recheck-finished')
        if source.is_file() and not source.is_symlink() and not destination.exists():source.rename(destination)
        closed.append(not source.exists() and not source.is_symlink())
    report.update(ended_unix=time.time(),scoped_permits_closed=all(closed),training_started=False)
    save_json(output,report)
print(json.dumps({k:report.get(k) for k in ('status','seed','original_prefix_transitions','actual_return_screen','scoped_permits_closed','error')}),flush=True)
sys.exit(0 if report['status']=='passed' and report['scoped_permits_closed'] else 1)
