"""Check the 44 frozen rerun stops, or live-replay selected prefixes in an isolated root."""
import argparse
from collections import Counter
import copy
import json
from pathlib import Path

from battle_engine.worker import Worker
from sts_ai_framework.headless.agent import HeadlessCollector, save_json
from sts_ai_framework.headless.batch160 import lane_lock
from sts_ai_framework.headless.combat import HeadlessCombatBridge
from sts_ai_framework.headless.repair_batch_report import diagnostic, read, verified_replay
from sts_ai_framework.headless.repair_ledger import digest, recorded
from sts_ai_framework.headless.repair_pilot import pilot
from sts_ai_framework.headless.repair_signoff import require, runtime, verify_live

REPORT_SHA256 = '5007bc7aa218caf38b5c3eaf1f7148c2065e92cd67c667df810ada1cac864788'


def inventory(root):
    data, sha = read(root/'reports/unfinished63-report.json')
    require(sha == REPORT_SHA256 and data['schema'] == 'headless-unfinished-63-report-v1'
            and data['planned'] == data['attempted'] == data['independently_replayed'] == 63
            and data['completed'] == 19 and data['verification_errors'] == 0, 'Unexpected source batch')
    cases = [r for r in data['cases'] if r['status']=='aborted']
    require(len(cases) == len({c['seed'] for c in cases}) == 44, 'Incomplete rerun inventory')
    for c in cases:
        require(verified_replay(c),'Original prefix was not independently verified')
        yield c,diagnostic(root,c)


def check_recorded(case,d,worker):
    if not d.get('after'):
        bridge=HeadlessCombatBridge()
        before=bridge.observe(d['before']['state'],run_id=case['attempt_id'],sequence=d['before']['sequence'])
        commands=(d.get('prediction') or {}).get('commands') or []
        req={'snapshot':before.snapshot}
        if commands:
            req.update(operation='predict',command=json.loads(commands[0]['command']))
        else:
            req.update(search_profile='FAST',max_expansions=5000,timeout_ms=250)
        p=worker.execute(req,1000)
        plan=bridge.prepare(before,p)
        return {'status':'valid_command_no_archived_after','method':plan.native_method,
                'args':list(plan.native_args),'predicted_screen':p['expected_screen']}
    result=recorded(case,d,worker)
    if result['status']=='recorded_step_matches':
        bad=copy.deepcopy(d)
        raw=bad['after']['state']
        receipts=[raw['combat_receipt']['rng']]
        if raw.get('combat'):receipts.append(raw['combat']['snapshot']['_headless_rng'])
        for receipt in receipts:
            r=receipt['streams']['misc']
            r['state0']=f"{int(r['state0'],16)^1:016x}"
        require(recorded(case,bad,worker)['status']=='unresolved','Forged RNG was accepted')
    return result


def live_cases(root,batch,cases,seeds):
    require(root != batch and not root.is_relative_to(batch), 'Require an isolated pilot root')
    require(len(seeds)==len(set(seeds)) and set(seeds)<=cases.keys(),'Unknown/repeated seed')
    with lane_lock(root):
        collector=HeadlessCollector(root,str(root/'tools/start-session.sh'),str(root/'native-bin/battle-engine-worker'),
                                    str(root/'value-format12.pth'))
        for seed in seeds:
            case,d=cases[seed]
            out=root/'reports'/f'pilot-{seed}.json'
            proof=root/'reports'/f'proof-{seed}.json'
            require(not out.exists() and not proof.exists(),'Preserve pilot evidence')
            try:
                report=pilot(collector,batch,case,d,latest=True)
                save_json(out,report)
                checked=verify_live(case,d,out,Path(case['episode']['path']),root,runtime(root),latest=True)
                save_json(proof,checked)
                yield {'seed':seed,'status':'live_verified','proof':str(proof)}
            except (ValueError, OSError, KeyError, RuntimeError, TypeError, StopIteration) as exc:
                yield {'seed':seed,'status':'unresolved','error':repr(exc),'receipt':str(out)}


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--batch',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    mode=p.add_mutually_exclusive_group(required=True)
    mode.add_argument('--worker',type=Path)
    mode.add_argument('--pilot-root',type=Path)
    p.add_argument('--seeds',type=int,nargs='+')
    a=p.parse_args(argv)
    batch=a.batch.resolve(strict=True)
    out=a.output.resolve()
    require(not out.exists() and not out.is_relative_to(batch),'Require a fresh diagnostic output')
    cases={c['seed']:(c,d) for c,d in inventory(batch)}
    if a.pilot_root:
        rows=list(live_cases(a.pilot_root.resolve(strict=True),batch,cases,a.seeds or []))
        require(rows,'Select at least one live seed')
    else:
        w=Worker(str(a.worker.resolve(strict=True)))
        try:
            rows=[]
            for seed,(c,d) in cases.items():
                row={k:c[k] for k in ('seed','case_id','lane','role','diagnostic_sha256')}
                try:row.update(check_recorded(c,d,w))
                except (ValueError,RuntimeError,KeyError,TypeError) as e:row.update(status='unresolved',difference=repr(e))
                rows.append(row)
        finally:w.close()
    counts=dict(Counter(r['status'] for r in rows))
    save_json(out,{'schema':'headless-unfinished63-repair-check-v1','source_report_sha256':REPORT_SHA256,
                   'worker_sha256':digest(a.worker) if a.worker else None,'counts':counts,'cases':rows})
    print(json.dumps(counts))
    return int(bool(counts.get('unresolved')))


if __name__=='__main__':raise SystemExit(main())
