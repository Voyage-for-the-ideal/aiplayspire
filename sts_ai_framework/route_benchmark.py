"""Isolated, serial whole-run route A/B. No training and no change to user saves.

Run with the spire Python: python -m sts_ai_framework.route_benchmark --output <new-dir>.
The isolated client auto-starts JNI searches. The audit reads the actual native request budget.
"""
import argparse
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import socketserver
import subprocess
import sys
import threading
import time
from types import SimpleNamespace

from battle_engine import protocol

ROLES = {"IRONCLAD": ("RT20IC01", "RT20IC02", "RT20IC03"),
         "DEFECT": ("RT20DF01", "RT20DF02", "RT20DF03")}
MODS = ("BaseMod.jar", "SaveStateMod.jar", "StSLib.jar", "LudicrousSpeed.jar",
        "BattleAiMod.jar", "CommunicationMod.jar")
BUDGET = {"backend": "cpp-jni", "profile": "BALANCED", "max_expansions": 15000, "timeout_ms": 30000}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"),
                                     ensure_ascii=True).encode()).hexdigest()


def sha(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def jsonl(path, value):
    with Path(path).open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(value, ensure_ascii=True, separators=(",", ":")) + "\n")


def seed_long(value):
    total = 0
    for character in value.upper().replace("O", "0"):
        total = (total * 35 + "0123456789ABCDEFGHIJKLMNPQRSTUVWXYZ".index(character)) & ((1 << 64) - 1)
    return total if total < 1 << 63 else total - (1 << 64)


def seed_group(value):
    import uuid
    raw = bytearray(hashlib.md5(("seed:" + str(seed_long(value))).encode()).digest())
    raw[6], raw[8] = (raw[6] & 15) | 48, (raw[8] & 63) | 128
    return str(uuid.UUID(bytes=bytes(raw)))


def sequence():
    result = []
    for index in range(3):
        for role in ROLES:
            policies = ("planner", "resource_v2") if len(result) // 2 % 2 == 0 else ("resource_v2", "planner")
            result.extend({"character": role, "seed": ROLES[role][index], "policy": p} for p in policies)
    return result


class CachedCompletions:
    """Benchmark-only exact-request response reuse; normal agent is untouched."""
    def __init__(self, underlying, directory):
        self.underlying, self.directory = underlying, Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)

    def create(self, **kwargs):
        path = self.directory / (digest(kwargs) + ".json")
        if path.exists():
            value = json.loads(path.read_text(encoding="utf-8"))
            return SimpleNamespace(choices=[SimpleNamespace(
                message=SimpleNamespace(content=value["content"]), finish_reason=value["finish_reason"])])
        response = self.underlying.create(**kwargs)
        choice = response.choices[0]
        write_json(path, {"content": choice.message.content, "finish_reason": choice.finish_reason})
        return response


def copied_file(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        # Snapshot copies must survive a later build/update of the source jars.
        shutil.copy2(source, target)


def prepare(args):
    from dotenv import load_dotenv
    load_dotenv(args.repo / "sts_ai_framework" / ".env")
    from .value_checkpoint import resolve_value_checkpoint
    checkpoint = Path(resolve_value_checkpoint(args.repo / "selectcard"))
    if not checkpoint.is_file():
        raise FileNotFoundError(checkpoint)
    reference = args.output / "reference"
    reference.mkdir(parents=True, exist_ok=True)
    prefs = reference / "preferences"
    if not prefs.exists():
        shutil.copytree(args.game_dir / "preferences", prefs)
    appdata = reference / "appdata" / "ModTheSpire"
    appdata.mkdir(parents=True, exist_ok=True)
    for name in ("BaseMod", "SaveStateMod"):
        source = Path(os.environ["LOCALAPPDATA"]) / "ModTheSpire" / name
        if source.exists() and not (appdata / name).exists():
            shutil.copytree(source, appdata / name)
    options = appdata / "BattleAIMod" / "options.properties"
    options.parent.mkdir(parents=True, exist_ok=True)
    if not options.exists():
        options.write_text("search_profile=BALANCED\nauto_start_ai=true\ncontroller_mode=FULL_UI\n", encoding="ascii")
    enabled = {"defaultList": "RouteBenchmark", "lists": {"RouteBenchmark": list(MODS)}}
    if not (appdata / "mod_lists.json").exists():
        write_json(appdata / "mod_lists.json", enabled)
    runtime = {"game": sha(args.game_dir / "desktop-1.0.jar"),
               "mods": {name: sha(args.repo / "_ModTheSpire" / "mods" / name) for name in MODS},
               "checkpoint": sha(checkpoint), "combat_budget": BUDGET,
               "initial_files": {str(p.relative_to(reference)): sha(p) for p in sorted(reference.rglob("*")) if p.is_file()}}
    sources = {str(p.relative_to(args.repo)): sha(p) for p in sorted((args.repo / "sts_ai_framework").rglob("*.py"))
               if "tests" not in p.parts}
    manifest = {"format": "route-paired-v1", "runtime_fingerprint": digest(runtime),
                "runtime": runtime, "checkpoint": str(checkpoint), "sources": sources,
                "sequence": sequence(), "exploratory": True}
    path = args.output / "manifest.json"
    if path.exists():
        old = json.loads(path.read_text(encoding="utf-8"))
        if old.get("driver_revisions"):
            manifest["driver_revisions"] = old["driver_revisions"]
        if old != manifest:
            driver = "sts_ai_framework\\route_benchmark.py" if os.name == "nt" else "sts_ai_framework/route_benchmark.py"
            old_policy = {k: v for k, v in old["sources"].items() if k != driver}
            new_policy = {k: v for k, v in sources.items() if k != driver}
            old_rest = {k: v for k, v in old.items() if k not in ("sources", "driver_revisions")}
            new_rest = {k: v for k, v in manifest.items() if k not in ("sources", "driver_revisions")}
            if not (args.resume_navigation and old_policy == new_policy and old_rest == new_rest):
                raise ValueError("frozen benchmark sources/runtime changed; use a fresh output directory")
            backup = args.output / ("manifest-driver-" + old["sources"][driver][:12] + ".json")
            if not backup.exists():
                write_json(backup, old)
            revisions = old.get("driver_revisions", [{"sha256": old["sources"][driver], "reason": "original"}])
            manifest["driver_revisions"] = revisions + [{"sha256": sources[driver],
                "reason": "user-approved deterministic empty reward cancel navigation; policy sources unchanged"}]
            write_json(path, manifest)
    else:
        write_json(path, manifest)
    return manifest


def stage(args, directory):
    for name in ("desktop-1.0.jar", "ModTheSpire.jar"):
        copied_file(args.game_dir / name, directory / name)
    for name in MODS:
        copied_file(args.repo / "_ModTheSpire" / "mods" / name, directory / "mods" / name)
    # Preferences, binaries and appdata are independent snapshots, not hard links.
    if not (directory / "jre").exists():
        for source in (args.game_dir / "jre").rglob("*"):
            if source.is_file():
                copied_file(source, directory / "jre" / source.relative_to(args.game_dir / "jre"))
    shutil.copytree(args.output / "reference" / "preferences", directory / "preferences")
    shutil.copytree(args.output / "reference" / "appdata", directory / "appdata")


def single(args):
    from . import config
    config.ROUTE_POLICY = args.policy
    from .__main__ import _is_action_effective
    from .game_client import GameClient
    from .llm_agent import LLMAgent
    from .models import ActionType
    from .run_lifecycle import get_lifecycle_action, is_lifecycle_state
    from .routing.planner import RoutePlanner
    from .routing.resource import ResourceRoutePlanner
    from .routing.strength import deck_profile
    from .routing.telemetry import RouteForecastTracker
    from selectcard.policy.contracts import observable_state
    from .run_log import setup_run_log
    setup_run_log(str(args.output))
    manifest = json.loads((args.manifest).read_text(encoding="utf-8"))
    os.environ["STS_VALUE_CHECKPOINT"] = manifest["checkpoint"]
    client = GameClient("http://127.0.0.1:15000")
    agent = LLMAgent(game_client=client, debug_prompt_file=None)
    if agent.value_engine is None:
        raise RuntimeError("benchmark requires the existing frozen value checkpoint")
    agent.llm_client = SimpleNamespace(chat=SimpleNamespace(completions=CachedCompletions(
        agent.llm_client.chat.completions, args.manifest.parent / "llm-cache")))
    tracker = RouteForecastTracker()
    pending = None
    started = time.monotonic()
    last_progress = started
    last_signature = None
    seen = set()
    result = {"character": args.character, "seed": args.seed, "policy": args.policy,
              "driver_sha256": sha(Path(__file__)),
              "runtime_fingerprint": manifest["runtime_fingerprint"], "combat_budget": BUDGET,
              "terminal_known": False, "act2_victory": False, "act3_victory": False,
              "heart_victory": False, "boss_entries": []}
    try:
        while time.monotonic() - started < args.run_timeout:
            value = client.get_state()
            if value is None:
                if time.monotonic() - last_progress > 180:
                    raise TimeoutError("game state missing for 180s")
                time.sleep(.5)
                continue
            observation = observable_state(value)
            signature = digest({"state": observation, "screen": value.screen_type,
                "phase": value.room_phase, "monsters": [m.model_dump() for m in value.monsters],
                "hand": [c.reference for c in value.hand], "choices": value.choice_list,
                "selected_character": value.selected_character, "ascension": value.ascension_level})
            if signature != last_signature:
                last_signature, last_progress = signature, time.monotonic()
            elif time.monotonic() - last_progress > 300:
                raise TimeoutError("unchanged game state for 300s")
            error = tracker.observe(value)
            if error:
                jsonl(args.output / "prediction-errors.jsonl", error)
            if not is_lifecycle_state(value) and value.character:
                if "start_fingerprint" not in result:
                    if value.floor != 0 or value.seed_group != seed_group(args.seed):
                        raise ValueError("floor-0 seed verification failed")
                    if value.character != args.character or value.ascension_level != 20:
                        raise ValueError("actual character/ascension differs from paired manifest")
                    if not any(c.base_damage is not None or c.damage is not None
                               or c.base_block is not None or c.block is not None for c in value.deck):
                        raise ValueError("deployed bridge is missing permanent-card numerics; rebuild CommunicationMod")
                    result.update(start_fingerprint=digest({"state": observation, "choices": value.choice_list,
                        "event": value.event.model_dump(mode="json") if value.event else None}),
                        seed_group=value.seed_group)
                    jsonl(args.output / "observations.jsonl", {"kind": "start", "state": value.model_dump(mode="json")})
                result["terminal_floor"] = value.floor
                result["act2_victory"] |= (value.act or 0) >= 3
                result["act3_victory"] |= (value.act or 0) >= 4
                boss = (value.act, value.floor)
                if value.room_phase == "COMBAT" and value.visible_boss not in ("NO_BOSS", "UNKNOWN_BOSS"):
                    # The map's last room is the actual boss, never just any fight
                    # sharing the same visible-boss context.
                    if any(m.id in ("Champ", "TheCollector", "The Collector", "BronzeAutomaton", "Bronze Automaton",
                        "SlimeBoss", "Hexaghost", "TheGuardian", "TimeEater", "AwakenedOne", "Donu", "Deca", "CorruptHeart") for m in value.monsters) and boss not in seen:
                        seen.add(boss)
                        result["boss_entries"].append({"act": value.act, "floor": value.floor,
                            "boss": value.visible_boss, "hp": value.player.current_hp,
                            "max_hp": value.player.max_hp, "gold": value.player.gold,
                            "profile": deck_profile(value).as_dict(), "deck": [c.reference for c in value.deck]})
            if value.screen_type == "GAME_OVER" and value.game_over_reason in ("victory", "defeat"):
                result.update(terminal_known=True, victory=value.game_over_reason == "victory",
                    terminal_floor=value.floor, terminal_act=value.act,
                    heart_victory=value.game_over_reason == "victory" and value.act == 4)
                result["act3_victory"] |= result["victory"]
                break
            if pending is not None:
                before, action, at = pending
                if _is_action_effective(before, value, action):
                    pending = None
                elif time.monotonic() - at < 15:
                    time.sleep(.25)
                    continue
                else:
                    pending = None
            if is_lifecycle_state(value):
                action = get_lifecycle_action(value, character=args.character, ascension=20, auto_restart=True)
            elif value.room_phase == "COMBAT":
                # The staged profile enables the in-game JNI auto-start hook.
                time.sleep(.5)
                continue
            elif empty_reward_cancel(value):
                from .models import GameAction
                action = GameAction(type=ActionType.CANCEL)
                jsonl(args.output / "navigation.jsonl", {"floor": value.floor,
                    "screen": value.screen_type, "action": "cancel", "reason": "only_legal_return_button"})
            elif value.screen_type != "NONE" and (value.choice_list or value.can_proceed):
                if value.screen_type == "MAP":
                    baseline, candidate = RoutePlanner().choose(value), ResourceRoutePlanner().choose(value)
                    jsonl(args.output / "maps.jsonl", {"floor": value.floor, "act": value.act,
                        "state": value.model_dump(mode="json"), "baseline": asdict(baseline), "candidate": asdict(candidate)})
                action = agent.choose_action(value)
            else:
                time.sleep(.25)
                continue
            if action and action.type != ActionType.WAIT:
                submitted, _, message = client.submit_action(action)
                if not submitted:
                    raise RuntimeError("action submission failed: " + message)
                jsonl(args.output / "actions.jsonl", {"floor": value.floor, "screen": value.screen_type,
                    "action": action.model_dump(mode="json")})
                if value.screen_type == "MAP":
                    tracker.begin(value, getattr(agent, "_pending_route_forecast", None))
                pending = value.model_copy(deep=True), action, time.monotonic()
            time.sleep(.25)
        if not result["terminal_known"]:
            result["error"] = "whole-run timeout"
    except Exception as error:
        result["error"] = str(error)
    result["elapsed_seconds"] = time.monotonic() - started
    write_json(args.output / "result.json", result)
    print(json.dumps(result, ensure_ascii=True), flush=True)
    return 0 if result["terminal_known"] else 1


def empty_reward_cancel(value):
    return (value.screen_type == "COMBAT_REWARD" and not value.choice_list
            and not value.can_proceed and value.can_cancel and not value.reward_choices)


def read_rows(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()] if path.exists() else []


def search_evidence(directory):
    records = read_rows(directory / "search.jsonl")
    requests = [row for row in records if row["kind"] == "request"]
    # STATUS_UPDATE and streamed prefixes repeat cumulative metrics. Count only
    # the last metrics snapshot of each actual request, not every progress frame.
    metrics = []
    current = None
    for row in records:
        if row["kind"] == "request":
            if current is not None:
                metrics.append(current)
            current = None
        elif row.get("metrics"):
            current = row["metrics"]
    if current is not None:
        metrics.append(current)
    reasons = {}
    for row in metrics:
        reason = row.get("search_stop_reason", row.get("stop_reason", "UNKNOWN"))
        reasons[reason] = reasons.get(reason, 0) + 1
    return {"budget_verified": bool(requests) and not any(r["kind"] == "error" for r in records)
            and all(r.get("max_expansions") == 15000 and r.get("timeout_ms") == 30000
                    and r.get("search_profile") == "BALANCED" for r in requests),
            "requests": len(requests), "responses_with_metrics": len(metrics),
            "expanded_nodes": sum(r.get("expanded_nodes", 0) for r in metrics), "stop_reasons": reasons}


def prediction_summary(directory):
    rows = [r for r in read_rows(directory / "prediction-errors.jsonl") if r.get("status") == "matched"]
    return {"rooms": len(rows), **{field + "_mae": sum(abs(r[field + "_error"]) for r in rows) / len(rows)
            if rows else None for field in ("hp", "gold")},
            "camp_actions": [r for r in rows if r["symbol"] == "R"]}


def first_divergence(output, seed):
    from selectcard.policy.contracts import observable_state
    baseline = read_rows(output / (seed + "-planner") / "maps.jsonl")
    candidate = read_rows(output / (seed + "-resource_v2") / "maps.jsonl")
    for left, right in zip(baseline, candidate):
        same_state = digest(observable_state(left["state"])) == digest(observable_state(right["state"]))
        a, b = left["baseline"], right["candidate"]
        if not same_state or a["choice_index"] != b["choice_index"]:
            def selected(decision):
                return next((c for c in decision["candidates"] if c["choice_index"] == decision["choice_index"]), None)
            return {"floor": left["floor"], "act": left["act"], "same_public_state": same_state,
                    "baseline": selected(a), "candidate": selected(b),
                    "kind": "route_choice" if same_state else "state_diverged_before_route_choice"}
    return None


def compare(output):
    manifest_path = output / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    original_driver = (manifest.get("driver_revisions") or [{}])[0].get("sha256")
    rows = []
    for item in sequence():
        path = output / (item["seed"] + "-" + item["policy"]) / "result.json"
        if path.exists():
            rows.append(json.loads(path.read_text(encoding="utf-8")))
    pairs = []
    for role, seeds in ROLES.items():
        for seed in seeds:
            pair = {r["policy"]: r for r in rows if r["character"] == role and r["seed"] == seed}
            before, after = pair.get("planner"), pair.get("resource_v2")
            valid = bool(before and after and before.get("terminal_known") and after.get("terminal_known")
                and before.get("driver_sha256", original_driver) == after.get("driver_sha256", original_driver)
                and before.get("search_evidence", {}).get("budget_verified")
                and after.get("search_evidence", {}).get("budget_verified")
                and all(before.get(k) == after.get(k) and before.get(k) is not None for k in
                        ("start_fingerprint", "runtime_fingerprint", "combat_budget")))
            entry = {"character": role, "seed": seed, "valid_pair": valid,
                     "baseline": before, "candidate": after}
            if valid:
                entry["first_divergence"] = first_divergence(output, seed)
                entry["floor_delta"] = after["terminal_floor"] - before["terminal_floor"]
                for field in ("act2_victory", "act3_victory", "heart_victory"):
                    entry[field + "_delta"] = int(after[field]) - int(before[field])
            pairs.append(entry)
    result = {"exploratory": True, "promotable": False, "expected_pairs": 6,
              "driver_revisions": manifest.get("driver_revisions", []),
              "valid_pairs": sum(p["valid_pair"] for p in pairs), "pairs": pairs,
              "completed_runs": sum(r.get("terminal_known", False) for r in rows)}
    write_json(output / "comparison.json", result)
    return result


def archive_incomplete_pairs(output):
    affected = set()
    for item in sequence():
        directory = output / (item["seed"] + "-" + item["policy"])
        if directory.exists():
            result = directory / "result.json"
            if not result.exists() or not json.loads(result.read_text(encoding="utf-8")).get("terminal_known"):
                affected.add(item["seed"])
    for seed in sorted(affected):
        for policy in ("planner", "resource_v2"):
            source = output / (seed + "-" + policy)
            if not source.exists():
                continue
            destination = output / "interrupted" / source.name
            suffix = 1
            while destination.exists():
                destination = output / "interrupted" / (source.name + "-" + str(suffix))
                suffix += 1
            # Verify resolved targets before moving whole runtime directories.
            if source.resolve().parent != output.resolve() or not destination.resolve().is_relative_to(output.resolve()):
                raise ValueError("archive target leaves the explicitly named evaluation directory")
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(source), str(destination))
    return sorted(affected)


def run(args):
    manifest = prepare(args)
    if args.retry_incomplete_pairs:
        print("RETRY_PAIRS " + json.dumps(archive_incomplete_pairs(args.output)), flush=True)
    for item in manifest["sequence"]:
        directory = args.output / (item["seed"] + "-" + item["policy"])
        result_path = directory / "result.json"
        if result_path.exists():
            continue
        directory.mkdir(parents=True, exist_ok=True)
        stage(args, directory / "client")
        env = os.environ.copy()
        java = args.game_dir / "jre" / "bin" / "java.exe"
        common = ["-Xms256m", "-Xmx2048m", "-jar", "ModTheSpire.jar", "--profile", "RouteBenchmark",
                  "--skip-launcher", "--skip-intro", "--jre51"]
        children, handles = [], []
        try:
            runtime = directory / "client"
            current_env = dict(env, LOCALAPPDATA=str(runtime / "appdata"), APPDATA=str(runtime / "appdata"))
            flags = ["-Dcommunicationmod.port=15000", "-Dbattleai.clientBudgetMs=30000",
                     "-Dbattleai.trace="+str((directory / "search.jsonl").resolve()), "-Dbattleai.runSeed="+item["seed"]]
            handle = (directory / "client.log").open("w", encoding="utf-8")
            handles.append(handle)
            children.append(subprocess.Popen([str(java), *flags, *common], cwd=runtime, stdout=handle,
                stderr=subprocess.STDOUT, env=current_env, creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0)))
            print("RUN " + json.dumps(item), flush=True)
            handle = (directory / "agent.log").open("w", encoding="utf-8")
            handles.append(handle)
            agent = subprocess.Popen([sys.executable, "-u", "-m", "sts_ai_framework.route_benchmark", "--single",
                "--output", str(directory), "--manifest", str(args.output / "manifest.json"),
                "--character", item["character"], "--seed", item["seed"], "--policy", item["policy"],
                "--run-timeout", str(args.run_timeout)], cwd=args.repo, stdout=handle,
                stderr=subprocess.STDOUT, env=dict(env, PYTHONIOENCODING="utf-8"),
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            children.append(agent)
            agent.wait(timeout=args.run_timeout + 300)
            if not result_path.exists():
                raise RuntimeError("agent exited without result; see agent.log")
        except Exception as error:
            if not result_path.exists():
                write_json(result_path, {**item, "terminal_known": False, "error": str(error)})
        finally:
            for child in reversed(children):
                if child.poll() is None:
                    child.terminate()
                    try:
                        child.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=10)
            for handle in handles:
                handle.close()
        result = json.loads(result_path.read_text(encoding="utf-8"))
        result["search_evidence"] = search_evidence(directory)
        result["prediction_error"] = prediction_summary(directory)
        write_json(result_path, result)
        summary = compare(args.output)
        print("PROGRESS " + json.dumps({k: summary[k] for k in ("valid_pairs", "completed_runs")}), flush=True)
        if result.get("error", "").startswith(("deployed bridge", "actual character/ascension", "floor-0 seed")):
            raise RuntimeError("shared preflight failed; see " + str(result_path))
    return compare(args.output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--game-dir", type=Path, default=Path("D:/Program Files/Slay the Spire"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--compare-only", action="store_true")
    parser.add_argument("--resume-navigation", action="store_true",
                        help="Accept an explicitly reviewed driver-only navigation revision; freeze all policy/runtime sources")
    parser.add_argument("--retry-incomplete-pairs", action="store_true",
                        help="Preserve incomplete attempts and rerun both arms of their seeds")
    parser.add_argument("--single", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--policy", choices=("planner", "resource_v2"))
    parser.add_argument("--character", choices=tuple(ROLES))
    parser.add_argument("--seed")
    parser.add_argument("--run-timeout", type=int, default=10800)
    args = parser.parse_args()
    args.repo, args.output = args.repo.resolve(), args.output.resolve()
    if args.single:
        return single(args)
    args.output.mkdir(parents=True, exist_ok=True)
    if args.compare_only:
        print(json.dumps(compare(args.output), ensure_ascii=True), flush=True)
    elif args.prepare_only:
        prepare(args)
        print("Prepared frozen manifest", flush=True)
    else:
        print(json.dumps(run(args), ensure_ascii=True), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
