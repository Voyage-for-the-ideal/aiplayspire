"""Tests for the map/key/history context data layer."""

import gzip
import json
import os
import unittest

import sts_mapgen
from map_context import (
    FLAG_BURNING,
    FLAG_REACHABLE,
    FLAG_VALID,
    FLAG_CURRENT_CANDIDATE,
    HISTORY_HORIZON,
    MAP_CONTEXT_VERSION,
    MAP_GRID_CELLS,
    ROOM_SYMBOL_TO_ID,
    RunMapContextBuilder,
    build_run_contexts,
    decode_map_features,
    forward_consistent_positions,
    inference_context,
    keys_token_features,
    segment_of_floor,
    split_act_segments,
)

ARCHIVED_RUN = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "STS Data", "Monthly_2020_07", "2020-07-10-19-14#1423.json.gz",
)
ARCHIVED_SEED = -6913098335243691825


def _load_archived_run():
    if not os.path.exists(ARCHIVED_RUN):
        return None
    with gzip.open(ARCHIVED_RUN, "rt", encoding="utf-8") as handle:
        runs = json.load(handle)
    for wrapped in runs:
        event = wrapped.get("event") if isinstance(wrapped, dict) else None
        if isinstance(event, dict) and str(event.get("seed_played")) == str(ARCHIVED_SEED):
            return event
    return None


class SegmentTests(unittest.TestCase):
    # Real floor layout: act rooms occupy 15 rows, then "B"; the inter-act
    # None separator sits on the boss-reward floor (17 / 34), which has no map
    # room and therefore no map context.
    PATH = (
        ["M", "M", "?", "M", "?", "R", "E", "?", "T", "?", "M", "M", "M", "$", "R", "B",
         None]
        + ["M", "$", "M", "?", "?", "R", "M", "E", "T", "R", "$", "E", "M", "M", "R", "B",
           None]
        + ["M", "T", "?", "$", "?", "E", "M", "E", "T", "R", "E", "E", "M", "$", "R", "B"]
    )

    def test_segments_split_on_none_and_boss(self):
        segments = split_act_segments(self.PATH)
        self.assertEqual(len(segments), 3)
        start, symbols = segments[0]
        self.assertEqual(start, 0)
        self.assertEqual(symbols[-1], "B")
        self.assertEqual(len(symbols), 16)
        start, symbols = segments[1]
        self.assertEqual(start, 17)
        start, symbols = segments[2]
        self.assertEqual(start, 34)

    def test_floor_lookup(self):
        segments = split_act_segments(self.PATH)
        self.assertEqual(segment_of_floor(segments, 1), (1, 0, 0))
        self.assertEqual(segment_of_floor(segments, 15), (1, 0, 14))
        self.assertEqual(segment_of_floor(segments, 16), (1, 0, 15))
        self.assertIsNone(segment_of_floor(segments, 17))  # boss-reward floor
        self.assertEqual(segment_of_floor(segments, 18), (2, 1, 0))
        self.assertEqual(segment_of_floor(segments, 33), (2, 1, 15))
        self.assertIsNone(segment_of_floor(segments, 34))
        self.assertEqual(segment_of_floor(segments, 35), (3, 2, 0))
        self.assertEqual(segment_of_floor(segments, 50), (3, 2, 15))
        self.assertIsNone(segment_of_floor(segments, 0))


class ConsistencyTests(unittest.TestCase):
    def test_forward_positions_follow_reconstructed_map(self):
        nodes = sts_mapgen.generate_act_map(ARCHIVED_SEED, 1, ascension_level=3)
        symbols = ["M", "M", "?", "M", "?", "R", "E", "?", "T", "?", "M", "M", "M", "$", "R"]
        rows = forward_consistent_positions(nodes, symbols)
        self.assertTrue(all(row for row in rows))
        # Traversal was validated by validate_map_reconstruction.py; the final
        # row must be non-empty and contained in row 14 columns.
        self.assertTrue(rows[-1])

    def test_inconsistent_symbol_yields_empty_row(self):
        nodes = sts_mapgen.generate_act_map(ARCHIVED_SEED, 1, ascension_level=3)
        rows = forward_consistent_positions(nodes, ["R"] * 15)  # row 0 is all monsters
        self.assertEqual(rows[0], set())


@unittest.skipUnless(_load_archived_run(), "archived run data not available")
class RunMapContextBuilderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.event = _load_archived_run()
        cls.builder = RunMapContextBuilder(cls.event)

    def test_seed_and_segments_recovered(self):
        self.assertEqual(self.builder.seed, ARCHIVED_SEED)
        self.assertEqual(len(self.builder.segments), 3)

    def test_context_layout_is_packed_and_fixed_size(self):
        context = self.builder.context_for_floor(3)
        self.assertEqual(context["act"], 1)
        self.assertEqual(len(context["map_rooms"]), MAP_GRID_CELLS)
        self.assertEqual(len(context["map_flags"]), MAP_GRID_CELLS)
        self.assertNotIn("plan_rooms", context)
        self.assertEqual(len(context["history_rooms"]), HISTORY_HORIZON)
        self.assertEqual(len(context["key_states"]), 3)
        self.assertEqual(context["key_known_mask"], [1, 1, 0])

    def test_history_is_current_act_most_recent_first(self):
        # Floor 9 -> act 1, row 8; walked rooms = symbols[:8] reversed.
        context = self.builder.context_for_floor(9)
        expected = ["?", "E", "R", "?", "M", "?", "M", "M"]
        self.assertEqual(
            context["history_rooms"], [ROOM_SYMBOL_TO_ID[s] for s in expected]
        )

    def test_history_empty_at_act_start(self):
        context = self.builder.context_for_floor(18)  # act 2, row 0
        self.assertEqual(context["history_rooms"], [0] * HISTORY_HORIZON)

    def test_history_excludes_previous_act(self):
        # Act 2 floors must not carry act 1 rooms even at short in-act depth.
        context = self.builder.context_for_floor(19)  # act 2, row 1
        self.assertEqual(
            context["history_rooms"],
            [ROOM_SYMBOL_TO_ID["M"]] + [0] * (HISTORY_HORIZON - 1),
        )

    def test_mid_act_death_history_contains_no_future_rooms(self):
        died = {
            "seed_played": "not-a-seed",
            "path_per_floor": ["M", "?", "$", "E", "R"],
        }
        builder = RunMapContextBuilder(died)
        # History is the walked prefix of this act, most recent first, and
        # never contains the current or any future room.
        expected_by_floor = {
            1: [],
            2: ["M"],
            3: ["?", "M"],
            4: ["$", "?", "M"],
            5: ["E", "$", "?", "M"],
        }
        for floor, walked in expected_by_floor.items():
            context = builder.context_for_floor(floor)
            self.assertEqual(
                context["history_rooms"],
                [ROOM_SYMBOL_TO_ID[s] for s in walked]
                + [0] * (HISTORY_HORIZON - len(walked)),
            )

    def test_current_candidates_only_on_current_row(self):
        context = self.builder.context_for_floor(3)
        visited_rows = {
            index // 7
            for index, bits in enumerate(context["map_flags"])
            if bits & FLAG_CURRENT_CANDIDATE
        }
        self.assertTrue(visited_rows.issubset({2}))
        self.assertIn(2, visited_rows)

    def test_map_cells_only_for_connected_rooms(self):
        context = self.builder.context_for_floor(3)
        rooms = [room for room in context["map_rooms"] if room]
        self.assertTrue(rooms)
        self.assertTrue(all(bits & FLAG_VALID for bits in context["map_flags"] if bits))

    def test_ruby_key_flips_after_recall(self):
        # This run chose RECALL at floor 23 (act 2).
        before = self.builder.context_for_floor(22)
        at = self.builder.context_for_floor(23)
        after = self.builder.context_for_floor(24)
        self.assertEqual(before["key_states"][0], 0)
        self.assertEqual(at["key_states"][0], 0)  # decision-time snapshot
        self.assertEqual(after["key_states"][0], 1)

    def test_emerald_state_uses_burning_elite_on_path(self):
        # The act 2 burning elite is either on the realized path (key flips on
        # later floors) or not (never flips); both are valid, but the flag must
        # be monotone in floor within the act.
        floors = range(18, 24)
        values = [self.builder.context_for_floor(f)["key_states"][1] for f in floors]
        self.assertEqual(values, sorted(values))

    def test_missing_seed_degrades_to_history_only(self):
        run = dict(self.event)
        run["seed_played"] = "not-a-seed"
        builder = RunMapContextBuilder(run)
        self.assertIsNone(builder.seed)
        context = builder.context_for_floor(3)
        self.assertFalse(any(context["map_rooms"]))
        self.assertTrue(any(context["history_rooms"]))
        self.assertEqual(context["key_states"][0], 0)  # ruby needs recall only

    def test_archived_treasure_does_not_fabricate_sapphire_label(self):
        run = dict(self.event)
        path = list(run["path_per_floor"])
        path[2] = "T"
        run["path_per_floor"] = path
        context = RunMapContextBuilder(run).context_for_floor(3)
        self.assertEqual(context["key_states"][2], 0)
        self.assertEqual(context["key_known_mask"][2], 0)

    def test_act_four_strictly_proves_all_keys(self):
        run = {
            "seed_played": ARCHIVED_SEED,
            "path_per_floor": ["B", "B", "B", "M"],
            "campfire_choices": [],
            "relics_obtained": [],
        }
        context = RunMapContextBuilder(run).context_for_floor(4)
        self.assertEqual(context["act"], 4)
        self.assertEqual(context["key_states"], [1, 1, 1])
        self.assertEqual(context["key_known_mask"], [1, 1, 1])

    def test_build_run_contexts_matches_individual_calls(self):
        floors = [1, 5, 17, 40]
        self.assertEqual(
            build_run_contexts(self.event, floors),
            [self.builder.context_for_floor(f) for f in floors],
        )


class InferenceContextTests(unittest.TestCase):
    STATE = {
        "act": 1,
        "floor_in_act": 3,
        "current_node": (3, 3),
        "map_nodes": [
            {"x": 3, "y": 3, "symbol": "M", "burning": False,
             "children": [{"x": 3, "y": 4}, {"x": 4, "y": 4}]},
            {"x": 4, "y": 4, "symbol": "E", "burning": True,
             "children": [{"x": 4, "y": 5}]},
            {"x": 3, "y": 4, "symbol": "R", "burning": False, "children": []},
        ],
        "plan": ["R", "E"],  # ignored: router plans are not model input
        "history": ["M", "E", "R"],
        "keys": {"ruby": True, "emerald": False, "sapphire": False},
    }

    def test_packing(self):
        context = inference_context(self.STATE)
        self.assertEqual(len(context["map_rooms"]), MAP_GRID_CELLS)
        self.assertEqual(context["map_rooms"][3 * 7 + 3], ROOM_SYMBOL_TO_ID["M"])
        self.assertEqual(context["map_rooms"][4 * 7 + 4], ROOM_SYMBOL_TO_ID["E"])
        self.assertTrue(context["map_flags"][4 * 7 + 4] & FLAG_BURNING)
        self.assertTrue(context["map_flags"][3 * 7 + 3] & FLAG_CURRENT_CANDIDATE)
        self.assertTrue(context["map_flags"][4 * 7 + 4] & FLAG_REACHABLE)
        self.assertFalse(context["map_flags"][3 * 7 + 4] & FLAG_REACHABLE)
        self.assertEqual((context["map_flags"][3 * 7 + 3] >> 4) & 3, 2)
        self.assertEqual((context["map_flags"][4 * 7 + 4] >> 6) & 3, 1)
        self.assertNotIn("plan_rooms", context)
        # Chronological input becomes most-recent-first packing.
        self.assertEqual(
            context["history_rooms"][:3],
            [ROOM_SYMBOL_TO_ID["R"], ROOM_SYMBOL_TO_ID["E"], ROOM_SYMBOL_TO_ID["M"]],
        )
        self.assertEqual(len(context["history_rooms"]), HISTORY_HORIZON)
        self.assertEqual(context["key_states"], [1, 0, 0])
        self.assertEqual(context["key_known_mask"], [1, 1, 1])

    def test_empty_state_degrades(self):
        context = inference_context({})
        self.assertEqual(context["map_rooms"], [0] * MAP_GRID_CELLS)
        self.assertNotIn("plan_rooms", context)
        self.assertEqual(context["history_rooms"], [0] * HISTORY_HORIZON)
        self.assertEqual(context["key_states"], [0, 0, 0])
        self.assertEqual(context["key_known_mask"], [0, 0, 0])


class FeatureDecodeTests(unittest.TestCase):
    def test_decode_roundtrip(self):
        map_rooms = [0] * MAP_GRID_CELLS
        map_flags = [0] * MAP_GRID_CELLS
        index = 4 * 7 + 2
        map_rooms[index] = ROOM_SYMBOL_TO_ID["E"]
        map_flags[index] = (
            FLAG_VALID | FLAG_BURNING | FLAG_CURRENT_CANDIDATE | FLAG_REACHABLE
            | (2 << 4) | (1 << 6)
        )
        rooms, flags = decode_map_features(map_rooms, map_flags)
        self.assertEqual(len(rooms), MAP_GRID_CELLS)
        self.assertEqual(len(flags[0]), 7)
        row = flags[index]
        self.assertEqual(row[:3], [1.0, 1.0, 1.0])
        self.assertAlmostEqual(row[3], 2 / 6)
        self.assertAlmostEqual(row[4], 4 / 14)
        self.assertAlmostEqual(row[5], 2 / 3)
        self.assertAlmostEqual(row[6], 1 / 3)

    def test_keys_token_features(self):
        map_flags = [FLAG_BURNING] * 3
        features = keys_token_features([0, 0, 0], map_flags, 2)
        self.assertEqual(features[6], 1.0)  # burning pending
        features = keys_token_features([1, 1, 0], [], 3)
        self.assertEqual(features[6], 0.0)  # emerald collected
        self.assertAlmostEqual(features[7], 2 / 3)


if __name__ == "__main__":
    unittest.main()
