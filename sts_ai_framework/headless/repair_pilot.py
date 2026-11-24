"""Replay a signed original episode prefix on an isolated engine, then exercise the stop."""
import argparse
from dataclasses import asdict
import gzip
import json
from pathlib import Path
import uuid

from battle_engine.worker import Worker
from .agent import HeadlessCollector, ROLES, check_decision_progress, check_permits, require, save_json, verify_driver
from .batch import lane_lock
from .collection import EpisodeWriter, validate_episode
from .combat import HeadlessCombatBridge, is_native_combat_selection, verify_combat_effect
from .interaction import IroncladInteractionBridge, verify_effect
from .live_confirmation import SeededMatcher
from .repair_ledger import digest, inventory
from .repair_four import TARGETS, verify_four
from .replay import command, verify_replay


def pilot(collector,batch,case,diag,*, latest=False):
    root=collector.root; seed=case['seed']; run=uuid.uuid4().hex
    episode=Path(case['episode']['path']).resolve(strict=True)
    if not episode.is_relative_to(batch) or digest(episode)!=case['episode']['sha256']:
        raise ValueError('Original episode digest mismatch')
    original=validate_episode(episode,require_complete=False)
    if original['final_chain']!=case['episode']['final_chain']: raise ValueError('Original chain mismatch')
    trace={'case_id':case['case_id'],'seed':seed,'original_sha256':digest(episode),
           'diagnostic_sha256':case['diagnostic_sha256'],'native_sha256':digest(Path(collector.native)),
           'phase':'prefix','prefix_steps':0,'target_steps':[]}
    engine=Worker(collector.launcher);native=Worker(collector.native);writer=None; cur=None
    match=SeededMatcher();combat=HeadlessCombatBridge();menus=IroncladInteractionBridge(characters=ROLES)
    collector.policy.begin_episode();seen={};suffix=[]
    def match_state(archived, current):
        # The old exporter omitted dead slots on the unsupported Forethought
        # screen. Preserve all its other facts, including live powers/intent.
        old=archived.get('interaction',{}).get('selection',{})
        new=current.get('interaction',{}).get('selection',{})
        if latest and 'combat' not in archived and old.get('purpose')=='unknown' and new.get('purpose')=='forethought':
            snapshot=current['combat']['snapshot']
            current=dict(current,monsters=[public for public, native in zip(
                current['monsters'],snapshot['cur_map_node_state']['monster_data'],strict=True)
                if not native['escaped'] and not any(native['creature'][k] for k in
                                                     ('is_dead','is_dying','is_escaping'))])
        match.match(archived,current)
    def search(snapshot, raw):
        cap, timeout=collector.budget_for(raw.get('room'))
        request=dict(snapshot=snapshot,session_id=run,search_profile=collector.search_profile,
                     max_expansions=cap,timeout_ms=timeout,search_policy=collector.combat_search)
        if not collector.tactical_ordering:request['tactical_ordering']=False
        if collector.segment_length!=1:request['segment_length']=collector.segment_length
        if collector.dedup_mode!='default':request['dedup_mode']=collector.dedup_mode
        return native.execute(request,timeout,check_cancel=lambda:check_permits(root)) if timeout==0 else native.execute(request,timeout)
    def step(method=None,args=None,native_cmd=None):
        nonlocal cur
        check_permits(root);raw=cur['state'];seq=cur['sequence'];is_combat=raw['phase']=='PLAYER_TURN' or is_native_combat_selection(raw)
        # The archived prefix can contain valid click/undo loops. Progress is
        # evaluated from the failed boundary when testing the new policy.
        if not latest or trace['phase']=='target':
            check_decision_progress(collector.policy,run,raw,seen)
        if is_combat:
            b=combat.observe(raw,run_id=run,sequence=seq)
            req={'snapshot':b.snapshot}
            if method is not None or native_cmd is not None:
                req.update(operation='predict',command=native_cmd if native_cmd is not None else command(method,args))
                response=native.execute(req,1000)
            else:response=search(b.snapshot,raw)
            plan=combat.prepare(b,require(response)); actual=(plan.native_method,list(plan.native_args))
            if not combat.validate_before(plan,b):raise ValueError('Stale combat plan')
        else:
            if native_cmd is not None: raise ValueError('Original native command lost its combat context')
            b=menus.observe(raw,run_id=run,sequence=seq)
            if method is None: chosen,_=collector.policy.choose(b)
            else:
                row=[a for a in b.actions if (a.native_method,list(a.native_args))==(method,args)]
                if len(row)!=1:raise ValueError('Original menu action is not uniquely legal')
                chosen=row[0]
            plan=menus.prepare(b,chosen.api_action);actual=(chosen.native_method,list(chosen.native_args))
            if not menus.validate_before(plan,b):raise ValueError('Stale menu plan')
        if method is not None and actual!=(method,args):raise ValueError('Original action changed')
        method,args=actual
        request=dict(operation='action',expected_state_token=cur['state_token'],method=method,args=args)
        reply=engine.execute(request,15000)
        if not reply.get('supported'):
            save_json(root/'reports'/f'driver-{run}-{seq}.json',dict(before=cur,request=request,reply=reply))
        next_frame=require(reply)
        verify_driver(cur,next_frame,method,args)
        if is_combat:
            a=combat.observe(next_frame['state'],run_id=run,sequence=next_frame['sequence'])
            fp=native.execute(dict(operation='fingerprint',snapshot=a.snapshot),1000) if a.snapshot else {}
            effect=verify_combat_effect(plan,a,fp)
        else:
            a=menus.observe_outcome(next_frame['state'],run_id=run,sequence=next_frame['sequence'],combat=combat,
                               import_snapshot=lambda s:native.execute(dict(operation='fingerprint',snapshot=s),1000))
            effect=verify_effect(plan,a).status
        if effect!='APPLIED':
            save_json(root/'reports'/f'mismatch-{run}.json',dict(before=cur,after=next_frame,method=method,args=args,effect=effect,
                      prediction=json.loads(plan.prediction_json) if is_combat else None,fingerprint=fp if is_combat else None))
            raise ValueError(f'{seq}: {method} effect {effect}')
        writer.transition(before_sequence=seq,after_sequence=next_frame['sequence'],method=method,args=args,
                          policy={'policy':'original-seed-pilot'},effect=effect)
        writer.observation(next_frame)
        if method=='skipRewardCard':collector.policy._skipped_rewards.add((run,tuple(c.uuid for c in b.state.card_reward_cards)))
        cur=next_frame
        if not latest and seed in TARGETS and trace['phase']=='target':suffix.append((method,args,cur['state']))
        return {'method':method,'args':args,'phase':cur['state']['phase'],'sequence':cur['sequence']}
    try:
        boot=require(engine.execute({'operation':'state'},15000))
        cur=require(engine.execute(dict(operation='new_run',expected_state_token=boot['state_token'],
                                    seed=seed,character=case['role'],ascension=collector.ascension),15000))
        writer=EpisodeWriter(root,role=case['role'],seed=seed,policy=dict(purpose='original-stop-repair',ascension=collector.ascension,
              combat_search_settings=collector.runtime_search,
              model_sha256=collector.model_sha,profile_manifest_sha256=collector.profile_sha,runtime_sha256=collector.runtime_hashes))
        writer.observation(cur)
        with gzip.open(episode,'rt',encoding='utf-8') as source:
            for line in source:
                row=json.loads(line)['record']
                if row['type']=='observation':
                    if row['sequence']!=cur['sequence']:raise ValueError('Original prefix sequence changed')
                    match_state(row['state'],cur['state'])
                elif row['type']=='transition':
                    step(row['native_method'],row['native_args']);trace['prefix_steps']+=1
        if trace['prefix_steps']!=original['transitions'] or cur['sequence']!=diag['before']['sequence']:
            raise ValueError('Original stop boundary not reached')
        match_state(diag['before']['state'],cur['state'])
        target_state=cur['state'];trace['phase']='target'
        pred=(diag.get('prediction') or {}).get('commands') or []
        c=json.loads(pred[0]['command']) if pred else None
        receipt=(diag.get('after') or {}).get('state',{}).get('driver_receipt',{})
        method=diag.get('method',receipt.get('method'));args=diag.get('args',receipt.get('args'))
        trace['target_steps'].append(step(method,args,c))
        for _ in range(32):
            raw=cur['state']
            event=raw.get('interaction',{}).get('event',{})
            if (event.get('class_name'),event.get('phase'))==('SecretPortal','ACCEPT'):
                trace['target_steps'].append(step('chooseDialogOption',[0]))
                continue
            if raw['phase'] not in ('REWARD','CARD_PICK','CARD_SELECT'):break
            trace['target_steps'].append(step())
        else:raise ValueError('Reward or selection did not settle')
        if raw['phase']=='PLAYER_TURN':
            # Demand a usable successor, not merely a different screen.
            frame=combat.observe(raw,run_id=run,sequence=cur['sequence'])
            combat.prepare(frame,require(search(frame.snapshot,raw)))
        else:
            final=menus.observe(raw,run_id=run,sequence=cur['sequence'])
            if not final.actions:
                raise ValueError('No legal successor decision')
            if latest and raw['phase'] in ('GAME_OVER','RUN_COMPLETE'):
                end=next(a for a in final.actions if a.native_method=='LOCAL_ACK_TERMINAL')
                trace['terminal']=asdict(menus.acknowledge_terminal(menus.prepare(final,end.api_action),final))
        if not latest and seed in TARGETS:trace['four_picker']=verify_four(seed,target_state,suffix)
        trace['trajectory']=writer.finish(error='targeted_confirmation_complete')
        trace['replay']=verify_replay(trace['trajectory']['path'],collector.native,require_complete=False)
        trace['status']='original_stop_crossed'
    except Exception as exc:
        trace.update(status='unresolved',error=repr(exc))
        if cur:save_json(root/'reports'/f'last-observation-{run}.json',cur)
        if writer and not writer.closed:trace['trajectory']=writer.finish(error=exc)
    finally:
        engine.close();native.close()
    return trace


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for k in ('root','batch','previous'):p.add_argument('--'+k,required=True,type=Path)
    p.add_argument('--seeds',required=True,nargs='+',type=int);args=p.parse_args()
    root=args.root.resolve(strict=True);batch=args.batch.resolve(strict=True)
    if root==batch or root.is_relative_to(batch):raise ValueError('Require isolated pilot root')
    records={c['seed']:(c,d) for c,d in inventory(batch,args.previous)}
    with lane_lock(root):
        collector=HeadlessCollector(root,str(root/'tools/start-session.sh'),str(root/'native-bin/battle-engine-worker'),str(root/'value-format12.pth'))
        for seed in args.seeds:
            out=root/'reports'/f'pilot-{seed}.json'
            if out.exists():raise ValueError('Pilot receipts must be immutable')
            r=pilot(collector,batch,*records[seed]);save_json(out,r)
            print(seed,r['status'],r.get('error',''),flush=True)


if __name__=='__main__':main()
