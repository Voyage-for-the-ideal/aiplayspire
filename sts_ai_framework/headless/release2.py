"""Bind the 63 release-1 stops to lane results and verify a candidate's live pilots."""
import argparse
import json
from pathlib import Path

from .agent import HeadlessCollector, save_json
from .batch import lane_lock
from .batch160 import lane_schedule
from .live_confirmation import streams
from .collection import validate_episode
from .repair_batch_report import action, coordinates, diagnostic, read
from .repair_ledger import digest
from .repair_pilot import pilot
from .repair_signoff import release_digest, require, runtime, verify_live


SOURCE_SHA256 = 'fc1a0d85f48bfb714abcb1618c48437f19c39490fa702792a474e2639a1ac84f'
STOP_COUNT = 63


def inventory(batch, source):
    reported, sha = read(source)
    require(sha == SOURCE_SHA256 and reported['schema'] == 'rerun-lane-outcomes-pre-independent-review-v1',
            'Unrecognized source report')
    cases = {c['case_id']: c for c in reported['cases']}
    lanes = {lane['lane']: lane for lane in reported['lanes']}
    require(len(cases) == len(reported['cases']) == 160 and set(lanes) == {0, 1}, 'Invalid source inventory')
    require(digest(batch/'reports/repair-signoff.json') == reported['signoff_sha256'], 'Source signoff changed')
    stops = []
    for lane in range(2):
        directory = batch/f'lane{lane}'
        actual, sha = read(directory/'reports/batch-status.json')
        expected = lane_schedule(lane)
        require(sha == lanes[lane]['results_sha256'] and actual['plan']['cases'] == expected
                and actual['plan']['ascension'] == 10, 'Archived lane plan or results changed')
        attempts = {a['case_id']: a for a in actual['attempts']}
        require(len(attempts) == len(actual['attempts']) == len(expected), 'Missing or duplicate attempts')
        for c in expected:
            name = c['case_id']; summary = cases[name]; a = attempts[name]; e = a['result']
            require(all(summary[k] == c[k] for k in ('seed', 'role')) and summary['lane'] == lane
                    and all(e[k] == summary[k] for k in ('seed', 'role', 'status', 'last_floor', 'transitions'))
                    and e['attempt_id'] == a['attempt_id'], 'Stop identity differs: ' + name)
            if e['status'] != 'aborted':
                require(e['status'] == 'complete', 'Unclassified attempt: ' + name)
                continue
            case = {**summary, 'episode': e, 'attempt_id': a['attempt_id']}
            diag = diagnostic(batch, case)
            require(diag['episode_id'] == e['episode_id'] and e['error'] == diag['error'] == summary['error']
                    and coordinates(diag['before']['state']) == summary['coordinates']['new_before']
                    and action(diag) == summary['coordinates']['new_action'],
                    'Stop boundary differs: ' + name)
            streams(diag['before']['state'])
            path = episode(case, batch)
            require(path.parent == directory/'episodes', 'Episode is not in the matching lane')
            stops.append((case, diag))
    require(len(stops) == STOP_COUNT and len({c['seed'] for c, _ in stops}) == STOP_COUNT,
            'Expected all 63 distinct stops')
    return stops


def episode(case, batch):
    path = Path(case['episode']['path']).resolve(strict=True)
    require(path.parent == batch/f"lane{case['lane']}"/'episodes', 'Archived episode outside its lane')
    return path


def validate(candidate, source_batch, source, pilots, output, expected_release):
    identities = runtime(candidate)
    require(release_digest(candidate) == expected_release, 'Candidate changed during case verification')
    receipts = {}
    for p in pilots:
        p = p.resolve(strict=True)
        require(p.parent == candidate and not any((p/k).exists() or (p/k).is_symlink()
                for k in ('ALLOW_COLLECTION', 'ALLOW_RUN_SMOKE')), 'Pilot isolation or permit closure failed')
        require(runtime(p) == identities and release_digest(p) == expected_release, 'Pilot build differs')
        for f in sorted((p/'reports').glob('pilot-*.json')):
            seed = int(f.stem.split('-')[-1])
            require(seed not in receipts, 'Duplicate pilot receipt')
            receipts[seed] = f
    outcomes = []
    for case, diag in inventory(source_batch, source):
        row = {k: case[k] for k in ('case_id', 'seed', 'role', 'diagnostic_sha256')}
        receipt = receipts.get(case['seed'])
        try:
            require(receipt is not None, 'Missing original-seed replay')
            original = episode(case, source_batch)
            require(digest(original) == case['episode']['sha256'], 'Source episode changed')
            archived = validate_episode(original, require_complete=False)
            require(archived['final_chain'] == case['episode']['final_chain']
                    and archived['transitions'] == case['episode']['transitions'], 'Source episode chain changed')
            row['live'] = verify_live(case, diag, receipt, original, candidate, identities, latest=True)
            row['passed'] = True
        except (KeyError, ValueError, OSError, RuntimeError, TypeError, IndexError, StopIteration) as exc:
            row.update(passed=False, error=repr(exc))
            if receipt:
                row['receipt'] = str(receipt)
                try: row['pilot_error'] = read(receipt)[0].get('error')
                except (OSError, ValueError, TypeError, AttributeError): pass
        outcomes.append(row)
    count = sum(r['passed'] for r in outcomes)
    report = {'schema': 'headless-63-signoff-v1', 'passed': count == STOP_COUNT, 'verified': count,
              'source_report_sha256': digest(source), 'release_sha256': expected_release,
              'runtime_sha256': identities, 'cases': outcomes, 'training': False}
    require(not output.exists() and not output.is_relative_to(source_batch), 'Require fresh signoff output')
    save_json(output, report)
    return report


def save_pilot_receipt(out, seed, report):
    try:
        save_json(out, report)
    except (TypeError, ValueError) as exc:
        # A per-case payload bug must not drop the rest of the lane. An I/O
        # failure on both writes remains an operational error, not a pass.
        trajectory = report.get('trajectory')
        path = trajectory.get('path') if isinstance(trajectory, dict) else None
        report = dict(seed=seed, status='unresolved', error=f'Pilot receipt serialization failed: {exc!r}')
        if isinstance(path, str): report['trajectory_path'] = path
        save_json(out, report)
    return report


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='mode', required=True)
    for mode in ('plan', 'pilot', 'verify'):
        action = sub.add_parser(mode)
        action.add_argument('--batch', type=Path, required=True)
        action.add_argument('--source', type=Path, required=True)
        if mode != 'plan':
            action.add_argument('--root', type=Path, required=True)
        if mode == 'pilot':
            action.add_argument('--seeds', type=int, nargs='+', required=True)
        if mode == 'verify':
            action.add_argument('--pilots', type=Path, nargs=2, required=True)
            action.add_argument('--output', type=Path, required=True)
            action.add_argument('--release', required=True)
    args = p.parse_args(argv)
    args.batch = args.batch.resolve(strict=True)
    if args.mode == 'verify':
        report = validate(args.root.resolve(strict=True), args.batch, args.source, args.pilots,
                          args.output.resolve(), args.release)
        print(f"{report['verified']}/{STOP_COUNT} original stops verified")
        return int(not report['passed'])
    cases = {c['seed']: (c, d) for c, d in inventory(args.batch, args.source)}
    if args.mode == 'plan':
        # Start with selection loops, then end-turn, play-card, and event cases.
        loops = (5050339, 5050355, 5050319)
        rank = {'endTurn': 0, 'playCard': 1, 'usePotion': 2}
        remainder = sorted(set(cases)-set(loops), key=lambda s: (rank.get(cases[s][0]['method'], 3), s))
        print(' '.join(map(str, (*loops, *remainder))))
        return 0
    root = args.root.resolve(strict=True)
    require(len(args.seeds) == len(set(args.seeds)) and set(args.seeds) <= cases.keys(), 'Unknown or repeated seed')
    require(root != args.batch and not root.is_relative_to(args.batch), 'Require an independent pilot runtime')
    with lane_lock(root):
        collector = HeadlessCollector(root, str(root/'tools/start-session.sh'),
                                      str(root/'native-bin/battle-engine-worker'), str(root/'value-format12.pth'))
        for seed in args.seeds:
            out = root/'reports'/f'pilot-{seed}.json'
            require(not out.exists(), 'Pilot receipts are immutable')
            try:
                report = pilot(collector, args.batch, *cases[seed], latest=True)
            except (KeyError, ValueError, OSError, RuntimeError, TypeError) as exc:
                report = dict(seed=seed, status='unresolved', error=repr(exc))
            report = save_pilot_receipt(out, seed, report)
            print(seed, report['status'], report.get('error', ''), flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
