"""Native menu candidates scored by the existing format-12 V(S) and map router."""
import copy
import json

from ..llm_agent_parts.event_expectimax import generated_card_choice, score_choice
from ..llm_agent_parts.value_payload import value_state_payload
from ..routing.planner import RoutePlanner


class HeadlessValuePolicy:
    def __init__(self, model_path):
        import torch
        from selectcard.src.inference import STSInferenceEngine
        torch.set_num_threads(1)
        self.engine = STSInferenceEngine(model_path)
        self.router = RoutePlanner()
        self._match_memory = {}
        self._skipped_rewards = set()

    def begin_episode(self):
        # These contain visible facts from one run, never cross-run policy state.
        self._match_memory.clear()
        self._skipped_rewards.clear()

    def choose(self, frame):
        action, metadata = self._choose(frame)
        if json.loads(action.evidence_json)["kind"] == "skip_card":
            self._skipped_rewards.add((frame.run_id, tuple(c.uuid for c in frame.state.card_reward_cards)))
        return action, metadata

    def declined_offers(self, run_id, raw):
        """Only actual skipped candidate UUID groups still visible in this reward."""
        return [r['card_uuids'] for r in raw.get('interaction',{}).get('rewards',[])
                if r.get('type')=='CARD' and (run_id,tuple(r['card_uuids'])) in self._skipped_rewards]

    def _choose(self, frame):
        if not frame.actions: raise ValueError("No semantically legal menu action")
        state = frame.state
        if frame.actions[0].native_method == "chooseMatchCard":
            facts = json.loads(frame.raw_json)["interaction"]["match_game"]
            memory = self._match_memory.setdefault((frame.run_id, state.act, state.floor), {})
            for row in facts["cards"]:
                if row["face_up"]: memory[row["slot"]] = row["card_id"]
            last = facts.get("last_flip")
            if last: memory[last["slot"]] = last["card_id"]
            face = [r for r in facts["cards"] if r["face_up"]]
            def priority(action):
                slot = action.native_args[0]; identity = memory.get(slot)
                pair = identity is not None and (any(r["card_id"] == identity for r in face) if face else sum(memory.get(a.native_args[0]) == identity for a in frame.actions) > 1)
                return (int(pair), int(slot not in memory), -slot)
            return max(frame.actions, key=priority), {"policy": "visible-match-memory"}
        if state.screen_type == "MAP":
            route = self.router.choose(state)
            choices = [a for a in frame.actions if a.api_action.choice_index == route.choice_index]
            if len(choices) != 1: raise ValueError("Map router produced no unique legal action")
            return choices[0], {"policy": "existing-map-router", "mode": route.mode}
        # Reward consumption is mandatory bookkeeping; choices inside rewards are scored later.
        if state.screen_type == "COMBAT_REWARD":
            rewards = [a for a in frame.actions if a.native_method != "proceed" and
                       (frame.run_id, tuple(json.loads(a.evidence_json).get("offered_cards", []))) not in self._skipped_rewards]
            if rewards: return rewards[0], {"policy": "reward-bookkeeping"}
            proceeds = [a for a in frame.actions if a.native_method == "proceed"]
            if proceeds: return proceeds[0], {"policy": "all-rewards-handled-or-declined"}
            raise ValueError('All visible rewards declined but no source-backed exit is available')
        if len(frame.actions) == 1: return frame.actions[0], {"policy": "forced-legal-control"}
        if state.screen_type in ("GRID", "HAND_SELECT"):
            confirmations = [a for a in frame.actions if a.api_action.type.value == "proceed"]
            if confirmations: return confirmations[0], {"policy": "selection-confirm"}
        payload = value_state_payload(state); rows = []
        for action in frame.actions:
            evidence = json.loads(action.evidence_json); kind = evidence["kind"]
            score = None; choice = None
            if kind in ("skip_card", "leave_shop", "skip_boss_relic", "cancel_purge", "proceed"):
                choice = {"action": "skip"}
            elif kind == "take_card":
                c = evidence["card"]; choice = {"action": "pick_card", "target": c["id"] + ("+"+str(c["upgrades"]) if c["upgrades"] else "")}
            elif kind == "take_combat_card":
                c = evidence["card"]; score = max(0,c.get("base_damage",0)) + max(0,c.get("base_block",0)) + 2*max(0,c.get("base_magic_number",0))
            elif kind == "bowl": choice = {"action": "singing_bowl"}
            elif kind == "take_boss_relic": choice = {"action": "buy_relic", "target": evidence["id"], "cost": 0}
            elif kind == "buy": choice = {"action": "buy_"+evidence["item_kind"], "target": evidence["id"], "cost": evidence["price"]}
            elif kind == "campfire":
                option = evidence["option"]
                if option == "RestOption": choice = {"action": "rest"}
                elif option == "RecallOption":
                    choice = {"action": "take_key", "target": "ruby"}
                    if state.act >= 3 and not state.keys.ruby: score = 10.0
                elif option == "SmithOption":
                    values = [self.engine.evaluate_state(self.engine._apply_choice(payload, {"action": "upgrade_card", "target": c.reference})) for c in state.deck if c.can_upgrade]
                    score = max(values) if values else -1.0
                elif option == "LiftOption":
                    after = copy.deepcopy(payload)
                    for r in after["relic_states"]:
                        if r["id"] == "Girya": r["counter"] += 1
                    score = self.engine.evaluate_state(after)
            elif kind in ("select", "purge"):
                if evidence.get("selected"): continue
                c = next((c for c in state.grid_cards if c.uuid == evidence["uuid"]), None)
                if c is not None:
                    purpose = evidence.get("purpose", "purge")
                    if evidence.get('library_draft') is True:
                        choice = generated_card_choice(c.reference, payload)
                    elif purpose=='bonfire_offer':
                        effects=[{'type':'remove_card','card_id':c.reference}]
                        if c.rarity=='RARE':effects += [{'type':'gain_max_hp','amount':10},{'type':'full_heal'}]
                        elif c.rarity=='UNCOMMON':effects += [{'type':'full_heal'}]
                        elif c.rarity in ('COMMON','SPECIAL'):effects += [{'type':'gain_hp','amount':5}]
                        elif c.rarity=='CURSE':effects += [{'type':'obtain_relic','relic_id':
                            'Circlet' if any(r.id=='Spirit Poop' for r in state.relics) else 'Spirit Poop'}]
                        choice={'action':'composite_event','effects':effects}
                    elif state.room_phase != "COMBAT" and purpose in ("upgrade", "purge", "duplicate", "transform"):
                        choice = {"action": {"upgrade": "upgrade_card", "purge": "remove_card", "duplicate": "duplicate", "transform": "tosh"}[purpose], "target": c.reference}
                    else:
                        power = max(0, c.base_damage or 0) + max(0, c.base_block or 0) + 2*max(0, c.base_magic_number or 0)
                        power += 8 if c.type == "POWER" else 0
                        power -= 100 if c.type in ("STATUS", "CURSE") else 0
                        score = -power if purpose in ("discard", "exhaust", "purge", "scry") else power
            elif kind == "event" and state.event and state.event.class_name in ("TheLibrary", "KnowingSkull"):
                option = next(c for c in state.event.choices if c.button_index == evidence["option"]["button_index"])
                scored = score_choice(state, option, self.engine, payload)
                if scored is not None:
                    score = scored[0]
            elif kind == "event":
                values = []
                for outcome in evidence["option"]["outcomes"]:
                    effects=outcome['effects']
                    receipt=evidence.get('we_meet_again',{})
                    if receipt.get('phase')=='INTRO' and receipt.get('button')==2:
                        # The value payload has references, not UUIDs: resolve the
                        # exact source-selected instance before simulating removal.
                        target=next(c for c in state.deck if c.uuid==receipt['cost']['uuid'])
                        effects=copy.deepcopy(effects)
                        for effect in effects:
                            if effect['type']=='remove_card':effect['card_id']=target.reference
                    hypothetical = self.engine._apply_choice(payload, {"action": "composite_event", "effects": effects})
                    values.append(outcome["probability"] * self.engine.evaluate_state(hypothetical))
                score = sum(values)
            elif kind in ("open_chest", "enter_shop", "heart_step"):
                score = 2.0
            if score is None and choice is not None: score = self.engine.evaluate_state(self.engine._apply_choice(payload, choice))
            if score is not None: rows.append((float(score), action))
        if not rows: raise ValueError("No supported value-policy candidate")
        score, action = max(rows, key=lambda row: row[0])
        return action, {"policy": "format12-post-action-value", "score": score}
