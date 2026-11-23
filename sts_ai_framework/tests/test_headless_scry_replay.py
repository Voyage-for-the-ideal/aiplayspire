"""Exact receipts for consecutive Cut Through Fate Scry screens."""
import copy
import json
from types import SimpleNamespace
import unittest

from sts_ai_framework.headless.interaction import (
    _next_omniscience_just_lucky_scry, _omniscience_just_lucky_into_cut_scry,
    _queued_cut_through_fate_scry, _queued_omniscience_cut_through_fate_scry)
from sts_ai_framework.tests.test_headless_combat import combat_raw


def pair(duplicate):
    def public(uid, name, cost=1):
        return dict(uuid=uid,id=name,cost=cost,costForTurn=cost,timesUpgraded=0,misc=0,exhaust=False)
    def attack(uid, purge, damage):
        return dict(uuid=uid,card_id='CutThroughFate',cost=1,cost_for_turn=1,times_upgraded=0,
                    misc=0,base_damage=7,base_magic_number=2,damage=damage,purge_on_use=purge,exhaust=False)
    cards=[public(str(i),'Strike_P') for i in range(4)]
    first='first-attack';second=first if duplicate else 'second-attack'
    damage=14 if duplicate else 8
    original=attack(first,False,damage);queued=attack(second,duplicate,damage)
    relic='Kunai' if duplicate else 'Pen Nib'
    old_count,new_count=(2,0) if duplicate else (8,9)
    powers=[dict(id='Dexterity',amount=1)] if duplicate else [dict(id='Strength',amount=1)]
    armor=[dict(id='Plated Armor',amount=2)] if duplicate else []
    rng=combat_raw()['combat']['snapshot']['_headless_rng']
    before=dict(seed=12,floor=1,act=1,room='MonsterRoom',roomPhase='COMBAT',phase='CARD_SELECT',
                map=[],history={},observedKeys=[],
                monsters=[dict(id='Shelled Parasite' if duplicate else 'Champ',hp=30,block=0,powers=armor)],
                player=dict(drawPile=cards,drawCount=4,drawPileCount=4,hand=[],handSize=0,discardPile=[],
                            discardCount=0,discardPileCount=0,exhaustPile=[],masterDeck=[public(second,'CutThroughFate')],
                            stance='Wrath' if duplicate else 'None',
                            powers=powers+([dict(id='DuplicationPower',amount=0)] if duplicate else []),
                            relics=[dict(id=relic,counter=old_count)]),
                cardSelection=dict(options=cards[::-1][:2],selected=[]),
                driver_receipt=dict(method='playCard' if duplicate else 'usePotion',card_uuid=first if duplicate else None),
                combat_receipt=dict(rng=rng,played_card_uuids=[first],turn=1,active_monsters=1),
                interaction=dict(selection=dict(kind='grid',purpose='scry',count=2,locked=False,can_confirm=True,
                                                 any_number=True,can_pick_zero=True,auto_confirm=True),
                                 scry_replay=dict(source=original,target=0,autoplay=not duplicate,remaining=1,
                                                  next=dict(card=queued,target=0,ignore_energy_total=duplicate,energy_on_use=1))))
    after=copy.deepcopy(before)
    after['combat_receipt']['played_card_uuids'].append(second)
    after['monsters'][0].update(hp=30-damage,powers=[dict(id='Plated Armor',amount=1)] if duplicate else [])
    after['player'].update(powers=powers,relics=[dict(id=relic,counter=new_count)],
                           hand=[cards[-1]],handSize=1,drawPile=cards[:-1],drawCount=3,drawPileCount=3,
                           discardPile=[public(first,'CutThroughFate')],discardCount=1,discardPileCount=1)
    after['cardSelection']['options']=cards[-2:-4:-1]
    after['interaction']['scry_replay']=dict(source=queued,target=0,autoplay=True,remaining=0,next=None)
    return before,after


def accepts(before,after,selected=()):
    return _queued_cut_through_fate_scry(SimpleNamespace(raw_json=json.dumps(before)),
                                         SimpleNamespace(raw_json=json.dumps(after)),list(selected))


class ScryReplayTest(unittest.TestCase):
    def test_distilled_chaos_and_duplication_verify_distinct_queue_contracts(self):
        for mode in (False,True):
            with self.subTest(duplicate=mode):
                before,after=pair(mode)
                self.assertTrue(accepts(before,after))
                for change in (
                    lambda x:x['player']['drawPile'].reverse(),
                    lambda x:x['cardSelection']['options'].reverse(),
                    lambda x:x['monsters'][0].update(hp=29),
                    lambda x:x['combat_receipt']['played_card_uuids'].append('unbound'),
                    lambda x:x['interaction']['scry_replay']['source'].update(uuid='unbound'),
                    lambda x:x['player']['relics'][0].update(counter=1),
                ):
                    altered=copy.deepcopy(after);change(altered)
                    self.assertFalse(accepts(before,altered),change)
                before['interaction']['scry_replay']['next']['target']=1
                self.assertFalse(accepts(before,after))

    def test_selected_card_settles_before_the_next_draw(self):
        before,after=pair(False)
        draw=before['player']['drawPile']
        pick=draw[-1];pick['costForTurn']=0
        before['cardSelection']['selected']=[pick]
        after['player'].update(hand=[draw[-2]],drawPile=draw[:-2],drawCount=2,drawPileCount=2,
                               discardCount=2,discardPileCount=2)
        after['player']['discardPile'].insert(0,dict(pick,costForTurn=pick['cost']))
        after['cardSelection']['options']=draw[-3::-1]
        self.assertTrue(accepts(before,after,[pick['uuid']]))
        after['player']['discardPile'].reverse()
        self.assertFalse(accepts(before,after,[pick['uuid']]))

    def test_omniscience_just_lucky_replay_binds_fresh_receipt_and_pending_kunai(self):
        from sts_ai_framework.headless.event_receipts import Stream
        source=dict(index=2,uuid='just-lucky',id='JustLucky',type='ATTACK',timesUpgraded=0,
                    baseDamage=3,baseBlock=2,baseMagicNumber=1,damage=-1,block=-1,magicNumber=1,
                    cost=0,costForTurn=0,misc=0,exhaust=False)
        omni=dict(index=0,uuid='omni',id='Omniscience',type='SKILL',timesUpgraded=0,
                  baseDamage=-1,baseBlock=-1,baseMagicNumber=2,damage=0,block=1,magicNumber=2,
                  cost=4,costForTurn=4,misc=0,exhaust=True)
        draw=dict(index=0,uuid='draw',id='Strike_P',type='ATTACK',timesUpgraded=0,
                  baseDamage=6,baseBlock=-1,baseMagicNumber=-1,damage=6,block=-1,magicNumber=-1,
                  cost=1,costForTurn=1,misc=0,exhaust=False)
        hand=dict(draw,index=0,uuid='hand',block=1)
        controls=dict(kind='grid',purpose='scry',count=1,locked=False,can_confirm=True,
                      any_number=True,can_pick_zero=True,auto_confirm=True)
        rng=copy.deepcopy(combat_raw()['combat']['snapshot']['_headless_rng'])
        before_raw=dict(phase='CARD_SELECT',roomPhase='COMBAT',interaction=dict(selection=controls),
                        monsters=[dict(id='Cultist',hp=50,block=0)],
                        player=dict(masterDeck=[copy.deepcopy(omni),source],hand=[hand],drawPile=[draw],discardPile=[],
                                    exhaustPile=[copy.deepcopy(omni)],exhaustCount=1,block=0,stance='Neutral',
                                    powers=[dict(id='Confusion',amount=-1),dict(id='Dexterity',amount=2),
                                            dict(id='Thorns',amount=3)],
                                    relics=[dict(id='Pen Nib',counter=6),dict(id='Kunai',counter=0)]),
                        cardSelection=dict(kind='grid',options=[draw],selected=[]),
                        combat_receipt=dict(rng=rng,played_card_uuids=['omni','omni-copy','just-lucky'],
                                            turn=1,active_monsters=1),
                        driver_receipt=dict(method='chooseGridCard',args=[0],card_uuid='draw',
                                            request_id=4,sequence=3,source_state_token='older'))
        after_raw=copy.deepcopy(before_raw)
        after_raw['driver_receipt']=dict(method='confirmGrid',args=[],card_uuid=None,request_id=5,
                                         sequence=4,source_state_token='before-token')
        after_raw['combat_receipt']['played_card_uuids'].append('fresh-just-lucky')
        stream=Stream(rng['streams']['card_random']);stream.bounded(1)
        after_raw['combat_receipt']['rng']['streams']['card_random']=stream.state()
        after_raw['monsters'][0]['hp']=47
        player=after_raw['player'];player['block']=4;player['exhaustCount']=2
        player['powers'][1]['amount']=3;player['relics'][0]['counter']=7;player['relics'][1]['counter']=1
        player['hand'][0]['block']=2
        player['exhaustPile'][0].update(damage=-1,block=-1,magicNumber=2)
        player['exhaustPile'].append(dict(source,index=1,damage=3,block=4,magicNumber=1,exhaust=True))
        before=SimpleNamespace(raw_json=json.dumps(before_raw),state_token='before-token')
        after=SimpleNamespace(raw_json=json.dumps(after_raw),request_id=5,sequence=4)
        self.assertTrue(_next_omniscience_just_lucky_scry(before,after,[]))
        for path,value in (
                (('player','block'),0),
                (('player','relics',0,'counter'),8),
                (('monsters',0,'hp'),46),
                (('combat_receipt','played_card_uuids',-1),'just-lucky')):
            altered=copy.deepcopy(after_raw);target=altered
            for key in path[:-1]:target=target[key]
            target[path[-1]]=value
            candidate=SimpleNamespace(raw_json=json.dumps(altered),request_id=5,sequence=4)
            self.assertFalse(_next_omniscience_just_lucky_scry(before,candidate,[]),path)

    def test_just_lucky_tail_into_omniscience_cut_through_fate_scry(self):
        from sts_ai_framework.headless.event_receipts import Stream
        def card(uid,name,damage,block,magic,kind='ATTACK',exhaust=False):
            return dict(index=0,uuid=uid,id=name,type=kind,timesUpgraded=0,upgraded=False,
                        baseDamage=damage,baseBlock=block,baseMagicNumber=magic,damage=damage,
                        block=block,magicNumber=magic,cost=1,costForTurn=1,misc=0,exhaust=exhaust)
        omni=card('omni','Omniscience',-1,-1,2,'SKILL',True)
        lucky=card('lucky','JustLucky',3,2,1,exhaust=True);lucky['block']=4
        cut=card('cut','CutThroughFate',7,-1,2)
        draw=[card('bane','AscendersBane',-1,-1,-1,'CURSE'),card('strike','Strike_P',9,-1,-1)]
        controls=dict(kind='grid',purpose='scry',count=1,locked=False,can_confirm=True,
                      any_number=True,can_pick_zero=True,auto_confirm=True)
        rng=copy.deepcopy(combat_raw()['combat']['snapshot']['_headless_rng'])
        before_raw=dict(phase='CARD_SELECT',roomPhase='COMBAT',interaction=dict(selection=controls,flow={}),
                        monsters=[dict(id='Cultist',hp=47,block=0),dict(id='AwakenedOne',hp=302,block=0)],
                        player=dict(masterDeck=[copy.deepcopy(omni),copy.deepcopy(lucky),copy.deepcopy(cut)],
                                    hand=[],drawPile=draw,discardPile=[],exhaustPile=[lucky],exhaustCount=1,
                                    block=4,stance='Calm',powers=[dict(id='Confusion',amount=-1),
                                    dict(id='Dexterity',amount=3),dict(id='Thorns',amount=3)],
                                    relics=[dict(id='Pen Nib',counter=7),dict(id='Kunai',counter=1)]),
                        cardSelection=dict(kind='grid',options=[dict(draw[-1],index=0)],selected=[]),
                        combat_receipt=dict(rng=rng,played_card_uuids=['omni','lucky','lucky-copy'],
                                            turn=1,active_monsters=2),
                        driver_receipt=dict(method='confirmGrid',args=[],card_uuid=None,request_id=5,
                                            sequence=4,source_state_token='older'))
        after_raw=copy.deepcopy(before_raw);after_raw['interaction']['selection']=dict(controls,count=2)
        source={'card_id':'CutThroughFate','uuid':'cut','times_upgraded':0,'upgraded':False,
                'cost':1,'cost_for_turn':1,'base_damage':7,'base_block':-1,'base_magic_number':2,
                'damage':7,'block':2,'magic_number':2,'misc':0,'exhaust':True,'is_ethereal':False,
                'self_retain':False,'retain':False,'free_to_play_once':False,'purge_on_use':False,
                'shuffle_back_into_draw_pile':False,'dont_trigger_on_use_card':False,
                'target_enum_ordinal':0,'_private':{}}
        after_raw['interaction']['scry_replay']=dict(source=source,target=1,autoplay=True,remaining=1,next=None)
        after_raw['cardSelection']['options']=[dict(draw[-1],index=0),dict(draw[-2],index=1)]
        after_raw['combat_receipt']['played_card_uuids'].append('cut')
        stream=Stream(rng['streams']['card_random']);stream.bounded(1)
        after_raw['combat_receipt']['rng']['streams']['card_random']=stream.state()
        after_raw['driver_receipt']=dict(method='confirmGrid',args=[],card_uuid=None,request_id=6,
                                         sequence=5,source_state_token='before-token')
        after_raw['monsters'][0]['hp']=37;player=after_raw['player'];player['block']=9
        player['relics'][0]['counter']=8;player['relics'][1]['counter']=2
        player['exhaustPile'][0].update(damage=3,block=2,magicNumber=1)
        before=SimpleNamespace(raw_json=json.dumps(before_raw),state_token='before-token')
        after=SimpleNamespace(raw_json=json.dumps(after_raw),request_id=6,sequence=5)
        self.assertTrue(_omniscience_just_lucky_into_cut_scry(before,after,[]))
        for path,value in ((('player','block'),8),(('monsters',0,'hp'),38),
                           (('interaction','scry_replay','target'),-1)):
            altered=copy.deepcopy(after_raw);target=altered
            for key in path[:-1]:target=target[key]
            target[path[-1]]=value
            candidate=SimpleNamespace(raw_json=json.dumps(altered),request_id=6,sequence=5)
            self.assertFalse(_omniscience_just_lucky_into_cut_scry(before,candidate,[]),path)

    def test_omniscience_cut_through_fate_purge_copy_is_inferred(self):
        from sts_ai_framework.headless.event_receipts import Stream
        def card(uid,name,damage,block,magic,kind='ATTACK',cost=1,exhaust=False):
            return dict(index=0,uuid=uid,id=name,type=kind,timesUpgraded=0,upgraded=False,
                        baseDamage=damage,baseBlock=block,baseMagicNumber=magic,damage=damage,
                        block=block,magicNumber=magic,cost=cost,costForTurn=cost,misc=0,exhaust=exhaust)
        cut=card('cut','CutThroughFate',7,-1,2);lucky=card('lucky','JustLucky',3,2,1,exhaust=True)
        draw=[card('follow','FollowUp',11,-1,-1),card('bane','AscendersBane',-1,-1,-1,'CURSE',-2),
              card('strike','Strike_P',9,-1,-1)]
        controls=dict(kind='grid',purpose='scry',count=2,locked=False,can_confirm=True,
                      any_number=True,can_pick_zero=True,auto_confirm=True)
        source={'card_id':'CutThroughFate','uuid':'cut','times_upgraded':0,'upgraded':False,
                'cost':1,'cost_for_turn':1,'base_damage':7,'base_block':-1,'base_magic_number':2,
                'damage':7,'block':2,'magic_number':2,'misc':0,'exhaust':True,'is_ethereal':False,
                'self_retain':False,'retain':False,'free_to_play_once':False,'purge_on_use':False,
                'shuffle_back_into_draw_pile':False,'dont_trigger_on_use_card':False,
                'target_enum_ordinal':0,'_private':{}}
        rng=copy.deepcopy(combat_raw()['combat']['snapshot']['_headless_rng'])
        before_raw=dict(phase='CARD_SELECT',roomPhase='COMBAT',interaction=dict(selection=controls,flow={},
                        scry_replay=dict(source=source,target=1,autoplay=True,remaining=1,next=None)),
                        monsters=[dict(id='Cultist',hp=37,block=0),dict(id='AwakenedOne',hp=302,block=0)],
                        player=dict(masterDeck=[cut],hand=[],handSize=0,drawPile=draw,drawCount=3,
                                    drawPileCount=3,discardPile=[],exhaustPile=[lucky],exhaustCount=1,
                                    block=9,stance='Calm',powers=[dict(id='Confusion',amount=-1),
                                    dict(id='Dexterity',amount=3),dict(id='Thorns',amount=3)],
                                    relics=[dict(id='Pen Nib',counter=8),dict(id='Kunai',counter=2)]),
                        cardSelection=dict(kind='grid',options=[dict(draw[-1],index=0),dict(draw[-2],index=1)],selected=[]),
                        combat_receipt=dict(rng=rng,played_card_uuids=['omni','cut'],turn=1,active_monsters=2),
                        driver_receipt=dict(method='confirmGrid',args=[],card_uuid=None,request_id=6,
                                            sequence=5,source_state_token='older'))
        after_raw=copy.deepcopy(before_raw);copy_card=dict(source,uuid='cut-copy',exhaust=False,purge_on_use=True)
        after_raw['interaction']['scry_replay']=dict(source=copy_card,target=1,autoplay=True,remaining=0,next=None)
        after_raw['combat_receipt']['played_card_uuids'].append('cut-copy')
        stream=Stream(rng['streams']['card_random']);cost=stream.bounded(4);stream.bounded(1)
        after_raw['combat_receipt']['rng']['streams']['card_random']=stream.state()
        after_raw['driver_receipt']=dict(method='confirmGrid',args=[],card_uuid=None,request_id=7,
                                         sequence=6,source_state_token='before-token')
        after_raw['monsters'][0]['hp']=30;player=after_raw['player']
        drawn=dict(draw[-1],index=0,cost=cost,costForTurn=cost,damage=9,block=2)
        player.update(drawPile=draw[:-1],drawCount=2,drawPileCount=2,hand=[drawn],handSize=1,
                      exhaustPile=[lucky,dict(cut,index=1,damage=7,block=2,magicNumber=2,exhaust=True)],
                      exhaustCount=2)
        player['relics'][0]['counter']=9;player['relics'][1]['counter']=0
        after_raw['cardSelection']['options']=[dict(draw[-2],index=0),dict(draw[-3],index=1)]
        before=SimpleNamespace(raw_json=json.dumps(before_raw),state_token='before-token')
        after=SimpleNamespace(raw_json=json.dumps(after_raw),request_id=7,sequence=6)
        self.assertTrue(_queued_omniscience_cut_through_fate_scry(before,after,[]))
        for path,value in ((('player','handSize'),2),(('monsters',0,'hp'),31),
                           (('interaction','scry_replay','source','uuid'),'cut')):
            altered=copy.deepcopy(after_raw);target=altered
            for key in path[:-1]:target=target[key]
            target[path[-1]]=value
            candidate=SimpleNamespace(raw_json=json.dumps(altered),request_id=7,sequence=6)
            self.assertFalse(_queued_omniscience_cut_through_fate_scry(before,candidate,[]),path)
