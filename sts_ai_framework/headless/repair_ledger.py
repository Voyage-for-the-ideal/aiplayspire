"""Read-only inventory and fresh predictions for the 102 outstanding batch160 stops.

A recorded effect match is not a live continuation or permission to submit a batch.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

from battle_engine.worker import Worker
from .agent import ROLES, verify_driver
from .combat import HeadlessCombatBridge, is_native_combat_selection, verify_combat_effect
from .interaction import IroncladInteractionBridge, verify_effect
from .replay import command


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def first_diff(x, y, path=''):
    if type(x) is type(y):
        if isinstance(x, dict):
            for key in sorted(x.keys() | y.keys()):
                if key not in x or key not in y: return path+'/'+key+' (missing)'
                delta = first_diff(x[key], y[key], path+'/'+key)
                if delta: return delta
        elif isinstance(x, list):
            if len(x) != len(y): return f'{path}/length: {len(x)} != {len(y)}'
            for index, (a, b) in enumerate(zip(x, y)):
                delta = first_diff(a, b, f'{path}/{index}')
                if delta: return delta
        elif x != y: return f'{path}: {x!r} != {y!r}'
        return None
    return f'{path}: {type(x).__name__} != {type(y).__name__}'


def inventory(batch, previous):
    original = batch/'reports/batch160-report.json'
    old = json.loads(previous.read_bytes())
    if old['original_report_sha256'] != digest(original): raise ValueError('Repair audit belongs to another batch')
    cases = json.loads(original.read_bytes())['cases']
    known = {r['case_id']:r for r in old['cases']}
    aborted = [c for c in cases if c['status']=='aborted']
    if (len(cases), len(aborted), len(known)) != (160,128,26): raise ValueError('Invalid batch inventory')
    found = set()
    for case in aborted:
        path = batch/f"lane{case['lane']}"/'reports'/f"failure-{case['attempt_id']}.json"
        sha = digest(path)
        d = json.loads(path.read_bytes())
        if (sha != case['diagnostic_sha256'] or any(d[k]!=case[k] for k in ('role','seed','attempt_id'))
                or d['before']['state']['seed']!=case['seed']): raise ValueError('Evidence changed: '+case['case_id'])
        if case['case_id'] in known:
            r = known[case['case_id']]
            if r['seed']!=case['seed'] or r['diagnostic_sha256']!=sha: raise ValueError('Previous proof mismatch')
            found.add(case['case_id'])
            continue
        yield case, d
    if found != known.keys(): raise ValueError('Previous proofs do not cover 26 distinct failures')


def recorded(case, d, worker):
    raw=d['before']['state']; after=d.get('after')
    pred = (d.get('prediction') or {}).get('commands') or []
    intended = json.loads(pred[0]['command']) if pred else None
    receipt=after['state'].get('driver_receipt',{}) if after else {}
    method=d.get('method',receipt.get('method')); args=d.get('args',receipt.get('args'))
    row=dict(case_id=case['case_id'],lane=case['lane'],role=case['role'],seed=case['seed'],
             attempt_id=case['attempt_id'],episode_sha256=case['episode']['sha256'],diagnostic_sha256=case['diagnostic_sha256'],
             sequence=d['before']['sequence'],state_token=d['before']['state_token'],phase=raw['phase'],
             method=method,args=args,intended_command=intended,original_error=d['error'],has_after=bool(after))
    if not after:
        row.update(status='requires_original_seed',last_receipt=raw.get('driver_receipt'))
        return row
    run=case['attempt_id']; before=d['before']; combat=HeadlessCombatBridge()
    try:
        if method is None or not isinstance(args,list): raise ValueError('Missing command receipt')
        verify_driver(before,after,method,args)
        if after['sequence'] != before['sequence']+1: raise ValueError('Wrong successor sequence')
        if raw['phase']=='PLAYER_TURN' or is_native_combat_selection(raw):
            b=combat.observe(raw,run_id=run,sequence=before['sequence'])
            p=worker.execute(dict(operation='predict',snapshot=b.snapshot,command=command(method,args)),1000)
            if not p.get('supported'): raise ValueError('Predict: '+str(p.get('reason')))
            plan=combat.prepare(b,p)
            if (plan.native_method,list(plan.native_args))!=(method,args): raise ValueError('Wrong predicted action')
            a=combat.observe(after['state'],run_id=run,sequence=after['sequence'])
            fp=worker.execute(dict(operation='fingerprint',snapshot=a.snapshot),1000) if a.snapshot is not None else {}
            verdict=verify_combat_effect(plan,a,fp)
            if verdict!='APPLIED':
                row['difference']=fp.get('reason') or next((diff for x,y in [('expected_after','projection'),
                    ('expected_exact_rng','exact_rng'),('expected_native_key','native_key')] if
                    (diff:=first_diff(p.get(x),fp.get(y),y))), 'private state, unmodelled stream, or transition guard')
        else:
            menus=IroncladInteractionBridge(characters=ROLES)
            b=menus.observe(raw,run_id=run,sequence=before['sequence'])
            actions=[a for a in b.actions if a.native_method==method and list(a.native_args)==args]
            if len(actions)!=1: raise ValueError('Original action is not uniquely legal')
            a=menus.observe_outcome(after['state'],run_id=run,sequence=after['sequence'],combat=combat,
                    import_snapshot=lambda s:worker.execute(dict(operation='fingerprint',snapshot=s),1000))
            result=verify_effect(menus.prepare(b,actions[0].api_action),a); verdict=result.status
            if verdict!='APPLIED':row['difference']=result.reason
        row['status']='recorded_step_matches' if verdict=='APPLIED' else 'unresolved'
    except (KeyError,TypeError,ValueError,RuntimeError) as exc:
        row.update(status='unresolved',difference=str(exc))
    return row


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ('batch','previous','worker','output'): parser.add_argument('--'+key,type=Path,required=True)
    args=parser.parse_args();batch=args.batch.resolve(strict=True);out=args.output.resolve()
    if out.is_relative_to(batch) or out==args.previous.resolve():raise ValueError('Preserve original evidence')
    worker=Worker(args.worker.resolve(strict=True))
    try: rows=[recorded(c,d,worker) for c,d in inventory(batch,args.previous)]
    finally: worker.close()
    if (len({r['seed'] for r in rows}),sum(r['has_after'] for r in rows))!=(102,73): raise ValueError('Incomplete ledger')
    result=dict(schema='headless-batch160-repair-ledger-v2',batch_sha256=digest(batch/'reports/batch160-report.json'),
                previous_audit_sha256=digest(args.previous),native_sha256=digest(args.worker),
                counts=dict(Counter(r['status'] for r in rows)),cases=rows)
    out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps(result['counts']))


if __name__=='__main__':main()
