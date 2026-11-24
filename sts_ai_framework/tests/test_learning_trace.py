import copy
import json
from pathlib import Path
import tempfile
import unittest

from selectcard.src.live_contract import value_state
from selectcard.src.live_smoke import fixture_state, record_fixture, fixture_seed
from sts_ai_framework.learning_trace import LearningRecorder, LearningGameClient
from sts_ai_framework.models import ActionType, GameAction
from sts_ai_framework.routing.telemetry import RouteForecastTracker
from sts_ai_framework.llm_agent_parts.value_payload import value_state_payload


def read_rows(directory):
    return [json.loads(line) for path in sorted(Path(directory).glob("*.jsonl"))
            for line in path.read_text(encoding="utf-8").splitlines()]


class LearningTraceTests(unittest.TestCase):
    def test_ack_retry_confirmation_and_censoring(self):
        with tempfile.TemporaryDirectory() as root:
            recorder = LearningRecorder(Path(root) / "trace")
            before = fixture_state()
            recorder.observe(before, 0)
            action = GameAction(type=ActionType.CHOOSE, choice_index=0)
            recorder.begin(before, action, 0)
            recorder.begin(before, action, 1)
            recorder.observe(before, 2)
            self.assertEqual(len([r for r in read_rows(recorder.directory) if r["record_type"] == "decision_submitted"]), 1)
            self.assertFalse(any(r["record_type"] == "decision" for r in read_rows(recorder.directory)))
            recorder.observe(before, 16)
            recorder.close()
            rows = read_rows(recorder.directory)
            self.assertTrue(any(r["record_type"] == "unconfirmed" for r in rows))
            self.assertIsNone(rows[-1]["outcome"]["heart_victory"])
            self.assertEqual(rows[-1]["outcome"]["terminal_floor"], 5)

    def test_room_signed_recovery_and_seedless_terminal_death(self):
        with tempfile.TemporaryDirectory() as root:
            recorder = LearningRecorder(Path(root) / "trace")
            record_fixture(recorder, fixture_seed("train"), heart=True)
            record_fixture(recorder, fixture_seed("val"), died=True)
            rows = read_rows(recorder.directory)
            rooms = [r for r in rows if r["record_type"] == "room_transition"]
            self.assertEqual({r["status"] for r in rooms}, {"survived", "died"})
            alive = next(r for r in rooms if r["status"] == "survived")
            self.assertEqual(alive["after"]["resources"]["hp"] - alive["before"]["resources"]["hp"], 5)
            self.assertIsNotNone(alive["combat_end"])
            ends = [r["outcome"] for r in rows if r["record_type"] == "run_end"]
            self.assertTrue(any(o["heart_victory"] and o["terminal_floor"] == 57 and o["terminal_floor_raw"] == 56 for o in ends))
            self.assertTrue(any(o["termination_reason"] == "defeat" and o["terminal_floor"] == 6 for o in ends))
            self.assertEqual(recorder.completed_runs, 2)

    def test_macro_proxy_uses_fresh_state_and_rejected_submission_has_no_label(self):
        class Client:
            current = fixture_state()
            accepted = False
            def get_state(self):
                return self.current
            def submit_action(self, action):
                return self.accepted, {}, None
        with tempfile.TemporaryDirectory() as root:
            recorder = LearningRecorder(Path(root) / "trace")
            original = Client()
            client = LearningGameClient(original, recorder)
            action = GameAction(type=ActionType.CHOOSE, choice_index=0)
            client.submit_action(action)
            self.assertIsNone(recorder.pending)
            original.accepted = True
            client.submit_action(action)
            self.assertIsNotNone(recorder.pending)
            recorder.close()

    def test_logs_only_public_value_features_and_actual_ascension(self):
        payload = value_state_payload(fixture_state(ascension_level=12))
        self.assertEqual(payload["ascension"], 12)
        for key in ("seed", "seed_group", "rng", "outcome", "router_plan"):
            with self.subTest(key=key), self.assertRaises(ValueError):
                value_state(dict(payload, **{key: "bad"}))
        nested = copy.deepcopy(payload)
        nested["map_nodes"][0]["rng_state"] = "bad"
        with self.assertRaises(ValueError):
            value_state(nested)

    def test_new_directories_only_and_new_run_closes_previous_as_censored(self):
        with tempfile.TemporaryDirectory() as root:
            directory = Path(root) / "trace"
            recorder = LearningRecorder(directory)
            recorder.observe(fixture_state(seed_group="a"))
            recorder.observe(fixture_state(seed_group="b"))
            recorder.close()
            self.assertEqual(len(list(directory.glob("*.jsonl"))), 2)
            self.assertTrue(all(not r["outcome"]["terminal_known"] for r in read_rows(directory) if r["record_type"] == "run_end"))
            with self.assertRaises(FileExistsError):
                LearningRecorder(directory)

    def test_route_tracker_handles_terminal_without_seed(self):
        tracker = RouteForecastTracker()
        before = fixture_state(screen_type="MAP")
        tracker.begin(before, {"trajectory": [{"symbol": "M", "before": {"hp": 40},
            "after": {"hp": 30, "gold": 60}, "action": "combat"}]})
        terminal = fixture_state(screen_type="GAME_OVER", floor=6, seed_group=None,
                                 game_over_reason="defeat")
        self.assertEqual(tracker.observe(terminal)["status"], "terminal")

    def test_room_gap_and_unconfirmed_map_are_not_deaths(self):
        with tempfile.TemporaryDirectory() as root:
            recorder = LearningRecorder(Path(root) / "trace")
            before = fixture_state(screen_type="MAP", choice_list=["M"])
            recorder.observe(before, 0)
            recorder.begin(before, GameAction(type=ActionType.CHOOSE, choice_index=0), 0)
            recorder.observe(before, 16)
            recorder.begin(before, GameAction(type=ActionType.CHOOSE, choice_index=0), 17)
            recorder.observe(before.model_copy(update={"act": 2, "floor": 18}), 18)
            recorder.close()
            statuses = [r["status"] for r in read_rows(recorder.directory) if r["record_type"] == "room_transition"]
            self.assertEqual(statuses, ["unconfirmed", "observation_gap"])


if __name__ == "__main__":
    unittest.main()
