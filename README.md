# aiplayspire — Slay the Spire AI Bot

一个完整的《杀戮尖塔》(Slay the Spire) AI 代理系统,结合大语言模型(LLM)、本地价值网络和战斗树搜索,自动游玩游戏。

## 项目组成

```
StSCommunicationMod/     ← Java 游戏 Mod,通过 HTTP 暴露游戏状态和控制接口
sts_ai_framework/        ← Python AI 主客户端,轮询状态 → 决策 → 提交动作
selectcard/              ← Python 深度学习项目,训练 Set Transformer 生存价值网络
STSStateSaver/           ← Java Mod,完整战斗状态序列化(JSON)
LudicrousSpeed/          ← Java Mod,快速战斗模拟引擎
scumthespire/            ← Java Mod,战斗 AI 树搜索(BattleAiMod)
cardcrawl/               ← 反编译的游戏源码(只读参考,~2000 个 Java 文件)
```

六个子系统协同工作:

1. **StSCommunicationMod** 注入游戏进程,在 `localhost:5000` 启动 HTTP 服务,提供 `/state`、`/action`、`/card_info` 三个端点
2. **sts_ai_framework** 循环轮询游戏状态,根据当前场景(战斗/事件/商店/选卡等)调用 LLM 或本地价值网络做决策,然后通过 HTTP 向游戏提交动作
3. **selectcard** 从数十万局历史对局数据中训练出一个 Set Transformer 模型,用于评估卡牌、遗物、事件、商店决策的期望价值
4. **STSStateSaver** 序列化完整战斗状态(玩家、怪物、能力、遗物、充能球、动作队列、选择界面)到 JSON,支持存档/读档
5. **LudicrousSpeed** 快速模拟引擎,用阻塞式模拟替代正常游戏动作循环,提供 Command 模式(打牌、药水、界面选择)以加速批量推演
6. **scumthespire** 战斗 AI (BattleAiMod),采用服务端/客户端架构 — AI 服务端无头运行,客户端发送战斗状态,服务端通过 `BattleAiController` 树搜索最优出牌序列

自动战斗功能由 **STSStateSaver + LudicrousSpeed + scumthespire** 组成。完整模块说明、架构设计、协作流程和扩展建议见 [AUTOFIGHT.md](AUTOFIGHT.md)。

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

### 4. 构建 Battle AI Mod (可选)

这三个 Mod 构成战斗树搜索流水线: **STSStateSaver** 序列化状态 → **LudicrousSpeed** 快速模拟 → **scumthespire** 树搜索最优出牌。

首次需建立依赖目录:

```bash
mkdir -p lib _ModTheSpire/mods
cp "D:/Program Files/Slay the Spire/desktop-1.0.jar" lib/
cp "D:/Program Files/Slay the Spire/ModTheSpire.jar" lib/
cp "D:/Program Files/Slay the Spire/mods/BaseMod.jar" _ModTheSpire/mods/
cp "D:/Program Files/Slay the Spire/mods/StSLib.jar" _ModTheSpire/mods/
```

按依赖顺序构建:

```bash
cd STSStateSaver && mvn package      # → ../_ModTheSpire/mods/SaveStateMod.jar
cd LudicrousSpeed && mvn package     # → ../_ModTheSpire/mods/LudicrousSpeed.jar
cd StSCommunicationMod && mvn package # → ../_ModTheSpire/mods/CommunicationMod.jar
cd scumthespire && mvn package       # → ../_ModTheSpire/mods/BattleAiMod.jar
```

或将所有四个 Java Mod 一并构建:

```bash
./build_all.sh
```

### 运行 Battle AI

1. 创建 `savestates` 和 `startstates` 空目录在游戏根目录
2. 启动服务端(无头模式): `java -DisServer=true -jar ModTheSpire.jar`
3. 启动客户端,正常开始一局,进入战斗后点击 "Start Steve" 按钮

### 5. 启动 LLM AI

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
├── AUTOFIGHT.md                       ← 自动战斗模块说明与架构文档
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
│   └── processed_data_v2/             ← Parquet 训练数据
├── STSStateSaver/                     ← 战斗状态序列化 Mod
│   └── src/main/java/savestate/
│       ├── SaveState.java             ← 根状态对象,完整战斗状态序列化
│       ├── PlayerState.java           ← 玩家状态
│       ├── monsters/                  ← 怪物状态(exordium/city/beyond/ending)
│       ├── powers/                    ← 能力效果状态(按角色/怪物分类)
│       ├── relics/                    ← 遗物状态
│       ├── orbs/                      ← 充能球状态
│       ├── actions/                   ← 动作队列状态
│       └── selectscreen/             ← 选择界面状态
├── LudicrousSpeed/                    ← 快速战斗模拟引擎
│   └── src/main/java/ludicrousspeed/
│       ├── simulator/
│       │   ├── ActionSimulator.java   ← 阻塞式模拟主循环
│       │   ├── commands/              ← Command 模式(打牌/药水/选择)
│       │   └── patches/               ← 游戏行为覆写(加速/跳过渲染)
│       └── Controller.java            ← 控制器接口
├── scumthespire/                      ← 战斗 AI 树搜索
│   └── src/main/java/battleaimod/
│       ├── BattleAiMod.java           ← Mod 入口
│       ├── battleai/
│       │   ├── BattleAiController.java ← 树搜索控制器
│       │   ├── TurnNode.java          ← 回合节点(按价值排序)
│       │   ├── StateNode.java         ← 单次动作状态快照
│       │   └── playorder/             ← 各角色出牌启发式排序
│       ├── networking/
│       │   ├── AiServer.java          ← AI 服务端(端口5125)
│       │   └── AiClient.java          ← AI 客户端
│       └── ValueFunctions.java        ← 状态价值评估函数
├── build_all.sh                       ← 一键构建所有 Java Mod
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
