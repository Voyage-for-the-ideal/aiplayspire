import json
from collections import deque
from dataclasses import dataclass, replace
from enum import IntEnum


class Phase(IntEnum):
    PICK = 0
    CONFIRM = 1
    SETTLE = 2
    COMPLETE = 3
    CENSORED = 4


@dataclass(frozen=True)
class Ticket:
    instance: int
    generation: int
    phase: Phase
    rng: int


@dataclass(frozen=True)
class Session:
    phase: Phase = Phase.PICK
    generation: int = 0
    rng: int = 0
    cached: Ticket | None = None
    applied: tuple[Ticket, ...] = ()
    terminal_evidence: bool = False


@dataclass(frozen=True)
class Protocol:
    sessions: tuple = (Session(), Session())
    late: frozenset = frozenset()


def successors(state, variant):
    for instance, session in enumerate(state.sessions):
        def update(next_session=None, late=state.late):
            sessions = list(state.sessions)
            if next_session is not None:
                sessions[instance] = next_session
            return Protocol(tuple(sessions), late)

        phase = session.phase
        if phase == Phase.CENSORED and session.generation == 0:
            yield f"restart[{instance}]", update(Session(generation=1)), None
        if phase in (Phase.COMPLETE, Phase.CENSORED):
            continue
        if phase == Phase.SETTLE:
            yield f"real-terminal[{instance}]", update(replace(session, phase=Phase.COMPLETE, terminal_evidence=True)), None
            continue
        ticket = Ticket(instance, session.generation, phase, session.rng)
        if session.cached is None:
            yield f"submit[{instance}]", update(replace(session, cached=ticket), state.late | {ticket}), None
        if phase == Phase.CONFIRM and session.rng == 0:
            yield f"rng-drift[{instance}]", update(replace(session, rng=1)), None
        if session.cached is not None:
            yield f"timeout[{instance}]", update(replace(session, phase=Phase.CENSORED)), None
        for delivered in sorted(state.late, key=lambda t: (t.instance, t.generation, t.phase, t.rng)):
            if delivered in session.applied:
                if variant == "duplicate":
                    yield f"repeat[{instance}]", update(replace(session, applied=(*session.applied, delivered))), None
                continue
            accepts = session.cached == delivered == ticket
            if variant == "rng-drift" and session.cached == delivered and delivered is not None:
                accepts = (delivered.instance, delivered.generation, delivered.phase) == (instance, session.generation, phase)
            if variant == "old-generation" and session.cached is not None:
                accepts |= (delivered.instance, delivered.phase, delivered.rng) == (instance, phase, session.rng)
            if accepts:
                next_phase = Phase(int(phase) + 1)
                yield f"deliver[{instance}]({delivered})", update(replace(
                    session, phase=next_phase, cached=None, applied=(*session.applied, delivered))), ticket
        if variant == "censored-as-defeat" and session.cached:
            yield f"timeout-as-defeat[{instance}]", update(replace(session, phase=Phase.COMPLETE)), None


def protocol_invariant(previous, current, expected):
    for before, after in zip(previous.sessions, current.sessions):
        if len(after.applied) != len(set(after.applied)):
            return "ACTION_APPLIED_TWICE"
        if after.phase is Phase.COMPLETE and not after.terminal_evidence:
            return "TERMINAL_REQUIRES_GAME_EVIDENCE"
        if len(after.applied) != len(before.applied) + 1:
            continue
        applied = after.applied[-1]
        if applied.generation != expected.generation:
            return "RECEIPT_MUST_MATCH_GENERATION"
        if applied.rng != expected.rng:
            return "REUSE_REQUIRES_EXACT_RNG"
        if applied != expected or applied != before.cached:
            return "ACTION_REQUIRES_FRESH_TICKET"
    return None


def jvm_successors(owners, shared):
    for instance in range(2):
        if any(owner == instance for _, owner in owners):
            continue
        location = 0 if shared else instance
        yield f"child-extract[{instance}]", tuple(sorted((*owners, (location, instance)))), None


def jvm_invariant(previous, current, evidence):
    if len({location for location, _ in current}) != len(current):
        return "ISOLATE_JVM_TEMP_ROOT"
    return None


def explore(initial, successors, invariant):
    queue = deque([(initial, ())])
    seen = {initial}
    edges = 0
    while queue:
        state, trace = queue.popleft()
        for action, next_state, evidence in successors(state):
            edges += 1
            path = (*trace, action)
            violation = invariant(state, next_state, evidence)
            if violation:
                return {"states": len(seen), "edges": edges, "violation": violation, "counterexample": path}
            if next_state not in seen:
                seen.add(next_state)
                queue.append((next_state, path))
    return {"states": len(seen), "edges": edges, "violation": None}


def check():
    correct = {
        "protocol": explore(Protocol(), lambda s: successors(s, "correct"), protocol_invariant),
        "jvm": explore((), lambda s: jvm_successors(s, False), jvm_invariant),
    }
    if any(row["violation"] is not None for row in correct.values()):
        raise AssertionError(correct)
    witnesses = {
        "9f05461^": (explore((), lambda s: jvm_successors(s, True), jvm_invariant), "ISOLATE_JVM_TEMP_ROOT"),
        "20e1969/6101ea7-rng-omitted": (
            explore(Protocol(), lambda s: successors(s, "rng-drift"), protocol_invariant), "REUSE_REQUIRES_EXACT_RNG"),
        "fault-injection/duplicate": (
            explore(Protocol(), lambda s: successors(s, "duplicate"), protocol_invariant), "ACTION_APPLIED_TWICE"),
        "fault-injection/late-reply": (
            explore(Protocol(), lambda s: successors(s, "old-generation"), protocol_invariant), "RECEIPT_MUST_MATCH_GENERATION"),
        "fault-injection/censored-defeat": (
            explore(Protocol(), lambda s: successors(s, "censored-as-defeat"), protocol_invariant), "TERMINAL_REQUIRES_GAME_EVIDENCE"),
    }
    for result, expected in witnesses.values():
        if result["violation"] != expected:
            raise AssertionError((result, expected))
    return {"correct": correct, "counterexamples": {name: pair[0] for name, pair in witnesses.items()}}


if __name__ == "__main__":
    print(json.dumps(check(), indent=2))
