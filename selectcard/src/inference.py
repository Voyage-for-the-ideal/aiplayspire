import torch
import copy
from functools import lru_cache
try:
    from .model import STSValueNetwork
    from .checkpointing import load_checkpoint
    from .encoding import ItemVocabulary, encode_items, parse_item_name
    from .dataset import GlobalFeatureEncoder
    from .config import Config
    from .data_contract import HAZARD_ENDPOINTS, HAZARD_OUTPUT_DIM
    from .boss_context import NUM_BOSS_IDS, boss_id, get_visible_boss
    from .character_context import NUM_CHARACTER_IDS, character_id
    from .content_catalog import VanillaContentCatalog
    from . import map_context
except ImportError:
    from model import STSValueNetwork
    from checkpointing import load_checkpoint
    from encoding import ItemVocabulary, encode_items, parse_item_name
    from dataset import GlobalFeatureEncoder
    from config import Config
    from data_contract import HAZARD_ENDPOINTS, HAZARD_OUTPUT_DIM
    from boss_context import NUM_BOSS_IDS, boss_id, get_visible_boss
    from character_context import NUM_CHARACTER_IDS, character_id
    from content_catalog import VanillaContentCatalog
    import map_context


V2_ITEM_TOKEN_BUDGET = 64  # v2 checkpoints were trained with this item budget


@lru_cache(maxsize=1)
def _vanilla_content_catalog():
    return VanillaContentCatalog.from_repo()


def resolve_deck_entry(deck, target):
    """The deck entry ``target`` denotes, or None when the deck has no such card.

    Deck entries carry the training convention's upgrade suffix (``Bash+1``),
    while a target may arrive bare (``Bash``) or in the display form the shop
    screen produces (``Bash+``).  An exact match wins, since the framework
    sends references on both sides; otherwise match on ``(base, level)``, where
    a bare target means the unupgraded copy.
    """
    if target in deck:
        return target
    if not isinstance(target, str) or not target:
        return None
    wanted = parse_item_name(target)
    for entry in deck:
        if parse_item_name(entry) == wanted:
            return entry
    return None


def upgraded_reference(reference):
    """The reference for one upgrade applied to ``reference``."""
    base, level = parse_item_name(reference)
    return f"{base}+{level + 1}"


def upgrade_level(reference):
    """Upgrade level encoded in a card reference (0 = unupgraded)."""
    return parse_item_name(reference)[1]


def canonical_reference(reference):
    """Rewrite a card reference into the deck's canonical ``ID``/``ID+N`` form.

    Targets can arrive in the shop screen's display spelling (``Bash+``); the
    deck should only ever hold one spelling so exact-match lookups keep working.
    Non-string and empty inputs are returned unchanged.
    """
    if not isinstance(reference, str) or not reference:
        return reference
    base_name, level = parse_item_name(reference)
    if level <= 0:
        return base_name
    return f"{base_name}+{level}"


def base_card_name(reference):
    """Base name of a card reference, ignoring any upgrade level."""
    return parse_item_name(reference)[0]


STARTER_ORDER = (
    "Strike_R", "Strike_G", "Strike_B", "Strike_P", "Strike",
    "Defend_R", "Defend_G", "Defend_B", "Defend_P", "Defend",
)

STARTER_STRIKES = frozenset({"Strike_R", "Strike_G", "Strike_B", "Strike_P", "Strike"})

STARTER_STRIKE_AND_DEFENDS = frozenset(STARTER_ORDER)


def prefer_unupgraded_entry(deck, base_name):
    """First deck entry named ``base_name``, preferring the unupgraded copy."""
    fallback = None
    for entry in deck:
        entry_base, level = parse_item_name(entry)
        if entry_base != base_name:
            continue
        if level == 0:
            return entry
        if fallback is None:
            fallback = entry
    return fallback


def resolve_starter_entry(deck):
    """The starter card an unqualified removal should take, or None."""
    for base_name in STARTER_ORDER:
        entry = prefer_unupgraded_entry(deck, base_name)
        if entry is not None:
            return entry
    return None


def _is_excluded(card, exclude_ids):
    """Whether ``card`` is barred by ``exclude_ids``.

    Exclusion sets and deck entries normally share a spelling, but tolerate the
    display form (``Bash+``) so an exclusion can never silently miss.
    """
    if card in exclude_ids:
        return True
    wanted = parse_item_name(card)
    return any(parse_item_name(excluded) == wanted for excluded in exclude_ids)


def compose_hazard_value(hazards, heart_conditional, floor, heart_bonus_floors=None):
    """Compose monotone progress survival into an interpretable scalar value."""
    if set(hazards) != set(HAZARD_ENDPOINTS):
        raise ValueError("hazards must define every configured bucket endpoint")
    floor = float(floor)
    bonus = float(
        Config.HEART_WIN_BONUS_FLOORS
        if heart_bonus_floors is None
        else heart_bonus_floors
    )
    if not 0.0 <= floor <= HAZARD_ENDPOINTS[-1]:
        raise ValueError("floor must be between 0 and 57")
    if bonus < 0.0:
        raise ValueError("heart_bonus_floors must be non-negative")
    if not 0.0 <= float(heart_conditional) <= 1.0:
        raise ValueError("heart_conditional must be a probability")

    survival = {}
    cumulative = 1.0
    expected_floor = floor
    previous = 0.0
    for endpoint in HAZARD_ENDPOINTS:
        probability = float(hazards[endpoint])
        if not 0.0 <= probability <= 1.0:
            raise ValueError("hazard values must be probabilities")
        if endpoint <= floor:
            survival[endpoint] = 1.0
        else:
            cumulative *= 1.0 - probability
            survival[endpoint] = cumulative
            width = endpoint - max(floor, previous)
            expected_floor += width * cumulative
        previous = float(endpoint)
    heart_probability = cumulative * float(heart_conditional)
    scalar = (expected_floor + bonus * heart_probability) / (
        HAZARD_ENDPOINTS[-1] + bonus
    )
    return survival, heart_probability, expected_floor, scalar

class InferenceTokenizer:
    """Use the same frozen canonical item encoding as training."""
    def __init__(self, vocabulary, max_seq_len=64, max_upgrade=15, max_count=10):
        self.vocabulary = vocabulary
        self.max_seq_len = max_seq_len
        self.max_upgrade = max_upgrade
        self.max_count = max_count

    def encode(self, deck, relics):
        seq, upgrades, counts = encode_items(
            deck,
            relics,
            self.vocabulary,
            self.max_seq_len,
            self.max_upgrade,
            self.max_count,
        )
        return (
            torch.tensor([seq], dtype=torch.long),
            torch.tensor([upgrades], dtype=torch.long),
            torch.tensor([counts], dtype=torch.long),
        )

class STSInferenceEngine:
    def __init__(self, model_path=None):
        self.content_catalog = _vanilla_content_catalog()
        if model_path:
            checkpoint, vocabulary, self.feature_encoder = load_checkpoint(model_path)
            architecture = checkpoint["model_config"]
            self.model = STSValueNetwork(**architecture)
            self.model.load_state_dict(checkpoint["model_state_dict"])
        else:
            vocabulary = ItemVocabulary().freeze()
            self.feature_encoder = GlobalFeatureEncoder()
            architecture = {
                "vocab_size": len(vocabulary),
                "max_upgrade": Config.MAX_UPGRADE,
                "max_count": Config.MAX_COUNT,
                "d_model": Config.D_MODEL,
                "n_heads": Config.N_HEADS,
                "n_layers": Config.N_LAYERS,
                "num_global_features": Config.NUM_GLOBAL_FEATURES,
                "num_characters": NUM_CHARACTER_IDS,
                "num_bosses": NUM_BOSS_IDS,
                "dropout": Config.DROPOUT,
                "global_conditioning": Config.GLOBAL_CONDITIONING,
                "norm_position": Config.NORM_POSITION,
                "use_map_context": Config.USE_MAP_CONTEXT,
                "num_key_features": 9,
            }
            self.model = STSValueNetwork(**architecture)
        self.uses_map_context = bool(getattr(self.model, "use_map_context", False))
        self.supports_key_value = bool(
            model_path and self.uses_map_context and architecture.get("num_key_features") == 9
        )
        item_budget = (
            Config.MAX_ITEM_TOKENS if self.uses_map_context else V2_ITEM_TOKEN_BUDGET
        )
        self.tokenizer = InferenceTokenizer(
            vocabulary,
            max_seq_len=item_budget,
            max_upgrade=architecture["max_upgrade"],
            max_count=architecture["max_count"],
        )
        self.model.eval()

    def _global_features(self, state):
        values = self.feature_encoder.transform_state(state)
        return torch.tensor([values], dtype=torch.float32)

    def _boss_id(self, state):
        return torch.tensor([boss_id(get_visible_boss(state))], dtype=torch.long)

    def _character_id(self, state):
        if not isinstance(state, dict):
            raise ValueError("state must be a dictionary with a character field")
        return torch.tensor([character_id(state.get("character"))], dtype=torch.long)

    def _context_tensors(self, state):
        """Pack optional map/history/key fields of ``state`` into model tensors.

        ``_apply_choice`` deep-copies the state dict, so hypothetical next
        states inherit the same act map; only keys changed by the choice need
        updating by the caller.
        """
        context = map_context.inference_context(state if isinstance(state, dict) else {})
        keys_features = map_context.keys_token_features(
            context["key_states"], context["map_flags"], context["act"],
            context["key_known_mask"],
        )
        history_ids = context["history_rooms"]
        history_dist = [
            index if room else map_context.HISTORY_HORIZON
            for index, room in enumerate(history_ids)
        ]
        return (
            torch.tensor([keys_features], dtype=torch.float32),
            torch.tensor([context["map_rooms"]], dtype=torch.long),
            torch.tensor([context["map_flags"]], dtype=torch.long),
            torch.tensor([history_ids], dtype=torch.long),
            torch.tensor([history_dist], dtype=torch.long),
        )

    def _model_logits(self, state):
        seq_tokens, upgrades, counts = self.tokenizer.encode(state['deck'], state['relics'])
        global_feats = self._global_features(state)
        character_ids = self._character_id(state)
        boss_ids = self._boss_id(state)
        if not self.uses_map_context:
            return self.model(
                seq_tokens, upgrades, counts, global_feats, character_ids, boss_ids
            )
        context = self._context_tensors(state)
        logits, key_logits = self.model(
            seq_tokens, upgrades, counts, global_feats, character_ids, boss_ids,
            keys_features=context[0], map_rooms=context[1],
            map_flags=context[2], history_rooms=context[3],
            history_dist=context[4], return_keys=True,
        )
        self._last_key_logits = key_logits
        return logits

    def _evaluate_state_distribution(self, state, heart_bonus_floors=None):
        with torch.no_grad():
            logits = self._model_logits(state)
            probabilities = torch.sigmoid(logits).squeeze(0).tolist()
        hazards = dict(zip(HAZARD_ENDPOINTS, probabilities[:HAZARD_OUTPUT_DIM]))
        heart_conditional = probabilities[HAZARD_OUTPUT_DIM]
        survival, heart_probability, expected_floor, scalar = compose_hazard_value(
            hazards,
            heart_conditional,
            float(state["floor"]),
            heart_bonus_floors=heart_bonus_floors,
        )
        result = {
            "hazards": hazards,
            "survival": survival,
            "heart_conditional": heart_conditional,
            "heart_probability": heart_probability,
            "expected_floor": expected_floor,
            "value": scalar,
        }
        self._last_evaluation = result
        return result

    def evaluate_state_hazards(self, state):
        """Return calibrated stopping hazards keyed by bucket endpoint."""
        return self._evaluate_state_distribution(state)["hazards"]

    def evaluate_state_survival(self, state):
        """Return monotone cumulative progress probabilities by endpoint."""
        return self._evaluate_state_distribution(state)["survival"]

    def evaluate_state(self, state, heart_bonus_floors=None):
        """Return expected progress plus the configured Heart-win premium."""
        return self._evaluate_state_distribution(state, heart_bonus_floors)["value"]

    def evaluate_state_logits(self, state):
        """Evaluate a single state dictionary and return raw logits."""
        with torch.no_grad():
            logits = self._model_logits(state)
        values = logits.squeeze(0).tolist()
        return {
            "hazards": dict(zip(HAZARD_ENDPOINTS, values[:HAZARD_OUTPUT_DIM])),
            "heart_conditional": values[HAZARD_OUTPUT_DIM],
        }

    def evaluate_state_keys(self, state):
        """Return P(ruby/emerald/sapphire collected | S) from the key head."""
        if not self.uses_map_context:
            raise ValueError("key head requires a map-context checkpoint")
        with torch.no_grad():
            self._model_logits(state)
            return torch.sigmoid(self._last_key_logits).squeeze(0).tolist()

    def _print_choice_score(self, label, score):
        print(f"{label} -> V(S') = {score:.4f}")
        if not getattr(self, "debug", Config.VALUE_DEBUG):
            return
        result = getattr(self, "_last_evaluation", None)
        if result:
            buckets = " ".join(
                f"h{endpoint}={result['hazards'][endpoint]:.3f}/"
                f"S{endpoint}={result['survival'][endpoint]:.3f}"
                for endpoint in HAZARD_ENDPOINTS
                if endpoint > 49
            )
            print(
                f"  {buckets} E[F]={result['expected_floor']:.2f} "
                f"P(Heart)={result['heart_probability']:.3f} "
                f"V={result['value']:.3f}"
            )
        
    def _apply_choice(self, current_state, choice):
        """Create a hypothetical new state based on the choice"""
        new_state = copy.deepcopy(current_state)
        action = choice.get("action")
        target = choice.get("target")

        def _normalize_relic_name(relic_item):
            if isinstance(relic_item, dict):
                return relic_item.get("name") or relic_item.get("id") or ""
            return str(relic_item)

        def _add_obtained_card(card_id, amount=1):
            card = canonical_reference(card_id)
            if not card:
                return
            catalog = getattr(self, "content_catalog", None)
            if catalog is None:
                catalog = _vanilla_content_catalog()
            egg_for_type = {
                "ATTACK": "Molten Egg 2",
                "POWER": "Frozen Egg 2",
                "SKILL": "Toxic Egg 2",
            }
            relic_names = {
                _normalize_relic_name(relic) for relic in new_state.get("relics", [])
            }
            egg = egg_for_type.get(catalog.card_type(card))
            if upgrade_level(card) == 0 and egg in relic_names:
                card = upgraded_reference(card)
            new_state["deck"].extend([card] * amount)

        def _get_omamori_charge_pool():
            relic_states = new_state.get("relic_states", []) or []
            for relic in relic_states:
                name = _normalize_relic_name(relic)
                relic_id = relic.get("id") if isinstance(relic, dict) else name
                if name == "Omamori" or relic_id == "Omamori":
                    counter = relic.get("counter", -1) if isinstance(relic, dict) else -1
                    if counter is None or counter < 0:
                        if isinstance(relic, dict):
                            relic["counter"] = 2
                            counter = 2
                        else:
                            return None
                    return relic
            return None

        def _consume_omamori_charge_if_possible():
            relic = _get_omamori_charge_pool()
            if relic is None:
                return False
            counter = relic.get("counter", -1)
            if counter is None or counter < 0:
                return False
            if counter > 0:
                relic["counter"] = counter - 1
                return True
            return False

        def _should_block_curse_add(card_id):
            if not card_id:
                return False
            normalized = str(card_id).strip().lower()
            # Omamori blocks any curse; the explicit list covers curses whose
            # id doesn't contain "curse" (Decay/Parasite included).
            if "curse" not in normalized and normalized not in {"regret", "pain", "normality", "doubt", "shame", "clumsy", "injury", "writhe", "decay", "parasite", "curse of the bell"}:
                return False
            return _consume_omamori_charge_if_possible()

        if action == "composite_event":
            for effect in choice.get("effects", []):
                e_type = effect.get("type")
                if e_type == "lose_hp":
                    new_state["hp"] = max(1, new_state["hp"] - effect["amount"])
                elif e_type == "gain_hp":
                    new_state["hp"] = min(new_state["max_hp"], new_state["hp"] + effect["amount"])
                elif e_type == "lose_max_hp":
                    new_state["max_hp"] = max(1, new_state["max_hp"] - effect["amount"])
                    new_state["hp"] = min(new_state["hp"], new_state["max_hp"])
                elif e_type == "gain_max_hp":
                    new_state["max_hp"] += effect["amount"]
                    new_state["hp"] += effect["amount"]
                elif e_type == "gain_gold":
                    new_state["gold"] += effect["amount"]
                elif e_type == "lose_gold":
                    new_state["gold"] = max(0, new_state["gold"] - effect["amount"])
                elif e_type == "lose_all_gold":
                    new_state["gold"] = 0
                elif e_type == "remove_card":
                    card_id = effect.get("card_id")
                    if card_id:
                        entry = resolve_deck_entry(new_state["deck"], card_id)
                        if entry is not None:
                            new_state["deck"].remove(entry)
                    else:
                        # Unqualified removal: take the first starter still in the
                        # deck, preferring its unupgraded copy.
                        for _ in range(effect.get("amount", 1)):
                            entry = resolve_starter_entry(new_state["deck"])
                            if entry is None:
                                break
                            new_state["deck"].remove(entry)
                elif e_type == "random_upgrade":
                    amount = effect.get("amount", 1)
                    chosen = self.rank_cards_for_purpose(new_state, "upgrade", amount)
                    for target_card in chosen:
                        if upgrade_level(target_card) != 0:
                            continue
                        entry = resolve_deck_entry(new_state["deck"], target_card)
                        if entry is not None:
                            new_state["deck"].remove(entry)
                            new_state["deck"].append(upgraded_reference(entry))
                elif e_type == "upgrade_card":
                    entry = resolve_deck_entry(
                        new_state["deck"], effect.get("card_id")
                    )
                    if entry is not None:
                        base_name, level = parse_item_name(entry)
                        new_state["deck"].remove(entry)
                        if level == 0:
                            new_state["deck"].append(f"{base_name}+1")
                        elif base_name == "Searing Blow":
                            # The only card that can be upgraded repeatedly.
                            new_state["deck"].append(upgraded_reference(entry))
                        else:
                            new_state["deck"].append(entry)
                elif e_type == "duplicate":
                    target_card = effect.get("card_id")
                    amount = effect.get("amount", 1)
                    entry = resolve_deck_entry(new_state["deck"], target_card)
                    if entry is not None:
                        new_state["deck"].extend([entry] * amount)
                elif e_type == "obtain_relic":
                    # Incomplete event semantics can contain obtain_relic
                    # without identifying the relic. Ignore it safely; the
                    # event scorer filters these effects from expectimax.
                    relic_id = effect.get("relic_id")
                    if not relic_id:
                        continue
                    replacements = {
                        "Black Blood": ("Burning Blood",),
                        "Ring of the Serpent": ("Ring of the Snake", "Ring of Snake"),
                        "Frozen Core": ("Cracked Core",),
                        "Holy Water": ("Pure Water",),
                    }
                    for starter in replacements.get(relic_id, ()):
                        if starter in new_state["relics"]:
                            new_state["relics"].remove(starter)
                    if relic_id not in new_state["relics"]:
                        new_state["relics"].append(relic_id)

                    if relic_id == "Empty Cage":
                        for _ in range(2):
                            ranked = self.rank_cards_for_purpose(new_state, "purge", 1)
                            if not ranked:
                                break
                            if ranked[0] not in new_state["deck"]:
                                break
                            new_state["deck"].remove(ranked[0])
                    elif relic_id == "Astrolabe":
                        # Random transform results are unknown; removing the three best
                        # transform candidates is a deterministic conservative approximation.
                        curse_ids = {
                            "Regret", "Pain", "Normality", "Doubt", "Shame", "Clumsy",
                            "Injury", "Writhe", "Decay", "Parasite", "Pride", "CurseOfTheBell",
                        }
                        cards = self.rank_cards_for_purpose(
                            new_state, "transform", 3, exclude_ids=curse_ids
                        )
                        for card in cards:
                            if card in new_state["deck"]:
                                new_state["deck"].remove(card)
                    elif relic_id == "Pandora's Box":
                        new_state["deck"] = [
                            card for card in new_state["deck"]
                            if base_card_name(card) not in STARTER_STRIKE_AND_DEFENDS
                        ]
                    elif relic_id == "Calling Bell" and not _should_block_curse_add("CurseOfTheBell"):
                        new_state["deck"].append("CurseOfTheBell")
                elif e_type == "lose_relic":
                    relic_id = effect.get("relic_id")
                    if relic_id in new_state["relics"]:
                        new_state["relics"].remove(relic_id)
                elif e_type == "add_card":
                    card_id = effect.get("card_id")
                    if _should_block_curse_add(card_id):
                        continue
                    _add_obtained_card(card_id, effect.get("amount", 1))
                elif e_type == "remove_curses":
                    exact_ids = set(effect.get("card_ids", []))
                    if exact_ids:
                        new_state["deck"] = [card for card in new_state["deck"] if card not in exact_ids]
                        continue
                    curse_names = {
                        "Regret", "Pain", "Normality", "Doubt", "Shame", "Clumsy",
                        "Injury", "Writhe", "Decay", "Parasite", "Pride",
                    }
                    excluded = set(effect.get("exclude_ids", []))
                    new_state["deck"] = [card for card in new_state["deck"] if card not in curse_names or card in excluded]
                elif e_type == "full_heal":
                    new_state["hp"] = new_state["max_hp"]
                elif e_type == "upgrade_all_cards":
                    new_state["deck"] = [card if "+" in card else card + "+1" for card in new_state["deck"]]
                elif e_type == "upgrade_matching":
                    # Upgrades starter Strike/Defend cards that can still upgrade.
                    # Already upgraded copies stay put, matching the vanilla
                    # effect, which only ever offers upgradeable cards.
                    new_state["deck"] = [
                        f"{base_card_name(card)}+1"
                        if base_card_name(card) in STARTER_STRIKE_AND_DEFENDS
                        and upgrade_level(card) == 0
                        else card
                        for card in new_state["deck"]
                    ]
                elif e_type == "replace_starter_strikes":
                    # Vampires replaces every starter Strike by tag, so the
                    # upgrade level is deliberately ignored here.
                    new_state["deck"] = [
                        card for card in new_state["deck"]
                        if base_card_name(card) not in STARTER_STRIKES
                    ]
                    new_state["deck"].extend([effect.get("card_id", "Bite")] * effect.get("amount", 5))
        elif action == "singing_bowl":
            new_state["max_hp"] += 2
            new_state["hp"] += 2
        elif action == "pick_card" and target:
            _add_obtained_card(target)
        elif action == "buy_card" and target:
            _add_obtained_card(target)
            new_state["gold"] -= choice.get("cost", 0)
        elif action == "buy_relic" and target:
            new_state["relics"].append(target)
            new_state["gold"] -= choice.get("cost", 0)
        elif action == "buy_potion":
            new_state["gold"] -= choice.get("cost", 0)
        elif action == "take_key" and target in {"ruby", "emerald", "sapphire"}:
            new_state.setdefault("keys", {})[target] = True
        elif action == "remove_card" and target:
            entry = resolve_deck_entry(new_state["deck"], target)
            if entry is not None:
                new_state["deck"].remove(entry)
            new_state["gold"] -= choice.get("cost", 0)
        elif action == "tosh" and target:
            entry = resolve_deck_entry(new_state["deck"], target)
            if entry is not None:
                new_state["deck"].remove(entry)
        elif action == "duplicate" and target:
            entry = resolve_deck_entry(new_state["deck"], target)
            if entry is not None:
                new_state["deck"].append(entry)
        elif action == "upgrade_card" and target:
            # A target the deck does not hold must not invent a card: every
            # other branch here no-ops on a miss, and the framework only sends
            # targets it resolved from the deck.
            entry = resolve_deck_entry(new_state["deck"], target)
            if entry is not None:
                base_name, level = parse_item_name(entry)
                new_state["deck"].remove(entry)
                if level == 0:
                    new_state["deck"].append(f"{base_name}+1")
                elif base_name == "Searing Blow":
                    new_state["deck"].append(upgraded_reference(entry))
                else:
                    new_state["deck"].append(entry)
        elif action == "rest":
            heal_amt = int(new_state["max_hp"] * 0.3)
            if "Regal Pillow" in new_state["relics"]:
                heal_amt += 15
            new_state["hp"] = min(new_state["hp"] + heal_amt, new_state["max_hp"])
        elif action == "skip":
            pass
            
        return new_state

    def recommend_choice(self, current_state, choices, exclude_purge_ids=None):
        """
        Evaluate multiple choices (e.g. Card rewards, Map paths)
        Returns the best choice dict.

        exclude_purge_ids: optional set of card IDs to skip during purge/transform evaluation.
        Used to exclude curse cards from transform candidates (curse→curse is a net loss).
        """
        best_score = -1.0
        best_choice = None

        for choice in choices:
            needs_purge_eval = False
            needs_upgrade_eval = False
            needs_duplicate_eval = False

            if choice.get("action") == "composite_event":
                for ef in choice.get("effects", []):
                    if ef.get("type") == "remove_card" and (not ef.get("card_id") or ef.get("card_id") == "unknown_card"):
                        needs_purge_eval = True
                    if ef.get("type") == "upgrade_card" and (not ef.get("card_id") or ef.get("card_id") == "unknown_card"):
                        needs_upgrade_eval = True
                    if ef.get("type") == "duplicate" and (not ef.get("card_id") or ef.get("card_id") == "unknown_card"):
                        needs_duplicate_eval = True
            elif choice.get("action") == "remove_card" and not choice.get("target"):
                needs_purge_eval = True
            elif choice.get("action") == "upgrade_card" and not choice.get("target"):
                needs_upgrade_eval = True
            elif choice.get("action") == "duplicate" and not choice.get("target"):
                needs_duplicate_eval = True

            if needs_purge_eval:
                max_score_for_choice = -1.0
                best_card_to_purge = None

                deck = current_state.get("deck", [])
                unique_cards = list(dict.fromkeys(deck))

                if not unique_cards:
                    hypothetical_state = self._apply_choice(current_state, choice)
                    score = self.evaluate_state(hypothetical_state)
                    max_score_for_choice = score
                else:
                    for card in unique_cards:
                        if exclude_purge_ids and card in exclude_purge_ids:
                            non_excluded = [c for c in unique_cards if c not in exclude_purge_ids]
                            if non_excluded:
                                continue

                        mod_choice = copy.deepcopy(choice)
                        if mod_choice.get("action") == "composite_event":
                            for ef in mod_choice.get("effects", []):
                                if ef.get("type") == "remove_card" and (not ef.get("card_id") or ef.get("card_id") == "unknown_card"):
                                    ef["card_id"] = card
                        elif mod_choice.get("action") == "remove_card":
                            mod_choice["target"] = card

                        hypo_state = self._apply_choice(current_state, mod_choice)
                        score = self.evaluate_state(hypo_state)
                        if score > max_score_for_choice:
                            max_score_for_choice = score
                            best_card_to_purge = card

                score = max_score_for_choice
                eval_choice = copy.deepcopy(choice)
                if best_card_to_purge:
                    eval_choice["_purge_intent_id"] = best_card_to_purge

                self._print_choice_score(f"Choice: composite_purge (remove={best_card_to_purge})", score)

            elif needs_upgrade_eval:
                max_score_for_choice = -1.0
                best_card_to_upgrade = None

                deck = current_state.get("deck", [])
                unique_unupgraded = list(
                    dict.fromkeys(c for c in deck if upgrade_level(c) == 0)
                )

                if not unique_unupgraded:
                    hypothetical_state = self._apply_choice(current_state, choice)
                    score = self.evaluate_state(hypothetical_state)
                    max_score_for_choice = score
                else:
                    for card in unique_unupgraded:
                        mod_choice = copy.deepcopy(choice)
                        if mod_choice.get("action") == "composite_event":
                            for ef in mod_choice.get("effects", []):
                                if ef.get("type") == "upgrade_card" and (not ef.get("card_id") or ef.get("card_id") == "unknown_card"):
                                    ef["card_id"] = card
                        elif mod_choice.get("action") == "upgrade_card":
                            mod_choice["target"] = card

                        hypo_state = self._apply_choice(current_state, mod_choice)
                        score = self.evaluate_state(hypo_state)
                        if score > max_score_for_choice:
                            max_score_for_choice = score
                            best_card_to_upgrade = card

                score = max_score_for_choice
                eval_choice = copy.deepcopy(choice)
                if best_card_to_upgrade:
                    eval_choice["_smith_intent_id"] = best_card_to_upgrade

                self._print_choice_score(f"Choice: composite_upgrade (upgrade={best_card_to_upgrade})", score)

            elif needs_duplicate_eval:
                max_score_for_choice = -1.0
                best_card_to_dup = None

                deck = current_state.get("deck", [])
                unique_cards = list(dict.fromkeys(deck))

                if not unique_cards:
                    hypothetical_state = self._apply_choice(current_state, choice)
                    score = self.evaluate_state(hypothetical_state)
                    max_score_for_choice = score
                else:
                    for card in unique_cards:
                        mod_choice = copy.deepcopy(choice)
                        if mod_choice.get("action") == "composite_event":
                            for ef in mod_choice.get("effects", []):
                                if ef.get("type") == "duplicate" and (not ef.get("card_id") or ef.get("card_id") == "unknown_card"):
                                    ef["card_id"] = card
                        else:
                            mod_choice["target"] = card

                        hypo_state = self._apply_choice(current_state, mod_choice)
                        score = self.evaluate_state(hypo_state)
                        if score > max_score_for_choice:
                            max_score_for_choice = score
                            best_card_to_dup = card

                score = max_score_for_choice
                eval_choice = copy.deepcopy(choice)
                if best_card_to_dup:
                    eval_choice["_duplicate_intent_id"] = best_card_to_dup

                self._print_choice_score(f"Choice: duplicate (copy={best_card_to_dup})", score)

            else:
                hypothetical_state = self._apply_choice(current_state, choice)
                score = self.evaluate_state(hypothetical_state)
                eval_choice = choice
                self._print_choice_score(f"Choice: {eval_choice}", score)

            if score > best_score:
                best_score = score
                best_choice = eval_choice

        return best_choice

    def rank_cards_for_purpose(self, current_state, purpose, n, exclude_ids=None):
        """Return the top-n card IDs for a given grid purpose.

        purpose behavior:
        - purge: remove each card, evaluate. Highest V = worst card.
        - transform: same as purge, but skips curse cards (curse→curse is no benefit).
        - upgrade: upgrade each unupgraded card, evaluate. Highest V = best upgrade target.
        - duplicate: copy each card, evaluate. Highest V = best copy target.

        exclude_ids: optional set of card IDs to skip (e.g. curse cards for transform).
        Falls back to all cards if excluding leaves nothing.
        """
        deck = current_state.get("deck", [])
        unique_cards = list(dict.fromkeys(deck))
        scored = []

        for card in unique_cards:
            if exclude_ids and _is_excluded(card, exclude_ids):
                non_excluded = [
                    c for c in unique_cards if not _is_excluded(c, exclude_ids)
                ]
                if non_excluded:
                    continue

            hypo_state = copy.deepcopy(current_state)
            if purpose in ("purge", "transform"):
                if card in hypo_state["deck"]:
                    hypo_state["deck"].remove(card)
            elif purpose == "upgrade":
                if upgrade_level(card) > 0:
                    continue
                if card in hypo_state["deck"]:
                    hypo_state["deck"].remove(card)
                    hypo_state["deck"].append(upgraded_reference(card))
            elif purpose == "duplicate":
                if card in hypo_state["deck"]:
                    hypo_state["deck"].append(card)

            score = self.evaluate_state(hypo_state)
            scored.append((card, score))

        scored.sort(key=lambda x: x[1], reverse=True)
        return [card for card, _ in scored[:n]]

    def shop_greedy_search(self, state, goods):
        """Greedy iterative shopping: repeatedly buy the single item that most improves V(state), until nothing helps."""
        bought_items = []
        remaining = list(goods)

        while True:
            base_score = self.evaluate_state(state)
            best_improvement = 0
            best_item = None

            for item in remaining:
                if state["gold"] >= item.get("cost", 0):
                    hypo_state = self._apply_choice(state, item)
                    score = self.evaluate_state(hypo_state)
                    improvement = score - base_score
                    if improvement > best_improvement:
                        best_improvement = improvement
                        best_item = item

            if best_item:
                state = self._apply_choice(state, best_item)
                bought_items.append(best_item)
                remaining.remove(best_item)
                print(f"Bought {best_item['target']} for {best_item['cost']}g (V: {base_score:.4f} -> {base_score + best_improvement:.4f})")
            else:
                break

        return bought_items

# Example usage
if __name__ == "__main__":
    engine = STSInferenceEngine()
    dummy_state = {
        "deck": ["Strike_R", "Strike_R", "Defend_R"],
        "relics": ["Burning Blood"],
        "hp": 50,
        "max_hp": 80,
        "gold": 150,
        "floor": 5,
        "ascension": 20
    }
    
    print("--- Testing Card Reward ---")
    choices = [
        {"action": "pick_card", "target": "Demon Form"},
        {"action": "pick_card", "target": "Shrug It Off"},
        {"action": "skip", "target": None}
    ]
    best = engine.recommend_choice(dummy_state, choices)
    print(f"Best Choice: {best}")
    
    print("\n--- Testing Greedy Shop Search ---")
    goods = [
        {"action": "buy_card", "target": "Apotheosis", "cost": 200}, # Too expensive
        {"action": "buy_relic", "target": "Orichalcum", "cost": 100},
        {"action": "buy_card", "target": "Pommel Strike", "cost": 45},
        {"action": "remove_card", "target": "Strike_R", "cost": 50}
    ]
    # Since weights are random, behaviour is random.
    bought = engine.shop_greedy_search(dummy_state, goods)
    print(f"Finished shopping. Items bought: {bought}")
