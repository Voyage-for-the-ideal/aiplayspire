"""Event opening relics: pre-draw choices, RNG and the combat handoff."""
import copy
import json
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from sts_ai_framework.headless.event_receipts import (
    Stream, _opening_piles, _opening_relic_counters, _opening_snecko_costs,
    _toolbox_offers, toolbox_event_pick_receipt,
)
from sts_ai_framework.models import Card, MonsterState, PlayerState, RelicState
from sts_ai_framework.tests.test_headless_combat import combat_raw


def relic(name, counter=-1):
    return RelicState(id=name, name=name, counter=counter)


def card(uid, name, cost=1, exhaust=False):
    return Card(index=0, uuid=uid, id=name, name=name, cost=cost, cost_for_turn=cost, exhausts=exhaust)


def streams():
    return combat_raw()['combat']['snapshot']['_headless_rng']


class OpeningRelicsTests(unittest.TestCase):
    def test_toolbox_pauses_between_ordered_predraw_relics_and_before_draw(self):
        deck=[card('d1','Strike_P'),card('d2','Defend_P')]
        rels=[relic('HolyWater'),relic('Toolbox'),relic('Ninja Scroll'),relic('Kunai')]
        pre=[card('m'+str(i),'Miracle',0) for i in range(3)]
        post=[card('s'+str(i),'Shiv',0) for i in range(3)]
        left=SimpleNamespace(relics=rels)
        right=SimpleNamespace(hand=pre,draw_pile=deck,exhaust_pile=[],discard_pile=[])
        self.assertTrue(_opening_piles(left,right,deck,True))
        right.hand=post+pre
        self.assertFalse(_opening_piles(left,right,deck,True))
        right.hand=pre+post+deck[::-1];right.draw_pile=[]
        self.assertTrue(_opening_piles(left,right,deck,False))
        right.hand[0]=pre[1]
        self.assertFalse(_opening_piles(left,right,deck,False))
        self.assertEqual(_opening_relic_counters(left)[-1].counter,0)

    def test_snecko_only_rolls_after_draw_for_cards_with_nonnegative_cost(self):
        state=streams()['streams']
        deck=[card('one','Strike_P'),card('two','Regret',-2),card('three','Deflect',0)]
        rng=Stream(state['card_random'])
        hand=[c.model_copy(update=dict(cost=n,cost_for_turn=n)) for c,n in zip(deck,[rng.bounded(4),-2,rng.bounded(4)])]
        left=SimpleNamespace(deck=deck,relics=[relic('Snecko Eye')])
        right=SimpleNamespace(hand=hand)
        expected=dict(state)
        self.assertTrue(_opening_snecko_costs(left,right,expected,False))
        self.assertEqual(expected['card_random'],rng.state())
        right.hand[0]=hand[0].model_copy(update={'cost_for_turn':99})
        self.assertFalse(_opening_snecko_costs(left,right,dict(state),False))
        expected=dict(state)
        self.assertTrue(_opening_snecko_costs(left,right,expected,True))
        self.assertEqual(expected,state)

    def test_toolbox_options_use_ordered_distinct_rng_picks_and_fresh_uuids(self):
        from sts_ai_framework.headless.event_receipts import _TOOLBOX_POOL
        state=streams()['streams'];rng=Stream(state['card_random']);ids=[]
        while len(ids)<3:
            c=_TOOLBOX_POOL[rng.bounded(len(_TOOLBOX_POOL))]
            if c not in ids:ids.append(c)
        offered=[dict(id=c,uuid=str(i),upgraded=False,bottled=False) for i,c in enumerate(ids)]
        raw=dict(cardRewardOptions=offered,player=dict(masterDeck=[],hand=[{'uuid':'generated'}],drawPile=[],
                                                       discardPile=[],exhaustPile=[]))
        left=SimpleNamespace(relics=[relic('Toolbox')])
        expected=dict(state)
        self.assertTrue(_toolbox_offers(left,raw,expected))
        self.assertEqual(expected['card_random'],rng.state())
        offered[0]['uuid']='generated'
        self.assertFalse(_toolbox_offers(left,raw,dict(state)))

    def test_event_pick_rejects_wrong_copy_wrong_draw_or_early_damage(self):
        # The real Linux run independently checks the Java/native snapshot import.
        rng=streams()
        rels=[relic('PureWater'),relic('Toolbox'),relic('Mercury Hourglass')]
        deck=[card('d'+str(i),'Strike_P') for i in range(6)]
        miracle=card('miracle','Miracle',0,True)
        choice=card('fresh','Dramatic Entrance',0,True)
        player=PlayerState(current_hp=50,max_hp=70,energy=3,block=0,gold=2)
        monster=MonsterState(id='BanditBear',name='Bear',current_hp=39,max_hp=39,block=0,intent='STRONG_DEBUFF')
        world=dict(keys={},relics=rels,deck=deck,discard_pile=[],exhaust_pile=[],potions=[],player=player)
        left=SimpleNamespace(**world,monsters=[monster],hand=[miracle],draw_pile=deck)
        right=SimpleNamespace(**world,monsters=[monster.model_copy(update={'current_hp':36})],
                              hand=[miracle,choice,*deck[-1:0:-1]],draw_pile=deck[:1])
        base=dict(room='EventRoom',roomPhase='COMBAT',seed=1,floor=29,act=2,ascension=5,runMode={},map=[],history={},observedKeys={},
                  interaction=dict(flow={'event_class':'MaskedBandits'},card_reward={'source_action':'ChooseOneColorless'}),
                  combat_receipt=dict(turn=1,player_dead=False,played_card_uuids=[],active_monsters=1,battle_over=False,rng=rng))
        def offer(name, uid, cost=0, exhaust=False):
            return dict(id=name,uuid=uid,timesUpgraded=0,cost=cost,costForTurn=cost,misc=0,exhaust=exhaust)
        before=SimpleNamespace(raw_json=json.dumps(dict(base,phase='CARD_PICK',cardRewardOptions=[
            offer('Blind','offered0'),offer('Dramatic Entrance','offered1',exhaust=True),offer('Jack Of All Trades','offered2')])),state=left)
        after=SimpleNamespace(raw_json=json.dumps(dict(base,phase='PLAYER_TURN',driver_receipt=dict(
            method='chooseRewardCard',args=[1],sequence=457))),state=right,sequence=457,native_combat_handoff=False)
        with patch('sts_ai_framework.headless.event_receipts.HeadlessCombatBridge') as adapter:
            adapter.return_value.observe.return_value.snapshot={'turn':1,'_headless_rng':rng}
            before.run_id=after.run_id='live'
            self.assertTrue(toolbox_event_pick_receipt(before,after,1))
            self.assertFalse(toolbox_event_pick_receipt(before,after,0))
            right.hand[1]=choice.model_copy(update={'uuid':'offered1'})
            self.assertFalse(toolbox_event_pick_receipt(before,after,1))
            right.hand[1]=choice;right.hand[2:]=right.hand[2:][::-1]
            self.assertFalse(toolbox_event_pick_receipt(before,after,1))
            right.hand[2:]=right.hand[2:][::-1];right.monsters=[monster]
            self.assertFalse(toolbox_event_pick_receipt(before,after,1))
