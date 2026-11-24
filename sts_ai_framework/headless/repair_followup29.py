"""Strict original-prefix verification for the two r4 follow-up reports."""
import argparse
from collections import Counter
import json
from pathlib import Path
import shutil

from .agent import HeadlessCollector, save_json
from .batch160 import lane_lock, retry_schedule
from .collection import validate_episode
from .live_confirmation import streams
from .repair_batch_report import action, diagnostic, read, verified_replay
from .repair_ledger import digest
from .repair_pilot import pilot
from .repair_signoff import release_digest, require, runtime, verify_live
from .release2 import save_pilot_receipt


SOURCES = {
    'recheck44': ('e58217277838fded9f2018c809705658a6517b076f5b4427c8d6a61d32b752f7', 44, 17),
    'fresh40': ('5aa44aaefbec1568f72126263ad1a082b7491d58a713d6ace26efd1df126b0b6', 40, 12),
}
CONTENTS = ('repo','tools','classes','native-bin','profile-baseline','profile-baseline-manifest.json','value-format12.pth')


def inventory(root):
    cases = {}
    for scope,(expected_sha,total,aborted) in SOURCES.items():
        phase=root/scope
        report,sha=read(phase/'reports'/f'{scope}-report.json')
        require(sha==expected_sha and report['schema']=='headless-batch63-followup-report-v1'
                and report['scope']==scope and report['ascension']==10 and report['verification_errors']==0
                and report['attempted']==report['planned']==report['independently_replayed']==total
                and report['preservation']['passed'] is True,'Unrecognized source report: '+scope)
        stops=[c for c in report['cases'] if c['status']=='aborted']
        require(len(stops)==aborted,'Wrong stop count: '+scope)
        for c in stops:
            seed=c['seed']
            require(seed not in cases and verified_replay(c),'Duplicate or unverified original prefix')
            d=diagnostic(phase,c)
            require(d['episode_id']==c['episode']['episode_id'] and d['before']['state']['seed']==seed,
                    'Original stop identity changed')
            streams(d['before']['state']);action(d)
            cases[seed]=(phase,c,d)
    schedule=retry_schedule(cases.keys())
    for row in schedule:
        phase,c,_=cases[row['seed']]
        require(row['source_scope']==phase.name and row['lane']==c['lane'] and row['role']==c['role']
                and row['case_id']==phase.name+'-'+c['case_id'],'Original case coordinates changed')
    return cases,schedule


def old_episode(phase, case):
    path=Path(case['episode']['path']).resolve(strict=True)
    require(path.parent==phase/f"lane{case['lane']}"/'episodes' and digest(path)==case['episode']['sha256'],
            'Original prefix escaped its lane or changed')
    result=validate_episode(path,require_complete=False)
    require(result['final_chain']==case['episode']['final_chain'] and result['transitions']==case['episode']['transitions'],
            'Original prefix chain/count changed')
    return path


def verified_reports(candidate, source, expected_release):
    from .event_receipts import verify_followup29_landing
    require(release_digest(candidate)==expected_release,'Candidate changed during verification')
    cases,order=inventory(source); files=runtime(candidate); results=[]
    for i in (0,1):
        lane=candidate/f'pilot{i}'
        require(lane.resolve(strict=True)==lane and release_digest(lane)==expected_release and all(
                not (lane/k).exists() and not (lane/k).is_symlink() for k in ('ALLOW_COLLECTION','ALLOW_RUN_SMOKE')),
                'Pilot build or permit closure differs')
    for row in order:
        seed=row['seed'];phase,c,d=cases[seed]
        receipt=candidate/f"pilot{row['lane']}"/'reports'/f'pilot-{seed}.json'
        entry={**row,'diagnostic_sha256':c['diagnostic_sha256'],'original_source_sha256':SOURCES[phase.name][0]}
        try:
            old=old_episode(phase,c)
            entry['live']=verify_live(c,d,receipt,old,candidate,files,latest=True,landing_matcher=verify_followup29_landing)
            entry['passed']=True
        except (OSError,ValueError,RuntimeError,KeyError,IndexError,TypeError,StopIteration) as e:
            entry.update(passed=False,error=repr(e),receipt=str(receipt))
            try:entry['pilot_error']=read(receipt)[0].get('error')
            except (OSError,ValueError,TypeError,AttributeError):pass
        results.append(entry)
    count=sum(r['passed'] for r in results)
    return {'schema':'headless-29-signoff-v1','ascension':10,'training':False,'verified':count,
            'passed':count==len(order)==29,'release_sha256':expected_release,'runtime_sha256':files,'cases':results}


def run_pilot(root, source, lane):
    cases,order=inventory(source)
    require(not root.is_relative_to(source) and root.name==f'pilot{lane}','Unexpected pilot root')
    with lane_lock(root):
        collector=HeadlessCollector(root,str(root/'tools/start-session.sh'),str(root/'native-bin/battle-engine-worker'),
                                    str(root/'value-format12.pth'))
        for row in order:
            if row['lane']!=lane:continue
            seed=row['seed'];phase,c,d=cases[seed]
            out=root/'reports'/f'pilot-{seed}.json'
            require(not out.exists(),'Pilot receipts are immutable')
            try:
                old_episode(phase,c)
                result=pilot(collector,phase,c,d,latest=True)
            except (OSError,ValueError,KeyError,RuntimeError,TypeError,StopIteration) as e:
                result=dict(seed=seed,status='unresolved',error=repr(e))
            result=save_pilot_receipt(out,seed,result)
            print(seed,result['status'],result.get('error',''),flush=True)


def stage(candidate, source, target):
    cases,order=inventory(source);path=candidate/'reports/signoff29.json'; r=read(path)[0]
    expected=release_digest(candidate)
    require(r['schema']=='headless-29-signoff-v1' and r['passed'] is True and r['verified']==29
            and r['ascension']==10 and r['training'] is False and r['release_sha256']==expected
            and r['runtime_sha256']==runtime(candidate) and len(r['cases'])==29,'No matching complete signoff')
    require([(item['seed'],item['diagnostic_sha256']) for item in r['cases'] if item['passed'] is True
             and item['live']['independent_replay'] is True]==[(row['seed'],cases[row['seed']][1]['diagnostic_sha256']) for row in order],
            'Signoff must cover exactly the original 29 stops')
    require(not target.is_relative_to(source) and not target.is_relative_to(candidate),
            'Require an independent rerun root')
    target.mkdir();(target/'reports').mkdir()
    schedule={'schema':'headless-batch63-followup-plan-v1','scope':'retry29','ascension':10,'max_actions':3000,
              'cases':order,'source_report_sha256':{k:v[0] for k,v in SOURCES.items()},'signoff_sha256':digest(path)}
    save_json(target/'schedule.json',schedule)
    shutil.copy2(path,target/'reports/signoff29.json')
    for i in range(2):
        lane=target/f'lane{i}';lane.mkdir()
        for name in CONTENTS:
            src=candidate/name
            if src.is_dir():shutil.copytree(src,lane/name,ignore=shutil.ignore_patterns('__pycache__','*.pyc','.git','debug'))
            else:shutil.copy2(src,lane/name)
        (lane/'reports').mkdir();(lane/'saves').mkdir()
        require(release_digest(lane)==expected,'Staged runtime differs from signoff')
    save_json(target/'freeze.json',{'release_sha256':expected,'schedule_sha256':digest(target/'schedule.json'),
              'signoff_sha256':digest(target/'reports/signoff29.json')})


def verify_stage(target):
    f=read(target/'freeze.json')[0]
    require(digest(target/'schedule.json')==f['schedule_sha256']
            and digest(target/'reports/signoff29.json')==f['signoff_sha256'],'Staged evidence changed')
    for i in (0,1):require(release_digest(target/f'lane{i}')==f['release_sha256'],'Staged lane differs from signoff')


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode',choices=('plan','pilot','verify','stage','verify-stage'))
    p.add_argument('--source',type=Path);p.add_argument('--root',type=Path)
    p.add_argument('--target',type=Path);p.add_argument('--release')
    p.add_argument('--lane',type=int,choices=(0,1))
    a=p.parse_args(argv)
    if a.mode=='verify-stage':
        verify_stage(a.target.resolve(strict=True));return 0
    source=a.source.resolve(strict=True)
    if a.mode=='plan':
        _,rows=inventory(source)
        print(json.dumps(dict(attempts=len(rows),lanes=Counter(r['lane'] for r in rows),seeds=[r['seed'] for r in rows])))
        return 0
    root=a.root.resolve(strict=True)
    if a.mode=='pilot':
        require(a.lane is not None,'Require an explicit pilot lane')
        run_pilot(root,source,a.lane)
    elif a.mode=='verify':
        path=root/'reports/signoff29.json'
        require(not path.exists() and a.release,'Require a fresh release signoff')
        result=verified_reports(root,source,a.release)
        save_json(path,result)
        print(f"{result['verified']}/29 original stops verified")
        return int(not result['passed'])
    elif a.mode=='stage':
        stage(root,source,a.target.absolute())
    return 0


if __name__=='__main__':raise SystemExit(main())
