import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import http_acceptance
import patch_upstream

HERE = Path(__file__).resolve().parent


def fixture_state():
    return {
        "screen_type": "MAIN_MENU", "choice_list": [], "can_proceed": False, "can_cancel": False,
        "headless": {"engine_phase": "BOOT", "read_only": True, "floor": 0, "act": 0, "graphics_type": "Mock",
                     **{name: False for name in ("run_started", "collection_enabled", "player_initialized", "map_initialized",
                                                 "room_initialized", "seed_initialized", "rng_streams_initialized", "gl_present")}},
    }


class PatchTest(unittest.TestCase):
    def fixture(self, root):
        (root / "install").mkdir()
        (root / "reports").mkdir()
        archive = root / "install/upstream-source.tar.gz"
        with tarfile.open(archive, "w:gz") as stream:
            for name, edits in patch_upstream.EDITS.items():
                source = "\n".join(before for before, _ in edits).encode()
                member = tarfile.TarInfo("pin/" + name)
                member.size = len(source)
                stream.addfile(member, io.BytesIO(source))
                target = root / "upstream" / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source)
        return archive

    def test_patch_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = self.fixture(root)
            with patch.object(patch_upstream, "ARCHIVE_SHA256", hashlib.sha256(archive.read_bytes()).hexdigest()):
                self.assertEqual(patch_upstream.apply(root)["files_written"], 2)
                self.assertEqual(patch_upstream.apply(root)["files_written"], 0)
            self.assertIn("Phase.BOOT", (root / "upstream" / (patch_upstream.PREFIX + "GameKernel.java")).read_text())

    def test_drift_rejects_all_writes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = self.fixture(root)
            first, second = [root / "upstream" / name for name in patch_upstream.EDITS]
            original = first.read_bytes()
            second.write_bytes(b"unexpected user change")
            with patch.object(patch_upstream, "ARCHIVE_SHA256", hashlib.sha256(archive.read_bytes()).hexdigest()):
                with self.assertRaisesRegex(ValueError, "Unexpected source change"):
                    patch_upstream.apply(root)
            self.assertEqual(first.read_bytes(), original)
            self.assertEqual(second.read_bytes(), b"unexpected user change")

    def test_archive_fingerprint_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            with self.assertRaisesRegex(ValueError, "archive fingerprint"):
                patch_upstream.apply(root)

    def test_context_must_be_unique(self):
        name = next(iter(patch_upstream.EDITS))
        with self.assertRaisesRegex(ValueError, "match once"):
            patch_upstream.patched(name, b"unrelated")


class ReadonlyContractTest(unittest.TestCase):
    def test_compatible_with_current_model(self):
        if (HERE.parents[1] / "sts_ai_framework/models.py").is_file():
            sys.path.insert(0, str(HERE.parents[1]))
        # Server acceptance uses the same three source files in an isolated contract directory.
        from sts_ai_framework.models import GameState
        data = fixture_state()
        http_acceptance.validate(data)
        model = GameState(**data)
        self.assertEqual(model.screen_type, "MAIN_MENU")
        for field in ("player", "floor", "act", "character", "ascension_level", "seed_group"):
            self.assertIsNone(getattr(model, field))
        self.assertFalse(model.choice_list or model.can_proceed or model.can_cancel)

    def test_guard_rejects_started_run_or_graphics(self):
        for key in ("run_started", "collection_enabled", "player_initialized", "map_initialized", "room_initialized",
                    "seed_initialized", "rng_streams_initialized", "gl_present"):
            data = fixture_state()
            data["headless"][key] = True
            with self.assertRaises(AssertionError):
                http_acceptance.validate(data)
        for key in ("seed", "rng_state", "player", "floor", "character", "ascension_level"):
            with self.assertRaises(AssertionError):
                http_acceptance.validate({**fixture_state(), key: 0})

    def test_no_action_channel_and_loopback_only(self):
        source = (HERE / "ReadonlyStateServer.java").read_text()
        self.assertIn('new InetSocketAddress("127.0.0.1", port)', source)
        self.assertNotIn("HeadlessCli", source)
        self.assertNotIn(".newRun(", source)
        self.assertNotIn(".step(", source)
        self.assertNotIn("ActionController", source)
        self.assertIn("NoRunGuard.verify()", source)

    def test_systemd_no_autostart_and_read_only_scope(self):
        unit = (HERE / "headless-spire-state.service").read_text()
        for text in ("Restart=no", "MemoryMax=768M", "MemorySwapMax=0", "CPUQuota=100%", "NoNewPrivileges=yes",
                     "ProtectSystem=strict", "IPAddressDeny=any", "IPAddressAllow=localhost"):
            self.assertIn(text, unit)
        self.assertNotIn("[Install]", unit)
        self.assertNotIn("sts_ai_framework", unit)
        self.assertNotIn("battle_engine", unit)

    @unittest.skipUnless(sys.platform.startswith("linux"), "Linux start-script guard; also run on server")
    def test_gate_blocks_start_before_any_process(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "tools").mkdir()
            script = root / "tools/start-state.sh"
            shutil.copyfile(HERE / script.name, script)
            gate = root / "ALLOW_COLLECTION"
            for symlink in (False, True):
                if symlink:
                    gate.symlink_to(root / "absent-target")
                else:
                    gate.touch()
                result = subprocess.run(["bash", str(script)], capture_output=True, text=True)
                self.assertEqual(result.returncode, 78, result.stderr)
                self.assertFalse((root / "saves").exists())
                gate.unlink()


if __name__ == "__main__":
    unittest.main()
