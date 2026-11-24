"""Stage previously verified stops and a fresh 80-case batch without opening a permit."""
import argparse
import gzip
import json
from pathlib import Path
import secrets
import shutil

from .agent import save_json
from .batch160 import fresh80_schedule, read_followup_schedule, retry13_schedule, retry16_schedule, retry25_schedule
from .layout import module_path, package_layout
from .repair_batch_report import diagnostic, read, verified_replay
from .repair_followup29 import CONTENTS
from .repair_ledger import digest
from .repair_signoff import release_digest, require


SCOPES=('retry13','fresh80')
R5_RETRY29_SHA256='5e7be90baf506aafbb5d5a0ef0587ab15de4f7c2515b0799f95dc7ff136a97f1'
R6_REPORTS={
    'retry13':('28b2f2dbe4d342bc7be0e4168ce32f62372d8ef0021ad7c39f9780154dff94c7',13,4),
    'fresh80':('b461a8271593439f6e769b9990ab8c608f6f8a4418ec58958b7e2ad678531115',80,21),
}
R7_REPORTS={
    'retry25':('f49b957248e1b6e23b166a0289d938261de495af55483a7c4c6fce8fed8dd56e',25,7),
    'fresh80':('13e56559808a01d2f1bc1f038e9e3552788464bc483f1a26e2545a3508591094',80,9),
}
SCHEDULER_MODULES=('batch160','batch160_next')


def _scheduler_overlay(candidate,runner_repo):
    repo=candidate/'repo'
    require(package_layout(repo)==package_layout(runner_repo),'Runner and signed candidate use different module layouts')
    return {module_path(repo,name).relative_to(candidate).as_posix() for name in SCHEDULER_MODULES}


def batch_stops(root,scope,sha256,total,stops,*,require_preservation=True):
    """Read the verified stop inventory of a frozen follow-up batch."""
    report,sha=read(root/'reports'/f'{scope}-report.json')
    require(sha==sha256 and report['scope']==scope and report['ascension']==10
            and report['planned']==report['attempted']==report['independently_replayed']==total
            and report['verification_errors']==0, 'Unexpected source report: '+scope)
    if require_preservation:
        require(report['aborted']==stops and not report['errors'] and report['preservation']['passed'],
                'Source report did not pass preservation: '+scope)
    aborted=[case for case in report['cases'] if case['status']=='aborted']
    require(len(aborted)==len({case['seed'] for case in aborted})==stops,'Incomplete '+scope)
    for case in aborted:
        require(verified_replay(case),'Source prefix is not signed')
        yield case,diagnostic(root,case)


def scoped_inventory(root,published):
    for scope,spec in published.items():
        phase=root/scope
        for case,details in batch_stops(phase,scope,*spec):
            yield phase,case,details


def stage(candidate, source, target):
    rows=[case for case,_ in batch_stops(source,'retry29',R5_RETRY29_SHA256,29,13,require_preservation=False)]
    source_cases={case['seed']:case for case in rows}
    retry=retry13_schedule(source_cases)
    for plan in retry:
        case=source_cases[plan['seed']]
        require(all(plan[k]==case[k] for k in plan),'Original retry13 coordinates changed')
    proof_path=candidate/'reports/retry29-regression.json'
    proof,proof_sha=read(proof_path)
    worker=candidate/'native-bin/battle-engine-worker'
    require(proof['schema']=='headless-retry29-regression-v1'
            and proof['source_report_sha256']==R5_RETRY29_SHA256 and proof['worker_sha256']==digest(worker)
            and proof['counts']=={'recorded_step_matches':13} and len(proof['cases'])==13,
            'Expected the successful Linux r6 replay receipt')
    witness={p['seed']:p for p in proof['cases']}
    for case in rows:
        p=witness[case['seed']]
        require(all(p[k]==case[k] for k in ('case_id','lane','role','attempt_id','diagnostic_sha256'))
                and p['episode_sha256']==case['episode']['sha256']
                and p['archived_prefix_transitions']==case['episode']['transitions']
                and p['rejects_changed_rng'] is True and p['status']=='recorded_step_matches',
                'Missing matching r6 stop or prefix proof')
    _stage(candidate,source,source/'lane0',target,'retry13',retry,
           candidate/'repo',{'r6-regression.json':(proof_path,proof_sha)},proof['worker_sha256'],set())


def stage_r7(candidate,source,target,runner_repo):
    overlay=_scheduler_overlay(candidate,runner_repo)
    rows=list(scoped_inventory(source,R6_REPORTS))
    retry=retry25_schedule([{**case,'source_scope':phase.name} for phase,case,_ in rows])
    paths={name:candidate/'reports'/name for name in ('recorded-v1.json','no-after-pilots.json')}
    receipts={name:(path,digest(path)) for name,path in paths.items()}
    proofs={name:read(path)[0] for name,path in paths.items()}
    worker_sha=digest(candidate/'native-bin/battle-engine-worker')
    for proof in proofs.values():
        require(proof['schema']=='headless-retry13-fresh80-repair-v1' and proof['worker_sha256']==worker_sha
                and proof['sources']=={k:v[0] for k,v in R6_REPORTS.items()},'R7 proof refers to another build or batch')
    recorded=proofs['recorded-v1.json'];live=proofs['no-after-pilots.json']
    eligible={case['seed']:(phase,case,diagnostic) for phase,case,diagnostic in rows}
    boundaries={seed for seed,(_,_,d) in eligible.items() if not d.get('after')}
    require(len(boundaries)==3 and recorded['counts']=={'recorded_step_matches':22,
                'valid_command_no_archived_after':2,'requires_live_driver':1}
            and live['counts']=={'original_stop_crossed':3},'Incomplete R7 proof')
    archived={p['seed']:p for p in recorded['cases'] if p['status']=='recorded_step_matches'}
    pilots={p['seed']:p for p in live['cases']}
    require(len(recorded['cases'])==25 and len(live['cases'])==3 and set(pilots)==boundaries
            and set(archived)==set(eligible)-boundaries,'R7 witness coverage differs from the stop inventory')
    for seed,(phase,case,_) in eligible.items():
        if seed not in boundaries:
            p=archived[seed]
            require(p['scope']==phase.name and all(p[k]==case[k] for k in
                    ('case_id','lane','role','attempt_id','diagnostic_sha256'))
                    and p['episode_sha256']==case['episode']['sha256'] and p['rejects_changed_rng'] is True,
                    'Archived stop receipt differs from source')
            continue
        p=pilots[seed];ep=p['trajectory'];replay=p['replay'];f=Path(ep['path']).resolve(strict=True)
        require(p['status']=='original_stop_crossed' and p['case_id']==case['case_id']
                and p['native_sha256']==worker_sha and p['diagnostic_sha256']==case['diagnostic_sha256']
                and p['prefix_steps']==case['episode']['transitions'] and p['original_sha256']==case['episode']['sha256']
                and f.parent==candidate/'episodes' and ep['seed']==seed and ep['role']==case['role']
                and replay['replay_verified'] is True and replay['native_sha256']==worker_sha
                and replay['episode_sha256']==ep['sha256']==digest(f) and replay['final_chain']==ep['final_chain'],
                'Live stop receipt differs from source')
        with gzip.open(f,'rt',encoding='utf8') as stream:
            header=json.loads(next(stream))['record']
        policy=header['policy'];runtime=policy['runtime_sha256']
        require(header['seed']==seed and header['role']==case['role'] and policy['ascension']==10
                and policy['model_sha256']==digest(candidate/'value-format12.pth')
                and policy['profile_manifest_sha256']==digest(candidate/'profile-baseline-manifest.json'),
                'Live R7 profile identity changed')
        require(bool(runtime),'Missing live runtime identity')
        for path,sha in runtime.items():
            full=(candidate/path).resolve(strict=True)
            require(full.is_relative_to(candidate) and digest(full)==sha,'Live R7 runtime changed: '+path)
            if path.startswith('repo/') and path not in overlay:
                require(digest(runner_repo/path.removeprefix('repo/'))==sha,'Queued runtime changed: '+path)
    used={c['seed'] for scope in ('retry13','fresh80') for c in read_followup_schedule(source/scope/'schedule.json')['cases']}
    _stage(candidate,source,candidate,target,'retry25',retry,runner_repo,receipts,worker_sha,used)


def stage_r8(candidate,source,target,runner_repo):
    overlay=_scheduler_overlay(candidate,runner_repo)
    rows=list(scoped_inventory(source,R7_REPORTS))
    retry=retry16_schedule([{**case,'source_scope':phase.name} for phase,case,_ in rows])
    eligible={case['seed']:(phase,case,diag) for phase,case,diag in rows}
    paths={name:candidate/'reports'/name for name in ('archived-v5.json','pilots-v5.json')}
    recorded,recorded_sha=read(paths['archived-v5.json'])
    pilots,pilots_sha=read(paths['pilots-v5.json'])
    worker_sha=digest(candidate/'native-bin/battle-engine-worker')
    for proof in (recorded,pilots):
        require(proof['schema']=='headless-retry25-fresh80-repair-v1'
                and proof['sources']=={scope:spec[0] for scope,spec in R7_REPORTS.items()}
                and proof['worker_sha256']==worker_sha,'R8 witness refers to another batch or build')
    live_seeds={seed for seed,(_,_,d) in eligible.items() if not d.get('after')}
    portal={seed for seed,(_,_,d) in eligible.items()
            if d['before']['state'].get('interaction',{}).get('event',{}).get('class_name')=='SecretPortal'}
    archived={row['seed']:row for row in recorded['cases'] if row['status']=='recorded_step_matches'}
    live={row['seed']:row for row in pilots['cases']}
    require(len(live_seeds)==3 and len(portal)==1 and len(recorded['cases'])==16
            and len(pilots['cases'])==4 and pilots['counts']=={'original_stop_crossed':4}
            and set(archived)==set(eligible)-live_seeds and set(live)==live_seeds|portal,'Incomplete R8 witnesses')
    for seed,p in archived.items():
        phase,case,_=eligible[seed]
        require(p['scope']==phase.name and all(p[k]==case[k] for k in
                ('case_id','lane','role','attempt_id','diagnostic_sha256'))
                and p['episode_sha256']==case['episode']['sha256'] and p['rejects_changed_rng'] is True,
                'R8 archived witness differs from source')
    for seed,p in live.items():
        _,case,_=eligible[seed]
        ep=p['trajectory'];replay=p['replay'];path=Path(ep['path']).resolve(strict=True)
        require(p['status']=='original_stop_crossed' and p['case_id']==case['case_id']
                and p['native_sha256']==worker_sha and p['diagnostic_sha256']==case['diagnostic_sha256']
                and p['original_sha256']==case['episode']['sha256'] and p['prefix_steps']==case['episode']['transitions']
                and path.parent==candidate/'episodes' and (ep['seed'],ep['role'])==(seed,case['role'])
                and replay['replay_verified'] is True and replay['native_sha256']==worker_sha
                and replay['episode_sha256']==ep['sha256']==digest(path) and replay['final_chain']==ep['final_chain'],
                'R8 live witness differs from source')
        with gzip.open(path,'rt',encoding='utf8') as stream:
            header=json.loads(next(stream))['record']
        policy=header['policy']
        require((header['seed'],header['role'],policy['ascension'])==(seed,case['role'],10)
                and policy['model_sha256']==digest(candidate/'value-format12.pth')
                and policy['profile_manifest_sha256']==digest(candidate/'profile-baseline-manifest.json')
                and bool(policy['runtime_sha256']),'R8 live profile or runtime identity changed')
        for name,sha in policy['runtime_sha256'].items():
            file=(candidate/name).resolve(strict=True)
            require(file.is_relative_to(candidate) and digest(file)==sha,'R8 live runtime changed: '+name)
            if name.startswith('repo/') and name not in overlay:
                require(digest(runner_repo/name.removeprefix('repo/'))==sha,'R8 runner runtime changed: '+name)
    old=source/'retry25/lane0'
    _verify_policy(candidate,old,runner_repo)
    for name in ('value-format12.pth','profile-baseline-manifest.json','tools/start-session.sh'):
        require(digest(candidate/name)==digest(old/name),'Original evaluation input changed: '+name)
    used={c['seed'] for root,scopes in (
        (source,('retry25','fresh80')),
        (source.with_name('runtime-acceptance-batch-r6-retry13-fresh80'),('retry13','fresh80')),
    ) for scope in scopes for c in read_followup_schedule(root/scope/'schedule.json')['cases']}
    _stage(candidate,source,candidate,target,'retry16',retry,runner_repo,
           {name:(paths[name],sha) for name,sha in zip(paths,(recorded_sha,pilots_sha))},worker_sha,used)


def _verify_policy(candidate,base,runner_repo):
    for name in ('policy','agent'):
        expected=digest(module_path(candidate/'repo',name))
        require(digest(module_path(base/'repo',name))==expected,'Frozen policy changed: '+name)
        require(digest(module_path(runner_repo,name))==expected,'Runner policy changed: '+name)
    routing=Path('repo/sts_ai_framework/routing')
    def routing_files(root):
        return {p.relative_to(root):digest(p) for p in (root/routing).rglob('*.py')}
    require(routing_files(candidate)==routing_files(base),'Frozen route planner changed')
    require({p.relative_to(runner_repo):digest(p) for p in (runner_repo/'sts_ai_framework/routing').rglob('*.py')}
            =={p.relative_to(candidate/'repo'):digest(p) for p in (candidate/routing).rglob('*.py')},
            'Runner route planner changed')


def _stage(candidate,source,base,target,retry_scope,retry,runner_repo,receipts,worker_sha,used):
    _verify_policy(candidate,base,runner_repo)
    require(not target.is_relative_to(source) and not target.is_relative_to(candidate),'Staging needs a separate root')
    require(digest(base/'value-format12.pth')=='73d092fe8330d916e662bac5e8999eb251b34404085d171abe92389bbeacf0f6',
            'Frozen value checkpoint changed')
    random=secrets.SystemRandom()
    for _ in range(4):
        picks=random.sample(range(1<<40,1<<52),80)
        if not used.intersection(picks):break
    else:raise ValueError('Random schedule overlapped archived seeds')
    fresh=fresh80_schedule(picks)
    target.mkdir()
    (target/'reports').mkdir()
    for name,(path,_) in receipts.items():shutil.copy2(path,target/'reports'/name)
    proof_hashes={name:sha for name,(_,sha) in receipts.items()}
    expected=None
    plan_hashes={}
    for scope,cases in zip((retry_scope,'fresh80'),(retry,fresh)):
        phase=target/scope
        (phase/'reports').mkdir(parents=True)
        plan=phase/'schedule.json'
        save_json(plan,{'schema':'headless-batch63-followup-plan-v1','scope':scope,'ascension':10,
                        'max_actions':3000,'cases':cases,'repair_receipts':proof_hashes})
        read_followup_schedule(plan)
        plan_hashes[scope]=digest(plan)
        for i in (0,1):
            lane=phase/f'lane{i}'
            lane.mkdir()
            for name in CONTENTS:
                item=runner_repo if name=='repo' else (candidate if name=='native-bin' else base)/name
                if item.is_dir():
                    shutil.copytree(item,lane/name,ignore=shutil.ignore_patterns(
                        '__pycache__','*.pyc','.git','debug','build','bin'))
                else:
                    shutil.copy2(item,lane/name)
            (lane/'reports').mkdir()
            (lane/'saves').mkdir()
            require(digest(lane/'native-bin/battle-engine-worker')==worker_sha,'Worker staging mismatch')
            actual=release_digest(lane)
            if expected is None:expected=actual
            require(actual==expected,'Lanes would run different frozen inputs')
    save_json(target/'freeze.json',{'schema':f'headless-{retry_scope}-fresh80-freeze-v1','release_sha256':expected,
                                'schedule_sha256':plan_hashes,'repair_receipts':proof_hashes,'ascension':10,
                                'evaluation_only':True})


def verify(target, scope):
    frozen,_=read(target/'freeze.json')
    schemas={'headless-retry13-fresh80-freeze-v1','headless-retry25-fresh80-freeze-v1','headless-retry16-fresh80-freeze-v1'}
    require(frozen['schema'] in schemas and frozen['evaluation_only'] is True,
            'Unexpected staged release')
    receipts=frozen.get('repair_receipts') or {'r6-regression.json':frozen['repair_proof_sha256']}
    require(all(digest(target/'reports'/name)==sha for name,sha in receipts.items()),'Repair witness changed')
    phase=target/scope
    require(digest(phase/'schedule.json')==frozen['schedule_sha256'][scope],'Frozen seed allocation changed')
    for i in (0,1):
        require(release_digest(phase/f'lane{i}')==frozen['release_sha256'],'Frozen runtime changed')


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='mode',required=True)
    prepare=sub.add_parser('stage')
    for key in ('candidate','source','target'):prepare.add_argument(key,type=Path)
    r7=sub.add_parser('stage-r7')
    for key in ('candidate','source','target','runner_repo'):r7.add_argument(key,type=Path)
    r8=sub.add_parser('stage-r8')
    for key in ('candidate','source','target','runner_repo'):r8.add_argument(key,type=Path)
    check=sub.add_parser('verify');check.add_argument('target',type=Path);check.add_argument('scope',choices=(*SCOPES,'retry25','retry16'))
    a=parser.parse_args(argv)
    if a.mode=='stage':stage(a.candidate.resolve(strict=True),a.source.resolve(strict=True),a.target.resolve())
    elif a.mode=='stage-r7':stage_r7(a.candidate.resolve(strict=True),a.source.resolve(strict=True),a.target.resolve(),
                                    a.runner_repo.resolve(strict=True))
    elif a.mode=='stage-r8':stage_r8(a.candidate.resolve(strict=True),a.source.resolve(strict=True),a.target.resolve(),
                                    a.runner_repo.resolve(strict=True))
    else:verify(a.target.resolve(strict=True),a.scope)


if __name__=='__main__':main()
