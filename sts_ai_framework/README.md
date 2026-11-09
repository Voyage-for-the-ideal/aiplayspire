# Slay the Spire AI Framework

这是一个用于驱动 Slay the Spire 的 Python AI 客户端。
它通过 HTTP 与 CommunicationMod 通信，循环执行“取状态 -> 选动作 -> 提交动作”。

## 项目目标

1. 持续轮询游戏状态并转为结构化数据。
2. 用本地策略模型（价值网络 + 地图路由）做决策；复杂或非结构化界面再走 LLM 兜底。
3. 将动作提交给 Mod，并检测动作是否生效。

## 精简后的项目结构

```text
sts_ai_framework/
  __main__.py                     # CLI 入口与主循环（动作生效检测、运行日志安装）
  __init__.py
  config.py                       # .env 配置加载
  game_client.py                  # /state /action /card_info HTTP 客户端
  models.py                       # Pydantic 状态与动作模型
  run_lifecycle.py                # 对局生命周期状态机：结束→主菜单→选角/难度→下一局
  knowledge_base.py               # 怪物/卡牌知识
  agent_base.py                   # Agent 抽象基类
  llm_agent.py                    # LLMAgent 组装与初始化（DeepSeek 客户端 + 本地价值网络）
  run_log.py                      # 运行日志：终端输出双写 + EVENT 结构化事件
  llm_agent_parts/
    __init__.py
    action_mixin.py               # choose_action 主流程、状态流转与回退
    choice_mixin.py               # 统一选项、按钮处理、战斗 fallback
    decision_mixin.py             # 本地 value model 决策（商店/选卡/事件/Boss遗物）
    info_prompt_mixin.py          # 卡牌解析、结构化事件效果与非地图 Prompt 构建
    route_mixin.py                # MAP 分发、legacy 基线与确定性 fallback 适配
  routing/                        # 确定性地图规划器（图、上下文、特征、评分、决策）
    map_graph.py                  # 地图 DAG 校验、普通/Winged 边与路径枚举
    context.py                    # RouteContext 与 HP/Gold 状态分桶
    features.py                   # 恢复、强制风险、弹性与钥匙可达性特征
    policy.py                     # 集中式路由参数
    evaluator.py                 # 路径与候选评分
    planner.py                   # 约束过滤、确定性选择与四档 fallback
    decision.py                  # RouteDecision 及评分分项
  tests/
    test_main_loop.py             # 战斗接管判定回归测试
    test_run_lifecycle.py         # 生命周期状态机回归测试
    test_run_log.py               # 运行日志 Tee/ANSI/事件格式测试
    test_structured_events.py     # 结构化事件与动作生效检测测试
  requirements.txt
  README.md
  .env
```

说明：

1. 旧的临时测试脚本和缓存文件已移除。
2. 目录已按职责拆分，llm_agent.py 不再堆积业务细节。

## 环境准备

前置条件：

1. 已安装 Slay the Spire。
2. 已启用 BaseMod 与 CommunicationMod。
3. Python 3.10+（建议 conda）。

示例：

```bash
conda create -n spire python=3.10
conda activate spire
cd D:/code/aiplayspire
pip install -r sts_ai_framework/requirements.txt
```

## 配置

编辑 sts_ai_framework/.env：

```env
STS_API_BASE_URL=http://localhost:5000
LLM_MODEL=deepseek-flash[1m]
DEBUG_PROMPT_FILE=debug/latest_prompt.txt

# DeepSeek API (OpenAI 兼容格式)
DEEPSEEK_API_KEY=sk-...
# DEEPSEEK_BASE_URL=https://api.deepseek.com
# 默认绕过系统代理（Windows 注册表代理无法访问校内端点，会报 APIConnectionError）
LLM_TRUST_ENV=false

# 自动重开（对局结束后自动开始下一局，需要 CommunicationMod >= 1.4.0）
AUTO_RESTART=true
CHARACTER=DEFECT        # DEFECT（机器人） / IRONCLAD / SILENT / WATCHER
ASCENSION=15            # 进阶等级，0 = 普通难度
RESTART_DELAY=2.0       # 对局结束/开局提交后的额外等待（秒）
BATTLE_STALL_WARN_SECONDS=300  # 战斗状态无变化的停摆告警阈值（秒），0 关闭
```

角色的统一配置入口是 `sts_ai_framework/.env` 中的 `CHARACTER`；修改这一项即可影响自动连续开局。命令行 `--character` 仅用于临时覆盖本次运行。

## 自动重开

对局结束（死亡/胜利/解锁界面）后，框架会自动：返回主菜单 → 按配置选择角色与进阶等级 → 开始下一局 → Neow 祝福走现有 LLM 决策链路。期间 `run_log` 会记录 `run_end` / `run_start` 结构化事件（含原因、角色、难度）。若主菜单存在残留存档，会先按游戏原生逻辑放弃该存档再开新局。

- 需要 CommunicationMod 1.4.0+（旧版 mod 下主菜单不可见，框架会照旧重试后退出）。
- 关闭自动重开：`.env` 设 `AUTO_RESTART=false` 或命令行 `--no-auto-restart`（对局结束停在结算界面等待人工处理）。
- 战斗内搜索不存在胜利路线时，BattleAiMod 1.4.1+ 会自动打出存活最久的死亡路线让角色战死（`stop_reason: ALL_LOSE`），死亡后照常触发自动重开，详见 [AUTOFIGHT.md](../AUTOFIGHT.md)。
- 战斗停摆看门狗（仅告警）：战斗中状态连续 `BATTLE_STALL_WARN_SECONDS` 秒（默认 300，0 关闭）无任何变化时，`run_log` 记录 `battle_stall` 事件并在终端提示人工检查——战斗决策属于 BattleAiMod，框架只报告不干预。

## 启动

在项目根目录执行：

```bash
cd D:/code/aiplayspire
python -m sts_ai_framework --model deepseek-flash[1m] --interval 2.0
```

可用参数：

1. --model：覆盖 .env 的 LLM_MODEL。
2. --interval：轮询与行动间隔（秒）。
3. --debug-prompt-file：将最新 Prompt 持续写入指定文件。
4. --character：自动重开角色（默认取 .env 的 CHARACTER，默认 DEFECT（机器人）；可选 IRONCLAD/SILENT/DEFECT/WATCHER）。
5. --ascension：自动重开进阶等级（默认取 .env 的 ASCENSION，0 = 普通）。
6. --no-auto-restart：关闭自动重开。
7. --restart-delay：对局结束/开局提交后的额外等待（秒）。
8. --policy-checkpoint：离线非战斗策略 checkpoint 路径；默认关闭。
9. --policy-mode：策略运行模式 `shadow`（默认，仅影子评分）或 `policy`（接管显式启用的决策类型）。
10. --policy-types：显式启用策略的决策类型列表；其余保持影子评分。
11. --policy-trace-dir：策略 trace 输出目录，同时用于采集训练数据。
12. --policy-runtime-manifest：实际 mod/战斗配置指纹 JSON，作为采集元数据保存。
13. --max-runs：采集模式下最多运行的局数（需正整数并配合 --policy-trace-dir）。

策略数据、训练与评估说明见 [docs/NONCOMBAT_POLICY.md](../docs/NONCOMBAT_POLICY.md)。

## 通信与动作协议

Mod 接口：

1. GET /state
2. POST /action
3. POST /card_info

主要动作类型（见 models.py）：

1. play
2. potion
3. end_turn
4. wait
5. proceed
6. choose
7. confirm
8. skip
9. cancel
10. set_ascension（生命周期：设置角色选择界面的进阶等级，0 关闭进阶模式）

## 行为策略概览

1. 选择态严格使用 choice_list 与 choice_index 映射。
2. 按钮态在无 choice_list 时处理 proceed/cancel。
3. 地图路由使用确定性规划器执行（6 层有限视野、硬约束与安全约束检查），不进入 LLM。
4. 战斗失败回退优先安全动作，避免直接无脑 end_turn。
5. 商店/事件/选卡可走本地 value model 决策。
6. LLM Prompt 只服务于非地图兜底，融合卡牌信息与结构化事件效果，不包含地图路线信息。

## 常见问题

1. 无法连接到游戏：确认 Mod 已加载且端口与 STS_API_BASE_URL 一致。
2. 动作“已提交”但未生效：可能仍在动画或动作队列中，稍后会同步到状态。
3. 模型报错：检查模型名与对应 API Key 是否已配置。

## 后续建议

1. 为 llm_agent_parts 增加类型注解协议（Protocol）约束，降低 mixin 耦合风险。
2. 增加回归测试覆盖 choice/button/combat 三类关键状态的组合场景。
