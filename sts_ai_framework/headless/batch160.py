"""Fixed two-lane acceptance: 160 fixed cases, one attempt per case, differences do not stop the lane."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import signal
import threading
import time
import uuid

from . import agent as agent_module
from battle_engine.profiles import TURN_POLICIES
from .agent import HeadlessCollector, ROLES, check_permits, resource_sample, save_json
from .suite import failed_attempt

try:
    import fcntl
except ImportError:  # Windows test/import compatibility; production lanes use flock.
    fcntl = None


def full_schedule(seed_base=5050201):
    if type(seed_base) is not int or seed_base < 0:raise ValueError('Invalid seed base')
    return [{'case_id':f'{role}-{round_index+1:02d}','role':role,
             'seed':seed_base+round_index*len(ROLES)+i,'lane':round_index%2,'round':round_index+1}
            for round_index in range(40) for i,role in enumerate(ROLES)]


def lane_schedule(lane, seed_base=5050201):
    if type(lane) is not int or lane not in (0,1):raise ValueError('Expected lane 0 or 1')
    return [case for case in full_schedule(seed_base) if case['lane']==lane]


def selected_seeds(path):
    """Read the frozen subset; the staging gate binds it to the source report."""
    data = json.loads(Path(path).read_bytes())
    seeds = data['seeds']
    valid = {c['seed'] for c in full_schedule()}
    if (data.get('schema') != 'headless-unfinished-63-v1' or not isinstance(seeds,list)
            or len(seeds) != 63 or any(type(s) is not int for s in seeds)
            or seeds != sorted(set(seeds)) or not set(seeds) <= valid):
        raise ValueError('Invalid unfinished-cohort schedule')
    return set(seeds)


def read_followup_schedule(path):
    """Read a staged plan for one of the admitted follow-up cohorts."""
    data=json.loads(Path(path).read_bytes())
    cases=data['cases']
    scope=data['scope']
    if data.get('schema')!='headless-batch63-followup-plan-v1' or data.get('ascension')!=10 or data.get('max_actions')!=3000:
        raise ValueError('Invalid follow-up schedule envelope')
    if scope=='recheck44':
        seeds={c['seed'] for c in cases}
        expected=[c for c in full_schedule() if c['seed'] in seeds]
        if len(expected)!=44 or [sum(c['lane']==i for c in expected) for i in (0,1)]!=[21,23]:
            raise ValueError('Invalid recheck cohort')
    elif scope=='fresh40':
        expected=full_schedule(5050401)[:40]
    elif scope=='retry29':
        seeds={c['seed'] for c in cases}
        expected=retry_schedule(seeds)
    elif scope=='retry13':
        expected=retry13_schedule({c['seed'] for c in cases})
    elif scope=='retry25':
        expected=retry25_schedule(cases, frozen=True)
    elif scope=='retry16':
        expected=retry16_schedule(cases, frozen=True)
    elif scope=='fresh80':
        expected=fresh80_schedule([c['seed'] for c in cases])
    else:
        raise ValueError('Unknown follow-up cohort')
    if cases!=expected:
        raise ValueError('Follow-up schedule identity changed')
    return data


def _retry_cases(seeds):
    rows=[]
    for scope,cases in (('recheck44',full_schedule()),('fresh40',full_schedule(5050401)[:40])):
        for c in cases:
            if c['seed'] in seeds:rows.append({**c,'case_id':scope+'-'+c['case_id'],'source_scope':scope})
    return rows


def retry_schedule(seeds):
    rows=_retry_cases(seeds)
    if len(rows)!=29 or len(seeds)!=29 or [sum(c['lane']==i for c in rows) for i in (0,1)]!=[15,14]:
        raise ValueError('Invalid 29-case retry schedule')
    return rows


def retry13_schedule(seeds):
    rows=_retry_cases(seeds)
    if len(rows)!=13 or len(seeds)!=13 or [sum(c['lane']==i for c in rows) for i in (0,1)]!=[6,7]:
        raise ValueError('Invalid 13-case retry schedule')
    return rows


def retry25_schedule(source, *, frozen=False):
    """Preserve lane, role and seed from the r6 retry13 and fresh80 reports."""
    old={c['case_id']:c for c in _retry_cases({r['seed'] for r in source})}
    fresh={c['case_id']:c for c in full_schedule()[:80]}
    rows=[]
    for c in source:
        keys=('case_id','role','seed','lane','round','source_scope')
        row={k:c[k] for k in keys}
        scope=row['source_scope']
        identity=row['case_id']
        if frozen:
            if not identity.startswith(scope+'-'):raise ValueError('Invalid r6 retry case identity')
            identity=identity[len(scope)+1:]
        match=(old if scope=='retry13' else fresh if scope=='fresh80' else {}).get(identity)
        if (match is None or any(c[k]!=match[k] for k in ('role','lane','round'))
                or type(c['seed']) is not int or (scope=='retry13' and c['seed']!=match['seed'])
                or (scope=='fresh80' and not (1<<40)<=c['seed']<(1<<52))):
            raise ValueError('Invalid original coordinates for retry25')
        row['case_id']=scope+'-'+identity
        rows.append(row)
    if (len(rows)!=len({c['seed'] for c in rows}) or len(rows)!=len({c['case_id'] for c in rows})
            or [sum(c['source_scope']==s for c in rows) for s in ('retry13','fresh80')]!=[4,21]
            or [sum(c['lane']==i for c in rows) for i in (0,1)]!=[14,11]):
        raise ValueError('Invalid 25-case retry schedule')
    return rows


def fresh80_schedule(seeds):
    # Store ordinary exact JSON integers, outside any historical sequence.
    if len(seeds)!=80 or any(type(s) is not int or not (1<<40)<=s<(1<<52) for s in seeds) or len(set(seeds))!=80:
        raise ValueError('Fresh allocation needs 80 distinct random seeds')
    return [{**case,'seed':seed} for case,seed in zip(full_schedule()[:80],seeds)]


def retry16_schedule(source, *, frozen=False):
    """Preserve coordinates of the 16 signed r7 diagnostics."""
    rows=[]
    for case in source:
        row={k:case[k] for k in ('case_id','role','seed','lane','round','source_scope')}
        scope=row['source_scope']
        prefix=scope+'-' if scope in ('retry25','fresh80') else None
        identity=row['case_id']
        if (prefix is None or type(row['seed']) is not int or row['seed']<0
                or row['role'] not in ROLES or type(row['round']) is not int or not 1<=row['round']<=40
                or type(row['lane']) is not int or row['lane']!=(row['round']-1)%2
                or frozen and not identity.startswith(prefix)):
            raise ValueError('Invalid original coordinates for retry16')
        identity=identity[len(prefix):] if frozen else identity
        base=f"{row['role']}-{row['round']:02d}"
        random_case=identity==(base if scope=='fresh80' else 'fresh80-'+base)
        if random_case:
            valid=row['round']<=20 and (1<<40)<=row['seed']<(1<<52)
        else:
            source_rows=_retry_cases({row['seed']})
            valid=scope=='retry25' and any(identity=='retry13-'+c['case_id'] and
                                            all(row[k]==c[k] for k in ('role','round','lane')) for c in source_rows)
        if not valid:raise ValueError('Invalid retry16 case identity or seed')
        row['case_id']=prefix+identity
        rows.append(row)
    if (len(rows)!=16 or len({r['seed'] for r in rows})!=16 or len({r['case_id'] for r in rows})!=16
            or [sum(r['source_scope']==scope for r in rows) for scope in ('retry25','fresh80')]!=[7,9]
            or [sum(r['lane']==i for r in rows) for i in (0,1)]!=[9,7]):
        raise ValueError('Invalid 16-case retry schedule')
    return rows


def make_plan(root, lane, *, ascension=10, max_actions=3000, seed_base=5050201, configuration=None, seeds=None,
              schedule=None, pilot20=False):
    if type(ascension) is not int or not 0<=ascension<=20:raise ValueError('Invalid ascension')
    if type(max_actions) is not int or max_actions<1:raise ValueError('Invalid action budget')
    global_cases = full_schedule(seed_base)
    cases = lane_schedule(lane,seed_base)
    if pilot20:
        if seeds is not None or schedule is not None or ascension!=10 or seed_base==5050201:
            raise ValueError('Pilot requires A10 and a new seed base')
        global_cases=global_cases[:20]
        cases=[c for c in global_cases if c['lane']==lane]
    if schedule is not None:
        if seeds is not None or (ascension,max_actions,seed_base)!=(10,3000,5050201):
            raise ValueError('Follow-up uses the staged A10 schedule and action budget')
        global_cases=schedule['cases']
        cases=[c for c in global_cases if c['lane']==lane]
        seed_base=5050401 if schedule['scope']=='fresh40' else 5050201
    if seeds is not None:
        if seed_base != 5050201 or ascension != 10 or max_actions != 3000:
            raise ValueError('Retain original A10 plan and action budget')
        global_cases = [c for c in global_cases if c['seed'] in seeds]
        cases = [c for c in cases if c['seed'] in seeds]
        if len(global_cases) != 63 or len(seeds) != 63 or not 0 < len(cases) < 63:
            raise ValueError('Unfinished cohort must be split over two lanes')
    return {'schema':'headless-independent-batch-v1','lane':lane,'root':str(Path(root).resolve()),
            'ascension':ascension,'max_actions':max_actions,'seed_base':seed_base,
            'total_cases':len(global_cases),'lane_count':2,'cases_per_lane':len(cases),
            'per_role_per_lane':{'fresh40':5,'fresh80':10}.get(schedule['scope']) if schedule is not None else
                                (len(cases)//len(ROLES) if seeds is None else None),
            'global_cases':global_cases,'cases':cases,
            'configuration':dict(configuration or {})}


@contextmanager
def lane_lock(root):
    """Keep one process per lane root, including initialization and status writes."""
    reports=Path(root)/'reports';reports.mkdir(parents=True,exist_ok=True)
    path=reports/'headless-batch.lock'
    stream=path.open('a+b');locked=False
    try:
        if fcntl is not None:
            try:fcntl.flock(stream.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
            except OSError as error:raise RuntimeError('Another batch process owns this lane root') from error
        else:
            import msvcrt
            if path.stat().st_size==0:stream.write(b'\0');stream.flush()
            stream.seek(0)
            try:msvcrt.locking(stream.fileno(),msvcrt.LK_NBLCK,1)
            except OSError as error:raise RuntimeError('Another batch process owns this lane root') from error
        locked=True
        yield
    finally:
        if locked:
            if fcntl is not None:fcntl.flock(stream.fileno(),fcntl.LOCK_UN)
            else:
                import msvcrt
                stream.seek(0);msvcrt.locking(stream.fileno(),msvcrt.LK_UNLCK,1)
        stream.close()


def _load(path,plan):
    if path.exists():
        state=json.loads(path.read_text(encoding='utf-8'))
        if state.get('plan')!=plan:raise ValueError('Batch plan changed')
    else:
        state={'plan':plan,'started_unix':time.time(),'attempts':[],'status':'pending','finished':False}
        save_json(path,state)
    cases={case['case_id']:case for case in plan['cases']};seen=set();identities=set()
    for attempt in state['attempts']:
        identity=attempt['attempt_id']
        if (attempt['case_id'] not in cases or attempt['case_id'] in seen or identity in identities
                or uuid.UUID(identity).hex!=identity or attempt['result']['status'] not in ('running','complete','aborted')):
            raise ValueError('Invalid or duplicate batch reservation')
        case=cases[attempt['case_id']]
        if any(attempt['result'].get(k)!=v for k,v in (('role',case['role']),('seed',case['seed']),('attempt_id',identity))):
            raise ValueError('Batch result identity mismatch')
        seen.add(attempt['case_id']);identities.add(identity)
    return state


def _save(path,state,**facts):
    results=[a['result']['status'] for a in state['attempts']]
    state.update(facts,updated_unix=time.time(),summary={
        'attempted':len(results),'completed':results.count('complete'),'differences':results.count('aborted'),
        'running':results.count('running'),'remaining':len(state['plan']['cases'])-len(results)})
    save_json(path,state)


def idle_resources():
    try:sample=resource_sample(None,None)
    except Exception as error:sample={'sampling_error':repr(error)}
    sample.update(threads=threading.active_count(),sampled_unix=time.time())
    try:sample['open_fds']=len(list(Path('/proc/self/fd').iterdir()))
    except OSError:sample['open_fds']=None
    return sample


def _stop_reason(root):
    try:check_permits(root)
    except Exception as error:return repr(error)
    return None


def _stopped(path,state,reason,*,stage='operational',failure=False):
    _save(path,state,status='stopped',finished=False,stop_reason=reason,stop_stage=stage,
          failure=failure,ended_unix=time.time())
    return 1 if failure else 130


def _recover(root,path,state):
    cases={case['case_id']:case for case in state['plan']['cases']}
    for attempt in state['attempts']:
        if attempt['result']['status']=='running':
            attempt.update(result=failed_attempt(root,cases[attempt['case_id']],attempt['attempt_id'],
                RuntimeError('Previous reserved attempt interrupted; censored and not retried')),
                interrupted=True,ended_unix=time.time())
            _save(path,state,status='running',finished=False)


def _execute(collector,path,state):
    _recover(collector.root,path,state)
    attempted={attempt['case_id'] for attempt in state['attempts']}
    for case in state['plan']['cases']:
        if case['case_id'] in attempted:continue
        reason=_stop_reason(collector.root)
        if reason:return _stopped(path,state,reason)
        attempt_id=uuid.uuid4().hex
        attempt={'case_id':case['case_id'],'attempt_id':attempt_id,'started_unix':time.time(),
                 'result':{'status':'running','attempt_id':attempt_id,'role':case['role'],'seed':case['seed'],
                           'censored':True,'transitions':0,'last_floor':None}}
        state['attempts'].append(attempt)
        # save_json flushes/fsyncs the file, atomically replaces it and fsyncs
        # the directory on Linux before episode can launch any process.
        _save(path,state,status='running',finished=False)
        interrupted=False
        try:
            result=collector.episode(case['role'],case['seed'],attempt_id=attempt_id)
            if not isinstance(result,dict) or result.get('status') not in ('complete','aborted'):
                raise ValueError('Episode returned an invalid result')
            if any(result.get(k,v)!=v for k,v in (('role',case['role']),('seed',case['seed']),('attempt_id',attempt_id))):
                raise ValueError('Episode result identity mismatch')
            if result['status']=='aborted':
                diagnostic=failed_attempt(collector.root,case,attempt_id,RuntimeError(result.get('error','Episode aborted')))
                result={**diagnostic,**result,'diagnostic':diagnostic['diagnostic'],'censored':True}
            result={'attempt_id':attempt_id,'role':case['role'],'seed':case['seed'],**result}
        except KeyboardInterrupt as error:
            interrupted=True
            result=failed_attempt(collector.root,case,attempt_id,error)
        except Exception as error:
            result=failed_attempt(collector.root,case,attempt_id,error)
        attempt.update(result=result,collector_after_close=idle_resources(),ended_unix=time.time())
        _save(path,state,status='running',finished=False)
        print(json.dumps({'lane':state['plan']['lane'],'case_id':case['case_id'],'seed':case['seed'],
                          'status':result['status'],'floor':result.get('last_floor'),
                          'transitions':result.get('transitions',0)},ensure_ascii=False),flush=True)
        reason='KeyboardInterrupt' if interrupted else _stop_reason(collector.root)
        if reason:return _stopped(path,state,reason)
    differences=any(a['result']['status']!='complete' for a in state['attempts'])
    _save(path,state,status='finished_with_differences' if differences else 'passed',finished=True,
          failure=False,stop_reason=None,stop_stage=None,ended_unix=time.time())
    return 0


def run_batch(collector,path=None,*,lane,seed_base=5050201,configuration=None):
    path=Path(path) if path is not None else Path(collector.root)/'reports/batch-status.json'
    plan=make_plan(collector.root,lane,ascension=collector.ascension,max_actions=collector.max_actions,
                   seed_base=seed_base,configuration=configuration)
    with lane_lock(collector.root):
        return _execute(collector,path,_load(path,plan))


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lane',type=int,choices=(0,1),required=True)
    parser.add_argument('--root',type=Path,required=True)
    for name in ('launcher','native','checkpoint'):parser.add_argument('--'+name,required=True)
    parser.add_argument('--max-actions',type=int,default=3000)
    parser.add_argument('--ascension',type=int,default=10)
    parser.add_argument('--seed-base',type=int,default=5050201)
    parser.add_argument('--combat-search',choices=TURN_POLICIES,default='legacy')
    cohort=parser.add_mutually_exclusive_group()
    cohort.add_argument('--selection',type=Path)
    cohort.add_argument('--schedule',type=Path)
    cohort.add_argument('--pilot20',action='store_true')
    args=parser.parse_args(argv);root=args.root.resolve();path=root/'reports/batch-status.json'
    signal.signal(signal.SIGTERM,agent_module.request_stop);signal.signal(signal.SIGINT,agent_module.request_stop)
    configuration={k:str(getattr(args,k)) for k in ('launcher','native','checkpoint')}
    search_options={}
    if args.combat_search!='legacy':
        configuration['combat_search']=args.combat_search
        search_options['combat_search']=args.combat_search
    plan=make_plan(root,args.lane,ascension=args.ascension,max_actions=args.max_actions,
                   seed_base=args.seed_base,configuration=configuration,
                   seeds=selected_seeds(args.selection) if args.selection else None,
                   schedule=read_followup_schedule(args.schedule) if args.schedule else None,
                   pilot20=args.pilot20)
    try:
        with lane_lock(root):
            state=_load(path,plan)
            _recover(root,path,state)
            # A finished lane needs neither a fresh model nor a new permit.
            # Running reservations still need recovery/censoring inside _execute.
            if len(state['attempts'])==len(plan['cases']) and all(a['result']['status']!='running' for a in state['attempts']):
                return _execute(type('FinishedLane',(),{'root':root})(),path,state)
            reason=_stop_reason(root)
            if reason:return _stopped(path,state,reason,stage='initializing',failure=True)
            _save(path,state,status='initializing',finished=False)
            try:collector=HeadlessCollector(root,args.launcher,args.native,args.checkpoint,
                                           ascension=args.ascension,max_actions=args.max_actions,**search_options)
            except Exception as error:return _stopped(path,state,repr(error),stage='initializing',failure=True)
            return _execute(collector,path,state)
    except Exception as error:
        # Do not rewrite another process's status or an immutable existing plan.
        diagnostic=root/'reports'/f'batch-startup-failure-{uuid.uuid4().hex}.json'
        if diagnostic.parent.is_dir():save_json(diagnostic,{'error':repr(error),'requested_plan':plan,'failed_unix':time.time()})
        print(json.dumps({'status':'stopped','failure':True,'error':repr(error),'diagnostic':str(diagnostic)}),flush=True)
        return 1


if __name__=='__main__':raise SystemExit(main())
