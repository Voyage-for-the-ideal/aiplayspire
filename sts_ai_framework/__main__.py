import logging
import time
import os
import sys
import argparse
from dotenv import load_dotenv
from colorama import init, Fore, Style

# Try importing from package, otherwise fall back to local (if user runs script directly inside folder, though discouraged)
try:
    from .config import (STS_API_BASE_URL, LLM_MODEL, DEBUG_PROMPT_FILE, RUN_LOG_DIR,
                         RUN_DEADLINE_SECONDS, COMBAT_HEARTBEAT_SECONDS)
    from .game_client import GameClient
    from .llm_agent import LLMAgent
    from .models import ActionType
    from .run_log import setup_run_log, install_stdout_tee
    from .run_events import RunEvents, RunSession, classify_run_end
except ImportError:
    # Hack to allow running python sts_ai_framework/__main__.py
    sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    from sts_ai_framework.config import (STS_API_BASE_URL, LLM_MODEL, DEBUG_PROMPT_FILE, RUN_LOG_DIR,
                                         RUN_DEADLINE_SECONDS, COMBAT_HEARTBEAT_SECONDS)
    from sts_ai_framework.game_client import GameClient
    from sts_ai_framework.llm_agent import LLMAgent
    from sts_ai_framework.models import ActionType
    from sts_ai_framework.run_log import setup_run_log, install_stdout_tee
    from sts_ai_framework.run_events import RunEvents, RunSession, classify_run_end

# Initialize colorama
init()


def _fetch_post_action_state(client, retries: int = 2, delay: float = 0.15):
    """提交后立即读取状态用于快速确认 (0.15s x 2, 未读到则交由 pending 后续轮询)。"""
    for _ in range(retries):
        s = client.get_state()
        if s is not None:
            return s
        time.sleep(delay)
    return None


def _action_label(state, action) -> str:
    """动作的人类可读简述, 用于单行摘要 (不参与结构化事件)。"""
    if action.type == ActionType.PLAY and action.card_index is not None:
        if action.card_index < len(state.hand):
            return f"打牌 {state.hand[action.card_index].name}"
        return f"打牌[{action.card_index}]"
    if action.type == ActionType.POTION:
        return f"药水[{action.potion_index}]"
    if action.type == ActionType.CHOOSE and action.choice_index is not None:
        choices = state.choice_list or []
        if action.choice_index < len(choices):
            return f"选择[{action.choice_index}] {choices[action.choice_index]}"
        return f"选择[{action.choice_index}]"
    return action.type.value


def main():
    parser = argparse.ArgumentParser(description="运行杀戮尖塔 AI Agent")
    parser.add_argument("--model", type=str, default=LLM_MODEL, help="使用的 DeepSeek 模型 (例如 deepseek-v4-flash)")
    parser.add_argument("--interval", type=float, default=2.0, help="行动间隔时间 (秒)")
    parser.add_argument("--debug-prompt-file", type=str, default=DEBUG_PROMPT_FILE, help="将最新 Prompt 持续写入到指定文件，便于调试")
    args = parser.parse_args()

    # 安装运行日志:此后所有终端输出同时写入 debug/run_YYYYMMDD_HHMMSS.log
    run_log_path = setup_run_log(RUN_LOG_DIR)
    tee = install_stdout_tee()
    run_id = os.path.basename(run_log_path)[:-4]  # 去掉 .log 后缀, .jsonl 与之同名

    print(Fore.YELLOW + "正在启动杀戮尖塔 AI 框架..." + Style.RESET_ALL)
    print(f"模型: {args.model}")
    print(f"连接到 Mod 地址: {STS_API_BASE_URL}")
    if args.debug_prompt_file:
        print(f"Prompt 调试文件: {args.debug_prompt_file}")
    print(f"运行日志: {run_log_path} (结构化事件: {os.path.join(RUN_LOG_DIR, run_id + '.jsonl')})")

    events = RunEvents(
        RUN_LOG_DIR, run_id,
        pid=os.getpid(),
        cli={"model": args.model, "interval": args.interval},
        base_url=STS_API_BASE_URL,
    )
    client = GameClient(base_url=STS_API_BASE_URL)
    session = RunSession(events, RUN_DEADLINE_SECONDS, COMBAT_HEARTBEAT_SECONDS)

    # 终局状态 (finally 中统一收尾)
    status, confidence, reason = "unknown", "low", "未知"
    started_ok = False

    try:
        # Check connection
        print("正在检查与 Mod 的连接...")
        state, kind, msg = client.get_state_detailed()
        if not state:
            print(Fore.RED + "无法连接到游戏。请确保《杀戮尖塔》已启动并加载了 CommunicationMod。" + Style.RESET_ALL)
            print(Fore.YELLOW + "5秒后重试..." + Style.RESET_ALL)
            time.sleep(5)
            state, kind, msg = client.get_state_detailed()
            if not state:
                status, confidence, reason = "startup_failed", "high", f"启动连接失败 ({kind}: {msg})"
                print(Fore.RED + "仍然无法连接。退出程序。" + Style.RESET_ALL)
                return

        print(Fore.GREEN + "连接成功!" + Style.RESET_ALL)
        started_ok = True

        agent = LLMAgent(model_name=args.model, game_client=client, debug_prompt_file=args.debug_prompt_file or None)

        # 重试计数器
        retry_count = 0
        max_retries = 10  # 动画可能很长
        last_fail_kind = None
        last_idle = {"phase": None, "screen": None, "ts": 0.0}

        while True:
            state, kind, msg = client.get_state_detailed()
            if not state:
                retry_count += 1
                session.on_fetch_fail(kind, msg, time.time())
                if retry_count > max_retries:
                    status, confidence, reason = classify_run_end(
                        session.last_state, session.err_counts,
                        session.battles.last_battle_result, killed=False,
                    )
                    print(Fore.RED + f"\n连接丢失或游戏结束 ({reason})。" + Style.RESET_ALL)
                    break

                # 重试进度行: 错误类型变化或每 3 次才输出, 避免日志刷屏
                if kind != last_fail_kind or retry_count % 3 == 0:
                    sys.stdout.write(f"\r{Fore.YELLOW}无法获取状态 ({kind}), 正在重试 ({retry_count}/{max_retries})...{Style.RESET_ALL}")
                    sys.stdout.flush()
                    last_fail_kind = kind
                time.sleep(1)
                continue

            # 如果成功获取状态，重置计数器并清除之前的重试消息
            if retry_count > 0:
                sys.stdout.write("\n")  # 换行
                retry_count = 0
                last_fail_kind = None

            ts = time.time()
            battle_signal = session.on_state_ok(state, ts)

            # COMBAT: 状态变化/心跳时才重绘进度行, 不轮询刷屏; 静默提交 WAIT
            if state.screen_type == "NONE" and state.room_phase == "COMBAT":
                action = agent.choose_action(state)
                if action:
                    client.submit_action(action)
                if battle_signal:
                    sys.stdout.write(f"\r战斗进行中... (第 {state.floor} 层 | HP: {state.player.current_hp}/{state.player.max_hp} | 能量: {state.player.energy})   ")
                    sys.stdout.flush()
                time.sleep(args.interval)
                continue

            # Non-combat playable states: single-line summary per decision
            if state.screen_type != "NONE" and (state.choice_list or state.can_proceed):
                print(Fore.BLUE + f"\n--- 第 {state.floor} 层 (HP: {state.player.current_hp}/{state.player.max_hp} | 能量: {state.player.energy} | 屏幕: {state.screen_type}) ---" + Style.RESET_ALL)

                # Ask agent for action
                action = agent.choose_action(state)
                meta = getattr(agent, "last_decision", None) or {}
                source = meta.get("source", "unknown") if isinstance(meta, dict) else "unknown"

                if action:
                    label = _action_label(state, action)
                    t0 = time.time()
                    decision_id = session.record_decision(state, action, source, t0, meta)
                    submitted, server_resp, error_msg = client.submit_action(action)
                    latency_ms = (time.time() - t0) * 1000.0
                    session.tracker.on_submit(decision_id, submitted, server_resp, error_msg, latency_ms, time.time())

                    if submitted:
                        post_state = _fetch_post_action_state(client)
                        eff = session.tracker.confirm_immediate(decision_id, post_state, latency_ms, time.time())
                        if eff == "confirmed":
                            print(Fore.GREEN + f"✓ [{state.floor} {state.screen_type}] {label} → 已生效 ({latency_ms / 1000:.2f}s)" + Style.RESET_ALL)
                        else:
                            print(Fore.YELLOW + f"⏳ [{state.floor} {state.screen_type}] {label} → 已提交, 待后续确认" + Style.RESET_ALL)
                    else:
                        print(Fore.RED + f"✗ [{state.floor} {state.screen_type}] {label} 提交失败: {error_msg}" + Style.RESET_ALL)
                else:
                    print(Fore.YELLOW + "Agent 未选择任何行动。" + Style.RESET_ALL)

            else:
                # idle: 屏幕/阶段变化或 30s 才重绘, 不轮询刷屏
                if (state.room_phase != last_idle["phase"] or state.screen_type != last_idle["screen"]
                        or time.time() - last_idle["ts"] > 30.0):
                    sys.stdout.write(f"\r等待可操作状态... (当前阶段: {state.room_phase}, 屏幕: {state.screen_type})   ")
                    sys.stdout.flush()
                    last_idle = {"phase": state.room_phase, "screen": state.screen_type, "ts": time.time()}

            time.sleep(args.interval)

    except KeyboardInterrupt:
        status, confidence, reason = "killed", "high", "手动停止 (KeyboardInterrupt)"
        print("\n正在停止 AI...")
    except Exception as e:
        status, confidence, reason = "error", "high", str(e)
        import traceback
        traceback.print_exc()
    finally:
        ts = time.time()
        try:
            if started_ok:
                summary = session.finish(status, confidence, reason, ts)
                print(Fore.CYAN + "\n" + summary + Style.RESET_ALL)
            else:
                events.run_end_once(
                    status=status, confidence=confidence, reason=reason,
                    last_state=None, last_state_hash=None,
                    error={"kind": "CONNECTION_ERROR", "consecutive_failures": 2,
                           "counts": {"CONNECTION_ERROR": 2}},
                    duration_s=round(ts - session.started, 1),
                    ts=time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(ts)), t=round(ts, 3),
                )
        except Exception as e:
            print(Fore.RED + f"收尾处理异常: {e}" + Style.RESET_ALL)
        # 顺序: 摘要已经 tee 进 .log -> 恢复 stdout -> 关闭 JSONL 句柄 -> 关闭 logging
        tee.close()
        events.close()
        logging.shutdown()


if __name__ == "__main__":
    main()
