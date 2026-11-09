"""Paired real-engine combat/replay checks. Standard library only; never edits source saves.

Prepare baseline jars before changing source, then run --phase baseline and --phase candidate.
The manifest is reused across phases. Every stage uses the unmodified SearchProfile budget.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import time


BUDGETS = {"FAST": (5000, 10000), "BALANCED": (15000, 30000), "DEEP": (50000, 90000)}
ROLES = ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")


def obj(value):
    return json.loads(value) if isinstance(value, str) else value


def read_state(path):
    return obj(json.loads(path.read_text(encoding="utf-8-sig")))


def state_info(state):
    player = obj(state["player_state"])
    creature = obj(player["creature"])
    potions = obj(player["potions"])
    return {"role": player["chosen_class_name"], "hp": creature["current_health"],
            "potions": sum(obj(p)["id"] != "Potion Slot" for p in potions)}


def compare_results(output):
    paths = [output / phase / "results.json" for phase in ("baseline", "candidate")]
    if not all(path.exists() for path in paths):
        return
    baseline, candidate = [json.loads(path.read_text(encoding="utf-8")) for path in paths]
    reference = {row["key"]: row for row in baseline}
    pairs = []
    for after in candidate:
        before = reference.get(after["key"])
        if before is None:
            continue
        if before["source"] != after["source"]:
            raise RuntimeError("Mismatched snapshot manifests; use a fresh output directory")
        valid = not before.get("error") and not after.get("error")
        pairs.append({"key": after["key"], "valid_pair": bool(valid),
                      "new_failure": bool(not before.get("error") and (after.get("error") or
                                          before.get("victory") and not after.get("victory"))),
                      "hp_delta": after["hp"] - before["hp"] if valid else None,
                      "potions_delta": after["potions"] - before["potions"] if valid else None,
                      "seconds_delta": after["elapsed_seconds"] - before["elapsed_seconds"] if valid else None,
                      "expanded_delta": sum(s["expanded_nodes"] for s in after["stages"]) -
                                        sum(s["expanded_nodes"] for s in before["stages"]) if valid else None})
    summary = {"pairs": pairs, "valid_pairs": sum(p["valid_pair"] for p in pairs),
               "new_failures": sum(p["new_failure"] for p in pairs),
               "complete": len(pairs) == len(baseline) == len(candidate) and all(p["valid_pair"] for p in pairs)}
    (output / "comparison.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")


def manifest(game, output):
    path = output / "manifest.json"
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    candidates = {role: [] for role in ROLES}
    for save in sorted((game / "startstates").rglob("start.txt")):
        try:
            state = read_state(save)
            # Old integer-index combat histories cannot be restored by the
            # current container-based SaveState format. Never rewrite a save.
            if any(not isinstance(entry, dict) for field in ("cards_played_this_combat", "grid_selected_cards", "drawn_cards")
                   for entry in obj(state.get(field, []))):
                continue
            role = state_info(state)["role"]
            if role not in candidates:
                continue
            room = obj(state["cur_map_node_state"])
            monsters = obj(room["monster_data"])
            if isinstance(monsters, str):
                continue
            kind = next((obj(m)["type_name"] for m in monsters
                         if obj(m)["type_name"] in ("BOSS", "ELITE")), "NORMAL")
            candidates[role].append({"path": str(save), "role": role, "kind": kind,
                                     "floor": state["floor_num"]})
        except (KeyError, TypeError, ValueError):
            continue
    chosen = []
    for role, saves in candidates.items():
        selected = []
        for kind in ("NORMAL", "ELITE", "BOSS"):
            match = next((s for s in saves if s["kind"] == kind and s not in selected), None)
            if match:
                selected.append(match)
        selected.extend(s for s in saves if s not in selected)
        if len(selected) < 4:
            raise RuntimeError(f"Need four real saves for {role}, found {len(selected)}")
        chosen.extend(selected[:4])
    path.write_text(json.dumps(chosen, indent=2), encoding="utf-8")
    return chosen


def send(sock, payload):
    encoded = json.dumps(payload, ensure_ascii=True).encode("ascii")
    if len(encoded) > 65535:
        raise RuntimeError("Request exceeds DataInputStream.writeUTF limit")
    sock.sendall(struct.pack(">H", len(encoded)) + encoded)


def exact(sock, length):
    result = bytearray()
    while len(result) < length:
        chunk = sock.recv(length - len(result))
        if not chunk:
            raise EOFError("Search server disconnected")
        result.extend(chunk)
    return bytes(result)


def receive(sock):
    # Responses contain ASCII or UTF-8 JSON; Java modified-UTF encodes NUL only
    # inside escaped JSON strings, so normal UTF-8 is sufficient here.
    size = struct.unpack(">H", exact(sock, 2))[0]
    text = exact(sock, size).decode("utf-8")
    return text if text == "DONE" else json.loads(text)


def run_fight(port, source, directory, profile):
    directory.mkdir(parents=True, exist_ok=True)
    current = source
    stages = []
    started = time.monotonic()
    for index in range(30):
        stage = directory / str(index)
        stage.mkdir(exist_ok=True)
        start = stage / "start.txt"
        shutil.copy2(current, start)
        expansions, timeout = BUDGETS[profile]
        with socket.create_connection(("127.0.0.1", port), timeout=10) as sock:
            sock.settimeout(timeout / 1000 + 90)
            send(sock, {"fileName": str(start), "client_cwd": str(stage),
                        "search_profile": profile, "num_turns": expansions,
                        "max_expansions": expansions, "timeout_ms": timeout,
                        "command_file": str(stage / "commands.json")})
            final = None
            while True:
                reply = receive(sock)
                if reply == "DONE":
                    break
                if reply.get("type") == "ERROR":
                    raise RuntimeError(reply["message"])
                if reply.get("type") == "COMMAND_LIST":
                    final = reply
            if final is None:
                raise RuntimeError("Missing COMMAND_LIST")
            if "commands" not in final:
                final = json.loads(Path(final["command_path"]).read_text(encoding="utf-8"))
            send(sock, {"type": "REPLAY", "fileName": str(start),
                        "client_cwd": str(stage), "commands": final["commands"], "timeout_ms": 30000})
            replay = receive(sock)
            (stage / "replay.json").write_text(json.dumps(replay, indent=2), encoding="utf-8")
            if not replay.get("diff_valid") or replay.get("final_state_key") != final["final_state_key"]:
                raise RuntimeError(f"Replay divergence: {replay}")
        stages.append(final["metrics"])
        current = stage / "end.txt"
        end = state_info(read_state(current))
        if final.get("battle_complete") or end["hp"] <= 0 or not final.get("should_replan"):
            return {**end, "victory": bool(final.get("battle_complete")),
                    "elapsed_seconds": time.monotonic() - started, "stages": stages,
                    "replay_valid": True, "stop_reason": final.get("stop_reason")}
    raise RuntimeError("Exceeded 30 replanning stages")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-dir", type=Path, required=True)
    parser.add_argument("--baseline-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--phase", choices=("baseline", "candidate"), required=True)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--port", type=int, default=15125)
    parser.add_argument("--profiles", nargs="+", choices=tuple(BUDGETS), default=["BALANCED", "FAST", "DEEP"])
    parser.add_argument("--manifest-only", action="store_true", help="Select compatible snapshots without starting the game")
    args = parser.parse_args()
    args.game_dir = args.game_dir.resolve()
    args.baseline_dir = args.baseline_dir.resolve()
    args.repo = args.repo.resolve()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    saves = manifest(args.game_dir, output)
    if args.manifest_only:
        print(json.dumps(saves, indent=2))
        return
    for save in saves:
        digest = hashlib.sha256(Path(save["path"]).read_bytes()).hexdigest()
        if save.get("sha256", digest) != digest:
            raise RuntimeError(f"Source snapshot changed: {save['path']}")
        save["sha256"] = digest
    (output / "manifest.json").write_text(json.dumps(saves, indent=2), encoding="utf-8")
    runtime = output / args.phase / "runtime"
    mods = runtime / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    if not (runtime / "jre").exists():
        shutil.copytree(args.game_dir / "jre", runtime / "jre")
    for name in ("desktop-1.0.jar", "ModTheSpire.jar"):
        target = runtime / name
        if not target.exists():
            shutil.copy2(args.game_dir / name, target)
    for jar in (args.repo / "_ModTheSpire" / "mods").glob("*.jar"):
        shutil.copy2(jar, mods / jar.name)
    if args.phase == "baseline":
        for jar in args.baseline_dir.glob("*.jar"):
            shutil.copy2(jar, mods / jar.name)
    java = args.game_dir / "jre" / "bin" / "java.exe"
    # MTS persists enabled mods during startup, even when launching via CLI.
    # Isolate its config so a benchmark cannot change the user's normal profile.
    environment = os.environ.copy()
    environment["LOCALAPPDATA"] = str(runtime / "appdata")
    environment["APPDATA"] = str(runtime / "appdata")
    config = runtime / "appdata" / "ModTheSpire"
    config.mkdir(parents=True, exist_ok=True)
    enabled = ["BaseMod.jar", "SaveStateMod.jar", "StSLib.jar", "LudicrousSpeed.jar", "BattleAiMod.jar", "CommunicationMod.jar"]
    (config / "mod_lists.json").write_text(json.dumps({"defaultList": "Benchmark", "lists": {"Benchmark": enabled}}), encoding="utf-8")
    command = [str(java), "-DisServer=true", "-DisPlaidMode=true", "-Dbattleai.testMode=true",
               f"-Dbattleai.port={args.port}", "-Xms512m", "-Xmx2048m", "-jar", "ModTheSpire.jar",
               "--profile", "Benchmark",
               "--skip-launcher", "--skip-intro", "--jre51"]
    results_path = output / args.phase / "results.json"
    results = json.loads(results_path.read_text(encoding="utf-8")) if results_path.exists() else []
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", args.port))
    with (runtime / "server.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=runtime, stdout=log, stderr=subprocess.STDOUT, env=environment,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            deadline = time.monotonic() + 120
            while True:
                if process.poll() is not None:
                    raise RuntimeError(f"Server exited {process.returncode}; see {runtime / 'server.log'}")
                try:
                    with socket.create_connection(("127.0.0.1", args.port), timeout=1) as sock:
                        send(sock, {"type": "PING"})
                        if receive(sock).get("test_mode"):
                            break
                except (OSError, EOFError):
                    pass
                if time.monotonic() > deadline:
                    raise TimeoutError(f"Server startup timeout; see {runtime / 'server.log'}")
                time.sleep(1)
            for profile in args.profiles:
                subset = saves if profile == "BALANCED" else [next(s for s in saves if s["role"] == r) for r in ROLES]
                for index, save in enumerate(subset):
                    key = f"{profile}-{index}-{save['role']}"
                    if any(r["key"] == key for r in results):
                        continue
                    print(f"{args.phase} {key} {save['kind']}", flush=True)
                    try:
                        result = run_fight(args.port, Path(save["path"]), output / args.phase / key, profile)
                    except Exception as error:
                        result = {"error": str(error), "replay_valid": False}
                    results.append({"key": key, "source": save, **result})
                    results_path.write_text(json.dumps(results, indent=2), encoding="utf-8")
                    print(json.dumps({"key": key, **result}), flush=True)
        finally:
            try:
                with socket.create_connection(("127.0.0.1", args.port), timeout=2) as sock:
                    send(sock, {"type": "SHUTDOWN"})
                    receive(sock)
                process.wait(timeout=10)
            except (OSError, EOFError, subprocess.TimeoutExpired):
                process.terminate()
                process.wait(timeout=10)
    compare_results(output)


if __name__ == "__main__":
    main()
