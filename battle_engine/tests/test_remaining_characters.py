"""Remaining cards and independent SaveState selection continuation imports."""
import copy
import os
from pathlib import Path
import unittest

from battle_engine.server import execute_worker
from battle_engine.tests import test_character_strategy as helpers

EXE = Path(os.environ.get("BATTLE_ENGINE_WORKER", str(Path(__file__).resolve().parents[1] / "bin/battle-engine-worker.exe")))


@unittest.skipUnless(EXE.is_file(), "Build the native worker")
class RemainingCharactersTests(unittest.TestCase):
    card = helpers.CharacterStrategyTests.card
    state = helpers.CharacterStrategyTests.state
    run_worker = helpers.CharacterStrategyTests.run_worker
    sequence = helpers.CharacterStrategyTests.sequence
    play = helpers.CharacterStrategyTests.play
    select = helpers.CharacterStrategyTests.select

    def test_forge_upgrades_watcher_costs_and_skips_status_cards(self):
        cases = [("Vault", 3, 2), ("Eruption", 2, 1), ("Omniscience", 4, 3),
                 ("Study", 2, 1), ("Crescendo", 1, 0), ("MasterReality", 1, 0),
                 ("Adaptation", 1, 0), ("Scrawl", 1, 0), ("ClearTheMind", 1, 0)]
        s = self.state(role="WATCHER", hand=[self.card(name, cost=cost) for name, cost, _ in cases]
                       + [self.card("Wound", cost=-2)])
        s["player_state"]["potions"] = [dict(id="BlessingOfTheForge", slot=0)]
        r = self.run_worker(s, action_type="POTION", potion_index=0)
        for actual, (name, _, cost) in zip(r["projection"]["hand"], cases):
            self.assertEqual((actual["id"], actual["upgrades"], actual["cost"], actual["turn_cost"]),
                             (name, 1, cost, cost))
        self.assertEqual(r["projection"]["hand"][-1]["upgrades"], 0)

    def test_forge_preserves_turn_cost_adjustment(self):
        for name, base, upgraded in (("Vault", 3, 2), ("Eruption", 2, 1)):
            for turn_cost in (0, 1, base, base + 1):
                with self.subTest(name=name, turn_cost=turn_cost):
                    c = self.card(name, cost=base)
                    c["cost_for_turn"] = turn_cost
                    s = self.state(role="WATCHER", hand=[c])
                    s["player_state"]["potions"] = [dict(id="BlessingOfTheForge", slot=0)]
                    actual = self.run_worker(s, action_type="POTION", potion_index=0)["projection"]["hand"][0]
                    expected = max(0, upgraded + turn_cost - base) if turn_cost > 0 else 0
                    self.assertEqual((actual["cost"], actual["turn_cost"]), (upgraded, expected))

    def test_forge_respects_prior_upgrades_and_blood_for_blood_cost(self):
        cards = [self.card("Vault", up=True, cost=2), self.card("Searing Blow", up=True, cost=2),
                 self.card("Blood for Blood", cost=2), self.card("Blood for Blood", cost=0)]
        cards[1]["times_upgraded"] = 2
        s = self.state(role="IRONCLAD", hand=cards)
        s["player_state"]["potions"] = [dict(id="BlessingOfTheForge", slot=0)]
        hand = self.run_worker(s, action_type="POTION", potion_index=0)["projection"]["hand"]
        self.assertEqual([(c["upgrades"], c["cost"], c["turn_cost"]) for c in hand],
                         [(1, 2, 2), (3, 2, 2), (1, 1, 1), (1, 0, 0)])

    def test_forge_other_roles_and_force_field(self):
        groups = [
            ("IRONCLAD", [("Infernal Blade", 1, 0), ("Barricade", 3, 2),
                          ("Dark Embrace", 2, 1), ("Exhume", 1, 0)]),
            ("DEFECT", [("Dualcast", 1, 0), ("Double Energy", 1, 0),
                        ("Creative AI", 3, 2), ("Force Field", 2, 2)]),
            ("THE_SILENT", [("Venomology", 1, 0), ("Bullet Time", 3, 2),
                            ("Distraction", 1, 0), ("Envenom", 2, 1),
                            ("Night Terror", 3, 2), ("Phantasmal Killer", 1, 0),
                            ("Setup", 1, 0), ("Terror", 1, 0),
                            ("Tools of the Trade", 1, 0)]),
            ("WATCHER", [("Beta", 2, 1)]),
        ]
        for role, cases in groups:
            with self.subTest(role=role):
                s = self.state(role=role, hand=[self.card(n, cost=before) for n, before, _ in cases])
                s["player_state"]["potions"] = [dict(id="BlessingOfTheForge", slot=0)]
                hand = self.run_worker(s, action_type="POTION", potion_index=0)["projection"]["hand"]
                self.assertEqual([(c["id"], c["upgrades"], c["cost"], c["turn_cost"]) for c in hand],
                                 [(n, 1, after, after) for n, _, after in cases])

    def test_potions_create_shivs_and_miracles_at_original_cost(self):
        for role, potion, card, count, upgrades in (
            ("THE_SILENT", "CunningPotion", "Shiv", 3, 1),
            ("WATCHER", "BottledMiracle", "Miracle", 2, 0),
        ):
            for bark in (False, True):
                with self.subTest(potion=potion, bark=bark):
                    s = self.state(role=role, hand=[self.card("Defend_R")])
                    s["player_state"]["potions"] = [dict(id=potion, slot=0)]
                    if bark:
                        s["player_state"]["relics"] = [dict(relic_id="SacredBark", counter=-1)]
                    hand = self.run_worker(s, action_type="POTION", potion_index=0)["projection"]["hand"]
                    made = [c for c in hand if c["id"] == card]
                    self.assertEqual(len(made), count * (2 if bark else 1))
                    self.assertTrue(all((c["upgrades"], c["cost"], c["turn_cost"]) == (upgrades, 0, 0) for c in made))

    def test_discovery_potion_keeps_base_cost_and_free_turn_cost(self):
        cases = [
            ("SkillPotion", "skills", {"Vault": (3, 2), "Omniscience": (4, 3), "Study": (2, 1)}),
            ("AttackPotion", "attacks", {"Wallop": (2, 2), "Ragnarok": (3, 3), "Consecrate": (0, 0)}),
            ("PowerPotion", "powers", {"DevaForm": (3, 3), "Study": (2, 1), "Adaptation": (1, 0)}),
            ("ColorlessPotion", "colorless", {"Trip": (0, 0), "Apotheosis": (2, 1), "Mind Blast": (2, 1)}),
        ]
        for potion, pool, costs in cases:
            for master_reality in (False, True):
                with self.subTest(potion=potion, master_reality=master_reality):
                    s = self.state(role="WATCHER", hand=[self.card("Defend_P")],
                                   powers=[("MasterRealityPower", 1)] if master_reality else [])
                    s["rng_state"]["card_rng_counter"] = 0
                    s["_combat_card_pools"] = self.foreign()["_combat_card_pools"]
                    s["_combat_card_pools"][pool] = list(costs)
                    s["player_state"]["potions"] = [dict(id=potion, slot=0)]
                    r = self.sequence(s, dict(action_type="POTION", potion_index=0), self.select(0))
                    made = r["projection"]["hand"][-1]
                    self.assertEqual(made["cost"], costs[made["id"]][int(master_reality)])
                    self.assertEqual((made["upgrades"], made["turn_cost"]), (int(master_reality), 0))

    def test_discovery_potion_x_cost_stays_x(self):
        s = self.state(role="IRONCLAD", hand=[self.card("Defend_R")])
        s["rng_state"]["card_rng_counter"] = 0
        s["_combat_card_pools"] = self.foreign()["_combat_card_pools"]
        s["_combat_card_pools"]["attacks"] = ["Whirlwind", "Skewer", "Strike_R"]
        s["player_state"]["potions"] = [dict(id="AttackPotion", slot=0)]
        seen = set()
        for i in range(3):
            made = self.sequence(s, dict(action_type="POTION", potion_index=0), self.select(i))["projection"]["hand"][-1]
            if made["id"] in ("Whirlwind", "Skewer"):
                seen.add(made["id"])
                self.assertEqual((made["cost"], made["turn_cost"]), (-1, -1))
        self.assertEqual(seen, {"Whirlwind", "Skewer"})

    def test_liquid_memories_discount_and_existing_cards(self):
        for bark, discard, selections in (
            (False, [("Vault", 3)], []),
            (False, [("Vault", 3), ("Eruption", 2)], [0]),
            (True, [("Vault", 3), ("Eruption", 2)], []),
            (True, [("Vault", 3), ("Eruption", 2), ("Whirlwind", -1)], [0, 0]),
        ):
            with self.subTest(bark=bark, discard=discard):
                s = self.state(role="WATCHER", hand=[self.card("Defend_P")], powers=[("MasterRealityPower", 1)])
                s["player_state"]["discard_pile"] = [self.card(name, cost=cost) for name, cost in discard]
                s["player_state"]["potions"] = [dict(id="LiquidMemories", slot=0)]
                if bark:
                    s["player_state"]["relics"] = [dict(relic_id="SacredBark", counter=-1)]
                r = self.sequence(s, dict(action_type="POTION", potion_index=0), *(self.select(i) for i in selections))
                added = r["projection"]["hand"][1:]
                expected = discard[:2 if bark else 1]
                self.assertEqual([(c["id"], c["upgrades"], c["cost"], c["turn_cost"]) for c in added],
                                 [(name, 0, cost, 0 if cost >= 0 else cost) for name, cost in expected])

    def test_liquid_memories_full_hand_leaves_discard_unchanged(self):
        s = self.state(role="WATCHER", hand=[self.card("Defend_P") for _ in range(10)])
        s["player_state"]["discard_pile"] = [self.card("Vault", cost=3)]
        s["player_state"]["potions"] = [dict(id="LiquidMemories", slot=0)]
        r = self.run_worker(s, action_type="POTION", potion_index=0)
        self.assertEqual(len(r["projection"]["hand"]), 10)
        c = r["projection"]["discard_pile"][0]
        self.assertEqual((c["id"], c["cost"], c["turn_cost"]), ("Vault", 3, 3))

    def test_escape_plan_skill_attack_and_empty_draw(self):
        for up in (False, True):
            for drawn in ("Deflect", "Slice", None):
                with self.subTest(up=up, drawn=drawn):
                    s = self.state("Escape Plan", up, draw=[self.card(drawn)] if drawn else [])
                    r = self.run_worker(s)
                    self.assertEqual(r["projection"]["block"], (5 if up else 3) if drawn == "Deflect" else 0)

    def test_escape_plan_no_draw_does_not_reuse_prior_draw_type(self):
        s = self.state("Escape Plan", draw=[self.card("Deflect")], powers=[("No Draw", 1)])
        self.assertEqual(self.run_worker(s)["projection"]["block"], 0)

    def test_nightmare_keeps_original_and_creates_three_next_turn(self):
        for up in (False, True):
            s = self.state(hand=[self.card("Night Terror", up), self.card("Catalyst", cost=1)])
            r = self.sequence(s, self.play(), dict(action_type="END"))
            self.assertEqual(sum(c["id"] == "Catalyst" for c in r["projection"]["hand"]), 4)
            self.assertNotIn("Night Terror", r["powers"])

    def test_nightmare_choice_and_stacking_preserves_first_target(self):
        s = self.state(hand=[self.card("Night Terror", cost=0), self.card("Night Terror", cost=0), self.card("Catalyst"), self.card("Deflect")])
        r = self.sequence(s, self.play(), self.select(1), self.play(), self.select(0), dict(action_type="END"))
        self.assertEqual(sum(c["id"] == "Catalyst" for c in r["projection"]["hand"]), 7)

    def test_nightmare_private_target_import(self):
        s = self.state(hand=[self.card("Deflect")])
        s["player_state"]["creature"]["powers"] = [dict(power_id="Night Terror", amount=3, card=self.card("Catalyst"))]
        r = self.sequence(s, dict(action_type="END"))
        self.assertEqual(sum(c["id"] == "Catalyst" for c in r["projection"]["hand"]), 3)

    def test_deva_grows_and_upgrade_only_removes_ethereal(self):
        for up in (False, True):
            s = self.state("DevaForm", up, role="WATCHER")
            r = self.sequence(s, self.play(), dict(action_type="END"), dict(action_type="END"))
            self.assertEqual(r["projection"]["energy"], 5)
            self.assertEqual(r["powers"]["DevaForm"], 1)

    def test_deva_private_energy_import(self):
        s = self.state(role="WATCHER")
        s["player_state"]["creature"]["powers"] = [dict(power_id="DevaForm", amount=2, energy_gain_amount=7)]
        r = self.sequence(s, dict(action_type="END"))
        self.assertEqual(r["projection"]["energy"], 10)
        del s["player_state"]["creature"]["powers"][0]["energy_gain_amount"]
        self.assertFalse(execute_worker(EXE, s, 1, 50, "import")["supported"])

    def test_omniscience_plays_twice_free_and_exhausts_once(self):
        for up in (False, True):
            s = self.state("Omniscience", up, role="WATCHER", draw=[self.card("Defend_P")])
            r = self.sequence(s, self.play(), self.select(0))
            self.assertEqual(r["projection"]["block"], 10)
            self.assertEqual(r["projection"]["energy"], 2)
            self.assertEqual([c["id"] for c in r["projection"]["exhaust_pile"]].count("Defend_P"), 1)

    def test_wish_all_options_and_upgrade(self):
        for up in (False, True):
            for option in range(3):
                with self.subTest(up=up, option=option):
                    r = self.sequence(self.state("Wish", up, role="WATCHER"), self.play(), self.select(option))
                    self.assertEqual(r["player_stats"]["strength"], (4 if up else 3) if option == 0 else 0)
                    self.assertEqual(r["powers"].get("Plated Armor", 0), (8 if up else 6) if option == 2 else 0)
                    self.assertEqual(r["player_stats"]["gold"], (30 if up else 25) if option == 1 else 0)

    def test_wish_is_mandatory(self):
        s = self.state("Wish", role="WATCHER")
        r = execute_worker(EXE, s, 100, 100, "sequence", metadata=dict(actions=[self.play(), self.select(65535)]))
        self.assertFalse(r["supported"])

    def test_lesson_learned_upgrades_master_not_combat_copy(self):
        for up in (False, True):
            s = self.state("LessonLearned", up, role="WATCHER", draw=[self.card("Defend_P")])
            s["player_state"]["master_deck"] = [self.card("Defend_P")]
            s["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"] = 10
            r = self.run_worker(s)
            self.assertEqual(r["master_deck"][0]["upgrades"], 1)
            self.assertEqual(r["projection"]["draw_pile"][0]["upgrades"], 0)

    def test_lesson_learned_nonlethal_and_minions_do_not_upgrade(self):
        for minion in (False, True):
            s = self.state("LessonLearned", role="WATCHER")
            s["player_state"]["master_deck"] = [self.card("Defend_P")]
            if minion:
                m = s["cur_map_node_state"]["monster_data"][0]["creature"]
                m.update(current_health=5, powers=[dict(power_id="Minion", amount=-1)])
            self.assertEqual(self.run_worker(s)["master_deck"][0]["upgrades"], 0)

    def test_lesson_learned_requires_real_master_deck(self):
        r = execute_worker(EXE, self.state("LessonLearned"), 1, 50, "step", metadata=dict(card_index=0))
        self.assertFalse(r["supported"])
        self.assertIn("missing_master_deck", r["reason"])

    def test_search_prefers_realized_lesson_upgrade_over_equal_lethal(self):
        import json
        s = self.state(role="WATCHER", hand=[self.card("Strike_P", cost=0), self.card("LessonLearned", cost=0)])
        s["player_state"]["master_deck"] = [self.card("Defend_P")]
        s["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"] = 5
        r = execute_worker(EXE, s, 300, 250)
        self.assertTrue(r["supported"], r)
        self.assertEqual(json.loads(r["commands"][0]["command"])["card_index"], 1)

    def foreign(self, up=False):
        s = self.state("ForeignInfluence", up, role="WATCHER")
        s["rng_state"]["card_rng_counter"] = 0
        pools = {k: ["Strike_R"] for k in ("any", "attacks", "skills", "powers", "colorless", "common", "uncommon", "rare")}
        pools.update(library_attacks_common=["Slice", "Cleave", "Cold Snap"],
                     library_attacks_uncommon=["Dash", "WheelKick", "Uppercut"],
                     library_attacks_rare=["Meteor Strike", "Glass Knife", "Ragnarok"])
        s["_combat_card_pools"] = pools
        return s

    def test_foreign_uses_independent_card_rng_and_unique_choices(self):
        s = self.foreign()
        a, b = self.run_worker(s), self.run_worker(s)
        self.assertEqual(a, b)
        self.assertEqual(len({c["id"] for c in a["selection"]["options"]}), 3)
        self.assertGreaterEqual(a["card_rng_counter"], 3)

    def test_foreign_upgrade_and_skip(self):
        for up in (False, True):
            s = self.foreign(up)
            r = self.sequence(s, self.play(), self.select(0))
            self.assertEqual(r["projection"]["hand"][0]["turn_cost"], 0 if up else r["projection"]["hand"][0]["cost"])
            skipped = self.sequence(s, self.play(), self.select(65535))
            self.assertEqual(skipped["projection"]["hand"], [])

    def test_foreign_missing_rng_or_pools_is_explicit(self):
        for field in ("card_rng_counter", "_combat_card_pools"):
            s = self.foreign()
            if field == "card_rng_counter":
                del s["rng_state"][field]
            else:
                del s[field]
            r = execute_worker(EXE, s, 1, 50, "step", metadata=dict(card_index=0))
            self.assertFalse(r["supported"])

    def typed(self, name, **data):
        return dict(state_class="savestate.actions."+name, state=data)

    def selection_state(self, chosen=(), task="DiscardActionState", hand=None, draw=None, amount=1):
        s = self.state(hand=hand or [self.card("Tactician", cost=-2), self.card("Strike_G")], draw=draw)
        p = s["player_state"]
        for pile in ("hand", "draw_pile", "discard_pile", "exhaust_pile"):
            for i, c in enumerate(p[pile]):
                c["uuid"] = pile+str(i)
        selected = [p["hand"][i] for i in chosen]
        p["hand"] = [c for i, c in enumerate(p["hand"]) if i not in chosen]
        played = self.card("Survivor", uuid="played")
        p["limbo"] = [played];p["card_in_use"] = played
        s.update(screen_name="HAND_SELECT", is_screen_up=True)
        s["hand_select_screen_state"] = dict(num_cards_to_select=amount, selected_cards=selected,
            num_selected=len(selected), were_cards_retrieved=False, can_pick_zero=False, up_to=False,
            any_number=False, is_disabled=len(selected) != amount,
            current_action_state=self.typed(task, amount=amount),
            action_queue=[self.typed("GainEnergyActionState", energyGain=2), self.typed("UseCardActionState", card=played, exhaustCard=False, targetIndex=-2)],
            card_queue_state=[])
        return s

    def test_fresh_worker_restores_discard_queue_and_emits_one_click(self):
        s = self.selection_state()
        r = self.run_worker(s, "search")
        import json
        cmd = json.loads(r["commands"][0]["command"])
        self.assertEqual(cmd, dict(type="HAND_SELECT", card_index=0))
        self.assertEqual(r["expected_after"]["energy"], 3)  # effects wait for the explicit UI confirmation
        self.assertEqual(r["expected_screen"], "HAND_SELECT")
        after = self.selection_state(chosen=(0,))
        confirmed = self.run_worker(after, "search")
        self.assertEqual(json.loads(confirmed["commands"][0]["command"]), dict(type="HAND_SELECT_CONFIRM"))
        self.assertEqual(confirmed["expected_after"]["energy"], 6)

    def test_partial_selection_restores_selected_cards_without_replay(self):
        s = self.selection_state(chosen=(1,), amount=2,
            hand=[self.card("Tactician", cost=-2), self.card("Deflect"), self.card("Strike_G")])
        r = self.run_worker(s, "import")
        self.assertEqual(r["projection"]["hand"][0]["id"], "Tactician")
        result = self.sequence(s, self.select(0), self.select(65535))
        self.assertEqual(result["projection"]["energy"], 6)
        self.assertEqual(len(result["projection"]["hand"]), 1)

    def test_unknown_continuation_rejected_before_command(self):
        s = self.selection_state()
        s["hand_select_screen_state"]["action_queue"].insert(0, self.typed("UnknownExecutableActionState"))
        r = execute_worker(EXE, s, 100, 50)
        self.assertFalse(r["supported"])
        self.assertEqual(r["reason"], "continuation_action:UnknownExecutableActionState")

    def test_no_unvalidated_selection_without_snapshot_is_admitted(self):
        s = self.state();s.update(screen_name="HAND_SELECT", is_screen_up=True)
        self.assertFalse(execute_worker(EXE, s, 1, 50, "import")["supported"])

    def test_fast_hand_confirmation_metadata_is_respected(self):
        s = self.selection_state()
        s["_combat_card_pools"] = self.foreign()["_combat_card_pools"]
        s["_combat_card_pools"]["fast_hand_confirm"] = True
        r = self.sequence(s, self.select(0))
        self.assertEqual(r["projection"]["energy"], 6)

    def test_grid_scry_zero_selection_restores_remaining_draw(self):
        s = self.selection_state()
        p = s["player_state"]
        p["draw_pile"] = [self.card("Defend_P", uuid="draw0"), self.card("Strike_P", uuid="draw1")]
        view = s.pop("hand_select_screen_state")
        s["screen_name"] = "GRID"
        view.update(num_cards=3, any_number=True, group_cards=p["draw_pile"],
                    current_action_state=self.typed("ScryActionState", amount=3))
        s["grid_card_select_screen_state"] = view
        r = self.sequence(s, self.select(65535))
        self.assertEqual(len(r["projection"]["draw_pile"]), 2)
        self.assertEqual(r["projection"]["energy"], 5)

    def test_wish_reward_snapshot_import_and_wire_index(self):
        import json
        s = self.selection_state()
        view = s.pop("hand_select_screen_state")
        s["screen_name"] = "CARD_REWARD"
        choices = [self.card(c, cost=-2, uuid="option"+str(i)) for i, c in enumerate(("BecomeAlmighty", "FameAndFortune", "LiveForever"))]
        view.update(reward_group=choices, skippable=False, current_action_state=self.typed("ChooseOneActionState", choices=choices))
        s["card_reward_screen_state"] = view
        result = self.sequence(s, self.select(1))
        self.assertEqual(result["player_stats"]["gold"], 25)
        wire = self.run_worker(s, "search")
        command = json.loads(wire["commands"][0]["command"])
        self.assertEqual(command["type"], "CARD_REWARD_SELECT")
        self.assertIn(command["card_index"], (0, 1, 2))

    def test_discovery_reward_snapshot_import_and_wire_index(self):
        import json
        s = self.selection_state()
        view = s.pop("hand_select_screen_state")
        s["screen_name"] = "CARD_REWARD"
        skills = [self.card(name, cost=cost, uuid="skill"+str(i))
                  for i, (name, cost) in enumerate((("Vault", 3), ("Omniscience", 4), ("Study", 2)))]
        view.update(reward_group=skills, skippable=False,
                    current_action_state=self.typed("DiscoveryActionState", retrieveCard=False,
                                                   returnColorless=False, cardType="SKILL", amount=1))
        s["card_reward_screen_state"] = view
        result = self.sequence(s, self.select(1))
        made = result["projection"]["hand"][-1]
        self.assertEqual((made["id"], made["upgrades"], made["turn_cost"]), ("Omniscience", 0, 0))
        wire = self.run_worker(s, "search")
        command = json.loads(wire["commands"][0]["command"])
        self.assertEqual(command["type"], "CARD_REWARD_SELECT")
        self.assertIn(command["card_index"], (0, 1, 2))
        # After the select the Discovery action is complete, so the worker can
        # now validate the post-selection boundary instead of refusing the
        # whole state as an unsupported selector.
        self.assertEqual(wire["expected_screen"], "NONE")
        self.assertIn("expected_after", wire)


    def test_missing_inflight_cleanup_is_rejected(self):
        s = self.selection_state()
        s["hand_select_screen_state"]["action_queue"] = [self.typed("GainEnergyActionState", energyGain=2)]
        r = execute_worker(EXE, s, 10, 100, "import")
        self.assertFalse(r["supported"])
        self.assertEqual(r["reason"], "continuation_card_in_flight")

    def test_restored_card_queue_expands_beyond_ten_and_survives_search_copy(self):
        s = self.selection_state(chosen=(0,))
        view = s["hand_select_screen_state"]
        view["card_queue_state"] = [dict(card=self.card("Defend_P", cost=1, uuid="queued"+str(i)),
            monster_index=None, energy_on_use=3, ignore_energy_total=False, autoplay_card=True,
            random_target=False, is_end_turn_auto_play=False) for i in range(12)]
        r = self.sequence(s, self.select(65535))
        self.assertEqual(r["projection"]["block"], 60)
        self.assertEqual(r["projection"]["energy"], 6)
        search = execute_worker(EXE, s, 100, 200)
        self.assertTrue(search["supported"], search)
        self.assertEqual(search["expected_after"]["block"], 60)

    def test_immediate_queue_continuation_inserts_after_first_and_deduplicates(self):
        s = self.selection_state(chosen=(0,))
        view = s["hand_select_screen_state"]
        calm = self.card("ClearTheMind", cost=0, uuid="calm")
        # Java inserts this new card after the first pending item. The second
        # identical NewQueue action must not enqueue the same card twice.
        action = self.typed("NewQueueCardActionState", cardState=calm, targetIndex=-2,
                            randomTarget=False, immediateCard=True, autoplayCard=True)
        view["action_queue"].extend([action, copy.deepcopy(action)])
        view["card_queue_state"] = [dict(card=self.card(card, cost=0, uuid=card),
            monster_index=0, energy_on_use=0, ignore_energy_total=False, autoplay_card=True,
            random_target=False, is_end_turn_auto_play=False) for card in ("Crescendo", "Strike_P")]
        before = s["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"]
        r = self.sequence(s, self.select(65535))
        self.assertEqual(before-r["projection"]["monsters"][0]["hp"], 6)
        self.assertEqual(sum(c["id"] == "ClearTheMind" for c in r["projection"]["exhaust_pile"]), 1)

    def test_all_150_cards_both_upgrades_execute_or_have_draw_triggers(self):
        """300 source-cost dispatch smoke cases, separate from interaction parity."""
        import re
        from battle_engine.tools.coverage import inventory, ROOT
        rows = inventory()["cards"]
        self.assertEqual(len(rows), 150)
        for row in rows:
            source = (ROOT / row["source"]).read_text(encoding="utf-8")
            cost = int(re.search(r'super\([^;]*?"(?:[^"\\]|\\.)*"\s*,\s*(-?\d+)\s*,', source, re.S).group(1))
            upgraded_cost = next((int(value) for op, value in row["upgrade_operations"] if op == "upgradeBaseCost"), cost)
            for up in (False, True):
                with self.subTest(card=row["id"], up=up):
                    s = self.foreign(up)
                    p = s["player_state"]
                    p["chosen_class_name"] = row["role"]
                    p["energy_panel_total_energy"] = 20
                    card = self.card(row["id"], up, cost=upgraded_cost if up else cost)
                    p["hand"] = [card, self.card("Defend_P")]
                    p["draw_pile"] = [self.card("Defend_P") for _ in range(3)]
                    p["master_deck"] = [self.card("Defend_P")]
                    s["cur_map_node_state"]["monster_data"][0]["creature"].update(current_health=50000, max_health=50000)
                    if row["id"] == "Grand Finale":
                        p["draw_pile"] = []
                    if cost == -2:
                        rejected = execute_worker(EXE, s, 1, 100, "step", metadata=dict(card_index=0))
                        self.assertFalse(rejected["supported"])
                        p["hand"] = [self.card("Backflip")]
                        p["draw_pile"] = [card]
                    result = self.run_worker(s)
                    self.assertIn("projection", result)


if __name__ == "__main__":
    unittest.main()
