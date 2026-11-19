"""Start an isolated Java test engine, run read-only snapshot replay checks, then stop it.

No live-game saves/configuration are changed. Runtime copies stay under battle_engine/out.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import threading
import types

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from battle_engine import protocol
from battle_engine.server import Gateway, ROOT


ORACLE_TIPS = (
    "BLOCK TIP", "CARD_REWARD_TIP", "COMBAT_TIP", "ENERGY_USE_TIP", "INTENT_TIP",
    "M_POWER_TIP", "NO_FTUE", "POTION_TIP", "POWER_TIP", "RELIC_TIP", "SHUFFLE_TIP",
)


def freeze_oracle_preferences(source, destination):
    """Copy the profile's unlocks and skip tutorials only inside the oracle."""
    shutil.copytree(source, destination)
    # libGDX preferences encode booleans as strings. Slot 0 uses STSTips;
    # later slots prefix it with the slot number.
    files = {destination / "STSTips", *(p for p in destination.glob("*STSTips") if p.is_file())}
    for path in files:
        tips = json.loads(path.read_text(encoding="utf-8-sig")) if path.is_file() else {}
        tips.update(dict.fromkeys(ORACLE_TIPS, "true"))
        path.write_text(json.dumps(tips), encoding="utf-8")


def check_fallback(runtime, entries, port):
    options = types.SimpleNamespace(worker=ROOT / "bin/battle-engine-worker.exe", fallback_port=port,
                                    no_fallback=False, max_time_ms=200)
    results = []
    with Gateway(("127.0.0.1", 0), options) as gateway:
        thread = threading.Thread(target=gateway.serve_forever, daemon=True); thread.start()
        try:
            for role in ("THE_SILENT", "WATCHER"):
                source = next(e for e in entries if e["role"] == role)
                directory = runtime / "fallback" / role; directory.mkdir(parents=True)
                path = directory / "start.txt"; shutil.copy2(source["path"], path)
                with socket.create_connection(gateway.server_address, timeout=5) as sock:
                    sock.settimeout(30)
                    protocol.send(sock, json.dumps(dict(fileName=str(path.resolve()), client_cwd=str(directory.resolve()),
                        command_file=str((directory / "commands.json").resolve()), max_expansions=64, num_turns=64,
                        search_profile="FAST", timeout_ms=1000)))
                    final = None
                    while True:
                        text = protocol.receive(sock)
                        if text == "DONE": break
                        reply = json.loads(text)
                        if reply.get("type") == "ERROR": raise RuntimeError(str(reply))
                        if reply.get("type") == "COMMAND_LIST": final = reply
                    if final is None: raise RuntimeError("fallback missing COMMAND_LIST")
                    results.append(dict(role=role, passed=True, stop_reason=final.get("stop_reason")))
        finally:
            gateway.shutdown(); thread.join()
    (runtime / "fallback-report.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(f"Real Java fallback PASS {len(results)}/2", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-dir", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--port", type=int, default=15126)
    parser.add_argument("--timeout-ms", type=int, default=500)
    parser.add_argument("--simulations", type=int, default=1000)
    parser.add_argument("--steps", type=int, default=1)
    parser.add_argument("--allow-boundary-fallback", action="store_true")
    parser.add_argument("--balanced-ab", choices=("smoke", "pilot", "formal"))
    parser.add_argument("--pilot-arm", choices=("legacy", "depth", "combined"), default="combined")
    parser.add_argument("--worker", type=Path, help="Explicit candidate binary for --balanced-ab")
    args = parser.parse_args()
    if args.balanced_ab and not args.worker:
        parser.error("--balanced-ab requires --worker")
    runtime = ROOT / "out" / f"oracle-{time.time_ns()}"
    mods = runtime / "mods"
    mods.mkdir(parents=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", args.port))
    shutil.copytree(args.game_dir / "jre", runtime / "jre")
    for name in ("desktop-1.0.jar", "ModTheSpire.jar"):
        shutil.copy2(args.game_dir / name, runtime / name)
    enabled = ["BaseMod.jar", "SaveStateMod.jar", "StSLib.jar", "LudicrousSpeed.jar", "BattleAiMod.jar", "CommunicationMod.jar"]
    for name in enabled:
        source = (args.game_dir / "mods" if args.balanced_ab else ROOT.parent / "_ModTheSpire/mods") / name
        shutil.copy2(source, mods / name)
    freeze_oracle_preferences(args.game_dir / "preferences", runtime / "preferences")
    env = os.environ.copy()
    env["LOCALAPPDATA"] = env["APPDATA"] = str(runtime / "appdata")
    config = runtime / "appdata/ModTheSpire"
    config.mkdir(parents=True)
    (config / "mod_lists.json").write_text(json.dumps(dict(defaultList="Oracle", lists={"Oracle": enabled})), encoding="utf-8")
    command = [str(args.game_dir.resolve() / "jre/bin/java.exe"), "-DisServer=true", "-DisPlaidMode=true",
        "-Dbattleai.testMode=true", f"-Dbattleai.port={args.port}", "-Dcommunicationmod.port=15001",
        "-Xms512m", "-Xmx2048m", "-jar", "ModTheSpire.jar",
        "--profile", "Oracle", "--skip-launcher", "--skip-intro", "--jre51"]
    with (runtime / "server.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=runtime, env=env, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            deadline = time.monotonic() + 120
            while True:
                if process.poll() is not None:
                    raise RuntimeError(f"Java test engine exited: {runtime / 'server.log'}")
                try:
                    with socket.create_connection(("127.0.0.1", args.port), timeout=1) as sock:
                        protocol.send(sock, '{"type":"PING"}')
                        pong = json.loads(protocol.receive(sock))
                        if pong.get("test_mode"):
                            break
                except (OSError, EOFError):
                    pass
                if time.monotonic() > deadline:
                    raise TimeoutError(f"Java startup timeout: {runtime / 'server.log'}")
                time.sleep(1)
            if args.balanced_ab:
                from battle_engine.tools import balanced_ab
                return balanced_ab.main([
                    "--manifest", str(args.manifest.resolve()), "--oracle-port", str(args.port),
                    "--worker", str(args.worker.resolve()), "--java-jar", str(mods / "BattleAiMod.jar"),
                    "--output", str(runtime / "balanced-ab"), "--split", args.balanced_ab,
                    "--pilot-arm", args.pilot_arm,
                ])
            acceptance_command = [sys.executable, "-u", str(ROOT / "tools/acceptance.py"), "--manifest", str(args.manifest.resolve()),
                "--oracle-port", str(args.port), "--output", str(runtime / "report.json"),
                "--timeout-ms", str(args.timeout_ms), "--simulations", str(args.simulations), "--steps", str(args.steps)]
            if args.allow_boundary_fallback:
                acceptance_command.append("--allow-boundary-fallback")
            result = subprocess.call(acceptance_command)
            entries = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
            if all(any(e["role"] == r for e in entries) for r in ("THE_SILENT", "WATCHER")):
                check_fallback(runtime, entries, args.port)
            return result
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait()
            print(f"Oracle artifacts: {runtime}")


if __name__ == "__main__":
    raise SystemExit(main())
