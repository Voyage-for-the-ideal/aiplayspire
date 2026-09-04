import logging
import time
import os
import sys
import argparse
from dotenv import load_dotenv
from colorama import init, Fore, Style

# Try importing from package, otherwise fall back to local (if user runs script directly inside folder, though discouraged)
try:
    from .config import STS_API_BASE_URL, LLM_MODEL, DEBUG_PROMPT_FILE, RUN_LOG_DIR
    from .config import AUTO_RESTART, CHARACTER, ASCENSION, RESTART_DELAY, BATTLE_STALL_WARN_SECONDS
    from .game_client import GameClient
    from .llm_agent import LLMAgent
    from .models import ActionType
    from .run_lifecycle import LIFECYCLE_SCREENS, get_lifecycle_action, is_lifecycle_state
    from .battle_stall import StallWatcher
    from .run_log import setup_run_log, log_event, install_stdout_tee
except ImportError:
    # Hack to allow running python sts_ai_framework/__main__.py
    sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    from sts_ai_framework.config import STS_API_BASE_URL, LLM_MODEL, DEBUG_PROMPT_FILE, RUN_LOG_DIR
    from sts_ai_framework.config import AUTO_RESTART, CHARACTER, ASCENSION, RESTART_DELAY, BATTLE_STALL_WARN_SECONDS
    from sts_ai_framework.game_client import GameClient
    from sts_ai_framework.llm_agent import LLMAgent
    from sts_ai_framework.models import ActionType
    from sts_ai_framework.run_lifecycle import LIFECYCLE_SCREENS, get_lifecycle_action, is_lifecycle_state
    from sts_ai_framework.battle_stall import StallWatcher
    from sts_ai_framework.run_log import setup_run_log, log_event, install_stdout_tee

# Initialize colorama
init()


def _is_battle_owned_state(state) -> bool:
    return state.room_phase == "COMBAT"


def _is_action_effective(prev_state, next_state, action) -> bool:
    if next_state is None:
        return False

    # WAIT 不要求状态变化
    if action.type == ActionType.WAIT:
        return True

    if action.type in (ActionType.PLAY, ActionType.POTION):
        if len(next_state.hand) != len(prev_state.hand):
            return True
        if next_state.player.energy != prev_state.player.energy:
            return True
        if len(next_state.potions) != len(prev_state.potions):
            return True
        if [m.current_hp for m in next_state.monsters] != [m.current_hp for m in prev_state.monsters]:
            return True
        return False

    if action.type == ActionType.END_TURN:
        if prev_state.is_end_turn_button_enabled and not next_state.is_end_turn_button_enabled:
            return True
        if next_state.room_phase != prev_state.room_phase or next_state.screen_type != prev_state.screen_type:
            return True
        return False

    # 选择类动作，观察界面/可选项是否变化
    if next_state.screen_type != prev_state.screen_type:
        return True
    if next_state.choice_list != prev_state.choice_list:
        return True
    # 角色选择界面的字段变化（选角/难度调整不改变屏幕与选项列表）
    if getattr(next_state, "selected_character", None) != getattr(prev_state, "selected_character", None):
        return True
    if action.type == ActionType.SET_ASCENSION:
        if getattr(next_state, "ascension_mode", None) != getattr(prev_state, "ascension_mode", None):
            return True
        if getattr(next_state, "ascension_level", None) != getattr(prev_state, "ascension_level", None):
            return True
    prev_event = getattr(prev_state, "event", None)
    next_event = getattr(next_state, "event", None)
    if prev_event != next_event:
        return True
    if next_state.can_proceed != prev_state.can_proceed or next_state.can_cancel != prev_state.can_cancel:
        return True
    if next_state.floor != prev_state.floor or next_state.room_phase != prev_state.room_phase:
        return True
    return False


def _fetch_post_action_state(client, retries: int = 2, delay: float = 0.15):
    for _ in range(retries):
        s = client.get_state()
        if s is not None:
            return s
        time.sleep(delay)
    return None

def main():
    parser = argparse.ArgumentParser(description="运行杀戮尖塔 AI Agent")
    parser.add_argument("--model", type=str, default=LLM_MODEL, help="使用的 DeepSeek 模型 (例如 deepseek-v4-flash)")
    parser.add_argument("--interval", type=float, default=2.0, help="行动间隔时间 (秒)")
    parser.add_argument("--debug-prompt-file", type=str, default=DEBUG_PROMPT_FILE, help="将最新 Prompt 持续写入到指定文件，便于调试")
    parser.add_argument("--character", type=str, default=CHARACTER,
                        help="自动重开时选择的角色 (IRONCLAD/SILENT/DEFECT/WATCHER)")
    parser.add_argument("--ascension", type=int, default=ASCENSION,
                        help="自动重开时的进阶等级 (0 = 普通难度)")
    parser.add_argument("--no-auto-restart", dest="auto_restart", action="store_false",
                        help="对局结束后不自动开始下一局（保持旧行为，等待人工处理）")
    parser.set_defaults(auto_restart=AUTO_RESTART)
    parser.add_argument("--restart-delay", type=float, default=RESTART_DELAY,
                        help="对局结束/开局提交后的额外等待时间 (秒)")
    args = parser.parse_args()

    # 安装运行日志:此后所有终端输出同时写入 debug/run_YYYYMMDD_HHMMSS.log
    run_log_path = setup_run_log(RUN_LOG_DIR)
    tee = install_stdout_tee()

    print(Fore.YELLOW + "正在启动杀戮尖塔 AI 框架..." + Style.RESET_ALL)
    print(f"模型: {args.model}")
    print(f"连接到 Mod 地址: {STS_API_BASE_URL}")
    if args.auto_restart:
        print(f"自动重开: 开启 (角色: {args.character}, 进阶: A{args.ascension})")
    else:
        print("自动重开: 关闭")
    if args.debug_prompt_file:
        print(f"Prompt 调试文件: {args.debug_prompt_file}")
    print(f"运行日志: {run_log_path}")

    client = GameClient(base_url=STS_API_BASE_URL)
    
    # Check connection
    print("正在检查与 Mod 的连接...")
    state = client.get_state()
    if not state:
        print(Fore.RED + "无法连接到游戏。请确保《杀戮尖塔》已启动并加载了 CommunicationMod。" + Style.RESET_ALL)
        # return # Allow retry or just fail
        # Let's fail gracefully but maybe user hasn't started game yet.
        print(Fore.YELLOW + "5秒后重试..." + Style.RESET_ALL)
        time.sleep(5)
        state = client.get_state()
        if not state:
            print(Fore.RED + "仍然无法连接。退出程序。" + Style.RESET_ALL)
            return

    print(Fore.GREEN + "连接成功!" + Style.RESET_ALL)

    agent = LLMAgent(model_name=args.model, game_client=client, debug_prompt_file=args.debug_prompt_file or None)
    
    # 重试计数器
    retry_count = 0
    max_retries = 10 # 增加重试次数，因为动画可能很长
    pending_submission = None
    pending_submission_timeout = 15.0
    prev_state = None
    stall_watcher = StallWatcher(BATTLE_STALL_WARN_SECONDS)

    try:
        while True:
            state = client.get_state()
            if not state:
                retry_count += 1
                if retry_count > max_retries:
                    print(Fore.RED + "\n连接丢失或游戏结束 (连续多次获取状态失败)。" + Style.RESET_ALL)
                    break

                # 在同一行显示重试状态，避免刷屏
                sys.stdout.write(f"\r{Fore.YELLOW}无法获取状态 (Mod忙碌或动画中)，正在重试 ({retry_count}/{max_retries})...{Style.RESET_ALL}")
                sys.stdout.flush()
                time.sleep(1)
                continue

            # 如果成功获取状态，重置计数器并清除之前的重试消息
            if retry_count > 0:
                sys.stdout.write("\n") # 换行
                retry_count = 0

            # 对局边界日志:进入 GAME_OVER 记 run_end；从生命周期界面回到对局内记 run_start
            if prev_state is not None:
                prev_screen = prev_state.screen_type
                if prev_screen != "GAME_OVER" and state.screen_type == "GAME_OVER":
                    log_event(event="run_end", reason=state.game_over_reason,
                              ts=time.strftime("%H:%M:%S"), floor=state.floor, act=state.act,
                              character=state.character, ascension=state.ascension_level,
                              hp=f"{state.player.current_hp}/{state.player.max_hp}" if state.player else None)
                if prev_screen in LIFECYCLE_SCREENS and state.screen_type not in LIFECYCLE_SCREENS and state.character:
                    log_event(event="run_start", character=state.character,
                              ascension=state.ascension_level, ts=time.strftime("%H:%M:%S"))
            prev_state = state

            # Battle-stall watchdog: combat is BattleAiMod's job, so all the
            # framework can do about a frozen fight is report it.
            stall_seconds = stall_watcher.observe(state, time.monotonic(),
                                                  in_battle=_is_battle_owned_state(state))
            if stall_seconds is not None:
                print(Fore.RED + f"\n战斗停滞告警: 战斗状态已 {stall_seconds:.0f} 秒无任何变化，"
                                 f"BattleAiMod 可能已停止行动，请人工检查游戏。" + Style.RESET_ALL)
                log_event(event="battle_stall", stall_seconds=round(stall_seconds, 1),
                          ts=time.strftime("%H:%M:%S"), floor=state.floor, act=state.act,
                          hp=f"{state.player.current_hp}/{state.player.max_hp}" if state.player else None,
                          screen=state.screen_type)

            # The Mod acknowledges actions when they enter its queue, before the
            # game necessarily consumes them.  Do not enqueue the same choice on
            # every polling iteration while the old screen is still visible.
            if pending_submission is not None:
                pending_state, pending_action, submitted_at = pending_submission
                if _is_action_effective(pending_state, state, pending_action):
                    pending_submission = None
                elif time.monotonic() - submitted_at < pending_submission_timeout:
                    time.sleep(args.interval)
                    continue
                else:
                    print(Fore.YELLOW + "排队动作超时且状态未变化，允许重新决策。" + Style.RESET_ALL)
                    pending_submission = None

            # Run lifecycle: game over / main menu / character select.
            # Must be checked BEFORE the battle-owned branch: the death screen
            # still reports room_phase COMBAT, but combat is over.
            if is_lifecycle_state(state):
                action = get_lifecycle_action(
                    state,
                    character=args.character,
                    ascension=args.ascension,
                    auto_restart=args.auto_restart,
                )

                if action is None or action.type == ActionType.WAIT:
                    if state.screen_type == "GAME_OVER":
                        if args.auto_restart:
                            sys.stdout.write(f"\r{Fore.YELLOW}对局结束 (原因: {state.game_over_reason or '未知'})，正在返回主菜单...   {Style.RESET_ALL}")
                        else:
                            sys.stdout.write(f"\r{Fore.YELLOW}对局结束 (原因: {state.game_over_reason or '未知'})，自动重开已关闭，等待人工处理...   {Style.RESET_ALL}")
                    else:
                        sys.stdout.write(f"\r{Fore.YELLOW}位于主菜单 (自动重开{'开启' if args.auto_restart else '关闭'})...   {Style.RESET_ALL}")
                    sys.stdout.flush()
                    time.sleep(args.interval)
                    continue

                print(Fore.BLUE + f"\n--- 生命周期操作 (屏幕: {state.screen_type}) ---" + Style.RESET_ALL)
                msg = f"行动: {action.type}"
                if action.type == ActionType.CHOOSE:
                    msg += f" 选择索引: {action.choice_index} ({(state.choice_list or [''])[action.choice_index] if action.choice_index is not None and action.choice_index < len(state.choice_list or []) else '?'})"
                elif action.type == ActionType.SET_ASCENSION:
                    msg += f" 难度: A{action.level}"
                print(msg)

                log_event(action=action.type.value, choice=action.choice_index, level=action.level,
                          ts=time.strftime("%H:%M:%S"), screen=state.screen_type,
                          selected=state.selected_character, ascension=state.ascension_level)
                submitted, server_resp, error_msg = client.submit_action(action)
                effective = False
                post_state = None
                if submitted:
                    post_state = _fetch_post_action_state(client)
                    effective = _is_action_effective(state, post_state, action)
                    if effective:
                        print(Fore.GREEN + "生命周期操作已生效。" + Style.RESET_ALL)
                    else:
                        print(Fore.YELLOW + "生命周期操作已提交，暂未观察到状态变化（界面过渡中）。" + Style.RESET_ALL)
                        pending_submission = (state, action, time.monotonic())
                else:
                    print(Fore.RED + f"生命周期操作提交失败: {error_msg}" + Style.RESET_ALL)
                log_event(result=action.type.value, submitted=submitted, effective=effective,
                          error=error_msg if not submitted else None, ts=time.strftime("%H:%M:%S"),
                          post_screen=post_state.screen_type if submitted and post_state else None)

                # 开局提交后多等一拍，让菜单淡出/地图生成完成
                time.sleep(args.restart_delay if action.type == ActionType.PROCEED else args.interval)
                continue

            # COMBAT: BattleAiMod handles all combat-time decision screens,
            # including HAND_SELECT/GRID/CARD_REWARD.
            if _is_battle_owned_state(state):
                sys.stdout.write(f"\r战斗进行中... (第 {state.floor} 层 | HP: {state.player.current_hp}/{state.player.max_hp} | 能量: {state.player.energy})   ")
                sys.stdout.flush()
                time.sleep(args.interval)
                continue

            # Non-combat playable states: verbose output
            if state.screen_type != "NONE" and (state.choice_list or state.can_proceed):
                print(Fore.BLUE + f"\n--- 第 {state.floor} 层 (HP: {state.player.current_hp}/{state.player.max_hp} | 能量: {state.player.energy} | 屏幕: {state.screen_type}) ---" + Style.RESET_ALL)

                # Ask agent for action
                action = agent.choose_action(state)

                if action:
                    pre_action_state = state
                    # 决策事件(提交前):记录意图与前置状态
                    log_event(action=action.type.value, card=action.card_index, target=action.target_index,
                              potion=action.potion_index, choice=action.choice_index,
                              ts=time.strftime("%H:%M:%S"), floor=state.floor, act=state.act,
                              phase=state.room_phase, screen=state.screen_type,
                              hp=f"{state.player.current_hp}/{state.player.max_hp}",
                              energy=state.player.energy, hand=len(state.hand))
                    msg = f"行动: {action.type}"
                    if action.type == ActionType.PLAY:
                         msg += f" 卡牌索引: {action.card_index} 目标索引: {action.target_index}"
                    elif action.type == ActionType.POTION:
                        msg += f" 药水索引: {action.potion_index} 目标索引: {action.target_index}"
                    elif action.type == ActionType.CHOOSE:
                         msg += f" 选择索引: {action.choice_index}"
                    print(msg)

                    submitted, server_resp, error_msg = client.submit_action(action)
                    post_state = None
                    if submitted:
                        print(Fore.GREEN + "行动已提交到 Mod 队列。" + Style.RESET_ALL)
                        if server_resp is not None:
                            print(f"Mod 响应: {server_resp}")

                        post_state = _fetch_post_action_state(client)
                        if _is_action_effective(pre_action_state, post_state, action):
                            print(Fore.GREEN + "检测到动作已生效。" + Style.RESET_ALL)
                        else:
                            print(Fore.YELLOW + "动作已提交，但暂未观察到明显状态变化（可能仍在动画或队列处理中）。" + Style.RESET_ALL)
                            if action.type != ActionType.WAIT:
                                pending_submission = (pre_action_state, action, time.monotonic())
                    else:
                        print(Fore.RED + f"行动提交失败: {error_msg}" + Style.RESET_ALL)

                    # 结果事件(提交后):提交状态、是否生效、失败原因与后置状态
                    effective = _is_action_effective(pre_action_state, post_state, action) if submitted else None
                    log_event(result=action.type.value, submitted=submitted, effective=effective,
                              error=error_msg if not submitted else None,
                              ts=time.strftime("%H:%M:%S"), floor=state.floor,
                              post_hp=f"{post_state.player.current_hp}/{post_state.player.max_hp}" if post_state else None,
                              post_energy=post_state.player.energy if post_state else None,
                              post_hand=len(post_state.hand) if post_state else None,
                              post_screen=post_state.screen_type if post_state else None,
                              post_phase=post_state.room_phase if post_state else None)
                else:
                    print(Fore.YELLOW + "Agent 未选择任何行动。" + Style.RESET_ALL)

            else:
                # print(f"Waiting for combat... (Current phase: {state.room_phase})")
                # Don't spam logs
                sys.stdout.write(f"\r等待可操作状态... (当前阶段: {state.room_phase}, 屏幕: {state.screen_type})   ")
                sys.stdout.flush()
                pass

            time.sleep(args.interval)

    except KeyboardInterrupt:
        print("\n正在停止 AI...")
    finally:
        # 写入残留行缓冲、恢复 sys.stdout,并关闭日志 handler 确保全部落盘
        tee.close()
        logging.shutdown()

if __name__ == "__main__":
    main()
