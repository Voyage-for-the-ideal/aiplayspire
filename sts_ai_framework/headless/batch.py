"""Fixed two-lane acceptance: one attempt per case, differences do not stop the lane."""
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
from .agent import HeadlessCollector, ROLES, check_permits, resource_sample, save_json
from .suite import failed_attempt

try:
    import fcntl
except ImportError:  # Windows test/import compatibility; production lanes use flock.
    fcntl = None


def full_schedule(seed_base=5050101):
    if type(seed_base) is not int or seed_base < 0:raise ValueError('Invalid seed base')
    return [{'case_id':f'{role}-{round_index+1:02d}','role':role,
             'seed':seed_base+round_index*len(ROLES)+i,'lane':round_index%2,'round':round_index+1}
            for round_index in range(6) for i,role in enumerate(ROLES)]


def lane_schedule(lane, seed_base=5050101):
    if type(lane) is not int or lane not in (0,1):raise ValueError('Expected lane 0 or 1')
    return [case for case in full_schedule(seed_base) if case['lane']==lane]


def make_plan(root, lane, *, ascension=10, max_actions=3000, seed_base=5050101, configuration=None):
    if type(ascension) is not int or not 0<=ascension<=20:raise ValueError('Invalid ascension')
    if type(max_actions) is not int or max_actions<1:raise ValueError('Invalid action budget')
    return {'schema':'headless-independent-batch-v1','lane':lane,'root':str(Path(root).resolve()),
            'ascension':ascension,'max_actions':max_actions,'seed_base':seed_base,
            'total_cases':24,'lane_count':2,'cases_per_lane':12,'per_role_per_lane':3,
            'global_cases':full_schedule(seed_base),'cases':lane_schedule(lane,seed_base),
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


def run_batch(collector,path=None,*,lane,seed_base=5050101,configuration=None):
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
    parser.add_argument('--seed-base',type=int,default=5050101)
    args=parser.parse_args(argv);root=args.root.resolve();path=root/'reports/batch-status.json'
    signal.signal(signal.SIGTERM,agent_module.request_stop);signal.signal(signal.SIGINT,agent_module.request_stop)
    configuration={k:str(getattr(args,k)) for k in ('launcher','native','checkpoint')}
    if (root/'combat-search.json').exists(): configuration['combat_search']=agent_module.read_combat_search(root/'combat-search.json')
    plan=make_plan(root,args.lane,ascension=args.ascension,max_actions=args.max_actions,
                   seed_base=args.seed_base,configuration=configuration)
    try:
        with lane_lock(root):
            state=_load(path,plan)
            _recover(root,path,state)
            # A finished lane needs neither a fresh model nor a new permit.
            # Running reservations still need recovery/censoring inside _execute.
            if len(state['attempts'])==12 and all(a['result']['status']!='running' for a in state['attempts']):
                return _execute(type('FinishedLane',(),{'root':root})(),path,state)
            reason=_stop_reason(root)
            if reason:return _stopped(path,state,reason,stage='initializing',failure=True)
            _save(path,state,status='initializing',finished=False)
            try:collector=HeadlessCollector(root,args.launcher,args.native,args.checkpoint,
                                           ascension=args.ascension,max_actions=args.max_actions)
            except Exception as error:return _stopped(path,state,repr(error),stage='initializing',failure=True)
            return _execute(collector,path,state)
    except Exception as error:
        # Do not rewrite another process's status or an immutable existing plan.
        diagnostic=root/'reports'/f'batch-startup-failure-{uuid.uuid4().hex}.json'
        if diagnostic.parent.is_dir():save_json(diagnostic,{'error':repr(error),'requested_plan':plan,'failed_unix':time.time()})
        print(json.dumps({'status':'stopped','failure':True,'error':repr(error),'diagnostic':str(diagnostic)}),flush=True)
        return 1


if __name__=='__main__':raise SystemExit(main())
