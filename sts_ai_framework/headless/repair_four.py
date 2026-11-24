"""Extra source-level receipts for the last four original picker stops.

Only these four inventories are supported here; general gameplay verification
still uses the ordinary native bridge and the independent full-prefix replay.
"""
import hashlib
import json

from .live_confirmation import streams

TARGETS={5050285:'Dual Wield',5050293:'True Grit',5050339:'Seek',5050355:'Seek'}
PILES=('hand','drawPile','discardPile','exhaustPile')


def check(ok,message):
    if not ok:raise ValueError('Four-picker receipt: '+message)


def index(p):
    cards=[(c['uuid'],(name,c)) for name in PILES for c in p[name]]
    check(len(dict(cards))==len(cards),'duplicate card UUID across battle piles')
    return dict(cards)


def verify_four(seed,before,steps):
    """steps are (method,args,observed_state) from the actual episode, not predictions."""
    if seed not in TARGETS:return None
    source=TARGETS[seed]; p=before['player']
    check(before['seed']==seed and before['phase']=='PLAYER_TURN','not at original normal turn')
    check(len(steps) in (2,3) and steps[0][0]=='playCard' and steps[0][2]['phase']=='CARD_SELECT','original card did not open selection')
    args=steps[0][1]; check(len(args)==2 and args[1]==-1 and 0<=args[0]<len(p['hand']),'wrong original card or monster')
    card=p['hand'][args[0]];check(card['id']==source,'wrong played card')
    opened=steps[0][2]; choice=opened['cardSelection']
    expected='chooseGridCard' if source=='Seek' else 'chooseHandCard'
    name,idx,_=steps[1];check(name==expected and len(idx)==1 and 0<=idx[0]<len(choice['options']),'wrong picker control')
    target=choice['options'][idx[0]];uid=target['uuid']
    if len(steps)==3:
        check(steps[2][0]=='confirmHandSelect' and not steps[2][1] and source!='Seek'
              and [c['uuid'] for c in steps[1][2]['cardSelection']['selected']]==[uid],'wrong confirmed UUID')
    final=steps[-1][2];old=index(p);new=index(final['player'])
    check(uid in old and old[uid][0]==('drawPile' if source=='Seek' else 'hand') and uid!=card['uuid'],'wrong source pile')
    check(final['phase']=='PLAYER_TURN' and final['combat_receipt']['turn']==before['combat_receipt']['turn'],'no normal successor on same turn')
    baseline=streams(before)
    for method,args,state in steps:
        check(all(state[k]==before[k] for k in ('seed','act','floor','room','roomPhase')) and streams(state)==baseline,
              'room or exact gameplay RNG changed at '+method)
    check(card['uuid'] in new and new[card['uuid']][0]==('exhaustPile' if source=='Seek' else 'discardPile'),'wrong played card cleanup')
    # All the unrelated battle cards keep the same pile and identity, even if a
    # copied card shares ID/upgrades with an existing one.
    check(all(x in new and new[x][0]==loc for x,(loc,c) in old.items() if x not in (card['uuid'],uid)),'unrelated card moved')
    added=sorted(set(new)-set(old))
    if source=='Dual Wield':
        # The multi-choice Java path replaces the selected instance with
        # dupeAmount + 1 stat-equivalent copies; neither copy inherits its UUID.
        check(uid not in new and len(added)==2+int(card['timesUpgraded']>0),'wrong duplication count/original retained')
        for fresh in added:
            dest,c=new[fresh];check(dest=='hand' and all(c[k]==target[k] for k in ('id','timesUpgraded','misc','cost','baseDamage','baseBlock','baseMagicNumber')),'wrong copied card')
    else:
        check(not added and uid in new and new[uid][0]==('hand' if source=='Seek' else 'exhaustPile'),'chosen card did not arrive')
        check(all(new[uid][1][k]==target[k] for k in ('id','timesUpgraded','misc','cost')),'selected card identity changed')
    return {'source_card':source,'played_uuid':card['uuid'],'selected_uuid':uid,'selected_id':target['id'],
            'successor_phase':final['phase'],'turn':final['combat_receipt']['turn'],'new_uuids':added,
            'identical_rng_streams':len(baseline),'rng_sha256':hashlib.sha256(json.dumps(baseline,sort_keys=True).encode()).hexdigest(),
            'methods':[s[0] for s in steps]}
