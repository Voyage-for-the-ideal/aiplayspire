import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from battle_engine.tools import a10_campaign as c
from battle_engine.tools.balanced_ab import ROLES, ROOMS
from battle_engine.tools.oracle import isolated_java_env


class CampaignTests(unittest.TestCase):
    def test_game_jvms_use_separate_temp_roots(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            runtimes=[root/"left game",root/"right game"]
            for path in runtimes:
                path.mkdir()
            a,b=(isolated_java_env(path) for path in runtimes)
            self.assertNotEqual(a["TEMP"],b["TEMP"])

    def fixture(self, root):
        mods=root/"mods"
        mods.mkdir()
        for path in (root/"worker",root/"checkpoint",mods/"BattleAiMod.jar"):
            path.write_text("fixture")
        return SimpleNamespace(output=root/"battle_engine"/"out"/"evaluation",source=[root],game_dir=root,worker=root/"worker",
                               checkpoint=root/"checkpoint",exclude_manifest=[],rounds=1,runs=1,chunk_seconds=2)

    def test_never_launches_formal_while_capture_has_gaps(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            args=self.fixture(root)
            incomplete={r:{m:0 for m in ROOMS} for r in ROLES}
            with patch.object(c,"ROOT",root), \
                 patch.object(c,"inventory"), patch.object(c,"coverage",return_value=incomplete), \
                 patch.object(c,"run_child",return_value=0) as child, patch.object(c,"MIN_FREE_BYTES",0):
                self.assertEqual(c.campaign(args),1)
            status=json.loads((args.output/"status.json").read_text())
            self.assertEqual(status["stage"],"needs_review")
            self.assertEqual([call.args[3] for call in child.call_args_list],
                             ["battle_engine.tools.a10_capture"]*4)

    def test_freeze_does_not_launch_java_search_or_promote(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            args=self.fixture(root)
            complete={r:{m:23 for m in ROOMS} for r in ROLES}
            report=dict(gates={"deployment":dict(cpp_invalid=0,java_invalid=1)})
            with patch.object(c,"ROOT",root), \
                 patch.object(c,"inventory"), patch.object(c,"coverage",return_value=complete), \
                 patch.object(c,"run_child",return_value=0) as child, patch.object(c,"MIN_FREE_BYTES",0), \
                 patch.object(c,"freeze",return_value={"cases":[]}), patch.object(c,"manifest_cases"), \
                 patch.object(c,"read_report",return_value=report):
                self.assertEqual(c.campaign(args),0)
            child.assert_not_called()
            status=json.loads((args.output/"status.json").read_text())
            self.assertEqual(status["stage"],"corpus_frozen")
            self.assertNotIn("formal_report",status)
