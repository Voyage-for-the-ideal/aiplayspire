"""Fetch grid source identity, queued cleanup, RNG and pick order."""
import copy
import json
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import HeadlessCombatBridge, _fetch_evidence, _selection_lineage
from sts_ai_framework.headless.event_receipts import Stream
from sts_ai_framework.tests.test_headless_headbutt import headbutt_raw

EXE=Path(os.environ.get('BATTLE_ENGINE_WORKER',Path(__file__).resolve().parents[1]/'bin/battle-engine-worker.exe'))


def fetch_grid(name,up=False):
    raw=headbutt_raw();s=raw['combat']['snapshot'];p=s['player_state']
    v=s['grid_card_select_screen_state'];c=p['card_in_use']
    cost=4-int(up) if name=='Omniscience' else int(name=='Hologram')
    c.update(card_id=name,times_upgraded=int(up),upgraded=up,cost=cost,cost_for_turn=cost,
             exhaust=(name in ('Seek','Omniscience') or not up),target_enum_ordinal=2)
    s['cards_played_this_combat']=[{'card_index':-1,'card_state':copy.deepcopy(c)}]
    s['cards_played_this_turn_backup']=[copy.deepcopy(c)]
    v['action_queue'][0]['state'].update(card=copy.deepcopy(c),targetIndex=-2,exhaustCard=c['exhaust'])
    n=1+int(up and name=='Seek')
    if name in ('Seek','Omniscience'):
        p['draw_pile'],p['discard_pile']=p['discard_pile'],p['draw_pile']
        raw['player']['discardPile'],raw['player']['drawPile']=raw['player']['drawPile'],raw['player']['discardPile']
        # This fixture contains basic Bash/Strike cards; Java stable order
        # sorts BASIC cards by name without reversing equal UUIDs.
        order=sorted(range(len(v['group_cards'])),key=lambda i:v['group_cards'][i]['card_id'])
        v['group_cards']=[v['group_cards'][i] for i in order]
        raw['cardSelection']['options']=[raw['cardSelection']['options'][i] for i in order]
        raw['combat']['legal_actions']=[{'type':'GRID_SELECT','card_index':i,'uuid':c['uuid']} for i,c in enumerate(v['group_cards'])]
    v['num_cards']=n
    raw['interaction']['selection'].update(purpose=name.lower(),count=n)
    v['current_action_state']={'state_class':'savestate.actions.FetchCardGridActionState','state':{'source_card':name,'number_of_cards':n,'owner_index':-1}}
    return raw


@unittest.skipUnless(EXE.is_file(),'Build native worker')
class FetchGridTests(unittest.TestCase):
    def setUp(self):
        self.worker=Worker(EXE);self.addCleanup(self.worker.close)

    def runop(self,s,op='fingerprint',index=0):
        kw={'command':{'type':'GRID_SELECT','card_index':index}} if op=='predict' else {}
        return self.worker.execute(dict(snapshot=s,operation=op,**kw),1000)

    def test_hologram_moves_actual_uuid_without_liquid_memories_discount(self):
        for up in (False,True):
            raw=fetch_grid('Hologram',up);s=HeadlessCombatBridge().observe(raw,run_id='local',sequence=1).snapshot
            fp=self.runop(s);self.assertTrue(fp['supported'],fp)
            self.assertEqual(fp['selection'],_fetch_evidence(s))
            out=self.runop(s,'predict',1);self.assertTrue(out['supported'],out)
            c=s['player_state']['discard_pile'][1]
            self.assertEqual(out['expected_card_uuids']['hand'][-1],c['uuid'])
            self.assertEqual(out['expected_after']['hand'][-1]['turn_cost'],c['cost_for_turn'])
            dest='discard_pile' if up else 'exhaust_pile'
            self.assertEqual(out['expected_card_uuids'][dest][-1],s['player_state']['card_in_use']['uuid'])
            self.assertEqual(out['expected_exact_rng'],fp['exact_rng'])

    def test_omniscience_plays_original_and_copy_but_exhausts_only_original(self):
        s=fetch_grid('Omniscience')['combat']['snapshot']
        p=s['player_state'];v=s['grid_card_select_screen_state']
        result=self.runop(s);self.assertTrue(result['supported'],result)
        self.assertEqual(result['selection'],_fetch_evidence(s))
        i=next(i for i,c in enumerate(v['group_cards']) if c['card_id']=='Strike_R')
        v['group_cards'][i]['cost_for_turn']=0
        self.assertFalse(self.runop(s)['supported'])  # Same UUID, different source state.
        v['group_cards'][i]['cost_for_turn']=v['group_cards'][i]['cost']
        picked=v['group_cards'][i]['uuid']
        played=self.runop(s,'predict',i)
        self.assertTrue(played['supported'],played)
        self.assertEqual(played['expected_card_uuids']['exhaust_pile'][-2:],[p['card_in_use']['uuid'],picked])
        self.assertEqual(played['expected_after']['monsters'][0]['hp'],result['projection']['monsters'][0]['hp']-12)

    def test_omniscience_vault_clears_the_pending_purge_copy(self):
        s=fetch_grid('Omniscience')['combat']['snapshot'];p=s['player_state'];v=s['grid_card_select_screen_state']
        target=v['group_cards'][0];uid=target['uuid']
        for card in (target,next(c for c in p['draw_pile'] if c['uuid']==uid)):
            card.update(card_id='Vault',cost=3,cost_for_turn=3,exhaust=True,target_enum_ordinal=2)
        before=self.runop(s)
        played=self.runop(s,'predict',0);self.assertTrue(played['supported'],played)
        self.assertEqual(played['expected_exact_rng']['card_random']['counter'],
                         before['exact_rng']['card_random']['counter']+1)
        self.assertEqual(played['expected_card_uuids']['exhaust_pile'][-2:],
                         [p['card_in_use']['uuid'],uid])
        self.assertEqual(played['expected_after']['turn'],before['projection']['turn']+1)

    def test_nested_omniscience_binds_original_and_fresh_queued_copy(self):
        s=fetch_grid('Omniscience')['combat']['snapshot']
        player=s['player_state'];grid=s['grid_card_select_screen_state']
        target=dict(player['card_in_use'],uuid='00000000-0000-0000-0000-000000000982')
        player['draw_pile'].append(target);grid['group_cards'].insert(0,target)
        played=self.runop(s,'predict',0)
        self.assertTrue(played['supported'],played)
        expected=played['expected_selection']
        self.assertEqual(expected['pending_card']['uuid'],target['uuid'])
        clone,=expected['autoplay_queue']
        self.assertTrue(clone['purge'])
        self.assertIsNone(clone['uuid'])
        self.assertEqual(played['expected_card_uuids']['exhaust_pile'],[player['card_in_use']['uuid']])
        actual=copy.deepcopy(expected)
        actual['autoplay_queue'][0]['uuid']='00000000-0000-0000-0000-000000000984'
        self.assertTrue(_selection_lineage(s,s,expected,actual))
        actual['autoplay_queue'][0]['uuid']=target['uuid']
        self.assertFalse(_selection_lineage(s,s,expected,actual))

    def test_hologram_letter_opener_applies_once_after_picking(self):
        s=fetch_grid('Hologram')['combat']['snapshot']
        player=s['player_state'];grid=s['grid_card_select_screen_state']
        player['relics'].append({'relic_id':'Letter Opener','counter':0,'_private':{}})
        source=player['card_in_use']
        s['cards_played_this_turn_backup'][:0]=[
            dict(source,card_id='Defend_B',uuid=f'00000000-0000-0000-0000-00000000098{i}')
            for i in (5,6)]
        grid['action_queue'].insert(0,{'state_class':'savestate.actions.SelectionLetterOpenerActionState',
                                       'state':{'amount':5}})
        actual=self.runop(s)
        self.assertTrue(actual['supported'],actual)
        self.assertIn(['selection-letter-opener-v1',5],actual['selection_native_key'])
        result=self.runop(s,'predict',0)
        self.assertTrue(result['supported'],result)
        self.assertEqual(result['expected_after']['monsters'][0]['hp'],actual['projection']['monsters'][0]['hp']-5)
        self.assertEqual(result['expected_card_uuids']['hand'][-1],grid['group_cards'][0]['uuid'])
        grid['action_queue'][0]['state']['amount']=6
        self.assertFalse(self.runop(s)['supported'])

    def test_seek_plus_defers_letter_opener_until_the_second_pick(self):
        raw=fetch_grid('Seek',True);s=raw['combat']['snapshot'];p=s['player_state']
        grid=s['grid_card_select_screen_state']
        p['relics'].append({'relic_id':'Letter Opener','counter':0,'_private':{}})
        s['cards_played_this_turn_backup'][:0]=[
            dict(p['card_in_use'],card_id='Defend_B',uuid=f'00000000-0000-0000-0000-00000000088{i}')
            for i in (1,2)]
        grid['action_queue'].insert(0,{'state_class':'savestate.actions.SelectionLetterOpenerActionState',
                                       'state':{'amount':5}})
        fp=self.runop(s)
        self.assertTrue(fp['supported'],fp)
        first=self.runop(s,'predict',0)
        self.assertEqual(first['expected_after']['monsters'],fp['projection']['monsters'])
        grid['selected_cards']=[grid['group_cards'][0]]
        result=self.runop(s,'predict',1)
        self.assertTrue(result['supported'],result)
        self.assertEqual(result['expected_after']['monsters'][0]['hp'],fp['projection']['monsters'][0]['hp']-5)

    def test_secret_weapon_and_technique_use_randomized_matching_options(self):
        from uuid import UUID
        from sts_ai_framework.tests.test_headless_combat import combat_raw
        for name,picks in (('Secret Weapon',(0,2,4)),('Secret Technique',(1,3,5))):
            with self.subTest(name=name):
                s=combat_raw()['combat']['snapshot'];p=s['player_state']
                c=p['hand'][0]
                c.update(card_id=name,cost=0,cost_for_turn=0,target_enum_ordinal=2,exhaust=True)
                cards=[dict(c,card_id=identity,uuid=str(UUID(int=940+i)),cost=1,cost_for_turn=1,exhaust=False)
                       for i,identity in enumerate(('Strike_R','Defend_R','Pommel Strike','Shrug It Off','Twin Strike','Warcry'))]
                p['draw_pile']=cards
                r=self.worker.execute(dict(operation='predict',snapshot=s,
                        command={'type':'CARD','card_index':0,'monster_index':-1}),1000)
                self.assertTrue(r['supported'],r)
                rng=Stream(s['_headless_rng']['streams']['card_random'])
                order=[picks[0]]
                for idx in picks[1:]:order.insert(rng.bounded(len(order)),idx)
                self.assertEqual(r['expected_selection']['options'],[cards[i]['uuid'] for i in order])
                self.assertEqual(r['expected_exact_rng']['card_random'],rng.state())
                grid=fetch_grid('Seek')['combat']['snapshot']
                gp=grid['player_state'];gp['draw_pile']=cards
                gp['hand']=[]  # The fetch skill was the last card in hand.
                gp['relics'].append({'relic_id':'Unceasing Top','counter':-1,
                        '_private':{'UnceasingTop.canDraw':True,'UnceasingTop.disabledUntilEndOfTurn':False}})
                gp['card_in_use'].update(card_id=name)
                v=grid['grid_card_select_screen_state']
                v['current_action_state']['state']['source_card']=name
                v['action_queue'][0]['state']['card']=copy.deepcopy(gp['card_in_use'])
                v['group_cards']=[cards[i] for i in order]
                opened=self.runop(grid)
                self.assertTrue(opened['supported'],opened)
                self.assertEqual(opened['projection']['hand'],[])
                picked=self.runop(grid,'predict',0)
                self.assertTrue(picked['supported'],picked)
                self.assertEqual(picked['expected_card_uuids']['hand'][-1],cards[order[0]]['uuid'])
                v['group_cards'][-1]=cards[1 if name=='Secret Weapon' else 0]
                self.assertFalse(self.runop(grid)['supported'])

    def test_hologram_models_pending_hex_after_selection(self):
        s=fetch_grid('Hologram')['combat']['snapshot'];p=s['player_state'];v=s['grid_card_select_screen_state']
        p['creature']['powers'].append({'power_id':'Hex','amount':1})
        p['draw_pile']=[dict(p['discard_pile'][0],uuid='00000000-0000-0000-0000-000000000971')]
        v['action_queue'].insert(0,{'state_class':'savestate.actions.SelectionHexActionState','state':{'amount':1}})
        fp=self.runop(s);self.assertTrue(fp['supported'],fp)
        self.assertEqual(fp['selection'],_fetch_evidence(s))
        self.assertIn(['selection-skill-followup-v1',0,1,False],fp['selection_native_key'])
        out=self.runop(s,'predict',1);self.assertTrue(out['supported'],out)
        self.assertEqual([c['id'] for c in out['expected_after']['draw_pile']],['Dazed',p['draw_pile'][0]['card_id']])
        self.assertEqual(out['expected_card_uuids']['draw_pile'],[None,p['draw_pile'][0]['uuid']])
        streams=copy.deepcopy(fp['exact_rng']);rng=Stream(streams['card_random']);rng.bounded(1)
        streams['card_random']=rng.state()
        self.assertEqual(out['expected_exact_rng'],streams)
        v['action_queue'].reverse()
        self.assertFalse(self.runop(s)['supported'])

    def test_seek_plus_retains_selection_order_and_allows_deselection(self):
        raw=fetch_grid('Seek',True);s=HeadlessCombatBridge().observe(raw,run_id='local',sequence=1).snapshot
        v=s['grid_card_select_screen_state'];fp=self.runop(s);self.assertTrue(fp['supported'],fp)
        one=self.runop(s,'predict',2);self.assertTrue(one['supported'],one)
        self.assertEqual(one['expected_screen'],'GRID');self.assertEqual(one['expected_selection']['selected'],[v['group_cards'][2]['uuid']])
        v['selected_cards']=[copy.deepcopy(v['group_cards'][2])]
        imported=self.runop(s);self.assertTrue(imported['supported'],imported)
        self.assertEqual(imported['selection'],one['expected_selection']);self.assertEqual(imported['selection_native_key'],one['expected_selection_native_key'])
        undo=self.runop(s,'predict',2);self.assertEqual(undo['expected_selection']['selected'],[])
        end=self.runop(s,'predict',0);self.assertTrue(end['supported'],end)
        self.assertEqual(end['expected_screen'],'NONE')
        self.assertEqual(end['expected_card_uuids']['hand'][-2:],[v['group_cards'][i]['uuid'] for i in (2,0)])
        self.assertEqual(end['expected_card_uuids']['exhaust_pile'][-1],s['player_state']['card_in_use']['uuid'])
        self.assertEqual(end['expected_exact_rng'],fp['exact_rng'])

    def test_seek_plus_search_finishes_partial_grid_without_undo(self):
        raw=fetch_grid('Seek',True);s=HeadlessCombatBridge().observe(raw,run_id='local',sequence=1).snapshot
        grid=s['grid_card_select_screen_state'];options=grid['group_cards']
        first=self.worker.execute(dict(snapshot=s,search_profile='FAST',max_expansions=1),1000)
        self.assertTrue(first['supported'],first)
        self.assertEqual(first['expected_screen'],'GRID')
        picked=json.loads(first['commands'][0]['command'])['card_index']
        self.assertEqual(first['expected_selection']['selected'],[options[picked]['uuid']])
        grid['selected_cards']=[copy.deepcopy(options[picked])]
        imported=self.runop(s)
        self.assertTrue(imported['supported'],imported)
        for budget in (1,100):
            with self.subTest(budget=budget):
                search=self.worker.execute(dict(snapshot=s,search_profile='FAST',max_expansions=budget),1000)
                self.assertTrue(search['supported'],search)
                command=json.loads(search['commands'][0]['command'])
                self.assertEqual(command['type'],'GRID_SELECT')
                self.assertNotEqual(command['card_index'],picked)
                self.assertEqual(search['expected_screen'],'NONE')
                self.assertEqual(search['expected_card_uuids']['hand'][-2:],
                                 [options[i]['uuid'] for i in (picked,command['card_index'])])
                self.assertEqual(search['expected_exact_rng'],imported['exact_rng'])

    def test_seek_open_stable_rarity_names_status_and_no_rng(self):
        from uuid import UUID
        from sts_ai_framework.tests.test_headless_combat import combat_raw
        s=combat_raw()['combat']['snapshot'];p=s['player_state']
        p['chosen_class_name']='DEFECT';seek=p['hand'][0];seek.update(card_id='Seek',cost=0,cost_for_turn=0,exhaust=True)
        cards=[]
        for i,(name,cost) in enumerate([('Strike_B',1),('Cold Snap',1),('Defend_B',1),('Dazed',-2),
                                        ('Doubt',-2),('Cold Snap',1),('Glacier',2)]):
            c=copy.deepcopy(seek);c.update(card_id=name,cost=cost,cost_for_turn=cost,uuid=str(UUID(int=900+i)),exhaust=False)
            cards.append(c)
        p['draw_pile']=cards
        op=dict(operation='predict',snapshot=s,command={'type':'CARD','card_index':0,'monster_index':-1})
        r=self.worker.execute(op,1000);self.assertTrue(r['supported'],r)
        # CURSE, UNCOMMON, COMMON, BASIC, then STATUS; both Cold Snaps
        # retain their original object order, and Defend precedes Strike.
        self.assertEqual(r['expected_selection']['options'],[cards[i]['uuid'] for i in (4,6,1,5,2,0,3)])
        self.assertEqual(r['expected_exact_rng']['card_random'],s['_headless_rng']['streams']['card_random'])
        s2=copy.deepcopy(s);short=s2['player_state'];short['hand'][0].update(times_upgraded=1,upgraded=True)
        short['draw_pile']=[cards[0],cards[1]] # would be reordered by a grid
        out=self.worker.execute(dict(op,snapshot=s2),1000);self.assertTrue(out['supported'],out)
        self.assertEqual(out['expected_screen'],'NONE')
        self.assertEqual(out['expected_card_uuids']['hand'][-2:],[cards[0]['uuid'],cards[1]['uuid']])
        self.assertEqual(out['expected_exact_rng']['card_random'],s['_headless_rng']['streams']['card_random'])

    def test_missing_source_uuid_extra_cleanup_wrong_count_rejected(self):
        for change in ('option_uuid','extra_action','exhaust','owner','partial_duplicate','count','tied_order'):
            raw=fetch_grid('Seek',True);s=raw['combat']['snapshot'];v=s['grid_card_select_screen_state']
            if change=='option_uuid':v['group_cards'][0]=copy.deepcopy(v['group_cards'][1])
            if change=='extra_action':v['action_queue'].append(copy.deepcopy(v['action_queue'][0]))
            if change=='exhaust':v['action_queue'][0]['state']['exhaustCard']=False
            if change=='owner':v['action_queue'][0]['state']['targetIndex']=0
            if change=='partial_duplicate':v['selected_cards']=[v['group_cards'][0]]*2
            if change=='count':v['num_cards']=1
            if change=='tied_order':
                # Two visually identical Strikes, same pool membership, reversed identities.
                v['group_cards'][1],v['group_cards'][2]=v['group_cards'][2],v['group_cards'][1]
            self.assertFalse(self.runop(s)['supported'],change)
