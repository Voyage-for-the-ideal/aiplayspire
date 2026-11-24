import importlib.util
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch
import zipfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from preflight import MODS, PROJECT_MODS, check_jre, check_profile, inspect_mods, sha256, verify_release


class DeploymentTests(unittest.TestCase):
    def test_prepare_preserves_game_reserialized_json_and_properties(self):
        spec = importlib.util.spec_from_file_location("prepare_runtime", HERE / "prepare-runtime.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            profile = root / "mod_lists.json"
            profile.write_text('{"lists": {"Fixture": []}, "defaultList": "Fixture"}')
            before = profile.read_bytes()
            module.write_initial(profile, json.dumps({"defaultList": "Fixture", "lists": {"Fixture": []}}, indent=2) + "\n")
            self.assertEqual(profile.read_bytes(), before)
            options = root / "options.properties"
            options.write_text("#game timestamp\nbackend=external\nsearch_profile=FAST\nextra=preserved\n")
            before = options.read_bytes()
            module.write_initial(options, "search_profile=FAST\nbackend=external\n")
            self.assertEqual(options.read_bytes(), before)
            with self.assertRaisesRegex(ValueError, "existing configuration differs"):
                module.write_initial(options, "search_profile=DEEP\n")

    def test_idle_checker_rejects_profile_dialog_and_any_run(self):
        spec = importlib.util.spec_from_file_location("idle_acceptance", HERE / "idle-acceptance.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.assert_menu({"screen_type": "MAIN_MENU", "choice_list": ["play"]})
        for state in ({"screen_type": "MAIN_MENU", "choice_list": []},
                      {"screen_type": "CHAR_SELECT", "choice_list": ["play"]},
                      {"screen_type": "MAIN_MENU", "choice_list": ["play"], "floor": 1},
                      {"screen_type": "MAIN_MENU", "choice_list": ["play"], "seed_group": "fixture"}):
            with self.subTest(state=state), self.assertRaises(ValueError):
                module.assert_menu(state)

    def test_menu_profile_bootstrap_only_initializes_an_empty_selector(self):
        spec = importlib.util.spec_from_file_location("prepare_runtime", HERE / "prepare-runtime.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as tmp:
            game = Path(tmp)
            self.assertTrue(module.initialize_menu_profile(game, "Fixture"))
            path = game / "preferences/STSSaveSlots"
            self.assertEqual(json.loads(path.read_text()), {"DEFAULT_SLOT": "0", "PROFILE_NAME": "Fixture"})
            before = path.read_bytes()
            self.assertFalse(module.initialize_menu_profile(game, "Other"))
            self.assertEqual(path.read_bytes(), before)
            self.assertFalse((game / "saves").exists())
        with tempfile.TemporaryDirectory() as tmp:
            game = Path(tmp)
            (game / "saves").mkdir()
            (game / "saves/run.autosave").write_text("fixture")
            with self.assertRaisesRegex(ValueError, "existing run files"):
                module.initialize_menu_profile(game, "Fixture")

    @unittest.skipUnless(platform.system() == "Linux", "requires native Linux symlinks")
    def test_runtime_prepares_jre_discovery_and_rejects_existing_other_java(self):
        spec = importlib.util.spec_from_file_location("prepare_runtime", HERE / "prepare-runtime.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            game, java = root / "game", root / "system-java"
            java.write_text("fixture")
            java.chmod(0o755)
            with self.assertRaisesRegex(ValueError, "jre/bin/java"):
                check_jre(game, java)
            module.ensure_jre(game, java)
            module.ensure_jre(game, java)
            check_jre(game, java)
            other = root / "other-java"
            other.write_text("different")
            with self.assertRaisesRegex(ValueError, "existing JRE differs"):
                module.ensure_jre(game, other)
            self.assertEqual((game / "jre/bin/java").resolve(), java.resolve())

    def test_release_fingerprints_detect_changed_files_and_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            file = root / "source.py"
            file.write_text("source")
            manifest = root / "deployment-manifest.json"
            manifest.write_text(json.dumps({"files": {"source.py": sha256(file)}}))
            self.assertEqual(verify_release(root), sha256(manifest))
            file.write_text("changed")
            with self.assertRaisesRegex(ValueError, "fingerprint"):
                verify_release(root)
            manifest.write_text(json.dumps({"files": {"../outside": "bad"}}))
            with self.assertRaisesRegex(ValueError, "fingerprint"):
                verify_release(root)

    def test_mod_inventory_checks_ids_and_source_versions(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            game = root / "game"
            (game / "mods").mkdir(parents=True)
            for name, modid in MODS.items():
                with zipfile.ZipFile(game / "mods" / name, "w") as jar:
                    jar.writestr("ModTheSpire.json", json.dumps({"modid": modid, "version": "1.0.0"}))
                if name in PROJECT_MODS:
                    path = root / PROJECT_MODS[name] / "pom.xml"
                    path.parent.mkdir()
                    path.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>1.0.0</version></project>')
            info = root / "StSCommunicationMod/src/main/resources/ModTheSpire.json"
            info.parent.mkdir(parents=True)
            info.write_text('{"version":"1.0.0"}')
            self.assertEqual(set(inspect_mods(root, game)), set(MODS))
            info.write_text('{"version":"2.0.0"}')
            with self.assertRaisesRegex(ValueError, "version mismatch"):
                inspect_mods(root, game)

    def test_profile_requires_exact_mods_fast_and_small_display(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = root / ".config/ModTheSpire"
            (config / "BattleAIMod").mkdir(parents=True)
            (config / "mod_lists.json").write_text(json.dumps({"lists": {"Collector": list(MODS)}}))
            (config / "BattleAIMod/options.properties").write_text("auto_start_ai=true\nsearch_profile=FAST\nbackend=external\nclient_port=5125\n")
            (root / "info.displayconfig").write_text("1024\n576\n30\nfalse\nfalse\nfalse\n")
            c = {"home": root, "game": root, "profile": "Collector", "port": 5125}
            check_profile(c)
            (config / "mod_lists.json").write_text('{"lists":{"Collector":["BaseMod.jar"]}}')
            with self.assertRaisesRegex(ValueError, "six required"):
                check_profile(c)

    def test_retired_gateway_has_no_runtime_entry(self):
        for path in (HERE.parents[1] / "battle_engine/start-game.sh", HERE / "run-collector.sh",
                     HERE / "systemd/aiplayspire-gateway.service", HERE / "install-systemd.sh"):
            self.assertFalse(path.exists(), path)

    def test_gateway_readiness_uses_real_json_protocol_and_rejects_wrong_mode(self):
        spec = importlib.util.spec_from_file_location("wait_ready", HERE / "wait-ready.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        connection = MagicMock()
        with patch.object(module.socket, "create_connection", return_value=connection), \
             patch.object(module.protocol, "send") as send, \
             patch.object(module.protocol, "receive", return_value='{"type":"PONG","ready":true,"mode":"cpp-only"}'):
            module.probe("gateway")
            self.assertEqual(json.loads(send.call_args.args[1]), {"type": "PING"})
        with patch.object(module.socket, "create_connection", return_value=connection), \
             patch.object(module.protocol, "send"), \
             patch.object(module.protocol, "receive", return_value='{"type":"PONG","ready":true,"mode":"java-enabled"}'):
            with self.assertRaises(ValueError):
                module.probe("gateway")

    def test_archived_units_record_the_original_launch_gates(self):
        game = (HERE / "systemd/aiplayspire-game.service.historical").read_text()
        collector = (HERE / "systemd/aiplayspire-collector.service.historical").read_text()
        self.assertIn("ConditionPathExists=/home/ubuntu/aiplayspire-runtime/ALLOW_GAME", game)
        self.assertIn("ConditionPathExists=/home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION", collector)
        self.assertIn("KillSignal=SIGINT", collector)
        self.assertIn("KillMode=control-group", collector)
        self.assertIn("BindsTo=aiplayspire-game.service", collector)
        self.assertIn("MemoryMax=2560M", (HERE / "systemd/aiplayspire.slice").read_text())


if __name__ == "__main__":
    unittest.main()
