"""Check archived A14/A15 aborts using fresh predictions, without running the game."""
import argparse
from collections import Counter
from copy import deepcopy
import json
from pathlib import Path

from battle_engine.worker import Worker
from sts_ai_framework.headless.event_receipts import Stream
from sts_ai_framework.headless.repair_ledger import digest, recorded


def cases(batch):
    plan = json.loads((batch / 'plan.json').read_bytes())
    for lane, ascension in enumerate(plan['ascensions']):
        root = batch / f'lane{lane}'
        src = root / 'reports/collection-summary.json'
        summary = json.loads(src.read_bytes())
        if len(summary['results']) != 80 or summary['model_sha256'] != plan['model_sha256']:
            raise ValueError(f'Invalid archived lane: {root}')
        for index, row in enumerate(summary['results']):
            if (row['seed'], row['role']) != (plan['seed_base'] + index, plan['roles'][index % 4]):
                raise ValueError(f'Archived schedule mismatch: {root}/{index}')
            if row['status'] != 'aborted':
                continue
            diag = root / 'reports' / f"failure-{row['attempt_id']}.json"
            data = json.loads(diag.read_bytes())
            if any(row[key] != data[key] for key in ('seed', 'role', 'attempt_id')):
                raise ValueError(f'Diagnostic identity mismatch: {diag}')
            if data['before']['state']['ascension'] != ascension:
                raise ValueError(f'Diagnostic difficulty mismatch: {diag}')
            case = dict(row, case_id=f'{batch.name}/A{ascension}/{row["seed"]}', lane=lane,
                        episode={'sha256': row['sha256']}, diagnostic_sha256=digest(diag))
            yield case, data


def negative_probe(case, data, worker):
    # Alter the successor, keeping the original driver's method and source token.
    mutated = deepcopy(data)
    state = mutated['after']['state']
    hp = state['player']['hp']
    state['player']['hp'] = hp - 1 if hp else 1
    result = recorded(case, mutated, worker)
    if result['status'] == 'recorded_step_matches':
        raise ValueError(f'Accepted changed successor HP: {case["case_id"]}')
    checks = {'rejected_changed_hp': True}
    if data['before']['state']['phase'] == 'EVENT':
        mutated = deepcopy(data)
        rng = mutated['after']['state']['combat_receipt']['rng']['streams']
        potion = Stream(rng['potion'])
        potion.long()
        rng['potion'] = potion.state()
        if recorded(case, mutated, worker)['status'] == 'recorded_step_matches':
            raise ValueError(f'Accepted an extra potion RNG draw: {case["case_id"]}')
        checks['rejected_extra_potion_rng'] = True
    return checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--batch', action='append', required=True, type=Path)
    parser.add_argument('--worker', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    batches = [p.resolve(strict=True) for p in args.batch]
    out = args.output.resolve()
    if any(out.is_relative_to(batch) for batch in batches):
        parser.error('Write the audit outside the archived batches')
    worker = Worker(args.worker.resolve(strict=True))
    rows = []
    try:
        for batch in batches:
            for case, data in cases(batch):
                result = recorded(case, data, worker)
                result['ascension'] = data['before']['state']['ascension']
                if result['status'] == 'recorded_step_matches':
                    result.update(negative_probe(case, data, worker))
                rows.append(result)
    finally:
        worker.close()
    counts = dict(Counter(r['status'] for r in rows))
    report = dict(worker_sha256=digest(args.worker), counts=counts, cases=rows)
    with out.open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2, ensure_ascii=False)
        stream.write('\n')
    print(json.dumps(counts))


if __name__ == '__main__':
    main()
