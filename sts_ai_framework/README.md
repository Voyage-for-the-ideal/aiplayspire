# Slay the Spire AI Framework (杀戮尖塔 AI 框架)

本项目是一个 Python 客户端框架，用于通过 HTTP 与 Slay the Spire 的 Communication Mod 通信，并用大语言模型自动决策游戏操作。

本框架对应的 Mod 源码位于：`D:\code\sts_workspace\StSCommunicationMod`

## 1. 项目定位

- `sts_ai_framework` 负责：轮询游戏状态、构造 Prompt、调用 LLM、把动作发送给 Mod。
- `StSCommunicationMod` 负责：在游戏进程内暴露 HTTP 接口并执行动作队列。
- 两者通过本地 HTTP 通信（默认 `http://localhost:5000`）。

## 2. 与 Mod 的精确对应关系

在 `D:\code\sts_workspace\StSCommunicationMod` 中，关键代码如下：

- `src/main/java/com/example/communicationmod/CommunicationMod.java`
    - 在 `receivePostInitialize()` 中启动 HTTP 服务：`server.start(5000)`。
    - 在 `receivePostUpdate()` 中更新状态并消费动作队列。
- `src/main/java/com/example/communicationmod/CommunicationServer.java`
    - 注册接口：`/state`、`/action`、`/card_info`。
    - `/action` 为 POST 入队模型，返回 `{"status": "queued"}`。

本框架中的对应文件：

- `config.py`：读取 `STS_API_BASE_URL`、`LLM_MODEL`。
- `game_client.py`：
    - `GET /state` -> 反序列化为 `GameState`
    - `POST /action` -> 发送动作 JSON
    - `POST /card_info` -> 按卡牌 ID 查询描述
- `models.py`：定义状态结构与动作结构（Pydantic）。
- `llm_agent.py`：状态转 Prompt、调用 litellm、解析 JSON 动作。
- `__main__.py`：主循环入口（轮询 -> 决策 -> 执行）。

## 3. 功能范围（按当前代码实现）

本项目不仅支持战斗操作，也支持非战斗选择界面：

- 战斗操作：`play`、`potion`、`end_turn`
- 带 `choice_list` 的选择界面（事件/删牌/地图节点等）：严格输出 `choose + choice_index`
- 无 `choice_list` 的按钮界面（如部分战后奖励页/仅前进页面）：执行 `proceed` 或 `cancel`
- 当非玩家可操作时机（如结束回合按钮不可点）会返回 `wait`

说明：

- `GRID` 删牌等界面严格以 `choice_list` 为准，不再把 `can_proceed`/`can_cancel` 混入候选，避免错误点击前进。
- 当界面没有 `choice_list`，但存在 `can_proceed`/`can_cancel` 时，会按“按钮态”处理，而不是错误回退为 `end_turn`。
- 当选择界面的候选项可映射到当前可见卡牌时，AI 会按需通过本地知识库或 `/card_info` 查询卡牌效果，并把效果补充到 Prompt 中。
- 地图界面会消费 Mod 导出的 ASCII 地图、当前位置（楼层 + 从左往右第几个房间）以及完整地图事实，由 AI 自主做路径评估后再映射回 `choice_index`。
- LLM 解析失败时采用分层回退：优先 `wait`/安全动作，再考虑 `end_turn`，避免直接空过。
- `game_client.py` 区分“动作已提交（HTTP 成功）”与“动作已生效（状态发生变化）”。
- Mod 端实际执行是异步的（入队成功不等于立刻生效）。

### 最近更新

- 修复：`GRID` 删牌界面严格按照 `choice_list` 选择，不再把 `proceed` 作为固定候选注入，避免 AI 在删牌时反复输出前进。
- 修复：`COMBAT_REWARD` 等 `choice_list` 为空但 `can_proceed=true` 的界面，新增“按钮态”处理，允许正确执行前进而不是误输出 `end_turn`。
- 改进：AI 现在会在必要时为选择界面的候选卡牌按需查询效果，优先使用本地知识库，未知时再调用 `/card_info`。
- 改进：移除了商店后“固定选第一个地图点”的脚本行为，地图选路改为纯 AI 决策。
- 改进：新增地图候选路线摘要（怪物/精英/火堆/商店等可达统计），帮助模型进行长线选路。
- 改进：地图展示已去除坐标文案，改为 ASCII 地图与“第几个房间”的人类可读表达。

## 4. 环境准备

前置条件：

1. 已安装《杀戮尖塔》
2. 已启用 BaseMod 与 CommunicationMod
3. 已安装 Anaconda/Miniconda（或任意 Python 3.10+ 环境）

推荐使用 conda：

```bash
conda create -n spire python=3.10
conda activate spire
cd D:\code\sts_workspace\sts_ai_framework
pip install -r requirements.txt
```

依赖（来自 `requirements.txt`）：

- `requests`
- `pydantic>=2.0`
- `python-dotenv`
- `colorama`
- `litellm`

## 5. 配置

编辑 `.env`：

```env
STS_API_BASE_URL=http://localhost:5000
LLM_MODEL=gpt-4o

# 按你实际使用的服务商填写
OPENAI_API_KEY=...
# ANTHROPIC_API_KEY=...
# DEEPSEEK_API_KEY=...
# MOONSHOT_API_KEY=...
```

建议：不要把包含真实密钥的 `.env` 提交到版本库。

## 6. 启动顺序

1. 启动游戏并勾选 BaseMod、CommunicationMod。
2. 进入一局游戏（战斗或可选择界面均可被 AI 处理）。
3. 在终端运行框架。

注意运行目录：

- 如果使用 `python -m sts_ai_framework`，请在 `D:\code\sts_workspace` 目录执行。

```bash
cd D:\code\sts_workspace
conda activate spire
python -m sts_ai_framework --model gpt-4o --interval 2.0
```

参数：

- `--model`：覆盖 `.env` 的 `LLM_MODEL`
- `--interval`：轮询与行动间隔（秒，默认 `2.0`）
- `--debug-prompt-file`：把每次最新发送给 LLM 的 Prompt 覆盖写入指定文件，便于在编辑器中实时查看

示例：

```bash
cd D:\code\aiplayspire
python -m sts_ai_framework --model deepseek/deepseek-chat --debug-prompt-file debug\latest_prompt.txt
```

也可以在 `.env` 中设置：

```env
DEBUG_PROMPT_FILE=debug/latest_prompt.txt
```

## 7. 动作协议（框架侧）

`models.py` 中定义的动作类型：

- `play`
- `potion`
- `end_turn`
- `wait`
- `proceed`
- `choose`
- `confirm`
- `skip`
- `cancel`

常见字段：

- `card_index`：手牌索引（0-based）
- `potion_index`：药水槽索引（0-based）
- `target_index`：目标怪索引（0-based）
- `choice_index`：选项索引（0-based）

`to_api_payload()` 会同时兼容写出 `card_index/card` 与 `target_index/target` 字段。

## 8. 项目结构

```text
sts_ai_framework/
    __main__.py          # CLI 入口与主循环
    config.py            # 环境变量配置
    game_client.py       # HTTP 通信层
    models.py            # 状态/动作数据模型
    agent_base.py        # Agent 抽象基类
    llm_agent.py         # 基于 LLM 的策略实现
    knowledge_base.py    # 怪物/卡牌静态知识
    requirements.txt
```

## 9. 常见问题

- 连接失败：
    - 确认游戏已通过 ModTheSpire 启动。
    - 确认 CommunicationMod 已加载。
    - 确认 `.env` 中 `STS_API_BASE_URL` 与 Mod 端口一致（默认 5000）。
- AI 长时间等待：
    - 某些动画或游戏线程忙碌阶段，`/state` 可能短暂不可用，主循环会自动重试。
- 看到“动作已提交到 Mod 队列”，但没有“检测到动作已生效”：
    - 这通常是动作还在队列或动画中。
    - 也可能是动作在游戏侧被判定为非法（例如目标/时机不满足）。
    - 可适当增大 `--interval`，并观察下一轮状态。
- 模型调用报错：
    - 检查 API Key、模型名与服务商兼容性（本项目通过 `litellm` 调用）。

## 10. 可扩展点

- 扩充 `knowledge_base.py` 中怪物与卡牌知识。
- 在 `llm_agent.py` 中修改 Prompt、决策规则与错误恢复逻辑。
- 在 `models.py` 中新增字段以适配 Mod 侧更丰富状态。
