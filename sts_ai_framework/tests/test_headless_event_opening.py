"""Event opening relics: pre-draw choices, RNG and the combat handoff."""
import copy
import json
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from sts_ai_framework.headless.event_receipts import (
    Stream, _opening_piles, _opening_relic_counters, _opening_snecko_costs,
    _bandits_pay, _toolbox_offers, toolbox_event_pick_receipt,
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
    def test_masked_bandits_payment_binds_all_target_rolls(self):
        original=streams()
        rolls=Stream(original['streams']['headless'])
        for _ in range(4):rolls.bounded(3)
        paid=copy.deepcopy(original)
        paid['streams']['headless']=rolls.state()
        raw=dict(seed=1,floor=20,act=2,ascension=1,runMode={},room='EventRoom',
                 roomPhase='EVENT',phase='EVENT',map=[],history={},observedKeys={},
                 interaction=dict(flow={'event_class':'MaskedBandits'}))
        common=dict(deck=[],potions=[],relics=[],keys={})
        player=PlayerState(current_hp=37,max_hp=70,gold=4,block=0,energy=0)
        left=SimpleNamespace(**common,player=player)
        choice=SimpleNamespace(button_index=0,enabled=True)
        event=SimpleNamespace(id='Masked Bandits',class_name='MaskedBandits',phase='PAID_1',choices=[choice])
        right=SimpleNamespace(**common,player=player.model_copy(update={'gold':0}),event=event)
        before=dict(raw,combat_receipt={'rng':original})
        self.assertTrue(_bandits_pay(left,right,before,dict(raw,combat_receipt={'rng':paid})))
        self.assertFalse(_bandits_pay(left,right,before,dict(raw,combat_receipt={'rng':original})))
        rolls.bounded(3)
        paid['streams']['headless']=rolls.state()
        self.assertFalse(_bandits_pay(left,right,before,dict(raw,combat_receipt={'rng':paid})))

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
        rels=[relic('PureWater'),relic('Blood Vial'),relic('Toolbox'),relic('Mercury Hourglass')]
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
            # Blood Vial already healed before the picker; do not heal it twice.
            right.player=player.model_copy(update={'current_hp':52})
            self.assertFalse(toolbox_event_pick_receipt(before,after,1))
            right.player=player
            self.assertFalse(toolbox_event_pick_receipt(before,after,0))
            right.hand[1]=choice.model_copy(update={'uuid':'offered1'})
            self.assertFalse(toolbox_event_pick_receipt(before,after,1))
            right.hand[1]=choice;right.hand[2:]=right.hand[2:][::-1]
            self.assertFalse(toolbox_event_pick_receipt(before,after,1))
            right.hand[2:]=right.hand[2:][::-1];right.monsters=[monster]
            self.assertFalse(toolbox_event_pick_receipt(before,after,1))


class ToolboxDrawEffectsTests(unittest.TestCase):
    def setUp(self):
        self.rng = streams()
        self.after_rng = copy.deepcopy(self.rng)
        self.draw_pile = [card(f'd{i}', 'Strike_P') for i in range(8)]
        self.picked = card('picked', 'Blind', 0)
        self.relics = [relic('Toolbox')]

    def receipt(self, hand, draw_pile, *, before_hand=(), exhaust=(), discard=(), after_rng=None):
        player = PlayerState(current_hp=50, max_hp=70, energy=4, block=0, gold=2)
        monster = MonsterState(id='Cultist', name='Cultist', current_hp=48, max_hp=48, block=0, intent='BUFF')
        world = dict(keys={}, relics=self.relics, deck=self.draw_pile, potions=[], player=player, monsters=[monster])
        left = SimpleNamespace(**world, hand=list(before_hand), draw_pile=self.draw_pile, discard_pile=[], exhaust_pile=[])
        right = SimpleNamespace(**world, hand=list(hand), draw_pile=list(draw_pile),
                                discard_pile=list(discard), exhaust_pile=list(exhaust))
        rng = self.after_rng if after_rng is None else after_rng
        base = dict(room='MonsterRoom', roomPhase='COMBAT', seed=1, floor=29, act=2, ascension=1,
                    runMode={}, map=[], history={}, observedKeys={},
                    interaction=dict(card_reward={'source_action': 'ChooseOneColorless'}))
        receipt = dict(turn=1, player_dead=False, played_card_uuids=[], active_monsters=1, battle_over=False)
        offers = [dict(id=name, uuid=f'offer{i}', timesUpgraded=0, cost=0, costForTurn=0, misc=0, exhaust=False)
                  for i, name in enumerate(('Blind', 'Panacea', 'Finesse'))]
        before = SimpleNamespace(raw_json=json.dumps(dict(base, phase='CARD_PICK', cardRewardOptions=offers,
                                 combat_receipt=dict(receipt, rng=self.rng))), state=left, run_id='live')
        after = SimpleNamespace(raw_json=json.dumps(dict(base, phase='PLAYER_TURN', combat_receipt=dict(receipt, rng=rng),
                                driver_receipt=dict(method='chooseRewardCard', args=[0], sequence=2))),
                                state=right, sequence=2, run_id='live', native_combat_handoff=False)
        with patch('sts_ai_framework.headless.event_receipts.HeadlessCombatBridge') as adapter:
            adapter.return_value.observe.return_value.snapshot = {'turn': 1, '_headless_rng': rng}
            return toolbox_event_pick_receipt(before, after, 0)

    def test_deus_ex_machina_uses_upgrade_and_exhausts_the_drawn_instance(self):
        miracles = [card(f'm{i}', 'Miracle', 0, True) for i in range(3)]
        for level, count in ((0, 2), (1, 3)):
            with self.subTest(upgrades=level):
                deus = card('deus', 'DeusExMachina', -2, True).model_copy(update={'upgrades': level})
                self.draw_pile[-1] = deus
                hand = [self.picked, *self.draw_pile[-2:2:-1], *miracles[:count]]
                draw_pile = self.draw_pile[:3]
                self.assertTrue(self.receipt(hand, draw_pile, exhaust=[deus]))
                self.assertFalse(self.receipt(hand[:-1], draw_pile, exhaust=[deus]))
                self.assertFalse(self.receipt(hand, draw_pile))
                self.assertFalse(self.receipt(hand[:-1] + [miracles[0]], draw_pile, exhaust=[deus]))

    def test_upgraded_deus_sends_overflow_miracle_to_discard(self):
        self.relics += [relic('HolyWater')]
        deus = card('deus', 'DeusExMachina', -2, True).model_copy(update={'upgrades': 1})
        self.draw_pile[-1] = deus
        queued = [card(f'q{i}', 'Miracle', 0, True) for i in range(3)]
        miracles = [card(f'm{i}', 'Miracle', 0, True) for i in range(3)]
        hand = [self.picked, *queued, *self.draw_pile[-2:2:-1], *miracles[:2]]
        self.assertTrue(self.receipt(hand, self.draw_pile[:3], exhaust=[deus], discard=miracles[2:]))
        self.assertFalse(self.receipt(hand + miracles[2:], self.draw_pile[:3], exhaust=[deus]))

    def test_endless_agony_copies_are_lifo_and_keep_the_pre_snecko_cost(self):
        self.relics += [relic('Snecko Eye')]
        agony = card('agony', 'Endless Agony', 0, True)
        upgraded = agony.model_copy(update={'uuid': 'agony-plus', 'upgrades': 1})
        self.draw_pile[-3], self.draw_pile[-5] = agony, upgraded
        rng = Stream(self.rng['streams']['card_random'])
        drawn = []
        for original in self.draw_pile[:0:-1]:
            cost = rng.bounded(4)
            drawn.append(original.model_copy(update={'cost': cost, 'cost_for_turn': cost}))
        self.after_rng['streams']['card_random'] = rng.state()
        copies = [upgraded.model_copy(update={'uuid': 'copy-plus'}), agony.model_copy(update={'uuid': 'copy'})]
        hand = [self.picked, *drawn, *copies]
        self.assertTrue(self.receipt(hand, self.draw_pile[:1]))
        self.assertFalse(self.receipt(hand[:-1], self.draw_pile[:1]))
        self.assertFalse(self.receipt(hand[:-2] + copies[::-1], self.draw_pile[:1]))
        self.assertFalse(self.receipt(hand[:-1] + [copies[1].model_copy(update={'cost': drawn[2].cost})],
                                      self.draw_pile[:1]))
        self.assertFalse(self.receipt(hand, self.draw_pile[:1], after_rng=self.rng))

    def test_mark_of_pain_inserts_two_fresh_wounds_in_rng_order(self):
        self.relics += [relic('Mark of Pain')]
        wound1, wound2 = [card(f'w{i}', 'Wound', -2) for i in range(2)]
        rng = Stream(self.rng['streams']['card_random'])
        self.assertEqual((rng.bounded(3), rng.bounded(4)), (1, 0))
        self.after_rng['streams']['card_random'] = rng.state()
        hand = [self.picked, *self.draw_pile[:2:-1]]
        d0, d1, d2 = self.draw_pile[:3]
        pile = [wound2, d0, wound1, d1, d2]
        self.assertTrue(self.receipt(hand, pile))
        self.assertFalse(self.receipt(hand, [wound2, wound1, d0, d1, d2]))
        self.assertFalse(self.receipt(hand, pile[1:]))
        self.assertFalse(self.receipt(hand, [wound1, *pile[1:]]))
        self.assertFalse(self.receipt(hand, pile, after_rng=self.rng))

    def test_bag_of_preparation_can_draw_a_wound_if_queued_after_mark(self):
        self.relics += [relic('Mark of Pain'), relic('Bag of Preparation')]
        self.draw_pile = self.draw_pile[:6]
        wound1, wound2 = [card(f'w{i}', 'Wound', -2) for i in range(2)]
        rng = Stream(self.rng['streams']['card_random'])
        self.assertEqual((rng.bounded(1), rng.bounded(2)), (0, 0))
        self.after_rng['streams']['card_random'] = rng.state()
        hand = [self.picked, *self.draw_pile[::-1], wound1]
        self.assertTrue(self.receipt(hand, [wound2]))
        self.relics[-2:] = self.relics[-2:][::-1]
        self.assertFalse(self.receipt(hand, [wound2]))
        # Bag emptied the draw pile before Mark, so only the second insertion rolls.
        rng = Stream(self.rng['streams']['card_random'])
        rng.bounded(1)
        self.after_rng['streams']['card_random'] = rng.state()
        self.assertTrue(self.receipt(hand[:-1], [wound2, wound1]))
