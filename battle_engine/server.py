"""C++-only loopback gateway by default. Run: python -m battle_engine.server."""
import argparse
import json
import logging
import os
from pathlib import Path
import socket
import socketserver
import subprocess
import tempfile
import threading
import time
import uuid

from . import protocol
from .state import context, load, projection
from .profiles import budgets
from .worker import Worker

ROOT = Path(__file__).resolve().parent


def json_text(value):
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"))


def execute_worker(worker, state, expansions, timeout_ms, operation="search", manager=None, metadata=None):
    request = dict(snapshot=state, max_expansions=expansions, timeout_ms=timeout_ms, operation=operation)
    request.update(metadata or {})
    if manager is not None:
        return manager.execute(request, timeout_ms)
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    completed = subprocess.run([str(worker)], input=json_text(request).encode("utf-8"),
                               capture_output=True, timeout=timeout_ms / 1000 + 2,
                               creationflags=flags)
    if completed.returncode:
        raise RuntimeError(f"worker_exit:{completed.returncode} stderr={completed.stderr[-2000:]!r}")
    result = json.loads(completed.stdout)
    if not isinstance(result, dict) or not isinstance(result.get("supported"), bool):
        raise ValueError("invalid_worker_response")
    return result


def validate_response(response):
    if response.get("type") != "COMMAND_LIST" or len(response.get("commands", [])) != 1:
        raise ValueError("worker must return exactly one guarded action")
    envelope = response["commands"][0]
    if not isinstance(envelope.get("expected_before"), dict):
        raise ValueError("missing expected_before")
    command = json.loads(envelope["command"])
    kind = command.get("type")
    required = {"CARD": ("card_index", "monster_index", "display_string"),
                "POTION": ("potion_index", "monster_index"), "END": (),
                "HAND_SELECT": ("card_index",), "GRID_SELECT": ("card_index",),
                "HAND_SELECT_CONFIRM": (), "GRID_SELECT_CONFIRM": (), "CARD_REWARD_SELECT": ("card_index",)}
    if kind not in required or any(k not in command for k in required[kind]):
        raise ValueError("invalid command schema")
    if response.get("should_replan") is not True:
        raise ValueError("single-action response must request replanning")


class Gateway(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, options):
        self.options = options
        self.search_lock = threading.Lock()
        self.worker = Worker(options.worker)
        self.closed = False
        super().__init__(address, Handler)

    def server_close(self):
        self.closed = True
        super().server_close()
        self.worker.close()


class Handler(socketserver.BaseRequestHandler):
    def finish(self):
        with self.server.search_lock:
            if not self.server.closed and self.server.worker.process is not None:
                try:
                    self.server.worker.execute(dict(operation="release_session", session_id=self.session_id), 100)
                except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired):
                    logging.exception("session_cleanup_error")

    def setup(self):
        self.session_id = uuid.uuid4().hex
        self.battle_key = None
        self.expected_after = None
        self.expected_screen = None
        self.cpp_disabled = False
        self.disabled_reason = None
        self.last_before = None
        self.last_response = None
        self.request.settimeout(300)

    def handle(self):
        while True:
            try:
                raw = protocol.receive_bytes(self.request)
            except (EOFError, OSError):
                return
            try:
                request = json.loads(protocol.decode(raw))
                if request.get("type") == "PING":
                    protocol.send(self.request, json_text(dict(type="PONG", backend="cpp-gateway",
                        mode="cpp-only" if self.server.options.no_fallback else "java-enabled",
                        ready=Path(self.server.options.worker).is_file())))
                    continue  # Existing Java PING endpoint has no DONE frame.
                with self.server.search_lock:
                    self.trace = {}
                    started = time.monotonic()
                    try:
                        self.run_request(request, raw)
                    except Exception as error:
                        self.trace["error"] = str(error)
                        raise
                    finally:
                        if self.trace and getattr(self.server.options, "telemetry", None):
                            self.trace["wall_ms"] = round((time.monotonic() - started) * 1000, 3)
                            self.trace["ts"] = time.time()
                            with Path(self.server.options.telemetry).open("a", encoding="utf-8") as stream:
                                stream.write(json_text(self.trace) + "\n")
            except (OSError, EOFError):
                return
            except Exception as error:
                logging.exception("request_error")
                try:
                    protocol.send(self.request, json_text(dict(type="ERROR", message=str(error))))
                    protocol.send(self.request, "DONE")
                except OSError:
                    return

    def run_request(self, request, raw):
        options = self.server.options
        state = load(Path(request["fileName"]))
        if "combat_card_pools" in request:
            state["_combat_card_pools"] = request["combat_card_pools"]
        player = state["player_state"]
        self.trace = dict(source=request["fileName"], seed=state["rng_state"]["seed"], floor=state["floor_num"],
                          role=player["chosen_class_name"], turn=state["turn"], hp=player["creature"]["current_health"],
                          max_hp=player["creature"]["max_health"], timeout_ms=request.get("timeout_ms"),
                          potions=sum(p["id"] != "Potion Slot" for p in player["potions"]),
                          encounter=state.get("lastCombatMetricKey"))
        if getattr(options, "java_only", False):
            self.fallback(raw, request, "baseline_java")
            return
        key = (state["rng_state"]["seed"], state["floor_num"], state["player_state"]["chosen_class_name"])
        if key != self.battle_key:
            self.battle_key, self.expected_after, self.cpp_disabled = key, None, False
            self.expected_screen = None
            self.disabled_reason = None
            self.last_before, self.last_response = None, None
        before = projection(state)
        profile, expansions, timeout_ms = budgets(request, options.max_time_ms)
        # Full source equality is required for retransmission. The old public
        # projection omits dynamic card attributes and selection continuations.
        signature = dict(state=state, profile=profile, expansions=expansions, timeout_ms=timeout_ms)
        # A dropped response can be requested again before the live state changed.
        if not self.cpp_disabled and signature == self.last_before and self.last_response is not None:
            self.respond(self.last_response, request)
            return
        screen = state["screen_name"] if state.get("is_screen_up") else "NONE"
        if self.expected_after is not None and (before != self.expected_after or
                self.expected_screen is not None and screen != self.expected_screen):
            self.cpp_disabled = True
            self.disabled_reason = "prediction_mismatch"
            logging.error("prediction_mismatch battle=%s; quarantining C++ for this battle", key)
        if self.cpp_disabled:
            self.fallback(raw, request, self.disabled_reason)
            return
        started = time.monotonic()
        try:
            response = execute_worker(options.worker, state, expansions, timeout_ms, "search", self.server.worker,
                                      dict(session_id=self.session_id, search_profile=profile))
            if response["supported"]:
                validate_response(response)
                if response["commands"][0]["expected_before"] != before:
                    raise ValueError("worker source projection differs from request")
                if response["commands"][0].get("expected_context") != context(state):
                    raise ValueError("worker source identity differs from request")
        except (subprocess.TimeoutExpired, OSError, RuntimeError, ValueError) as error:
            self.fallback(raw, request, str(error))
            return
        if not response["supported"]:
            self.fallback(raw, request, response.get("reason", "unsupported"))
            return
        self.expected_after = response.pop("expected_after", None)
        self.expected_screen = response.pop("expected_screen", None)
        self.trace.update(backend="cpp", metrics=response.get("metrics"), command_count=len(response["commands"]))
        if self.expected_after is None:
            self.cpp_disabled = True
            self.disabled_reason = "unvalidated_boundary"
        self.last_before, self.last_response = signature, response
        logging.info("import_ok battle=%s elapsed_ms=%.1f metrics=%s", key,
                     (time.monotonic() - started) * 1000, json_text(response.get("metrics", {})))
        self.respond(response, request)

    def respond(self, response, request):
        text = json_text(response)
        try:
            payload = protocol.encode(text)
        except ValueError:
            if not request.get("command_file"):
                raise ValueError("large response requires command_file")
            path = Path(request["command_file"]).resolve()
            path.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent, delete=False) as stream:
                temporary = stream.name
                stream.write(text)
            os.replace(temporary, path)
            small = {k: v for k, v in response.items() if k != "commands"}
            small["command_path"] = str(path)
            payload = protocol.encode(json_text(small))
        protocol.send_bytes(self.request, payload)
        protocol.send(self.request, "DONE")

    def fallback(self, raw, request, reason):
        options = self.server.options
        self.expected_after, self.last_response, self.last_before = None, None, None
        self.expected_screen = None
        if options.no_fallback:
            logging.error("cpp_only_error battle=%s reason=%s", self.battle_key, reason)
            if hasattr(self, "trace"):
                self.trace.update(backend="cpp_error", reason=reason)
            raise RuntimeError(f"C++ backend stopped: {reason}; mode=cpp-only (no Java connection attempted)")
        logging.warning("forward_to_fallback battle=%s reason=%s", self.battle_key, reason)
        if hasattr(self, "trace"):
            self.trace.update(backend="java" if reason == "baseline_java" else "fallback", reason=reason)
        # Buffer a complete fallback transaction before exposing any commands.
        # On mid-stream failure no partially delivered Java plan is executed.
        replies = []
        try:
            with socket.create_connection(("127.0.0.1", options.fallback_port), timeout=3) as backend:
                deadline = time.monotonic() + min(max(int(request.get("timeout_ms", 30000)), 1), 90000) / 1000 + 15
                backend.sendall(len(raw).to_bytes(2, "big") + raw)
                total = 0
                while True:
                    backend.settimeout(max(0.01, deadline - time.monotonic()))
                    if time.monotonic() >= deadline:
                        raise TimeoutError("fallback deadline")
                    reply = protocol.receive_bytes(backend)
                    replies.append(reply)
                    total += len(reply)
                    if total > 16 * 1024 * 1024:
                        raise ValueError("fallback response stream too large")
                    if protocol.decode(reply) == "DONE":
                        break
                    decoded = json.loads(protocol.decode(reply))
                    if decoded.get("type") == "COMMAND_LIST" and hasattr(self, "trace"):
                        self.trace.update(metrics=decoded.get("metrics"), command_count=len(decoded.get("commands", [])),
                                          stop_reason=decoded.get("stop_reason"), battle_complete=decoded.get("battle_complete"))
        except (OSError, EOFError, ValueError) as error:
            raise RuntimeError(f"Fallback 127.0.0.1:{options.fallback_port} failed; "
                               f"start the Java server with -Dbattleai.port={options.fallback_port}. "
                               f"C++ reason={reason}; {error}") from error
        for reply in replies:
            protocol.send_bytes(self.request, reply)


def parse_options(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=5125)
    parser.add_argument("--worker", type=Path, default=ROOT / "bin" / ("battle-engine-worker.exe" if os.name == "nt" else "battle-engine-worker"))
    parser.add_argument("--fallback-port", type=int, default=5126)
    routing = parser.add_mutually_exclusive_group()
    routing.add_argument("--enable-java-fallback", action="store_false", dest="no_fallback",
                         help="Explicitly opt into the legacy Java search service")
    routing.add_argument("--no-fallback", action="store_true", dest="no_fallback",
                         help="C++ only (default); report errors without connecting to Java")
    parser.set_defaults(no_fallback=True)
    parser.add_argument("--max-time-ms", type=int, default=90000,
                        help="Explicit global ceiling; normal budgets come from search_profile")
    parser.add_argument("--log-dir", type=Path, default=ROOT / "logs")
    parser.add_argument("--java-only", action="store_true", help="Paired benchmark: route every request to Java")
    parser.add_argument("--telemetry", type=Path, help="Append per-request measurements as JSONL")
    options = parser.parse_args(argv)
    if options.java_only and options.no_fallback:
        parser.error("--java-only requires --enable-java-fallback")
    if not 1 <= options.port <= 65535 or not 1 <= options.max_time_ms <= 90000:
        parser.error("port must be in 1..65535 and max-time-ms must be in 1..90000")
    if not options.no_fallback and (not 1 <= options.fallback_port <= 65535 or options.fallback_port == options.port):
        parser.error("Java fallback port must be in 1..65535 and differ from port")
    options.worker = options.worker.resolve()
    if not options.worker.is_file():
        parser.error(f"worker missing: {options.worker}; run build.ps1 first")
    return options


def main():
    options = parse_options()
    options.log_dir.mkdir(parents=True, exist_ok=True)
    if options.telemetry:
        options.telemetry.parent.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s",
                        handlers=[logging.FileHandler(options.log_dir / "gateway.log", encoding="utf-8"), logging.StreamHandler()])
    with Gateway(("127.0.0.1", options.port), options) as server:
        logging.info("BATTLE_ENGINE_READY port=%s worker=%s mode=%s fallback=%s", options.port, options.worker,
                     "cpp-only" if options.no_fallback else "java-enabled",
                     "disabled" if options.no_fallback else options.fallback_port)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
