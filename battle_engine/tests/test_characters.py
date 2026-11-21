"""Offline, hand-derived effect expectations; these are not Java replay proofs."""
import copy
from pathlib import Path
import unittest
import os

from battle_engine.native_api import execute_worker
from battle_engine.state import load

ROOT = Path(__file__).resolve().parents[1]
EXE = Path(os.environ.get("BATTLE_ENGINE_WORKER", str(ROOT / "bin/battle-engine-worker.exe")))


@unittest.skipUnless(EXE.is_file(), "Run battle_engine/build.ps1")
class CharacterEffectTests(unittest.TestCase):
    def state(self, role, card, upgraded=False):
        s = load(ROOT / "tests/fixtures/ironclad.json")
        p = s["player_state"]
        p["chosen_class_name"] = role
        p["hand"] = [dict(card_id=card, cost=1, cost_for_turn=1, times_upgraded=int(upgraded))]
        p["draw_pile"] = []
        p["relics"] = []
        p["potions"] = [dict(id="Potion Slot", slot=-1)] * 3
        return s

    def step(self, state):
        result = execute_worker(EXE, state, 1, 50, "step", metadata=dict(card_index=0))
        self.assertTrue(result["supported"], result)
        return result

    def test_four_roles_reach_native_import(self):
        for role in ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"):
            with self.subTest(role=role):
                s = self.state(role, "Strike_R")
                r = execute_worker(EXE, s, 100, 50)
                self.assertTrue(r["supported"], r)

    def test_corrected_vendor_color_metadata_does_not_gate_red_or_blue_cards(self):
        for role, card in (("IRONCLAD", "Brutality"), ("IRONCLAD", "Combust"),
                           ("DEFECT", "Buffer"), ("DEFECT", "Compile Driver")):
            r = execute_worker(EXE, self.state(role, card), 1, 20, "import")
            self.assertTrue(r["supported"], r)

    def test_neutralize_damage_and_weak(self):
        for upgraded in (False, True):
            r = self.step(self.state("THE_SILENT", "Neutralize", upgraded))
            self.assertEqual(r["projection"]["monsters"][0]["hp"], 50 - (4 if upgraded else 3))
            self.assertEqual(r["enemy_weak"], 2 if upgraded else 1)

    def test_poisoned_stab_damage_and_poison(self):
        for upgraded in (False, True):
            r = self.step(self.state("THE_SILENT", "Poisoned Stab", upgraded))
            self.assertEqual(r["projection"]["monsters"][0]["hp"], 50 - (8 if upgraded else 6))
            self.assertEqual(r["enemy_poison"], 4 if upgraded else 3)

    def test_deadly_poison(self):
        for upgraded in (False, True):
            r = self.step(self.state("THE_SILENT", "Deadly Poison", upgraded))
            self.assertEqual(r["enemy_poison"], 7 if upgraded else 5)

    def test_deflect_and_backflip_block(self):
        for card, base, up in (("Deflect", 4, 7), ("Backflip", 5, 8)):
            for upgraded in (False, True):
                self.assertEqual(self.step(self.state("THE_SILENT", card, upgraded))["projection"]["block"], up if upgraded else base)

    def test_blade_dance_and_master_reality(self):
        for upgraded in (False, True):
            s = self.state("THE_SILENT", "Blade Dance", upgraded)
            s["player_state"]["creature"]["powers"] = [dict(power_id="MasterRealityPower", amount=1)]
            r = self.step(s)
            self.assertEqual(len(r["projection"]["hand"]), 4 if upgraded else 3)
            self.assertTrue(all(c["id"] == "Shiv" and c["upgrades"] == 1 for c in r["projection"]["hand"]))

    def test_footwork(self):
        for upgraded in (False, True):
            r = self.step(self.state("THE_SILENT", "Footwork", upgraded))
            self.assertEqual(r["player_stats"]["dexterity"], 3 if upgraded else 2)

    def test_eruption_changes_stance_after_damage(self):
        for upgraded in (False, True):
            s = self.state("WATCHER", "Eruption", upgraded)
            r = self.step(s)
            self.assertEqual(r["projection"]["monsters"][0]["hp"], 41)
            self.assertEqual(r["stance"], 2)

    def test_leaving_calm_grants_energy(self):
        s = self.state("WATCHER", "Eruption")
        s["player_state"]["stance"] = "Calm"
        r = self.step(s)
        self.assertEqual(r["projection"]["energy"], 4)

    def test_vigilance(self):
        for upgraded in (False, True):
            r = self.step(self.state("WATCHER", "Vigilance", upgraded))
            self.assertEqual(r["projection"]["block"], 12 if upgraded else 8)
            self.assertEqual(r["stance"], 1)

    def test_mantra_enters_divinity_and_retains_remainder(self):
        for upgraded in (False, True):
            s = self.state("WATCHER", "Prostrate", upgraded)
            s["player_state"]["creature"]["powers"] = [dict(power_id="Mantra", amount=8)]
            r = self.step(s)
            self.assertEqual(r["stance"], 3)
            self.assertEqual(r["projection"]["energy"], 5)
            self.assertEqual(r["powers"].get("Mantra", 0), int(upgraded))

    def test_unknown_character_card_is_an_error_in_release(self):
        for role, card in (("THE_SILENT", "MOD_UNKNOWN_GREEN"), ("WATCHER", "MOD_UNKNOWN_PURPLE")):
            r = execute_worker(EXE, self.state(role, card), 1, 20)
            self.assertFalse(r["supported"])
            self.assertIn(r["reason"].split(":")[0], ("unknown_card", "unimplemented_card"))


if __name__ == "__main__":
    unittest.main()
