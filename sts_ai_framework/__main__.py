import time
import os
import sys
import argparse
from dotenv import load_dotenv
from colorama import init, Fore, Style

# Try importing from package, otherwise fall back to local (if user runs script directly inside folder, though discouraged)
try:
    from .config import STS_API_BASE_URL, LLM_MODEL, DEBUG_PROMPT_FILE
    from .game_client import GameClient
    from .llm_agent import LLMAgent
    from .models import ActionType
except ImportError:
    # Hack to allow running python sts_ai_framework/__main__.py
    sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    from sts_ai_framework.config import STS_API_BASE_URL, LLM_MODEL, DEBUG_PROMPT_FILE
    from sts_ai_framework.game_client import GameClient
    from sts_ai_framework.llm_agent import LLMAgent
    from sts_ai_framework.models import ActionType

# Initialize colorama
init()


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
    parser.add_argument("--model", type=str, default=LLM_MODEL, help="使用的 LLM 模型 (例如 gpt-4o, claude-3-opus, gemini-pro)")
    parser.add_argument("--interval", type=float, default=2.0, help="行动间隔时间 (秒)")
    parser.add_argument("--debug-prompt-file", type=str, default=DEBUG_PROMPT_FILE, help="将最新 Prompt 持续写入到指定文件，便于调试")
    args = parser.parse_args()

    print(Fore.YELLOW + "正在启动杀戮尖塔 AI 框架..." + Style.RESET_ALL)
    print(f"模型: {args.model}")
    print(f"连接到 Mod 地址: {STS_API_BASE_URL}")
    if args.debug_prompt_file:
        print(f"Prompt 调试文件: {args.debug_prompt_file}")

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

            # Check if game is in a playable state (combat or choice screen)
            if state.room_phase == "COMBAT" or (state.screen_type != "NONE" and (state.choice_list or state.can_proceed)):
                print(Fore.BLUE + f"\n--- 第 {state.floor} 层 (HP: {state.player.current_hp}/{state.player.max_hp} | 能量: {state.player.energy} | 屏幕: {state.screen_type}) ---" + Style.RESET_ALL)
                
                # Ask agent for action
                action = agent.choose_action(state)
                
                if action:
                    pre_action_state = state
                    msg = f"行动: {action.type}"
                    if action.type == ActionType.PLAY:
                         msg += f" 卡牌索引: {action.card_index} 目标索引: {action.target_index}"
                    elif action.type == ActionType.POTION:
                        msg += f" 药水索引: {action.potion_index} 目标索引: {action.target_index}"
                    elif action.type == ActionType.CHOOSE:
                         msg += f" 选择索引: {action.choice_index}"
                    print(msg)
                    
                    submitted, server_resp, error_msg = client.submit_action(action)
                    if submitted:
                        print(Fore.GREEN + "行动已提交到 Mod 队列。" + Style.RESET_ALL)
                        if server_resp is not None:
                            print(f"Mod 响应: {server_resp}")

                        post_state = _fetch_post_action_state(client)
                        if _is_action_effective(pre_action_state, post_state, action):
                            print(Fore.GREEN + "检测到动作已生效。" + Style.RESET_ALL)
                        else:
                            print(Fore.YELLOW + "动作已提交，但暂未观察到明显状态变化（可能仍在动画或队列处理中）。" + Style.RESET_ALL)
                    else:
                        print(Fore.RED + f"行动提交失败: {error_msg}" + Style.RESET_ALL)
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

if __name__ == "__main__":
    main()
