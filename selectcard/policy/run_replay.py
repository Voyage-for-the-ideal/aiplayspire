"""Causal, conservative historical replay. Terminal lists are never consulted.

Unknown core mutations terminate the remaining prefix, not earlier samples.
Independent terminal observations are audit labels, not reconciliation hints.
"""
from collections import Counter, defaultdict
from functools import lru_cache
import math
import re

from selectcard.src.content_catalog import VanillaContentCatalog, canonical_card_id
from selectcard.src.reconstructor import RunReconstructor
from selectcard.src.map_context import forward_consistent_positions, nodes_as_payload
from selectcard.src.sts_mapgen import generate_act_map
from .contracts import FORMAT, CHARACTERS, SCALARS, digest, split_for, validate_example
from .profiles import RUN_PROFILE, project_state
from selectcard.src.boss_context import canonicalize_boss_name

REPLACEMENTS = {"Black Blood": "Burning Blood", "Ring of the Serpent": "Ring of the Snake",
                "FrozenCore": "Cracked Core", "Frozen Core": "Cracked Core", "HolyWater": "PureWater", "Holy Water": "PureWater"}
OPAQUE_RELICS = {"War Paint", "Whetstone", "Pandora's Box", "Astrolabe", "Empty Cage", "Tiny House",
                "DollysMirror", "Calling Bell", "Orrery"}
EGGS = {"ATTACK": "Molten Egg 2", "SKILL": "Toxic Egg 2", "POWER": "Frozen Egg 2"}
MODE_FLAGS = ("is_daily", "is_endless", "is_trial", "is_beta", "chose_seed", "special_seed")
NO_DECK_NEOW = {"", "NONE", "TEN_PERCENT_HP_BONUS", "TWENTY_PERCENT_HP_BONUS", "HUNDRED_GOLD",
                "TWO_FIFTY_GOLD", "THREE_SMALL_POTIONS"}


class Uncertain(ValueError):
    pass


@lru_cache(maxsize=8)
def act_map(seed, act, ascension):
    return generate_act_map(seed, act, ascension_level=ascension)


def run_identity(run):
    transport = {"play_id", "timestamp", "local_time", "playtime", "player_experience", "seed_source_timestamp"}
    return digest({k: v for k, v in run.items() if k not in transport})


def card_parts(value):
    if not isinstance(value, str):
        raise Uncertain("invalid_card_id")
    match = re.fullmatch(r"(.+?)(?:\+(\d*))?", value)
    return match[1], (int(match[2]) if match[2] else 1) if "+" in value else 0


class CausalRunReplay:
    def __init__(self, run, catalog=None):
        self.raw = run
        self.catalog = catalog or VanillaContentCatalog.from_repo()
        self.stats = Counter()
        self.run_id = run_identity(run)
        self.seed_group = str(int(run["seed_played"]))
        self.character = run["character_chosen"]
        if self.character not in CHARACTERS:
            raise Uncertain("unknown_character")
        self.ascension = int(run.get("ascension_level", 0))
        self.deck = list(RunReconstructor.STARTER_DECKS[self.character])
        if self.ascension >= 10:
            self.deck.append("AscendersBane")
        self.relics = [RunReconstructor.STARTER_RELICS[self.character]]
        self.omamori = 0
        self.blocked = None
        self.stopped_floor = None
        self.exclusions = []
        self.include_map = False
        self.outcome = {"terminal_known": bool(run.get("victory") or run.get("killed_by")),
                        "victory": bool(run.get("victory")), "heart_victory": False}
        combats = run.get("damage_taken") or []
        self.outcome["heart_victory"] = bool(run.get("victory") and combats and
            str(combats[-1].get("enemies", "")).lower() in ("the heart", "corrupt heart", "the corrupt heart"))
        if not self.outcome["terminal_known"]:
            self.outcome.update(victory=None, heart_victory=None)
        elif run.get("victory") and not self.outcome["heart_victory"]:
            last = canonicalize_boss_name(combats[-1].get("enemies")) if combats else None
            if last not in ("Time Eater", "Awakened One", "Donu and Deca"):
                self.outcome["heart_victory"] = None
        self.outcome["terminal_floor"] = run.get("floor_reached")
        self.paths = run.get("path_per_floor") or []

    def add_card(self, value, eggs=None):
        identity, upgrades = card_parts(value)
        if identity not in self.catalog.cards:
            raise Uncertain("unknown_card:" + identity)
        # Metrics can record attempted curse rewards blocked by Omamori.
        if self.catalog.card_type(identity) == "CURSE" and self.omamori:
            self.omamori -= 1
            return
        owned = self.relics if eggs is None else eggs
        if not upgrades and EGGS.get(self.catalog.card_type(identity)) in owned:
            upgrades = 1
        self.deck.append(identity + (f"+{upgrades}" if upgrades else ""))

    def remove_card(self, value):
        identity, upgrades = card_parts(value)
        for i, card in enumerate(self.deck):
            if card_parts(card) == (identity, upgrades):
                self.deck.pop(i)
                return
        raise Uncertain("unmatched_card_removal:" + value)

    def upgrade_card(self, value, *, metric_before=False):
        identity, upgrades = card_parts(value)
        matches = [(card_parts(c)[1], i) for i, c in enumerate(self.deck) if card_parts(c)[0] == identity]
        if identity == "Searing Blow":
            # Campfire logs the pre-upgrade metric ID; event helpers may log
            # the post-upgrade ID or just cardID. Different levels are not
            # interchangeable when the event omitted the target's level.
            target = upgrades if metric_before else upgrades-1 if "+" in value else None
            matches = [(level,i) for level,i in matches if target is None or level == target]
            if len({level for level,_ in matches}) > 1:
                raise Uncertain("ambiguous_searing_blow_upgrade")
        else:
            matches = [(level,i) for level,i in matches if level == 0]
        if not matches:
            raise Uncertain("unmatched_card_upgrade:" + value)
        level, index = min(matches)
        if level and identity != "Searing Blow":
            raise Uncertain("unmatched_card_upgrade_level:" + value)
        self.deck[index] = f"{identity}+{level+1}"

    def add_relic(self, value):
        identity = self.catalog.canonical_relic_id(value)
        if identity not in self.catalog.relics:
            raise Uncertain("unknown_relic:" + identity)
        if identity in self.relics:
            return
        old = REPLACEMENTS.get(identity)
        if old and old in self.relics:
            self.relics.remove(old)
        self.relics.append(identity)
        if identity == "Omamori":
            self.omamori = 2
        if identity in OPAQUE_RELICS:
            raise Uncertain("unlogged_relic_mutation:" + identity)
        if identity == "Necronomicon":
            self.add_card("Necronomicurse")

    def apply_event(self, event):
        obtained = event.get("cards_obtained") or []
        transformed = event.get("cards_transformed") or []
        if transformed and len(transformed) != len(obtained):
            raise Uncertain("unlogged_transform_result")
        for key in event.get("relics_lost") or []:
            identity = self.catalog.canonical_relic_id(key)
            if identity not in self.relics:
                raise Uncertain("unmatched_relic_loss")
            self.relics.remove(identity)
            if identity == "Necronomicon":
                raise Uncertain("unlogged_necronomicon_removal")
        for key in event.get("relics_obtained") or []:
            self.add_relic(key)
        for value in (event.get("cards_removed") or []) + transformed:
            self.remove_card(value)
        if event.get("event_name") == "Vampires" and any(card_parts(c)[0] == "Bite" for c in obtained):
            # Vanilla Vampires removes every starter Strike without logging
            # their IDs individually; this rule is causal and deterministic.
            self.deck = [c for c in self.deck if not card_parts(c)[0].startswith("Strike_")]
        for value in event.get("cards_upgraded") or []:
            self.upgrade_card(value)
        for value in obtained:
            self.add_card(value)

    def neow(self, events, relics, choices):
        if "neow_bonus" not in self.raw or "neow_cost" not in self.raw:
            raise Uncertain("missing_neow_log")
        bonus, cost = self.raw.get("neow_bonus", ""), self.raw.get("neow_cost", "NONE")
        if cost not in ("NONE", "CURSE", "TEN_PERCENT_HP_LOSS", "NO_GOLD", "PERCENT_DAMAGE"):
            raise Uncertain("unsupported_neow_cost")
        if cost == "CURSE" and not any(self.catalog.card_type(c) == "CURSE" for e in events for c in e.get("cards_obtained") or []):
            raise Uncertain("unlogged_neow_curse")
        required = {"REMOVE_CARD": "cards_removed", "REMOVE_TWO": "cards_removed", "REMOVE_TWO_CARDS": "cards_removed",
                    "TRANSFORM_CARD": "cards_transformed", "TRANSFORM_TWO_CARDS": "cards_transformed", "UPGRADE_CARD": "cards_upgraded",
                    "ONE_RANDOM_RARE_CARD": "cards_obtained"}
        expected = 2 if bonus in ("REMOVE_TWO", "REMOVE_TWO_CARDS", "TRANSFORM_TWO_CARDS") else 1
        if bonus in required and sum(len(e.get(required[bonus]) or []) for e in events) != expected:
            raise Uncertain("unlogged_neow_target:" + bonus)
        if bonus == "THREE_ENEMY_KILL":
            self.add_relic("NeowsBlessing")
        elif bonus in ("RANDOM_COMMON_RELIC", "ONE_RANDOM_RARE_RELIC", "ONE_RARE_RELIC", "BOSS_RELIC"):
            if len(relics) != 1:
                raise Uncertain("unlogged_neow_relic")
            if bonus == "BOSS_RELIC":
                self.relics.clear()
        elif bonus not in NO_DECK_NEOW and bonus not in required and bonus not in (
                "THREE_CARDS", "THREE_RARE_CARDS", "RANDOM_COLORLESS", "RANDOM_COLORLESS_2"):
            raise Uncertain("unsupported_neow:" + bonus)
        if bonus in ("THREE_CARDS", "THREE_RARE_CARDS", "RANDOM_COLORLESS", "RANDOM_COLORLESS_2") and len(choices) != 1:
            raise Uncertain("missing_neow_reward")

    def context(self, floor, act, boss_reward=False):
        # Never instantiate a builder on the whole future path or terminal relics.
        prefix = self.paths[:floor]
        start = 0
        for i, value in enumerate(prefix):
            if value is None:
                start = i + 1
        symbols = [s for s in prefix[start:] if s is not None]
        if boss_reward:
            # A reward separator is not yet the next Act's generated map.
            prior = self.paths[:floor-1]
            start = max([i + 1 for i, s in enumerate(prior) if s is None] or [0])
            symbols = prior[start:]
        past = symbols if boss_reward else symbols[:-1]
        history = [{"floor": start+i+1, "resolved_symbol": s} for i, s in enumerate(past)][-8:]
        nodes, current, map_known, position_known = [], {}, False, False
        try:
            if 1 <= act <= 3:
                generated = act_map(int(self.seed_group), act, self.ascension)
                nodes = nodes_as_payload(generated)
                map_known = True
                # Wing acquisition/charges are not guessed from final relics.
                if len(symbols) <= 15 and not any(r in self.relics for r in ("WingedGreaves", "Wing Boots")):
                    positions = forward_consistent_positions(generated, symbols) if symbols else []
                    if positions and len(positions[-1]) == 1:
                        current = {"x": next(iter(positions[-1])), "y": len(symbols)-1}
                        position_known = True
            elif act == 4:
                nodes = [dict(x=3, y=y, symbol=s, children=[dict(x=3,y=y+1)] if y < 3 else [])
                         for y,s in enumerate(("R", "$", "E", "B"))]
                map_known = True
                if symbols and symbols == ["R", "$", "E", "B"][:len(symbols)]:
                    current, position_known = dict(x=3, y=len(symbols)-1), True
        except (ValueError, IndexError, KeyError):
            nodes, map_known = [], False
        for node in nodes:
            node["is_current"] = position_known and (node["x"], node["y"]) == (current["x"], current["y"])
            # Account-wide Heart unlock and burning-elite status are not logged.
            node["burning"] = False
        return dict(map_nodes=nodes, current_map_node=current, current_map_choices=[], path_history=history,
                    known=dict(map=map_known, position=position_known, history=True))

    def state(self, floor, act, task, *, same_floor_relics=False):
        cards = Counter(card_parts(c) for c in self.deck)
        state = dict(character=self.character, ascension=self.ascension, floor=floor, act=act, screen_type=task,
                     deck=[dict(id=key, upgrades=level, count=count) for (key, level), count in sorted(cards.items())],
                     relics=[dict(id=r, counter=None) for r in self.relics], visible_boss=None)
        context = self.context(floor, act, task == "BOSS_REWARD")
        state.update(context)
        # A standard Act's first boss is displayed upon map generation. Its
        # identity in the boss-combat log is evidence of that public context,
        # not a survival/damage feature. Seed-only fallback is unsafe on early
        # accounts, whose unseen bosses override the shuffled boss list.
        allowed = ({"Slime Boss", "Hexaghost", "The Guardian"}, {"Champ", "The Collector", "Bronze Automaton"},
                   {"Time Eater", "Awakened One", "Donu and Deca"})
        if act <= 3:
            records = sorted((int(c.get("floor", 999)), canonicalize_boss_name(c.get("enemies"))) for c in self.raw.get("damage_taken") or [])
            current_bosses = [(cf,b) for cf,b in records if b in allowed[act-1]]
            if current_bosses:
                state["visible_boss"] = current_bosses[0][1]
                # Second boss identity is visible by its own reward screen.
                if act == 3 and self.ascension == 20:
                    seen = [b for cf,b in current_bosses if cf <= floor]
                    if seen:
                        state["visible_boss"] = seen[-1]
        else:
            state["visible_boss"] = "Corrupt Heart"
        state["known"]["visible_boss"] = state["visible_boss"] is not None
        for field, source in (("hp", "current_hp_per_floor"), ("max_hp", "max_hp_per_floor"), ("gold", "gold_per_floor")):
            values = self.raw.get(source) or []
            # Gold reward pickup order is absent; Bowl and potion use can change HP.
            safe = task == "CARD_REWARD" and field != "gold" and not same_floor_relics and not {"Singing Bowl", "Bloody Idol"}.intersection(self.relics)
            safe = safe and (self.paths[floor-1] if 0 < floor <= len(self.paths) else None) != "B"
            safe = safe and floor not in (self.raw.get("potions_floor_usage") or [])
            value = values[floor-1] if safe and 0 < floor <= len(values) else None
            state[field] = value if isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and value >= 0 else None
            state["known"][field] = state[field] is not None
        for field in SCALARS:
            state["known"].setdefault(field, state.get(field) is not None)
        return project_state(state, RUN_PROFILE)

    def sample(self, floor, act, task, record, decision_id):
        if "not_picked" not in record or not isinstance(record["not_picked"], list):
            raise Uncertain("missing_candidate_log")
        if task == "CARD_REWARD" and record.get("picked") is None:
            raise Uncertain("missing_chosen_card")
        picked = record.get("picked")
        cards = list(record.get("not_picked") or [])
        if task == "CARD_REWARD":
            if picked not in (None, "SKIP", "Singing Bowl"):
                cards.append(picked)
            actions = [dict(action_id=f"card:{i}", kind="take_card", entity_kind="card", entity_id=card_parts(c)[0],
                            upgrades=card_parts(c)[1], legal=True) for i, c in enumerate(cards)]
            actions.append(dict(action_id="skip", kind="skip", legal=True))
            if "Singing Bowl" in self.relics:
                actions.append(dict(action_id="bowl", kind="bowl", amount=2, legal=True))
            chosen = "skip" if picked in (None, "SKIP") else "bowl" if picked == "Singing Bowl" else f"card:{len(cards)-1}"
            if picked == "Singing Bowl" and "Singing Bowl" not in self.relics:
                raise Uncertain("unproven_bowl_action")
        else:
            if picked in (None, "SKIP") and len(cards) != 3:
                raise Uncertain("unproven_boss_skip")
            if picked not in (None, "SKIP"):
                cards.append(picked)
            actions = [dict(action_id=f"relic:{i}", kind="take_relic", entity_kind="relic", entity_id=c, legal=True) for i, c in enumerate(cards)]
            actions.append(dict(action_id="skip", kind="leave", legal=True))
            chosen = f"relic:{len(cards)-1}" if picked not in (None, "SKIP") else "skip"
        if not cards:
            raise Uncertain("missing_candidates")
        for candidate in actions:
            entity = candidate.get("entity_id")
            if entity and entity not in (self.catalog.cards if task == "CARD_REWARD" else self.catalog.relics):
                raise Uncertain("unknown_candidate:" + entity)
            if task == "CARD_REWARD" and entity and not candidate["upgrades"] and EGGS.get(self.catalog.card_type(entity)) in self.relics:
                candidate["upgrades"] = 1
        state = self.state(floor, act, task)
        row = dict(format=FORMAT, run_id=self.run_id, seed_group=self.seed_group, split=split_for(self.seed_group),
                   decision_id=decision_id, decision_type=task, pre_state=state,
                   candidates=actions, chosen_action_id=chosen, outcome=self.outcome.copy(),
                   run_mode={key: self.raw.get(key, 0 if key == "special_seed" else False) for key in MODE_FLAGS},
                   input_profile=RUN_PROFILE, provenance=dict(status="exact", source="causal-run-prefix-v1",
                       state_timing="pre_action", unknown_optional=[k for k,v in state["known"].items() if not v]))
        validate_example(row)
        return row

    def map_sample(self, floor, act):
        """Emit only a uniquely identified next node, never use later path suffixes."""
        if not (1 <= act <= 3 and 0 < floor < len(self.paths)):
            return None
        if self.paths[floor - 1] in (None, "B") or self.paths[floor] in (None, "B"):
            return None
        if any(r in self.relics for r in ("WingedGreaves", "Wing Boots")):
            return None
        state = self.state(floor, act, "MAP")
        if not state["known"].get("position"):
            return None
        current = state["current_map_node"]
        nodes = {(n["x"], n["y"]): n for n in state["map_nodes"]}
        node = nodes.get((current["x"], current["y"]))
        if node is None or current["y"] >= 14:
            return None
        children = sorted({(e["x"], e["y"]) for e in node["children"]})
        if len(children) < 2 or any(xy not in nodes for xy in children):
            return None
        start = max([i + 1 for i, s in enumerate(self.paths[:floor]) if s is None] or [0])
        prefix = self.paths[start:floor]
        if len(prefix) != current["y"] + 1:
            return None
        possible = forward_consistent_positions(act_map(int(self.seed_group), act, self.ascension), prefix + [self.paths[floor]])
        if not possible or len(possible[-1]) != 1:
            return None
        chosen = (next(iter(possible[-1])), current["y"] + 1)
        if chosen not in children:
            return None
        state["current_map_choices"] = [dict(x=x, y=y, symbol=nodes[(x, y)]["symbol"]) for x, y in children]
        candidates = [dict(action_id=f"map:{x}:{y}", kind="map", legal=True, x=x, y=y,
                           purpose=nodes[(x, y)]["symbol"]) for x, y in children]
        row = dict(format=FORMAT, run_id=self.run_id, seed_group=self.seed_group, split=split_for(self.seed_group),
                   decision_id=f"map:{floor}", decision_type="MAP", pre_state=state, candidates=candidates,
                   chosen_action_id=f"map:{chosen[0]}:{chosen[1]}", outcome=self.outcome.copy(),
                   run_mode={key: self.raw.get(key, 0 if key == "special_seed" else False) for key in MODE_FLAGS},
                   input_profile=RUN_PROFILE, provenance=dict(status="exact", source="causal-run-map-v1",
                       state_timing="pre_action", unknown_optional=[k for k,v in state["known"].items() if not v]))
        validate_example(row)
        return row

    def replay(self):
        choices, events, relics, campfires, purchases, purges = (defaultdict(list) for _ in range(6))
        for field, target in (("card_choices", choices), ("event_choices", events), ("relics_obtained", relics), ("campfire_choices", campfires)):
            for i, row in enumerate(self.raw.get(field) or []):
                target[int(row.get("floor", 0))].append((i, row))
        for field, floors, target in (("items_purchased", "item_purchase_floors", purchases), ("items_purged", "items_purged_floors", purges)):
            if len(self.raw.get(field) or []) != len(self.raw.get(floors) or []):
                self.blocked = "mismatched_transaction_logs"
                return
            for value, floor in zip(self.raw.get(field) or [], self.raw.get(floors) or []):
                target[int(floor)].append(value)
        act = 1
        boss_index = 0
        limit = min(57, max(len(self.paths), max(choices, default=0), max(relics, default=0)))
        for floor in range(limit + 1):
            try:
                before_relics = list(self.relics)
                new_relics = [r["key"] for _, r in relics[floor]]
                if floor == 0:
                    self.neow([e for _,e in events[0]], new_relics, [r for _,r in choices[0]])
                symbol = self.paths[floor-1] if 0 < floor <= len(self.paths) else None
                separator = 1 < floor <= len(self.paths) and symbol is None and self.paths[floor-2] == "B"
                is_boss_reward = separator and boss_index < 2 and act <= 2
                if is_boss_reward:
                    records = self.raw.get("boss_relics") or []
                    if boss_index < len(records):
                        record = records[boss_index]
                        yield self.sample(floor, act, "BOSS_REWARD", record, f"boss:{boss_index}")
                        self.stats["BOSS_REWARD"] += 1
                        if record.get("picked") not in (None, "SKIP"):
                            self.add_relic(record["picked"])
                    boss_index += 1
                # Ignore transport duplicates of an already processed purchase/relic.
                for identity in new_relics:
                    if identity not in purchases[floor]:
                        self.add_relic(identity)
                for item in purchases[floor]:
                    kind = self.catalog.classify_item(item)
                    if kind == "card":
                        self.add_card(item)
                    elif kind == "relic":
                        self.add_relic(item)
                    elif kind != "potion":
                        raise Uncertain("unknown_purchase")
                if purchases[floor] and purges[floor]:
                    # Removing and then reacquiring the same ID may change upgrade counts.
                    if any(canonical_card_id(c) == canonical_card_id(p) for c in purchases[floor] for p in purges[floor]):
                        raise Uncertain("ambiguous_purchase_purge_order")
                for value in purges[floor]:
                    self.remove_card(value)
                for _, event in events[floor]:
                    self.apply_event(event)
                if events[floor] and choices[floor]:
                    raise Uncertain("ambiguous_event_reward_double_log")
                for _, campfire in campfires[floor]:
                    if campfire.get("key") == "SMITH":
                        self.upgrade_card(campfire["data"], metric_before=True)
                    elif campfire.get("key") == "PURGE":
                        self.remove_card(campfire["data"])
                if symbol in ("M", "E", "B") and any(card_parts(c)[0] == "LessonLearned" for c in self.deck):
                    raise Uncertain("unlogged_lesson_learned_upgrade")
                if symbol == "T" and "Cursed Key" in self.relics:
                    raise Uncertain("unlogged_cursed_key_curse")
                for index, record in choices[floor]:
                    picked = record.get("picked")
                    if not isinstance(picked, str) or not picked:
                        raise Uncertain("missing_chosen_card")
                    if picked == "Singing Bowl" and "Singing Bowl" not in self.relics:
                        raise Uncertain("unproven_bowl_action")
                    # Acquisition order of same-floor relics and rewards is not logged.
                    supported = floor > 0 and symbol in ("M", "E", "B") and not events[floor] and not new_relics
                    if supported:
                        yield self.sample(floor, act, "CARD_REWARD", record, f"card:{index}")
                        self.stats["CARD_REWARD"] += 1
                    else:
                        self.stats["excluded_choice_origin_or_order"] += 1
                        self.exclusions.append(dict(decision_id=f"card:{index}", floor=floor, reason="choice_origin_or_relic_order"))
                    if picked and picked not in ("SKIP", "Singing Bowl"):
                        identity, upgrades = card_parts(picked)
                        new_egg = EGGS.get(self.catalog.card_type(identity))
                        if not upgrades and new_egg in self.relics and new_egg not in before_relics:
                            raise Uncertain("ambiguous_same_floor_egg_order")
                        self.add_card(picked, eggs=before_relics)
                if self.include_map and not is_boss_reward:
                    map_row = self.map_sample(floor, act)
                    if map_row is not None:
                        yield map_row
                        self.stats["MAP"] += 1
                    elif floor > 0:
                        self.stats["excluded_map_ambiguous_or_unsupported"] += 1
                if is_boss_reward:
                    act += 1
                elif act == 3 and separator:
                    act = 4
            except (Uncertain, ValueError, KeyError, TypeError) as exc:
                self.blocked = str(exc)
                self.stopped_floor = floor
                self.stats["prefix_stopped"] += 1
                return
