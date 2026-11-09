import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import torch
from .contracts import FORMAT, observable_state, split_for, validate_example
from .encoding import FEATURES, action_parts, build_vocabulary, encode_batch, state_parts
from .model import SetPolicy, HeartValue
from .training import awr_weights, heart_return, is_bc_example, train_epoch
from .data import build_examples
from .evaluate_runs import compare_runs
from .reconstruction import Budget, RNG, Unsupported, jround, recover_historical


def example(run_id="example", heart=True):
    state = observable_state({"character": "IRONCLAD", "ascension_level": 20, "screen_type": "CARD_REWARD", "floor": 10, "act": 1,
                              "player": {"current_hp": 40, "max_hp": 80, "gold": 100}, "deck": [{"id": "Strike_R", "upgrades": 0}]})
    return {"format": FORMAT, "run_id": run_id, "seed_group": "same-seed", "decision_id": "1", "split": split_for("same-seed"),
            "decision_type": "CARD_REWARD", "pre_state": state,
            "candidates": [{"action_id": "a", "kind": "take_card", "entity_id": "Bash", "entity_kind": "card", "legal": True},
                           {"action_id": "b", "kind": "skip", "legal": True},
                           {"action_id": "c", "kind": "buy_card", "legal": False, "price": 999}],
            "chosen_action_id": "a", "run_mode": {"is_daily": False, "is_trial": False, "is_endless": False, "is_beta": False, "chose_seed": False, "special_seed": 0},
            "outcome": {"terminal_known": True, "victory": heart, "heart_victory": heart}, "provenance": {"status": "exact"}}


class PolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_projection_excludes_hidden_information(self):
        raw = {"seed": 1, "rng_state": {}, "next_boss": "secret", "outcome": True,
               "character": "DEFECT", "ascension_level": 20, "player": {"gold": 7}, "master_deck": ["future"]}
        projected = observable_state(raw)
        for key in ("seed", "rng_state", "next_boss", "outcome", "master_deck"):
            self.assertNotIn(key, projected)
        self.assertIsNone(projected["hp"])
        self.assertFalse(projected["known"]["hp"])

    def test_set_policy_is_candidate_permutation_equivariant(self):
        row = example()
        vocabulary = build_vocabulary([row])
        model = SetPolicy(len(vocabulary)).eval()
        with torch.no_grad():
            p = model(encode_batch([row], vocabulary)).softmax(-1)[0]
            permuted = copy.deepcopy(row)
            permuted["candidates"] = [row["candidates"][i] for i in (2, 0, 1)]
            q = model(encode_batch([permuted], vocabulary)).softmax(-1)[0]
        torch.testing.assert_close(q, p[[2, 0, 1]], atol=1e-6, rtol=1e-5)
        self.assertEqual(p[2].item(), 0.)

    def test_candidates_preserve_event_cost_and_permanent_card_value(self):
        first = dict(kind="select_card", legal=True, entity_id="RitualDagger", misc=15)
        second = dict(first, misc=60)
        self.assertNotEqual(action_parts(first)[1], action_parts(second)[1])
        option = dict(kind="EFFECT", legal=True, outcomes=[dict(probability=1., effects=[dict(type="lose_gold", amount=30)])])
        expensive = copy.deepcopy(option)
        expensive["outcomes"][0]["effects"][0]["amount"] = 100
        self.assertNotEqual(action_parts(option)[1], action_parts(expensive)[1])

    def test_first_row_and_observed_wing_choices_seed_reachability(self):
        s = example()["pre_state"]
        s["current_map_node"] = {"x": 0, "y": -1}
        s["current_map_choices"] = [{"x": 0, "y": 0}]
        s["map_nodes"] = [dict(x=0, y=0, symbol="M", children=[dict(x=1, y=1)]),
                          dict(x=1, y=1, symbol="R", children=[]), dict(x=3, y=1, symbol="$", children=[])]
        visible = [(name, nums[FEATURES.index("legal")]) for name, nums in state_parts(s) if name.startswith("map:")]
        self.assertEqual(visible, [("map:M", 1.), ("map:R", 1.), ("map:$", 0.)])
        s["current_map_choices"].append({"x": 3, "y": 1, "winged": True})
        reachable_shop = next(nums for name, nums in state_parts(s) if name == "map:$")
        self.assertEqual(reachable_shop[FEATURES.index("legal")], 1.)

    def test_all_illegal_and_overflow_rejected(self):
        row = example()
        for c in row["candidates"]:
            c["legal"] = False
        with self.assertRaises(ValueError):
            encode_batch([row], build_vocabulary([row]))
        row = example()
        row["pre_state"]["deck"] = [{"id": str(i)} for i in range(74)]
        with self.assertRaises(ValueError):
            build_vocabulary([row])

    def test_bc_and_awr_training_closure_and_separate_critic(self):
        won, lost = example(), example("lost", False)
        vocabulary = build_vocabulary([won, lost])
        policy = SetPolicy(len(vocabulary), d_model=24, layers=1, heads=4, dropout=0.)
        critic = HeartValue(len(vocabulary), d_model=24, layers=1, heads=4, dropout=0.)
        self.assertFalse(set(map(id, policy.parameters())) & set(map(id, critic.parameters())))
        optimizer = torch.optim.AdamW(policy.parameters(), lr=.01)
        before = copy.deepcopy(policy.state_dict())
        train_epoch(policy, [won], vocabulary, optimizer, batch_size=2)
        self.assertTrue(any(not torch.equal(v, before[k]) for k, v in policy.state_dict().items()))
        values = copy.deepcopy(critic.state_dict())
        train_epoch(policy, [won, lost], vocabulary, optimizer, critic=critic, batch_size=2)
        self.assertTrue(all(torch.equal(v, values[k]) for k, v in critic.state_dict().items()))
        self.assertTrue(is_bc_example(won))
        self.assertFalse(is_bc_example(lost))
        lost["outcome"] = {"terminal_known": False, "victory": None, "heart_victory": None}
        with self.assertRaises(ValueError):
            heart_return(lost)

    def test_awr_negative_advantage_is_positive_weight_and_detached(self):
        value = torch.tensor([.9, .1], requires_grad=True)
        weights = awr_weights(torch.tensor([0., 1.]), value)
        self.assertFalse(weights.requires_grad)
        self.assertGreater(weights[0].item(), 0)
        self.assertAlmostEqual(weights[1].item(), 20., places=4)

    def test_unknown_terminal_labels_and_hidden_input_rejected(self):
        row = example()
        row["pre_state"]["rng"] = {"seed": 123}
        with self.assertRaises(ValueError):
            validate_example(row)
        row = example()
        row["outcome"] = {"terminal_known": False, "victory": False, "heart_victory": False}
        with self.assertRaises(ValueError):
            validate_example(row)

    def test_ordinary_run_never_uses_chosen_or_future_to_guess_stock(self):
        run = {"seed_played": 123, "items_purchased": ["Membership Card"], "master_deck": ["future"]}
        first = recover_historical(run, "SHOP_SCREEN", 6)
        run["items_purchased"] = ["another label"]
        self.assertEqual(first, recover_historical(run, "SHOP_SCREEN", 6))
        self.assertEqual(first["status"], "ambiguous")
        with self.assertRaises(Unsupported):
            RNG([1, 2], Budget(max_states=0)).integer(1)

    def test_mathutils_round_uses_double_addition(self):
        self.assertEqual(jround(305.4999), 305)

    def test_related_seeds_grouped_and_censored_preserved(self):
        row = example()
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "trace.jsonl"
            decision = {**row, "record_type": "decision", "confirmed": True}
            path.write_text("\n".join(json.dumps(r) for r in [
                {"record_type": "run_start", "run_id": row["run_id"], "seed_group": "same-seed"}, decision, decision]), encoding="utf-8")
            examples, stats = build_examples([path])
        self.assertEqual(stats["duplicates"], 1)
        self.assertFalse(examples[0]["outcome"]["terminal_known"])
        self.assertIsNone(examples[0]["outcome"]["heart_victory"])
        self.assertEqual(examples[0]["split"], split_for("same-seed"))

    def test_real_game_comparison_rejects_missing_pairs_or_changed_budget(self):
        row = dict(character="IRONCLAD", seed_group="1", ascension=20, terminal_known=True, heart_victory=False,
                   runtime_fingerprint="actualmods", combat_budget="fixed", start_fingerprint="start")
        with self.assertRaises(ValueError):
            compare_runs([row], [])
        changed = dict(row, combat_budget="larger")
        with self.assertRaises(ValueError):
            compare_runs([row], [changed])
        report = compare_runs([row], [dict(row, heart_victory=True)], samples=100)
        self.assertFalse(report["promotable"])

    def test_cli_bc_checkpoint_awr_roundtrip(self):
        from .train import main, load_checkpoint
        rows = []
        for split in ("train", "val"):
            group = next(str(i) for i in range(1000) if split_for(str(i)) == split)
            for won in (True, False):
                row = example(split + str(won), won)
                row.update(seed_group=group, split=split)
                rows.append(row)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            data = root / "examples.jsonl"
            data.write_text("\n".join(json.dumps(r) for r in rows), encoding="utf-8")
            base = ["train", "--data", str(data), "--stage", "confirm", "--epochs", "1", "--batch-size", "2"]
            with patch("sys.argv", base + ["--output-dir", str(root / "bc")]):
                main()
            _, artifact = load_checkpoint(root / "bc" / "policy.pt")
            self.assertEqual(artifact["training"]["train_samples"], 1)
            with patch("sys.argv", base + ["--output-dir", str(root / "awr"), "--mode", "awr", "--checkpoint", str(root / "bc" / "policy.pt")]):
                main()
            _, artifact = load_checkpoint(root / "awr" / "policy.pt")
            self.assertEqual(artifact["training"]["train_samples"], 2)
            self.assertIsNotNone(artifact["critic"])


if __name__ == "__main__":
    unittest.main()
