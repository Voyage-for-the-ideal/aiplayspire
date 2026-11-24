"""Synthetic observations only: no engine, client, or Agent is constructed."""
import copy
import json
import unittest
from uuid import UUID

from sts_ai_framework.headless.state import BRIDGE_SCHEMA, HeadlessContractError, HeadlessStateMapper
from sts_ai_framework.models import GameState


def card(number=1, **changes):
    result = {"id": "Searing Blow", "uuid": str(UUID(int=number)), "name": "本地化展示名称",
              "cost": 2, "costForTurn": 0, "timesUpgraded": 3, "upgraded": True,
              "type": "ATTACK", "rarity": "UNCOMMON", "target": "ENEMY", "misc": 0,
              "bottled": False, "canUpgrade": True, "baseDamage": 27, "damage": 99,
              "baseBlock": -1, "block": -1, "baseMagicNumber": 0, "magicNumber": 0, "exhaust": False}
    result.update(changes)
    return result


def observation(**changes):
    result = {"bridgeSchema": BRIDGE_SCHEMA, "phase": "MAP", "floor": 3, "act": 1, "ascension": 10,
              "room": "MonsterRoom", "roomPhase": "COMPLETE", "firstRoomChosen": True,
              "act_boss": "The Guardian", "observedKeys": {}, "history": [],
              "player": {"className": "IRONCLAD", "hp": 55, "maxHp": 80, "gold": 99, "block": 0, "energy": 0,
                         "masterDeck": [card()], "hand": [], "drawPile": [], "discardPile": [], "exhaustPile": [],
                         "relics": [], "potions": []},
              "map": {"current": {"x": 1, "y": 2}, "nodes": [
                  {"x": 0, "y": 1, "symbol": "M", "burning": False,
                   "children": [{"x": 1, "y": 2}, {"x": 1, "y": 2}]},
                  {"x": 1, "y": 2, "symbol": "E", "burning": True, "children": [{"x": 1, "y": 3}]},
                  {"x": 1, "y": 3, "symbol": "R", "burning": False, "children": []},
              ]}}
    result.update(changes)
    return result


def mapped(raw=None):
    return HeadlessStateMapper().map_state(raw or observation(), run_id="synthetic-A", sequence=0)


class CardContractTest(unittest.TestCase):
    def test_real_standard_run_has_nullable_special_seed(self):
        raw = observation(runMode={"is_daily": False, "is_endless": False, "is_trial": False,
                                   "is_beta": False, "chose_seed": True, "special_seed": None})
        self.assertIsNone(mapped(raw).run_mode["special_seed"])

    def test_real_ui_boss_edge_row16_normalizes_to_kernel_boss_exit(self):
        raw = observation(floor=15)
        raw["map"] = {"current": {"x": 1, "y": 14}, "nodes": [
            {"x": 1, "y": 14, "symbol": "R", "burning": False, "children": [{"x": 3, "y": 16}]}]}
        state = mapped(raw)
        self.assertEqual([(n.x, n.y) for n in state.map_nodes], [(1, 14), (-1, 15)])
        self.assertEqual((state.map_nodes[0].children[0].x, state.map_nodes[0].children[0].y), (-1, 15))

    def test_upgrade_count_and_permanent_vs_combat_values(self):
        result = mapped()
        c = result.deck[0]
        self.assertEqual((c.reference, c.upgrades), ("Searing Blow+3", 3))
        self.assertEqual((c.cost, c.cost_for_turn, c.base_damage, c.damage), (2, 0, 27, 99))
        self.assertEqual(c.base_magic_number, 0)
        self.assertIsNone(c.base_block)
        self.assertFalse(c.is_playable)

    def test_count_is_not_inferred_from_name_or_boolean(self):
        raw = observation()
        raw["player"]["masterDeck"] = [card(name="展示+99", timesUpgraded=0, upgraded=True)]
        self.assertEqual(mapped(raw).deck[0].reference, "Searing Blow")
        del raw["player"]["masterDeck"][0]["timesUpgraded"]
        with self.assertRaises(HeadlessContractError):
            mapped(raw)

    def test_real_uuid_survives_reorder_and_all_piles(self):
        raw = observation(phase="PLAYER_TURN", roomPhase="COMBAT")
        raw["player"]["masterDeck"] = [card(1), card(2)]
        raw["player"]["hand"] = [card(1, timesUpgraded=4)]  # Temporary combat upgrade may share the deck UUID.
        raw["player"]["drawPile"] = [card(2)]
        raw["player"]["discardPile"] = [card(3)]
        raw["player"]["exhaustPile"] = [card(4)]
        mapper = HeadlessStateMapper()
        first = mapper.map_state(raw, run_id="A", sequence=0)
        raw["player"]["masterDeck"].reverse()
        second = mapper.map_state(raw, run_id="A", sequence=1)
        self.assertEqual(second.deck[0].uuid, first.deck[1].uuid)
        self.assertEqual(second.deck[0].index, 0)
        self.assertEqual(first.hand[0].uuid, first.deck[0].uuid)
        self.assertEqual(first.hand[0].upgrades, 4)
        self.assertEqual((first.draw_pile_size, first.discard_pile_size, first.exhaust_pile_size), (1, 1, 1))

    def test_duplicate_card_ids_are_distinct_but_duplicate_uuid_in_group_fails(self):
        raw = observation()
        raw["player"]["masterDeck"] = [card(1), card(2)]
        self.assertEqual(len(mapped(raw).deck), 2)
        raw["player"]["masterDeck"][1]["uuid"] = card(1)["uuid"]
        with self.assertRaisesRegex(HeadlessContractError, "Duplicate UUID"):
            mapped(raw)

    def test_missing_uuid_is_not_replaced_by_id_or_index(self):
        for value in (None, "Searing Blow", ""):
            raw = observation()
            raw["player"]["masterDeck"][0]["uuid"] = value
            with self.assertRaises(HeadlessContractError):
                mapped(raw)

    def test_all_selection_and_reward_cards_keep_full_metadata(self):
        for kind, screen in (("grid", "GRID"), ("hand", "HAND_SELECT")):
            raw = observation(phase="CARD_SELECT", cardSelection={"kind": kind, "options": [card()], "selected": [card()]})
            result = mapped(raw)
            self.assertEqual(result.screen_type, screen)
            self.assertEqual(result.grid_cards[0].reference, "Searing Blow+3")
            self.assertEqual(result.grid_cards[0].uuid, result.grid_selected_cards[0].uuid)
            self.assertIsNone(result.grid_cards[0].choice_index)
        result = mapped(observation(phase="CARD_PICK", cardRewardOptions=[card()]))
        self.assertEqual(result.card_reward_cards[0].upgrades, 3)

    def test_group_order_index_and_counts_are_checked(self):
        raw = observation()
        raw["player"]["masterDeck"][0]["index"] = 1
        with self.assertRaises(HeadlessContractError):
            mapped(raw)
        raw = observation()
        raw["player"]["drawPileCount"] = 2
        with self.assertRaises(HeadlessContractError):
            mapped(raw)


class MapContractTest(unittest.TestCase):
    def test_coordinates_real_unique_edges_current_and_burning(self):
        state = mapped()
        self.assertEqual([(n.x, n.y) for n in state.map_nodes], [(0, 1), (1, 2), (1, 3)])
        self.assertEqual(len(state.map_nodes[0].children), 1)
        self.assertTrue(state.map_nodes[1].burning and state.map_nodes[1].is_current)
        self.assertEqual((state.current_map_node.x, state.current_map_node.y), (1, 2))
        self.assertEqual(state.map_position.floor, 2)  # Map row is not global floor 3.
        self.assertEqual(state.current_map_choices, [])

    def test_order_does_not_change_graph_and_backing_cells_are_pruned(self):
        raw = observation()
        expected = mapped(raw).map_nodes
        raw["map"]["nodes"].reverse()
        raw["map"]["nodes"].append({"x": 6, "y": 7, "symbol": "M", "burning": False, "children": []})
        self.assertEqual(mapped(raw).map_nodes, expected)

    def test_virtual_boss_is_outside_105_grid_cells(self):
        raw = observation(floor=15)
        raw["map"] = {"current": {"x": 3, "y": 14}, "nodes": [
            {"x": 3, "y": 14, "symbol": "R", "burning": False,
             "children": [{"x": 3, "y": 15}, {"x": -1, "y": 15}]}]}
        state = mapped(raw)
        self.assertEqual([(n.x, n.y) for n in state.map_nodes], [(3, 14), (-1, 15)])
        self.assertEqual(len(state.map_nodes[0].children), 1)
        raw["map"]["current"] = {"x": -1, "y": 15}
        self.assertEqual(mapped(raw).current_map_node.symbol, "B")

    def test_neow_sentinel_is_not_a_fabricated_grid_node(self):
        raw = observation(phase="NEOW", floor=0, firstRoomChosen=False)
        raw["map"]["current"] = {"x": 0, "y": -1}
        self.assertIsNone(mapped(raw).current_map_node)

    def test_current_row_is_not_confused_with_global_floor(self):
        with self.assertRaisesRegex(HeadlessContractError, "map row"):
            mapped(observation(act=2, floor=19))

    def test_bad_duplicate_dangling_and_winged_topology_fail(self):
        for mutation in ("duplicate", "dangling", "skip", "winged"):
            raw = observation()
            if mutation == "duplicate":
                raw["map"]["nodes"].append(copy.deepcopy(raw["map"]["nodes"][0]))
            else:
                edge = raw["map"]["nodes"][0]["children"][0]
                if mutation == "dangling": edge["x"] = 6
                elif mutation == "skip": edge["y"] = 3
                else: edge["winged"] = True
            with self.assertRaises(HeadlessContractError, msg=mutation):
                mapped(raw)


class KeysHistoryContractTest(unittest.TestCase):
    def test_partial_keys_preserve_known_mask_through_json(self):
        mapper = HeadlessStateMapper()
        payload = mapper.map_payload(observation(observedKeys={"emerald": True}), run_id="A", sequence=0)
        self.assertEqual(payload["keys"], {"emerald": True})
        state = GameState(**json.loads(json.dumps(payload)))
        self.assertEqual(state.keys.model_fields_set, {"emerald"})

    def test_confirmed_true_survives_act_change_and_map_absence(self):
        mapper = HeadlessStateMapper()
        mapper.map_state(observation(observedKeys={"emerald": True, "ruby": False}), run_id="A", sequence=0)
        next_state = mapper.map_state(observation(act=2, floor=18, map=None), run_id="A", sequence=1)
        self.assertEqual(next_state.keys.model_fields_set, {"emerald"})
        self.assertTrue(next_state.keys.emerald)
        self.assertEqual(next_state.map_nodes, [])
        self.assertFalse(next_state.keys.sapphire)
        self.assertNotIn("sapphire", next_state.keys.model_fields_set)

    def test_false_observation_is_known_but_missing_is_unknown(self):
        state = mapped(observation(observedKeys={"sapphire": False}))
        self.assertEqual(state.keys.model_fields_set, {"sapphire"})
        self.assertEqual(mapped().keys.model_fields_set, set())

    def test_neither_burning_map_nor_act_infers_unobserved_keys(self):
        self.assertEqual(mapped().keys.model_fields_set, set())
        self.assertEqual(mapped(observation(act=4, floor=53, map=None)).keys.model_fields_set, set())

    def test_cross_run_reset_and_regression_rejection(self):
        mapper = HeadlessStateMapper()
        mapper.map_state(observation(observedKeys={"emerald": True}), run_id="A", sequence=0)
        with self.assertRaisesRegex(HeadlessContractError, "regressed"):
            mapper.map_state(observation(observedKeys={"emerald": False}), run_id="A", sequence=1)
        state = mapper.map_state(observation(), run_id="B", sequence=0)
        self.assertEqual(state.keys.model_fields_set, set())

    def test_history_preserves_selected_vs_resolved_and_drops_current_future(self):
        raw = observation(floor=20, act=2, history=[
            {"floor": 19, "selected_symbol": "?", "resolved_symbol": "M"},
            {"floor": 17, "selected_symbol": "B", "resolved_symbol": "B"},
            {"floor": 20, "selected_symbol": "$", "resolved_symbol": "$"},
            {"floor": 21, "selected_symbol": "?", "resolved_symbol": "E"},
            {"floor": 18, "selected_symbol": "M", "resolved_symbol": None}])
        state = mapped(raw)
        self.assertEqual([x.floor for x in state.path_history], [17, 19])
        self.assertEqual((state.path_history[1].selected_symbol, state.path_history[1].resolved_symbol), ("?", "M"))

    def test_duplicate_history_floor_is_not_silently_merged(self):
        entry = {"floor": 1, "selected_symbol": "?", "resolved_symbol": "M"}
        with self.assertRaises(HeadlessContractError):
            mapped(observation(history=[entry, entry]))


class LifecycleContractTest(unittest.TestCase):
    def test_boot_compatible_and_no_fake_run(self):
        result = HeadlessStateMapper().map_state({"phase": "BOOT", "floor": 0, "act": 0})
        self.assertEqual(result.screen_type, "MAIN_MENU")
        self.assertIsNone(result.player)
        with self.assertRaises(HeadlessContractError):
            HeadlessStateMapper().map_state({"phase": "BOOT", "floor": 5})

    def test_rejects_legacy_lossy_schema_unknown_character_and_missing_ascension(self):
        for field, value in (("bridgeSchema", "legacy"), ("phase", None), ("ascension", None)):
            with self.assertRaises(HeadlessContractError): mapped(observation(**{field: value}))
        raw = observation()
        raw["player"]["className"] = "SILENT"
        with self.assertRaises(HeadlessContractError): mapped(raw)

    def test_four_characters_and_no_write_capabilities(self):
        for character in ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"):
            raw = observation(phase="PLAYER_TURN")
            raw["player"]["className"] = character
            state = mapped(raw)
            self.assertEqual(state.character, character)
            self.assertFalse(state.can_proceed or state.can_cancel or state.is_end_turn_button_enabled or state.choice_list)

    def test_hidden_rng_future_boss_and_router_plan_are_not_forwarded(self):
        raw = observation(seed=123, rng_state={"ai": 42}, next_boss="hidden-A20", router_plan=[1, 2])
        payload = HeadlessStateMapper().map_payload(raw, run_id="A", sequence=0)
        self.assertFalse(set(payload) & {"seed", "rng_state", "router_plan"})
        self.assertNotIn("next_boss", payload)
        self.assertEqual(payload["visible_boss"], "The Guardian")
        self.assertEqual(mapped(observation(phase="BOSS_RELIC")).visible_boss, "NO_BOSS")
        self.assertEqual(mapped(observation(room="MonsterRoomBoss", roomPhase="COMPLETE")).visible_boss, "NO_BOSS")

    def test_snapshot_order_retry_and_rewind_rules(self):
        mapper = HeadlessStateMapper()
        raw = observation()
        first = mapper.map_state(raw, run_id="A", sequence=5)
        self.assertEqual(mapper.map_state(raw, run_id="A", sequence=5), first)
        for raw2, sequence in ((raw, 4), (observation(floor=4), 5), (observation(floor=2), 6)):
            with self.assertRaises(HeadlessContractError):
                mapper.map_state(raw2, run_id="A", sequence=sequence)
        mapper.reset()
        self.assertIsInstance(mapper.map_state(observation(floor=1, map=None), run_id="A", sequence=0), GameState)

    def test_failed_mapping_does_not_commit_future_keys_or_mutate_input(self):
        mapper = HeadlessStateMapper()
        mapper.map_state(observation(), run_id="A", sequence=0)
        bad = observation(floor=4, observedKeys={"emerald": True})
        bad["player"]["masterDeck"][0]["uuid"] = "broken"
        before = copy.deepcopy(bad)
        with self.assertRaises(HeadlessContractError): mapper.map_state(bad, run_id="A", sequence=1)
        self.assertEqual(bad, before)
        self.assertEqual(mapper.map_state(observation(), run_id="A", sequence=1).keys.model_fields_set, set())

    def test_late_model_validation_failure_also_keeps_key_memory_atomic(self):
        mapper = HeadlessStateMapper()
        mapper.map_state(observation(), run_id="A", sequence=0)
        with self.assertRaises(HeadlessContractError):
            mapper.map_state(observation(roomPhase=123, observedKeys={"emerald": True}), run_id="A", sequence=1)
        self.assertEqual(mapper.map_state(observation(), run_id="A", sequence=1).keys.model_fields_set, set())

    def test_valid_mapping_preserves_input_and_boot_resets_run_memory(self):
        raw = observation(observedKeys={"emerald": True})
        before = copy.deepcopy(raw)
        mapper = HeadlessStateMapper()
        mapper.map_state(raw, run_id="A", sequence=10)
        self.assertEqual(raw, before)
        mapper.map_state({"phase": "BOOT"})
        self.assertEqual(mapper.map_state(observation(), run_id="A", sequence=0).keys.model_fields_set, set())


if __name__ == "__main__":
    unittest.main()
