"""Read captured real first-combat snapshots; native simulation only, no game commands."""
import argparse
import json
from pathlib import Path

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import HeadlessCombatBridge


def check(root, executable, output):
    worker = Worker(executable); rows = []
    try:
        for role in ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"):
            row = {"role": role}
            try:
                raw = json.loads((root / f"reports/first-combat-{role}.json").read_text(encoding="utf-8"))
                bridge = HeadlessCombatBridge(); frame = bridge.observe(raw, run_id="real-smoke-" + role, sequence=0)
                row.update(source_mapping="passed", legal_actions=len(frame.legal_actions))
                fp = worker.execute({"operation": "fingerprint", "snapshot": frame.snapshot}, 1000)
                row.update(native_import=fp["supported"], import_reason=fp.get("reason"))
                if fp["supported"]:
                    reply = worker.execute({"snapshot": frame.snapshot, "session_id": frame.run_id,
                        "search_profile": "FAST", "max_expansions": 1000, "timeout_ms": 200}, 200)
                    row.update(search_supported=reply["supported"], search_reason=reply.get("reason"))
                    (root / f"reports/native-search-{role}.json").write_text(json.dumps(reply, indent=2), encoding="utf-8")
                    if reply["supported"]:
                        plan = bridge.prepare(frame, reply)
                        row["native_plan"] = {"method": plan.native_method, "args": plan.native_args}
            except Exception as error:
                row["error"] = repr(error)
            rows.append(row); print(json.dumps(row), flush=True)
    finally:
        worker.close()
    output.write_text(json.dumps({"scope": "real-first-combat-source/native-read-only", "roles": rows,
        "game_commands_executed_by_checker": 0}, indent=2), encoding="utf-8")
    return all("native_plan" in r for r in rows)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, required=True); p.add_argument("--worker", type=Path, required=True); p.add_argument("--output", type=Path, required=True)
    a = p.parse_args(); raise SystemExit(not check(a.root, a.worker, a.output))
