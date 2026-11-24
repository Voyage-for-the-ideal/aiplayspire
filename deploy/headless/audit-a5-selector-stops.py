"""Replay archived failing transitions without starting a game or changing the batch."""
import argparse
from collections import Counter
import copy
import hashlib
import json
from pathlib import Path

from battle_engine.worker import Worker
from sts_ai_framework.headless.repair_ledger import recorded


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('batch','worker','output'): parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--seed', type=int, action='append')
    args=parser.parse_args()
    batch, output=args.batch.resolve(), args.output.resolve()
    if output.is_relative_to(batch): parser.error('Preserve the source batch')
    rows=[]
    worker=Worker(args.worker.resolve(strict=True))
    try:
        for lane in range(2):
            directory=batch/f'lane{lane}'/'reports'
            summary=json.loads((directory/'collection-summary.json').read_bytes())
            for case in summary['results']:
                if case['status']!='aborted' or args.seed and case['seed'] not in args.seed: continue
                path=directory/f"failure-{case['attempt_id']}.json"
                d=json.loads(path.read_bytes())
                assert (d['seed'],d['role'],d['attempt_id'])==(case['seed'],case['role'],case['attempt_id'])
                identity=dict(case,case_id=f"{lane}-{case['seed']}",lane=lane,
                              episode={'sha256':case.get('sha256')},diagnostic_sha256=digest(path))
                row=recorded(identity,d,worker)
                if row['status']=='recorded_step_matches':
                    bad=copy.deepcopy(d)
                    (bad['after']['state']['driver_receipt'])['source_state_token']='detached'
                    assert recorded(identity,bad,worker)['status']=='unresolved','Accepted a detached receipt'
                rows.append(row)
    finally:
        worker.close()
    counts=dict(Counter(r['status'] for r in rows))
    with output.open('x',encoding='utf-8') as f:
        json.dump(dict(worker_sha256=digest(args.worker),counts=counts,cases=rows),f,indent=2)
    print(json.dumps(counts))


if __name__=='__main__':
    main()
