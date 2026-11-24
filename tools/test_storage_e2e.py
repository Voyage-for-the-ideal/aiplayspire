import copy
import json
import os
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from sts_ai_framework.headless.collection import EpisodeWriter
from sts_ai_framework.headless.replay import verify_replay
from sts_ai_framework.tests.test_headless_combat import combat_raw


def strike_frames():
    before = combat_raw()
    before["seed"] = 42
    after = copy.deepcopy(before)
    raw = after["combat"]["snapshot"]
    player = raw["player_state"]
    strike = player["hand"].pop(0)
    player["discard_pile"].append(strike)
    player["energy_panel_total_energy"] -= 1
    raw["cards_played_this_turn_backup"] = [copy.deepcopy(strike)]
    raw["cards_played_this_combat"] = [{"card_state": copy.deepcopy(strike), "card_index": -1}]
    raw["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"] -= 6
    after["player"]["energy"] -= 1
    after["player"]["discardPile"].append(after["player"]["hand"].pop(0))
    after["player"]["hand"][0]["index"] = 0
    after["monsters"][0]["hp"] -= 6
    after["combat"]["legal_actions"] = [{"type": "CARD", "card_index": 0, "monster_index": -1,
                                         "uuid": player["hand"][0]["uuid"]},
                                        *after["combat"]["legal_actions"][2:]]
    after["driver_receipt"] = dict(source_state_token="turn-0", sequence=1, request_id=2,
                                   method="playCard", args=[0, 0])
    return before, after


class ReplayE2E(unittest.TestCase):
    def test_native_replay_rejects_wrong_hp_even_with_valid_hash_chain(self):
        worker = Path(os.environ["BATTLE_ENGINE_WORKER"])
        self.assertTrue(worker.is_file())
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "ALLOW_COLLECTION").write_text("test")
            for damage_delta in (0, 1):
                before, after = strike_frames()
                after["monsters"][0]["hp"] += damage_delta
                after["combat"]["snapshot"]["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"] += damage_delta
                writer = EpisodeWriter(root, role="IRONCLAD", seed=42, policy={"ascension": 0})
                writer.observation(dict(sequence=0, state_token="turn-0", state=before))
                writer.transition(before_sequence=0, after_sequence=1, method="playCard", args=[0, 0],
                                  effect="APPLIED", policy={})
                writer.observation(dict(sequence=1, state_token="turn-1", state=after))
                result = writer.finish(error="fixture ends before terminal")
                if damage_delta:
                    with self.assertRaisesRegex(ValueError, "Replay effect failed"):
                        verify_replay(result["path"], worker, require_complete=False)
                else:
                    replay = verify_replay(result["path"], worker, require_complete=False)
                    self.assertEqual(replay["checks"], {"combat": 1, "menu": 0})
                    with self.assertRaisesRegex(ValueError, "censored"):
                        verify_replay(result["path"], worker)


class SQLiteE2E(unittest.TestCase):
    def test_export_reopen_and_replay_duplicate_archive(self):
        from selectcard.policy.test_run_policy import write_corpus
        from selectcard.policy.from_runs import export_runs
        from selectcard.policy.shards import ShardDataset, verify_dataset

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "archives"
            runs = write_corpus(source, pilot=True)
            (source / "duplicate.run").write_text(json.dumps(runs[0]), encoding="utf-8")
            output = root / "dataset"
            exported = export_runs(source, output, stage="pilot", shard_size=3)
            self.assertEqual(exported["report"]["reasons"]["duplicate_run"], 1)
            uri = (output / "index.sqlite").as_uri() + "?mode=ro"
            with closing(sqlite3.connect(uri, uri=True)) as db:
                self.assertEqual(db.execute("PRAGMA integrity_check").fetchone(), ("ok",))
                self.assertEqual(db.execute("SELECT count(*) FROM examples").fetchone()[0],
                                 exported["report"]["samples"])
            self.assertEqual(verify_dataset(output), exported)
            first = ShardDataset(output, "train", cache_size=1)
            try:
                expected = list(first)
            finally:
                first.close()
            again = ShardDataset(output, "train", cache_size=1)
            try:
                self.assertEqual(list(again), expected)
                self.assertEqual(len({(r["run_id"], r["decision_id"]) for r in again}), len(again))
            finally:
                again.close()


if __name__ == "__main__":
    unittest.main()
