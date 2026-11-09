"""Causal-prefix and component checks for the .run-only research probe."""
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import unittest

from .reconstruction import Budget, SUPPORTED_ENGINE_SHA256, Unsupported
from .shop_run_probe import (Catalog, ROLES, analyze_shop, merchant_terms, prefix_for_shop,
                            probe_initial_shop, validate_purchases)


ROOT = Path(__file__).resolve().parents[2]


def example_run():
    return dict(seed_played="1215860580655121942", build_version="2022-12-18",
                character_chosen="IRONCLAD", ascension_level=1, neow_bonus="UPGRADE_CARD", neow_cost="NONE",
                path_per_floor=["M", "?", "$"],
                card_choices=[dict(floor=1, picked="Clash", not_picked=["Pummel", "Reckless Charge"])],
                event_choices=[dict(floor=2, event_name="Liars Game", player_choice="Ignored")],
                relics_obtained=[], potions_obtained=[], potions_floor_spawned=[1], potions_floor_usage=[])


class RunOnlyShopTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        jar = Path(os.environ.get("STS_GAME_JAR", ROOT / "lib/desktop-1.0.jar"))
        if not jar.is_file() or not shutil.which("javap"):
            raise unittest.SkipTest("verified game jar and existing JDK javap are required")
        if hashlib.sha256(jar.read_bytes()).hexdigest() != SUPPORTED_ENGINE_SHA256:
            raise unittest.SkipTest("game jar differs from the verified engine")
        cls.catalog = Catalog(ROOT, jar)

    def test_future_and_current_labels_cannot_change_prediction(self):
        run = example_run()
        prefix = prefix_for_shop(run, 3)
        original = analyze_shop(prefix, self.catalog)
        run.update(master_deck=["Alchemize+9"], relics=["The Courier"], victory=True,
                   items_purchased=["Secret Technique"], item_purchase_floors=[3],
                   seed_state={"card": [123, 456]})
        run["card_choices"].append(dict(floor=3, picked="Secret Technique", not_picked=[]))
        run["event_choices"].append(dict(floor=4, event_name="Lab", player_choice="Got Potions"))
        run["path_per_floor"].extend(["$", "B", "$"])
        run["relics_obtained"].append(dict(floor=3, key="The Courier"))
        run["potions_floor_spawned"].extend([3, 4])
        run["potions_floor_usage"].append(3)
        self.assertEqual(prefix_for_shop(run, 3), prefix)
        self.assertEqual(analyze_shop(prefix_for_shop(run, 3), self.catalog), original)

    def test_known_prior_rewards_match_without_current_purchase(self):
        prediction = analyze_shop(prefix_for_shop(example_run(), 3), self.catalog)
        self.assertEqual(prediction["status"], "conditional_partial")
        self.assertTrue(all(row["match"] for row in prediction["prefix_checks"]))
        self.assertEqual(prediction["consistent_unlock_levels"], [4, 5])
        self.assertEqual(prediction["card_inventory_variants"], 1)
        self.assertFalse(prediction["training_eligible"])

    def test_same_prefix_different_account_unlocks_different_relics(self):
        prediction = analyze_shop(prefix_for_shop(example_run(), 3), self.catalog)
        hypotheses = prediction["account_relic_hypotheses"]
        self.assertEqual(len(hypotheses), 2)
        self.assertEqual([row["id"] for row in hypotheses[0]["relics"]],
                         ["White Beast Statue", "Mercury Hourglass", "Lee's Waffle"])
        self.assertEqual([row["id"] for row in hypotheses[1]["relics"]],
                         ["Self Forming Clay", "Toxic Egg 2", "Lee's Waffle"])
        self.assertEqual(hypotheses[0]["profile"]["IRONCLAD"], hypotheses[1]["profile"]["IRONCLAD"])
        self.assertNotEqual(hypotheses[0]["profile"], hypotheses[1]["profile"])

    def test_held_out_mismatch_rejects_without_selecting_hypothesis(self):
        run = example_run()
        prediction = analyze_shop(prefix_for_shop(run, 3), self.catalog)
        original = copy.deepcopy(prediction)
        run.update(items_purchased=["Secret Technique"], item_purchase_floors=[3])
        validation = validate_purchases(run, 3, prediction, self.catalog)
        self.assertTrue(validation["rejected_all_component_hypotheses"])
        self.assertEqual(prediction, original)

    def test_courier_and_unknown_histories_are_unresolved(self):
        prefix = prefix_for_shop(example_run(), 3)
        prefix["relics_obtained"] = [dict(floor=1, key="The Courier")]
        with self.assertRaisesRegex(Unsupported, "courier"):
            merchant_terms(prefix)
        prediction = analyze_shop(prefix, self.catalog)
        self.assertEqual(prediction["status"], "unresolved")
        self.assertFalse(prediction["training_eligible"])

    def test_shared_search_budget_is_enforced(self):
        with self.assertRaisesRegex(Unsupported, "budget_exhausted"):
            probe_initial_shop(prefix_for_shop(example_run(), 3), self.catalog, budget=Budget(max_states=1))

    def test_static_seed_derived_pools_against_held_out_engine_exports(self):
        # The predictor receives seed/role/profile and static content only.
        # Fixture pools are comparison labels, never supplied to the predictor.
        path = ROOT / "debug/policy-reconstruction/oracle-v4/shops.jsonl"
        if not path.is_file():
            self.skipTest("optional local engine exports are absent")
        count = 0
        with path.open(encoding="utf-8") as handle:
            for line in handle:
                fixture = json.loads(line)
                if "action" in fixture:
                    continue
                i = int(fixture["case_id"].split("-")[1])
                # Role and seed come from the independent collector's test design.
                role = list(ROLES)[i % 4]
                pools = self.catalog.initial_relic_pools(900000 + i, role, {r: 0 for r in ROLES})
                expected = fixture["before"]
                for tier, values in expected["relic_pools"].items():
                    self.assertEqual(pools[tier], values, (i, tier))
                cards = self.catalog.pools(role, 0)
                for tier, values in expected["cards"].items():
                    self.assertEqual({x["id"] for x in cards[tier]}, {x["id"] for x in values}, (i, tier))
                self.assertEqual([dict(x, base_price={"COMMON": 50, "UNCOMMON": 75, "RARE": 100}[x["rarity"]])
                                  for x in self.catalog.potion_pools[role]], expected["potions"])
                prefix = dict(seed_played=str(900000 + i), build_version="2022-12-18", path_per_floor=[],
                              relics_obtained=[], event_choices=[])
                sale = merchant_terms(prefix)["sale_slot"]
                actual_sale = [x["slot_id"] for x in fixture["expected"]["items"] if x["on_sale"]]
                self.assertEqual(actual_sale, [f"colored:{sale}"])
                count += 1
        self.assertEqual(count, 200)


if __name__ == "__main__":
    unittest.main()
