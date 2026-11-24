"""Independent original-case gate. A distinct, version-bound proof per diagnostic."""
import argparse
import gzip
import hashlib
import os
import json
from pathlib import Path

from battle_engine.worker import Worker
from .agent import save_json
from .collection import validate_episode
from .live_confirmation import SeededMatcher, streams
from .layout import runtime_files
from .repair_ledger import digest, inventory, recorded
from .repair_four import TARGETS, verify_four
from .replay import command, verify_replay

MIN_TESTS = {"framework": 532, "native": 284}  # previous complete Linux suite, before the four new regressions
PICKERS={'REWARD','CARD_SELECT','CARD_PICK'}


def rows(path):
    with gzip.open(path,'rt',encoding='utf8') as stream:
        for line in stream:
            yield json.loads(line)['record']


def require(ok, reason):
    if not ok: raise ValueError(reason)


def runtime(root):
    return {p.relative_to(root).as_posix():digest(p) for p in runtime_files(root)}


# Earlier exports do not contain the consumed, ordered relic pool. Require an
# independent source-backed award record as well as the archived after-frame.
RELIC_METRIC_SEEDS={5050281,5050320,5050303,5050359}


def needs_live(case,diag):
    return not diag.get('after') or diag['after']['state']['phase'] in PICKERS or case['seed'] in RELIC_METRIC_SEEDS


def release_digest(root):
    """Hash immutable execution inputs, not outputs, archive mounts or Python bytecode."""
    table={}
    for name in ('repo','tools','classes','profile-baseline','native-bin'):
        for parent,dirs,files in os.walk(root/name):
            dirs[:]=[d for d in dirs if d not in ('debug','.git','__pycache__','.pytest_cache')]
            for f in files:
                if f.endswith(('.pyc','.pyo')):continue
                path=Path(parent)/f
                table[path.relative_to(root).as_posix()]=digest(path)
    for f in ('value-format12.pth','profile-baseline-manifest.json'):table[f]=digest(root/f)
    return hashlib.sha256(json.dumps(table,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def target_command(diag,step):
    """Never substitute a search action when the original command was recorded."""
    rec=(diag.get('after') or {}).get('state',{}).get('driver_receipt',{})
    method=diag.get('method',rec.get('method'))
    args=diag.get('args',rec.get('args'))
    if method is not None:
        require((method,args)==(step['native_method'],step['native_args']),'Different original driver action')
    pred=(diag.get('prediction') or {}).get('commands') or []
    if pred:
        expected=json.loads(pred[0]['command'])
        actual=command(step['native_method'],step['native_args'])
        require(all(actual.get(k)==v for k,v in expected.items() if k not in ('display_string','uuid')),
                'Different original native command or target')


class CaseMatcher(SeededMatcher):
    def match(self,old,new):
        super().match(old,new)
        for key in ('act_boss','screen','roomSymbol','observedKeys','gold'):
            if key in old:self.compare(old[key],new[key],key)
        a,b=old['combat_receipt'],new['combat_receipt']
        for key in ('turn','room_class','room_phase','active_monsters','player_dead','battle_over'):
            self.compare(a[key],b[key],'receipt/'+key)
        left,right=a['played_card_uuids'],b['played_card_uuids']
        require(len(left)==len(right),'Played UUID count changed')
        for x,y in zip(left,right):self.compare({'uuid':x},{'uuid':y},'played UUID')
        x=old.get('driver_receipt',{});y=new.get('driver_receipt',{})
        if x.get('card_uuid'):self.compare({'uuid':x['card_uuid']},{'uuid':y['card_uuid']},'driver UUID')


class LatestCaseMatcher(CaseMatcher):
    def match(self,old,new):
        super().match(old,new)
        if old['phase'] != 'EVENT': return
        a=old.get('interaction',{}).get('event')
        if a:
            b=new.get('interaction',{}).get('event') or {}
            self.compare(a['id'],b.get('id'),'event identity')
        for key in ('dialogOptions','dialogOptionEnabled','dialogWaiting'):
            if key in old: self.compare(old[key],new.get(key),key)


def verify_live(case,diag,receipt,original,candidate,identities,*, latest=False, landing_matcher=None):
    result=json.loads(receipt.read_bytes());root=receipt.parent.parent
    require(result.get('status')=='original_stop_crossed' and result['case_id']==case['case_id']
            and result['seed']==case['seed'] and result['diagnostic_sha256']==case['diagnostic_sha256']
            and result['original_sha256']==case['episode']['sha256'], 'Wrong or incomplete pilot receipt')
    f=Path(result['trajectory']['path']).resolve(strict=True)
    require(f.is_relative_to(root/'episodes') and digest(f)==result['trajectory']['sha256'],'Pilot trajectory SHA')
    pilot=iter(rows(f));old=iter(rows(original));first=next(pilot);src=next(old);policy=first['policy']
    require((first['role'],first['seed'],policy['ascension'])==(src['role'],src['seed'],src['policy']['ascension'])==
            (case['role'],case['seed'],10),'Role/seed/A10 binding')
    require(policy['runtime_sha256']==identities and result['native_sha256']==identities['native-bin/battle-engine-worker']
            and policy['model_sha256']==src['policy']['model_sha256']==digest(candidate/'value-format12.pth')
            and policy['profile_manifest_sha256']==src['policy']['profile_manifest_sha256']==digest(candidate/'profile-baseline-manifest.json'),
            'Runtime, checkpoint or profile differs from the frozen candidate')
    matcher=LatestCaseMatcher() if latest else CaseMatcher()
    n=0;last=None
    for o in old:
        if o['type']=='footer':break
        p=next(pilot);require(p['type']==o['type'],'Different prefix structure')
        if o['type']=='observation':
            require(p['sequence']==o['sequence'],'Different prefix sequence');matcher.match(o['state'],p['state']);last=p
        else:
            require((p['native_method'],p['native_args'])==(o['native_method'],o['native_args']),'Different prefix action')
            n+=1
    require(n==result['prefix_steps']==case['episode']['transitions']
            and last['sequence']==diag['before']['sequence'],'Original stop not reached')
    matcher.match(diag['before']['state'],last['state'])
    suffix=0;target_state=last['state'];steps=[];landings=[]
    for p in pilot:
        if p['type']=='transition':
            method,args=p['native_method'],p['native_args']
            if suffix==0:target_command(diag,p)
            require(p['effect']=='APPLIED','Unverified suffix');suffix+=1
        elif p['type']=='observation':
            last=p
            if suffix==1 and diag.get('after'):
                if landing_matcher is not None and landing_matcher(matcher,diag,p['state'],before=target_state):
                    pass  # A narrowly typed late-settlement witness owns this landing.
                elif latest and diag['after']['state']['phase'] != p['state']['phase']:
                    # Only match separately verified upstream drill-through
                    # transitions (e.g. the obsolete Colosseum dialog).
                    from .event_receipts import verify_release2_landing
                    require(verify_release2_landing(matcher,diag,p['state']),'Different recorded action landing')
                else:
                    matcher.match(diag['after']['state'],p['state'])
                if not latest and case['seed'] in RELIC_METRIC_SEEDS:
                    item=p['state']['interaction']['dead_search_receipt']['relic_id']
                    require(item and item==diag['after']['state']['player']['relics'][-1]['id'],'Wrong source-awarded relic')
            if latest:
                state=p['state']
                landings.append({'sequence':p['sequence'],'method':method,'args':args,'phase':state['phase'],
                                 'act':state['act'],'floor':state['floor'],'room':state['room'],
                                 'turn':state['combat_receipt']['turn'],'rng':streams(state)})
            if not latest and case['seed'] in TARGETS:steps.append((method,args,p['state']))
        else:
            require(p['type']=='footer' and p['error']=='targeted_confirmation_complete','Interrupted confirmation')
    require(suffix>0 and suffix==len(result['target_steps']) and last['state']['phase'] not in PICKERS,
            'Unfinished selection/reward continuation')
    four=None if latest else verify_four(case['seed'],target_state,steps)
    if four:require(result.get('four_picker')==four,'Actual four-picker receipt mismatch')
    replay=verify_replay(f,candidate/'native-bin/battle-engine-worker',require_complete=False)
    require(replay['replay_verified'] and replay['transitions']==n+suffix and replay['final_chain']==result['trajectory']['final_chain'],
            'Independent replay failed')
    return {'receipt':str(receipt),'receipt_sha256':digest(receipt),'episode_sha256':digest(f),'original_prefix_steps':n,
            'verified_suffix_steps':suffix,'successor':last['state']['phase'],'independent_replay':True,
            **({'landings':landings,'source_rng_streams_compared':14,'recorded_after':bool(diag.get('after'))} if latest else {}),
            **({'four_picker':four} if four else {})}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for k in ('root','batch','previous','prior26','regressions','frozen','out'):
        parser.add_argument('--'+k,type=Path,required=True)
    parser.add_argument('--pilot',type=Path,action='append',required=True)
    a=parser.parse_args();root=a.root.resolve(strict=True);orig=a.batch.resolve(strict=True)
    out=a.out.resolve();require(root!=orig and not out.is_relative_to(orig) and not out.exists(),'Separate immutable output required')
    files=runtime(root); worker=root/'native-bin/battle-engine-worker'
    freeze=json.loads(a.frozen.read_bytes());require(freeze['release_sha256']==release_digest(root),'Candidate changed since freeze')
    prior=json.loads(a.prior26.read_bytes());old=json.loads(a.previous.read_bytes())
    def signatures(proof):return sorted((c['case_id'],c['seed'],c['diagnostic_sha256'],c['status']) for c in proof['cases'])
    require(prior['rechecked_failed_transitions']==26 and prior['worker_sha256']==digest(worker)
            and signatures(prior)==signatures(old) and prior['original_report_sha256']==digest(orig/'reports/batch160-report.json'),
            'Old 26 not fully revalidated')
    regression=json.loads(a.regressions.read_bytes())
    require(regression['runtime_sha256']==files and set(regression['suites'])=={'framework','native'} and
            all(s['exit_code']==0 and s['ran']>=MIN_TESTS[name] and s['skips']==0 and digest(Path(s['log']))==s['sha256']
                for name,s in regression['suites'].items()),
            'Full regression evidence missing')
    live={}
    for d in a.pilot:
        d=d.resolve(strict=True)
        require(d.parent==root and not any((d/k).exists() for k in ('ALLOW_COLLECTION','ALLOW_RUN_SMOKE')),'Pilot isolation/closure')
        require(runtime(d)==files and release_digest(d)==freeze['release_sha256'],'Pilot executable hash differs from candidate')
        for p in (d/'reports').glob('pilot-*.json'):
            seed=int(p.stem.split('-')[-1]);require(seed not in live,'Ambiguous live proof');live[seed]=p
    w=Worker(worker);results=[]
    try:
        for case,diag in inventory(orig,a.previous):
            row=recorded(case,diag,w)
            need_live=needs_live(case,diag)
            try:
                if row['has_after'] and row['status']!='recorded_step_matches':
                    raise ValueError('Original landing rejected: requires separate reviewed counterexample and corrected landing')
                if need_live:
                    require(case['seed'] in live,'Missing original-seed pilot')
                    old_path=Path(case['episode']['path']).resolve(strict=True)
                    require(old_path.is_relative_to(orig) and digest(old_path)==case['episode']['sha256'],'Broken original trace')
                    checked=validate_episode(old_path,require_complete=False)
                    require(checked['final_chain']==case['episode']['final_chain'],'Original chain mismatch')
                    row['live']=verify_live(case,diag,live[case['seed']],old_path,root,files)
                row['passed']=True
            except (ValueError,OSError,RuntimeError,KeyError,StopIteration) as exc:
                row.update(passed=False,gate_error=repr(exc))
            results.append(row)
    finally:w.close()
    require(len(results)==102 and len({r['seed'] for r in results})==102,'Incomplete ledger')
    count=sum(r['passed'] for r in results)
    report={'schema':'headless-102-signoff-v1','passed':count==102,'verified':count,'previous_26_sha256':digest(a.prior26),
            'regressions_sha256':digest(a.regressions),'original_report_sha256':digest(orig/'reports/batch160-report.json'),
            'release_sha256':freeze['release_sha256'],'runtime_sha256':files,'checkpoint':digest(root/'value-format12.pth'),'profile':digest(root/'profile-baseline-manifest.json'),
            'cases':results,'training':False}
    save_json(out,report);print(json.dumps({'passed':report['passed'],'verified':count}))
    return 0 if report['passed'] else 1


if __name__=='__main__': raise SystemExit(main())
