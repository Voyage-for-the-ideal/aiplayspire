# Slay the Spire AI Framework

这是一个用于驱动 Slay the Spire 的 Python AI 客户端。
它通过 HTTP 与 CommunicationMod 通信，循环执行“取状态 -> 选动作 -> 提交动作”。

## 项目目标

1. 持续轮询游戏状态并转为结构化数据。
2. 用 LLM + 本地策略模型做决策。
3. 将动作提交给 Mod，并检测动作是否生效。

## 精简后的项目结构

```text
sts_ai_framework/
  __main__.py                     # CLI 入口与主循环
  __init__.py
  config.py                       # .env 配置加载
  game_client.py                  # /state /action /card_info HTTP 客户端
  models.py                       # Pydantic 状态与动作模型
  knowledge_base.py               # 怪物/卡牌知识
  agent_base.py                   # Agent 抽象基类
  llm_agent.py                    # LLMAgent 组装与初始化（轻量入口）
  llm_agent_parts/
    __init__.py
    action_mixin.py               # choose_action 主流程、LLM 调用与回退
    choice_mixin.py               # 统一选项、营火/奖励处理、战斗 fallback
    decision_mixin.py             # 本地 value model 决策（商店/选卡/事件）
    info_prompt_mixin.py          # 卡牌解析、地图摘要、Prompt 构建
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
LLM_MODEL=gpt-4o
DEBUG_PROMPT_FILE=debug/latest_prompt.txt

# 按需配置供应商密钥（litellm）
# OPENAI_API_KEY=...
# DEEPSEEK_API_KEY=...
# ANTHROPIC_API_KEY=...
```

## 启动

在项目根目录执行：

```bash
cd D:/code/aiplayspire
python -m sts_ai_framework --model deepseek/deepseek-chat --interval 2.0
```

可用参数：

1. --model：覆盖 .env 的 LLM_MODEL。
2. --interval：轮询与行动间隔（秒）。
3. --debug-prompt-file：将最新 Prompt 持续写入指定文件。

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

## 行为策略概览

1. 选择态严格使用 choice_list 与 choice_index 映射。
2. 按钮态在无 choice_list 时处理 proceed/cancel。
3. 战斗失败回退优先安全动作，避免直接无脑 end_turn。
4. 商店/事件/选卡可走本地 value model 决策。
5. Prompt 会融合地图摘要、卡牌信息和战斗上下文。

## 常见问题

1. 无法连接到游戏：确认 Mod 已加载且端口与 STS_API_BASE_URL 一致。
2. 动作“已提交”但未生效：可能仍在动画或动作队列中，稍后会同步到状态。
3. 模型报错：检查模型名与对应 API Key 是否已配置。

## 后续建议

1. 在 tests 目录新增回归测试，覆盖 choice/button/combat 三类关键状态。
2. 为 llm_agent_parts 增加类型注解协议（Protocol）约束，降低 mixin 耦合风险。
