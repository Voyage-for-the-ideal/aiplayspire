"""Inventory the sealed A5 dual-lane batch and retry only technical aborts."""
import argparse
from collections import Counter, defaultdict, deque
import hashlib
import json
from pathlib import Path
import signal
import uuid

from sts_ai_framework.headless.agent import HeadlessCollector, ROLES, request_stop, save_json
from sts_ai_framework.headless.batch import lane_lock


SETTINGS = dict(search_policy="legacy", search_profile="DEEP", max_expansions=15000, timeout_ms=0)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_bytes())


def outcome(row):
    if row["status"] == "aborted" and row["censored"] is True and row["terminal"] is None:
        return "technical_abort"
    if row["status"] != "complete" or row["censored"] is not False:
        raise ValueError("Invalid attempt status")
    value = row["terminal"]["outcome"]
    if value not in ("victory", "defeat"):
        raise ValueError("Unexpected terminal outcome")
    return value


def failure_family(record):
    state = record.get("before", {}).get("state", {})
    menu = state.get("interaction", {})
    cmd = (record.get("prediction") or {}).get("commands") or []
    if cmd:
        cmd = json.loads(cmd[0]["command"])
    else:
        cmd = {}
    name = (cmd.get("display_string", "").rstrip("+") or (menu.get("event") or {}).get("class_name")
            or (menu.get("selection") or {}).get("purpose") or record.get("method") or cmd.get("type") or "driver")
    return f"{state.get('phase', 'BOOT')}:{name}"


def inventory(batch):
    sealed = read(batch / "reports/final-summary.json")
    summaries, counts, groups = [], {}, defaultdict(list)
    seeds, attempts = set(), set()
    for lane in range(2):
        folder = batch / f"lane{lane}"
        source = folder / "reports/collection-summary.json"
        rows = read(source)["results"]
        if len(rows) != 80 or Counter(row["role"] for row in rows) != {role: 20 for role in ROLES}:
            raise ValueError("Expected twenty cases per role and original lane")
        summaries.append(digest(source))
        counts[str(lane)] = {role: dict(Counter(outcome(row) for row in rows if row["role"] == role))
                             for role in ROLES}
        for row in rows:
            attempt, seed = row["attempt_id"], row["seed"]
            if attempt in attempts or seed in seeds:
                raise ValueError("Duplicate original case")
            attempts.add(attempt)
            seeds.add(seed)
            if outcome(row) != "technical_abort":
                continue
            episode = Path(row["path"]).resolve(strict=True)
            diagnostic = (folder / "reports" / f"failure-{attempt}.json").resolve(strict=True)
            if not episode.is_relative_to(folder / "episodes") or not diagnostic.is_relative_to(folder / "reports"):
                raise ValueError("Original evidence left its lane")
            facts = read(diagnostic)
            if any(facts[k] != row[k] for k in ("attempt_id", "role", "seed")) or digest(episode) != row["sha256"]:
                raise ValueError("Original evidence identity mismatch")
            family = failure_family(facts)
            groups[family].append(dict(original_lane=lane, original_attempt=attempt, seed=seed, role=row["role"],
                                       last_floor=row["last_floor"], family=family, original_sha256=row["sha256"],
                                       original_chain=row["final_chain"], diagnostic_sha256=digest(diagnostic)))
    # Start one representative from each mechanism early, before its repeats.
    pending = deque(deque(sorted(values, key=lambda x: x["last_floor"] or 0))
                    for values in sorted(groups.values(), key=lambda group: (-len(group), group[0]["family"])))
    cases = []
    while pending:
        group = pending.popleft()
        cases.append(group.popleft())
        if group:
            pending.append(group)
    if len(seeds) != sealed["attempts"] or len(cases) != sealed["technical_abort"]:
        raise ValueError("Sealed batch counts disagree")
    return dict(batch=str(batch), sealed_sha256=digest(batch / "reports/final-summary.json"),
                source_summary_sha256=summaries, counts=counts, cases=cases)


def run(root, manifest, shard, shards):
    if not 0 <= shard < shards or shards < 1:
        raise ValueError("Invalid worker shard")
    source = read(manifest)
    if root.is_relative_to(Path(source["batch"])):
        raise ValueError("Retries require a separate runtime")
    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)
    with lane_lock(root):
        agent = HeadlessCollector(root, str(root / "tools/start-session.sh"), str(root / "native-bin/battle-engine-worker"),
                                  str(root / "value-format12.pth"), ascension=5)
        if agent.runtime_search != SETTINGS:
            raise ValueError("Unexpected retry search settings")
        for case in source["cases"][shard::shards]:
            receipt = root / "reports" / f"retry-{case['seed']}.json"
            if receipt.exists():
                raise ValueError(f"Retry receipt already exists: {receipt}")
            attempt = uuid.uuid4().hex
            print(json.dumps(dict(kind="start", attempt_id=attempt, **case)), flush=True)
            result = agent.episode(case["role"], case["seed"], attempt_id=attempt)
            row = dict(original=case, outcome=outcome(result), result=result, manifest_sha256=digest(manifest),
                       search=agent.runtime_search)
            save_json(receipt, row)
            print(json.dumps(dict(kind="settled", seed=case["seed"], outcome=row["outcome"],
                                  floor=result["last_floor"], attempt_id=attempt)), flush=True)


def status(root, manifest):
    source = read(manifest)
    wanted = {case["seed"]: case for case in source["cases"]}
    signature = digest(manifest)
    outcomes, results = defaultdict(Counter), {}
    for path in sorted(root.glob("worker*/reports/retry-*.json")):
        row = read(path)
        case, result = row["original"], row["result"]
        seed = case["seed"]
        if (wanted.get(seed) != case or seed in results or row["manifest_sha256"] != signature
                or row["search"] != SETTINGS or row["outcome"] != outcome(result)
                or (result["seed"], result["role"]) != (seed, case["role"])):
            raise ValueError(f"Invalid retry receipt: {path}")
        outcomes[(case["original_lane"], case["role"])][row["outcome"]] += 1
        results[seed] = row
    return dict(scheduled=len(wanted), settled=len(results),
                outcomes=dict(Counter(row["outcome"] for row in results.values())),
                by_lane={str(i): {role: dict(outcomes[(i, role)]) for role in ROLES} for i in range(2)},
                aborted=[dict(seed=seed, role=row["original"]["role"],
                              floor=row["result"]["last_floor"], diagnostic=row["result"]["diagnostic"])
                         for seed, row in results.items() if row["outcome"] == "technical_abort"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="operation", required=True)
    inv = commands.add_parser("inventory")
    inv.add_argument("--batch", required=True, type=Path)
    inv.add_argument("--output", required=True, type=Path)
    retry = commands.add_parser("run")
    retry.add_argument("--root", required=True, type=Path)
    retry.add_argument("--manifest", required=True, type=Path)
    retry.add_argument("--shard", required=True, type=int)
    retry.add_argument("--shards", required=True, type=int)
    report = commands.add_parser("status")
    report.add_argument("--root", required=True, type=Path)
    report.add_argument("--manifest", required=True, type=Path)
    args = parser.parse_args()
    if args.operation == "inventory":
        batch, output = args.batch.resolve(strict=True), args.output.resolve()
        if output.is_relative_to(batch):
            raise ValueError("Preserve the original batch")
        data = inventory(batch)
        with output.open("x", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        print(json.dumps(dict(counts=data["counts"], retries=len(data["cases"]))))
    elif args.operation == "run":
        run(args.root.resolve(strict=True), args.manifest.resolve(strict=True), args.shard, args.shards)
    else:
        print(json.dumps(status(args.root.resolve(strict=True), args.manifest.resolve(strict=True)), indent=2))


if __name__ == "__main__":
    main()
