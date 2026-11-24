"""Synthetic lifecycle fixtures; no engine, game actions, Agent, or collection."""
import copy
import unittest
from unittest.mock import Mock

from sts_ai_framework.headless.flow import FLOW_SCHEMA
from sts_ai_framework.headless.interaction import IroncladInteractionBridge, verify_effect
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.models import GameAction
from sts_ai_framework.policy_runtime import candidates_for
from sts_ai_framework.tests.test_headless_interaction import raw, selection, event
from sts_ai_framework.tests.test_headless_combat import gambler_raw
from sts_ai_framework.headless.combat import HeadlessCombatBridge, _gambler_evidence, MODELLED
from battle_engine.state import projection


def enrich(source, **facts):
    source["interaction"]["flow"] = {"schema": FLOW_SCHEMA,
        "dungeon": {1: "Exordium", 2: "TheCity", 3: "TheBeyond", 4: "TheEnding"}[source["act"]],
        "path": [], "flight": False, "final_act_available": True, "campfire_history": [],
        "bosses_remaining": 1, "player_dead": False, "victory": False,
        "result_screen": "NONE", "terminal": None, "route": "none", **facts}
    return source


def node(x, y, symbol="M", children=()):
    return {"x": x, "y": y, "symbol": symbol, "burning": False,
            "children": [{"x": a, "y": b} for a, b in children]}


def map_frame(first=True, act=1, charges=None, flight=False):
    s = enrich(raw("MAP", act=act, floor={1: 1, 2: 18, 3: 35, 4: 53}[act] if first else {1: 0, 2: 17, 3: 34, 4: 52}[act],
                   firstRoomChosen=first), flight=flight, path=[{"x": 1, "y": 0}] if first else [])
    s["map"] = {"current": {"x": 1 if first else 0, "y": 0 if first else -1}, "nodes": [
        node(1, 0, children=[(1, 1)]), node(3, 0, children=[(3, 1)]),
        node(1, 1, "$", [(1, 2)]), node(3, 1, "R", [(3, 2)]), node(1, 2), node(3, 2)]}
    if charges is not None: s["player"]["relics"] = [{"id": "WingedGreaves", "name": "display", "counter": charges}]
    candidates = [(1, 1, "$", False)] if first else [(1, 0, "M", False), (3, 0, "M", False)]
    if first and (flight or (charges or 0) > 0): candidates.append((3, 1, "R", True))
    s["interaction"]["map_choices"] = [{"x": x, "y": y, "symbol": symbol, "winged": w} for x, y, symbol, w in candidates]
    return s


def moved(source, x=1, y=1):
    s = copy.deepcopy(source); s.update(phase="PROCEED", floor=source["floor"] + 1, firstRoomChosen=True, room="ShopRoom")
    s["map"]["current"] = {"x": x, "y": y}
    s["interaction"]["flow"]["path"].append({"x": x, "y": y})
    s["interaction"].update(can_proceed=True)
    s["interaction"]["flow"]["route"] = "map"
    return s


def gambling_chip_outcome(source, *, map_target=None, second_boss=False):
    s=gambler_raw();s['combat']['snapshot']['hand_select_screen_state']['current_action_state']['state']['notchip']=False
    s['combat']['snapshot']['player_state']['relics'].append({'relic_id':'Gambling Chip','counter':-1,'_private':{'GamblingChip.activated':True}})
    s['player']['relics'].append({'id':'Gambling Chip','name':'chip','counter':-1})
    s.update(act=source['act'],floor=source['floor']+int(map_target is not None or second_boss),ascension=source['ascension'],
        map=copy.deepcopy(source['map']),firstRoomChosen=True,room='MonsterRoomBoss' if second_boss or map_target==(-1,15) else 'MonsterRoom')
    s['combat']['snapshot'].update(act_num=s['act'],floor_num=s['floor'],ascension_level=s['ascension'])
    enrich(s,**copy.deepcopy(source['interaction']['flow']))
    if map_target is not None:
        x,y=map_target;s['map']['current']={'x':x,'y':y}
        s['interaction']['flow']['path'].append({'x':x,'y':y})
    if second_boss:s['interaction']['flow']['bosses_remaining']=1
    return s


def handoff_fingerprint(snapshot):
    return {'supported':True,'rng_restore_mode':'exact_state','projection':projection(snapshot),'native_key':None,
        'selection':_gambler_evidence(snapshot),'selection_native_key':['full-native-import-state'],
        'exact_rng':{k:copy.deepcopy(snapshot['_headless_rng']['streams'][k]) for k in MODELLED}}


def camp(options=("RestOption", "SmithOption", "RecallOption"), **facts):
    s = enrich(raw("CAMPFIRE", room="RestRoom", roomPhase="INCOMPLETE"), **facts)
    s["interaction"]["campfire"] = {"selected": False, "options": [
        {"native_index": i, "class_name": name, "usable": True} for i, name in enumerate(options)]}
    return s


def camp_done(source, key="REST"):
    s = copy.deepcopy(source); s.update(phase="PROCEED", roomPhase="COMPLETE")
    s["interaction"].update(can_proceed=True)
    s["interaction"]["flow"].update(route="map")
    s["interaction"]["flow"]["campfire_history"].append({"floor": s["floor"], "key": key})
    return s


def boss(act=1, route=None, ascension=10, remaining=1, treasure=False):
    room = "TreasureRoomBoss" if treasure else "MonsterRoomBoss"
    route = route or ("next_act" if treasure else {1: "boss_chest", 2: "boss_chest", 3: "heart_event", 4: "true_victory"}[act])
    s = enrich(raw("PROCEED", act=act, floor={1: 17 if treasure else 16, 2: 34 if treasure else 33, 3: 51 if ascension == 20 else 50, 4: 56}[act],
                   ascension=ascension, room=room), route=route, bosses_remaining=remaining)
    s["map"] = {"current": {"x": -1, "y": 15}, "nodes": []}
    s["interaction"]["can_proceed"] = True
    return s


def next_act(source):
    act = source["act"] + 1
    s = map_frame(first=False, act=act)
    s["floor"] = source["floor"]; s["ascension"] = source["ascension"]
    s["player"] = copy.deepcopy(source["player"])
    s["observedKeys"] = copy.deepcopy(source["observedKeys"])
    p = s["player"]
    if not any(r["id"] == "Mark of the Bloom" for r in p["relics"]):
        p["hp"] = min(p["maxHp"], p["hp"] + (int((p["maxHp"] - p["hp"]) * .75 + .5) if s["ascension"] >= 5 else p["maxHp"]))
    if act == 4:  # TheEnding constructor keeps the old VictoryRoom until the first map selection.
        s["room"] = "VictoryRoom"; s["map"]["current"] = {"x": -1, "y": 15}
    return s


def heart(at="INTRO", keys=False):
    s = event(phase=at)
    s.update(act=3, floor=52, room="VictoryRoom", map={"current": {"x": -1, "y": 15}, "nodes": []})
    s["interaction"]["event"].update(id="Spire Heart", class_name="SpireHeart")
    s["observedKeys"] = {"ruby": keys, "emerald": keys, "sapphire": keys}
    return enrich(s)


def terminal(outcome="defeat", heart_win=False):
    act = 4 if heart_win else 3
    victory = outcome == "victory"
    room = "TrueVictoryRoom" if heart_win else "VictoryRoom" if victory else "MonsterRoomBoss"
    s = enrich(raw("RUN_COMPLETE" if victory else "GAME_OVER", act=act, floor=57 if heart_win else 52, room=room),
        player_dead=not heart_win, victory=victory, result_screen="VICTORY" if heart_win else "DEATH",
        terminal={"outcome": outcome, "heart": heart_win})
    s["map"] = {"current": {"x": -1, "y": 15}, "nodes": []}
    if not victory: s["player"]["hp"] = 0
    return s


class FlowCase(unittest.TestCase):
    def plan(self, source, *, index=None):
        self.bridge = IroncladInteractionBridge()
        self.before = self.bridge.observe(source, run_id="offline-flow", sequence=0)
        return self.bridge.prepare(self.before, GameAction(type="proceed") if index is None else GameAction(type="choose", choice_index=index))

    def effect(self, plan, source, sequence=1):
        return verify_effect(plan, self.bridge.observe(source, run_id="offline-flow", sequence=sequence)).status


class NativeOutcomeHandoffTest(unittest.TestCase):
    def frames(self):
        before=map_frame(first=False);after=gambling_chip_outcome(before,map_target=(1,0))
        return before,after

    def test_map_outcome_is_read_only_and_original_flow_checks_still_apply(self):
        source,raw_after=self.frames();bridge=IroncladInteractionBridge();combat=HeadlessCombatBridge()
        before=bridge.observe(source,run_id='handoff',sequence=0);plan=bridge.prepare(before,GameAction(type='choose',choice_index=0))
        with self.assertRaisesRegex(HeadlessContractError,'native combat continuation'):bridge.observe(raw_after,run_id='handoff',sequence=1)
        callback=Mock(side_effect=handoff_fingerprint)
        after=bridge.observe_outcome(raw_after,run_id='handoff',sequence=1,combat=combat,import_snapshot=callback)
        self.assertEqual(after.actions,());self.assertFalse(after.state.can_proceed);self.assertTrue(after.native_combat_handoff)
        self.assertEqual(callback.call_args.args[0],raw_after['combat']['snapshot'])
        self.assertEqual(verify_effect(plan,after).status,'APPLIED')
        with self.assertRaises(HeadlessContractError):bridge.prepare(after,GameAction(type='choose',choice_index=0))
        # Subsequent decisions still use the native selection bridge, not menus.
        self.assertTrue(combat.observe(raw_after,run_id='handoff',sequence=1).legal_json)
        wrong=copy.deepcopy(raw_after);wrong['interaction']['flow']['path']=[]
        after=bridge.observe_outcome(wrong,run_id='handoff',sequence=2,combat=combat,import_snapshot=handoff_fingerprint)
        self.assertEqual(verify_effect(plan,after).status,'UNVERIFIED')

    def test_handoff_requires_actual_import_fullguard_rng_uuid_and_source_controls(self):
        source,after=self.frames();bridge=IroncladInteractionBridge();combat=HeadlessCombatBridge()
        for mutate in (lambda p:p.pop('selection_native_key'),lambda p:p.update(selection_native_key=None),
                lambda p:p.update(selection_native_key=[]),lambda p:p.update(supported=False),
                lambda p:p.update(rng_restore_mode='legacy_counter'),lambda p:p['exact_rng']['headless'].update(counter=99),
                lambda p:p['selection']['remaining'].reverse(),lambda p:p['projection'].update(hp=1)):
            proof=handoff_fingerprint(after['combat']['snapshot']);mutate(proof)
            with self.assertRaises(HeadlessContractError):bridge.observe_outcome(after,run_id='handoff',sequence=1,combat=combat,import_snapshot=lambda s:proof)
        for mutate in (
                lambda r:r['interaction'].pop('schema'),
                lambda r:r['combat']['snapshot']['hand_select_screen_state']['current_action_state']['state'].update(notchip=True),
                lambda r:r['combat']['snapshot']['player_state']['relics'][-1]['_private'].update({'GamblingChip.activated':False}),
                lambda r:r['combat']['snapshot']['hand_select_screen_state'].update(can_pick_zero=False),
                lambda r:r['combat']['legal_actions'][0].update(uuid='00000000-0000-0000-0000-000000000999'),
                lambda r:r['combat']['snapshot']['hand_select_screen_state']['action_queue'].append({'state_class':'savestate.actions.UnknownActionState','state':{}}),
                lambda r:r['combat']['snapshot']['hand_select_screen_state']['current_action_state'].update(state_class='savestate.actions.UnknownActionState')):
            bad=copy.deepcopy(after);mutate(bad);callback=Mock(side_effect=handoff_fingerprint)
            with self.assertRaises(HeadlessContractError):bridge.observe_outcome(bad,run_id='handoff',sequence=1,combat=combat,import_snapshot=callback)
            callback.assert_not_called()

    def test_event_start_combat_and_second_boss_use_the_same_validated_handoff(self):
        for second_boss in (False,True):
            source=boss(act=3,route='second_boss',ascension=20,remaining=2) if second_boss else enrich(event([{'type':'start_combat'}],followup='COMBAT'))
            if source.get('map') is None:
                source['floor']=1;source['map']={'current':{'x':1,'y':0},'nodes':[node(1,0,children=[(1,1)]),node(1,1)]}
            after=gambling_chip_outcome(source,second_boss=second_boss)
            source['player']=copy.deepcopy(after['player'])
            bridge=IroncladInteractionBridge();before=bridge.observe(source,run_id='handoff',sequence=0)
            plan=bridge.prepare(before,GameAction(type='proceed') if second_boss else GameAction(type='choose',choice_index=0))
            observed=bridge.observe_outcome(after,run_id='handoff',sequence=1,combat=HeadlessCombatBridge(),import_snapshot=handoff_fingerprint)
            self.assertEqual(verify_effect(plan,observed).status,'APPLIED')

    def test_ordinary_menu_outcome_keeps_existing_route_without_native_request(self):
        source=map_frame();bridge=IroncladInteractionBridge();callback=Mock()
        before=bridge.observe(source,run_id='handoff',sequence=0);plan=bridge.prepare(before,GameAction(type='choose',choice_index=0))
        after=bridge.observe_outcome(moved(source),run_id='handoff',sequence=1,combat=HeadlessCombatBridge(),import_snapshot=callback)
        self.assertFalse(after.native_combat_handoff);self.assertTrue(after.actions);callback.assert_not_called()
        self.assertEqual(verify_effect(plan,after).status,'APPLIED')


class MapFlowTest(FlowCase):
    def test_first_row_and_existing_policy_alignment(self):
        s = map_frame(first=False)
        plan = self.plan(s, index=1)
        self.assertEqual(plan.action.native_args, (3, 0))
        self.assertEqual(len(candidates_for(self.before.state)), 2)
        self.assertEqual(self.effect(plan, moved(s, 3, 0)), "APPLIED")

    def test_normal_edges_and_nonadjacent_winged_choices(self):
        for charges in (0, 1, 2, -2):
            with self.subTest(charges=charges):
                s = map_frame(charges=charges)
                plan = self.plan(s, index=0)
                self.assertEqual(len(self.before.actions), 2 if charges > 0 else 1)
                self.assertEqual(self.effect(plan, moved(s)), "APPLIED")

    def test_winged_counter_debit_and_no_physical_edge_pollution(self):
        for charges, expected in ((1, -2), (2, 1)):
            s = map_frame(charges=charges); plan = self.plan(s, index=1)
            self.assertEqual(len(self.before.state.map_nodes[0].children), 1)
            after = moved(s, 3, 1)
            self.assertEqual(self.effect(plan, after), "UNVERIFIED")
            after["player"]["relics"][0]["counter"] = expected
            self.assertEqual(self.effect(plan, after, 2), "APPLIED")

    def test_illegal_incomplete_and_mislabelled_map_choices(self):
        for mutation in (lambda s:s["interaction"]["map_choices"].clear(),
                         lambda s:s["interaction"]["map_choices"][0].update(winged=True),
                         lambda s:s["interaction"]["map_choices"][0].update(x=3),
                         lambda s:s["interaction"]["map_choices"].append(copy.deepcopy(s["interaction"]["map_choices"][0]))):
            s = map_frame(); mutation(s)
            with self.assertRaises(HeadlessContractError): self.plan(s, index=0)

    def test_ack_only_wrong_floor_target_or_path_never_apply(self):
        s = map_frame(); plan = self.plan(s, index=0)
        self.assertEqual(self.effect(plan, s), "PENDING")
        after = moved(s); after["interaction"]["flow"]["path"] = []
        self.assertEqual(self.effect(plan, after, 2), "UNVERIFIED")
        self.assertFalse(self.bridge.validate_before(plan, self.bridge.observe(after, run_id="offline-flow", sequence=3)))

    def test_final_row_boss_sentinel(self):
        s = enrich(raw("MAP", floor=15), path=[{"x": 3, "y": 14}])
        s["map"] = {"current": {"x": 3, "y": 14}, "nodes": [node(3, 14, "R", [(-1, 15)])]}
        s["interaction"]["map_choices"] = [{"x": -1, "y": 15, "symbol": "B", "winged": False}]
        plan = self.plan(s, index=0); after = moved(s, -1, 15)
        after.update(phase="PLAYER_TURN", room="MonsterRoomBoss", roomPhase="COMBAT")
        self.assertEqual(self.effect(plan, after), "APPLIED")

    def test_act4_boss_leaf_is_reachable_but_not_a_new_boss_exit(self):
        s = map_frame(act=4); s["floor"] = 55
        s["map"] = {"current": {"x": 3, "y": 2}, "nodes": [node(3, 2, "E", [(3, 3)]), node(3, 3, "B")]}
        s["interaction"]["map_choices"] = [{"x": 3, "y": 3, "symbol": "B", "winged": False}]
        plan = self.plan(s, index=0); self.assertEqual(plan.action.native_args, (3, 3))
        after = moved(s, 3, 3); after.update(room="MonsterRoomBoss", roomPhase="COMBAT", phase="PLAYER_TURN")
        self.assertEqual(self.effect(plan, after), "APPLIED")

    def test_absent_flow_keeps_legacy_projection_actions_closed(self):
        s = map_frame(); del s["interaction"]["flow"]
        frame = IroncladInteractionBridge().observe(s, run_id="legacy", sequence=0)
        self.assertEqual(frame.actions, ())

    def test_flight_uses_native_greaves_debit_when_relic_is_present(self):
        for charges in (None, 0, 2):
            s = map_frame(charges=charges, flight=True); plan = self.plan(s, index=1)
            after = moved(s, 3, 1)
            if charges is not None: after["player"]["relics"][0]["counter"] = -2 if charges <= 1 else charges - 1
            self.assertEqual(self.effect(plan, after), "APPLIED")

    def test_flow_booleans_and_act_identity_are_strict(self):
        for field, value in (("flight", "false"), ("victory", 0), ("dungeon", "TheCity")):
            s = map_frame(); s["interaction"]["flow"][field] = value
            with self.assertRaises(HeadlessContractError): self.plan(s, index=0)


class CampfireFlowTest(FlowCase):
    def test_native_disabled_hole_and_policy_names(self):
        s = camp(); s["interaction"]["campfire"]["options"][0]["usable"] = False
        plan = self.plan(s, index=0)
        self.assertEqual(plan.action.native_args, (1,))
        self.assertEqual(self.before.state.choice_list, ["SmithOption", "RecallOption"])
        self.assertEqual(len(candidates_for(self.before.state)), 2)

    def test_relic_locks_upgrade_cap_and_already_recalled(self):
        s = camp(); s["observedKeys"] = {"ruby": True}
        s["player"]["relics"] = [{"id": n, "name": n, "counter": -1} for n in ("Coffee Dripper", "Fusion Hammer")]
        self.assertEqual(IroncladInteractionBridge().observe(s, run_id="locks", sequence=0).actions, ())

    def test_selected_and_unknown_options_do_not_submit(self):
        s = camp(("UnknownOption",)); self.assertEqual(IroncladInteractionBridge().observe(s, run_id="x", sequence=0).actions, ())
        s = camp(); s["interaction"]["campfire"]["selected"] = True
        self.assertEqual(IroncladInteractionBridge().observe(s, run_id="x", sequence=0).actions, ())

    def test_rest_requires_hp_and_new_floor_specific_receipt(self):
        s = camp(); plan = self.plan(s, index=0); after = camp_done(s)
        self.assertEqual(self.effect(plan, after), "UNVERIFIED")
        after["player"]["hp"] += 24
        self.assertEqual(self.effect(plan, after, 2), "APPLIED")
        after["interaction"]["flow"]["campfire_history"][-1]["floor"] -= 1
        self.assertEqual(self.effect(plan, after, 3), "UNVERIFIED")

    def test_full_hp_and_mark_bloom_still_need_completion_receipt(self):
        for full in (False, True):
            s = camp(); s["player"]["hp"] = 80 if full else 55
            s["player"]["relics"] = [{"id": "Mark of the Bloom", "name": "bloom", "counter": -1}]
            plan = self.plan(s, index=0)
            self.assertEqual(self.effect(plan, camp_done(s)), "APPLIED")

    def test_regal_pillow_and_dream_catcher_followup(self):
        s = camp(); s["player"]["relics"] = [{"id": n, "name": n, "counter": -1} for n in ("Regal Pillow", "Dream Catcher")]
        plan = self.plan(s, index=0); after = camp_done(s); after["player"]["hp"] = 80
        self.assertEqual(self.effect(plan, after), "UNVERIFIED")
        after.update(phase="CARD_PICK", cardRewardOptions=copy.deepcopy(s["player"]["masterDeck"]))
        after["interaction"]["card_reward"] = {"skip": True, "bowl": False}
        self.assertEqual(self.effect(plan, after, 2), "APPLIED")

    def test_smith_and_toke_opening_is_separate_from_commit(self):
        for option, purpose in (("SmithOption", "upgrade"), ("TokeOption", "purge")):
            s = camp((option,))
            if purpose == "purge": s["player"]["relics"] = [{"id": "Peace Pipe", "name": "pipe", "counter": -1}]
            plan = self.plan(s, index=0)
            after = enrich(selection(purpose)); after.update(room="RestRoom", roomPhase="INCOMPLETE")
            after["player"] = copy.deepcopy(s["player"])
            self.assertEqual(self.effect(plan, after), "APPLIED")
            after["interaction"]["selection"]["purpose"] = "duplicate"
            self.assertEqual(self.effect(plan, after, 2), "UNVERIFIED")

    def test_lift_requires_girya_increment_and_receipt(self):
        s = camp(("LiftOption",)); s["player"]["relics"] = [{"id": "Girya", "name": "g", "counter": 2}]
        plan = self.plan(s, index=0); after = camp_done(s, "LIFT")
        self.assertEqual(self.effect(plan, after), "UNVERIFIED")
        after["player"]["relics"][0]["counter"] = 3
        self.assertEqual(self.effect(plan, after, 2), "APPLIED")
        after["phase"] = "CAMPFIRE"; after["roomPhase"] = "INCOMPLETE"
        self.assertEqual(IroncladInteractionBridge().observe(after, run_id="capped", sequence=0).actions, ())

    def test_recall_requires_observed_ruby_not_just_receipt(self):
        s = camp(("RecallOption",)); plan = self.plan(s, index=0); after = camp_done(s, "RECALL")
        self.assertEqual(self.effect(plan, after), "UNVERIFIED")
        after["observedKeys"] = {"ruby": True}
        self.assertEqual(self.effect(plan, after, 2), "APPLIED")

    def test_dig_requires_relic_reward_and_new_receipt(self):
        s = camp(("DigOption",)); s["player"]["relics"] = [{"id": "Shovel", "name": "s", "counter": -1}]
        plan = self.plan(s, index=0); after = camp_done(s, "DIG"); after["phase"] = "REWARD"
        after["interaction"]["rewards"] = [{"native_index": 0, "type": "RELIC", "relic_id": "Anchor"}]
        self.assertEqual(self.effect(plan, after), "APPLIED")
        after["interaction"]["rewards"] = []
        self.assertEqual(self.effect(plan, after, 2), "UNVERIFIED")


class ActFlowTest(FlowCase):
    def test_forged_proceed_route_is_rejected(self):
        s = boss(); s["interaction"]["flow"]["route"] = "second_boss"
        with self.assertRaises(HeadlessContractError): self.plan(s)

    def test_unsupported_next_act_does_not_advertise_proceed(self):
        s = boss(act=3, treasure=True)
        frame = IroncladInteractionBridge().observe(s, run_id="unsupported", sequence=0)
        self.assertEqual(frame.actions, ()); self.assertFalse(frame.state.can_proceed)

    def test_event_combat_reward_returns_to_event_not_map(self):
        s = enrich(raw("REWARD", room="EventRoom"), route="event_resume", event_class="BigFish")
        s["interaction"].update(rewards=[], can_proceed=True)
        plan = self.plan(s); after = enrich(event(phase="RESULT")); after["room"] = "EventRoom"
        self.assertEqual(self.effect(plan, after), "APPLIED")
        after = map_frame(); after["floor"] = s["floor"]; after["map"]["current"] = {"x": 1, "y": 2}; after["interaction"]["map_choices"] = []
        self.assertEqual(self.effect(plan, after, 2), "UNVERIFIED")

    def test_regular_room_proceed_opens_map_without_floor_increment(self):
        s = moved(map_frame()); plan = self.plan(s); after = copy.deepcopy(s); after["phase"] = "MAP"
        after["interaction"]["map_choices"] = [{"x": 1, "y": 2, "symbol": "M", "winged": False}]
        self.assertEqual(self.effect(plan, after), "APPLIED")

    def test_boss_reward_to_chest_is_not_next_act(self):
        s = boss(); plan = self.plan(s); after = copy.deepcopy(s)
        after.update(phase="BOSS_CHEST", room="TreasureRoomBoss", floor=17)
        after["interaction"]["chest"] = {"boss": True, "open": False, "gold_reward": False, "gold_amount": 0, "relic_tier": "boss"}
        self.assertEqual(self.effect(plan, after), "APPLIED")
        self.assertEqual(self.effect(plan, next_act(boss(treasure=True)), 2), "UNVERIFIED")

    def test_act1_act2_transitions_heal_keys_and_reset_path(self):
        for act in (1, 2):
            s = boss(act=act, treasure=True); s["observedKeys"] = {"ruby": True}
            plan = self.plan(s); after = next_act(s)
            self.assertEqual(self.effect(plan, after), "APPLIED")
            after["interaction"]["flow"]["path"] = [{"x": 1, "y": 0}]
            self.assertEqual(self.effect(plan, after, 2), "UNVERIFIED")

    def test_heal_rounding_a4_and_bloom(self):
        for ascension, bloom in ((4, False), (5, False), (20, True)):
            s = boss(treasure=True, ascension=ascension)
            if bloom: s["player"]["relics"] = [{"id": "Mark of the Bloom", "name": "b", "counter": -1}]
            plan = self.plan(s); after = next_act(s)
            self.assertEqual(self.effect(plan, after), "APPLIED")
            after["player"]["hp"] -= 1
            self.assertEqual(self.effect(plan, after, 2), "UNVERIFIED")

    def test_a20_second_boss_stays_act3_and_requires_count(self):
        s = boss(act=3, ascension=20, remaining=2, route="second_boss")
        plan = self.plan(s); after = copy.deepcopy(s); after.update(phase="PLAYER_TURN", floor=52, roomPhase="COMBAT")
        after["interaction"]["flow"]["bosses_remaining"] = 1
        self.assertEqual(self.effect(plan, after), "APPLIED")
        after["interaction"]["flow"]["bosses_remaining"] = 2
        self.assertEqual(self.effect(plan, after, 2), "UNVERIFIED")

    def test_act3_boss_routes_to_heart_dialog(self):
        s = boss(act=3); plan = self.plan(s); after = heart(); after["floor"] = 51
        self.assertEqual(self.effect(plan, after), "APPLIED")

    def test_heart_control_sequence_and_key_branch(self):
        for at, target in (("INTRO", "MIDDLE"), ("MIDDLE", "MIDDLE_2"), ("MIDDLE_2", "DEATH")):
            s = heart(at); plan = self.plan(s, index=0)
            self.assertEqual(self.effect(plan, heart(target)), "APPLIED")
            self.assertEqual(self.effect(plan, heart(at), 2), "PENDING")
        s = heart("MIDDLE_2", True); plan = self.plan(s, index=0)
        self.assertEqual(self.effect(plan, heart("GO_TO_ENDING", True)), "APPLIED")

    def test_final_act_requires_all_keys_and_fresh_map(self):
        s = heart("GO_TO_ENDING", True); plan = self.plan(s, index=0)
        after = next_act(s)
        self.assertEqual(self.effect(plan, after), "APPLIED")
        s = heart("GO_TO_ENDING", False); plan = self.plan(s, index=0)
        self.assertEqual(self.effect(plan, next_act(s)), "UNVERIFIED")

    def test_act3_completion_is_victory_not_combat_death(self):
        s = heart("DEATH"); plan = self.plan(s, index=0)
        self.assertEqual(self.effect(plan, terminal("victory")), "APPLIED")
        self.assertEqual(self.effect(plan, terminal("defeat"), 2), "UNVERIFIED")

    def test_heart_win_transition_requires_full_terminal_evidence(self):
        s = boss(act=4); plan = self.plan(s)
        self.assertEqual(self.effect(plan, terminal("victory", True)), "APPLIED")
        t = terminal("victory", True); t["interaction"]["flow"]["terminal"] = None
        self.assertEqual(self.effect(plan, t, 2), "UNVERIFIED")


class TerminalFlowTest(FlowCase):
    def test_terminal_local_receipt_idempotent_and_never_restarts(self):
        for outcome, heart_win in (("defeat", False), ("victory", False), ("victory", True)):
            s = terminal(outcome, heart_win); plan = self.plan(s)
            receipt = self.bridge.acknowledge_terminal(plan, self.before)
            self.assertEqual(receipt, self.bridge.acknowledge_terminal(plan, self.before))
            self.assertEqual((receipt.outcome, receipt.heart), (outcome, heart_win))
            self.assertEqual(self.before.state.game_over_reason, outcome)
            self.assertEqual(plan.action.native_method, "LOCAL_ACK_TERMINAL")
            self.assertEqual(verify_effect(plan, self.before).status, "LOCAL_ONLY")

    def test_intermediate_run_complete_without_result_has_no_legal_ack(self):
        s = terminal(); s["phase"] = "RUN_COMPLETE"; s["interaction"]["flow"].update(terminal=None, result_screen="NONE", player_dead=False)
        frame = IroncladInteractionBridge().observe(s, run_id="partial", sequence=0)
        self.assertEqual(frame.actions, ()); self.assertIsNone(frame.state.game_over_reason)

    def test_contradictory_terminal_is_rejected(self):
        for mutation in (lambda f:f.update(victory=True), lambda f:f.update(player_dead=False), lambda f:f["terminal"].update(heart=True)):
            s = terminal(); mutation(s["interaction"]["flow"])
            with self.assertRaises(HeadlessContractError): self.plan(s)

    def test_wrong_run_or_stale_terminal_ack(self):
        s = terminal(); plan = self.plan(s)
        foreign = self.bridge.observe(s, run_id="other-run", sequence=0)
        with self.assertRaises(HeadlessContractError): self.bridge.acknowledge_terminal(plan, foreign)

    def test_changed_terminal_result_after_ack_is_rejected(self):
        s = terminal(); plan = self.plan(s); self.bridge.acknowledge_terminal(plan, self.before)
        s["player"]["gold"] += 1
        frame = self.bridge.observe(s, run_id="offline-flow", sequence=1)
        new_plan = self.bridge.prepare(frame, GameAction(type="proceed"))
        with self.assertRaises(HeadlessContractError): self.bridge.acknowledge_terminal(new_plan, frame)


if __name__ == "__main__": unittest.main()
