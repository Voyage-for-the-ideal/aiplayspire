"""Capture first-turn A10 SaveStates from a separate vanilla-game profile."""
from __future__ import annotations

import argparse
from contextlib import ExitStack
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
from types import SimpleNamespace

from battle_engine import protocol
from battle_engine.tools.a10_corpus import coverage, inventory
from battle_engine.tools.balanced_ab import ROLES, digest, write
from battle_engine.tools.oracle import freeze_oracle_preferences, isolated_java_env


MODS=("BaseMod.jar","SaveStateMod.jar","StSLib.jar","LudicrousSpeed.jar","BattleAiMod.jar","CommunicationMod.jar")


def launch(stack, install, runtime, flags, *, client=False):
    runtime.mkdir()
    for name in ("desktop-1.0.jar","ModTheSpire.jar"):
        shutil.copyfile(install/name,runtime/name)
    (runtime/"mods").mkdir()
    for name in MODS:
        shutil.copyfile(install/"mods"/name,runtime/"mods"/name)
    shutil.copytree(install/"jre",runtime/"jre")
    freeze_oracle_preferences(install/"preferences",runtime/"preferences")
    config=runtime/"appdata"/"ModTheSpire"
    config.mkdir(parents=True)
    write(config/"mod_lists.json",dict(defaultList="A10Capture",lists={"A10Capture":list(MODS)}))
    if client:
        options=config/"BattleAIMod"
        options.mkdir()
        (options/"options.properties").write_text("auto_start_ai=true\nsearch_profile=BALANCED\n")
    env=isolated_java_env(runtime)
    log=stack.enter_context((runtime/"game.log").open("w",encoding="utf-8"))
    command=[str(runtime/"jre"/"bin"/"java.exe"),*flags,"-Xms256m","-Xmx1536m","-jar","ModTheSpire.jar",
             "--profile","A10Capture","--skip-launcher","--skip-intro","--jre51"]
    process=subprocess.Popen(command,cwd=runtime,env=env,stdout=log,stderr=subprocess.STDOUT,
                             creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))

    def stop():
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
    stack.callback(stop)
    return process


def wait_for_java(process, port):
    deadline=time.monotonic()+120
    while process.poll() is None and time.monotonic()<deadline:
        try:
            with socket.create_connection(("127.0.0.1",port),timeout=.5) as sock:
                protocol.send(sock,'{"type":"PING"}')
                if json.loads(protocol.receive(sock)).get("test_mode"):
                    return
        except (OSError,EOFError):
            pass
        time.sleep(.5)
    raise RuntimeError("A10 capture search engine did not become ready")


def offline_call(**kwargs):
    raise RuntimeError("A10 corpus capture uses only the frozen local value model and rules")


def request_battle():
    # The source mod owns the readiness/overlap gate. This avoids depending on
    # AutoStart's one-time room-entry hook, which can run before the socket is ready.
    try:
        with socket.create_connection(("127.0.0.1",5200),timeout=.5) as sock:
            protocol.send(sock,'{"command":"startAi"}')
            protocol.receive(sock)
    except (OSError,EOFError):
        pass


def drive(client, role, deadline, processes, goal_floor, runs):
    # Use the same actual selections and bridge, with no external LLM request.
    from sts_ai_framework.llm_agent import LLMAgent
    from sts_ai_framework.models import ActionType, GameAction
    from sts_ai_framework.run_lifecycle import get_lifecycle_action, is_lifecycle_state

    agent=LLMAgent(game_client=client,debug_prompt_file=None)
    if agent.value_engine is None:
        raise RuntimeError("A10 capture requires the frozen format-12 checkpoint")
    agent.llm_client.close()
    agent.llm_client=SimpleNamespace(chat=SimpleNamespace(completions=SimpleNamespace(create=offline_call)))
    started=False
    completed=0
    previous_action=None
    progress=None
    moved_at=time.monotonic()
    while time.monotonic()<deadline:
        if any(p.poll() is not None for p in processes):
            raise RuntimeError("A10 capture game process exited; see game.log")
        state=client.get_state()
        if state is None:
            if time.monotonic()-moved_at>180:
                raise TimeoutError("No original game state for 180 seconds")
            time.sleep(.4)
            continue
        signature=(state.seed_group,state.floor,state.screen_type,state.room_phase,
                   state.selected_character,state.ascension_level,tuple(state.choice_list or ()),
                   state.is_end_turn_button_enabled,state.grid_selected_count,
                   (state.player.current_hp,state.player.block,state.player.energy) if state.player else None,
                   tuple(c.reference for c in state.hand),
                   tuple((m.id,m.current_hp,m.block,m.intent) for m in state.monsters))
        if signature!=progress:
            progress,moved_at=signature,time.monotonic()
        elif time.monotonic()-moved_at>180:
            raise TimeoutError(f"Original game stalled: {role}, floor {state.floor}, {state.screen_type}")
        if state.character and not is_lifecycle_state(state):
            if state.character!=role or state.ascension_level!=10:
                raise ValueError(f"Wrong run identity: {state.character} A{state.ascension_level}")
            started=True
            if goal_floor and (state.floor or 0)>=goal_floor:
                return "requested_floor",completed
        if started and state.screen_type=="GAME_OVER":
            completed+=1
            if completed==runs:
                return "terminal",completed
            started=False
            agent.history.clear()
            agent.last_screen_type=None
        if is_lifecycle_state(state):
            action=get_lifecycle_action(state,character=role,ascension=10,auto_restart=True)
        elif state.room_phase=="COMBAT":
            request_battle()
            time.sleep(.5)
            continue
        elif state.screen_type=="COMBAT_REWARD" and not state.choice_list and not state.can_proceed and state.can_cancel:
            action=GameAction(type=ActionType.CANCEL)
        elif state.screen_type!="NONE" and (state.choice_list or state.can_proceed):
            action=agent.choose_action(state)
        else:
            action=None
        if action and action.type!=ActionType.WAIT:
            key=(state.floor,state.screen_type,state.selected_character,tuple(state.choice_list or ()),
                 state.ascension_level,str(action.to_api_payload()))
            now=time.monotonic()
            if previous_action is None or previous_action[0]!=key or now-previous_action[1]>=3:
                ok,_,message=client.submit_action(action)
                if not ok:
                    raise RuntimeError(message)
                previous_action=(key,now)
        time.sleep(.25)
    return "time_limit",completed


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-dir",required=True,type=Path)
    parser.add_argument("--checkpoint",required=True,type=Path)
    parser.add_argument("--output",required=True,type=Path)
    parser.add_argument("--role",required=True,choices=ROLES)
    parser.add_argument("--timeout-seconds",type=int,default=1200)
    parser.add_argument("--stop-floor",type=int,default=0)
    parser.add_argument("--runs",type=int,default=1)
    parser.add_argument("--java-port",type=int,default=15157)
    parser.add_argument("--http-port",type=int,default=15057)
    parser.add_argument("--oracle-http-port",type=int,default=15058)
    args=parser.parse_args(argv)
    if args.timeout_seconds<=0 or args.stop_floor<0 or args.runs<=0 or (args.stop_floor and args.runs!=1):
        parser.error("Invalid capture bounds")
    ports={args.java_port,args.http_port,args.oracle_http_port,5124,5200}
    if len(ports)!=5:
        parser.error("Capture ports must be distinct")
    for port in ports:
        with socket.socket() as sock:
            sock.bind(("127.0.0.1",port))
    source=args.game_dir.resolve(strict=True)
    root=args.output.resolve()
    root.mkdir(parents=True)
    os.environ["STS_VALUE_CHECKPOINT"]=str(args.checkpoint.resolve(strict=True))
    os.environ.setdefault("DEEPSEEK_API_KEY","offline-capture")
    import torch
    from sts_ai_framework.game_client import GameClient
    torch.set_num_threads(1)
    result={"role":args.role,"ascension":10,"checkpoint_sha256":digest(args.checkpoint)}
    try:
        with ExitStack() as stack:
            oracle=launch(stack,source,root/"oracle",
                          ["-DisServer=true","-DisPlaidMode=true","-Dbattleai.testMode=true",
                           f"-Dbattleai.port={args.java_port}",f"-Dcommunicationmod.port={args.oracle_http_port}"])
            wait_for_java(oracle,args.java_port)
            client=launch(stack,source,root/"client",
                          ["-Dbattleai.backend=java",f"-Dbattleai.clientPort={args.java_port}",
                           f"-Dcommunicationmod.port={args.http_port}"],client=True)
            endpoint=GameClient(f"http://127.0.0.1:{args.http_port}")
            result["stop"],result["completed_runs"]=drive(
                endpoint,args.role,time.monotonic()+args.timeout_seconds,(oracle,client),args.stop_floor,args.runs)
    except (RuntimeError,ValueError,OSError,TimeoutError) as error:
        result["error"]=str(error)
    result["counts"]=coverage(inventory([root/"client"/"startstates"]))
    write(root/"capture.json",result)
    print(json.dumps(result,ensure_ascii=False),flush=True)
    return 1 if "error" in result else 0


if __name__=="__main__":
    raise SystemExit(main())
