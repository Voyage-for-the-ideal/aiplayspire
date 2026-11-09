"""Read-only snapshot import/search checks, plus optional real-engine one-action replay."""
import argparse
import hashlib
import json
from pathlib import Path
import socket
import sys
import threading
import types
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from battle_engine import protocol
from battle_engine.server import Gateway, ROOT, execute_worker, validate_response
from battle_engine.state import load, projection


def replay(port, source, commands, state_file=None):
    with socket.create_connection(("127.0.0.1", port), timeout=5) as sock:
        sock.settimeout(40)
        request = dict(type="REPLAY", fileName=str(source.resolve()), client_cwd=str(source.parent.resolve()),
                       commands=commands, timeout_ms=30000, include_projection=True)
        if state_file is not None:
            request["state_file"] = str(state_file.resolve())
        protocol.send(sock, json.dumps(request))
        response = json.loads(protocol.receive(sock))  # REPLAY is one frame, unlike a search transaction.
        if response.get("type") != "REPLAY" or response.get("error") or not response.get("diff_valid"):
            raise RuntimeError(f"Java replay failed: {response}")
        return response


def card_pools(port, source):
    with socket.create_connection(("127.0.0.1", port), timeout=5) as sock:
        sock.settimeout(10)
        protocol.send(sock, json.dumps(dict(type="CARD_POOLS", fileName=str(source.resolve()))))
        response = json.loads(protocol.receive(sock))
        if response.get("type") != "CARD_POOLS":
            raise RuntimeError(f"Java card-pool export failed: {response}")
        return response["combat_card_pools"]


def transition_matches(result, actual, state_file):
    if actual["projection"] != result["expected_after"]:
        return False
    if "expected_screen" in result:
        # v1 omits screens. Inspect the actual saved boundary, including in
        # single-step mode; equal HP/card piles alone are not proof of closure.
        state = load(state_file)
        screen = state["screen_name"] if state.get("is_screen_up", False) else "NONE"
        return screen == result["expected_screen"]
    return True  # Keep old workers' v1 replay contract readable.


def boundary_fallback(args, source, result, destination):
    """Verify a deliberate native -> live selection -> Java -> real replay handoff.

    This is a hybrid route check, explicitly distinct from native transition equivalence.
    """
    replay(args.oracle_port, source, result["commands"], destination)
    selected = load(destination)
    if selected["screen_name"] not in ("HAND_SELECT", "GRID", "CARD_REWARD"):
        raise ValueError("unvalidated boundary was not an observable selection screen")
    pools = card_pools(args.oracle_port, destination)
    options = types.SimpleNamespace(worker=args.worker.resolve(), fallback_port=args.oracle_port,
                                    no_fallback=False, max_time_ms=args.timeout_ms)
    final = None
    with Gateway(("127.0.0.1", 0), options) as gateway:
        thread = threading.Thread(target=gateway.serve_forever, daemon=True); thread.start()
        try:
            with socket.create_connection(gateway.server_address, timeout=5) as sock:
                sock.settimeout(30)
                protocol.send(sock, json.dumps(dict(fileName=str(destination.resolve()),
                    client_cwd=str(destination.parent.resolve()), combat_card_pools=pools,
                    command_file=str(destination.with_name("boundary_commands.json").resolve()),
                    max_expansions=64, num_turns=64, timeout_ms=1000, search_profile="FAST")))
                while True:
                    text = protocol.receive(sock)
                    if text == "DONE": break
                    message = json.loads(text)
                    if message.get("type") == "ERROR": raise RuntimeError(str(message))
                    if message.get("type") == "COMMAND_LIST": final = message
        finally:
            gateway.shutdown(); thread.join()
    if final is None:
        raise ValueError("selection fallback did not produce COMMAND_LIST")
    if "commands" not in final:
        final = json.loads(Path(final["command_path"]).read_text(encoding="utf-8"))
    kinds = [json.loads(c["command"])["type"] for c in final["commands"] if c is not None]
    if not any(k in ("HAND_SELECT", "GRID_SELECT", "CARD_REWARD_SELECT") for k in kinds):
        raise ValueError("selection fallback produced no selection command")
    replay(args.oracle_port, destination, final["commands"], destination.with_name("after_java.json"))
    return dict(screen=selected["screen_name"], commands=kinds, java_replay_valid=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--worker", type=Path, default=ROOT / "bin/battle-engine-worker.exe")
    parser.add_argument("--output", type=Path, default=ROOT / "out/acceptance-report.json")
    parser.add_argument("--protocol-only", action="store_true")
    parser.add_argument("--oracle-port", type=int)
    parser.add_argument("--simulations", type=int, default=1000)
    parser.add_argument("--timeout-ms", type=int, default=200)
    parser.add_argument("--steps", type=int, default=1, help="Real-engine actions to compare per supported snapshot")
    parser.add_argument("--allow-boundary-fallback", action="store_true",
                        help="Verify actual Java selection handoff instead of requiring native-only continuation")
    args = parser.parse_args()
    if args.steps < 1 or ((args.steps > 1 or args.allow_boundary_fallback) and not args.oracle_port):
        parser.error("steps must be positive; multiple steps require --oracle-port")
    if args.protocol_only:
        suite = unittest.defaultTestLoader.loadTestsFromName("battle_engine.tests.test_backend.ProtocolTests")
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    entries = json.loads(args.manifest.read_text(encoding="utf-8-sig")) if args.manifest else [
        dict(path=str(ROOT / "tests/fixtures/ironclad.json"), role="IRONCLAD", kind="SYNTHETIC")]
    rows = []
    for case_index, entry in enumerate(entries):
        row = dict(source=entry["path"], role=entry["role"], passed=False)
        try:
            source = Path(entry["path"])
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            row["sha256"] = digest
            if entry.get("sha256", digest) != digest:
                raise ValueError("snapshot SHA256 changed")
            state = load(source)
            if args.oracle_port:
                state["_combat_card_pools"] = card_pools(args.oracle_port, source)
            result = execute_worker(args.worker.resolve(), state, args.simulations, args.timeout_ms, "import")
            row["supported"] = result["supported"]
            if not result["supported"]:
                row["reason"] = result["reason"]
                row["passed"] = False
            else:
                if result["projection"] != projection(state):
                    raise ValueError("native/Python source projection differs")
                result = execute_worker(args.worker.resolve(), state, args.simulations, args.timeout_ms)
                if not result["supported"]:
                    row["search_supported"] = False
                    if not args.oracle_port and result["reason"].startswith("runtime_card_pool_missing:"):
                        row["reason"] = result["reason"]
                        row["route"] = "UNSUPPORTED"
                        row["passed"] = False
                        rows.append(row)
                        print(f"FAIL {row['role']:10} cpp_import=True search=UNSUPPORTED {row['reason']}")
                        continue
                    raise ValueError(f"search unsupported: {result['reason']}")
                row["search_supported"] = True
                validate_response(result)
                row["metrics"] = result["metrics"]
                row["command"] = json.loads(result["commands"][0]["command"])
                row["passed"] = "expected_after" in result
                row["boundary_validated"] = "expected_after" in result
                if not row["passed"]:
                    row["reason"] = "unvalidated_boundary"
                if args.oracle_port:
                    if "expected_after" not in result:
                        if args.allow_boundary_fallback:
                            boundary_file = args.output.resolve().parent / "replay_states" / str(case_index) / "boundary_1.json"
                            row["boundary_fallback"] = boundary_fallback(args, source, result, boundary_file)
                            row["oracle"] = "BOUNDARY_FALLBACK_PASS"
                            row["passed"] = False
                        else:
                            row["oracle"] = "SKIP: terminal/selection boundary"
                            row["passed"] = False
                    else:
                        next_file = args.output.resolve().parent / "replay_states" / str(case_index) / "1.json" if args.steps > 1 or "expected_screen" in result else None
                        actual = replay(args.oracle_port, source, result["commands"], next_file)
                        row["oracle"] = "PASS" if transition_matches(result, actual, next_file) else "FAIL"
                        row["passed"] = row["oracle"] == "PASS"
                        if not row["passed"]:
                            row["expected_after"] = result["expected_after"]
                            row["actual_after"] = actual["projection"]
                        row["replay_steps"] = 1
                        for step in range(1, args.steps):
                            if not row["passed"]:
                                break
                            source = next_file
                            state = load(source)
                            state["_combat_card_pools"] = card_pools(args.oracle_port, source)
                            result = execute_worker(args.worker.resolve(), state, args.simulations, args.timeout_ms)
                            if not result["supported"]:
                                raise ValueError(f"step {step + 1} needs Java fallback: {result['reason']}")
                            validate_response(result)
                            if "expected_after" not in result:
                                if args.allow_boundary_fallback:
                                    boundary_file = args.output.resolve().parent / "replay_states" / str(case_index) / f"boundary_{step + 1}.json"
                                    row["boundary_fallback"] = boundary_fallback(args, source, result, boundary_file)
                                    row["oracle"] = "BOUNDARY_FALLBACK_PASS"
                                    row["passed"] = False
                                    break
                                raise ValueError(f"step {step + 1} reached unvalidated terminal/selection boundary")
                            next_file = args.output.resolve().parent / "replay_states" / str(case_index) / f"{step + 1}.json"
                            actual = replay(args.oracle_port, source, result["commands"], next_file)
                            row["passed"] = transition_matches(result, actual, next_file)
                            row["oracle"] = "PASS" if row["passed"] else "FAIL"
                            row["replay_steps"] += 1
                            if not row["passed"]:
                                row["expected_after"], row["actual_after"] = result["expected_after"], actual["projection"]
        except Exception as error:
            row["passed"] = False
            row["error"] = str(error)
            if args.oracle_port:
                row["oracle"] = "ERROR"
        rows.append(row)
        print(f"{'PASS' if row['passed'] else 'FAIL'} {row['role']:10} "
              f"cpp={row.get('supported')} oracle={row.get('oracle', 'NOT_RUN')} "
              f"{row.get('error', row.get('reason', ''))}")
    report = dict(passed=sum(r["passed"] for r in rows), total=len(rows),
                  native_search=sum(r.get("search_supported", False) for r in rows),
                  oracle_pass=sum(r.get("oracle") == "PASS" for r in rows),
                  boundary_fallback_pass=sum(r.get("oracle") == "BOUNDARY_FALLBACK_PASS" for r in rows),
                  replay_steps=sum(r.get("replay_steps", 0) for r in rows),
                  real_engine_checked=bool(args.oracle_port), worker=str(args.worker.resolve()),
                  worker_sha256=hashlib.sha256(args.worker.read_bytes()).hexdigest(),
                  simulations=args.simulations, timeout_ms=args.timeout_ms, rows=rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"PASS {report['passed']}/{report['total']}; native_search={report['native_search']}; "
          f"oracle_pass={report['oracle_pass']}; boundary_fallback={report['boundary_fallback_pass']}; "
          f"real_engine_checked={report['real_engine_checked']}; {args.output.resolve()}")
    return 0 if all(r["passed"] for r in rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
