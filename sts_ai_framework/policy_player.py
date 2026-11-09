"""Play supported reward choices with a policy checkpoint; leave other screens to the user."""
import argparse
import json
import math
from pathlib import Path
import sys
import time

from .__main__ import _is_action_effective
from .battle_stall import StallWatcher
from .config import BATTLE_STALL_WARN_SECONDS, STS_API_BASE_URL
from .game_client import GameClient
from .policy_runtime import PolicyAgent
from .run_lifecycle import is_lifecycle_state

DEFAULT_CHECKPOINT = (Path(__file__).resolve().parent.parent / "selectcard" /
                      "experiment_results" / "run_policy" / "pilot-121289" /
                      "model" / "best.pt")
REWARD_SCREENS = frozenset({"CARD_REWARD", "BOSS_REWARD"})


def state_key(state):
    return json.dumps(state.model_dump(mode="json"), sort_keys=True, ensure_ascii=False)


class PolicyPlayer:
    """One polling step at a time; never invokes a baseline decision agent."""

    def __init__(self, client, policy, *, stall_seconds=BATTLE_STALL_WARN_SECONDS,
                 pending_timeout=15.0, output=print):
        self.client = client
        self.policy = policy
        self.stall = StallWatcher(stall_seconds)
        self.pending_timeout = pending_timeout
        self.output = output
        self.pending = None
        self.last_notice = None
        self.last_attempt = None
        self.timeout_reported = False

    def notice(self, key, message):
        if self.last_notice != key:
            self.output(message)
            self.last_notice = key

    def step(self, state, now):
        if state is None:
            self.notice(("connection",), "等待 CommunicationMod 状态；请检查游戏连接。")
            return
        key = state_key(state)
        if self.last_attempt is not None and self.last_attempt != key:
            self.last_attempt = None
        # The game-over screen can retain room_phase=COMBAT.
        in_battle = state.room_phase == "COMBAT" and not is_lifecycle_state(state)
        stalled = self.stall.observe(state, now, in_battle)
        if stalled is not None:
            self.output(f"战斗停滞 {stalled:.0f} 秒，请检查 Battle AI 和搜索服务。")
        if self.pending is not None:
            previous, action, submitted_at = self.pending
            if _is_action_effective(previous, state, action):
                self.output("动作已生效。")
                self.pending = None
                self.timeout_reported = False
            else:
                if now - submitted_at >= self.pending_timeout and not self.timeout_reported:
                    self.output("动作已提交但尚未确认，请检查游戏；等待状态变化，避免重复提交。")
                    self.timeout_reported = True
                return
        if is_lifecycle_state(state):
            self.notice(("lifecycle", key), f"{state.screen_type}：请手动开始或结束对局。")
            return
        if in_battle:
            self.notice(("combat", state.floor), f"第 {state.floor} 层战斗中，由 Battle AI 接管。")
            return
        if state.screen_type not in REWARD_SCREENS or state.screen_type not in self.policy.artifact["supported_decisions"]:
            self.notice(("manual", key), f"{state.screen_type}：请手动操作；等待状态变化。")
            return
        if self.last_attempt == key:
            return
        self.last_attempt = key
        try:
            result = self.policy._score(state)
        except Exception as exc:
            self.notice(("error", key), f"Policy 推理失败：{exc}；请手动操作。")
            return
        if result is None:
            self.notice(("incomplete", key), f"{state.screen_type} 的候选或状态不完整，请手动操作。")
            return
        action, _ = result
        # A user may have operated the screen while the model was running.
        current = self.client.get_state()
        if current is None or state_key(current) != key:
            self.notice(("changed", key), "决策期间状态已变化或暂时不可读取，等待下一次轮询。")
            self.last_attempt = None
            return
        submitted, _, error = self.client.submit_action(action)
        if not submitted:
            self.notice(("submit-error", key), f"动作提交失败：{error}；请检查并手动处理。")
            return
        comparison = self.policy.last_comparison or {}
        self.output(f"Policy 选择 {comparison.get('selected', action.type.value)}，动作已提交，等待确认。")
        self.pending = (state, action, now)
        self.timeout_reported = False
        self.last_notice = None


def main(argv=None):
    parser = argparse.ArgumentParser(description="使用 policy 模型选择奖励，其余非战斗界面手动操作")
    parser.add_argument("--policy-checkpoint", type=Path, default=DEFAULT_CHECKPOINT)
    parser.add_argument("--api-base-url", default=STS_API_BASE_URL)
    parser.add_argument("--interval", type=float, default=2.0)
    args = parser.parse_args(argv)
    if not math.isfinite(args.interval) or args.interval <= 0:
        parser.error("--interval 必须是大于 0 的有限数")
    checkpoint = args.policy_checkpoint.expanduser().resolve()
    try:
        policy = PolicyAgent(None, str(checkpoint), mode="policy", enabled_types=REWARD_SCREENS)
        supported = policy.artifact["supported_decisions"]
        profile = policy.artifact["input_profile"]
        if not isinstance(supported, (list, tuple, set)) or not all(isinstance(x, str) for x in supported):
            raise ValueError("checkpoint supported_decisions 格式错误")
        if not REWARD_SCREENS.intersection(supported):
            raise ValueError("checkpoint 未支持奖励选牌或 Boss 遗物选择")
    except Exception as exc:
        print(f"Policy checkpoint 加载失败 ({checkpoint}): {exc}", file=sys.stderr)
        return 1
    print(f"Policy checkpoint: {checkpoint}\n输入规范: {profile}\n支持的决策: {', '.join(sorted(supported))}")
    print(f"自动处理: {', '.join(sorted(REWARD_SCREENS.intersection(supported)))}；游戏地址: {args.api_base_url}")
    player = PolicyPlayer(GameClient(base_url=args.api_base_url), policy)
    try:
        while True:
            player.step(player.client.get_state(), time.monotonic())
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("\nPolicy 程序已停止。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
