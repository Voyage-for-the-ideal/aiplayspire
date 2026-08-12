"""run_events.py 测试: JSONL 事件、状态指纹、候选语义、pending 三态、楼层摘要、终局分类。"""

import json
import os
import tempfile
import unittest

from sts_ai_framework.models import (ActionType, Card, GameAction, GameState,
                                     MonsterState, PlayerState, PotionState)
from sts_ai_framework.run_events import (BattleTracker, FloorTracker, PendingTracker,
                                         RunEvents, RunSession, classify_run_end,
                                         extract_candidates, state_diff, state_fingerprint)


def make_card(index=0, uuid="u0", card_id="Strike_R", name="打击", upgrades=0):
    return Card(index=index, uuid=uuid, id=card_id, name=name, cost=1,
                cost_for_turn=1, upgrades=upgrades)


def make_monster(index=0, name="狡猾的哥布林", mid="GremlinNob", hp=82, intent="ATTACK"):
    return MonsterState(index=index, name=name, id=mid, current_hp=hp, max_hp=82,
                        block=0, intent=intent)


def make_state(floor=1, hp=70, max_hp=80, gold=99, screen="NONE", phase="EVENT", hand=None,
               monsters=None, deck=None, choice_list=None, **kw):
    return GameState(
        player=PlayerState(current_hp=hp, max_hp=max_hp, block=0, energy=3, gold=gold),
        hand=hand if hand is not None else [],
        monsters=monsters if monsters is not None else [],
        floor=floor,
        act=1,
        room_phase=phase,
        screen_type=screen,
        draw_pile_size=0,
        discard_pile_size=0,
        exhaust_pile_size=0,
        deck=deck if deck is not None else [],
        choice_list=choice_list,
        **kw,
    )


def read_events(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


class RunEventsTest(unittest.TestCase):
    def test_write_jsonl_lines(self):
        with tempfile.TemporaryDirectory() as tmp:
            events = RunEvents(tmp, "run_test", pid=1)
            events.emit("decision", decision_id="f1-rest-1", floor=1)
            events.close()
            with open(os.path.join(tmp, "run_test.jsonl"), encoding="utf-8") as f:
                lines = f.read().splitlines()
            self.assertEqual(len(lines), 2)  # run_start + decision
            for line in lines:
                rec = json.loads(line)
                self.assertEqual(rec["schema"], "sts-ai-run/v2")
                self.assertEqual(rec["run_id"], "run_test")
            self.assertEqual(json.loads(lines[0])["event"], "run_start")
            self.assertEqual(json.loads(lines[1])["event"], "decision")

    def test_run_end_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            events = RunEvents(tmp, "run_x")
            events.run_end_once(status="died")
            events.run_end_once(status="died")
            events.close()
            ends = [r for r in read_events(os.path.join(tmp, "run_x.jsonl"))
                    if r["event"] == "run_end"]
            self.assertEqual(len(ends), 1)


class FingerprintTest(unittest.TestCase):
    def test_same_state_same_hash(self):
        s1 = make_state()
        s2 = make_state()
        self.assertEqual(state_fingerprint(s1), state_fingerprint(s2))

    def test_none_state(self):
        self.assertIsNone(state_fingerprint(None))

    def test_hp_change_differs(self):
        a = make_state(hp=70)
        b = make_state(hp=50)
        self.assertNotEqual(state_fingerprint(a), state_fingerprint(b))

    def test_intent_change_differs(self):
        a = make_state(monsters=[make_monster()])
        b = make_state(monsters=[make_monster(intent="DEFEND")])
        self.assertNotEqual(state_fingerprint(a), state_fingerprint(b))

    def test_hand_uuid_change_differs(self):
        a = make_state(hand=[make_card(uuid="u1")])
        b = make_state(hand=[make_card(uuid="u2")])
        self.assertNotEqual(state_fingerprint(a), state_fingerprint(b))

    def test_ansi_normalized(self):
        a = make_state(screen="EVENT", choice_list=["\x1b[31m红色选项\x1b[0m"])
        b = make_state(screen="EVENT", choice_list=["红色选项"])
        self.assertEqual(state_fingerprint(a), state_fingerprint(b))

    def test_combat_entry_changes_fingerprint(self):
        a = make_state(screen="NONE", phase="COMBAT", monsters=[make_monster()])
        b = make_state(screen="NONE", phase="COMBAT", monsters=[make_monster(hp=50)])
        self.assertNotEqual(state_fingerprint(a), state_fingerprint(b))

    def test_grid_preview_change_differs(self):
        a = make_state(screen="GRID", grid_confirm_up=False)
        b = make_state(screen="GRID", grid_confirm_up=True)
        self.assertNotEqual(state_fingerprint(a), state_fingerprint(b))


class StateDiffTest(unittest.TestCase):
    def test_deck_added(self):
        pre = make_state(deck=[make_card(uuid="u1")])
        post = make_state(deck=[make_card(uuid="u1"), make_card(uuid="u2", card_id="Inflame", name="燃烧")])
        d = state_diff(pre, post)
        self.assertEqual(d["deck_added"], ["Inflame"])
        self.assertEqual(d["deck_removed"], [])

    def test_upgrade(self):
        pre = make_state(deck=[make_card(uuid="u1", upgrades=0)])
        post = make_state(deck=[make_card(uuid="u1", upgrades=1)])
        d = state_diff(pre, post)
        self.assertEqual(d["upgraded"], ["u1"])

    def test_transform_one_in_one_out(self):
        pre = make_state(deck=[make_card(uuid="u1", card_id="Strike_R")])
        post = make_state(deck=[make_card(uuid="u2", card_id="Shrug_It_Off", name="耸肩")])
        d = state_diff(pre, post)
        self.assertEqual(d["deck_added"], ["Shrug_It_Off"])
        self.assertEqual(d["deck_removed"], ["Strike_R"])

    def test_gold_and_relic(self):
        pre = make_state(gold=99)
        post = make_state(gold=88)
        self.assertEqual(state_diff(pre, post)["gold"], -11)


class ExtractCandidatesTest(unittest.TestCase):
    def test_event_semantics(self):
        from sts_ai_framework.models import EventChoiceState, EventOutcome, EventState
        from sts_ai_framework.models import EventEffect
        outcome = EventOutcome(probability=1.0, effects=[EventEffect(type="gain_gold", amount=50)])
        choice = EventChoiceState(button_index=1, action_index=1, enabled=True,
                                  label="拿走金币", kind="GOLD", outcomes=[outcome])
        state = make_state(screen="EVENT", phase="EVENT",
                           event=EventState(id="event_x", class_name="X", phase="WAIT",
                                            semantics_status="KNOWN", decision_kind="DETERMINISTIC",
                                            choices=[choice]))
        kind, candidates, sem = extract_candidates(state)
        self.assertEqual(kind, "event")
        self.assertEqual(len(candidates), 1)
        self.assertEqual(candidates[0]["stable_id"], 1)
        self.assertEqual(candidates[0]["name"], "拿走金币")
        self.assertEqual(candidates[0]["kind"], "GOLD")
        self.assertEqual(candidates[0]["semantics"]["outcomes"][0]["effects"][0]["type"], "gain_gold")
        self.assertEqual(sem["event_id"], "event_x")

    def test_map_semantics(self):
        from sts_ai_framework.models import MapChoiceState, CurrentMapNodeState
        state = make_state(
            screen="MAP", phase="MAP",
            current_map_choices=[
                MapChoiceState(choice_index=0, x=4, y=10, symbol="M", human_label="普通战", lane_index_from_left=1),
                MapChoiceState(choice_index=1, x=4, y=12, symbol="R", human_label="篝火", lane_index_from_left=2),
            ],
            current_map_node=CurrentMapNodeState(x=3, y=10, symbol="M", human_label="普通战"),
        )
        kind, candidates, sem = extract_candidates(state)
        self.assertEqual(kind, "map")
        self.assertEqual(candidates[0]["stable_id"], "4_10")
        self.assertEqual(candidates[0]["semantics"]["room_type"], "monster")
        self.assertEqual(candidates[1]["semantics"]["room_type"], "rest")
        self.assertEqual(sem["room_type"], "monster")

    def test_card_reward(self):
        state = make_state(
            screen="CARD_REWARD", phase="COMPLETE",
            deck=[make_card(card_id="Inflame", name="燃烧")],
            reward_card_ids=["Inflame", "Shrug_It_Off"],
            choice_list=["Inflame", "Shrug_It_Off", "Skip"],
        )
        kind, candidates, _ = extract_candidates(state)
        self.assertEqual(kind, "card_reward")
        self.assertEqual(candidates[0]["stable_id"], "Inflame")
        self.assertEqual(candidates[0]["name"], "燃烧")
        self.assertEqual(candidates[2]["name"], "Skip")

    def test_rest_fallback(self):
        state = make_state(screen="REST", choice_list=["RestOption", "SmithOption"])
        kind, candidates, _ = extract_candidates(state)
        self.assertEqual(kind, "rest")
        self.assertEqual(candidates[0]["name"], "RestOption")


class PendingTrackerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.events = RunEvents(self.tmp.name, "run_pending")
        self.tracker = PendingTracker(self.events, deadline_s=20.0)

    def tearDown(self):
        self.events.close()
        self.tmp.cleanup()

    def _submit_and_pending(self, state, action):
        did, _, _ = self.tracker.register(state, action, "llm", 100.0)
        self.tracker.on_submit(did, True, {"status": "queued"}, None, 5.0, 100.2)
        return did

    def test_immediate_confirm(self):
        state = make_state(screen="REST", choice_list=["a", "b"])
        did, _, _ = self.tracker.register(state, GameAction(type=ActionType.CHOOSE, choice_index=1),
                                          "value_network", 100.0)
        self.tracker.on_submit(did, True, {"status": "queued"}, None, 5.0, 100.2)
        # 即时读取到变化状态 -> confirmed/immediate
        changed = make_state(screen="GRID", choice_list=["confirm"])
        status = self.tracker.confirm_immediate(did, changed, 200.0, 101.0)
        self.assertEqual(status, "confirmed")
        decisions = [r for r in read_events(self.events.path) if r["event"] == "decision"]
        self.assertEqual(len(decisions), 1)
        self.assertEqual(decisions[0]["status"], "confirmed")
        self.assertEqual(decisions[0]["confirm_method"], "immediate")
        self.assertEqual(decisions[0]["decision_id"], did)

    def test_pending_then_poll_confirm(self):
        state = make_state(screen="REST", choice_list=["a", "b"])
        did = self._submit_and_pending(state, GameAction(type=ActionType.CHOOSE, choice_index=1))
        # 即时无变化 -> pending
        self.assertEqual(self.tracker.confirm_immediate(did, state, 200.0, 101.0), "pending")
        # 同态轮询: 无新 outcome
        self.tracker.check(state, 110.0)
        decisions = [r for r in read_events(self.events.path) if r["event"] == "decision"]
        self.assertEqual(len(decisions), 0)
        # 状态变化 -> confirmed/poll
        changed = make_state(screen="GRID", choice_list=["confirm"], hp=70)
        self.tracker.check(changed, 112.0)
        decisions = [r for r in read_events(self.events.path) if r["event"] == "decision"]
        self.assertEqual(len(decisions), 1)
        self.assertEqual(decisions[0]["status"], "confirmed")
        self.assertEqual(decisions[0]["confirm_method"], "poll")
        self.assertEqual(self.tracker.pending_count(), 0)

    def test_unrelated_hp_change_does_not_confirm_rest(self):
        state = make_state(screen="REST", choice_list=["RestOption", "SmithOption"])
        did = self._submit_and_pending(state, GameAction(type=ActionType.CHOOSE, choice_index=1))
        changed_hp = make_state(screen="REST", hp=60,
                                choice_list=["RestOption", "SmithOption"])
        self.tracker.check(changed_hp, 110.0)
        self.assertEqual(1, self.tracker.pending_count())
        self.assertFalse(any(r["event"] == "decision" for r in read_events(self.events.path)))

    def test_duplicate_register_reuses_pending_decision(self):
        state = make_state(screen="GRID", choice_list=["confirm"],
                           grid_selected_count=1, grid_num_cards=1)
        action = GameAction(type=ActionType.CHOOSE, choice_index=0)
        first, _, _ = self.tracker.register(state, action, "heuristic", 100.0)
        second, _, _ = self.tracker.register(state, action, "heuristic", 101.0)
        self.assertEqual(first, second)
        self.assertEqual(1, self.tracker._seq)
        self.assertEqual(1, self.tracker.pending_count())

    def test_grid_card_click_effective_when_preview_shows(self):
        # 打铁网格: 点卡后原版进入预览模式并清空 selectedCards,
        # 因此 grid_selected_count 不变; grid_confirm_up 变 true 才是生效信号
        grid_card = {"choice_index": 1, "uuid": "u1", "id": "Clothesline",
                     "name": "Clothesline", "upgrades": 0, "can_upgrade": True}
        state = make_state(screen="GRID", choice_list=["confirm", "clothesline"],
                           grid_cards=[grid_card], grid_purpose="upgrade",
                           grid_num_cards=1, grid_selected_count=0,
                           grid_confirm_up=False)
        did = self._submit_and_pending(state, GameAction(type=ActionType.CHOOSE, choice_index=1))
        self.assertEqual(self.tracker.confirm_immediate(did, state, 200.0, 101.0), "pending")
        preview = make_state(screen="GRID", choice_list=["confirm", "clothesline"],
                             grid_cards=[grid_card], grid_purpose="upgrade",
                             grid_num_cards=1, grid_selected_count=0,
                             grid_confirm_up=True)
        self.tracker.check(preview, 112.0)
        decisions = [r for r in read_events(self.events.path) if r["event"] == "decision"]
        self.assertEqual(len(decisions), 1)
        self.assertEqual(decisions[0]["status"], "confirmed")
        self.assertEqual(decisions[0]["confirm_method"], "poll")

    def test_grid_confirm_click_effective_when_screen_closes(self):
        grid_card = {"choice_index": 1, "uuid": "u1", "id": "Clothesline",
                     "name": "Clothesline", "upgrades": 0, "can_upgrade": True}
        state = make_state(screen="GRID", choice_list=["confirm", "clothesline"],
                           grid_cards=[grid_card], grid_purpose="upgrade",
                           grid_num_cards=1, grid_selected_count=0,
                           grid_confirm_up=True)
        did = self._submit_and_pending(state, GameAction(type=ActionType.CHOOSE, choice_index=0))
        # 预览模式下点击 confirm: 屏幕尚未离开 GRID 时不生效
        self.tracker.check(state, 110.0)
        self.assertEqual(1, self.tracker.pending_count())
        closed = make_state(screen="REST", choice_list=[])
        self.tracker.check(closed, 112.0)
        decisions = [r for r in read_events(self.events.path) if r["event"] == "decision"]
        self.assertEqual(len(decisions), 1)
        self.assertEqual(decisions[0]["status"], "confirmed")

    def test_deadline_timeout(self):
        state = make_state(screen="REST", choice_list=["a"])
        did = self._submit_and_pending(state, GameAction(type=ActionType.CHOOSE, choice_index=0))
        self.tracker.check(state, 121.0)  # 超过 20s deadline
        decisions = [r for r in read_events(self.events.path) if r["event"] == "decision"]
        self.assertEqual(len(decisions), 1)
        self.assertEqual(decisions[0]["status"], "rejected_timeout")
        self.assertEqual(decisions[0]["decision_id"], did)

    def test_flush_interrupted(self):
        state = make_state(screen="REST", choice_list=["a"])
        did, _, _ = self.tracker.register(state, GameAction(type=ActionType.CHOOSE, choice_index=0),
                                          "heuristic", 100.0)
        self.tracker.on_submit(did, True, {"status": "queued"}, None, 5.0, 100.2)
        self.tracker.flush("interrupted", 130.0)
        decisions = [r for r in read_events(self.events.path) if r["event"] == "decision"]
        self.assertEqual(len(decisions), 1)
        self.assertEqual(decisions[0]["status"], "interrupted")

    def test_submit_failed(self):
        state = make_state(screen="REST", choice_list=["a"])
        did, _, _ = self.tracker.register(state, GameAction(type=ActionType.CHOOSE, choice_index=0),
                                          "llm", 100.0)
        self.tracker.on_submit(did, False, None, "http_500: boom", 5.0, 100.2)
        decisions = [r for r in read_events(self.events.path) if r["event"] == "decision"]
        self.assertEqual(len(decisions), 1)
        self.assertEqual(decisions[0]["status"], "submit_failed")
        self.assertEqual(self.tracker.pending_count(), 0)

    def test_decision_event_fields(self):
        state = make_state(screen="REST", floor=32, choice_list=["RestOption", "SmithOption"])
        did, kind, chosen = self.tracker.register(
            state, GameAction(type=ActionType.CHOOSE, choice_index=1),
            "value_network", 100.0,
            meta={"source": "value_network", "score": 0.81},
        )
        self.assertEqual(did, "f32-rest-1")
        self.assertEqual(kind, "rest")
        self.assertEqual(chosen["index"], 1)
        self.assertEqual(chosen["name"], "SmithOption")
        self.tracker.on_submit(did, False, None, "test", 1.0, 101.0)
        dec = [r for r in read_events(self.events.path) if r["event"] == "decision"][0]
        self.assertEqual(dec["source"], "value_network")
        self.assertEqual(dec["meta"]["score"], 0.81)
        self.assertEqual(dec["pre"]["hp"], 70)


class FloorTrackerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.events = RunEvents(self.tmp.name, "run_floor")
        self.floors = FloorTracker(self.events)

    def tearDown(self):
        self.events.close()
        self.tmp.cleanup()

    def test_enter_exit_rows(self):
        self.floors.update(make_state(floor=6), 1.0)
        self.floors.update(make_state(floor=7, hp=40), 5.0)
        rows = self.floors.finalize(8.0)
        self.assertEqual(rows[0]["floor"], 6)
        self.assertEqual(rows[0]["hp"], "70→40")  # 进层 70, 离层 40
        self.assertEqual(rows[1]["floor"], 7)
        events = [r["event"] for r in read_events(self.events.path)]
        self.assertIn("floor_entry", events)
        self.assertIn("floor_exit", events)

    def test_boss_floor_inferred(self):
        state = make_state(floor=16, screen="NONE", phase="COMBAT", monsters=[make_monster()])
        self.floors.update(state, 1.0)
        self.assertEqual(self.floors._infer_room(state), "Boss")

    def test_delta_aggregation(self):
        self.floors.update(make_state(floor=6), 1.0)
        self.floors.observe_delta({"deck_added": ["Inflame"], "gold": 25,
                                   "relics_added": ["Vajra"], "hp": -12})
        self.floors.update(make_state(floor=7), 5.0)
        rows = self.floors.finalize(6.0)
        rewards = rows[0]["rewards"]  # _exit 已 join 为字符串
        self.assertIn("+Inflame", rewards)
        self.assertIn("金币+25", rewards)
        self.assertIn("遗物:Vajra", rewards)

    def test_observe_decision_and_battle(self):
        self.floors.update(make_state(floor=6), 1.0)
        self.floors.observe_decision("rest", {"action": "choose", "index": 1, "name": "SmithOption"})
        self.floors.observe_battle("b1", "victory", ["哥布林"])
        self.floors.observe_battle("b2", "victory", ["哥布林", "地精大头"])
        rows = self.floors.finalize(5.0)
        self.assertIn("SmithOption", rows[0]["decisions"])
        self.assertIn("哥布林", rows[0]["encounter"])


class ClassifyRunEndTest(unittest.TestCase):
    def test_died_in_combat(self):
        state = make_state(screen="NONE", phase="COMBAT", hp=21, monsters=[make_monster(hp=82)])
        status, conf, _ = classify_run_end(state, {"TIMEOUT": 10, "MOD_BUSY": 1}, None)
        self.assertEqual(status, "died")
        self.assertEqual(conf, "high")

    def test_connection_lost(self):
        state = make_state()
        status, conf, _ = classify_run_end(state, {"CONNECTION_ERROR": 10}, None)
        self.assertEqual(status, "connection_lost")
        self.assertEqual(conf, "high")

    def test_noncombat_silent_low_confidence(self):
        state = make_state(screen="EVENT")
        status, conf, _ = classify_run_end(state, {"TIMEOUT": 10}, None)
        self.assertEqual(status, "connection_lost")
        self.assertEqual(conf, "low")

    def test_boss_victory_game_finished(self):
        state = make_state(floor=33, screen="BOSS_REWARD")
        status, conf, _ = classify_run_end(state, {}, "victory")
        self.assertEqual(status, "game_finished")
        self.assertEqual(conf, "medium")

    def test_killed(self):
        status, conf, _ = classify_run_end(None, {}, None, killed=True)
        self.assertEqual(status, "killed")


class FakeClient:
    """脚本化 GameClient: 依次吐出预设状态, 耗尽后返回 TIMEOUT。"""

    def __init__(self, states, submit_resp=None):
        self.states = list(states)
        self.submitted = []
        self.submit_resp = submit_resp or (True, {"status": "queued"}, "submitted")

    def get_state_detailed(self):
        if self.states:
            return self.states.pop(0), "OK", ""
        return None, "TIMEOUT", "eof"

    def get_state(self):
        return self.get_state_detailed()[0]

    def submit_action(self, action):
        self.submitted.append(action)
        return self.submit_resp


class RunSessionIntegrationTest(unittest.TestCase):
    def test_event_sequence(self):
        combat1 = make_state(floor=6, hp=70, screen="NONE", phase="COMBAT",
                             monsters=[make_monster()])
        combat2 = make_state(floor=6, hp=52, screen="NONE", phase="COMBAT",
                             monsters=[make_monster(hp=0)])  # 战斗中, 敌人死亡但战斗未结束
        tmp = tempfile.TemporaryDirectory()
        events = RunEvents(tmp.name, "run_int")
        session = RunSession(events, deadline_s=20.0, heartbeat_s=30.0)

        t = 1.0
        # 主循环驱动: 可操作态 -> 决策
        s0 = make_state(floor=6, hp=70, screen="REST", choice_list=["RestOption", "SmithOption"])
        session.on_state_ok(s0, t)
        did = session.record_decision(s0, GameAction(type=ActionType.CHOOSE, choice_index=1),
                                      "value_network", t, {"score": 0.8})
        session.tracker.on_submit(did, True, {"status": "queued"}, None, 5.0, t + 0.2)
        self.assertEqual(session.tracker.confirm_immediate(did, s0, 5.0, t + 0.3), "pending")

        # 战斗序列: 进入战斗 -> 状态变化 -> 战斗中静默 (Mod 停止响应 = 死亡)
        for state in [combat1, combat2]:
            t += 2.0
            session.on_state_ok(state, t)

        # 静默超时 -> 结束
        for _ in range(11):
            t += 1.0
            session.on_fetch_fail("TIMEOUT", "eof", t)
        status, conf, _ = classify_run_end(session.last_state, session.err_counts,
                                           session.battles.last_battle_result)
        summary = session.finish(status, conf, "测试", t)
        events.close()

        records = read_events(events.path)
        kinds = [r["event"] for r in records]
        # 事件类型齐全且顺序合理
        self.assertIn("run_start", kinds)
        self.assertIn("decision", kinds)
        self.assertIn("battle_start", kinds)
        self.assertIn("battle_end", kinds)  # finish 兜底 end_forced
        self.assertEqual(kinds.count("decision"), 1)  # 每动作恰 1 条 decision
        self.assertNotIn("heartbeat", kinds)
        self.assertNotIn("battle_state_change", kinds)
        self.assertEqual(kinds[-2], "run_end")
        self.assertEqual(kinds[-1], "run_summary")
        # 终局分类: 战斗中静默 -> died
        self.assertEqual(records[-2]["status"], "died")
        # 摘要 markdown 含表头
        self.assertIn("|层|房间|", summary)
        tmp.cleanup()


if __name__ == "__main__":
    unittest.main()
