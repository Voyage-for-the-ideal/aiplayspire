"""Opt-in structured policy adapter; default agent behavior stays unchanged."""
import logging
from .models import ActionType, GameAction

LOG = logging.getLogger(__name__)


def candidates_for(state):
    """Complete structured menu or None; never embed localized button text."""
    from selectcard.policy.contracts import DECISIONS
    screen = state.screen_type
    if screen not in DECISIONS or state.room_phase == "COMBAT":
        return None
    rows = []
    def add(action_id, kind, command, legal=True, **features):
        rows.append(dict(action_id=action_id, kind=kind, legal=bool(legal), command=command, **features))
    def choice(identity, kind, index, **features):
        add(identity, kind, {"type": "choose", "choice_index": index}, **features)
    choices = state.choice_list or []
    if screen == "SHOP_SCREEN":
        if not state.shop_inventory or state.shop_purge_cost is None:
            return None
        for item in state.shop_inventory:
            index = item.get("choice_index")
            if item.get("sold_out"):
                continue
            choice(item["slot_id"], "buy_" + item["kind"], index,
                   legal=item.get("legal", False) and index is not None,
                   entity_id=item["id"], entity_kind=item["kind"], price=item["price"], upgrades=item.get("upgrades", 0))
        if state.shop_purge_available:
            legal = state.player.gold >= state.shop_purge_cost
            choice("purge", "purge", 0 if legal else None, legal=legal, price=state.shop_purge_cost)
    elif screen == "CARD_REWARD":
        if not state.card_reward_cards:
            return None
        for i, card in enumerate(state.card_reward_cards):
            choice("card:" + str(i), "take_card", i, entity_id=card.id, entity_kind="card", upgrades=card.upgrades, misc=card.misc)
        if "bowl" in choices:
            choice("bowl", "bowl", choices.index("bowl"), amount=2)
    elif screen == "BOSS_REWARD":
        for i, identity in enumerate(choices):
            choice("relic:" + str(i), "take_relic", i, entity_id=identity, entity_kind="relic")
    elif screen == "REST":
        known = {"RestOption", "SmithOption", "RecallOption", "LiftOption", "DigOption", "TokeOption"}
        if any(c not in known for c in choices):
            return None
        for i, kind in enumerate(choices):
            choice(kind, kind, i)
    elif screen == "MAP":
        if len(state.current_map_choices) != len(choices):
            return None
        for node in state.current_map_choices:
            choice(f"map:{node.x}:{node.y}", "map", node.choice_index, x=node.x, y=node.y, purpose=node.symbol)
    elif screen == "GRID":
        if state.grid_purpose in ("", "unknown"):
            return None
        for card in state.grid_cards:
            if card.choice_index is None:
                return None
            selected = any(c.uuid == card.uuid for c in state.grid_selected_cards)
            choice("target:" + card.uuid, "deselect_card" if selected else "select_card", card.choice_index, entity_id=card.id,
                   entity_kind="card", upgrades=card.upgrades, purpose=state.grid_purpose, misc=card.misc, is_bottled=card.is_bottled)
    elif screen == "EVENT":
        event = state.event
        if event is None or event.semantics_status != "KNOWN":
            return None
        for option in event.choices:
            if option.action_index is None:
                if option.enabled:
                    return None
                continue
            if option.kind == "UNKNOWN":
                return None
            choice("event:" + str(option.button_index), option.kind, option.action_index,
                   legal=option.enabled, purpose=event.id + ":" + event.phase,
                   outcomes=[o.model_dump(exclude_none=True) for o in option.outcomes])
    elif screen == "CHEST":
        if choices:
            choice("open", "open_chest", 0)
    elif screen == "COMBAT_REWARD":
        if len(state.reward_choices) != len(choices):
            return None
        for reward in state.reward_choices:
            potion_legal = reward.type.upper() != "POTION" or (any(p.is_empty for p in state.potions) and not any(r.id == "Sozu" for r in state.relics))
            choice("reward:" + str(reward.choice_index), "reward_" + reward.type, reward.choice_index,
                   legal=potion_legal, amount=reward.amount or 0, entity_id=reward.relic_id or reward.potion_id,
                   entity_kind="relic" if reward.relic_id else "potion")
    if state.can_proceed:
        add("confirm" if screen == "GRID" else "proceed", "confirm" if screen == "GRID" else "leave", {"type": "proceed"})
    if state.can_cancel:
        add("leave", "skip" if screen == "CARD_REWARD" else "cancel", {"type": "cancel"})
    # The shop's legacy leave entry is an alias of the same semantic action.
    if screen == "SHOP_SCREEN" and choices and choices[-1] == "leave" and not any(r["action_id"] == "leave" for r in rows):
        choice("leave", "leave", len(choices) - 1)
    for potion in state.potions:
        if potion.is_empty:
            continue
        if potion.can_use and not potion.requires_target:
            add("use_potion:" + str(potion.index), "use_potion", {"type": "potion", "potion_index": potion.index}, entity_id=potion.id, entity_kind="potion")
        if potion.can_discard:
            add("discard_potion:" + str(potion.index), "discard_potion", {"type": "discard_potion", "potion_index": potion.index}, entity_id=potion.id, entity_kind="potion")
    return rows if rows and any(row["legal"] for row in rows) else None


def chosen_id(state, action, candidates):
    command = action.model_dump(mode="json", exclude_none=True)
    for candidate in candidates:
        if candidate["command"] == command:
            return candidate["action_id"]
    # Normalize existing agent's aliases without manufacturing extra actions.
    if action.type in (ActionType.SKIP, ActionType.CANCEL):
        return next((r["action_id"] for r in candidates if r["action_id"] == "leave"), None)
    if action.type == ActionType.CHOOSE and action.choice_index is not None:
        choices = state.choice_list or []
        if action.choice_index < len(choices) and choices[action.choice_index] in ("leave", "confirm"):
            target = choices[action.choice_index]
            return next((r["action_id"] for r in candidates if r["action_id"] == target), None)
    return None


class PolicyAgent:
    def __init__(self, baseline, checkpoint, *, mode="shadow", enabled_types=()):
        from selectcard.policy.train import load_checkpoint
        self.baseline = baseline
        self.model, self.artifact = load_checkpoint(checkpoint)
        self.model.eval()
        self.mode = mode
        self.enabled_types = set(enabled_types)
        self.last_comparison = None
        self.last_behavior = None

    def choose_action(self, state):
        self.last_comparison = None
        self.last_behavior = {"source": "baseline"}
        try:
            result = self._score(state)
        except Exception:
            LOG.warning("Policy inference unsupported/failed; retaining baseline", exc_info=True)
            result = None
            self.last_behavior["reason"] = "inference_failed"
        if result is not None and self.mode == "policy" and state.screen_type in self.enabled_types:
            self.last_behavior = {"source": "policy", "screen": state.screen_type}
            # The baseline may resume on the next screen (notably SHOP_ROOM).
            # Keep its screen bookkeeping current even when policy takes over.
            if self.baseline is not None:
                sync = getattr(self.baseline, "_sync_pending_event_state", None)
                if callable(sync):
                    sync(state)
                self.baseline.last_screen_type = state.screen_type
            return result[0]
        if "reason" not in self.last_behavior:
            self.last_behavior["reason"] = "unsupported_or_untrained" if result is None else "shadow_or_type_disabled"
        # Baseline exceptions propagate normally; never call a network-backed
        # fallback twice because it raised inside the policy's try block.
        baseline_action = self.baseline.choose_action(state)
        if result is not None:
            self.last_comparison["baseline"] = chosen_id(state, baseline_action, result[1]) if baseline_action else None
            LOG.info("policy_shadow %s", self.last_comparison)
        return baseline_action

    def _score(self, state):
        from selectcard.policy.contracts import observable_state
        from selectcard.policy.encoding import encode_batch, action_parts
        from selectcard.policy.profiles import RUN_PROFILE, MIXED_PROFILE, project_example
        import torch
        candidates = candidates_for(state)
        if candidates is None or state.screen_type not in self.artifact["supported_decisions"]:
            return None
        if self.artifact["input_profile"] == RUN_PROFILE:
            # Potion controls belong to a separate live flow. A card reward
            # needs its explicit skip action; boss rewards can force a choice.
            allowed = {"take_card", "skip", "bowl"} if state.screen_type == "CARD_REWARD" else {"take_relic", "leave", "cancel"}
            candidates = [dict(c) for c in candidates if c["kind"] in allowed]
            for candidate in candidates:
                if state.screen_type == "BOSS_REWARD" and candidate["kind"] == "cancel":
                    candidate["kind"] = "leave"
            exit_count = sum(c["kind"] in ("leave", "skip") for c in candidates)
            if exit_count > 1 or (state.screen_type == "CARD_REWARD" and exit_count != 1):
                return None
        elif self.artifact["input_profile"] == MIXED_PROFILE and state.screen_type == "BOSS_REWARD":
            candidates = [dict(c, kind="leave") if c["kind"] == "cancel" else c for c in candidates]
        if self.artifact["input_profile"] == MIXED_PROFILE:
            for candidate in candidates:
                if candidate["legal"] and any(token not in self.artifact["vocabulary"] for token in action_parts(candidate)[0]):
                    raise ValueError("unseen candidate semantics; retaining baseline")
        for candidate in candidates:
            if candidate.get("entity_id") and candidate["legal"]:
                token = candidate.get("entity_kind", "card") + ":" + candidate["entity_id"]
                if token not in self.artifact["vocabulary"]:
                    raise ValueError("unseen candidate entity; collect/BC before enabling this choice")
        row = project_example(dict(pre_state=observable_state(state), candidates=candidates), self.artifact["input_profile"])
        batch = encode_batch([row], self.artifact["vocabulary"])
        with torch.no_grad():
            probability = self.model(batch).softmax(dim=-1)[0]
        if not torch.isfinite(probability).all():
            raise ValueError("nonfinite policy prediction")
        selected = candidates[probability.argmax().item()]
        self.last_comparison = {"selected": selected["action_id"], "probabilities": {c["action_id"]: p for c, p in zip(candidates, probability.tolist())}}
        return GameAction(**selected["command"]), candidates
