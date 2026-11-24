"""Source-specific event receipts. All checks are observational; RNG clones never touch the game."""
import copy
import json
from uuid import UUID
from ..event_pools import chest_relic_pool
from ..models import Card
from .combat import HeadlessCombatBridge, validate_rng
from .state import HeadlessContractError

_MASK = (1 << 64) - 1


class Stream:
    """libGDX RandomXS128 and the game's per-API-call counter (not per word)."""
    def __init__(self, value):
        self.a = int(value['state0'], 16)
        self.b = int(value['state1'], 16)
        self.counter = value['counter']

    def word(self):
        x = self.a ^ ((self.a << 23) & _MASK)
        self.a, self.b = self.b, x ^ self.b ^ (x >> 17) ^ (self.b >> 26)
        return (self.a + self.b) & _MASK

    def long(self):
        self.counter += 1
        return self.word()

    def bounded(self, n):
        self.counter += 1
        while True:
            bits = self.word() >> 1
            if bits - bits % n + n - 1 < 1 << 63:
                return bits % n

    def state(self):
        return dict(state0=f'{self.a:016x}',state1=f'{self.b:016x}',counter=self.counter)

    def rewind_word(self):
        x = self.b ^ self.a ^ (self.a >> 26)
        y = x ^ (x >> 17) ^ (x >> 34) ^ (x >> 51)
        self.a, self.b = (y ^ (y << 23) ^ (y << 46)) & _MASK, self.a


def _shuffle(seed, values):
    """java.util.Collections.shuffle(list, new java.util.Random(seed))."""
    values = list(values)
    seed = (seed ^ 0x5deece66d) & ((1 << 48)-1)
    for n in range(len(values),1,-1):
        while True:
            seed = (seed*0x5deece66d+11) & ((1<<48)-1)
            bits = seed >> 17
            if n & (n-1) == 0:
                j = (n*bits) >> 31
                break
            j = bits % n
            if bits-j+n-1 < 1<<31: break
        values[n-1],values[j] = values[j],values[n-1]
    return values


def _streams(raw):
    rng=raw['combat_receipt']['rng']
    aliases={k+'_rng_counter':v['counter'] for k,v in rng['streams'].items()}
    return validate_rng({'_engine':'headless','_headless_rng':rng,'rng_state':aliases})['streams']


def _controls(event):
    return [(c.button_index,c.enabled) for c in event.choices]


_DIALOG={
    ("N'loth",'Nloth','0',2):'1',
    ('Fountain of Cleansing','FountainOfCurseRemoval','0',1):'1',
    ('The Mausoleum','TheMausoleum','INTRO',1):'RESULT',
    ('Colosseum','Colosseum','INTRO',0):'FIGHT',
    ('Masked Bandits','MaskedBandits','PAID_1',0):'PAID_2',
    ('Masked Bandits','MaskedBandits','PAID_2',0):'PAID_3',
    ('Falling','Falling','INTRO',0):'CHOICE',
    ('SensoryStone','SensoryStone','INTRO',0):'INTRO_2',
    ('Winding Halls','WindingHalls','0',0):'1',
    ('Mysterious Sphere','MysteriousSphere','INTRO',0):'PRE_COMBAT',
    ('Mysterious Sphere','MysteriousSphere','INTRO',1):'END',
    ('SecretPortal','SecretPortal','INTRO',0):'ACCEPT',
    ('SecretPortal','SecretPortal','INTRO',1):'LEAVE',
    ('The Joust','TheJoust','HALT',0):'EXPLANATION',
}

def _set_choice(choice,kind,followup,*effects):
    choice.update(kind=kind,followup=followup,outcomes=[dict(probability=1.,effects=list(effects))])


def source_menu(raw):
    """Fix the projected semantics, not the archived envelope or its token."""
    event=raw['interaction']['event']
    name=event['class_name']
    if name not in {'Nloth','TheMausoleum','FountainOfCurseRemoval','Colosseum','Falling','SensoryStone','Vampires','TheLibrary','Designer','WindingHalls','MysteriousSphere','SecretPortal','TheJoust'}:return event
    event=copy.deepcopy(event)
    if name!='Designer':
        for c in event['choices']:
            identity=(event['id'],name,event['phase'],c['button_index'])
            if identity in _DIALOG:
                if name in ('MysteriousSphere','SecretPortal'):
                    c['followup']='EVENT'  # Keep the strategic effects; this action first opens a confirmation.
                else:
                    _set_choice(c,'CONTINUE' if name in {'Colosseum','Falling','SensoryStone','WindingHalls','TheJoust'} else 'LEAVE','EVENT')
            if name=='TheJoust' and event['phase']=='EXPLANATION' and c['button_index'] in (0,1):
                # The labels describe the source controls; legacy semantics had the odds/payouts swapped.
                chance,reward=(.7,100) if c['button_index']==0 else (.3,250)
                cost=dict(type='lose_gold',amount=50)
                c['outcomes']=[dict(probability=chance,effects=[cost,dict(type='gain_gold',amount=reward)]),
                               dict(probability=1-chance,effects=[cost])]
            if (name,event['phase'])==('TheJoust','COMPLETE'):
                _set_choice(c,'LEAVE','MAP')
            if identity==('Fountain of Cleansing','FountainOfCurseRemoval','0',0):
                c['followup']='EVENT'
            if identity==('Mysterious Sphere','MysteriousSphere','PRE_COMBAT',0):
                _set_choice(c,'COMBAT','COMBAT',dict(type='start_combat',encounter='2 Orb Walkers'))
            if identity==('SecretPortal','SecretPortal','ACCEPT',0):
                _set_choice(c,'NAVIGATION','COMBAT',dict(type='jump_to_boss'))
            if identity==('Colosseum','Colosseum','FIGHT',0):
                _set_choice(c,'COMBAT','COMBAT',dict(type='start_combat',encounter='Colosseum Slavers'))
            if name=='Vampires' and event['phase']=='0' and c['button_index'] < len(event['choices'])-1:
                c['followup']='EVENT'
            if name=='TheLibrary' and event['phase']=='0':
                c['followup']='GRID' if c['button_index']==0 else 'EVENT'
        return event
    facts=raw['interaction'].get('designer')
    choices=event['choices']
    if facts is None:
        # Old bridge read inherited screenNum=0 instead of Designer.curScreen.
        # Only recover its two unambiguous controls; do not use display text.
        if event['phase']!='0':return event
        if len(choices)==1 and choices[0].get('kind')=='CONTINUE' and event.get('semantics_status')=='KNOWN':
            facts={'phase':'INTRO'}
        elif len(choices)==4 and all(c.get('kind')=='UNKNOWN' for c in choices):
            rng=Stream(_streams(raw)['misc'])
            if rng.counter!=2:raise HeadlessContractError('Missing Designer offer facts')
            rng.rewind_word();rng.rewind_word()
            facts={'phase':'MAIN','upgrade_one':bool(rng.word()&1),'remove_one':bool(rng.word()&1)}
        else:return event
    stage=facts['phase']
    if stage not in {'INTRO','MAIN','DONE'} or len(choices)!=(4 if stage=='MAIN' else 1):
        raise HeadlessContractError('Invalid Designer stage/controls')
    event.update(phase=stage,semantics_status='KNOWN',decision_kind='COMPLEX' if stage=='MAIN' else 'FORCED')
    if stage!='MAIN':
        _set_choice(choices[0],'CONTINUE','EVENT' if stage=='INTRO' else 'MAP')
        return event
    up,remove=facts['upgrade_one'],facts['remove_one']
    if type(up)!=bool or type(remove)!=bool:raise HeadlessContractError('Malformed Designer offers')
    adjust,clean,full,hp=(50,75,110,5) if raw['ascension']>=15 else (40,60,90,3)
    select=lambda purpose,n:dict(type='select_cards',purpose=purpose,count=n)
    gold=lambda cost:dict(type='lose_gold',amount=cost)
    _set_choice(choices[0],'UPGRADE_CARD' if up else 'EFFECT','GRID' if up else 'EVENT',gold(adjust),
                select('UPGRADE',1) if up else dict(type='random_upgrade',amount=2))
    _set_choice(choices[1],'REMOVE_CARD' if remove else 'TRANSFORM_CARD','GRID',gold(clean),
                select('PURGE' if remove else 'TRANSFORM',1 if remove else 2),
                *([] if remove else [dict(type='random_transform',count=2)]))
    _set_choice(choices[2],'MULTI_SELECT','GRID',gold(full),select('PURGE',1),dict(type='random_upgrade',amount=1))
    _set_choice(choices[3],'LEAVE','EVENT',dict(type='lose_hp',amount=hp))
    return event


def _dead_search(left,right,a,b,streams):
    e=left.event
    # The non-ambush outcome is selected by a shuffled three-item reward
    # queue, not sampled uniformly afresh each time. Reconstruct the queue
    # privately here; it is not exposed to the policy or to /state.
    risk=e.choices[0].outcomes[0]
    if len(risk.effects)!=1 or risk.effects[0].type!='commit_encounter':return False
    base=35 if a['ascension']>=15 else 25
    chance=round(100*risk.probability)
    if chance/100!=risk.probability or chance not in (base,base+25):return False
    searches=(chance-base)//25
    s=Stream(streams['misc'])
    if s.counter!=2+searches:return False  # no proof of the intervening calls
    raw=Stream(streams['misc'])
    for _ in range(1+searches):raw.rewind_word()
    # Constructor's first long seeded the java.util.Random shuffle; its
    # second call selected the monster. Reject a constructor/history mismatch.
    seed=(raw.a+raw.b)&_MASK
    if ('3 Sentries','Gremlin Nob','Lagavulin Event')[raw.bounded(3)]!=risk.effects[0].encounter:return False
    for i in range(searches):
        if raw.bounded(100)<base+25*i:return False
    if (raw.a,raw.b)!=(s.a,s.b) or s.bounded(100)<chance:return False
    expected=dict(streams,misc=s.state())
    reward=_shuffle(seed,['GOLD','NOTHING','RELIC'])[searches]
    nxt=right.event.choices[0].outcomes[0]
    if (_controls(e)!=[(0,True),(1,True)] or _controls(right.event)!=_controls(e)
            or nxt.probability!=(chance+25)/100 or nxt.effects!=risk.effects):return False
    ids={r.id for r in left.relics}
    player=left.player
    potions=left.potions
    if reward=='RELIC':
        rng=Stream(streams['relic']);roll=rng.bounded(100);expected['relic']=rng.state()
        tier='COMMON' if roll<50 else 'UNCOMMON' if roll<83 else 'RARE'
        eligible=dict(chest_relic_pool(tier,left.character,ids))
        if (len(right.relics)!=len(left.relics)+1 or right.relics[:-1]!=left.relics
                or right.relics[-1].id not in eligible or right.relics[-1].id in
                {'Bottled Flame','Bottled Lightning','Bottled Tornado','Whetstone'}):return False
        # A new exporter additionally binds the actual post-award metric. The
        # pooled reward is random; never assume its identity before execution.
        metric=b['interaction'].get('dead_search_receipt')
        if metric is not None and metric.get('relic_id')!=right.relics[-1].id:return False
        if right.relics[-1].id=='Potion Belt':
            if (len(right.potions)!=len(left.potions)+2 or right.potions[:-2]!=left.potions
                    or not all(p.id=='Potion Slot' and p.is_empty for p in right.potions[-2:])):return False
            potions=right.potions
    else:
        if right.relics!=left.relics:return False
        if reward=='GOLD' and 'Ectoplasm' not in ids:
            heal=5 if 'Bloody Idol' in ids and 'Mark of the Bloom' not in ids else 0
            player=player.model_copy(update={'gold':player.gold+30,'current_hp':min(player.max_hp,player.current_hp+heal)})
    metric=b['interaction'].get('dead_search_receipt')
    if metric is not None and (metric.get('count')!=searches+1 or metric.get('chance')!=chance+25):return False
    return (expected==_streams(b) and left.deck==right.deck and potions==right.potions
            and right.player==player)


def _bandits(before,after,a,b):
    left,right=before.state,after.state
    toolbox=b['phase']=='CARD_PICK' and b['interaction'].get('card_reward',{}).get('source_action')=='ChooseOneColorless'
    if (a['room']!=b['room'] or a['room']!='EventRoom' or a['roomPhase']!='EVENT'
            or (b['phase']!='PLAYER_TURN' and not toolbox) or b['roomPhase']!='COMBAT' or left.keys!=right.keys
            or a['seed']!=b['seed'] or a['map']!=b['map'] or a['history']!=b['history']
            or b['interaction']['flow']['event_class']!='MaskedBandits' or left.deck!=right.deck
            or [r.id for r in left.relics]!=[r.id for r in right.relics]
            or [p.id for p in left.potions]!=[p.id for p in right.potions]):return False
    receipt=b['combat_receipt']
    if (receipt['turn']!=1 or receipt['active_monsters']!=3 or receipt['battle_over']
            or receipt['player_dead'] or receipt['played_card_uuids']):return False
    if not toolbox:
        snapshot=HeadlessCombatBridge().observe(b,run_id=after.run_id,sequence=after.sequence).snapshot
        if (snapshot is None or snapshot['turn']!=1
                or snapshot['_headless_rng']!=receipt['rng']):return False
    streams=_streams(a);expected=dict(streams)
    misc=Stream(streams['misc']);misc.bounded(31 if a['runMode']['is_daily'] else 11)
    ai=Stream(streams['ai'])
    for _ in range(3):ai.bounded(100)
    shuffle=Stream(streams['shuffle']);deck=_shuffle(shuffle.long(),left.deck)
    expected.update(misc=misc.state(),ai=ai.state(),shuffle=shuffle.state())
    if toolbox and not _toolbox_offers(left, b, expected):return False
    if not _opening_snecko_costs(left,right,expected,toolbox):return False
    if expected!=_streams(b):return False
    hp=Stream(streams['monster_hp'])
    if hp.counter!=3:return False
    hp.rewind_word();hp.rewind_word()
    asc=a['ascension']>=7
    roster=[('BanditChild',34 if asc else 30,'ATTACK'),('BanditLeader',(37 if asc else 35)+hp.bounded(5),'UNKNOWN'),
            ('BanditBear',(40 if asc else 38)+hp.bounded(5),'STRONG_DEBUFF')]
    ids={r.id for r in left.relics}
    damage=3 if not toolbox and 'Mercury Hourglass' in ids else 0
    if ([(m.id,m.max_hp,m.intent) for m in right.monsters]!=roster
            or any(m.current_hp!=m.max_hp-damage for m in right.monsters)):return False
    if not _opening_piles(left,right,deck,toolbox):return False
    healed=2 if 'Blood Vial' in ids and 'Mark of the Bloom' not in ids else 0
    # The ordinary EventRoom isn't marked elite and contains no bosses.
    energy=3+len(ids & {'Ectoplasm','Cursed Key','Coffee Dripper','Fusion Hammer','Sozu','Runic Dome',
                          'Velvet Choker',"Philosopher's Stone",'Busted Crown','Mark of Pain'})
    if 'Lantern' in ids:energy+=1
    if right.relics!=_opening_relic_counters(left):return False
    return right.player==left.player.model_copy(update={'current_hp':min(left.player.max_hp,left.player.current_hp+healed),
                    'energy':energy,'block':10 if 'Anchor'in ids and not toolbox else 0})


_OPENING_INNATE={'Backstab','BootSequence','Dramatic Entrance','Mind Blast','Writhe','Pride'}
_OPENING_UPGRADED={'Brutality','Infinite Blades','After Image','Chill','Hello World','Storm',
                   'Machine Learning','BattleHymn','Alpha','Establishment'}
_PREDRAW_CARDS={'PureWater':('Miracle',), 'HolyWater':('Miracle',)*3, 'Ninja Scroll':('Shiv',)*3}
_OPENING_RELIC_COUNTERS={'Letter Opener':0,'Velvet Choker':0,'Pocketwatch':0,'HornCleat':1,
                         'Ornamental Fan':0,'Kunai':0,'Shuriken':0}


def _opening_relic_counters(state):
    return [r.model_copy(update={'counter':_OPENING_RELIC_COUNTERS[r.id]})
            if r.id in _OPENING_RELIC_COUNTERS else r for r in state.relics]


def _predraw_cards(relics, paused):
    """Only the relic callbacks queued before Toolbox have finished at its picker."""
    cards=[]
    for relic in relics:
        if paused and relic.id=='Toolbox':break
        cards.extend(_PREDRAW_CARDS.get(relic.id,()))
    return cards


def _opening_card(c):
    return c.is_bottled or c.id in _OPENING_INNATE or c.upgrades>0 and c.id in _OPENING_UPGRADED


def _opening_draw(deck,relics):
    ids={r.id for r in relics}
    return min(len(deck),max(5+2*('Snecko Eye' in ids),sum(map(_opening_card,deck)))+2*('Bag of Preparation' in ids)
               +2*('Ring of the Snake' in ids)+('Ring of the Serpent' in ids))


def _opening_piles(left,right,deck,paused):
    deck=sorted(deck,key=_opening_card)
    generated=_predraw_cards(left.relics,paused)
    count=0 if paused else min(10-len(generated),_opening_draw(deck,left.relics))
    distinct={c.uuid for c in deck}
    def facts(cards):return [(c.uuid,c.id,c.upgrades,c.misc) for c in cards]
    actual=right.hand[:len(generated)]
    return (facts(right.draw_pile)==facts(deck[:len(deck)-count])
            and facts(right.hand[len(generated):])==facts(deck[len(deck)-count:][::-1])
            and not right.exhaust_pile and not right.discard_pile
            and [c.id for c in actual]==generated
            and len({c.uuid for c in actual})==len(actual)
            and all(c.uuid not in distinct and c.upgrades==0 and c.cost==c.cost_for_turn==0 for c in actual))


def _opening_snecko_costs(left,right,expected,paused):
    if paused or not any(r.id=='Snecko Eye' for r in left.relics):return True
    cards={c.uuid:c for c in left.deck}
    rng=Stream(expected['card_random'])
    for drawn in right.hand:
        original=cards.get(drawn.uuid)
        if original is None:continue
        cost=rng.bounded(4) if original.cost>=0 else original.cost
        if drawn.cost!=cost or drawn.cost_for_turn!=cost:return False
    expected['card_random']=rng.state()
    return True


def _toolbox_offers(state,after,expected):
    if not any(r.id=='Toolbox' for r in state.relics):return False
    rng=Stream(expected['card_random']);offers=[]
    for _ in range(256):
        identity=_TOOLBOX_POOL[rng.bounded(len(_TOOLBOX_POOL))]
        if identity not in offers:offers.append(identity)
        if len(offers)==3:break
    offered=after['cardRewardOptions']
    uuids={c['uuid'] for c in offered}
    occupied={c['uuid'] for pile in ('masterDeck','hand','drawPile','discardPile','exhaustPile')
              for c in after['player'][pile]}
    if ([c['id'] for c in offered]!=offers or len(uuids)!=3 or uuids.intersection(occupied)
            or any(c['upgraded'] or c['bottled'] for c in offered)):
        return False
    expected['card_random']=rng.state()
    return True


def _toolbox_opening_piles(left, right, choice, queued, occupied, expected, ids):
    def generated(identity, cost=0, *, upgradable=True, exhaust=True):
        return Card(index=0, uuid='', id=identity, name=identity, cost=cost, cost_for_turn=cost,
                    can_upgrade=upgradable, exhausts=exhaust)

    picked = generated(choice['id'], choice['cost'], upgradable=choice.get('canUpgrade', False),
                       exhaust=choice['exhaust']).model_copy(update={
        'upgrades': choice['timesUpgraded'], 'cost_for_turn': choice['costForTurn'], 'misc': choice['misc'],
    })
    hand = [*left.hand, picked, *(generated(identity) for identity in queued)]
    draw_pile = list(left.draw_pile)
    discard, exhaust = [], []
    randomized = set()
    rng = Stream(expected['card_random'])

    def draw(amount):
        batch = []
        for _ in range(min(amount, 10 - len(hand), len(draw_pile))):
            source = draw_pile.pop()
            card = source
            if 'Snecko Eye' in ids and card.cost >= 0:
                cost = rng.bounded(4)
                randomized.add(card.uuid)
                card = card.model_copy(update={'cost': cost, 'cost_for_turn': cost})
            hand.append(card)
            batch.append((source, card))
        # DrawCardAction finishes this batch before addToTop effects resolve, last drawn first.
        for source, drawn in reversed(batch):
            if source.id == 'DeusExMachina':
                hand.remove(drawn)
                exhaust.append(drawn)
                copies = [generated('Miracle') for _ in range(2 + int(source.upgrades > 0))]
            elif source.id == 'Endless Agony':
                # triggerWhenDrawn copies the card before Confusion rerolls the original.
                copies = [source.model_copy(update={'uuid': ''})]
            else:
                continue
            room = 10 - len(hand)
            hand.extend(copies[:room])
            discard.extend(copies[room:])

    initial = 5 + 2 * ('Snecko Eye' in ids) + ('Ring of the Serpent' in ids)
    extra_innate = max(0, sum(map(_opening_card, draw_pile)) - initial)
    draw(initial)
    for relic in left.relics:
        if relic.id in ('Bag of Preparation', 'Ring of the Snake'):
            draw(2)
        elif relic.id == 'Mark of Pain':
            # At battle start, after the main draw. Later relics may draw the new Wounds.
            for _ in range(2):
                pos = rng.bounded(len(draw_pile)) if draw_pile else 0
                draw_pile.insert(pos, generated('Wound', -2, upgradable=False, exhaust=False))

    upgrade_index = None
    if 'WarpedTongs' in ids:
        eligible = [i for i, c in enumerate(hand) if c.can_upgrade and c.type != 'STATUS']
        if eligible:
            shuffle = Stream(expected['shuffle'])
            upgrade_index = _shuffle(shuffle.long(), eligible)[0]
            expected['shuffle'] = shuffle.state()
    # Excess innate cards queue a separate pre-turn action after the battle-start actions.
    draw(extra_innate)
    expected['card_random'] = rng.state()

    for planned, observed, upgrade in ((hand, right.hand, upgrade_index),
                                       (draw_pile, right.draw_pile, None),
                                       (discard, right.discard_pile, None),
                                       (exhaust, right.exhaust_pile, None)):
        if len(planned) != len(observed):
            return False
        for i, (want, got) in enumerate(zip(planned, observed)):
            if (want.id, want.upgrades + int(i == upgrade), want.misc) != (got.id, got.upgrades, got.misc):
                return False
            if want.uuid:
                if got.uuid != want.uuid:
                    return False
            else:
                if not got.uuid or got.uuid in occupied or got.exhausts != want.exhausts:
                    return False
                occupied.add(got.uuid)
            if not want.uuid or want.uuid in randomized:
                if (want.cost, want.cost_for_turn) != (got.cost, got.cost_for_turn):
                    return False
    return True


def toolbox_event_pick_receipt(before,after,index):
    """Verify the Toolbox copy, queued opening draw and combat handoff."""
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    left,right=before.state,after.state
    ids={r.id for r in left.relics}
    stable=('seed','floor','act','ascension','room','roomPhase','map','history','observedKeys','runMode')
    if (any(a[k]!=b[k] for k in stable) or a['phase']!='CARD_PICK'
            or a['roomPhase']!='COMBAT' or a['interaction']['card_reward'].get('source_action')!='ChooseOneColorless'
            or 'Toolbox' not in ids or left.deck!=right.deck or left.keys!=right.keys
            or b['phase'] not in ('PLAYER_TURN','CARD_SELECT')
            or b['phase']=='CARD_SELECT' and not after.native_combat_handoff
            or left.discard_pile or left.exhaust_pile
            or len(left.draw_pile)!=len(left.deck)
            or [p.id for p in left.potions]!=[p.id for p in right.potions]):
        return False
    offered=a['cardRewardOptions']
    if not 0<=index<len(offered) or len(offered)!=3:return False
    driver=b['driver_receipt']
    if (driver['method'],driver['args'],driver['sequence'])!=('chooseRewardCard',[index],after.sequence):return False
    relic_to_card=_predraw_cards(left.relics,True)
    total=_predraw_cards(left.relics,False)
    queued=total[len(relic_to_card):]
    if [c.id for c in left.hand]!=relic_to_card or len(total)+1>10:return False
    occupied={c.uuid for c in left.deck+left.draw_pile+left.hand}|{c['uuid'] for c in offered}
    expected=_streams(a)
    if not _toolbox_opening_piles(left,right,offered[index],queued,occupied,expected,ids):
        return False
    ra,rb=a['combat_receipt'],b['combat_receipt']
    if ({k:v for k,v in ra.items() if k!='rng'}!={k:v for k,v in rb.items() if k!='rng'}
            or (rb['turn'],rb['battle_over'],rb['player_dead'],rb['played_card_uuids'])!=(1,False,False,[])
            or rb['active_monsters']!=len(right.monsters)):
        return False
    if expected!=_streams(b):return False
    damage=3 if 'Mercury Hourglass' in ids else 0
    if ([(m.id,m.current_hp,m.max_hp,m.block) for m in right.monsters]!=[
            (m.id,m.current_hp-max(0,damage-m.block),m.max_hp,max(0,m.block-damage)) for m in left.monsters]
            or any(old.intent!=new.intent and not (old.intent=='NONE' and 'Runic Dome' in ids)
                   for old,new in zip(left.monsters,right.monsters,strict=True))
            or right.player!=left.player.model_copy(update={
                'energy':left.player.energy+int(any(r.id=='Happy Flower' and r.counter==0 for r in left.relics)),
                'block':min(999,left.player.block+10*('Anchor' in ids))})
            or right.relics!=left.relics):
        return False
    snapshot=HeadlessCombatBridge().observe(b,run_id=after.run_id,sequence=after.sequence).snapshot
    return snapshot is not None and snapshot['turn']==1 and snapshot['_headless_rng']==rb['rng']


# Legacy archives did not export the Neow catalog. The frozen Silent profile's
# card iteration order was independently read in 21 same-profile combat receipts.
# Those are src pool orders; initializeCardPools copies them with addToBottom,
# reversing the mutable pools used by getRewardCards/getCard.
_SILENT_SRC = {
    'common': 'Flying Knee|Dodge and Roll|Sucker Punch|PiercingWail|Prepared|Outmaneuver|Backflip|Slice|Quick Slash|Acrobatics|Poisoned Stab|Dagger Throw|Deflect|Blade Dance|Dagger Spray|Deadly Poison|Underhanded Strike',
    'uncommon': 'Predator|All Out Attack|Distraction|Footwork|Masterful Stab|Flechettes|Bouncing Flask|Backstab|Dash|Eviscerate|Reflex|Infinite Blades|Noxious Fumes|Heel Hook|Terror|Well Laid Plans|Finisher|Escape Plan|Calculated Gamble|Skewer|Riddle With Holes|Endless Agony|Blur|Caltrops|Choke|Expertise|Tactician|Leg Sweep|Crippling Poison',
    'rare': 'Venomology|Malaise|Phantasmal Killer|Die Die Die|Adrenaline|Envenom|Doppelganger|Burst|Wraith Form v2|Tools of the Trade|Night Terror|Unload|After Image|Bullet Time|Glass Knife|A Thousand Cuts',
}
_SILENT_POTIONS = ('Poison Potion|CunningPotion|GhostInAJar|Block Potion|Dexterity Potion|Energy Potion|Explosive Potion|'
                   'Fire Potion|Strength Potion|Swift Potion|Weak Potion|FearPotion|AttackPotion|SkillPotion|PowerPotion|'
                   'ColorlessPotion|SteroidPotion|SpeedPotion|BlessingOfTheForge|Regen Potion|Ancient Potion|LiquidBronze|'
                   'GamblersBrew|EssenceOfSteel|DuplicationPotion|DistilledChaos|LiquidMemories|CultistPotion|Fruit Juice|'
                   'SneckoOil|FairyPotion|SmokeBomb|EntropicBrew').split('|')
_TOOLBOX_POOL = ('Madness|Thinking Ahead|Mind Blast|Metamorphosis|Jack Of All Trades|Swift Strike|Good Instincts|'
                 'Master of Strategy|Magnetism|Finesse|Discovery|Chrysalis|Transmutation|Panacea|Purity|Enlightenment|'
                 'Forethought|Flash of Steel|HandOfGreed|Mayhem|Apotheosis|Secret Weapon|Panache|Violence|Deep Breath|'
                 'Secret Technique|Blind|The Bomb|Impatience|Dramatic Entrance|Trip|PanicButton|Sadistic Nature|Dark Shackles').split('|')
_DEFECT_SRC = {
    'common': 'Steam|Cold Snap|Leap|Beam Cell|Hologram|Conserve Battery|Sweeping Beam|Coolheaded|Gash|Stack|Barrage|Compile Driver|Redo|Streamline|Ball Lightning|Go for the Eyes',
    'uncommon': 'Doom and Gloom|Defragment|Capacitor|White Noise|Skim|Scrape|Lockon|Reprogram|Auto Shields|Reinforced Body|Double Energy|Darkness|Rip and Tear|FTL|Force Field|Tempest|Heatsinks|Static Discharge|BootSequence|Chill|Loop|Self Repair|Melter|Chaos|Blizzard|Aggregate|Fusion|Consume|Glacier|Hello World|Steam Power|Genetic Algorithm|Storm',
    'rare': 'Multi-Cast|Thunder Strike|Biased Cognition|Machine Learning|Electrodynamics|Buffer|Rainbow|Seek|All For One|Reboot|Amplify|Creative AI|Fission',
}


def _opening_cards(rng,catalog):
    """Standard Act I: initial rare offset +5, no egg/prism, no upgraded offers."""
    offset=5
    cards=[];rarities=[]
    for _ in range(3):
        roll=rng.bounded(100)+offset
        tier='rare' if roll<3 else 'uncommon' if roll<40 else 'common'
        if tier=='rare':offset=5
        if tier=='common':offset-=1
        pool=catalog[tier]
        if not pool or len(set(pool)-set(cards))==0:raise ValueError('exhausted reward catalog')
        while True:
            card=pool[rng.bounded(len(pool))]
            if card not in cards:break
        cards.append(card);rarities.append(tier)
    for tier in rarities:
        if tier!='rare':rng.long()  # randomBoolean(0f) still advances nextFloat
    return cards


def _tiny_house(left,right,a,b):
    starter,potion_pool={'THE_SILENT':('Ring of the Snake',_SILENT_POTIONS),
        'IRONCLAD':('Burning Blood',['BloodPotion','ElixirPotion','HeartOfIron',*_SILENT_POTIONS[3:]])}[left.character]
    if (a['floor']!=0 or a['act']!=1 or a['phase']!='NEOW' or b['phase']!='REWARD'
            or a['room']!='NeowRoom' or b['room']!='NeowRoom' or b['roomPhase']!='EVENT'
            or any(a[k]!=b[k] for k in ('seed','map','history','runMode','floor','act','ascension'))
            or any(a['runMode'][k] for k in ('is_daily','is_trial','is_endless'))
            or any(x['interaction']['flow']['event_class']!='NeowEvent' for x in (a,b))
            or [(r.id,r.counter) for r in left.relics]!=[(starter,-1)]
            or [(r.id,r.counter) for r in right.relics]!=[('Tiny House',-1)]
            or left.potions!=right.potions or left.keys!=right.keys
            or right.player!=left.player.model_copy(update={'current_hp':left.player.current_hp+5,'max_hp':left.player.max_hp+5})):return False
    choice=left.event.choices[3]
    if len(choice.outcomes)!=1 or choice.outcomes[0].probability!=1:return False
    effects=choice.outcomes[0].effects
    if [(e.type,e.relic_id,e.pool) for e in effects]!=[('lose_relic',starter,None),('random_relic',None,'BOSS')]:return False
    streams=_streams(a);misc=Stream(streams['misc']);cards=Stream(streams['card'])
    if cards.counter!=0:return False
    deck=copy.deepcopy(a['player']['masterDeck'])
    changed=_shuffle(misc.long(),[c for c in deck if c['canUpgrade']])[0]
    increases={'Strike_G':{'baseDamage':3},'Defend_G':{'baseBlock':3},'Survivor':{'baseBlock':3},
               'Neutralize':{'baseDamage':1,'baseMagicNumber':1},
               'Strike_R':{'baseDamage':3},'Defend_R':{'baseBlock':3},'Bash':{'baseDamage':2,'baseMagicNumber':1}}
    for stat,v in increases[changed['id']].items():changed[stat]+=v
    changed.update(name=changed['name']+'+',upgraded=True,timesUpgraded=1,canUpgrade=False)
    if deck!=b['player']['masterDeck']:return False
    potion=potion_pool[misc.bounded(len(potion_pool))]
    catalog=b['interaction'].get('neow_card_catalog')
    if catalog is None:
        if left.character!='THE_SILENT':return False
        catalog={k:v.split('|')[::-1] for k,v in _SILENT_SRC.items()}
    offers=_opening_cards(cards,catalog)
    if dict(streams,misc=misc.state(),card=cards.state())!=_streams(b):return False
    raw=b['rewards'];native=b['interaction']['rewards']
    if not (len(raw)==len(native)==3 and [r['type'] for r in raw]==['GOLD','POTION','CARD']
            and raw[0]['gold']==50 and raw[0]['bonusGold']==0 and raw[1]['potion']==potion and raw[2]['cards']==offers
            and native[0]=={'native_index':0,'type':'GOLD','amount':50}
            and native[1]=={'native_index':1,'type':'POTION','potion_id':potion}
            and native[2]['native_index']==2 and native[2]['type']=='CARD'):return False
    uuids=[str(UUID(v)) for v in native[2]['card_uuids']]
    return len(set(uuids))==len(uuids)==3 and not set(uuids).intersection(c.uuid for c in left.deck)


_STARTER_STRIKES = {'Strike_R','Strike_G','Strike_B','Strike_P'}


def _room_context(a, b, name):
    return (a['room']==b['room']=='EventRoom'
            and all(a[k]==b[k] for k in ('seed','floor','act','ascension','runMode','map','history','observedKeys'))
            and all(raw['interaction']['flow']['event_class']==name for raw in (a,b)))


def portal_boss_receipt(before, after):
    from .flow import _current, _keys_preserved, _path
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    f,g=(state['interaction']['flow'] for state in (a,b))
    left,right=before.state,after.state
    receipt=b.get('combat_receipt',{})
    return (a['room']=='EventRoom' and f['event_class']=='SecretPortal'
            and a['roomPhase']=='EVENT' and b['room']=='MonsterRoomBoss'
            and b['roomPhase']=='COMBAT' and b['phase'] in ('PLAYER_TURN','CARD_SELECT','CARD_PICK')
            and a['seed']==b['seed'] and left.act==right.act==3
            and right.floor==left.floor+1 and right.character==left.character
            and right.ascension_level==left.ascension_level and _keys_preserved(left,right)
            and _current(b)==(-1,15) and _path(g)==_path(f)+[(1,15)]
            and left.deck==right.deck and [r.id for r in left.relics]==[r.id for r in right.relics]
            and left.potions==right.potions and left.player.gold==right.player.gold
            and receipt.get('room_class')=='MonsterRoomBoss' and receipt.get('turn')==1
            and receipt.get('active_monsters',0)>0 and receipt.get('battle_over') is False
            and receipt.get('played_card_uuids')==[])


def _card_facts(card):
    return {k:v for k,v in card.items() if k!='index'}


def _fountain(left,right,a,b):
    # All removable curses are processed, not just the first copy of each card ID.
    original=a['player']['masterDeck']
    protected={'AscendersBane','CurseOfTheBell','Necronomicurse'}
    retained=[c for c in original if c['type']!='CURSE' or c['id'] in protected or c['bottled']]
    removed_parasites=sum(c['id']=='Parasite' for c in original)-sum(c['id']=='Parasite' for c in retained)
    maximum=max(1,left.player.max_hp-3*removed_parasites)
    player=left.player.model_copy(update={'max_hp':maximum,'current_hp':min(maximum,left.player.current_hp)})
    return (_room_context(a,b,'FountainOfCurseRemoval') and a['phase']==b['phase']=='EVENT'
            and a['roomPhase']==b['roomPhase']=='EVENT' and right.event is not None
            and (right.event.id,right.event.class_name,right.event.phase)==('Fountain of Cleansing','FountainOfCurseRemoval','1')
            and _controls(right.event)==[(0,True)] and player==right.player
            and left.potions==right.potions and left.relics==right.relics and _streams(a)==_streams(b)
            and [_card_facts(c) for c in retained]==[_card_facts(c) for c in b['player']['masterDeck']])


def _joust(left,right,a,b,button):
    phase=left.event.phase
    stage={'EXPLANATION':'PRE_JOUST','PRE_JOUST':'JOUST','JOUST':'COMPLETE'}[phase]
    if not (_room_context(a,b,'TheJoust') and a['phase']==b['phase']=='EVENT'
            and a['roomPhase']==b['roomPhase']=='EVENT' and right.event is not None
            and (right.event.id,right.event.class_name,right.event.phase)==('The Joust','TheJoust',stage)
            and _controls(right.event)==[(0,True)] and left.deck==right.deck
            and left.potions==right.potions and left.relics==right.relics):
        return False
    streams=_streams(a)
    player=left.player
    bet_after=b['interaction']['joust_bet_owner']
    if type(bet_after)!=bool:return False
    if phase=='EXPLANATION':
        if button not in (0,1) or bet_after!=(button==1):return False
        player=player.model_copy(update={'gold':max(0,player.gold-50)})
    elif button!=0 or a['interaction'].get('joust_bet_owner') is not bet_after:
        return False
    elif phase=='PRE_JOUST':
        misc=Stream(streams['misc']);misc.long();streams=dict(streams,misc=misc.state())
    else:
        # The immediately preceding PRE_JOUST call sampled the result, not this action.
        misc=streams['misc']
        if misc['counter']<=0:return False
        owner_wins=((int(misc['state0'],16)+int(misc['state1'],16))&_MASK)>>40 < 5033165
        relics={r.id for r in left.relics}
        if owner_wins==bet_after and 'Ectoplasm' not in relics:
            healed=5 if 'Bloody Idol' in relics and 'Mark of the Bloom' not in relics else 0
            player=player.model_copy(update={'gold':player.gold+(250 if owner_wins else 100),
                                             'current_hp':min(player.max_hp,player.current_hp+healed)})
    return player==right.player and streams==_streams(b)


def _falling_choice(left,right,a,b,button):
    if not (_room_context(a,b,'Falling') and a['phase']==b['phase']=='EVENT'
            and a['roomPhase']==b['roomPhase']=='EVENT' and right.event is not None
            and (right.event.class_name,right.event.phase)==('Falling','RESULT')
            and _controls(right.event)==[(0,True)] and left.player==right.player
            and left.relics==right.relics and left.potions==right.potions and _streams(a)==_streams(b)):
        return False
    original=a['player']['masterDeck']
    groups={kind:[c for c in original if c['type']==kind and not c['bottled']] for kind in ('ATTACK','SKILL','POWER')}
    count=sum(map(bool,groups.values()))
    kinds=('SKILL','POWER','ATTACK')
    if count==0: return button==0 and left.deck==right.deck
    kind=kinds[button]
    if not groups[kind] or _controls(left.event)!=[(i,bool(groups[k])) for i,k in enumerate(kinds)]:return False
    # The constructor sets the three offers, in attack/skill/power order.
    # No further misc call occurs between the constructor and this prompt.
    misc=_streams(a)['misc']; rng=Stream(misc)
    if rng.counter<count:return False
    rng.counter-=count
    for _ in range(count):rng.rewind_word()
    offers={k:g[rng.bounded(len(g))] for k,g in groups.items() if g}
    target=offers[kind]
    effects=left.event.choices[button].outcomes[0].effects
    return (rng.state()==misc and len(effects)==1 and (effects[0].type,effects[0].card_id)==('remove_card',target['id'])
            and [_card_facts(c) for c in original if c['uuid']!=target['uuid']]
            ==[_card_facts(c) for c in b['player']['masterDeck']])


# AbstractDungeon.getColorlessCardFromPool sorts by card ID after filtering
# the pinned COLORLESS pool by rarity (including healing cards).
_COLORLESS_REWARDS=(
    'Bandage Up|Blind|Dark Shackles|Deep Breath|Discovery|Dramatic Entrance|Enlightenment|Finesse|Flash of Steel|Forethought|Good Instincts|Impatience|Jack Of All Trades|Madness|Mind Blast|Panacea|PanicButton|Purity|Swift Strike|Trip'.split('|'),
    'Apotheosis|Chrysalis|HandOfGreed|Magnetism|Master of Strategy|Mayhem|Metamorphosis|Panache|Sadistic Nature|Secret Technique|Secret Weapon|The Bomb|Thinking Ahead|Transmutation|Violence'.split('|'))


def _sensory_stone(left,right,a,b,button):
    if not (_room_context(a,b,'SensoryStone') and a['phase']=='EVENT' and b['phase']=='REWARD'
            and b['roomPhase']=='COMPLETE' and 0<=button<=2
            and left.deck==right.deck and left.relics==right.relics and left.potions==right.potions
            and not any(a['runMode'].get(k) for k in ('is_daily','is_trial','is_endless'))):
        return False
    streams=_streams(a); card=Stream(streams['card']); misc=Stream(streams['misc']);misc.long()
    size=3
    for relic in left.relics:
        size+=1 if relic.id=='Question Card' else -2 if relic.id=='Busted Crown' else 0
    if size<=0:return False
    hp_loss=max(0,5*button-int(button>0 and any(r.id=='TungstenRod' for r in left.relics)))
    if left.player.current_hp<=hp_loss or right.player!=left.player.model_copy(update={'current_hp':left.player.current_hp-hp_loss}):return False
    offers=[]
    for _ in range(button+1):
        row=[]
        for _ in range(size):
            pool=_COLORLESS_REWARDS[int((card.long()>>40)<5033165)]  # Java nextFloat < 0.3f
            if not set(pool)-set(row):return False
            while True:
                name=pool[card.bounded(len(pool))]
                if name not in row:break
            row.append(name)
        offers.append(row)
    if _streams(b)!=dict(streams,card=card.state(),misc=misc.state()):return False
    wire=b['rewards']; native=b['interaction']['rewards']; uuids=set(c.uuid for c in left.deck)
    if len(wire)!=len(native) or len(wire)!=len(offers):return False
    for i,(x,y,ids) in enumerate(zip(wire,native,offers)):
        if (x.get('type')!='CARD' or x.get('cards')!=ids or y['type']!='CARD'
                or y['native_index']!=i or len(y['card_uuids'])!=len(ids)):return False
        for u in y['card_uuids']:
            u=str(UUID(u))
            if u in uuids:return False
            uuids.add(u)
    return True


def _vampires(left,right,a,b,button):
    if not (_room_context(a,b,'Vampires') and a['phase']==b['phase']=='EVENT'
            and a['roomPhase']==b['roomPhase']=='EVENT' and right.event is not None
            and (right.event.id,right.event.class_name,right.event.phase)==('Vampires','Vampires','1')
            and _controls(right.event)==[(0,True)] and left.potions==right.potions and _streams(a)==_streams(b)):
        return False
    hp=left.player.max_hp
    # ceil(0.3f * maxHealth), clamped by source for 1/2 max HP.
    cost=min(hp-1,(3*hp+9)//10)
    has_vial=any(r.id=='Blood Vial' for r in left.relics)
    if _controls(left.event)!=list(enumerate([True]*(3 if has_vial else 2))):return False
    if button==0:
        expected=left.player.model_copy(update={'max_hp':hp-cost,'current_hp':min(hp-cost,left.player.current_hp)})
        relics=left.relics
    elif button==1 and has_vial:
        expected=left.player
        relics=[r for r in left.relics if r.id!='Blood Vial']
    else:return False
    choice=left.event.choices[button]
    if len(choice.outcomes)!=1 or choice.outcomes[0].probability!=1:return False
    effects=choice.outcomes[0].effects
    if (len(effects)!=2 or (effects[1].type,effects[1].card_id,effects[1].amount)!=('replace_starter_strikes','Bite',5)
            or (button==0 and (effects[0].type,effects[0].amount)!=('lose_max_hp',cost))
            or (button==1 and (effects[0].type,effects[0].relic_id)!=('lose_relic','Blood Vial'))):return False
    original=a['player']['masterDeck'];actual=b['player']['masterDeck']
    retained=[_card_facts(c) for c in original if c['id'] not in _STARTER_STRIKES]
    if right.player!=expected or right.relics!=relics or len(actual)!=len(retained)+5:
        return False
    if [_card_facts(c) for c in actual[:-5]]!=retained:return False
    upgraded=int(any(r.id=='Molten Egg 2' for r in relics))
    uuids={c['uuid'] for c in actual[-5:]}
    return (len(uuids)==5 and not uuids.intersection(c['uuid'] for c in original)
            and all((c['id'],c['type'],c['rarity'],c['target'],c['timesUpgraded'],c['upgraded'],c['bottled'],
                     c['cost'],c['costForTurn'],c['misc'],c['baseDamage'],c['baseBlock'],c['baseMagicNumber'],c['exhaust'])
                    ==('Bite','ATTACK','SPECIAL','ENEMY',upgraded,bool(upgraded),False,1,1,0,7+upgraded,-1,2+upgraded,False)
                    for c in actual[-5:]))


def _event_fight(before,after,a,b,name):
    """The event creates/activates its specific encounter, not a bare screen change."""
    left,right=before.state,after.state
    sphere=name=='MysteriousSphere'
    if (not _room_context(a,b,name) or a['phase']!='EVENT' or a['roomPhase']!='EVENT'
            or b['roomPhase']!='COMBAT' or left.deck!=right.deck
            or [r.id for r in left.relics]!=[r.id for r in right.relics]
            or [p.id for p in left.potions]!=[p.id for p in right.potions]
            or (left.player.max_hp,left.player.gold)!=(right.player.max_hp,right.player.gold)
            or any(a['runMode'].get(k) for k in ('is_trial','is_endless'))):
        return False
    receipt=b['combat_receipt']
    expected_count=2 if sphere else 1
    if (receipt['turn'],receipt['active_monsters'],receipt['battle_over'],receipt['player_dead'],
            receipt['played_card_uuids'],receipt['room_phase'],receipt['room_class'])!=(1,expected_count,False,False,[],'COMBAT','EventRoom'):
        return False
    streams=_streams(a)
    expected=dict(streams)
    hp=Stream(streams['monster_hp']);misc=Stream(streams['misc']);ai=Stream(streams['ai']);shuffle=Stream(streams['shuffle'])
    asc=a['ascension']
    if sphere:
        misc.bounded(51 if a['runMode']['is_daily'] else 11)
        # The event constructor already spawned both walkers. Each constructor
        # consumes temporary base HP and then the authoritative ascension HP.
        if a['combat_receipt']['active_monsters']!=2 or hp.counter!=4:return False
        for _ in range(4):hp.rewind_word()
        hp.counter-=4
        roster=[]
        for _ in range(2):
            hp.bounded(7)
            health=(92+hp.bounded(11)) if asc>=7 else (90+hp.bounded(7))
            roster.append(('Orb Walker',health,'ATTACK' if ai.bounded(100)<40 else 'ATTACK_DEBUFF'))
        if hp.state()!=streams['monster_hp']:return False
    else:
        identity=_shuffle(misc.long(),['TheGuardian','Hexaghost','SlimeBoss'])[0]
        health,intent={
            'TheGuardian':(250 if asc>=9 else 240,'DEFEND'),
            'Hexaghost':(264 if asc>=9 else 250,'UNKNOWN'),
            'SlimeBoss':(150 if asc>=9 else 140,'STRONG_DEBUFF'),
        }[identity]
        hp.bounded(1);ai.bounded(100)
        roster=[(identity,health,intent)]
    deck=_shuffle(shuffle.long(),left.deck)
    expected.update(monster_hp=hp.state(),misc=misc.state(),ai=ai.state(),shuffle=shuffle.state())
    ids={r.id for r in left.relics}
    toolbox=b['phase']=='CARD_PICK' and b['interaction'].get('card_reward',{}).get('source_action')=='ChooseOneColorless'
    if toolbox and not _toolbox_offers(left,b,expected):return False
    if not _opening_snecko_costs(left,right,expected,toolbox or b['phase']=='EVENT'):return False
    if expected!=_streams(b) or [(m.id,m.max_hp) for m in right.monsters]!=[(i,h) for i,h,_ in roster]:
        return False
    facts=lambda cards:[(c.uuid,c.id,c.upgrades,c.misc) for c in cards]
    if not sphere and b['phase']=='EVENT':
        # Pre-fix Mind Bloom was observable before turn-start actions.
        return (right.event is not None and right.event.phase=='FIGHT'
                and left.player==right.player and left.relics==right.relics
                and not right.hand and not right.discard_pile and not right.exhaust_pile
                and facts(right.draw_pile)==facts(sorted(deck,key=_opening_card))
                and all(m.current_hp==m.max_hp and m.intent=='NONE' for m in right.monsters))
    if b['phase']=='CARD_SELECT' and not after.native_combat_handoff:return False
    if b['phase'] not in ('PLAYER_TURN','CARD_SELECT') and not toolbox:return False
    if not toolbox:
        snapshot=HeadlessCombatBridge().observe(b,run_id=after.run_id,sequence=after.sequence).snapshot
        opening_damage=3 if 'Mercury Hourglass' in ids else 0
        if (snapshot is None or snapshot['turn']!=1 or snapshot['_headless_rng']!=receipt['rng']
                or [(m.current_hp,m.intent) for m in right.monsters]!=[(h-opening_damage,i) for _,h,i in roster]):
            return False
        if sphere and any(not any(x['power_id']=='Generic Strength Up Power' and x['amount']==(5 if asc>=17 else 3)
                                  for x in m['creature']['powers'])
                          for m in snapshot['cur_map_node_state']['monster_data']):
            return False
    heal=(2*('Blood Vial' in ids)+25*(not sphere and 'Pantograph' in ids)) if 'Mark of the Bloom' not in ids else 0
    if right.player.current_hp!=min(left.player.max_hp,left.player.current_hp+heal):return False
    if toolbox and [(m.current_hp,m.intent) for m in right.monsters]!=[(h,i) for _,h,i in roster]:
        return False
    return _opening_piles(left,right,deck,toolbox)


def _colosseum_slavers(before,after,a,b):
    left,right=before.state,after.state
    toolbox=b['phase']=='CARD_PICK' and b['interaction'].get('card_reward',{}).get('source_action')=='ChooseOneColorless'
    if (not _room_context(a,b,'Colosseum') or a['roomPhase']!='EVENT' or b['roomPhase']!='COMBAT'
            or (b['phase'] not in ('EVENT','PLAYER_TURN','CARD_SELECT') and not toolbox) or left.deck!=right.deck
            or [r.id for r in left.relics]!=[r.id for r in right.relics]
            or [p.id for p in left.potions]!=[p.id for p in right.potions]):
        return False
    receipt=b['combat_receipt']
    if (receipt['turn']!=1 or receipt['active_monsters']!=2 or receipt['battle_over'] or receipt['player_dead']
            or receipt['played_card_uuids'] or receipt['room_phase']!='COMBAT' or receipt['room_class']!='EventRoom'):
        return False
    streams=_streams(a);expected=dict(streams)
    hp=Stream(streams['monster_hp']);ai=Stream(streams['ai']);shuffle=Stream(streams['shuffle'])
    low=48 if a['ascension']>=7 else 46
    roster=[(identity,low+hp.bounded(5)) for identity in ('SlaverBlue','SlaverRed')]
    intents=['ATTACK' if ai.bounded(100)>=40 else 'ATTACK_DEBUFF']
    ai.bounded(100);intents.append('ATTACK')
    deck=_shuffle(shuffle.long(),left.deck)
    expected.update(monster_hp=hp.state(),ai=ai.state(),shuffle=shuffle.state())
    if toolbox and not _toolbox_offers(left,b,expected):return False
    if not _opening_snecko_costs(left,right,expected,toolbox or b['phase']=='EVENT'):return False
    if expected!=_streams(b) or [(m.id,m.max_hp) for m in right.monsters]!=roster:
        return False
    if (right.player.max_hp,right.player.gold)!=(left.player.max_hp,left.player.gold):return False
    deck.sort(key=_opening_card)
    facts=lambda cards:[(c.uuid,c.id,c.upgrades,c.misc) for c in cards]
    if b['phase']=='EVENT':
        # Old archives stop on a stale event flag before turn-start actions.
        # This is evidence of fight entry, not a runnable next decision.
        return (right.event is not None and right.event.phase=='POST_COMBAT'
                and left.player==right.player and left.relics==right.relics and not right.hand
                and not right.discard_pile and not right.exhaust_pile and facts(right.draw_pile)==facts(deck)
                and all(m.current_hp==m.max_hp and m.intent=='NONE' for m in right.monsters))
    if b['phase']=='CARD_SELECT' and not after.native_combat_handoff:return False
    ids={r.id for r in left.relics}
    damage=3 if not toolbox and 'Mercury Hourglass' in ids else 0
    if [(m.current_hp,m.intent) for m in right.monsters]!=[(hp-damage,intent) for (_,hp),intent in zip(roster,intents)]:
        return False
    if not toolbox:
        snapshot=HeadlessCombatBridge().observe(b,run_id=after.run_id,sequence=after.sequence).snapshot
        if snapshot is None or snapshot['turn']!=1 or snapshot['_headless_rng']!=receipt['rng']:return False
    healed=2 if 'Blood Vial' in ids and 'Mark of the Bloom' not in ids else 0
    if right.player.current_hp!=min(left.player.max_hp,left.player.current_hp+healed):return False
    return b['phase']=='CARD_SELECT' or _opening_piles(left,right,deck,toolbox)


def _colosseum_wave_deck(before,after,forecast,played):
    if played!='LessonLearned':return before==after
    if not isinstance(forecast,list) or len(forecast)!=len(before) or len(before)!=len(after):return False
    changed=0
    stable=('index','uuid','id','type','rarity','target','misc','bottled','exhaust')
    for old,new,card in zip(before,after,forecast):
        if (new['id'],new['timesUpgraded'],new['cost'],new['costForTurn'])!=(card['id'],card['upgrades'],card['cost'],card['turn_cost']):
            return False
        if old==new:continue
        if (not old['canUpgrade'] or new['timesUpgraded']!=old['timesUpgraded']+1 or new['upgraded'] is not True
                or any(old[k]!=new[k] for k in stable)):
            return False
        changed+=1
    return changed==int(any(c['canUpgrade'] for c in before))


def colosseum_slavers_return(a,b,predicted,played=None):
    """The first wave wins the fight, not the room: vanilla reopens its dialog."""
    view=b.get('interaction',{}).get('event') or {}
    receipt=b['combat_receipt']
    forecast=predicted['expected_after']
    relics={r['id'] for r in a['player']['relics']}
    heal=12 if 'Black Blood' in relics else 6 if 'Burning Blood' in relics else 0
    if 'Magic Flower' in relics:heal=heal*3//2
    if 'Mark of the Bloom' in relics:heal=0
    hp=min(forecast['max_hp'],forecast['hp']+heal)
    if (not _room_context(a,b,'Colosseum') or a['roomPhase']!='COMBAT'
            or b['phase']!='EVENT' or b['roomPhase']!='EVENT'
            or (view.get('id'),view.get('class_name'),view.get('phase'))!=('Colosseum','Colosseum','POST_COMBAT')
            or [(x['button_index'],x['enabled']) for x in view['choices']]!=[(0,True),(1,True)]
            or [x['creature']['id'] for x in a['combat']['snapshot']['cur_map_node_state']['monster_data']]!=['SlaverBlue','SlaverRed']
            or receipt['active_monsters']!=0 or receipt['player_dead'] or receipt['battle_over']
            or any(x['hp']!=0 for x in forecast['monsters'])
            or b['player']['hp']!=hp or forecast['hp']<=0 or b['player']['maxHp']!=forecast['max_hp']
            or not _colosseum_wave_deck(a['player']['masterDeck'],b['player']['masterDeck'],predicted.get('expected_master_deck'),played)
            or b.get('rewards')):
        return False
    expected={**predicted['unmodelled_rng'],**predicted['expected_exact_rng']}
    actual=_streams(b)
    shuffle=Stream(expected['shuffle']);shuffle.long()  # Colosseum.reopen -> preBattlePrep
    expected['shuffle']=shuffle.state()
    # AbstractRoom rolls a potion even though the first wave has no rewards UI.
    # Older exports lack the potion chance modifier. For those only, accept
    # either fully predicted RNG branch, never an arbitrary counter jump.
    potion=Stream(expected['potion']);roll=potion.bounded(100); miss=potion.state()
    role=a['player']['className']
    if role not in {'IRONCLAD','THE_SILENT','DEFECT','WATCHER'}:return False
    rarity=[0,1,2]+[0]*16+[1]*8+[2]*6   # per-class prefix then vanilla PotionHelper order
    tier_roll=potion.bounded(100); tier=0 if tier_roll<65 else 1 if tier_roll<90 else 2
    for _ in range(256):
        if rarity[potion.bounded(len(rarity))]==tier:break
    else:return False
    hit=potion.state()
    mod=a['interaction'].get('colosseum_potion_modifier')
    has_beast=any(r['id']=='White Beast Statue' for r in a['player']['relics'])
    if mod is None and not has_beast:
        if actual['potion'] not in (miss,hit):return False
        expected['potion']=actual['potion']
    else:
        won=roll<(100 if has_beast else 40+mod)
        expected['potion']=hit if won else miss
        if mod is not None and b['interaction'].get('colosseum_potion_modifier')!=mod+(-10 if won else 10):return False
    return expected==actual


def _skull_blocked_potion(left,right,a,b):
    if not (_room_context(a,b,'KnowingSkull') and a['phase']==b['phase']=='EVENT'
            and a['roomPhase']==b['roomPhase']=='EVENT' and right.event is not None
            and (right.event.id,right.event.class_name,right.event.phase)==('Knowing Skull','KnowingSkull','ASK')
            and _controls(left.event)==_controls(right.event)==[(i,True) for i in range(4)]
            and left.deck==right.deck and left.relics==right.relics and left.potions==right.potions
            and not any(a['runMode'].get(k) for k in ('is_daily','is_trial','is_endless'))):
        return False
    before,after=(e.choices[0] for e in (left.event,right.event))
    if not all(len(x.outcomes)==1 and x.outcomes[0].probability==1 for x in (before,after)):
        return False
    old,new=before.outcomes[0].effects,after.outcomes[0].effects
    if (len(old)!=2 or len(new)!=2 or (old[0].type,old[1].type)!=('lose_hp','random_potion')
            or type(old[0].amount) is not int or old[0].amount<=0
            or new[0]!=old[0].model_copy(update={'amount':old[0].amount+1}) or old[1]!=new[1]
            or left.event.choices[1:]!=right.event.choices[1:]):
        return False
    ids={r.id for r in left.relics}
    loss=max(0,old[0].amount-int('TungstenRod' in ids))
    if left.player.current_hp<=loss:return False
    streams=_streams(a)
    if 'Sozu' not in ids:
        if not left.potions or any(p.is_empty for p in left.potions):return False
        # PotionHelper initializes three class-specific and thirty shared
        # potions for a standard run. It draws before checking slot capacity.
        potion=Stream(streams['potion']); potion.bounded(33)
        streams=dict(streams,potion=potion.state())
    return _streams(b)==streams and right.player==left.player.model_copy(
        update={'current_hp':left.player.current_hp-loss})


def _skull_fatal_gold(left,right,a,b):
    if not (_room_context(a,b,'KnowingSkull') and a['roomPhase']==b['roomPhase']=='EVENT'
            and a['phase']=='EVENT' and b['phase']=='GAME_OVER' and right.game_over_reason=='defeat'
            and left.deck==right.deck and left.relics==right.relics and left.potions==right.potions
            and _streams(a)==_streams(b)):
        return False
    choice=left.event.choices[1]
    if len(choice.outcomes)!=1 or choice.outcomes[0].probability!=1:return False
    effects=choice.outcomes[0].effects
    if len(effects)!=2 or (effects[0].type,effects[1].type,effects[1].amount)!=('lose_hp','gain_gold',90):return False
    ids={r.id for r in left.relics}
    if left.player.current_hp>max(0,effects[0].amount-int('TungstenRod' in ids)):return False
    if 'Mark of the Bloom' not in ids and (
            any(p.id=='FairyPotion' for p in left.potions)
            or any(r.id=='Lizard Tail' and r.counter==-1 for r in left.relics)):
        return False
    # KnowingSkull.obtainReward grants gold synchronously even after the HP
    # action created the death screen; do not apply this rule to other events.
    if 'Bloody Idol' in ids and 'Ectoplasm' not in ids:return False
    return right.player==left.player.model_copy(update={'current_hp':0,
             'gold':left.player.gold+(0 if 'Ectoplasm' in ids else 90)})


def designer_purge_receipt(before,after,targets):
    """Designer full service removes the selected card, then upgrades via misc RNG."""
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    menu=a['interaction']
    if not (a['phase']=='CARD_SELECT' and menu.get('flow',{}).get('event_class')=='Designer'
            and menu.get('selection',{}).get('purpose')=='purge'):return None
    try:
        left,right=before.state,after.state;selection=menu['selection']
        if (not _room_context(a,b,'Designer') or a['roomPhase']!=b['roomPhase'] or a['roomPhase']!='EVENT'
                or b['phase']!='EVENT' or menu['designer']['phase']!='DONE' or b['interaction']['designer']['phase']!='DONE'
                or right.event is None or right.event.phase!='DONE' or _controls(right.event)!=[(0,True)]
                or left.relics!=right.relics or left.potions!=right.potions
                or len(targets)!=1 or selection['count']!=1 or selection['locked'] is not True
                or selection['preview_uuid']!=targets[0] or selection['can_confirm'] is not True
                or targets[0] not in {c['uuid'] for c in a['cardSelection']['options']}):
            return False
        deck=a['player']['masterDeck'];uid=targets[0]
        card=next(c for c in deck if c['uuid']==uid)
        if card['bottled'] or card['id'] in {'AscendersBane','CurseOfTheBell','Necronomicurse'}:return False
        player=left.player
        if card['id']=='Parasite':
            maximum=max(1,player.max_hp-3)
            player=player.model_copy(update={'max_hp':maximum,'current_hp':min(maximum,player.current_hp)})
        if right.player!=player:return False
        original=[c for c in deck if c['uuid']!=uid];actual=b['player']['masterDeck']
        if len(original)!=len(actual):return False
        streams,final=_streams(a),_streams(b)
        # Legacy diagnostics retained the locked preview and full RNG, but
        # lacked the private option enum. The two exact RNG branches disambiguate.
        option=selection.get('designer_option','REMOVE' if streams==final else 'REMOVE_AND_UPGRADE')
        if option not in ('REMOVE','REMOVE_AND_UPGRADE'):return False
        expected=dict(streams);upgraded=None
        if option=='REMOVE_AND_UPGRADE':
            rng=Stream(streams['misc'])
            upgrades=_shuffle(rng.long(),[c for c in original if c['canUpgrade']])
            expected['misc']=rng.state()
            upgraded=upgrades[0]['uuid'] if upgrades else None
        if expected!=final:return False
        upgrade_fields={'name','baseDamage','baseBlock','baseMagicNumber','damage','block','magicNumber',
                        'upgraded','timesUpgraded','canUpgrade','cost','costForTurn','exhaust'}
        for old,new in zip(original,actual):
            old,new=_card_facts(old),_card_facts(new)
            if old['uuid']!=upgraded:
                if old!=new:return False
            elif (new['timesUpgraded']!=old['timesUpgraded']+1 or new['upgraded'] is not True
                    or any(old[k]!=new[k] for k in old.keys()-upgrade_fields)):
                return False
        return True
    except (KeyError,IndexError,AttributeError,TypeError,ValueError,StopIteration):return False


def library_grid(raw):
    menu=raw['interaction'];selection=menu.get('selection',{});grid=raw.get('cardSelection',{})
    return (raw['phase']=='CARD_SELECT' and raw['room']=='EventRoom' and raw['roomPhase']=='EVENT'
            and menu.get('flow',{}).get('event_class')=='TheLibrary'
            and selection.get('source_event_class','TheLibrary')=='TheLibrary'
            and selection.get('source_event_phase','1')=='1'
            and selection['kind']==grid.get('kind')=='grid' and selection['count']==1
            and selection['locked'] is False and selection['can_confirm'] is False
            and selection['any_number'] is False and selection['can_pick_zero'] is False
            and selection['auto_confirm'] is True and not grid['selected']
            and len({x['id'] for x in grid['options']})==len(grid['options'])==20)


def _library_offer_ids(rng,pools,offset,rare):
    def draw():
        roll=rng.bounded(100)+offset
        pool=pools['rare' if roll<rare else 'uncommon' if roll<rare+37 else 'common']
        return pool[rng.bounded(len(pool))]
    cards=[]
    for _ in range(20):
        card=draw()
        for _ in range(128):
            duplicate=False
            # Source can reroll more than once per scan, and still repeats
            # the scan if a newly drawn card equals an earlier entry.
            for other in cards:
                if card==other:
                    duplicate=True
                    card=draw()
            if not duplicate:break
        else:raise ValueError('Library draft did not settle')
        cards.insert(0,card)
    return cards


def _library_open(left,right,a,b):
    if (not _room_context(a,b,'TheLibrary') or not library_grid(b)
            or a['phase']!='EVENT' or a['roomPhase']!='EVENT'
            or left.deck!=right.deck or left.player!=right.player
            or left.relics!=right.relics or left.potions!=right.potions):return False
    old=_streams(a);final=_streams(b)
    options=b['cardSelection']['options']
    want=[x['id'] for x in options];uuids={x['uuid'] for x in options}
    if len(uuids)!=20 or uuids.intersection(c.uuid for c in left.deck):return False
    if any(c['upgraded']!=(int(c['timesUpgraded'])==1)
            or c['timesUpgraded']!=_egg_upgrade(left,c['type'])
            or c['bottled'] for c in options):return False
    draft=a['interaction'].get('library_draft')
    if draft is None:
        # Old Defect archive: fixed catalog order recorded by the same
        # frozen profile in combat, but did not export the current rare offset.
        if left.character!='DEFECT':return False
        pools={tier:order.split('|')[::-1] for tier,order in _DEFECT_SRC.items()}
        offsets=range(-40,6)
    else:
        pools={tier:draft[tier] for tier in ('common','uncommon','rare')}
        offset=draft['offset']
        if (type(offset) is not int or not -40<=offset<=5
                or any(not pool or len(pool)!=len(set(pool)) for pool in pools.values())):return False
        offsets=(offset,)
    rare=9 if any(r.id=="Nloth's Gift" for r in left.relics) else 3
    for offset in offsets:
        rng=Stream(old['card'])
        if _library_offer_ids(rng,pools,offset,rare)==want and dict(old,card=rng.state())==final:
            return True
    return False


def _egg_upgrade(state,card_type):
    eggs={'ATTACK':'Molten Egg 2','SKILL':'Toxic Egg 2','POWER':'Frozen Egg 2'}
    return int(any(r.id==eggs.get(card_type) for r in state.relics))


def _blacksmith_rummage(left,right,a,b):
    event=right.event
    if (not _room_context(a,b,'AccursedBlacksmith') or a['phase']!=b['phase'] or b['phase']!='EVENT'
            or a['roomPhase']!=b['roomPhase'] or b['roomPhase']!='EVENT' or event is None
            or (event.id,event.class_name,event.phase)!=('Accursed Blacksmith','AccursedBlacksmith','2')
            or _controls(event)!=[(0,True)] or _streams(a)!=_streams(b) or left.potions!=right.potions
            or any(a['runMode'].get(k) for k in ('is_daily','is_trial','is_endless'))):
        return False
    relics=list(left.relics)
    ids={r.id for r in relics}
    if 'WarpedTongs' in ids or not right.relics or (right.relics[-1].id,right.relics[-1].counter)!=('WarpedTongs',-1):
        return False
    raw_old,raw_new=a['player']['masterDeck'],b['player']['masterDeck']
    hp,max_hp,gold=left.player.current_hp,left.player.max_hp,left.player.gold
    ward=next((i for i,r in enumerate(relics) if r.id=='Omamori' and r.counter>0),None)
    if ward is not None:
        relics[ward]=relics[ward].model_copy(update={'counter':relics[ward].counter-1})
        if raw_old!=raw_new:return False
    else:
        if (len(raw_new)!=len(raw_old)+1 or raw_new[:-1]!=raw_old
                or raw_new[-1]['uuid'] in {c['uuid'] for c in raw_old}
                or not right.deck or (right.deck[-1].id,right.deck[-1].type,right.deck[-1].upgrades,
                                      right.deck[-1].is_bottled)!=('Pain','CURSE',0,False)):
            return False
        for relic in relics:  # The curse's pickup hooks run in inventory order.
            if relic.id=='Darkstone Periapt':
                max_hp+=6
                if 'Mark of the Bloom' not in ids:hp+=6
            if relic.id=='CeramicFish' and 'Ectoplasm' not in ids:
                gold+=9
                if 'Bloody Idol' in ids and 'Mark of the Bloom' not in ids:hp=min(max_hp,hp+5)
    return (right.relics[:-1]==relics and right.player==left.player.model_copy(
        update={'current_hp':hp,'max_hp':max_hp,'gold':gold}))


def _mushrooms_eat(left,right,a,b):
    if (not _room_context(a,b,'Mushrooms') or (a['roomPhase'],b['roomPhase'])!=('EVENT','EVENT')
            or b['phase']!='EVENT' or right.event is None or right.event.phase!='1'
            or _controls(right.event)!=[(0,True)] or _streams(a)!=_streams(b)
            or left.potions!=right.potions or any(a['runMode'].get(k) for k in ('is_daily','is_trial','is_endless'))):
        return False
    relics=list(left.relics)
    ids={r.id for r in relics}
    ward=next((i for i,r in enumerate(relics) if r.id=='Omamori' and r.counter>0),None)
    max_hp,hp,gold=left.player.max_hp,left.player.current_hp,left.player.gold
    healing='Mark of the Bloom' not in ids
    if healing:
        hp=min(max_hp,hp+max_hp//4)
    if ward is not None:
        relics[ward]=relics[ward].model_copy(update={'counter':relics[ward].counter-1})
        if left.deck!=right.deck:return False
    else:
        if (len(right.deck)!=len(left.deck)+1 or right.deck[:-1]!=left.deck
                or right.deck[-1].uuid in {c.uuid for c in left.deck}
                or (right.deck[-1].id,right.deck[-1].type,right.deck[-1].upgrades,right.deck[-1].is_bottled)!=('Parasite','CURSE',0,False)):
            return False
        for relic in relics:  # onObtainCard fires in relic inventory order.
            if relic.id=='Darkstone Periapt':
                max_hp+=6
                hp+=6*healing
            if relic.id=='CeramicFish' and 'Ectoplasm' not in ids:
                gold+=9
                if healing and 'Bloody Idol' in ids:hp=min(max_hp,hp+5)
    return right.relics==relics and right.player==left.player.model_copy(
        update={'current_hp':hp,'max_hp':max_hp,'gold':gold})


def library_selection_receipt(before,after,target):
    try:
        a,b=json.loads(before.raw_json),json.loads(after.raw_json)
        if (not library_grid(a) or not _room_context(a,b,'TheLibrary') or b['phase']!='EVENT'
                or b['roomPhase']!='EVENT' or _streams(a)!=_streams(b)):return False
        left,right=before.state,after.state
        player=left.player
        relic_ids={r.id for r in left.relics}
        if 'CeramicFish' in relic_ids and 'Ectoplasm' not in relic_ids:
            updates={'gold':player.gold+9}
            if 'Bloody Idol' in relic_ids and 'Mark of the Bloom' not in relic_ids:
                updates['current_hp']=min(player.max_hp,player.current_hp+5)
            player=player.model_copy(update=updates)
        if (right.event is None or right.event.phase!='1' or _controls(right.event)!=[(0,True)]
                or player!=right.player or left.relics!=right.relics or left.potions!=right.potions):return False
        chosen=next(c for c in a['cardSelection']['options'] if c['uuid']==target)
        old,new=a['player']['masterDeck'],b['player']['masterDeck']
        return (len(new)==len(old)+1 and new[:-1]==old and new[-1]['uuid'] not in {c['uuid'] for c in old}
                and new[-1]['uuid'] not in {c['uuid'] for c in a['cardSelection']['options']}
                and new[-1]['timesUpgraded']==_egg_upgrade(left,chosen['type'])
                and not new[-1]['bottled'] and new[-1]['id']==chosen['id'])
    except (KeyError,IndexError,AttributeError,TypeError,ValueError,StopIteration):return False


def _golden_idol_take(left,right,a,b):
    # Taking the idol executes immediately; trap costs belong to later clicks.
    event=right.event
    return (_room_context(a,b,'GoldenIdolEvent') and a['phase']==b['phase']=='EVENT'
            and a['roomPhase']==b['roomPhase']=='EVENT' and event is not None
            and (event.id,event.class_name,event.phase)==('Golden Idol','GoldenIdolEvent','1')
            and _controls(event)==[(0,True),(1,True),(2,True)]
            and left.player==right.player and left.deck==right.deck and left.potions==right.potions
            and left.keys==right.keys and not any(r.id=='Golden Idol' for r in left.relics)
            and a['player']['masterDeck']==b['player']['masterDeck']
            and b['player']['relics']==a['player']['relics']+[{'id':'Golden Idol','counter':-1,'name':b['player']['relics'][-1]['name']}]
            and _streams(a)==_streams(b))

def _verify(before,after,button):
    left,right=before.state,after.state;e=left.event
    key=(e.id,e.class_name,e.phase,button)
    end=right.event.phase if right.event else None
    golden=key==('Golden Idol','GoldenIdolEvent','0',0)
    simple=key in _DIALOG
    designer=key==('Designer','Designer','INTRO',0)
    dead=key==('Dead Adventurer','DeadAdventurer','INTRO',0)
    altar=key==('Forgotten Altar','ForgottenAltar','0',2) and any(r.id=='Darkstone Periapt' for r in left.relics)
    coin=key==('Neow','NeowEvent','3',2) and any(r.id=='Old Coin' for r in right.relics) and not any(r.id=='Old Coin' for r in left.relics)
    bandits=key==('Masked Bandits','MaskedBandits','INTRO',1) and any(r.id in ('Blood Vial','Toolbox') for r in left.relics)
    house=key==('Neow','NeowEvent','3',3) and left.character in ('THE_SILENT','IRONCLAD') and any(r.id=='Tiny House' for r in right.relics)
    amp=key[:3]==('Vampires','Vampires','0') and (button==0 or button==1 and len(e.choices)==3)
    colosseum=key==('Colosseum','Colosseum','FIGHT',0)
    skull=key==('Knowing Skull','KnowingSkull','ASK',1) and right.screen_type=='GAME_OVER'
    skull_potion=(key==('Knowing Skull','KnowingSkull','ASK',0) and right.event is not None
                  and (any(r.id=='Sozu' for r in left.relics)
                       or left.potions and all(not p.is_empty for p in left.potions)))
    library=key==('The Library','TheLibrary','0',0)
    falling=key[:3]==('Falling','Falling','CHOICE')
    sensory=key[:3]==('SensoryStone','SensoryStone','INTRO_2')
    fountain=key==('Fountain of Cleansing','FountainOfCurseRemoval','0',0)
    tomb=key==('Tomb of Lord Red Mask','TombRedMask','INTRO',0) and any(r.id=='Red Mask' for r in left.relics)
    joust=key[:2]==('The Joust','TheJoust') and e.phase in ('EXPLANATION','PRE_JOUST','JOUST')
    mushrooms=key==('Mushrooms','Mushrooms','0',1)
    blacksmith=key==('Accursed Blacksmith','AccursedBlacksmith','0',1)
    event_fight=key in {('Mysterious Sphere','MysteriousSphere','PRE_COMBAT',0),('MindBloom','MindBloom','INTRO',0)}
    if not (golden or simple or designer or dead or altar or coin or bandits or house or amp or colosseum
            or skull or skull_potion or library or falling or sensory or fountain or tomb or joust
            or mushrooms or blacksmith or event_fight):return None
    a,b=json.loads(before.raw_json),json.loads(after.raw_json)
    if golden:return _golden_idol_take(left,right,a,b)
    if mushrooms:return _mushrooms_eat(left,right,a,b)
    if blacksmith:return _blacksmith_rummage(left,right,a,b)
    if fountain:return _fountain(left,right,a,b)
    if joust:return _joust(left,right,a,b,button)
    if event_fight:return _event_fight(before,after,a,b,e.class_name)
    if amp:return _vampires(left,right,a,b,button)
    if colosseum:return _colosseum_slavers(before,after,a,b)
    if skull:return _skull_fatal_gold(left,right,a,b)
    if skull_potion:return _skull_blocked_potion(left,right,a,b)
    if library:return _library_open(left,right,a,b)
    if falling:return _falling_choice(left,right,a,b,button)
    if sensory:return _sensory_stone(left,right,a,b,button)
    if designer and 'designer' not in a['interaction'] and a['interaction']['event']['phase']=='INTRO':
        # Already-semantic third-party menu: honor its explicit transition
        # contract, rather than treating it as the legacy screenNum adapter.
        return None
    if bandits:return _bandits(before,after,a,b)
    if house:return _tiny_house(left,right,a,b)
    if dead:
        risk=e.choices[0].outcomes[0].probability
        if not 0<risk<1:return False
        ambush=Stream(_streams(a)['misc']).bounded(100)<round(risk*100)
        last=risk==(.85 if a['ascension']>=15 else .75)
        if end!=('FAIL' if ambush else 'SUCCESS' if last else 'INTRO'):return False
        if ambush or last:return None  # separate encounter / final-result verifier
    if (a['room']!=b['room'] or a['room']!=('NeowRoom' if coin else 'EventRoom')
            or a['roomPhase']!='EVENT' or b['roomPhase']!='EVENT' or a['phase']!=b['phase']
            or left.keys!=right.keys or not right.event or (right.event.id,right.event.class_name)!=(e.id,e.class_name)
            or any(a.get(k)!=b.get(k) for k in ('seed','map','history','ascension','runMode'))
            or any(r.get('interaction',{}).get('flow',{}).get('event_class')!=e.class_name for r in (a,b))):return False
    source=_streams(a)
    if dead:return _dead_search(left,right,a,b,source)
    if source!=_streams(b):return False
    if simple or designer:
        if designer:
            deck=left.deck
            eligible=sum(not c.is_bottled for c in deck)
            if len(right.event.choices)!=4:return False
            remove=right.event.choices[1].kind=='REMOVE_CARD'
            costs=(50,75,110) if a['ascension']>=15 else (40,60,90)
            controls=list(enumerate([left.player.gold>=costs[0] and any(c.can_upgrade for c in deck),
                       left.player.gold>=costs[1] and eligible>=(1 if remove else 2),
                       left.player.gold>=costs[2] and eligible>=1, True]))
        elif e.class_name=='Falling':
            available=[any(c.type==kind and not c.is_bottled for c in left.deck) for kind in ('SKILL','POWER','ATTACK')]
            controls=list(enumerate(available)) if any(available) else [(0,True)]
        elif e.class_name=='SensoryStone':controls=[(0,True),(1,True),(2,True)]
        elif e.class_name=='WindingHalls':controls=[(0,True),(1,True),(2,True)]
        elif e.class_name=='TheJoust':controls=[(0,True),(1,True)]
        else:controls=[(0,True)]
        return (end==('MAIN' if designer else _DIALOG[key]) and _controls(right.event)==controls
                and left.player==right.player and left.deck==right.deck
                and left.relics==right.relics and left.potions==right.potions)
    ids={r.id for r in left.relics}
    if tomb:
        gold=0 if 'Ectoplasm' in ids else 222
        heal=5 if gold and 'Bloody Idol' in ids and 'Mark of the Bloom' not in ids else 0
        player=left.player.model_copy(update={'gold':left.player.gold+gold,
            'current_hp':min(left.player.max_hp,left.player.current_hp+heal)})
        return (end=='RESULT' and _controls(right.event)==[(0,True)] and player==right.player
                and left.deck==right.deck and left.relics==right.relics and left.potions==right.potions)
    if altar:
        if 'Omamori' in ids and next(r for r in left.relics if r.id=='Omamori').counter>0:return False
        if a.get('runMode',{}).get('is_daily') or a.get('runMode',{}).get('is_trial'):return False
        expected=left.player.model_copy(update={'max_hp':left.player.max_hp+6,
                      'current_hp':left.player.current_hp+(0 if 'Mark of the Bloom' in ids else 6)})
        added=[c for c in right.deck if c.uuid not in {x.uuid for x in left.deck}]
        return (end=='99' and _controls(right.event)==[(0,True)] and left.potions==right.potions and left.relics==right.relics
                and expected==right.player and len(added)==1 and right.deck==[*left.deck,added[0]]
                and added[0].id=='Decay' and added[0].type=='CURSE' and added[0].upgrades==0 and not added[0].is_bottled)
    if coin:
        option=next(c for c in e.choices if c.button_index==button)
        if len(option.outcomes)!=1:return False
        outcome=option.outcomes[0]
        effects=outcome.effects
        if (outcome.probability!=1 or len(effects)!=2 or effects[0].type!='lose_max_hp' or effects[0].amount is None
                or effects[0].amount<=0 or effects[1].type!='random_relic' or effects[1].pool!='RARE'):return False
        maximum=max(1,left.player.max_hp-effects[0].amount)
        heal=5 if 'Bloody Idol' in ids and 'Ectoplasm' not in ids and 'Mark of the Bloom' not in ids else 0
        player=left.player.model_copy(update={'max_hp':maximum,'current_hp':min(maximum,left.player.current_hp+heal),
                    'gold':left.player.gold+(0 if 'Ectoplasm' in ids else 300)})
        return (end=='99' and _controls(right.event)==[(0,True)] and right.player==player and right.deck==left.deck
                and right.potions==left.potions and len(right.relics)==len(left.relics)+1 and right.relics[:-1]==left.relics
                and right.relics[-1].id=='Old Coin' and right.relics[-1].counter==-1)


def event_receipt(before,after,button):
    """None delegates unhandled branches; malformed evidence for owned branches fails closed."""
    try:return _verify(before,after,button)
    except (KeyError,IndexError,AttributeError,TypeError,ValueError,StopIteration):return False


def _astrolabe_targets(a,index):
    sel=a.get('interaction',{}).get('selection') or {}
    if (a.get('phase'),a.get('room'),a.get('roomPhase'),a.get('act'),a.get('floor'))!=('CARD_SELECT','NeowRoom','EVENT',1,0):
        return []
    if (sel.get('kind'),sel.get('purpose'),sel.get('count'),sel.get('auto_confirm'))!=('grid','transform',3,True):
        return []
    if not any(r['id']=='Astrolabe' for r in a['player']['relics']):return []
    grid=a['cardSelection']; cards=grid['selected']; options=grid['options']
    if len(cards)!=2 or not 0<=index<len(options):return []
    targets=[*cards,options[index]]
    return targets if len({c['uuid'] for c in targets})==3 else []


def _astrolabe_landing(a,b):
    event=b.get('interaction',{}).get('event') or {}
    return ((b['phase'],b['room'],b['roomPhase'],event.get('id'),event.get('class_name'),event.get('phase'))==
            ('NEOW','NeowRoom','EVENT','Neow','NeowEvent','99')
            and [(x['button_index'],x['enabled']) for x in event['choices']]==[(0,True)]
            and all(a[k]==b[k] for k in ('seed','floor','act','ascension','runMode','map','history','observedKeys','firstRoomChosen'))
            and not b.get('rewards') and not b.get('cardSelection'))


def neow_cage_receipt(a,b,index):
    sel=a.get('interaction',{}).get('selection') or {}
    old=a['player']
    if ((a['phase'],a['room'],a['roomPhase'],a['act'],a['floor'])!=('CARD_SELECT','NeowRoom','INCOMPLETE',1,0)
            or (sel.get('kind'),sel.get('purpose'),sel.get('count'),sel.get('auto_confirm'))!=('grid','purge',2,True)
            or [r['id'] for r in old['relics']]!=['Empty Cage']):return None
    grid=a['cardSelection'];options=grid['options'];selected=grid['selected']
    if len(selected)!=1 or not 0<=index<len(options) or options[index]['uuid']==selected[0]['uuid']:
        return None
    if b['phase']=='CARD_SELECT':return None
    picked={selected[0]['uuid'],options[index]['uuid']}
    new=b['player'];original=old['masterDeck']
    return (_astrolabe_landing(a,b) and _streams(a)==_streams(b)
            and picked<={c['uuid'] for c in original if not c['bottled'] and c['id'] not in
                          ('AscendersBane','CurseOfTheBell','Necronomicurse')}
            and [_card_facts(c) for c in new['masterDeck']]
                ==[_card_facts(c) for c in original if c['uuid'] not in picked]
            and {k:v for k,v in old.items() if k!='masterDeck'}=={k:v for k,v in new.items() if k!='masterDeck'})


def astrolabe_selection_receipt(a,b,index):
    """None delegates ordinary grids; the source-owned Astrolabe must consume three."""
    facts=a.get('interaction',{}).get('astrolabe')
    if facts is None:return None
    try:
        targets=_astrolabe_targets(a,index)
        if not targets or not _astrolabe_landing(a,b):return False
        if any(a['player'][key]!=b['player'][key] for key in ('hp','maxHp','gold','relics','potions')):return False
        pools,colors=facts['catalog'],facts['colors']
        roles={'IRONCLAD':'RED','THE_SILENT':'GREEN','DEFECT':'BLUE','WATCHER':'PURPLE'}
        streams=_streams(a);misc=Stream(streams['misc'])
        expected=[]
        for original in targets:
            color=colors[original['uuid']]
            if (color=='CURSE')!=(original['type']=='CURSE') or color not in ('COLORLESS','CURSE',roles[a['player']['className']]):
                return False
            catalog=pools[color.lower()] if color in ('COLORLESS','CURSE') else pools['common']+pools['uncommon']+pools['rare']
            eligible=[identity for identity in catalog if identity!=original['id']]
            if not eligible or len(set(eligible))!=len(eligible):return False
            expected.append((eligible[misc.bounded(len(eligible))],int(color!='CURSE')))
        if _streams(b)!=dict(streams,misc=misc.state()):return False
        old=a['player']['masterDeck'];new=b['player']['masterDeck']
        removed={c['uuid'] for c in targets};seen={c['uuid'] for c in old}
        if not removed<=seen or not all(c['uuid'] in seen for c in targets):return False
        retained=[_card_facts(c) for c in old if c['uuid'] not in removed]
        additions=new[len(retained):]
        added={c['uuid'] for c in additions}
        return ([_card_facts(c) for c in new[:len(retained)]]==retained and len(added)==3 and not added&seen
                and [(c['id'],c['timesUpgraded']) for c in additions]==expected
                and all(not c['bottled'] and c['upgraded']==bool(c['timesUpgraded']) and c['misc']==0 for c in additions))
    except (KeyError,IndexError,TypeError,ValueError):return False


def verify_followup29_landing(matcher,diag,state,*,before):
    """Typed bindings for the two archived stops that preceded actual settlement."""
    try:
        source=diag['before']['state'];old=diag['after']['state']
        action,args=diag['method'],diag['args']
        view=source.get('interaction',{}).get('event') or {}
        if (action,args,view.get('class_name'),view.get('phase'))==('chooseDialogOption',[0],'MindBloom','INTRO'):
            if old['phase']!='EVENT' or state['phase']!='PLAYER_TURN' or _streams(old)!=_streams(state):
                return False
            if not _room_context(source,old,'MindBloom') or not _room_context(before,state,'MindBloom'):return False
            from .interaction import IroncladInteractionBridge
            def check(a,b,run):
                bridge=IroncladInteractionBridge(characters=(a['player']['className'],))
                return _event_fight(bridge.observe(a,run_id=run,sequence=1),bridge.observe(b,run_id=run,sequence=2),a,b,'MindBloom')
            if not check(source,old,'mind-bloom-archive') or not check(before,state,'mind-bloom-live'):return False
            matcher.compare(old['player']['masterDeck'],state['player']['masterDeck'],'mind-bloom/masterDeck')
            matcher.compare(old['map'],state['map'],'mind-bloom/map')
            return True
        if action=='chooseGridCard' and len(args)==1 and type(args[0])==int:
            original=_astrolabe_targets(source,args[0]);actual=_astrolabe_targets(before,args[0])
            if not original or not actual or not _astrolabe_landing(source,old) or _streams(source)!=_streams(old):
                return False
            if source['player']['masterDeck']!=old['player']['masterDeck']:return False
            for x,y in zip(original,actual):
                matcher.compare({'uuid':x['uuid']},{'uuid':y['uuid']},'astrolabe/selected')
            return astrolabe_selection_receipt(before,state,args[0]) is True
        return False
    except (KeyError,IndexError,AttributeError,TypeError,ValueError):return False


def verify_release2_landing(matcher,diag,state):
    """Compare the archived hidden Colosseum dialog to its real first-turn landing.

    The normal event verifier independently derives the RNG and encounter for
    each observation. This narrow compatibility check only compares the facts
    present at both boundaries, rather than pretending the old dialog was legal.
    """
    try:
        source=diag['before']['state'];old=diag['after']['state']
        view=source['interaction']['event']
        event=old.get('interaction',{}).get('event',{})
        if (diag['method']!='chooseDialogOption' or diag['args']!=[0]
                or (view['id'],view['class_name'],view['phase'])!=('Colosseum','Colosseum','FIGHT')
                or old['phase']!='EVENT' or (event['class_name'],event['phase'])!=('Colosseum','POST_COMBAT')
                or old['roomPhase']!=state['roomPhase'] or state['roomPhase']!='COMBAT'
                or state['phase'] not in ('PLAYER_TURN','CARD_SELECT')
                or not _room_context(source,old,'Colosseum') or not _room_context(old,state,'Colosseum')
                or _streams(old)!=_streams(state)):
            return False
        roster=lambda raw:[(m['id'],m['maxHp']) for m in raw['monsters']]
        if roster(old)!=roster(state) or [identity for identity,_ in roster(state)]!=['SlaverBlue','SlaverRed']:
            return False
        for raw in (old,state):
            r=raw['combat_receipt']
            if (r['room_class'],r['room_phase'],r['active_monsters'],r['turn'],r['battle_over'],
                    r['player_dead'],r['played_card_uuids'])!=('EventRoom','COMBAT',2,1,False,False,[]):
                return False
        matcher.compare(old['player']['masterDeck'],state['player']['masterDeck'],'colosseum/masterDeck')
        return True
    except (KeyError,IndexError,AttributeError,TypeError,ValueError):return False
