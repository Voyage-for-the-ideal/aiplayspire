"""One-shot original-trigger signoff vs full-run outcomes; reads finished artifacts, never plays a game."""
import argparse
import hashlib
import json
from pathlib import Path

from .batch160 import full_schedule
from .live_confirmation import SeededMatcher, streams
from .replay import command


def check(ok, message):
    if not ok: raise ValueError(message)


def read(path):
    data=path.read_bytes()
    return json.loads(data), hashlib.sha256(data).hexdigest()


def indexed(report):
    schedule=full_schedule()
    check(report.get('schema')=='headless-batch160-report-v1','Wrong batch report schema')
    cases={c['case_id']:c for c in report['cases']}
    check(len(cases)==len(report['cases'])==len(schedule)==160,'Incomplete or duplicate cases')
    for expected in schedule:
        row=cases[expected['case_id']]
        check(all(row.get(k)==expected[k] for k in ('lane','role','seed','round')),'Incorrect case identity')
    return cases


def verified_replay(row):
    e=row.get('episode',{});r=row.get('independent_replay',{})
    return (not row.get('verification_error') and r.get('valid') is True and r.get('replay_verified') is True
            and r.get('status')==row['status'] and r.get('role')==row['role'] and e.get('role')==row['role']
            and e.get('seed')==row['seed'] and e.get('attempt_id')==row.get('attempt_id')
            and isinstance(r.get('transitions'),int) and r['transitions']==e.get('transitions')
            and bool(r.get('episode_sha256')) and r['episode_sha256']==e.get('sha256')
            and bool(r.get('final_chain')) and r['final_chain']==e.get('final_chain'))


def diagnostic(root,case):
    # Reconstruct the lane-local filename; absolute paths in archived reports can point to the old host.
    aid=case['attempt_id']
    check(isinstance(aid,str) and len(aid)==32 and all(c in '0123456789abcdef' for c in aid),'Invalid attempt ID')
    directory=(root/f"lane{case['lane']}"/'reports').resolve()
    path=(directory/f'failure-{aid}.json').resolve()
    check(path.parent==directory and directory.is_relative_to(root),'Diagnostic escaped its lane')
    d,h=read(path)
    check(h==case.get('diagnostic_sha256') and d.get('attempt_id')==aid and d.get('seed')==case['seed']
          and d.get('role')==case['role'],'Missing or mismatched original diagnostic identity')
    return d


def action(d):
    receipt=(d.get('after') or {}).get('state',{}).get('driver_receipt',{})
    method=d.get('method',receipt.get('method'));args=d.get('args',receipt.get('args'))
    cmd=None
    if method and isinstance(args,list):
        try: cmd=command(method,args)
        except ValueError: cmd={'menu_method':method,'args':args}
    rows=(d.get('prediction') or {}).get('commands') or []
    planned=json.loads(rows[0]['command']) if rows else None
    if planned and 'uuid' in planned:
        check(planned.get('type')=='CARD','Unbound command UUID for this action family')
        index=planned.get('card_index');hand=d['before']['state']['combat']['snapshot']['player_state']['hand']
        check(type(index) is int and 0<=index<len(hand) and hand[index]['uuid']==planned['uuid'],'Predicted card UUID contradicts source hand')
    if planned: planned={k:v for k,v in planned.items() if k not in ('display_string','uuid')}
    if planned and cmd:check(cmd==planned,'Diagnostic command contradicts receipt')
    return cmd or planned


def freeze_audit(gate,gatehash,root):
    """The supervisor's final preservation audit authenticates the lane manifest."""
    try:
        manifest,h=read(root/'frozen-runtime-manifest.json');files=manifest['files']
        check(files.get('reports/repair-signoff.json')==gatehash,'Frozen signoff hash differs')
        expected=dict(gate['runtime_sha256'])
        check(bool(expected.get('native-bin/battle-engine-worker')),'Missing signed native identity')
        expected.update({'profile-baseline-manifest.json':gate['profile'],'value-format12.pth':gate['checkpoint']})
        for lane in range(2):
            check(all(files.get(f'lane{lane}/'+k)==v for k,v in expected.items()),f'Lane {lane} files differ from signoff')
        return {'status':'manifest_matches','manifest_sha256':h,'signed_lane_files':len(expected)}
    except (KeyError,OSError,TypeError,ValueError) as e:
        return {'status':'insufficient_or_mismatched_freeze','reason':str(e)}


def diff_path(a,b,path):
    if type(a)!=type(b):return path
    if isinstance(a,list):
        if len(a)!=len(b):return path+'/length'
        for i,(x,y) in enumerate(zip(a,b)):
            at=diff_path(x,y,f'{path}/{i}')
            if at:return at
    elif isinstance(a,dict):
        if set(a)!=set(b):return path+'/keys'
        for k in sorted(a):
            at=diff_path(a[k],b[k],path+'/'+k)
            if at:return at
    elif a!=b:return path
    return None


def difference_fields(d):
    pred=d.get('prediction') or {};actual=d.get('fingerprint') or {}
    return [p for k,j in (('expected_after','projection'),('expected_exact_rng','exact_rng'),
                           ('expected_native_key','native_key'),('expected_selection','selection'),
                           ('expected_selection_native_key','selection_native_key')) if k in pred and j in actual
            if (p:=diff_path(pred[k],actual[j],k))]


def coordinates(raw):
    check(all(k in raw for k in ('seed','phase','act','floor','room','player')),'Missing source boundary coordinates')
    return [raw[k] for k in ('seed','phase','act','floor','room')]+[(raw.get('combat') or {}).get('snapshot',{}).get('turn')]


def compare_stop(old,new):
    """'New' means a different stop in the original-seed pair, not a new underlying bug."""
    a,b=old['before']['state'],new['before']['state']
    ca,cb=coordinates(a),coordinates(b);streams(a);streams(b)
    location={'old_before':ca,'new_before':cb,'new_action':action(new),'old_action':action(old)}
    if ca!=cb:return 'new_difference','different_before_location',location
    matcher=SeededMatcher()
    try:
        matcher.match(a,b)
        if ca[-1] is not None:matcher.compare(a['combat']['snapshot'],b['combat']['snapshot'],'combat_snapshot')
        else:
            for k in ('dialogOptions','dialogOptionEnabled','dialogWaiting','act_boss','screen'):
                if k in a:matcher.compare(a[k],b.get(k),k)
    except ValueError as e:
        # A changed exporter can alter prior fields at the very same location.
        # Distinguish actual RNG/route divergence from a schema/presentation change.
        if streams(a)!=streams(b):return 'new_difference','different_source_rng',location
        return 'insufficient_evidence','source_state_not_equivalent: '+str(e),location
    if location['old_action'] is None or location['new_action'] is None:
        return 'insufficient_evidence','missing_original_or_current_action',location
    if location['old_action']!=location['new_action']:return 'new_difference','different_action_at_same_state',location
    if not old.get('error') or not new.get('error'):return 'insufficient_evidence','missing_failure_evidence',location
    if old['error']!=new['error']:return 'insufficient_evidence','same_trigger_but_error_changed',location
    od,nd=difference_fields(old),difference_fields(new);location.update(old_difference_fields=od,new_difference_fields=nd)
    if od!=nd:return 'insufficient_evidence','same_trigger_but_mismatch_fields_changed',location
    ao,an=old.get('after'),new.get('after')
    if bool(ao)!=bool(an):return 'insufficient_evidence','different_after_evidence_availability',location
    if ao:
        try:
            matcher.match(ao['state'],an['state'])
            osa=(ao['state'].get('combat') or {}).get('snapshot');nsa=(an['state'].get('combat') or {}).get('snapshot')
            if osa is not None:matcher.compare(osa,nsa,'after/combat_snapshot')
        except ValueError as e:return 'insufficient_evidence','different_actual_landing: '+str(e),location
    return 'repeated_old_difference','matching_state_rng_action_failure_and_available_landing',location


def summarize(original, signoff, rerun):
    original=Path(original).resolve();rerun=Path(rerun).resolve()
    check(original!=rerun,'Rerun and original must be separate directories')
    old,oldhash=read(original/'reports/batch160-report.json');new,newhash=read(rerun/'reports/batch160-report.json')
    gate,gatehash=read(Path(signoff));baseline=indexed(old);current=indexed(new)
    check(gate['schema']=='headless-102-signoff-v1' and gate.get('passed') is True and gate.get('verified')==102
          and gate.get('original_report_sha256')==oldhash,'Signoff did not pass against this original report')
    signed={r['case_id']:r for r in gate['cases']}
    check(len(signed)==len(gate['cases'])==102,'Incomplete or duplicate signed cases')
    for k,r in signed.items():
        c=baseline[k]
        check(r['passed'] is True and c['status']=='aborted' and r.get('seed')==c['seed'] and bool(r.get('diagnostic_sha256'))
              and r['diagnostic_sha256']==c.get('diagnostic_sha256'),'Signed trigger is not bound to the original case')
    freeze=freeze_audit(gate,gatehash,rerun)
    groups={k:[] for k in ('repeated_old_difference','new_difference','insufficient_evidence')}
    roles={r['role']:{'planned':40,'attempted':0,'terminal':0,'victory':0,'defeat':0} for r in current.values()}
    for k,c in current.items():
        base=baseline[k];role=roles[c['role']];role['attempted']+=int(c['status'] in ('aborted','complete'))
        row={w:c[w] for w in ('case_id','seed','lane','role','status')};row['original_signed_102']=k in signed
        try:
            check(verified_replay(c),'Missing independent replay of the complete trajectory or valid prefix')
            verify_native=(gate.get('runtime_sha256') or {}).get('native-bin/battle-engine-worker')
            if verify_native:check(c['independent_replay'].get('native_sha256')==verify_native,'Independent replay used a different worker from signoff')
            if c['status']=='complete':
                t=c['episode']['terminal']
                check(t['outcome'] in ('victory','defeat') and t['character']==c['role'] and t['ascension']==10 and t['run_id']==c['episode']['episode_id']
                      and c['episode'].get('censored') is False and bool(t['receipt_id']),'Missing terminal receipt')
                role['terminal']+=1;role[t['outcome']]+=1;continue
            check(c['status']=='aborted','Missing completed attempt')
            d=diagnostic(rerun,c);row.update(new_diagnostic_sha256=c['diagnostic_sha256'],error=d['error'])
            check(d['before']['state']['seed']==c['seed'],'Reported source seed mismatch')
            coordinates(d['before']['state']);streams(d['before']['state'])
            if base['status']=='complete':
                check(verified_replay(base),'Unverified original completion')
                bucket,reason,detail='new_difference','original_case_reached_terminal',{}
            else:
                oldd=diagnostic(original,base);row['original_diagnostic_sha256']=base['diagnostic_sha256']
                bucket,reason,detail=compare_stop(oldd,d)
        except (KeyError,ValueError,TypeError,OSError,IndexError,RuntimeError) as exc:
            bucket,reason,detail='insufficient_evidence',str(exc),{}
        groups[bucket].append(dict(row,reason=reason,**detail))
    lanes=[]
    check(sorted(row['lane'] for row in new['lanes'])==[0,1],'Missing lane resource reports')
    for lane in new['lanes']:
        n=lane['lane'];line={k:lane.get(k) for k in ('lane','attempted','completed','aborted','started_unix','ended_unix','cgroup_cpu_seconds',
                'peak_sampled_rss_bytes','peak_sampled_pss_bytes','cgroup_memory_peak_bytes','cgroup_swap_peak_bytes','oom_kill')}
        start,end=line['started_unix'],line['ended_unix']
        line['elapsed_seconds']=end-start if isinstance(start,(int,float)) and isinstance(end,(int,float)) and end>=start else None
        oom=[r.get('collector_after_close',{}).get('cgroup_memory_events',{}).get('oom') for r in current.values() if r['lane']==n]
        line['oom_events']=max((v for v in oom if type(v) is int),default=None);lanes.append(line)
    return {'schema':'headless-repair-batch-comparison-v1','original_report_sha256':oldhash,'rerun_report_sha256':newhash,
            'signoff_sha256':gatehash,'signed_original_triggers':gate['cases'],'original_triggers_passed':len(signed),
            'outcomes_by_role':roles,'lane_resources':lanes,'differences':groups,'counts':{k:len(v) for k,v in groups.items()},
            'batch_status':new.get('status'),'batch_errors':new.get('errors'),'freeze':freeze,'preservation':new.get('preservation'),
            'supervisor':new.get('supervisor'),'training':False}


def markdown(report):
    lines=['# 160 局复验','',f"原中止点签收：{report['original_triggers_passed']}/102。此数不是整局通过数。",
           f"批报告状态：{report['batch_status']}；两路冻结清单：{report['freeze']['status']}。"
           +" 保留服务审计："+('通过' if (report.get('preservation') or {}).get('passed') is True else '未确认')+"。",
           '','| 角色 | 尝试 | 经回放到终局 | 胜 | 负 |','|---|---:|---:|---:|---:|']
    for role,r in report['outcomes_by_role'].items():lines.append(f"| {role} | {r['attempted']} | {r['terminal']} | {r['victory']} | {r['defeat']} |")
    lines+=['','| 进程 | 耗时 s | CPU s | RSS B | PSS B | cgroup 内存峰 B | swap 峰 B | OOM / kill |','|---|---:|---:|---:|---:|---:|---:|---|']
    for r in report['lane_resources']:
        lines.append('| '+' | '.join(str(r[k]) if r[k] is not None else '缺证' for k in ('lane','elapsed_seconds','cgroup_cpu_seconds','peak_sampled_rss_bytes',
                     'peak_sampled_pss_bytes','cgroup_memory_peak_bytes','cgroup_swap_peak_bytes'))+f" | {r['oom_events']} / {r['oom_kill']} |")
    lines+=['','“新差异”指相对原种子的新中止点，不推断其底层根因一定是新问题。']
    for k,label in [('repeated_old_difference','重复旧差异'),('new_difference','新差异'),('insufficient_evidence','证据不足')]:
        rows=report['differences'][k];lines+=['',f'## {label}（{len(rows)}）']
        lines.extend(f"- {r['case_id']} / {r['seed']}：{r['reason']}" for r in rows)
    lines+=['','## 逐案签收（原触发点）']
    lines.extend(f"- {r['case_id']} / {r['seed']}：通过；原诊断 SHA-256 `{r['diagnostic_sha256']}`" for r in report['signed_original_triggers'])
    return '\n'.join(lines)+'\n'


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('original','signoff','rerun'):p.add_argument('--'+name,type=Path,required=True)
    args=p.parse_args(argv);r=summarize(args.original,args.signoff,args.rerun);out=args.rerun.resolve()/'reports'
    for name,data in [('repair160-comparison.json',json.dumps(r,ensure_ascii=False,indent=2)+'\n'),('repair160-comparison.md',markdown(r))]:
        with (out/name).open('x',encoding='utf8') as f:f.write(data)
    print(json.dumps({'original_passed':r['original_triggers_passed'],'outcomes':r['outcomes_by_role'],'counts':r['counts']}));return 0


if __name__=='__main__':raise SystemExit(main())
