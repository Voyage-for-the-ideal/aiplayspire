"""Five original-seed live confirmations, followed by an independently checked gate."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import signal
import time
import uuid

from battle_engine.worker import Worker
from . import agent as agent
from .agent import HeadlessCollector, ROLES, check_permits, require, save_json, resource_sample, verify_driver
from .batch import lane_lock, idle_resources
from .collection import EpisodeWriter, validate_episode
from .combat import (HeadlessCombatBridge, is_native_combat_selection, is_headbutt_selection,
                              validate_rng, verify_combat_effect)
from .interaction import IroncladInteractionBridge, verify_effect
from .replay import command, verify_replay

TARGETS = (('IRONCLAD-03','IRONCLAD',5050109,'headbutt'),
           ('IRONCLAD-06','IRONCLAD',5050121,'headbutt'),
           ('DEFECT-03','DEFECT',5050111,'rip_and_tear'),
           ('THE_SILENT-04','THE_SILENT',5050114,'mirror'),
           ('IRONCLAD-05','IRONCLAD',5050117,'orrery'))


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def rows(path):
    with gzip.open(path,'rt',encoding='utf-8') as stream:
        return [json.loads(line)['record'] for line in stream]


def streams(raw):
    receipt=raw['combat_receipt']['rng']
    aliases={name+'_rng_counter':r['counter'] for name,r in receipt['streams'].items()}
    return validate_rng({'_engine':'headless','_headless_rng':receipt,'rng_state':aliases})['streams']


class SeededMatcher:
    """Random UUIDs get a persistent bijection; gameplay facts and all 14 streams match."""
    def __init__(self): self.forward={};self.reverse={}
    def compare(self, old, new, path=''):
        if type(old)!=type(new):raise ValueError('Seeded state type differs: '+path)
        if isinstance(old,dict):
            for key,value in old.items():
                if key=='name':continue  # Localized presentation is not an identity.
                if key not in new:raise ValueError('Seeded state field missing: '+path+'/'+key)
                if key=='uuid':
                    other=new[key]
                    if self.forward.get(value,other)!=other or self.reverse.get(other,value)!=value:
                        raise ValueError('Seeded card identity changed: '+path)
                    self.forward[value]=other;self.reverse[other]=value
                else:self.compare(value,new[key],path+'/'+key)
        elif isinstance(old,list):
            if len(old)!=len(new):raise ValueError('Seeded state list length differs: '+path)
            for i,(a,b) in enumerate(zip(old,new)):self.compare(a,b,path+'/'+str(i))
        elif old!=new:raise ValueError('Seeded state differs: '+path)
    def match(self,old,new):
        for key in ('seed','floor','act','ascension','phase','room','roomPhase','firstRoomChosen','player',
                    'map','history','rewards','cardSelection','cardRewardOptions','monsters'):
            if key in old:
                if key not in new:raise ValueError('Seeded state missing '+key)
                self.compare(old[key],new[key],key)
        if streams(old)!=streams(new):raise ValueError('Original-seed gameplay RNG differs')


def evidence(batch):
    data=json.loads((batch/'evidence/manifest.json').read_text())
    expected=[{'case_id':c,'role':r,'seed':s,'goal':g} for c,r,s,g in TARGETS]
    if [{k:item[k] for k in ('case_id','role','seed','goal')} for item in data['cases']]!=expected:
        raise ValueError('Confirmation schedule changed')
    for item in data['cases']:
        for name in ('episode','diagnostic'):
            path=(batch/item[name]).resolve(strict=True)
            if not path.is_relative_to(batch/'evidence') or sha(path)!=item[name+'_sha256']:
                raise ValueError('Original evidence fingerprint changed')
        valid=validate_episode(batch/item['episode'],require_complete=False)
        header=rows(batch/item['episode'])[0]
        diagnostic=json.loads((batch/item['diagnostic']).read_text())
        if (valid['role']!=item['role'] or (header['seed'],header['policy']['ascension'])!=(item['seed'],10)
                or diagnostic['before']['state']['seed']!=item['seed'] or valid['transitions']>3000):
            raise ValueError('Original role/seed/budget does not match target')
    return data['cases']


def orrery_ready(raw,last_method):
    rewards=raw.get('interaction',{}).get('rewards',[])
    return (last_method=='skipRewardCard' and raw.get('phase')=='REWARD' and raw.get('room')=='ShopRoom'
            and len(rewards)==1 and rewards[0]['type']=='CARD')


def goal_check(goal,before,steps):
    """Only fresh, actually observed frames and applied commands establish coverage."""
    if not steps or any(step['effect']!='APPLIED' for step in steps):return False
    first=steps[0];after=steps[-1]['after'];method,args=first['method'],first['args']
    if goal in ('headbutt','rip_and_tear'):
        if method!='playCard' or len(args)!=2:return False
        card=before['combat']['snapshot']['player_state']['hand'][args[0]]
        if goal=='rip_and_tear':
            return (len(steps)==1 and card['card_id']=='Rip and Tear' and args[1]==-1
                    and streams(after)['card_random']['counter']==streams(before)['card_random']['counter']+2)
        if card['card_id']!='Headbutt' or len(steps)!=2 or not is_headbutt_selection(first['after']):return False
        middle=first['after']['combat']['snapshot'];selected=steps[1]
        if selected['method']!='chooseGridCard' or len(selected['args'])!=1:return False
        # Headbutt's source options are references into the discard pile.
        target=middle['grid_card_select_screen_state']['group_cards'][selected['args'][0]]['uuid']
        final=after.get('combat',{}).get('snapshot',{})
        draw=final.get('player_state',{}).get('draw_pile',[])
        return (not is_native_combat_selection(after) and final.get('screen_name')=='NONE'
                and bool(draw) and draw[-1]['uuid']==target)
    if goal=='mirror':
        if method!='chooseGridCard' or before.get('room')!='ShopRoom' or after.get('phase')!='SHOP':return False
        old=before['player'];new=after['player'];target=before['cardSelection']['options'][args[0]]
        original={c['uuid']:c for c in old['masterDeck']};result={c['uuid']:c for c in new['masterDeck']}
        added=[c for uid,c in result.items() if uid not in original]
        return (len(steps)==1 and old['gold']==new['gold'] and original.keys()<=result.keys()
                and len(added)==1 and added[0]['id']==target['id']
                and added[0]['timesUpgraded']==target['timesUpgraded'] and not added[0]['bottled']
                and all({k:v for k,v in c.items() if k!='index'}=={k:v for k,v in result[u].items() if k!='index'} for u,c in original.items())
                and after['interaction']['shop']['purge_available'] is True)
    if goal=='orrery':
        return (len(steps)==1 and method=='proceed' and args==[] and before.get('room')=='ShopRoom'
                and before['interaction'].get('return_shop') is True and after.get('phase')=='SHOP'
                and streams(before)==streams(after))
    return False


class Confirmation:
    def __init__(self,collector): self.collector=collector
    def episode(self,item,batch):
        root=self.collector.root;check_permits(root);identifier=uuid.uuid4().hex
        reservation=root/'reports'/f'attempt-{identifier}.json'
        save_json(reservation,{'status':'running','attempt_id':identifier,'case_id':item['case_id'],'seed':item['seed']})
        source=rows(batch/item['episode']);observations=[r for r in source if r['type']=='observation']
        transitions=[r for r in source if r['type']=='transition']
        diagnostic=json.loads((batch/item['diagnostic']).read_text())
        engine=cpp=writer=None;context={};result=None;started=time.time();cpu_start=time.process_time()
        resources={'started_unix':started,'peak_rss_bytes':0,'peak_pss_bytes':0,'sampled_cpu_seconds':0}
        def sample():
            value=resource_sample(engine,cpp)
            for key in ('rss','pss'):resources['peak_'+key+'_bytes']=max(resources['peak_'+key+'_bytes'],value[key+'_bytes'])
            resources['sampled_cpu_seconds']=max(resources['sampled_cpu_seconds'],value['cpu_seconds']-cpu_start)
            resources.update(elapsed_seconds=time.time()-started,ended_unix=time.time())
        try:
            self.collector.policy.begin_episode();engine=Worker(self.collector.launcher);cpp=Worker(self.collector.native)
            boot=require(engine.execute({'operation':'state'},15000))
            current=require(engine.execute({'operation':'new_run','expected_state_token':boot['state_token'],
                                           'seed':item['seed'],'character':item['role'],'ascension':10},15000))
            writer=EpisodeWriter(root,role=item['role'],seed=item['seed'],policy={
                'purpose':'targeted-live-confirmation','model_sha256':self.collector.model_sha,'ascension':10,
                'runtime_sha256':self.collector.runtime_hashes,'profile_manifest_sha256':self.collector.profile_sha})
            writer.observation(current);combat=HeadlessCombatBridge();menus=IroncladInteractionBridge(characters=ROLES)
            matcher=SeededMatcher();last_method=None;prefix_count=0
            def execute(method,args):
                nonlocal current,last_method
                check_permits(root);before=current;raw=before['state'];sample()
                context.clear();context.update(before=before,method=method,args=list(args))
                native=raw['phase']=='PLAYER_TURN' or is_native_combat_selection(raw)
                if native:
                    frame=combat.observe(raw,run_id=writer.episode_id,sequence=before['sequence'])
                    prediction=require(cpp.execute({'operation':'predict','snapshot':frame.snapshot,'command':command(method,args)},1000))
                    context['prediction']=prediction;plan=combat.prepare(frame,prediction)
                    if (plan.native_method,list(plan.native_args))!=(method,list(args)):raise ValueError('Forced command is not exactly legal')
                    if not combat.validate_before(plan,frame):raise ValueError('Stale native action')
                else:
                    frame=menus.observe(raw,run_id=writer.episode_id,sequence=before['sequence'])
                    choices=[a for a in frame.actions if (a.native_method,list(a.native_args))==(method,list(args))]
                    if len(choices)!=1:raise ValueError('Recorded menu action is not uniquely legal')
                    plan=menus.prepare(frame,choices[0].api_action)
                    if not menus.validate_before(plan,frame):raise ValueError('Stale menu action')
                after=require(engine.execute({'operation':'action','expected_state_token':before['state_token'],'method':method,'args':list(args)},15000))
                context['after']=after;verify_driver(before,after,method,args)
                if native:
                    observed=combat.observe(after['state'],run_id=writer.episode_id,sequence=after['sequence'])
                    fp=cpp.execute({'operation':'fingerprint','snapshot':observed.snapshot},1000) if observed.snapshot else {}
                    context['fingerprint']=fp;effect=verify_combat_effect(plan,observed,fp)
                else:
                    observed=menus.observe_outcome(after['state'],run_id=writer.episode_id,sequence=after['sequence'],combat=combat,
                        import_snapshot=lambda s:cpp.execute({'operation':'fingerprint','snapshot':s},1000))
                    effect=verify_effect(plan,observed).status
                if effect!='APPLIED':raise ValueError('Confirmation action effect: '+effect)
                writer.transition(before_sequence=before['sequence'],after_sequence=after['sequence'],method=method,args=args,
                                  effect=effect,policy={'policy':'original-prefix-or-target-confirmation'})
                writer.observation(after)
                if method=='skipRewardCard':
                    self.collector.policy._skipped_rewards.add((writer.episode_id,tuple(c.uuid for c in frame.state.card_reward_cards)))
                current=after;last_method=method
                return {'before':raw,'after':after['state'],'method':method,'args':list(args),'effect':effect}
            for index,old in enumerate(observations):
                matcher.match(old['state'],current['state'])
                if item['goal']=='orrery' and orrery_ready(current['state'],last_method):break
                if index==len(transitions):break
                transition=transitions[index]
                execute(transition['native_method'],transition['native_args']);prefix_count+=1
            target_before=current['state'];start_sequence=current['sequence'];steps=[]
            if item['goal']=='orrery':
                if not orrery_ready(target_before,last_method):raise ValueError('Orrery original trigger not reached')
                frame=menus.observe(target_before,run_id=writer.episode_id,sequence=current['sequence'])
                action,_=self.collector.policy.choose(frame)
                if action.native_method!='proceed':raise ValueError('Declined reward would reopen instead of returning')
                steps.append(execute(action.native_method,action.native_args))
            else:
                matcher.match(diagnostic['before']['state'],target_before)
                if item['goal']=='mirror':method,args='chooseGridCard',diagnostic['after']['state']['driver_receipt']['args']
                else:
                    c=json.loads(diagnostic['prediction']['commands'][0]['command'])
                    method,args='playCard',[c['card_index'], -1 if item['goal']=='rip_and_tear' else c['monster_index']]
                steps.append(execute(method,args))
                if item['goal']=='headbutt':
                    frame=combat.observe(current['state'],run_id=writer.episode_id,sequence=current['sequence'])
                    prediction=require(cpp.execute({'snapshot':frame.snapshot,'session_id':writer.episode_id,
                        'search_profile':'FAST','max_expansions':5000,'timeout_ms':250},250))
                    plan=combat.prepare(frame,prediction)
                    steps.append(execute(plan.native_method,plan.native_args))
            if not goal_check(item['goal'],target_before,steps):raise ValueError('Required live target coverage not demonstrated')
            trajectory=writer.finish(error='targeted_live_confirmation_complete')
            result={'case_id':item['case_id'],'role':item['role'],'seed':item['seed'],'goal':item['goal'],'status':'verified',
                    'attempt_id':identifier,'trajectory':trajectory,'prefix_transitions':prefix_count,
                    'target_start_sequence':start_sequence,'target_steps':len(steps),'resources':resources}
        except Exception as error:
            path=root/'reports'/f'failure-{identifier}.json';save_json(path,{'case':item,'error':repr(error),**context})
            trajectory=writer.finish(error=error) if writer is not None and not writer.closed else None
            result={'case_id':item['case_id'],'role':item['role'],'seed':item['seed'],'goal':item['goal'],'status':'aborted',
                    'attempt_id':identifier,'diagnostic':str(path),'error':repr(error),'trajectory':trajectory,'resources':resources}
        finally:
            sample()
            for worker in (engine,cpp):
                if worker is not None:worker.close()
            if result is not None:
                result['collector_after_close']=idle_resources();save_json(reservation,result)
        return result


def verify_gate(batch):
    root=batch/'pilot';data=evidence(batch);report=json.loads((root/'reports/confirmation-status.json').read_text())
    if len(report['attempts'])!=5 or any(a['status']!='verified' for a in report['attempts']):raise ValueError('All five live targets must pass')
    proofs=[]
    for item,result in zip(data,report['attempts']):
        if any(result[k]!=item[k] for k in ('case_id','role','seed','goal')):raise ValueError('Confirmation identity mismatch')
        trajectory=result['trajectory'];path=Path(trajectory['path']).resolve(strict=True)
        if not path.is_relative_to(root/'episodes') or sha(path)!=trajectory['sha256']:raise ValueError('Confirmation trajectory changed')
        proof=verify_replay(path,root/'native-bin/battle-engine-worker',require_complete=False)
        if (proof['transitions'],proof['final_chain'])!=(trajectory['transitions'],trajectory['final_chain']):raise ValueError('Confirmation chain mismatch')
        fresh=rows(path);header=fresh[0];old=rows(batch/item['episode'])
        if (header['role'],header['seed'],header['policy']['ascension'])!=(item['role'],item['seed'],10):raise ValueError('Confirmation header changed')
        policy=header['policy']
        if policy['model_sha256']!=sha(root/'value-format12.pth') or policy['profile_manifest_sha256']!=sha(root/'profile-baseline-manifest.json'):raise ValueError('Confirmation frozen inputs changed')
        for name,digest in policy['runtime_sha256'].items():
            source=(root/name).resolve(strict=True)
            if not source.is_relative_to(root) or sha(source)!=digest:raise ValueError('Confirmation runtime changed')
        obs=[r for r in fresh if r['type']=='observation'];trans=[r for r in fresh if r['type']=='transition']
        old_obs=[r for r in old if r['type']=='observation'];old_trans=[r for r in old if r['type']=='transition']
        count=result['prefix_transitions'];matcher=SeededMatcher()
        if count>len(old_trans):raise ValueError('Prefix count exceeds original evidence')
        for index in range(count+1):
            matcher.match(old_obs[index]['state'],obs[index]['state'])
            if index<count and any(trans[index][k]!=old_trans[index][k] for k in ('native_method','native_args')):raise ValueError('Original prefix commands changed')
        if len(trans)!=count+result['target_steps'] or obs[count]['sequence']!=result['target_start_sequence']:raise ValueError('Target boundary changed')
        steps=[{'method':t['native_method'],'args':t['native_args'],'effect':t['effect'],'after':obs[i+1]['state']}
               for i,t in enumerate(trans) if i>=count]
        if item['goal']=='orrery':
            if count<1 or not orrery_ready(obs[count]['state'],trans[count-1]['native_method']):raise ValueError('Orrery source trigger absent')
        else:
            diagnostic=json.loads((batch/item['diagnostic']).read_text());matcher.match(diagnostic['before']['state'],obs[count]['state'])
        if not goal_check(item['goal'],obs[count]['state'],steps):raise ValueError('Independent live target check failed')
        if fresh[-1]['error']!='targeted_live_confirmation_complete':raise ValueError('Target was interrupted')
        proofs.append({'case_id':item['case_id'],'seed':item['seed'],'goal':item['goal'],'passed':True,'replay':proof})
    result={'passed':True,'schema':'headless-five-live-gate-v1','verified_unix':time.time(),'proofs':proofs,
            'confirmation_status_sha256':sha(root/'reports/confirmation-status.json'),'native_sha256':sha(root/'native-bin/battle-engine-worker')}
    save_json(batch/'reports/pilot-gate.json',result);return result


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--batch',type=Path,required=True);p.add_argument('--verify',action='store_true')
    a=p.parse_args(argv);batch=a.batch.resolve(strict=True);root=batch/'pilot'
    if a.verify:
        try:verify_gate(batch);return 0
        except Exception as error:
            save_json(batch/'reports/pilot-gate.json',{'passed':False,'error':repr(error),'verified_unix':time.time()});return 1
    signal.signal(signal.SIGTERM,agent.request_stop);signal.signal(signal.SIGINT,agent.request_stop)
    path=root/'reports/confirmation-status.json'
    with lane_lock(root):
        if path.exists():raise ValueError('A confirmation job is single-submission, not automatically retried')
        state={'status':'running','attempts':[],'started_unix':time.time()};save_json(path,state)
        try:
            collector=HeadlessCollector(root,str(root/'tools/start-session.sh'),str(root/'native-bin/battle-engine-worker'),str(root/'value-format12.pth'))
            runner=Confirmation(collector)
            for item in evidence(batch):
                check_permits(root)
                try:result=runner.episode(item,batch)
                except Exception as error:
                    diagnostic=root/'reports'/f'failure-{uuid.uuid4().hex}.json'
                    save_json(diagnostic,{'case':item,'error':repr(error),'stage':'confirmation-wrapper'})
                    result={k:item[k] for k in ('case_id','role','seed','goal')}
                    result.update(status='aborted',error=repr(error),diagnostic=str(diagnostic))
                state['attempts'].append(result);save_json(path,state)
            passed=all(r['status']=='verified' for r in state['attempts'])
            state.update(status='passed' if passed else 'finished_with_differences',ended_unix=time.time());save_json(path,state)
            return 0 if passed else 1
        except Exception as error:
            state.update(status='stopped',error=repr(error),ended_unix=time.time());save_json(path,state);return 1


if __name__=='__main__':raise SystemExit(main())
