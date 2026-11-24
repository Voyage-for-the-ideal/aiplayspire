"""Single-lane gated Headless collector using existing V(S), router, and native search."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import signal
from pathlib import Path
import time
import uuid

from battle_engine.profiles import TURN_POLICIES, deployment_budget
from battle_engine.worker import Worker
from .collection import EpisodeWriter, validate_episode
from .combat import HeadlessCombatBridge, verify_combat_effect, is_native_combat_selection
from .interaction import IroncladInteractionBridge, verify_effect
from .layout import module_path, runtime_files
from .policy import HeadlessValuePolicy
from .receipt import verify_driver
from ..models import GameAction

ROLES = ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")
_STOP_REQUESTED = False


def request_stop(signum, frame):
    global _STOP_REQUESTED
    _STOP_REQUESTED = True


def check_permits(root):
    if _STOP_REQUESTED: raise ValueError("Collection stop requested")
    for name in ("ALLOW_RUN_SMOKE", "ALLOW_COLLECTION"):
        path = Path(root) / name
        if not path.is_file() or path.is_symlink(): raise ValueError("Scoped permit missing: "+name)


def require(reply):
    if not reply.get("supported"): raise RuntimeError(reply.get("reason", "engine error"))
    return reply


def save_json(path, value):
    """Atomically persist an attempt/status update before starting more work."""
    temporary = path.with_suffix('.tmp')
    with temporary.open('w', encoding='utf-8') as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2)+'\n')
        stream.flush(); os.fsync(stream.fileno())
    temporary.replace(path)
    if hasattr(os, 'O_DIRECTORY'):
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try: os.fsync(directory)
        finally: os.close(directory)


def resource_sample(engine, cpp):
    sample = {"rss_bytes": 0, "pss_bytes": 0, "cpu_seconds": 0.0, "pids": []}
    try: sample['os_threads'] = len(list(Path('/proc/self/task').iterdir()))
    except OSError: pass
    for pid in [os.getpid()] + [w.process.pid for w in (engine, cpp) if w is not None and w.process is not None]:
        try:
            fields = {}
            for line in Path(f"/proc/{pid}/smaps_rollup").read_text().splitlines():
                if ":" in line:
                    key, value = line.split(":", 1)
                    if key in ("Rss", "Pss"): fields[key] = int(value.split()[0])*1024
            stat = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
            sample["cpu_seconds"] += (int(stat[11])+int(stat[12]))/os.sysconf("SC_CLK_TCK")
            sample["rss_bytes"] += fields.get("Rss", 0); sample["pss_bytes"] += fields.get("Pss", 0); sample["pids"].append(pid)
        except (OSError, ValueError): pass
    try:
        group=next(line[3:] for line in Path('/proc/self/cgroup').read_text().splitlines() if line.startswith('0::'))
        for name,file in (('cgroup_memory_peak_bytes','memory.peak'),('cgroup_swap_peak_bytes','memory.swap.peak')):
            sample[name]=int((Path('/sys/fs/cgroup')/group.lstrip('/')/file).read_text())
        root=Path('/sys/fs/cgroup')/group.lstrip('/')
        sample['cgroup_memory_events']={k:int(v) for k,v in (line.split() for line in (root/'memory.events').read_text().splitlines())}
        cpu={k:int(v) for k,v in (line.split() for line in (root/'cpu.stat').read_text().splitlines())}
        sample['cgroup_cpu_seconds']=cpu['usage_usec']/1e6
    except (OSError,ValueError,StopIteration): pass
    return sample


def check_decision_progress(policy, run, raw, seen):
    """Count identical decisions, distinguishing visible, already declined reward groups."""
    progress = {k: v for k, v in raw.items() if k != 'driver_receipt'}
    if raw['phase'] == 'REWARD':
        progress['declined_offers'] = policy.declined_offers(run, raw)
    stable = json.dumps(progress, sort_keys=True, separators=(',', ':'))
    key = hashlib.sha256(stable.encode()).hexdigest()
    seen[key] = seen.get(key, 0) + 1
    if seen[key] >= 3:
        raise ValueError('Repeated identical decision without gameplay progress')


def read_combat_search(path):
    cfg = json.loads(path.read_text(encoding="utf-8"))
    if set(cfg)!={"search_profile","search_policy","max_expansions","timeout_ms"}:
        raise ValueError("Unexpected combat-search.json settings")
    if cfg["search_policy"] not in TURN_POLICIES or cfg["search_profile"] not in ("FAST","BALANCED","DEEP"):
        raise ValueError("Invalid combat search policy/profile")
    for key,lo,hi in (("max_expansions",1,1000000),("timeout_ms",0 if cfg["search_policy"]=="legacy" else 1,90000)):
        if type(cfg[key]) is not int or not lo<=cfg[key]<=hi: raise ValueError("Invalid combat "+key)
    return cfg

def reusable_segment_step(snapshot, fingerprint, step):
    from battle_engine.state import context, projection
    if not snapshot or snapshot.get("is_screen_up") or not fingerprint.get("supported"): return False
    envelope = step["commands"][0]
    piles = {k:[c.get("uuid") for c in snapshot["player_state"][k]]
             for k in ("hand","draw_pile","discard_pile","exhaust_pile")}
    return (step["required_native_key"] == fingerprint["native_key"]
            and step["required_path_context"] == fingerprint["path_context"]
            and step["required_uuids"] == piles
            and envelope["expected_context"] == context(snapshot)
            and envelope["expected_before"] == projection(snapshot))


class HeadlessCollector:
    combat_search = "legacy"
    frozen_budgets = None
    search_profile = "FAST"
    runtime_search = None
    tactical_ordering = True
    segment_length = 1
    dedup_mode = "default"

    def __init__(self, root, launcher, native, checkpoint, *, max_actions=3000, ascension=10,
                 combat_search="legacy", segment_length=1, dedup_mode="default", tactical_ordering=True):
        self.root = Path(root).resolve(); self.launcher = launcher; self.native = native
        check_permits(self.root)
        if Path(__file__).resolve()!=module_path(self.root/'repo','agent').resolve():
            raise ValueError('Collector must load its own isolated runtime')
        self.policy = HeadlessValuePolicy(checkpoint)
        self.model_sha = hashlib.sha256(Path(checkpoint).read_bytes()).hexdigest()
        # Every attempt keeps the adapter/build identity that actually produced it.
        files = runtime_files(self.root, native)
        self.runtime_hashes = {path.relative_to(self.root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                               for path in files}
        profile = self.root / "profile-baseline-manifest.json"
        self.profile_sha = hashlib.sha256(profile.read_bytes()).hexdigest() if profile.is_file() else None
        self.max_actions = max_actions
        if type(ascension) is not int or not 0 <= ascension <= 20: raise ValueError("Invalid ascension")
        self.ascension = ascension
        if combat_search not in TURN_POLICIES:
            raise ValueError("Invalid combat search policy")
        self.combat_search = combat_search
        self.tactical_ordering = tactical_ordering
        if segment_length not in (1, 2, 4): raise ValueError("Invalid segment length")
        if dedup_mode not in ("default","new_turn") or (dedup_mode=="new_turn" and combat_search!="legacy"):
            raise ValueError("New-turn dedup is a legacy policy experiment")
        self.segment_length, self.dedup_mode = segment_length, dedup_mode
        search_path = self.root / "combat-search.json"
        if search_path.exists():
            self.runtime_search = read_combat_search(search_path)
            if combat_search != self.runtime_search["search_policy"]:
                raise ValueError("CLI policy differs from frozen combat-search.json")
            self.search_profile = self.runtime_search["search_profile"]
            self.runtime_hashes[search_path.name] = hashlib.sha256(search_path.read_bytes()).hexdigest()

    def budget_for(self, room):
        if self.frozen_budgets is None:
            if self.runtime_search is not None:
                return self.runtime_search["max_expansions"], self.runtime_search["timeout_ms"]
            return deployment_budget(room, self.combat_search)
        # Vanilla event encounters and special monster rooms use the ordinary tier.
        category = {"MonsterRoomElite":"ELITE","MonsterRoomBoss":"BOSS","ELITE":"ELITE","BOSS":"BOSS"}.get(room,"MONSTER")
        row = self.frozen_budgets[category]
        return row["max_expansions"], row["timeout_ms"]

    @property
    def combat_policy(self):
        return "native-"+self.search_profile.lower() if self.combat_search == "legacy" else self.combat_search

    def episode(self, role, seed, *, attempt_id=None):
        check_permits(self.root)  # Check before launching a Java process or requesting new_run.
        attempt_id = attempt_id or uuid.uuid4().hex
        if uuid.UUID(attempt_id).hex != attempt_id: raise ValueError('Expected a canonical attempt UUID')
        reports = self.root / 'reports'; reports.mkdir(exist_ok=True)
        attempt_path = reports / f'attempt-{attempt_id}.json'
        if attempt_path.exists(): raise ValueError('Attempt UUID already exists')
        attempt = {'schema': 'headless-attempt-v1', 'attempt_id': attempt_id, 'role': role,
                   'seed': seed, 'status': 'running', 'started_unix': time.time()}
        save_json(attempt_path, attempt)
        engine = cpp = writer = current = result = None; context = {'stage': 'initializing'}
        cpu_start = time.process_time(); started = time.monotonic()
        resources = {"peak_rss_bytes": 0, "peak_pss_bytes": 0, "sampled_cpu_seconds": 0.0, "started_unix":time.time()}
        try:
            self.policy.begin_episode()
            engine = Worker(self.launcher); cpp = Worker(self.native)
            context['stage'] = 'boot'
            boot = require(engine.execute({"operation": "state"}, 15000))
            context.update(stage='new_run', boot=boot)
            current = require(engine.execute({"operation": "new_run", "expected_state_token": boot["state_token"],
                "seed": seed, "character": role, "ascension": self.ascension}, 15000))
            writer = EpisodeWriter(self.root, role=role, seed=seed, policy={"combat": self.combat_policy, "combat_search_settings": self.runtime_search, "segment_length":self.segment_length, "dedup_mode":self.dedup_mode, "tactical_ordering":self.tactical_ordering, "noncombat": "format12-value+existing-router", "model_sha256": self.model_sha, "ascension": self.ascension, "runtime_sha256": self.runtime_hashes, "profile_manifest_sha256": self.profile_sha, "attempt_id": attempt_id})
            run = writer.episode_id; writer.observation(current)
            combat = HeadlessCombatBridge(); menus = IroncladInteractionBridge(characters=ROLES); seen = {}; opened_boss_chests=set()
            suffix = []
            fingerprint = {}
            for step in range(self.max_actions):
                check_permits(self.root)
                if step % 5 == 0:
                    sample = resource_sample(engine, cpp)
                    resources["peak_rss_bytes"] = max(resources["peak_rss_bytes"], sample["rss_bytes"])
                    resources["peak_pss_bytes"] = max(resources["peak_pss_bytes"], sample["pss_bytes"])
                    resources["sampled_cpu_seconds"] = max(resources["sampled_cpu_seconds"], sample["cpu_seconds"]-cpu_start)
                    resources["elapsed_seconds"] = time.monotonic()-started
                raw = current["state"]; context = {"step": step, "phase": raw["phase"], "before": current}
                check_decision_progress(self.policy, run, raw, seen)
                if raw["phase"] == "PLAYER_TURN" or is_native_combat_selection(raw):
                    before = combat.observe(raw, run_id=run, sequence=current["sequence"])
                    cap, timeout = self.budget_for(raw.get("room"))
                    request = {"snapshot": before.snapshot, "session_id": run,
                               "search_profile": self.search_profile, "max_expansions": cap, "timeout_ms": timeout}
                    context["request"] = request
                    if not self.tactical_ordering: request["tactical_ordering"] = False
                    if self.combat_search != "legacy":
                        request["search_policy"] = self.combat_search
                    if self.segment_length != 1: request["segment_length"] = self.segment_length
                    if self.dedup_mode != "default": request["dedup_mode"] = self.dedup_mode
                    if suffix and reusable_segment_step(before.snapshot, fingerprint, suffix[0]):
                        native = suffix.pop(0)
                    else:
                        native = require(cpp.execute(request, timeout, check_cancel=lambda: check_permits(self.root))
                                         if timeout==0 else cpp.execute(request, timeout))
                        suffix = list(native.pop("continuations", []))
                    context["prediction"] = native
                    plan = combat.prepare(before, native)
                    if not combat.validate_before(plan, before): raise ValueError("Stale combat plan")
                    method, args = plan.native_method, plan.native_args
                    context.update(method=method,args=args)
                    policy = {"policy": self.combat_policy, "metrics": native.get("metrics", {})}
                    after = require(engine.execute({"operation": "action", "expected_state_token": current["state_token"], "method": method, "args": args}, 15000))
                    context["after"] = after
                    verify_driver(current, after, method, args)
                    observed = combat.observe(after["state"], run_id=run, sequence=after["sequence"])
                    fingerprint = cpp.execute({"operation": "fingerprint", "snapshot": observed.snapshot}, 1000) if observed.snapshot is not None else {}
                    context["fingerprint"] = fingerprint
                    effect = verify_combat_effect(plan, observed, fingerprint)
                else:
                    suffix.clear()
                    fingerprint = {}
                    before = menus.observe(raw, run_id=run, sequence=current["sequence"])
                    terminal = next((a for a in before.actions if a.native_method == "LOCAL_ACK_TERMINAL"), None)
                    if terminal is not None:
                        receipt = menus.acknowledge_terminal(menus.prepare(before, terminal.api_action), before)
                        result = writer.finish(terminal=receipt); result["validation"] = validate_episode(result["path"]); result.update(resources=resources, attempt_id=attempt_id)
                        return result
                    selected, policy = self.policy.choose(before)
                    plan = menus.prepare(before, selected.api_action)
                    if not menus.validate_before(plan, before): raise ValueError("Stale menu plan")
                    method, args = selected.native_method, selected.native_args
                    context.update(method=method,args=args)
                    if method=='openChest' and raw['phase']=='BOSS_CHEST':
                        location=(raw['act'],raw['floor'])
                        if location in opened_boss_chests: raise ValueError('Boss chest reopen loop')
                        opened_boss_chests.add(location)
                    if method.startswith("LOCAL_"): raise ValueError("Unimplemented local intent in collector")
                    after = require(engine.execute({"operation": "action", "expected_state_token": current["state_token"], "method": method, "args": args}, 15000))
                    context["after"] = after
                    verify_driver(current, after, method, args)
                    def import_outcome(snapshot):
                        proof=cpp.execute({'operation':'fingerprint','snapshot':snapshot},1000)
                        context['fingerprint']=proof
                        return proof
                    observed = menus.observe_outcome(after["state"], run_id=run, sequence=after["sequence"],
                        combat=combat,import_snapshot=import_outcome)
                    effect = verify_effect(plan, observed).status
                context.update(method=method, args=args, effect=effect)
                if effect != "APPLIED": raise ValueError("Unverified action effect: " + effect)
                writer.transition(before_sequence=current["sequence"], after_sequence=after["sequence"], method=method, args=args, effect=effect, policy=policy)
                writer.observation(after); current = after
                if step % 25 == 0: print(json.dumps({"role": role, "step": step, "floor": raw["floor"], "phase": raw["phase"]}), flush=True)
            raise ValueError("Episode action budget reached")
        except Exception as error:
            diagnostic = reports / f'failure-{attempt_id}.json'
            save_json(diagnostic, {"attempt_id": attempt_id, "episode_id": writer.episode_id if writer else None,
                                   "role": role, "seed": seed, "error": repr(error), **context})
            if writer is not None and not writer.closed:
                result = writer.finish(error=error)
            else:
                sealed_path = result.get('path') if result is not None else None
                result = {'episode_id': writer.episode_id if writer else None, 'role': role, 'seed': seed,
                          'status': 'aborted', 'censored': True, 'terminal': None,
                          'transitions': writer.transitions if writer else 0,
                          'last_floor': writer.last_floor if writer else None}
                if sealed_path: result['unvalidated_episode_path'] = sealed_path
            result.update(attempt_id=attempt_id, diagnostic=str(diagnostic), resources=resources, error=repr(error))
            return result
        finally:
            sample=resource_sample(engine,cpp)
            resources['peak_rss_bytes']=max(resources['peak_rss_bytes'],sample['rss_bytes'])
            resources['peak_pss_bytes']=max(resources['peak_pss_bytes'],sample['pss_bytes'])
            resources['sampled_cpu_seconds']=max(resources['sampled_cpu_seconds'],sample['cpu_seconds']-cpu_start)
            resources.update(elapsed_seconds=time.monotonic()-started,ended_unix=time.time())
            for worker in (engine, cpp):
                if worker is not None: worker.close()
            if result is not None:
                attempt.update(status=result['status'], ended_unix=time.time(), result=result)
                save_json(attempt_path, attempt)


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, required=True); p.add_argument("--launcher", required=True); p.add_argument("--native", required=True)
    p.add_argument("--checkpoint", required=True); p.add_argument("--seed", type=int, default=5050001); p.add_argument("--max-actions", type=int, default=3000)
    p.add_argument("--runtime-id",help="Frozen matching hardware/lane, game and noncombat configuration ID for throughput comparisons")
    p.add_argument("--segment-length", choices=(1,2,4), type=int, default=1)
    p.add_argument("--dedup-mode", choices=("default","new_turn"), default="default")
    p.add_argument("--tactical-ordering", action=argparse.BooleanOptionalAction, default=True)
    p.add_argument("--combat-search", choices=TURN_POLICIES, default="legacy")
    p.add_argument("--ascension", type=int, default=10)
    p.add_argument("--rounds", type=int, default=1, help="Number of seed blocks across the selected roles")
    p.add_argument("--budgets-file", type=Path, help="Frozen MONSTER, ELITE, BOSS budgets shared by both arms")
    p.add_argument("--roles", nargs="+", choices=ROLES, default=list(ROLES))
    a = p.parse_args(argv)
    signal.signal(signal.SIGTERM, request_stop); signal.signal(signal.SIGINT, request_stop)
    agent = HeadlessCollector(a.root, a.launcher, a.native, a.checkpoint, max_actions=a.max_actions,
                              ascension=a.ascension, combat_search=a.combat_search, segment_length=a.segment_length, dedup_mode=a.dedup_mode, tactical_ordering=a.tactical_ordering)
    if a.rounds < 1: p.error("--rounds must be positive")
    if a.budgets_file:
        if agent.runtime_search is not None: p.error("--budgets-file would override combat-search.json")
        table = json.loads(a.budgets_file.read_text(encoding="utf-8-sig"))
        for room in ("MONSTER","ELITE","BOSS"):
            for key,upper in (("max_expansions",1000000),("timeout_ms",90000)):
                value=table[room][key]
                lower=0 if key=="timeout_ms" and a.combat_search=="legacy" else 1
                if type(value) is not int or not lower<=value<=upper: p.error("Invalid frozen budget")
        agent.frozen_budgets=table
    else:
        table={k:dict(zip(("max_expansions","timeout_ms"),agent.budget_for(k))) for k in ("MONSTER","ELITE","BOSS")}
    budget_id=hashlib.sha256(json.dumps(table,sort_keys=True,separators=(",",":")).encode()).hexdigest()
    results = []; started = time.time(); batch_id = str(time.time_ns())
    for n, role in enumerate(a.roles*a.rounds):
        if _STOP_REQUESTED: break
        result = agent.episode(role, a.seed+n); results.append(result)
        print(json.dumps({k: result[k] for k in ("role", "status", "transitions", "last_floor")}), flush=True)
        summary = json.dumps({"batch_id": batch_id, "started_unix": started, "ended_unix": time.time(),
                              "runtime_id":a.runtime_id,"model_sha256":agent.model_sha,"budget_id":budget_id,"expected_attempts":len(a.roles)*a.rounds,"native_build":agent.runtime_hashes[Path(a.native).resolve().relative_to(agent.root).as_posix()],
                              "combat_policy":a.combat_search,"segment_length":a.segment_length,"dedup_mode":a.dedup_mode,"tactical_ordering":a.tactical_ordering,"results": results}, indent=2)
        (a.root / f"reports/collection-batch-{batch_id}.json").write_text(summary, encoding="utf-8")
        (a.root / "reports/collection-summary.json").write_text(summary, encoding="utf-8")
    return 130 if _STOP_REQUESTED else 0 if len(results) == len(a.roles)*a.rounds and all(r["status"] == "complete" for r in results) else 1


if __name__ == "__main__": raise SystemExit(main())
