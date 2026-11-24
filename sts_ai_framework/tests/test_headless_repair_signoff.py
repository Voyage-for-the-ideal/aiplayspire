import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from sts_ai_framework.headless.batch160_next import _scheduler_overlay
from sts_ai_framework.headless.layout import module_path
from sts_ai_framework.headless.repair_ledger import first_diff
from sts_ai_framework.headless.repair_signoff import CaseMatcher,runtime,target_command


class SignedRepairEvidenceTests(unittest.TestCase):
    def test_exact_original_potion_slot_and_target(self):
        diag={'prediction':{'commands':[{'command':json.dumps({'type':'POTION','potion_index':0,'monster_index':1})}]}}
        step={'native_method':'usePotion','native_args':[0,1]}
        target_command(diag,step)
        for args in ([1,1],[0,0],[0,-1]):
            with self.subTest(args=args), self.assertRaises(ValueError):
                target_command(diag,dict(step,native_args=args))

    def test_original_menu_action_and_negative_field_path(self):
        diag={'method':'chooseRewardCard','args':[2]}
        with self.assertRaises(ValueError):target_command(diag,{'native_method':'skipRewardCard','native_args':[]})
        self.assertEqual(first_diff({'target':[10,20]},{'target':[10,19]}),'/target/1: 20 != 19')

    def test_known_uuid_must_agree_across_public_hand_and_driver(self):
        data={'turn':1,'room_class':'MonsterRoom','room_phase':'COMBAT','active_monsters':1,'player_dead':False,
              'battle_over':False,'played_card_uuids':['a']}
        old={'combat_receipt':data,'driver_receipt':{'card_uuid':'a'}}
        new={'combat_receipt':dict(data,played_card_uuids=['b']),'driver_receipt':{'card_uuid':'different'}}
        with patch('sts_ai_framework.headless.live_confirmation.SeededMatcher.match'):
            with self.assertRaisesRegex(ValueError,'identity'):CaseMatcher().match(old,new)

    def test_runtime_identity_preserves_the_actual_flat_or_package_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            native=root/'native-bin/battle-engine-worker'
            native.parent.mkdir()
            native.write_bytes(b'worker')
            repo=root/'repo'
            flat=module_path(repo,'agent')
            flat.parent.mkdir(parents=True)
            flat.write_bytes(b'flat agent')
            old=runtime(root)
            self.assertEqual(set(old),{'native-bin/battle-engine-worker','repo/sts_ai_framework/headless_agent.py'})
            self.assertEqual(old['repo/sts_ai_framework/headless_agent.py'],hashlib.sha256(b'flat agent').hexdigest())
            self.assertIn('repo/sts_ai_framework/headless_batch160.py',_scheduler_overlay(root,repo))
            package=repo/'sts_ai_framework/headless'
            package.mkdir()
            (package/'__init__.py').write_bytes(b'')
            module_path(repo,'agent').write_bytes(b'moved agent')
            self.assertEqual(set(runtime(root)),{'native-bin/battle-engine-worker',
                'repo/sts_ai_framework/headless/__init__.py','repo/sts_ai_framework/headless/agent.py'})
            self.assertIn('repo/sts_ai_framework/headless/batch160.py',_scheduler_overlay(root,repo))
            with self.assertRaisesRegex(ValueError,'different module layouts'):
                _scheduler_overlay(root,root/'legacy-runner-repo')
