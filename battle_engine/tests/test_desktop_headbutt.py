"""The Windows SaveState uses its own grid/cleanup wire schema, not Linux's enriched schema."""
import copy
import json
import os
from pathlib import Path
import unittest

from battle_engine.worker import Worker
from sts_ai_framework.tests.test_headless_headbutt import normal_headbutt_raw, opened_headbutt_raw

EXE = os.environ.get("BATTLE_ENGINE_WORKER", str(Path(__file__).resolve().parents[1] /
                     ("bin/battle-engine-worker.exe" if os.name == "nt" else "bin/battle-engine-worker")))


def desktop_snapshot(raw):
    snapshot = raw["combat"]["snapshot"]
    snapshot.pop("_engine")
    snapshot.pop("_headless_rng")
    return snapshot


def desktop_grid(raw):
    snapshot = desktop_snapshot(opened_headbutt_raw(raw))
    view = snapshot["grid_card_select_screen_state"]
    view["current_action_state"]["state"] = {"sourceIndex": -1, "amount": 0}
    view.update(is_discard=True, can_cancel=False, card_select_amount=0)
    for key in ("returnToHand", "reboundCard"):
        del view["action_queue"][0]["state"][key]
    player = snapshot["player_state"]
    pending_index = sum(len(player[pile]) for pile in ("master_deck","draw_pile","hand","discard_pile","exhaust_pile","limbo"))
    snapshot["cards_played_this_turn"] = [pending_index]
    snapshot["cards_played_this_turn_backup"] = []
    snapshot["cards_played_this_combat"] = [{"card_index":pending_index,"card_state":None}]
    player["card_in_use"]["modifiers"] = None
    return snapshot


@unittest.skipUnless(Path(EXE).is_file(), "Build native worker")
class DesktopHeadbuttTest(unittest.TestCase):
    def setUp(self):
        self.worker = Worker(EXE)
        self.addCleanup(self.worker.close)

    def predict(self, snapshot, command):
        reply = self.worker.execute({"snapshot": snapshot, "operation": "predict", "command": command}, 2000)
        self.assertTrue(reply["supported"], reply)
        return reply

    def test_desktop_selects_exact_discard_copy_and_applies_cleanup(self):
        raw = normal_headbutt_raw()
        opened = desktop_grid(raw)
        before = desktop_snapshot(raw)
        pending = opened["player_state"]["card_in_use"]["uuid"]
        discard = [c["uuid"] for c in before["player_state"]["discard_pile"]]
        root = self.predict(before, {"type": "CARD", "card_index": 0, "monster_index": 0})
        self.assertEqual(root["expected_screen"], "GRID")
        self.assertEqual(root["expected_desktop_headbutt_pending_uuid"], pending)
        self.assertEqual(root["expected_desktop_headbutt_uuids"]["discard_pile"], discard)
        result = self.predict(opened, {"type": "GRID_SELECT", "card_index": 1})
        self.assertEqual(result["expected_screen"], "NONE")
        self.assertEqual(json.loads(result["commands"][0]["command"]), {"type": "GRID_SELECT", "card_index": 1})
        ids = result["expected_desktop_headbutt_uuids"]
        self.assertEqual(ids["draw_pile"][-1], discard[1])
        self.assertEqual(ids["discard_pile"], [discard[0], *discard[2:], pending])
        self.assertEqual(result["expected_after"]["rng"], root["expected_after"]["rng"])

    def test_desktop_empty_and_single_discard_do_not_open_grid(self):
        for count in (0, 1):
            with self.subTest(count=count):
                snapshot = desktop_snapshot(normal_headbutt_raw())
                player = snapshot["player_state"]
                player["discard_pile"] = player["discard_pile"][:count]
                if count:
                    player["discard_pile"][0]["cost_for_turn"] = 0
                    cost = player["discard_pile"][0]["cost"]
                result = self.predict(snapshot, {"type": "CARD", "card_index": 0, "monster_index": 0})
                self.assertEqual(result["expected_screen"], "NONE")
                self.assertEqual(len(result["expected_after"]["draw_pile"]), count)
                if count:
                    self.assertEqual(result["expected_after"]["draw_pile"][-1]["turn_cost"], cost)

    def test_desktop_rejects_wrong_options_or_unknown_pending_effect(self):
        valid = desktop_grid(normal_headbutt_raw())
        for variant in ("reordered", "extra_action", "wrong_in_use"):
            with self.subTest(variant=variant):
                snapshot = copy.deepcopy(valid)
                view = snapshot["grid_card_select_screen_state"]
                if variant == "reordered":
                    view["group_cards"].reverse()
                elif variant == "extra_action":
                    view["action_queue"].append(copy.deepcopy(view["action_queue"][0]))
                else:
                    snapshot["player_state"]["card_in_use"]["uuid"] = snapshot["player_state"]["discard_pile"][0]["uuid"]
                reply = self.worker.execute({"snapshot": snapshot, "operation": "import"}, 2000)
                self.assertFalse(reply["supported"], reply)
