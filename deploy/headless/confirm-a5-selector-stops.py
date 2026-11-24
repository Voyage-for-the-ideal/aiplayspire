"""Confirm selected archived failures on a separate A5 collector runtime."""
import argparse
import json
import re
from pathlib import Path

from sts_ai_framework.headless.agent import HeadlessCollector, save_json
from sts_ai_framework.headless.batch import lane_lock
from sts_ai_framework.headless.repair_ledger import digest
from sts_ai_framework.headless.repair_pilot import pilot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('root', 'batch'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--seed', type=int, action='append', required=True)
    parser.add_argument('--label', default='')
    args = parser.parse_args()
    if args.label and not re.fullmatch(r'[a-z0-9-]+', args.label):
        parser.error('Invalid receipt label')
    root, batch = args.root.resolve(strict=True), args.batch.resolve(strict=True)
    if root.is_relative_to(batch):
        parser.error('Require isolated runtime')
    cases = {}
    reports_by_lane = [batch / f'lane{lane}' / 'reports' for lane in range(2)]
    if (batch / 'reports' / 'collection-summary.json').is_file():
        reports_by_lane = [batch / 'reports']
    for lane, reports in enumerate(reports_by_lane):
        for row in json.loads((reports / 'collection-summary.json').read_bytes())['results']:
            seed = row['seed']
            if seed not in args.seed or row['status'] != 'aborted':
                continue
            path = reports / f"failure-{row['attempt_id']}.json"
            raw = json.loads(path.read_bytes())
            if seed in cases or any(row[key] != raw[key] for key in ('seed', 'role', 'attempt_id')):
                raise ValueError(f'Invalid case {seed}')
            episode = {key: row[key] for key in ('path', 'sha256', 'final_chain')}
            cases[seed] = (dict(row, case_id=f'{lane}-{seed}', episode=episode,
                                diagnostic_sha256=digest(path)), raw)
    if cases.keys() != set(args.seed):
        parser.error('Every requested seed must refer to one archived stop')
    with lane_lock(root):
        collector = HeadlessCollector(root, str(root / 'tools/start-session.sh'),
                                      str(root / 'native-bin/battle-engine-worker'),
                                      str(root / 'value-format12.pth'), ascension=5)
        if collector.runtime_search != dict(search_profile='DEEP', search_policy='legacy',
                                            max_expansions=15000, timeout_ms=0):
            raise ValueError('Expected frozen legacy / DEEP / 15000 / 0')
        for seed in args.seed:
            tag = '-' + args.label if args.label else ''
            out = root / 'reports' / f'pilot-{seed}{tag}.json'
            if out.exists():
                raise ValueError(f'Pilot receipt already exists: {out}')
            result = pilot(collector, batch, *cases[seed], latest=True)
            save_json(out, result)
            print(seed, result['status'], result.get('error', ''), flush=True)


if __name__ == '__main__':
    main()
