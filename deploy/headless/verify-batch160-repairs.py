#!/usr/bin/env python3
"""Recheck recorded batch-160 failing transitions without starting a game."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import time

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import HeadlessCombatBridge, verify_combat_effect
from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect

GROUPS = {
    'knowing_skull_direct_card': {5050346, 5050324, 5050255, 5050272, 5050279},
    'dead_curl_up_latch': {5050316, 5050266, 5050273, 5050257, 5050317},
    'mantra_source_constant': {5050276, 5050340, 5050304},
    'red_slaver_entangle': {5050229, 5050335, 5050221, 5050358},
    'pen_nib_armed_sentinel': {5050345, 5050244, 5050321, 5050203, 5050336, 5050231, 5050357, 5050271, 5050264},
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--batch', required=True, type=Path)
    parser.add_argument('--worker', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    batch = args.batch.resolve(strict=True)
    output = args.output.resolve()
    if output.is_relative_to(batch):
        raise ValueError('Output must not overwrite the original batch')
    original = batch / 'reports/batch160-report.json'
    report = json.loads(original.read_text())
    cases = {case['seed']: case for case in report['cases']}
    results = []
    worker = Worker(args.worker.resolve(strict=True))
    try:
        for group, seeds in GROUPS.items():
            for seed in sorted(seeds):
                case = cases[seed]
                diagnostic = (batch / ('lane%d/reports/failure-%s.json' %
                                      (case['lane'], case['attempt_id']))).resolve(strict=True)
                if not diagnostic.is_relative_to(batch) or sha(diagnostic) != case['diagnostic_sha256']:
                    raise ValueError('Original diagnostic integrity failed: ' + case['case_id'])
                data = json.loads(diagnostic.read_text())
                before, after = data['before'], data['after']
                row = {'case_id':case['case_id'], 'seed':seed, 'group':group,
                       'diagnostic_sha256':sha(diagnostic)}
                if group == 'knowing_skull_direct_card':
                    bridge = IroncladInteractionBridge(characters=('IRONCLAD','THE_SILENT','DEFECT','WATCHER'))
                    left = bridge.observe(before['state'], run_id='audit', sequence=before['sequence'])
                    actions = [a for a in left.actions if a.native_method == data['method'] and list(a.native_args) == data['args']]
                    if len(actions) != 1: raise ValueError('Action is not uniquely legal')
                    plan = bridge.prepare(left, actions[0].api_action)
                    right = bridge.observe(after['state'], run_id='audit', sequence=after['sequence'])
                    if verify_effect(plan, right).status != 'APPLIED': raise ValueError('Event postconditions differ')
                    row.update(status='RECORDED_EVENT_EFFECT_MATCHED', event_rng_independently_predicted=False)
                else:
                    bridge = HeadlessCombatBridge()
                    left = bridge.observe(before['state'], run_id='audit', sequence=before['sequence'])
                    command = json.loads(data['prediction']['commands'][0]['command'])
                    prediction = worker.execute({'operation':'predict','snapshot':left.snapshot,'command':command},1000)
                    plan = bridge.prepare(left, prediction)
                    right = bridge.observe(after['state'], run_id='audit', sequence=after['sequence'])
                    fingerprint = worker.execute({'operation':'fingerprint','snapshot':right.snapshot},1000)
                    if verify_combat_effect(plan, right, fingerprint) != 'APPLIED':
                        raise ValueError('Fresh prediction differs: ' + case['case_id'])
                    bad = copy.deepcopy(fingerprint)
                    stream = next(iter(bad['exact_rng'].values()))
                    stream['counter'] += 1
                    if verify_combat_effect(plan, right, bad) != 'UNVERIFIED':
                        raise ValueError('Corrupted RNG incorrectly accepted')
                    row.update(status='RECORDED_COMBAT_EFFECT_MATCHED', projection_equal=True,
                               modeled_rng_equal=True, native_key_equal=True, corrupted_rng_rejected=True)
                results.append(row)
    finally:
        worker.close()
    result = {'schema':'headless-batch160-repair-audit-v1','generated_unix':time.time(),
              'original_report_sha256':sha(original),'worker_sha256':sha(args.worker.resolve()),
              'new_games':0,'training_admission':False,'rechecked_failed_transitions':len(results),
              'cases':results}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'rechecked':len(results), 'groups':{k:len(v) for k,v in GROUPS.items()},
                      'output':str(output)}))


if __name__ == '__main__':
    main()
