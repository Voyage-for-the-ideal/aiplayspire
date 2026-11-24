import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

from sts_ai_framework.route_benchmark import (CachedCompletions, ROLES, compare,
    archive_incomplete_pairs, empty_reward_cancel, jsonl, prediction_summary,
    search_evidence, seed_group, seed_long, sequence, write_json)


class BenchmarkTests(unittest.TestCase):
    def test_navigation_is_only_the_sole_return_button(self):
        value = SimpleNamespace(screen_type="COMBAT_REWARD", choice_list=[], can_proceed=False,
                                can_cancel=True, reward_choices=[])
        self.assertTrue(empty_reward_cancel(value))
        value.choice_list = ["card"]
        self.assertFalse(empty_reward_cancel(value))
        value.choice_list, value.can_proceed = [], True
        self.assertFalse(empty_reward_cancel(value))

    def test_retry_preserves_interrupted_pair_not_valid_other_pairs(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            for seed in ("RT20IC01", "RT20DF02"):
                for policy in ("planner", "resource_v2"):
                    write_json(output / (seed + "-" + policy) / "result.json",
                               {"terminal_known": seed != "RT20DF02" or policy == "planner"})
            self.assertEqual(archive_incomplete_pairs(output), ["RT20DF02"])
            self.assertTrue((output / "interrupted/RT20DF02-planner/result.json").exists())
            self.assertFalse((output / "RT20DF02-planner").exists())
            self.assertTrue((output / "RT20IC01-planner/result.json").exists())

    def test_manifest_has_six_pairs_in_alternating_order(self):
        rows = sequence()
        self.assertEqual(len(rows), 12)
        for i in range(0, 12, 2):
            self.assertEqual(rows[i]["seed"], rows[i+1]["seed"])
            self.assertEqual(rows[i]["character"], rows[i+1]["character"])
            self.assertEqual({rows[i]["policy"], rows[i+1]["policy"]}, {"planner", "resource_v2"})
        self.assertNotEqual(rows[0]["policy"], rows[2]["policy"])
        self.assertEqual(len({row["seed"] for row in rows}), 6)

    def test_seed_conversion_and_java_uuid(self):
        self.assertEqual(seed_long("10"), 35)
        self.assertEqual(seed_long("O"), 0)
        self.assertEqual(seed_group("1"), "c6c10119-a668-3ad9-9648-3b98db38694b")
        self.assertEqual(seed_group("1"), seed_group("01"))
        self.assertEqual(len({seed_group(seed) for seeds in ROLES.values() for seed in seeds}), 6)

    def test_cache_reuses_exact_request_only(self):
        class Backend:
            count = 0
            def create(self, **kwargs):
                self.count += 1
                return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='{"type":"proceed"}'), finish_reason="stop")])
        with tempfile.TemporaryDirectory() as directory:
            backend = Backend()
            cache = CachedCompletions(backend, directory)
            self.assertEqual(cache.create(model="x").choices[0].message.content,
                             cache.create(model="x").choices[0].message.content)
            self.assertEqual(backend.count, 1)
            cache.create(model="y")
            self.assertEqual(backend.count, 2)

    def test_budget_mismatch_is_not_verified(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            self.assertFalse(search_evidence(path)["budget_verified"])
            jsonl(path / "search.jsonl", {"kind": "request", "max_expansions": 15000,
                "timeout_ms": 2000, "search_profile": "BALANCED"})
            self.assertFalse(search_evidence(path)["budget_verified"])

    def test_streamed_metrics_are_not_double_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            jsonl(path / "search.jsonl", {"kind": "request", "max_expansions": 15000,
                "timeout_ms": 30000, "search_profile": "BALANCED"})
            for n, reason in ((30, "RUNNING"), (1000, "RUNNING"), (2000, "VICTORY")):
                jsonl(path / "search.jsonl", {"kind": "response", "metrics": {"expanded_nodes": n, "stop_reason": reason}})
            evidence = search_evidence(path)
            self.assertTrue(evidence["budget_verified"])
            self.assertEqual(evidence["expanded_nodes"], 2000)
            self.assertEqual(evidence["responses_with_metrics"], 1)

    def test_invalid_pairs_and_interruptions_are_not_losses(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            row = dict(character="IRONCLAD", seed="RT20IC01", policy="planner", terminal_known=True)
            write_json(path / "RT20IC01-planner" / "result.json", row)
            result = compare(path)
            self.assertEqual(result["valid_pairs"], 0)
            self.assertEqual(result["completed_runs"], 1)
            self.assertFalse(result["promotable"])
            self.assertNotIn("floor_delta", result["pairs"][0])

    def test_prediction_error_excludes_observation_gaps(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            jsonl(path / "prediction-errors.jsonl", {"status": "matched", "symbol": "M", "hp_error": -2, "gold_error": 3})
            jsonl(path / "prediction-errors.jsonl", {"status": "observation_gap", "hp_error": None})
            summary = prediction_summary(path)
            self.assertEqual(summary["rooms"], 1)
            self.assertEqual(summary["hp_mae"], 2)


if __name__ == "__main__":
    unittest.main()
