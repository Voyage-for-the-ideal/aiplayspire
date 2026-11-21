"""Isolated real-game smoke test of the packaged JNI path; no search server."""
import argparse
import json
import os
from pathlib import Path
import shutil
import tempfile
import subprocess
import sys
import time
from zipfile import ZipFile, ZipInfo

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from battle_engine.native_api import ROOT


ORACLE_TIPS = (
    "BLOCK TIP", "CARD_REWARD_TIP", "COMBAT_TIP", "ENERGY_USE_TIP", "INTENT_TIP",
    "M_POWER_TIP", "NO_FTUE", "POTION_TIP", "POWER_TIP", "RELIC_TIP", "SHUFFLE_TIP",
)


def freeze_oracle_preferences(source, destination):
    """Copy the profile's unlocks and skip tutorials only inside the oracle."""
    shutil.copytree(source, destination)
    # libGDX preferences encode booleans as strings. Slot 0 uses STSTips;
    # later slots prefix it with the slot number.
    files = {destination / "STSTips", *(p for p in destination.glob("*STSTips") if p.is_file())}
    for path in files:
        tips = json.loads(path.read_text(encoding="utf-8-sig")) if path.is_file() else {}
        tips.update(dict.fromkeys(ORACLE_TIPS, "true"))
        path.write_text(json.dumps(tips), encoding="utf-8")


def isolated_java_env(runtime):
    """Keep ModTheSpire native/JAR extraction private, including JRE relaunches."""
    tmp=(runtime/"tmp").resolve()
    tmp.mkdir()
    # ModTheSpire also starts its own JVM. Environment options survive the
    # relaunch, unlike a flag passed only to the first process.
    return dict(os.environ,LOCALAPPDATA=str(runtime/"appdata"),APPDATA=str(runtime/"appdata"),
                TEMP=str(tmp),TMP=str(tmp),TMPDIR=str(tmp),
                JAVA_TOOL_OPTIONS=f'-Djava.io.tmpdir="{tmp}" -Daiplayspire.disablePhysicalController=true')


INPUT_GUARD_JAR = "AIPlaySpireOfflineInputGuard.jar"


def build_input_guard(game_dir, build_root):
    """Build the test-only joystick shim; leave original JARs and mods untouched."""
    javac=shutil.which("javac")
    if not javac:
        raise RuntimeError("The original-engine test launcher needs the existing JDK (javac)")
    build_root.mkdir(parents=True)
    source=Path(__file__).with_name("vanilla_support")
    subprocess.run([javac,"--release","8","-classpath",str(game_dir/"ModTheSpire.jar"),
                    "-d",str(build_root),str(source/"DisablePhysicalController.java")],
                   check=True,timeout=60)
    target=build_root/INPUT_GUARD_JAR
    with ZipFile(target,"x") as jar:
        jar.writestr(ZipInfo("ModTheSpire.json"),(source/"ModTheSpire.json").read_bytes())
        klass="aiplayspire/offline/DisablePhysicalController.class"
        jar.writestr(ZipInfo(klass),(build_root/klass).read_bytes())
    return target



def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-dir",type=Path,required=True)
    parser.add_argument("--snapshot",type=Path,required=True)
    parser.add_argument("--battle-jar",type=Path,default=ROOT.parent/"scumthespire/target/BattleAiMod.jar")
    parser.add_argument("--segment-length",type=int,choices=(1,2,4),default=1)
    parser.add_argument("--search-policy",choices=("legacy","layered-v1"),default="legacy")
    parser.add_argument("--tactical-ordering",action=argparse.BooleanOptionalAction,default=True)
    parser.add_argument("--dedup-mode",choices=("default","new_turn"),default="default")
    parser.add_argument("--profile",choices=("FAST","BALANCED","DEEP"),default="BALANCED")
    parser.add_argument("--budget-ms",type=int)
    parser.add_argument("--max-expansions",type=int)
    parser.add_argument("--test-takeover",action="store_true")
    mode=parser.add_mutually_exclusive_group()
    mode.add_argument("--full-fight",action="store_true")
    mode.add_argument("--selector-case",choices=("meditate","meditate-plus","exhume","exhume-single","forethought","forethought-plus",
                                               "forethought-zero","omniscience","omniscience-attack","omniscience-scry"))
    parser.add_argument("--auto-start",action="store_true")
    parser.add_argument("--keep-runtime",action="store_true",help="Keep copied game binaries in addition to traces and diagnostics")
    parser.add_argument("--timeout",type=int,default=120)
    args=parser.parse_args(argv)
    if args.selector_case and (args.test_takeover or args.auto_start):
        parser.error("Selector replay drives its own commands; omit takeover and autostart options")
    game=args.game_dir.resolve(strict=True)
    out=ROOT/"out"; out.mkdir(exist_ok=True)
    runtime=Path(tempfile.mkdtemp(prefix="jni-smoke-",dir=out))
    mods=runtime/"mods"; mods.mkdir()
    for name in ("ModTheSpire.jar","desktop-1.0.jar"): shutil.copy2(game/name,runtime/name)
    enabled=["BaseMod.jar","StSLib.jar","SaveStateMod.jar","LudicrousSpeed.jar","BattleAiMod.jar"]
    for name in enabled: shutil.copy2(args.battle_jar if name=="BattleAiMod.jar" else game/"mods"/name,mods/name)
    shutil.copytree(game/"jre",runtime/"jre")
    freeze_oracle_preferences(game/"preferences",runtime/"preferences")
    guard=build_input_guard(game,runtime/"test-classes")
    source=Path(__file__).with_name("vanilla_support")/"NativeBattleSmoke.java"
    cp=os.pathsep.join(map(str,[runtime/"ModTheSpire.jar",runtime/"desktop-1.0.jar",mods/"*",runtime/"test-classes"]))
    subprocess.run(["javac","--release","8","-classpath",cp,"-d",str(runtime/"test-classes"),str(source),str(source.with_name("SelectorSmoke.java"))],check=True)
    with ZipFile(guard,"a") as jar:
        for name in ("NativeBattleSmoke","SelectorSmoke"):
            klass=f"aiplayspire/offline/{name}.class"
            jar.write(runtime/"test-classes"/klass,klass)
    shutil.copy2(guard,mods/guard.name)
    config=runtime/"appdata"/"ModTheSpire"; config.mkdir(parents=True)
    (config/"mod_lists.json").write_text(json.dumps(dict(defaultList="NativeSmoke",lists={"NativeSmoke":[*enabled,guard.name]})))
    settings=config/"BattleAIMod";settings.mkdir()
    (settings/"options.properties").write_text(f"auto_start_ai={str(args.auto_start).lower()}\nsearch_profile={args.profile}\n",encoding="ascii")
    env=isolated_java_env(runtime)
    trace=runtime/"trace.jsonl"
    env["JAVA_TOOL_OPTIONS"]+=f' -Daiplayspire.smokeSnapshot="{args.snapshot.resolve(strict=True)}" -Dbattleai.trace="{trace}"'
    env["JAVA_TOOL_OPTIONS"]+=f' -Dbattleai.segmentLength={args.segment_length} -Daiplayspire.smokeAuto={str(args.auto_start).lower()} -Duser.home="{runtime}"'
    env["JAVA_TOOL_OPTIONS"]+=f" -Dbattleai.searchPolicy={args.search_policy} -Dbattleai.dedupMode={args.dedup_mode}"
    env["JAVA_TOOL_OPTIONS"]+=f" -Dbattleai.tacticalOrdering={str(args.tactical_ordering).lower()}"
    if args.selector_case: env["JAVA_TOOL_OPTIONS"]+=f" -Daiplayspire.selectorCase={args.selector_case}"
    if args.test_takeover: env["JAVA_TOOL_OPTIONS"]+=" -Daiplayspire.testTakeover=true"
    if args.max_expansions is not None: env["JAVA_TOOL_OPTIONS"]+=f" -Dbattleai.maxExpansions={args.max_expansions}"
    if args.budget_ms is not None: env["JAVA_TOOL_OPTIONS"]+=f" -Dbattleai.clientBudgetMs={args.budget_ms}"
    command=[str(runtime/"jre/bin/java.exe"),"-Xmx1536m","-jar","ModTheSpire.jar","--profile","NativeSmoke","--skip-launcher","--skip-intro","--jre51"]
    print(runtime,flush=True)
    with (runtime/"game.log").open("w") as log:
        proc=subprocess.Popen(command,cwd=runtime,env=env,stdout=log,stderr=subprocess.STDOUT,
                              creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
        try:
            deadline=time.monotonic()+args.timeout
            while time.monotonic()<deadline and proc.poll() is None:
                if trace.exists():
                    records=[json.loads(line) for line in trace.read_bytes().split(b"\n")[:-1]]
                    if any(row["kind"]=="error" for row in records): return 1
                    expected="selector_verified" if args.selector_case else "terminal" if args.full_fight else "applied"
                    if any(row["kind"]==expected for row in records) and (not args.test_takeover or (runtime/"takeover-verified").is_file()):
                        if args.selector_case:
                            import hashlib
                            with ZipFile(args.battle_jar) as jar:
                                dll=jar.read("native/windows-x86_64/battle_jni.dll")
                            evidence=dict(case=args.selector_case, real_engine_checked=True,
                                snapshot_sha256=hashlib.sha256((runtime/"selector-start.json").read_bytes()).hexdigest(),
                                jar_sha256=hashlib.sha256(args.battle_jar.read_bytes()).hexdigest(),
                                native_build=hashlib.sha256(dll).hexdigest(), trace=str(trace))
                            (runtime/"selector-result.json").write_text(json.dumps(evidence,indent=2))
                        if args.full_fight:
                            import hashlib
                            from battle_engine.state import load
                            terminal=next(r for r in records if r["kind"]=="terminal")
                            responses=[r for r in records if r["kind"]=="response"]
                            initial_hp=load(args.snapshot)["player_state"]["creature"]["current_health"]
                            evidence=dict(sha256=hashlib.sha256(args.snapshot.read_bytes()).hexdigest(),
                                real_engine_checked=True,outcome="win" if terminal["outcome"]=="victory" else "loss",
                                hp_lost=initial_hp-terminal["hp"],native_build=responses[0]["build"],
                                potions_used=sum(json.loads(r["command"])["type"]=="POTION" for r in responses) if all("command" in r for r in responses) else None,
                                settings={k:v for k,v in next(r for r in records if r["kind"]=="request").items() if k not in ("session","kind")})
                            (runtime/"battle-result.json").write_text(json.dumps(evidence,indent=2))
                        print(f"Verified {expected} in real game via JNI; trace: {trace}");return 0
                time.sleep(.2)
            return 1
        finally:
            if proc.poll() is None:
                proc.terminate()
                try: proc.wait(timeout=10)
                except subprocess.TimeoutExpired: proc.kill();proc.wait()
            if not args.keep_runtime:
                # This call created the directory exclusively. Never remove the original game,
                # user configuration, traces or diagnostics; a locked copy can be removed later.
                base=(ROOT/"out").resolve(strict=True)
                if runtime.resolve(strict=True).parent != base: raise RuntimeError("unexpected oracle root")
                for name in ("desktop-1.0.jar","ModTheSpire.jar","mods","jre","tmp","test-classes"):
                    item=runtime/name
                    if item.is_symlink() or item.resolve().parent!=runtime.resolve(): raise RuntimeError("unexpected oracle path")
                    try:
                        if item.is_dir(): shutil.rmtree(item)
                        elif item.exists(): item.unlink()
                    except OSError as e: print(f"Retained locked QA artifact {item}: {e}", file=sys.stderr)

if __name__=="__main__": raise SystemExit(main())
