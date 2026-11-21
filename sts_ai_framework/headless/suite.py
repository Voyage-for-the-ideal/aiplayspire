"""Resumable, serial multi-seed acceptance; a failed episode stops the batch."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import signal
import threading
import time
import uuid

from . import agent as agent_module
from .agent import HeadlessCollector, ROLES, check_permits, resource_sample, save_json


def schedule(per_role=10, seed_base=5050101):
    if type(per_role) is not int or not 1 <= per_role <= 10:
        raise ValueError("Expected one to ten episodes per role")
    return [{"case_id": f"{role}-{n+1:02d}", "role": role, "seed": seed_base+n*len(ROLES)+i}
            for n in range(per_role) for i, role in enumerate(ROLES)]


def save(path, value):
    save_json(path, value)


def failed_attempt(root, case, attempt_id, error):
    reports = root / 'reports'; reports.mkdir(exist_ok=True)
    diagnostic = reports / f'failure-{attempt_id}.json'
    if not diagnostic.exists():
        save_json(diagnostic, {'attempt_id': attempt_id, 'role': case['role'], 'seed': case['seed'],
                               'error': repr(error), 'stage': 'suite'})
    return {'attempt_id': attempt_id, 'role': case['role'], 'seed': case['seed'],
            'status': 'aborted', 'censored': True, 'terminal': None, 'transitions': 0,
            'last_floor': None, 'error': repr(error), 'diagnostic': str(diagnostic)}


def run_suite(collector, path, *, per_role=10, seed_base=5050101, retry_failed=False):
    plan = {"schema": "headless-multiseed-acceptance-v1", "ascension": collector.ascension,
            "max_actions": collector.max_actions, "cases": schedule(per_role, seed_base)}
    if path.exists():
        state = json.loads(path.read_text(encoding='utf-8'))
        if state['plan'] != plan: raise ValueError("Acceptance plan changed")
    else:
        state = {"plan": plan, "started_unix": time.time(), "attempts": [], "status": "pending"}
        save(path, state)
    # A killed process may have left a durable reservation but no committed result.
    # Keep it censored and require the same explicit retry as any other failed case.
    cases = {case['case_id']: case for case in plan['cases']}
    for attempt in state['attempts']:
        if attempt.get('result', {}).get('status') == 'running':
            attempt_id = attempt.setdefault('attempt_id', uuid.uuid4().hex)
            attempt['result'] = failed_attempt(collector.root, cases[attempt['case_id']], attempt_id,
                                                RuntimeError('Previous attempt interrupted before result commit'))
            attempt['ended_unix'] = time.time()
            state.update(status='stopped_on_difference', updated_unix=time.time())
            save(path, state)
    for case in plan['cases']:
        previous = [a for a in state['attempts'] if a['case_id'] == case['case_id']]
        if any(a['result']['status'] == 'complete' for a in previous): continue
        if previous and not retry_failed: raise ValueError("Previous case failed; inspect/fix before --retry-failed")
        check_permits(collector.root)
        attempt_id = uuid.uuid4().hex
        attempt = {'case_id': case['case_id'], 'attempt': len(previous)+1, 'attempt_id': attempt_id,
                   'started_unix': time.time(),
                   'result': {'status': 'running', 'role': case['role'], 'seed': case['seed'],
                              'censored': True, 'transitions': 0, 'last_floor': None}}
        state['attempts'].append(attempt)
        state.update(status='running', updated_unix=time.time()); save(path, state)
        try:
            result = collector.episode(case['role'], case['seed'], attempt_id=attempt_id)
        except Exception as error:
            result = failed_attempt(collector.root, case, attempt_id, error)
        idle = resource_sample(None, None)
        idle.update(threads=threading.active_count(), open_fds=len(list(Path('/proc/self/fd').iterdir())) if Path('/proc/self/fd').is_dir() else None)
        attempt.update(result=result, collector_after_close=idle, ended_unix=time.time())
        state.update(status='running' if result['status']=='complete' else 'stopped_on_difference', updated_unix=time.time())
        save(path, state)
        print(json.dumps({"case_id":case['case_id'], "seed":case['seed'], "status":result['status'],
                          "floor":result['last_floor'], "transitions":result['transitions']}, ensure_ascii=False), flush=True)
        if result['status'] != 'complete': return 1
    state.update(status='complete', ended_unix=time.time()); save(path, state)
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    for name in ('launcher','native','checkpoint'): parser.add_argument('--'+name, required=True)
    parser.add_argument('--per-role', type=int, default=10)
    parser.add_argument('--seed-base', type=int, default=5050101)
    parser.add_argument('--ascension', type=int, default=10)
    parser.add_argument('--max-actions', type=int, default=3000)
    parser.add_argument('--retry-failed', action='store_true')
    args=parser.parse_args(argv)
    signal.signal(signal.SIGTERM, agent_module.request_stop); signal.signal(signal.SIGINT, agent_module.request_stop)
    collector=HeadlessCollector(args.root,args.launcher,args.native,args.checkpoint,
                                ascension=args.ascension,max_actions=args.max_actions)
    return run_suite(collector,args.root/'reports/multiseed-status.json',per_role=args.per_role,
                     seed_base=args.seed_base,retry_failed=args.retry_failed)


if __name__ == '__main__': raise SystemExit(main())
