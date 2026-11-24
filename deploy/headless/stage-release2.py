"""Stage a new two-lane batch only after release-2 and prior signoffs pass."""
import argparse
import json
from pathlib import Path
import shutil

from sts_ai_framework.headless.agent import save_json
from sts_ai_framework.headless.release2 import SOURCE_SHA256, STOP_COUNT, inventory
from sts_ai_framework.headless.repair_ledger import digest
from sts_ai_framework.headless.repair_signoff import release_digest, require, runtime


P = Path('/home/ubuntu/headless-spire-poc')
OLD = P/'runtime-acceptance-batch160-v1'
SOURCE = P/'runtime-acceptance-batch160-v2'
CANDIDATE = P/'runtime-acceptance-batch160-repairs-v3'
NEW = P/'runtime-acceptance-batch160-v3'
REPORT = 'release1-outcomes.json'
CONTENTS = ('repo', 'tools', 'classes', 'native-bin', 'profile-baseline', 'profile-baseline-manifest.json', 'value-format12.pth')
PROOFS = ('release2-signoff.json', 'repair-signoff.json', 'previous26.json', 'frozen.json')
RUNNERS = ('run-batch160.sh', 'audit-batch160.py', 'stage-release2.py')


def check_proofs(path, root, expected_stops):
    latest, prior, older, frozen = (json.loads((path/n).read_bytes()) for n in PROOFS)
    identity = runtime(root)
    release = release_digest(root)
    require(frozen['release_sha256'] == release == prior['release_sha256'] == latest['release_sha256']
            and latest['runtime_sha256'] == prior['runtime_sha256'] == identity, 'Frozen runtime changed')
    require(latest['schema'] == 'headless-63-signoff-v1' and latest['passed'] is True
            and latest['verified'] == STOP_COUNT and latest['training'] is False
            and latest['source_report_sha256'] == SOURCE_SHA256, 'Latest 63-stop verification did not pass')
    require(len(latest['cases']) == STOP_COUNT and all(c['passed'] is True and c['live']['independent_replay'] is True
            for c in latest['cases']) and {(c['seed'],c['diagnostic_sha256']) for c in latest['cases']} == expected_stops,
            'Latest case proof missing or detached from source')
    require(prior['schema'] == 'headless-102-signoff-v1' and prior['passed'] is True and prior['verified'] == 102
            and len(prior['cases']) == 102 and len({c['seed'] for c in prior['cases']}) == 102
            and all(c['passed'] is True for c in prior['cases']) and prior['training'] is False
            and prior['original_report_sha256'] == digest(OLD/'reports/batch160-report.json'), 'Prior 102 regression missing')
    require(older['rechecked_failed_transitions'] == 26 and len(older['cases']) == 26
            and older['worker_sha256'] == identity['native-bin/battle-engine-worker']
            and prior['previous_26_sha256'] == digest(path/'previous26.json'), 'Prior 26 regression missing')
    require(prior['checkpoint'] == digest(root/'value-format12.pth')
            and prior['profile'] == digest(root/'profile-baseline-manifest.json'), 'Checkpoint or profile changed')


def stops(candidate):
    return {(c['seed'],c['diagnostic_sha256']) for c, _ in inventory(SOURCE,candidate/'evidence'/REPORT)}

def selection(stops):
    return {'schema':'headless-unfinished-63-v1','source_report_sha256':SOURCE_SHA256,
            'seeds':sorted(seed for seed,_ in stops)}


def stage(unfinished=False):
    candidate,new = roots(unfinished)
    require(candidate.resolve(strict=True) == candidate, 'Unexpected release root')
    expected = stops(candidate)
    check_proofs(candidate/'reports', candidate, expected)
    new.mkdir(); (new/'reports').mkdir(); (new/'tools').mkdir()
    if unfinished:save_json(new/'selection.json', selection(expected))
    for name in PROOFS: shutil.copy2(candidate/'reports'/name, new/'reports'/name)
    shutil.copy2(OLD/'preservation-baseline.json', new/'preservation-baseline.json')
    for name in RUNNERS: shutil.copy2(candidate/'tools'/name, new/'tools'/name)
    for i in range(2):
        lane = new/f'lane{i}'
        lane.mkdir()
        for name in CONTENTS:
            src = candidate/name
            if src.is_dir():
                shutil.copytree(src,lane/name,ignore=shutil.ignore_patterns('__pycache__','*.pyc','.git','debug'))
            else: shutil.copy2(src,lane/name)
        (lane/'reports').mkdir(); (lane/'saves').mkdir()
        check_proofs(new/'reports', lane, expected)
    files = {p.relative_to(new).as_posix():digest(p) for p in new.rglob('*')
             if p.is_file() and 'reports' not in p.relative_to(new).parts}
    files.update({f'reports/{n}':digest(new/'reports'/n) for n in PROOFS})
    save_json(new/'frozen-runtime-manifest.json', {'files':files})


def roots(unfinished):
    if unfinished:
        return tuple(r.with_name(r.name+'-unfinished') for r in (CANDIDATE,NEW))
    return CANDIDATE,NEW


def verify(path):
    unfinished = path.name == NEW.name+'-unfinished'
    candidate,new = roots(unfinished)
    require(path == new and new.resolve(strict=True) == new, 'Unexpected batch root')
    expected = stops(candidate)
    if unfinished:
        require(json.loads((new/'selection.json').read_bytes()) == selection(expected), 'Unfinished cohort changed')
    for i in range(2): check_proofs(new/'reports', new/f'lane{i}', expected)
    require(digest(new/'preservation-baseline.json') == digest(OLD/'preservation-baseline.json'),
            'Preservation baseline changed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--stage', action='store_true')
    action.add_argument('--verify', type=Path)
    parser.add_argument('--unfinished', action='store_true')
    a = parser.parse_args()
    stage(a.unfinished) if a.stage else verify(a.verify)
