"""Exact receipts for consecutive Cut Through Fate Scry screens."""
import copy
import json
from types import SimpleNamespace
import unittest

from sts_ai_framework.headless.interaction import _queued_cut_through_fate_scry
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
