"""Conservative recovery contracts and a vanilla initial-shop generator.

The generator consumes *pre-generation* engine snapshots. Ordinary .run files
do not contain these; having a matching purchased item is never evidence of
unique recovery. No guessed RNG counters or end-of-run deck reconciliation.
"""
import copy
import time
from collections import Counter
from selectcard.src.sts_mapgen import RandomXS128, _f32

SUPPORTED_ENGINE_SHA256 = "cfad868ac8d65a88e71a0bf096fb09f78811e553effe0787c5309a655e081673"


class Unsupported(ValueError):
    pass


class Budget:
    def __init__(self, max_states=10000, seconds=30):
        self.max_states = min(max_states, 10000)
        self.deadline = time.monotonic() + min(seconds, 30)
        self.states = 0

    def tick(self):
        self.states += 1
        if self.states > self.max_states or time.monotonic() > self.deadline:
            raise Unsupported("search_budget_exhausted")


class RNG:
    def __init__(self, value, budget):
        # State words, not counters: random(int), random(float), etc. consume
        # different Java Random operations despite each advancing counter by one.
        self.xs = RandomXS128(seed=int(value[0]), seed1=int(value[1]))
        self.budget = budget

    def integer(self, limit):
        self.budget.tick()
        return self.xs.next_int_bounded(limit + 1)

    def floating(self, low, high):
        self.budget.tick()
        return _f32(_f32(low) + _f32(_f32(_f32(high) - _f32(low)) * self.xs.next_float()))


def jround(value):
    # Actual jar bytecode promotes the float to double before adding 16384.5d.
    return int(_f32(value) + 16384.5) - 16384


def generate_shop(snapshot, budget=None):
    """Exact supported vanilla initial shop; purchases are separately observed.

    No silently approximated mod hooks: snapshot version and preview changes
    are explicit. Pools are exported in real AbstractCard.compareTo order.
    """
    budget = budget or Budget()
    if snapshot.get("format") != "shop-generation-v1":
        raise Unsupported("unsupported_snapshot_format")
    if snapshot.get("engine_sha256") != SUPPORTED_ENGINE_SHA256:
        raise Unsupported("unverified_engine_version")
    if snapshot.get("hooks") != "vanilla":
        raise Unsupported("unverified_generation_hooks")
    s = copy.deepcopy(snapshot)
    rngs = {name: RNG(s["rng"][name], budget) for name in ("card", "merchant", "potion")}
    card, merchant, potion = (rngs[k] for k in ("card", "merchant", "potion"))
    pools = s["cards"]
    owned = set(s["owned_relics"])
    items = []

    def pick_card(rarity, kind):
        order = {"RARE": ("RARE", "UNCOMMON", "COMMON"), "UNCOMMON": ("UNCOMMON", "COMMON"), "COMMON": ("COMMON",)}[rarity]
        for tier in order:
            choices = [c for c in pools[tier] if c["type"] == kind]
            if choices:
                return copy.deepcopy(choices[card.integer(len(choices) - 1)])
        # Vanilla redirects absent common/uncommon powers upward.
        if kind == "POWER":
            for tier in ("UNCOMMON", "RARE"):
                choices = [c for c in pools[tier] if c["type"] == kind]
                if choices:
                    return copy.deepcopy(choices[card.integer(len(choices) - 1)])
        raise Unsupported("empty_card_type_pool")

    for i, kind in enumerate(("ATTACK", "ATTACK", "SKILL", "SKILL", "POWER")):
        for _ in range(10000):
            roll = card.integer(99) + s["card_blizz"]
            rarity = "RARE" if roll < s["rare_chance"] else "UNCOMMON" if roll < s["rare_chance"] + s["uncommon_chance"] else "COMMON"
            c = pick_card(rarity, kind)
            if c["color"] != "COLORLESS" and (i not in (1, 3) or c["id"] != items[-1]["id"]):
                break
        else:
            raise Unsupported("card_reroll_exhausted")
        items.append(dict(c, slot_id=f"colored:{i}", kind="card", on_sale=False, sold_out=False))
    for i, rarity in enumerate(("UNCOMMON", "RARE")):
        choices = [c for c in pools["COLORLESS"] if c["rarity"] == rarity]
        if not choices:
            raise Unsupported("empty_colorless_pool")
        c = copy.deepcopy(choices[card.integer(len(choices) - 1)])
        items.append(dict(c, slot_id=f"colorless:{i}", kind="card", on_sale=False, sold_out=False))
    for c in items:
        price = _f32(c["base_price"] * merchant.floating(.9, 1.1))
        if c["color"] == "COLORLESS":
            price = _f32(price * _f32(1.2))
        c["price"] = int(price)
        c["upgrades"] = c.get("preview_upgrades", 0)
    sale = merchant.integer(4)
    items[sale]["price"] //= 2
    items[sale]["on_sale"] = True

    def relic_key(tier, end=True):
        budget.tick()
        pool = s["relic_pools"][tier]
        if pool:
            key = pool.pop(-1 if end else 0)
        elif tier in ("COMMON", "SHOP"):
            return relic_key("UNCOMMON", False)
        elif tier == "UNCOMMON":
            return relic_key("RARE", False)
        else:
            key = "Circlet"
        if not s["relic_catalog"][key]["can_spawn"]:
            return relic_key(tier)
        return key

    for i in range(3):
        if i == 2:
            tier = "SHOP"
        else:
            roll = merchant.integer(99)
            tier = "COMMON" if roll < 48 else "UNCOMMON" if roll < 82 else "RARE"
        key = relic_key(tier)
        price = jround(_f32(s["relic_catalog"][key]["base_price"] * merchant.floating(.95, 1.05)))
        items.append(dict(slot_id=f"relic:{i}", kind="relic", id=key, price=price, upgrades=0, on_sale=False, sold_out=False))
    for i in range(3):
        roll = potion.integer(99)
        rarity = "COMMON" if roll < s["potion_common"] else "UNCOMMON" if roll < s["potion_common"] + s["potion_uncommon"] else "RARE"
        for _ in range(10000):
            p = s["potions"][potion.integer(len(s["potions"]) - 1)]
            if p["rarity"] == rarity:
                break
        else:
            raise Unsupported("potion_reroll_exhausted")
        price = jround(_f32(p["base_price"] * merchant.floating(.95, 1.05)))
        items.append(dict(slot_id=f"potion:{i}", kind="potion", id=p["id"], price=price, upgrades=0, on_sale=False, sold_out=False))
    purge = s["purge_base"]
    for multiplier, affect_purge in ((1.1 if s["ascension"] >= 16 else 1., False), (.8 if "The Courier" in owned else 1., True), (.5 if "Membership Card" in owned else 1., True)):
        if multiplier == 1.:
            continue
        for item in items:
            item["price"] = jround(_f32(item["price"] * _f32(multiplier)))
        if affect_purge:
            # Vanilla applies each discount to the base, not previous cost.
            purge = jround(_f32(s["purge_base"] * _f32(multiplier)))
    if "Smiling Mask" in owned:
        purge = 50
    keys = ("slot_id", "kind", "id", "price", "upgrades", "on_sale", "sold_out")
    return {"items": [{k: item[k] for k in keys} for item in items], "purge_cost": purge, "purge_available": True}


def apply_purchase(snapshot, stock, slot, budget=None):
    """Reproduce visible stock changes from one observed vanilla transaction.

    Courier colored-card refills use MathUtils' independent, unseeded stream;
    its actual pre-action state is mandatory. This is another reason .run seed
    plus floor cannot certify historical shop recovery.
    """
    budget = budget or Budget()
    s, result = copy.deepcopy(snapshot), copy.deepcopy(stock)
    if s.get("engine_sha256") != SUPPORTED_ENGINE_SHA256:
        raise Unsupported("unverified_engine_version")
    if s.get("format") != "shop-generation-v1" or s.get("hooks") != "vanilla":
        raise Unsupported("unverified_generation_hooks")
    rng = {k: RNG(s["rng"][k], budget) for k in ("card", "merchant", "potion")}
    card, merchant, potion = (rng[k] for k in ("card", "merchant", "potion"))
    owned = set(s["owned_relics"])
    if slot == "purge":
        if not result["purge_available"]:
            return result
        base = s["purge_base"] + 25
        multiplier = (.8 if "The Courier" in owned else 1.) * (.5 if "Membership Card" in owned else 1.)
        result.update(purge_available=False, purge_cost=50 if "Smiling Mask" in owned else jround(_f32(base * _f32(multiplier))))
        return result
    item = next(i for i in result["items"] if i["slot_id"] == slot)
    if item["sold_out"]:
        return result

    def roll_relic_tier():
        value = merchant.integer(99)
        return "COMMON" if value < 48 else "UNCOMMON" if value < 82 else "RARE"

    def pop_relic(tier, end=True):
        budget.tick()
        pool = s["relic_pools"][tier]
        if pool:
            identity = pool.pop(-1 if end else 0)
        elif tier in ("COMMON", "SHOP"):
            return pop_relic("UNCOMMON", False)
        elif tier == "UNCOMMON":
            return pop_relic("RARE", False)
        else:
            identity = "Circlet"
        if not s["relic_catalog"][identity]["can_spawn"]:
            return pop_relic(tier)
        return identity

    def restock_price(base):
        value = jround(_f32(base * merchant.floating(.95, 1.05)))
        for relic, multiplier in (("The Courier", .8), ("Membership Card", .5)):
            if relic in owned:
                value = jround(_f32(value * _f32(multiplier)))
        return value

    if item["kind"] == "relic":
        identity = item["id"]
        owned.add(identity)
        if identity == "Membership Card":
            for row in result["items"]:
                row["price"] = jround(_f32(row["price"] * .5))
            result["purge_cost"] = 50 if "Smiling Mask" in owned else jround(_f32(s["purge_base"] * .5))
        if identity == "Smiling Mask":
            result["purge_cost"] = 50
        eggs = {"Molten Egg 2": "ATTACK", "Toxic Egg 2": "SKILL", "Frozen Egg 2": "POWER"}
        if identity in eggs:
            types = {c["id"]: c["type"] for pool in s["cards"].values() for c in pool}
            for row in result["items"]:
                if row["kind"] == "card" and types[row["id"]] == eggs[identity] and row["upgrades"] == 0:
                    row["upgrades"] = 1
        if "The Courier" not in owned:
            item["sold_out"] = True
        else:
            for _ in range(10000):
                key = pop_relic(roll_relic_tier())
                if key not in ("Old Coin", "Smiling Mask", "MawBank", "The Courier"):
                    break
            else:
                raise Unsupported("courier_relic_reroll_exhausted")
            item.update(id=key, price=restock_price(s["relic_catalog"][key]["base_price"]))
    elif item["kind"] == "potion":
        if "Sozu" in owned or s.get("empty_potion_slots", 0) == 0:
            return result
        if "The Courier" not in owned:
            item["sold_out"] = True
        else:
            value = potion.integer(99)
            rarity = "COMMON" if value < s["potion_common"] else "UNCOMMON" if value < s["potion_common"] + s["potion_uncommon"] else "RARE"
            while True:
                p = s["potions"][potion.integer(len(s["potions"]) - 1)]
                if p["rarity"] == rarity:
                    break
            item.update(id=p["id"], price=restock_price(p["base_price"]))
    elif item["kind"] == "card":
        if "The Courier" not in owned:
            result["items"].remove(item)
            prefix = slot.split(":")[0]
            # Live bridge indices address current list positions after removal.
            for i, row in enumerate(r for r in result["items"] if r["slot_id"].startswith(prefix + ":")):
                row["slot_id"] = f"{prefix}:{i}"
        else:
            if slot.startswith("colorless:"):
                rarity = "RARE" if merchant.floating(0., 1.) < s["colorless_rare_chance"] else "UNCOMMON"
                choices = [c for c in s["cards"]["COLORLESS"] if c["rarity"] == rarity]
                c = choices[card.integer(len(choices) - 1)]
            else:
                if not s["rng"].get("math"):
                    raise Unsupported("missing_unseeded_math_rng")
                math_rng = RNG(s["rng"]["math"], budget)
                if "purchase_sound_rng" not in s:
                    raise Unsupported("missing_audio_background_rng_context")
                if s["purchase_sound_rng"]:
                    # SHOP_PURCHASE's pitch consumes MathUtils RNG before refill.
                    math_rng.floating(-.1, .1)
                types = {c["id"]: c["type"] for pool in s["cards"].values() for c in pool}
                kind = types[item["id"]]
                roll = card.integer(99) + s["card_blizz"]
                rarity = "RARE" if roll < s["rare_chance"] else "UNCOMMON" if roll < s["rare_chance"] + s["uncommon_chance"] else "COMMON"
                tiers = {"RARE": ("RARE", "UNCOMMON"), "UNCOMMON": ("UNCOMMON",), "COMMON": ("COMMON", "UNCOMMON", "RARE")}[rarity]
                choices = []
                for tier in tiers:
                    choices = [c for c in s["cards"][tier] if c["type"] == kind and c["color"] != "COLORLESS"]
                    if choices:
                        break
                if not choices:
                    raise Unsupported("courier_card_pool_empty")
                c = choices[math_rng.integer(len(choices) - 1)]
            price = _f32(c["base_price"] * merchant.floating(.9, 1.1))
            if c["color"] == "COLORLESS":
                price = _f32(price * _f32(1.2))
            for relic, multiplier in (("The Courier", .8), ("Membership Card", .5)):
                if relic in owned:
                    price = _f32(price * _f32(multiplier))
            item.update(id=c["id"], price=int(price), upgrades=c.get("preview_upgrades", 0))
            # OnSaleTag holds the old card object, even if its slot is restocked.
            item["on_sale"] = False
    return result


def recover_historical(run, decision_type, floor):
    """Proof-oriented assessment; deliberately does not read future choices."""
    if any(run.get(k) for k in ("is_daily", "is_trial", "is_endless", "is_beta", "special_seed", "chose_seed")):
        return {"status": "unsupported", "reason": "nonstandard_mode"}
    if run.get("seed_played") is None:
        return {"status": "unsupported", "reason": "missing_seed"}
    if decision_type == "MAP" and isinstance(floor, int) and floor > 0:
        from selectcard.src.map_context import _cached_act_map, forward_consistent_positions
        prefix = (run.get("path_per_floor") or [])[:floor]
        act, symbols = 1, []
        for symbol in prefix:
            if symbol == "B":
                act += 1
                symbols = []
            elif symbol is not None:
                symbols.append(symbol)
        if 1 <= act <= 3 and 0 < len(symbols) <= 15:
            try:
                nodes = _cached_act_map(int(run["seed_played"]), act, int(run.get("ascension_level", 0)))
                positions = forward_consistent_positions(nodes, symbols)[-1]
                # This component audit makes no claim about Wing Boots charges,
                # complete pre-state, or selected action; those remain unproven.
                return {"status": "ambiguous", "reason": "missing_predecision_state_and_wing_charge_proof",
                        "floor": floor, "prefix_only_coordinate_candidates_without_wings": len(positions), "searched_states": 0}
            except (ValueError, KeyError, IndexError):
                return {"status": "unsupported", "reason": "map_prefix_or_version_mismatch", "floor": floor}
    reasons = {
        "SHOP_SCREEN": "missing_rng_states_pools_unlocks_and_purchase_order",
        "MAP": "room_symbols_do_not_uniquely_identify_coordinates",
        "REST": "missing_legal_menu_and_relic_counters",
        "EVENT": "missing_menu_phase_and_predecision_resources",
        "NEOW": "missing_offered_options_and_prior_run_flags",
        "CARD_REWARD": "candidate_ids_present_but_exact_pre_state_and_special_options_unproven",
        "BOSS_REWARD": "candidate_ids_present_but_exact_pre_state_unproven",
    }
    return {"status": "ambiguous", "reason": reasons.get(decision_type, "missing_complete_predecision_trace"),
            "floor": floor, "searched_states": 0}


def validate_fixtures(fixtures):
    rows, characters, actions = [], Counter(), Counter()
    for fixture in fixtures:
        snapshot = fixture["before"]
        if "action" not in fixture:
            characters[snapshot["character"]] += 1
        else:
            actions[fixture["action"].split(":")[0]] += 1
        try:
            actual = (apply_purchase(snapshot, fixture["stock_before"], fixture["action"]) if "action" in fixture else generate_shop(snapshot))
            match = actual == fixture["expected"]
            rows.append({"case_id": fixture["case_id"], "phase": "purchase" if "action" in fixture else "initial", "status": "match" if match else "mismatch",
                         **({} if match else {"expected": fixture["expected"], "reconstructed": actual})})
        except (Unsupported, KeyError, ValueError) as exc:
            rows.append({"case_id": fixture["case_id"], "phase": "purchase" if "action" in fixture else "initial", "status": "unsupported", "reason": str(exc)})
    complete = sum(characters.values()) >= 200 and all(characters[c] >= 50 for c in ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"))
    purchases_complete = all(actions[k] > 0 for k in ("colored", "colorless", "relic", "potion", "purge"))
    return {"cases": rows, "characters": dict(characters), "purchase_cases": dict(actions), "required_cases_complete": complete,
            "generation_gate_passed": complete and all(r["status"] == "match" for r in rows if r["phase"] == "initial"),
            "purchase_gate_passed": purchases_complete and all(r["status"] == "match" for r in rows if r["phase"] == "purchase"),
            "historical_recovery_gate_passed": False,
            "note": "Full-state equivalence in the tested scope does not establish unique .run recovery."}
