"""Explicit headless Battle AI integration runner. No third-party dependencies."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
from typing import Any

try:
    from .manifest import Fixture, ManifestError, load_manifest, validate_search_response
    from .protocol import ProtocolError, receive_json, receive_text, send_json
except ImportError:  # Direct execution: python battle_test/run.py
    from manifest import Fixture, ManifestError, load_manifest, validate_search_response
    from protocol import ProtocolError, receive_json, receive_text, send_json

ROOT = Path(__file__).resolve().parent
DEFAULT_GAME_DIR = Path(r"D:\Program Files\Slay the Spire")
REQUIRED_MODS = ("BaseMod.jar", "StSLib.jar", "SaveStateMod.jar", "LudicrousSpeed.jar", "BattleAiMod.jar")


class RunnerError(RuntimeError):
    pass


def game_dir(value: str | None) -> Path:
    return Path(value or os.environ.get("STS_GAME_DIR") or DEFAULT_GAME_DIR).expanduser().resolve()


def verify_installation(directory: Path) -> None:
    required = [directory / "SlayTheSpire.exe", directory / "ModTheSpire.jar"]
    required.extend(directory / "mods" / name for name in REQUIRED_MODS)
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise RunnerError("missing game or mod files:\n" + "\n".join(missing))


def build_command(directory: Path, port: int) -> list[str]:
    java = directory / "jre" / "bin" / "java.exe"
    if not java.is_file():
        java = Path(shutil.which("java") or "java")
    mod_list = ",".join(str(directory / "mods" / name) for name in REQUIRED_MODS)
    return [str(java), "-Dbattleai.testMode=true", "-DisServer=true", "-Dbattleai.port=%d" % port,
            "-jar", str(directory / "ModTheSpire.jar"), "--skip-launcher", "--mods", mod_list]


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def terminate_process_tree(process: subprocess.Popen[Any], timeout: float = 5) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True, timeout=timeout)
    else:
        process.terminate()
        try:
            process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()


class Server:
    def __init__(self, directory: Path, port: int, artifacts: Path) -> None:
        self.port = port
        self.connection: socket.socket | None = None
        self.stdout = (artifacts / "server.stdout.log").open("w", encoding="utf-8")
        self.stderr = (artifacts / "server.stderr.log").open("w", encoding="utf-8")
        self.process = subprocess.Popen(build_command(directory, port), cwd=directory, stdout=self.stdout,
                                        stderr=self.stderr, text=True)

    def wait_ready(self, timeout: float) -> None:
        deadline = time.monotonic() + timeout
        last_error: OSError | None = None
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RunnerError("server exited before becoming ready (exit %s)" % self.process.returncode)
            try:
                connection = socket.create_connection(("127.0.0.1", self.port), timeout=1)
                connection.settimeout(2)
                send_json(connection, {"type": "PING"})
                response = receive_json(connection)
                if response.get("type") == "PONG":
                    self.connection = connection
                    return
                connection.close()
            except (OSError, ProtocolError) as exc:
                last_error = exc
            time.sleep(0.2)
        raise RunnerError("server did not become ready: %s" % last_error)

    def request(self, request: dict[str, Any], timeout: float) -> dict[str, Any]:
        if self.connection is None:
            raise RunnerError("server connection is not ready")
        self.connection.settimeout(timeout)
        send_json(self.connection, request)
        while True:
            response = receive_json(self.connection)
            if response.get("type") == "COMMAND_LIST":
                done = receive_text(self.connection)
                if done != "DONE":
                    raise RunnerError("expected DONE after command list")
                return response

    def replay(self, request: dict[str, Any], timeout: float) -> dict[str, Any]:
        if self.connection is None:
            raise RunnerError("server connection is not ready")
        self.connection.settimeout(timeout)
        send_json(self.connection, request)
        response = receive_json(self.connection)
        if response.get("type") != "REPLAY":
            raise RunnerError("expected REPLAY response")
        return response

    def close(self) -> None:
        try:
            if self.connection is not None:
                self.connection.settimeout(2)
                send_json(self.connection, {"type": "SHUTDOWN"})
                receive_json(self.connection)
        except (OSError, ProtocolError):
            pass
        finally:
            if self.connection is not None:
                self.connection.close()
            terminate_process_tree(self.process)
            self.stdout.close()
            self.stderr.close()


def search_request(fixture: Fixture, artifact_dir: Path) -> dict[str, Any]:
    return {
        "fileName": str(fixture.file),
        "client_cwd": str(artifact_dir),
        "command_file": str(artifact_dir / (fixture.id + ".commands.json")),
        "max_expansions": fixture.max_expansions,
        "num_turns": fixture.max_expansions,
        "timeout_ms": int(fixture.timeout_seconds * 1000),
        # DEEP suppresses interim-path streaming; the explicit limits above
        # still control the actual budget for deterministic fixture runs.
        "search_profile": "DEEP",
    }


def run_fixture(server: Server, fixture: Fixture, artifact_dir: Path) -> list[str]:
    if not fixture.file.is_file():
        return ["missing captured state fixture: %s" % fixture.file]
    request = search_request(fixture, artifact_dir)
    (artifact_dir / (fixture.id + ".request.json")).write_text(json.dumps(request, indent=2), encoding="ascii")
    try:
        response = server.request(request, fixture.timeout_seconds)
    except (OSError, ProtocolError, RunnerError) as exc:
        return ["search request failed: %s" % exc]
    if "commands" not in response and isinstance(response.get("command_path"), str):
        try:
            response = json.loads(Path(response["command_path"]).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            return ["could not load command-file fallback: %s" % exc]
    (artifact_dir / (fixture.id + ".response.json")).write_text(json.dumps(response, indent=2), encoding="ascii")
    errors = validate_search_response(response)
    if errors:
        return errors
    replay_request = {
        "type": "REPLAY",
        "fileName": str(fixture.file),
        "client_cwd": str(artifact_dir),
        "commands": response["commands"],
        "timeout_ms": int(fixture.timeout_seconds * 1000),
    }
    try:
        replay = server.replay(replay_request, fixture.timeout_seconds)
    except (OSError, ProtocolError, RunnerError) as exc:
        return ["replay request failed: %s" % exc]
    (artifact_dir / (fixture.id + ".replay.json")).write_text(json.dumps(replay, indent=2), encoding="ascii")
    if replay.get("error") is not None:
        errors.append("replay failed: %s" % replay["error"])
    if replay.get("diff_valid") is not True:
        errors.append("replay diff validation failed")
    if replay.get("commands_executed") != len(response["commands"]):
        errors.append("replay did not execute every returned command")
    if replay.get("final_state_key") != response["final_state_key"]:
        errors.append("search and replay final_state_key differ")
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-dir")
    parser.add_argument("--fixture")
    parser.add_argument("--keep-artifacts", action="store_true")
    parser.add_argument("--isolation-check", action="store_true")
    parser.add_argument("--ready-timeout", type=float, default=45)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        fixtures = load_manifest(ROOT / "fixtures" / "manifest.json")
        if args.fixture:
            fixtures = [fixture for fixture in fixtures if fixture.id == args.fixture]
            if not fixtures:
                raise RunnerError("unknown fixture: %s" % args.fixture)
        directory = game_dir(args.game_dir)
        verify_installation(directory)
    except (ManifestError, RunnerError) as exc:
        print("ERROR:", exc, file=sys.stderr)
        return 2

    artifacts = ROOT / "artifacts" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    artifacts.mkdir(parents=True)
    server = Server(directory, free_port(), artifacts)
    failures: dict[str, list[str]] = {}
    try:
        server.wait_ready(args.ready_timeout)
        for fixture in fixtures:
            errors = run_fixture(server, fixture, artifacts)
            if fixture.expected_result == "known_failure" and not errors:
                failures[fixture.id] = ["known_failure unexpectedly passed; remove its exemption"]
                print("FAIL", fixture.id, failures[fixture.id][0])
            elif errors and fixture.expected_result != "known_failure":
                failures[fixture.id] = errors
                print("FAIL", fixture.id, "; ".join(errors))
            elif errors:
                print("KNOWN_FAILURE", fixture.id, "; ".join(errors))
            else:
                print("PASS", fixture.id)
    except RunnerError as exc:
        print("ERROR:", exc, file=sys.stderr)
        return 2
    finally:
        server.close()

    (artifacts / "summary.json").write_text(json.dumps({"failures": failures}, indent=2), encoding="ascii")
    if failures:
        print("Artifacts:", artifacts)
        return 1
    if not args.keep_artifacts:
        shutil.rmtree(artifacts)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
