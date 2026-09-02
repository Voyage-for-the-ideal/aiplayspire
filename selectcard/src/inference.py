import torch
import copy
try:
    from .model import STSValueNetwork
    from .checkpointing import load_checkpoint
    from .encoding import ItemVocabulary, encode_items
    from .dataset import GlobalFeatureEncoder
    from .config import Config
    from .data_contract import HAZARD_ENDPOINTS, HAZARD_OUTPUT_DIM
    from .boss_context import NUM_BOSS_IDS, boss_id, get_visible_boss
except ImportError:
    from model import STSValueNetwork
    from checkpointing import load_checkpoint
    from encoding import ItemVocabulary, encode_items
    from dataset import GlobalFeatureEncoder
    from config import Config
    from data_contract import HAZARD_ENDPOINTS, HAZARD_OUTPUT_DIM
    from boss_context import NUM_BOSS_IDS, boss_id, get_visible_boss
import re


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
                "num_bosses": NUM_BOSS_IDS,
                "dropout": Config.DROPOUT,
                "global_conditioning": Config.GLOBAL_CONDITIONING,
                "norm_position": Config.NORM_POSITION,
            }
            self.model = STSValueNetwork(**architecture)
        self.tokenizer = InferenceTokenizer(
            vocabulary,
            max_seq_len=Config.MAX_SEQ_LEN,
            max_upgrade=architecture["max_upgrade"],
            max_count=architecture["max_count"],
        )
        self.model.eval()

    def _global_features(self, state):
        values = self.feature_encoder.transform_state(state)
        return torch.tensor([values], dtype=torch.float32)

    def _boss_id(self, state):
        return torch.tensor([boss_id(get_visible_boss(state))], dtype=torch.long)
        
    def _evaluate_state_distribution(self, state, heart_bonus_floors=None):
        seq_tokens, upgrades, counts = self.tokenizer.encode(state['deck'], state['relics'])
        global_feats = self._global_features(state)
        boss_ids = self._boss_id(state)

        with torch.no_grad():
            logits = self.model(seq_tokens, upgrades, counts, global_feats, boss_ids)
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
        seq_tokens, upgrades, counts = self.tokenizer.encode(state['deck'], state['relics'])
        global_feats = self._global_features(state)
        boss_ids = self._boss_id(state)
        
        with torch.no_grad():
            logits = self.model(seq_tokens, upgrades, counts, global_feats, boss_ids)
        values = logits.squeeze(0).tolist()
        return {
            "hazards": dict(zip(HAZARD_ENDPOINTS, values[:HAZARD_OUTPUT_DIM])),
            "heart_conditional": values[HAZARD_OUTPUT_DIM],
        }

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
            if "curse" not in normalized and normalized not in {"regret", "pain", "normality", "doubt", "shame", "clumsy", "injury", "writhe", "curse of the bell"}:
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
                    if card_id and card_id in new_state["deck"]:
                        new_state["deck"].remove(card_id)
                    elif not card_id:
                        for _ in range(effect.get("amount", 1)):
                            if "Strike_R" in new_state["deck"]:
                                new_state["deck"].remove("Strike_R")
                            elif "Strike_G" in new_state["deck"]:
                                new_state["deck"].remove("Strike_G")
                            elif "Strike_B" in new_state["deck"]:
                                new_state["deck"].remove("Strike_B")
                            elif "Strike_P" in new_state["deck"]:
                                new_state["deck"].remove("Strike_P")
                            elif "Strike" in new_state["deck"]:
                                new_state["deck"].remove("Strike")
                            elif "Defend_R" in new_state["deck"]:
                                new_state["deck"].remove("Defend_R")
                            elif "Defend_G" in new_state["deck"]:
                                new_state["deck"].remove("Defend_G")
                            elif "Defend_B" in new_state["deck"]:
                                new_state["deck"].remove("Defend_B")
                            elif "Defend_P" in new_state["deck"]:
                                new_state["deck"].remove("Defend_P")
                            elif "Defend" in new_state["deck"]:
                                new_state["deck"].remove("Defend")
                elif e_type == "random_upgrade":
                    amount = effect.get("amount", 1)
                    chosen = self.rank_cards_for_purpose(new_state, "upgrade", amount)
                    for target_card in chosen:
                        if target_card in new_state["deck"] and "+" not in target_card:
                            new_state["deck"].remove(target_card)
                            new_state["deck"].append(target_card + "+1")
                elif e_type == "upgrade_card":
                    target_card = effect.get("card_id")
                    if target_card and target_card in new_state["deck"]:
                        new_state["deck"].remove(target_card)
                        match = re.match(r"(Searing Blow)\+(\d+)", target_card)
                        if match:
                            new_state["deck"].append(f"Searing Blow+{int(match.group(2)) + 1}")
                        elif target_card == "Searing Blow":
                            new_state["deck"].append("Searing Blow+1")
                        elif not target_card.endswith("+1"):
                            new_state["deck"].append(target_card + "+1")
                    else:
                        new_state["deck"].append(target_card)
                elif e_type == "duplicate":
                    target_card = effect.get("card_id")
                    amount = effect.get("amount", 1)
                    if target_card and target_card in new_state["deck"]:
                        new_state["deck"].extend([target_card] * amount)
                elif e_type == "obtain_relic":
                    relic_id = effect["relic_id"]
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
                        starters = {
                            "Strike_R", "Strike_G", "Strike_B", "Strike_P", "Strike",
                            "Defend_R", "Defend_G", "Defend_B", "Defend_P", "Defend",
                        }
                        new_state["deck"] = [
                            card for card in new_state["deck"]
                            if re.sub(r"\+\d*$", "", card) not in starters
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
                    new_state["deck"].extend([card_id] * effect.get("amount", 1))
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
                    starters = {"Strike_R", "Strike_G", "Strike_B", "Strike_P", "Strike", "Defend_R", "Defend_G", "Defend_B", "Defend_P", "Defend"}
                    new_state["deck"] = [card + "+1" if card in starters else card for card in new_state["deck"]]
                elif e_type == "replace_starter_strikes":
                    starters = {"Strike_R", "Strike_G", "Strike_B", "Strike_P", "Strike"}
                    new_state["deck"] = [card for card in new_state["deck"] if card not in starters]
                    new_state["deck"].extend([effect.get("card_id", "Bite")] * effect.get("amount", 5))
        elif action == "pick_card" and target:
            new_state["deck"].append(target)
        elif action == "buy_card" and target:
            new_state["deck"].append(target)
            new_state["gold"] -= choice.get("cost", 0)
        elif action == "buy_relic" and target:
            new_state["relics"].append(target)
            new_state["gold"] -= choice.get("cost", 0)
        elif action == "buy_potion":
            new_state["gold"] -= choice.get("cost", 0)
        elif action == "remove_card" and target:
            if target in new_state["deck"]:
                new_state["deck"].remove(target)
            new_state["gold"] -= choice.get("cost", 0)
        elif action == "tosh" and target:
            if target in new_state["deck"]:
                new_state["deck"].remove(target)
        elif action == "duplicate" and target:
            if target in new_state["deck"]:
                new_state["deck"].append(target)
        elif action == "upgrade_card" and target:
            if target in new_state["deck"]:
                new_state["deck"].remove(target)
                match = re.match(r"(Searing Blow)\+(\d+)", target)
                if match:
                    new_state["deck"].append(f"Searing Blow+{int(match.group(2)) + 1}")
                elif target == "Searing Blow":
                    new_state["deck"].append("Searing Blow+1")
                elif not target.endswith("+1"):
                    new_state["deck"].append(target + "+1")
                else:
                    new_state["deck"].append(target)
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
                unique_unupgraded = list(dict.fromkeys(c for c in deck if "+" not in c))

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
            if exclude_ids and card in exclude_ids:
                non_excluded = [c for c in unique_cards if c not in exclude_ids]
                if non_excluded:
                    continue

            hypo_state = copy.deepcopy(current_state)
            if purpose in ("purge", "transform"):
                if card in hypo_state["deck"]:
                    hypo_state["deck"].remove(card)
            elif purpose == "upgrade":
                if "+" in card:
                    continue
                if card in hypo_state["deck"]:
                    hypo_state["deck"].remove(card)
                    match = re.match(r"(Searing Blow)\+(\d+)", card)
                    if match:
                        hypo_state["deck"].append(f"Searing Blow+{int(match.group(2)) + 1}")
                    elif card == "Searing Blow":
                        hypo_state["deck"].append("Searing Blow+1")
                    elif not card.endswith("+1"):
                        hypo_state["deck"].append(card + "+1")
                    else:
                        hypo_state["deck"].append(card)
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
