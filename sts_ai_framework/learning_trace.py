"""Opt-in append-only learning telemetry. Never starts games or training jobs."""
import copy
import time
import uuid
from pathlib import Path
import json

from selectcard.src.live_contract import TRACE_SCHEMA, fingerprint, value_state, value_fingerprint
from selectcard.policy.contracts import observable_state
from .llm_agent_parts.value_payload import value_state_payload
from .policy_runtime import candidates_for, chosen_id
from .policy_trace import confirms, LIFECYCLE
from .routing.strength import deck_profile


def snapshot(state):
    public = observable_state(state)
    result = {"observable": public, "floor": state.floor, "act": state.act,
              "screen": state.screen_type, "room_phase": state.room_phase, "value_state": None}
    if state.player is not None:
        result["resources"] = {"hp": state.player.current_hp, "max_hp": state.player.max_hp,
            "gold": state.player.gold, "potions": public["potions"]}
    # A fallback ascension used by old clients is not an observed training feature.
    if state.player is not None and state.character and state.ascension_level is not None:
        try:
            result["value_state"] = value_state(value_state_payload(state))
        except ValueError as error:
            result["value_state_error"] = str(error)
    if state.screen_type == "MAP":
        result["deck_profile"] = deck_profile(state).as_dict()
    return result


class LearningRecorder:
    def __init__(self, directory, *, metadata=None, timeout=15.):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=False)
        self.metadata = copy.deepcopy(metadata or {})
        self.timeout = timeout
        self.handle = self.run_id = self.seed_group = None
        self.pending = self.room = self.intent = None
        self.current = None
        self.queries = []
        self.sequence = self.decisions = self.completed_runs = 0
        self.last_floor = 0

    def write(self, kind, **fields):
        self.sequence += 1
        row = dict(schema=TRACE_SCHEMA, run_id=self.run_id, sequence=self.sequence,
                   record_type=kind, monotonic=time.monotonic(), **fields)
        self.handle.write(json.dumps(row, ensure_ascii=False, allow_nan=False) + "\n")
        self.handle.flush()
        return row

    def observe(self, state, now=None):
        if state is None:
            return
        now = time.monotonic() if now is None else now
        terminal = state.screen_type == "GAME_OVER"
        # Settlement states may omit seed_group. A different *present* seed starts a new run.
        if self.handle and state.seed_group and state.seed_group != self.seed_group:
            self.finish("run_changed")
        if self.handle is None:
            if state.screen_type in LIFECYCLE or not state.character or not state.seed_group:
                return
            self.run_id, self.seed_group = uuid.uuid4().hex, state.seed_group
            self.sequence = self.decisions = self.last_floor = 0
            self.handle = (self.directory / (self.run_id + ".jsonl")).open("x", encoding="utf-8")
            self.write("run_start", seed_group=self.seed_group, attached_floor=state.floor,
                character=state.character, ascension=state.ascension_level, run_mode=state.run_mode,
                metadata=self.metadata, started_at=time.time())
        if state.floor is not None:
            self.last_floor = max(self.last_floor, min(57, state.floor))
        after = snapshot(state)
        if self.pending:
            before, action, record, submitted = self.pending
            if confirms(before, state, action):
                candidates = record["queries"]
                if state.screen_type == "GRID":
                    self.intent = {"id": record["intent_id"], "queries": candidates}
                actual = after.get("value_state")
                matches = [q["query_id"] for q in candidates if actual is not None
                    and value_fingerprint(q["state"]) == value_fingerprint(actual)]
                stable = state.screen_type not in ("GRID", "GAME_OVER") and state.room_phase != "COMBAT"
                self.write("decision", decision_id=record["decision_id"], intent_id=record["intent_id"],
                    decision_type=before.screen_type, pre_state_hash=record["pre_state_hash"],
                    query_ids=[q["query_id"] for q in candidates], matched_query_ids=matches if stable else [],
                    chosen_action_id=record["chosen_action_id"], command=record["command"],
                    candidates=record["candidates"], before=record["before"], after=after,
                    behavior=record["behavior"], confirmed=True, stable=stable)
                if state.screen_type != "GRID":
                    self.intent = None
                self.pending = None
                self.queries = []
            elif now - submitted >= self.timeout:
                self.write("unconfirmed", decision_id=record["decision_id"], reason="timeout")
                self.pending = self.intent = None
                self.queries = []
                if self.room and not self.room["entered"]:
                    self._end_room("unconfirmed", after)
        self._observe_room(state, after)
        self.current = after
        if terminal and state.game_over_reason in ("victory", "defeat"):
            self.finish(state.game_over_reason, state=state)
            self.completed_runs += 1
        elif state.screen_type in ("MAIN_MENU", "CHAR_SELECT"):
            self.finish("left_run")

    def value_query(self, payload, predictions):
        if self.handle is None or self.current is None:
            return
        query = self.write("value_query", query_id=str(self.sequence + 1),
            pre_state_hash=fingerprint(self.current), state=value_state(payload),
            predictions={k: predictions[k] for k in ("expected_floor", "heart_probability", "value",
                "active_bins", "hazards", "survival", "heart_conditional") if k in predictions})
        self.queries.append(query)
        # Bound pending audit memory; every query is still retained on disk.
        if len(self.queries) > 512:
            self.write("coverage_gap", reason="query_buffer_cap", dropped=len(self.queries) - 512)
        self.queries = self.queries[-512:]

    def begin(self, state, action, now=None, *, behavior=None):
        if (self.handle is None or self.pending or state.screen_type in LIFECYCLE
                or state.room_phase == "COMBAT" or action.type.value in ("wait", "play", "end_turn", "set_ascension")):
            return
        before = snapshot(state)
        candidates = candidates_for(state) or []
        selected = chosen_id(state, action, candidates) if candidates else None
        self.decisions += 1
        decision_id = str(self.decisions)
        owner = fingerprint(before)
        queries = [q for q in self.queries if q["pre_state_hash"] == owner]
        intent_id = decision_id
        if state.screen_type == "GRID" and self.intent:
            intent_id = self.intent["id"]
            queries = self.intent["queries"] + queries
        queries = list({q["query_id"]: q for q in queries}.values())
        if len(queries) > 512:
            self.write("coverage_gap", reason="intent_query_cap", dropped=len(queries) - 512)
            queries = queries[-512:]
        record = dict(decision_id=decision_id, intent_id=intent_id, before=before,
            pre_state_hash=owner, queries=queries, chosen_action_id=selected,
            candidates=candidates, command=action.to_api_payload(), behavior=behavior or {"source": "baseline"})
        self.write("decision_submitted", **{k: v for k, v in record.items() if k != "queries"},
                   query_ids=[q["query_id"] for q in queries])
        self.pending = (state.model_copy(deep=True), action, record,
                        time.monotonic() if now is None else now)
        if state.screen_type == "MAP" and action.type.value == "choose" and self.room is None:
            target = next((c for c in state.current_map_choices if c.choice_index == action.choice_index), None)
            if target is not None:
                self.room = dict(decision_id=decision_id, before=before,
                    target=target.model_dump(mode="json"), entered=False, combat_end=None)

    def _observe_room(self, state, after):
        if not self.room:
            return
        start = self.room["before"]
        expected = state.act == start["act"] and state.floor == start["floor"] + 1
        if expected:
            self.room["entered"] = True
            if (self.room["combat_end"] is None and state.room_phase != "COMBAT"
                    and state.screen_type == "COMBAT_REWARD"):
                self.room["combat_end"] = after
        if state.screen_type == "GAME_OVER":
            status = "died" if expected and state.game_over_reason == "defeat" else "observation_gap"
            self._end_room(status, after, terminal_reason=state.game_over_reason)
        elif state.screen_type == "MAP" and state.floor != start["floor"]:
            node = state.current_map_node
            target = self.room["target"]
            matched = expected and node is not None and (node.x, node.y) == (target["x"], target["y"])
            self._end_room("survived" if matched else "observation_gap", after)

    def _end_room(self, status, after=None, *, terminal_reason=None):
        self.write("room_transition", **self.room, status=status, after=after, terminal_reason=terminal_reason)
        self.room = None

    def finish(self, reason, *, state=None):
        if self.handle is None:
            return
        if self.pending:
            self.write("unconfirmed", decision_id=self.pending[2]["decision_id"], reason=reason)
        if self.room:
            self._end_room("censored", self.current)
        known = (reason in ("victory", "defeat") and state is not None
                 and type(state.floor) is int and 0 <= state.floor <= 57)
        heart = known and reason == "victory" and state.act == 4 and (state.floor or 0) >= 55
        self.write("run_end", outcome=dict(terminal_known=known,
            terminal_floor_raw=state.floor if known else None,
            terminal_act=state.act if known else None,
            terminal_floor=57 if heart else self.last_floor,
            last_observed_floor=self.last_floor, heart_victory=bool(heart) if known else None,
            victory=reason == "victory" if known else None, termination_reason=reason))
        self.handle.close()
        self.handle = self.pending = self.room = self.intent = self.current = None
        self.queries = []

    def close(self):
        self.finish("collector_closed")


class LearningGameClient:
    """Captures internal macro submissions as well as main-loop actions."""
    def __init__(self, client, recorder):
        self.client, self.recorder = client, recorder
        self.behavior_provider = lambda: {"source": "baseline"}

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
