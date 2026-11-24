import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock

from sts_ai_framework.headless import live_confirmation as live
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_combat import combat_raw
from sts_ai_framework.tests.test_headless_headbutt import headbutt_raw


def state():
    raw=combat_raw();raw['combat_receipt']={'rng':copy.deepcopy(raw['combat']['snapshot']['_headless_rng'])}
    return raw


class LiveConfirmationTest(unittest.TestCase):
    def test_five_targets_use_original_distinct_seeds(self):
        self.assertEqual([t[2] for t in live.TARGETS],[5050109,5050121,5050111,5050114,5050117])
        self.assertEqual([t[3] for t in live.TARGETS],['headbutt','headbutt','rip_and_tear','mirror','orrery'])

    def test_seeded_uuid_match_is_bijective_and_persistent(self):
        matcher=live.SeededMatcher();matcher.compare({'uuid':'old'},{'uuid':'fresh'})
        matcher.compare({'uuid':'old'},{'uuid':'fresh'})
        with self.assertRaises(ValueError):matcher.compare({'uuid':'old'},{'uuid':'other'})
        with self.assertRaises(ValueError):matcher.compare({'uuid':'another'},{'uuid':'fresh'})

    def test_matching_ignores_cosmetic_math_but_checks_all_gameplay_streams(self):
        old=state();new=copy.deepcopy(old)
        new['combat_receipt']['rng']['math']['state0']='0000000000000123'
        live.SeededMatcher().match(old,new)
        for name in old['combat_receipt']['rng']['streams']:
            bad=copy.deepcopy(new);bad['combat_receipt']['rng']['streams'][name]['counter']+=1
            with self.subTest(name=name),self.assertRaises(ValueError):live.SeededMatcher().match(old,bad)

    def test_missing_rng_and_semantic_state_differences_stop_confirmation(self):
        old=state();new=copy.deepcopy(old);new['player']['gold']+=1
        with self.assertRaises(ValueError):live.SeededMatcher().match(old,new)
        del new['combat_receipt']
        with self.assertRaises((ValueError,KeyError,HeadlessContractError)):live.SeededMatcher().match(old,new)

    def test_orrery_requires_actual_skip_and_single_source_card_reward(self):
        raw={'phase':'REWARD','room':'ShopRoom','interaction':{'rewards':[{'type':'CARD'}]}}
        self.assertTrue(live.orrery_ready(raw,'skipRewardCard'))
        self.assertFalse(live.orrery_ready(raw,'openCardReward'))
        raw['interaction']['rewards'].append({'type':'CARD'})
        self.assertFalse(live.orrery_ready(raw,'skipRewardCard'))

    def test_headbutt_goal_requires_open_and_resolve_exact_selected_uuid(self):
        before=state();before['combat']['snapshot']['player_state']['hand'][0]['card_id']='Headbutt'
        middle=headbutt_raw();selected=middle['combat']['snapshot']['grid_card_select_screen_state']['group_cards'][0]
        after=state();after['combat']['snapshot']['player_state']['draw_pile'].append(copy.deepcopy(selected))
        steps=[{'effect':'APPLIED','method':'playCard','args':[0,0],'after':middle},
               {'effect':'APPLIED','method':'chooseGridCard','args':[0],'after':after}]
        self.assertTrue(live.goal_check('headbutt',before,steps))
        self.assertFalse(live.goal_check('headbutt',before,steps[:1]))
        after['combat']['snapshot']['player_state']['draw_pile'][-1]['uuid']='wrong'
        self.assertFalse(live.goal_check('headbutt',before,steps))

    def test_rip_and_tear_requires_unspecified_target_and_two_random_draws(self):
        before=state();before['combat']['snapshot']['player_state']['hand'][0]['card_id']='Rip and Tear'
        after=copy.deepcopy(before);after['combat_receipt']['rng']['streams']['card_random']['counter']+=2
        step={'effect':'APPLIED','method':'playCard','args':[0,-1],'after':after}
        self.assertTrue(live.goal_check('rip_and_tear',before,[step]))
        step['args'][1]=0;self.assertFalse(live.goal_check('rip_and_tear',before,[step]))
        step['args'][1]=-1;after['combat_receipt']['rng']['streams']['card_random']['counter']+=1
        self.assertFalse(live.goal_check('rip_and_tear',before,[step]))

    def test_orrery_goal_checks_actual_return_and_rng(self):
        before=state();before.update(room='ShopRoom');before['interaction']={'return_shop':True}
        after=copy.deepcopy(before);after['phase']='SHOP'
        step={'effect':'APPLIED','method':'proceed','args':[],'after':after}
        self.assertTrue(live.goal_check('orrery',before,[step]))
        after['combat_receipt']['rng']['streams']['merchant']['counter']+=1
        self.assertFalse(live.goal_check('orrery',before,[step]))

    @patch.object(live.signal,'signal')
    @patch.object(live,'check_permits')
    @patch.object(live,'evidence')
    @patch.object(live,'HeadlessCollector')
    @patch.object(live,'Confirmation')
    def test_difference_or_wrapper_exception_continues_all_five(self,confirmation,collector,evidence,permits,signals):
        cases=[{'case_id':c,'role':r,'seed':s,'goal':g} for c,r,s,g in live.TARGETS];evidence.return_value=cases
        confirmation.return_value.episode.side_effect=[RuntimeError('difference')]+[
            {**case,'status':'verified'} for case in cases[1:]]
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);(root/'pilot/reports').mkdir(parents=True)
            self.assertEqual(live.main(['--batch',str(root)]),1)
            status=json.loads((root/'pilot/reports/confirmation-status.json').read_text())
            self.assertEqual(len(status['attempts']),5)
            self.assertEqual(status['status'],'finished_with_differences')
            self.assertTrue(Path(status['attempts'][0]['diagnostic']).is_file())
            self.assertEqual(confirmation.return_value.episode.call_count,5)

    @patch.object(live.signal,'signal')
    @patch.object(live,'check_permits')
    @patch.object(live,'evidence')
    @patch.object(live,'HeadlessCollector')
    @patch.object(live,'Confirmation')
    def test_operational_stop_prevents_starting_next_case(self,confirmation,collector,evidence,permits,signals):
        cases=[{'case_id':c,'role':r,'seed':s,'goal':g} for c,r,s,g in live.TARGETS];evidence.return_value=cases
        permits.side_effect=[None,ValueError('permit revoked')]
        confirmation.return_value.episode.return_value={**cases[0],'status':'verified'}
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);(root/'pilot/reports').mkdir(parents=True)
            self.assertEqual(live.main(['--batch',str(root)]),1)
            self.assertEqual(confirmation.return_value.episode.call_count,1)
            self.assertEqual(json.loads((root/'pilot/reports/confirmation-status.json').read_text())['status'],'stopped')

    @patch.object(live,'evidence',return_value=[])
    def test_independent_gate_rejects_missing_or_partial_confirmations(self,evidence):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);(root/'pilot/reports').mkdir(parents=True);(root/'reports').mkdir()
            (root/'pilot/reports/confirmation-status.json').write_text('{"attempts":[{"status":"verified"}]}')
            self.assertEqual(live.main(['--batch',str(root),'--verify']),1)
            self.assertFalse(json.loads((root/'reports/pilot-gate.json').read_text())['passed'])

    @patch.object(live.signal,'signal')
    def test_single_submission_never_restarts_existing_pilot(self,signals):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);(root/'pilot/reports').mkdir(parents=True)
            path=root/'pilot/reports/confirmation-status.json';path.write_text('{"attempts":[]}')
            with self.assertRaisesRegex(ValueError,'single-submission'):live.main(['--batch',str(root)])
            self.assertEqual(path.read_text(),'{"attempts":[]}')
