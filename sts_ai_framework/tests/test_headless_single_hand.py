"""The filtered hand, selected cards and pending UseCardAction are three distinct owners."""
from copy import deepcopy
import unittest
from uuid import UUID

from sts_ai_framework.headless.combat import _single_hand_evidence, _normal_card_lineage
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_combat import combat_raw


def sample(source='Dual Wield',chosen=False):
    s=combat_raw()['combat']['snapshot'];p=s['player_state'];nums=lambda i:str(UUID(int=i))
    original=deepcopy(p['hand'][0]);make=lambda id,n:dict(original,card_id=id,uuid=nums(n))
    p['hand']=[make('Strike_R',101),make('Bash',102)]
    p['draw_pile']=[];p['discard_pile']=[];p['exhaust_pile']=[];p['limbo']=[]
    hidden=[make('Defend_R',103)] if source=='Dual Wield' else []
    c=make(source,104);c.update(times_upgraded=int(source=='True Grit'),exhaust=False,purge_on_use=False,
            dont_trigger_on_use_card=False,cost=1,cost_for_turn=1,free_to_play_once=False,retain=False)
    p['card_in_use']=c
    s['screen_name']='HAND_SELECT';s['is_screen_up']=True
    s['hand_select_screen_state']={'any_number':False,'can_pick_zero':False,'num_cards_to_select':1,
        'were_cards_retrieved':False,'up_to':False,'selected_cards':[p['hand'].pop(0)] if chosen else [],
        'current_action_state':{'state_class':'savestate.actions.SingleHandActionState','state':{
            'source':source,'copies':1,'owner_index':-1,'hidden':hidden}},'card_queue_state':[],
        'action_queue':[{'state_class':'savestate.actions.UseCardActionState','state':{
            'card':deepcopy(c),'targetIndex':-2,'reboundCard':False,'exhaustCard':False,'returnToHand':False}}]}
    return s


class SingleHandEvidence(unittest.TestCase):
    def test_corruption_binds_pending_skill_exhaust(self):
        s=sample('Armaments');p=s['player_state'];q=s['hand_select_screen_state']['action_queue'][0]['state']
        p['creature']['powers'].append({'power_id':'Corruption','amount':-1})
        q['exhaustCard']=True
        self.assertTrue(_single_hand_evidence(s)['pending_card']['exhaust'])
        p['creature']['powers'].clear()
        with self.assertRaises(HeadlessContractError):_single_hand_evidence(s)

    def test_blocking_skills_require_exact_juggernaut_tail_but_never_run_it_during_selection(self):
        for source,block in (('True Grit',9),('Armaments',5)):
            with self.subTest(source=source):
                s=sample(source);p=s['player_state'];q=s['hand_select_screen_state']['action_queue']
                p['card_in_use']['block']=block;q[0]['state']['card']['block']=block
                p['creature']['powers'].append({'power_id':'Juggernaut','amount':5})
                with self.assertRaises(HeadlessContractError):_single_hand_evidence(s)
                q.append({'state_class':'savestate.actions.SelectionJuggernautActionState','state':{'amount':5}})
                self.assertEqual(_single_hand_evidence(s)['selected'],[])
                q[-1]['state']['amount']=4
                with self.assertRaises(HeadlessContractError):_single_hand_evidence(s)

    def test_orange_pellets_requires_the_exact_flash_purge_continuation(self):
        s=sample('Armaments');p=s['player_state'];q=s['hand_select_screen_state']['action_queue']
        p['relics'].append({'relic_id':'OrangePellets','counter':-1,'_private':{
            'OrangePellets.ATTACK':False,'OrangePellets.SKILL':False,'OrangePellets.POWER':False}})
        with self.assertRaises(HeadlessContractError):_single_hand_evidence(s)
        q.insert(0,{'state_class':'savestate.actions.SelectionOrangePelletsActionState','state':{}})
        self.assertEqual(_single_hand_evidence(s)['selected'],[])
        q[0]['state']['amount']=1
        with self.assertRaises(HeadlessContractError):_single_hand_evidence(s)

    def test_duplicate_action_and_wrong_target_are_rejected(self):
        for source in ('True Grit','Dual Wield','Armaments'):
            for mutation in ('queue','owner','same_uuid','wrong_source','count'):
                with self.subTest(source=source,mutation=mutation):
                    s=sample(source,True);actual=_single_hand_evidence(s);self.assertEqual(len(actual['selected']),1)
                    h=s['hand_select_screen_state'];q=h['action_queue']
                    if mutation=='queue':q.extend(deepcopy(q))
                    if mutation=='owner':q[0]['state']['targetIndex']=0
                    if mutation=='same_uuid':h['selected_cards'][0]['uuid']=actual['remaining'][0]
                    if mutation=='wrong_source':h['current_action_state']['state']['source']='Headbutt'
                    if mutation=='count':h['num_cards_to_select']=2
                    with self.assertRaises(HeadlessContractError):_single_hand_evidence(s)

    def test_copies_must_not_inherit_hidden_selected_or_pending_uuids(self):
        s=sample(chosen=True);p=s['player_state'];e=_single_hand_evidence(s)
        out=deepcopy(s);new=out['player_state']
        visible=p['hand'][0];uids=e['remaining']+e['hidden']+[str(UUID(int=551)),str(UUID(int=552))]
        new['hand']=[dict(visible,uuid=u) for u in uids];new['discard_pile']=[p['card_in_use']]
        expected={'hand':e['remaining']+e['hidden']+[None,None],'draw_pile':[],'exhaust_pile':[],'discard_pile':[p['card_in_use']['uuid']]}
        self.assertTrue(_normal_card_lineage(s,out,expected))
        for stale in [e['hidden'][0],e['selected'][0],e['pending_card']['uuid'],uids[-2]]:
            changed=deepcopy(out);changed['player_state']['hand'][-1]['uuid']=stale
            self.assertFalse(_normal_card_lineage(s,changed,expected))
