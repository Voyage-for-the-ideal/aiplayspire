"""Original-source expectations; offline simulation, not live-engine parity."""
import copy
import os
from pathlib import Path
import unittest

from battle_engine.server import execute_worker
from battle_engine.state import load

ROOT = Path(__file__).resolve().parents[1]
EXE = Path(os.environ.get("BATTLE_ENGINE_WORKER", str(ROOT / "bin/battle-engine-worker.exe")))


@unittest.skipUnless(EXE.is_file(), "Build the native worker first")
class CharacterStrategyTests(unittest.TestCase):
    def card(self, name, up=False, cost=1, **fields):
        if name in ("Glass Knife", "WindmillStrike"):
            fields.setdefault("base_damage", (12 if up else 8) if name == "Glass Knife" else (10 if up else 7))
        if name == "Perseverance":
            fields.setdefault("base_block", 7 if up else 5)
        return dict(card_id=name, times_upgraded=int(up), cost=cost, cost_for_turn=cost, **fields)

    def state(self, name="Strike_G", up=False, role="THE_SILENT", hand=None, draw=None, powers=()):
        s = load(ROOT / "tests/fixtures/ironclad.json")
        p = s["player_state"]
        p.update(chosen_class_name=role, hand=hand or [self.card(name, up)],
                 draw_pile=draw or [], discard_pile=[], exhaust_pile=[], relics=[],
                 potions=[dict(id="Potion Slot", slot=-1)] * 3)
        p["creature"]["powers"] = [dict(power_id=k, amount=v) for k, v in powers]
        m = s["cur_map_node_state"]["monster_data"][0]["creature"]
        m.update(current_health=500, max_health=500)
        return s

    def run_worker(self, s, operation="step", **kwargs):
        if operation == "step":
            kwargs.setdefault("card_index", 0)
        r = execute_worker(EXE, s, 4000, 1500, operation, metadata=kwargs)
        self.assertTrue(r["supported"], r)
        return r

    def sequence(self, s, *actions):
        return self.run_worker(s, "sequence", actions=list(actions))

    def play(self, i=0):
        return dict(action_type="CARD", card_index=i)

    def select(self, i):
        return dict(action_type="SELECT", select_index=i)

    def test_wraith_form_and_artifact(self):
        for up in (False, True):
            for artifact in (0, 1):
                with self.subTest(up=up, artifact=artifact):
                    r = self.run_worker(self.state("Wraith Form v2", up, powers=[("Artifact", artifact)]))
                    self.assertEqual(r["powers"]["IntangiblePlayer"], 3 if up else 2)
                    self.assertEqual(r["powers"].get("Wraith Form v2", 0), 0 if artifact else -1)

    def test_catalyst_large_poison_and_snecko_skull(self):
        for up in (False, True):
            for skull in (False, True):
                with self.subTest(up=up, skull=skull):
                    s = self.state("Catalyst", up)
                    s["cur_map_node_state"]["monster_data"][0]["creature"]["powers"] = [dict(power_id="Poison", amount=100)]
                    if skull:
                        s["player_state"]["relics"] = [dict(relic_id="Snake Skull", counter=-1)]
                    r = self.run_worker(s)
                    self.assertEqual(r["enemy_poison"], (300 if up else 200) + int(skull))

    def test_catalyst_without_poison_does_not_strip_artifact(self):
        s = self.state("Catalyst")
        s["player_state"]["relics"] = [dict(relic_id="Snake Skull", counter=-1)]
        self.assertEqual(self.run_worker(s)["enemy_poison"], 0)

    def test_accuracy_shiv_and_after_image(self):
        s = self.state(hand=[self.card("Accuracy"), self.card("After Image"), self.card("Shiv", cost=0)])
        r = self.sequence(s, self.play(), self.play(), self.play())
        self.assertEqual(r["projection"]["monsters"][0]["hp"], 492)
        self.assertEqual(r["projection"]["block"], 1)

    def test_burst_catalyst_multiplies_current_poison(self):
        s = self.state(hand=[self.card("Burst"), self.card("Catalyst", True)])
        s["cur_map_node_state"]["monster_data"][0]["creature"]["powers"] = [dict(power_id="Poison", amount=20)]
        r = self.sequence(s, self.play(), self.play())
        self.assertEqual(r["enemy_poison"], 180)

    def test_discard_tactician_and_reflex(self):
        s = self.state(hand=[self.card("Survivor"), self.card("Tactician", cost=-2), self.card("Reflex", cost=-2)],
                       draw=[self.card("Deflect", cost=0), self.card("Slice", cost=0)])
        r = self.sequence(s, self.play(), self.select(0))
        self.assertEqual(r["projection"]["energy"], 3)
        self.assertEqual([c["id"] for c in r["projection"]["hand"]], ["Reflex"])
        s["player_state"]["hand"] = [self.card("Survivor"), self.card("Tactician", cost=-2), self.card("Reflex", cost=-2)]
        r = self.sequence(s, self.play(), self.select(1))
        self.assertEqual(len(r["projection"]["hand"]), 3)

    def test_forced_discard_has_no_selector(self):
        s = self.state(hand=[self.card("Concentrate"), self.card("Tactician", True, cost=-2)])
        r = self.run_worker(s)
        self.assertEqual(r["selection"]["task"], "INVALID")
        self.assertEqual(r["projection"]["energy"], 6)

    def test_scry_zero_and_multiple_selection(self):
        for selected in ([], [0, 2]):
            with self.subTest(selected=selected):
                s = self.state("ThirdEye", role="WATCHER", draw=[self.card("Strike_P"), self.card("Defend_P"), self.card("Eruption")])
                r = self.sequence(s, self.play(), *(self.select(i) for i in selected), self.select(65535))
                self.assertEqual(len(r["projection"]["draw_pile"]), 3-len(selected))
                self.assertEqual(r["selection"]["task"], "INVALID")
                self.assertEqual(r["projection"]["block"], 7)

    def test_scry_does_not_trigger_manual_discard(self):
        s = self.state("ThirdEye", role="WATCHER", draw=[self.card("Tactician", cost=-2)], powers=[("Nirvana", 3)])
        s["player_state"]["relics"] = [dict(relic_id="Tough Bandages", counter=-1)]
        r = self.sequence(s, self.play(), self.select(0), self.select(65535))
        self.assertEqual(r["projection"]["energy"], 2)
        self.assertEqual(r["projection"]["block"], 10)

    def test_empty_scry_still_triggers_nirvana_without_shuffle(self):
        s = self.state("ThirdEye", role="WATCHER", powers=[("Nirvana", 3)])
        s["player_state"]["discard_pile"] = [self.card("Strike_P")]
        r = self.run_worker(s)
        self.assertEqual(r["projection"]["block"], 10)
        self.assertEqual(r["projection"]["draw_pile"], [])
        self.assertEqual(r["projection"]["rng"]["shuffle_rng_counter"], 1)

    def test_rushdown_fortress_and_calm_energy(self):
        s = self.state(role="WATCHER", hand=[self.card("Adaptation", cost=0), self.card("MentalFortress"), self.card("Eruption")],
                       draw=[self.card("Strike_P"), self.card("Defend_P")])
        s["player_state"]["stance"] = "Calm"
        r = self.sequence(s, self.play(), self.play(), self.play())
        self.assertEqual(r["projection"]["energy"], 3)
        self.assertEqual(r["projection"]["block"], 4)
        self.assertEqual(len(r["projection"]["hand"]), 2)

    def test_same_stance_does_not_trigger_fortress(self):
        s = self.state("Crescendo", role="WATCHER", powers=[("Controlled", 6)])
        s["player_state"]["stance"] = "Wrath"
        self.assertEqual(self.run_worker(s)["projection"]["block"], 0)

    def test_tantrum_intrinsic_shuffle_flag_is_supported(self):
        s = self.state(role="WATCHER", hand=[self.card("Tantrum", shuffle_back_into_draw_pile=True)])
        r = self.run_worker(s)
        self.assertEqual([c["id"] for c in r["projection"]["draw_pile"]], ["Tantrum"])
        s["player_state"]["hand"][0]["card_id"] = "Strike_P"
        rejected = execute_worker(EXE, s, 1, 50, "import")
        self.assertFalse(rejected["supported"])
        self.assertEqual(rejected["reason"], "card_flags:Strike_P")

    def test_conditional_combo_cards_use_previous_card_type(self):
        for name, previous, expected in (("FollowUp", "Strike_P", 2), ("FollowUp", "Defend_P", 1)):
            with self.subTest(previous=previous):
                s = self.state(role="WATCHER", hand=[self.card(previous), self.card(name)])
                self.assertEqual(self.sequence(s, self.play(), self.play())["projection"]["energy"], expected)

    def test_poison_priority_requires_catalyst_in_hand(self):
        s = self.state("Deadly Poison", draw=[self.card("Catalyst")])
        first = self.run_worker(s, "strategy")["actions"][0]["priority"]
        s["player_state"]["hand"].append(self.card("Catalyst"))
        second = self.run_worker(s, "strategy")["actions"][0]["priority"]
        self.assertGreater(second, first)

    def test_colorless_cards_use_silent_legacy_fallback(self):
        s = self.state(hand=[self.card("Master of Strategy", cost=0), self.card("Grand Finale", cost=0)])
        ranks = {x["source"]: x["priority"] for x in self.run_worker(s, "strategy")["actions"] if x["type"] == 0}
        # Remove the generic zero-cost attack boost to compare just fallback ranks.
        self.assertEqual(ranks[0], -2)
        self.assertEqual(ranks[1]-5000, -1)

    def test_selection_objectives_are_distinct(self):
        s = self.state(hand=[self.card("Tactician", cost=-2), self.card("Strike_G"), self.card("Catalyst")])
        scores = self.run_worker(s, "strategy")["selections"]
        self.assertGreater(scores[0][0], scores[1][0])
        self.assertGreater(scores[0][2], scores[1][2])
        self.assertLess(scores[0][3], scores[1][3])

    def test_fear_no_evil_is_not_an_exit_against_buffing_enemy(self):
        s = self.state(role="WATCHER", hand=[self.card("Eruption"), self.card("FearNoEvil")])
        m = s["cur_map_node_state"]["monster_data"][0]
        m.update(next_move=1, intent_name="ATTACK", move_history=["1"])
        # An unaffordable exit is not considered reachable.
        s["player_state"]["hand"][1]["cost_for_turn"] = 3
        r = self.run_worker(s, "strategy")
        eruption = next(x for x in r["actions"] if x["type"] == 0 and x["source"] == 0)
        self.assertLess(eruption["priority"], 0)

    def test_mechanism_rules_do_not_change_numeric_score_contract(self):
        s = self.state("Catalyst", powers=[("IntangiblePlayer", 3), ("Wraith Form v2", -1)])
        r = self.run_worker(s, "evaluate")
        self.assertEqual(r["mechanism_rules"]["intangible-future"], 60)
        self.assertEqual(r["mechanism_rules"]["wraith-dexterity-cost"], -20)
        self.assertEqual(r["evaluation"]["total"], sum(v for k, v in r["evaluation"].items() if k != "total"))

    def test_long_turn_is_not_cut_at_fifty_actions(self):
        s = self.state("Flash of Steel", draw=[self.card("Flash of Steel", cost=0) for _ in range(59)])
        s["player_state"]["hand"][0].update(cost=0, cost_for_turn=0)
        m = s["cur_map_node_state"]["monster_data"][0]["creature"]
        m.update(current_health=180, max_health=180)
        r = self.run_worker(s, "search")
        self.assertEqual(r["metrics"]["search_stop_reason"], "VICTORY")
        self.assertGreaterEqual(r["metrics"]["cached_actions_remaining"], 50)

    def test_vault_twelfth_card_resets_time_warp_without_enemy_turn(self):
        s = self.state("Vault", role="WATCHER", powers=[("IntangiblePlayer", 2)])
        m = s["cur_map_node_state"]["monster_data"][0]
        m.update(next_move=2, intent_name="ATTACK", move_history=["2"])
        m["creature"].update(id="TimeEater", powers=[dict(power_id="Time Warp", amount=11)])
        r = self.run_worker(s)
        self.assertEqual(r["projection"]["hp"], 70)
        self.assertEqual(r["projection"]["turn"], 2)
        self.assertEqual(r["enemy_strength"], 2)
        self.assertEqual(r["powers"]["IntangiblePlayer"], 2)

    def test_blasphemy_death_and_intangible_exception(self):
        for intangible in (0, 2):
            with self.subTest(intangible=intangible):
                s = self.state("Blasphemy", role="WATCHER", powers=[("IntangiblePlayer", intangible)] if intangible else [])
                r = self.sequence(s, self.play(), dict(action_type="END"))
                self.assertEqual(r["projection"]["hp"], 69 if intangible else 0)
                self.assertEqual(r["outcome"], 0 if intangible else 2)

    def test_expunger_restores_hit_count(self):
        for up in (False, True):
            s = self.state(role="WATCHER", hand=[self.card("Expunger", up, base_magic_number=4)])
            self.assertEqual(self.run_worker(s)["projection"]["monsters"][0]["hp"], 500-4*(15 if up else 9))

    def test_missing_dynamic_attributes_are_rejected(self):
        for card, field in (("Glass Knife", "base_damage"), ("WindmillStrike", "base_damage"), ("Perseverance", "base_block")):
            with self.subTest(card=card):
                s = self.state(card)
                del s["player_state"]["hand"][0][field]
                r = execute_worker(EXE, s, 1, 50, "import")
                self.assertFalse(r["supported"])
                self.assertIn("missing_"+field, r["reason"])

    def test_glass_knife_restores_reduced_damage(self):
        s = self.state(hand=[self.card("Glass Knife", base_damage=4)])
        self.assertEqual(self.run_worker(s)["projection"]["monsters"][0]["hp"], 492)

    def test_alpha_does_not_upgrade_beta_without_master_reality(self):
        for up in (False, True):
            s = self.state("Alpha", up, role="WATCHER")
            r = self.run_worker(s)
            self.assertEqual(r["projection"]["draw_pile"][0]["id"], "Beta")
            self.assertEqual(r["projection"]["draw_pile"][0]["upgrades"], 0)

    def test_wallop_and_talk_to_the_hand_block_actual_hit(self):
        s = self.state("Wallop", role="WATCHER")
        s["cur_map_node_state"]["monster_data"][0]["creature"].update(current_block=5,
            powers=[dict(power_id="BlockReturnPower", amount=2)])
        r = self.run_worker(s)
        self.assertEqual(r["projection"]["block"], 6)

    def test_aoe_preserves_vigor_for_every_hit(self):
        s = self.state("Dagger Spray", powers=[("Vigor", 5)])
        r = self.run_worker(s)
        self.assertEqual(r["projection"]["monsters"][0]["hp"], 482)
        self.assertNotIn("Vigor", r["powers"])

    def test_retained_perseverance_and_establishment(self):
        s = self.state(role="WATCHER", hand=[self.card("Perseverance", cost=2)], powers=[("EstablishmentPower", 1)])
        r = self.sequence(s, dict(action_type="END"), self.play())
        self.assertEqual(r["projection"]["block"], 7)
        self.assertEqual(r["projection"]["energy"], 2)

    def test_retain_does_not_protect_ethereal_card(self):
        s = self.state(role="WATCHER", hand=[self.card("Ghostly Armor"), self.card("Strike_P")], powers=[("Retain Cards", 1)])
        r = self.sequence(s, dict(action_type="END"), self.select(0), self.select(65535))
        self.assertIn("Ghostly Armor", [c["id"] for c in r["projection"]["exhaust_pile"]])

    def test_source_audit_matches_runtime_catalog(self):
        from battle_engine.tools.coverage import inventory
        report = inventory()
        self.assertEqual(len(report["cards"]), 150)
        self.assertEqual(report["catalog_without_dispatch"], [])
        self.assertTrue(all(c["source"] for c in report["cards"]))
        for role in ("THE_SILENT", "WATCHER"):
            self.assertEqual(report["roles"][role]["missing"], report["roles"][role]["runtime_missing"])


def _damage_case(name, role, values):
    def test(self):
        for up in (False, True):
            with self.subTest(up=up):
                r = self.run_worker(self.state(name, up, role))
                self.assertEqual(r["projection"]["monsters"][0]["hp"], 500-values[int(up)])
    return test


for _name, _values in {
    "Backstab": (11, 15), "Bane": (7, 10), "Dagger Spray": (8, 12), "Dash": (10, 13),
    "Die Die Die": (13, 17), "Slice": (6, 9), "Underhanded Strike": (12, 16),
    "Predator": (15, 20), "Masterful Stab": (12, 16), "Eviscerate": (21, 27),
    "Endless Agony": (4, 6), "Quick Slash": (8, 12), "Sucker Punch": (7, 9),
    "Grand Finale": (50, 60), "Glass Knife": (16, 24), "Choke": (12, 12),
}.items():
    setattr(CharacterStrategyTests, "test_damage_" + _name.replace(" ", "_"), _damage_case(_name, "THE_SILENT", _values))

for _name, _values in {
    "BowlingBash": (7, 10), "CarveReality": (6, 10), "ReachHeaven": (10, 15),
    "SashWhip": (8, 10), "FollowUp": (7, 11), "FearNoEvil": (8, 11),
    "Tantrum": (9, 12), "WindmillStrike": (7, 10), "SandsOfTime": (20, 26),
    "SignatureMove": (30, 40), "Brilliance": (12, 16), "Wallop": (9, 12),
    "TalkToTheHand": (5, 7),
}.items():
    setattr(CharacterStrategyTests, "test_damage_" + _name, _damage_case(_name, "WATCHER", _values))


if __name__ == "__main__":
    unittest.main()
