"""One-shot, read-only replay and resource report after both 80-case lanes exit."""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import time

from .batch160 import make_plan, read_followup_schedule, selected_seeds
from .replay import verify_replay


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def summarize(root, *, replay=verify_replay, seeds=None, schedule=None):
    root = Path(root).resolve()
    records, lanes, errors = [], [], []
    seen_seeds = set()
    for lane in (0, 1):
        directory = root / f'lane{lane}'
        plan = make_plan(directory, lane, seeds=seeds, schedule=schedule)
        expected = plan['cases']
        status_path = directory / 'reports/batch-status.json'
        try:
            state = json.loads(status_path.read_text(encoding='utf-8'))
            if (state['plan']['cases'] != expected or state['plan']['global_cases'] != plan['global_cases']
                    or state['plan']['ascension'] != 10 or state['plan']['max_actions'] != 3000):
                raise ValueError('Lane plan differs from frozen 160-case schedule')
            attempts = state['attempts']
            ids = [a['case_id'] for a in attempts]
            if len(set(ids)) != len(ids) or not set(ids) <= {c['case_id'] for c in expected}:
                raise ValueError('Duplicate or unexpected case reservation')
        except Exception as error:
            errors.append({'lane': lane, 'error': repr(error)})
            state, attempts = {}, []
        lane_rows = []
        for case in expected:
            assert case['seed'] not in seen_seeds
            seen_seeds.add(case['seed'])
            attempt = next((a for a in attempts if a['case_id'] == case['case_id']), None)
            row = {'lane': lane, **case, 'status': 'not_attempted'}
            if attempt is not None:
                result = attempt.get('result', {})
                row.update(status=result.get('status', 'interrupted'), attempt_id=attempt.get('attempt_id'),
                           episode=result, collector_after_close=attempt.get('collector_after_close', {}))
                if row['status'] == 'running':
                    row['status'] = 'interrupted'
                if (result.get('role'), result.get('seed')) != (case['role'], case['seed']):
                    row['verification_error'] = 'Result role/seed mismatch'
                elif result.get('path'):
                    try:
                        path = Path(result['path']).resolve(strict=True)
                        if not path.is_relative_to(directory / 'episodes'):
                            raise ValueError('Episode path outside its lane')
                        if sha(path) != result.get('sha256'):
                            raise ValueError('Episode SHA mismatch')
                        with gzip.open(path, 'rt', encoding='utf-8') as source:
                            header = json.loads(next(source))['record']
                        policy = header['policy']
                        if (header['role'], header['seed'], policy['ascension']) != (case['role'], case['seed'], 10):
                            raise ValueError('Episode header differs from case')
                        if policy['model_sha256'] != sha(directory/'value-format12.pth'):
                            raise ValueError('Model identity mismatch')
                        if policy['profile_manifest_sha256'] != sha(directory/'profile-baseline-manifest.json'):
                            raise ValueError('Profile identity mismatch')
                        for name, digest in policy['runtime_sha256'].items():
                            runtime_path = (directory/name).resolve(strict=True)
                            if not runtime_path.is_relative_to(directory) or sha(runtime_path) != digest:
                                raise ValueError('Frozen runtime identity mismatch: '+name)
                        proof = replay(path, directory / 'native-bin/battle-engine-worker',
                                       require_complete=row['status'] == 'complete')
                        if (proof['final_chain'], proof['transitions']) != (result['final_chain'], result['transitions']):
                            raise ValueError('Episode chain/count mismatch')
                        if proof['status'] != row['status'] or proof['role'] != case['role']:
                            raise ValueError('Episode footer differs from attempt result')
                        row['independent_replay'] = proof
                    except Exception as error:
                        row['verification_error'] = repr(error)
                elif row['status'] == 'complete':
                    row['verification_error'] = 'Completed case missing episode'
                diagnostic = result.get('diagnostic')
                if diagnostic:
                    try:
                        path = Path(diagnostic).resolve(strict=True)
                        if not path.is_relative_to(directory / 'reports'):
                            raise ValueError('Diagnostic outside its lane')
                        row['diagnostic_sha256'] = sha(path)
                    except Exception as error:
                        row['verification_error'] = repr(error)
            records.append(row); lane_rows.append(row)
        completed = sum(r['status'] == 'complete' for r in lane_rows)
        attempted = sum(r['status'] in ('complete', 'aborted') for r in lane_rows)
        idle = [r.get('collector_after_close', {}) for r in lane_rows]
        resources = [r.get('episode', {}).get('resources', {}) for r in lane_rows]
        def maximum(items, key): return max((x.get(key, 0) for x in items), default=0)
        lanes.append({'lane': lane, 'runner_status': state.get('status', 'missing'),
                      'attempted': attempted, 'completed': completed, 'aborted': attempted-completed,
                      'peak_sampled_rss_bytes': maximum(resources, 'peak_rss_bytes'),
                      'peak_sampled_pss_bytes': maximum(resources, 'peak_pss_bytes'),
                      'cgroup_memory_peak_bytes': maximum(idle, 'cgroup_memory_peak_bytes'),
                      'cgroup_swap_peak_bytes': maximum(idle, 'cgroup_swap_peak_bytes'),
                      'cgroup_cpu_seconds': maximum(idle, 'cgroup_cpu_seconds'),
                      'oom_kill': max((x.get('cgroup_memory_events', {}).get('oom_kill', 0) for x in idle), default=0),
                      'idle_rss_bytes': [x.get('rss_bytes') for x in idle if 'rss_bytes' in x],
                      'idle_fd_values': sorted({x['open_fds'] for x in idle if isinstance(x.get('open_fds'), int)}),
                      'idle_os_thread_values': sorted({x['os_threads'] for x in idle if 'os_threads' in x}),
                      'started_unix': state.get('started_unix'), 'ended_unix': state.get('ended_unix')})
    attempts = sum(lane['attempted'] for lane in lanes)
    complete = sum(lane['completed'] for lane in lanes)
    verified = sum('independent_replay' in row for row in records)
    verification_errors = sum('verification_error' in row for row in records)
    preservation = {}
    supervisor = {}
    for name, destination in (('preservation-report.json', preservation), ('supervisor-result.json', supervisor)):
        try:
            destination.update(json.loads((root/'reports'/name).read_text(encoding='utf-8')))
        except Exception as error:
            errors.append({'report': name, 'error': repr(error)})
    if preservation.get('passed') is not True:
        errors.append({'report': 'preservation', 'error': 'Preservation audit did not pass'})
    if supervisor.get('lane_exit_codes') != [0, 0] or supervisor.get('scoped_permits_closed') is not True:
        errors.append({'report': 'supervisor', 'error': 'Lane interrupted or permits closure unconfirmed'})
    if any(lane['oom_kill'] or lane['cgroup_swap_peak_bytes'] for lane in lanes):
        errors.append({'report': 'resources', 'error': 'OOM kill or lane swap use recorded'})
    planned = len(schedule['cases']) if schedule is not None else 160 if seeds is None else len(seeds)
    finished = attempts == planned and not errors and not verification_errors
    report = {'schema': 'headless-batch160-report-v1' if seeds is None else 'headless-unfinished-63-report-v1',
              'generated_unix': time.time(),
              'status': ('passed' if complete == planned else 'finished_with_differences') if finished else 'incomplete_or_report_error',
              'planned': planned, 'attempted': attempts, 'completed': complete, 'aborted': attempts-complete,
              'independently_replayed': verified, 'verification_errors': verification_errors,
              'legacy_training_admission': False, 'lane_count': 2,
              'completed_by_role': dict(Counter(r['role'] for r in records if r['status'] == 'complete')),
              'attempted_by_role': dict(Counter(r['role'] for r in records if r['status'] in ('complete', 'aborted'))),
              'lanes': lanes, 'cases': records, 'errors': errors,
              'preservation': preservation, 'supervisor': supervisor}
    if schedule is not None:
        report.update(schema='headless-batch63-followup-report-v1',scope=schedule['scope'],ascension=10)
    return report


def markdown(report):
    lines = [f"# Headless {report['planned']} 局双进程报告", '',
             f"状态：{report['status']}；已尝试 {report['attempted']}/{report['planned']}，真实终局 {report['completed']}，差异中止 {report['aborted']}。",
             f"独立回放 {report['independently_replayed']} 条完整轨迹或有效前缀；报告校验错误 {report['verification_errors']}。",
             '差异中止按删失保留，不记作游戏失败；数据不接入训练。', '',
             '| 进程 | 已尝试 | 终局 | 差异中止 | cgroup峰值MiB | CPU秒 | swap峰值 |',
             '|---|---:|---:|---:|---:|---:|---:|']
    for lane in report['lanes']:
        lines.append(f"| {lane['lane']} | {lane['attempted']} | {lane['completed']} | {lane['aborted']} | "
                     f"{lane['cgroup_memory_peak_bytes']/1048576:.1f} | {lane['cgroup_cpu_seconds']:.1f} | {lane['cgroup_swap_peak_bytes']} |")
    lines += ['', 'RSS/PSS 为进程采样，cgroup 峰值为整条进程线的内核口径，二者分别保留。', '',
              '| Case | 种子 | 状态 | 楼层 | 已验证动作 | 诊断 |', '|---|---:|---|---:|---:|---|']
    for row in report['cases']:
        result = row.get('episode', {})
        lines.append(f"| {row['case_id']} | {row['seed']} | {row['status']} | {result.get('last_floor', '')} | "
                     f"{result.get('transitions', '')} | {result.get('diagnostic', '')} |")
    if report['errors']:
        lines += ['', '报告错误：', '```json', json.dumps(report['errors'], ensure_ascii=False, indent=2), '```']
    return '\n'.join(lines)+'\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    cohort=parser.add_mutually_exclusive_group()
    cohort.add_argument('--selection', type=Path)
    cohort.add_argument('--schedule', type=Path)
    args = parser.parse_args(argv)
    schedule=read_followup_schedule(args.schedule) if args.schedule else None
    report = summarize(args.root, seeds=selected_seeds(args.selection) if args.selection else None, schedule=schedule)
    directory = args.root / 'reports'; directory.mkdir(exist_ok=True)
    name = schedule['scope']+'-report' if schedule is not None else 'unfinished63-report' if args.selection else 'batch160-report'
    (directory / f'{name}.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    (directory / f'{name}.md').write_text(markdown(report), encoding='utf-8')
    print(json.dumps({k: report[k] for k in ('status', 'attempted', 'completed', 'aborted', 'verification_errors')}), flush=True)
    return 0 if report['status'] in ('passed', 'finished_with_differences') else 1


if __name__ == '__main__': raise SystemExit(main())
