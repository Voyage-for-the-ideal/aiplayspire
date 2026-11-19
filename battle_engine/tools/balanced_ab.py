"""Paired A10 combat evaluation against the original Java BALANCED executor."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import copy
import hashlib
import json
import math
from pathlib import Path
import random
import socket
import statistics
import subprocess
import shutil
import time
from zipfile import ZipFile

from battle_engine import protocol
from battle_engine.profiles import deployment_budget
from battle_engine.state import load, normalize
from battle_engine.tools.acceptance import card_pools, replay, transition_matches
from battle_engine.worker import Worker

ROLES = ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")
ROOMS = ("MONSTER", "ELITE", "BOSS")
TIERS = ("deployment", "complete")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, obj):
    with path.open("x", encoding="utf-8") as output:
        json.dump(obj, output, indent=2, ensure_ascii=False)
        output.write("\n")


def case_data(path):
    state = load(path)
    role = state["player_state"]["chosen_class_name"]
    current_room = state["cur_map_node_state"]
    room = current_room["room_type"]
    rng = state["rng_state"]
    seed = rng.get("seed", state.get("seed"))
    if (state.get("ascension_level"), state.get("turn")) != (10, 1) or state.get("is_screen_up") or (
        role not in ROLES or room not in ROOMS or type(seed) is not int
        or current_room.get("phase_name")!="COMBAT" or current_room.get("is_battle_over")
        or state.get("cards_played_this_turn")
    ):
        raise ValueError(f"Expected first-turn A10 combat: {path}")
    return role, room, seed


def manifest_cases(path, expected=None):
    manifest = json.loads(path.read_text(encoding="utf-8-sig"))
    rows = []
    identities = set()
    seed_sets = defaultdict(set)
    counts = Counter()
    for row in manifest["cases"]:
        source = (path.parent / row["path"]).resolve(strict=True)
        sha = digest(source)
        if sha != row["sha256"]:
            raise ValueError(f"Snapshot changed: {source}")
        role, room, seed = case_data(source)
        split = row["split"]
        if split not in ("pilot", "holdout") or (role, room, seed) in identities or any(
            seed in seeds for label, seeds in seed_sets.items() if label != split
        ):
            raise ValueError(f"Duplicate encounter or overlapping splits: {source}")
        if (role, room, seed) != (row["role"], row["room"], row["seed"]):
            raise ValueError(f"Manifest identity mismatch: {source}")
        identities.add((role, room, seed))
        seed_sets[split].add(seed)
        counts[split, role, room] += 1
        rows.append(dict(path=source, role=role, room=room, seed=seed, split=split, sha256=sha))
    if expected == "formal":
        for split, count in (("pilot", 3), ("holdout", 20)):
            if any(counts[split, role, room] != count for role in ROLES for room in ROOMS):
                raise ValueError(f"{split}: need exactly {count} snapshots per role and room")
    return rows


def request_java(port, state, directory, cap, timeout):
    # AiServer writes its end snapshot beside the requested input, not client_cwd.
    isolated = directory / "start.json"
    shutil.copyfile(state, isolated)
    target = directory / "commands.json"
    request = dict(fileName=str(isolated), client_cwd=str(directory), command_file=str(target), end_suffix="/search-end.json",
                   search_profile="BALANCED", max_expansions=cap, timeout_ms=timeout)
    deadline = time.monotonic() + timeout / 1000 + 30
    final = None
    with socket.create_connection(("127.0.0.1", port), timeout=5) as conn:
        protocol.send(conn, json.dumps(request))
        while True:
            conn.settimeout(max(.001, deadline - time.monotonic()))
            msg = protocol.receive(conn)
            if msg == "DONE":
                break
            reply = json.loads(msg)
            if reply.get("type") == "ERROR":
                raise ValueError(reply)
            if reply.get("type") == "COMMAND_LIST":
                final = reply
    if final is None:
        raise ValueError("Java search did not return COMMAND_LIST")
    if "command_path" in final:
        path = Path(final["command_path"]).resolve()
        if path != target:
            raise ValueError("Unexpected Java plan path")
        final = json.loads(path.read_text(encoding="utf-8"))
    return final


def terminal(state):
    hp = state["player_state"]["creature"]["current_health"]
    if hp <= 0:
        return "loss"
    room = state["cur_map_node_state"]
    monsters = room.get("monster_data")
    survivors_clear = isinstance(monsters, list) and all(
        m.get("escaped") is True or (
            m["creature"].get("is_dead") is True and m["creature"].get("current_health", 1) <= 0
            and not m["creature"].get("half_dead")
        ) for m in monsters
    )
    # The original engine marks the battle over and settles relics before its
    # reward transition changes COMBAT to COMPLETE.
    if room.get("is_battle_over") is True and room.get("phase_name") in ("COMBAT", "COMPLETE") and survivors_clear:
        return "win"
    return None


def permanent_resources(state):
    player = state["player_state"]
    return player["creature"]["max_health"], player.get("gold", 0)


def replay_snapshot_key(state, preserved_ids=None):
    """Normalize only observed representation differences for index-based replay."""
    snapshot=copy.deepcopy(state)
    for potion in snapshot["player_state"].get("potions",[]):
        if potion.get("id")=="Potion Slot":
            # Search keeps the previous slot ordinal on an empty placeholder;
            # the real executor saves -1. List position and all live slots stay.
            potion.pop("slot",None)
    for row in snapshot["cur_map_node_state"].get("monster_data",[]):
        creature=row["creature"]
        # Monster bobbing/animation can move and resize vertical hitboxes
        # independently of combat. Do not discard flags, intents or RNG.
        for key in ("draw_y","hb_h"):
            creature.pop(key,None)
        for hitbox in (creature.get("hb"),creature.get("health_hb"),row.get("intent_hb")):
            if isinstance(hitbox,dict):
                for key in ("c_y","y","height"):
                    hitbox.pop(key,None)
    if preserved_ids is not None:
        aliases={}
        for uuid in combat_cards(state):
            if uuid not in preserved_ids:
                aliases.setdefault(uuid, f"generated-card:{len(aliases)}")

        def alias(value):
            if isinstance(value,dict):
                return {k:alias(v) for k,v in value.items()}
            if isinstance(value,list):
                return [alias(v) for v in value]
            return aliases.get(value,value) if isinstance(value,str) else value

        return alias(snapshot)
    return snapshot


def combat_cards(state):
    player=state["player_state"]
    return [card["uuid"] for pile in ("hand","draw_pile","discard_pile","exhaust_pile","limbo")
            for card in player.get(pile,[]) or [] if isinstance(card,dict) and isinstance(card.get("uuid"),str)]


def replay_snapshots_match(start, expected, actual):
    # New cards generated by replay get different UUIDs. Keep first-source IDs
    # exact and rewrite new IDs by ordered pile location, including references.
    preserved=set()

    def collect(value):
        if isinstance(value,dict):
            if "card_id" in value and isinstance(value.get("uuid"),str):
                preserved.add(value["uuid"])
            for item in value.values():
                collect(item)
        elif isinstance(value,list):
            for item in value:
                collect(item)

    collect(start)
    return replay_snapshot_key(expected,preserved)==replay_snapshot_key(actual,preserved)


def execute_case(port, worker, row, tier, side, workdir, *, policy="layered-v1", tactical=True):
    directory = workdir.resolve()
    directory.mkdir(parents=True)
    state_path = row["path"]
    state = load(state_path)
    initial_hp = state["player_state"]["creature"]["current_health"]
    initial_max_hp, initial_gold = permanent_resources(state)
    cap, timeout = (15000, 90000) if tier == "complete" else deployment_budget(row["room"])
    remaining_nodes, remaining_ms = 20 * cap, 20 * timeout
    result = {"outcome": "incomplete", "steps": [], "allocated": [cap, timeout], "potions_used": 0}
    start = time.monotonic()
    try:
        for attempt in range(1000):
            if (outcome := terminal(state)) is not None:
                max_hp, gold = permanent_resources(state)
                result.update(outcome=outcome, hp_lost=initial_hp-state["player_state"]["creature"]["current_health"],
                              max_hp_gained=max_hp-initial_max_hp, gold_gained=gold-initial_gold)
                break
            nodes, ms = min(cap, remaining_nodes), min(timeout, remaining_ms)
            if min(nodes, ms) <= 0:
                result["reason"] = "cumulative_search_budget"
                break
            stepdir = directory / f"{attempt:04d}"
            stepdir.mkdir()
            start_step = time.monotonic()
            if side == "java":
                reply = request_java(port, state_path, stepdir, nodes, ms)
                replay_source = stepdir / "start.json"  # resolve the plan's relative guard snapshots here
                m = reply["metrics"]
                spent_ms = max(1, m["elapsed_ms"])
            else:
                replay_source = state_path
                snap = normalize(state)
                snap["_combat_card_pools"] = card_pools(port, state_path)
                reply = worker.execute(dict(snapshot=snap, search_profile="BALANCED" if tier=="complete" else "FAST",
                                            search_policy=policy, tactical_ordering=tactical, max_expansions=nodes,
                                            timeout_ms=ms), ms)
                if not reply.get("supported"):
                    raise ValueError(f"Native unsupported: {reply.get('reason')}")
                m = reply["metrics"]
                spent_ms = max(1, math.ceil(m["planner_us"] / 1000)) if policy=="layered-v1" else max(1,m["search_ms"])
            spent_nodes = m["expanded_nodes"]
            if type(spent_nodes) is not int or not 0 <= spent_nodes <= nodes:
                raise ValueError("Invalid expansion accounting")
            remaining_nodes -= spent_nodes
            remaining_ms -= spent_ms
            commands = reply.get("commands")
            if not commands:
                raise ValueError("No executable search result")
            step = dict(requested=[nodes, ms], used=[spent_nodes, spent_ms], metrics=m)
            result["steps"].append(step)
            next_path = stepdir / "after.json"
            write(stepdir / "search-reply.json", reply)
            actual = replay(port, replay_source, commands, next_path)
            write(stepdir / "replay-reply.json", actual)
            next_state = load(next_path)
            if side == "cpp" and reply.get("expected_after") is not None:
                if not transition_matches(reply, actual, next_path):
                    raise ValueError("C++ prediction differs from original engine")
            elif side == "cpp" and terminal(next_state) is None:
                raise ValueError("Unverified native boundary")
            if side == "cpp" and terminal(next_state) is not None and (
                reply.get("predicted_outcome") != (1 if terminal(next_state) == "win" else 2)
            ):
                raise ValueError("C++ terminal prediction differs from original engine")
            if side == "java" and reply.get("final_state_key") and actual.get("final_state_key") != reply["final_state_key"]:
                step["search_final_state_key"] = reply["final_state_key"]
                step["replay_final_state_key"] = actual.get("final_state_key")
                expected=load(stepdir/"search-end.json")
                if not replay_snapshots_match(state,expected,next_state):
                    raise ValueError("Java search and replay disagree")
                step["replay_equivalence"]="normalized_full_snapshot"
            if digest(next_path) == digest(state_path):
                raise ValueError("Search did not advance combat")
            result["potions_used"] += sum(
                json.loads(entry.get("command","{}")).get("type") == "POTION"
                for entry in commands if entry is not None
            )
            step["elapsed_ms"] = round((time.monotonic()-start_step)*1000, 2)
            state_path, state = next_path, next_state
        else:
            result["reason"] = "action_limit"
    except (ValueError, RuntimeError, TimeoutError, OSError, KeyError, EOFError, subprocess.TimeoutExpired) as error:
        result["reason"] = str(error)
    result["elapsed_s"] = round(time.monotonic() - start, 3)
    return result


def summarize(pairs, *, repetitions=5000):
    by_role = defaultdict(lambda: defaultdict(list))
    counters = Counter()
    won_hp = []
    won_potions = []
    won_max_hp = []
    won_gold = []
    invalid_cpp = 0
    for row in pairs:
        c, j = row["cpp"], row["java"]
        counters["cpp", c["outcome"]] += 1
        counters["java", j["outcome"]] += 1
        if c["outcome"] == "win" and j["outcome"] == "win":
            won_hp.append(c["hp_lost"] - j["hp_lost"])
            won_potions.append(c["potions_used"] - j["potions_used"])
            won_max_hp.append(c.get("max_hp_gained",0) - j.get("max_hp_gained",0))
            won_gold.append(c.get("gold_gained",0) - j.get("gold_gained",0))
        if c["outcome"] == "incomplete" and c.get("reason") not in ("cumulative_search_budget", "action_limit"):
            invalid_cpp += 1
        # Worst case for C++: its incomplete fights lose; Java's win.
        delta = int(c["outcome"] == "win") - int(j["outcome"] != "loss")
        by_role[row["role"]][row["seed"]].append(delta)
    blocks = {role: [(sum(block),len(block)) for block in seeds.values()] for role,seeds in by_role.items()}
    per_role = {role: sum(a for a,b in group)/sum(b for a,b in group) for role,group in blocks.items()}
    interval = None
    if pairs:
        scores = []
        rng = random.Random(42)
        for _ in range(repetitions):
            rates = []
            for blocks_for_role in blocks.values():
                selected = rng.choices(blocks_for_role, k=len(blocks_for_role))
                rates.append(sum(a for a,b in selected)/sum(b for a,b in selected))
            scores.append(statistics.mean(rates))
        scores.sort()
        interval = [scores[int((len(scores)-1)*p)] for p in (.025,.975)]
    return {
        "pairs": len(pairs), "wins_losses_incomplete": {f"{s}_{k}":n for (s,k),n in counters.items()},
        "conservative_delta": statistics.mean(per_role.values()) if per_role else None,
        "conservative_delta_by_role": per_role, "conservative_ci95": interval, "cpp_invalid": invalid_cpp,
        "common_wins":len(won_hp), "common_wins_delta_hp_lost":statistics.mean(won_hp) if won_hp else None,
        "common_wins_delta_potions":statistics.mean(won_potions) if won_potions else None,
        "common_wins_delta_max_hp":statistics.mean(won_max_hp) if won_max_hp else None,
        "common_wins_delta_gold":statistics.mean(won_gold) if won_gold else None,
        "passed": bool(interval and interval[0]>0 and len(per_role)==len(ROLES)
                       and min(per_role.values())>=-.05 and invalid_cpp==0),
    }


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--worker", type=Path, required=True)
    parser.add_argument("--oracle-port", type=int, required=True)
    parser.add_argument("--java-jar", type=Path, required=True, help="BattleAiMod JAR of the running test oracle")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--split", choices=("pilot","formal","smoke"), default="pilot")
    parser.add_argument("--pilot-arm", choices=("legacy","depth","combined"), default="combined")
    args=parser.parse_args(argv)
    if args.split=="formal" and args.pilot_arm!="combined":
        parser.error("Formal run requires the combined candidate")
    cases=manifest_cases(args.manifest,args.split)
    cases=[r for r in cases if r["split"]==("holdout" if args.split=="formal" else "pilot")]
    if not cases:
        parser.error("No matching cases")
    with ZipFile(args.java_jar) as mod:
        java_version = json.loads(mod.read("ModTheSpire.json"))["version"]
    if java_version != "1.10.6":
        raise ValueError(f"Expected BattleAiMod 1.10.6, got {java_version}")
    metadata={key:digest(path) for key,path in (("manifest_sha256",args.manifest),
                                               ("worker_sha256",args.worker),("java_sha256",args.java_jar))}
    args.output.mkdir(parents=True)
    write(args.output/"identity.json",metadata)
    reports={}
    worker=Worker(args.worker.resolve())
    try:
        for tier in TIERS:
            pairs=[]
            for i,row in enumerate(cases):
                pair={k:row[k] for k in ("role","room","seed","sha256")}
                for side in (("cpp","java") if i%2==0 else ("java","cpp")):
                    pair[side]=execute_case(args.oracle_port,worker,row,tier,side,args.output/tier/str(i)/side,
                                            policy="legacy" if args.pilot_arm=="legacy" else "layered-v1",
                                            tactical=args.pilot_arm!="depth")
                pairs.append(pair)
                write(args.output/tier/str(i)/"pair.json",pair)
            reports[tier]=summarize(pairs)
    finally:
        worker.close()
    approved=args.split=="formal" and all(r["passed"] for r in reports.values())
    write(args.output/"report.json",dict(identity=metadata,gates=reports,passed=approved))
    print(json.dumps(reports,ensure_ascii=False))
    return 0 if approved or args.split!="formal" else 1


if __name__=="__main__":
    raise SystemExit(main())
