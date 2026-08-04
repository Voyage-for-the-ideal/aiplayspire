from pathlib import Path
import tempfile
import unittest

from battle_test.manifest import ManifestError, load_manifest, validate_search_response


class ManifestTest(unittest.TestCase):
    def test_project_manifest_has_all_fixtures(self):
        manifest = Path(__file__).parents[1] / "fixtures" / "manifest.json"
        fixtures = load_manifest(manifest)
        self.assertEqual(len(fixtures), 12)
        self.assertEqual({fixture.character for fixture in fixtures}, {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"})

    def test_turn_metrics_must_balance(self):
        response = {"type": "COMMAND_LIST", "commands": [], "stop_reason": "COMPLETED", "battle_complete": True, "should_replan": False, "final_state_key": "a" * 64,
                    "metrics": {"generated_turn_states": 3, "unique_turn_states": 1, "duplicate_turn_states": 1}}
        self.assertIn("unique_turn_states + duplicate_turn_states must equal generated_turn_states", validate_search_response(response))

    def test_incomplete_result_with_progress_must_request_replanning(self):
        response = self.valid_response()
        response.update({"battle_complete": False, "should_replan": False,
                         "stop_reason": "SEARCH_EXHAUSTED", "commands": [None, {"command": "{}"}]})

        self.assertIn("incomplete response with command progress must request replanning",
                      validate_search_response(response))

    def test_incomplete_result_without_progress_must_not_request_replanning(self):
        response = self.valid_response()
        response.update({"battle_complete": False, "should_replan": True,
                         "stop_reason": "EXPANSION_LIMIT", "commands": [None]})

        self.assertIn("incomplete response without command progress must not request replanning",
                      validate_search_response(response))

    def test_rejects_wrong_fixture_count(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text('{"fixtures": []}', encoding="utf-8")
            with self.assertRaises(ManifestError):
                load_manifest(path)

    @staticmethod
    def valid_response():
        return {
            "type": "COMMAND_LIST",
            "commands": [],
            "stop_reason": "VICTORY",
            "battle_complete": True,
            "should_replan": False,
            "final_state_key": "a" * 64,
            "metrics": {
                "generated_turn_states": 1,
                "unique_turn_states": 1,
                "duplicate_turn_states": 0,
            },
        }
