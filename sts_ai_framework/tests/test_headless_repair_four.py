"""The final receipt checks the actually selected instance and all gameplay RNG bits."""
import copy
import unittest
from unittest.mock import patch
from sts_ai_framework.headless.repair_four import verify_four


def fixture():
    def card(uid,id):
        return dict(uuid=uid,id=id,timesUpgraded=0,misc=0,cost=1,baseBlock=0,baseDamage=2,baseMagicNumber=-1)
    p=dict(hand=[card('source','Seek')],drawPile=[card('s1','Strike_B'),card('s2','Strike_B')],discardPile=[],exhaustPile=[])
    start=dict(seed=5050339,phase='PLAYER_TURN',room='MonsterRoom',roomPhase='COMBAT',act=1,floor=1,player=p,combat_receipt={'turn':1},rng=11)
    opened=copy.deepcopy(start);opened['phase']='CARD_SELECT';opened['cardSelection']={'options':list(reversed(p['drawPile']))}
    done=copy.deepcopy(start);dest=done['player'];dest['exhaustPile']=dest['hand'];dest['hand']=[dest['drawPile'].pop()]
    return start,[('playCard',[0,-1],opened),('chooseGridCard',[0],done)]


class FourReceiptTest(unittest.TestCase):
    @patch('sts_ai_framework.headless.repair_four.streams',side_effect=lambda raw:{str(i):(raw['rng'],i) for i in range(14)})
    def test_identical_cards_cannot_swap_and_rng_must_match_through_picker(self,_):
        start,steps=fixture()
        r=verify_four(start['seed'],start,steps)
        self.assertEqual((r['selected_uuid'],r['identical_rng_streams']),('s2',14))
        for mutation in ('wrong_choice','different_rng_word','no_selection','different_cleanup'):
            with self.subTest(mutation):
                a,s=copy.deepcopy((start,steps))
                if mutation=='wrong_choice':s[1][1][0]=1
                if mutation=='different_rng_word':s[0][2]['rng']+=1
                if mutation=='no_selection':s.pop()
                if mutation=='different_cleanup':
                    p=s[1][2]['player'];p['discardPile']=p.pop('exhaustPile');p['exhaustPile']=[]
                with self.assertRaises(ValueError):verify_four(start['seed'],a,s)
