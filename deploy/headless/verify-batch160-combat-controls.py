#!/usr/bin/env python3
"""Run per-case adversarial controls on the original 49 ordinary combat landings."""
import argparse
import copy
import json
from pathlib import Path
from uuid import UUID

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import HeadlessCombatBridge, verify_combat_effect
from sts_ai_framework.headless.repair_ledger import inventory, digest


def controls(batch, previous, worker):
    for case,d in inventory(batch,previous):
        if d['phase']!='PLAYER_TURN' or not d.get('after'): continue
        bridge=HeadlessCombatBridge();run=case['attempt_id']
        def frame(row):return HeadlessCombatBridge().observe(row['state'],run_id=run,sequence=row['sequence'])
        before=frame(d['before']);after=frame(d['after'])
        cmd=json.loads(d['prediction']['commands'][0]['command'])
        plan=bridge.prepare(before,worker.execute(dict(operation='predict',snapshot=before.snapshot,command=cmd),1000))
        def verdict(a):
            fp=worker.execute(dict(operation='fingerprint',snapshot=a.snapshot),1000)
            return verify_combat_effect(plan,a,fp)
        positive=verdict(after)
        def altered():
            return copy.deepcopy(d['after'])
        rng=altered();sn=rng['state']['combat']['snapshot']
        stream=sn['_headless_rng']['streams']['card_random']
        stream['state0']=f"{(int(stream['state0'],16)^1):016x}"
        bad_rng=verdict(frame(rng))
        # A separate, *internally consistent* extra XS128 draw. This catches
        # validators that trust source counters independently of the action.
        extra=altered();extra_s=extra['state']['combat']['snapshot']
        stream=extra_s['_headless_rng']['streams']['card_random']
        mask=(1<<64)-1;old0=int(stream['state0'],16);old1=int(stream['state1'],16)
        mixed=(old0^(old0<<23))&mask
        stream.update(state0=f'{old1:016x}',state1=f'{(mixed^old1^(mixed>>17)^(old1>>26)):016x}',counter=stream['counter']+1)
        extra_s['rng_state']['card_random_rng_counter']+=1
        extra_draw=verdict(frame(extra))
        forged=altered();sn=forged['state']['combat']['snapshot']
        # Modify the public and private copies together so a superficial exporter
        # consistency check still passes; choose an inherited rather than minted UUID.
        expected=json.loads(plan.prediction_json)['expected_card_uuids']
        done=False
        for k,pub in (('discard_pile','discardPile'),('draw_pile','drawPile'),('exhaust_pile','exhaustPile'),('hand','hand')):
            for i,uid in enumerate(expected[k]):
                if uid is None:continue
                forged_uid=str(UUID(int=UUID(uid).int^1))
                sn['player_state'][k][i]['uuid']=forged_uid
                forged['state']['player'][pub][i]['uuid']=forged_uid
                if k=='hand':
                    for act in forged['state']['combat']['legal_actions']:
                        if act.get('type')=='CARD' and act.get('card_index')==i:act['uuid']=forged_uid
                done=True;break
            if done:break
        if not done:raise ValueError('Missing inherited UUID for '+str(case['seed']))
        bad_uuid=verdict(frame(forged))
        if positive!='APPLIED' or bad_rng!='UNVERIFIED' or extra_draw!='UNVERIFIED' or bad_uuid!='UNVERIFIED':
            raise AssertionError((case['seed'],positive,bad_rng,bad_uuid))
        yield dict(case_id=case['case_id'],seed=case['seed'],diagnostic_sha256=case['diagnostic_sha256'],
                   original_action=cmd,original_landing=positive,corrupted_rng=bad_rng,extra_rng_draw=extra_draw,forged_uuid=bad_uuid)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for k in ('batch','previous','worker','output'):p.add_argument('--'+k,type=Path,required=True)
    a=p.parse_args();out=a.output.resolve();batch=a.batch.resolve()
    if out.is_relative_to(batch):raise ValueError('Preserve original reports')
    native=Worker(a.worker.resolve())
    try:rows=list(controls(batch,a.previous,native))
    finally:native.close()
    if len(rows)!=49:raise ValueError('Expected 49 distinct ordinary combat landings')
    with out.open('x',encoding='utf8') as f:json.dump(dict(schema='batch160-combat-negative-controls-v1',original_sha256=digest(batch/'reports/batch160-report.json'),worker_sha256=digest(a.worker),new_games=0,cases=rows),f,indent=2)
    print(f'{len(rows)} original landings verified; {3*len(rows)} corrupted landings rejected')
