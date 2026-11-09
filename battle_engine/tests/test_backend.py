import copy
import contextlib
import io
import json
from pathlib import Path
import socket
import socketserver
import struct
import subprocess
import tempfile
import threading
import types
import unittest
import os
from unittest.mock import patch

from battle_engine import protocol, server
from battle_engine.state import context, load, normalize, projection

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/ironclad.json"
WORKER = Path(os.environ.get("BATTLE_ENGINE_WORKER", str(ROOT / "bin/battle-engine-worker.exe")))


class ProtocolTests(unittest.TestCase):
    def test_roundtrip(self):
        for value in ("", "DONE", "中文路径", "a\0b", "😀", "\ud800", "\udfff", "x" * 65535):
            with self.subTest(value=value[:20]):
                self.assertEqual(protocol.decode(protocol.encode(value)), value)

    def test_java_encoding_bytes(self):
        self.assertEqual(protocol.encode("\0😀"), bytes.fromhex("c080eda0bdedb880"))

    def test_limit_is_encoded_bytes(self):
        with self.assertRaises(ValueError):
            protocol.encode("中" * 21846)
        self.assertEqual(len(protocol.encode("中" * 21845)), 65535)

    def test_malformed(self):
        for data in (b"\0", b"\xf0\x9f\x98\x80", b"\xe0", b"\xc1\x81", b"\xe0\x80\x80"):
            with self.subTest(data=data), self.assertRaises(ValueError):
                protocol.decode(data)

    def test_fragmented_frame(self):
        class Fragmented:
            data = bytearray(b"\0\x04DONE")
            def recv(self, n):
                result = bytes(self.data[:1]); del self.data[:1]; return result
        self.assertEqual(protocol.receive(Fragmented()), "DONE")

    def test_truncated_frame(self):
        a, b = socket.socketpair()
        with a, b:
            a.sendall(b"\0\x04DO"); a.shutdown(socket.SHUT_WR)
            with self.assertRaises(EOFError):
                protocol.receive(b)


class StateTests(unittest.TestCase):
    def test_oracle_screen_mismatch_fails_even_with_same_v1_projection(self):
        from battle_engine.tools import acceptance
        state = load(FIXTURE)
        view = projection(state)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "after.json"
            state.update(screen_name="HAND_SELECT", is_screen_up=True)
            path.write_text(json.dumps(state), encoding="utf-8")
            actual = dict(projection=view)
            self.assertFalse(acceptance.transition_matches(dict(expected_after=view, expected_screen="NONE"), actual, path))
            self.assertTrue(acceptance.transition_matches(dict(expected_after=view, expected_screen="HAND_SELECT"), actual, path))
            self.assertTrue(acceptance.transition_matches(dict(expected_after=view), actual, None))

    def test_offline_unvalidated_boundary_is_not_native_acceptance(self):
        from battle_engine.tools import acceptance
        imported = dict(supported=True, projection=projection(load(FIXTURE)))
        response = dict(type="COMMAND_LIST", supported=True, should_replan=True,
                        commands=[dict(command='{"type":"END"}', expected_before=imported["projection"])], metrics={})
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "report.json"
            with patch("sys.argv", ["acceptance", "--output", str(report)]), \
                 patch.object(acceptance, "execute_worker", side_effect=[imported, response]):
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(acceptance.main(), 1)
            result = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(result["passed"], 0)
            self.assertEqual(result["rows"][0]["reason"], "unvalidated_boundary")

    def test_unsupported_or_missing_pool_is_not_native_acceptance(self):
        from battle_engine.tools import acceptance
        cases = [[dict(supported=False, reason="character:UNKNOWN")],
                 [dict(supported=True, projection=projection(load(FIXTURE))),
                  dict(supported=False, reason="runtime_card_pool_missing:skills")]]
        for replies in cases:
            with self.subTest(replies=replies), tempfile.TemporaryDirectory() as tmp:
                report = Path(tmp) / "report.json"
                with patch("sys.argv", ["acceptance", "--output", str(report)]), \
                     patch.object(acceptance, "execute_worker", side_effect=replies):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(acceptance.main(), 1)
                result = json.loads(report.read_text(encoding="utf-8"))
                self.assertEqual(result["passed"], 0)
                self.assertEqual(result["native_search"], 0)

    def test_legacy_nested_strings(self):
        value = {"player_state": json.dumps({"hand": json.dumps([])})}
        self.assertEqual(normalize(value), {"player_state": {"hand": []}})

    def test_cost_and_slot_order_are_preserved(self):
        state = load(FIXTURE)
        state["player_state"]["hand"][0]["cost_for_turn"] = 0
        p = projection(state)
        self.assertEqual(p["hand"][0]["turn_cost"], 0)
        self.assertEqual(p["potions"], ["Fire Potion", "Potion Slot", "Potion Slot"])

    def test_oracle_exception_never_counts_as_pass(self):
        from battle_engine.tools import acceptance
        imported = dict(supported=True, projection=projection(load(FIXTURE)))
        response = dict(type="COMMAND_LIST", supported=True, should_replan=True,
                        commands=[dict(command='{"type":"END"}', expected_before=imported["projection"])],
                        metrics={}, expected_after=imported["projection"])
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "report.json"
            with patch("sys.argv", ["acceptance", "--oracle-port", "1", "--output", str(report)]), \
                 patch.object(acceptance, "execute_worker", side_effect=[imported, response]), \
                 patch.object(acceptance, "card_pools", return_value={}), \
                 patch.object(acceptance, "replay", side_effect=TimeoutError("test timeout")):
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(acceptance.main(), 1)
            self.assertEqual(json.loads(report.read_text(encoding="utf-8"))["passed"], 0)


class OptionsTests(unittest.TestCase):
    def options(self, *args):
        # Argument parsing tests do not require a compiled worker.
        with patch.object(Path, "is_file", return_value=True):
            return server.parse_options(list(args))

    def test_default_is_cpp_only(self):
        self.assertTrue(self.options().no_fallback)

    def test_cpp_only_ignores_unused_fallback_port(self):
        self.assertTrue(self.options("--port", "5126").no_fallback)

    def test_java_fallback_requires_explicit_opt_in(self):
        self.assertFalse(self.options("--enable-java-fallback").no_fallback)
        self.assertTrue(self.options("--no-fallback").no_fallback)

    def test_java_only_requires_opt_in(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            self.options("--java-only")
        self.assertFalse(self.options("--java-only", "--enable-java-fallback").no_fallback)

    def test_conflicting_modes_are_rejected(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            self.options("--no-fallback", "--enable-java-fallback")


class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.state = load(FIXTURE)
        self.path = Path(self.tmp.name) / "start.txt"
        self.path.write_text(json.dumps(self.state), encoding="utf-8")
        options = types.SimpleNamespace(worker=WORKER, fallback_port=1, no_fallback=True, max_time_ms=50)
        self.gateway = server.Gateway(("127.0.0.1", 0), options)
        self.thread = threading.Thread(target=self.gateway.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.gateway.shutdown(); self.gateway.server_close(); self.thread.join()
        self.tmp.cleanup()

    def connect(self):
        sock = socket.create_connection(self.gateway.server_address, timeout=5)
        sock.settimeout(5)
        return sock

    def request(self, sock, **extra):
        protocol.send(sock, json.dumps(dict(fileName=str(self.path), timeout_ms=50, **extra)))
        replies = []
        while True:
            reply = protocol.receive(sock)
            if reply == "DONE":
                return replies
            replies.append(json.loads(reply))

    def response(self):
        return dict(type="COMMAND_LIST", supported=True, commands=[dict(command='{"type":"END"}',
                    expected_before=projection(self.state), expected_context=context(self.state))], should_replan=True,
                    expected_after=projection(self.state), battle_complete=False)

    def test_ping_without_done(self):
        with self.connect() as sock:
            for _ in range(2):
                protocol.send(sock, '{"type":"PING"}')
                self.assertEqual(json.loads(protocol.receive(sock))["type"], "PONG")

    def test_ping_is_not_blocked_by_search_lock(self):
        with self.gateway.search_lock, self.connect() as sock:
            protocol.send(sock, '{"type":"PING"}')
            self.assertEqual(json.loads(protocol.receive(sock))["type"], "PONG")

    def test_success_and_second_request_on_same_connection(self):
        with patch.object(server, "execute_worker", return_value=self.response()) as run, self.connect() as sock:
            for _ in range(2):
                self.assertEqual(self.request(sock)[0]["type"], "COMMAND_LIST")
            self.assertEqual(run.call_count, 1)  # Idempotent retry.

    def test_screen_prediction_is_checked_even_when_v1_projection_matches(self):
        response = self.response()
        response["expected_screen"] = "HAND_SELECT"
        with patch.object(server, "execute_worker", return_value=response) as run, self.connect() as sock:
            self.assertEqual(self.request(sock)[0]["type"], "COMMAND_LIST")
            self.state.update(screen_name="GRID", is_screen_up=True)
            self.path.write_text(json.dumps(self.state), encoding="utf-8")
            self.assertIn("prediction_mismatch", self.request(sock)[0]["message"])
            self.assertEqual(run.call_count, 1)

    def test_errors_always_end(self):
        with patch.object(server, "execute_worker", return_value=dict(supported=False, reason="character")), self.connect() as sock:
            self.assertEqual(self.request(sock)[0]["type"], "ERROR")
            self.assertEqual(self.request(sock)[0]["type"], "ERROR")

    def test_timeout_isolated(self):
        with patch.object(server, "execute_worker", side_effect=subprocess.TimeoutExpired("worker", 1)), self.connect() as sock:
            self.assertEqual(self.request(sock)[0]["type"], "ERROR")

    def test_cpp_only_never_connects_to_java(self):
        # Connect to the gateway before mocking the function used for Java forwarding.
        with self.connect() as sock, patch.object(server.socket, "create_connection") as connect_java, \
             patch.object(server, "execute_worker", return_value=dict(supported=False, reason="selection_screen")):
            response = self.request(sock)[0]
            self.assertEqual(response["type"], "ERROR")
            self.assertIn("mode=cpp-only", response["message"])
            connect_java.assert_not_called()

    def test_cpp_only_error_is_recorded_as_native_error(self):
        path = Path(self.tmp.name) / "requests.jsonl"
        self.gateway.options.telemetry = path
        with patch.object(server, "execute_worker", return_value=dict(supported=False, reason="selection_screen")), \
             self.connect() as sock:
            for _ in range(2):
                self.assertEqual(self.request(sock)[0]["type"], "ERROR")
        rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
        self.assertEqual(len(rows), 2)
        self.assertTrue(all(row["backend"] == "cpp_error" and row["reason"] == "selection_screen" for row in rows))

    def test_missing_fallback_service_has_done_and_diagnostic(self):
        self.gateway.options.no_fallback = False
        with patch.object(server, "execute_worker", return_value=dict(supported=False, reason="character")), self.connect() as sock:
            response = self.request(sock)[0]
            self.assertEqual(response["type"], "ERROR")
            self.assertIn("-Dbattleai.port=1", response["message"])

    def test_unvalidated_boundary_stops_next_request_in_cpp_only_mode(self):
        response = self.response(); del response["expected_after"]
        with patch.object(server, "execute_worker", return_value=response) as run, self.connect() as sock:
            self.assertEqual(self.request(sock)[0]["type"], "COMMAND_LIST")
            self.assertIn("unvalidated_boundary", self.request(sock)[0]["message"])
            self.assertEqual(run.call_count, 1)

    def test_cached_command_is_not_reused_after_power_context_changes(self):
        with patch.object(server, "execute_worker", side_effect=lambda *args: self.response()) as run, self.connect() as sock:
            self.request(sock)
            self.state["player_state"]["creature"]["powers"] = [dict(power_id="Strength", amount=2)]
            self.path.write_text(json.dumps(self.state), encoding="utf-8")
            self.assertEqual(self.request(sock)[0]["type"], "COMMAND_LIST")
            self.assertEqual(run.call_count, 2)

    def test_malformed_request_has_done(self):
        with self.connect() as sock:
            protocol.send(sock, "{")
            self.assertEqual(json.loads(protocol.receive(sock))["type"], "ERROR")
            self.assertEqual(protocol.receive(sock), "DONE")

    def test_big_response_uses_command_file(self):
        response = self.response()
        response["commands"][0]["command"] = json.dumps(dict(type="CARD", card_index=0, monster_index=0, display_string="x" * 70000))
        path = Path(self.tmp.name) / "commands.json"
        with patch.object(server, "execute_worker", return_value=response), self.connect() as sock:
            reply = self.request(sock, command_file=str(path))[0]
        self.assertEqual(Path(reply["command_path"]), path)
        self.assertEqual(json.loads(path.read_text(encoding="utf-8")),
                         {k: v for k, v in response.items() if k != "expected_after"})

    def test_prediction_mismatch_quarantines_battle(self):
        response = self.response()
        response["expected_after"] = copy.deepcopy(projection(self.state))
        response["expected_after"]["hp"] -= 5
        with patch.object(server, "execute_worker", return_value=response) as run, self.connect() as sock:
            self.request(sock)
            self.state["player_state"]["creature"]["current_health"] -= 1
            self.path.write_text(json.dumps(self.state), encoding="utf-8")
            self.assertEqual(self.request(sock)[0]["type"], "ERROR")
            self.assertEqual(self.request(sock)[0]["type"], "ERROR")
            self.assertEqual(run.call_count, 1)

    def test_fallback_relays_all_frames(self):
        observed = []
        class Legacy(socketserver.BaseRequestHandler):
            def handle(self):
                observed.append(protocol.receive(self.request))
                protocol.send(self.request, '{"type":"STATUS_UPDATE"}')
                protocol.send(self.request, '{"type":"COMMAND_LIST","commands":[]}')
                protocol.send(self.request, "DONE")
        with socketserver.TCPServer(("127.0.0.1", 0), Legacy) as fallback:
            thread = threading.Thread(target=fallback.serve_forever, daemon=True); thread.start()
            self.gateway.options.no_fallback = False
            self.gateway.options.fallback_port = fallback.server_address[1]
            try:
                with patch.object(server, "execute_worker", return_value=dict(supported=False, reason="character")), self.connect() as sock:
                    replies = self.request(sock)
                self.assertEqual([r["type"] for r in replies], ["STATUS_UPDATE", "COMMAND_LIST"])
                self.assertEqual(json.loads(observed[0])["fileName"], str(self.path))
            finally:
                fallback.shutdown(); thread.join()


@unittest.skipUnless(WORKER.is_file(), "Build the native worker with battle_engine/build.ps1")
class NativeTests(unittest.TestCase):
    def run_worker(self, state=None, operation="import"):
        return server.execute_worker(WORKER, state or load(FIXTURE), 100, 50, operation)

    def slimes_state(self, dead_index):
        state = load(FIXTURE)
        state.update(floor_num=4, turn=2, cards_played_this_turn=[])
        p = state["player_state"]
        p["creature"]["powers"] = [dict(power_id="Frail", amount=1)]
        p["potions"] = [dict(id="Potion Slot", slot=-1)] * 3
        alive = dict(creature=dict(id="SpikeSlime_M", current_health=22, max_health=28,
                                   current_block=0, is_dead=False, is_escaping=False,
                                   half_dead=False, powers=[]),
                     escaped=False, next_move=4, intent_name="DEBUFF", move_history=["4"])
        dead = dict(creature=dict(id="AcidSlime_S", current_health=0, max_health=9,
                                  current_block=0, is_dead=True, is_dying=True,
                                  is_escaping=False, half_dead=False, powers=[]),
                    escaped=False, next_move=1, intent_name="ATTACK", move_history=["1"])
        monsters = [alive]
        monsters.insert(dead_index, dead)
        state["cur_map_node_state"]["monster_data"] = monsters
        return state

    def test_import_matches_projection(self):
        result = self.run_worker()
        self.assertTrue(result["supported"], result)
        self.assertEqual(result["projection"], projection(load(FIXTURE)))

    def test_dead_slime_import_and_search_preserve_slot_order(self):
        for dead_index in (0, 1):
            with self.subTest(dead_index=dead_index):
                state = self.slimes_state(dead_index)
                before = projection(state)
                imported = self.run_worker(state)
                self.assertTrue(imported["supported"], imported)
                self.assertEqual(imported["projection"], before)
                result = self.run_worker(state, "search")
                self.assertTrue(result["supported"], result)
                server.validate_response(result)
                self.assertEqual(result["commands"][0]["expected_before"], before)
                self.assertEqual([m["id"] for m in result["expected_after"]["monsters"]],
                                 [m["id"] for m in before["monsters"]])
                self.assertEqual(result["expected_after"]["monsters"][dead_index],
                                 before["monsters"][dead_index])

    def test_frail_with_dead_slime_reduces_defend_to_three_block(self):
        for dead_index in (0, 1):
            with self.subTest(dead_index=dead_index):
                state = self.slimes_state(dead_index)
                result = server.execute_worker(WORKER, state, 100, 50, "step",
                                               metadata=dict(card_index=1))
                self.assertTrue(result["supported"], result)
                self.assertEqual(result["projection"]["block"], 3)
                self.assertEqual(result["powers"]["Frail"], 1)
                self.assertEqual(result["projection"]["monsters"], projection(state)["monsters"])

    def test_dead_and_escaped_monsters_do_not_take_turns(self):
        for inactive in ("dead", "escaped", "is_escaping"):
            with self.subTest(inactive=inactive):
                state = load(FIXTURE)
                p = state["player_state"]
                p["energy_panel_total_energy"] = 0
                p["potions"] = [dict(id="Potion Slot", slot=-1)] * 3
                monsters = state["cur_map_node_state"]["monster_data"]
                gone = copy.deepcopy(monsters[0])
                gone.update(next_move=1, intent_name="ATTACK", move_history=["1"])
                if inactive == "dead":
                    gone["creature"].update(current_health=0, is_dead=True)
                elif inactive == "escaped":
                    gone["escaped"] = True  # HP stays positive and is_escaping stays false.
                else:
                    gone["creature"]["is_escaping"] = True
                monsters.insert(0, gone)
                result = self.run_worker(state, "search")
                self.assertTrue(result["supported"], result)
                self.assertEqual(json.loads(result["commands"][0]["command"])["type"], "END")
                self.assertEqual(result["expected_after"]["turn"], 2)
                self.assertEqual(result["expected_after"]["hp"], p["creature"]["current_health"])
                self.assertEqual(result["expected_after"]["monsters"][0], projection(state)["monsters"][0])

    def test_half_dead_monster_still_revives(self):
        state = load(FIXTURE)
        p = state["player_state"]
        p["energy_panel_total_energy"] = 0
        p["potions"] = [dict(id="Potion Slot", slot=-1)] * 3
        monsters = state["cur_map_node_state"]["monster_data"]
        reviving = copy.deepcopy(monsters[0])
        reviving["creature"].update(id="Darkling", current_health=0, max_health=50,
                                    half_dead=True, is_dead=False)
        reviving.update(next_move=5, intent_name="BUFF", move_history=["4", "5"])
        monsters.insert(0, reviving)
        imported = self.run_worker(state)
        self.assertTrue(imported["supported"], imported)
        self.assertEqual(imported["projection"], projection(state))
        result = self.run_worker(state, "search")
        self.assertTrue(result["supported"], result)
        self.assertEqual(json.loads(result["commands"][0]["command"])["type"], "END")
        self.assertEqual(result["expected_after"]["monsters"][0]["hp"], 25)

    def test_indexed_and_inline_combat_history(self):
        state = load(FIXTURE)
        state["cards_played_this_turn"] = [0]  # draw-pile Bash: fixture master_deck is empty.
        state["cards_played_this_turn_backup"] = [state["player_state"]["hand"][1]]
        state["cards_played_this_combat"] = [dict(card_index=0, card_state=None),
                                            dict(card_index=-1, card_state=dict(card_id="Creative AI"))]
        result = self.run_worker(state)
        self.assertTrue(result["supported"], result)
        self.assertEqual(result["mechanics"], dict(cards_played=2, attacks_played=1, skills_played=1, powers_played=1))

    def test_search_guarded_single_action(self):
        result = self.run_worker(operation="search")
        self.assertTrue(result["supported"], result)
        server.validate_response(result)

    def test_repeat_is_deterministic(self):
        a, b = self.run_worker(operation="search"), self.run_worker(operation="search")
        self.assertTrue(a["supported"], a)
        self.assertEqual(a["commands"], b["commands"])

    def test_explicit_capability_reasons(self):
        variants = []
        for role in ("UNKNOWN",):
            s = load(FIXTURE); s["player_state"]["chosen_class_name"] = role; variants.append((s, "character:"))
        s = load(FIXTURE); s["screen_name"] = "GRID"; variants.append((s, "selection_screen"))
        s = load(FIXTURE); s["player_state"]["hand"][0]["card_id"] = "Unknown"; variants.append((s, "unknown_card:"))
        s = load(FIXTURE); del s["rng_state"]["shuffle_rng_counter"]; variants.append((s, "shuffle_rng_counter"))
        for state, reason in variants:
            with self.subTest(reason=reason):
                result = self.run_worker(state)
                self.assertFalse(result["supported"], result)
                self.assertIn(reason, result["reason"])

    def test_real_worker_over_tcp(self):
        options = types.SimpleNamespace(worker=WORKER, fallback_port=1, no_fallback=True, max_time_ms=50)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "start.txt"; path.write_text(FIXTURE.read_text(encoding="utf-8"), encoding="utf-8")
            with server.Gateway(("127.0.0.1", 0), options) as gateway:
                thread = threading.Thread(target=gateway.serve_forever, daemon=True); thread.start()
                try:
                    with socket.create_connection(gateway.server_address, timeout=5) as sock:
                        sock.settimeout(5)
                        protocol.send(sock, json.dumps(dict(fileName=str(path), max_expansions=100, timeout_ms=50)))
                        result = json.loads(protocol.receive(sock))
                        self.assertEqual(result["type"], "COMMAND_LIST", result)
                        self.assertEqual(protocol.receive(sock), "DONE")
                        protocol.send(sock, '{"type":"PING"}')
                        self.assertEqual(json.loads(protocol.receive(sock))["type"], "PONG")
                finally:
                    gateway.shutdown(); thread.join()


if __name__ == "__main__":
    unittest.main()
