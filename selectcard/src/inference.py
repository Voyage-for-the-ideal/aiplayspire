import os
import torch
import copy
from src.model import STSValueNetwork
from src.dataset import SimpleTokenizer
from src.config import Config
import re

class DummyTokenizer:
    def encode(self, deck, relics):
        seq_len = len(deck) + len(relics)
        seq_tokens = torch.randint(1, 100, (1, seq_len))
        upgrades = torch.zeros((1, seq_len), dtype=torch.long)
        counts = torch.ones((1, seq_len), dtype=torch.long)
        return seq_tokens, upgrades, counts

class InferenceTokenizer:
    """包装 SimpleTokenizer 并在 inference 阶段执行与训练对齐的 encode 逻辑"""
    def __init__(self, vocab_path, max_seq_len=64):
        self.tokenizer = SimpleTokenizer()
        self.tokenizer.load(vocab_path)
        self.max_seq_len = max_seq_len

    def encode(self, deck, relics):
        from collections import Counter
        
        # 处理卡牌升级形态 (去除+1等后缀，分离出基础名字和升级数)
        # 注意此逻辑必须和训练时构建 dataset.py 中的完全一致
        card_items = []
        for c in deck:
            if "+" in c:
                parts = c.split("+")
                name = parts[0]
                upg = min(int(parts[1]) if parts[1].isdigit() else 1, 14)
            else:
                name = c
                upg = 0
            if name != "AscendersBane": # 如果训练时排除了某些初始牌
                card_items.append({"name": name, "upg": upg, "type": "card"})
                
        relic_items = [{"name": r, "upg": 0, "type": "relic"} for r in relics]
        all_items = card_items + relic_items
        
        # 统计同样名字同样升级的卡牌个数
        item_counts = Counter([(item["name"], item["upg"]) for item in all_items])
        
        seq, upg_seq, cnt_seq = [], [], []
        for (name, upg), count in item_counts.items():
            tok_id = self.tokenizer.item2id.get(name, 1) # 1 is [MASK] or UNK usually if not found
            seq.append(tok_id)
            upg_seq.append(upg)
            cnt_seq.append(min(count, 9)) # max_count
            
        # Padding
        if len(seq) < self.max_seq_len:
            pad_len = self.max_seq_len - len(seq)
            seq += [0] * pad_len
            upg_seq += [0] * pad_len
            cnt_seq += [0] * pad_len
        else:
            seq = seq[:self.max_seq_len]
            upg_seq = upg_seq[:self.max_seq_len]
            cnt_seq = cnt_seq[:self.max_seq_len]
            
        return (
            torch.tensor([seq], dtype=torch.long),
            torch.tensor([upg_seq], dtype=torch.long),
            torch.tensor([cnt_seq], dtype=torch.long)
        )

def extract_global_features(state):
    # state is a dict: {'hp': 50, 'max_hp': 70, 'gold': 150, 'floor': 12, 'ascension': 20, ...}
    hp = float(state.get('hp', 0.0))
    gold = float(state.get('gold', 0.0))
    floor = float(state.get('floor', 1.0))
    ascension = float(state.get('ascension', 20.0))

    # Same normalizations as STSDataset
    floor_norm = max(0.0, min(floor / 55.0, 1.0))
    ascension_norm = max(0.0, min(ascension / 20.0, 1.0))
    hp_std = (hp - 50.2277) / 158.0118
    gold_std = (gold - 222.6476) / 3719.5747

    global_feats = [floor_norm, hp_std, gold_std, ascension_norm, 0.0, 0.0, 0.0, 0.0]
    return torch.tensor([global_feats], dtype=torch.float32)

class STSInferenceEngine:
    def __init__(self, model_path=None, vocab_path=None):
        if not vocab_path and model_path:
            vocab_path = os.path.join(os.path.dirname(model_path), "vocab.json")
            
# Infer exact vocab_size from checkpoint if available, to avoid embedding size mismatch
        state_dict = None
        if model_path and os.path.exists(model_path):
            state_dict = torch.load(model_path, map_location="cpu", weights_only=True)

        # load tokenizer first to know vocab size fallback
        if vocab_path and os.path.exists(vocab_path):
            self.tokenizer = InferenceTokenizer(vocab_path, max_seq_len=64)
            vocab_size = len(self.tokenizer.tokenizer) + Config.VOCAB_BUFFER
        else:
            self.tokenizer = DummyTokenizer()  # Fallback only
            vocab_size = 1000

        if state_dict is not None and "token_emb.weight" in state_dict:
            vocab_size = state_dict["token_emb.weight"].shape[0]

        # init model
        self.model = STSValueNetwork(
            vocab_size=vocab_size,
            max_upgrade=Config.MAX_UPGRADE,
            max_count=Config.MAX_COUNT,
            d_model=Config.D_MODEL,
            n_heads=Config.N_HEADS,
            n_layers=Config.N_LAYERS,
            num_global_features=Config.NUM_GLOBAL_FEATURES,
            dropout=Config.DROPOUT
        )
        if state_dict is not None:
            self.model.load_state_dict(state_dict)
        self.model.eval()
        
    def evaluate_state(self, state):
        """Evaluate a single state dictionary and return survival probability."""
        seq_tokens, upgrades, counts = self.tokenizer.encode(state['deck'], state['relics'])
        global_feats = extract_global_features(state)

        with torch.no_grad():
            logits = self.model(seq_tokens, upgrades, counts, global_feats)
            prob = torch.sigmoid(logits)
        return prob.item()

    def evaluate_state_logits(self, state):
        """Evaluate a single state dictionary and return raw logits."""
        seq_tokens, upgrades, counts = self.tokenizer.encode(state['deck'], state['relics'])
        global_feats = extract_global_features(state)
        
        with torch.no_grad():
            logits = self.model(seq_tokens, upgrades, counts, global_feats)
        return logits.item()
        
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
                    import random
                    amount = effect.get("amount", 1)
                    unupgraded = [c for c in new_state["deck"] if "+" not in c]
                    actual = min(amount, len(unupgraded))
                    if actual > 0:
                        chosen = random.sample(unupgraded, actual)
                        for tc in chosen:
                            new_state["deck"].remove(tc)
                            new_state["deck"].append(tc + "+1")
                elif e_type == "upgrade_card":
                    target_card = effect.get("card_id")
                    if target_card and target_card in new_state["deck"]:
                        new_state["deck"].remove(target_card)
                        import re
                        match = re.match(r"(Searing Blow)\+(\d+)", target_card)
                        if match:
                            new_state["deck"].append(f"Searing Blow+{int(match.group(2)) + 1}")
                        elif target_card == "Searing Blow":
                            new_state["deck"].append("Searing Blow+1")
                        elif not target_card.endswith("+1"):
                            new_state["deck"].append(target_card + "+1")
                        else:
                            new_state["deck"].append(target_card)
                elif e_type == "obtain_relic":
                    new_state["relics"].append(effect["relic_id"])
                elif e_type == "add_card":
                    card_id = effect.get("card_id")
                    if _should_block_curse_add(card_id):
                        continue
                    new_state["deck"].append(card_id)
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
        elif action == "upgrade_card" and target:
            if target in new_state["deck"]:
                new_state["deck"].remove(target)
                import re
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

    def recommend_choice(self, current_state, choices):
        """
        Evaluate multiple choices (e.g. Card rewards, Map paths)
        Returns the best choice dict.
        """
        best_score = -1.0
        best_choice = None
        
        for choice in choices:
            needs_purge_eval = False
            if choice.get("action") == "composite_event":
                for ef in choice.get("effects", []):
                    if ef.get("type") == "remove_card" and (not ef.get("card_id") or ef.get("card_id") == "unknown_card"):
                        needs_purge_eval = True
                        break
            elif choice.get("action") == "remove_card" and not choice.get("target"):
                needs_purge_eval = True

            if needs_purge_eval:
                max_score_for_choice = -1.0
                best_card_to_purge = None
                
                deck = current_state.get("deck", [])
                unique_cards = list(set(deck))
                
                if not unique_cards:
                    hypothetical_state = self._apply_choice(current_state, choice)
                    score = self.evaluate_state(hypothetical_state)
                    max_score_for_choice = score
                else:
                    for card in unique_cards:
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
                
                print(f"Choice: composite_purge (remove={best_card_to_purge}) -> V(S') = {score:.4f}")
                
            else:
                hypothetical_state = self._apply_choice(current_state, choice)
                score = self.evaluate_state(hypothetical_state)
                eval_choice = choice
                print(f"Choice: {eval_choice} -> V(S') = {score:.4f}")

            if score > best_score:
                best_score = score
                best_choice = eval_choice

        return best_choice

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
