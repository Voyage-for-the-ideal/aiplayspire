# aiplayspire — Slay the Spire AI Bot

一个完整的《杀戮尖塔》(Slay the Spire) AI 代理系统,通过大语言模型(LLM)和本地价值网络,自动游玩游戏。

## 项目组成

```
StSCommunicationMod/     ← Java 游戏 Mod,通过 HTTP 暴露游戏状态和控制接口
sts_ai_framework/        ← Python AI 主客户端,轮询状态 → 决策 → 提交动作
selectcard/              ← Python 深度学习项目,训练 Set Transformer 生存价值网络
cardcrawl/               ← 反编译的游戏源码(只读参考,~2000 个 Java 文件)
```

三个子系统协同工作:

1. **StSCommunicationMod** 注入游戏进程,在 `localhost:5000` 启动 HTTP 服务,提供 `/state`、`/action`、`/card_info` 三个端点
2. **sts_ai_framework** 循环轮询游戏状态,根据当前场景(战斗/事件/商店/选卡等)调用 LLM 或本地价值网络做决策,然后通过 HTTP 向游戏提交动作
3. **selectcard** 从数十万局历史对局数据中训练出一个 Set Transformer 模型,用于评估卡牌、遗物、事件、商店决策的期望价值

## 快速开始

### 前置条件

- Java 运行环境(JRE)
- **ModTheSpire** + **BaseMod** (Steam 创意工坊安装)
- Python 3.10+
- Slay the Spire 游戏本体

### 1. 构建并安装 Mod

```bash
# 使用 Maven
cd StSCommunicationMod && mvn package

# 或使用自带构建脚本(无需 Maven)
javac Build.java && java Build
```

将生成的 `target/CommunicationMod.jar` 复制到游戏 `mods/` 目录,启动 ModTheSpire 并勾选 BaseMod + Communication Mod。

### 2. 安装 Python 依赖

```bash
pip install -r sts_ai_framework/requirements.txt
```

### 3. 配置

编辑 `sts_ai_framework/.env`:

```env
STS_API_BASE_URL=http://localhost:5000
LLM_MODEL=deepseek-chat
DEEPSEEK_API_KEY=your_api_key_here
```

### 4. 启动 AI

```bash
# 先启动游戏并进入一局
python -m sts_ai_framework --model deepseek/deepseek-chat --interval 2.0
```

## 决策架构

AI 根据 `screen_type` 选择不同的决策路径:

| screen_type | 决策方式 | 说明 |
|---|---|---|
| `NONE` (战斗) | LLM | 由大模型选择打牌 / 结束回合 |
| `EVENT` | 本地价值网络 | 正则解析事件选项效果,模型评估 |
| `SHOP_SCREEN` | 本地价值网络 | 贪心搜索最优购买组合 |
| `CARD_REWARD` | 本地价值网络 | 评估每张候选卡的价值 |
| `REST` (营火) | 本地价值网络 | 评估休息/锻造/挖掘/回忆等选项 |
| `GRID` | 本地价值网络 | 统一拦截删牌/强化/变换/复制选择 |
| `BOSS_REWARD` | 本地价值网络 | 评估 Boss 遗物 |
| `CHEST` | 硬编码 | 自动打开宝箱 |
| `COMBAT_REWARD` | 硬编码 | 优先级:遗物 > 金币 > 药水 > 卡牌 |
| `GAME_OVER` | 硬编码 | 自动前进 |
| `MAP` | LLM | BFS 距离辅助 LLM 选路 |
| `HAND_SELECT` | LLM | 卡牌选择类事件 |

## 主要特性

- **多模型支持**: 通过 litellm 支持 DeepSeek / OpenAI / Anthropic 等 LLM
- **动作生效检测**: 提交动作后轮询状态变化,自动重试或回退
- **安全回退链**: LLM 失败时依次尝试选项映射 → 前进/取消 → 战斗合法出牌 → 等待
- **地图分析**: BFS 从当前节点计算到最近营火/商店/精英的距离,辅助 LLM 决策
- **Omamori 感知**: 事件决策时自动检测是否持有驱魔护符,避免错估诅咒
- **Set Transformer 价值网络**: 排列不变性的牌组编码,无需位置信息

## 训练价值网络 (selectcard)

```bash
cd selectcard

# 数据预处理:原始 JSON → Parquet 训练样本
python src/data_pipeline.py

# 训练模型
python src/train.py

# 启动推理 API
uvicorn src.api:app --reload
```

模型输入: 牌组(卡牌 ID + 升级等级 + 数量)、遗物、楼层、HP、金币、进阶等级
模型输出: 当前阶段存活的概率 (Act 1 / Act 2 / Act 3 分段标签)

## 项目文件说明

```
aiplayspire/
├── README.md                          ← 本文件
├── CLAUDE.md                          ← Claude Code 项目指引
├── TODO.md                            ← 当前开发任务清单
├── .gitignore
├── StSCommunicationMod/               ← Java Mod
│   ├── src/main/java/                 ← Mod 源码
│   ├── pom.xml                        ← Maven 配置
│   ├── Build.java                     ← 无 Maven 构建脚本
│   └── build.ps1                      ← PowerShell 构建
├── sts_ai_framework/                  ← AI 客户端
│   ├── __main__.py                    ← 入口,主循环
│   ├── models.py                      ← Pydantic 状态/动作模型
│   ├── game_client.py                 ← HTTP 通信
│   ├── knowledge_base.py              ← 怪物 AI 模式 / 卡牌知识
│   ├── llm_agent.py                   ← Agent 组装入口
│   ├── llm_agent_parts/               ← Mixin 决策组件
│   ├── config.py                      ← 配置加载
│   ├── requirements.txt
│   └── .env                           ← API Key 等敏感配置
├── selectcard/                        ← 深度学习项目
│   ├── src/
│   │   ├── data_pipeline.py           ← 数据流水线
│   │   ├── reconstructor.py           ← 状态重建器
│   │   ├── model.py                   ← Set Transformer 模型
│   │   ├── dataset.py                 ← 数据集 / 分词器
│   │   ├── train.py                   ← 训练脚本
│   │   ├── inference.py               ← 推理引擎
│   │   └── api.py                     ← FastAPI 推理服务
│   ├── checkpoints/                   ← 模型权重
│   └── processed_data/                ← Parquet 训练数据
└── cardcrawl/                         ← 反编译游戏源码(只读)
    ├── cards/                         ← 卡牌类 (~300+)
    ├── relics/                        ← 遗物类 (~170)
    ├── powers/                        ← 能力效果类
    ├── monsters/                      ← 怪物类
    ├── actions/                       ← 动作队列类
    ├── rooms/                         ← 房间类型
    ├── events/                        ← 事件类
    └── ...
```

## 许可证

本项目仅供学习和研究使用。《杀戮尖塔》(Slay the Spire) 版权归 Mega Crit Games 所有。
