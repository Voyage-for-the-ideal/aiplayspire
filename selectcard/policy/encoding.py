"""Observable set encoding, shared by BC, AWR and live inference."""
from collections import Counter
import torch
from .contracts import CHARACTERS, MAX_ITEMS, MAX_CANDIDATES, SCALARS, SCALES
from .profiles import RUN_PROFILE, LIVE_PROFILE, MIXED_PROFILE, CONTEXT_FIELDS, OPTIONAL_FIELDS, project_example

FEATURES = ("upgrade", "count", "counter", "x", "y", "price", "amount", "probability", "sale", "sold", "legal", "extra",
            "gain_hp", "lose_hp", "gain_max_hp", "lose_max_hp", "gain_gold", "lose_gold", "effect_count", "outcomes_known", "bottled")
WIDTH = len(FEATURES)


def vector(**values):
    return [float(values.get(k, 0) or 0) for k in FEATURES]


def action_parts(action):
    tokens = ["action:" + action["kind"]]
    if action.get("entity_id"):
        tokens.append(action.get("entity_kind", "card") + ":" + action["entity_id"])
    if action.get("purpose"):
        tokens.append("purpose:" + action["purpose"])
    expected = Counter()
    outcomes = action.get("outcomes") or []
    probabilities_known = bool(outcomes) and all(o.get("probability") is not None for o in outcomes)
    for outcome in outcomes:
        for effect in outcome.get("effects") or []:
            tokens.append("effect:" + effect["type"])
            if probabilities_known:
                expected[effect["type"]] += outcome["probability"] * (effect.get("amount") or effect.get("count") or 0)
                expected["effect_count"] += outcome["probability"]
            for key, kind in (("card_id", "card"), ("relic_id", "relic")):
                if effect.get(key):
                    tokens.append(kind + ":" + effect[key])
    return tokens, vector(upgrade=action.get("upgrades", 0) / 10, count=action.get("quantity", 1) / 10,
                         x=action.get("x", 0) / 6, y=action.get("y", 0) / 14,
                         price=action.get("price", 0) / 1000, amount=action.get("amount", 0) / 100,
                         extra=action.get("misc", 0) / 100, bottled=action.get("is_bottled", False),
                         legal=action.get("legal", False), outcomes_known=probabilities_known,
                         **{k: expected[k] / (10 if k == "effect_count" else 1000 if "gold" in k else 100)
                            for k in ("gain_hp", "lose_hp", "gain_max_hp", "lose_max_hp", "gain_gold", "lose_gold", "effect_count")})


def state_parts(state):
    rows = []
    def add(token, **features):
        rows.append((token, vector(**features)))
    add("character:" + state["character"])
    if state.get("input_profile") in (RUN_PROFILE, MIXED_PROFILE):
        fields = CONTEXT_FIELDS + OPTIONAL_FIELDS if state.get("input_profile") == MIXED_PROFILE else CONTEXT_FIELDS
        for field in fields:
            add("context:" + field + (":known" if state["known"].get(field) else ":unknown"))
    add("boss:" + (state.get("visible_boss") or "UNKNOWN"))
    for key in ("ruby", "emerald", "sapphire"):
        value = (state.get("keys") or {}).get(key)
        add("key:" + key, amount=bool(value), extra=value is not None)
    current = state.get("current_map_node") or {}
    nodes = {(n["x"], n["y"]): n for n in state.get("map_nodes") or [] if 0 <= n["x"] < 7 and 0 <= n["y"] < 15}
    reachable, pending = set(), [(current.get("x"), current.get("y"))]
    # Includes first-row choices and currently legal Wing Boots jumps, without
    # inventing any router plan or future RNG-dependent information.
    pending.extend((n["x"], n["y"]) for n in state.get("current_map_choices") or [])
    while pending:
        position = pending.pop()
        if position in reachable:
            continue
        reachable.add(position)
        pending.extend((e["x"], e["y"]) for e in nodes.get(position, {}).get("children", []))
    for y in range(15):
        for x in range(7):
            n = nodes.get((x, y))
            if n is None:
                add("<PAD>")
            else:
                # Encode the three unique outgoing directions as a bit set.
                directions = {max(-1, min(1, e["x"] - x)) for e in n.get("children", [])}
                add("map:" + n.get("symbol", "?"), x=x / 6, y=y / 14,
                    amount=n.get("is_current", False), extra=sum(1 << (d + 1) for d in directions) / 7,
                    sale=n.get("burning", False), legal=(x, y) in reachable)
    for index, history in enumerate((state.get("path_history") or [])[-8:]):
        add("history:" + history.get("resolved_symbol", "?"), counter=index / 8)
    cards = Counter()
    for c in state.get("deck") or []:
        cards[(c["id"], c.get("upgrades", 0), c.get("misc", 0), c.get("is_bottled", False))] += c.get("count", 1)
    item_count = len(cards) + len(state.get("relics") or []) + len(state.get("potions") or [])
    if item_count > MAX_ITEMS:
        raise ValueError("73-item policy cap exceeded")
    for (identity, upgrades, misc, bottled), count in sorted(cards.items()):
        add("card:" + identity, upgrade=upgrades / 10, count=count / 10, extra=misc / 100, bottled=bottled)
    for relic in state.get("relics") or []:
        counter = relic.get("counter")
        add("relic:" + relic["id"], counter=(counter or 0) / 100, extra=counter is not None)
    for potion in state.get("potions") or []:
        add("potion:" + potion["id"], x=potion.get("index", 0) / 5, extra=potion.get("is_empty", False))
    for item in state.get("shop_inventory") or []:
        add(item["kind"] + ":" + item["id"], price=item.get("price", 0) / 1000,
            upgrade=item.get("upgrades", 0) / 10, sale=item.get("on_sale", False),
            sold=item.get("sold_out", False), legal=item.get("legal", False))
    for selected in state.get("grid_selected_cards") or []:
        add("card:" + selected["id"], upgrade=selected.get("upgrades", 0) / 10, sold=1)
    for field in ("screen_type", "grid_purpose"):
        if state.get(field):
            add(field + ":" + state[field])
    for key in ("id", "phase", "decision_kind"):
        if (state.get("event") or {}).get(key):
            add("event_" + key + ":" + state["event"][key])
    if state.get("purge_cost") is not None:
        add("purge_cost", price=state["purge_cost"] / 1000)
    return rows


def build_vocabulary(examples):
    tokens = set("character:" + c for c in CHARACTERS)
    for example in examples:
        example = project_example(example, example.get("input_profile", example["pre_state"].get("input_profile", LIVE_PROFILE)))
        if example["pre_state"].get("input_profile") in (RUN_PROFILE, MIXED_PROFILE):
            fields = CONTEXT_FIELDS + OPTIONAL_FIELDS if example["pre_state"].get("input_profile") == MIXED_PROFILE else CONTEXT_FIELDS
            tokens.update("context:"+field+":"+status for field in fields for status in ("known", "unknown"))
        tokens.update(token for token, _ in state_parts(example["pre_state"]))
        for candidate in example["candidates"]:
            tokens.update(action_parts(candidate)[0])
    return {token: index for index, token in enumerate(["<PAD>", "<UNK>"] + sorted(tokens - {"<PAD>", "<UNK>"}))}


def encode_batch(examples, vocabulary, *, device="cpu"):
    sets, actions, features, masks, globals_, labels = [], [], [], [], [], []
    for example in examples:
        example = project_example(example, example.get("input_profile", example["pre_state"].get("input_profile", LIVE_PROFILE)))
        s, candidates = example["pre_state"], example["candidates"]
        if s.get("character") not in CHARACTERS or not 0 <= s.get("ascension", -1) <= 20:
            raise ValueError("unknown character/ascension")
        if not candidates or len(candidates) > MAX_CANDIDATES or not any(c.get("legal") for c in candidates):
            raise ValueError("empty/overflow/all-illegal candidate set")
        rows = state_parts(s)
        parts = [action_parts(c) for c in candidates]
        # Menus are observable sets too; every alternative reaches the encoder.
        for tokens, numeric in parts:
            rows.extend((token, numeric) for token in tokens)
        sets.append(rows)
        actions.append([tokens for tokens, _ in parts])
        features.append([numeric for _, numeric in parts])
        masks.append([bool(c.get("legal")) for c in candidates])
        global_row = []
        for key, scale in zip(SCALARS, SCALES):
            known = (s.get("known") or {}).get(key, s.get(key) is not None)
            global_row.extend([(s.get(key) or 0) / scale if known else 0., float(known)])
        globals_.append(global_row)
        labels.append(next((i for i, c in enumerate(candidates) if c["action_id"] == example.get("chosen_action_id")), -1))
    b, n, a = len(examples), max(map(len, sets)), max(map(len, actions))
    l = max(len(tokens) for group in actions for tokens in group)
    ids = torch.zeros((b, n), dtype=torch.long)
    nums = torch.zeros((b, n, WIDTH))
    candidate_ids = torch.zeros((b, a, l), dtype=torch.long)
    candidate_nums = torch.zeros((b, a, WIDTH))
    legal = torch.zeros((b, a), dtype=torch.bool)
    for i, rows in enumerate(sets):
        ids[i, :len(rows)] = torch.tensor([vocabulary.get(token, 1) for token, _ in rows])
        nums[i, :len(rows)] = torch.tensor([numeric for _, numeric in rows])
        for j, tokens in enumerate(actions[i]):
            candidate_ids[i, j, :len(tokens)] = torch.tensor([vocabulary.get(t, 1) for t in tokens])
        candidate_nums[i, :len(features[i])] = torch.tensor(features[i])
        legal[i, :len(masks[i])] = torch.tensor(masks[i])
    return {k: v.to(device) for k, v in dict(ids=ids, nums=nums, globals=torch.tensor(globals_, dtype=torch.float32),
            candidate_ids=candidate_ids, candidate_nums=candidate_nums, legal=legal, labels=torch.tensor(labels)).items()}
