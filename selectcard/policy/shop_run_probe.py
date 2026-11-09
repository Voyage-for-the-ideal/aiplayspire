"""Investigate first Act-I shops from .run prefixes, without target snapshots.

This is an explicitly conditional research probe, never a training importer.
It enumerates six own-card unlock levels and rejects unmodelled prefixes.
Current choices and future fields are read only by the separate validator.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

from .audit import iter_runs
from .reconstruction import Budget, RNG, Unsupported, jround, SUPPORTED_ENGINE_SHA256
from selectcard.src.sts_mapgen import RandomXS128, _f32

ROLES = {"IRONCLAD": "RED", "THE_SILENT": "GREEN", "DEFECT": "BLUE", "WATCHER": "PURPLE"}
SAFE_NEOW = {"TEN_PERCENT_HP_BONUS", "TWENTY_PERCENT_HP_BONUS", "THREE_ENEMY_KILL", "HUNDRED_GOLD",
             "TWO_FIFTY_GOLD", "UPGRADE_CARD", "REMOVE_CARD", "REMOVE_TWO", "TRANSFORM_CARD",
             "TRANSFORM_TWO_CARDS", "THREE_CARDS", "THREE_RARE_CARDS", "ONE_RANDOM_RARE_CARD",
             "RANDOM_COLORLESS", "RANDOM_COLORLESS_2"}
SAFE_EVENTS = {"Liars Game", "Golden Idol", "The Cleric", "Shining Light", "Golden Shrine",
               "Upgrade Shrine", "Bonfire Elementals", "World of Goop", "Golden Wing", "Lab"}


def method_body(source, signature):
    start = source.index("{", source.index(signature))
    depth = 1
    end = start + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start + 1:end - 1]


def java_hash_order(keys):
    """JDK8 HashMap iteration for these static, non-treeified vanilla catalogs."""
    table, size = [[] for _ in range(16)], 0
    for key in keys:
        h = 0
        for char in key:
            h = (31 * h + ord(char)) & 0xffffffff
        h ^= h >> 16
        if key in table[h & (len(table) - 1)]:
            continue
        table[h & (len(table) - 1)].append(key)
        size += 1
        if len(table[h & (len(table) - 1)]) >= 8:
            raise Unsupported("catalog_hash_tree_or_early_resize_unmodelled")
        if size > len(table) * .75:
            old, table = table, [[] for _ in range(len(table) * 2)]
            for bucket in old:
                for old_key in bucket:
                    k = 0
                    for char in old_key:
                        k = (31 * k + ord(char)) & 0xffffffff
                    k ^= k >> 16
                    table[k & (len(table) - 1)].append(old_key)
    return [key for bucket in table for key in bucket]


class Catalog:
    def __init__(self, root, game_jar=None):
        self.root = Path(root)
        cards = {}
        for path in (self.root / "cardcrawl/cards").rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            identity = re.search(r'(?:String\s+(?:ID|CARD_ID)\s*=|super\s*\()\s*"([^"]+)"', source)
            fields = [re.search(r'\bCard' + kind + r'\.([A-Z_]+)', source) for kind in ("Type", "Color", "Rarity")]
            if identity and all(fields):
                cards[path.stem] = dict(id=identity[1], type=fields[0][1], color=fields[1][1], rarity=fields[2][1])
        library = (self.root / "cardcrawl/helpers/CardLibrary.java").read_text(encoding="utf-8")
        names = []
        for function in ("addRedCards", "addGreenCards", "addBlueCards", "addPurpleCards", "addColorlessCards", "addCurseCards"):
            names += re.findall(r'add\(\(AbstractCard\) new (\w+)\(\)\)', method_body(library, "void " + function + "()"))
        missing = sorted(set(names) - cards.keys())
        if missing:
            raise Unsupported("unparsed_card_classes:" + ",".join(missing))
        self.cards = {cards[name]["id"]: cards[name] for name in names}
        self.order = java_hash_order([cards[name]["id"] for name in names])
        helper = (self.root / "cardcrawl/helpers/PotionHelper.java").read_text(encoding="utf-8")
        potion_method = method_body(helper, "ArrayList<String> getPotions(")
        common = potion_method[potion_method.index('retVal.add("Block Potion")'):]
        common_ids = re.findall(r'retVal.add\("([^"]+)"\)', common)
        self.potions = {}
        for path in (self.root / "cardcrawl/potions").glob("*.java"):
            source = path.read_text(encoding="utf-8")
            identity = re.search(r'(?:String\s+(?:POTION_ID|ID)\s*=)\s*"([^"]+)"', source)
            rarity = re.search(r'PotionRarity\.(COMMON|UNCOMMON|RARE)', source)
            if identity and rarity:
                self.potions[identity[1]] = dict(id=identity[1], rarity=rarity[1])
        role_potions = {"IRONCLAD": ["BloodPotion", "ElixirPotion", "HeartOfIron"],
                        "THE_SILENT": ["Poison Potion", "CunningPotion", "GhostInAJar"],
                        "DEFECT": ["FocusPotion", "PotionOfCapacity", "EssenceOfDarkness"],
                        "WATCHER": ["BottledMiracle", "StancePotion", "Ambrosia"]}
        self.potion_pools = {role: [self.potions[key] for key in ids + common_ids] for role, ids in role_potions.items()}

        relics = {}
        for path in (self.root / "cardcrawl/relics").glob("*.java"):
            source = path.read_text(encoding="utf-8")
            identity = re.search(r'super\("([^"]+)"', source)
            tier = re.search(r'\bRelicTier\.([A-Z_]+)', source)
            if identity and tier:
                relics[path.stem] = dict(id=identity[1], tier=tier[1], class_name=path.stem)
        library = (self.root / "cardcrawl/helpers/RelicLibrary.java").read_text(encoding="utf-8")
        groups = {name: [] for name in ("", "Red", "Green", "Blue", "Purple")}
        for group, name in re.findall(r'add(Red|Green|Blue|Purple)?\(new (\w+)\(\)\)', method_body(library, "void initialize()")):
            groups[group].append(relics[name])
        self.relics = {r["id"]: r for values in groups.values() for r in values}
        self.relic_order = {group: java_hash_order([r["id"] for r in values]) for group, values in groups.items()}
        self.unlocks = None if game_jar is None else self.read_unlocks(game_jar)

    def read_unlocks(self, game_jar):
        source = (self.root / "cardcrawl/unlock/UnlockTracker.java").read_text(encoding="utf-8")
        source = method_body(source, "ArrayList<AbstractUnlock> getUnlockBundle(")
        sections = re.split(r'case (IRONCLAD|THE_SILENT|DEFECT|WATCHER):', source)
        bundle_classes = {}
        for i in range(1, len(sections), 2):
            role, section = sections[i:i+2]
            levels = re.split(r'case (\d+):', section)
            bundle_classes[role] = {int(levels[j]) + 1: re.findall(r'new (\w+Unlock)\(', levels[j+1]) for j in range(1, len(levels), 2)}
        names = {name for levels in bundle_classes.values() for classes in levels.values() for name in classes}
        with zipfile.ZipFile(game_jar) as jar:
            qualified = {Path(path).stem: path[:-6].replace('/', '.') for path in jar.namelist()
                         if path.startswith('com/megacrit/cardcrawl/unlock/') and path.endswith('.class') and Path(path).stem in names}
        if set(qualified) != names:
            raise Unsupported("missing_unlock_classes")
        output = subprocess.run(['javap', '-classpath', str(game_jar), '-c', *[qualified[n] for n in sorted(names)]],
                                capture_output=True, text=True, encoding='utf-8', errors='replace', check=True).stdout
        metadata = {}
        for name, body in re.findall(r'Compiled from "(\w+)\.java"(.*?)(?=Compiled from |\Z)', output, flags=re.S):
            kind = re.search(r'UnlockType\.(CARD|RELIC):', body)
            identity = re.search(r'// String ([^\r\n]+)', body)
            if kind and identity:
                metadata[name] = (kind[1], identity[1])
        if set(metadata) != names:
            raise Unsupported("unparsed_unlock_bytecode")
        return {role: {level: [metadata[name] for name in classes] for level, classes in levels.items()}
                for role, levels in bundle_classes.items()}

    def locked(self, role, level, kind):
        if self.unlocks is None:
            raise Unsupported("unlock_metadata_required")
        return {identity for threshold, bundle in self.unlocks[role].items() if threshold > level
                for item_kind, identity in bundle if item_kind == kind}

    def pools(self, role, level=5):
        color = ROLES[role]
        locked = self.locked(role, level, "CARD") if self.unlocks is not None else set()
        result = {rarity: [self.cards[key] for key in self.order if self.cards[key]["color"] == color
                          and self.cards[key]["rarity"] == rarity and key not in locked] for rarity in ("COMMON", "UNCOMMON", "RARE")}
        result["COLORLESS"] = sorted([c for c in self.cards.values() if c["color"] == "COLORLESS"
                                      and c["rarity"] in ("UNCOMMON", "RARE")], key=lambda c: c["id"])
        return result

    def initial_relic_pools(self, seed, role, profile):
        locked = set().union(*(self.locked(r, level, "RELIC") for r, level in profile.items()))
        group = {"IRONCLAD": "Red", "THE_SILENT": "Green", "DEFECT": "Blue", "WATCHER": "Purple"}[role]
        order = self.relic_order[""] + self.relic_order[group]
        rng = RandomXS128(int(seed))
        pools = {}
        for tier in ("COMMON", "UNCOMMON", "RARE", "SHOP", "BOSS"):
            values = [key for key in order if self.relics[key]["tier"] == tier and key not in locked]
            java = JavaRandom(rng.next_long())
            for index in range(len(values), 1, -1):
                other = java.bounded(index)
                values[index-1], values[other] = values[other], values[index-1]
            pools[tier] = values
        return pools


class JavaRandom:
    def __init__(self, seed):
        self.state = (seed ^ 0x5deece66d) & ((1 << 48) - 1)

    def bits(self, count):
        self.state = (self.state * 0x5deece66d + 0xb) & ((1 << 48) - 1)
        return self.state >> (48 - count)

    def bounded(self, bound):
        if bound & (bound - 1) == 0:
            return (bound * self.bits(31)) >> 31
        while True:
            bits = self.bits(31)
            value = bits % bound
            if bits - value + bound - 1 < 1 << 31:
                return value


def new_rng(seed, budget):
    xs = RandomXS128(int(seed))
    return RNG([xs.get_state(0), xs.get_state(1)], budget)


def rarity(roll, rare=3):
    return "RARE" if roll < rare else "UNCOMMON" if roll < rare + 37 else "COMMON"


def reward_cards(card, pools, blizz, budget):
    result = []
    for _ in range(3):
        tier = rarity(card.integer(99) + blizz)
        if tier == "COMMON":
            blizz = max(-40, blizz - 1)
        elif tier == "RARE":
            blizz = 5
        while True:
            budget.tick()
            item = pools[tier][card.integer(len(pools[tier]) - 1)]
            if item not in result:
                result.append(item)
                break
    for item in result:
        if item["rarity"] != "RARE":
            card.floating(0., 1.)  # Act I chance is zero, but the call still occurs.
    return result, blizz


def draw_potion(potion, pool, budget):
    roll = potion.integer(99)
    tier = "COMMON" if roll < 65 else "UNCOMMON" if roll < 90 else "RARE"
    while True:
        budget.tick()
        item = pool[potion.integer(len(pool) - 1)]
        if item["rarity"] == tier:
            return item


def prefix_for_shop(run, floor):
    """Explicit projection: no terminal deck, current choices, RNG or future rows."""
    result = {key: run.get(key) for key in ("seed_played", "character_chosen", "ascension_level", "build_version",
               "neow_bonus", "neow_cost", "is_daily", "is_trial", "is_endless", "is_beta", "special_seed")}
    result["path_per_floor"] = (run.get("path_per_floor") or [])[:floor - 1]
    for key in ("card_choices", "event_choices", "relics_obtained", "potions_obtained", "damage_taken"):
        result[key] = [row for row in (run.get(key) or []) if row.get("floor", -1) < floor]
    for key in ("potions_floor_spawned", "potions_floor_usage"):
        result[key] = [value for value in (run.get(key) or []) if value < floor]
    return result


def probe_initial_shop(prefix, catalog, unlock_level=5, budget=None):
    """Conditional prediction, no access to the original run or validation labels."""
    if prefix.get("build_version") != "2022-12-18":
        raise Unsupported("unverified_build_version")
    if any(prefix.get(key) for key in ("is_daily", "is_trial", "is_endless", "is_beta", "special_seed")):
        raise Unsupported("nonstandard_mode")
    if prefix.get("neow_bonus") not in SAFE_NEOW or prefix.get("neow_cost") not in ("NONE", "NO_GOLD", "TEN_PERCENT_HP_LOSS", "PERCENT_DAMAGE"):
        raise Unsupported("unmodelled_neow_rng_or_drawback")
    path = prefix["path_per_floor"]
    if any(symbol not in ("M", "?", "R") for symbol in path):
        raise Unsupported("prior_shop_elite_treasure_or_act_transition")
    if prefix["relics_obtained"] or any(row.get("relics_obtained") for row in prefix["event_choices"]):
        raise Unsupported("prior_relic_effects_or_pool_changes")
    if prefix["potions_floor_usage"]:
        raise Unsupported("potion_use_type_unknown")
    events = {int(row["floor"]): row for row in prefix["event_choices"]}
    for row in events.values():
        if row.get("event_name") not in SAFE_EVENTS and row.get("event_name") not in ("Living Wall", "Big Fish"):
            raise Unsupported("unmodelled_event:" + str(row.get("event_name")))
    for floor, symbol in enumerate(path, 1):
        if symbol == "?" and floor not in events:
            raise Unsupported("unrecorded_event_or_combat")
    role = prefix["character_chosen"]
    if role not in ROLES:
        raise Unsupported("unknown_role")
    # Transforming into Alchemize could consume potionRng without a potion-use row.
    if role == "THE_SILENT" and prefix["neow_bonus"].startswith("TRANSFORM"):
        raise Unsupported("unobserved_alchemize_possible")
    budget = budget or Budget()
    card, merchant, potion = (new_rng(prefix["seed_played"], budget) for _ in range(3))
    pools = catalog.pools(role, unlock_level)
    blizz, checks, potion_chance = 5, [], 40
    if prefix["neow_bonus"] in ("RANDOM_COLORLESS", "RANDOM_COLORLESS_2"):
        tier = "RARE" if prefix["neow_bonus"] == "RANDOM_COLORLESS_2" else "UNCOMMON"
        pool, offered = [c for c in pools["COLORLESS"] if c["rarity"] == tier], []
        for _ in range(3):
            while True:
                budget.tick()
                item = pool[card.integer(len(pool) - 1)]
                if item not in offered:
                    offered.append(item)
                    break
        rows = [r for r in prefix["card_choices"] if r["floor"] == 0]
        if len(rows) != 1:
            raise Unsupported("missing_neow_colorless_choice")
        row = rows[0]
        recorded = sorted(row["not_picked"] + ([] if row["picked"] == "SKIP" else [row["picked"]]))
        predicted = sorted(c["id"] for c in offered)
        checks.append(dict(floor=0, kind="neow_colorless", recorded=recorded, predicted=predicted, match=recorded == predicted))
    choices = {}
    for row in prefix["card_choices"]:
        if row["floor"] > 0:
            if row["floor"] in choices:
                raise Unsupported("multiple_rewards_on_one_floor")
            choices[row["floor"]] = row
    for floor, symbol in enumerate(path, 1):
        if symbol != "M":
            if events.get(floor, {}).get("event_name") == "Lab":
                for _ in range(2 if int(prefix["ascension_level"] or 0) >= 15 else 3):
                    potion.integer(len(catalog.potion_pools[role]) - 1)
            continue
        offered, blizz = reward_cards(card, pools, blizz, budget)
        if floor in choices:
            row = choices[floor]
            recorded = row["not_picked"] + ([] if row["picked"] in ("SKIP", "Singing Bowl") else [row["picked"]])
            recorded = sorted(re.sub(r'\+\d*$', '', key) for key in recorded)
            predicted = sorted(item["id"] for item in offered)
            checks.append(dict(floor=floor, recorded=recorded, predicted=predicted, match=recorded == predicted))
        # Every fight rolls for a potion even if the player does not take it.
        spawned = potion.integer(99) < potion_chance
        if spawned:
            draw_potion(potion, catalog.potion_pools[role], budget)
        expected_spawn = floor in prefix["potions_floor_spawned"]
        checks.append(dict(floor=floor, kind="potion_spawn", predicted=spawned, recorded=expected_spawn, match=spawned == expected_spawn))
        potion_chance += -10 if spawned else 10
    if not all(row["match"] for row in checks):
        return dict(status="inconsistent_under_assumptions", prefix_checks=checks, searched_states=1)
    items = []
    for i, kind in enumerate(("ATTACK", "ATTACK", "SKILL", "SKILL", "POWER")):
        while True:
            budget.tick()
            tier = rarity(card.integer(99) + blizz, 9)
            tiers = {"RARE": ("RARE", "UNCOMMON", "COMMON"), "UNCOMMON": ("UNCOMMON", "COMMON"), "COMMON": ("COMMON",)}[tier]
            if kind == "POWER":
                tiers = ("RARE", "UNCOMMON") if tier == "RARE" else ("UNCOMMON", "RARE")
            candidates = next((sorted([c for c in pools[t] if c["type"] == kind], key=lambda c: c["id"])
                               for t in tiers if any(c["type"] == kind for c in pools[t])), None)
            if not candidates:
                raise Unsupported("empty_card_type")
            item = candidates[card.integer(len(candidates) - 1)]
            if i not in (1, 3) or item["id"] != items[-1]["id"]:
                items.append(dict(item, slot_id=f"colored:{i}"))
                break
    for i, tier in enumerate(("UNCOMMON", "RARE")):
        candidates = [c for c in pools["COLORLESS"] if c["rarity"] == tier]
        items.append(dict(candidates[card.integer(len(candidates) - 1)], slot_id=f"colorless:{i}"))
    for item in items:
        base = {"COMMON": 50, "UNCOMMON": 75, "RARE": 150}[item["rarity"]]
        price = _f32(base * merchant.floating(.9, 1.1))
        if item["color"] == "COLORLESS":
            price = _f32(price * _f32(1.2))
        item.update(price=int(price), upgrades=0, on_sale=False)
    sale = merchant.integer(4)
    items[sale]["price"] //= 2
    items[sale]["on_sale"] = True
    relic_slots = []
    for i in range(3):
        roll = merchant.integer(99) if i < 2 else None
        tier = ("COMMON" if roll < 48 else "UNCOMMON" if roll < 82 else "RARE") if i < 2 else "SHOP"
        base = {"COMMON": 150, "UNCOMMON": 250, "RARE": 300, "SHOP": 150}[tier]
        relic_slots.append(dict(slot_id=f"relic:{i}", requested_tier=tier, price=jround(_f32(base * merchant.floating(.95, 1.05)))))
    potion_items = []
    for i in range(3):
        item = draw_potion(potion, catalog.potion_pools[role], budget)
        base = {"COMMON": 50, "UNCOMMON": 75, "RARE": 100}[item["rarity"]]
        potion_items.append(dict(item, slot_id=f"potion:{i}", price=jround(_f32(base * merchant.floating(.95, 1.05)))))
    if int(prefix["ascension_level"] or 0) >= 16:
        for item in items + potion_items + relic_slots:
            item["price"] = jround(_f32(item["price"] * _f32(1.1)))
    return dict(status="conditional_partial", assumptions=["vanilla_matching_engine", f"own_card_unlock_level={unlock_level}", "no_unrecorded_reload_or_rng_hooks"],
                cards=items, potions=potion_items, relic_slots=relic_slots, purge_cost=75,
                prefix_checks=checks, missing=["relic_ids_and_account_unlock_profile", "complete_predecision_state"],
                searched_states=1, rng_operations=budget.states, training_eligible=False)


def relic_variants(prefix, catalog, slots, unlock_level):
    """Two legal account hypotheses, independent of current/future purchases."""
    role = prefix["character_chosen"]
    picked = [row["picked"] for row in prefix["card_choices"] if row["picked"] not in ("SKIP", "Singing Bowl")]
    for row in prefix["event_choices"]:
        picked.extend(row.get("cards_obtained", []))
    picked = [catalog.cards[re.sub(r'\+\d*$', '', key)] for key in picked if re.sub(r'\+\d*$', '', key) in catalog.cards]
    # Starter cards are BASIC; bottles require nonbasic cards.
    def can_spawn(key):
        if key in ("The Courier", "MawBank", "Old Coin", "Smiling Mask"):
            return False
        kinds = {"Bottled Flame": "ATTACK", "Bottled Lightning": "SKILL", "Bottled Tornado": "POWER"}
        if key in kinds:
            if prefix["neow_bonus"].startswith("TRANSFORM") or prefix["neow_bonus"] == "ONE_RANDOM_RARE_CARD":
                raise Unsupported("unrecorded_neow_card_affects_bottle_spawn")
            if any(row.get("cards_removed") for row in prefix["event_choices"]):
                raise Unsupported("card_removal_affects_bottle_spawn")
            return any(card["type"] == kinds[key] and card["rarity"] != "BASIC" for card in picked)
        return True  # At this supported early floor, other non-boss conditions all hold.
    variants = []
    for other_level in (0, 5):
        profile = {r: unlock_level if r == role else other_level for r in ROLES}
        pools = catalog.initial_relic_pools(prefix["seed_played"], role, profile)
        items = []
        for slot in slots:
            tier = slot["requested_tier"]
            if not pools[tier]:
                raise Unsupported("relic_pool_exhaustion")
            while True:
                key = pools[tier].pop()
                if can_spawn(key):
                    break
            items.append(dict(slot, id=key))
        variants.append(dict(profile=profile, relics=items))
    return variants


def merchant_terms(prefix):
    """Seed-only merchant-stream terms; no card/relic/potion IDs are assumed."""
    if prefix.get("build_version") != "2022-12-18" or any(prefix.get(k) for k in ("is_daily", "is_trial", "is_endless", "is_beta", "special_seed")):
        raise Unsupported("unverified_version_or_mode")
    owned = {r["key"] for r in prefix["relics_obtained"]}
    owned.update(key for row in prefix["event_choices"] for key in row.get("relics_obtained", []))
    if "The Courier" in owned:
        raise Unsupported("courier_purchase_stream_not_modelled")
    rng = new_rng(prefix["seed_played"], Budget())
    before = prefix["path_per_floor"].count("$")
    for _ in range(before + 1):
        cards = [rng.floating(.9, 1.1) for _ in range(7)]
        sale = rng.integer(4)
        relics = []
        for i in range(3):
            roll = rng.integer(99) if i < 2 else None
            tier = ("COMMON" if roll < 48 else "UNCOMMON" if roll < 82 else "RARE") if i < 2 else "SHOP"
            relics.append(dict(requested_tier=tier, price_factor=rng.floating(.95, 1.05)))
        potions = [rng.floating(.95, 1.05) for _ in range(3)]
    return dict(status="conditional_stream_terms", assumptions=["vanilla_matching_engine", "no_unrecorded_extra_shop_generation",
                "no_courier_before_current_shop"], prior_shops=before, sale_slot=sale, card_price_factors=cards,
                relic_slots=relics, potion_price_factors=potions, training_eligible=False)


def analyze_shop(prefix, catalog):
    budget = Budget()
    try:
        candidates = [probe_initial_shop(prefix, catalog, level, budget) for level in range(6)]
        matched = [(level, value) for level, value in enumerate(candidates) if value["status"] == "conditional_partial"]
        if not matched:
            result = candidates[-1]
        else:
            # Preserve every consistent hypothesis. Never select using held-out purchases.
            result = dict(matched[-1][1])
            result["consistent_unlock_levels"] = [level for level, _ in matched]
            result["card_inventory_variants"] = len({json.dumps(value["cards"], sort_keys=True) for _, value in matched})
            result["display_hypothesis_unlock_level"] = matched[-1][0]
            result["candidate_inventories"] = [dict(unlock_level=level, cards=value["cards"], potions=value["potions"]) for level, value in matched]
            try:
                result["account_relic_hypotheses"] = relic_variants(prefix, catalog, result["relic_slots"], matched[-1][0])
            except Unsupported as exc:
                result["relic_hypotheses_unresolved"] = str(exc)
        result["searched_states"] = 6
        result["training_eligible"] = False
    except (Unsupported, KeyError, ValueError) as exc:
        result = dict(status="unresolved", reason=str(exc), training_eligible=False)
    try:
        result["merchant_stream"] = merchant_terms(prefix)
    except (Unsupported, KeyError, ValueError) as exc:
        result["merchant_stream"] = dict(status="unresolved", reason=str(exc))
    return result


def validate_purchases(run, floor, prediction, catalog):
    """Held-out checks never choose a hypothesis or upgrade recovery status."""
    actual = [key for key, f in zip(run.get("items_purchased", []), run.get("item_purchase_floors", [])) if f == floor]
    checks = []
    for candidate in prediction.get("candidate_inventories", []):
        stock = Counter(item["id"] for key in ("cards", "potions") for item in candidate[key])
        chosen = Counter(re.sub(r'\+\d*$', '', key) for key in actual if re.sub(r'\+\d*$', '', key) in catalog.cards or key in catalog.potions)
        checks.append(dict(unlock_level=candidate["unlock_level"], checks=sum(chosen.values()),
                           missing_items=dict(chosen - stock), match=not bool(chosen - stock)))
    return dict(purchases=actual, candidate_checks=checks, rejected_all_component_hypotheses=bool(checks) and all(not x["match"] for x in checks),
                note="Held-out purchases can reject, but cannot select RNG states or establish full recovery.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--game-jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.input_dir.is_dir() or not args.game_jar.is_file():
        parser.error("input directory and game jar must exist")
    if args.output.exists():
        parser.error("output must be a new path")
    engine_hash = hashlib.sha256(args.game_jar.read_bytes()).hexdigest()
    if engine_hash != SUPPORTED_ENGINE_SHA256:
        parser.error("unverified game jar")
    try:
        catalog = Catalog(Path(__file__).resolve().parents[2], args.game_jar)
    except (Unsupported, FileNotFoundError, subprocess.CalledProcessError) as exc:
        parser.error(str(exc))
    reports, runs = [], list(iter_runs(args.input_dir))
    for run in runs:
        for floor, room in enumerate(run.get("path_per_floor") or [], 1):
            if room != "$":
                continue
            prefix = prefix_for_shop(run, floor)
            prediction = analyze_shop(prefix, catalog)
            reports.append(dict(seed=run["seed_played"], role=run["character_chosen"], floor=floor,
                                prediction=prediction, validation=validate_purchases(run, floor, prediction, catalog)))
    summary = dict(runs=len(runs), shop_visits=len(reports), statuses=dict(Counter(r["prediction"]["status"] for r in reports)),
                   unresolved_reasons=dict(Counter(r["prediction"].get("reason") for r in reports if r["prediction"]["status"] == "unresolved")),
                   merchant_stream_terms=sum(r["prediction"]["merchant_stream"]["status"] == "conditional_stream_terms" for r in reports),
                   held_out_rejections=sum(r["validation"]["rejected_all_component_hypotheses"] for r in reports),
                   distinct_account_relic_hypotheses=sum(len({tuple(x["id"] for x in h["relics"]) for h in r["prediction"].get("account_relic_hypotheses", [])}) > 1 for r in reports))
    report = dict(format="run-only-shop-probe-v1", engine_sha256=engine_hash, summary=summary, cases=reports,
                  note="Conditional component reconstruction; no claim of complete inventory recovery or population coverage.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
