"""Append-only causal trajectories: queue ACKs and retries are not labels."""
import json
from pathlib import Path
import time
import uuid
from .policy_runtime import candidates_for, chosen_id
from selectcard.policy.contracts import digest, observable_state

LIFECYCLE = {"MAIN_MENU", "CHAR_SELECT", "GAME_OVER"}


def confirms(before, after, action):
    if after is None:
        return False
    if after.screen_type != before.screen_type or after.floor != before.floor:
        return True
    left, right = observable_state(before), observable_state(after)
    kind = action.type.value
    if kind in ("potion", "discard_potion"):
        return left["potions"] != right["potions"]
    if before.screen_type == "SHOP_SCREEN":
        return left["gold"] != right["gold"] and left["shop_inventory"] != right["shop_inventory"]
    if before.screen_type == "GRID":
        return left["grid_selected_count"] != right["grid_selected_count"] or left["grid_selected_cards"] != right["grid_selected_cards"]
    if before.screen_type == "MAP":
        return left["current_map_node"] != right["current_map_node"]
    if before.screen_type == "EVENT":
        return left["event"] != right["event"] or before.event != after.event
    if before.screen_type == "COMBAT_REWARD":
        return before.reward_choices != after.reward_choices
    return False


class TraceRecorder:
    def __init__(self, directory, *, metadata=None, timeout=15.):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.metadata = metadata or {}
        self.timeout = timeout
        self.handle = None
        self.run_id = None
        self.pending = None
        self.step = 0
        self.completed_runs = 0
        self.last_observed_floor = 0

    def write(self, value):
        self.handle.write(json.dumps({"run_id": self.run_id, **value}, ensure_ascii=False) + "\n")
        self.handle.flush()

    def observe(self, state, now=None):
        if state is None:
            return
        now = time.monotonic() if now is None else now
        if self.handle is None and state.screen_type not in LIFECYCLE and state.character:
            self.run_id = uuid.uuid4().hex
            self.step = 0
            self.last_observed_floor = state.floor or 0
            self.handle = (self.directory / (self.run_id + ".jsonl")).open("x", encoding="utf-8")
            self.write({"record_type": "run_start", "seed_group": state.seed_group,
                        "started_at": time.time(),
                        "character": state.character, "ascension": state.ascension_level,
                        "metadata": self.metadata, "attached_floor": state.floor,
                        "run_mode": state.run_mode,
                        "start_fingerprint": digest({"state": observable_state(state), "menu": state.choice_list,
                                                     "event": state.event.model_dump(mode="json") if state.event else None})})
        if self.handle is None:
            return
        if state.floor is not None:
            self.last_observed_floor = max(self.last_observed_floor, min(57, state.floor))
        if self.pending is not None:
            before, action, record, submitted = self.pending
            if confirms(before, state, action):
                record.update(record_type="decision", confirmed=True, post_state=observable_state(state))
                self.write(record)
                self.pending = None
            elif now - submitted >= self.timeout:
                self.write({"record_type": "unconfirmed", "decision_id": record["decision_id"], "reason": "timeout"})
                self.pending = None
        if state.screen_type == "GAME_OVER" and state.game_over_reason in ("victory", "defeat"):
            victory = state.game_over_reason == "victory"
            self.finish({"terminal_known": True, "victory": victory,
                         "terminal_floor_raw": state.floor,
                         "terminal_floor": 57 if victory and state.act == 4 and (state.floor or 0) >= 55 else state.floor,
                         "heart_victory": victory and state.act == 4 and (state.floor or 0) >= 55})
            self.completed_runs += 1
        elif state.screen_type in ("MAIN_MENU", "CHAR_SELECT"):
            self.finish({"terminal_known": False, "victory": None, "heart_victory": None})

    def begin(self, state, action, now=None, *, behavior=None):
        if action.type.value in ("wait", "play", "end_turn", "set_ascension"):
            return
        if self.handle is None or self.pending is not None or state.room_phase == "COMBAT" or not state.seed_group or state.screen_type in LIFECYCLE:
            return
        candidates = candidates_for(state)
        selected = chosen_id(state, action, candidates) if candidates else None
        if selected is None:
            self.write({"record_type": "coverage_gap", "screen": state.screen_type, "reason": "unstructured_menu_or_unmapped_action"})
            return
        self.step += 1
        record = {"decision_id": str(self.step), "decision_type": state.screen_type,
                  "pre_state": observable_state(state), "candidates": candidates, "chosen_action_id": selected,
                  "provenance": {"status": "exact", "source": "confirmed_live_bridge", "pre_state_hash": digest(observable_state(state))}}
        if behavior is not None:
            record["behavior"] = behavior
        self.pending = (state.model_copy(deep=True), action, record, time.monotonic() if now is None else now)

    def finish(self, outcome):
        if self.handle:
            outcome = {"terminal_floor": self.last_observed_floor,
                       "last_observed_floor": self.last_observed_floor, **outcome}
            self.write({"record_type": "run_end", "outcome": outcome})
            self.handle.close()
        self.handle = None
        self.pending = None

    def close(self):
        self.finish({"terminal_known": False, "victory": None, "heart_victory": None})


class TracingGameClient:
    """Observe every submission, including local multi-step agent flows.

    A fresh state is captured immediately before queue insertion. Recording at
    choose_action's caller would miss actions submitted *inside* that method.
    """
    def __init__(self, client, recorder):
        self.client = client
        self.recorder = recorder
        self.behavior_provider = lambda: None

    def __getattr__(self, name):
        return getattr(self.client, name)

    def get_state(self):
        state = self.client.get_state()
        self.recorder.observe(state)
        return state

    def submit_action(self, action):
        before = self.get_state()
        result = self.client.submit_action(action)
        if result[0] and before is not None:
            self.recorder.begin(before, action, behavior=self.behavior_provider())
        return result
