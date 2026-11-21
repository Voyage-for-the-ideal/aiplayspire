"""Frozen A10 corpus helpers and offline readers of historical reports (no Java search entry)."""
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
from battle_engine.profiles import benchmark_budget
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
        holdout = manifest.get("holdout_per_cell", 20)
        if type(holdout) is not int or holdout < 10: raise ValueError("Need at least 10 holdouts per cell")
        for split, count in (("pilot", 3), ("holdout", holdout)):
            if any(counts[split, role, room] != count for role in ROLES for room in ROOMS):
                raise ValueError(f"{split}: need exactly {count} snapshots per role and room")
    return rows


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


def summarize(pairs, *, repetitions=5000):
    by_role = defaultdict(lambda: defaultdict(list))
    counters = Counter()
    won_hp = []
    won_potions = []
    won_max_hp = []
    won_gold = []
    invalid_cpp = 0
    invalid_java = 0
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
        if j["outcome"] == "incomplete" and j.get("reason") not in ("cumulative_search_budget", "action_limit"):
            invalid_java += 1
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
        "java_invalid": invalid_java,
        "common_wins":len(won_hp), "common_wins_delta_hp_lost":statistics.mean(won_hp) if won_hp else None,
        "common_wins_delta_potions":statistics.mean(won_potions) if won_potions else None,
        "common_wins_delta_max_hp":statistics.mean(won_max_hp) if won_max_hp else None,
        "common_wins_delta_gold":statistics.mean(won_gold) if won_gold else None,
        "passed": bool(interval and interval[0]>0 and len(per_role)==len(ROLES)
                       and min(per_role.values())>=-.05 and invalid_cpp==0 and invalid_java==0),
    }
